package com.breckneck.deptbook.domain.model

import java.math.BigDecimal

import java.io.Serializable
import java.util.Date

data class Goal(
    val id: Int = 0,
    var name: String,
    var sum: BigDecimal,
    var savedSum: BigDecimal,
    var currency: String,
    var photoPath: String?,
    var creationDate: Date,
    var goalDate: Date?,
    var openingAdjustment: BigDecimal = savedSum
) : Serializable