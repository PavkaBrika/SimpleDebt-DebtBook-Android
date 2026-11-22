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
internal fun DebtDetailsScreen(vm: DebtDetailsViewModel, host: DebtDetailsFragment.OnButtonClickListener,
    sort: () -> Unit, delete: () -> Unit, share: () -> Unit, onRecord: (Int) -> Unit, onDue: (Int) -> Unit, onUndo: (Int) -> Unit) {
    val today = localDay()
    val name by vm.humanName.observeAsState("")
    val currency = vm.humanCurrency.orEmpty()
    val sum by vm.overallSum.observeAsState()
    val state by vm.screenState.observeAsState()
    val busy by vm.isDeleting.observeAsState(false)
    val rows by vm.visibleDebtRows.observeAsState(emptyList())
    val loans by vm.loans.observeAsState()
    val section by vm.section.observeAsState(DebtSection.OPEN)
    val adjustment by vm.openingAdjustment.observeAsState(BigDecimal.ZERO)
    val ready = !busy && state in listOf(DebtDetailsState.SUCCESS, DebtDetailsState.EMPTY)
    var manage by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val colors = bubbleColors()
    Column(Modifier.fillMaxSize().background(colors.background).semantics { testTagsAsResourceId = true }) {
        BubbleTopBar(name, host::onBackButtonClick, PaddingValues(0.dp),
            leadingTitle = { vm.humanId?.let { PersonMotif(it, Modifier.size(28.dp)) } }) { size ->
            if (section != DebtSection.SETTLED) BubbleIconButton({ vm.humanId?.let { host.addNewDebtFragment(it, currency, name) } },
                Modifier.testTag("addDebtButton"), enabled = ready, diameter = size) {
                Icon(Icons.Outlined.Add, stringResource(R.string.newdebt))
            }
        }
        LazyColumn(Modifier.testTag("personHistory"), contentPadding = PaddingValues(16.dp),
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
                        if (adjustment.signum() != 0) {
                            val amount = MoneyDisplay.formatExact(adjustment, currency)
                            Text(stringResource(R.string.opening_adjustment, if (adjustment.signum() > 0) "+$amount" else amount, currency),
                                style = MaterialTheme.typography.bodySmall)
                        }
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
                FlowRow(Modifier.fillMaxWidth().selectableGroup().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    for (tab in DebtSection.entries) {
                        if (tab == DebtSection.EARLY && loans?.earlyEntries.isNullOrEmpty()) continue
                        val (label, tag) = when (tab) {
                            DebtSection.OPEN -> R.string.open_debts to "openDebtsTab"
                            DebtSection.SETTLED -> R.string.settled_debts to "settledDebtsTab"
                            DebtSection.EARLY -> R.string.early_entries to "earlyEntriesTab"
                        }
                        val selected = section == tab
                        Box(Modifier.testTag(tag).selectable(selected, role = Role.Tab) { vm.selectDebtSection(tab) }
                            .heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                            Text(stringResource(label), color = if (selected) colors.ink else colors.muted,
                                style = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
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
            when (state) {
                DebtDetailsState.ERROR -> item { BubbleOutlinedButton(vm::retry) { Text(stringResource(R.string.history_retry)) } }
                DebtDetailsState.LOADING, null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                DebtDetailsState.EMPTY -> item {
                    val message = when (section) {
                        DebtSection.OPEN -> R.string.no_open_debts
                        DebtSection.SETTLED -> R.string.no_settled_debts
                        DebtSection.EARLY -> R.string.no_early_entries
                    }
                    Text(stringResource(message), Modifier.testTag("emptyDebtsLayout"), color = colors.muted)
                }
                DebtDetailsState.SUCCESS -> items(rows, key = { it.id }) { row ->
                    val loan = loans?.loan(row.id)
                    if (loan != null) {
                        LoanCard(row, loan.remaining, currency, today, ready,
                            { onRecord(row.id) }, { onDue(row.id) }, { vm.openRepayment(row) })
                    } else {
                        HistoryEntry(row, currency, enabled = ready) {
                            if (row.repaymentOfId != null && !row.cancelled) onUndo(row.id) else onRecord(row.id)
                        }
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
private fun HistoryEntry(row: DebtDomain, currency: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = bubbleColors()
    val early = row.repaymentOfId == null
    BubbleGroup(Modifier.padding(start = if (early) 0.dp else 16.dp, top = 2.dp, bottom = 2.dp)) {
        BubbleListRow(Modifier.clickable(enabled = enabled, onClick = onClick).testTag("record-${row.id}").padding(12.dp),
            leading = { Icon(if (row.sum.signum() < 0) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                null, Modifier.background(colors.fill, CircleShape).padding(8.dp).size(16.dp)) },
            trailing = { Text(row.sum.signedMoney(currency), fontWeight = FontWeight.SemiBold,
                textDecoration = if (row.cancelled) TextDecoration.LineThrough else null) }) {
            Text(stringResource(row.historyTitle(early)), style = MaterialTheme.typography.titleSmall)
            Text(LedgerDate.display(row.date), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            row.info?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
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
