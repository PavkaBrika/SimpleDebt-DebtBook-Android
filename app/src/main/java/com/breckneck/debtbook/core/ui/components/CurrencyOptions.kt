package com.breckneck.debtbook.core.ui.components

import java.util.Currency
import util.DeviceCurrency

internal fun currencyOptions(common: List<String>, selected: String): List<String> {
    val commonCodes = common.mapTo(mutableSetOf(), ::currencyCode)
    val current = selected.takeIf { it.isNotBlank() && it !in commonCodes }?.let { code ->
        runCatching { currencyLabel(Currency.getInstance(code)) }.getOrDefault(code)
    }
    val others = DeviceCurrency.currencies.filter { it.currencyCode !in commonCodes && it.currencyCode != selected }
        .map(::currencyLabel)
    return listOfNotNull(current) + common + others
}

internal fun currencyCode(label: String): String = label.substringAfterLast(' ')

private fun currencyLabel(currency: Currency): String = "${currency.displayName} ${currency.currencyCode}"
