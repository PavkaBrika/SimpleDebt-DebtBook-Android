package com.breckneck.debtbook.debt.reminders

import com.breckneck.debtbook.debt.awaitTag
import androidx.compose.ui.test.*
import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.breckneck.debtbook.core.MainActivity
import database.AppDataBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.breckneck.deptbook.domain.util.LedgerDate

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
                compose.awaitTag("addHumanButton").performClick()
                compose.awaitTag("humanNameEditText").performTextReplacement(name)
                compose.awaitTag("debtSumEditText").performTextReplacement("125")
                val today = LedgerDate.today()
                compose.onNodeWithTag("createDebtDate").performScrollTo().performClick()
                compose.enterDebtDate(scenario, testDay(1))
                compose.onNodeWithTag("dueSaveButton").assertIsNotEnabled()
                compose.enterDebtDate(scenario, today)
                compose.onNodeWithTag("dueSaveButton").performClick()

                compose.awaitTag("createDebtDue").performScrollTo().performClick()
                compose.onNodeWithTag("dueDatePicker").assertExists()
                compose.onNodeWithTag("dueReminderSwitch").assertDoesNotExist()
                compose.enterDebtDate(scenario, testDay(-1))
                compose.onNodeWithTag("dueSaveButton").assertIsNotEnabled()
                compose.enterDebtDate(scenario, "2099-11-29")
                compose.onNodeWithTag("dueSaveButton").performClick()
                compose.onNodeWithTag("dueDatePicker").assertDoesNotExist()
                compose.awaitTag("createDebtReminder").performScrollTo().performClick()
                val time = compose.awaitTag("createDebtReminderTime").assertIsDisplayed()
                assertTrue(time.fetchSemanticsNode().boundsInWindow.bottom <=
                    compose.awaitTag("setFinanceButton").fetchSemanticsNode().boundsInWindow.top)
                time.performClick()
                compose.awaitTag("timeInput").assertExists()
                compose.onNodeWithTag("timeConfirm").performClick()
                scenario.recreate()
                compose.onNodeWithTag("createDebtReminder").assertIsOn()
                assertEquals(null, newPerson())
                compose.awaitTag("setFinanceButton").performClick()
                compose.waitUntil(15_000) { newPerson() != null }
                val person = checkNotNull(newPerson())
                val first = dao.getAllDebtsById(person.id).single()
                assertEquals(today.toString(), first.date)
                assertEquals("2099-11-29", first.dueDay)
                assertEquals(540, first.reminderMinute)
                assertNotNull(first.reminderToken)

                compose.awaitTag("addDebtButton").performClick()
                compose.awaitTag("debtSumEditText").performTextReplacement("25")
                compose.awaitTag("createDebtDue").performScrollTo().performClick()
                compose.enterDebtDate(scenario, "2099-12-01")
                compose.onNodeWithTag("dueSaveButton").performClick()
                compose.onNodeWithTag("debtDueSheet").assertDoesNotExist()
                compose.awaitTag("setFinanceButton").performClick()
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
