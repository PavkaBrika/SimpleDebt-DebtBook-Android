package com.breckneck.debtbook.core.navigation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import kotlin.reflect.KClass

internal typealias ScreenContent = @Composable (NavBackStackEntry, () -> Unit, @Composable BoxScope.() -> Unit) -> Unit

// Keep the saved navigator name from Navigation Compose when restoring an existing stack.
@Navigator.Name("composable")
internal class ScreenNavigator : Navigator<ScreenNavigator.Screen>() {
    val backStack get() = state.backStack
    var isPop = false
        private set

    override fun createDestination() = Screen(this) { _, _, _ -> }

    override fun navigate(entries: List<NavBackStackEntry>, navOptions: NavOptions?, navigatorExtras: Extras?) {
        isPop = false
        entries.forEach(state::pushWithTransition)
    }

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        isPop = true
        state.popWithTransition(popUpTo, savedState)
    }

    fun prepareForTransition(entry: NavBackStackEntry) = state.prepareForTransition(entry)

    fun completeTransitions() = state.transitionsInProgress.value.forEach(state::markTransitionComplete)

    class Screen(
        navigator: ScreenNavigator,
        val content: ScreenContent,
    ) : NavDestination(navigator)

    class Builder(
        private val screenNavigator: ScreenNavigator,
        route: KClass<*>,
        private val content: ScreenContent,
    ) : NavDestinationBuilder<Screen>(screenNavigator, route, emptyMap()) {
        override fun instantiateDestination() = Screen(screenNavigator, content)
    }
}
