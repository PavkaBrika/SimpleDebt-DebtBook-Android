package util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.icu.util.ULocale
import android.os.Build
import androidx.core.os.ConfigurationCompat
import java.util.Currency
import java.util.Locale

object DeviceCurrency {
    fun get(context: Context): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.systemLocales?.get(0)
        } else {
            ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]
        }
        return forLocale(locale ?: Locale.getDefault())
    }

    internal fun forLocale(locale: Locale): String {
        if (locale.country.length == 2) return forCountry(locale.country)?.currencyCode ?: "USD"
        if (locale.language.isEmpty()) return "USD"

        val currency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val language = Locale.Builder().setLanguage(locale.language).setScript(locale.script).build()
            forCountry(ULocale.addLikelySubtags(ULocale.forLocale(language)).country)
        } else {
            currencyForLanguage(locale)
        }
        return currency?.currencyCode ?: "USD"
    }

    private fun currencyForLanguage(locale: Locale): Currency? {
        // Android 6 lacks the public ICU API. System locale choices usually include the country.
        val country = when (locale.language) {
            "ar" -> "EG"
            "de" -> "DE"
            "en" -> "US"
            "es" -> "ES"
            "fr" -> "FR"
            "it" -> "IT"
            "nl" -> "NL"
            "pt" -> "BR"
            "ru" -> "RU"
            "sv" -> "SE"
            "uk" -> "UA"
            "vi" -> "VN"
            "zh" -> if (locale.script == "Hant") "TW" else "CN"
            else -> null
        }
        if (country != null) return forCountry(country)

        return Locale.getAvailableLocales().asSequence()
            .filter { it.language == locale.language && (locale.script.isEmpty() || it.script == locale.script) }
            .mapNotNull { forCountry(it.country) }
            .distinct()
            .singleOrNull()
    }

    private fun forCountry(country: String): Currency? =
        runCatching { Currency.getInstance(Locale.Builder().setRegion(country).build()) }.getOrNull()
            ?.takeIf { it.defaultFractionDigits >= 0 }

    val currencies: List<Currency> by lazy {
        Locale.getISOCountries().mapNotNull(::forCountry).distinct().sortedBy { it.currencyCode }
    }
}
