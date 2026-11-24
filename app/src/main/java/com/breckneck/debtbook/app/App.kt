package com.breckneck.debtbook.app

import android.app.Application
import android.util.Log
import com.breckneck.debtbook.BuildConfig
import com.yandex.mobile.ads.common.YandexAds
import dagger.hilt.android.HiltAndroidApp
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

@HiltAndroidApp
class App: Application() {

    override fun onCreate() {
        super.onCreate()

        com.breckneck.debtbook.debt.reminders.DebtReminders.get(this).start()

        if (!BuildConfig.DEBUG) {
            YandexAds.setUserConsent(false)
            activateAppMetrica()
            YandexAds.initialize(this) {
                Log.i("YandexAds", "Yandex initialized")
            }
        }
    }

    private fun activateAppMetrica() {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val config = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY)
            .handleFirstActivationAsUpdate(packageInfo.firstInstallTime != packageInfo.lastUpdateTime)
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
