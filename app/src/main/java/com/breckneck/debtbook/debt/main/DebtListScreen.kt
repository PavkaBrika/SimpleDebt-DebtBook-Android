package com.breckneck.debtbook.debt.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.DebtFilterChip
import com.breckneck.debtbook.debt.DebtUpgradeHint
import com.breckneck.debtbook.debt.DebtUpgradeNotice
import com.breckneck.debtbook.debt.rememberDebtUpgradeHintTransition
import com.breckneck.debtbook.debt.PersonAvatar
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.LedgerDate
import java.math.BigDecimal
import kotlinx.coroutines.delay

internal fun BigDecimal.signedMoney(currency: String) = (if (signum() > 0) "+" else "") + financeMoney(currency)

@Composable
internal fun localDay(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    var day by remember { mutableStateOf(LedgerDate.today()) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) { day = LedgerDate.today() }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return day
}

@Composable
internal fun dueLabel(day: String, open: Boolean, today: String): String = when {
    open && day == today -> stringResource(R.string.debt_today)
    open && day == remember(today) { LedgerDate.dayFromPickerMillis(LedgerDate.pickerMillis(today) + 86_400_000L) } ->
        stringResource(R.string.debt_tomorrow)
    open && day < today -> stringResource(R.string.debt_overdue, LedgerDate.display(day))
    else -> stringResource(R.string.due_date, LedgerDate.display(day))
}

@Composable
internal fun DebtListScreen(vm: DebtViewModel, hostInsets: PaddingValues, host: DebtFragment.OnButtonClickListener,
    visible: Boolean, onCurrencyClick: (String) -> Unit) {
    val colors = bubbleColors()
    val today = localDay()
    val balance = vm.balance.observeAsState().value
    val topics by vm.availableUpgradeTopics.observeAsState()
    val guidance by vm.upgradeProgress.collectAsState()
    LaunchedEffect(visible, topics) {
        if (visible && topics != null) vm.recordFirstDebtListVisit()
    }
    val deadlines by vm.deadlines.observeAsState(emptyMap())
    val settled by vm.settled.observeAsState(emptySet())
    val people by vm.resultedHumanList.observeAsState(emptyList())
    val failed by vm.loadFailed.observeAsState(false)
    val searching by vm.isSearching.observeAsState(false)
    val currencyHint = rememberDebtUpgradeHintTransition(
        R.string.debt_upgrade_open_currencies.takeIf {
            !searching && topics?.otherCurrencies == true && guidance?.showCurrencyHint == true
        },
    )
    val query by vm.query.collectAsState()
    val filter by vm.humanFilter.observeAsState(Filter.ALL)
    val order = vm.humanOrder.observeAsState().value
    val orderOpen by vm.isSortDialogOpened.observeAsState(false)
    val peopleScroll = rememberLazyListState()
    val searchScroll = rememberLazyListState()
    val focus = LocalFocusManager.current

    fun closeSearch() {
        focus.clearFocus()
        vm.onStopSearch()
    }

    BackHandler(enabled = searching, onBack = ::closeSearch)

    BubbleScaffold(
        hostInsets = hostInsets,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        topBar = {
            DebtListHeader(
                hostInsets = hostInsets,
                searching = searching,
                query = query,
                onStartSearch = {
                    searchScroll.requestScrollToItem(0)
                    vm.onStartSearch()
                },
                onQueryChange = {
                    vm.setSearchQuery(it)
                    searchScroll.requestScrollToItem(0)
                },
                onCloseSearch = ::closeSearch,
            )
        },
        bottomAction = {
            if (!searching) {
                BubbleButton(host::onAddButtonClick, Modifier.fillMaxWidth().testTag("addHumanButton")) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.newdebt), Modifier.padding(start = 8.dp))
                }
            }
        },
        tabs = if (searching) null else { { BubbleScreenTabs(R.id.mainFragment, host::onTabSelected) } },
    ) { padding ->
        val currency = balance?.currency ?: vm.selectedCurrency
        LazyColumn(
            Modifier.fillMaxSize().testTag("peopleList"),
            state = if (searching) searchScroll else peopleScroll,
            contentPadding = padding,
        ) {
            if (!searching) item(key = "balance") {
                BubbleBalanceCard(
                    title = stringResource(R.string.debt_total),
                    currency = currency,
                    balance = balance?.total?.signedMoney(currency) ?: "—",
                    onCurrencyClick = balance?.let { { onCurrencyClick(currency) } },
                    modifier = Modifier.testTag("debtBalance"),
                    showCoins = true,
                    balanceValue = balance?.total,
                ) {
                    FlowRow(maxItemsInEachRow = if (it) 1 else 2,
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, value) in listOf(R.string.show_positive to balance?.receivable, R.string.show_negative to balance?.payable?.abs())) {
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (label == R.string.show_positive) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                                        null, Modifier.size(15.dp), tint = colors.heroMuted)
                                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = colors.heroMuted)
                                }
                                Text(value?.financeMoney(currency) ?: "—", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            if (!searching) {
                item(key = "upgradeCurrencyHint") {
                    DebtUpgradeHint(
                        transition = currencyHint,
                        title = R.string.debt_upgrade_currency_hint,
                        description = R.string.debt_upgrade_currency_description,
                        dismissLabel = R.string.debt_upgrade_close_currency_hint,
                        onAction = { onCurrencyClick(currency) },
                        onDismiss = vm::dismissCurrencyHint,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            item(key = "peopleHeader") {
                BubbleSectionTitle(stringResource(R.string.debt_people), Modifier.padding(top = 12.dp)) {
                    Row(Modifier.clip(CircleShape).combinedClickable(onClick = vm::onHumanSortDialogOpen,
                        onLongClick = host::onFilterButtonLongClick).testTag("filterHumanButton")
                        .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.background(colors.paper, CircleShape).border(1.dp, colors.border, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp), tint = colors.muted)
                            Text(stringResource(R.string.order_by), Modifier.padding(start = 4.dp), color = colors.muted)
                        }
                    }
                }
            }
            item(key = "peopleFilters") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((choice, label) in listOf(Filter.ALL to R.string.show_all, Filter.POSITIVE to R.string.show_positive,
                        Filter.NEGATIVE to R.string.show_negative)) {
                        DebtFilterChip(stringResource(label), filter == choice, { order?.let { vm.onSetHumanSort(choice, it) } })
                    }
                }
            }
            if (failed) item(key = "retry") { DebtListRetry(vm::setListStateLoading) }
            if (balance == null && !failed) item(key = "loading") { DebtListProgress() }
            if (balance != null && people.isEmpty() && !failed) item(key = "empty") {
                BubbleGroup { Text(stringResource(if (query.isBlank()) R.string.debt_empty_currency else R.string.debt_empty_search),
                    Modifier.fillMaxWidth().padding(22.dp), textAlign = TextAlign.Center, color = colors.muted) }
            }
            itemsIndexed(people, key = { _, person -> person.id }, contentType = { _, _ -> "person" }) { index, person ->
                BubbleGroupedRow(index, people.size, Modifier.animateItem(fadeInSpec = tween(180), placementSpec = null, fadeOutSpec = null)) {
                    BubbleListRow(
                        modifier = Modifier
                            .clickable(role = Role.Button) { host.onHumanClick(person.id, currency, person.name) }
                            .testTag("person-${person.id}").padding(14.dp),
                        leading = { PersonAvatar(person.id, person.photo) },
                        trailing = {
                            Text(
                                person.sumDebt.signedMoney(currency),
                                fontWeight = FontWeight.Bold,
                                color = if (person.sumDebt.signum() < 0) colors.danger else colors.onMint,
                            )
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = colors.muted)
                        },
                    ) {
                        Text(person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val day = deadlines[person.id]
                        Text(if (day != null) dueLabel(day, true, today) else stringResource(
                            if (person.sumDebt.signum() == 0 && person.id in settled) R.string.repayment_closed else R.string.debt_no_due),
                            style = MaterialTheme.typography.bodySmall, color = if (day != null && day < today) colors.danger else colors.muted)
                    }
                }
            }
        }
    }
    guidance?.takeIf { visible && topics != null && it.showNotice }?.let {
        DebtUpgradeNotice(it.topics, vm::dismissUpgradeNotice)
    }
    if (orderOpen && order != null) {
        DebtPeopleOrderSheet(order, onDismiss = vm::onHumanSortDialogClose) { choice, rememberChoice ->
            vm.onSetHumanSort(filter, choice)
            if (rememberChoice) vm.saveHumanOrder(choice)
            host.onTickVibration()
        }
    }
}

@Composable
private fun DebtListProgress() {
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        showProgress = true
    }
    if (showProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
private fun DebtListHeader(
    hostInsets: PaddingValues,
    searching: Boolean,
    query: String,
    onStartSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
) {
    val colors = bubbleColors()
    val direction = LocalLayoutDirection.current
    val title = stringResource(R.string.debts)
    val titleStyle = bubbleRootTitleStyle()
    val titleHeight = rememberTextMeasurer().measure(title, style = titleStyle, maxLines = 1).size.height
    val headerHeight = with(LocalDensity.current) { maxOf(bubbleRootTitleMinHeight, titleHeight.toDp()) }

    Row(
        Modifier.fillMaxWidth().background(colors.background)
            .padding(
                start = hostInsets.calculateStartPadding(direction) + 16.dp,
                end = hostInsets.calculateEndPadding(direction) + 16.dp,
                top = hostInsets.calculateTopPadding() + bubbleRootTitleTopPadding,
                bottom = 8.dp,
            ).height(headerHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = colors.headerBubble,
                border = BorderStroke(1.dp, colors.border),
            ) {
                BubbleSearchField(
                    label = stringResource(R.string.find_person),
                    query = query,
                    onQueryChange = onQueryChange,
                    onClearOrClose = { if (query.isEmpty()) onCloseSearch() else onQueryChange("") },
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        } else {
            BubbleRootTitle(title, Modifier.weight(1f))
            BubbleIconButton(onClick = onStartSearch) {
                Icon(Icons.Outlined.Search, stringResource(R.string.find_person))
            }
        }
    }
}

@Composable
private fun DebtListRetry(onRetry: () -> Unit) {
    BubbleOutlinedButton(onRetry) { Text(stringResource(R.string.history_retry)) }
}
