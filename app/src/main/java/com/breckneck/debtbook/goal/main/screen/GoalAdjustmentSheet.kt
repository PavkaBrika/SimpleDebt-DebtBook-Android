package com.breckneck.debtbook.goal.main.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.main.GoalAdjustment
import com.breckneck.debtbook.goal.main.model.GoalUi
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
    val colors = bubbleColors()
    val enabled = !adjustment.isSaving
    val input = rememberTextFieldState(initialText = adjustment.sumText)
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
    ModalBottomSheet(
        onDismissRequest = { if (enabled) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.background,
        contentColor = colors.ink,
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if (withdraw) R.string.goal_withdraw_money else R.string.goal_add_money),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss, enabled = enabled) {
                    Surface(shape = CircleShape, color = colors.paper, border = BorderStroke(1.dp, colors.border)) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.cancel), Modifier.size(20.dp), tint = colors.ink)
                        }
                    }
                }
            }
            Text(
                "${goal.name} · ${stringResource(R.string.goal_balance, goal.savedAmount)}",
                color = colors.muted, style = MaterialTheme.typography.bodyMedium,
            )
            val amountStyle = MaterialTheme.typography.displaySmall.copy(color = colors.onMint, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                state = input,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().background(colors.mint, RoundedCornerShape(25.dp))
                    .semantics { if (errorMessage != null) error(errorMessage) },
                inputTransformation = {
                    val value = asCharSequence().toString()
                    if (value != originalText.toString()) {
                        if (DecimalInput.isEditable(value, goal.currency)) onSumChange(value) else revertAllChanges()
                    }
                },
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = amountStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                cursorBrush = SolidColor(colors.onMint),
                decorator = { editor ->
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(stringResource(R.string.sum), color = colors.onMint, style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                if (input.text.isEmpty()) Text("0", style = amountStyle, color = colors.onMint.copy(alpha = .65f))
                                editor()
                            }
                            Text(goal.currency, fontSize = 16.sp, color = colors.onMint, fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )
            if (errorMessage != null) {
                Text(errorMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = colors.danger)
            }
            BubbleButton(onClick = onConfirm, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("goal_adjustment_confirm")) {
                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                Text(stringResource(if (withdraw) R.string.goal_withdraw else R.string.goal_top_up), Modifier.padding(start = 8.dp))
            }
        }
    }
}
