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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.FinanceInterval
import com.breckneck.deptbook.domain.util.LedgerDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun FinanceScreen(
    vm: FinanceViewModel,
    hostInsets: PaddingValues,
    host: FinanceFragment.OnButtonClickListener,
    onCurrencyClick: (String) -> Unit,
) {
    val state by vm.summary.collectAsState()
    val entryDay = state.period.entryDay(LedgerDate.today())
    FinanceContent(
        state = state,
        setPeriod = vm::setPeriod,
        stepPeriod = vm::stepPeriod,
        onCurrencyClick = { onCurrencyClick(state.currency) },
        saveDirection = vm::setDirection,
        onCategory = { host.onFinanceCategoryClick(it.name, it.id, it.state, state.currency, entryDay) },
        onAdd = { host.onAddFinanceButtonClick(it, entryDay, state.currency) },
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
    val directions = remember { listOf(FinanceCategoryState.INCOME, FinanceCategoryState.EXPENSE) }
    val pager = key(directions) {
        rememberPagerState(directions.indexOf(state.direction)) { directions.size }
    }
    val selected = directions[pager.targetPage]
    val listStates = key(directions) { listOf(rememberLazyListState(), rememberLazyListState()) }
    val scope = rememberCoroutineScope()
    var tabAnimation by remember { mutableStateOf<Job?>(null) }
    var choosingPeriod by rememberSaveable { mutableStateOf(false) }
    val today = LedgerDate.today()
    val currentPeriodAction: (() -> Unit)? =
        if (state.period.isCurrent(today)) null else ({ setPeriod(state.period.atToday(today)) })
    val layoutDirection = LocalLayoutDirection.current
    val windowSize = LocalWindowInfo.current.containerSize
    val shortWindow = with(LocalDensity.current) { windowSize.height.toDp() < 420.dp }
    val headerInRow = shortWindow && with(LocalDensity.current) { windowSize.width.toDp() >= 540.dp }

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
                val title: @Composable () -> Unit = {
                    Text(
                        stringResource(R.string.finances),
                        Modifier.padding(vertical = 4.dp),
                        color = colors.ink,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (headerInRow) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        title()
                        Box(Modifier.weight(1f)) { FinanceOverview(state) }
                    }
                } else {
                    if (!shortWindow) title()
                    FinanceOverview(state)
                }
                Spacer(Modifier.height(8.dp))
                FinanceDirection(selected, directions) { direction ->
                    tabAnimation?.cancel()
                    tabAnimation = scope.launch { pager.animateScrollToPage(directions.indexOf(direction)) }
                }
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
                pinAddAction = !shortWindow,
                stepPeriod = stepPeriod,
                onPeriodClick = { choosingPeriod = true },
                onCurrentPeriod = currentPeriodAction,
                onCurrencyClick = onCurrencyClick,
                onCategory = onCategory,
                onAdd = { onAdd(directions[page]) },
                retry = retry,
            )
        }
    }
    if (choosingPeriod) {
        FinancePeriodSheet(state.period, onApply = {
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
    pinAddAction: Boolean,
    stepPeriod: (Int) -> Unit,
    onPeriodClick: () -> Unit,
    onCurrentPeriod: (() -> Unit)?,
    onCurrencyClick: () -> Unit,
    onCategory: (FinanceCategory) -> Unit,
    onAdd: () -> Unit,
    retry: () -> Unit,
) {
    val colors = bubbleColors()
    val totals = state.totalsFor(direction)
    val amount = totals.total.takeIf { state.hasTotals }
    val expenses = direction == FinanceCategoryState.EXPENSE
    val palette = financeBalancePalette(direction)
    val layout = LocalLayoutDirection.current
    val start = padding.calculateStartPadding(layout)
    val end = padding.calculateEndPadding(layout)
    val addAction: @Composable (Modifier) -> Unit = { modifier ->
        BubbleButton(onClick = onAdd, modifier = modifier, colors = financeActionColors(direction)) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            val label = if (expenses) R.string.finance_add_expense else R.string.finance_add_income
            Text(stringResource(label), Modifier.padding(start = 8.dp))
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (pinAddAction) {
                val belowAction = (padding.calculateBottomPadding() - 16.dp).coerceAtLeast(0.dp)
                Box(Modifier.padding(start = start, end = end, bottom = belowAction)) {
                    addAction(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
            }
        },
    ) { bars ->
        val listPadding = if (pinAddAction) {
            PaddingValues(
                start = start,
                top = padding.calculateTopPadding(),
                end = end,
                bottom = bars.calculateBottomPadding() + 16.dp,
            )
        } else {
            padding
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(listPadding),
            state = listState,
            contentPadding = listPadding,
        ) {
            item(key = "balance") {
                key(state.currency) {
                    BubbleBalanceCard(
                        stringResource(if (expenses) R.string.expenses else R.string.revenues),
                        state.currency,
                        amount?.financeMoney(state.currency) ?: "…",
                        onCurrencyClick = onCurrencyClick,
                        balanceValue = amount,
                        palette = palette,
                        artwork = { FinanceSummaryArtwork(direction, palette, it) },
                    ) {
                        FinancePeriodControls(
                            label = state.period.label(),
                            palette = palette,
                            onPrevious = { stepPeriod(-1) },
                            onNext = { stepPeriod(1) },
                            onSelectPeriod = onPeriodClick,
                            onCurrentPeriod = onCurrentPeriod,
                        )
                    }
                }
            }
            if (!pinAddAction) {
                item(key = "add-operation") { addAction(Modifier.fillMaxWidth().padding(top = 8.dp)) }
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
}

@Preview
@Composable
private fun FinancePreview() {
    val period = FinancePeriod(FinanceInterval.DAY, LedgerDate.today())
    DebtBookTheme {
        FinanceContent(
            FinanceSummary("RUB", FinanceCategoryState.INCOME, period, summarizeFinances(emptyList(), "RUB", period)),
            {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
