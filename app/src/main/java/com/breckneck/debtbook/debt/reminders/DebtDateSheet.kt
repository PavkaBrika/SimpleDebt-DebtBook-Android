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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtDateSheet(
    title: String,
    initialDay: String?,
    minDay: String? = null,
    maxDay: String? = null,
    initialReminder: Boolean? = null,
    confirmText: String = stringResource(R.string.save),
    onDismiss: () -> Unit,
    onSave: suspend (day: String, remind: Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reminders = remember(context) { DebtReminders.get(context) }
    val scope = rememberCoroutineScope()
    val spacing = MaterialTheme.spacing
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
    val calendar = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        initialDisplayedMonthMillis = displayedMonth,
        yearRange = minOf(displayYear, DatePickerDefaults.YearRange.first)..maxOf(displayYear, DatePickerDefaults.YearRange.last),
        selectableDates = selectableDates
    )
    val day = calendar.selectedDateMillis?.takeIf(selectableDates::isSelectableDate)?.let(LedgerDate::dayFromPickerMillis)
    val showReminder = initialReminder != null && day != null && DebtReminderPolicy.canSchedule(day)
    var allowed by remember { mutableStateOf(reminders.allowed()) }
    var enabled by rememberSaveable { mutableStateOf(initialReminder == true && allowed) }
    var error by rememberSaveable { mutableStateOf(false) }
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

    DebtBookBottomSheet(
        onDismiss = { if (!busy) onDismiss() },
        gesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !busy,
            shouldDismissOnClickOutside = !busy
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = spacing.space24).testTag("debtDueSheet"),
            verticalArrangement = Arrangement.spacedBy(spacing.space12)
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                DatePicker(
                    state = calendar,
                    title = null,
                    headline = {
                        Text(title, modifier = Modifier.padding(start = spacing.space24, bottom = spacing.space12),
                            style = MaterialTheme.typography.titleLarge)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dueDatePicker"),
                    colors = DatePickerDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if (showReminder) {
                Column(Modifier.fillMaxWidth().padding(horizontal = spacing.space24)) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().testTag("dueReminderSwitch")
                                .toggleable(value = enabled, enabled = !busy, role = Role.Switch) { checked ->
                                    allowed = reminders.allowed()
                                    enabled = checked && allowed
                                    if (checked && !allowed && Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.padding(horizontal = spacing.space16, vertical = spacing.space8),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.space8)
                        ) {
                            Text(stringResource(R.string.due_enable), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = enabled, onCheckedChange = null, enabled = !busy)
                        }
                    }
                    if (!allowed) {
                        Text(
                            stringResource(R.string.due_permission_denied),
                            modifier = Modifier.padding(top = spacing.space8).testTag("duePermissionStatus"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { openNotificationSettings(context) }, enabled = !busy) {
                            Text(stringResource(R.string.due_notification_settings))
                        }
                    }
                }
            }
            if (error) {
                Text(
                    stringResource(R.string.due_save_failed),
                    modifier = Modifier.padding(horizontal = spacing.space24),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.space24),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8, Alignment.End)
            ) {
                TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.testTag("dueCancelButton")) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    modifier = Modifier.testTag("dueSaveButton"),
                    enabled = day != null && !busy,
                    onClick = {
                        val selectedDay = day ?: return@Button
                        busy = true
                        error = false
                        scope.launch(Dispatchers.Main.immediate) {
                            try {
                                onSave(selectedDay, showReminder && enabled && reminders.allowed() &&
                                    DebtReminderPolicy.canSchedule(selectedDay))
                                onDismiss()
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
                ) { Text(confirmText) }
            }
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(intent)
}
