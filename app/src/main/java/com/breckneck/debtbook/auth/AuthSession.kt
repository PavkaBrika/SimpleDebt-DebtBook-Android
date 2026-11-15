package com.breckneck.debtbook.auth

import com.breckneck.deptbook.domain.util.PINCodeAction

/** Deliberately not persisted: process recreation must require authentication again. */
object AuthSession {
    var isUnlocked: Boolean = false
        private set

    fun unlock() { isUnlocked = true }
    fun lock() { isUnlocked = false }

    fun canAccess(pinEnabled: Boolean): Boolean = !pinEnabled || isUnlocked
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
