package com.breckneck.debtbook.debt.reminders

import androidx.compose.ui.test.*
import com.breckneck.debtbook.debt.awaitTag
import com.breckneck.debtbook.settings.edgeSwipe
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.AuthSession
import com.breckneck.debtbook.auth.PinCodeStore
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.deptbook.domain.util.CRYPTO_FILE_NAME
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import database.AppDataBase
import entity.Debt
import entity.Human
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.text.DecimalFormatSymbols

class ReminderPinNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun notificationPendingIntentKeepsPinAndOpensTheSpecificDebt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDataBase(context).appDao()
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val previous = prefs.all.toMap()
        val file = File(context.filesDir, CRYPTO_FILE_NAME)
        val bytes = file.takeIf { it.exists() }?.readBytes()
        val id = 900066
        val token = "pin-navigation-${System.nanoTime()}"
        val now = System.currentTimeMillis()
        val reminders = DebtReminders.get(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        try {
            dao.deleteDebtsByHumanId(id); dao.deleteHumanById(id)
            dao.insertHuman(Human(id, "Protected owner", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(id, BigDecimal("10.5"), id, "Requested original", "2026-11-16"))
            dao.saveDebt(Debt(id + 1, BigDecimal("50"), id, "Other original", "2026-11-16"))
            dao.updateDebt(dao.getAllDebtsById(id).first { it.id == id }.copy(
                dueDay = DebtReminderPolicy.today(now), reminderMinute = 0, reminderToken = token))
            val fromShade = InstrumentationRegistry.getArguments().getString("openNotificationShade") == "true"
            if (fromShade) reminders.refresh() else {
                val posted = reminders.deliverNow(id, token, now)
                assertTrue(posted || dao.getAllDebtsById(id).single { it.id == id }.reminderDelivered)
            }
            prefs.edit().putBoolean("pin_code_is_enabled", true).putBoolean("fingerprint_auth_is_enabled", false)
                .putLong("pin_lock_delay_millis", 0L).putBoolean("ads_disabled", true).commit()
            PinCodeStore(context).save("1234"); AuthSession.lock()
            fun notification() = notifications.activeNotifications.firstOrNull { it.tag == DebtReminders.TAG && it.id == id }
            fun waitForPinKeypad() = compose.waitUntil(15_000) {
                compose.onAllNodesWithText("9").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            run {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitForPinKeypad()
                compose.waitUntil(if (fromShade) 120_000 else 5000) { notification() != null }
                val inst = InstrumentationRegistry.getInstrumentation()
                val entry = inst.addMonitor(ReminderEntryActivity::class.java.name, null, false)
                try {
                    if (fromShade) {
                        // Device acceptance mode: tap the actual notification in the expanded Android shade.
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(
                            inst.uiAutomation.executeShellCommand("cmd statusbar expand-notifications")
                        ).use { it.readBytes() }
                    } else checkNotNull(notification()).notification.contentIntent.send()
                    assertNotNull("Notification must open ReminderEntryActivity", inst.waitForMonitorWithTimeout(entry, 30_000))
                } finally { inst.removeMonitor(entry) }
                waitForPinKeypad()
                assertFalse(AuthSession.isUnlocked)
                "9999".forEach { compose.onNodeWithText(it.toString()).performClick() }
                assertFalse(AuthSession.isUnlocked)
                "1234".forEach { compose.onNodeWithText(it.toString()).performClick() }
                compose.waitUntil(15000) { AuthSession.isUnlocked }
                val amount = "10${DecimalFormatSymbols().decimalSeparator}50 EUR"
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText(amount, substring = true)
                        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                }
                compose.onNodeWithTag("debtActionsCancel").assertExists()
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single() as MainActivity
                    val nav = activity.supportFragmentManager.findFragmentById(R.id.frameLayout) as androidx.navigation.fragment.NavHostFragment
                    val owner = requireNotNull(nav.childFragmentManager.primaryNavigationFragment)
                    assertEquals(id, androidx.lifecycle.ViewModelProvider(owner)[com.breckneck.debtbook.debt.details.DebtDetailsViewModel::class.java].debtActionId.value)
                }
                if (android.os.Build.VERSION.SDK_INT >= 35) {
                    inst.edgeSwipe(false) {}
                } else compose.onNodeWithTag("debtActionsCancel").performClick()
                compose.onNodeWithText("Protected owner").assertIsDisplayed()
                compose.onNodeWithText("Reminder already sent").assertDoesNotExist()
                compose.onNodeWithText("Напоминание уже отправлено").assertDoesNotExist()
                if (android.os.Build.VERSION.SDK_INT >= 35) {
                    android.os.SystemClock.sleep(400)
                    inst.edgeSwipe(false) {}
                    compose.awaitTag("addHumanButton").assertIsDisplayed()
                }
            }
        } finally {
            if (android.os.Build.VERSION.SDK_INT >= 29) InstrumentationRegistry.getInstrumentation()
                .uiAutomation.executeShellCommand("cmd statusbar collapse").close()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }.distinct().forEach { it.finish() }
            }
            dao.deleteDebtsByHumanId(id); dao.deleteHumanById(id)
            notifications.cancel(DebtReminders.TAG, id)
            context.getSharedPreferences("debt_reminder_schedule", Context.MODE_PRIVATE).edit().remove("sent-$id-$token").commit()
            val editor = prefs.edit()
            for (key in listOf("pin_code_is_enabled", "fingerprint_auth_is_enabled", "ads_disabled"))
                (previous[key] as? Boolean)?.let { editor.putBoolean(key, it) } ?: editor.remove(key)
            (previous["pin_lock_delay_millis"] as? Long)?.let { editor.putLong("pin_lock_delay_millis", it) } ?: editor.remove("pin_lock_delay_millis")
            editor.commit(); bytes?.let { file.writeBytes(it) } ?: file.delete(); AuthSession.lock()
        }
    }
}
