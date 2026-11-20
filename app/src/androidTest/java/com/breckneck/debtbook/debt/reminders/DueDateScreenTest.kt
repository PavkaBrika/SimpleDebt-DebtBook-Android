package com.breckneck.debtbook.debt.reminders

import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
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
            compose.onAllNodesWithTag("debtDueSheet").fetchSemanticsNodes().isEmpty()
        }
        await("history") {
            try {
                onView(withTagValue(equalTo(id))).check(matches(isDisplayed()))
                true
            } catch (_: AssertionError) {
                false
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun open() {
        waitForHistory()
        onView(withTagValue(equalTo(id))).perform(click())
        compose.onNodeWithTag("dueSettingsButton").performClick()
        compose.onNodeWithTag("dueDateButton").assertExists()
    }

    private fun save() { compose.onNodeWithTag("dueSaveButton").performClick() }

    @Test fun dueDateReminderEditDisableAndRemoveSurviveRecreationBothDirections() {
        assertTrue("Grant notifications before running", DebtReminders.get(context).allowed())
        for (sign in listOf(1, -1)) {
            dao.insertHuman(Human(id, "Due screen", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(id, BigDecimal(100 * sign), id, null, "2026-11-16"))
            // A pending reminder can expire if Android did not run the app on the due day.
            dao.updateDebt(row().copy(dueDay = "2020-01-01", reminderMinute = 540, reminderToken = "expired-1"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.onHumanClick(id, "EUR", "Due screen") }
                waitForHistory()
                onView(withId(R.id.dueTextView)).check(matches(withText(containsString(context.getString(R.string.due_off)))))
                open()
                compose.onNodeWithTag("dueReminderSwitch").assertIsOff()
                compose.onNodeWithTag("dueTimeButton").assertDoesNotExist()
                compose.onNodeWithTag("dueDateButton").performClick()
                pressBack()
                compose.onNodeWithTag("dueDateButton").assertExists()
                compose.selectDueDay(scenario, 2099, 12, 31)
                compose.onNodeWithTag("dueReminderSwitch").performClick()
                scenario.recreate()
                compose.onNodeWithTag("dueReminderSwitch").assertIsOn()
                save()
                await("due save") { row().dueDay == "2099-12-31" && row().reminderToken != null }
                assertEquals("2099-12-31", row().dueDay)
                val token = row().reminderToken
                open(); compose.selectDueDay(scenario, 2099, 12, 30)
                compose.selectMorningTime(scenario, 10, 15)
                save(); await("reschedule") { row().reminderMinute == 615 }
                assertNotEquals(token, row().reminderToken)
                open(); compose.selectDueDay(scenario, 2020, 1, 1); save()
                compose.onNodeWithTag("dueError").assertExists()
                assertEquals("2099-12-30", row().dueDay)
                compose.onNodeWithTag("dueReminderSwitch").performClick()
                save(); await("disable") { row().reminderToken == null }
                assertEquals("2020-01-01", row().dueDay)
                waitForHistory()
                onView(withId(R.id.dueTextView)).check(matches(withText(containsString(context.getString(R.string.due_overdue, com.breckneck.deptbook.domain.util.LedgerDate.display("2020-01-01"))))))
                open()
                compose.onNodeWithTag("dueClearButton").performClick()
                compose.onNodeWithTag("dueReminderSwitch").assertDoesNotExist()
                compose.onNodeWithTag("dueTimeButton").assertDoesNotExist()
                save()
                await("clear due") { row().dueDay == null }
                assertNull(row().reminderToken)
            }
            clean()
        }
    }
}
