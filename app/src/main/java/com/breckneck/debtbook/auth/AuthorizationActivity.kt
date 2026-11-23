package com.breckneck.debtbook.auth

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import javax.inject.Inject
import com.breckneck.debtbook.core.enableBubbleEdgeToEdge
import com.breckneck.deptbook.domain.usecase.Settings.GetPinLockDelay
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.screen.AuthorizationScreen
import com.breckneck.debtbook.auth.util.BiometricPromptManager
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.deptbook.domain.util.PINCodeAction.CHECK
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthorizationActivity : AppCompatActivity() {
    @Inject lateinit var getPinLockDelay: GetPinLockDelay
    private val vm by viewModels<AuthorizationViewModel>()
    val biometricPromptManager = BiometricPromptManager(this)
    private var completed = false
    private var trustedCaller = false
    private val recovery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Result codes/extras alone never unlock the app.
        if (it.resultCode == RESULT_OK && AuthSession.isUnlocked) completeAuthentication()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableBubbleEdgeToEdge()
        trustedCaller = callingActivity?.packageName == packageName
        vm.setPINCodeAction(allowedPINAction(
            intent.getStringExtra("PINCodeState"),
            pinEnabled = vm.isPINCodeEnabled.value == true,
            trustedCaller = trustedCaller
        ))

        if (vm.pinCodeAction.value == CHECK && AuthSession.canAccess(
                pinEnabled = vm.isPINCodeEnabled.value == true,
                nowMillis = SystemClock.elapsedRealtime(),
                delayMillis = getPinLockDelay.execute().millis)) {
            completeAuthentication()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        vm.loadStoredPIN(this)

        setContent {
            DebtBookTheme {
                AuthorizationScreen(
                    activity = this,
                    vm = vm,
                    biometricPromptManager = biometricPromptManager,
                    onAuthenticated = ::completeAuthentication,
                    onRecoverPin = { recovery.launch(Intent(this, PinRecoveryActivity::class.java)) },
                )
            }
        }
    }

    /** Called only after a verified PIN, allowed biometric success, or a disabled lock. */
    private fun completeAuthentication() {
        if (completed) return
        completed = true
        AuthSession.unlock()
        if (trustedCaller) {
            setResult(RESULT_OK, Intent().putExtra("PINCodeState", vm.pinCodeAction.value.name))
        } else {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        finish()
    }

    override fun onStart() {
        super.onStart()
        if (!completed && vm.isFingerprintAuthEnabled.value && vm.pinCodeAction.value == CHECK) {
            biometricPromptManager.showBiometricPrompt(
                title = getString(R.string.login_in_the_debtbook),
                description = getString(R.string.scan_your_fingerprint),
                authenticators = BIOMETRIC_STRONG,
                negativeButtonText = getString(R.string.cancel)
            )
        }
    }
}
