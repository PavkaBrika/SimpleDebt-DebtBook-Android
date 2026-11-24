package com.breckneck.debtbook.finance.details.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.details.FinanceDetailsViewModel
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.debtbook.finance.util.GetFinanceCategoryNameInLocalLanguage
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.util.*

@Composable
internal fun FinanceDetailsScreen(vm: FinanceDetailsViewModel, hostInsets: PaddingValues, onBack: () -> Unit, edit: (Finance) -> Unit, add: () -> Unit) {
    val history by vm.history.collectAsState()
    val colors = bubbleColors()
    val title = GetFinanceCategoryNameInLocalLanguage().execute(vm.name, vm.direction, LocalContext.current)
    val direction = stringResource(if (vm.direction == FinanceCategoryState.EXPENSE) R.string.expenses else R.string.revenues)
    val total = remember(history.entries) { history.entries.sumOf { it.sum } }
    var actionId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = history.entries.find { it.id == actionId }

    BubbleScaffold(
        hostInsets = hostInsets,
        topBar = {
            BubbleTopBar(title, onBack, hostInsets)
        },
        bottomAction = {
            BubbleButton(add, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text(stringResource(R.string.finance_add_entry), Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.Top) {
            item {
                Column(Modifier.fillMaxWidth().background(colors.mint, RoundedCornerShape(26.dp)).padding(16.dp)) {
                    Text(stringResource(R.string.finance_all_time, direction), color = colors.onMint)
                    Text(if (history.loading || history.loadFailed) "…" else total.financeMoney(vm.currency),
                        color = colors.onMint, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                }
            }
            item { BubbleSectionTitle(stringResource(R.string.finance_entries), Modifier.padding(top = 16.dp, bottom = 8.dp)) { Text(history.entries.size.toString(), color = colors.muted) } }
            when {
                history.loadFailed -> item { BubbleOutlinedButton(vm::refresh) { Text(stringResource(R.string.history_retry)) } }
                history.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                history.entries.isEmpty() -> item {
                    Text(stringResource(if (vm.direction == FinanceCategoryState.EXPENSE) R.string.there_are_no_expenses_yet
                        else R.string.there_are_no_incomes_yet), color = colors.muted)
                }
                else -> itemsIndexed(history.entries, key = { _, entry -> entry.id }) { index, entry ->
                    BubbleGroupedRow(index, history.entries.size, Modifier.animateItem()) {
                        FinanceHistoryRow(entry, title, onClick = { actionId = entry.id })
                    }
                }
            }
        }
    }
    if (selected != null) {
        FinanceEntrySheet(
            selected,
            title,
            vm.direction,
            deleting = history.deleting,
            deleteFailed = history.deleteFailed,
            onEdit = {
                if (actionId == selected.id) {
                    actionId = null
                    edit(selected)
                }
            },
            onDelete = { vm.delete(selected) },
            onDismiss = { actionId = null },
        )
    }
}


@Composable
internal fun FinanceHistoryRow(entry: Finance, categoryName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = bubbleColors()
    val label = entry.info?.takeIf { it.isNotBlank() } ?: categoryName
    BubbleListRow(
        modifier.clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        leading = { Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.background(colors.fill, CircleShape).padding(6.dp).size(16.dp)) },
        trailing = {
            Text(entry.sum.financeMoney(entry.currency), fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = colors.muted)
        },
    ) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(entry.displayDate(), color = colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

internal fun Finance.displayDate(): String = LedgerDate.display(calendarDay ?: LedgerDate.fromDate(date))
