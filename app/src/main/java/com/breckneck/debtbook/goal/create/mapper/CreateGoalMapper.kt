package com.breckneck.debtbook.goal.create.mapper

import java.math.BigDecimal

import com.breckneck.debtbook.common.empty
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.debtbook.goal.create.model.CreateGoalUi
import com.breckneck.deptbook.domain.model.Goal
import java.util.Calendar

fun Goal.toCreateGoalUi(): CreateGoalUi =
    CreateGoalUi(
        name = name,
        sum = DecimalInput.format(sum, currency = currency),
        savedSum = if (savedSum.signum() != 0) DecimalInput.format(savedSum, currency = currency) else String.empty,
        currency = currency,
        goalDate = presentationGoalDate,
        imagePath = photoPath,
    )

fun CreateGoalUi.toDomain(
    sum: BigDecimal,
    savedSum: BigDecimal,
    photoPath: String?,
    originalGoal: Goal? = null,
): Goal =
    Goal(
        id = originalGoal?.id ?: 0,
        name = name.trim(),
        sum = sum,
        savedSum = savedSum,
        currency = currency,
        photoPath = photoPath,
        creationDate = originalGoal?.creationDate ?: Calendar.getInstance().time,
        goalDate = goalDate,
        goalDay = goalDate?.let(com.breckneck.deptbook.domain.util.LedgerDate::fromDate),
    )
