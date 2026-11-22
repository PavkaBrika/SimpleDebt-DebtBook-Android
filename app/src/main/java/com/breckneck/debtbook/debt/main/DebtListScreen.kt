package com.breckneck.debtbook.debt.main

import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.PersonMotif
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.ScreenState
import java.math.BigDecimal

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
    sort: () -> Unit, rename: (HumanDomain) -> Unit) {
    val colors = bubbleColors()
    val today = localDay()
    val currency by vm.currency.observeAsState("")
    val deadlines by vm.deadlines.observeAsState(emptyMap())
    val settled by vm.settled.observeAsState(emptySet())
    val people by vm.resultedHumanList.observeAsState(emptyList())
    val sums by vm.mainSums.observeAsState(BigDecimal.ZERO to BigDecimal.ZERO)
    val failed by vm.loadFailed.observeAsState(false)
    val overviewCurrencies by vm.overviewCurrencies.observeAsState(emptyList())
    val state by vm.screenState.observeAsState()
    val searching by vm.isSearching.observeAsState(false)
    val query by vm.query.collectAsState()
    val filter by vm.humanFilter.observeAsState(Filter.ALL)
    var picker by rememberSaveable { mutableStateOf(false) }
    val total = sums.first + sums.second
    BubbleScaffold(
        hostInsets = hostInsets,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        bottomAction = {
            BubbleButton(host::onAddButtonClick, Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("addHumanButton")) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text(stringResource(R.string.newdebt), Modifier.padding(start = 8.dp))
            }
        },
        tabs = { BubbleBottomBar(R.id.mainFragment, host::onTabSelected) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("peopleList"), contentPadding = padding,
            verticalArrangement = Arrangement.Top) {
            item {
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.debts), Modifier.weight(1f), color = colors.ink,
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    BubbleIconButton({ if (searching) vm.onStopSearch() else vm.onStartSearch() }) {
                        Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, stringResource(if (searching) R.string.cancel else R.string.find_person))
                    }
                }
            }
            item {
                BubbleBalanceCard(stringResource(R.string.debt_total), currency, if (failed || state == ScreenState.LOADING) "…" else total.signedMoney(currency),
                    { picker = true }, showCoins = true, balanceValue = total.takeUnless { failed || state == ScreenState.LOADING }) {
                    if (!failed && state != ScreenState.LOADING) FlowRow(maxItemsInEachRow = if (it) 1 else 2,
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, value) in listOf(R.string.show_positive to sums.first, R.string.show_negative to sums.second.abs())) {
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (label == R.string.show_positive) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                                        null, Modifier.size(15.dp), tint = colors.heroMuted)
                                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = colors.heroMuted)
                                }
                                Text(value.financeMoney(currency), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            item {
                BubbleSectionTitle(stringResource(R.string.debt_people), Modifier.padding(top = 12.dp)) {
                    Row(Modifier.clip(CircleShape).combinedClickable(onClick = sort,
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
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((choice, label) in listOf(Filter.ALL to R.string.show_all, Filter.POSITIVE to R.string.show_positive,
                        Filter.NEGATIVE to R.string.show_negative)) {
                        FilterChip(filter == choice, { vm.humanOrder.value?.let { vm.onSetHumanSort(choice, it) } },
                            { Text(stringResource(label)) }, shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(containerColor = colors.paper, labelColor = colors.muted,
                                selectedContainerColor = colors.mint, selectedLabelColor = colors.onMint),
                            border = BorderStroke(1.dp, if (filter == choice) colors.mint else colors.border))
                    }
                }
            }
            if (searching) item {
                OutlinedTextField(query, vm::setSearchQuery, Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    label = { Text(stringResource(R.string.find_person)) }, singleLine = true)
            }
            if (state == ScreenState.LOADING) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (failed) item { BubbleOutlinedButton(vm::setListStateLoading) { Text(stringResource(R.string.history_retry)) } }
            else if (people.isEmpty()) item {
                BubbleGroup { Text(stringResource(if (query.isBlank()) R.string.debt_empty_currency else R.string.debt_empty_search),
                    Modifier.fillMaxWidth().padding(22.dp), textAlign = TextAlign.Center, color = colors.muted) }
            }
            itemsIndexed(people, key = { _, person -> person.id }) { index, person ->
                BubbleGroupedRow(index, people.size) {
                    BubbleListRow(Modifier.combinedClickable(onClick = { host.onHumanClick(person.id, currency, person.name) },
                        onLongClickLabel = stringResource(R.string.person_rename), onLongClick = { rename(person) })
                        .testTag("person-${person.id}").padding(14.dp),
                        leading = { PersonMotif(person.id) },
                        trailing = { Text(person.sumDebt.signedMoney(currency), fontWeight = FontWeight.Bold,
                            color = if (person.sumDebt.signum() < 0) colors.danger else colors.onMint) }) {
                        Text(person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val day = deadlines[person.id]
                        Text(if (day != null) dueLabel(day, true, today) else stringResource(
                            if (person.id in settled) R.string.repayment_closed else R.string.debt_no_due),
                            style = MaterialTheme.typography.bodySmall, color = if (day != null && day < today) colors.danger else colors.muted)
                    }
                }
            }
        }
    }
    if (picker) {
        val options = (overviewCurrencies + currency).distinct().filter(String::isNotBlank)
        SettingsPickerBottomSheet(stringResource(R.string.select_currency), options,
            options.indexOf(currency), {
                vm.selectCurrency(options[it]); picker = false
            }, { picker = false })
    }
}
