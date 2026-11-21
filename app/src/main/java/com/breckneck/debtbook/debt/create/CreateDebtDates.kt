package com.breckneck.debtbook.debt.create

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleFieldRow
import com.breckneck.debtbook.debt.reminders.DebtDateSheet
import com.breckneck.deptbook.domain.util.LedgerDate

private enum class DateField { DEBT, DUE }

@Composable
internal fun CreateDebtDates(vm: CreateDebtViewModel) {
    val date by vm.date.observeAsState()
    val draftDueDay by vm.dueDay.observeAsState()
    val reminder by vm.remind.observeAsState(false)
    val saving by vm.isSaving.observeAsState(false)
    var openField by rememberSaveable { mutableStateOf<DateField?>(null) }
    val dateLabel = stringResource(R.string.debt_entry_date)
    val dueLabel = stringResource(R.string.debt_deadline)
    BubbleFieldRow(Icons.Outlined.DateRange, dateLabel, Modifier.testTag("createDebtDate")
        .clickable(enabled = !saving && !vm.hasRepayments) { openField = DateField.DEBT }) {
        Text(date?.let(LedgerDate::display).orEmpty())
    }
    BubbleFieldRow(Icons.Outlined.DateRange, dueLabel, Modifier.testTag("createDebtDue")
        .clickable(enabled = !saving) { openField = DateField.DUE }) {
        Text(draftDueDay?.let(LedgerDate::display) ?: stringResource(R.string.debt_no_due))
    }
    when (openField) {
        DateField.DEBT -> {
            val today = LedgerDate.today()
            DebtDateSheet(
                title = dateLabel,
                initialDay = date,
                maxDay = draftDueDay?.let { minOf(today, it) } ?: today,
                confirmText = stringResource(R.string.due_apply),
                onDismiss = { openField = null }
            ) { day, _ -> vm.setStoredDate(day) }
        }
        DateField.DUE -> DebtDateSheet(
            title = dueLabel,
            initialDay = draftDueDay,
            minDay = date?.let(LedgerDate::canonical),
            initialReminder = reminder.takeIf { vm.canRemind },
            confirmText = stringResource(R.string.due_apply),
            onDismiss = { openField = null },
            onSave = vm::setDueDraft
        )
        null -> Unit
    }
}
