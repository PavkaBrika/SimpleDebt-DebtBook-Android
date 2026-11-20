package com.breckneck.debtbook.debt.reminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.DebtBookBottomSheet
import com.breckneck.debtbook.core.ui.theme.spacing
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal data class DebtDueDraft(val day: String? = null, val reminderMinute: Int? = null)

private enum class DuePage { SETTINGS, DATE, TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtDueSheet(
    initial: DebtDueDraft,
    allowReminder: Boolean = true,
    confirmText: String = stringResource(R.string.save),
    onDismiss: () -> Unit,
    onSave: suspend (DebtDueDraft) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reminders = remember(context) { DebtReminders.get(context) }
    val scope = rememberCoroutineScope()
    val spacing = MaterialTheme.spacing
    var day by rememberSaveable { mutableStateOf(initial.day) }
    var minute by rememberSaveable { mutableStateOf(initial.reminderMinute ?: 9 * 60) }
    var enabled by rememberSaveable { mutableStateOf(initial.reminderMinute != null && reminders.allowed()) }
    var allowed by remember { mutableStateOf(reminders.allowed()) }
    var page by rememberSaveable { mutableStateOf(DuePage.SETTINGS) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

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

    fun changeReminder(checked: Boolean) {
        error = null
        allowed = reminders.allowed()
        enabled = checked && allowed
        if (checked && !allowed && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun save() {
        if (busy) return
        val selectedDay = day
        val selectedMinute = minute.takeIf { selectedDay != null && enabled && allowReminder && reminders.allowed() }
        if (selectedMinute != null &&
            DebtReminderPolicy.trigger(requireNotNull(selectedDay), selectedMinute) <= System.currentTimeMillis()) {
            error = R.string.due_future_required
            return
        }
        busy = true
        error = null
        scope.launch(Dispatchers.Main.immediate) {
            try {
                onSave(DebtDueDraft(selectedDay, selectedMinute))
                onDismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e("DebtDueSheet", "Could not save due date", failure)
                error = R.string.due_save_failed
            } finally {
                busy = false
            }
        }
    }

    DebtBookBottomSheet(
        onDismiss = { if (!busy) onDismiss() },
        gesturesEnabled = !busy && page == DuePage.SETTINGS,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !busy,
            shouldDismissOnClickOutside = !busy && page == DuePage.SETTINGS
        )
    ) {
        BackHandler(enabled = page != DuePage.SETTINGS) { page = DuePage.SETTINGS }
        Column(
            Modifier.fillMaxWidth().padding(spacing.space24).testTag("debtDueSheet"),
            verticalArrangement = Arrangement.spacedBy(spacing.space12)
        ) {
            Text(stringResource(when (page) {
                DuePage.SETTINGS -> R.string.due_settings
                DuePage.DATE -> R.string.due_choose_date
                DuePage.TIME -> R.string.due_choose_time
            }), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("dueTitle"))

            val datePicker = if (page == DuePage.DATE) {
                rememberDatePickerState(initialSelectedDateMillis = day?.let(LedgerDate::pickerMillis))
            } else null
            val timePicker = if (page == DuePage.TIME) rememberTimePickerState(minute / 60, minute % 60) else null

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                when (page) {
                    DuePage.SETTINGS -> {
                        val selected = day
                        DueOption(
                            stringResource(R.string.due_choose_date),
                            selected?.let { LedgerDate.display(it) },
                            "dueDateButton", !busy
                        ) { page = DuePage.DATE }
                        if (selected != null) {
                            TextButton(
                                onClick = { day = null; enabled = false; error = null },
                                modifier = Modifier.testTag("dueClearButton"),
                                enabled = !busy
                            ) { Text(stringResource(R.string.due_clear)) }
                            if (allowReminder) {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.due_enable)) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    trailingContent = {
                                        Switch(
                                            checked = enabled,
                                            onCheckedChange = null,
                                            enabled = !busy
                                        )
                                    },
                                    modifier = Modifier.testTag("dueReminderSwitch")
                                        .toggleable(value = enabled, enabled = !busy, role = Role.Switch, onValueChange = ::changeReminder)
                                )
                                if (enabled) {
                                    DueOption(
                                        stringResource(R.string.due_choose_time),
                                        reminderTimeLabel(context, minute),
                                        "dueTimeButton", !busy
                                    ) { page = DuePage.TIME }
                                }
                                if (!allowed) {
                                    Text(stringResource(R.string.due_permission_denied),
                                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("duePermissionStatus"))
                                    TextButton(onClick = { openNotificationSettings(context) }, enabled = !busy) {
                                        Text(stringResource(R.string.due_notification_settings))
                                    }
                                }
                            }
                        }
                        error?.let {
                            Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("dueError"))
                        }
                    }
                    DuePage.DATE -> DatePicker(
                        state = requireNotNull(datePicker), title = null, headline = null,
                        modifier = Modifier.testTag("dueDatePicker")
                    )
                    DuePage.TIME -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimePicker(state = requireNotNull(timePicker), modifier = Modifier.testTag("dueTimePicker"))
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.space8, Alignment.End)) {
                TextButton(onClick = { if (page == DuePage.SETTINGS) onDismiss() else page = DuePage.SETTINGS },
                    enabled = !busy, modifier = Modifier.testTag("dueCancelButton")) {
                    Text(stringResource(R.string.cancel))
                }
                if (page != DuePage.SETTINGS || day != null || initial.day != null) Button(onClick = {
                    when (page) {
                        DuePage.SETTINGS -> save()
                        DuePage.DATE -> {
                            day = requireNotNull(datePicker?.selectedDateMillis).let(LedgerDate::dayFromPickerMillis)
                            error = null
                            page = DuePage.SETTINGS
                        }
                        DuePage.TIME -> {
                            minute = requireNotNull(timePicker).hour * 60 + timePicker.minute
                            error = null
                            page = DuePage.SETTINGS
                        }
                    }
                }, enabled = !busy && (page != DuePage.DATE || datePicker?.selectedDateMillis != null),
                    modifier = Modifier.testTag("dueSaveButton")) {
                    Text(if (page == DuePage.SETTINGS) confirmText else stringResource(R.string.due_select))
                }
            }
        }
    }
}

@Composable
private fun DueOption(title: String, value: String?, tag: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        supportingContent = value?.let { { Text(it) } },
        modifier = Modifier.testTag(tag).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(intent)
}

internal fun reminderTimeLabel(context: Context, minute: Int): String {
    val utc = TimeZone.getTimeZone("UTC")
    val clock = Calendar.getInstance(utc).apply {
        clear()
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
    }
    return DateFormat.getTimeFormat(context).apply { timeZone = utc }.format(clock.time)
}
