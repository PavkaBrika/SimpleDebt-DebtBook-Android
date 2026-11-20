package com.breckneck.debtbook.debt.reminders

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import database.AppDataBase
import entity.Debt
import entity.Human
import repository.AppDataRepositoryImpl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/** Requires notifications granted on a disposable emulator before instrumentation starts. */
class DebtReminderDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = AppDataBase(context)
    private val dao get() = db.appDao()
    private val reminders get() = DebtReminders.get(context)
    private val id = 900060
    private fun active() = context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.tag == DebtReminders.TAG && it.id == id }
    private fun assertActive(expected: Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (active() != expected && System.currentTimeMillis() < end) Thread.sleep(50)
        assertEquals(expected, active())
    }
    private fun original() = dao.getAllDebts().single { it.id == id }
    private fun seed(sign: Int = 1) {
        dao.insertHuman(Human(id, "Reminder test", BigDecimal.ZERO, "EUR"))
        dao.saveDebt(Debt(id, BigDecimal(100 * sign), id, null, "2026-11-16"))
    }
    @Before fun setup() { cleanup(); assertTrue("Grant POST_NOTIFICATIONS before test", reminders.allowed()) }
    @After fun cleanup() {
        dao.deleteDebtsByHumanId(id); dao.deleteHumanById(id)
        context.getSharedPreferences("debt_reminder_schedule", Context.MODE_PRIVATE).edit().apply {
            context.getSharedPreferences("debt_reminder_schedule", Context.MODE_PRIVATE).all.keys.filter { it.startsWith("sent-$id-") }.forEach(::remove)
        }.commit()
        reminders.reconcile()
    }
    @Test fun oneDeliveryStaleTokenPartialFullUndoAndDeleteBothDirections() {
        for (sign in listOf(1, -1)) {
            seed(sign)
            val now = System.currentTimeMillis()
            val today = DebtReminderPolicy.today(now)
            dao.updateDebt(original().copy(dueDay = today, reminderMinute = 0, reminderToken = "delivery-1"))
            reminders.reconcile()
            assertFalse(reminders.deliverNow(id, "stale", now))
            assertTrue(reminders.deliverNow(id, "delivery-1", now))
            assertActive(true)
            assertFalse(reminders.deliverNow(id, "delivery-1", now))
            dao.saveDebt(Debt(0, BigDecimal(-20 * sign), id, null, "2026-11-16", repaymentOfId = id, operationId = "payment-000000$sign-1"))
            reminders.reconcile()
            assertTrue(original().reminderDelivered)
            assertActive(true)
            dao.saveDebt(Debt(0, BigDecimal(-80 * sign), id, null, "2026-11-16", repaymentOfId = id, operationId = "payment-000000$sign-2"))
            reminders.reconcile()
            assertActive(false); assertNull(original().reminderToken)
            dao.removeDebt(dao.getAllDebtsById(id).last())
            reminders.reconcile()
            assertActive(false); assertFalse(reminders.deliverNow(id, "delivery-1", now))
            cleanup()
            seed(sign)
            dao.updateDebt(original().copy(dueDay = today, reminderMinute = 0, reminderToken = "delete-1"))
            assertTrue(reminders.deliverNow(id, "delete-1", now))
            dao.removeDebt(original()); reminders.reconcile()
            assertActive(false); assertFalse(reminders.deliverNow(id, "delete-1", now))
            cleanup()
        }
    }
    @Test fun repeatedReplanningAndRestoreOnlyKeepFutureUndeliveredOutstandingReminders() {
        val repo = AppDataRepositoryImpl(context)
        val before = repo.getAllAppData()
        try {
            seed()
            dao.setDebtDueDate(id, "2099-12-31", 540, "future-1", System.currentTimeMillis())
            repeat(3) { reminders.reconcile() }
            val snapshot = repo.getAllAppData()
            repo.replaceAllAppData(BackupCodec().decode(BackupCodec().encode(snapshot)).data)
            repeat(3) { reminders.reconcile() }
            assertEquals("future-1", original().reminderToken)
            assertFalse(original().reminderDelivered)
            dao.setDebtDueDate(id, "2099-12-30", 600, "future-2", System.currentTimeMillis())
            reminders.reconcile()
            assertFalse(reminders.deliverNow(id, "future-1"))
            val unsent = repo.getAllAppData()
            val future = DebtReminderPolicy.trigger("2099-12-30", 600) + 1000
            assertTrue(reminders.deliverNow(id, "future-2", future))
            assertActive(true)
            repo.replaceAllAppData(unsent) // Old unsent snapshot after a clock/timezone change.
            assertTrue(original().reminderDelivered)
            assertFalse(reminders.deliverNow(id, "future-2", future))
            assertActive(false)
            val expired = snapshot.copy(debtList = snapshot.debtList.map { if (it.id == id) it.copy(dueDay = "2020-01-01") else it })
            repo.replaceAllAppData(expired)
            assertNull(original().reminderToken); assertActive(false)
            val delivered = snapshot.copy(debtList = snapshot.debtList.map { if (it.id == id) it.copy(reminderDelivered = true) else it })
            repo.replaceAllAppData(delivered)
            reminders.reconcile()
            assertTrue(original().reminderDelivered); assertActive(false)
            dao.deleteDebtsByHumanId(id); dao.deleteHumanById(id); reminders.reconcile()
            assertActive(false)
        } finally { repo.replaceAllAppData(before) }
    }
}
