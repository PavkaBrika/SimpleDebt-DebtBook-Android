package com.breckneck.debtbook.debt.create

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleFieldRow
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.reminders.DebtDateSheet
import com.breckneck.debtbook.debt.reminders.DebtReminderTimeDialog
import com.breckneck.debtbook.debt.reminders.DebtReminders
import com.breckneck.debtbook.debt.reminders.openNotificationSettings
import com.breckneck.debtbook.debt.reminders.rememberDebtCalendar
import com.breckneck.debtbook.debt.reminders.formatMinute
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate

@Composable
internal fun DebtEntryDate(vm: CreateDebtViewModel) {
    val date by vm.date.observeAsState()
    val due by vm.dueDay.observeAsState()
    val saving by vm.isSaving.observeAsState(false)
    var open by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val title = stringResource(R.string.debt_entry_date)
    BubbleFieldRow(Icons.Outlined.DateRange, title, Modifier.testTag("createDebtDate")
        .clickable(enabled = !saving && !vm.hasRepayments) { focus.clearFocus(); open = true }) {
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
internal fun DebtDueFields(vm: CreateDebtViewModel, revealTimeRow: suspend () -> Unit) {
    val date by vm.date.observeAsState()
    val due by vm.dueDay.observeAsState()
    val minute by vm.minute.observeAsState(DebtReminderPolicy.DEFAULT_MINUTE)
    val reminder by vm.remind.observeAsState(false)
    val saving by vm.isSaving.observeAsState(false)
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    var timeOpen by rememberSaveable { mutableStateOf(false) }
    var revealTime by rememberSaveable { mutableStateOf(false) }
    var requestedPermission by rememberSaveable { mutableStateOf(false) }
    val colors = bubbleColors()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reminders = remember(context) { DebtReminders.get(context) }
    var allowed by remember { mutableStateOf(reminders.allowed()) }
    val schedulable = due?.let(DebtReminderPolicy::canSchedule) == true

    fun enableReminder() {
        if (due != null && schedulable) {
            vm.setDueDraft(due, minute)
            revealTime = true
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = reminders.allowed()
        if (allowed) enableReminder()
    }
    DisposableEffect(lifecycle, reminders) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = reminders.allowed()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BubbleFieldRow(
        Icons.Outlined.DateRange,
        stringResource(R.string.debt_deadline),
        Modifier.testTag("createDebtDue").clickable(enabled = !saving, role = Role.Button) { focus.clearFocus(); calendarOpen = true },
    ) { Text(due?.let(LedgerDate::display) ?: stringResource(R.string.debt_no_due)) }

    if (vm.canRemind) {
        HorizontalDivider(color = colors.border)
        val enabled = !saving && (schedulable || reminder)
        BubbleFieldRow(
            Icons.Outlined.Notifications,
            stringResource(R.string.due_enable),
            Modifier.testTag("createDebtReminder").toggleable(reminder, enabled = enabled, role = Role.Switch) { checked ->
                if (!checked) {
                    revealTime = false
                    vm.setDueDraft(due, null)
                } else {
                    allowed = reminders.allowed()
                    if (allowed) {
                        enableReminder()
                    } else {
                        requestedPermission = true
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            },
            trailing = { Switch(reminder, onCheckedChange = null, enabled = enabled) },
        ) {}
        val timeVisibility = remember { MutableTransitionState(reminder) }
        timeVisibility.targetState = reminder
        LaunchedEffect(revealTime, timeVisibility.isIdle, timeVisibility.currentState) {
            if (revealTime && timeVisibility.isIdle && timeVisibility.currentState) {
                withFrameNanos {}
                revealTimeRow()
                revealTime = false
            }
        }
        AnimatedVisibility(visibleState = timeVisibility) {
            Column {
                HorizontalDivider(color = colors.border)
                BubbleFieldRow(
                    Icons.Outlined.Schedule,
                    stringResource(R.string.debt_time),
                    Modifier.testTag("createDebtReminderTime").clickable(enabled = !saving, role = Role.Button) {
                        focus.clearFocus()
                        timeOpen = true
                    },
                ) { Text(formatMinute(minute)) }
                if (vm.hasPastReminder()) Text(
                    stringResource(R.string.debt_future_time),
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp).testTag("debtReminderTimeError")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = colors.danger,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (requestedPermission && !allowed) {
            Text(
                stringResource(R.string.due_permission_denied),
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp).testTag("duePermissionStatus"),
                color = colors.muted,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { openNotificationSettings(context) }, enabled = !saving) {
                Text(stringResource(R.string.due_notification_settings))
            }
        }
    }

    if (calendarOpen) {
        CreateDebtDueCalendar(due, date?.let(LedgerDate::canonical), { calendarOpen = false }) { day ->
            val retainedMinute = minute.takeIf { reminder && day?.let(DebtReminderPolicy::canSchedule) == true }
            vm.setDueDraft(day, retainedMinute)
            calendarOpen = false
        }
    }
    if (timeOpen) {
        DebtReminderTimeDialog(minute, { timeOpen = false }) {
            vm.setDueDraft(due, it)
            timeOpen = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateDebtDueCalendar(initialDay: String?, minDay: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    val calendar = rememberDebtCalendar(initialDay, minDay = minDay)
    val day = calendar.selectedDateMillis?.takeIf(calendar.selectableDates::isSelectableDate)?.let(LedgerDate::dayFromPickerMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({ onSelect(day) }, Modifier.testTag("dueSaveButton"), enabled = day != null) {
                Text(stringResource(R.string.due_apply))
            }
        },
        dismissButton = {
            TextButton(onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DatePicker(
                state = calendar,
                modifier = Modifier.testTag("dueDatePicker"),
                title = {
                    Text(
                        stringResource(R.string.debt_deadline),
                        Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
            if (initialDay != null) {
                TextButton({ onSelect(null) }, Modifier.padding(start = 12.dp).testTag("clearDueDate")) {
                    Text(stringResource(R.string.debt_clear_date))
                }
            }
        }
    }
}
