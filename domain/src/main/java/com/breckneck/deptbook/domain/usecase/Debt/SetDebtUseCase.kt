package com.breckneck.deptbook.domain.usecase.Debt

import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.repository.DebtRepository
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import java.math.BigDecimal
import java.util.UUID

class SetDebtUseCase(val debtRepository: DebtRepository) {

    fun execute(
        sum: BigDecimal,
        idHuman: Int,
        info: String?,
        date: String,
        dueDay: String? = null,
        reminderMinute: Int? = null
    ) {
        val debt = DebtDomain(
            id = 0,
            sum = sum,
            idHuman = idHuman,
            info = info,
            date = date,
            dueDay = dueDay,
            reminderMinute = reminderMinute,
            reminderToken = reminderMinute?.let { UUID.randomUUID().toString() }
        )
        DebtReminderPolicy.validate(debt)
        dueDay?.let { require(DebtReminderPolicy.canSetDueDay(date, it)) { "Due day precedes the debt" } }
        if (reminderMinute != null) {
            require(DebtReminderPolicy.canSchedule(requireNotNull(dueDay))) { "Reminder day has passed" }
        }
        debtRepository.setDebt(debt)
    }
}
