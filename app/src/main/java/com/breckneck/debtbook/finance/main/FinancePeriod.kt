package com.breckneck.debtbook.finance.main

import com.breckneck.deptbook.domain.util.FinanceInterval
import com.breckneck.deptbook.domain.util.LedgerDate
import java.util.Calendar
import java.util.concurrent.TimeUnit

internal data class FinancePeriod(
    val interval: FinanceInterval,
    val day: String,
    val endDay: String = day,
) {
    init {
        require(interval != FinanceInterval.DATE_RANGE || day <= endDay)
    }

    private fun calendar() = Calendar.getInstance().apply {
        time = requireNotNull(LedgerDate.localDate(day))
    }

    private val field get() = when (interval) {
        FinanceInterval.DAY, FinanceInterval.DATE_RANGE -> Calendar.DATE
        FinanceInterval.WEEK -> Calendar.WEEK_OF_YEAR
        FinanceInterval.MONTH -> Calendar.MONTH
        FinanceInterval.YEAR -> Calendar.YEAR
    }

    fun shifted(count: Int): FinancePeriod {
        if (interval == FinanceInterval.DATE_RANGE) {
            val length = daysBetween(day, endDay) + 1
            return shiftRange(length * count)
        }
        val anchor = calendar().apply {
            if (interval == FinanceInterval.MONTH || interval == FinanceInterval.YEAR) set(Calendar.DAY_OF_MONTH, 1)
            add(field, count)
        }
        return FinancePeriod(interval, LedgerDate.fromDate(anchor.time))
    }

    fun atToday(today: String): FinancePeriod =
        if (interval == FinanceInterval.DATE_RANGE) shiftRange(daysBetween(endDay, today))
        else FinancePeriod(interval, today)

    fun isAtToday(today: String): Boolean =
        if (interval == FinanceInterval.DATE_RANGE) endDay == today else day == today

    fun entryDay(today: String): String =
        if (interval == FinanceInterval.DATE_RANGE && today in day..endDay) today else day

    fun days(): ClosedRange<String> {
        if (interval == FinanceInterval.DATE_RANGE) return day..endDay
        val calendar = calendar()
        when (interval) {
            FinanceInterval.DAY, FinanceInterval.DATE_RANGE -> Unit
            FinanceInterval.WEEK -> calendar.add(Calendar.DATE, -(calendar.get(Calendar.DAY_OF_WEEK) + 5).mod(7))
            FinanceInterval.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            FinanceInterval.YEAR -> calendar.set(Calendar.DAY_OF_YEAR, 1)
        }
        val start = LedgerDate.fromDate(calendar.time)
        calendar.add(field, 1)
        calendar.add(Calendar.DATE, -1)
        return start..LedgerDate.fromDate(calendar.time)
    }

    private fun shiftRange(days: Long): FinancePeriod {
        val offset = TimeUnit.DAYS.toMillis(days)
        return copy(
            day = LedgerDate.dayFromPickerMillis(LedgerDate.pickerMillis(day) + offset),
            endDay = LedgerDate.dayFromPickerMillis(LedgerDate.pickerMillis(endDay) + offset),
        )
    }

    private fun daysBetween(start: String, end: String): Long =
        TimeUnit.MILLISECONDS.toDays(LedgerDate.pickerMillis(end) - LedgerDate.pickerMillis(start))
}
