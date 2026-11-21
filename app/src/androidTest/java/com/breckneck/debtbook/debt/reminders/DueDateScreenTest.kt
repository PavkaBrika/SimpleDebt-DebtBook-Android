package com.breckneck.debtbook.debt.reminders

import com.breckneck.debtbook.debt.awaitTag
import androidx.compose.ui.test.*
import com.breckneck.debtbook.debt.record
import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.*
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.deptbook.domain.util.LedgerDate
import database.AppDataBase
import entity.Debt
import entity.Human
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal


class DueDateScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao get() = AppDataBase(context).appDao()
    private val prefs get() = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val id = 900065
    private var oldPin = false
    private var oldAds = false
    @Before fun setup() {
        oldPin = prefs.getBoolean("pin_code_is_enabled", false); oldAds = prefs.getBoolean("ads_disabled", false)
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true).commit()
        clean()
    }
    @After fun cleanup() {
        clean()
        prefs.edit().putBoolean("pin_code_is_enabled", oldPin).putBoolean("ads_disabled", oldAds).commit()
    }
    private fun clean() { dao.deleteDebtsByHumanId(id); dao.deleteHumanById(id) }
    private fun row() = dao.getAllDebtsById(id).first { it.id == id }
    private fun await(message: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 15000
        while (!condition()) { if (System.currentTimeMillis() > end) fail(message); Thread.sleep(50) }
    }
    private fun waitForHistory() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("debtDueSheet").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        await("history") {
            try {
                compose.record(id).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun edit() {
        waitForHistory()
        compose.record(id).performClick()
        compose.onNodeWithTag("dueSettingsButton").assertExists()
        compose.onNodeWithTag("editButton").performClick()
    }

    private fun applyDate() {
        compose.onNodeWithTag("dueSaveButton").assertIsEnabled().performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithTag("debtDueSheet").fetchSemanticsNodes(false).isEmpty() }
    }

    private fun saveForm() { compose.awaitTag("setFinanceButton").performClick() }

    @Test fun editReminderFromDebtFormBothDirectionsIncludingAfterRepayment() {
        assertTrue("Grant notifications before running", DebtReminders.get(context).allowed())
        for (sign in listOf(1, -1)) {
            dao.insertHuman(Human(id, "Due screen", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(id, BigDecimal(100 * sign), id, null, "2026-11-16"))
            dao.setDebtDueDate(id, "2099-12-31", 600, "legacy-token", System.currentTimeMillis())
            if (sign < 0) {
                dao.saveDebt(Debt(0, BigDecimal("20"), id, null, "2026-11-17",
                    repaymentOfId = id, operationId = "test-due-repayment-1"))
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.onHumanClick(id, "EUR", "Due screen") }
                waitForHistory()
                val datesColumn = hasAnyAncestor(hasTestTag("historyDebtDates"))
                compose.onNode(datesColumn and hasText(LedgerDate.display("2026-11-16")), useUnmergedTree = true).assertExists()
                compose.onNode(datesColumn and hasText(scenario.screenString(R.string.due_reminder_on) + " · 10:00"), useUnmergedTree = true).assertExists()

                compose.record(id).performClick()
                compose.onNodeWithTag("editButton").assertExists()
                compose.onNodeWithTag("debtActionsCancel").performClick()

                edit()
                if (sign < 0) {
                    compose.awaitTag("lockedAmount").assert(hasSetTextAction().not())
                    compose.onNodeWithTag("directionNegative").assertIsNotEnabled()
                    compose.onNodeWithTag("createDebtDate").assertIsNotEnabled()
                }
                compose.openDueCalendarInForm()
                compose.onNodeWithTag("dueReminderSwitch").assertIsOn()
                compose.enterDebtDate(scenario, "2026-11-15")
                compose.onNodeWithTag("dueSaveButton").assertIsNotEnabled()
                compose.onNodeWithTag("dueCancelButton").performClick()

                compose.openDueCalendarInForm()
                pressBack()
                compose.onNodeWithTag("debtDueSheet").assertDoesNotExist()
                compose.openDueCalendarInForm()
                applyDate()
                saveForm()
                waitForHistory()
                assertEquals("2099-12-31", row().dueDay)
                assertEquals(600, row().reminderMinute)
                assertEquals("legacy-token", row().reminderToken)

                edit()
                compose.openDueCalendarInForm()
                compose.enterDebtDate(scenario, "2099-12-29")
                applyDate()
                assertEquals("2099-12-31", row().dueDay)
                pressBack()
                waitForHistory()
                assertEquals("2099-12-31", row().dueDay)

                edit()
                compose.openDueCalendarInForm()
                compose.enterDebtDate(scenario, "2099-12-30")
                scenario.recreate()
                compose.onNodeWithTag("dueReminderSwitch").assertIsOn()
                applyDate()
                saveForm()
                await("reschedule") { row().dueDay == "2099-12-30" }
                assertNotEquals("legacy-token", row().reminderToken)
                assertEquals(600, row().reminderMinute)

                edit()
                compose.openDueCalendarInForm()
                compose.onNodeWithTag("dueTimeButton").performScrollTo().performClick()
                compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("timeInput")))[0].performTextReplacement("16")
                compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("timeInput")))[1].performTextReplacement("45")
                compose.onNodeWithTag("timeConfirm").performClick()
                compose.onNodeWithTag("dueTimeButton").assertTextContains("16:45", substring = true)
                applyDate()
                assertEquals(600, row().reminderMinute)
                val oldToken = row().reminderToken
                saveForm()
                await("minute changed") { row().reminderMinute == 16 * 60 + 45 }
                assertNotEquals(oldToken, row().reminderToken)
                fun scheduled() = android.app.PendingIntent.getBroadcast(context, id,
                    android.content.Intent(context, DebtReminderReceiver::class.java).apply {
                        action = DebtReminders.ACTION_DELIVER
                        data = android.net.Uri.parse("simpledebt://reminder/$id")
                    }, android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE) != null
                val scheduler = context.getSharedPreferences("debt_reminder_schedule", Context.MODE_PRIVATE)
                await("new time scheduled in Android") {
                    scheduled() && scheduler.getString("token-$id", null) == row().reminderToken
                }

                edit()
                compose.openDueCalendarInForm()
                compose.onNodeWithTag("dueReminderSwitch").performScrollTo().performClick()
                compose.onNodeWithTag("dueReminderSwitch").assertIsOff()
                applyDate()
                saveForm()
                await("disable") { row().reminderToken == null }
                assertEquals("2099-12-30", row().dueDay)
                await("disabled alarm removed") { !scheduled() }
                edit()
                compose.openDueCalendarInForm()
                compose.onNodeWithTag("dueReminderSwitch").performScrollTo().performClick()
                applyDate()
                saveForm()
                await("alarm enabled again") { scheduled() }
                edit()
                compose.openDueCalendarInForm()
                compose.onNodeWithTag("clearDueDate").performScrollTo().performClick()
                applyDate()
                assertNotNull(row().dueDay)
                saveForm()
                await("due date and scheduled alarm cleared") { row().dueDay == null && !scheduled() }
                assertEquals(0, (100 * sign).toBigDecimal().compareTo(row().sum))
                assertEquals(0, (if (sign > 0) 100 else -80).toBigDecimal().compareTo(dao.getHumanSumDebt(id)))
            }
            clean()
        }
    }
}
