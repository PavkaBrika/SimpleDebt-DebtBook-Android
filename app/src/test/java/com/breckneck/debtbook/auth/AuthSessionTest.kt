package com.breckneck.debtbook.auth

import com.breckneck.deptbook.domain.util.PINCodeAction
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthSessionTest {
    @Before fun lock() = AuthSession.lock()
    @After fun reset() = AuthSession.lock()

    @Test fun `fresh session and background lock require PIN again`() {
        assertFalse(AuthSession.canAccess(pinEnabled = true))
        AuthSession.unlock()
        assertTrue(AuthSession.canAccess(pinEnabled = true))
        AuthSession.lock()
        assertFalse(AuthSession.canAccess(pinEnabled = true))
        assertTrue(AuthSession.canAccess(pinEnabled = false))
    }

    @Test fun `external actions cannot enable replace or disable a PIN`() {
        for (enabled in listOf(false, true)) {
            for (action in PINCodeAction.entries) {
                assertEquals(PINCodeAction.CHECK, allowedPINAction(action.name, enabled, false))
            }
        }
    }

    @Test fun `enable cannot overwrite an existing PIN even from settings`() {
        assertEquals(PINCodeAction.CHECK, allowedPINAction("ENABLE", true, true))
        assertEquals(PINCodeAction.ENABLE, allowedPINAction("ENABLE", false, true))
    }

    @Test fun `settings can change or disable only an existing PIN`() {
        for (action in listOf(PINCodeAction.CHANGE, PINCodeAction.DISABLE)) {
            assertEquals(action, allowedPINAction(action.name, true, true))
            assertEquals(PINCodeAction.CHECK, allowedPINAction(action.name, false, true))
        }
        assertEquals(PINCodeAction.CHECK, allowedPINAction(null, true, true))
        assertEquals(PINCodeAction.CHECK, allowedPINAction("unknown", true, true))
    }
}
