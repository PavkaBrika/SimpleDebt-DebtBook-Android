package com.breckneck.debtbook.core.navigation

import android.content.Intent
import android.os.Build
import android.provider.Settings as DeviceSettings
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.AuthorizationViewModel
import com.breckneck.debtbook.auth.allowedPINAction
import com.breckneck.debtbook.auth.screen.PinEntryScreen
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.core.currency.CurrencyPickerScreen
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.main.AppTheme
import com.breckneck.debtbook.settings.main.AppearanceScreen
import com.breckneck.debtbook.settings.main.SettingsScreen
import com.breckneck.debtbook.settings.main.SettingsViewModel
import com.breckneck.debtbook.settings.openSettingsIntent
import com.breckneck.debtbook.settings.security.AppSecurityScreen
import com.breckneck.debtbook.settings.synchronization.BackupRoute
import com.breckneck.deptbook.domain.util.PINCodeAction

internal fun NavGraphBuilder.settingsDestinations(
    nav: NavHostController,
    insets: @Composable () -> PaddingValues,
    activity: MainActivity,
    main: MainViewModel,
) {
    destination<Settings>(nav) {
        val vm = hiltViewModel<SettingsViewModel>(entry)
        WhileResumed {
            vm.refreshDefaultCurrency()
            main.getIsPINCodeEnabled()
        }
        Surface(color = bubbleColors().background, contentColor = bubbleColors().ink) {
            SettingsScreen(
                vm, insets(), main,
                onBackup = { open(Backup) },
                onRate = { if (isCurrent) main.showAppRating(fromSettings = true) },
                onAppSecurity = { open(AppSecurity) },
                onDefaultCurrency = { open(DefaultCurrency) },
                onAppearance = { open(Appearance) },
                onExternal = { action -> if (isCurrent) activity.openSettingsLink(action) },
            )
        }
    }

    destination<DefaultCurrency>(nav) {
        val vm = hiltViewModel<SettingsViewModel>(entry)
        val currency by vm.defaultCurrency.observeAsState("")
        CurrencyPickerScreen(
            title = stringResource(R.string.default_currency),
            selectedCode = currency,
            hostInsets = insets(),
            onBack = ::back,
            onCurrencySelected = { code ->
                if (isCurrent) {
                    vm.setDefaultCurrency(code)
                    activity.startTickVibration()
                    back()
                }
            },
        )
    }

    destination<Appearance>(nav) {
        val vm = hiltViewModel<SettingsViewModel>(entry)
        val theme by vm.appTheme.observeAsState()
        AppearanceScreen(AppTheme.fromPreference(theme), insets(), ::back) { selection ->
            if (isCurrent) {
                activity.startTickVibration()
                vm.setAppTheme(selection.name)
                main.getAppTheme()
            }
        }
    }

    destination<AppSecurity>(nav) {
        val vm = hiltViewModel<SettingsViewModel>(entry)
        var biometrics by remember { mutableIntStateOf(BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG)) }
        val prompt = remember(activity, vm) {
            BiometricPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (isCurrent && main.isPINCodeEnabled.value == true) vm.setIsFingerprintAuthEnabled(true)
                }
            })
        }
        WhileResumed {
            main.getIsPINCodeEnabled()
            vm.getIsFingerprintAuthEnabled()
            biometrics = BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG)
        }
        AppSecurityScreen(
            hostInsets = insets(),
            vm = vm,
            pinEnabled = main.isPINCodeEnabled,
            showBiometrics = biometrics == BiometricManager.BIOMETRIC_SUCCESS ||
                (Build.VERSION.SDK_INT >= 30 && biometrics == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
            onBack = ::back,
            onPin = { open(PinManagement(it.name)) },
            onBiometrics = {
                if (isCurrent) when {
                    vm.isFingerprintAuthEnabled.value == true -> vm.setIsFingerprintAuthEnabled(false)
                    biometrics == BiometricManager.BIOMETRIC_SUCCESS -> prompt.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle(activity.getString(R.string.unlock_with_fingerprint))
                            .setAllowedAuthenticators(BIOMETRIC_STRONG)
                            .setNegativeButtonText(activity.getString(R.string.cancel)).build()
                    )
                    Build.VERSION.SDK_INT >= 30 -> activity.openSettingsIntent(Intent(DeviceSettings.ACTION_BIOMETRIC_ENROLL)
                        .putExtra(DeviceSettings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG))
                }
            },
            onTick = activity::startTickVibration,
        )
    }

    destination<PinManagement>(nav) {
        val vm = hiltViewModel<AuthorizationViewModel>(entry)
        val action = remember(vm) {
            allowedPINAction(entry.toRoute<PinManagement>().action, vm.isPINCodeEnabled.value == true, trustedCaller = true)
                .also {
                    vm.setPINCodeAction(it)
                    if (it != PINCodeAction.CHECK) vm.loadStoredPIN(activity)
                }
        }
        DisposableEffect(activity) {
            activity.window.addFlags(FLAG_SECURE)
            onDispose { activity.window.clearFlags(FLAG_SECURE) }
        }
        if (action == PINCodeAction.CHECK) {
            WhileResumed { back() }
        } else {
            val title = when (action) {
                PINCodeAction.ENABLE -> R.string.enable_pin
                PINCodeAction.CHANGE -> R.string.change_pin_code
                PINCodeAction.DISABLE -> R.string.disable_pin
                PINCodeAction.CHECK -> R.string.change_pin_code
            }
            PinEntryScreen(vm, insets(), title = stringResource(title), onBack = ::back, onAuthenticated = {
                main.getIsPINCodeEnabled()
                back()
            })
        }
    }

    destination<Backup>(nav) {
        BackupRoute(hiltViewModel(entry), insets(), activity, ::back)
    }
}

private fun MainActivity.openSettingsLink(action: Int) {
    val intent = when (action) {
        R.string.write_email_to_developer -> Intent(Intent.ACTION_SENDTO, "mailto:pavlikbrichkin@yandex.ru".toUri())
            .putExtra(Intent.EXTRA_SUBJECT, "${getString(R.string.email_subject)} ${BuildConfig.VERSION_NAME}")
        R.string.support_us -> Intent(Intent.ACTION_VIEW, "https://pay.cloudtips.ru/p/5c867537".toUri())
        else -> Intent(Intent.ACTION_VIEW, getString(R.string.privacy_policy_url).toUri())
    }
    openSettingsIntent(intent)
}
