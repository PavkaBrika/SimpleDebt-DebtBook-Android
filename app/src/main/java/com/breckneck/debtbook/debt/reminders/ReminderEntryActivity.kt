package com.breckneck.debtbook.debt.reminders

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.breckneck.debtbook.auth.ActivityAuthGate
import com.breckneck.debtbook.auth.AuthSession
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.deptbook.domain.usecase.Settings.GetPINCodeEnabled
import com.breckneck.deptbook.domain.usecase.Settings.GetPinLockDelay
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

/** Authenticate before CLEAR_TOP can remove an already displayed PIN activity. */
@AndroidEntryPoint
class ReminderEntryActivity : AppCompatActivity() {
    @Inject lateinit var pinEnabled: GetPINCodeEnabled
    @Inject lateinit var lockDelay: GetPinLockDelay
    private val gate = ActivityAuthGate(this, { pinEnabled.execute() }, { lockDelay.execute().millis })
    private var forwarded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gate.onCreate(savedInstanceState)
    }
    override fun onStart() { super.onStart(); gate.onStart() }
    override fun onStop() { if (!forwarded) gate.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); gate.onSaveInstanceState(outState) }
    override fun onResume() {
        super.onResume()
        if (!forwarded && AuthSession.canAccess(pinEnabled.execute(), SystemClock.elapsedRealtime(), lockDelay.execute().millis)) {
            forwarded = true
            startActivity(Intent(this, MainActivity::class.java).apply {
                action = DebtReminders.ACTION_OPEN
                putExtra(MainActivity.REMINDER_REQUEST, UUID.randomUUID().toString())
                putExtra(DebtReminders.EXTRA_DEBT, intent.getIntExtra(DebtReminders.EXTRA_DEBT, -1))
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
    }
}
