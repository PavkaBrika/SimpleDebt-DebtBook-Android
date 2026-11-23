package com.breckneck.debtbook.settings.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.currency.CurrencyPickerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DefaultCurrencyFragment : Fragment() {
    private val vm by viewModels<SettingsViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val host = requireActivity() as SettingsFragment.OnButtonClickListener
        return bubbleScreen(R.id.defaultCurrencyComposeView) { insets ->
            val currency by vm.defaultCurrency.observeAsState("")
            CurrencyPickerScreen(
                title = stringResource(R.string.default_currency),
                selectedCode = currency,
                hostInsets = insets,
                onBack = { finishSelection() },
                onCurrencySelected = { code ->
                    finishSelection {
                        vm.setDefaultCurrency(code)
                        host.onTickVibration()
                    }
                },
            )
        }
    }

    private fun finishSelection(save: () -> Unit = {}) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.defaultCurrencyFragment) return
        save()
        nav.popBackStack()
    }
}
