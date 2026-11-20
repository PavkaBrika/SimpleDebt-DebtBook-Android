package com.breckneck.debtbook.goal.main.mapper

import com.breckneck.deptbook.domain.util.MoneyDisplay
import com.breckneck.deptbook.domain.util.compareTo

import android.content.Context
import com.breckneck.debtbook.R
import com.breckneck.debtbook.common.toDMYFormat
import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.deptbook.domain.model.Goal
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

fun Goal.toUi(context: Context): GoalUi {
    val isReached = sum - savedSum <= 0

    val dateChipText: String?
    val isOverdue: Boolean

    when {
        isReached -> {
            val diffInDays = TimeUnit.DAYS
                .convert(Date().time - creationDate.time, TimeUnit.MILLISECONDS)
                .coerceAtLeast(1L)

            dateChipText = if (diffInDays == 1L) {
                context.getString(R.string.achieved_in_day, diffInDays)
            } else {
                context.getString(R.string.achieved_in_days, diffInDays)
            }

            isOverdue = false
        }

        presentationGoalDate != null -> {
            val date = presentationGoalDate!!

            isOverdue = isPastGoalDay

            dateChipText = if (isOverdue) {
                context.getString(R.string.overdue)
            } else {
                date.toDMYFormat()
            }
        }

        else -> {
            dateChipText = null
            isOverdue = false
        }
    }

    return GoalUi(
        goalId = id,
        name = name,
        photoPath = photoPath,
        hasPhoto = photoPath != null && File(photoPath!!).exists(),
        savedAmount = MoneyDisplay.format(savedSum, currency),
        totalAmount = MoneyDisplay.format(sum, currency),
        currency = currency,
        isReached = isReached,
        remainingAmount = if (!isReached) MoneyDisplay.format(sum - savedSum, currency) else null,
        date = dateChipText,
        isOverdue = isOverdue,
    )
}
