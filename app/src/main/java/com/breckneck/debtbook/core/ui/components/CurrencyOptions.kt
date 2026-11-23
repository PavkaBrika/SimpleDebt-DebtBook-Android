package com.breckneck.debtbook.core.ui.components

import java.text.Normalizer
import java.util.Currency
import java.util.Locale

internal data class CurrencyChoice(
    val code: String,
    val label: String,
    private val searchText: String,
) {
    fun matches(normalizedQuery: String): Boolean = searchText.contains(normalizedQuery)
}

internal fun currencyChoices(
    common: List<String>,
    selected: String,
    regionalCurrency: String,
    locale: Locale,
): List<CurrencyChoice> {
    val labels = currencyOptions(common, selected).associateBy(::currencyCode)
    val regionalLabel = labels[regionalCurrency] ?: currencyLabel(regionalCurrency)
    val orderedLabels = listOf(regionalLabel) + (labels - regionalCurrency).values
    return orderedLabels.map { label ->
        val code = currencyCode(label)
        val region = currencyRegion(code)
        val country = region?.let { Locale.Builder().setRegion(it).build() } ?: Locale.ROOT
        val currency = runCatching { Currency.getInstance(code) }.getOrNull()
        val name = currencyNames[code] ?: currency?.getDisplayName(Locale.ENGLISH)
        val localizedName = currency?.getDisplayName(locale)
        CurrencyChoice(
            code = code,
            label = label,
            searchText = listOfNotNull(
                code, name, localizedName, country.getDisplayCountry(locale),
                country.getDisplayCountry(Locale.ENGLISH),
            ).joinToString("\n").normalizeCurrencySearch(),
        )
    }
}

internal fun currencyOptions(common: List<String>, selected: String): List<String> {
    val commonCodes = common.mapTo(mutableSetOf(), ::currencyCode)
    val current = selected.takeIf { it.isNotBlank() && it !in commonCodes }
    return listOfNotNull(current?.let(::currencyLabel)) + common +
        currencyRegions.keys.filter { it !in commonCodes && it != current }.map(::currencyLabel)
}

internal fun currencyCode(label: String): String = label.substringAfterLast(' ')

internal fun currencyLabel(code: String): String {
    if (code.isBlank()) return code
    val flag = currencyRegion(code)?.map { Character.toChars(it - 'A' + 0x1F1E6).concatToString() }?.joinToString("")
    return "${flag ?: "🌐"} $code"
}

internal fun String.normalizeCurrencySearch(): String =
    Normalizer.normalize(trim().lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(combiningMarks, "")

private val combiningMarks = Regex("\\p{M}+")
private val countryCodes = Locale.getISOCountries().toSet()
private fun currencyRegion(code: String): String? = when (code) {
    "ANG" -> "CW"
    else -> currencyRegions[code] ?: code.take(2).takeIf { it in countryCodes }
}

// ISO 4217 List One (2026-09-17), with issuing regions from Unicode CLDR 48.
// For shared currencies use the EU flag or a representative issuing country.
private val currencyRegions = """
    AED:AE AFN:AF ALL:AL AMD:AM AOA:AO ARS:AR AUD:AU AWG:AW
    AZN:AZ BAM:BA BBD:BB BDT:BD BHD:BH BIF:BI BMD:BM BND:BN
    BOB:BO BRL:BR BSD:BS BTN:BT BWP:BW BYN:BY BZD:BZ CAD:CA
    CDF:CD CHF:CH CLP:CL CNY:CN COP:CO CRC:CR CUP:CU CVE:CV
    CZK:CZ DJF:DJ DKK:DK DOP:DO DZD:DZ EGP:EG ERN:ER ETB:ET
    EUR:EU FJD:FJ FKP:FK GBP:GB GEL:GE GHS:GH GIP:GI GMD:GM
    GNF:GN GTQ:GT GYD:GY HKD:HK HNL:HN HTG:HT HUF:HU IDR:ID
    ILS:IL INR:IN IQD:IQ IRR:IR ISK:IS JMD:JM JOD:JO JPY:JP
    KES:KE KGS:KG KHR:KH KMF:KM KPW:KP KRW:KR KWD:KW KYD:KY
    KZT:KZ LAK:LA LBP:LB LKR:LK LRD:LR LSL:LS LYD:LY MAD:MA
    MDL:MD MGA:MG MKD:MK MMK:MM MNT:MN MOP:MO MRU:MR MUR:MU
    MVR:MV MWK:MW MXN:MX MYR:MY MZN:MZ NAD:NA NGN:NG NIO:NI
    NOK:NO NPR:NP NZD:NZ OMR:OM PAB:PA PEN:PE PGK:PG PHP:PH
    PKR:PK PLN:PL PYG:PY QAR:QA RON:RO RSD:RS RUB:RU RWF:RW
    SAR:SA SBD:SB SCR:SC SDG:SD SEK:SE SGD:SG SHP:SH SLE:SL
    SOS:SO SRD:SR SSP:SS STN:ST SVC:SV SYP:SY SZL:SZ THB:TH
    TJS:TJ TMT:TM TND:TN TOP:TO TRY:TR TTD:TT TWD:TW TZS:TZ
    UAH:UA UGX:UG USD:US UYU:UY UZS:UZ VED:VE VES:VE VND:VN
    VUV:VU WST:WS XAF:CM XCD:KN XCG:CW XOF:SN XPF:PF YER:YE
    ZAR:ZA ZMW:ZM ZWG:ZW
""".trim().split(Regex("\\s+")).associate { it.substringBefore(':') to it.substringAfter(':') }

// Names for currencies missing from the database on some supported versions of Android.
private val currencyNames = mapOf(
    "BYN" to "Belarusian ruble",
    "MRU" to "Mauritanian ouguiya",
    "SLE" to "Sierra Leonean leone",
    "STN" to "São Tomé and Príncipe dobra",
    "VED" to "Venezuelan digital bolívar",
    "VES" to "Venezuelan sovereign bolívar",
    "XCG" to "Caribbean guilder",
    "ZWG" to "Zimbabwe gold",
)
