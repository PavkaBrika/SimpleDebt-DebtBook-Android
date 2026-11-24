package com.breckneck.debtbook.finance.details.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.components.ConfirmationContent
import com.breckneck.debtbook.core.ui.components.sheetActionColors
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.financeActionColors
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.util.FinanceCategoryState

@Composable
internal fun FinanceEntrySheet(
    entry: Finance,
    categoryName: String,
    direction: FinanceCategoryState,
    deleting: Boolean,
    deleteFailed: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = bubbleColors()
    var confirmDelete by rememberSaveable(entry.id) { mutableStateOf(false) }
    var editRequested by rememberSaveable(entry.id) { mutableStateOf(false) }
    BubbleBottomSheet(
        onDismiss = { if (editRequested) onEdit() else onDismiss() },
        dismissEnabled = !deleting,
        hideRequested = editRequested,
    ) { enabled, close ->
        if (confirmDelete) {
            ConfirmationContent(
                title = stringResource(R.string.finance_delete_entry),
                message = stringResource(R.string.finance_delete_entry_message),
                confirmText = stringResource(R.string.delete),
                dismissText = stringResource(R.string.cancel),
                onConfirm = onDelete,
                onDismiss = close,
                enabled = enabled,
                errorMessage = if (deleteFailed) stringResource(R.string.history_change_failed) else null,
            )
        } else {
            BubbleSheetHeader(entry.sum.financeMoney(entry.currency), close, enabled = enabled)
            Text("$categoryName · ${entry.displayDate()}", color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            entry.info?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            BubbleButton(
                onClick = { editRequested = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                colors = financeActionColors(direction),
            ) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                Text(stringResource(R.string.finance_edit_entry), Modifier.padding(start = 8.dp))
            }
            BubbleButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                colors = sheetActionColors(colors.dangerContainer, colors.danger),
            ) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                Text(stringResource(R.string.delete), Modifier.padding(start = 8.dp))
            }
        }
    }
}
