package com.breckneck.debtbook.settings.main

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.breckneck.debtbook.R

internal enum class AppTheme(@param:StringRes val label: Int, val nightMode: Int) {
    SYSTEM(R.string.system_theme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(R.string.light_theme, AppCompatDelegate.MODE_NIGHT_NO),
    DARK(R.string.dark_theme, AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromPreference(value: String?): AppTheme = when (value) {
            // Earlier versions saved the localized display label.
            "LIGHT", "Light", "Светлая", "Licht", "Lumière" -> LIGHT
            "DARK", "Dark", "Темная", "Dunkelheit", "Sombre" -> DARK
            else -> SYSTEM
        }
    }
}
