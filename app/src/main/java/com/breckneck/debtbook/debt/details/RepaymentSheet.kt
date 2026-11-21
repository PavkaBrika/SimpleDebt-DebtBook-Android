package com.breckneck.debtbook.debt.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.components.BubbleValueRow
import com.breckneck.debtbook.core.ui.components.DebtBookBottomSheet
import com.breckneck.debtbook.core.ui.components.DebtBookOutlinedAction
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.core.ui.theme.spacing
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.MoneyDisplay
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepaymentSheet(vm: DebtDetailsViewModel, debt: DebtDomain) {
    val screen by vm.screenState.observeAsState()
    val amount by vm.repaymentAmount.observeAsState("")
    val remaining by vm.repaymentRemaining.observeAsState(BigDecimal.ZERO)
    val error by vm.repaymentError.observeAsState()
    val busy by vm.isDeleting.observeAsState(false)
    val ready = screen in listOf(DebtDetailsState.SUCCESS, DebtDetailsState.EMPTY) && !busy
    val currency = vm.humanCurrency.orEmpty()
    val rest = MoneyDisplay.formatExact(remaining, currency)
    val spacing = MaterialTheme.spacing
    val colors = bubbleColors()
    val errorText = when (error) {
        RepaymentError.INVALID_AMOUNT -> stringResource(R.string.repayment_invalid)
        RepaymentError.EXCEEDS_REMAINING -> stringResource(R.string.repayment_over, rest, currency)
        RepaymentError.SAVE_FAILED -> stringResource(R.string.repayment_save_failed)
        null -> null
    }

    DebtBookBottomSheet(
        onDismiss = { if (!busy) vm.closeRepayment() },
        gesturesEnabled = !busy, containerColor = colors.background, contentColor = colors.ink,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !busy,
            shouldDismissOnClickOutside = !busy
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(spacing.space24).semantics { testTagsAsResourceId = true },
            verticalArrangement = Arrangement.spacedBy(spacing.space12)
        ) {
            BubbleSheetHeader(stringResource(R.string.debt_repay), vm::closeRepayment, enabled = !busy)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.space8)
            ) {
                Text(LocalContext.current.originalDebtLabel(debt, currency), style = MaterialTheme.typography.bodyMedium)
                BubbleGroup { BubbleValueRow(stringResource(R.string.debt_record_remaining), remaining.financeMoney(currency)) }
                OutlinedTextField(
                    value = amount,
                    onValueChange = vm::changeRepaymentAmount,
                    modifier = Modifier.fillMaxWidth().testTag("repaymentAmountInput")
                        .semantics { if (errorText != null) error(errorText) },
                    label = { Text(stringResource(R.string.repayment_amount)) },
                    shape = RoundedCornerShape(24.dp),
                    suffix = { Text(currency) },
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.mint, unfocusedContainerColor = colors.mint),
                    enabled = !busy,
                    singleLine = true,
                    isError = errorText != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ready) vm.saveRepayment() })
                )
                if (remaining.signum() > 0) {
                    DebtBookOutlinedAction(
                        label = stringResource(R.string.repayment_full),
                        icon = Icons.Outlined.Calculate,
                        onClick = { vm.changeRepaymentAmount(DecimalInput.format(remaining, currency = currency)) },
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth().testTag("repaymentFullButton")
                    )
                }
                val entered = DecimalInput.parse(amount, currency, previous = remaining)
                if (entered != null && entered.signum() > 0 && entered <= remaining) {
                    Text(stringResource(R.string.debt_after_repayment, (remaining - entered).financeMoney(currency)), color = colors.muted)
                }
            }
            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            BubbleButton(onClick = vm::saveRepayment, enabled = ready,
                modifier = Modifier.fillMaxWidth().testTag("repaymentSaveButton")) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
