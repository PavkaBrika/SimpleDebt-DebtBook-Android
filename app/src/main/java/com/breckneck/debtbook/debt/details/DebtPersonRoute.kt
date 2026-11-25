package com.breckneck.debtbook.debt.details

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.toRoute
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.core.navigation.DEBT_PERSON_RESULT
import com.breckneck.debtbook.core.navigation.DebtForm
import com.breckneck.debtbook.core.navigation.Destination
import com.breckneck.debtbook.core.navigation.EditDebtPerson
import com.breckneck.debtbook.core.navigation.LocalRouteActive
import com.breckneck.debtbook.core.ui.components.ConfirmationBottomSheet
import com.breckneck.debtbook.debt.reminders.DebtDateSheet
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.usecase.Debt.ExportDebtDataInExcelUseCase
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DebtSheet { ACTIONS, DUE, REMOVE }
private enum class PersonSheet { DELETE, SHARE, STATEMENT }

@Composable
internal fun Destination.DebtPersonRoute(insets: PaddingValues, activity: MainActivity, main: MainViewModel) {
    val vm = hiltViewModel<DebtDetailsViewModel, DebtDetailsViewModel.Factory>(entry) {
        it.create(entry.toRoute())
    }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val deleted by vm.humanDeleted.observeAsState(false)
    val failed by vm.operationFailed.observeAsState(false)
    val active = LocalRouteActive.current
    LaunchedEffect(active, deleted) {
        if (active && deleted) {
            activity.startClickVibration()
            main.onActionClick()
            back()
        }
    }
    LaunchedEffect(failed) {
        if (failed) {
            Toast.makeText(activity, R.string.history_change_failed, Toast.LENGTH_LONG).show()
            vm.dismissOperationError()
        }
    }
    WhileResumed {
        val changed = entry.savedStateHandle.remove<Int>(DEBT_PERSON_RESULT)
        if (changed == null) vm.onResume()
        else {
            if (changed != 0) vm.showAddedDebt(changed > 0)
            vm.retry()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = vm.retry()
        }
        ContextCompat.registerReceiver(activity, receiver, IntentFilter().apply {
            addAction(ACTION_DATE_CHANGED)
            addAction(ACTION_TIMEZONE_CHANGED)
            addAction(ACTION_TIME_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            vm.repaymentFeedback.collect { event ->
                val feedback = when (event) {
                    RepaymentFeedback.SETTLED -> HapticFeedbackConstantsCompat.CONFIRM
                    RepaymentFeedback.OVER_LIMIT -> HapticFeedbackConstantsCompat.REJECT
                }
                ViewCompat.performHapticFeedback(view, feedback)
            }
        } finally {
            activity.unregisterReceiver(receiver)
        }
    }

    var initialSheet by rememberSaveable { mutableStateOf(DebtSheet.ACTIONS) }
    var personSheet by rememberSaveable { mutableStateOf<PersonSheet?>(null) }
    DebtDetailsScreen(
        vm, insets, onBack = ::back,
        onAdd = { id, currency, name ->
            if (isCurrent) main.onActionClick()
            open(DebtForm(idHuman = id, currency = currency, name = name))
        },
        editPerson = { vm.humanId?.let { open(EditDebtPerson(it)) } },
        delete = { personSheet = PersonSheet.DELETE },
        share = { personSheet = PersonSheet.SHARE },
        onRecord = { id -> initialSheet = DebtSheet.ACTIONS; vm.selectDebtAction(id) },
        onDue = { id -> initialSheet = DebtSheet.DUE; vm.selectDebtAction(id) },
        onRepaymentAction = { id -> initialSheet = DebtSheet.REMOVE; vm.selectDebtAction(id) },
    )
    DebtSheets(vm, initialSheet, resetSheet = { initialSheet = DebtSheet.ACTIONS }) { debt, currency, name, hasRepayments, canRemind ->
        val pending = canRemind && debt.reminderMinute != null && !debt.reminderDelivered &&
            debt.dueDay?.let(DebtReminderPolicy::canSchedule) == true
        if (isCurrent) main.onActionClick()
        open(DebtForm(
            idHuman = debt.idHuman, idDebt = debt.id, currency = currency, name = name,
            date = debt.date, sum = debt.sum.toPlainString(), info = debt.info,
            dueDayOnEdit = debt.dueDay, remindOnEdit = pending,
            minuteOnEdit = debt.reminderMinute ?: -1, hasRepayments = hasRepayments, canRemind = canRemind,
        ))
    }
    val closePersonSheet = { personSheet = null }
    when (personSheet) {
        PersonSheet.DELETE -> {
            val name by vm.humanName.observeAsState()
            ConfirmationBottomSheet(
                title = name?.takeIf(String::isNotBlank)?.let { stringResource(R.string.person_delete_title, it) }
                    ?: stringResource(R.string.person_delete_action),
                message = stringResource(R.string.person_delete_message),
                confirmText = stringResource(R.string.delete),
                dismissText = stringResource(R.string.cancel),
                onConfirm = { closePersonSheet(); vm.deleteHuman() },
                onDismiss = closePersonSheet,
                modifier = Modifier.testTag("personDeleteSheet"),
            )
        }
        PersonSheet.SHARE -> DebtShareSheet(closePersonSheet, { personSheet = PersonSheet.STATEMENT }) {
            closePersonSheet()
            scope.shareExcel(activity, vm)
        }
        PersonSheet.STATEMENT -> DebtStatementSheet(vm, closePersonSheet) { name, text ->
            val send = Intent(ACTION_SEND).setType("text/plain")
                .putExtra(EXTRA_SUBJECT, name)
                .putExtra(EXTRA_TEXT, text)
            closePersonSheet()
            activity.startActivity(createChooser(send, name))
        }
        null -> Unit
    }
}

@Composable
private fun DebtSheets(
    vm: DebtDetailsViewModel,
    initialSheet: DebtSheet,
    resetSheet: () -> Unit,
    onEdit: (DebtDomain, String, String, Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val repaymentId by vm.repaymentDebtId.observeAsState()
    val debtId by vm.debtActionId.observeAsState()
    val history by vm.history.observeAsState()
    val busy by vm.isDeleting.observeAsState(false)
    val restoreRemaining by vm.restoreRemaining.observeAsState()
    val selected = repaymentId ?: debtId
    val debt = history?.firstOrNull { it.id == selected }
    if (debt != null && repaymentId != null) {
        RepaymentSheet(vm, debt)
    } else if (debt != null) key(debt.id) {
        var sheet by rememberSaveable { mutableStateOf(initialSheet) }
        val rest = vm.remaining(debt)
        val currency = vm.humanCurrency.orEmpty()
        val hasRepayments = vm.hasRepayments(debt.id)
        val earlyEntry = debt.repaymentOfId == null && vm.originalDebt(debt.id) == null
        val close = { resetSheet(); vm.selectDebtAction(null) }
        val dueEditable = earlyEntry || rest.signum() > 0
        when (if (sheet == DebtSheet.DUE && !dueEditable) DebtSheet.ACTIONS else sheet) {
            DebtSheet.ACTIONS -> DebtActionsSheet(
                debt, debt.repaymentOfId?.let(vm::originalDebt), currency, rest, hasRepayments, earlyEntry,
                onDismiss = close,
                onRepay = { close(); vm.openRepayment(debt) },
                onDue = { sheet = DebtSheet.DUE },
                onEdit = {
                    close()
                    onEdit(
                        debt, currency, vm.humanName.value.orEmpty(), hasRepayments,
                        dueEditable
                    )
                },
                onRemove = { sheet = DebtSheet.REMOVE }
            )
            DebtSheet.DUE -> DebtDateSheet(
                title = stringResource(R.string.due_settings), initialDay = debt.dueDay,
                minDay = LedgerDate.canonical(debt.date), clearable = true,
                initialMinute = debt.reminderMinute ?: DebtReminderPolicy.DEFAULT_MINUTE,
                initialReminder = (debt.reminderMinute != null && !debt.reminderDelivered).takeIf { rest.signum() > 0 },
                onDismiss = close
            ) { day, minute ->
                val pendingMinute = debt.reminderMinute.takeUnless { debt.reminderDelivered || rest.signum() <= 0 ||
                    debt.dueDay.orEmpty() < LedgerDate.today() }
                if (day != debt.dueDay || minute != pendingMinute) vm.saveDue(debt.id, day, minute)
            }
            DebtSheet.REMOVE -> {
                val repayment = debt.repaymentOfId != null
                if (repayment && debt.cancelled) {
                    RestoreRepaymentSheet(debt, debt.repaymentOfId?.let(vm::originalDebt), currency, busy, restoreRemaining,
                        onRestore = { vm.restoreRepayment(debt.id) },
                        onDismiss = { if (initialSheet == DebtSheet.REMOVE) close() else sheet = DebtSheet.ACTIONS })
                } else {
                    val action = stringResource(if (repayment) R.string.repayment_cancel else R.string.delete)
                    ConfirmationBottomSheet(
                        title = action,
                        message = if (repayment) listOfNotNull(
                            "${MoneyDisplay.formatExact(debt.sum.abs(), currency)} $currency · ${LedgerDate.display(debt.date)}",
                            debt.repaymentOfId?.let(vm::originalDebt)?.let { context.originalDebtLabel(it, currency) },
                            stringResource(R.string.repayment_cancel_confirm, MoneyDisplay.formatExact(debt.sum.abs(), currency), currency)
                        ).joinToString("\n")
                        else stringResource(when {
                            earlyEntry -> R.string.early_entry_delete_question
                            hasRepayments -> R.string.debt_delete_with_repayments_question
                            else -> R.string.debt_delete_question
                        }),
                        confirmText = action,
                        dismissText = stringResource(R.string.cancel),
                        onConfirm = { close(); vm.deleteDebt(debt) },
                        onDismiss = { if (initialSheet == DebtSheet.REMOVE) close() else sheet = DebtSheet.ACTIONS }
                    )
                }
            }
        }
    }
}


private fun CoroutineScope.shareExcel(context: Context, vm: DebtDetailsViewModel) {
    val humanName = vm.humanName.value.orEmpty()
    val currency = vm.humanCurrency.orEmpty()
    val rows = vm.resultedDebtList.value.orEmpty().map({ withHistoryDescription(context, vm, it) })
    val titles = listOf(context.getString(R.string.date), "${context.getString(R.string.sum)} ($currency)", context.getString(R.string.comment))
    launch {
        try {
            val uri = withContext(Dispatchers.IO) {
                context.writeDebtWorkbook(humanName) { output ->
                    ExportDebtDataInExcelUseCase().execute(rows, humanName, currency, titles, output)
                }
            }
            if (Build.VERSION.SDK_INT >= 29) Toast.makeText(context, R.string.excel_folder_toast_hint, Toast.LENGTH_SHORT).show()
            val send = Intent(ACTION_SEND).setType(EXCEL_MIME).putExtra(EXTRA_STREAM, uri)
                .addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(createChooser(send, humanName))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e("DebtPersonRoute", "Could not export debt history", failure)
            Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show()
        }
    }
}

private fun withHistoryDescription(context: Context, vm: DebtDetailsViewModel, debt: DebtDomain): DebtDomain {
    val details = if (debt.repaymentOfId != null) {
        debt.repaymentOfId?.let(vm::originalDebt)?.let { context.originalDebtLabel(it, vm.humanCurrency.orEmpty()) }
    } else {
        val remaining = vm.loans.value?.loan(debt.id)?.remaining ?: debt.sum.abs()
        context.getString(R.string.debt_remaining, MoneyDisplay.formatExact(remaining, vm.humanCurrency.orEmpty()), vm.humanCurrency)
    }
    val description = listOfNotNull(context.getString(debt.historyTitle()), details, debt.info?.takeIf { it.isNotBlank() })
    return debt.copy(info = description.joinToString("; "))
}
