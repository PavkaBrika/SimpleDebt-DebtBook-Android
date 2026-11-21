package com.breckneck.debtbook.debt.create

import android.os.Bundle
import android.provider.ContactsContract.Contacts
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.reminders.DebtReminders
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.LedgerDate
import java.math.BigDecimal

@Composable
internal fun CreateDebtScreen(vm: CreateDebtViewModel, args: Bundle, back: () -> Unit, feedback: () -> Unit,
    saved: (Int, String, String) -> Unit) {
    val context = LocalContext.current
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true, false).forEach { direction ->
                    FilterChip(positive == direction, { positive = direction; feedback() },
                        { Text(stringResource(if (direction) R.string.show_positive else R.string.show_negative)) },
                        Modifier.weight(1f).testTag(if (direction) "directionPositive" else "directionNegative"),
                        enabled = !busy && !vm.hasRepayments)
                }
            }
            if (vm.hasRepayments) {
                BubbleGroup { BubbleValueRow(stringResource(R.string.sum), "${amount.text} $currency",
                    Modifier.testTag("lockedAmount")) }
            } else BubbleAmountField(amount, currency, { error = 0 }, Modifier.testTag("debtSumEditText"), acceptEdits = !busy)
            BubbleGroup {
                if (personId > 0) BubbleValueRow(stringResource(R.string.person_name), name.text.toString())
                else {
                    BubbleTextFieldRow(Icons.Outlined.Person, stringResource(R.string.person_name), name, "", !busy,
                        null, { true }, { error = 0 }, Modifier.testTag("humanNameEditText"))
                    TextButton({
                        try { contact.launch(null) } catch (_: android.content.ActivityNotFoundException) {
                            Toast.makeText(context, R.string.no_permission, Toast.LENGTH_SHORT).show()
                        }
                    }, enabled = !busy) { Text(stringResource(R.string.contacts)) }
                }
                BubbleFieldRow(Icons.Outlined.Payments, stringResource(R.string.goal_currency),
                    Modifier.clickable(enabled = !busy && personId <= 0) { picker = true }) { Text(currency) }
                BubbleTextFieldRow(Icons.Outlined.Edit, stringResource(R.string.comment), note, "", !busy, null,
                    { true }, {}, Modifier.testTag("debtInfoEditText"))
                CreateDebtDates(vm)
            }
        }
        if (error != 0 || failed) Text(stringResource(if (error != 0) error else R.string.history_change_failed),
            Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
        BubbleButton({
            val parsed = DecimalInput.parse(amount.text.toString(), currency, previous?.abs())
            error = when {
                name.text.isBlank() && personId <= 0 -> R.string.youmustentername
                parsed == null || parsed.signum() < 0 -> R.string.invalid_decimal_amount
                parsed.signum() == 0 -> R.string.zerodebt
                else -> 0
            }
            if (error == 0 && parsed != null) {
                if (vm.remind.value == true && vm.hasDueChange && !DebtReminders.get(context).allowed()) {
                    vm.dueDay.value?.let { vm.setDueDraft(it, false) }
                }
                feedback()
                vm.saveDebt(personId, debtId, name.text.toString().trim(), if (positive) parsed else -parsed,
                    vm.date.value ?: LedgerDate.today(), note.text.toString())
            }
        }, Modifier.fillMaxWidth().padding(12.dp).testTag("setFinanceButton"), enabled = !busy && savedId == null) {
            Text(stringResource(R.string.debt_save))
        }
    }
    if (picker) {
        val options = stringArrayResource(R.array.currencies).toList()
        SettingsPickerBottomSheet(stringResource(R.string.select_currency), options, options.indexOfFirst { it.substringAfterLast(' ') == currency },
            { vm.currency.value = options[it].substringAfterLast(' '); picker = false }, { picker = false })
    }
}
