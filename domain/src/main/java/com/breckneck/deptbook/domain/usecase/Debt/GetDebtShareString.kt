package com.breckneck.deptbook.domain.usecase.Debt

import java.math.BigDecimal

import com.breckneck.deptbook.domain.model.DebtDomain
import java.text.DecimalFormat

class GetDebtShareString {

    fun execute(debtList: List<DebtDomain>, name: String, currency: String, sum: BigDecimal, addSumInShareText: Boolean): String {
        val formatDebtSum = FormatDebtSum()
        val stringBuilder = StringBuilder("$name: ${formatDebtSum.execute(sum)} $currency\n\n")
        if (!addSumInShareText) {
            for (debt in debtList) {
                var sumString = formatDebtSum.execute(debt.sum)
                if (!sumString.contains("-"))
                    sumString = "+$sumString"
                if (debt.info == null)
                    stringBuilder.append("${com.breckneck.deptbook.domain.util.LedgerDate.display(debt.date)}: $sumString ${currency}\n")
                else
                    stringBuilder.append("${com.breckneck.deptbook.domain.util.LedgerDate.display(debt.date)}: $sumString $currency (${debt.info})\n")
            }
        } else {
            var sumForEachNote = BigDecimal.ZERO
            for (debt in debtList) {
                var sumString = formatDebtSum.execute(debt.sum)
                if (!sumString.contains("-"))
                    sumString = "+$sumString"
                sumForEachNote += debt.sum
                if (debt.info == null)
                    stringBuilder.append("${com.breckneck.deptbook.domain.util.LedgerDate.display(debt.date)}: $sumString $currency  (${formatDebtSum.execute(sumForEachNote)} ${currency})\n")
                else
                    stringBuilder.append("${com.breckneck.deptbook.domain.util.LedgerDate.display(debt.date)}: $sumString $currency (${debt.info}) (${formatDebtSum.execute(sumForEachNote)})\n")
            }
        }
        return stringBuilder.toString()
    }
}