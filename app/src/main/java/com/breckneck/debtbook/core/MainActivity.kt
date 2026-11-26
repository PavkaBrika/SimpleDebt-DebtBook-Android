package com.breckneck.debtbook.core

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.key
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.breckneck.debtbook.app.App
import com.breckneck.debtbook.app.wasUpdated
import kotlinx.coroutines.flow.first
import androidx.navigation.NavHostController
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.ActivityAuthGate
import com.breckneck.debtbook.auth.DebtSystemPicker
import com.breckneck.debtbook.core.navigation.DebtBookHost
import com.breckneck.debtbook.core.navigation.DebtPerson
import com.breckneck.debtbook.core.navigation.Debts
import com.breckneck.debtbook.core.navigation.Finances
import com.breckneck.debtbook.core.navigation.MainTab
import com.breckneck.debtbook.core.navigation.selectTab
import com.breckneck.debtbook.debt.reminders.DebtReminders
import com.breckneck.debtbook.settings.main.AppTheme
import com.breckneck.debtbook.settings.openSettingsIntent
import com.breckneck.debtbook.settings.rating.appReviewIntent
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import dagger.hilt.android.AndroidEntryPoint
import database.AppDataBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var interstitialAd: InterstitialAd? = null
    private var interstitialAdLoader: InterstitialAdLoader? = null
    private var vib: Vibrator? = null
    private val interstitialTAG = "INTERSTITIAL AD"

    private val vm by viewModels<MainViewModel>()
    private val authGate = ActivityAuthGate(this,
        pinEnabled = {
            vm.getIsPINCodeEnabled()
            vm.isPINCodeEnabled.value == true
        },
        lockDelayMillis = { vm.pinLockDelayMillis() },
    )
    @get:VisibleForTesting
    internal var navController: NavHostController? = null
        private set
    private var openingReminder = false
    private var handledReminder: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authGate.onCreate(savedInstanceState)
        handledReminder = savedInstanceState?.getString(HANDLED_REMINDER)
        enableBubbleEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splash ->
                splash.animate().alpha(0f).setDuration(150).withEndAction { splash.remove() }.start()
            }
        }
        vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val preferences = getPreferences(MODE_PRIVATE)
        val restoreNavigation = savedInstanceState?.getBoolean(FINANCES_ROOT_STATE) == true
        val fromLauncher = intent.action == ACTION_OPEN_FROM_LAUNCHER ||
            (savedInstanceState != null && intent.action == null)

        vm.debtQuantity.observe(this) { vm.showAppRating(fromSettings = false) }
        vm.getAppTheme()
        vm.appTheme.observe(this) { theme ->
            AppCompatDelegate.setDefaultNightMode(AppTheme.fromPreference(theme).nightMode)
        }

        if (!BuildConfig.DEBUG || BuildConfig.DEMO_ADS) {
            lifecycleScope.launch {
                (application as App).adsReady.first { it }
                if (vm.adsDisabled.value != true) {
                    interstitialAdLoader = InterstitialAdLoader(applicationContext)
                    loadInterstitialAd()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { openReminderWhenUnlocked() }
        }
        lifecycleScope.launch {
            val launchTab = if (!restoreNavigation && fromLauncher) {
                val lastTab = preferences.getString(LAST_TAB, null)
                when {
                    lastTab != null -> MainTab.entries.firstOrNull { it.persisted && it.key == lastTab }
                        ?: MainTab.FINANCES
                    wasUpdated() -> hasExistingDebtBook()?.let { hasPeople ->
                        if (hasPeople) MainTab.DEBTS else MainTab.FINANCES
                    }
                    else -> MainTab.FINANCES
                }
            } else null
            setContent {
                // A saved controller from the previous graph can still have Debts as its root.
                key(Finances) { DebtBookHost(this@MainActivity, vm, launchTab, preferences) }
            }
        }
    }

    private suspend fun hasExistingDebtBook(): Boolean? = withContext(Dispatchers.IO) {
        try {
            AppDataBase(this@MainActivity).appDao().hasDebtPeople()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("MainActivity", "Unable to read the existing debt book at launch", error)
            null
        }
    }

    internal fun onGraphReady(controller: NavHostController) {
        navController = controller
        openReminderWhenUnlocked()
    }

    override fun onStart() {
        super.onStart()
        authGate.onStart()
    }

    override fun onResume() {
        super.onResume()
        DebtReminders.get(this).refresh()
    }

    override fun onPostResume() {
        super.onPostResume()
        authGate.onPostResume()
    }

    internal fun canAccessApp() = authGate.canAccess()

    internal fun prepareDebtPickerIntent(picker: DebtSystemPicker, intent: Intent): Intent {
        val handler = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        if (handler != null && handler.exported && handler.packageName != "android" &&
            handler.applicationInfo.flags and systemFlags != 0
        ) {
            authGate.beginSystemPicker(picker)
            return intent.setComponent(ComponentName(handler.packageName, handler.name))
        }
        return intent
    }

    internal fun completeDebtPicker(picker: DebtSystemPicker, selectionRead: Boolean) =
        authGate.completeSystemPicker(picker, selectionRead)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        authGate.cancelSystemPicker()
        setIntent(intent)
        openReminderWhenUnlocked()
    }

    private fun openReminderWhenUnlocked() {
        val nav = navController ?: return
        if (openingReminder || intent.action != DebtReminders.ACTION_OPEN ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !canAccessApp()) return

        val request = intent
        val token = request.getStringExtra(REMINDER_REQUEST)
        if (token != null && token == handledReminder) return
        val id = request.getIntExtra(DebtReminders.EXTRA_DEBT, -1)
        openingReminder = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val dao = AppDataBase(this@MainActivity).appDao()
                    val row = dao.getAllDebts().firstOrNull { it.id == id && it.repaymentOfId == null }
                    row?.let { dao.getHuman(it.idHuman) }
                }
                if (intent !== request || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                    !canAccessApp()) return@launch
                handledReminder = token
                request.action = null
                request.removeExtra(DebtReminders.EXTRA_DEBT)
                if (result == null) {
                    Toast.makeText(this@MainActivity, R.string.due_missing, Toast.LENGTH_SHORT).show()
                } else {
                    nav.popBackStack<Debts>(inclusive = false)
                    nav.selectTab(MainTab.DEBTS)
                    nav.navigate(DebtPerson(result.id, result.currency, result.name, openDebtId = id))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, R.string.repayment_save_failed, Toast.LENGTH_SHORT).show()
            } finally {
                openingReminder = false
                if (intent !== request) openReminderWhenUnlocked()
            }
        }
    }

    override fun onStop() {
        authGate.onStop()
        super.onStop()
    }

    internal fun maybeShowInterstitial() {
        if (vm.adsDisabled.value == true || interstitialAd == null ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !canAccessApp() || vm.appRating != null) return
        if (adPolicy.tryStart(SystemClock.elapsedRealtime())) showInterstitialAd()
    }

    private fun loadInterstitialAd() {
        val request = AdRequest.Builder(if (BuildConfig.DEMO_ADS) "demo-interstitial-yandex" else "R-M-1753297-2").build()
        interstitialAdLoader?.loadAd(request, object : InterstitialAdLoadListener {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.i(interstitialTAG, "Interstitial ad load success")
                this@MainActivity.interstitialAd = interstitialAd
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                Log.e(interstitialTAG, "Interstitial ad load failed: ${error.description}")
            }
        })
    }

    private fun showInterstitialAd() {
        interstitialAd?.apply {
            setAdEventListener(object : InterstitialAdEventListener {
                override fun onAdShown() {
                    Log.e(interstitialTAG, "Interstitial ad shown")
                    vm.onAdShow()
                }

                override fun onAdFailedToShow(adError: AdError) {
                    Log.e(interstitialTAG, "Interstitial ad failed to show: ${adError.description}")
                    adPolicy.finish()
                    destroyInterstitialAd()
                    loadInterstitialAd()
                }

                override fun onAdDismissed() {
                    Log.e(interstitialTAG, "Interstitial ad dismissed")
                    adPolicy.finish()
                    destroyInterstitialAd()
                    loadInterstitialAd()
                }

                override fun onAdClicked() {
                    Log.e(interstitialTAG, "Interstitial ad clicked")
                }

                override fun onAdImpression(impressionData: ImpressionData?) {
                    Log.e(interstitialTAG, "Interstitial ad impression")
                }
            })
            show(this@MainActivity)
        }
    }

    override fun onDestroy() {
        authGate.cancelSystemPicker()
        interstitialAdLoader?.cancelLoading()
        interstitialAdLoader = null
        destroyInterstitialAd()
        adPolicy.finish()
        super.onDestroy()
    }

    internal fun startTickVibration() {
        try {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && (vib != null)) {
                vib!!.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.EFFECT_TICK))
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    internal fun startClickVibration() {
        try {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && (vib != null)) {
                vib!!.vibrate(
                    VibrationEffect.createOneShot(
                        100,
                        VibrationEffect.EFFECT_DOUBLE_CLICK
                    )
                )
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    private fun destroyInterstitialAd() {
        interstitialAd?.setAdEventListener(null)
        interstitialAd = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        authGate.onSaveInstanceState(outState)
        outState.putString(HANDLED_REMINDER, handledReminder)
        outState.putBoolean(FINANCES_ROOT_STATE, navController != null)
    }

    internal fun onFilterButtonLongClick() {
        if (BuildConfig.DEBUG && !BuildConfig.DEMO_ADS) return
        vm.toggleAdsDisabled()
        Toast.makeText(this, if (vm.adsDisabled.value == true) "✓" else "✗", Toast.LENGTH_SHORT).show()
    }

    internal fun continueAppRating() {
        val rating = vm.appRating ?: return
        when {
            rating.writingReview -> {
                if (openSettingsIntent(appReviewIntent(this, rating.rating, rating.reviewText))) {
                    vm.closeAppRating()
                }
            }
            rating.rating in 1..3 -> vm.showAppReview()
            rating.rating in 4..5 -> {
                launchMarketIntent()
                vm.closeAppRating()
            }
        }
    }

    private fun launchMarketIntent() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
        } catch (_: ActivityNotFoundException) {
            openSettingsIntent(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
        }
    }

    companion object {
        internal const val ACTION_OPEN_FROM_LAUNCHER = "com.breckneck.debtbook.action.OPEN_FROM_LAUNCHER"
        internal const val LAST_TAB = "last_tab"
        internal const val REMINDER_REQUEST = "reminderRequest"
        private const val HANDLED_REMINDER = "handledReminder"
        private const val FINANCES_ROOT_STATE = "financesRootState"
        private val adPolicy = InterstitialPolicy(SystemClock.elapsedRealtime())
    }
}
