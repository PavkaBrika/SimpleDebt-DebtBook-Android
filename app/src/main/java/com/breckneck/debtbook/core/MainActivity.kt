package com.breckneck.debtbook.core

import android.content.ActivityNotFoundException
import androidx.core.net.toUri
import com.breckneck.debtbook.settings.openSettingsIntent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.breckneck.debtbook.debt.reminders.DebtReminders
import com.breckneck.debtbook.auth.AuthSession
import database.AppDataBase
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.navigation.ui.onNavDestinationSelected
import com.breckneck.debtbook.auth.ActivityAuthGate
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.ViewStub
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.graphics.Insets
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.debt.details.DebtDetailsFragment
import com.breckneck.debtbook.debt.main.DebtFragment
import com.breckneck.debtbook.debt.create.CreateDebtFragment
import com.breckneck.debtbook.finance.create_category.CreateFinanceCategoryFragment
import com.breckneck.debtbook.finance.create.CreateFinanceFragment
import com.breckneck.debtbook.finance.details.FinanceDetailsFragment
import com.breckneck.debtbook.finance.main.FinanceFragment
import com.breckneck.debtbook.goal.create.CreateGoalsFragment
import com.breckneck.debtbook.goal.details.GoalDetailsFragment
import com.breckneck.debtbook.goal.main.GoalsFragment
import com.breckneck.debtbook.settings.main.AppTheme
import com.breckneck.debtbook.settings.main.SettingsFragment
import com.breckneck.debtbook.settings.rating.AppRatingSheet
import com.breckneck.debtbook.settings.rating.appReviewIntent
import com.breckneck.debtbook.settings.synchronization.SynchronizationFragment
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.model.Goal
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.*
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), DebtFragment.OnButtonClickListener,
    CreateDebtFragment.OnButtonClickListener, DebtDetailsFragment.OnButtonClickListener,
    SettingsFragment.OnButtonClickListener, SynchronizationFragment.SynchronizationInterface,
    FinanceFragment.OnButtonClickListener, CreateFinanceFragment.OnClickListener,
    CreateFinanceCategoryFragment.OnClickListener, FinanceDetailsFragment.OnClickListener,
    GoalsFragment.OnClickListener, CreateGoalsFragment.OnButtonClickListener, GoalDetailsFragment.OnClickListener {

    private var bannerAd: BannerAdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var interstitialAdLoader: InterstitialAdLoader? = null
    private var vib: Vibrator? = null

    private val TAG = "MainActivity"
    private val bannerTAG = "BANNER AD"
    private val interstitialTAG = "INTERSTITIAL AD"

    private val vm by viewModels<MainViewModel>()
    private val authGate = ActivityAuthGate(this,
        pinEnabled = {
            vm.getIsPINCodeEnabled()
            vm.isPINCodeEnabled.value == true
        },
        lockDelayMillis = { vm.pinLockDelayMillis() },
    )
    internal var screenInsets by mutableStateOf(PaddingValues(0.dp))
        private set
    private lateinit var tabMenu: Menu
    lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authGate.onCreate(savedInstanceState)
        enableBubbleEdgeToEdge()
//        val config = resources.configuration
//        val lang = "fr" // your language code
//        val locale = Locale(lang)
//        Locale.setDefault(locale)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
//            config.setLocale(locale)
//        else
//            config.locale = locale
        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vib = vibratorManager.defaultVibrator
        } else {
            vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        configureAppRating()
        vm.debtQuantity.observe(this) { vm.showAppRating(fromSettings = false) }

        vm.getAppTheme()
        vm.appTheme.observe(this) { theme ->
            AppCompatDelegate.setDefaultNightMode(AppTheme.fromPreference(theme).nightMode)
        }

        if (!BuildConfig.DEBUG && vm.adsDisabled.value != true) {
            var adWidthPixels = findViewById<ConstraintLayout>(R.id.rootLayout).width
            if (adWidthPixels == 0) {
                adWidthPixels = resources.displayMetrics.widthPixels
            }
            val adWidth = (adWidthPixels / resources.displayMetrics.density).roundToInt()

            val bannerSlot = findViewById<ViewStub>(R.id.bannerAdSlot)
            bannerAd = (bannerSlot.inflate() as BannerAdView).apply {
                setAdSize(BannerAdSize.sticky(this@MainActivity, adWidth))
                setBannerAdEventListener(object : BannerAdEventListener {
                    override fun onAdLoaded() {
                        Log.e(bannerTAG, "BANNER LOADED")
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        Log.e(bannerTAG, "Banner load failed: ${error.description}")
                    }

                    override fun onAdClicked() {
                        Log.e(bannerTAG, "BANNER CLICKED")
                    }

                    override fun onImpression(impressionData: ImpressionData?) {
                        Log.e(bannerTAG, "BANNER IMPRESSION")
                    }
                })
                loadAd(AdRequest.Builder("R-M-1753297-1").build())
            }

            interstitialAdLoader = InterstitialAdLoader(applicationContext)
            loadInterstitialAd()
        }

        //NAVIGATION

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment
        navController = navHostFragment.navController

        configureHostInsets()
        // NavigationUI keeps each tab's saved back stack. The destination draws its own bar.
        tabMenu = PopupMenu(this, findViewById<View>(R.id.bottomDock)).apply { inflate(R.menu.menu_bottom_nav) }.menu

        val navigationPreferences = getPreferences(MODE_PRIVATE)
        if (savedInstanceState == null && intent.action == ACTION_OPEN_FROM_LAUNCHER) {
            val savedTab = navigationPreferences.getString(LAST_TAB, null)
            val tabId = RESTORABLE_TABS.entries.firstOrNull { it.value == savedTab }?.key
            if (tabId != null && tabId != navController.currentDestination?.id) {
                tabMenu.findItem(tabId)?.onNavDestinationSelected(navController)
            }
        }
        var previousDestinationId = navController.currentDestination?.id
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == previousDestinationId) return@addOnDestinationChangedListener
            previousDestinationId = destination.id
            val tab = RESTORABLE_TABS[destination.id] ?: return@addOnDestinationChangedListener
            navigationPreferences.edit { putString(LAST_TAB, tab) }
        }
    }

    override fun onTabSelected(id: Int) {
        val from = navController.currentDestination?.id
        if (from in TOP_LEVEL_DESTINATIONS && from != id &&
            tabMenu.findItem(id)?.onNavDestinationSelected(navController) == true
        ) {
            maybeShowInterstitial()
        }
    }

    private fun configureHostInsets() {
        val root = findViewById<View>(R.id.rootLayout)
        val dock = findViewById<View>(R.id.bottomDock)
        var safeBars = Insets.NONE

        fun updateScreenInsets() {
            val density = resources.displayMetrics.density
            screenInsets = PaddingValues.Absolute(
                left = (safeBars.left / density).dp,
                top = (safeBars.top / density).dp,
                right = (safeBars.right / density).dp,
                bottom = (dock.height / density).dp,
            )
        }
        dock.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateScreenInsets() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            safeBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val keyboardOpen = ime > safeBars.bottom
            dock.translationY = if (keyboardOpen) -ime.toFloat() else 0f
            dock.updatePadding(left = safeBars.left, right = safeBars.right, bottom = if (keyboardOpen) 0 else safeBars.bottom)
            updateScreenInsets()
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onStart() {
        super.onStart()
        authGate.onStart()
    }

    override fun onResume() {
        super.onResume()
        DebtReminders.get(this).refresh()
        openReminderWhenUnlocked()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) openReminderWhenUnlocked()
    }

    private var openingReminder = false
    private fun openReminderWhenUnlocked() {
        if (openingReminder || intent.action != DebtReminders.ACTION_OPEN) return
        vm.getIsPINCodeEnabled()
        if (!AuthSession.canAccess(vm.isPINCodeEnabled.value == true, SystemClock.elapsedRealtime(), vm.pinLockDelayMillis())) return
        val requestedIntent = intent
        val id = requestedIntent.getIntExtra(DebtReminders.EXTRA_DEBT, -1)
        openingReminder = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val dao = AppDataBase(this@MainActivity).appDao()
                    val row = dao.getAllDebts().firstOrNull { it.id == id && it.repaymentOfId == null }
                    row?.let { dao.getHuman(it.idHuman) }
                }
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                if (!AuthSession.canAccess(vm.isPINCodeEnabled.value == true, SystemClock.elapsedRealtime(), vm.pinLockDelayMillis())) return@launch
                requestedIntent.action = null
                requestedIntent.removeExtra(DebtReminders.EXTRA_DEBT)
                if (result == null) Toast.makeText(this@MainActivity, R.string.due_missing, Toast.LENGTH_SHORT).show()
                else {
                    navController.popBackStack(R.id.mainFragment, false)
                    navController.navigate(R.id.action_mainFragment_to_debtDetailsFragment, Bundle().apply {
                        putInt("idHuman", result.id); putString("currency", result.currency)
                        putString("name", result.name); putInt("openDebtId", id)
                    })
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(this@MainActivity, R.string.repayment_save_failed, Toast.LENGTH_SHORT).show() }
            finally {
                openingReminder = false
                if (intent !== requestedIntent && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) openReminderWhenUnlocked()
            }
        }
    }

    override fun onStop() {
        authGate.onStop()
        super.onStop()
    }

    private fun maybeShowInterstitial() {
        if (vm.adsDisabled.value == true || interstitialAd == null ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            navController.currentDestination?.id !in TOP_LEVEL_DESTINATIONS ||
            vm.appRating != null) return
        if (adPolicy.tryStart(SystemClock.elapsedRealtime())) showInterstitialAd()
    }

    override fun onSupportNavigateUp(): Boolean {
        return super.onSupportNavigateUp()
    }

    private fun loadInterstitialAd() {
        val request = AdRequest.Builder("R-M-1753297-2").build()
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
        bannerAd?.destroy()
        interstitialAdLoader?.cancelLoading()
        interstitialAdLoader = null
        destroyInterstitialAd()
        adPolicy.finish()
        super.onDestroy()
    }

    private fun startTickVibration() {
        try {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && (vib != null)) {
                vib!!.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.EFFECT_TICK))
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    private fun startClickVibration() {
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
    }

    //MainFragment interfaces
    override fun onHumanClick(idHuman: Int, currency: String, name: String) {
        val args = Bundle()
        args.putInt("idHuman", idHuman)
        args.putString("currency", currency)
        args.putString("name", name)
        navController.navigate(R.id.action_mainFragment_to_debtDetailsFragment, args)
        vm.onActionClick()
    }

    override fun onAddButtonClick() {
        navController.navigate(R.id.action_mainFragment_to_newDebtFragment)
        vm.onActionClick()
    }

    override fun onFilterButtonLongClick() {
        if (BuildConfig.DEBUG) return
        vm.toggleAdsDisabled()
        val isNowDisabled = vm.adsDisabled.value == true
        Toast.makeText(this, if (isNowDisabled) "✓" else "✗", Toast.LENGTH_SHORT).show()
    }

    override fun onAddDebt() {
        runOnUiThread {
            vm.onAddDebt()
        }
    }

    //NewDebtFragment interfaces
    override fun onSetButtonClick() {
        startTickVibration()
    }

    override fun DebtDetailsNewHuman(idHuman: Int, currency: String, name: String) {
        val args = Bundle()
        args.putInt("idHuman", idHuman)
        args.putString("currency", currency)
        args.putString("name", name)
        navController.navigate(R.id.action_newDebtFragment_to_debtDetailsFragment, args)
        vm.onActionClick()
    }

    override fun DebtDetailsExistHuman(idHuman: Int, currency: String, name: String) {
        val args = Bundle()
        args.putInt("idHuman", idHuman)
        args.putString("currency", currency)
        args.putString("name", name)
        navController.navigate(R.id.action_newDebtFragment_to_debtDetailsFragment, args)
        vm.onActionClick()
    }

    //DebtDetailsFragment interfaces
    override fun onTickVibration() {
        startTickVibration()
    }

    override fun addNewDebtFragment(idHuman: Int, currency: String, name: String) {
        val args = Bundle()
        args.putInt("idHuman", idHuman)
        args.putString("currency", currency)
        args.putString("name", name)
        navController.navigate(R.id.action_debtDetailsFragment_to_newDebtFragment, args)
        vm.onActionClick()
    }

    override fun editDebt(debtDomain: DebtDomain, currency: String, name: String, hasRepayments: Boolean, canRemind: Boolean) {
        val args = Bundle()
        args.putInt("idDebt", debtDomain.id)
        args.putInt("idHuman", debtDomain.idHuman)
        args.putString("sum", debtDomain.sum.toPlainString())
        args.putString("date", debtDomain.date)
        args.putString("dueDayOnEdit", debtDomain.dueDay)
        args.putBoolean("hasRepayments", hasRepayments)
        args.putBoolean("canRemind", canRemind)
        val pending = canRemind && debtDomain.reminderMinute != null && !debtDomain.reminderDelivered &&
            debtDomain.dueDay?.let { DebtReminderPolicy.canSchedule(it) } == true
        args.putBoolean("remindOnEdit", pending)
        debtDomain.reminderMinute?.let { args.putInt("minuteOnEdit", it) }
        if (debtDomain.info == null)
            args.putString("info", "")
        else
            args.putString("info", debtDomain.info.toString())
        args.putString("name", name)
        args.putString("currency", currency)
        navController.navigate(R.id.action_debtDetailsFragment_to_newDebtFragment, args)
        vm.onActionClick()
    }

    override fun deleteHuman() {
        startClickVibration()
        navController.popBackStack()
        vm.onActionClick()
    }

    //finance interface
    override fun onAddFinanceButtonClick(financeCategoryState: FinanceCategoryState, day: String, currency: String) {
        val args = Bundle()
        args.putString("categoryState", financeCategoryState.toString())
        args.putString("day", day)
        args.putString("currency", currency)
        args.putBoolean("isEditFinance", false)
        navController.navigate(R.id.action_financeFragment_to_createFinanceFragment, args)
        vm.onActionClick()
    }

    override fun onAddCategoryButtonClick(financeCategoryState: FinanceCategoryState) {
        val args = Bundle()
        args.putString("categoryState", financeCategoryState.toString())
        navController.navigate(R.id.action_createFinanceFragment_to_createFinanceCategoryFragment, args)
        vm.onActionClick()
    }

    override fun onFinanceCategoryClick(
        categoryName: String,
        categoryId: Int,
        financeCategoryState: FinanceCategoryState,
        currency: String,
        day: String
    ) {
        val args = Bundle()
        args.putString("day", day)
        args.putString("categoryName", categoryName)
        args.putInt("categoryId", categoryId)
        args.putString("categoryState", financeCategoryState.name)
        args.putString("currency", currency)
        navController.navigate(R.id.action_financeFragment_to_financeDetailsFragment, args)
        vm.onActionClick()
    }

    //finance details interface

    override fun onAddCategoryFinance(categoryId: Int, currency: String, day: String, direction: FinanceCategoryState) {
        val args = Bundle().apply {
            putInt("categoryId", categoryId)
            putString("currency", currency)
            putString("day", day)
            putString("categoryState", direction.name)
        }
        navController.navigate(R.id.action_financeDetailsFragment_to_createFinanceFragment, args)
        vm.onActionClick()
    }

    override fun onEditFinanceClick(finance: Finance, direction: FinanceCategoryState) {
        val args = Bundle()
        args.putString("categoryState", direction.name)
        args.putSerializable("financeEdit", finance)
        args.putBoolean("isEditFinance", true)
        navController.navigate(R.id.action_financeDetailsFragment_to_createFinanceFragment, args)
        vm.onActionClick()
    }

    //synchronization interface
    override fun onBackButtonClick() {
        navController.popBackStack()
    }

    //settings interface
    override fun onRateAppButtonClick() {
        vm.showAppRating(fromSettings = true)
    }

    override fun onAuthorizationButtonClick() {
        if (navController.currentDestination?.id != R.id.settingsFragment) return
        navController.navigate(R.id.action_settingsFragment_to_synchronizationFragment)
    }

    //goals interface

    override fun onAddGoalButtonClick() {
        if (navController.currentDestination?.id != R.id.goalsFragment) return
        navController.navigate(R.id.action_goalsFragment_to_createGoalsFragment)
        vm.onActionClick()
    }

    override fun onGoalClick(goal: Goal) {
        if (navController.currentDestination?.id != R.id.goalsFragment) return
        val args = Bundle()
        args.putSerializable("goal", goal)
        navController.navigate(R.id.action_goalsFragment_to_goalDetailsFragment, args)
        vm.onActionClick()
    }

    override fun onEditGoalButtonClick(goal: Goal) {
        val action = when (navController.currentDestination?.id) {
            R.id.goalsFragment -> R.id.action_goalsFragment_to_createGoalsFragment
            R.id.goalDetailsFragment -> R.id.action_goalDetailsFragment_to_createGoalsFragment
            else -> return
        }
        val args = Bundle().apply {
            putSerializable("goal", goal)
            putBoolean("isEditGoal", true)
            putBoolean("returnToGoalDetails", action == R.id.action_goalDetailsFragment_to_createGoalsFragment)
        }
        navController.navigate(action, args)
        vm.onActionClick()
    }

    private fun configureAppRating() {
        findViewById<ComposeView>(R.id.appRatingHost).setContent {
            val lifecycleState by lifecycle.currentStateAsState()
            val rating = vm.appRating
            if (rating != null && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
                AuthSession.canAccess(vm.isPINCodeEnabled.value == true, SystemClock.elapsedRealtime(), vm.pinLockDelayMillis())
            ) {
                DebtBookTheme {
                    AppRatingSheet(
                        state = rating,
                        onSelect = {
                            onTickVibration()
                            vm.selectAppRating(it)
                        },
                        onReviewChange = vm::updateAppReview,
                        onContinue = ::continueAppRating,
                        onBackToRating = vm::returnToAppRating,
                        onDismiss = vm::closeAppRating,
                    )
                }
            }
        }
    }

    private fun continueAppRating() {
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
        private val adPolicy = InterstitialPolicy(SystemClock.elapsedRealtime())
        private const val LAST_TAB = "last_tab"
        private val RESTORABLE_TABS = mapOf(
            R.id.mainFragment to "debts",
            R.id.financeFragment to "finances",
            R.id.goalsFragment to "goals",
        )
        private val TOP_LEVEL_DESTINATIONS = RESTORABLE_TABS.keys + R.id.settingsFragment
    }
}
