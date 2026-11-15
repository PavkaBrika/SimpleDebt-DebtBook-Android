package com.breckneck.deptbook.domain.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Form input only. Persisted money remains Double until the R1b migration.
 * Accept a single decimal dot OR comma, at most two fraction digits, no grouping,
 * exponent, suffix, NaN or infinity. Never parse only the prefix of a value.
 */
object DecimalInput {
    private val complete = Regex("[+-]?[0-9]+(?:[.,][0-9]{1,2})?")
    private val editing = Regex("[+-]?[0-9]*(?:[.,][0-9]{0,2})?")

    fun parse(text: String): Double? {
        val input = text.trim()
        if (!complete.matches(input)) return null
        return input.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun isEditable(text: String): Boolean = editing.matches(text)

    fun format(value: Double, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("0.##", DecimalFormatSymbols(locale)).apply {
            isGroupingUsed = false
        }.format(value)
}
