package com.breckneck.deptbook.domain.usecase.Debt

import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.repository.DebtRepository
import com.breckneck.deptbook.domain.util.RepaymentPolicy
import java.math.BigDecimal

class RepaymentExceedsRemaining(val remaining: BigDecimal) : IllegalArgumentException("Repayment exceeds remaining debt")

class RecordRepaymentUseCase(private val repository: DebtRepository, private val transaction: DataTransaction) {
    enum class Result { ALREADY_RECORDED, RECORDED, SETTLED }

    /** Returns true if restoring this payment settles its original loan. */
    fun restore(id: Int, humanId: Int): Boolean = repository.restoreRepayment(id, humanId)

    fun execute(originalId: Int, humanId: Int, amount: BigDecimal, day: String, requestId: String): Result = transaction.run {
        require(amount.signum() > 0)
        val history = repository.getAllDebtsById(humanId)
        val previous = history.firstOrNull { it.operationId == requestId }
        if (previous != null) {
            require(previous.repaymentOfId == originalId && previous.sum.abs().compareTo(amount) == 0) { "Repayment request reused" }
            Result.ALREADY_RECORDED
        } else {
            val original = requireNotNull(history.firstOrNull { it.id == originalId && it.repaymentOfId == null })
            val remaining = RepaymentPolicy.remaining(original, history)
            if (amount > remaining) throw RepaymentExceedsRemaining(remaining)
            require(original.sum.signum() != 0)
            repository.setDebt(DebtDomain(0, amount * (-original.sum.signum()).toBigDecimal(), humanId,
                null, day, repaymentOfId = original.id, operationId = requestId))
            if (amount.compareTo(remaining) == 0) Result.SETTLED else Result.RECORDED
        }
    }
}
