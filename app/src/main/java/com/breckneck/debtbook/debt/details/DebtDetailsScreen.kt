package com.breckneck.debtbook.debt.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.PersonMotif
import com.breckneck.debtbook.debt.main.dueLabel
import com.breckneck.debtbook.debt.main.localDay
import com.breckneck.debtbook.debt.main.signedMoney
import com.breckneck.debtbook.debt.reminders.formatMinute
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import java.math.BigDecimal

@Composable
internal fun DebtDetailsScreen(vm: DebtDetailsViewModel, hostInsets: PaddingValues, host: DebtDetailsFragment.OnButtonClickListener,
    sort: () -> Unit, delete: () -> Unit, share: () -> Unit, onRecord: (Int) -> Unit, onDue: (Int) -> Unit, onUndo: (Int) -> Unit) {
    val today = localDay()
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
    BubbleScaffold(
        hostInsets = hostInsets,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            BubbleTopBar(name, host::onBackButtonClick, hostInsets,
                leadingTitle = { vm.humanId?.let { PersonMotif(it, Modifier.size(28.dp)) } }) { size ->
                BubbleIconButton({ vm.humanId?.let { host.addNewDebtFragment(it, currency, name) } },
                    Modifier.testTag("addDebtButton"), enabled = ready, diameter = size) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.newdebt))
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("personHistory"), contentPadding = padding,
            verticalArrangement = Arrangement.Top) {
            item {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.mint, contentColor = colors.onMint) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        val label = when (sum?.signum()) {
                            1 -> R.string.show_positive
                            -1 -> R.string.show_negative
                            else -> R.string.debt_person_balance
                        }
                        Text("${stringResource(label)} · $currency", style = MaterialTheme.typography.labelLarge)
                        sum?.let { AnimatedAmount(it.abs(), it.abs().financeMoney(currency), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold)) }
                    }
                }
            }
            item {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        BubbleOutlinedButton({ manage = true }, enabled = ready) {
                            Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(16.dp))
                            Text(stringResource(R.string.debt_manage), Modifier.padding(start = 4.dp))
                        }
                        DropdownMenu(manage, { manage = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.person_rename)) }, { manage = false; renaming = true })
                            DropdownMenuItem({ Text(stringResource(R.string.person_delete_action)) }, { manage = false; delete() })
                        }
                    }
                    BubbleOutlinedButton(share, Modifier.testTag("shareHumanButton"), enabled = ready && !vm.history.value.isNullOrEmpty()) {
                        Icon(Icons.Outlined.NorthEast, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.debt_share_short), Modifier.padding(start = 4.dp))
                    }
                }
            }
            item {
                // Keep the archived-loan view reachable even when its current count is zero.
                FlowRow(Modifier.fillMaxWidth().selectableGroup().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    for (archive in listOf(false, true)) {
                        val selected = archive == settled
                        val label = stringResource(if (archive) R.string.settled_debts else R.string.open_debts)
                        val modifier = Modifier.testTag(if (archive) "settledDebtsTab" else "openDebtsTab")
                            .selectable(selected, role = Role.Tab) { vm.selectDebtSection(archive) }.heightIn(min = 48.dp)
                        Box(modifier, contentAlignment = Alignment.CenterStart) {
                            Text(label, color = if (selected) colors.ink else colors.muted,
                                style = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            when (state) {
                DebtDetailsState.ERROR -> item { BubbleOutlinedButton(vm::retry) { Text(stringResource(R.string.history_retry)) } }
                DebtDetailsState.LOADING, null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                DebtDetailsState.EMPTY -> item { Text(stringResource(if (settled) R.string.no_settled_debts else R.string.no_open_debts),
                    Modifier.testTag("emptyDebtsLayout"), color = colors.muted) }
                DebtDetailsState.SUCCESS -> items(rows.filter { it.repaymentOfId == null }, key = { it.id }) { row ->
                    val rest = vm.remaining(row)
                    LoanCard(row, rest, currency, today, ready, { onRecord(row.id) }, { onDue(row.id) }, { vm.openRepayment(row) })
                }
            }
            item {
                BubbleSectionTitle(stringResource(R.string.history), Modifier.padding(top = 12.dp, bottom = 6.dp)) {
                    BubbleOutlinedButton(sort, Modifier.testTag("sortButton"), enabled = !busy && vm.debtOrder.value != null) {
                        Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.filter))
                    }
                }
            }
            if (ready) itemsIndexed(history, key = { _, entry -> "event-${entry.id}" }) { index, entry ->
                BubbleGroupedRow(index, history.size) {
                    val tag = if (entry.repaymentOfId == null) "event-${entry.id}" else "record-${entry.id}"
                    BubbleListRow(Modifier.clickable {
                        if (entry.repaymentOfId != null && !entry.cancelled) onUndo(entry.id) else onRecord(entry.id)
                    }.testTag(tag).padding(12.dp),
                        leading = { Icon(if (entry.sum.signum() < 0) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                            null, Modifier.background(colors.fill, CircleShape).padding(8.dp).size(16.dp)) },
                        trailing = { Text(entry.sum.signedMoney(currency), fontWeight = FontWeight.SemiBold,
                            textDecoration = if (entry.cancelled) TextDecoration.LineThrough else null) }) {
                        Text(stringResource(entry.historyTitle()), style = MaterialTheme.typography.titleSmall)
                        Text(LedgerDate.display(entry.date), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
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
private fun LoanCard(row: DebtDomain, rest: BigDecimal, currency: String, today: String, enabled: Boolean,
    actions: () -> Unit, due: () -> Unit, repay: () -> Unit) {
    val colors = bubbleColors()
    val principal = row.sum.abs()
    BubbleGroup(Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = actions)
            .testTag("record-${row.id}").padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val badgeWidth = maxWidth * .48f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(row.info?.takeIf(String::isNotBlank) ?: stringResource(row.historyTitle()),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (!row.info.isNullOrBlank()) Text(stringResource(row.historyTitle()),
                            style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    if (principal.signum() > 0) {
                        val day = row.dueDay
                        val closed = rest.signum() == 0
                        val overdue = !closed && day != null && day < today
                        val label = when {
                            closed -> stringResource(R.string.repayment_closed)
                            day != null -> dueLabel(day, true, today)
                            else -> stringResource(R.string.debt_no_due)
                        }
                        TextButton(due, enabled = enabled, modifier = Modifier.widthIn(max = badgeWidth),
                            contentPadding = PaddingValues(horizontal = 8.dp), colors = ButtonDefaults.textButtonColors(
                                containerColor = if (overdue) colors.dangerContainer else if (closed) colors.mint else colors.fill,
                                contentColor = if (overdue) colors.danger else colors.onMint)) {
                            Icon(if (closed) Icons.Outlined.Check else Icons.Outlined.DateRange, null, Modifier.size(14.dp))
                            Text(label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Column(Modifier.testTag("historyDebtDates")) {
                Text(LedgerDate.display(row.date), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                if (!row.reminderDelivered && row.reminderMinute != null && rest.signum() > 0 && row.dueDay.orEmpty() >= today) {
                    Text(stringResource(R.string.due_reminder_on) + " · " + formatMinute(row.reminderMinute!!))
                }
            }
            if (principal.signum() > 0) {
                val paid = principal - rest
                val fraction by animateFloatAsState(paid.divide(principal, java.math.MathContext.DECIMAL64).toFloat(), label = "Repayment")
                LinearProgressIndicator({ fraction }, Modifier.fillMaxWidth(), color = colors.onMint, trackColor = colors.fill,
                    drawStopIndicator = {})
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.debt_repaid, MoneyDisplay.formatExact(paid, currency), currency),
                        style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    Text(stringResource(R.string.debt_out_of, principal.financeMoney(currency)),
                        style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                AnimatedAmount(rest, stringResource(R.string.debt_remaining, MoneyDisplay.formatExact(rest, currency), currency),
                    style = MaterialTheme.typography.bodyMedium)
            } else Text(row.sum.signedMoney(currency))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(16.dp), tint = colors.onMint)
                Text(stringResource(R.string.debt_record_actions), style = MaterialTheme.typography.labelLarge, color = colors.onMint)
            }
        }
        if (rest.signum() > 0) {
            BubbleButton(repay, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("repay-${row.id}"), enabled = enabled, colors = ButtonDefaults.buttonColors(
                    containerColor = colors.mint, contentColor = colors.onMint)) {
                Icon(Icons.Outlined.SouthWest, null, Modifier.size(16.dp))
                Text(stringResource(R.string.debt_repay), Modifier.padding(start = 6.dp))
            }
        }
    }
}
