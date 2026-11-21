package com.breckneck.deptbook.domain.model

import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.util.Filter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PersonDebtHistoryTest {
    private val lent = DebtDomain(1, BigDecimal("1000"), 1, "Поездка", "2026-10-01")
    private val borrowed = DebtDomain(2, BigDecimal("-1000"), 1, "Билеты", "2026-10-04")

    private fun payment(original: DebtDomain, id: Int, amount: String, date: String) = DebtDomain(
        id, BigDecimal(amount), original.idHuman, null, date,
        repaymentOfId = original.id, operationId = "request-000000000$id"
    )

    private fun assertVisibleIn(history: PersonDebtHistory, vararg filters: Filter) {
        assertEquals(filters.toSet(), Filter.entries.filter(history::matches).toSet())
    }

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertEquals(0, actual.compareTo(BigDecimal(expected)), "Expected $expected, got $actual")
    }

    @Test fun `opposite loans keep their own outstanding balance through closure reload and cancellation`() {
        val originals = listOf(lent, borrowed)
        val initial = PersonDebtHistory.from(originals)
        assertEquals(originals, initial.openLoans.map { it.original })
        assertTrue(initial.settledLoans.isEmpty())
        assertVisibleIn(initial, Filter.ALL, Filter.POSITIVE, Filter.NEGATIVE)

        val first = payment(lent, 3, "-400", "2026-10-05")
        val partial = PersonDebtHistory.from(originals + first)
        assertAmount("600", partial.openLoans.single { it.original == lent }.remaining)
        assertAmount("1000", partial.openLoans.single { it.original == borrowed }.remaining)
        assertTrue(partial.settledLoans.isEmpty())
        assertVisibleIn(partial, Filter.ALL, Filter.POSITIVE, Filter.NEGATIVE)

        val last = payment(lent, 4, "-600", "2026-10-06")
        val rows = listOf(last, lent, first, borrowed)
        val closed = PersonDebtHistory.from(rows)
        assertEquals(borrowed, closed.openLoans.single().original)
        assertEquals(lent, closed.settledLoans.single().original)
        assertEquals(listOf(first, last), closed.settledLoans.single().repayments)
        assertAmount("0", closed.settledLoans.single().remaining)
        assertEquals(closed.settledLoans.single(), closed.loan(lent.id))
        assertEquals(closed.openLoans.single(), closed.loan(borrowed.id))
        assertNull(closed.loan(last.id))
        assertVisibleIn(closed, Filter.ALL, Filter.NEGATIVE)

        val person = HumanDomain(1, "Анна", BigDecimal("-1000"), "RUB", BigDecimal.ZERO)
        val backup = AppDataLists(listOf(person), rows, emptyList(), emptyList(), emptyList(), emptyList())
        val codec = BackupCodec()
        val restored = codec.decode(codec.encode(backup)).data
        val archive = PersonDebtHistory.from(restored.debtList)
        assertEquals(listOf(first.id, last.id), archive.settledLoans.single().repayments.map { it.id })
        assertEquals(lent.date, archive.settledLoans.single().original.date)
        assertAmount("1000", archive.settledLoans.single().original.sum)
        assertEquals("RUB", restored.humanList.single().currency)
        assertVisibleIn(archive, Filter.ALL, Filter.NEGATIVE)

        val reopened = PersonDebtHistory.from(restored.debtList.map {
            if (it.id == last.id) it.copy(cancelled = true) else it
        })
        val loan = reopened.openLoans.single { it.original.id == lent.id }
        assertAmount("600", loan.remaining)
        assertEquals(listOf(first.id, last.id), loan.repayments.map { it.id })
        assertFalse(loan.repayments.first().cancelled)
        assertTrue(loan.repayments.last().cancelled)
        assertTrue(reopened.settledLoans.isEmpty())
        assertVisibleIn(reopened, Filter.ALL, Filter.POSITIVE, Filter.NEGATIVE)
    }

    @Test fun `borrowed loan preserves exact legacy amounts until the last fraction is repaid`() {
        val old = borrowed.copy(sum = BigDecimal("-9007199254740993.00001"), date = "unknown legacy day")
        val first = payment(old, 3, "9007199254740993", "2026-11-02")
        val last = payment(old, 4, "0.00001", "2026-11-02")
        val partial = PersonDebtHistory.from(listOf(first, old))
        assertAmount("0.00001", partial.openLoans.single().remaining)
        assertVisibleIn(partial, Filter.ALL, Filter.NEGATIVE)

        val closed = PersonDebtHistory.from(listOf(last, old, first))
        val loan = closed.settledLoans.single()
        assertEquals(old, loan.original)
        assertEquals(listOf(first, last), loan.repayments)
        assertTrue(closed.openLoans.isEmpty())
        assertVisibleIn(closed, Filter.ALL)
    }

    @Test fun `legacy zero entries and opening adjustment do not create open or settled loans`() {
        val legacy = lent.copy(sum = BigDecimal("0.00"), date = "undated")
        val adjustment = BigDecimal("30.5001")
        val person = HumanDomain(1, "Анна", adjustment, "RUB", adjustment)
        val backup = AppDataLists(listOf(person), listOf(legacy), emptyList(), emptyList(), emptyList(), emptyList())
        val restored = BackupCodec().run { decode(encode(backup)).data }
        assertEquals(person, restored.humanList.single())
        val history = PersonDebtHistory.from(restored.debtList)
        assertEquals(legacy.id, history.zeroAmountEntries.single().id)
        assertTrue(history.openLoans.isEmpty())
        assertTrue(history.settledLoans.isEmpty())
        assertVisibleIn(history, Filter.ALL)
        assertVisibleIn(PersonDebtHistory.from(emptyList()), Filter.ALL)
    }
}
