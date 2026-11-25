package com.breckneck.debtbook.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch

internal inline fun <reified T : Any> NavGraphBuilder.destination(
    nav: NavController,
    waitForContent: Boolean = false,
    crossinline content: @Composable Destination.() -> Unit,
) {
    val navigator = nav.navigatorProvider.getNavigator(ScreenNavigator::class.java)
    destination(ScreenNavigator.Builder(navigator, T::class) { entry, reportPlacement, overlay ->
        val current by nav.currentBackStackEntryAsState()
        val lifecycle by entry.lifecycle.currentStateAsState()
        val active = entry == current
        val scope = remember(nav, entry, reportPlacement) { Destination(nav, entry, reportPlacement) }
        CompositionLocalProvider(LocalRouteActive provides active) {
            val placement = if (waitForContent) Modifier else Modifier.onPlaced { reportPlacement() }
            Box(Modifier.fillMaxSize().then(placement).testTag(T::class.simpleName.orEmpty())
                .semantics {
                    testTagsAsResourceId = true
                    this[RouteResumed] = active && lifecycle == Lifecycle.State.RESUMED
                }) {
                scope.content()
                overlay()
            }
        }
    })
}

internal val RouteResumed = SemanticsPropertyKey<Boolean>("RouteResumed")

internal val LocalRouteActive = staticCompositionLocalOf { true }

@Composable
internal fun RouteBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled && LocalRouteActive.current, onBack = onBack)
}

internal class Destination(
    val nav: NavController,
    val entry: NavBackStackEntry,
    val reportPlacement: () -> Unit,
) {
    val isCurrent get() = nav.currentBackStackEntry == entry

    fun back() {
        entry.lifecycleScope.launch {
            if (isCurrent) nav.popBackStack()
        }
    }

    fun open(route: Any, options: NavOptionsBuilder.() -> Unit = {}) {
        if (isCurrent) nav.navigate(route, options)
    }

    fun <T : Any> returnResult(key: String, value: T) {
        entry.lifecycleScope.launch {
            if (!isCurrent) return@launch
            nav.previousBackStackEntry?.savedStateHandle?.set(key, value)
            nav.popBackStack()
        }
    }

    @Composable
    fun WhileResumed(block: suspend () -> Unit) {
        val currentBlock by rememberUpdatedState(block)
        DisposableEffect(entry) {
            val job = entry.lifecycleScope.launch {
                entry.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { currentBlock() }
            }
            onDispose { job.cancel() }
        }
    }

    @Composable
    fun <T : Any> Result(key: String, consume: (T) -> Unit) {
        WhileResumed { entry.savedStateHandle.remove<T>(key)?.let(consume) }
    }

    @Composable
    fun isResumed(): Boolean {
        val state by entry.lifecycle.currentStateAsState()
        return state == Lifecycle.State.RESUMED
    }
}

internal const val CURRENCY_RESULT = "currencyResult"
internal const val CATEGORY_RESULT = "categoryResult"
internal const val DEBT_LIST_CURRENCY = "debtListCurrency"
internal const val DEBT_PERSON_RESULT = "debtPersonChanged"
internal const val GOAL_EDIT_RESULT = "goalEdited"
