package com.breckneck.debtbook.debt.reminders

import com.breckneck.debtbook.debt.awaitTag
import androidx.compose.ui.test.*
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
import com.breckneck.debtbook.core.MainActivity
import java.text.SimpleDateFormat
import com.breckneck.deptbook.domain.util.LedgerDate
import java.util.GregorianCalendar
import androidx.compose.material3.R as MaterialR

internal fun ActivityScenario<MainActivity>.screenString(id: Int): String {
    var value = ""
    onActivity { value = it.getString(id) }
    return value
}

internal fun ComposeTestRule.enterDebtDate(scenario: ActivityScenario<MainActivity>, date: String) {
    val inputMode = onNodeWithContentDescription(scenario.screenString(MaterialR.string.m3c_date_picker_switch_to_input_mode))
    val dateInput = hasSetTextAction() and hasAnyAncestor(hasTestTag("dueDatePicker"))
    if (onAllNodes(dateInput).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()) inputMode.performClick()
    var input = ""
    scenario.onActivity { activity ->
        val locale = androidx.core.os.ConfigurationCompat.getLocales(activity.resources.configuration)[0]!!
        val pattern = DateFormat.getBestDateTimePattern(locale, "yyyyMMdd").filter { it in "yMd" }
        input = SimpleDateFormat(pattern, locale)
            .format(requireNotNull(LedgerDate.localDate(date)))
    }
    onNode(dateInput).performTextReplacement(input)
}

internal fun ComposeTestRule.openDueCalendarInForm() {
    awaitTag("createDebtDue").performScrollTo().performClick()
    onNodeWithTag("dueDatePicker").assertExists()
}

internal fun testDay(offset: Int) = LedgerDate.fromDate(GregorianCalendar().apply { add(java.util.Calendar.DAY_OF_MONTH, offset) }.time)
