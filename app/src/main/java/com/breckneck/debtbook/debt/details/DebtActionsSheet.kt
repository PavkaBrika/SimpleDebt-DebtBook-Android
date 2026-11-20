package com.breckneck.debtbook.debt.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.DebtBookBottomSheet
import com.breckneck.debtbook.core.ui.components.DebtBookOutlinedAction
import com.breckneck.debtbook.core.ui.theme.spacing
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.LedgerDate
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtActionsSheet(
    debt: DebtDomain,
    original: DebtDomain?,
    currency: String,
    remaining: BigDecimal,
    hasRepayments: Boolean,
    onDismiss: () -> Unit,
    onRepay: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    val isRepayment = debt.repaymentOfId != null
    DebtBookBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(spacing.space24)
                .semantics { testTagsAsResourceId = true },
            verticalArrangement = Arrangement.spacedBy(spacing.space12)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(debt.historyTitle()), style = MaterialTheme.typography.titleLarge)
                    Text("${DecimalInput.format(debt.sum.abs())} $currency · ${LedgerDate.display(debt.date)}",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (!isRepayment && !hasRepayments) {
                    IconButton(onClick = onRemove, modifier = Modifier.testTag("deleteButton")) {
                        Icon(painterResource(R.drawable.ic_baseline_delete_outline_24), stringResource(R.string.delete))
                    }
                }
            }
            if (isRepayment) {
                original?.let {
                    Text(context.originalDebtLabel(it, currency), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!debt.cancelled) {
                    DebtBookOutlinedAction(
                        stringResource(R.string.repayment_cancel), onRemove,
                        modifier = Modifier.fillMaxWidth().testTag("deleteButton")
                    )
                }
            } else {
                if (hasRepayments) {
                    Text(stringResource(R.string.debt_remaining, DecimalInput.format(remaining), currency),
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (remaining.signum() > 0) {
                    Button(onClick = onRepay, modifier = Modifier.fillMaxWidth().testTag("repaymentButton")) {
                        Text(stringResource(if (debt.sum.signum() > 0) R.string.repayment_received else R.string.repayment_paid))
                    }
                }
                DebtBookOutlinedAction(
                    stringResource(R.string.edit), onEdit,
                    modifier = Modifier.fillMaxWidth().testTag("editButton"),
                    icon = Icons.Outlined.Edit
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).testTag("debtActionsCancel")) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
