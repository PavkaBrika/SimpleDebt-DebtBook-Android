package repository

import java.math.BigDecimal

import com.breckneck.deptbook.data.storage.HumanStorage
import entity.Human
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.repository.HumanRepository

class HumanRepositoryImpl(private val humanStorage: HumanStorage) : HumanRepository {

    private fun Human.toDomain() = HumanDomain(id, name, sumDebt, currency, openingAdjustment)

    override fun getHuman(id: Int) = humanStorage.getHuman(id)?.toDomain()

    override fun getAllHumans() = humanStorage.getAllHumans().map { it.toDomain() }

    override fun replaceAllHumans(humanList: List<HumanDomain>) {
        val humanDataList: List<Human> = humanList.map {
            Human(id = it.id, name = it.name, sumDebt = it.sumDebt, currency = it.currency, openingAdjustment = it.openingAdjustment)
        }
        humanStorage.replaceAllHumans(humanList = humanDataList)
    }

    override fun getPositiveHumans(): List<HumanDomain> {
        val humanList: List<Human> = humanStorage.getPositiveHumans()
        val humanDomainList: List<HumanDomain> = humanList.map {
            HumanDomain(id = it.id, name = it.name, sumDebt = it.sumDebt, currency = it.currency, openingAdjustment = it.openingAdjustment)
        }
        return humanDomainList
    }

    override fun getNegativeHumans(): List<HumanDomain> {
        val humanList: List<Human> = humanStorage.getNegativeHumans()
        val humanDomainList: List<HumanDomain> = humanList.map {
            HumanDomain(id = it.id, name = it.name, sumDebt = it.sumDebt, currency = it.currency, openingAdjustment = it.openingAdjustment)
        }
        return humanDomainList
    }

    override fun insertHuman(humanDomain: HumanDomain) {
        humanStorage.insertHuman(
            Human(
                id = humanDomain.id,
                name = humanDomain.name,
                sumDebt = humanDomain.sumDebt,
                currency = humanDomain.currency, openingAdjustment = humanDomain.openingAdjustment
            )
        )
    }

    override fun getLastHumanId(): Int {
        return humanStorage.getLastHumanId()
    }

    override fun addSum(humanId: Int, sum: BigDecimal) {
        humanStorage.addSum(humanId = humanId, sum = sum)
    }

    override fun getAllDebtsSum(currency: String): List<BigDecimal> {
        return humanStorage.getAllDebtsSum(currency)
    }

    override fun getHumanSumDebt(humanId: Int): BigDecimal {
        return humanStorage.getHumanSumDebtUseCase(humanId = humanId)
    }

    override fun deleteHuman(id: Int) {
        humanStorage.deleteHumanById(id)
    }

    override fun updateHuman(human: HumanDomain) {
        humanStorage.updateHuman(
            Human(
                id = human.id,
                name = human.name,
                sumDebt = human.sumDebt,
                currency = human.currency, openingAdjustment = human.openingAdjustment
            )
        )
    }
}