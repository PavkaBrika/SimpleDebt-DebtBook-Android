package com.breckneck.deptbook.data.storage

import com.breckneck.deptbook.domain.model.DebtDueChange
import entity.Debt

interface DebtStorage {

    fun getAllDebtsById(id: Int) : List<Debt>

    fun getAllDebts(): List<Debt>

    fun replaceAllDebts(debtList: List<Debt>)

    fun setDebt(debt: Debt)

    fun deleteDebt(debt: Debt)

    fun restoreRepayment(id: Int, humanId: Int): Boolean

    fun editDebt(debt: Debt, due: DebtDueChange?)

    fun deleteDebtsByHumanId(id: Int)

    fun getDebtQuantity(): Int
}
