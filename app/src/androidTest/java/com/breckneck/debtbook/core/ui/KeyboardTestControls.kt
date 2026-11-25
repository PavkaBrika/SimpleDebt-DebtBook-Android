package com.breckneck.debtbook.core.ui

import android.os.Build
import android.view.Window
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard

internal fun ComposeTestRule.hideKeyboardBeforeBack(window: Window) {
    val ime = WindowInsetsCompat.Type.ime()
    // Below Android 11 IME insets are inferred from layout and can lag a pending show request.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        closeSoftKeyboard()
    } else {
        runOnUiThread { WindowCompat.getInsetsController(window, window.decorView).hide(ime) }
    }
    waitUntil(8_000) {
        runOnUiThread { ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(ime) == false }
    }
}
