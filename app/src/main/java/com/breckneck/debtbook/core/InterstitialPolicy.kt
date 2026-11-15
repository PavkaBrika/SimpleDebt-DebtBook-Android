package com.breckneck.debtbook.core

/** Process-session limits. Only an explicit move between top-level tabs may call tryStart. */
class InterstitialPolicy(private val sessionStartedAt: Long) {
    private var attempts = 0
    private var lastAttemptAt = sessionStartedAt
    private var showing = false

    fun tryStart(now: Long): Boolean {
        if (showing || attempts >= 2 || now - lastAttemptAt < MIN_INTERVAL_MS) return false
        showing = true
        attempts++
        lastAttemptAt = now
        return true
    }

    fun finish() { showing = false }

    companion object { const val MIN_INTERVAL_MS = 180_000L }
}
