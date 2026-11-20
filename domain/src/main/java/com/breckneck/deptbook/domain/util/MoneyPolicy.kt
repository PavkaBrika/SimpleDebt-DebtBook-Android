package com.breckneck.deptbook.domain.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Decimal amounts are never converted through binary floating point on a write path. */
object MoneyPolicy {
    val maxInput = BigDecimal("999999999999999999.9999")
    private val plain = Regex("[+-]?[0-9]{1,18}(?:[.,][0-9]{1,4})?")

    // Explicit product currency table; independent of the device's currency database.
    fun scale(currency: String): Int = when (currency) {
        "JPY" -> 0
        "BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND" -> 3
        "CLF", "UYW" -> 4
        else -> 2
    }

    /** New input rejects excess precision rather than silently changing the amount. */
    fun parse(text: String, currency: String? = null): BigDecimal? {
        val value = text.trim()
        if (!plain.matches(value)) return null
        return BigDecimal(value.replace(',', '.')).takeIf {
            it.abs() <= maxInput && (currency == null || it.stripTrailingZeros().scale() <= scale(currency))
        }?.stripTrailingZeros()
    }

    /** Only explicit conversion/export uses rounding; persisted legacy amounts stay lossless. */
    fun rounded(value: BigDecimal, currency: String): BigDecimal =
        value.setScale(scale(currency), RoundingMode.HALF_EVEN)

    fun stored(value: BigDecimal): String {
        require(value.precision() <= 100 && value.scale() in -100..100) { "Amount out of range" }
        return value.stripTrailingZeros().toPlainString()
    }
}

operator fun BigDecimal.compareTo(value: Int): Int = compareTo(value.toBigDecimal())
fun abs(value: BigDecimal): BigDecimal = value.abs()
