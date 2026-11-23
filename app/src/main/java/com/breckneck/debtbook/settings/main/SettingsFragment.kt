package com.breckneck.debtbook.settings.main

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.core.net.toUri
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.settings.openSettingsIntent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.compose.material3.Surface
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    interface OnButtonClickListener {
        fun onRateAppButtonClick()
        fun onAuthorizationButtonClick()
        fun onTickVibration()
        fun onTabSelected(id: Int)
    }

    private val vm by viewModels<SettingsViewModel>()
    private val main by activityViewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener("settingsFragmentKey") { _, result ->
            if (result.getBoolean("isListModified")) main.setIsNeedUpdateDebtData(true)
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDefaultCurrency()
        main.getIsPINCodeEnabled()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val host = requireActivity() as OnButtonClickListener
        return bubbleScreen(R.id.settingsComposeView) { insets ->
            Surface(color = bubbleColors().background, contentColor = bubbleColors().ink) {
                SettingsScreen(vm, insets, main, host,
                    onAppSecurity = { openSettingsPage(R.id.action_settingsFragment_to_appSecurityFragment) },
                    onDefaultCurrency = { openSettingsPage(R.id.action_settingsFragment_to_defaultCurrencyFragment) },
                    onAppearance = { openSettingsPage(R.id.action_settingsFragment_to_appearanceFragment) },
                    onExternal = ::openExternal,
                )
            }
        }
    }

    private fun openSettingsPage(@IdRes action: Int) {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.settingsFragment) nav.navigate(action)
    }

    private fun openExternal(action: Int) {
        val intent = when (action) {
            R.string.write_email_to_developer -> Intent(Intent.ACTION_SENDTO, "mailto:pavlikbrichkin@yandex.ru".toUri())
                .putExtra(Intent.EXTRA_SUBJECT, "${getString(R.string.email_subject)} ${BuildConfig.VERSION_NAME}")
            R.string.support_us -> Intent(Intent.ACTION_VIEW, "https://pay.cloudtips.ru/p/5c867537".toUri())
            else -> Intent(Intent.ACTION_VIEW, getString(R.string.privacy_policy_url).toUri())
        }
        requireContext().openSettingsIntent(intent)
    }
}
