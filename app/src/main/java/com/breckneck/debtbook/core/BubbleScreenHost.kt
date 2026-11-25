package com.breckneck.debtbook.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.NavHostFragment
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.components.LocalHostedTabBarHeight

internal fun ComponentActivity.enableBubbleEdgeToEdge() {
    // Before API 26 the navigation buttons are always white.
    val darkScrim = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) 0x99000000.toInt() else Color.TRANSPARENT
    enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, darkScrim))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}

internal fun NavHostFragment.prepareScreenTransitions() {
    val manager = childFragmentManager
    manager.addOnBackStackChangedListener(object : FragmentManager.OnBackStackChangedListener {
        override fun onBackStackChanged() = Unit

        override fun onBackStackChangeCommitted(fragment: Fragment, pop: Boolean) {
            // A normal push or pop commits before creating the incoming view. A predictive
            // gesture already has its preview by commit time and keeps control of both animators.
            if (fragment === manager.primaryNavigationFragment && fragment.view == null &&
                view?.isLaidOut == true && requireContext().screenAnimationsEnabled()) {
                fragment.postponeEnterTransition()
            }
        }
    })
}

internal fun Context.screenAnimationsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ValueAnimator.areAnimatorsEnabled()
    else Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

internal fun Fragment.bubbleScreen(
    @IdRes viewId: Int,
    startTransitionOnLayout: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
): ComposeView {
    val host = requireActivity() as MainActivity
    return ComposeView(requireContext()).apply {
        id = viewId
        if (startTransitionOnLayout) doOnPreDraw { startPostponedEnterTransition() }
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val insets = host.screenInsets
                ?: WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
            DebtBookTheme {
                CompositionLocalProvider(LocalHostedTabBarHeight provides host.hostedTabBarHeight) { content(insets) }
            }
        }
    }
}
