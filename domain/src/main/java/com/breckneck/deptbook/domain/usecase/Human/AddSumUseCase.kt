package com.breckneck.deptbook.domain.usecase.Human

import java.math.BigDecimal

import com.breckneck.deptbook.domain.repository.HumanRepository

class AddSumUseCase(val humanRepository: HumanRepository) {

    fun execute(humanId: Int, sum: BigDecimal) {
        humanRepository.addSum(humanId = humanId, sum = sum)
    }
}