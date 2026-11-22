package com.breckneck.debtbook.settings.synchronization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.SettingsRow
import com.breckneck.debtbook.settings.synchronization.SynchronizationViewModel.RestorePreview
import com.breckneck.debtbook.settings.synchronization.SynchronizationViewModel.RestoreSource
import com.breckneck.debtbook.settings.synchronization.SynchronizationViewModel.SaveDestination
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

@Composable
internal fun BackupScreen(
    vm: SynchronizationViewModel,
    hostInsets: PaddingValues,
    driveAvailable: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSignIn: () -> Unit,
    onReconnect: () -> Unit,
    onSignOut: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val colors = bubbleColors()
    val authorized by vm.isAuthorized.observeAsState(false)
    val ready by vm.driveReady.observeAsState(false)
    val failed by vm.driveConnectionFailed.observeAsState(false)
    val savingTo by vm.savingTo.observeAsState()
    val restoring by vm.isRestoring.observeAsState(false)
    val hasPrevious by vm.hasPrevious.observeAsState(false)
    val copies by vm.driveBackups.observeAsState()
    val preview by vm.restorePreview.observeAsState()
    val error by vm.operationError.observeAsState()
    val email by vm.emailAddress.observeAsState("")
    val lastSaved by vm.lastSyncDate.observeAsState("")
    val name by vm.userName.observeAsState("")
    val busy = savingTo != null || restoring
    val enabled = !busy && copies == null && preview == null
    var confirm by rememberSaveable { mutableIntStateOf(0) }
    val saveProgress: @Composable () -> Unit = { LinearProgressIndicator(Modifier.fillMaxWidth()) }

    BubbleScaffold(
        hostInsets = hostInsets,
        topBar = {
            BubbleTopBar(stringResource(R.string.synchronization), onBack, hostInsets, backEnabled = !busy)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (restoring && copies == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.backup_wait))
            }
            if (error != null) Text(stringResource(R.string.backup_transfer_failed),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = colors.danger)
            BubbleSectionTitle(stringResource(R.string.authorize_to_avoid_losing_your_data))
            if (authorized) {
                BubbleGroup {
                    SettingsRow(name, email)
                    HorizontalDivider(color = colors.border)
                    if (failed) SettingsRow(stringResource(R.string.backup_drive_retry),
                        stringResource(R.string.backup_drive_connection_failed), enabled, onClick = onReconnect)
                    else SettingsRow(
                        title = stringResource(R.string.synchronize),
                        detail = when {
                            !ready -> stringResource(R.string.backup_drive_connecting)
                            lastSaved.isNotBlank() -> "${stringResource(R.string.last_synchronization)} $lastSaved"
                            else -> null
                        },
                        enabled = enabled && ready,
                        supportingContent = if (savingTo == SaveDestination.DRIVE) saveProgress else null,
                        onClick = { confirm = R.string.synchronize },
                    )
                    HorizontalDivider(color = colors.border)
                    SettingsRow(stringResource(R.string.backup_drive_choose), enabled = enabled && ready, onClick = vm::loadDriveBackups)
                    HorizontalDivider(color = colors.border)
                    SettingsRow(stringResource(R.string.log_out), enabled = enabled) { confirm = R.string.log_out }
                }
            } else if (driveAvailable) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BubbleGroup {
                        SettingsRow(stringResource(R.string.sign_in_with_google), enabled = enabled, onClick = onSignIn)
                    }
                    Text(
                        text = AnnotatedString.fromHtml(stringResource(R.string.backup_drive_privacy_notice)),
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = enabled, onClickLabel = stringResource(R.string.privacy_policy), onClick = onPrivacy)
                            .heightIn(min = 48.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                        color = colors.muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else Text(stringResource(R.string.backup_drive_unavailable), color = colors.muted)
            BubbleSectionTitle(stringResource(R.string.backup_local_title))
            BubbleGroup {
                SettingsRow(
                    title = stringResource(R.string.backup_export_file),
                    enabled = enabled,
                    supportingContent = if (savingTo == SaveDestination.FILE) saveProgress else null,
                    onClick = onExport,
                )
                HorizontalDivider(color = colors.border)
                SettingsRow(stringResource(R.string.backup_import_file), enabled = enabled, onClick = onImport)
                if (hasPrevious) {
                    HorizontalDivider(color = colors.border)
                    SettingsRow(stringResource(R.string.backup_restore_previous_local),
                        stringResource(R.string.backup_undo_hint), enabled, onClick = vm::restorePreviousLocalBackup)
                }
            }
            Text(stringResource(R.string.backup_file_hint), color = colors.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (confirm != 0) {
        val save = confirm == R.string.synchronize
        BubbleBottomSheet(onDismiss = { confirm = 0 }) { active, close ->
            BubbleSheetHeader(
                stringResource(if (save) R.string.synchronize else R.string.backup_drive_sign_out_title),
                close,
                enabled = active,
            )
            Text(
                if (save) stringResource(R.string.backup_drive_save_confirm, email)
                else stringResource(R.string.backup_drive_sign_out_message)
            )
            BubbleButton(onClick = {
                confirm = 0
                if (save) vm.uploadBackup() else onSignOut()
            }, modifier = Modifier.fillMaxWidth(), enabled = active && enabled) { Text(stringResource(if (save) R.string.synchronize else R.string.log_out)) }
        }
    }
    if (copies != null || preview != null) {
        val listScroll = rememberScrollState()
        val current = preview
        key(current) {
            BubbleBottomSheet(onDismiss = {
                // A dismiss animation can finish after another file has already been selected.
                if (vm.restorePreview.value === current) vm.backFromRestore()
            },
                scrollState = if (current == null) listScroll else rememberScrollState()) { active, close ->
                if (current == null) {
                    BubbleSheetHeader(stringResource(R.string.backup_drive_choose), close, enabled = active)
                    if (restoring) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else if (copies.isNullOrEmpty()) Text(stringResource(R.string.backup_drive_empty))
                    else {
                        Text(stringResource(R.string.backup_drive_versions_hint))
                        BubbleGroup {
                            copies.orEmpty().forEachIndexed { index, copy ->
                                if (index > 0) HorizontalDivider(color = colors.border)
                                SettingsRow(stringResource(sourceTitle(copy.source)),
                                    copy.preview?.let { previewDetails(it) } ?: stringResource(R.string.backup_copy_unreadable),
                                    active && !restoring && copy.preview != null) { vm.chooseDriveBackup(copy) }
                            }
                        }
                    }
                } else {
                    BubbleSheetHeader(stringResource(if (current.source == RestoreSource.BEFORE_RESTORE)
                        R.string.backup_restore_previous_local else R.string.backup_confirm_restore_title), close, enabled = active)
                    Text(current.fileName ?: stringResource(sourceTitle(current.source)), style = MaterialTheme.typography.titleMedium)
                    Text(previewDetails(current))
                    if (current.legacy) Text(stringResource(R.string.backup_legacy_warning,
                        TimeZone.getDefault().displayName, current.legacyFinanceCurrency))
                    Text(stringResource(R.string.recover_all_data_from_the_cloud_this_will_delete_the_current_application_data))
                    if (copies != null) BubbleOutlinedButton(close, Modifier.fillMaxWidth(), active) {
                        Text(stringResource(R.string.backup_back_to_copies))
                    }
                    BubbleButton(vm::confirmRestore, Modifier.fillMaxWidth(), active && !busy) {
                        Text(stringResource(R.string.backup_confirm_restore))
                    }
                }
            }
        }
    }
}

private fun sourceTitle(source: RestoreSource) = when (source) {
    RestoreSource.FILE -> R.string.backup_import_file
    RestoreSource.DRIVE_LATEST -> R.string.backup_drive_latest
    RestoreSource.DRIVE_PREVIOUS -> R.string.backup_drive_previous
    RestoreSource.BEFORE_RESTORE -> R.string.backup_before_restore
}

@Composable
private fun previewDetails(preview: RestorePreview): String {
    val date = preview.createdAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: stringResource(R.string.backup_unknown_date)
    return stringResource(R.string.backup_preview, date, preview.records, preview.photoCount)
}
