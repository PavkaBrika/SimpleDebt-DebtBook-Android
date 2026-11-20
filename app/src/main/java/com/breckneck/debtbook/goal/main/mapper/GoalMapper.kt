package com.breckneck.debtbook.goal.main.mapper

import com.breckneck.debtbook.common.toDMYFormat
import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.deptbook.domain.model.Goal
import com.breckneck.deptbook.domain.util.MoneyPolicy
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun Goal.toUi(): GoalUi {
    val reached = savedSum >= sum
    val percent = when {
        reached -> 100
        savedSum.signum() <= 0 -> 0
        else -> savedSum.movePointRight(2).divide(sum, 0, RoundingMode.DOWN).toInt()
    }
    return GoalUi(
        goalId = id,
        name = name,
        photoPath = photoPath?.takeIf { File(it).isFile },
        savedAmount = savedSum.formatGoalMoney(currency),
        totalAmount = sum.formatGoalMoney(currency),
        currency = currency,
        isReached = reached,
        remainingAmount = (sum - savedSum).max(BigDecimal.ZERO).formatGoalMoney(currency),
        deadline = presentationGoalDate?.toDMYFormat(),
        creationDate = creationDate.toDMYFormat(),
        isOverdue = isPastGoalDay,
        percent = percent,
        hasSavings = savedSum.signum() > 0,
    )
}

internal fun BigDecimal.formatGoalMoney(code: String, showPlus: Boolean = false): String {
    val locale = Locale.getDefault()
    val digits = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = maxOf(MoneyPolicy.scale(code), stripTrailingZeros().scale())
    }
    val symbol = if (code == "RUB") "₽"
        else runCatching { Currency.getInstance(code).getSymbol(locale) }.getOrDefault(code)
    val sign = if (showPlus && signum() > 0) "+" else ""
    return "$sign${digits.format(this)}\u00a0$symbol"
}
