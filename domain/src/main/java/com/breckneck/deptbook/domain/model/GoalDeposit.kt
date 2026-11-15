package com.breckneck.deptbook.domain.model

import java.math.BigDecimal

import java.util.Date

data class GoalDeposit(
    var id: Int = 0,
    var sum: BigDecimal,
    var date: Date,
    var goalId: Int
)