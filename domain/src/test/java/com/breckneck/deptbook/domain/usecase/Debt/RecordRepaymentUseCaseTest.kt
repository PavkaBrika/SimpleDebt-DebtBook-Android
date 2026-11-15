package com.breckneck.deptbook.domain.usecase.Debt

import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.repository.DebtRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal

class RecordRepaymentUseCaseTest {
    private val repository: DebtRepository = mock()
    private val transaction = object : DataTransaction { override fun <T> run(block: () -> T): T = block() }
    private val useCase = RecordRepaymentUseCase(repository, transaction)
    private val original = DebtDomain(1, BigDecimal("100"), 2, null, "unknown")
    private val key = "request-000000001"

    @Test fun `current remaining is checked inside transaction instead of trusting displayed balance`() {
        whenever(repository.getAllDebtsById(2)).thenReturn(listOf(original,
            DebtDomain(3, BigDecimal("-90"), 2, null, "2026-11-16", repaymentOfId = 1, operationId = "request-000000003")))
        val error = assertThrows(RepaymentExceedsRemaining::class.java) { useCase.execute(1, 2, BigDecimal("20"), "2026-11-16", key) }
        assertEquals(0, BigDecimal.TEN.compareTo(error.remaining))
        verify(repository, never()).setDebt(any())
    }
    @Test fun `retry of committed or cancelled request does not create another repayment`() {
        for (cancelled in listOf(false, true)) {
            whenever(repository.getAllDebtsById(2)).thenReturn(listOf(original,
                DebtDomain(3, BigDecimal("-100"), 2, null, "2026-11-16", repaymentOfId = 1, operationId = key, cancelled = cancelled)))
            useCase.execute(1, 2, BigDecimal("100"), "2026-11-17", key)
        }
        verify(repository, never()).setDebt(any())
        assertThrows(IllegalArgumentException::class.java) { useCase.execute(1, 2, BigDecimal("99"), "2026-11-17", key) }
    }
    @Test fun `repayment signs follow original record in either direction`() {
        for (sign in listOf(1, -1)) {
            whenever(repository.getAllDebtsById(2)).thenReturn(listOf(original.copy(sum = BigDecimal("100") * sign.toBigDecimal())))
            useCase.execute(1, 2, BigDecimal("20"), "2026-11-16", key)
            verify(repository).setDebt(check { row ->
                assertEquals(0, (-20 * sign).toBigDecimal().compareTo(row.sum))
                assertEquals(1, row.repaymentOfId)
                assertEquals(key, row.operationId)
            })
            clearInvocations(repository)
        }
    }
    @Test fun `shared running total excludes cancelled repayment`() {
        val rows = listOf(original,
            DebtDomain(3, BigDecimal("-20"), 2, "Received", "2026-11-16", repaymentOfId = 1, operationId = key),
            DebtDomain(4, BigDecimal("-30"), 2, "Cancelled", "2026-11-16", repaymentOfId = 1, operationId = "request-000000004", cancelled = true))
        val text = GetDebtShareString().execute(rows, "Person", "EUR", BigDecimal("80"), true)
        assertTrue(text.lines().last { it.isNotBlank() }.endsWith("(80)"), text)
    }
}
