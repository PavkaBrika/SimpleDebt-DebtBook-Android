package com.breckneck.debtbook.debt.statement

import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import com.breckneck.debtbook.R
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.model.PersonDebtHistory
import com.breckneck.deptbook.domain.util.DebtOrderAttribute
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyDisplay
import com.breckneck.deptbook.domain.util.sortDebts
import java.math.BigDecimal

/** Recipient-facing text shared by the person card and Bubble. Balance includes the opening adjustment. */
fun formatPersonDebtStatement(
    resources: Resources,
    name: String,
    currency: String,
    balance: BigDecimal,
    history: PersonDebtHistory,
    filter: Filter,
    order: Pair<DebtOrderAttribute, Boolean>,
    includeRunningTotals: Boolean
): String = buildString {
    val locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: java.util.Locale.getDefault()
    fun money(value: BigDecimal) = "${MoneyDisplay.formatExact(value, currency, locale)} $currency"
    fun signed(value: BigDecimal) = if (value.signum() > 0) "+${money(value)}" else money(value)
    var runningTotal = BigDecimal.ZERO
    fun appendEntry(row: DebtDomain, label: String) {
        append(LedgerDate.display(row.date, locale)).append(" · ").append(label)
        row.info?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        if (includeRunningTotals) {
            runningTotal += row.effectiveSum
            append('\n').append(resources.getString(R.string.statement_subtotal, signed(runningTotal)))
        }
    }

    appendLine(resources.getString(R.string.statement_title, name))
    val balanceLabel = when (balance.signum()) {
        1 -> R.string.statement_you_owe
        -1 -> R.string.statement_i_owe
        else -> R.string.statement_even
    }
    appendLine(resources.getString(balanceLabel, money(balance.abs())))
    val scope = when (filter) {
        Filter.ALL -> R.string.statement_all
        Filter.POSITIVE -> R.string.statement_positive
        Filter.NEGATIVE -> R.string.statement_negative
    }
    append(resources.getString(scope))

    val originals = (history.openLoans + history.settledLoans).map { it.original } + history.zeroAmountEntries
    val selected = originals.filter { history.includesEntry(it, filter) }
    if (selected.any { history.loan(it.id)?.hasKnownPurpose != true }) {
        val entryScope = when (filter) {
            Filter.ALL -> R.string.statement_entry_signs
            Filter.POSITIVE -> R.string.statement_positive_entries
            Filter.NEGATIVE -> R.string.statement_negative_entries
        }
        append('\n').append(resources.getString(entryScope))
    }
    for (row in sortDebts(selected, order)) {
        append("\n\n")
        val loan = history.loan(row.id)
        if (loan == null || !loan.hasKnownPurpose) {
            appendEntry(row, resources.getString(R.string.statement_entry, signed(row.sum)))
            continue
        }
        val lent = row.sum.signum() > 0
        val principal = if (lent) R.string.statement_lent else R.string.statement_borrowed
        appendEntry(row, resources.getString(principal, money(row.sum.abs())))
        for (payment in loan.repayments) {
            val label = when {
                payment.cancelled -> R.string.statement_cancelled
                lent -> R.string.statement_from_you
                else -> R.string.statement_from_me
            }
            append('\n')
            appendEntry(payment, resources.getString(label, money(payment.sum.abs())))
        }
        append('\n').append(resources.getString(R.string.statement_repaid, money(row.sum.abs() - loan.remaining)))
        append('\n').append(resources.getString(R.string.statement_remaining, money(loan.remaining)))
    }
}
