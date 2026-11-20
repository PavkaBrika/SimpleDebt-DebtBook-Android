package com.breckneck.debtbook.debt.reminders

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import com.breckneck.debtbook.core.MainActivity
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import androidx.compose.material3.R as MaterialR

internal fun ActivityScenario<MainActivity>.screenString(id: Int): String {
    var value = ""
    onActivity { value = it.getString(id) }
    return value
}

internal fun ComposeTestRule.selectDueDay(scenario: ActivityScenario<MainActivity>, year: Int, month: Int, day: Int) {
    onNodeWithTag("dueDateButton").performClick()
    onNodeWithContentDescription(scenario.screenString(MaterialR.string.m3c_date_picker_switch_to_input_mode)).performClick()
    var input = ""
    scenario.onActivity { activity ->
        val locale = activity.resources.configuration.locales[0]
        val pattern = DateFormat.getBestDateTimePattern(locale, "yyyyMMdd").filter { it in "yMd" }
        input = SimpleDateFormat(pattern, locale).format(GregorianCalendar(year, month - 1, day).time)
    }
    onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("dueDatePicker"))).performTextReplacement(input)
    onNodeWithTag("dueSaveButton").performClick()
}

internal fun ComposeTestRule.selectMorningTime(scenario: ActivityScenario<MainActivity>, hour: Int, minute: Int) {
    onNodeWithTag("dueTimeButton").performClick()
    var hourLabel = ""
    var minuteLabel = ""
    scenario.onActivity { activity ->
        hourLabel = activity.getString(
            if (DateFormat.is24HourFormat(activity)) MaterialR.string.m3c_time_picker_hour_24h_suffix
            else MaterialR.string.m3c_time_picker_hour_suffix, hour
        )
        minuteLabel = activity.getString(MaterialR.string.m3c_time_picker_minute_suffix, minute)
    }
    onNodeWithContentDescription(hourLabel).performSemanticsAction(SemanticsActions.OnClick)
    onNodeWithContentDescription(scenario.screenString(MaterialR.string.m3c_time_picker_minute_selection))
        .performSemanticsAction(SemanticsActions.OnClick)
    onNodeWithContentDescription(minuteLabel).performSemanticsAction(SemanticsActions.OnClick)
    onNodeWithTag("dueSaveButton").performClick()
}
