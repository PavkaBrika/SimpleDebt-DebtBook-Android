package com.breckneck.deptbook.domain.model

import java.math.BigDecimal

import java.io.Serializable
import java.util.Date

data class Finance(
    var id: Int = 0,
    var sum: BigDecimal,
    var date: Date,
    var info: String?,
    var financeCategoryId: Int,
    var currency: String = "USD",
    var calendarDay: String? = null,
): Serializable {
    val presentationDate: Date get() = calendarDay?.let(com.breckneck.deptbook.domain.util.LedgerDate::localDate) ?: date
}
