package com.breckneck.debtbook.debt.details

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.R
import com.breckneck.deptbook.domain.util.DecimalInput
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.math.BigDecimal

/** DialogFragment restores the form together with the parent's saved request ID. */
class RepaymentDialogFragment : DialogFragment() {
    private val vm by viewModels<DebtDetailsViewModel>({ requireParentFragment() })

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_repayment, null)
        val amount = view.findViewById<TextInputEditText>(R.id.repaymentAmountInput)
        val inputLayout = view.findViewById<TextInputLayout>(R.id.repaymentAmountLayout)
        val full = view.findViewById<View>(R.id.repaymentFullButton)
        amount.setText(vm.repaymentAmount.value.orEmpty())
        amount.doAfterTextChanged { vm.changeRepaymentAmount(it.toString()) }
        full.setOnClickListener { amount.setText(DecimalInput.format(vm.repaymentRemaining.value ?: BigDecimal.ZERO)) }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.repayment_received)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> vm.closeRepayment() }
            .create()

        fun render() {
            val debt = vm.repaymentDebtId.value?.let(vm::originalDebt)
            if (debt != null) {
                dialog.setTitle(if (debt.sum.signum() > 0) R.string.repayment_received else R.string.repayment_paid)
                view.findViewById<TextView>(R.id.repaymentContext).text = requireContext().originalDebtLabel(debt, vm.humanCurrency.orEmpty())
            }
            val rest = DecimalInput.format(vm.repaymentRemaining.value ?: BigDecimal.ZERO)
            view.findViewById<TextView>(R.id.repaymentRemaining).text = getString(R.string.debt_remaining, rest, vm.humanCurrency)
            view.findViewById<TextView>(R.id.repaymentBalanceHelp).text = getString(R.string.repayment_balance_help,
                DecimalInput.format(vm.overallSum.value ?: BigDecimal.ZERO), vm.humanCurrency)
            inputLayout.error = when (vm.repaymentError.value) {
                RepaymentError.INVALID_AMOUNT -> getString(R.string.repayment_invalid)
                RepaymentError.EXCEEDS_REMAINING -> getString(R.string.repayment_over, rest, vm.humanCurrency)
                RepaymentError.SAVE_FAILED -> getString(R.string.repayment_save_failed)
                null -> null
            }
            val busy = vm.isDeleting.value == true
            val ready = debt != null && vm.screenState.value == DebtDetailsState.SUCCESS
            amount.isEnabled = !busy
            full.isEnabled = !busy && ready
            isCancelable = !busy
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy && ready
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !busy
            view.findViewById<View>(R.id.repaymentProgress).visibility = if (busy) View.VISIBLE else View.GONE
        }
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { vm.saveRepayment() }
            render()
        }
        amount.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { vm.saveRepayment(); true } else false
        }
        vm.repaymentDebtId.observe(this) { if (it == null) dismissAllowingStateLoss() else render() }
        vm.repaymentRemaining.observe(this) { render() }
        vm.repaymentError.observe(this) { render() }
        vm.isDeleting.observe(this) { render() }
        vm.screenState.observe(this) { render() }
        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        vm.closeRepayment()
        super.onCancel(dialog)
    }

    companion object { const val TAG = "repayment" }
}
