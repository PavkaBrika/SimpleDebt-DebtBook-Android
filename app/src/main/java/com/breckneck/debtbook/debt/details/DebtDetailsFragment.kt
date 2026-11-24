package com.breckneck.debtbook.debt.details

import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.breckneck.debtbook.common.launchOnLifecycleStarted
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.ui.components.ConfirmationBottomSheet
import com.breckneck.debtbook.debt.reminders.DebtDateSheet
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.usecase.Debt.ExportDebtDataInExcelUseCase
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


internal const val DEBT_DETAILS_RESULT = "debtDetailsResult"

@AndroidEntryPoint
class DebtDetailsFragment : Fragment() {

    private val TAG = "DebtDetailsFragment"

    private enum class DebtSheet { ACTIONS, DUE, REMOVE }

    interface OnButtonClickListener {
        fun addNewDebtFragment(idHuman: Int, currency: String, name: String)

        fun editDebt(
            debtDomain: DebtDomain, currency: String, name: String,
            hasRepayments: Boolean = false, canRemind: Boolean = true
        )

        fun deleteHuman()

        fun onBackButtonClick()

        fun onTickVibration()
    }

    var buttonClickListener: OnButtonClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        buttonClickListener = context as OnButtonClickListener
    }

    private val vm by viewModels<DebtDetailsViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(R.id.debtDetailsSheets) { insets ->
            var initialSheet by rememberSaveable { mutableStateOf(DebtSheet.ACTIONS) }
            DebtDetailsScreen(vm, insets, requireActivity() as OnButtonClickListener,
                ::showDeleteHumanDialog, { showShareDialog(vm.humanName.value, vm.humanCurrency) },
                onRecord = { id -> initialSheet = DebtSheet.ACTIONS; vm.selectDebtAction(id) },
                onDue = { id -> initialSheet = DebtSheet.DUE; vm.selectDebtAction(id) },
                onRepaymentAction = { id -> initialSheet = DebtSheet.REMOVE; vm.selectDebtAction(id) })
            DebtSheets(initialSheet) { initialSheet = DebtSheet.ACTIONS }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchOnLifecycleStarted {
            vm.repaymentFeedback.collect { event ->
                val feedback = when (event) {
                    RepaymentFeedback.SETTLED -> HapticFeedbackConstantsCompat.CONFIRM
                    RepaymentFeedback.OVER_LIMIT -> HapticFeedbackConstantsCompat.REJECT
                }
                ViewCompat.performHapticFeedback(view, feedback)
            }
        }
        setFragmentResultListener(DEBT_DETAILS_RESULT) { _, result ->
            if (result.getBoolean("newDebt")) vm.showAddedDebt(result.getBoolean("positive"))
            vm.retry()
        }
        if (vm.isHumanDeleteDialogShown.value == true) showDeleteHumanDialog()
        if (vm.isShareDialogShown.value == true) showShareDialog(vm.humanName.value, vm.humanCurrency)
        vm.humanDeleted.observe(viewLifecycleOwner) { if (it) buttonClickListener?.deleteHuman() }
        vm.operationFailed.observe(viewLifecycleOwner) { if (it) {
            Toast.makeText(requireContext(), R.string.history_change_failed, Toast.LENGTH_LONG).show()
            vm.dismissOperationError()
        } }
    }

    private fun showDeleteHumanDialog() {
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(R.layout.dialog_are_you_sure)
        vm.onHumanDeleteDialogOpen()

        val personName = vm.humanName.value
        dialog.findViewById<TextView>(R.id.dialogTitle)!!.text = if (personName.isNullOrBlank()) {
            getString(R.string.person_delete_action)
        } else {
            getString(R.string.person_delete_title, personName)
        }
        dialog.findViewById<TextView>(R.id.dialogMessage)!!.setText(R.string.person_delete_message)

        val confirm = dialog.findViewById<Button>(R.id.okButton)!!
        confirm.setText(R.string.delete)
        confirm.setOnClickListener {
            vm.deleteHuman()
            dialog.dismiss()
        }

        val cancel = dialog.findViewById<Button>(R.id.cancelButton)!!
        cancel.setText(R.string.cancel)
        cancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            vm.onHumanDeleteDialogClose()
        }
        dialog.setOnCancelListener {
            vm.onHumanDeleteDialogClose()
        }

        dialog.show()
    }

    @androidx.compose.runtime.Composable
    private fun DebtSheets(initialSheet: DebtSheet, resetSheet: () -> Unit) {
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
            when (sheet) {
                DebtSheet.ACTIONS -> DebtActionsSheet(
                    debt, debt.repaymentOfId?.let(vm::originalDebt), currency, rest, hasRepayments, earlyEntry,
                    onDismiss = close,
                    onRepay = { close(); vm.openRepayment(debt) },
                    onDue = { sheet = DebtSheet.DUE },
                    onEdit = {
                        close()
                        buttonClickListener?.editDebt(
                            debt, currency, vm.humanName.value.orEmpty(), hasRepayments,
                            canRemind = !hasRepayments || rest.signum() > 0
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
                                debt.repaymentOfId?.let(vm::originalDebt)?.let { requireContext().originalDebtLabel(it, currency) },
                                stringResource(R.string.repayment_cancel_confirm, MoneyDisplay.formatExact(debt.sum.abs(), currency), currency)
                            ).joinToString("\n")
                            else stringResource(if (earlyEntry) R.string.early_entry_delete_question else R.string.debt_delete_question),
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

    private fun showShareDialog(humanName: String?, currency: String?) {
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(R.layout.dialog_share)
        vm.onShareDialogOpen()

        dialog.findViewById<Button>(R.id.deleteButton)!!.setOnClickListener {
            dialog.dismiss()
            DebtStatementPreviewDialog().show(childFragmentManager, "debtStatementPreview")
        }

        dialog.findViewById<Button>(R.id.editButton)!!.setOnClickListener {
            dialog.dismiss()
            val context = requireContext()
            val rows = vm.resultedDebtList.value.orEmpty().map(::withHistoryDescription)
            val titles = listOf(getString(R.string.date), "${getString(R.string.sum)} ($currency)", getString(R.string.comment))
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val uri = withContext(Dispatchers.IO) {
                        context.writeDebtWorkbook(humanName.orEmpty()) { output ->
                            ExportDebtDataInExcelUseCase().execute(rows, humanName.orEmpty(), currency.orEmpty(), titles, output)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 29) Toast.makeText(context, R.string.excel_folder_toast_hint, Toast.LENGTH_SHORT).show()
                    val send = Intent(ACTION_SEND).setType(EXCEL_MIME).putExtra(EXTRA_STREAM, uri)
                        .addFlags(FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(createChooser(send, humanName))
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Could not export debt history", failure)
                    Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.findViewById<Button>(R.id.cancelButton)!!.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            vm.onShareDialogClose()
        }
        dialog.setOnCancelListener {
            vm.onShareDialogClose()
        }

        dialog.show()
    }

    private fun withHistoryDescription(debt: DebtDomain): DebtDomain {
        val details = if (debt.repaymentOfId != null) {
            debt.repaymentOfId?.let(vm::originalDebt)?.let { requireContext().originalDebtLabel(it, vm.humanCurrency.orEmpty()) }
        } else {
            val remaining = vm.loans.value?.loan(debt.id)?.remaining ?: debt.sum.abs()
            getString(R.string.debt_remaining, MoneyDisplay.formatExact(remaining, vm.humanCurrency.orEmpty()), vm.humanCurrency)
        }
        val description = listOfNotNull(getString(debt.historyTitle()), details, debt.info?.takeIf { it.isNotBlank() })
        return debt.copy(info = description.joinToString("; "))
    }

    private val dayChanged = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { vm.retry() }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        ContextCompat.registerReceiver(requireContext(), dayChanged, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        requireContext().unregisterReceiver(dayChanged)
        super.onPause()
    }

}
