package com.breckneck.deptbook.domain.usecase.Human

import java.math.BigDecimal

import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.repository.HumanRepository

class SetHumanUseCase(private val humanRepository: HumanRepository) {

    fun execute(name: String, sumDebt: BigDecimal, currency: String) {
        humanRepository.insertHuman(HumanDomain(name = name, sumDebt = sumDebt, currency = currency, id = 0))
    }
}