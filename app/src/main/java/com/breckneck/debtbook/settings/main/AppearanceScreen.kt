package com.breckneck.debtbook.settings.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.SettingsChoiceRow

@Composable
internal fun AppearanceScreen(
    selectedTheme: AppTheme,
    hostInsets: PaddingValues,
    onBack: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
) {
    BubbleScaffold(
        hostInsets = hostInsets,
        topBar = { BubbleTopBar(stringResource(R.string.app_theme), onBack, hostInsets) },
    ) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
            BubbleGroup(Modifier.selectableGroup()) {
                AppTheme.entries.forEachIndexed { index, theme ->
                    if (index > 0) HorizontalDivider(color = bubbleColors().border)
                    SettingsChoiceRow(stringResource(theme.label), selected = theme == selectedTheme) {
                        onThemeSelected(theme)
                    }
                }
            }
        }
    }
}
