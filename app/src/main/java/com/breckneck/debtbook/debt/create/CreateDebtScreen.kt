package com.breckneck.debtbook.debt.create

import android.os.Bundle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.reminders.DebtReminders
import com.breckneck.debtbook.finance.financeActionColors
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.LedgerDate

@Composable
internal fun CreateDebtScreen(vm: CreateDebtViewModel, hostInsets: PaddingValues, args: Bundle, back: () -> Unit, feedback: () -> Unit,
    onContactClick: () -> Unit, onCurrencyClick: (String) -> Unit, saved: (Int, String, String) -> Unit) {
    val context = LocalContext.current
    val colors = bubbleColors()
    val focus = LocalFocusManager.current
    val personId = args.getInt("idHuman", -1)
    val debtId = args.getInt("idDebt", -1)
    val previous = args.getString("sum")?.toBigDecimalOrNull()
    val availablePeople by vm.availablePeople.observeAsState(emptyList())
    val selectedPersonId by vm.selectedPersonId.observeAsState()
    val selectedPerson = availablePeople.firstOrNull { it.id == selectedPersonId }
    val draftCurrency by vm.currency.observeAsState("")
    val currency = selectedPerson?.currency ?: draftCurrency
    val ownerId = if (personId > 0) personId else selectedPersonId ?: -1
    val amount = rememberTextFieldState(previous?.abs()?.let { DecimalInput.format(it, currency = currency) }.orEmpty())
    val name = rememberTextFieldState(args.getString("name").orEmpty())
    val note = rememberTextFieldState(args.getString("info").orEmpty())
    var positive by rememberSaveable { mutableStateOf((previous?.signum() ?: 1) >= 0) }
    var error by rememberSaveable { mutableIntStateOf(0) }
    val busy by vm.isSaving.observeAsState(false)
    val failed by vm.saveError.observeAsState(false)
    val savedOwner by vm.savedOwner.observeAsState()
    val loadingPhoto by vm.loadingPhoto.observeAsState(false)
    val scroll = rememberScrollState()
    val directionColors = financeActionColors(if (positive) FinanceCategoryState.INCOME else FinanceCategoryState.EXPENSE)
    val amountContainer by animateColorAsState(if (positive) colors.mint else colors.dangerContainer, tween(200), label = "Debt amount background")
    val amountInk by animateColorAsState(if (positive) colors.onMint else colors.danger, tween(200), label = "Debt amount color")
    LaunchedEffect(savedOwner) { savedOwner?.let { saved(it.id, it.currency, it.name) } }

    BubbleScaffold(
        hostInsets = hostInsets,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        topBar = {
            BubbleTopBar(stringResource(if (debtId > 0) R.string.editdebtcollaps else R.string.newdebt), back,
                hostInsets, backEnabled = !busy)
        },
        bottomAction = {
            if (error != 0 || failed) Text(stringResource(if (error != 0) error else R.string.history_change_failed),
                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            BubbleButton({
                val parsed = DecimalInput.parse(amount.text.toString(), currency, previous?.abs())
                error = when {
                    name.text.isBlank() && ownerId <= 0 -> R.string.youmustentername
                    parsed == null || parsed.signum() < 0 -> R.string.invalid_decimal_amount
                    parsed.signum() == 0 -> R.string.zerodebt
                    vm.hasPastReminder() && DebtReminders.get(context).allowed() -> R.string.debt_future_time
                    else -> 0
                }
                if (error == 0 && parsed != null) {
                    if (vm.remind.value == true && vm.hasDueChange && !DebtReminders.get(context).allowed()) {
                        vm.dueDay.value?.let { vm.setDueDraft(it, null) }
                    }
                    focus.clearFocus()
                    feedback()
                    vm.saveDebt(ownerId, debtId, name.text.toString().trim(), if (positive) parsed else -parsed,
                        vm.date.value ?: LedgerDate.today(), note.text.toString())
                }
            }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("setFinanceButton"),
                enabled = !busy && !loadingPhoto && savedOwner == null && (selectedPersonId == null || selectedPerson != null),
                colors = directionColors) {
                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                Text(stringResource(R.string.debt_save), Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BubbleDirectionTabs(
                directions = listOf(true, false),
                selected = positive,
                indicatorColor = directionColors.containerColor,
                enabled = !busy && !vm.hasRepayments,
                onSelect = { positive = it },
                tabModifier = { Modifier.testTag(if (it) "directionPositive" else "directionNegative") },
            ) { direction, active ->
                val ink = if (active) colors.onAction else colors.ink
                Row(
                    Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (direction) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast, null, Modifier.size(16.dp), tint = ink)
                    Text(stringResource(if (direction) R.string.show_positive else R.string.show_negative),
                        color = ink, style = MaterialTheme.typography.titleSmall)
                }
            }
            if (vm.hasRepayments) {
                BubbleGroup { BubbleValueRow(stringResource(R.string.sum), "${amount.text} $currency",
                    Modifier.testTag("lockedAmount")) }
            } else BubbleAmountField(
                amount, currency, { error = 0 }, Modifier.testTag("debtSumEditText"),
                acceptEdits = !busy, containerColor = amountContainer, contentColor = amountInk,
            ) {
                if (ownerId > 0) Text(currency, color = amountInk)
                else BubbleOutlinedButton({
                    focus.clearFocus()
                    onCurrencyClick(currency)
                }, enabled = !busy) {
                    Text(currency)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.select_currency), Modifier.size(16.dp))
                }
            }
            BubbleGroup {
                CreateDebtPersonFields(vm, personId > 0, name, selectedPerson, onContactClick) { error = 0 }
                HorizontalDivider(color = colors.border)
                BubbleTextFieldRow(Icons.Outlined.Edit, stringResource(R.string.comment), note, stringResource(R.string.debt_note_hint), !busy, null,
                    { true }, {}, Modifier.testTag("debtInfoEditText"))
                HorizontalDivider(color = colors.border)
                DebtEntryDate(vm)
            }
            BubbleSectionTitle(stringResource(R.string.debt_return_section))
            BubbleGroup { DebtDueFields(vm) { scroll.animateScrollTo(scroll.maxValue) } }
        }
    }
}
