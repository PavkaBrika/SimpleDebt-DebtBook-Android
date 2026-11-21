package com.breckneck.debtbook.goal.main.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleAmountField
import com.breckneck.debtbook.core.ui.components.BubbleBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.components.sheetActionColors
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.main.GoalAdjustment
import com.breckneck.debtbook.goal.main.GoalSheetPhase
import com.breckneck.debtbook.goal.main.model.GoalUi

@Composable
internal fun GoalAdjustmentSheet(
    goal: GoalUi,
    adjustment: GoalAdjustment,
    onSumChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    withdraw: Boolean = false,
) {
    val colors = bubbleColors()
    val input = rememberTextFieldState(initialText = adjustment.sumText)
    val errorMessage = when (adjustment.error) {
        GoalAdjustment.Error.INVALID_AMOUNT -> stringResource(R.string.goal_amount_invalid)
        GoalAdjustment.Error.EXCEEDS_BALANCE -> stringResource(R.string.goal_overdrawn)
        GoalAdjustment.Error.SAVE_FAILED -> stringResource(R.string.save_failed_retry)
        null -> null
    }
    BubbleBottomSheet(
        onDismiss = onDismiss,
        dismissEnabled = adjustment.phase == GoalSheetPhase.READY,
        hideRequested = adjustment.phase == GoalSheetPhase.SAVED,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) { enabled, close ->
        BubbleSheetHeader(
            title = stringResource(if (withdraw) R.string.goal_withdraw_money else R.string.goal_add_money),
            onClose = close,
            enabled = enabled,
        )
        Text(
            "${goal.name} · ${stringResource(R.string.goal_balance, goal.savedAmount)}",
            color = colors.muted, style = MaterialTheme.typography.bodyMedium,
        )
        BubbleAmountField(
            state = input,
            currency = goal.currency,
            onValueChange = onSumChange,
            acceptEdits = enabled,
            errorMessage = errorMessage,
            containerColor = if (withdraw) colors.dangerContainer else colors.mint,
            contentColor = if (withdraw) colors.danger else colors.onMint,
        )
        if (errorMessage != null) {
            Text(errorMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = colors.danger)
        }
        BubbleButton(
            onClick = onConfirm,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("goal_adjustment_confirm"),
            colors = sheetActionColors(
                containerColor = if (withdraw) colors.danger else colors.action,
                contentColor = if (withdraw) colors.paper else colors.onAction,
            ),
        ) {
            Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
            Text(stringResource(if (withdraw) R.string.goal_withdraw else R.string.goal_top_up), Modifier.padding(start = 8.dp))
        }
    }
}
