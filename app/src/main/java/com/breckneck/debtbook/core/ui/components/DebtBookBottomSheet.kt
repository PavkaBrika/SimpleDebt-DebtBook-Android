package com.breckneck.debtbook.core.ui.components

import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Project-standard bottom sheet wrapper.
 *
 * Enforces design system rules for every bottom sheet in the app:
 * - [dragHandle] = null — the default M3 drag pill is visually noisy and conflicts
 *   with DebtBook's calm design language; swipe-to-dismiss still works via back gesture / scrim tap.
 * - [skipPartiallyExpanded] = true — sheets always open fully expanded; half-expanded state
 *   is confusing and not used anywhere in the app.
 *
 * Content is responsible for its own top padding (24.dp) to compensate for the removed drag handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtBookBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    hideRequested: Boolean = false,
    gesturesEnabled: Boolean = true,
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = gesturesEnabled && !hideRequested,
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = null,
        properties = properties,
    ) {
        val dialogView = LocalView.current
        val window = (dialogView.parent as? DialogWindowProvider)?.window
        val lightSheet = containerColor.luminance() > .5f
        SideEffect {
            if (window != null) {
                // The modal owns a separate window. Its sheet surface already draws under the bar.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && lightSheet) {
                        0x99000000.toInt()
                    } else {
                        android.graphics.Color.TRANSPARENT
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
                WindowCompat.getInsetsController(window, dialogView)
                    .isAppearanceLightNavigationBars = lightSheet
            }
        }
        content()
    }
    if (hideRequested) {
        val onHidden by rememberUpdatedState(onDismiss)
        LaunchedEffect(sheetState) {
            sheetState.hide()
            if (!sheetState.isVisible) onHidden()
        }
    }
}
