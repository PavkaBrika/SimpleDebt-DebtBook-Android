package com.breckneck.debtbook.auth

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** Keeps the current navigation/form state behind the lock, including after process death. */
class ActivityAuthGate(
    private val activity: AppCompatActivity,
    private val pinEnabled: () -> Boolean,
    private val lockDelayMillis: () -> Long,
) {
    private var awaitingAuthentication = false
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        awaitingAuthentication = false
        // Neither a successful result nor an Intent extra is an authentication credential.
        if (canAccess()) {
            activity.window.decorView.visibility = View.VISIBLE
        } else {
            activity.finish()
        }
    }

    fun onCreate(savedInstanceState: Bundle?) {
        awaitingAuthentication = savedInstanceState?.getBoolean(AWAITING_AUTH) ?: false
        updateVisibility()
    }

    fun onStart() {
        if (canAccess()) AuthSession.onForeground()
        updateVisibility()
        if (!canAccess() && !awaitingAuthentication) {
            awaitingAuthentication = true
            launcher.launch(Intent(activity, AuthorizationActivity::class.java))
        }
    }

    fun onStop() {
        if (!activity.isChangingConfigurations) {
            // The CHECK activity owns the handoff; a fast biometric result may arrive
            // before Main finishes stopping. Do not revoke that verified result.
            if (!awaitingAuthentication) {
                AuthSession.onBackground(SystemClock.elapsedRealtime())
                if (lockDelayMillis() <= 0L) AuthSession.lock()
            }
            if (pinEnabled()) activity.window.decorView.visibility = View.INVISIBLE
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(AWAITING_AUTH, awaitingAuthentication)
    }

    private fun updateVisibility() {
        activity.window.decorView.visibility =
            if (canAccess()) View.VISIBLE else View.INVISIBLE
    }

    private fun canAccess() = AuthSession.canAccess(pinEnabled(),
        SystemClock.elapsedRealtime(), lockDelayMillis())

    private companion object {
        const val AWAITING_AUTH = "awaitingPinAuthentication"
    }
}
