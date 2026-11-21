package com.breckneck.debtbook.debt.reminders

import android.text.format.DateFormat
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.GregorianCalendar
import androidx.compose.material3.R as MaterialR

internal fun ActivityScenario<MainActivity>.screenString(id: Int): String {
    var value = ""
    onActivity { value = it.getString(id) }
    return value
}

internal fun ComposeTestRule.enterDebtDate(scenario: ActivityScenario<MainActivity>, date: LocalDate) =
    enterDebtDate(scenario, GregorianCalendar(date.year, date.monthValue - 1, date.dayOfMonth).time)

internal fun ComposeTestRule.enterDebtDate(scenario: ActivityScenario<MainActivity>, date: Date) {
    val inputMode = onNodeWithContentDescription(scenario.screenString(MaterialR.string.m3c_date_picker_switch_to_input_mode))
    val dateInput = hasSetTextAction() and hasAnyAncestor(hasTestTag("dueDatePicker"))
    if (onAllNodes(dateInput).fetchSemanticsNodes().isEmpty()) inputMode.performClick()
    var input = ""
    scenario.onActivity { activity ->
        val locale = activity.resources.configuration.locales[0]
        val pattern = DateFormat.getBestDateTimePattern(locale, "yyyyMMdd").filter { it in "yMd" }
        input = SimpleDateFormat(pattern, locale).format(date)
    }
    onNode(dateInput).performTextReplacement(input)
}

internal fun ComposeTestRule.openDueCalendarInForm() {
    onView(withId(R.id.debtDueForm)).perform(scrollTo())
    onNodeWithTag("createDebtDue").performClick()
    onNodeWithTag("dueDatePicker").assertExists()
}
