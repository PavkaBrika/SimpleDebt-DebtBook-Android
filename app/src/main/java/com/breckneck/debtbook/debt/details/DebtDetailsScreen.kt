package com.breckneck.debtbook.debt.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.DebtFilterChip
import com.breckneck.debtbook.debt.DebtOrderSheet
import com.breckneck.debtbook.debt.DebtUpgradeHint
import com.breckneck.debtbook.debt.PersonAvatar
import com.breckneck.debtbook.debt.main.dueLabel
import com.breckneck.debtbook.debt.main.localDay
import com.breckneck.debtbook.debt.main.signedMoney
import com.breckneck.debtbook.debt.reminders.formatMinute
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.DebtOrderAttribute
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import java.math.BigDecimal
import kotlinx.coroutines.delay

private val historyOrders = listOf(
    (DebtOrderAttribute.Date to true) to R.string.debt_order_date_newest,
    (DebtOrderAttribute.Date to false) to R.string.debt_order_date_oldest,
    (DebtOrderAttribute.CreationDate to true) to R.string.debt_order_created_newest,
    (DebtOrderAttribute.CreationDate to false) to R.string.debt_order_created_oldest,
    (DebtOrderAttribute.Sum to false) to R.string.debt_order_amount_highest,
    (DebtOrderAttribute.Sum to true) to R.string.debt_order_amount_lowest,
)

@Composable
internal fun DebtDetailsScreen(vm: DebtDetailsViewModel, hostInsets: PaddingValues, host: DebtDetailsFragment.OnButtonClickListener,
    editPerson: () -> Unit, delete: () -> Unit, share: () -> Unit, onRecord: (Int) -> Unit,
    onDue: (Int) -> Unit, onRepaymentAction: (Int) -> Unit) {
    val today = localDay()
    val name by vm.humanName.observeAsState("")
    val photo by vm.humanPhoto.observeAsState()
    val currency = vm.humanCurrency.orEmpty()
    val sum by vm.overallSum.observeAsState()
    val state by vm.screenState.observeAsState()
    val busy by vm.isDeleting.observeAsState(false)
    val rows by vm.visibleDebtRows.observeAsState(emptyList())
    val loans by vm.loans.observeAsState()
    val section by vm.section.observeAsState(DebtSection.OPEN)
    val guidance by vm.upgradeProgress.collectAsState()
    val adjustment by vm.openingAdjustment.observeAsState(BigDecimal.ZERO)
    val ready = !busy && state in listOf(DebtDetailsState.SUCCESS, DebtDetailsState.EMPTY)
    val refreshFailed by vm.refreshFailed.observeAsState(false)
    val order by vm.debtOrder.observeAsState()
    val filter by vm.debtFilter.observeAsState(Filter.ALL)
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        showLoading = false
        if (state == DebtDetailsState.LOADING) {
            delay(200)
            showLoading = true
        }
    }
    val colors = bubbleColors()
    BubbleScaffold(
        hostInsets = hostInsets,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            BubbleTopBar(name, host::onBackButtonClick, hostInsets,
                leadingTitle = {
                    vm.humanId?.let {
                        if (sum == null) Spacer(Modifier.size(28.dp))
                        else PersonAvatar(it, photo, size = 28.dp)
                    }
                }) { size ->
                BubbleActionsMenu(
                    stringResource(R.string.debt_manage), size, enabled = ready,
                    leadingAction = {
                        IconButton(share, Modifier.size(48.dp).testTag("shareHumanButton"),
                            enabled = ready && !vm.history.value.isNullOrEmpty()) {
                            Icon(Icons.Filled.Share, stringResource(R.string.debt_share_short))
                        }
                    },
                ) { dismiss ->
                    BubbleActionItem(stringResource(R.string.edit), Icons.Outlined.Edit, { dismiss(); editPerson() })
                    BubbleActionItem(stringResource(R.string.person_delete_action), Icons.Outlined.DeleteOutline,
                        { dismiss(); delete() }, destructive = true)
                }
            }
        },
        bottomAction = {
            if (sum != null) BubbleButton(
                { vm.humanId?.let { host.addNewDebtFragment(it, currency, name) } },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("addDebtButton"),
                enabled = ready,
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text(stringResource(R.string.newdebt), Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("personHistory"), contentPadding = padding) {
            sum?.let { balance ->
                item(key = "personBalance") {
                    val (label, container, ink) = when (balance.signum()) {
                        1 -> Triple(R.string.show_positive, colors.mint, colors.onMint)
                        -1 -> Triple(R.string.show_negative, colors.dangerContainer, colors.danger)
                        else -> Triple(R.string.debt_person_balance, colors.fill, colors.ink)
                    }
                    Surface(
                        modifier = Modifier.testTag("personBalanceCard"),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = container,
                        contentColor = ink,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("${stringResource(label)} · $currency", style = MaterialTheme.typography.labelLarge)
                            AnimatedAmount(balance.abs(), balance.abs().financeMoney(currency), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold))
                            if (adjustment.signum() != 0) {
                                val amount = MoneyDisplay.formatExact(adjustment, currency)
                                Text(stringResource(R.string.opening_adjustment, if (adjustment.signum() > 0) "+$amount" else amount, currency),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (ready && loans?.earlyEntries?.isNotEmpty() == true && guidance?.showEarlyEntriesHint == true) {
                    item(key = "upgradeEarlyEntriesHint") {
                        val inEarlyEntries = section == DebtSection.EARLY
                        DebtUpgradeHint(
                            title = R.string.debt_upgrade_history_hint,
                            description = R.string.debt_upgrade_history_description,
                            action = if (inEarlyEntries) R.string.debt_upgrade_got_it else R.string.debt_upgrade_open_early_entries,
                            dismissLabel = R.string.debt_upgrade_close_history_hint,
                            onAction = {
                                if (inEarlyEntries) vm.completeEarlyEntriesHint()
                                else vm.onDebtSectionClick(DebtSection.EARLY)
                            },
                            onDismiss = vm::completeEarlyEntriesHint,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                item(key = "debtSections") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DebtHistoryTabs(section, !loans?.earlyEntries.isNullOrEmpty(), ready,
                            vm::onDebtSectionClick, Modifier.weight(1f))
                        BubbleIconButton({ sortOpen = true }, Modifier.testTag("sortButton"), enabled = ready && order != null) {
                            Icon(Icons.Outlined.Tune, stringResource(R.string.debt_history_order), Modifier.size(19.dp))
                        }
                    }
                }
                item(key = "historyControls") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((choice, label) in listOf(Filter.ALL to R.string.show_all, Filter.POSITIVE to R.string.show_positive,
                            Filter.NEGATIVE to R.string.show_negative)) {
                            DebtFilterChip(stringResource(label), filter == choice, {
                                order?.let { vm.onSetDebtSort(choice, it) }
                            }, Modifier.testTag("historyFilter-${choice.name}"), enabled = ready)
                        }
                    }
                }
            }
            if (refreshFailed) item(key = "historyRefreshError") {
                Text(stringResource(R.string.debt_history_refresh_failed), color = colors.danger)
                BubbleOutlinedButton(vm::retry) { Text(stringResource(R.string.history_retry)) }
            }
            when (state) {
                DebtDetailsState.ERROR -> item { BubbleOutlinedButton(vm::retry) { Text(stringResource(R.string.history_retry)) } }
                DebtDetailsState.LOADING, null -> if (showLoading) item {
                    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("personHistoryLoading"))
                }
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
                        LoanCard(row, loan.remaining, currency, today, ready, Modifier.animateItem(),
                            { onRecord(row.id) }, { onDue(row.id) }, { vm.openRepayment(row) })
                    } else {
                        HistoryEntry(row, currency, enabled = ready, modifier = Modifier.animateItem()) {
                            if (row.repaymentOfId != null && !row.cancelled) onRepaymentAction(row.id) else onRecord(row.id)
                        }
                    }
                }
            }
        }
    }
    if (sortOpen) order?.let { current ->
        DebtOrderSheet(stringResource(R.string.debt_history_order), current, historyOrders, { sortOpen = false }) { choice, rememberChoice ->
            vm.onSetDebtSort(filter, choice)
            if (rememberChoice) vm.saveDebtOrder(choice)
        }
    }
}

@Composable
private fun DebtHistoryTabs(section: DebtSection, hasEarlyEntries: Boolean, enabled: Boolean,
    select: (DebtSection) -> Unit, modifier: Modifier = Modifier) {
    val colors = bubbleColors()
    val sections = if (hasEarlyEntries) DebtSection.entries else listOf(DebtSection.OPEN, DebtSection.SETTLED)
    BubbleDirectionTabs(sections, section.takeIf(sections::contains) ?: DebtSection.OPEN, colors.action, enabled, select,
        modifier = modifier.testTag("debtHistoryTabs"), tabModifier = { tab ->
            Modifier.testTag(when (tab) {
                DebtSection.OPEN -> "openDebtsTab"
                DebtSection.SETTLED -> "settledDebtsTab"
                DebtSection.EARLY -> "earlyEntriesTab"
            })
        },
    ) { tab, active ->
        val label = when (tab) {
            DebtSection.OPEN -> R.string.open_debts
            DebtSection.SETTLED -> R.string.settled_debts
            DebtSection.EARLY -> R.string.early_entries
        }
        Text(stringResource(label), Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            color = if (active) colors.onAction else colors.ink, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HistoryEntry(row: DebtDomain, currency: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = bubbleColors()
    val early = row.repaymentOfId == null
    BubbleGroup(modifier.padding(start = if (early) 0.dp else 16.dp, top = 2.dp, bottom = 2.dp)) {
        BubbleListRow(Modifier.clickable(enabled = enabled, onClick = onClick).testTag("record-${row.id}").padding(12.dp),
            leading = { Icon(if (row.sum.signum() < 0) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                null, Modifier.background(colors.fill, CircleShape).padding(8.dp).size(16.dp)) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.sum.signedMoney(currency), fontWeight = FontWeight.SemiBold,
                        textDecoration = if (row.cancelled) TextDecoration.LineThrough else null)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.muted)
                }
            }) {
            Text(stringResource(row.historyTitle(early)), style = MaterialTheme.typography.titleSmall)
            Text(LedgerDate.display(row.date), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            row.info?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LoanCard(row: DebtDomain, rest: BigDecimal, currency: String, today: String, enabled: Boolean,
    modifier: Modifier, actions: () -> Unit, due: () -> Unit, repay: () -> Unit) {
    val colors = bubbleColors()
    val principal = row.sum.abs()
    val lent = row.sum.signum() > 0
    val accent = if (lent) colors.onMint else colors.danger
    val accentContainer = if (lent) colors.mint else colors.dangerContainer
    val actionLabel = stringResource(R.string.debt_record_actions)
    BubbleGroup(modifier.padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClickLabel = actionLabel, onClick = actions)
            .testTag("record-${row.id}").padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val badgeWidth = maxWidth * .48f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(row.historyTitle()), color = accent,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        row.info?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
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
                        if (closed) {
                            Row(Modifier.widthIn(max = badgeWidth).background(colors.mint, CircleShape).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = colors.onMint)
                                Text(label, Modifier.padding(start = 4.dp), color = colors.onMint,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        } else TextButton(due, enabled = enabled, modifier = Modifier.widthIn(max = badgeWidth).testTag("loanDue-${row.id}"),
                            contentPadding = PaddingValues(horizontal = 8.dp), colors = ButtonDefaults.textButtonColors(
                                containerColor = if (overdue) colors.dangerContainer else colors.fill,
                                contentColor = if (overdue) colors.danger else colors.onMint)) {
                            Icon(Icons.Outlined.DateRange, null, Modifier.size(14.dp))
                            Text(label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = colors.muted)
                }
            }
            Column(Modifier.testTag("historyDebtDates")) {
                Text(LedgerDate.display(row.date), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                if (!row.reminderDelivered && row.reminderMinute != null && rest.signum() > 0 && row.dueDay.orEmpty() >= today) {
                    Text(stringResource(R.string.due_reminder_on) + " · " + formatMinute(row.reminderMinute!!))
                }
            }
            if (principal.signum() > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("loanRemaining-${row.id}"),
                    shape = MaterialTheme.shapes.large,
                    color = accentContainer,
                    contentColor = accent,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(stringResource(if (lent) R.string.debt_remaining_to_receive else R.string.debt_remaining_to_repay),
                            style = MaterialTheme.typography.labelLarge)
                        AnimatedAmount(rest, rest.financeMoney(currency),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                val paid = principal - rest
                val target = paid.divide(principal, java.math.MathContext.DECIMAL64).toFloat()
                var lastProgress by rememberSaveable { mutableFloatStateOf(target) }
                val progress = rememberAnimatedProgress(target, { lastProgress = it }, initialProgress = lastProgress)
                AnimatedLinearProgressIndicator(progress, Modifier.fillMaxWidth().testTag("repaymentProgress-${row.id}"),
                    color = accent, trackColor = colors.fill)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.debt_repaid, MoneyDisplay.formatExact(paid, currency), currency),
                        style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    Text(stringResource(R.string.debt_out_of, principal.financeMoney(currency)),
                        style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            } else Text(row.sum.signedMoney(currency))
        }
        if (rest.signum() > 0) {
            BubbleButton(repay, Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                .testTag("repay-${row.id}"), enabled = enabled, colors = ButtonDefaults.buttonColors(
                    containerColor = accentContainer, contentColor = accent)) {
                Icon(if (lent) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast, null, Modifier.size(16.dp))
                Text(stringResource(R.string.debt_repay), Modifier.padding(start = 6.dp))
            }
        }
    }
}
