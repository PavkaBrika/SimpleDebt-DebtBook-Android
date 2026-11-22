package com.breckneck.debtbook.settings.security

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.AuthorizationActivity
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.settings.main.SettingsViewModel
import com.breckneck.debtbook.settings.openSettingsIntent
import com.breckneck.deptbook.domain.util.PINCodeAction
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppSecurityFragment : Fragment() {
    private val vm by viewModels<SettingsViewModel>()
    private val main by activityViewModels<MainViewModel>()
    private val authorize = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshProtection()
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
    }

    override fun onResume() {
        super.onResume()
        refreshProtection()
        biometricStatus = BiometricManager.from(requireContext()).canAuthenticate(BIOMETRIC_STRONG)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        bubbleScreen(R.id.appSecurityComposeView) { insets ->
            AppSecurityScreen(
                hostInsets = insets,
                vm = vm,
                pinEnabled = main.isPINCodeEnabled,
                showBiometrics = biometricStatus == BiometricManager.BIOMETRIC_SUCCESS ||
                    (Build.VERSION.SDK_INT >= 30 && biometricStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
                onBack = {
                    val nav = findNavController()
                    if (nav.currentDestination?.id == R.id.appSecurityFragment) nav.popBackStack()
                },
                onPin = ::openPin,
                onBiometrics = ::changeBiometrics,
                onTick = (requireActivity() as MainActivity)::onTickVibration,
            )
        }

    private fun refreshProtection() {
        main.getIsPINCodeEnabled()
        vm.getIsFingerprintAuthEnabled()
    }

    private fun openPin(action: PINCodeAction) {
        authorize.launch(Intent(requireContext(), AuthorizationActivity::class.java)
            .putExtra("PINCodeState", action.name))
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
            Build.VERSION.SDK_INT >= 30 -> requireContext().openSettingsIntent(Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG))
        }
    }
}
