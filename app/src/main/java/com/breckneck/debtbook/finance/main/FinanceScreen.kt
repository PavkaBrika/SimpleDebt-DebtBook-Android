package com.breckneck.debtbook.finance.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.FinanceInterval
import com.breckneck.deptbook.domain.util.LedgerDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun FinanceScreen(
    vm: FinanceViewModel,
    hostInsets: PaddingValues,
    host: FinanceFragment.OnButtonClickListener,
    onCurrencyClick: (String) -> Unit,
) {
    val state by vm.summary.collectAsState()
    FinanceContent(
        state = state,
        setPeriod = vm::setPeriod,
        stepPeriod = vm::stepPeriod,
        onCurrencyClick = { onCurrencyClick(state.currency) },
        saveDirection = vm::setDirection,
        onCategory = { host.onFinanceCategoryClick(it.name, it.id, it.state, state.currency, state.period.day) },
        onAdd = { host.onAddFinanceButtonClick(it, state.period.day, state.currency) },
        retry = vm::refresh,
        onTab = host::onTabSelected,
        hostInsets = hostInsets,
    )
}

@Composable
private fun FinanceContent(
    state: FinanceSummary,
    setPeriod: (FinancePeriod) -> Unit,
    stepPeriod: (Int) -> Unit,
    onCurrencyClick: () -> Unit,
    saveDirection: (FinanceCategoryState) -> Unit,
    onCategory: (FinanceCategory) -> Unit,
    onAdd: (FinanceCategoryState) -> Unit,
    retry: () -> Unit,
    onTab: (Int) -> Unit,
    hostInsets: PaddingValues = PaddingValues(0.dp),
) {
    val colors = bubbleColors()
    val directions = FinanceCategoryState.entries
    val pager = rememberPagerState(state.direction.ordinal) { directions.size }
    val selected = directions[pager.targetPage]
    val listStates = listOf(rememberLazyListState(), rememberLazyListState())
    val scope = rememberCoroutineScope()
    var tabAnimation by remember { mutableStateOf<Job?>(null) }
    var choosingPeriod by rememberSaveable { mutableStateOf(false) }
    val today = LedgerDate.today()
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { saveDirection(directions[it]) }
    }

    BubbleScaffold(
        hostInsets = hostInsets,
        topBar = {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(
                        0f to colors.background.copy(alpha = .94f),
                        .6f to colors.background.copy(alpha = .86f),
                        1f to colors.background.copy(alpha = .54f),
                    ),
                )
                    .padding(
                        start = hostInsets.calculateStartPadding(layoutDirection) + 16.dp,
                        end = hostInsets.calculateEndPadding(layoutDirection) + 16.dp,
                        top = hostInsets.calculateTopPadding() + 8.dp,
                        bottom = 4.dp,
                    ),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.finances),
                        Modifier.weight(1f).padding(vertical = 4.dp),
                        color = colors.ink,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.period.day != today) {
                        TextButton(onClick = {
                            setPeriod(state.period.copy(day = today))
                            listStates[pager.currentPage].requestScrollToItem(0)
                        }) { Text(stringResource(R.string.finance_today)) }
                    }
                }
                FinanceDirection(selected) { direction ->
                    tabAnimation?.cancel()
                    tabAnimation = scope.launch { pager.animateScrollToPage(direction.ordinal) }
                }
            }
        },
        bottomAction = {
            val direction = directions[pager.settledPage]
            BubbleButton(
                onClick = { onAdd(direction) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                val label = if (direction == FinanceCategoryState.EXPENSE) R.string.finance_add_expense else R.string.finance_add_income
                Text(stringResource(label), Modifier.padding(start = 8.dp))
            }
        },
        tabs = { BubbleBottomBar(R.id.financeFragment, onTab) },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            FinancePage(
                state = state,
                direction = directions[page],
                listState = listStates[page],
                padding = padding,
                stepPeriod = stepPeriod,
                onPeriodClick = { choosingPeriod = true },
                onCurrencyClick = onCurrencyClick,
                onCategory = onCategory,
                retry = retry,
            )
        }
    }
    if (choosingPeriod) {
        PeriodSheet(state.period, onApply = {
            setPeriod(it)
            choosingPeriod = false
        }, onDismiss = { choosingPeriod = false })
    }
}

@Composable
private fun FinancePage(
    state: FinanceSummary,
    direction: FinanceCategoryState,
    listState: LazyListState,
    padding: PaddingValues,
    stepPeriod: (Int) -> Unit,
    onPeriodClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    onCategory: (FinanceCategory) -> Unit,
    retry: () -> Unit,
) {
    val colors = bubbleColors()
    val totals = state.totalsFor(direction)
    val amount = totals.total.takeIf { state.hasTotals }
    val expenses = direction == FinanceCategoryState.EXPENSE
    LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding), state = listState, contentPadding = padding) {
        item(key = "balance") {
            key(state.currency) {
                BubbleBalanceCard(
                    stringResource(if (expenses) R.string.expenses else R.string.revenues),
                    state.currency,
                    amount?.financeMoney(state.currency) ?: "…",
                    onCurrencyClick = onCurrencyClick,
                    balanceValue = amount,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val arrowColors = IconButtonDefaults.iconButtonColors(
                            containerColor = colors.heroBorder,
                            contentColor = colors.onHero,
                        )
                        IconButton({ stepPeriod(-1) }, colors = arrowColors) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.finance_previous))
                        }
                        TextButton(onPeriodClick, Modifier.weight(1f)) {
                            Icon(Icons.Outlined.DateRange, null, Modifier.size(16.dp), tint = colors.onHero)
                            Spacer(Modifier.width(5.dp))
                            Text(state.period.label(), color = colors.onHero, textAlign = TextAlign.Center)
                        }
                        IconButton({ stepPeriod(1) }, colors = arrowColors) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.finance_next))
                        }
                    }
                }
            }
        }
        item(key = "categories") {
            BubbleSectionTitle(stringResource(R.string.finance_categories), Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(stringResource(state.period.interval.labelResource), color = colors.muted)
            }
        }
        if (state.failed) item(key = "retry") {
            BubbleOutlinedButton(retry) { Text(stringResource(R.string.history_retry)) }
        }
        when {
            state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.hasTotals && totals.categories.isEmpty() -> item {
                Text(
                    stringResource(R.string.finance_empty_period),
                    Modifier.fillMaxWidth().padding(22.dp),
                    textAlign = TextAlign.Center,
                    color = colors.muted,
                )
            }
            else -> itemsIndexed(totals.categories, key = { _, row -> row.category.id }) { index, row ->
                BubbleGroupedRow(index, totals.categories.size, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) {
                    BubbleListRow(
                        Modifier.clickable { onCategory(row.category) }.padding(12.dp),
                        leading = { CategoryBadge(row.category) },
                        trailing = {
                            key(state.currency) {
                                AnimatedAmount(row.amount, row.amount.financeMoney(state.currency), style = MaterialTheme.typography.titleSmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp), tint = colors.muted)
                        },
                    ) {
                        Text(row.category.localizedName(), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.finance_share_of_total, row.percent), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSheet(period: FinancePeriod, onApply: (FinancePeriod) -> Unit, onDismiss: () -> Unit) {
    var interval by rememberSaveable { mutableStateOf(period.interval) }
    var day by rememberSaveable { mutableStateOf(period.day) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var choices by remember { mutableStateOf(false) }
    val draft = FinancePeriod(interval, day)
    val dateLabel = when (interval) {
        FinanceInterval.DAY -> R.string.finance_period_day
        FinanceInterval.WEEK -> R.string.finance_period_week_day
        FinanceInterval.MONTH -> R.string.finance_period_month_day
        FinanceInterval.YEAR -> R.string.finance_period_year_day
    }
    BubbleBottomSheet(onDismiss) { active, close ->
        BubbleSheetHeader(stringResource(R.string.finance_period), close, enabled = active)
        BubbleGroup {
            Box {
                BubbleFieldRow(Icons.Outlined.DateRange, stringResource(R.string.select_interval), Modifier.clickable(enabled = active, role = Role.Button) { choices = true },
                    trailing = { Icon(Icons.Outlined.ExpandMore, null) }) { Text(stringResource(interval.labelResource)) }
                DropdownMenu(choices, { choices = false }) {
                    FinanceInterval.entries.forEach { option ->
                        DropdownMenuItem({ Text(stringResource(option.labelResource)) }, { interval = option; choices = false })
                    }
                }
            }
            HorizontalDivider(color = bubbleColors().border)
            BubbleFieldRow(Icons.Outlined.DateRange, stringResource(dateLabel), Modifier.clickable(enabled = active, role = Role.Button) { calendar = true }) {
                Text(LedgerDate.display(day))
            }
        }
        if (interval != FinanceInterval.DAY) {
            Text(
                stringResource(R.string.finance_period_preview, draft.label()),
                color = bubbleColors().muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        BubbleButton({ onApply(draft) }, Modifier.fillMaxWidth(), enabled = active) {
            Text(stringResource(R.string.finance_apply_period))
        }
    }
    if (calendar) FinanceDatePicker(day, onSelect = { day = it; calendar = false }, onDismiss = { calendar = false })
}

private val FinanceInterval.labelResource: Int
    get() = when (this) {
        FinanceInterval.DAY -> R.string.day
        FinanceInterval.WEEK -> R.string.week
        FinanceInterval.MONTH -> R.string.month
        FinanceInterval.YEAR -> R.string.year_full
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
    val period = FinancePeriod(FinanceInterval.DAY, LedgerDate.today())
    DebtBookTheme {
        FinanceContent(
            FinanceSummary("RUB", FinanceCategoryState.EXPENSE, period, summarizeFinances(emptyList(), "RUB", period)),
            {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
