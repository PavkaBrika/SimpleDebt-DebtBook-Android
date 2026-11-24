package com.breckneck.debtbook.debt.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleListRow
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import java.math.BigDecimal

@Composable
internal fun DebtActionsSheet(
    debt: DebtDomain,
    original: DebtDomain?,
    currency: String,
    remaining: BigDecimal,
    hasRepayments: Boolean,
    earlyEntry: Boolean,
    onDismiss: () -> Unit,
    onRepay: () -> Unit,
    onEdit: () -> Unit,
    onDue: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = bubbleColors()
    val context = LocalContext.current
    val isRepayment = debt.repaymentOfId != null
    BubbleBottomSheet(onDismiss, Modifier.semantics { testTagsAsResourceId = true }) { enabled, close ->
        BubbleSheetHeader(stringResource(debt.historyTitle(earlyEntry)), close,
            enabled = enabled, closeModifier = Modifier.testTag("debtActionsCancel"))
        BubbleGroup {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val amount = if (earlyEntry) debt.sum else debt.sum.abs()
                val sign = if (earlyEntry && amount.signum() > 0) "+" else ""
                Text("$sign${MoneyDisplay.formatExact(amount, currency)} $currency · ${LedgerDate.display(debt.date)}",
                    style = MaterialTheme.typography.titleSmall)
                original?.let { Text(context.originalDebtLabel(it, currency), color = colors.muted) }
                if (hasRepayments) Text(
                    stringResource(R.string.debt_remaining, MoneyDisplay.formatExact(remaining, currency), currency),
                    color = colors.muted, style = MaterialTheme.typography.bodyMedium,
                )
                debt.info?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        BubbleGroup {
            if (isRepayment) {
                DebtSheetAction(
                    if (debt.cancelled) Icons.Outlined.Restore else Icons.Outlined.DeleteOutline,
                    if (debt.cancelled) R.string.repayment_restore else R.string.repayment_cancel,
                    if (debt.cancelled) "restoreRepaymentButton" else "deleteButton", enabled, onRemove,
                    destructive = !debt.cancelled,
                )
            } else {
                if (remaining.signum() > 0) {
                    DebtSheetAction(Icons.Outlined.SouthWest,
                        if (debt.sum.signum() > 0) R.string.repayment_received else R.string.repayment_paid,
                        "repaymentButton", enabled, onRepay)
                    HorizontalDivider(color = colors.border)
                }
                if (debt.sum.signum() != 0) {
                    DebtSheetAction(Icons.Outlined.DateRange, R.string.due_settings, "dueSettingsButton", enabled, onDue)
                    HorizontalDivider(color = colors.border)
                }
                DebtSheetAction(Icons.Outlined.Edit, R.string.edit, "editButton", enabled, onEdit)
                if (!hasRepayments) {
                    HorizontalDivider(color = colors.border)
                    DebtSheetAction(Icons.Outlined.DeleteOutline, R.string.delete, "deleteButton", enabled, onRemove, destructive = true)
                }
            }
        }
    }
}

@Composable
private fun DebtSheetAction(icon: ImageVector, label: Int, tag: String, enabled: Boolean, onClick: () -> Unit, destructive: Boolean = false) {
    val colors = bubbleColors()
    val ink = if (destructive) colors.danger else colors.ink
    BubbleListRow(
        Modifier.testTag(tag).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        leading = { Icon(icon, null, Modifier.size(20.dp), tint = ink) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = colors.muted) },
    ) { Text(stringResource(label), color = ink, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
internal fun RestoreRepaymentSheet(
    debt: DebtDomain,
    original: DebtDomain?,
    currency: String,
    busy: Boolean,
    rejectedRemaining: BigDecimal?,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = bubbleColors()
    val title = stringResource(R.string.repayment_restore)
    BubbleBottomSheet(onDismiss, dismissEnabled = !busy) { enabled, close ->
        BubbleSheetHeader(title, close, enabled = enabled)
        original?.let { Text(context.originalDebtLabel(it, currency), color = colors.muted) }
        Text(stringResource(R.string.repayment_restore_confirm, MoneyDisplay.formatExact(debt.sum.abs(), currency), currency))
        rejectedRemaining?.let {
            Text(stringResource(R.string.repayment_restore_over, MoneyDisplay.formatExact(it, currency), currency),
                color = colors.danger, modifier = Modifier.testTag("restoreRepaymentError"))
        }
        BubbleButton(onRestore, Modifier.fillMaxWidth().testTag("restoreRepaymentConfirm"), enabled = enabled) { Text(title) }
    }
}
