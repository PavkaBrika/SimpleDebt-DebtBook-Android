package com.breckneck.debtbook.finance.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleFieldRow
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.FinanceDatePicker
import com.breckneck.debtbook.settings.SettingsChoiceRow
import com.breckneck.deptbook.domain.util.FinanceInterval
import com.breckneck.deptbook.domain.util.LedgerDate
import java.text.SimpleDateFormat
import java.util.Locale

private enum class DateField { ANCHOR, START, END }

@Composable
internal fun FinancePeriodSheet(period: FinancePeriod, onApply: (FinancePeriod) -> Unit, onDismiss: () -> Unit) {
    val colors = bubbleColors()
    var interval by rememberSaveable { mutableStateOf(period.interval) }
    var day by rememberSaveable { mutableStateOf(period.day) }
    var rangeStart by rememberSaveable { mutableStateOf(period.days().start) }
    var rangeEnd by rememberSaveable { mutableStateOf(period.days().endInclusive) }
    var dateField by rememberSaveable { mutableStateOf<DateField?>(null) }
    var choosingWeek by rememberSaveable { mutableStateOf(false) }
    var choosingInterval by remember { mutableStateOf(false) }
    val draft = if (interval == FinanceInterval.DATE_RANGE) {
        if (rangeStart <= rangeEnd) FinancePeriod(interval, rangeStart, rangeEnd) else null
    } else {
        FinancePeriod(interval, day)
    }

    BubbleBottomSheet(onDismiss) { active, close ->
        BubbleSheetHeader(stringResource(R.string.finance_period), close, enabled = active)
        BubbleGroup {
            Box {
                BubbleFieldRow(
                    Icons.Outlined.DateRange,
                    stringResource(R.string.select_interval),
                    Modifier.clickable(enabled = active, role = Role.Button) { choosingInterval = true },
                    trailing = { Icon(Icons.Outlined.ExpandMore, null) },
                ) { Text(stringResource(interval.labelResource)) }
                DropdownMenu(choosingInterval, { choosingInterval = false }) {
                    FinanceInterval.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelResource)) },
                            onClick = {
                                interval = option
                                choosingInterval = false
                            },
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.border)
            when (interval) {
                FinanceInterval.DATE_RANGE -> {
                    PeriodField(R.string.finance_range_start, LedgerDate.display(rangeStart), active) {
                        dateField = DateField.START
                    }
                    HorizontalDivider(color = colors.border)
                    PeriodField(R.string.finance_range_end, LedgerDate.display(rangeEnd), active) {
                        dateField = DateField.END
                    }
                }
                FinanceInterval.WEEK ->
                    PeriodField(R.string.finance_choose_week, requireNotNull(draft).label(), active) { choosingWeek = true }
                else -> {
                    val label = when (interval) {
                        FinanceInterval.MONTH -> R.string.finance_period_month_day
                        FinanceInterval.YEAR -> R.string.finance_period_year_day
                        else -> R.string.finance_period_day
                    }
                    PeriodField(label, LedgerDate.display(day), active) { dateField = DateField.ANCHOR }
                }
            }
        }
        if (draft == null) {
            Text(stringResource(R.string.finance_range_invalid), color = colors.danger, style = MaterialTheme.typography.bodyMedium)
        } else if (interval == FinanceInterval.MONTH || interval == FinanceInterval.YEAR) {
            Text(
                stringResource(R.string.finance_period_preview, draft.label()),
                color = colors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        BubbleButton({ draft?.let(onApply) }, Modifier.fillMaxWidth(), enabled = active && draft != null) {
            Text(stringResource(R.string.finance_apply_period))
        }
    }
    dateField?.let { field ->
        val selectedDay = when (field) {
            DateField.ANCHOR -> day
            DateField.START -> rangeStart
            DateField.END -> rangeEnd
        }
        FinanceDatePicker(selectedDay, onSelect = { selected ->
            when (field) {
                DateField.ANCHOR -> day = selected
                DateField.START -> rangeStart = selected
                DateField.END -> rangeEnd = selected
            }
            dateField = null
        }, onDismiss = { dateField = null })
    }
    if (choosingWeek) {
        WeekPicker(day, onSelect = { weekStart ->
            val currentWeek = FinancePeriod(FinanceInterval.WEEK, day).days()
            if (weekStart != currentWeek.start) day = weekStart
            choosingWeek = false
        }, onDismiss = { choosingWeek = false })
    }
}

@Composable
private fun PeriodField(label: Int, value: String, enabled: Boolean, onClick: () -> Unit) {
    BubbleFieldRow(
        Icons.Outlined.DateRange,
        stringResource(label),
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) { Text(value) }
}

@Composable
private fun WeekPicker(day: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var monthDay by rememberSaveable { mutableStateOf(FinancePeriod(FinanceInterval.MONTH, day).days().start) }
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    val month = FinancePeriod(FinanceInterval.MONTH, monthDay)
    val weeks = remember(monthDay) {
        val monthEnd = month.days().endInclusive
        generateSequence(FinancePeriod(FinanceInterval.WEEK, monthDay)) { it.shifted(1) }
            .map { it.days() }
            .takeWhile { it.start <= monthEnd }
            .toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finance_choose_week)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ monthDay = month.shifted(-1).day }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.finance_previous_month))
                    }
                    TextButton({ choosingMonth = true }, Modifier.weight(1f)) {
                        Text(month.label(), textAlign = TextAlign.Center)
                    }
                    IconButton({ monthDay = month.shifted(1).day }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.finance_next_month))
                    }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup()) {
                    weeks.forEach { week ->
                        SettingsChoiceRow(week.dateLabel(), selected = day in week) { onSelect(week.start) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (choosingMonth) {
        FinanceDatePicker(monthDay, onSelect = {
            monthDay = FinancePeriod(FinanceInterval.MONTH, it).days().start
            choosingMonth = false
        }, onDismiss = { choosingMonth = false })
    }
}

internal val FinanceInterval.labelResource: Int
    get() = when (this) {
        FinanceInterval.DAY -> R.string.day
        FinanceInterval.WEEK -> R.string.week
        FinanceInterval.MONTH -> R.string.month
        FinanceInterval.YEAR -> R.string.year_full
        FinanceInterval.DATE_RANGE -> R.string.finance_date_range
    }

internal fun FinancePeriod.label(): String = when (interval) {
    FinanceInterval.DAY -> LedgerDate.display(day)
    FinanceInterval.WEEK, FinanceInterval.DATE_RANGE -> days().dateLabel()
    FinanceInterval.MONTH, FinanceInterval.YEAR ->
        SimpleDateFormat(if (interval == FinanceInterval.MONTH) "MMMM yyyy" else "yyyy", Locale.getDefault())
            .format(requireNotNull(LedgerDate.localDate(day)))
}

private fun ClosedRange<String>.dateLabel(): String =
    if (start == endInclusive) LedgerDate.display(start)
    else "${LedgerDate.display(start)} – ${LedgerDate.display(endInclusive)}"
