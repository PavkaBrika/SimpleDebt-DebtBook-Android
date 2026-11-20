package com.breckneck.deptbook.domain.util

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyDisplay {
    fun format(value: BigDecimal, currency: String, locale: Locale = Locale.getDefault()): String {
        val rounded = MoneyPolicy.rounded(value, currency)
        val scale = if (rounded.stripTrailingZeros().scale() > 0) rounded.scale() else 0
        val symbols = DecimalFormatSymbols(locale).apply { groupingSeparator = ' ' }
        return DecimalFormat("#,##0", symbols).apply {
            maximumFractionDigits = scale
            minimumFractionDigits = scale
        }.format(rounded)
    }
}
