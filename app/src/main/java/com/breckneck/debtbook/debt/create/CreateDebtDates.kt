package com.breckneck.debtbook.debt.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleFieldRow
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.reminders.DebtDateSheet
import com.breckneck.debtbook.debt.reminders.DebtReminders
import com.breckneck.debtbook.debt.reminders.formatMinute
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate

@Composable
internal fun DebtEntryDate(vm: CreateDebtViewModel) {
    val date by vm.date.observeAsState()
    val due by vm.dueDay.observeAsState()
    val saving by vm.isSaving.observeAsState(false)
    var open by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.debt_entry_date)
    BubbleFieldRow(Icons.Outlined.DateRange, title, Modifier.testTag("createDebtDate")
        .clickable(enabled = !saving && !vm.hasRepayments) { open = true }) {
        Text(date?.let(LedgerDate::display).orEmpty())
    }
    if (open) {
        val today = LedgerDate.today()
        DebtDateSheet(title = title, initialDay = date, maxDay = due?.let { minOf(today, it) } ?: today,
            confirmText = stringResource(R.string.due_apply), onDismiss = { open = false }
        ) { day, _ -> day?.let(vm::setStoredDate) }
    }
}

@Composable
internal fun DebtDueFields(vm: CreateDebtViewModel) {
    val date by vm.date.observeAsState()
    val due by vm.dueDay.observeAsState()
    val minute by vm.minute.observeAsState(DebtReminderPolicy.DEFAULT_MINUTE)
    val reminder by vm.remind.observeAsState(false)
    val saving by vm.isSaving.observeAsState(false)
    var open by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.debt_deadline)
    val colors = bubbleColors()
    BubbleFieldRow(Icons.Outlined.DateRange, title, Modifier.testTag("createDebtDue").clickable(enabled = !saving) { open = true }) {
        Text(due?.let(LedgerDate::display) ?: stringResource(R.string.debt_no_due))
    }
    if (vm.canRemind) {
        HorizontalDivider(color = colors.border)
        val enabled = due != null && !saving
        val context = LocalContext.current
        BubbleFieldRow(Icons.Outlined.Notifications, stringResource(R.string.due_enable),
            Modifier.toggleable(reminder, enabled = enabled, role = Role.Switch) { checked ->
                if (!checked) vm.setDueDraft(due, null)
                else if (due?.let { DebtReminderPolicy.trigger(it, minute) > System.currentTimeMillis() } == true &&
                    DebtReminders.get(context).allowed()) vm.setDueDraft(due, minute)
                else open = true
            }, trailing = { Switch(reminder, onCheckedChange = null, enabled = enabled) }) {}
        if (reminder) {
            HorizontalDivider(color = colors.border)
            BubbleFieldRow(Icons.Outlined.Schedule, stringResource(R.string.debt_time), Modifier.clickable(enabled = !saving) { open = true }) {
                Text(formatMinute(minute))
            }
        }
    }
    if (open) DebtDateSheet(title = title, initialDay = due, minDay = date?.let(LedgerDate::canonical), clearable = true,
        initialMinute = minute, initialReminder = reminder.takeIf { vm.canRemind },
        confirmText = stringResource(R.string.due_apply), onDismiss = { open = false }, onSave = vm::setDueDraft)
}
