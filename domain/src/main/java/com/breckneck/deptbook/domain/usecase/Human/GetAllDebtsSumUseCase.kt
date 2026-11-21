package com.breckneck.deptbook.domain.usecase.Human

import java.math.BigDecimal
import com.breckneck.deptbook.domain.util.compareTo

import com.breckneck.deptbook.domain.repository.HumanRepository
import com.breckneck.deptbook.domain.util.MoneyDisplay
import com.breckneck.deptbook.domain.util.abs

class GetAllDebtsSumUseCase(val humanRepository: HumanRepository) {

    fun execute(firstCurrency: String, secondCurrency: String): Pair<String, String> {
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
                    positiveText = "+${MoneyDisplay.format(positiveNumbers, firstCurrency)} $firstCurrency"
                else
                    positiveText = "$positiveText\n+${MoneyDisplay.format(positiveNumbers, firstCurrency)} $firstCurrency"
            if (negativeNumbers.signum() != 0)
                if (negativeText.isEmpty())
                    negativeText = "-${MoneyDisplay.format(negativeNumbers, firstCurrency)} $firstCurrency"
                else
                    negativeText = "$negativeText\n-${MoneyDisplay.format(negativeNumbers, firstCurrency)} $firstCurrency"
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
                    positiveText = "+${MoneyDisplay.format(positiveNumbers, secondCurrency)} $secondCurrency"
                else
                    positiveText = "$positiveText\n+${MoneyDisplay.format(positiveNumbers, secondCurrency)} $secondCurrency"
            if (negativeNumbers.signum() != 0)
                if (negativeText.isEmpty())
                    negativeText = "-${MoneyDisplay.format(negativeNumbers, secondCurrency)} $secondCurrency"
                else
                    negativeText = "$negativeText\n-${MoneyDisplay.format(negativeNumbers, secondCurrency)} $secondCurrency"
        }
        return Pair(positiveText, negativeText)
    }
}