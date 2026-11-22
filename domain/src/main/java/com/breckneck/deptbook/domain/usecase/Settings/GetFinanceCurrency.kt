package com.breckneck.deptbook.domain.usecase.Settings

import com.breckneck.deptbook.domain.repository.SettingsRepository

class GetFinanceCurrency(private val settingsRepository: SettingsRepository) {

    // Legacy backups without a currency use USD when no finance currency was saved.
    fun execute(): String = execute { "USD" }

    fun execute(fallback: () -> String): String {
        return settingsRepository.getFinanceCurrency() ?: fallback()
    }
}
