package com.breckneck.debtbook.debt.details

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.components.DebtBookBottomSheet
import com.breckneck.debtbook.core.ui.components.bubbleSwitchColors
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.statement.formatPersonDebtStatement
import com.breckneck.deptbook.domain.util.Filter

@Composable
internal fun DebtShareSheet(onDismiss: () -> Unit, onText: () -> Unit, onExcel: () -> Unit) {
    val colors = bubbleColors()
    BubbleBottomSheet(onDismiss, Modifier.testTag("debtShareSheet")) { enabled, close ->
        BubbleSheetHeader(stringResource(R.string.share_debt_history), close, enabled = enabled)
        BubbleGroup {
            DebtSheetAction(Icons.Outlined.Description, R.string.text_format, "shareDebtText", enabled, onText)
            HorizontalDivider(color = colors.border)
            DebtSheetAction(Icons.Outlined.TableChart, R.string.excel_format, "shareDebtExcel", enabled, onExcel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtStatementSheet(vm: DebtDetailsViewModel, onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    val colors = bubbleColors()
    val state by vm.screenState.observeAsState()
    val history by vm.loans.observeAsState()
    val filter by vm.debtFilter.observeAsState(Filter.ALL)
    val order by vm.debtOrder.observeAsState()
    val balance by vm.overallSum.observeAsState()
    val name by vm.humanName.observeAsState("")
    var includeSettled by rememberSaveable { mutableStateOf(false) }
    var includeEarly by rememberSaveable { mutableStateOf(false) }
    var closing by rememberSaveable { mutableStateOf(false) }
    val currentHistory = history
    val currentOrder = order
    val currentBalance = balance
    val hasSettled = currentHistory?.settledLoans?.any { currentHistory.includesEntry(it.original, filter) } == true
    val hasEarly = currentHistory?.earlyEntries?.any { currentHistory.includesEntry(it, filter) } == true
    val statement = if (state in listOf(DebtDetailsState.SUCCESS, DebtDetailsState.EMPTY) &&
        currentHistory != null && currentOrder != null && currentBalance != null
    ) formatPersonDebtStatement(
        LocalResources.current, name, vm.humanCurrency.orEmpty(), currentBalance, currentHistory, filter, currentOrder,
        includeSettled = hasSettled && includeSettled, includeEarlyEntries = hasEarly && includeEarly,
    ) else null

    DebtBookBottomSheet(onDismiss, Modifier.testTag("debtStatementSheet"),
        containerColor = colors.background, contentColor = colors.ink, hideRequested = closing) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BubbleSheetHeader(stringResource(R.string.statement_preview), { closing = true }, enabled = !closing,
                closeModifier = Modifier.testTag("closeDebtStatement"))
            if (hasSettled || hasEarly) {
                BubbleGroup {
                    if (hasSettled) {
                        StatementOption(R.string.statement_include_settled, "includeSettled", includeSettled, !closing) {
                            includeSettled = it
                        }
                    }
                    if (hasSettled && hasEarly) HorizontalDivider(color = colors.border)
                    if (hasEarly) {
                        StatementOption(R.string.statement_include_early_entries, "includeEarlyEntries", includeEarly, !closing) {
                            includeEarly = it
                        }
                    }
                }
            }
            Column(Modifier.weight(1f, fill = false).testTag("statementScroll")
                .animateContentSize(tween(240), alignment = Alignment.TopStart).verticalScroll(rememberScrollState())) {
                BubbleGroup {
                    Text(statement ?: stringResource(if (state == DebtDetailsState.ERROR) R.string.history_load_failed else R.string.loading),
                        Modifier.fillMaxWidth().testTag("statementText").padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            BubbleButton({ statement?.let { onSend(name, it) } }, Modifier.fillMaxWidth().testTag("sendDebtStatement"),
                enabled = statement != null && !closing) { Text(stringResource(R.string.statement_send)) }
        }
    }
}

@Composable
private fun StatementOption(label: Int, tag: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = bubbleColors()
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), Modifier.weight(1f), color = if (enabled) colors.ink else colors.muted)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = bubbleSwitchColors())
    }
}
