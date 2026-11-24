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

internal enum class DebtSystemPicker { CONTACT, PERSON_PHOTO }

/** Keeps the current navigation/form state behind the lock, including after process death. */
class ActivityAuthGate(
    private val activity: AppCompatActivity,
    private val pinEnabled: () -> Boolean,
    private val lockDelayMillis: () -> Long,
) {
    private var awaitingAuthentication = false
    private data class PickerRequest(val picker: DebtSystemPicker, val startedAt: Long)

    private var pendingPicker: PickerRequest? = null
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            cancelSystemPicker()
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
        if (pendingPicker == null) requestAuthentication()
    }

    internal fun beginSystemPicker(picker: DebtSystemPicker) {
        if (pendingPicker != null || !pinEnabled() || !canAccess()) return
        ContextCompat.registerReceiver(activity, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        pendingPicker = PickerRequest(picker, SystemClock.elapsedRealtime())
    }

    internal fun completeSystemPicker(picker: DebtSystemPicker, selectionRead: Boolean) {
        val request = pendingPicker?.takeIf { it.picker == picker } ?: return
        val elapsed = SystemClock.elapsedRealtime() - request.startedAt
        cancelSystemPicker()
        if (selectionRead && elapsed in 0 until PICKER_WINDOW_MILLIS &&
            activity.getSystemService(PowerManager::class.java)?.isInteractive == true &&
            activity.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
        ) {
            // Resume only the authenticated session that launched this in-process request.
            AuthSession.unlock()
            updateVisibility()
        }
    }

    internal fun cancelSystemPicker() {
        if (pendingPicker == null) return
        pendingPicker = null
        activity.unregisterReceiver(screenOffReceiver)
    }

    internal fun onPostResume() {
        cancelSystemPicker()
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
        const val PICKER_WINDOW_MILLIS = 5 * 60 * 1000L
    }
}
