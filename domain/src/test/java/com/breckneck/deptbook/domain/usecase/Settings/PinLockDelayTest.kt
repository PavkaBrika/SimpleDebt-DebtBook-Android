package com.breckneck.deptbook.domain.usecase.Settings

import com.breckneck.deptbook.domain.repository.SettingsRepository
import com.breckneck.deptbook.domain.util.PinLockDelay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class PinLockDelayTest {
    @Test fun `stored choices round trip and unknown values lock immediately`() {
        val repository = mock(SettingsRepository::class.java)
        val get = GetPinLockDelay(repository)
        val set = SetPinLockDelay(repository)
        for (delay in PinLockDelay.entries) {
            set.execute(delay)
            verify(repository).setPinLockDelayMillis(delay.millis)
            `when`(repository.getPinLockDelayMillis()).thenReturn(delay.millis)
            assertEquals(delay, get.execute())
        }
        for (value in listOf(-1L, 1L, Long.MAX_VALUE)) {
            `when`(repository.getPinLockDelayMillis()).thenReturn(value)
            assertEquals(PinLockDelay.IMMEDIATE, get.execute())
        }
    }
}
