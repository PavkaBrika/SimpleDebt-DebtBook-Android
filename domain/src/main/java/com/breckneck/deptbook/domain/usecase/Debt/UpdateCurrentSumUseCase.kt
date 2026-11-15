package com.breckneck.deptbook.domain.usecase.Debt

import java.math.BigDecimal

class UpdateCurrentSumUseCase {
    fun execute(sum: BigDecimal, pastSum: BigDecimal) : BigDecimal {
        return sum - pastSum
    }
}