package com.breckneck.deptbook.domain.usecase.Human

import com.breckneck.deptbook.domain.repository.HumanRepository

class GetHumanByIdUseCase(private val humanRepository: HumanRepository) {
    fun execute(id: Int) = humanRepository.getHumanById(id)
}
