package com.breckneck.deptbook.domain.usecase.Human

import java.math.BigDecimal
import com.breckneck.deptbook.domain.util.compareTo

import com.breckneck.deptbook.domain.repository.HumanRepository
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import com.breckneck.deptbook.domain.util.abs

class GetAllDebtsSumUseCase(val humanRepository: HumanRepository) {

    val decimalFormat = DecimalFormat("###,###,###.##").apply { maximumFractionDigits = 100 }
    val customSymbol: DecimalFormatSymbols = DecimalFormatSymbols()

    fun execute(firstCurrency: String, secondCurrency: String): Pair<String, String> {
        customSymbol.groupingSeparator = ' '
        decimalFormat.decimalFormatSymbols = customSymbol
        val debtsInFirstCurrency = humanRepository.getAllDebtsSum(currency = firstCurrency)
        val debtsInSecondCurrency = humanRepository.getAllDebtsSum(currency = secondCurrency)
        var positiveNumbers = BigDecimal.ZERO
        var positiveText = ""
        var negativeText = ""
        var negativeNumbers = BigDecimal.ZERO
        if (debtsInFirstCurrency.isNotEmpty()) {
            for (i in debtsInFirstCurrency.indices) {
                if (debtsInFirstCurrency[i] > 0) {
                    positiveNumbers += debtsInFirstCurrency[i]
                }
                if (debtsInFirstCurrency[i] < 0) {
                    negativeNumbers += abs(debtsInFirstCurrency[i])
                }
            }
            if (positiveNumbers.signum() != 0)
                if (positiveText.isEmpty())
                    positiveText = "+${decimalFormat.format(positiveNumbers)} $firstCurrency"
                else
                    positiveText = "$positiveText\n+${decimalFormat.format(positiveNumbers)} $firstCurrency"
            if (negativeNumbers.signum() != 0)
                if (negativeText.isEmpty())
                    negativeText = "-${decimalFormat.format(negativeNumbers)} $firstCurrency"
                else
                    negativeText = "$negativeText\n-${decimalFormat.format(negativeNumbers)} $firstCurrency"
        }
        positiveNumbers = BigDecimal.ZERO
        negativeNumbers = BigDecimal.ZERO
        if (debtsInSecondCurrency.isNotEmpty()) {
            for (i in debtsInSecondCurrency.indices) {
                if (debtsInSecondCurrency[i] > 0) {
                    positiveNumbers += debtsInSecondCurrency[i]
                }
                if (debtsInSecondCurrency[i] < 0) {
                    negativeNumbers += abs(debtsInSecondCurrency[i])
                }
            }
            if (positiveNumbers.signum() != 0)
                if (positiveText.isEmpty())
                    positiveText = "+${decimalFormat.format(positiveNumbers)} $secondCurrency"
                else
                    positiveText = "$positiveText\n+${decimalFormat.format(positiveNumbers)} $secondCurrency"
            if (negativeNumbers.signum() != 0)
                if (negativeText.isEmpty())
                    negativeText = "-${decimalFormat.format(negativeNumbers)} $secondCurrency"
                else
                    negativeText = "$negativeText\n-${decimalFormat.format(negativeNumbers)} $secondCurrency"
        }
        return Pair(positiveText, negativeText)
    }
}