package com.breckneck.debtbook.app

import android.app.Application
import android.util.Log
import com.yandex.mobile.ads.common.InitializationListener
import com.yandex.mobile.ads.common.MobileAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App: Application() {

    override fun onCreate() {
        super.onCreate()

        com.breckneck.debtbook.debt.reminders.DebtReminders.get(this).start()

        MobileAds.initialize(this, object: InitializationListener {
            override fun onInitializationCompleted() {
                Log.e("TAG", "Yandex initialized")
            }
        })
    }
}