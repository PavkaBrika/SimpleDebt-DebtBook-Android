package com.breckneck.debtbook.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.util.GetFinanceCategoryNameInLocalLanguage
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.util.FinanceCategoryState

@Composable
internal fun FinanceDirection(selected: FinanceCategoryState, select: (FinanceCategoryState) -> Unit) {
    val colors = bubbleColors()
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(colors.paper).padding(3.dp).selectableGroup()) {
        for (direction in listOf(FinanceCategoryState.EXPENSE, FinanceCategoryState.INCOME)) {
            val active = direction == selected
            Box(Modifier.weight(1f).clip(CircleShape).background(if (active) colors.action else colors.paper)
                .selectable(active, role = Role.Tab) { if (!active) select(direction) }
                .heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(if (direction == FinanceCategoryState.EXPENSE) R.string.expenses else R.string.revenues),
                    color = if (active) colors.onAction else colors.ink, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
internal fun FinanceCategory.localizedName() = GetFinanceCategoryNameInLocalLanguage().execute(name, state, LocalContext.current)

@Composable
internal fun CategoryBadge(category: FinanceCategory) {
    Box(Modifier.size(34.dp).background(Color(category.color.toColorInt()), CircleShape), contentAlignment = Alignment.Center) {
        Text(String(Character.toChars(category.image)), color = Color.Black)
    }
}

internal fun java.math.BigDecimal.financeMoney(currency: String): String {
    val symbol = if (currency == "RUB") "₽" else runCatching {
        java.util.Currency.getInstance(currency).symbol
    }.getOrDefault(currency)
    return com.breckneck.deptbook.domain.util.MoneyDisplay.formatExact(this, currency) + "\u00a0" + symbol
}
