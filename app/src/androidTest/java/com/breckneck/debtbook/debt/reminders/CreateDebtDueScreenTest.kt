package com.breckneck.debtbook.debt.reminders

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import database.AppDataBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class CreateDebtDueScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun setReminderOnFirstDebtAndDueDateOnNextDebt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDataBase(context).appDao()
        val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
        val pin = prefs.getBoolean("pin_code_is_enabled", false)
        val ads = prefs.getBoolean("ads_disabled", false)
        val name = "Due-${System.nanoTime().toString(36)}"
        fun newPerson() = dao.getAllHuman().firstOrNull { it.name == name }
        assertTrue("Grant notification permission before running", DebtReminders.get(context).allowed())
        try {
            prefs.edit().putBoolean("pin_code_is_enabled", false).putBoolean("ads_disabled", true).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.addHumanButton)).perform(click())
                onView(withId(R.id.humanNameEditText)).perform(replaceText(name), closeSoftKeyboard())
                onView(withId(R.id.debtSumEditText)).perform(replaceText("125"), closeSoftKeyboard())
                onView(withId(R.id.debtDueForm)).perform(scrollTo())
                val today = LocalDate.now()
                compose.onNodeWithTag("createDebtDate").performClick()
                compose.enterDebtDate(scenario, today.plusDays(1))
                compose.onNodeWithTag("dueSaveButton").assertIsNotEnabled()
                compose.enterDebtDate(scenario, today)
                compose.onNodeWithTag("dueSaveButton").performClick()

                compose.onNodeWithTag("createDebtDue").performClick()
                compose.onNodeWithTag("dueDatePicker").assertExists()
                compose.onNodeWithTag("dueReminderSwitch").assertDoesNotExist()
                compose.enterDebtDate(scenario, today.minusDays(1))
                compose.onNodeWithTag("dueSaveButton").assertIsNotEnabled()
                compose.enterDebtDate(scenario, LocalDate.of(2099, 11, 29))
                compose.onNodeWithTag("dueReminderSwitch").performScrollTo().performClick()
                scenario.recreate()
                compose.onNodeWithTag("dueReminderSwitch").assertIsOn()
                compose.onNodeWithTag("dueSaveButton").performClick()
                compose.onNodeWithTag("debtDueSheet").assertDoesNotExist()
                assertEquals(null, newPerson())
                onView(withId(R.id.setFinanceButton)).perform(click())
                compose.waitUntil(15_000) { newPerson() != null }
                val person = checkNotNull(newPerson())
                val first = dao.getAllDebtsById(person.id).single()
                assertEquals(today.toString(), first.date)
                assertEquals("2099-11-29", first.dueDay)
                assertEquals(540, first.reminderMinute)
                assertNotNull(first.reminderToken)

                onView(withId(R.id.addDebtButton)).perform(click())
                onView(withId(R.id.debtSumEditText)).perform(replaceText("25"), closeSoftKeyboard())
                onView(withId(R.id.debtDueForm)).perform(scrollTo())
                compose.onNodeWithTag("createDebtDue").performClick()
                compose.enterDebtDate(scenario, LocalDate.of(2099, 12, 1))
                compose.onNodeWithTag("dueSaveButton").performClick()
                compose.onNodeWithTag("debtDueSheet").assertDoesNotExist()
                onView(withId(R.id.setFinanceButton)).perform(click())
                compose.waitUntil(15_000) { dao.getAllDebtsById(person.id).size == 2 }
                val second = dao.getAllDebtsById(person.id).single { it.id != first.id }
                assertEquals("2099-12-01", second.dueDay)
                assertEquals(null, second.reminderMinute)
            }
        } finally {
            newPerson()?.let {
                dao.deleteDebtsByHumanId(it.id)
                dao.deleteHumanById(it.id)
            }
            prefs.edit().putBoolean("pin_code_is_enabled", pin).putBoolean("ads_disabled", ads).commit()
        }
    }
}
