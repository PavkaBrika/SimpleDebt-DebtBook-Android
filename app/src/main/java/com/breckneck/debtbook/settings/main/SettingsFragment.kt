package com.breckneck.debtbook.settings.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.AuthorizationActivity
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
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
    private val authorize = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        main.getIsPINCodeEnabled()
        vm.getIsFingerprintAuthEnabled()
    }
    private lateinit var prompt: BiometricPrompt
    private var biometricStatus by mutableIntStateOf(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prompt = BiometricPrompt(this, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (main.isPINCodeEnabled.value == true) vm.setIsFingerprintAuthEnabled(true)
            }
        })
        setFragmentResultListener("settingsFragmentKey") { _, result ->
            if (result.getBoolean("isListModified")) main.setIsNeedUpdateDebtData(true)
        }
    }

    override fun onResume() {
        super.onResume()
        main.getIsPINCodeEnabled()
        vm.getIsFingerprintAuthEnabled()
        biometricStatus = BiometricManager.from(requireContext()).canAuthenticate(BIOMETRIC_STRONG)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val host = requireActivity() as OnButtonClickListener
        return ComposeView(requireContext()).apply {
            id = R.id.settingsComposeView
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DebtBookTheme {
                    SettingsScreen(vm, main, host,
                        showBiometrics = biometricStatus == BiometricManager.BIOMETRIC_SUCCESS ||
                            (Build.VERSION.SDK_INT >= 30 && biometricStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
                        onBiometrics = ::changeBiometrics,
                        onPin = { action -> authorize.launch(Intent(requireContext(), AuthorizationActivity::class.java)
                            .putExtra("PINCodeState", action.name)) },
                        onTheme = { theme ->
                            vm.setAppTheme(theme)
                            main.getAppTheme()
                        },
                        onExternal = ::openExternal,
                    )
                }
            }
        }
    }

    private fun changeBiometrics() {
        when {
            vm.isFingerprintAuthEnabled.value == true -> vm.setIsFingerprintAuthEnabled(false)
            biometricStatus == BiometricManager.BIOMETRIC_SUCCESS -> prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.unlock_with_fingerprint))
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .setNegativeButtonText(getString(R.string.cancel)).build()
            )
            Build.VERSION.SDK_INT >= 30 -> launchExternal(Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG))
        }
    }

    private fun openExternal(action: Int) {
        val intent = when (action) {
            R.string.write_email_to_developer -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:pavlikbrichkin@yandex.ru"))
                .putExtra(Intent.EXTRA_SUBJECT, "${getString(R.string.email_subject)} ${BuildConfig.VERSION_NAME}")
            R.string.support_us -> Intent(Intent.ACTION_VIEW, Uri.parse("https://pay.cloudtips.ru/p/5c867537"))
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)))
        }
        launchExternal(intent)
    }

    private fun launchExternal(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.something_went_wrong, Toast.LENGTH_LONG).show()
        }
    }
}
