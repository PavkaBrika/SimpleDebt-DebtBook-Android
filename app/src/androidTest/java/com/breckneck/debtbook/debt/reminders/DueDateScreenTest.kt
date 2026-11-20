package com.breckneck.debtbook.debt.reminders

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
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
import java.time.LocalDate

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
        compose.onNodeWithTag("dueDatePicker").assertExists()
    }

    private fun save() { compose.onNodeWithTag("dueSaveButton").performClick() }

    @Test fun editAndDisableReminderWithoutAnExtraDateStep() {
        assertTrue("Grant notifications before running", DebtReminders.get(context).allowed())
        for (sign in listOf(1, -1)) {
            dao.insertHuman(Human(id, "Due screen", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(id, BigDecimal(100 * sign), id, null, "2026-11-16"))
            dao.setDebtDueDate(id, "2099-12-31", 600, "legacy-token", System.currentTimeMillis())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.onHumanClick(id, "EUR", "Due screen") }
                waitForHistory()
                onView(withId(R.id.dueTextView)).check(matches(withText(containsString(scenario.screenString(R.string.due_reminder_on)))))

                onView(withTagValue(equalTo(id))).perform(click())
                compose.onNodeWithTag("editButton").assertExists()
                compose.onNodeWithTag("debtActionsCancel").performClick()
                compose.onNodeWithTag("dueSettingsButton").assertDoesNotExist()

                open()
                compose.onNodeWithTag("dueReminderSwitch").assertIsOn()
                compose.onNodeWithTag("dueTimeButton").assertDoesNotExist()
                compose.onNodeWithTag("dueClearButton").assertDoesNotExist()
                compose.enterDebtDate(scenario, LocalDate.of(2026, 11, 15))
                compose.onNodeWithTag("dueSaveButton").assertIsNotEnabled()
                compose.onNodeWithTag("dueCancelButton").performClick()
                assertEquals("2099-12-31", row().dueDay)
                assertEquals(600, row().reminderMinute)
                assertEquals("legacy-token", row().reminderToken)

                open()
                pressBack()
                compose.onNodeWithTag("debtDueSheet").assertDoesNotExist()
                open()
                save()
                waitForHistory()
                assertEquals(600, row().reminderMinute)
                assertEquals("legacy-token", row().reminderToken)

                open()
                compose.enterDebtDate(scenario, LocalDate.of(2099, 12, 30))
                scenario.recreate()
                compose.onNodeWithTag("dueReminderSwitch").assertIsOn()
                save()
                await("reschedule") { row().dueDay == "2099-12-30" }
                assertNotEquals("legacy-token", row().reminderToken)
                assertEquals(540, row().reminderMinute)

                open()
                compose.onNodeWithTag("dueReminderSwitch").performClick()
                compose.onNodeWithTag("dueReminderSwitch").assertIsOff()
                save()
                await("disable") { row().reminderToken == null }
                assertEquals("2099-12-30", row().dueDay)
            }
            clean()
        }
    }
}
