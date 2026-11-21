package com.breckneck.deptbook.domain.util

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MoneyDisplayTest {
    @Test fun `displays kopecks and cents with two digits`() {
        val russian = Locale.forLanguageTag("ru-RU")
        assertEquals("10,50", MoneyDisplay.format("10.5".toBigDecimal(), "RUB", russian))
        assertEquals("-1 010,05", MoneyDisplay.format("-1010.05".toBigDecimal(), "RUB", russian))
        assertEquals("10.50", MoneyDisplay.format("10.5".toBigDecimal(), "EUR", Locale.US))
    }

    @Test fun `uses the minor units of the currency`() {
        assertEquals("10", MoneyDisplay.format("10".toBigDecimal(), "JPY", Locale.US))
        assertEquals("10.500", MoneyDisplay.format("10.5".toBigDecimal(), "KWD", Locale.US))
        assertEquals("10.5000", MoneyDisplay.format("10.5".toBigDecimal(), "CLF", Locale.US))
    }

    @Test fun `omits minor units when the displayed amount is whole`() {
        assertEquals("10", MoneyDisplay.format("10".toBigDecimal(), "EUR", Locale.US))
        assertEquals("-1 000", MoneyDisplay.format("-1000.000".toBigDecimal(), "KWD", Locale.US))
        assertEquals("0", MoneyDisplay.format("0.00".toBigDecimal(), "RUB", Locale.US))
        assertEquals("11", MoneyDisplay.format("10.999".toBigDecimal(), "EUR", Locale.US))
        assertEquals("10", DecimalInput.format("10.00".toBigDecimal(), Locale.US, "RUB"))
    }

    @Test fun `padding input does not change a precise stored amount`() {
        val russian = Locale.forLanguageTag("ru-RU")
        val precise = "10.5123".toBigDecimal()
        assertEquals("10,51", MoneyDisplay.format(precise, "RUB", russian))
        assertEquals("10,50", DecimalInput.format("10.5".toBigDecimal(), russian, "RUB"))
        assertEquals("10,5123", DecimalInput.format(precise, russian, "RUB"))
        assertEquals(precise, DecimalInput.parse(DecimalInput.format(precise, russian, "RUB"), "RUB", previous = precise))
    }
}
