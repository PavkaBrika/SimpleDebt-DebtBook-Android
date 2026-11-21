package com.breckneck.debtbook.debt.details

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.R
import com.breckneck.debtbook.databinding.SheetDebtStatementPreviewBinding
import com.breckneck.debtbook.debt.statement.formatPersonDebtStatement
import com.breckneck.deptbook.domain.usecase.Settings.GetAddSumInShareText
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DebtStatementPreviewDialog : BottomSheetDialogFragment(R.layout.sheet_debt_statement_preview) {
    private val card by viewModels<DebtDetailsViewModel>(ownerProducer = { requireParentFragment() })
    @Inject lateinit var getAddSumInShareText: GetAddSumInShareText

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
        card.screenState.observe(viewLifecycleOwner) { state ->
            val ready = state == DebtDetailsState.SUCCESS || state == DebtDetailsState.EMPTY
            sheet.statementText.text = when {
                ready -> formatPersonDebtStatement(
                    resources, card.humanName.value.orEmpty(), card.humanCurrency.orEmpty(),
                    requireNotNull(card.overallSum.value), requireNotNull(card.loans.value),
                    requireNotNull(card.debtFilter.value), requireNotNull(card.debtOrder.value),
                    getAddSumInShareText.execute()
                )
                state == DebtDetailsState.ERROR -> getString(R.string.history_load_failed)
                else -> getString(R.string.loading)
            }
            sheet.sendButton.isEnabled = ready
        }
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
