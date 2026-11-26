package com.breckneck.debtbook.core.navigation

import android.content.SharedPreferences
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import androidx.navigation.compose.rememberNavController
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.core.screenAnimationsEnabled
import com.breckneck.debtbook.debt.main.DebtViewModel
import com.breckneck.debtbook.debt.main.debtDestinations
import com.breckneck.debtbook.core.ui.components.BubbleBottomBar
import com.breckneck.debtbook.core.ui.components.LocalHostedTabBarHeight
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.rating.AppRatingSheet
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop

@Composable
internal fun DebtBookHost(
    activity: MainActivity,
    main: MainViewModel,
    launchTab: MainTab?,
    preferences: SharedPreferences,
) {
    val navigator = remember { ScreenNavigator() }
    val nav = rememberNavController(navigator)
    val animate = activity.screenAnimationsEnabled()

    LaunchedEffect(nav) {
        activity.onGraphReady(nav)
        // A restored task or reminder entry can set up the graph without a launcher tab visit.
        nav.currentBackStackEntryFlow.drop(if (launchTab == null) 1 else 0).collect { entry ->
            entry.destination.mainTab()?.takeIf { it.persisted }?.let { selected ->
                preferences.edit { putString(MainActivity.LAST_TAB, selected.key) }
            }
        }
    }

    DebtBookTheme {
        var tabPixels by remember { mutableIntStateOf(0) }
        val tabHeight = with(LocalDensity.current) { tabPixels.toDp() }
        DebtBookLayout(banner = { onSize -> MainBannerSlot(activity, main, onSize) }) { padding, bannerHeight ->
            val insets by rememberUpdatedState(padding)
            CompositionLocalProvider(LocalHostedTabBarHeight provides tabHeight) {
                PreparedScreenHost(
                    navController = nav,
                    startDestination = Finances,
                    initialize = { launchTab?.let(this::selectTab) },
                    animate = animate,
                    tabBar = { entry, tab ->
                        val searching = tab == MainTab.DEBTS &&
                            hiltViewModel<DebtViewModel>(entry).isSearching.observeAsState(false).value
                        if (!searching) {
                            HostedTabBar(tab, bannerHeight, onSize = { tabPixels = it }) { selected ->
                                if (nav.currentDestination.mainTab() != null && nav.selectTab(selected)) {
                                    activity.maybeShowInterstitial()
                                }
                            }
                        }
                    },
                    enterTransition = {
                        when {
                            isTabTransition(initialState.destination, targetState.destination) -> fadeIn(tween(220, easing = LinearOutSlowInEasing))
                            else -> slideInHorizontally(tween(280)) { it * 3 / 10 } + fadeIn(tween(280))
                        }
                    },
                    exitTransition = {
                        when {
                            isTabTransition(initialState.destination, targetState.destination) -> fadeOut(tween(220, easing = FastOutLinearInEasing))
                            else -> fadeOut(tween(280), targetAlpha = .65f)
                        }
                    },
                    popEnterTransition = {
                        when {
                            isTabTransition(initialState.destination, targetState.destination) -> fadeIn(tween(220, easing = LinearOutSlowInEasing))
                            else -> fadeIn(tween(280), initialAlpha = .65f)
                        }
                    },
                    popExitTransition = {
                        when {
                            isTabTransition(initialState.destination, targetState.destination) -> fadeOut(tween(220, easing = FastOutLinearInEasing))
                            else -> slideOutHorizontally(tween(280)) { it } + fadeOut(tween(110, delayMillis = 170))
                        }
                    },
                ) {
                    val screenInsets = @Composable { insets }
                    debtDestinations(nav, screenInsets, activity, main)
                    financeDestinations(nav, screenInsets, main)
                    goalDestinations(nav, screenInsets, main)
                    settingsDestinations(nav, screenInsets, activity, main)
                    currencyDestination(nav, screenInsets)
                }
            }
        }

        val lifecycleState by activity.lifecycle.currentStateAsState()
        val rating = main.appRating
        if (rating != null && lifecycleState == Lifecycle.State.RESUMED && activity.canAccessApp()) {
            AppRatingSheet(
                state = rating,
                onSelect = {
                    activity.startTickVibration()
                    main.selectAppRating(it)
                },
                onReviewChange = main::updateAppReview,
                onContinue = activity::continueAppRating,
                onBackToRating = main::returnToAppRating,
                onDismiss = main::closeAppRating,
            )
        }
    }
}

@Composable
private fun DebtBookLayout(
    banner: @Composable ((Int) -> Unit) -> Unit,
    content: @Composable BoxScope.(PaddingValues, Dp) -> Unit,
) {
    val density = LocalDensity.current
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val safePadding = safe.asPaddingValues()
    val ime = WindowInsets.ime
    val bottom = safe.only(WindowInsetsSides.Bottom).union(ime)
    var bannerPixels by remember { mutableIntStateOf(0) }
    val bannerHeight = with(density) { bannerPixels.toDp() }
    val contentPadding = PaddingValues.Absolute(
        left = safePadding.calculateLeftPadding(LayoutDirection.Ltr),
        top = safePadding.calculateTopPadding(),
        right = safePadding.calculateRightPadding(LayoutDirection.Ltr),
        bottom = bannerHeight +
            if (ime.getBottom(density) > WindowInsets.navigationBars.getBottom(density)) 0.dp
            else safePadding.calculateBottomPadding(),
    )
    Box(Modifier.fillMaxSize().background(bubbleColors().background)) {
        content(contentPadding, bannerHeight)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(bottom),
        ) {
            banner { bannerPixels = it }
        }
    }
}

@Composable
private fun BoxScope.HostedTabBar(
    tab: MainTab,
    bannerHeight: Dp,
    onSize: (Int) -> Unit,
    onTab: (MainTab) -> Unit,
) {
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(bottom = bannerHeight),
    ) {
        BubbleBottomBar(tab, onTab, Modifier.onSizeChanged { onSize(it.height) })
    }
}

internal fun NavDestination?.mainTab(): MainTab? = when {
    this == null -> null
    hasRoute<Debts>() -> MainTab.DEBTS
    hasRoute<Finances>() -> MainTab.FINANCES
    hasRoute<Goals>() -> MainTab.GOALS
    hasRoute<Settings>() -> MainTab.SETTINGS
    else -> null
}

private fun isTabTransition(from: NavDestination, to: NavDestination) =
    from.mainTab() != null && to.mainTab() != null

internal fun NavHostController.selectTab(tab: MainTab): Boolean {
    if (currentDestination.mainTab() == tab) return false
    if (tab == MainTab.FINANCES) return popBackStack<Finances>(inclusive = false, saveState = true)

    val route = when (tab) {
        MainTab.DEBTS -> Debts
        MainTab.FINANCES -> Finances
        MainTab.GOALS -> Goals
        MainTab.SETTINGS -> Settings
    }
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo<Finances> { saveState = true }
    }
    return true
}

private fun NavGraphBuilder.currencyDestination(nav: NavHostController, insets: @Composable () -> PaddingValues) {
    destination<Currencies>(nav) {
        val route = entry.toRoute<Currencies>()
        com.breckneck.debtbook.core.currency.CurrencyPickerScreen(
            title = androidx.compose.ui.res.stringResource(com.breckneck.debtbook.R.string.select_currency),
            selectedCode = route.selected,
            availableCodes = route.available,
            hostInsets = insets(),
            onBack = ::back,
            onCurrencySelected = { returnResult(CURRENCY_RESULT, it) },
        )
    }
}

@Preview
@Composable
private fun DebtBookLayoutPreview() {
    DebtBookTheme {
        DebtBookLayout(banner = {}) { _, bannerHeight ->
            HostedTabBar(MainTab.DEBTS, bannerHeight, {}, {})
        }
    }
}
