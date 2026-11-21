package com.breckneck.deptbook.domain.usecase.Debt

import java.math.BigDecimal

import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.model.DebtDueChange
import com.breckneck.deptbook.domain.repository.DebtRepository

class EditDebtUseCase(val debtRepository: DebtRepository) {

    fun execute(id: Int, sum: BigDecimal, idHuman: Int, info: String?, date: String, due: DebtDueChange? = null) {
        debtRepository.editDebt(DebtDomain(id, sum, idHuman, info, date), due)
    }
}
