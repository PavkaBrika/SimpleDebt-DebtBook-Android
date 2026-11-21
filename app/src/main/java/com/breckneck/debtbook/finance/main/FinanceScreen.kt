package com.breckneck.debtbook.finance.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.*
import com.breckneck.deptbook.domain.util.*
import com.breckneck.deptbook.domain.model.FinanceCategory
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun FinanceScreen(vm: FinanceViewModel, host: FinanceFragment.OnButtonClickListener) {
    val state by vm.summary.collectAsState()
    FinanceContent(state, vm::setPeriod, vm::setCurrency, vm::setDirection,
        onCategory = { host.onFinanceCategoryClick(it.name, it.id, it.state, state.currency, state.period.day) },
        onAdd = { host.onAddFinanceButtonClick(state.direction, state.period.day, state.currency) },
        retry = vm::refresh, onTab = host::onTabSelected)
}

@Composable
private fun FinanceContent(state: FinanceSummary, setPeriod: (FinancePeriod) -> Unit, setCurrency: (String) -> Unit,
    selectDirection: (FinanceCategoryState) -> Unit, onCategory: (FinanceCategory) -> Unit,
    onAdd: () -> Unit, retry: () -> Unit, onTab: (Int) -> Unit) {
    val colors = bubbleColors()
    val expenses = state.direction == FinanceCategoryState.EXPENSE
    val direction = stringResource(if (expenses) R.string.expenses else R.string.revenues)
    val intervals = listOf(R.string.day, R.string.week, R.string.month, R.string.year_full).map { stringResource(it) }
    var picker by rememberSaveable { mutableIntStateOf(0) }
    val slideDistance = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.roundToPx() }
    val period = state.period
    Column(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(stringResource(R.string.finances), color = colors.ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
            item { FinanceDirection(state.direction, selectDirection) }
            item {
                BubbleBalanceCard(direction, state.currency,
                    if (state.loading || state.failed) "…" else state.total.financeMoney(state.currency),
                    onCurrencyClick = { picker = R.string.select_currency }, balanceValue = state.total.takeUnless { state.loading || state.failed }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ setPeriod(period.copy(day = period.step(-1))) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.finance_previous))
                        }
                        TextButton({ picker = R.string.select_interval }, Modifier.weight(1f)) {
                            AnimatedContent(period, transitionSpec = {
                                val sign = if (targetState.day < initialState.day) -1 else 1
                                (slideInHorizontally(tween(180)) { sign * slideDistance } + fadeIn(tween(180))) togetherWith fadeOut(tween(100))
                            }, label = "Period") { Text(it.label(), color = colors.onHero) }
                        }
                        IconButton({ setPeriod(period.copy(day = period.step(1))) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.finance_next))
                        }
                    }
                }
            }
            item {
                BubbleSectionTitle(stringResource(R.string.finance_categories)) { Text(intervals[period.interval.ordinal], color = colors.muted) }
            }
            when {
                state.failed -> item { BubbleOutlinedButton(retry) { Text(stringResource(R.string.history_retry)) } }
                state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.categories.isEmpty() -> item { Text(stringResource(R.string.finance_empty_period), color = colors.muted) }
                else -> items(state.categories, key = { it.category.id }) { row ->
                    BubbleGroup(Modifier.animateItem(fadeInSpec = tween(260))) {
                        BubbleListRow(Modifier.clickable { onCategory(row.category) }.padding(12.dp),
                            leading = { CategoryBadge(row.category) },
                            trailing = { AnimatedAmount(row.amount, row.amount.financeMoney(state.currency)) }) {
                            Text(row.category.localizedName(), fontWeight = FontWeight.Bold)
                            Text("${row.percent}%", color = colors.muted)
                        }
                    }
                }
            }
            if (period.day != LedgerDate.today()) item {
                BubbleOutlinedButton({ setPeriod(period.copy(day = LedgerDate.today())) }) { Text(stringResource(R.string.finance_today)) }
            }
        }
        BubbleButton(onAdd, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(stringResource(if (expenses) R.string.finance_add_expense else R.string.finance_add_income))
        }
        BubbleBottomBar(R.id.financeFragment, onTab)
    }
    if (picker == R.string.goal_choose_date) {
        FinanceDatePicker(period.day, onSelect = { setPeriod(period.copy(day = it)); picker = 0 }, onDismiss = { picker = 0 })
    } else if (picker != 0) {
        val currencyPicker = picker == R.string.select_currency
        val options = if (currencyPicker) stringArrayResource(R.array.currencies).toList() else intervals + stringResource(R.string.goal_choose_date)
        SettingsPickerBottomSheet(stringResource(picker), options,
            if (currencyPicker) options.indexOfFirst { it.substringAfterLast(' ') == state.currency } else period.interval.ordinal,
            onItemSelected = {
                if (!currencyPicker && it == intervals.size) {
                    picker = R.string.goal_choose_date
                } else {
                    if (currencyPicker) setCurrency(options[it].substringAfterLast(' '))
                    else setPeriod(period.copy(interval = FinanceInterval.entries[it]))
                    picker = 0
                }
            }, onDismiss = { picker = 0 })
    }
}

private fun FinancePeriod.label(): String = when (interval) {
    FinanceInterval.DAY -> LedgerDate.display(day)
    FinanceInterval.WEEK -> days().let { LedgerDate.display(it.start) + " – " + LedgerDate.display(it.endInclusive) }
    else -> SimpleDateFormat(if (interval == FinanceInterval.MONTH) "MMMM yyyy" else "yyyy", Locale.getDefault())
        .format(requireNotNull(LedgerDate.localDate(day)))
}

@Preview
@Composable
private fun FinancePreview() {
    DebtBookTheme { FinanceContent(FinanceSummary("RUB", FinanceCategoryState.EXPENSE,
        FinancePeriod(FinanceInterval.DAY, LedgerDate.today()), loading = false), {}, {}, {}, {}, {}, {}, {}) }
}
