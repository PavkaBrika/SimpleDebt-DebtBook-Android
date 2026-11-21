package com.breckneck.debtbook.core


import android.content.ActivityNotFoundException
import androidx.core.net.toUri
import com.breckneck.debtbook.settings.openSettingsIntent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.view.doOnNextLayout
import android.graphics.Rect
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.navigation.ui.onNavDestinationSelected
import com.breckneck.debtbook.auth.ActivityAuthGate
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.updateLayoutParams
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.breckneck.debtbook.core.ui.components.BubbleBottomBar
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.deptbook.domain.util.PINCodeAction
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
import com.breckneck.debtbook.settings.main.SettingsFragment
import com.breckneck.debtbook.settings.synchronization.SynchronizationFragment
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.model.Goal
import com.breckneck.deptbook.domain.util.CLICKS_QUANTITY_FOR_AD_SHOW
import com.breckneck.deptbook.domain.util.DEBT_QUANTITY_FOR_LAST_SHOW_APP_RATE_DIALOG
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
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
        lockDelayMillis = { vm.pinLockDelayMillis() }
    )
    override var goalsContentInsets by mutableStateOf(PaddingValues(0.dp))
        private set
    private lateinit var tabMenu: Menu
    lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authGate.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

        if (vm.isAppRateDialogShow.value == true)
            showAppRateDialog(vm.isAppReviewDialogFromSettings.value!!)
        if (vm.isAppReviewDialogShow.value == true)
            showLowAppRateDialog(vm.isAppReviewDialogFromSettings.value!!)

        vm.debtQuantity.observe(this) {
            if (it >= vm.debtQuantityForAppRateDialogShow.value!! &&
                vm.debtQuantityForAppRateDialogShow.value!! <= DEBT_QUANTITY_FOR_LAST_SHOW_APP_RATE_DIALOG
            ) {
                showAppRateDialog(false)
            }
        }

        vm.getAppTheme()
        vm.appTheme.observe(this) { theme ->
            if (theme.equals(getString(R.string.dark_theme)))
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else if (theme.equals(getString(R.string.light_theme)))
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else if (theme.equals(getString(R.string.system_theme)))
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

//        YANDEX MOBILE ADVERTISEMENT
        MobileAds.setUserConsent(false)

        //BANNER AD
        val bannerAd: BannerAdView = findViewById(R.id.bannerAdView)

        if (vm.adsDisabled.value != true) {
            val adRequestBuild = AdRequest.Builder().build()

            var adWidthPixels = findViewById<ConstraintLayout>(R.id.rootLayout).width
            if (adWidthPixels == 0) {
                adWidthPixels = resources.displayMetrics.widthPixels
            }
            val adWidth = (adWidthPixels / resources.displayMetrics.density).roundToInt()

            bannerAd.apply {
                setAdUnitId("R-M-1753297-1")
//            setAdUnitId("R-M-DEMO-320x50")
                setAdSize(BannerAdSize.stickySize(applicationContext, adWidth))
                setBannerAdEventListener(object : BannerAdEventListener {
                    override fun onAdLoaded() {
                        Log.e(bannerTAG, "BANNER LOADED")
                        if (isDestroyed) {
                            bannerAd.destroy()
                            return
                        }
                    }

                    override fun onAdFailedToLoad(p0: AdRequestError) {
                        Log.e(bannerTAG, "BANNER LOAD FAILED")
                    }

                    override fun onAdClicked() {
                        Log.e(bannerTAG, "BANNER CLICKED")
                    }

                    override fun onLeftApplication() {
                        Log.e(bannerTAG, "BANNER LEFT")
                    }

                    override fun onReturnedToApplication() {
                        Log.e(bannerTAG, "BANNER RETURN")
                    }

                    override fun onImpression(p0: ImpressionData?) {
                        Log.e(bannerTAG, "BANNER IMPRESSION")
                    }
                })
                loadAd(adRequestBuild)
            }

            interstitialAdLoader = InterstitialAdLoader(applicationContext).apply {
                setAdLoadListener(object : InterstitialAdLoadListener {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        Log.e(interstitialTAG, "Interstitial ad load success")
                        this@MainActivity.interstitialAd = interstitialAd
                    }

                    override fun onAdFailedToLoad(p0: AdRequestError) {
                        Log.e(interstitialTAG, "Interstitial ad load failed")
                    }
                })
            }
            loadInterstitialAd()
        } else {
            bannerAd.visibility = View.GONE
        }

        //NAVIGATION

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.frameLayout) as NavHostFragment
        navController = navHostFragment.navController

        configureHostInsets()
        val bottomNavBar = findViewById<ComposeView>(R.id.bottomNavigationView)
        val selectedTab = mutableIntStateOf(0)
        // Reuse NavigationUI's tab options, including the saved back stack for each tab.
        tabMenu = PopupMenu(this, bottomNavBar).apply { inflate(R.menu.menu_bottom_nav) }.menu
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // The Compose lists draw their own tabs so its entire surface participates in fragment transitions.
            val showHostTabs = destination.id in TOP_LEVEL_DESTINATIONS && destination.id != R.id.goalsFragment && destination.id != R.id.settingsFragment && destination.id != R.id.financeFragment
            selectedTab.intValue = if (showHostTabs) destination.id else 0
            bottomNavBar.visibility = if (showHostTabs) View.VISIBLE else View.GONE
        }
        bottomNavBar.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        bottomNavBar.setContent {
            DebtBookTheme {
                BubbleBottomBar(
                    destination = selectedTab.intValue,
                    onDestinationSelected = ::onTabSelected,
                )
            }
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
        val host = findViewById<View>(R.id.frameLayout)
        val defaultContrast = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && window.isNavigationBarContrastEnforced
        var overlayGoals = false
        var safeBars = Insets.NONE

        fun updateGoalsInsets() {
            if (overlayGoals) {
                val density = resources.displayMetrics.density
                goalsContentInsets = PaddingValues.Absolute(
                    left = (safeBars.left / density).dp,
                    top = (safeBars.top / density).dp,
                    right = (safeBars.right / density).dp,
                    bottom = (dock.height / density).dp,
                )
            }
        }
        dock.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateGoalsInsets() }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(bars.bottom, ime.bottom)
            // Amount fields on the list and in details use a separate modal window.
            val goalForm = navController.currentDestination?.id == R.id.createGoalsFragment
            val drawBehindBars = overlayGoals && (!goalForm || ime.bottom <= bars.bottom)
            val revealInput = !overlayGoals && ime.bottom > bars.bottom && root.paddingBottom != ime.bottom
            safeBars = bars
            root.updatePadding(
                left = if (overlayGoals) 0 else bars.left, top = if (overlayGoals) 0 else bars.top,
                right = if (overlayGoals) 0 else bars.right, bottom = if (drawBehindBars) 0 else bottom,
            )
            dock.updatePadding(
                left = if (overlayGoals) bars.left else 0, right = if (overlayGoals) bars.right else 0,
                bottom = if (drawBehindBars) bars.bottom else 0,
            )
            updateGoalsInsets()
            if (revealInput) root.doOnNextLayout {
                (root.findFocus() as? EditText)?.let { input ->
                    input.requestRectangleOnScreen(Rect(0, 0, input.width, input.height), true)
                }
            }
            WindowInsetsCompat.CONSUMED
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val goals = when (destination.id) {
                R.id.goalsFragment, R.id.goalDetailsFragment, R.id.createGoalsFragment -> true
                else -> false
            }
            if (goals != overlayGoals) {
                overlayGoals = goals
                host.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomToTop = if (goals) ConstraintLayout.LayoutParams.UNSET else R.id.bottomDock
                    bottomToBottom = if (goals) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = !goals && defaultContrast
                }
            }
            ViewCompat.requestApplyInsets(root)
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
                    navController.navigate(R.id.debtDetailsFragment, Bundle().apply {
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
            vm.isAppRateDialogShow.value == true || vm.isAppReviewDialogShow.value == true) return
        if (adPolicy.tryStart(SystemClock.elapsedRealtime())) showInterstitialAd()
    }

    override fun onSupportNavigateUp(): Boolean {
        return super.onSupportNavigateUp()
    }

    private fun loadInterstitialAd() {
        val adRequestConfiguration = AdRequestConfiguration.Builder("R-M-1753297-2").build()
        interstitialAdLoader?.loadAd(adRequestConfiguration)
    }

    private fun showInterstitialAd() {
        interstitialAd?.apply {
            setAdEventListener(object : InterstitialAdEventListener {
                override fun onAdShown() {
                    Log.e(interstitialTAG, "Interstitial ad shown")
                    vm.onAdShow()
                }

                override fun onAdFailedToShow(p0: AdError) {
                    Log.e(interstitialTAG, "Interstitial ad failed to show")
                    adPolicy.finish()
                    destroyInterstitialAd()
                    loadInterstitialAd()
                }

                override fun onAdDismissed() {
                    Log.e(interstitialTAG, "Interstitial ad dismissed")
                    adPolicy.finish()
                    interstitialAd?.setAdEventListener(null)
                    interstitialAd = null
                    loadInterstitialAd()
                }

                override fun onAdClicked() {
                    Log.e(interstitialTAG, "Interstitial ad clicked")
                }

                override fun onAdImpression(p0: ImpressionData?) {
                    Log.e(interstitialTAG, "Interstitial ad impression")
                }
            })
            show(this@MainActivity)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        interstitialAdLoader?.setAdLoadListener(null)
        interstitialAdLoader = null
        destroyInterstitialAd()
        adPolicy.finish()
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
        args.putLong("dayInMillis", requireNotNull(com.breckneck.deptbook.domain.util.LedgerDate.localDate(day)).time)
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

    override fun onEditFinanceClick(finance: Finance) {
        val args = Bundle()
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
        showAppRateDialog(true)
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

    private fun showAppRateDialog(isFromSettings: Boolean) {
        val rateAppBottomSheetDialog = BottomSheetDialog(this)
        rateAppBottomSheetDialog.setContentView(R.layout.dialog_rate_app)
        rateAppBottomSheetDialog.setCancelable(false)
        rateAppBottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        vm.setAppReviewFromSettings(isFromSettings)
        vm.setAppRateDialogShown(shown = true)

        rateAppBottomSheetDialog.findViewById<Button>(R.id.confirmButton)!!.setOnClickListener {
            when (vm.appRate.value) {
                1, 2, 3 -> {
                    showLowAppRateDialog(fromSettings = isFromSettings)
                    rateAppBottomSheetDialog.dismiss()
                }

                4, 5 -> {
                    launchMarketIntent()
                    rateAppBottomSheetDialog.dismiss()
                }

                0 -> {
                    vm.setAppReviewFromSettings(false)
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.rate_app_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        rateAppBottomSheetDialog.findViewById<Button>(R.id.cancelButton)!!.setOnClickListener {
            rateAppBottomSheetDialog.dismiss()
        }

        rateAppBottomSheetDialog.setOnDismissListener {
            if (!isFromSettings)
                vm.onAppRateDismiss()
            vm.setAppRateDialogShown(shown = false)
            vm.setAppReviewFromSettings(false)
            vm.setAppRate(0)
        }

        val stars = listOf(R.id.rateStar1ImageView, R.id.rateStar2ImageView, R.id.rateStar3ImageView,
            R.id.rateStar4ImageView, R.id.rateStar5ImageView).map {
            requireNotNull(rateAppBottomSheetDialog.findViewById<ImageView>(it))
        }
        fun showRating(rating: Int) {
            stars.forEachIndexed { index, star ->
                star.isSelected = index + 1 == rating
                if (index < rating) star.setColorFilter(ContextCompat.getColor(this, R.color.yellow))
                else star.clearColorFilter()
            }
        }
        stars.forEachIndexed { index, star ->
            val rating = index + 1
            star.contentDescription = getString(R.string.settings_rating, rating)
            star.setOnClickListener {
                onTickVibration()
                vm.setAppRate(rating)
                showRating(rating)
            }
        }
        showRating(vm.appRate.value ?: 0)
        rateAppBottomSheetDialog.show()
    }

    private fun launchMarketIntent() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
        } catch (_: ActivityNotFoundException) {
            openSettingsIntent(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
        }
    }

    private fun showLowAppRateDialog(fromSettings: Boolean) {
        val lowRateBottomSheetDialog = BottomSheetDialog(this)
        lowRateBottomSheetDialog.setContentView(R.layout.dialog_low_app_rate)
        lowRateBottomSheetDialog.setCancelable(false)
        lowRateBottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        vm.setAppReviewDialogShown(true)
        val reviewEditText: TextInputEditText =
            lowRateBottomSheetDialog.findViewById(R.id.reviewEditText)!!
        val sendReviewButton: Button = lowRateBottomSheetDialog.findViewById(R.id.buttonOk)!!
        val cancelButton: Button = lowRateBottomSheetDialog.findViewById(R.id.buttonCancel)!!
        sendReviewButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:pavlikbrichkin@yandex.ru")
            intent.putExtra(
                Intent.EXTRA_SUBJECT,
                "${getString(R.string.email_subject)} ${BuildConfig.VERSION_NAME}"
            )
            intent.putExtra(Intent.EXTRA_TEXT, reviewEditText.text.toString())
            if (openSettingsIntent(intent)) lowRateBottomSheetDialog.dismiss()
        }

        if (vm.appReviewText.value?.isEmpty() == false) {
            reviewEditText.setText(vm.appReviewText.value)
        }

        reviewEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun afterTextChanged(p0: Editable?) {
                vm.setAppReviewText(text = p0.toString())
            }
        })

        cancelButton.setOnClickListener {
            lowRateBottomSheetDialog.dismiss()
        }

        lowRateBottomSheetDialog.setOnDismissListener {
            if (!fromSettings)
                vm.onAppRateDismiss()
            vm.setAppReviewFromSettings(false)
            vm.setAppReviewDialogShown(shown = false)
            vm.setAppReviewText(text = "")
        }

        lowRateBottomSheetDialog.show()
    }

    companion object {
        private val adPolicy = InterstitialPolicy(SystemClock.elapsedRealtime())
        private val TOP_LEVEL_DESTINATIONS = setOf(R.id.mainFragment, R.id.financeFragment,
            R.id.goalsFragment, R.id.settingsFragment)
    }
}
