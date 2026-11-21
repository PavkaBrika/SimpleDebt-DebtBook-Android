package com.breckneck.debtbook.finance.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.*
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.util.*
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
            verticalArrangement = Arrangement.Top) {
            item {
                Text(stringResource(R.string.finances), color = colors.ink, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
            item { Box(Modifier.padding(vertical = 8.dp)) { FinanceDirection(state.direction, selectDirection) } }
            item {
                BubbleBalanceCard(direction, state.currency,
                    if (state.loading || state.failed) "…" else state.total.financeMoney(state.currency),
                    onCurrencyClick = { picker = R.string.select_currency }, balanceValue = state.total.takeUnless { state.loading || state.failed }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ setPeriod(period.copy(day = period.step(-1))) },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = colors.heroBorder, contentColor = colors.onHero)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.finance_previous))
                        }
                        TextButton({ picker = R.string.select_interval }, Modifier.weight(1f)) {
                            Icon(Icons.Outlined.DateRange, null, Modifier.size(16.dp), tint = colors.onHero)
                            Spacer(Modifier.width(5.dp))
                            AnimatedContent(period, transitionSpec = {
                                val sign = if (targetState.day < initialState.day) -1 else 1
                                (slideInHorizontally(tween(180)) { sign * slideDistance } + fadeIn(tween(180))) togetherWith fadeOut(tween(100))
                            }, label = "Period") { Text(it.label(), color = colors.onHero) }
                        }
                        IconButton({ setPeriod(period.copy(day = period.step(1))) },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = colors.heroBorder, contentColor = colors.onHero)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.finance_next))
                        }
                    }
                }
            }
            item {
                BubbleSectionTitle(stringResource(R.string.finance_categories), Modifier.padding(top = 16.dp, bottom = 8.dp)) { Text(intervals[period.interval.ordinal], color = colors.muted) }
            }
            when {
                state.failed -> item { BubbleOutlinedButton(retry) { Text(stringResource(R.string.history_retry)) } }
                state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.categories.isEmpty() -> item { Text(stringResource(R.string.finance_empty_period), Modifier.fillMaxWidth().padding(22.dp),
                    textAlign = TextAlign.Center, color = colors.muted) }
                else -> itemsIndexed(state.categories, key = { _, row -> row.category.id }) { index, row ->
                    BubbleGroupedRow(index, state.categories.size, Modifier.animateItem(fadeInSpec = tween(260))) {
                        BubbleListRow(Modifier.clickable { onCategory(row.category) }.padding(12.dp),
                            leading = { CategoryBadge(row.category) },
                            trailing = {
                                AnimatedAmount(row.amount, row.amount.financeMoney(state.currency), style = MaterialTheme.typography.titleSmall)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp), tint = colors.muted)
                            }) {
                            Text(row.category.localizedName(), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.finance_share_of_total, row.percent), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (period.day != LedgerDate.today()) item {
                BubbleOutlinedButton({ setPeriod(period.copy(day = LedgerDate.today())) }) { Text(stringResource(R.string.finance_today)) }
            }
        }
        BubbleButton(onAdd, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Text(stringResource(if (expenses) R.string.finance_add_expense else R.string.finance_add_income), Modifier.padding(start = 8.dp))
        }
        BubbleBottomBar(R.id.financeFragment, onTab)
    }
    when (picker) {
        R.string.select_interval -> PeriodSheet(period, intervals, onApply = { setPeriod(it); picker = 0 }, onDismiss = { picker = 0 })
        R.string.select_currency -> {
            val options = stringArrayResource(R.array.currencies).toList()
            SettingsPickerBottomSheet(stringResource(picker), options,
                options.indexOfFirst { it.substringAfterLast(' ') == state.currency },
                onItemSelected = { setCurrency(options[it].substringAfterLast(' ')); picker = 0 }, onDismiss = { picker = 0 })
        }
    }
}

@Composable
private fun PeriodSheet(period: FinancePeriod, intervals: List<String>, onApply: (FinancePeriod) -> Unit, onDismiss: () -> Unit) {
    var interval by rememberSaveable { mutableStateOf(period.interval) }
    var day by rememberSaveable { mutableStateOf(period.day) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var choices by remember { mutableStateOf(false) }
    BubbleBottomSheet(onDismiss) { active, close ->
        BubbleSheetHeader(stringResource(R.string.finance_period), close, enabled = active)
        BubbleGroup {
            Box {
                BubbleFieldRow(Icons.Outlined.DateRange, stringResource(R.string.select_interval), Modifier.clickable { choices = true },
                    trailing = { Icon(Icons.Outlined.ExpandMore, null) }) { Text(intervals[interval.ordinal]) }
                DropdownMenu(choices, { choices = false }) {
                    intervals.forEachIndexed { index, label ->
                        DropdownMenuItem({ Text(label) }, { interval = FinanceInterval.entries[index]; choices = false })
                    }
                }
            }
            HorizontalDivider(color = bubbleColors().border)
            BubbleFieldRow(Icons.Outlined.DateRange, stringResource(R.string.finance_period_day), Modifier.clickable { calendar = true }) {
                Text(LedgerDate.display(day))
            }
        }
        BubbleButton({ onApply(FinancePeriod(interval, day)) }, Modifier.fillMaxWidth(), enabled = active) {
            Text(stringResource(R.string.finance_apply_period))
        }
    }
    if (calendar) FinanceDatePicker(day, onSelect = { day = it; calendar = false }, onDismiss = { calendar = false })
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
