package com.breckneck.deptbook.domain.util

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A day has no time zone. Unknown legacy text remains available verbatim. */
object LedgerDate {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun formatter(pattern: String, locale: Locale) = SimpleDateFormat(pattern, locale).apply {
        isLenient = false
        timeZone = utc
    }
    private fun parse(text: String, pattern: String, locale: Locale): Date? {
        val position = ParsePosition(0)
        return formatter(pattern, locale).parse(text, position)?.takeIf { position.index == text.length }
    }
    fun day(year: Int, month: Int, day: Int): String {
        val calendar = Calendar.getInstance(utc, Locale.ROOT).apply {
            clear()
            isLenient = false
            set(year, month, day)
        }
        return formatter("yyyy-MM-dd", Locale.ROOT).format(calendar.time)
    }
    fun fromDate(date: Date): String = Calendar.getInstance().apply { time = date }.let {
        day(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
    }
    fun localDate(day: String): Date? {
        if (canonical(day) != day) return null
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { isLenient = false }.parse("$day 12:00")
    }
    fun pickerMillis(day: String): Long {
        require(canonical(day) == day)
        return requireNotNull(parse(day, "yyyy-MM-dd", Locale.ROOT)).time
    }
    fun dayFromPickerMillis(millis: Long): String = formatter("yyyy-MM-dd", Locale.ROOT).format(Date(millis))
    fun fromPickerMillis(millis: Long): Date = requireNotNull(localDate(dayFromPickerMillis(millis)))
    fun today(): String = Calendar.getInstance().let {
        day(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
    }
    fun canonical(text: String): String? {
        if (Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(text)) {
            return parse(text, "yyyy-MM-dd", Locale.ROOT)?.let { text }
        }
        // Numeric dates such as 01/02/2024 are deliberately never guessed.
        val candidates = mutableSetOf<String>()
        for (locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"), Locale.GERMAN, Locale.FRENCH)) {
            for (pattern in listOf("d MMM yyyy", "d MMM yyyy 'г'.", "d MMM yyyy 'г.'", "d MMM yyyy 'j.'")) {
                parse(text, pattern, locale)?.let {
                    candidates += formatter("yyyy-MM-dd", Locale.ROOT).format(it)
                }
            }
        }
        return candidates.singleOrNull()
    }
    fun display(text: String, locale: Locale = Locale.getDefault()): String =
        canonical(text)?.let { parse(it, "yyyy-MM-dd", Locale.ROOT) }
            ?.let { formatter("d MMM yyyy", locale).format(it) } ?: text

    // Known days sort chronologically; unresolved legacy strings sort stably after them.
    fun sortKey(text: String): String = canonical(text)?.let { "0:$it" } ?: "1:$text"
}
