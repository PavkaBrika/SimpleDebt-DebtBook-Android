package com.breckneck.debtbook.finance.main

import com.breckneck.deptbook.domain.model.*
import com.breckneck.deptbook.domain.util.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class FinancePeriodTest {
    @Test fun weekCrossesYearAndMonthStopsBeforeNextMidnight() {
        val week = FinancePeriod(FinanceInterval.WEEK, "2027-01-01")
        assertEquals("2026-12-28".."2027-01-03", week.days())
        assertEquals("2027-01-08", week.shifted(1).day)
        val month = FinancePeriod(FinanceInterval.MONTH, "2024-01-31")
        assertEquals("2024-02-01", month.shifted(1).day)
        assertEquals("2024-02-01".."2024-02-29", month.shifted(1).days())
        val category = FinanceCategory(4, "Books", FinanceCategoryState.EXPENSE, "#ffffff", 65)
        val entries = listOf("2024-01-01", "2024-01-31", "2024-02-01").mapIndexed { index, day ->
            Finance(index + 1, "0.015".toBigDecimal(), Date(), "", 4, "RUB", day)
        }.toMutableList()
        val data = listOf(FinanceCategoryWithFinances(category, financeList = entries))
        repeat(2) {
            val result = summarizeFinances(data, "RUB", month).getValue(category.state)
            assertEquals(0, result.total.compareTo("0.030".toBigDecimal()))
            assertEquals(100, result.categories.single().percent)
        }
    }

    @Test fun aCalendarPeriodIsCurrentWhenItContainsToday() {
        val week = FinancePeriod(FinanceInterval.WEEK, "2026-12-31")
        assertTrue(week.isCurrent("2027-01-03"))
        assertFalse(week.isCurrent("2027-01-04"))
        assertTrue(FinancePeriod(FinanceInterval.MONTH, "2024-02-01").isCurrent("2024-02-29"))
        assertTrue(FinancePeriod(FinanceInterval.YEAR, "2024-01-01").isCurrent("2024-12-31"))
    }

    @Test fun customRangeShiftsCalendarDaysAndEndsAtToday() {
        val range = FinancePeriod(FinanceInterval.DATE_RANGE, "2024-02-28", "2024-03-01")
        assertEquals("2024-02-25".."2024-02-27", range.shifted(-1).days())
        assertEquals("2024-03-02".."2024-03-04", range.shifted(1).days())
        val current = range.atToday("2024-03-11")
        assertEquals("2024-03-09".."2024-03-11", current.days())
        assertTrue(current.isCurrent("2024-03-11"))
        assertFalse(current.isCurrent("2024-03-10"))
        assertEquals("2024-03-11", current.entryDay("2024-03-11"))
        assertEquals("2024-02-28", range.entryDay("2024-03-11"))
    }
}
