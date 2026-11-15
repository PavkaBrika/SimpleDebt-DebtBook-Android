package com.breckneck.debtbook.core

import org.junit.Assert.*
import org.junit.Test

class InterstitialPolicyTest {
    @Test fun `warmup cooldown in-flight and session cap prevent chains`() {
        val policy = InterstitialPolicy(0)
        assertFalse(policy.tryStart(59_999))
        assertTrue(policy.tryStart(60_000))
        assertFalse(policy.tryStart(400_000)) // still showing
        policy.finish()
        assertFalse(policy.tryStart(119_999)) // return from advertisement
        assertTrue(policy.tryStart(120_000))
        policy.finish()
        assertFalse(policy.tryStart(9_000_000)) // session cap
    }

    @Test fun `clock moving backwards cannot bypass warmup`() {
        assertFalse(InterstitialPolicy(10_000).tryStart(9_000))
    }
}
