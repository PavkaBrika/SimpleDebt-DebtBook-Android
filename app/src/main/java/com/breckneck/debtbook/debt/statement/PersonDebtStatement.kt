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
    includeSettled: Boolean = false,
    includeEarlyEntries: Boolean = false
): String = buildString {
    val locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: java.util.Locale.getDefault()
    fun money(value: BigDecimal) = "${MoneyDisplay.formatExact(value, currency, locale)} $currency"
    fun signed(value: BigDecimal) = if (value.signum() > 0) "+${money(value)}" else money(value)
    fun appendEntry(row: DebtDomain, label: String) {
        append(LedgerDate.display(row.date, locale)).append(" · ").append(label)
    }

    appendLine(resources.getString(R.string.statement_title))
    appendLine(name)
    val balanceLabel = when (balance.signum()) {
        1 -> R.string.statement_you_owe
        -1 -> R.string.statement_i_owe
        else -> R.string.statement_even
    }
    appendLine(resources.getString(balanceLabel, money(balance.abs())))
    appendLine()

    val selected = history.statementOriginals(includeSettled, includeEarlyEntries).filter { history.includesEntry(it, filter) }
    val unclassified = selected.any { history.loan(it.id) == null }
    val heading = when (filter) {
        Filter.ALL -> R.string.statement_all
        Filter.POSITIVE -> if (unclassified) R.string.statement_positive_entries else R.string.statement_positive
        Filter.NEGATIVE -> if (unclassified) R.string.statement_negative_entries else R.string.statement_negative
    }
    appendLine(resources.getString(heading))
    if (selected.isEmpty()) append(resources.getString(R.string.statement_empty))
    for ((index, row) in sortDebts(selected, order).withIndex()) {
        if (index > 0) append("\n\n")
        val loan = history.loan(row.id)
        if (loan == null) {
            appendEntry(row, resources.getString(R.string.statement_entry, signed(row.sum)))
            row.info?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
            continue
        }
        val lent = row.sum.signum() > 0
        val principal = if (lent) R.string.statement_lent else R.string.statement_borrowed
        appendEntry(row, resources.getString(principal, money(row.sum.abs())))
        for (payment in loan.repayments) {
            val label = if (payment.cancelled) R.string.statement_cancelled else R.string.statement_payment
            append('\n')
            appendEntry(payment, resources.getString(label, money(payment.sum.abs())))
        }
        append('\n').append(resources.getString(R.string.statement_repaid, money(row.sum.abs() - loan.remaining)))
        append('\n').append(resources.getString(R.string.statement_remaining, money(loan.remaining)))
    }
}
