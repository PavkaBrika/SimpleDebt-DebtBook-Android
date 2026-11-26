package com.breckneck.debtbook.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleDirectionTabs
import com.breckneck.debtbook.core.ui.theme.LocalDebtBookDarkTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.util.GetFinanceCategoryNameInLocalLanguage
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import java.math.BigDecimal
import java.util.Currency

internal val financeDirections = listOf(FinanceCategoryState.EXPENSE, FinanceCategoryState.INCOME)

@Composable
internal fun financeActionColors(direction: FinanceCategoryState): ButtonColors {
    val colors = bubbleColors()
    if (direction == FinanceCategoryState.INCOME) {
        return ButtonDefaults.buttonColors(containerColor = colors.action, contentColor = colors.onAction)
    }
    return if (LocalDebtBookDarkTheme.current) {
        ButtonDefaults.buttonColors(containerColor = Color(0xFFF1AD99), contentColor = Color(0xFF3F2020))
    } else {
        ButtonDefaults.buttonColors(containerColor = Color(0xFF783B39), contentColor = Color(0xFFFFF4EB))
    }
}

@Composable
internal fun FinanceDirection(
    selected: FinanceCategoryState,
    enabled: Boolean = true,
    select: (FinanceCategoryState) -> Unit,
) {
    val colors = bubbleColors()
    val action = financeActionColors(selected)
    BubbleDirectionTabs(financeDirections, selected, action.containerColor, enabled, select) { direction, active ->
        Text(
            stringResource(if (direction == FinanceCategoryState.EXPENSE) R.string.expenses else R.string.revenues),
            color = if (active) action.contentColor else colors.ink,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
internal fun FinanceCategory.localizedName() = GetFinanceCategoryNameInLocalLanguage().execute(name, state, LocalContext.current)

@Composable
internal fun CategoryBadge(category: FinanceCategory, modifier: Modifier = Modifier) {
    Box(modifier.size(38.dp).background(category.badgeColor, CircleShape), contentAlignment = Alignment.Center) {
        Text(String(Character.toChars(category.image)), color = Color.Black)
    }
}

internal val FinanceCategory.badgeColor: Color get() = Color(color.toColorInt())

internal fun BigDecimal.financeMoney(currency: String): String {
    val symbol = if (currency == "RUB") "₽" else runCatching {
        Currency.getInstance(currency).symbol
    }.getOrDefault(currency)
    return MoneyDisplay.formatExact(this, currency) + "\u00a0" + symbol
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceDatePicker(day: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val compact = LocalConfiguration.current.let { it.screenWidthDp < 380 || it.screenHeightDp < 560 || it.fontScale > 1.2f }
    val picker = rememberDatePickerState(initialSelectedDateMillis = LedgerDate.pickerMillis(day),
        initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton({ picker.selectedDateMillis?.let { onSelect(LedgerDate.dayFromPickerMillis(it)) } },
            enabled = picker.selectedDateMillis != null) { Text(stringResource(android.R.string.ok)) }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }) {
        DatePicker(picker, showModeToggle = !compact)
    }
}
