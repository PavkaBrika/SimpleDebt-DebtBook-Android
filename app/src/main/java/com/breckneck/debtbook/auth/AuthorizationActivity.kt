package com.breckneck.debtbook.auth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import javax.inject.Inject
import com.breckneck.debtbook.core.enableBubbleEdgeToEdge
import com.breckneck.deptbook.domain.usecase.Settings.GetPinLockDelay
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AtomicFile
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.screen.AuthorizationScreen
import com.breckneck.debtbook.auth.util.BiometricPromptManager
import com.breckneck.debtbook.auth.util.CryptoManager
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.deptbook.domain.util.CRYPTO_FILE_NAME
import com.breckneck.deptbook.domain.util.PINCodeAction.CHECK
import com.breckneck.deptbook.domain.util.PINCodeEnterState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

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
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
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

        val cryptoManager = runCatching { CryptoManager() }.getOrNull()
        val file = File(filesDir, CRYPTO_FILE_NAME)
        if (file.exists() || File(file.path + ".bak").exists()) {
            val pin = runCatching {
                requireNotNull(cryptoManager).decrypt(AtomicFile(file).openRead()).decodeToString()
            }.getOrNull()
            // Lost/restored keystore keys or malformed files never disable the lock.
            vm.setDecryptedPINCode(pin.orEmpty())
        } else {
            // Keep a valid legacy PIN from pre-Keystore versions; empty data stays locked.
            vm.setDecryptedPINCode(vm.currentPINCode.value)
        }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.pinCodeEnterState.collect { state ->
                    if (state == PINCodeEnterState.INCORRECT && Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, 255))
                    }
                }
            }
        }

        setContent {
            DebtBookTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AuthorizationScreen(
                        activity = this,
                        biometricPromptManager = biometricPromptManager,
                        onAuthenticated = ::completeAuthentication,
                        onRecoverPin = {
                            recovery.launch(Intent(this, PinRecoveryActivity::class.java))
                        }
                    )
                }
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
