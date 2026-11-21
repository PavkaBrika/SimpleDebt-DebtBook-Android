package com.breckneck.debtbook.goal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.ConfirmationBottomSheet
import com.breckneck.debtbook.goal.main.GoalDeletion
import com.breckneck.debtbook.goal.main.GoalSheetPhase

@Composable
internal fun DeleteGoalSheet(
    name: String,
    deletion: GoalDeletion,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmationBottomSheet(
        title = stringResource(R.string.goal_delete_title),
        message = stringResource(R.string.goal_delete_message, name),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        enabled = deletion.phase == GoalSheetPhase.READY,
        hideRequested = deletion.phase == GoalSheetPhase.SAVED,
        errorMessage = if (deletion.failed) stringResource(R.string.something_went_wrong) else null,
    )
}
