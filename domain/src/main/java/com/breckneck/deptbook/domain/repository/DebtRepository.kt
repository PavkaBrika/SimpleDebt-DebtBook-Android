package com.breckneck.deptbook.domain.repository

import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.model.DebtDueChange

interface DebtRepository {

    fun getAllDebts(): List<DebtDomain>

    fun getAllDebtsById(id: Int): List<DebtDomain>

    fun replaceAllDebts(debtList: List<DebtDomain>)

    fun setDebt(debtDomain: DebtDomain)

    fun deleteDebt(debtDomain: DebtDomain)

    fun restoreRepayment(id: Int, humanId: Int): Boolean

    fun editDebt(debtDomain: DebtDomain, due: DebtDueChange? = null)

    fun deleteDebtsByHumanId(id: Int)

    fun getDebtQuantity(): Int
}
