package com.breckneck.debtbook.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.breckneck.debtbook.BuildConfig
import com.yandex.mobile.ads.common.YandexAds
import dagger.hilt.android.HiltAndroidApp
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltAndroidApp
class App: Application() {
    @Inject lateinit var debtUsageAnalytics: DebtUsageAnalytics

    private val adsInitialized = MutableStateFlow(false)
    internal val adsReady = adsInitialized.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        com.breckneck.debtbook.debt.reminders.DebtReminders.get(this).start()

        if (!BuildConfig.DEBUG || BuildConfig.DEMO_ADS) {
            YandexAds.setUserConsent(false)
            YandexAds.setAppAdAnalyticsReporting(false)
            if (!BuildConfig.DEBUG) activateAppMetrica()
            YandexAds.initialize(this) {
                adsInitialized.value = true
                Log.i("YandexAds", "Yandex initialized")
            }
        }
    }

    private fun activateAppMetrica() {
        val updated = wasUpdated()
        debtUsageAnalytics.initialize(updated)
        val config = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY)
            .handleFirstActivationAsUpdate(updated)
            .withSessionsAutoTrackingEnabled(true)
            .withLocationTracking(false)
            .withAdvIdentifiersTracking(false)
            .withAppOpenTrackingEnabled(false)
            .withCrashReporting(false)
            .withNativeCrashReporting(false)
            .withAnrMonitoring(false)
            .withRevenueAutoTrackingEnabled(false)
            .build()
        AppMetrica.activate(this, config)
    }
}

internal fun Context.wasUpdated(): Boolean {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    return packageInfo.firstInstallTime != packageInfo.lastUpdateTime
}
