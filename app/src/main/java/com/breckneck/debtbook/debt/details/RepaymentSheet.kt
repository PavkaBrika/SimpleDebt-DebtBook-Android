package com.breckneck.debtbook.debt.details

import com.breckneck.deptbook.domain.util.MoneyDisplay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.DebtBookBottomSheet
import com.breckneck.debtbook.core.ui.components.DebtBookOutlinedAction
import com.breckneck.debtbook.core.ui.theme.spacing
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.DecimalInput
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepaymentSheet(vm: DebtDetailsViewModel, debt: DebtDomain) {
    val screen by vm.screenState.observeAsState()
    val amount by vm.repaymentAmount.observeAsState("")
    val remaining by vm.repaymentRemaining.observeAsState(BigDecimal.ZERO)
    val error by vm.repaymentError.observeAsState()
    val busy by vm.isDeleting.observeAsState(false)
    val ready = screen == DebtDetailsState.SUCCESS && !busy
    val currency = vm.humanCurrency.orEmpty()
    val rest = MoneyDisplay.formatExact(remaining, currency)
    val spacing = MaterialTheme.spacing
    val errorText = when (error) {
        RepaymentError.INVALID_AMOUNT -> stringResource(R.string.repayment_invalid)
        RepaymentError.EXCEEDS_REMAINING -> stringResource(R.string.repayment_over, rest, currency)
        RepaymentError.SAVE_FAILED -> stringResource(R.string.repayment_save_failed)
        null -> null
    }

    DebtBookBottomSheet(
        onDismiss = { if (!busy) vm.closeRepayment() },
        gesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !busy,
            shouldDismissOnClickOutside = !busy
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(spacing.space24).semantics { testTagsAsResourceId = true },
            verticalArrangement = Arrangement.spacedBy(spacing.space12)
        ) {
            Text(
                stringResource(if (debt.sum.signum() > 0) R.string.repayment_received else R.string.repayment_paid),
                style = MaterialTheme.typography.titleLarge
            )
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.space8)
            ) {
                Text(LocalContext.current.originalDebtLabel(debt, currency), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.debt_remaining, rest, currency), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amount,
                    onValueChange = vm::changeRepaymentAmount,
                    modifier = Modifier.fillMaxWidth().testTag("repaymentAmountInput")
                        .semantics { if (errorText != null) error(errorText) },
                    label = { Text(stringResource(R.string.repayment_amount)) },
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
            }
            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.space8, Alignment.End)) {
                TextButton(onClick = vm::closeRepayment, enabled = !busy) { Text(stringResource(R.string.cancel)) }
                Button(onClick = vm::saveRepayment, enabled = ready, modifier = Modifier.testTag("repaymentSaveButton")) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
