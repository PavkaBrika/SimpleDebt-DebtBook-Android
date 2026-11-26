package com.breckneck.debtbook.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.breckneck.debtbook.BuildConfig
import com.breckneck.deptbook.domain.util.LedgerDate
import dagger.hilt.android.qualifiers.ApplicationContext
import io.appmetrica.analytics.AppMetrica
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtUsageAnalytics internal constructor(
    private val preferences: SharedPreferences,
    private val enabled: Boolean,
    private val reportEvent: (String) -> Unit,
    private val today: () -> String = LedgerDate::today,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences("debt_usage_analytics", Context.MODE_PRIVATE),
        enabled = BuildConfig.APPMETRICA_API_KEY.isNotEmpty(),
        reportEvent = AppMetrica::reportEvent,
    )

    @Synchronized
    internal fun initialize(wasUpdated: Boolean) = track {
        if (!preferences.contains("eligible")) {
            preferences.edit().putBoolean("eligible", !wasUpdated).commit()
        }
    }

    internal fun debtCreated() = recordAction(canStart = true)

    internal fun existingDebtActionSaved() = recordAction(canStart = false)

    @Synchronized
    private fun recordAction(canStart: Boolean) = track {
        if (!preferences.getBoolean("eligible", false) || preferences.getBoolean("laterDayRecorded", false)) return@track
        val firstDay = preferences.getString("firstDay", null)
        val day = today()
        // Persist before handing off to the SDK, whose event API has no delivery acknowledgement.
        if (firstDay == null && canStart) {
            if (preferences.edit().putString("firstDay", day).commit()) reportEvent("first_debt_saved")
        } else if (firstDay != null && day > firstDay) {
            if (preferences.edit().putBoolean("laterDayRecorded", true).commit()) reportEvent("debt_action_on_later_day")
        }
    }

    private inline fun track(action: () -> Unit) {
        if (!enabled) return
        try {
            action()
        } catch (_: Exception) {
            Log.w("DebtUsageAnalytics", "Unable to record debt usage")
        }
    }
}
