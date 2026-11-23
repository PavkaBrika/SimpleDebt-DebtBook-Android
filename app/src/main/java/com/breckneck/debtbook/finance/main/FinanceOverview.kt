package com.breckneck.debtbook.finance.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.AnimatedAmount
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.financeActionColors
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.deptbook.domain.util.FinanceCategoryState

@Composable
internal fun FinanceOverview(state: FinanceSummary) {
    val colors = bubbleColors()
    val income = state.totalsFor(FinanceCategoryState.INCOME).total
    val expenses = state.totalsFor(FinanceCategoryState.EXPENSE).total
    val net = income - expenses
    val netColor = when (net.signum()) {
        1 -> financeActionColors(FinanceCategoryState.INCOME).containerColor
        -1 -> financeActionColors(FinanceCategoryState.EXPENSE).containerColor
        else -> colors.ink
    }
    val netStyle = MaterialTheme.typography.titleLarge.copy(color = netColor, fontWeight = FontWeight.SemiBold)

    val headingContent: @Composable () -> Unit = {
        Text(
            stringResource(R.string.finance_period_total),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
        )
    }
    val netValue: @Composable () -> Unit = {
        if (state.hasTotals) {
            val text = (if (net.signum() > 0) "+" else "") + net.financeMoney(state.currency)
            key(state.currency) { AnimatedAmount(net, text, style = netStyle) }
        } else {
            Text("…", style = netStyle)
        }
    }
    val incomeValue: @Composable (Modifier) -> Unit = { modifier ->
        OverviewValue(
            stringResource(R.string.revenues),
            if (state.hasTotals) income.financeMoney(state.currency) else "…",
            modifier,
        )
    }
    val expenseValue: @Composable (Modifier) -> Unit = { modifier ->
        OverviewValue(
            stringResource(R.string.expenses),
            if (state.hasTotals) expenses.financeMoney(state.currency) else "…",
            modifier,
        )
    }

    BubbleGroup {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (maxWidth >= 360.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1.25f)) {
                        headingContent()
                        netValue()
                    }
                    incomeValue(Modifier.weight(1f))
                    expenseValue(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        headingContent()
                        netValue()
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        incomeValue(Modifier.weight(1f))
                        expenseValue(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = bubbleColors().muted, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
