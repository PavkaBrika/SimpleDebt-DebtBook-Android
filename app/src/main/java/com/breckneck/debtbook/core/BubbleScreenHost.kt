package com.breckneck.debtbook.core

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme

internal fun ComponentActivity.enableBubbleEdgeToEdge() {
    // Before API 26 the navigation buttons are always white.
    val darkScrim = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) 0x99000000.toInt() else Color.TRANSPARENT
    enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, darkScrim))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}

internal fun Fragment.bubbleScreen(
    @IdRes viewId: Int,
    content: @Composable (PaddingValues) -> Unit,
): ComposeView {
    val host = requireActivity() as MainActivity
    return ComposeView(requireContext()).apply {
        id = viewId
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { DebtBookTheme { content(host.screenInsets) } }
    }
}
