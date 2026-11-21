package com.breckneck.debtbook.debt.main

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.ScreenState
import java.math.BigDecimal

internal fun BigDecimal.signedMoney(currency: String) = (if (signum() > 0) "+" else "") + financeMoney(currency)

@Composable
private fun localDay(): String {
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
internal fun dueLabel(day: String, open: Boolean, today: String = localDay()): String = when {
    open && day == today -> stringResource(R.string.debt_today)
    open && day < today -> stringResource(R.string.debt_overdue, LedgerDate.display(day))
    else -> stringResource(R.string.due_date, LedgerDate.display(day))
}

@Composable
internal fun DebtListScreen(vm: DebtViewModel, host: DebtFragment.OnButtonClickListener,
    sort: () -> Unit, rename: (HumanDomain) -> Unit) {
    val colors = bubbleColors()
    val today = localDay()
    val currency by vm.currency.observeAsState("")
    val deadlines by vm.deadlines.observeAsState(emptyMap())
    val settled by vm.settled.observeAsState(emptySet())
    val people by vm.resultedHumanList.observeAsState(emptyList())
    val sums by vm.mainSums.observeAsState(BigDecimal.ZERO to BigDecimal.ZERO)
    val failed by vm.loadFailed.observeAsState(false)
    val recordedCurrencies by vm.recordedCurrencies.observeAsState(emptyList())
    val state by vm.screenState.observeAsState()
    val searching by vm.isSearching.observeAsState(false)
    val query by vm.query.collectAsState()
    val filter by vm.humanFilter.observeAsState(Filter.ALL)
    var picker by rememberSaveable { mutableStateOf(false) }
    val total = sums.first + sums.second
    Column(Modifier.fillMaxSize().background(colors.background).semantics { testTagsAsResourceId = true }) {
        LazyColumn(Modifier.weight(1f).testTag("peopleList"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.debts), Modifier.weight(1f), color = colors.ink,
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    BubbleIconButton({ if (searching) vm.onStopSearch() else vm.onStartSearch() }) {
                        Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, stringResource(if (searching) R.string.cancel else R.string.find_person))
                    }
                }
            }
            if (searching) item {
                OutlinedTextField(query, vm::setSearchQuery, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.find_person)) }, singleLine = true)
            }
            item {
                BubbleBalanceCard(stringResource(R.string.debt_total), currency, if (failed || state == ScreenState.LOADING) "…" else total.signedMoney(currency),
                    { picker = true }, showCoins = true, balanceValue = total.takeUnless { failed || state == ScreenState.LOADING }) {
                    if (!failed && state != ScreenState.LOADING) FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, value) in listOf(R.string.show_positive to sums.first, R.string.show_negative to sums.second.abs())) {
                            Column {
                                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = colors.heroMuted)
                                Text(value.financeMoney(currency), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            item {
                BubbleSectionTitle(stringResource(R.string.debt_people)) {
                    Text(stringResource(R.string.order_by), Modifier.combinedClickable(onClick = sort,
                        onLongClick = host::onFilterButtonLongClick).testTag("filterHumanButton").padding(12.dp), color = colors.ink)
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((choice, label) in listOf(Filter.ALL to R.string.show_all, Filter.POSITIVE to R.string.show_positive,
                        Filter.NEGATIVE to R.string.show_negative)) {
                        FilterChip(filter == choice, { vm.humanOrder.value?.let { vm.onSetHumanSort(choice, it) } },
                            { Text(stringResource(label)) })
                    }
                }
            }
            if (state == ScreenState.LOADING) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (failed) item { BubbleOutlinedButton(vm::setListStateLoading) { Text(stringResource(R.string.history_retry)) } }
            else if (people.isEmpty()) item { Text(stringResource(R.string.debt_empty_currency), color = colors.muted) }
            items(people, key = { it.id }) { person ->
                BubbleGroup {
                    BubbleListRow(Modifier.combinedClickable(onClick = { host.onHumanClick(person.id, currency, person.name) },
                        onLongClickLabel = stringResource(R.string.person_rename), onLongClick = { rename(person) })
                        .testTag("person-${person.id}").padding(14.dp),
                        trailing = { Text(person.sumDebt.signedMoney(currency), fontWeight = FontWeight.Bold) }) {
                        Text(person.name, style = MaterialTheme.typography.titleMedium)
                        val day = deadlines[person.id]
                        Text(if (day != null) dueLabel(day, true, today) else stringResource(
                            if (person.id in settled) R.string.repayment_closed else R.string.debt_no_due),
                            color = if (day != null && day < today) MaterialTheme.colorScheme.error else colors.muted)
                    }
                }
            }
        }
        BubbleButton({ host.onAddButtonClick(currency) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("addHumanButton")) {
            Text(stringResource(R.string.newdebt))
        }
        BubbleBottomBar(R.id.mainFragment, host::onTabSelected)
    }
    if (picker) {
        val currencies = stringArrayResource(R.array.currencies).toList()
        val options = currencies + (recordedCurrencies + currency).distinct().filterNot { code -> currencies.any { it.substringAfterLast(' ') == code } }
        SettingsPickerBottomSheet(stringResource(R.string.select_currency), options,
            options.indexOfFirst { it.substringAfterLast(' ') == currency }, {
                vm.selectCurrency(options[it].substringAfterLast(' ')); picker = false
            }, { picker = false })
    }
}
