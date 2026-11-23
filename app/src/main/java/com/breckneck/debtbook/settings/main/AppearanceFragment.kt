package com.breckneck.debtbook.settings.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.SettingsChoiceRow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppearanceFragment : Fragment() {
    private val vm by viewModels<SettingsViewModel>()
    private val main by activityViewModels<MainViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val host = requireActivity() as SettingsFragment.OnButtonClickListener
        return bubbleScreen(R.id.appearanceComposeView) { insets ->
            val theme by vm.appTheme.observeAsState()
            AppearanceScreen(
                selectedTheme = AppTheme.fromPreference(theme),
                hostInsets = insets,
                onBack = {
                    val nav = findNavController()
                    if (nav.currentDestination?.id == R.id.appearanceFragment) nav.popBackStack()
                },
                onThemeSelected = { selected ->
                    host.onTickVibration()
                    vm.setAppTheme(selected.name)
                    main.getAppTheme()
                },
            )
        }
    }
}

@Composable
private fun AppearanceScreen(
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
