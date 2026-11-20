package com.breckneck.deptbook.domain.usecase.Human

import java.math.BigDecimal

import com.breckneck.deptbook.domain.repository.HumanRepository

class GetHumanSumDebtUseCase(val humanRepository: HumanRepository) {

    fun execute(humanId: Int): BigDecimal {
        return humanRepository.getHumanSumDebt(humanId = humanId)
    }
}