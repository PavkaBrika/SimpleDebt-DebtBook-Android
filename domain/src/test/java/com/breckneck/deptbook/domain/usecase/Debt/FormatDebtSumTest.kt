package com.breckneck.deptbook.domain.usecase.Debt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class FormatDebtSumTest {
    @Test fun `format retains exact fractional digits and normalizes zero`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val cases = mapOf("-0.0" to "0", "-0.005" to "-0.005", "-0.5555" to "-0.5555", "10000000.9999" to "10,000,000.9999")
            for ((value, expected) in cases) assertEquals(expected, FormatDebtSum().execute(value.toBigDecimal()))
        } finally { Locale.setDefault(previous) }
    }
}
