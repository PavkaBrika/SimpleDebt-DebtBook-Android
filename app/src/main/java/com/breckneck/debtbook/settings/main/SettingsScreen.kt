package com.breckneck.debtbook.settings.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.SettingsRow
import com.breckneck.deptbook.domain.util.PINCodeAction
import com.breckneck.deptbook.domain.util.PinLockDelay

@Composable
internal fun SettingsScreen(
    vm: SettingsViewModel,
    main: MainViewModel,
    host: SettingsFragment.OnButtonClickListener,
    showBiometrics: Boolean,
    onBiometrics: () -> Unit,
    onPin: (PINCodeAction) -> Unit,
    onTheme: (String) -> Unit,
    onExternal: (Int) -> Unit,
) {
    val colors = bubbleColors()
    var sheet by rememberSaveable { mutableIntStateOf(0) }
    val currency = stringArrayResource(R.array.currencies).toList()
    val themes = listOf(R.string.system_theme, R.string.light_theme, R.string.dark_theme).map { stringResource(it) }
    val first by vm.firstMainCurrency.observeAsState("")
    val second by vm.secondMainCurrency.observeAsState("")
    val default by vm.defaultCurrency.observeAsState("")
    val theme by vm.appTheme.observeAsState("")
    val share by vm.addSumInShareText.observeAsState(false)
    val locked by main.isPINCodeEnabled.observeAsState(false)
    val biometric by vm.isFingerprintAuthEnabled.observeAsState(false)
    val delay by vm.pinLockDelay.observeAsState(PinLockDelay.entries.first())
    val delays = listOf(R.string.pin_lock_immediately, R.string.pin_lock_30_seconds,
        R.string.pin_lock_1_minute, R.string.pin_lock_5_minutes).map { stringResource(it) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings), Modifier.semantics { heading() }, color = colors.ink,
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            BubbleSectionTitle(stringResource(R.string.application))
            BubbleGroup {
                for ((label, code) in listOf(R.string.first_main_currency to first, R.string.second_main_currency to second,
                    R.string.default_currency to default)) {
                    SettingsRow(stringResource(label), currency.firstOrNull { it.substringAfterLast(' ') == code } ?: code) { sheet = label }
                }
                SettingsRow(stringResource(R.string.app_theme), theme.ifEmpty { themes.first() }) { sheet = R.string.app_theme }
                SettingsRow(stringResource(R.string.more_detailed_share_text), stringResource(R.string.detailed_share_text_hint),
                    checked = share) { vm.setSumInShareText(!share) }
            }
            BubbleSectionTitle(stringResource(R.string.settings_data_security))
            BubbleGroup {
                SettingsRow(stringResource(R.string.synchronization), onClick = host::onAuthorizationButtonClick)
                SettingsRow(stringResource(R.string.pin_code_lock), stringResource(if (locked) R.string.pin_on else R.string.pin_off)) {
                    sheet = R.string.pin_code_lock
                }
            }
            BubbleSectionTitle(stringResource(R.string.info))
            BubbleGroup {
                SettingsRow(stringResource(R.string.rate_app), onClick = host::onRateAppButtonClick)
                for (label in listOf(R.string.write_email_to_developer, R.string.support_us, R.string.privacy_policy)) {
                    SettingsRow(stringResource(label)) { onExternal(label) }
                }
                SettingsRow(stringResource(R.string.app_version), BuildConfig.VERSION_NAME)
            }
        }
        BubbleBottomBar(R.id.settingsFragment, host::onTabSelected)
    }

    when (sheet) {
        0 -> Unit
        R.string.pin_code_lock -> BubbleBottomSheet(onDismiss = { sheet = 0 }) { enabled, close ->
            BubbleSheetHeader(stringResource(R.string.pin_code_lock), close, enabled = enabled)
            BubbleGroup {
                SettingsRow(stringResource(if (locked) R.string.disable_pin else R.string.enable_pin), enabled = enabled) {
                    onPin(if (locked) PINCodeAction.DISABLE else PINCodeAction.ENABLE)
                }
                if (locked) {
                    SettingsRow(stringResource(R.string.change_pin_code), enabled = enabled) { onPin(PINCodeAction.CHANGE) }
                    SettingsRow(stringResource(R.string.pin_lock_delay_title), delays[delay.ordinal], enabled) { sheet = R.string.pin_lock_delay_title }
                    if (showBiometrics) SettingsRow(stringResource(R.string.unlock_with_fingerprint),
                        enabled = enabled, checked = biometric, onClick = onBiometrics)
                }
            }
        }
        else -> {
            val options = when (sheet) {
                R.string.app_theme -> themes
                R.string.pin_lock_delay_title -> delays
                else -> currency
            }
            val selected = when (sheet) {
                R.string.app_theme -> themes.indexOf(theme).coerceAtLeast(0)
                R.string.pin_lock_delay_title -> delay.ordinal
                else -> currency.indexOfFirst { it.substringAfterLast(' ') == when (sheet) {
                    R.string.first_main_currency -> first
                    R.string.second_main_currency -> second
                    else -> default
                } }
            }
            val parent = if (sheet == R.string.pin_lock_delay_title) R.string.pin_code_lock else 0
            SettingsPickerBottomSheet(stringResource(sheet), options, selected, onItemSelected = { index ->
                val setting = sheet
                sheet = parent
                host.onTickVibration()
                val value = options[index]
                when (setting) {
                    R.string.app_theme -> onTheme(value)
                    R.string.pin_lock_delay_title -> vm.setPinLockDelay(PinLockDelay.entries[index])
                    R.string.default_currency -> vm.setDefaultCurrency(value.substringAfterLast(' '))
                    else -> {
                        val code = value.substringAfterLast(' ')
                        if (setting == R.string.first_main_currency) vm.setFirstMainCurrency(code) else vm.setSecondMainCurrency(code)
                        main.setIsNeedUpdateDebtSums(true)
                    }
                }
            }, onDismiss = { sheet = parent })
        }
    }
}
