package com.breckneck.deptbook.domain.model

import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.RepaymentPolicy
import java.math.BigDecimal

data class LoanHistory(
    val original: DebtDomain,
    val repayments: List<DebtDomain>,
    val remaining: BigDecimal
)

/** Derived from a single person's ledger. Currency and opening adjustment belong to the person. */
data class PersonDebtHistory(
    val openLoans: List<LoanHistory>,
    val settledLoans: List<LoanHistory>,
    val zeroAmountEntries: List<DebtDomain>
) {
    private val loansById by lazy {
        (openLoans.asSequence() + settledLoans.asSequence()).associateBy { it.original.id }
    }

    fun loan(id: Int): LoanHistory? = loansById[id]

    fun matches(filter: Filter): Boolean = when (filter) {
        Filter.ALL -> true
        Filter.POSITIVE -> openLoans.any { it.original.sum.signum() > 0 }
        Filter.NEGATIVE -> openLoans.any { it.original.sum.signum() < 0 }
    }

    companion object {
        fun from(rows: List<DebtDomain>): PersonDebtHistory {
            val byOriginal = rows.groupBy { it.repaymentOfId }
            val (originals, zeroAmountEntries) = byOriginal[null].orEmpty().partition { it.sum.signum() != 0 }
            val loans = originals.map { original ->
                // Linked repayments use ISO days. IDs break ties for multiple payments on the same day.
                val repayments = byOriginal[original.id].orEmpty().sortedWith(compareBy({ it.date }, { it.id }))
                LoanHistory(original, repayments, RepaymentPolicy.remaining(original, repayments))
            }
            val (settled, open) = loans.partition { it.remaining.signum() == 0 }
            return PersonDebtHistory(open, settled, zeroAmountEntries)
        }
    }
}
