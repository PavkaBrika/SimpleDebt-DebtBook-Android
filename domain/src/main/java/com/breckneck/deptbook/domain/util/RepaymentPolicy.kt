package com.breckneck.deptbook.domain.util

import com.breckneck.deptbook.domain.model.DebtDomain
import java.math.BigDecimal

/** A repayment belongs to one original operation, never to an inferred net balance. */
object RepaymentPolicy {
    fun remaining(original: DebtDomain, history: List<DebtDomain>): BigDecimal =
        original.sum.abs() - history.filter { it.repaymentOfId == original.id && !it.cancelled }
            .fold(BigDecimal.ZERO) { total, row -> total + row.sum.abs() }

    fun validate(history: List<DebtDomain>) {
        val byId = history.associateBy { it.id }
        require(byId.size == history.size) { "Duplicate debt IDs" }
        val keys = history.mapNotNull { it.operationId }
        require(keys.distinct().size == keys.size) { "Duplicate repayment request" }
        val repaid = mutableMapOf<Int, BigDecimal>()
        history.forEach { row ->
            if (row.repaymentOfId == null) {
                require(row.operationId == null && !row.cancelled) { "Invalid original debt" }
            } else {
                require(!row.recordedAsLoan) { "A repayment cannot be an original loan" }
                val original = requireNotNull(byId[row.repaymentOfId]) { "Missing original debt" }
                require(original.repaymentOfId == null && original.id != row.id && original.idHuman == row.idHuman) { "Invalid repayment link" }
                require(original.sum.signum() != 0 && row.sum.signum() == -original.sum.signum()) { "Invalid repayment direction" }
                require(row.sum.abs() <= original.sum.abs()) { "Repayment exceeds original debt" }
                require(row.operationId?.let { Regex("[A-Za-z0-9-]{16,64}").matches(it) } == true) { "Missing repayment request ID" }
                require(LedgerDate.canonical(row.date) == row.date) { "Invalid repayment day" }
                if (!row.cancelled) repaid[original.id] = (repaid[original.id] ?: BigDecimal.ZERO) + row.sum.abs()
            }
        }
        history.filter { it.repaymentOfId == null }.forEach {
            require((repaid[it.id] ?: BigDecimal.ZERO) <= it.sum.abs()) { "Repayment exceeds remaining debt" }
        }
    }
}
