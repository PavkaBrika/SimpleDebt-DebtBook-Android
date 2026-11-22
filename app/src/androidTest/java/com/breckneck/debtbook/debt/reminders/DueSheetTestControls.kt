package com.breckneck.debtbook.debt.reminders

import android.text.format.DateFormat
import androidx.compose.material3.R as MaterialR
import androidx.compose.ui.test.*
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.debt.awaitTag
import com.breckneck.deptbook.domain.util.LedgerDate
import java.text.SimpleDateFormat
import java.util.GregorianCalendar

internal fun ActivityScenario<MainActivity>.screenString(id: Int): String {
    var value = ""
    onActivity { value = it.getString(id) }
    return value
}

internal fun ComposeTestRule.enterDebtDate(scenario: ActivityScenario<MainActivity>, date: String) {
    val inputMode = hasContentDescription(scenario.screenString(MaterialR.string.m3c_date_picker_switch_to_input_mode))
    val dateInput = hasSetTextAction() and hasAnyAncestor(hasTestTag("dueDatePicker"))
    waitUntil(5_000) { onAllNodes(dateInput or inputMode).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    if (onAllNodes(dateInput).fetchSemanticsNodes().isEmpty()) onNode(inputMode).performClick()
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
    awaitTag("dueDatePicker").assertExists()
}

internal fun testDay(offset: Int) = LedgerDate.fromDate(GregorianCalendar().apply { add(java.util.Calendar.DAY_OF_MONTH, offset) }.time)
