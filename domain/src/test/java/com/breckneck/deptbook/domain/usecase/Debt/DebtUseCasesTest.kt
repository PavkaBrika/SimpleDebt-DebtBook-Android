package com.breckneck.deptbook.domain.usecase.Debt

import java.math.BigDecimal

import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.repository.DebtRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor

class DebtUseCasesTest {

    private val debtRepository = mock<DebtRepository>()

    @AfterEach
    fun tearDown() {
        Mockito.reset(debtRepository)
    }

    // --- GetAllDebts ---

    @Test
    fun `GetAllDebts should return list from repository`() {
        val debts = listOf(
            DebtDomain(id = 1, sum = "100.0".toBigDecimal().stripTrailingZeros(), idHuman = 1, info = "test", date = "1 Jan 2024"),
            DebtDomain(id = 2, sum = -"50.0".toBigDecimal().stripTrailingZeros(), idHuman = 1, info = null, date = "2 Jan 2024")
        )
        Mockito.`when`(debtRepository.getAllDebts()).thenReturn(debts)
        val result = GetAllDebts(debtRepository).execute()
        Assertions.assertEquals(debts, result)
    }

    @Test
    fun `GetAllDebts should return empty list`() {
        Mockito.`when`(debtRepository.getAllDebts()).thenReturn(emptyList())
        val result = GetAllDebts(debtRepository).execute()
        Assertions.assertTrue(result.isEmpty())
    }

    // --- GetAllDebtsByIdUseCase ---

    @Test
    fun `GetAllDebtsByIdUseCase should return debts for given humanId`() {
        val debts = listOf(DebtDomain(id = 1, sum = "200.0".toBigDecimal().stripTrailingZeros(), idHuman = 5, info = null, date = "5 Mar 2024"))
        Mockito.`when`(debtRepository.getAllDebtsById(5)).thenReturn(debts)
        val result = GetAllDebtsByIdUseCase(debtRepository).execute(id = 5)
        Assertions.assertEquals(debts, result)
    }

    @Test
    fun `GetAllDebtsByIdUseCase should return empty list for unknown humanId`() {
        Mockito.`when`(debtRepository.getAllDebtsById(99)).thenReturn(emptyList())
        val result = GetAllDebtsByIdUseCase(debtRepository).execute(id = 99)
        Assertions.assertTrue(result.isEmpty())
    }

    // --- GetDebtQuantity ---

    @Test
    fun `GetDebtQuantity should return count from repository`() {
        Mockito.`when`(debtRepository.getDebtQuantity()).thenReturn(7)
        val result = GetDebtQuantity(debtRepository).execute()
        Assertions.assertEquals(7, result)
    }

    @Test
    fun `GetDebtQuantity should return zero when no debts`() {
        Mockito.`when`(debtRepository.getDebtQuantity()).thenReturn(0)
        val result = GetDebtQuantity(debtRepository).execute()
        Assertions.assertEquals(0, result)
    }

    // --- SetDebtUseCase ---

    @Test
    fun `SetDebtUseCase should call setDebt with correct DebtDomain`() {
        SetDebtUseCase(debtRepository).execute(sum = "250.0".toBigDecimal().stripTrailingZeros(), idHuman = 3, info = "lunch", date = "10 Mar 2024")

        val captor = argumentCaptor<DebtDomain>()
        verify(debtRepository).setDebt(captor.capture())
        val debt = captor.firstValue
        Assertions.assertTrue(debt.recordedAsLoan)
        Assertions.assertEquals(0, debt.id)
        Assertions.assertEquals(0, ("250.0".toBigDecimal().stripTrailingZeros()).compareTo(debt.sum!!))
        Assertions.assertEquals(3, debt.idHuman)
        Assertions.assertEquals("lunch", debt.info)
        Assertions.assertEquals("10 Mar 2024", debt.date)
    }

    @Test
    fun `SetDebtUseCase should call setDebt with null info`() {
        SetDebtUseCase(debtRepository).execute(sum = "100.0".toBigDecimal().stripTrailingZeros(), idHuman = 1, info = null, date = "1 Apr 2024")

        val captor = argumentCaptor<DebtDomain>()
        verify(debtRepository).setDebt(captor.capture())
        Assertions.assertNull(captor.firstValue.info)
    }

    // --- DeleteDebtUseCase ---

    @Test
    fun `DeleteDebtUseCase should call deleteDebt with provided debt`() {
        val debt = DebtDomain(id = 10, sum = "150.0".toBigDecimal().stripTrailingZeros(), idHuman = 2, info = null, date = "15 Apr 2024")
        DeleteDebtUseCase(debtRepository).execute(debtDomain = debt)
        verify(debtRepository).deleteDebt(debt)
    }

    // --- DeleteDebtsByHumanIdUseCase ---

    @Test
    fun `DeleteDebtsByHumanIdUseCase should call deleteDebtsByHumanId with correct id`() {
        DeleteDebtsByHumanIdUseCase(debtRepository).execute(id = 8)
        verify(debtRepository).deleteDebtsByHumanId(id = 8)
    }

    @Test
    fun `DeleteDebtsByHumanIdUseCase should call deleteDebtsByHumanId exactly once`() {
        DeleteDebtsByHumanIdUseCase(debtRepository).execute(id = 1)
        verify(debtRepository, Mockito.times(1)).deleteDebtsByHumanId(1)
    }

    // --- EditDebtUseCase ---

    @Test
    fun `EditDebtUseCase should call editDebt with correct DebtDomain`() {
        EditDebtUseCase(debtRepository).execute(id = 5, sum = "300.0".toBigDecimal().stripTrailingZeros(), idHuman = 2, info = "edited", date = "20 Apr 2024")

        val captor = argumentCaptor<DebtDomain>()
        verify(debtRepository).editDebt(captor.capture(), org.mockito.kotlin.isNull())
        val debt = captor.firstValue
        Assertions.assertEquals(5, debt.id)
        Assertions.assertEquals(0, ("300.0".toBigDecimal().stripTrailingZeros()).compareTo(debt.sum!!))
        Assertions.assertEquals(2, debt.idHuman)
        Assertions.assertEquals("edited", debt.info)
        Assertions.assertEquals("20 Apr 2024", debt.date)
    }

    // --- ReplaceAllDebts ---

    @Test
    fun `ReplaceAllDebts should call replaceAllDebts with provided list`() {
        val debts = listOf(
            DebtDomain(id = 1, sum = "100.0".toBigDecimal().stripTrailingZeros(), idHuman = 1, info = null, date = "1 Jan 2024"),
            DebtDomain(id = 2, sum = -"200.0".toBigDecimal().stripTrailingZeros(), idHuman = 2, info = "test", date = "2 Jan 2024")
        )
        ReplaceAllDebts(debtRepository).execute(debtList = debts)
        verify(debtRepository).replaceAllDebts(debts)
    }

    @Test
    fun `ReplaceAllDebts should call replaceAllDebts with empty list`() {
        ReplaceAllDebts(debtRepository).execute(debtList = emptyList())
        verify(debtRepository).replaceAllDebts(emptyList())
    }

    // --- UpdateCurrentSumUseCase ---

    @Test
    fun `UpdateCurrentSumUseCase should return difference of sum minus pastSum`() {
        val result = UpdateCurrentSumUseCase().execute(sum = "500.0".toBigDecimal().stripTrailingZeros(), pastSum = "200.0".toBigDecimal().stripTrailingZeros())
        Assertions.assertEquals(0, ("300.0".toBigDecimal().stripTrailingZeros()).compareTo(result!!))
    }

    @Test
    fun `UpdateCurrentSumUseCase should return negative result when sum is less than pastSum`() {
        val result = UpdateCurrentSumUseCase().execute(sum = "100.0".toBigDecimal().stripTrailingZeros(), pastSum = "300.0".toBigDecimal().stripTrailingZeros())
        Assertions.assertEquals(0, (-"200.0".toBigDecimal().stripTrailingZeros()).compareTo(result!!))
    }

    @Test
    fun `UpdateCurrentSumUseCase should return zero when sum equals pastSum`() {
        val result = UpdateCurrentSumUseCase().execute(sum = "150.0".toBigDecimal().stripTrailingZeros(), pastSum = "150.0".toBigDecimal().stripTrailingZeros())
        Assertions.assertEquals(0, ("0.0".toBigDecimal().stripTrailingZeros()).compareTo(result!!))
    }

    // --- GetCurrentDateUseCase ---

    @Test
    fun `GetCurrentDateUseCase returns a canonical calendar day`() {
        val result = GetCurrentDateUseCase().execute()
        Assertions.assertTrue(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(result))
        Assertions.assertEquals(result, com.breckneck.deptbook.domain.util.LedgerDate.canonical(result))
    }

}
