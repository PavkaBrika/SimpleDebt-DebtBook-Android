package com.breckneck.deptbook.domain.model

import java.math.BigDecimal

data class HumanDomain(
    var id: Int,
    var name: String,
    var sumDebt: BigDecimal,
    var currency: String,
    var openingAdjustment: BigDecimal = sumDebt,
    val photo: String? = null,
)
