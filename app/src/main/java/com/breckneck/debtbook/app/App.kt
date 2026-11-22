package com.breckneck.debtbook.app

import android.app.Application
import android.util.Log
import com.breckneck.debtbook.BuildConfig
import com.yandex.mobile.ads.common.YandexAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App: Application() {

    override fun onCreate() {
        super.onCreate()

        com.breckneck.debtbook.debt.reminders.DebtReminders.get(this).start()

        if (!BuildConfig.DEBUG) {
            YandexAds.setUserConsent(false)
            YandexAds.initialize(this) {
                Log.i("YandexAds", "Yandex initialized")
            }
        }
    }
}
