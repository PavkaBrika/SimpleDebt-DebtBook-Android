package com.breckneck.deptbook.domain.usecase.Debt

import java.math.BigDecimal

import java.text.DecimalFormat

class FormatDebtSum {

    fun execute(sum:BigDecimal): String {
        val decimalFormat = DecimalFormat("###,###,###.####################")
        return decimalFormat.format(sum)
    }
}