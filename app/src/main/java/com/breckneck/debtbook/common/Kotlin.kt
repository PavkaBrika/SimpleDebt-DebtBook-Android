package com.breckneck.debtbook.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val String.Companion.empty
        get() = ""

fun Date.toDMYFormat(pattern: String = "d MMM yyyy"): String {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(this)
}
