package com.breckneck.debtbook.finance.details.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.debtbook.finance.details.FinanceDetailsViewModel
import com.breckneck.debtbook.finance.util.GetFinanceCategoryNameInLocalLanguage
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.util.*

@Composable
internal fun FinanceDetailsScreen(vm: FinanceDetailsViewModel, onBack: () -> Unit, edit: (Finance) -> Unit, add: () -> Unit) {
    val history by vm.history.collectAsState()
    val colors = bubbleColors()
    val title = GetFinanceCategoryNameInLocalLanguage().execute(vm.name, vm.direction, LocalContext.current)
    val direction = stringResource(if (vm.direction == FinanceCategoryState.EXPENSE) R.string.expenses else R.string.revenues)
    val total = remember(history.entries) { history.entries.sumOf { it.sum } }
    var actionId by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val selected = history.entries.find { it.id == actionId }
    LaunchedEffect(history.categoryDeleted) { if (history.categoryDeleted) onBack() }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        BubbleTopBar(title, onBack, PaddingValues(0.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(colors.mint, RoundedCornerShape(26.dp)).padding(16.dp)) {
                    Text(stringResource(R.string.finance_all_time, direction), color = colors.onMint)
                    Text(if (history.loading || history.loadFailed) "…" else total.financeMoney(vm.currency),
                        color = colors.onMint, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                }
            }
            item { BubbleSectionTitle(stringResource(R.string.finance_entries)) { Text(history.entries.size.toString(), color = colors.muted) } }
            when {
                history.loadFailed -> item { BubbleOutlinedButton(vm::refresh) { Text(stringResource(R.string.history_retry)) } }
                history.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                history.entries.isEmpty() -> item {
                    Text(stringResource(if (vm.direction == FinanceCategoryState.EXPENSE) R.string.there_are_no_expenses_yet
                        else R.string.there_are_no_incomes_yet), color = colors.muted)
                }
                else -> items(history.entries, key = { it.id }) { entry ->
                    FinanceHistoryRow(entry, title, onClick = { actionId = entry.id; confirm = false }, onEdit = { edit(entry) })
                }
            }
        }
        BubbleButton(add, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(stringResource(R.string.finance_add_entry))
        }
    }
    if (selected != null) {
        if (confirm) ConfirmationBottomSheet(stringResource(R.string.finance_delete_entry),
            stringResource(R.string.finance_delete_entry_message), { vm.delete(selected) }, { actionId = null },
            confirmText = stringResource(R.string.delete), dismissText = stringResource(R.string.cancel),
            enabled = !history.deleting, errorMessage = if (history.deleteFailed) stringResource(R.string.history_change_failed) else null)
        else ExtraFunctionsBottomSheet(MoneyDisplay.formatExact(selected.sum, vm.currency) + " " + vm.currency,
            onEdit = { actionId = null; edit(selected) }, onDelete = { confirm = true }, onDismiss = { actionId = null })
    }
}


@Composable
internal fun FinanceHistoryRow(entry: Finance, categoryName: String, onClick: () -> Unit, onEdit: () -> Unit) {
    val colors = bubbleColors()
    val label = entry.info?.takeIf { it.isNotBlank() } ?: categoryName
    val date = LedgerDate.display(entry.calendarDay ?: LedgerDate.fromDate(entry.date))
    val money = entry.sum.financeMoney(entry.currency)
    BubbleGroup {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            BubbleListRow(Modifier.weight(1f).clickable { onClick() }.padding(12.dp),
                trailing = { Text(money) }) {
                Text(label, fontWeight = FontWeight.Bold)
                Text(date, color = colors.muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton({ onEdit() }) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.finance_edit_named, "$label, $date, $money"))
            }
        }
    }
}
