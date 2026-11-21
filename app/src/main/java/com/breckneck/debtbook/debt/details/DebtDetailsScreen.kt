package com.breckneck.debtbook.debt.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.main.dueLabel
import com.breckneck.debtbook.debt.main.signedMoney
import com.breckneck.debtbook.debt.reminders.formatMinute
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import java.math.BigDecimal

@Composable
internal fun DebtDetailsScreen(vm: DebtDetailsViewModel, host: DebtDetailsFragment.OnButtonClickListener,
    sort: () -> Unit, delete: () -> Unit, share: () -> Unit) {
    val name by vm.humanName.observeAsState("")
    val currency = vm.humanCurrency.orEmpty()
    val sum by vm.overallSum.observeAsState()
    val state by vm.screenState.observeAsState()
    val busy by vm.isDeleting.observeAsState(false)
    val rows by vm.visibleDebtRows.observeAsState(emptyList())
    val history by vm.resultedDebtList.observeAsState(emptyList())
    val settled by vm.showSettledDebts.observeAsState(false)
    val ready = !busy && state in listOf(DebtDetailsState.SUCCESS, DebtDetailsState.EMPTY)
    var manage by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val colors = bubbleColors()
    Column(Modifier.fillMaxSize().background(colors.background).semantics { testTagsAsResourceId = true }) {
        BubbleTopBar(name, host::onBackButtonClick, PaddingValues(0.dp)) { size ->
            BubbleIconButton({ vm.humanId?.let { host.addNewDebtFragment(it, currency, name) } },
                Modifier.testTag("addDebtButton"), enabled = ready, diameter = size) {
                Icon(Icons.Outlined.Add, stringResource(R.string.newdebt))
            }
        }
        LazyColumn(Modifier.testTag("personHistory"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.hero, contentColor = colors.onHero) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(stringResource(R.string.debt_total))
                        sum?.let { AnimatedAmount(it, it.signedMoney(currency), style = MaterialTheme.typography.headlineLarge) }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        TextButton({ manage = true }, enabled = ready) { Text(stringResource(R.string.debt_manage)) }
                        DropdownMenu(manage, { manage = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.person_rename)) }, { manage = false; renaming = true })
                            DropdownMenuItem({ Text(stringResource(R.string.person_delete_action)) }, { manage = false; delete() })
                        }
                    }
                    TextButton(share, Modifier.testTag("shareHumanButton"), enabled = ready && history.isNotEmpty()) {
                        Text(stringResource(R.string.share_debt_history))
                    }
                    TextButton(sort, Modifier.testTag("sortButton"), enabled = !busy && vm.debtOrder.value != null) {
                        Text(stringResource(R.string.filter))
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!settled, { vm.selectDebtSection(false) }, { Text(stringResource(R.string.open_debts)) },
                        Modifier.testTag("openDebtsTab"))
                    FilterChip(settled, { vm.selectDebtSection(true) }, { Text(stringResource(R.string.settled_debts)) },
                        Modifier.testTag("settledDebtsTab"))
                }
            }
            when (state) {
                DebtDetailsState.ERROR -> item { BubbleOutlinedButton(vm::retry) { Text(stringResource(R.string.history_retry)) } }
                DebtDetailsState.LOADING, null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                DebtDetailsState.EMPTY -> item { Text(stringResource(if (settled) R.string.no_settled_debts else R.string.no_open_debts),
                    Modifier.testTag("emptyDebtsLayout"), color = colors.muted) }
                DebtDetailsState.SUCCESS -> items(rows, key = { it.id }) { row ->
                    val rest = vm.remaining(row)
                    HistoryRow(row, row.repaymentOfId?.let(vm::originalDebt), rest, currency, ready,
                        { vm.selectDebtAction(row.id) }, { vm.openRepayment(row) })
                }
            }
        }
    }
    if (renaming) {
        var input by rememberSaveable { mutableStateOf(name) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text(stringResource(R.string.person_rename)) },
            text = { OutlinedTextField(input, { input = it }, singleLine = true, label = { Text(stringResource(R.string.person_name)) }) },
            confirmButton = { TextButton({ vm.rename(input); renaming = false }, enabled = input.isNotBlank()) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ renaming = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun HistoryRow(row: DebtDomain, original: DebtDomain?, rest: BigDecimal, currency: String, enabled: Boolean,
    actions: () -> Unit, repay: () -> Unit) {
    val colors = bubbleColors()
    val principal = row.sum.abs()
    BubbleGroup {
        Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = actions)
            .testTag("record-${row.id}").padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(row.historyTitle()), style = MaterialTheme.typography.titleMedium)
            row.info?.takeIf(String::isNotBlank)?.let { Text(it) }
            Text(row.sum.signedMoney(currency), fontWeight = FontWeight.Bold)
            Column(Modifier.testTag("historyDebtDates")) {
                Text(LedgerDate.display(row.date), color = colors.muted)
                row.dueDay?.let { Text(dueLabel(it, rest.signum() > 0)) }
                if (!row.reminderDelivered && row.reminderMinute != null && rest.signum() > 0 && row.dueDay.orEmpty() >= LedgerDate.today()) {
                    Text(stringResource(R.string.due_reminder_on) + " · " + formatMinute(row.reminderMinute!!))
                }
            }
            if (original != null) {
                Text(androidx.compose.ui.platform.LocalContext.current.originalDebtLabel(original, currency), color = colors.muted)
            } else if (principal.signum() > 0) {
                val paid = principal - rest
                val fraction by animateFloatAsState(paid.divide(principal, java.math.MathContext.DECIMAL64).toFloat(), label = "Repayment")
                LinearProgressIndicator({ fraction }, Modifier.fillMaxWidth())
                Text(stringResource(R.string.debt_repaid, MoneyDisplay.formatExact(paid, currency), currency))
                AnimatedAmount(rest, stringResource(R.string.debt_remaining, MoneyDisplay.formatExact(rest, currency), currency))
            }
        }
        if (original == null && rest.signum() > 0) {
            BubbleOutlinedButton(repay, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("repay-${row.id}"), enabled = enabled) { Text(stringResource(R.string.debt_repay)) }
        }
    }
}
