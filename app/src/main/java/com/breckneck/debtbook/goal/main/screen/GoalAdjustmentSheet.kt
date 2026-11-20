package com.breckneck.debtbook.goal.main.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.DebtBookBottomSheet
import com.breckneck.debtbook.goal.main.GoalAdjustment
import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.debtbook.goal.ui.GoalButton
import com.breckneck.deptbook.domain.util.DecimalInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalAdjustmentSheet(
    goal: GoalUi,
    adjustment: GoalAdjustment,
    onSumChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    withdraw: Boolean = false,
) {
    val enabled = !adjustment.isSaving
    var input by remember(adjustment.goalId, withdraw) {
        mutableStateOf(TextFieldValue(adjustment.sumText))
    }
    val errorMessage = when (adjustment.error) {
        GoalAdjustment.Error.INVALID_AMOUNT -> stringResource(R.string.goal_amount_invalid)
        GoalAdjustment.Error.EXCEEDS_BALANCE -> stringResource(R.string.goal_overdrawn)
        GoalAdjustment.Error.SAVE_FAILED -> stringResource(R.string.save_failed_retry)
        null -> null
    }
    val canDismiss = rememberUpdatedState(enabled)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || canDismiss.value },
    )
    DebtBookBottomSheet(onDismiss = { if (enabled) onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (withdraw) R.string.goal_withdraw_money else R.string.goal_add_money),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(goal.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.goal_balance, goal.savedAmount))
            OutlinedTextField(
                value = input,
                onValueChange = {
                    val changed = it.text != input.text
                    if (!changed || DecimalInput.isEditable(it.text, goal.currency)) {
                        input = it
                        if (changed) onSumChange(it.text)
                    }
                },
                enabled = enabled,
                label = { Text(stringResource(R.string.sum)) },
                suffix = { Text(goal.currency) },
                isError = errorMessage != null,
                supportingText = errorMessage?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.cancel)) }
                GoalButton(onClick = onConfirm, enabled = enabled, modifier = Modifier.testTag("goal_adjustment_confirm")) {
                    Text(stringResource(if (withdraw) R.string.goal_withdraw else R.string.goal_top_up))
                }
            }
        }
    }
}
