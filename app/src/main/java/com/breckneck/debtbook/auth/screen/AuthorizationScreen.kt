package com.breckneck.debtbook.auth.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.AuthorizationViewModel
import com.breckneck.debtbook.auth.PinCodeStore
import com.breckneck.debtbook.auth.util.BiometricPromptManager
import com.breckneck.debtbook.auth.util.BiometricPromptManager.BiometricResult
import com.breckneck.debtbook.core.ui.components.BubbleIconButton
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.deptbook.domain.util.PINCodeAction.*
import com.breckneck.deptbook.domain.util.PINCodeEnterState
import com.breckneck.deptbook.domain.util.PINCodeEnterState.*

@Composable
internal fun AuthorizationScreen(
    activity: AppCompatActivity,
    vm: AuthorizationViewModel,
    biometricPromptManager: BiometricPromptManager,
    onAuthenticated: () -> Unit,
    onRecoverPin: () -> Unit,
) {
    val checking = vm.pinCodeAction.value == CHECK
    val biometricResult by biometricPromptManager.promptResults.collectAsState(initial = null)
    LaunchedEffect(biometricResult) {
        if (biometricResult == BiometricResult.AuthenticationSuccess &&
            vm.isFingerprintAuthEnabled.value && checking) {
            onAuthenticated()
        }
    }
    val emailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    PinEntryScreen(
        vm = vm,
        hostInsets = WindowInsets.safeDrawing.asPaddingValues(),
        onAuthenticated = onAuthenticated,
        biometricError = biometricResult is BiometricResult.AuthenticationError,
        onFingerprint = if (checking && vm.isFingerprintAuthEnabled.value) ({
            biometricPromptManager.showBiometricPrompt(
                title = activity.getString(R.string.login_in_the_debtbook),
                description = activity.getString(R.string.scan_your_fingerprint),
                authenticators = BIOMETRIC_STRONG,
                negativeButtonText = activity.getString(R.string.cancel),
            )
        }) else null,
        onRecoverPin = onRecoverPin.takeIf { checking },
        onExit = if (checking) activity::finishAndRemoveTask else null,
        onContact = {
            emailLauncher.launch(Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:pavlikbrichkin@yandex.ru".toUri()
                putExtra(Intent.EXTRA_SUBJECT, "${activity.getString(R.string.email_subject)} ${BuildConfig.VERSION_NAME}")
            })
        },
    )
}

@Composable
internal fun PinEntryScreen(
    vm: AuthorizationViewModel,
    hostInsets: PaddingValues,
    onAuthenticated: () -> Unit,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onFingerprint: (() -> Unit)? = null,
    onRecoverPin: (() -> Unit)? = null,
    onExit: (() -> Unit)? = null,
    onContact: (() -> Unit)? = null,
    biometricError: Boolean = false,
) {
    val context = LocalContext.current
    var saveFailed by remember { mutableStateOf(false) }
    fun savePIN(): Boolean {
        return try {
            PinCodeStore(context).save(vm.enteredPINCode.value)
            true
        } catch (_: Exception) {
            vm.resetPINCodes()
            vm.setPINCodeEnterState(FIRST)
            saveFailed = true
            false
        }
    }

    val pinCodeEnterState by vm.pinCodeEnterState.collectAsState()
    val enteredPINCode by vm.enteredPINCode.collectAsState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pinCodeEnterState) {
        if (pinCodeEnterState == INCORRECT) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }
    LaunchedEffect(enteredPINCode) {
        if (enteredPINCode.length == 4) {
            when (vm.pinCodeAction.value) {
                ENABLE -> {
                    if (pinCodeEnterState == FIRST) {
                        vm.setPastPINCode()
                        vm.setPINCodeEnterState(CONFIRMATION)
                    } else if (pinCodeEnterState == CONFIRMATION && vm.pastPINCode.value == enteredPINCode) {
                        if (savePIN()) {
                            vm.enablePINCode()
                            onAuthenticated()
                        }
                    } else {
                        vm.resetPINCodes()
                        vm.setPINCodeEnterState(INCORRECT)
                    }
                }
                DISABLE -> {
                    if (enteredPINCode == vm.currentPINCode.value) {
                        vm.turnOffPINCode()
                        onAuthenticated()
                    } else {
                        vm.resetPINCodes()
                        vm.setPINCodeEnterState(INCORRECT)
                    }
                }
                CHANGE -> {
                    if (pinCodeEnterState == FIRST && enteredPINCode == vm.currentPINCode.value) {
                        vm.setPastPINCode()
                        vm.setPINCodeEnterState(CONFIRMATION)
                    } else if (pinCodeEnterState == CONFIRMATION) {
                        if (savePIN()) onAuthenticated()
                    } else {
                        vm.resetPINCodes()
                        vm.setPINCodeEnterState(INCORRECT)
                    }
                }
                CHECK -> {
                    if (enteredPINCode == vm.currentPINCode.value) onAuthenticated()
                    else {
                        vm.resetPINCodes()
                        vm.setPINCodeEnterState(INCORRECT)
                    }
                }
            }
        }
    }

    val colors = bubbleColors()
    val topBar: (@Composable () -> Unit)? = if (title != null && onBack != null) {
        { BubbleTopBar(title, onBack, hostInsets) }
    } else null
    val prompt = when (pinCodeEnterState) {
        FIRST -> R.string.enter_pin
        CONFIRMATION -> if (vm.pinCodeAction.value == CHANGE) R.string.enter_new_pin else R.string.confirm_your_pin
        INCORRECT -> R.string.pin_code_is_incorrect
    }
    PinEntryLayout(
        hostInsets = hostInsets,
        prompt = prompt,
        pin = enteredPINCode,
        pinState = pinCodeEnterState,
        topBar = topBar,
        header = {
            if (onContact != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (onExit != null) {
                            TextButton(onClick = onExit) {
                                Text(stringResource(R.string.exit), color = colors.danger, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Image(
                        painterResource(R.drawable.app_icon),
                        contentDescription = stringResource(R.string.app_icon),
                        modifier = Modifier.size(48.dp),
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        BubbleIconButton(onContact) {
                            Icon(Icons.Outlined.MailOutline, stringResource(R.string.write_email_to_developer))
                        }
                    }
                }
            }
        },
        supportingContent = {
            if (vm.pinReadFailed.value || biometricError) {
                PinEntryError(stringResource(if (vm.pinReadFailed.value) R.string.pin_unavailable
                    else R.string.something_went_wrong_please_login_with_your_pin_code))
            }
            if (onRecoverPin != null) {
                TextButton(onClick = onRecoverPin) {
                    Text(stringResource(R.string.pin_recovery_title), color = colors.action, textAlign = TextAlign.Center)
                }
            }
        },
        onFingerprint = onFingerprint,
        onDigit = { digit ->
            vm.changeEnteredPINCode(enteredPINCode + digit)
            if (pinCodeEnterState == INCORRECT) vm.setPINCodeEnterState(FIRST)
        },
        onBackspace = { vm.changeEnteredPINCode(enteredPINCode.dropLast(1)) },
    )
    if (saveFailed) {
        AlertDialog(
            onDismissRequest = { saveFailed = false },
            title = { Text(stringResource(R.string.pin_save_failed_title)) },
            text = { Text(stringResource(R.string.pin_save_failed_message)) },
            confirmButton = {
                TextButton(onClick = { saveFailed = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@Composable
internal fun PINCodeSection(
    modifier: Modifier,
    pinCode: String,
    pinCodeEnterState: PINCodeEnterState,
) {
    val colors = bubbleColors()
    Row(
        modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(pinCode.length.toFloat(), 0f..4f, 3) },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(4) { index ->
            val filled = pinCode.length > index
            val color by animateColorAsState(
                when {
                    pinCodeEnterState == INCORRECT -> colors.danger
                    filled -> colors.action
                    else -> colors.muted
                },
                label = "PIN dot",
            )
            Box(Modifier.size(12.dp).then(
                if (filled) Modifier.background(color, CircleShape) else Modifier.border(1.5.dp, color, CircleShape)
            ))
        }
    }
}

@Composable
internal fun ButtonsSection(
    modifier: Modifier,
    onDigitButtonClick: (String) -> Unit,
    onBackspaceButtonClick: () -> Unit,
    onFingerprintButtonClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0..2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (column in 1..3) {
                    val digit = (row * 3 + column).toString()
                    PinKey(onClick = { onDigitButtonClick(digit) }, modifier = Modifier.weight(1f)) {
                        Text(digit, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onFingerprintButtonClick != null) {
                PinKey(onFingerprintButtonClick, Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.baseline_fingerprint_24),
                        stringResource(R.string.touch_to_show_fingerprint_scanner))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            PinKey({ onDigitButtonClick("0") }, Modifier.weight(1f)) {
                Text("0", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            PinKey(onBackspaceButtonClick, Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.baseline_backspace_24), stringResource(R.string.backspace))
            }
        }
    }
}

@Composable
private fun PinKey(onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = bubbleColors()
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.paper,
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) { content() }
    }
}
