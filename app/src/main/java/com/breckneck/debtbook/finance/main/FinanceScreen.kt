package com.breckneck.debtbook.finance.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.flow.first
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
    val directions = financeDirections
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
                        top = hostInsets.calculateTopPadding() + bubbleRootTitleTopPadding,
                        bottom = 4.dp,
                    ),
            ) {
                if (headerInRow) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        BubbleRootTitle(stringResource(R.string.finances))
                        Box(Modifier.weight(1f)) { FinanceOverview(state) }
                    }
                } else {
                    BubbleRootTitle(stringResource(R.string.finances))
                    FinanceOverview(state)
                }
                Spacer(Modifier.height(8.dp))
                FinanceDirection(selected) { direction ->
                    tabAnimation?.cancel()
                    tabAnimation = scope.launch { pager.animateScrollToPage(directions.indexOf(direction)) }
                }
            }
        },
        bottomAction = if (shortWindow) null else {
            { FinanceAddButton(selected, { onAdd(selected) }, Modifier.fillMaxWidth()) }
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
                inlineAddAction = if (shortWindow) ({ onAdd(directions[page]) }) else null,
                stepPeriod = stepPeriod,
                onPeriodClick = { choosingPeriod = true },
                onCurrentPeriod = currentPeriodAction,
                onCurrencyClick = onCurrencyClick,
                onCategory = onCategory,
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
private fun FinanceAddButton(direction: FinanceCategoryState, onAdd: () -> Unit, modifier: Modifier) {
    BubbleButton(onAdd, modifier.testTag("addFinanceButton"), colors = financeActionColors(direction)) {
        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
        val label = if (direction == FinanceCategoryState.EXPENSE) R.string.finance_add_expense else R.string.finance_add_income
        Text(stringResource(label), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FinancePage(
    state: FinanceSummary,
    direction: FinanceCategoryState,
    listState: LazyListState,
    padding: PaddingValues,
    inlineAddAction: (() -> Unit)?,
    stepPeriod: (Int) -> Unit,
    onPeriodClick: () -> Unit,
    onCurrentPeriod: (() -> Unit)?,
    onCurrencyClick: () -> Unit,
    onCategory: (FinanceCategory) -> Unit,
    retry: () -> Unit,
) {
    val colors = bubbleColors()
    val totals = state.totalsFor(direction)
    val amount = totals.total.takeIf { state.hasTotals }
    val expenses = direction == FinanceCategoryState.EXPENSE
    val palette = financeBalancePalette(direction)
    var sharesOpen by rememberSaveable(direction) { mutableStateOf(false) }
    val hasCategories = totals.categories.isNotEmpty()
    val sharesExpanded = sharesOpen && hasCategories
    val expansion = animateFloatAsState(if (sharesExpanded) 1f else 0f, tween(460), label = "Category shares")
    val chartRequester = remember { BringIntoViewRequester() }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var revealChart by remember { mutableStateOf(false) }
    LaunchedEffect(state.hasTotals, hasCategories) {
        if (state.hasTotals && !hasCategories) sharesOpen = false
    }
    val chartBottomInset = with(LocalDensity.current) { padding.calculateBottomPadding().toPx() }
    LaunchedEffect(sharesExpanded, revealChart) {
        if (sharesExpanded && revealChart) {
            snapshotFlow { expansion.value }.first { it == 1f }
            withFrameNanos {}
            if (chartSize != IntSize.Zero) {
                chartRequester.bringIntoView(
                    Rect(0f, 0f, chartSize.width.toFloat(), chartSize.height + chartBottomInset),
                )
            }
            revealChart = false
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        state = listState,
        contentPadding = padding,
    ) {
        item(key = "balance") {
            val progress = expansion.value
            key(state.currency) {
                BubbleBalanceCard(
                    stringResource(if (expenses) R.string.expenses else R.string.revenues),
                    state.currency,
                    amount?.financeMoney(state.currency) ?: "…",
                    onCurrencyClick = onCurrencyClick,
                    balanceValue = amount,
                    palette = palette,
                    artwork = {
                        FinanceSummaryChart(
                            totals,
                            palette,
                            expanded = sharesExpanded,
                            expansion = progress,
                            onToggle = if (hasCategories) ({
                                sharesOpen = !sharesOpen
                                revealChart = sharesOpen
                            }) else null,
                            modifier = it.bringIntoViewRequester(chartRequester).onSizeChanged { chartSize = it },
                        )
                    },
                    artworkExpansion = progress,
                ) {
                    FinancePeriodControls(
                        period = state.period,
                        palette = palette,
                        onPrevious = { stepPeriod(-1) },
                        onNext = { stepPeriod(1) },
                        onSelectPeriod = onPeriodClick,
                        onCurrentPeriod = onCurrentPeriod,
                    )
                }
            }
        }
        if (inlineAddAction != null) {
            item(key = "add-operation") {
                FinanceAddButton(direction, inlineAddAction, Modifier.fillMaxWidth().padding(top = 8.dp))
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
                val progress = expansion.value
                val placement = if (progress == 0f || progress == 1f) {
                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                } else Modifier
                BubbleGroupedRow(index, totals.categories.size, placement) {
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
