package com.breckneck.debtbook.debt.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.InvalidationTracker
import com.breckneck.debtbook.R
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.RepaymentPolicy
import database.AppDataBase
import database.ReminderRestoreHook
import java.util.concurrent.Executors

/** All OS scheduling is serialized. The database remains the source of truth. */
class DebtReminders private constructor(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val db by lazy { AppDataBase(context) }
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("debt_reminder_schedule", Context.MODE_PRIVATE)
    private val observer = object : InvalidationTracker.Observer("Debt", "Human") {
        override fun onInvalidated(tables: Set<String>) { refresh() }
    }

    fun start() {
        channel()
        executor.execute { safely { db.invalidationTracker.addObserver(observer) } }
        ReminderRestoreHook.afterRestore = { synchronized(this) { safely { reconcile(clear = true) } } }
        refresh()
    }

    fun allowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun refresh(onComplete: (() -> Unit)? = null) {
        executor.execute {
            synchronized(this) { safely { reconcile() } }
            onComplete?.invoke()
        }
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.due_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun alarm(id: Int, token: String? = null): PendingIntent = PendingIntent.getBroadcast(context, id,
        Intent(context, DebtReminderReceiver::class.java).apply {
            action = ACTION_DELIVER
            data = Uri.parse("simpledebt://reminder/$id")
            putExtra(EXTRA_DEBT, id)
            putExtra(EXTRA_TOKEN, token)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancel(id: Int) {
        val pending = alarm(id)
        alarms.cancel(pending)
        pending.cancel()
        notifications.cancel(TAG, id)
    }

    @Synchronized
    internal fun reconcile(clear: Boolean = false, now: Long = System.currentTimeMillis()) {
        channel()
        db.runInTransaction {
            val history = db.appDao().getAllDebts().map { it.toDomain() }
            val known = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
            val rows = history.filter { it.repaymentOfId == null }
            val ids = rows.map { it.id }.toSet()
            (known - ids).forEach(::cancel)
            if (clear) (known + ids).forEach(::cancel)
            rows.forEach { stored ->
                val remembered = stored.reminderToken?.let { prefs.getBoolean("sent-${stored.id}-$it", false) } == true
                val row = if (remembered && !stored.reminderDelivered) stored.copy(reminderDelivered = true) else stored
                if (row != stored) db.appDao().getAllDebts().firstOrNull { it.id == row.id }?.let {
                    db.appDao().updateDebt(it.copy(reminderDelivered = true))
                }
                val next = DebtReminderPolicy.next(row, history, now)
                if (next != null && allowed()) {
                    // A changed token also clears an obsolete already shown notification.
                    if (prefs.getString("token-${row.id}", null) != row.reminderToken) notifications.cancel(TAG, row.id)
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm(row.id, row.reminderToken))
                    prefs.edit().putString("token-${row.id}", row.reminderToken).apply()
                } else if (row.reminderDelivered && row.reminderToken != null &&
                    RepaymentPolicy.remaining(row, history).signum() > 0 && !clear) {
                    // Preserve a delivered notification until read, disabled, deleted or settled.
                    val pending = alarm(row.id, row.reminderToken)
                    alarms.cancel(pending)
                    pending.cancel()
                } else cancel(row.id)
            }
            prefs.edit().putStringSet("ids", ids.map { it.toString() }.toSet()).commit()
        }
    }

    fun deliver(id: Int, token: String?, done: () -> Unit) {
        executor.execute {
            try { synchronized(this) { safely { deliverNow(id, token) } } }
            finally { done() }
        }
    }

    @Synchronized
    internal fun deliverNow(id: Int, token: String?, now: Long = System.currentTimeMillis()): Boolean {
        if (token == null || !allowed() || prefs.getBoolean("sent-$id-$token", false)) return false
        val claimed = db.runInTransaction<Boolean> {
            val dao = db.appDao()
            val history = dao.getAllDebts()
            val row = history.firstOrNull { it.id == id } ?: return@runInTransaction false
            if (row.reminderToken != token || row.reminderDelivered || row.dueDay == null ||
                row.reminderMinute == null || row.dueDay!! < DebtReminderPolicy.today(now) ||
                DebtReminderPolicy.trigger(row.dueDay!!, row.reminderMinute!!) > now ||
                RepaymentPolicy.remaining(row.toDomain(), history.map { it.toDomain() }).signum() <= 0)
                return@runInTransaction false
            dao.updateDebt(row.copy(reminderDelivered = true))
            true
        }
        if (!claimed) return false
        prefs.edit().putBoolean("sent-$id-$token", true).commit()
        // Claim is committed before posting: process death here may lose a reminder, never replay it.
        db.runInTransaction {
            val history = db.appDao().getAllDebts().map { it.toDomain() }
            val row = history.firstOrNull { it.id == id }
            if (row != null && row.reminderToken == token && row.reminderDelivered &&
                RepaymentPolicy.remaining(row, history).signum() > 0 && allowed()) {
                notifications.notify(TAG, id, NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_reminder)
                    .setContentTitle(context.getString(R.string.due_notification_title))
                    .setContentText(context.getString(R.string.due_notification_text))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(openDebt(id)).setAutoCancel(true).build())
            }
        }
        return true
    }

    internal fun openDebt(id: Int): PendingIntent = PendingIntent.getActivity(context, id,
        Intent(context, ReminderEntryActivity::class.java).apply {
            action = ACTION_OPEN
            data = Uri.parse("simpledebt://debt/$id")
            putExtra(EXTRA_DEBT, id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun safely(block: () -> Unit) {
        try { block() } catch (error: Exception) { Log.w("DebtReminders", "Could not update reminder schedule", error) }
    }

    companion object {
        const val CHANNEL = "personal_debt_reminders"
        const val TAG = "personal-debt"
        const val EXTRA_DEBT = "reminderDebtId"
        const val EXTRA_TOKEN = "reminderToken"
        const val ACTION_DELIVER = "com.breckneck.debtbook.REMIND_DEBT"
        const val ACTION_OPEN = "com.breckneck.debtbook.OPEN_DEBT"
        @Volatile private var instance: DebtReminders? = null
        fun get(context: Context): DebtReminders = instance ?: synchronized(this) {
            instance ?: DebtReminders(context.applicationContext).also { instance = it }
        }
    }
}
