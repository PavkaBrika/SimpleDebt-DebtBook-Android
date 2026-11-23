package com.breckneck.debtbook.debt.details

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.R
import com.breckneck.debtbook.databinding.SheetDebtStatementPreviewBinding
import com.breckneck.debtbook.debt.statement.formatPersonDebtStatement
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DebtStatementPreviewDialog : BottomSheetDialogFragment(R.layout.sheet_debt_statement_preview) {
    private val card by viewModels<DebtDetailsViewModel>(ownerProducer = { requireParentFragment() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sheet = SheetDebtStatementPreviewBinding.bind(view)
        sheet.cancelButton.setOnClickListener { dismiss() }
        sheet.sendButton.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, card.humanName.value)
                .putExtra(Intent.EXTRA_TEXT, sheet.statementText.text.toString())
            startActivity(Intent.createChooser(send, card.humanName.value))
            dismiss()
        }
        fun render(state: DebtDetailsState?) {
            val ready = state == DebtDetailsState.SUCCESS || state == DebtDetailsState.EMPTY
            sheet.sendButton.isEnabled = ready
            if (!ready) {
                sheet.includeSettled.isEnabled = false
                sheet.includeEarlyEntries.isVisible = false
                sheet.statementText.setText(if (state == DebtDetailsState.ERROR) R.string.history_load_failed else R.string.loading)
                return
            }
            val history = requireNotNull(card.loans.value)
            val filter = requireNotNull(card.debtFilter.value)
            sheet.includeSettled.isEnabled = history.settledLoans.any { history.includesEntry(it.original, filter) }
            sheet.includeEarlyEntries.isVisible = history.earlyEntries.any { history.includesEntry(it, filter) }
            sheet.statementText.text = formatPersonDebtStatement(
                resources, card.humanName.value.orEmpty(), card.humanCurrency.orEmpty(),
                requireNotNull(card.overallSum.value), history, filter, requireNotNull(card.debtOrder.value),
                includeSettled = sheet.includeSettled.isChecked,
                includeEarlyEntries = sheet.includeEarlyEntries.isChecked
            )
        }
        sheet.includeSettled.setOnCheckedChangeListener { _, _ -> render(card.screenState.value) }
        sheet.includeEarlyEntries.setOnCheckedChangeListener { _, _ -> render(card.screenState.value) }
        card.screenState.observe(viewLifecycleOwner, ::render)
    }

    override fun onStart() {
        super.onStart()
        (requireDialog() as BottomSheetDialog).behavior.apply {
            setShouldRemoveExpandedCorners(false)
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
}
