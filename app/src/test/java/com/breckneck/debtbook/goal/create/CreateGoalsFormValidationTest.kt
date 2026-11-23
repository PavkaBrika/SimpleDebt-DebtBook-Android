package com.breckneck.debtbook.goal.create

import java.math.BigDecimal

import com.breckneck.debtbook.goal.create.model.NameError
import com.breckneck.debtbook.goal.create.model.SavedSumError
import com.breckneck.debtbook.goal.create.model.SumError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.breckneck.debtbook.goal.create.mapper.toCreateGoalUi
import com.breckneck.deptbook.domain.model.Goal
import java.util.Date
import java.util.Locale

class CreateGoalsFormValidationTest {

    @Test
    fun `validateName empty is EMPTY`() {
        assertEquals(NameError.EMPTY, CreateGoalsFormValidation.validateName(""))
    }

    @Test
    fun `validateName non-empty is null`() {
        assertNull(CreateGoalsFormValidation.validateName("Goal"))
    }

    @Test
    fun `validateSum empty is INVALID`() {
        val (value, err) = CreateGoalsFormValidation.validateSum("")
        assertNull(value)
        assertEquals(SumError.INVALID, err)
    }

    @Test
    fun `validateSum non-numeric is INVALID`() {
        val (value, err) = CreateGoalsFormValidation.validateSum("abc")
        assertNull(value)
        assertEquals(SumError.INVALID, err)
    }

    @Test
    fun `validateSum negative is NEGATIVE`() {
        val (value, err) = CreateGoalsFormValidation.validateSum("-0.01")
        assertEquals(0, (-"0.01".toBigDecimal().stripTrailingZeros()).compareTo(value!!))
        assertEquals(SumError.NEGATIVE, err)
    }

    @Test
    fun `validateSum zero is ZERO`() {
        val (value, err) = CreateGoalsFormValidation.validateSum("0")
        assertEquals(0, ("0.0".toBigDecimal().stripTrailingZeros()).compareTo(value!!))
        assertEquals(SumError.ZERO, err)
    }

    @Test
    fun `validateSum positive is valid`() {
        val (value, err) = CreateGoalsFormValidation.validateSum("1500.50")
        assertEquals(0, ("1500.50".toBigDecimal().stripTrailingZeros()).compareTo(value!!))
        assertNull(err)
    }

    @Test
    fun `validateSavedSum empty is zero without error`() {
        val (value, err) = CreateGoalsFormValidation.validateSavedSum("", sumDouble = "100.0".toBigDecimal().stripTrailingZeros())
        assertEquals(0, ("0.0".toBigDecimal().stripTrailingZeros()).compareTo(value!!))
        assertNull(err)
    }

    @Test
    fun `validateSavedSum invalid text`() {
        val (value, err) = CreateGoalsFormValidation.validateSavedSum("x", sumDouble = "100.0".toBigDecimal().stripTrailingZeros())
        assertEquals(0, ("0.0".toBigDecimal().stripTrailingZeros()).compareTo(value!!))
        assertEquals(SavedSumError.INVALID, err)
    }

    @Test
    fun `validateSavedSum greater or equal goal sum is error`() {
        val (_, errEq) = CreateGoalsFormValidation.validateSavedSum("100", sumDouble = "100.0".toBigDecimal().stripTrailingZeros())
        assertEquals(SavedSumError.GREATER_THAN_SUM, errEq)

        val (_, errGt) = CreateGoalsFormValidation.validateSavedSum("101", sumDouble = "100.0".toBigDecimal().stripTrailingZeros())
        assertEquals(SavedSumError.GREATER_THAN_SUM, errGt)
    }

    @Test
    fun `validateSavedSum below goal sum is valid`() {
        val (value, err) = CreateGoalsFormValidation.validateSavedSum("50", sumDouble = "100.0".toBigDecimal().stripTrailingZeros())
        assertEquals(0, ("50.0".toBigDecimal().stripTrailingZeros()).compareTo(value!!))
        assertNull(err)
    }

    @Test
    fun `validateSavedSum negative is NEGATIVE`() {
        val (value, err) = CreateGoalsFormValidation.validateSavedSum("-1", sumDouble = "100.0".toBigDecimal().stripTrailingZeros())
        assertEquals(0, ("0.0".toBigDecimal().stripTrailingZeros()).compareTo(value!!))
        assertEquals(SavedSumError.NEGATIVE, err)
    }

    @Test
    fun `validateSavedSum when goal sum invalid still parses saved`() {
        val (value, err) = CreateGoalsFormValidation.validateSavedSum("10", sumDouble = null)
        assertEquals(0, ("10.0".toBigDecimal().stripTrailingZeros()).compareTo(value!!))
        assertNull(err)
    }

    @Test
    fun `locale decimal edit values round trip without grouping or truncation`() {
        val previousLocale = Locale.getDefault()
        try {
            listOf(Locale.US, Locale("ru", "RU"), Locale.GERMANY, Locale.FRANCE).forEach { locale ->
                Locale.setDefault(locale)
                val goal = Goal(id = 7, name = "Trip", sum = "12345.67".toBigDecimal().stripTrailingZeros(), savedSum = "1234.56".toBigDecimal().stripTrailingZeros(),
                    currency = "€", creationDate = Date(), goalDate = null, photoPath = null)
                val ui = goal.toCreateGoalUi()
                val (sum, sumError) = CreateGoalsFormValidation.validateSum(ui.sum)
                val (saved, savedError) = CreateGoalsFormValidation.validateSavedSum(ui.savedSum, sum)
                assertEquals(goal.sum, sum!!)
                assertEquals(goal.savedSum, saved!!)
                assertNull(sumError)
                assertNull(savedError)
                assertEquals(0, ("12.34".toBigDecimal().stripTrailingZeros()).compareTo(CreateGoalsFormValidation.validateSum("12,34").first!!))
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `partial or ambiguous amounts never validate`() {
        listOf("12,34oops", "1,234.56", "1.234,56", "12 34", "12,345", "NaN", "Infinity", "1e4").forEach {
            assertEquals(it, SumError.INVALID, CreateGoalsFormValidation.validateSum(it).second)
            assertEquals(it, SavedSumError.INVALID, CreateGoalsFormValidation.validateSavedSum(it, "20000.0".toBigDecimal().stripTrailingZeros()).second)
        }
    }
}
