package com.breckneck.debtbook.finance.main

import com.breckneck.deptbook.domain.model.*
import com.breckneck.deptbook.domain.util.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class FinancePeriodTest {
    @Test fun weekCrossesYearAndMonthStopsBeforeNextMidnight() {
        val week = FinancePeriod(FinanceInterval.WEEK, "2027-01-01")
        assertEquals("2026-12-28".."2027-01-03", week.days())
        assertEquals("2027-01-08", week.step(1))
        val month = FinancePeriod(FinanceInterval.MONTH, "2024-01-31")
        assertEquals("2024-02-01", month.step(1))
        assertEquals("2024-02-01".."2024-02-29", month.copy(day = month.step(1)).days())
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
}
