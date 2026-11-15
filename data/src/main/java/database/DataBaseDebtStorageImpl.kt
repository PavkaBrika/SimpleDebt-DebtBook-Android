package database

import android.content.Context
import androidx.room.Room
import com.breckneck.deptbook.data.storage.DebtStorage
import entity.Debt
import util.DATA_BASE_NAME

class DataBaseDebtStorageImpl(context: Context): DebtStorage {

    private val db = AppDataBase(context = context)

    override fun getAllDebtsById(id: Int): List<Debt> {
        return db.appDao().getAllDebtsById(id = id)
    }

    override fun getAllDebts(): List<Debt> {
        return db.appDao().getAllDebts()
    }

    override fun replaceAllDebts(debtList: List<Debt>) {
        db.appDao().deleteAllDebts()
        db.appDao().insertAllDebts(debtList = debtList)
    }

    override fun setDebt(debt: Debt) {
        db.appDao().saveDebt(debt)
    }

    override fun deleteDebt(debt: Debt) {
        db.appDao().removeDebt(debt)
    }

    override fun editDebt(debt: Debt) {
        db.appDao().reviseDebt(debt)
    }

    override fun deleteDebtsByHumanId(id: Int) {
        db.appDao().deleteDebtsByHumanId(id = id)
    }

    override fun getDebtQuantity(): Int {
        return db.appDao().getDebtQuantity()
    }
}