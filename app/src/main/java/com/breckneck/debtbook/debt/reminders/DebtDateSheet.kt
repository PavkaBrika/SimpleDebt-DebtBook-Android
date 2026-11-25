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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.breckneck.debtbook.core.ui.components.BubbleOutlinedButton
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
    var revealTime by remember { mutableStateOf(false) }
    val timeRequester = remember { BringIntoViewRequester() }
    val timeVisibility = remember { MutableTransitionState(enabled) }
    timeVisibility.targetState = enabled
    LaunchedEffect(revealTime, timeVisibility.isIdle, timeVisibility.currentState) {
        if (revealTime && timeVisibility.isIdle && timeVisibility.currentState) {
            withFrameNanos {}
            timeRequester.bringIntoView()
            revealTime = false
        }
    }
    val unchangedTime = day == initialDay && initialReminder == true && minute == initialMinute
    val pastTime = day != null && showReminder && enabled && !unchangedTime &&
        DebtReminderPolicy.trigger(day, minute) <= System.currentTimeMillis()
    var error by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var closing by rememberSaveable { mutableStateOf(false) }
    val interactive = !busy && !closing

    fun saveDue(selectedDay: String?, selectedMinute: Int?) {
        if (busy || closing) return
        busy = true
        error = false
        scope.launch(Dispatchers.Main.immediate) {
            try {
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
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = reminders.allowed()
        enabled = granted && allowed
        revealTime = enabled
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
                    DebtCalendarPicker(
                        state = calendar,
                        compact = compact,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AnimatedVisibility(visible = showReminder) {
                    BubbleGroup(Modifier.padding(horizontal = 16.dp).testTag("dueReminderFields")) {
                        BubbleFieldRow(
                            Icons.Outlined.Notifications, stringResource(R.string.due_enable),
                            Modifier.testTag("dueReminderSwitch")
                                .toggleable(enabled, enabled = interactive && showReminder, role = Role.Switch) { checked ->
                                    allowed = reminders.allowed()
                                    enabled = checked && allowed
                                    revealTime = enabled
                                    if (checked && !allowed && Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            trailing = { Switch(enabled, onCheckedChange = null, enabled = interactive, colors = bubbleSwitchColors()) },
                        ) {}
                        AnimatedVisibility(
                            visibleState = timeVisibility,
                            enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                            exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
                        ) {
                            Column {
                                HorizontalDivider(color = colors.border)
                                BubbleFieldRow(
                                    Icons.Outlined.Schedule, stringResource(R.string.debt_time),
                                    Modifier.testTag("dueTimeButton").bringIntoViewRequester(timeRequester)
                                        .clickable(enabled = interactive && enabled) { pickingTime = true },
                                ) { Text(formatMinute(minute)) }
                            }
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (clearable && initialDay != null) {
                    BubbleOutlinedButton({ saveDue(null, null) }, Modifier.weight(1f).testTag("clearDueDate"), enabled = interactive) {
                        Text(stringResource(R.string.debt_clear_date), color = colors.danger, textAlign = TextAlign.Center)
                    }
                }
                BubbleButton(
                    modifier = Modifier.weight(1f).testTag("dueSaveButton"),
                    enabled = day != null && interactive && !pastTime,
                    onClick = {
                        val selectedDay = day ?: return@BubbleButton
                        val selectedMinute = minute.takeIf { showReminder && enabled && reminders.allowed() &&
                            DebtReminderPolicy.canSchedule(selectedDay) }
                        saveDue(selectedDay, selectedMinute)
                    },
                ) { Text(confirmText, textAlign = TextAlign.Center) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtCalendarPicker(
    state: DatePickerState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val formatter = remember { DatePickerDefaults.dateFormatter() }
    val colors = debtCalendarColors()
    DatePicker(
        state = state,
        modifier = modifier.testTag("dueDatePicker"),
        dateFormatter = formatter,
        colors = colors,
        title = null,
        headline = {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) {
                    DatePickerDefaults.DatePickerHeadline(
                        state.selectedDateMillis, state.displayMode, formatter,
                        Modifier.weight(1f).testTag("debtCalendarHeadline"), contentColor = colors.headlineContentColor,
                    )
                }
                if (!compact) {
                    val typing = state.displayMode == DisplayMode.Input
                    IconButton({ state.displayMode = if (typing) DisplayMode.Picker else DisplayMode.Input },
                        Modifier.size(48.dp).testTag("debtCalendarMode")) {
                        Icon(if (typing) Icons.Filled.DateRange else Icons.Filled.Edit,
                            stringResource(if (typing) R.string.debt_calendar_show else R.string.debt_calendar_input),
                            tint = colors.headlineContentColor)
                    }
                }
            }
        },
        showModeToggle = false,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun debtCalendarColors(): androidx.compose.material3.DatePickerColors {
    val colors = bubbleColors()
    return DatePickerDefaults.colors(
        containerColor = colors.paper,
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
private fun rememberDebtCalendar(initialDay: String?, minDay: String?, maxDay: String?,
    compact: Boolean): androidx.compose.material3.DatePickerState {
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
