package com.breckneck.debtbook.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Verification lives only in this ViewModel, never in Intent extras or saved state. */
class PinRecoveryViewModel : ViewModel() {
    enum class Step { VERIFY_OWNER, VERIFYING, NEW_PIN, CONFIRM_PIN, FINISHED }
    enum class Error { UNAVAILABLE, CANCELLED, MISMATCH, SAVE_FAILED }
    data class State(val step: Step = Step.VERIFY_OWNER, val digits: String = "", val error: Error? = null)

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var firstPin = ""

    fun beginVerification(): Boolean {
        if (state.value.step != Step.VERIFY_OWNER) return false
        mutableState.value = State(Step.VERIFYING)
        return true
    }

    fun onDeviceVerified() {
        if (state.value.step == Step.VERIFYING) mutableState.value = State(Step.NEW_PIN)
    }

    fun verificationFailed(error: Error) {
        firstPin = ""
        mutableState.value = State(error = error)
    }

    fun onBackground() {
        if (state.value.step == Step.NEW_PIN || state.value.step == Step.CONFIRM_PIN) {
            firstPin = ""
            mutableState.value = State()
        }
    }

    fun backspace() {
        mutableState.value = state.value.copy(digits = state.value.digits.dropLast(1), error = null)
    }

    fun enterDigit(digit: String, save: (String) -> Unit) {
        val current = state.value
        if (current.step != Step.NEW_PIN && current.step != Step.CONFIRM_PIN) return
        if (digit.length != 1 || digit[0] !in '0'..'9' || current.digits.length >= 4) return
        val pin = current.digits + digit
        mutableState.value = current.copy(digits = pin, error = null)
        if (pin.length != 4) return
        if (current.step == Step.NEW_PIN) {
            firstPin = pin
            mutableState.value = State(Step.CONFIRM_PIN)
        } else if (pin != firstPin) {
            firstPin = ""
            mutableState.value = State(Step.NEW_PIN, error = Error.MISMATCH)
        } else {
            try {
                save(pin)
                firstPin = ""
                mutableState.value = State(Step.FINISHED)
            } catch (_: Exception) {
                firstPin = ""
                mutableState.value = State(Step.NEW_PIN, error = Error.SAVE_FAILED)
            }
        }
    }
}
