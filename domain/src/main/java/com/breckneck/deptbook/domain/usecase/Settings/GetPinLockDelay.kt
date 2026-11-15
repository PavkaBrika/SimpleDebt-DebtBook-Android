package com.breckneck.deptbook.domain.usecase.Settings

import com.breckneck.deptbook.domain.repository.SettingsRepository
import com.breckneck.deptbook.domain.util.PinLockDelay

class GetPinLockDelay(private val settingsRepository: SettingsRepository) {
    fun execute(): PinLockDelay = PinLockDelay.fromMillis(settingsRepository.getPinLockDelayMillis())
}
