package com.breckneck.debtbook.settings.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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

@Composable
internal fun SettingsScreen(
    vm: SettingsViewModel,
    hostInsets: PaddingValues,
    main: MainViewModel,
    host: SettingsFragment.OnButtonClickListener,
    onAppSecurity: () -> Unit,
    onTheme: (String) -> Unit,
    onExternal: (Int) -> Unit,
) {
    val colors = bubbleColors()
    var sheet by rememberSaveable { mutableIntStateOf(0) }
    val themes = listOf(R.string.system_theme, R.string.light_theme, R.string.dark_theme).map { stringResource(it) }
    val defaultCurrency by vm.defaultCurrency.observeAsState("")
    val theme by vm.appTheme.observeAsState("")
    val share by vm.addSumInShareText.observeAsState(false)
    val locked by main.isPINCodeEnabled.observeAsState(false)

    BubbleScaffold(
        hostInsets = hostInsets,
        tabs = { BubbleBottomBar(R.id.settingsFragment, host::onTabSelected) },
    ) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings), Modifier.semantics { heading() }, color = colors.ink,
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            BubbleSectionTitle(stringResource(R.string.application))
            BubbleGroup {
                SettingsRow(stringResource(R.string.default_currency), value = defaultCurrency) {
                    sheet = R.string.default_currency
                }
                HorizontalDivider(color = colors.border)
                SettingsRow(stringResource(R.string.app_theme), value = theme.ifEmpty { themes.first() }) { sheet = R.string.app_theme }
                HorizontalDivider(color = colors.border)
                SettingsRow(stringResource(R.string.more_detailed_share_text),
                    checked = share) { vm.setSumInShareText(!share) }
            }
            BubbleSectionTitle(stringResource(R.string.settings_data_security))
            BubbleGroup {
                SettingsRow(stringResource(R.string.synchronization), value = stringResource(R.string.backup_formats), onClick = host::onAuthorizationButtonClick)
                HorizontalDivider(color = colors.border)
                SettingsRow(
                    stringResource(R.string.pin_code_lock),
                    value = stringResource(if (locked) R.string.pin_on else R.string.pin_off),
                    onClick = onAppSecurity,
                )
            }
            BubbleSectionTitle(stringResource(R.string.info))
            BubbleGroup {
                SettingsRow(stringResource(R.string.rate_app), onClick = host::onRateAppButtonClick)
                for (label in listOf(R.string.write_email_to_developer, R.string.support_us, R.string.privacy_policy)) {
                    HorizontalDivider(color = colors.border)
                    SettingsRow(stringResource(label)) { onExternal(label) }
                }
                HorizontalDivider(color = colors.border)
                SettingsRow(stringResource(R.string.app_version), value = BuildConfig.VERSION_NAME)
            }
        }
    }

    when (sheet) {
        0 -> Unit
        else -> {
            val options = when (sheet) {
                R.string.app_theme -> themes
                else -> currencyOptions(stringArrayResource(R.array.currencies).toList(), defaultCurrency)
            }
            val selected = when (sheet) {
                R.string.app_theme -> themes.indexOf(theme).coerceAtLeast(0)
                else -> options.indexOfFirst { currencyCode(it) == defaultCurrency }
            }
            SettingsPickerBottomSheet(stringResource(sheet), options, selected, onItemSelected = { index ->
                val setting = sheet
                sheet = 0
                host.onTickVibration()
                val value = options[index]
                when (setting) {
                    R.string.app_theme -> onTheme(value)
                    R.string.default_currency -> vm.setDefaultCurrency(currencyCode(value))
                }
            }, onDismiss = { sheet = 0 })
        }
    }
}
