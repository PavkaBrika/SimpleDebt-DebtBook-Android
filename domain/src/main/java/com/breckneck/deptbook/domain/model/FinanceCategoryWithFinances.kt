package com.breckneck.deptbook.domain.model

import java.math.BigDecimal

data class FinanceCategoryWithFinances(
    val financeCategory: FinanceCategory,
    var categorySum: BigDecimal = BigDecimal.ZERO,
    var categoryPercentage: Int = 0,
    val financeList: MutableList<Finance>
)