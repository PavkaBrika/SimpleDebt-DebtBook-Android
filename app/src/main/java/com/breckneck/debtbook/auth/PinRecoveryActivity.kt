package com.breckneck.debtbook.auth

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.PinRecoveryViewModel.Error
import com.breckneck.debtbook.auth.PinRecoveryViewModel.Step
import com.breckneck.debtbook.auth.screen.PinEntryError
import com.breckneck.debtbook.auth.screen.PinEntryLayout
import com.breckneck.debtbook.core.enableBubbleEdgeToEdge
import com.breckneck.debtbook.core.ui.components.BubbleBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.deptbook.domain.usecase.Settings.GetPINCodeEnabled
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Internal recovery route. System authentication grants only a PIN replacement. */
@AndroidEntryPoint
class PinRecoveryActivity : AppCompatActivity() {
    @Inject lateinit var getPinEnabled: GetPINCodeEnabled
    private val vm by viewModels<PinRecoveryViewModel>()
    private lateinit var prompt: BiometricPrompt
    private var completed = false
    private val deviceCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) vm.onDeviceVerified()
        else vm.verificationFailed(Error.CANCELLED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableBubbleEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (!getPinEnabled.execute()) { finish(); return }
        // Re-create the callback on configuration changes so AndroidX can reconnect it.
        prompt = BiometricPrompt(this, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                vm.onDeviceVerified()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                vm.verificationFailed(if (errorCode == BiometricPrompt.ERROR_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) Error.CANCELLED else Error.UNAVAILABLE)
            }
        })
        setContent {
            val state by vm.state.collectAsState()
            LaunchedEffect(state.step) {
                if (state.step == Step.FINISHED && !completed) {
                    completed = true
                    AuthSession.unlock()
                    setResult(RESULT_OK)
                    finish()
                }
            }
            DebtBookTheme {
                if (state.step == Step.NEW_PIN || state.step == Step.CONFIRM_PIN) {
                    RecoveryPinScreen(
                        state = state,
                        onDigit = { vm.enterDigit(it, PinCodeStore(this@PinRecoveryActivity)::save) },
                        onBackspace = vm::backspace,
                        onCancel = { finish() }
                    )
                } else if (state.step != Step.FINISHED) {
                    RecoverySheet(state, onVerify = ::verifyOwner, onCancel = { finish() })
                }
            }
        }
    }

    private fun verifyOwner() {
        if (!vm.beginVerification()) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) { vm.verificationFailed(Error.UNAVAILABLE); return }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                    vm.verificationFailed(Error.UNAVAILABLE)
                    return
                }
                // A credential fallback and a custom negative button cannot be combined.
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.pin_recovery_title))
                    .setDescription(getString(R.string.pin_recovery_system_description))
                    .setAllowedAuthenticators(authenticators)
                    .build())
            } else {
                // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is unsupported on API 23–29.
                @Suppress("DEPRECATION")
                val intent = keyguard.createConfirmDeviceCredentialIntent(
                    getString(R.string.pin_recovery_title), getString(R.string.pin_recovery_system_description))
                if (intent == null) vm.verificationFailed(Error.UNAVAILABLE) else deviceCredential.launch(intent)
            }
        } catch (_: Exception) {
            vm.verificationFailed(Error.UNAVAILABLE)
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) vm.onBackground()
        super.onStop()
    }
}

@Composable
private fun RecoverySheet(state: PinRecoveryViewModel.State, onVerify: () -> Unit, onCancel: () -> Unit) {
    BubbleBottomSheet(onDismiss = onCancel, modifier = Modifier.testTag("pinRecoverySheet")) { enabled, close ->
        BubbleSheetHeader(stringResource(R.string.pin_recovery_title), close, enabled = enabled)
        Text(
            stringResource(R.string.pin_recovery_message),
            style = MaterialTheme.typography.bodyMedium,
            color = bubbleColors().muted,
        )
        RecoveryError(state.error)
        BubbleButton(
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && state.step != Step.VERIFYING,
        ) {
            Text(stringResource(R.string.pin_recovery_verify))
        }
    }
}

@Composable
private fun RecoveryPinScreen(
    state: PinRecoveryViewModel.State,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = bubbleColors()
    PinEntryLayout(
        hostInsets = WindowInsets.safeDrawing.asPaddingValues(),
        prompt = if (state.step == Step.NEW_PIN) R.string.enter_new_pin else R.string.confirm_your_pin,
        pin = state.digits,
        header = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("pinRecoveryCancel")) {
                Text(stringResource(R.string.cancel), color = colors.action)
            }
        },
        supportingContent = { RecoveryError(state.error) },
        keypadModifier = Modifier.testTag("pinRecoveryKeypad"),
        onDigit = onDigit,
        onBackspace = onBackspace,
    )
}

@Composable
private fun RecoveryError(error: Error?) {
    error?.let {
        PinEntryError(stringResource(when (it) {
            Error.UNAVAILABLE -> R.string.pin_recovery_unavailable
            Error.CANCELLED -> R.string.pin_recovery_cancelled
            Error.MISMATCH -> R.string.pin_recovery_mismatch
            Error.SAVE_FAILED -> R.string.pin_save_failed_message
        }))
    }
}
