package com.breckneck.debtbook.finance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.LocalDebtBookDarkTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.util.GetFinanceCategoryNameInLocalLanguage
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import java.math.BigDecimal
import java.util.Currency

internal val financeDirections = listOf(FinanceCategoryState.INCOME, FinanceCategoryState.EXPENSE)

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
    val haptic = LocalHapticFeedback.current
    val action = financeActionColors(selected)
    val tabColor by animateColorAsState(action.containerColor, tween(200), label = "Direction color")
    BoxWithConstraints(Modifier.fillMaxWidth().clip(CircleShape).background(colors.fill).padding(4.dp)) {
        val width = maxWidth / financeDirections.size
        val offset by animateDpAsState(width * financeDirections.indexOf(selected), tween(200), label = "Direction")
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), 0) }.width(width).fillMaxHeight().background(tabColor, CircleShape))
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            for (direction in financeDirections) {
                val active = direction == selected
                Box(Modifier.weight(1f).clip(CircleShape)
                    .selectable(active, enabled = enabled, role = Role.Tab) {
                        if (!active) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            select(direction)
                        }
                    }
                    .heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (direction == FinanceCategoryState.EXPENSE) R.string.expenses else R.string.revenues),
                        color = if (active) action.contentColor else colors.ink, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
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
