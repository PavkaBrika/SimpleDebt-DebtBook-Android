package util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCurrencyTest {
    @Test fun explicitRegionTakesPriorityOverLanguage() {
        assertEquals("RUB", currency("ru-RU"))
        assertEquals("KZT", currency("ru-KZ"))
        assertEquals("GBP", currency("en-GB"))
        assertEquals("CAD", currency("fr-CA"))
        assertEquals("VND", currency("vi-VN"))
    }

    @Test fun languageAndScriptCanSupplyTheMissingRegion() {
        assertEquals("RUB", currency("ru"))
        assertEquals("VND", currency("vi"))
        assertEquals("TWD", currency("zh-Hant"))
        assertEquals("USD", currency("und"))
    }

    private fun currency(tag: String) = DeviceCurrency.forLocale(Locale.forLanguageTag(tag))
}
