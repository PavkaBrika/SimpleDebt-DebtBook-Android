package com.breckneck.deptbook.domain.util

import java.math.BigDecimal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Locale

class DecimalInputTest {
    @Test fun `decimal dot and comma consume the entire amount`() {
        for (input in listOf("12.34", "12,34", " 12,34 ")) {
            assertEquals(0, ("12.34".toBigDecimal().stripTrailingZeros()).compareTo(DecimalInput.parse(input)!!))
        }
        assertEquals(0, (-"12.34".toBigDecimal().stripTrailingZeros()).compareTo(DecimalInput.parse("-12,34")!!))
        for (invalid in listOf("12,34x", "12.34x", "1,234", "1.234,56", "1,234.56", "1 234", "1\u00a0234", "1e3", "NaN", "Infinity", "12,", ".", "", "--12", "9".repeat(400))) {
            assertNull(DecimalInput.parse(invalid), invalid)
        }
    }

    @Test fun `editing existing amounts round trips in all release locales without grouping`() {
        for (locale in listOf(Locale.US, Locale.UK, Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("ru-RU"))) {
            for (value in listOf("0.0".toBigDecimal().stripTrailingZeros(), "12.34".toBigDecimal().stripTrailingZeros(), -"12.34".toBigDecimal().stripTrailingZeros(), "1234567.89".toBigDecimal().stripTrailingZeros())) {
                assertEquals(value, DecimalInput.parse(DecimalInput.format(value, locale)), locale.toString())
            }
        }
    }

    @Test fun `partial input allowed while typing but not on save`() {
        assertTrue(DecimalInput.isEditable("12,"))
        assertNull(DecimalInput.parse("12,"))
        assertFalse(DecimalInput.isEditable("12,345"))
        assertFalse(DecimalInput.isEditable("1,2.3"))
    }
}
