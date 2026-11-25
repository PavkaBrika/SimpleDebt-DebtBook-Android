package com.breckneck.debtbook.debt.reminders

import com.breckneck.debtbook.debt.awaitTag
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import com.breckneck.debtbook.debt.record
import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.deptbook.domain.util.LedgerDate
import database.AppDataBase
import entity.Debt
import entity.Human
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
    private var oldTheme: String? = null
    @Before fun setup() {
        oldPin = prefs.getBoolean("pin_code_is_enabled", false); oldAds = prefs.getBoolean("ads_disabled", false)
        oldTheme = prefs.getString("app_theme", null)
        val dark = InstrumentationRegistry.getArguments().getString("theme") == "dark"
        prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true)
            .putString("app_theme", if (dark) "DARK" else "LIGHT").commit()
        clean()
    }
    @After fun cleanup() {
        clean()
        prefs.edit().putBoolean("pin_code_is_enabled", oldPin).putBoolean("ads_disabled", oldAds).putString("app_theme", oldTheme).commit()
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

    private fun openDue(fromActions: Boolean = false) {
        waitForHistory()
        if (fromActions) {
            compose.record(id).performClick()
            compose.awaitTag("dueSettingsButton").performClick()
        } else {
            compose.onNodeWithTag("loanDue-$id").performClick()
        }
        compose.awaitTag("debtDueSheet").assertIsDisplayed()
    }

    private fun applyDate() {
        compose.onNodeWithTag("dueSaveButton").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        waitForHistory()
    }

    private fun capture() {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "details-due-sheet.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun changeAndClearReminderFromDetailsInBothDirections() {
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
                val dates = hasAnyAncestor(hasTestTag("historyDebtDates"))
                compose.onNode(dates and hasText(LedgerDate.display("2026-11-16")), useUnmergedTree = true).assertExists()
                compose.onNode(dates and hasText(scenario.screenString(R.string.due_reminder_on) + " · 10:00"),
                    useUnmergedTree = true).assertExists()

                openDue(fromActions = true)
                compose.onNodeWithTag("dueReminderSwitch").assertIsOn()
                val calendar = compose.awaitTag("dueDatePicker").fetchSemanticsNode().boundsInWindow
                val headline = compose.awaitTag("debtCalendarHeadline").fetchSemanticsNode().boundsInWindow
                assertTrue(headline.left > calendar.left && headline.left < calendar.center.x)
                if (compose.onNodeWithTag("debtCalendarMode").isDisplayed()) {
                    val pencil = compose.onNodeWithTag("debtCalendarMode").fetchSemanticsNode().boundsInWindow
                    assertEquals(headline.center.y, pencil.center.y, 2f)
                    assertTrue(headline.right <= pencil.left)
                }
                if (sign > 0) capture()
                compose.enterDebtDate(scenario, "2026-11-15")
                compose.onNodeWithTag("dueSaveButton").assertIsNotEnabled()
                compose.onNodeWithTag("dueCancelButton").performClick()
                waitForHistory()

                openDue()
                compose.enterDebtDate(scenario, "2099-12-29")
                pressBack()
                compose.waitForIdle()
                if (compose.onNodeWithTag("debtDueSheet").isDisplayed()) pressBack()
                waitForHistory()
                assertEquals("2099-12-31", row().dueDay)
                openDue()
                applyDate()
                assertEquals("legacy-token", row().reminderToken)

                openDue()
                compose.enterDebtDate(scenario, "2099-12-30")
                scenario.recreate()
                compose.awaitTag("dueReminderSwitch").assertIsOn()
                assertEquals("2099-12-31", row().dueDay)
                applyDate()
                await("reschedule") { row().dueDay == "2099-12-30" }
                assertNotEquals("legacy-token", row().reminderToken)
                assertEquals(600, row().reminderMinute)

                openDue()
                compose.onNodeWithTag("dueTimeButton").performScrollTo().performClick()
                val inputs = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("timeInput")))
                inputs[0].performTextReplacement("16")
                inputs[1].performTextReplacement("45")
                compose.awaitTag("timeConfirm").performClick()
                compose.onNodeWithTag("dueTimeButton").assertTextContains("16:45")
                assertEquals(600, row().reminderMinute)
                val oldToken = row().reminderToken
                applyDate()
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
                openDue()
                compose.onNodeWithTag("dueReminderSwitch").performScrollTo().performClick().assertIsOff()
                applyDate()
                assertEquals("2099-12-30", row().dueDay)
                await("disabled alarm removed") { row().reminderToken == null && !scheduled() }
                openDue()
                val toggle = compose.onNodeWithTag("dueReminderSwitch").performScrollTo()
                val fields = compose.onNodeWithTag("dueReminderFields")
                val collapsed = fields.fetchSemanticsNode().size.height
                compose.mainClock.autoAdvance = false
                try {
                    toggle.performSemanticsAction(SemanticsActions.OnClick)
                    compose.mainClock.advanceTimeBy(96)
                    val growing = fields.fetchSemanticsNode().size.height
                    compose.mainClock.advanceTimeBy(320)
                    val expanded = fields.fetchSemanticsNode().size.height
                    assertTrue("Animated height $collapsed → $growing → $expanded", collapsed < growing && growing < expanded)
                } finally {
                    compose.mainClock.autoAdvance = true
                }
                applyDate()
                await("alarm enabled again") { scheduled() }
                openDue()
                val clear = compose.awaitTag("clearDueDate").assertIsDisplayed()
                val save = compose.awaitTag("dueSaveButton").assertIsDisplayed()
                assertTrue(clear.fetchSemanticsNode().boundsInWindow.right <= save.fetchSemanticsNode().boundsInWindow.left)
                assertNotNull(row().dueDay)
                clear.performClick()
                waitForHistory()
                await("due date and scheduled alarm cleared") {
                    row().dueDay == null && row().reminderMinute == null && row().reminderToken == null && !scheduled()
                }
                assertEquals(0, (100 * sign).toBigDecimal().compareTo(row().sum))
                assertEquals(0, (if (sign > 0) 100 else -80).toBigDecimal().compareTo(dao.getHumanSumDebt(id)))
            }
            clean()
        }
    }
}
