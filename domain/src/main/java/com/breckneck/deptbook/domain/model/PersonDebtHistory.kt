package com.breckneck.deptbook.domain.model

import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.RepaymentPolicy
import java.math.BigDecimal

data class LoanHistory(
    val original: DebtDomain,
    val repayments: List<DebtDomain>,
    val remaining: BigDecimal
) {
    val hasKnownPurpose: Boolean
        get() = original.recordedAsLoan || repayments.isNotEmpty() || original.dueDay != null
}

/** Derived from a single person's ledger. Currency and opening adjustment belong to the person. */
class PersonDebtHistory private constructor(
    private val unsettledEntries: List<LoanHistory>,
    val settledLoans: List<LoanHistory>,
    private val zeroAmountEntries: List<DebtDomain>
) {
    val openLoans = unsettledEntries.filter(LoanHistory::hasKnownPurpose)
    val earlyEntries = unsettledEntries.filterNot(LoanHistory::hasKnownPurpose).map { it.original } + zeroAmountEntries

    private val loansById by lazy {
        (openLoans.asSequence() + settledLoans.asSequence()).associateBy { it.original.id }
    }

    fun loan(id: Int): LoanHistory? = loansById[id]

    /** Preserve R2.4 ordering for equal dates or amounts, including in running totals. */
    fun statementOriginals(includeSettled: Boolean): List<DebtDomain> = buildList {
        unsettledEntries.mapTo(this) { it.original }
        if (includeSettled) settledLoans.mapTo(this) { it.original }
        addAll(zeroAmountEntries)
    }

    fun includesEntry(row: DebtDomain, filter: Filter): Boolean {
        val direction = row.repaymentOfId?.let { loan(it)?.original?.sum } ?: row.sum
        return when (filter) {
            Filter.ALL -> true
            Filter.POSITIVE -> direction.signum() >= 0
            Filter.NEGATIVE -> direction.signum() <= 0
        }
    }

    fun matches(filter: Filter, balance: BigDecimal): Boolean = when (filter) {
        Filter.ALL -> true
        Filter.POSITIVE -> balance.signum() > 0 || openLoans.any { it.original.sum.signum() > 0 }
        Filter.NEGATIVE -> balance.signum() < 0 || openLoans.any { it.original.sum.signum() < 0 }
    }

    companion object {
        fun from(rows: List<DebtDomain>): PersonDebtHistory {
            val byOriginal = rows.groupBy { it.repaymentOfId }
            val (originals, zeroAmountEntries) = byOriginal[null].orEmpty().partition { it.sum.signum() != 0 }
            val histories = originals.map { original ->
                // Linked repayments use ISO days. IDs break ties for multiple payments on the same day.
                val repayments = byOriginal[original.id].orEmpty().sortedWith(compareBy({ it.date }, { it.id }))
                LoanHistory(original, repayments, RepaymentPolicy.remaining(original, repayments))
            }
            val (settled, unsettled) = histories.partition { it.hasKnownPurpose && it.remaining.signum() == 0 }
            return PersonDebtHistory(unsettled, settled, zeroAmountEntries)
        }
    }
}
