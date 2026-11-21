package com.breckneck.debtbook.debt.details

import com.breckneck.deptbook.domain.util.MoneyDisplay
import android.content.Context
import androidx.annotation.StringRes
import com.breckneck.debtbook.R
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.LedgerDate

@StringRes
internal fun DebtDomain.historyTitle(earlyEntry: Boolean = false): Int = when {
    earlyEntry -> R.string.early_entry
    cancelled -> R.string.repayment_cancelled
    repaymentOfId != null && sum.signum() < 0 -> R.string.repayment_received
    repaymentOfId != null -> R.string.repayment_paid
    sum.signum() >= 0 -> R.string.debt_lent
    else -> R.string.debt_borrowed
}

internal fun Context.originalDebtLabel(debt: DebtDomain, currency: String): String =
    getString(R.string.repayment_context, MoneyDisplay.formatExact(debt.sum.abs(), currency), currency, LedgerDate.display(debt.date))
