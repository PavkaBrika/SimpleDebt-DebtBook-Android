package com.breckneck.debtbook.goal.create

import java.math.BigDecimal
import com.breckneck.deptbook.domain.util.compareTo

import com.breckneck.debtbook.goal.create.model.NameError
import com.breckneck.debtbook.goal.create.model.SavedSumError
import com.breckneck.debtbook.goal.create.model.SumError
import com.breckneck.deptbook.domain.util.DecimalInput

/**
 * Pure validation for create/edit goal form. Used by [CreateGoalsViewModel] and unit-tested directly.
 */
object CreateGoalsFormValidation {
    const val MAX_NAME_LENGTH = 100

    fun validateName(name: String): NameError? =
        if (name.isEmpty()) NameError.EMPTY else null

    fun validateSum(sumText: String, currency: String = "USD", previous: BigDecimal? = null): Pair<BigDecimal?, SumError?> {
        val sumDouble = DecimalInput.parse(sumText, currency, previous)
        val error = when {
            sumText.isEmpty() || sumDouble == null -> SumError.INVALID
            sumDouble < 0 -> SumError.NEGATIVE
            sumDouble.signum() == 0 -> SumError.ZERO
            else -> null
        }
        return Pair(sumDouble, error)
    }

    fun validateSavedSum(
        savedSumText: String,
        sumDouble: BigDecimal?,
        currency: String = "USD",
        previous: BigDecimal? = null,
    ): Pair<BigDecimal?, SavedSumError?> {
        if (savedSumText.isEmpty()) return Pair(BigDecimal.ZERO, null)
        val parsed = DecimalInput.parse(savedSumText, currency, previous) ?: return Pair(BigDecimal.ZERO, SavedSumError.INVALID)
        if (parsed < 0) return Pair(BigDecimal.ZERO, SavedSumError.NEGATIVE)
        if (sumDouble != null && parsed >= sumDouble) {
            return Pair(BigDecimal.ZERO, SavedSumError.GREATER_THAN_SUM)
        }
        return Pair(parsed, null)
    }
}
