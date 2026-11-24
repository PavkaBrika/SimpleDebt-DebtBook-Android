package com.breckneck.debtbook.auth

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Keeps the current navigation/form state behind the lock, including after process death. */
class ActivityAuthGate(
    private val activity: AppCompatActivity,
    private val pinEnabled: () -> Boolean,
    private val lockDelayMillis: () -> Long,
) {
    private var awaitingAuthentication = false
    private var contactPickerOpenedAt: Long? = null
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            cancelContactPicker()
        }
    }
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
        // The result is delivered before onPostResume. Keep the window hidden until then.
        if (contactPickerOpenedAt == null) requestAuthentication()
    }

    internal fun beginContactPicker() {
        if (contactPickerOpenedAt != null || !pinEnabled() || !canAccess()) return
        ContextCompat.registerReceiver(activity, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        contactPickerOpenedAt = SystemClock.elapsedRealtime()
    }

    internal fun completeContactPicker(contactRead: Boolean) {
        val elapsed = contactPickerOpenedAt?.let { SystemClock.elapsedRealtime() - it }
        cancelContactPicker()
        if (contactRead && elapsed != null && elapsed in 0 until CONTACT_PICKER_WINDOW_MILLIS &&
            activity.getSystemService(PowerManager::class.java)?.isInteractive == true &&
            activity.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
        ) {
            // Resume only the authenticated session that launched this in-process request.
            AuthSession.unlock()
            updateVisibility()
        }
    }

    internal fun cancelContactPicker() {
        if (contactPickerOpenedAt == null) return
        contactPickerOpenedAt = null
        activity.unregisterReceiver(screenOffReceiver)
    }

    internal fun onPostResume() {
        cancelContactPicker()
        requestAuthentication()
    }

    private fun requestAuthentication() {
        if (!activity.isFinishing && !canAccess() && !awaitingAuthentication) {
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
        const val CONTACT_PICKER_WINDOW_MILLIS = 5 * 60 * 1000L
    }
}
