package com.breckneck.deptbook.domain.usecase.Settings

import com.breckneck.deptbook.domain.repository.SettingsRepository
import com.breckneck.deptbook.domain.util.PinLockDelay

class SetPinLockDelay(private val settingsRepository: SettingsRepository) {
    fun execute(delay: PinLockDelay) = settingsRepository.setPinLockDelayMillis(delay.millis)
}
