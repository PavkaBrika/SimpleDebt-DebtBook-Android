package com.breckneck.debtbook.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

internal fun ComponentActivity.enableBubbleEdgeToEdge() {
    // Before API 26 the navigation buttons are always white.
    val darkScrim = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) 0x99000000.toInt() else Color.TRANSPARENT
    enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, darkScrim))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}

internal fun Context.screenAnimationsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ValueAnimator.areAnimatorsEnabled()
    else Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
