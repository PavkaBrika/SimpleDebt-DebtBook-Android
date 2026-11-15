package com.breckneck.deptbook.domain.usecase.Debt

import java.math.BigDecimal

import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.repository.DebtRepository

class SetDebtUseCase(val debtRepository: DebtRepository) {

        fun execute(sum: BigDecimal, idHuman: Int, info: String?, date: String) {
        debtRepository.setDebt(DebtDomain(id = 0 , sum = sum, idHuman = idHuman, info = info, date = date))
    }
}