package com.breckneck.debtbook.debt.details

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.R
import com.breckneck.debtbook.debt.statement.formatPersonDebtStatement
import com.breckneck.deptbook.domain.usecase.Settings.GetAddSumInShareText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DebtStatementPreviewDialog : DialogFragment() {
    private val card by viewModels<DebtDetailsViewModel>(ownerProducer = { requireParentFragment() })
    @Inject lateinit var getAddSumInShareText: GetAddSumInShareText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val preview = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.statement_preview)
            .setMessage(R.string.loading)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.statement_send) { dialog, _ ->
                val text = (dialog as AlertDialog).findViewById<TextView>(android.R.id.message)!!.text.toString()
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, card.humanName.value)
                    .putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(send, card.humanName.value))
            }.create()

        // A restored dialog can start before the card has finished reading its ledger.
        card.screenState.observe(this) { state ->
            val ready = state == DebtDetailsState.SUCCESS || state == DebtDetailsState.EMPTY
            val message = when {
                ready -> formatPersonDebtStatement(
                    resources, card.humanName.value.orEmpty(), card.humanCurrency.orEmpty(),
                    requireNotNull(card.overallSum.value), requireNotNull(card.loans.value),
                    requireNotNull(card.debtFilter.value), requireNotNull(card.debtOrder.value),
                    getAddSumInShareText.execute()
                )
                state == DebtDetailsState.ERROR -> getString(R.string.history_load_failed)
                else -> getString(R.string.loading)
            }
            preview.setMessage(message)
            preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = ready
        }
        return preview
    }
}
