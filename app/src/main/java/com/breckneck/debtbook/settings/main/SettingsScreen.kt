package com.breckneck.debtbook.settings.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    onDefaultCurrency: () -> Unit,
    onAppearance: () -> Unit,
    onExternal: (Int) -> Unit,
) {
    val colors = bubbleColors()
    val defaultCurrency by vm.defaultCurrency.observeAsState("")
    val theme by main.appTheme.observeAsState()
    val locked by main.isPINCodeEnabled.observeAsState(false)

    BubbleScaffold(
        hostInsets = hostInsets,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bubbleRootTitleTopPadding, bottom = 16.dp),
        tabs = { BubbleBottomBar(R.id.settingsFragment, host::onTabSelected) },
    ) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BubbleRootTitle(stringResource(R.string.settings))
            BubbleSectionTitle(stringResource(R.string.application))
            BubbleGroup {
                SettingsRow(stringResource(R.string.default_currency), value = currencyLabel(defaultCurrency), onClick = onDefaultCurrency)
                HorizontalDivider(color = colors.border)
                SettingsRow(stringResource(R.string.app_theme), value = stringResource(AppTheme.fromPreference(theme).label), onClick = onAppearance)
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
}
