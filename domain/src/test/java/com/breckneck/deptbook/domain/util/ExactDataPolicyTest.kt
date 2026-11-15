package com.breckneck.deptbook.domain.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Locale
import java.util.TimeZone

class ExactDataPolicyTest {
    @Test fun `decimal arithmetic retains cents above binary precision limit`() {
        assertEquals(BigDecimal("0.3"), MoneyPolicy.parse("0.1")!! + MoneyPolicy.parse("0,2")!!)
        assertEquals("999999999999999999.99", MoneyPolicy.stored(MoneyPolicy.parse("999999999999999999.98")!! + BigDecimal("0.01")))
    }
    @Test fun `input has explicit range and currency precision`() {
        assertNotNull(MoneyPolicy.parse("100", "JPY"))
        assertNull(MoneyPolicy.parse("100.01", "JPY"))
        assertNotNull(MoneyPolicy.parse("1.001", "KWD"))
        assertNull(MoneyPolicy.parse("1.001", "USD"))
        for (value in listOf("1e3", "NaN", "Infinity", "1,000.00", "1000000000000000000", "1.00001"))
            assertNull(MoneyPolicy.parse(value), value)
    }
    @Test fun `rounding is explicit symmetric half even`() {
        assertEquals(BigDecimal("1.00"), MoneyPolicy.rounded(BigDecimal("1.005"), "EUR"))
        assertEquals(BigDecimal("-1.02"), MoneyPolicy.rounded(BigDecimal("-1.015"), "EUR"))
        assertEquals(BigDecimal("2"), MoneyPolicy.rounded(BigDecimal("2.5"), "JPY"))
    }
    @Test fun `legacy precision is retained instead of rounded`() {
        assertEquals("0.30000000000000004", MoneyPolicy.stored(BigDecimal("0.30000000000000004")))
    }
    @Test fun `days are independent of language and time zone`() {
        val locale = Locale.getDefault()
        val zone = TimeZone.getDefault()
        try {
            for (language in listOf("ru-RU", "en-US", "de-DE", "fr-FR")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                for (timeZone in listOf("Pacific/Kiritimati", "America/Los_Angeles", "UTC")) {
                    TimeZone.setDefault(TimeZone.getTimeZone(timeZone))
                    assertEquals("2024-02-29", LedgerDate.day(2024, 1, 29))
                    assertEquals("2026-11-14", LedgerDate.canonical("14 Nov 2026"))
                    assertEquals("2024-02-05", LedgerDate.canonical("5 февр. 2024 г."))
                }
            }
        } finally { Locale.setDefault(locale); TimeZone.setDefault(zone) }
    }
    @Test fun `ambiguous and invalid dates remain verbatim`() {
        for (value in listOf("01/02/2024", "2024-02-30", "unknown date")) {
            assertNull(LedgerDate.canonical(value))
            assertEquals(value, LedgerDate.display(value))
        }
        assertTrue(LedgerDate.sortKey("2024-02-29") < LedgerDate.sortKey("unknown"))
        assertThrows(IllegalArgumentException::class.java) { LedgerDate.day(2023, 1, 29) }
    }
    @Test fun `calendar finance and goal days survive a timezone change without rewriting instants`() {
        val originalZone = TimeZone.getDefault()
        val timestamp = java.util.Date(1794700800123L)
        val finance = com.breckneck.deptbook.domain.model.Finance(sum = BigDecimal.ONE, date = timestamp, info = null, financeCategoryId = 1, currency = "EUR", calendarDay = "2026-11-15")
        val goal = com.breckneck.deptbook.domain.model.Goal(name = "Goal", sum = BigDecimal.TEN, savedSum = BigDecimal.ZERO, currency = "JPY", photoPath = null, creationDate = timestamp, goalDate = timestamp, goalDay = "2026-11-15")
        try {
            for (zone in listOf("Pacific/Kiritimati", "America/Los_Angeles", "Europe/Berlin", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals("2026-11-15", LedgerDate.fromDate(finance.presentationDate))
                val picked = LedgerDate.fromPickerMillis(LedgerDate.pickerMillis("2026-11-15"))
                assertEquals("2026-11-15", LedgerDate.fromDate(picked))
                assertEquals("2026-11-15", LedgerDate.fromDate(goal.presentationGoalDate!!))
                assertEquals(1794700800123L, finance.date.time)
                assertEquals(1794700800123L, goal.creationDate.time)
            }
        } finally { TimeZone.setDefault(originalZone) }
    }
}
