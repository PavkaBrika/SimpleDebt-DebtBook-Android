package com.breckneck.debtbook.auth

import com.breckneck.debtbook.auth.PinRecoveryViewModel.Error
import com.breckneck.debtbook.auth.PinRecoveryViewModel.Step
import org.junit.Assert.*
import org.junit.Test

class PinRecoveryViewModelTest {
    private fun enter(vm: PinRecoveryViewModel, pin: String, save: (String) -> Unit) =
        pin.forEach { vm.enterDigit(it.toString(), save) }

    @Test fun `digits and unsolicited success never grant recovery`() {
        val vm = PinRecoveryViewModel()
        vm.onDeviceVerified()
        enter(vm, "12341234") { fail("unauthorized write") }
        assertEquals(Step.VERIFY_OWNER, vm.state.value.step)
    }

    @Test fun `only verified matching confirmation replaces pin once`() {
        val vm = PinRecoveryViewModel()
        var writes = 0
        assertTrue(vm.beginVerification())
        assertFalse(vm.beginVerification())
        vm.onDeviceVerified()
        enter(vm, "1234") { fail("first entry must not write") }
        assertEquals(Step.CONFIRM_PIN, vm.state.value.step)
        enter(vm, "1234") { assertEquals("1234", it); writes++ }
        enter(vm, "1234") { writes++ }
        assertEquals(1, writes)
        assertEquals(Step.FINISHED, vm.state.value.step)
    }

    @Test fun `cancellation and mismatch leave old pin untouched`() {
        val vm = PinRecoveryViewModel()
        vm.beginVerification()
        vm.verificationFailed(Error.CANCELLED)
        enter(vm, "12341234") { fail("cancelled write") }
        vm.beginVerification(); vm.onDeviceVerified()
        enter(vm, "12349999") { fail("mismatched write") }
        assertEquals(Error.MISMATCH, vm.state.value.error)
        assertEquals(Step.NEW_PIN, vm.state.value.step)
    }

    @Test fun `write failure does not finish and allows retry`() {
        val vm = PinRecoveryViewModel()
        vm.beginVerification(); vm.onDeviceVerified()
        enter(vm, "12341234") { throw IllegalStateException("disk error") }
        assertEquals(Error.SAVE_FAILED, vm.state.value.error)
        enter(vm, "56785678") { assertEquals("5678", it) }
        assertEquals(Step.FINISHED, vm.state.value.step)
    }

    @Test fun `background or new process loses verification and entered pin`() {
        val vm = PinRecoveryViewModel()
        vm.beginVerification(); vm.onDeviceVerified()
        enter(vm, "1234") { fail() }
        vm.onBackground()
        enter(vm, "1234") { fail("grant survived background") }
        assertEquals(Step.VERIFY_OWNER, vm.state.value.step)
        assertEquals("", vm.state.value.digits)
        assertEquals(Step.VERIFY_OWNER, PinRecoveryViewModel().state.value.step)
    }
}
