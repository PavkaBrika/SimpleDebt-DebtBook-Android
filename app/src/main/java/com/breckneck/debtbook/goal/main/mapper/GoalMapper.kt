package com.breckneck.debtbook.goal.main.mapper

import com.breckneck.debtbook.common.toDMYFormat
import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.deptbook.domain.model.Goal
import com.breckneck.deptbook.domain.util.MoneyDisplay
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale
import kotlin.math.nextDown

fun Goal.toUi(): GoalUi {
    val reached = savedSum >= sum
    val fraction = when {
        reached -> BigDecimal.ONE
        savedSum.signum() <= 0 -> BigDecimal.ZERO
        else -> savedSum.divide(sum, 16, RoundingMode.DOWN)
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
        percent = fraction.movePointRight(2).toInt(),
        savedValue = savedSum,
        progress = if (reached) 1f else fraction.toFloat().coerceAtMost(1f.nextDown()),
    )
}

internal fun BigDecimal.formatGoalMoney(code: String, showPlus: Boolean = false): String {
    val locale = Locale.getDefault()
    val amount = MoneyDisplay.format(this, code, locale)
    val symbol = if (code == "RUB") "₽"
        else runCatching { Currency.getInstance(code).getSymbol(locale) }.getOrDefault(code)
    val sign = if (showPlus && signum() > 0) "+" else ""
    return "$sign$amount\u00a0$symbol"
}
