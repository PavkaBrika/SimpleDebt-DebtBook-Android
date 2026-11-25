package com.breckneck.debtbook.debt.create

import com.breckneck.debtbook.core.navigation.DebtForm
import com.breckneck.debtbook.core.navigation.LocalRouteActive
import com.breckneck.debtbook.core.navigation.RouteBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
import kotlinx.coroutines.launch

@Composable
internal fun CreateDebtScreen(vm: CreateDebtViewModel, hostInsets: PaddingValues, args: DebtForm, back: () -> Unit, feedback: () -> Unit,
    onContactClick: () -> Unit, onCurrencyClick: (String) -> Unit, saved: (Int, String, String, Boolean) -> Unit) {
    val context = LocalContext.current
    val colors = bubbleColors()
    val focus = LocalFocusManager.current
    val personId = args.idHuman
    val debtId = args.idDebt
    val previous = args.sum?.toBigDecimalOrNull()
    val availablePeople by vm.availablePeople.observeAsState(emptyList())
    val selectedPersonId by vm.selectedPersonId.observeAsState()
    val selectedPerson = availablePeople.firstOrNull { it.id == selectedPersonId }
    val draftCurrency by vm.currency.observeAsState("")
    val currency = selectedPerson?.currency ?: draftCurrency
    val ownerId = if (personId > 0) personId else selectedPersonId ?: -1
    val amount = rememberTextFieldState(previous?.abs()?.let { DecimalInput.format(it, currency = currency) }.orEmpty())
    val name = rememberTextFieldState(args.name.orEmpty())
    val note = rememberTextFieldState(args.info.orEmpty())
    var positive by rememberSaveable { mutableStateOf((previous?.signum() ?: 1) >= 0) }
    var showFieldErrors by rememberSaveable { mutableStateOf(false) }
    val busy by vm.isSaving.observeAsState(false)
    val failed by vm.saveError.observeAsState(false)
    val savedOwner by vm.savedOwner.observeAsState()
    val loadingPhoto by vm.loadingPhoto.observeAsState(false)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val nameRequester = remember { BringIntoViewRequester() }
    val amountText = amount.text.toString()
    val parsed = remember(amountText, currency, previous) { DecimalInput.parse(amountText, currency, previous?.abs()) }
    val amountError = when {
        amountText.isBlank() -> R.string.debt_amount_required
        parsed == null || parsed.signum() < 0 -> R.string.invalid_decimal_amount
        parsed.signum() == 0 -> R.string.zerodebt
        else -> null
    }
    val nameMissing = name.text.isBlank() && ownerId <= 0
    val directionColors = financeActionColors(if (positive) FinanceCategoryState.INCOME else FinanceCategoryState.EXPENSE)
    val saveFill by animateColorAsState(directionColors.containerColor, tween(200), label = "Save debt background")
    val saveInk by animateColorAsState(directionColors.contentColor, tween(200), label = "Save debt color")
    val amountContainer by animateColorAsState(if (positive) colors.mint else colors.dangerContainer, tween(200), label = "Debt amount background")
    val amountInk by animateColorAsState(if (positive) colors.onMint else colors.danger, tween(200), label = "Debt amount color")
    val active = LocalRouteActive.current
    RouteBackHandler(enabled = busy || savedOwner != null) {}
    LaunchedEffect(savedOwner, active) {
        if (active) savedOwner?.let { saved(it.id, it.currency, it.name, positive) }
    }

    BubbleScaffold(
        hostInsets = hostInsets,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        scrollBehindBarsWithIme = true,
        bottomBarScrim = false,
        topBar = {
            BubbleTopBar(stringResource(if (debtId > 0) R.string.editdebtcollaps else R.string.newdebt), back,
                hostInsets, backEnabled = !busy)
        },
        bottomAction = {
            if (failed) Text(stringResource(R.string.debt_save_failed),
                Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error)
            BubbleButton({
                showFieldErrors = true
                focus.clearFocus()
                val remindersAllowed = DebtReminders.get(context).allowed()
                when {
                    amountError != null -> scope.launch { scroll.animateScrollTo(0) }
                    nameMissing -> scope.launch {
                        withFrameNanos {}
                        nameRequester.bringIntoView()
                    }
                    vm.hasPastReminder() && remindersAllowed -> scope.launch { scroll.animateScrollTo(scroll.maxValue) }
                    parsed != null -> {
                        if (vm.remind.value == true && vm.hasDueChange && !remindersAllowed) {
                            vm.dueDay.value?.let { vm.setDueDraft(it, null) }
                        }
                        feedback()
                        vm.saveDebt(ownerId, debtId, name.text.toString().trim(), if (positive) parsed else -parsed,
                            vm.date.value ?: LedgerDate.today(), note.text.toString())
                    }
                }
            }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("setFinanceButton"),
                enabled = !busy && !loadingPhoto && savedOwner == null && (selectedPersonId == null || selectedPerson != null),
                colors = directionColors.copy(containerColor = saveFill, contentColor = saveInk)) {
                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                Text(stringResource(R.string.debt_save), Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        BubbleFormScroll(padding, Modifier.testTag("debtFormScroll"), scroll,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BubbleDirectionTabs(
                directions = listOf(true, false),
                selected = positive,
                indicatorColor = directionColors.containerColor,
                enabled = !busy && !vm.hasRepayments,
                onSelect = { positive = it },
                tabModifier = { Modifier.testTag(if (it) "directionPositive" else "directionNegative") },
            ) { direction, active ->
                val ink by animateColorAsState(
                    if (active) directionColors.contentColor else colors.ink, tween(200), label = "Debt tab color",
                )
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
            } else {
                val errorMessage = amountError?.takeIf { showFieldErrors }?.let { stringResource(it) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BubbleAmountField(
                        amount, currency, {}, Modifier.testTag("debtSumEditText"),
                        acceptEdits = !busy, errorMessage = errorMessage, containerColor = amountContainer, contentColor = amountInk,
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
                    if (errorMessage != null) Text(
                        errorMessage,
                        Modifier.padding(horizontal = 18.dp).testTag("debtAmountError")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = colors.danger, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            BubbleGroup {
                CreateDebtPersonFields(vm, personId > 0, name, selectedPerson, showFieldErrors && nameMissing, nameRequester, onContactClick)
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
