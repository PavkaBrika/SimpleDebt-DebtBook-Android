package com.breckneck.debtbook.settings.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.SettingsChoiceRow
import com.breckneck.debtbook.settings.SettingsRow
import com.breckneck.debtbook.settings.main.SettingsViewModel
import com.breckneck.deptbook.domain.util.PINCodeAction
import com.breckneck.deptbook.domain.util.PinLockDelay

@Composable
internal fun AppSecurityScreen(
    hostInsets: PaddingValues,
    vm: SettingsViewModel,
    pinEnabled: LiveData<Boolean>,
    showBiometrics: Boolean,
    onBack: () -> Unit,
    onPin: (PINCodeAction) -> Unit,
    onBiometrics: () -> Unit,
    onTick: () -> Unit,
) {
    val colors = bubbleColors()
    val locked by pinEnabled.observeAsState(false)
    val biometric by vm.isFingerprintAuthEnabled.observeAsState(false)
    val delay by vm.pinLockDelay.observeAsState(PinLockDelay.IMMEDIATE)
    var showDelay by rememberSaveable { mutableStateOf(false) }
    val delays = listOf(
        R.string.pin_lock_immediately, R.string.pin_lock_30_seconds,
        R.string.pin_lock_1_minute, R.string.pin_lock_5_minutes,
    ).map { stringResource(it) }

    BubbleScaffold(
        hostInsets = hostInsets,
        topBar = { BubbleTopBar(stringResource(R.string.pin_code_lock), onBack, hostInsets) },
    ) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
            BubbleGroup {
                if (locked) {
                    SettingsRow(stringResource(R.string.change_pin_code)) { onPin(PINCodeAction.CHANGE) }
                    HorizontalDivider(color = colors.border)
                    SettingsRow(stringResource(R.string.disable_pin)) { onPin(PINCodeAction.DISABLE) }
                    HorizontalDivider(color = colors.border)
                    SettingsRow(stringResource(R.string.pin_lock_delay_title), value = delays[delay.ordinal]) {
                        showDelay = true
                    }
                    if (showBiometrics) {
                        HorizontalDivider(color = colors.border)
                        SettingsRow(stringResource(R.string.unlock_with_fingerprint), checked = biometric, onClick = onBiometrics)
                    }
                } else {
                    SettingsRow(stringResource(R.string.enable_pin)) { onPin(PINCodeAction.ENABLE) }
                }
            }
        }
    }

    if (locked && showDelay) {
        BubbleBottomSheet(onDismiss = { showDelay = false }) { enabled, close ->
            BubbleSheetHeader(stringResource(R.string.pin_lock_delay_title), close, enabled = enabled)
            BubbleGroup(Modifier.selectableGroup()) {
                delays.forEachIndexed { index, label ->
                    if (index > 0) HorizontalDivider(color = colors.border)
                    SettingsChoiceRow(label, selected = index == delay.ordinal, enabled = enabled) {
                        vm.setPinLockDelay(PinLockDelay.entries[index])
                        onTick()
                        close()
                    }
                }
            }
            Text(
                stringResource(R.string.pin_lock_delay_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
    }
}
