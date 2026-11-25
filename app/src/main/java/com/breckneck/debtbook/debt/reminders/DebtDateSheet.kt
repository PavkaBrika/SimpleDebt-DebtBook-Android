package com.breckneck.debtbook.debt.reminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.DebtBookBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleFieldRow
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.components.bubbleSwitchColors
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val calendarGridWidth = 360.dp

@Composable
private fun debtCalendarWindowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

@Composable
internal fun isDebtCalendarCompact(): Boolean = debtCalendarWindowWidth() < calendarGridWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtDateSheet(
    title: String,
    initialDay: String?,
    minDay: String? = null,
    maxDay: String? = null,
    initialReminder: Boolean? = null,
    initialMinute: Int = DebtReminderPolicy.DEFAULT_MINUTE,
    clearable: Boolean = false,
    confirmText: String = stringResource(R.string.save),
    onDismiss: () -> Unit,
    onSave: suspend (day: String?, minute: Int?) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reminders = remember(context) { DebtReminders.get(context) }
    val scope = rememberCoroutineScope()
    val colors = bubbleColors()
    val screenWidth = debtCalendarWindowWidth()
    val compact = screenWidth < calendarGridWidth
    val calendarInset = if (!compact && screenWidth < calendarGridWidth + 32.dp) 0.dp else 16.dp
    val calendar = rememberDebtCalendar(initialDay, minDay, maxDay, compact)
    val day = calendar.selectedDateMillis?.takeIf(calendar.selectableDates::isSelectableDate)?.let(LedgerDate::dayFromPickerMillis)
    val showReminder = initialReminder != null && day != null && DebtReminderPolicy.canSchedule(day)
    var allowed by remember { mutableStateOf(reminders.allowed()) }
    var enabled by rememberSaveable { mutableStateOf(initialReminder == true && allowed) }
    var minute by rememberSaveable { mutableIntStateOf(initialMinute) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var clear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(day) { if (day != null) clear = false }
    val unchangedTime = day == initialDay && initialReminder == true && minute == initialMinute
    val pastTime = day != null && showReminder && enabled && !unchangedTime &&
        DebtReminderPolicy.trigger(day, minute) <= System.currentTimeMillis()
    var error by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var closing by rememberSaveable { mutableStateOf(false) }
    val interactive = !busy && !closing

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = reminders.allowed()
        enabled = granted && allowed
    }
    DisposableEffect(lifecycle, reminders) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = reminders.allowed()
                if (!allowed) enabled = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (pickingTime) {
        DebtReminderTimeDialog(minute, { pickingTime = false }) {
            minute = it
            pickingTime = false
        }
    }
    DebtBookBottomSheet(
        onDismiss = { if (!busy) onDismiss() },
        modifier = Modifier.testTag("debtDueSheet"),
        containerColor = colors.background,
        contentColor = colors.ink,
        hideRequested = closing,
        gesturesEnabled = interactive,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = interactive,
            shouldDismissOnClickOutside = interactive,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BubbleSheetHeader(title, { closing = true }, enabled = interactive,
                modifier = Modifier.padding(horizontal = 16.dp),
                closeModifier = Modifier.testTag("dueCancelButton"))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BubbleGroup(Modifier.padding(horizontal = calendarInset)) {
                    DatePicker(
                        state = calendar,
                        title = null,
                        modifier = Modifier.fillMaxWidth().testTag("dueDatePicker"),
                        showModeToggle = !compact,
                        colors = debtCalendarColors(colors.paper),
                    )
                    if (clearable && initialDay != null) {
                        TextButton(
                            { calendar.selectedDateMillis = null; clear = true; enabled = false },
                            modifier = Modifier.padding(horizontal = 12.dp).testTag("clearDueDate"),
                            enabled = interactive,
                        ) { Text(stringResource(R.string.debt_clear_date), color = colors.danger) }
                    }
                }
                AnimatedVisibility(visible = showReminder) {
                    BubbleGroup(Modifier.padding(horizontal = 16.dp)) {
                        BubbleFieldRow(
                            Icons.Outlined.Notifications, stringResource(R.string.due_enable),
                            Modifier.testTag("dueReminderSwitch")
                                .toggleable(enabled, enabled = interactive && showReminder, role = Role.Switch) { checked ->
                                    allowed = reminders.allowed()
                                    enabled = checked && allowed
                                    if (checked && !allowed && Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            trailing = { Switch(enabled, onCheckedChange = null, enabled = interactive, colors = bubbleSwitchColors()) },
                        ) {}
                        if (enabled) {
                            HorizontalDivider(color = colors.border)
                            BubbleFieldRow(
                                Icons.Outlined.Schedule, stringResource(R.string.debt_time),
                                Modifier.testTag("dueTimeButton").clickable(enabled = interactive) { pickingTime = true },
                            ) { Text(formatMinute(minute)) }
                        }
                        if (!allowed) {
                            Text(stringResource(R.string.due_permission_denied),
                                Modifier.padding(horizontal = 14.dp).testTag("duePermissionStatus"),
                                color = colors.muted, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { openNotificationSettings(context) }, enabled = interactive) {
                                Text(stringResource(R.string.due_notification_settings), color = colors.action)
                            }
                        }
                    }
                }
            }
            if (pastTime) Text(stringResource(R.string.debt_future_time), Modifier.padding(horizontal = 16.dp), color = colors.danger)
            if (error) Text(stringResource(R.string.due_save_failed), Modifier.padding(horizontal = 16.dp), color = colors.danger)
            BubbleButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("dueSaveButton"),
                enabled = (day != null || clear) && interactive && !pastTime,
                onClick = {
                    val selectedDay = day
                    busy = true
                    error = false
                    scope.launch(Dispatchers.Main.immediate) {
                        try {
                            val selectedMinute = minute.takeIf { selectedDay != null && showReminder && enabled &&
                                reminders.allowed() && DebtReminderPolicy.canSchedule(selectedDay) }
                            onSave(selectedDay, selectedMinute)
                            closing = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            Log.e("DebtDateSheet", "Could not save debt date", failure)
                            error = true
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(confirmText) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun debtCalendarColors(container: Color = bubbleColors().paper): androidx.compose.material3.DatePickerColors {
    val colors = bubbleColors()
    return DatePickerDefaults.colors(
        containerColor = container,
        titleContentColor = colors.muted,
        headlineContentColor = colors.ink,
        weekdayContentColor = colors.muted,
        subheadContentColor = colors.ink,
        navigationContentColor = colors.action,
        dayContentColor = colors.ink,
        todayContentColor = colors.action,
        todayDateBorderColor = colors.action,
        selectedDayContainerColor = colors.action,
        selectedDayContentColor = colors.onAction,
        yearContentColor = colors.ink,
        currentYearContentColor = colors.action,
        selectedYearContainerColor = colors.action,
        selectedYearContentColor = colors.onAction,
        dividerColor = colors.border,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberDebtCalendar(initialDay: String?, minDay: String? = null, maxDay: String? = null,
    compact: Boolean = isDebtCalendarCompact()): androidx.compose.material3.DatePickerState {
    val minMillis = minDay?.let(LedgerDate::pickerMillis)
    val maxMillis = maxDay?.let(LedgerDate::pickerMillis)
    val selectableDates = remember(minMillis, maxMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                (minMillis == null || utcTimeMillis >= minMillis) && (maxMillis == null || utcTimeMillis <= maxMillis)
        }
    }
    val initialMillis = initialDay?.let(LedgerDate::canonical)?.let(LedgerDate::pickerMillis)
        ?.takeIf(selectableDates::isSelectableDate)
    val todayMillis = LedgerDate.pickerMillis(LedgerDate.today())
    val displayedMonth = initialMillis ?: todayMillis.coerceIn(minMillis ?: Long.MIN_VALUE, maxMillis ?: Long.MAX_VALUE)
    val displayYear = LedgerDate.dayFromPickerMillis(displayedMonth).take(4).toInt()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        initialDisplayedMonthMillis = displayedMonth,
        initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker,
        yearRange = minOf(displayYear, DatePickerDefaults.YearRange.first)..maxOf(displayYear, DatePickerDefaults.YearRange.last),
        selectableDates = selectableDates
    )
    LaunchedEffect(state, compact) {
        if (compact && state.displayMode != DisplayMode.Input) state.displayMode = DisplayMode.Input
    }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtReminderTimeDialog(minute: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val colors = bubbleColors()
    val time = rememberTimePickerState(minute / 60, minute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        titleContentColor = colors.ink,
        title = { Text(stringResource(R.string.debt_time)) },
        text = {
            TimeInput(time, Modifier.testTag("timeInput"), colors = TimePickerDefaults.colors(
                containerColor = colors.paper,
                timeSelectorSelectedContainerColor = colors.mint,
                timeSelectorSelectedContentColor = colors.onMint,
                timeSelectorUnselectedContainerColor = colors.fill,
                timeSelectorUnselectedContentColor = colors.ink,
            ))
        },
        confirmButton = {
            TextButton({ onSelect(time.hour * 60 + time.minute) }, Modifier.testTag("timeConfirm")) {
                Text(stringResource(R.string.due_apply), color = colors.action)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel), color = colors.action) } },
    )
}

internal fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(intent)
}

internal fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
