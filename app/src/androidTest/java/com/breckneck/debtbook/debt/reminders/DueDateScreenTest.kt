package com.breckneck.debtbook.debt.reminders

import android.content.Context
import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import database.AppDataBase
import entity.Debt
import entity.Human
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class DueDateScreenTest {
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
    private fun open() {
        onView(withTagValue(equalTo(id))).perform(click())
        onView(withId(R.id.dueSettingsButton)).inRoot(isDialog()).perform(scrollTo(), click())
        onView(withId(R.id.dueDateButton))
            .inRoot(allOf(isDialog(), withDecorView(hasDescendant(allOf(withId(R.id.dueDateButton), isEnabled())))))
            .check(matches(isDisplayed()))
    }
    private fun date(year: Int, month: Int, day: Int) {
        onView(withId(R.id.dueDateButton)).inRoot(isDialog()).perform(click())
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
            override fun getConstraints() = isAssignableFrom(DatePicker::class.java)
            override fun getDescription() = "Select calendar date"
            override fun perform(ui: UiController, view: View) { (view as DatePicker).updateDate(year, month - 1, day) }
        })
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }
    private fun save() { onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()) }

    @Test fun dueDateReminderEditDisableAndRemoveSurviveRecreationBothDirections() {
        assertTrue("Grant notifications before running", DebtReminders.get(context).allowed())
        for (sign in listOf(1, -1)) {
            dao.insertHuman(Human(id, "Due screen", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(id, BigDecimal(100 * sign), id, null, "2026-11-16"))
            // A pending reminder can expire if Android did not run the app on the due day.
            dao.updateDebt(row().copy(dueDay = "2020-01-01", reminderMinute = 540, reminderToken = "expired-1"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.onHumanClick(id, "EUR", "Due screen") }
                await("history") { try { onView(withTagValue(equalTo(id))).check(matches(isDisplayed())); true } catch (_: Exception) { false } }
                onView(withId(R.id.dueTextView)).check(matches(withText(containsString(context.getString(R.string.due_off)))))
                open()
                onView(withId(R.id.dueReminderSwitch)).inRoot(isDialog()).check(matches(isNotChecked()))
                date(2099, 12, 31)
                onView(withId(R.id.dueReminderSwitch)).inRoot(isDialog()).perform(click())
                scenario.recreate()
                onView(withId(R.id.dueReminderSwitch)).inRoot(isDialog()).check(matches(isChecked()))
                save()
                await("due save") { row().reminderToken != null }
                assertEquals("2099-12-31", row().dueDay)
                val token = row().reminderToken
                open(); date(2099, 12, 30)
                onView(withId(R.id.dueTimeButton)).inRoot(isDialog()).perform(click())
                onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
                    override fun getConstraints() = isAssignableFrom(TimePicker::class.java)
                    override fun getDescription() = "Select reminder time"
                    override fun perform(ui: UiController, view: View) { (view as TimePicker).apply { hour = 10; minute = 15 } }
                })
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                save(); await("reschedule") { row().reminderMinute == 615 }
                assertNotEquals(token, row().reminderToken)
                open(); date(2020, 1, 1); save()
                await("past reminder validation") {
                    try { onView(withId(R.id.dueError)).inRoot(isDialog()).check(matches(isDisplayed())); true }
                    catch (_: AssertionError) { false }
                    catch (_: Exception) { false }
                }
                assertEquals("2099-12-30", row().dueDay)
                onView(withId(R.id.dueReminderSwitch)).inRoot(isDialog()).perform(click())
                save(); await("disable") { row().reminderToken == null }
                assertEquals("2020-01-01", row().dueDay)
                onView(withId(R.id.dueTextView)).check(matches(withText(containsString(context.getString(R.string.due_overdue, com.breckneck.deptbook.domain.util.LedgerDate.display("2020-01-01"))))))
                open(); onView(withId(R.id.dueClearButton)).inRoot(isDialog()).perform(click()); save()
                await("clear due") { row().dueDay == null }
                assertNull(row().reminderToken)
            }
            clean()
        }
    }
}
