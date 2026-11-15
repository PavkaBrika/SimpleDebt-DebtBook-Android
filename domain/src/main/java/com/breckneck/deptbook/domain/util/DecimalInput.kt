package com.breckneck.deptbook.domain.util

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

object DecimalInput {
    fun parse(text: String, currency: String? = "USD", previous: BigDecimal? = null): BigDecimal? {
        // Editing a note must not force rounding a legacy amount with extra precision.
        if (previous != null && text.trim().replace(',', '.') == previous.stripTrailingZeros().toPlainString()) return previous
        return MoneyPolicy.parse(text, currency)
    }
    fun isEditable(text: String, currency: String = "USD"): Boolean =
        Regex("[+-]?[0-9]{0,18}(?:[.,][0-9]{0,${MoneyPolicy.scale(currency)}})?").matches(text)
    fun format(value: BigDecimal, locale: Locale = Locale.getDefault()): String =
        value.stripTrailingZeros().toPlainString().replace('.', DecimalFormatSymbols(locale).decimalSeparator)
}
