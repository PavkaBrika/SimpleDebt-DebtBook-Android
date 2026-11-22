package com.breckneck.debtbook.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.breckneck.debtbook.R

internal fun Context.openSettingsIntent(
    intent: Intent,
    onUnavailable: () -> Unit = { Toast.makeText(this, R.string.settings_no_handler, Toast.LENGTH_LONG).show() },
): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    onUnavailable()
    false
}
