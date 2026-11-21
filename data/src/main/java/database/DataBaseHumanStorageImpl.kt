package database

import android.content.Context
import com.breckneck.deptbook.data.storage.HumanStorage
import entity.Human
import java.math.BigDecimal

class DataBaseHumanStorageImpl(context: Context) : HumanStorage {
    private val db = AppDataBase(context)
    override fun getHuman(id: Int) = db.appDao().getHuman(id)

    override fun getAllHumans(): List<Human> {
        val humanList = db.appDao().getAllHuman()
        return humanList
    }

    override fun replaceAllHumans(humanList: List<Human>) {
        db.appDao().deleteAllHumans()
        db.appDao().insertAllHumans(humanList = humanList)
    }

    override fun getPositiveHumans(): List<Human> {
        val humanList = db.appDao().getPositiveHumans()
        return humanList
    }

    override fun getNegativeHumans(): List<Human> {
        val humanList = db.appDao().getNegativeHumans()
        return humanList
    }

    override fun insertHuman(human: Human) {
        db.appDao().insertHuman(human)
    }

    override fun getLastHumanId(): Int {
        return db.appDao().getLastHumanId()
    }

    override fun addSum(humanId: Int, sum: BigDecimal) {
        db.appDao().addSum(humanId = humanId, sum = sum)
    }

    override fun getAllDebtsSum(currency: String): List<BigDecimal> {
        return db.appDao().getAllDebtsSum(currency)
    }

    override fun getHumanSumDebtUseCase(humanId: Int): BigDecimal {
        return db.appDao().getHumanSumDebt(humanId = humanId)
    }

    override fun deleteHumanById(id: Int) {
        db.appDao().deleteHumanById(id = id)
    }

    override fun updateHuman(human: Human) {
        db.appDao().updateHuman(human = human)
    }
}
