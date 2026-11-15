package com.breckneck.debtbook.auth

import com.breckneck.deptbook.domain.util.PINCodeAction

/** Deliberately not persisted: process recreation must require authentication again. */
object AuthSession {
    var isUnlocked: Boolean = false
        private set

    private var backgroundedAt: Long? = null

    fun unlock() { isUnlocked = true; backgroundedAt = null }
    fun lock() { isUnlocked = false; backgroundedAt = null }

    fun onBackground(nowMillis: Long) {
        if (isUnlocked && backgroundedAt == null) backgroundedAt = nowMillis
    }

    fun onForeground() { backgroundedAt = null }

    fun canAccess(pinEnabled: Boolean, nowMillis: Long = 0L, delayMillis: Long = 0L): Boolean {
        if (!pinEnabled) return true
        backgroundedAt?.let { since ->
            val elapsed = nowMillis - since
            if (elapsed < 0 || elapsed >= delayMillis.coerceAtLeast(0L)) lock()
        }
        return isUnlocked
    }

}

/** Launcher extras are untrusted, including requests to replace an existing PIN. */
internal fun allowedPINAction(
    requested: String?,
    pinEnabled: Boolean,
    trustedCaller: Boolean
): PINCodeAction = when {
    !trustedCaller -> PINCodeAction.CHECK
    requested == PINCodeAction.ENABLE.name && !pinEnabled -> PINCodeAction.ENABLE
    requested == PINCodeAction.CHANGE.name && pinEnabled -> PINCodeAction.CHANGE
    requested == PINCodeAction.DISABLE.name && pinEnabled -> PINCodeAction.DISABLE
    else -> PINCodeAction.CHECK
}
