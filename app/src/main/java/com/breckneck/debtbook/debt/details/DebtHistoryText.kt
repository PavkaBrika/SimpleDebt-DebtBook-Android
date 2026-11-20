package com.breckneck.debtbook.debt.details

import android.content.Context
import androidx.annotation.StringRes
import com.breckneck.debtbook.R
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.LedgerDate

@StringRes
internal fun DebtDomain.historyTitle(): Int = when {
    cancelled -> R.string.repayment_cancelled
    repaymentOfId != null && sum.signum() < 0 -> R.string.repayment_received
    repaymentOfId != null -> R.string.repayment_paid
    sum.signum() >= 0 -> R.string.debt_lent
    else -> R.string.debt_borrowed
}

internal fun Context.originalDebtLabel(debt: DebtDomain, currency: String): String =
    getString(R.string.repayment_context, DecimalInput.format(debt.sum.abs()), currency, LedgerDate.display(debt.date))
