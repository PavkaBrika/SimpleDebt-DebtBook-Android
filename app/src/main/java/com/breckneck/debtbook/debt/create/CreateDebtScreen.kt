package com.breckneck.debtbook.debt.create

import android.os.Bundle
import android.provider.ContactsContract.Contacts
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.reminders.DebtReminders
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.LedgerDate

@Composable
internal fun CreateDebtScreen(vm: CreateDebtViewModel, args: Bundle, back: () -> Unit, feedback: () -> Unit,
    saved: (Int, String, String) -> Unit) {
    val context = LocalContext.current
    val colors = bubbleColors()
    val haptic = LocalHapticFeedback.current
    val personId = args.getInt("idHuman", -1)
    val debtId = args.getInt("idDebt", -1)
    val previous = args.getString("sum")?.toBigDecimalOrNull()
    val currency by vm.currency.observeAsState("")
    val amount = rememberTextFieldState(previous?.abs()?.let { DecimalInput.format(it, currency = currency) }.orEmpty())
    val name = rememberTextFieldState(args.getString("name").orEmpty())
    val note = rememberTextFieldState(args.getString("info").orEmpty())
    var positive by rememberSaveable { mutableStateOf((previous?.signum() ?: 1) >= 0) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableIntStateOf(0) }
    val busy by vm.isSaving.observeAsState(false)
    val failed by vm.saveError.observeAsState(false)
    val savedId by vm.savedHumanId.observeAsState()
    LaunchedEffect(savedId) { savedId?.let { saved(it, currency, name.text.toString().trim()) } }

    // The system picker grants access to the chosen contact only.
    val contact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) try {
            context.contentResolver.query(uri, arrayOf(Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name.setTextAndPlaceCursorAtEnd(cursor.getString(0).orEmpty())
            }
        } catch (_: SecurityException) { Toast.makeText(context, R.string.no_permission, Toast.LENGTH_SHORT).show() }
    }

    Column(Modifier.fillMaxSize().background(bubbleColors().background).semantics { testTagsAsResourceId = true }) {
        BubbleTopBar(stringResource(if (debtId > 0) R.string.editdebtcollaps else R.string.newdebt), back,
            PaddingValues(0.dp), backEnabled = !busy)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(colors.fill).padding(4.dp).selectableGroup()) {
                for (direction in listOf(true, false)) {
                    val active = positive == direction
                    val enabled = !busy && !vm.hasRepayments
                    Row(Modifier.weight(1f).clip(CircleShape)
                        .background(if (active) colors.action else colors.fill)
                        .selectable(active, enabled = enabled, role = Role.Tab) {
                            if (!active) {
                                positive = direction
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                        .testTag(if (direction) "directionPositive" else "directionNegative")
                        .heightIn(min = 48.dp).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically) {
                        val ink = if (active) colors.onAction else colors.muted
                        Icon(if (direction) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast, null, Modifier.size(16.dp), tint = ink)
                        Text(stringResource(if (direction) R.string.show_positive else R.string.show_negative),
                            color = ink, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (vm.hasRepayments) {
                BubbleGroup { BubbleValueRow(stringResource(R.string.sum), "${amount.text} $currency",
                    Modifier.testTag("lockedAmount")) }
            } else BubbleAmountField(amount, currency, { error = 0 }, Modifier.testTag("debtSumEditText"), acceptEdits = !busy) {
                if (personId > 0) Text(currency)
                else BubbleOutlinedButton({ picker = true }, enabled = !busy) {
                    Text(currency)
                    Icon(Icons.Outlined.ExpandMore, stringResource(R.string.select_currency), Modifier.size(16.dp))
                }
            }
            BubbleGroup {
                if (personId > 0) BubbleValueRow(stringResource(R.string.person_name), name.text.toString())
                else {
                    BubbleTextFieldRow(Icons.Outlined.Person, stringResource(R.string.person_name), name, stringResource(R.string.debt_name_hint), !busy,
                        null, { true }, { error = 0 }, Modifier.testTag("humanNameEditText"))
                }
                HorizontalDivider(color = colors.border)
                BubbleTextFieldRow(Icons.Outlined.Edit, stringResource(R.string.comment), note, stringResource(R.string.debt_note_hint), !busy, null,
                    { true }, {}, Modifier.testTag("debtInfoEditText"))
                HorizontalDivider(color = colors.border)
                DebtEntryDate(vm)
            }
            if (personId <= 0) TextButton({
                try { contact.launch(null) } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, R.string.no_permission, Toast.LENGTH_SHORT).show()
                }
            }, enabled = !busy) {
                Icon(Icons.Outlined.Person, null, Modifier.size(16.dp))
                Text(stringResource(R.string.contacts), Modifier.padding(start = 6.dp))
            }
            BubbleSectionTitle(stringResource(R.string.debt_return_section))
            BubbleGroup { DebtDueFields(vm) }
        }
        if (error != 0 || failed) Text(stringResource(if (error != 0) error else R.string.history_change_failed),
            Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
        BubbleButton({
            val parsed = DecimalInput.parse(amount.text.toString(), currency, previous?.abs())
            error = when {
                name.text.isBlank() && personId <= 0 -> R.string.youmustentername
                parsed == null || parsed.signum() < 0 -> R.string.invalid_decimal_amount
                parsed.signum() == 0 -> R.string.zerodebt
                vm.hasPastReminder() && DebtReminders.get(context).allowed() -> R.string.debt_future_time
                else -> 0
            }
            if (error == 0 && parsed != null) {
                if (vm.remind.value == true && vm.hasDueChange && !DebtReminders.get(context).allowed()) {
                    vm.dueDay.value?.let { vm.setDueDraft(it, null) }
                }
                feedback()
                vm.saveDebt(personId, debtId, name.text.toString().trim(), if (positive) parsed else -parsed,
                    vm.date.value ?: LedgerDate.today(), note.text.toString())
            }
        }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("setFinanceButton"), enabled = !busy && savedId == null) {
            Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
            Text(stringResource(R.string.debt_save), Modifier.padding(start = 8.dp))
        }
    }
    if (picker) {
        val options = stringArrayResource(R.array.currencies).toList()
        SettingsPickerBottomSheet(stringResource(R.string.select_currency), options, options.indexOfFirst { it.substringAfterLast(' ') == currency },
            { vm.currency.value = options[it].substringAfterLast(' '); picker = false }, { picker = false })
    }
}
