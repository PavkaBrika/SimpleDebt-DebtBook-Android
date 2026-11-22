package com.breckneck.debtbook.core


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
import android.view.View
import android.view.ViewStub
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
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
import com.google.android.material.bottomnavigation.BottomNavigationView
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
        lockDelayMillis = { vm.pinLockDelayMillis() }
    )
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
        // One inset owner for the mixed XML/Compose NavHost. Consume here so the
        // legacy fitsSystemWindows children do not apply the bars a second time.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val revealInput = ime.bottom > bars.bottom && root.paddingBottom != ime.bottom
            root.updatePadding(left = bars.left, top = bars.top, right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom))
            if (revealInput) root.doOnNextLayout {
                // Legacy scrolling forms must reveal the focused field after the
                // inset changes their viewport, including with a large system font.
                (root.findFocus() as? EditText)?.let { input ->
                    input.requestRectangleOnScreen(Rect(0, 0, input.width, input.height), true)
                }
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(findViewById(R.id.rootLayout))

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

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.mainFragment -> vm.setIsBottomNavBarVisible(isVisible = true)
                R.id.settingsFragment -> vm.setIsBottomNavBarVisible(isVisible = true)
                R.id.financeFragment -> vm.setIsBottomNavBarVisible(isVisible = true)
                R.id.goalsFragment -> vm.setIsBottomNavBarVisible(isVisible = true)
                else -> vm.setIsBottomNavBarVisible(isVisible = false)
            }
        }

        val bottomNavBar = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavBar.setupWithNavController(navController)

        bottomNavBar.setOnItemSelectedListener { item ->
            val from = navController.currentDestination?.id
            val navigated = item.onNavDestinationSelected(navController)
            if (navigated && from != item.itemId && from in TOP_LEVEL_DESTINATIONS) {
                maybeShowInterstitial()
            }
            navigated
        }
        vm.isBottomNavViewVisible.observe(this) { visible ->
            bottomNavBar.clearAnimation()
            bottomNavBar.translationY = 0f
            bottomNavBar.visibility = if (visible) View.VISIBLE else View.GONE
        }
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
    override fun onAddFinanceButtonClick(financeCategoryState: FinanceCategoryState, dayInMillis: Long) {
        val args = Bundle()
        args.putString("categoryState", financeCategoryState.toString())
        args.putLong("dayInMillis", dayInMillis)
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
        currency: String
    ) {
        val args = Bundle()
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
        navController.navigate(R.id.action_settingsFragment_to_synchronizationFragment)
    }

    //goals interface

    override fun onAddGoalButtonClick() {
        navController.navigate(R.id.action_goalsFragment_to_createGoalsFragment)
        vm.onActionClick()
    }

    override fun onGoalClick(goal: Goal) {
        val args = Bundle()
        args.putSerializable("goal", goal)
        navController.navigate(R.id.action_goalsFragment_to_goalDetailsFragment, args)
        vm.onActionClick()
    }

    //goal details interface

    override fun onEditGoalButtonClick(goal: Goal) {
        val args = Bundle()
        args.putSerializable("goal", goal)
        args.putBoolean("isEditGoal", true)
        navController.navigate(R.id.action_goalDetailsFragment_to_createGoalsFragment, args)
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

        val rateStar1ImageView: ImageView =
            rateAppBottomSheetDialog.findViewById(R.id.rateStar1ImageView)!!
        val rateStar2ImageView: ImageView =
            rateAppBottomSheetDialog.findViewById(R.id.rateStar2ImageView)!!
        val rateStar3ImageView: ImageView =
            rateAppBottomSheetDialog.findViewById(R.id.rateStar3ImageView)!!
        val rateStar4ImageView: ImageView =
            rateAppBottomSheetDialog.findViewById(R.id.rateStar4ImageView)!!
        val rateStar5ImageView: ImageView =
            rateAppBottomSheetDialog.findViewById(R.id.rateStar5ImageView)!!
        rateStar1ImageView.setOnClickListener {
            rateStar1ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar2ImageView.clearColorFilter()
            rateStar3ImageView.clearColorFilter()
            rateStar4ImageView.clearColorFilter()
            rateStar5ImageView.clearColorFilter()
            onTickVibration()
            vm.setAppRate(1)
        }
        rateStar2ImageView.setOnClickListener {
            rateStar1ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar2ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar3ImageView.clearColorFilter()
            rateStar4ImageView.clearColorFilter()
            rateStar5ImageView.clearColorFilter()
            onTickVibration()
            vm.setAppRate(2)
        }
        rateStar3ImageView.setOnClickListener {
            rateStar1ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar2ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar3ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar4ImageView.clearColorFilter()
            rateStar5ImageView.clearColorFilter()
            onTickVibration()
            vm.setAppRate(3)
        }
        rateStar4ImageView.setOnClickListener {
            rateStar1ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar2ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar3ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar4ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar5ImageView.clearColorFilter()
            onTickVibration()
            vm.setAppRate(4)
        }
        rateStar5ImageView.setOnClickListener {
            rateStar1ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar2ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar3ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar4ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            rateStar5ImageView.setColorFilter(ContextCompat.getColor(it.context, R.color.yellow))
            onTickVibration()
            vm.setAppRate(5)
        }

        when (vm.appRate.value) {
            1 -> {
                rateStar1ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
            }

            2 -> {
                rateStar1ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar2ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
            }

            3 -> {
                rateStar1ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar2ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar3ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
            }

            4 -> {
                rateStar1ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar2ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar3ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar4ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
            }

            5 -> {
                rateStar1ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar2ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar3ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar4ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
                rateStar5ImageView.setColorFilter(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.yellow
                    )
                )
            }
        }

        rateAppBottomSheetDialog.show()
    }

    private fun launchMarketIntent() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse("market://details?id=${packageName}"))
        startActivity(intent)
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
            startActivity(intent)
            lowRateBottomSheetDialog.dismiss()
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
