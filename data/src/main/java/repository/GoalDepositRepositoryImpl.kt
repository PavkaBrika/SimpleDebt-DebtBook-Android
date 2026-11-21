package repository


import com.breckneck.deptbook.data.storage.GoalDepositStorage
import com.breckneck.deptbook.domain.model.GoalDeposit
import com.breckneck.deptbook.domain.repository.GoalDepositRepository
import entity.GoalDepositData
import entity.toEntity

class GoalDepositRepositoryImpl(private val goalDepositStorage: GoalDepositStorage) :
    GoalDepositRepository {

    override fun setGoalDeposit(goalDeposit: GoalDeposit) {
        goalDepositStorage.setGoalDepositData(goalDeposit.toEntity())
    }

    override fun getGoalDepositsByGoalId(goalId: Int): List<GoalDeposit> {
        return goalDepositStorage.getGoalDepositsDataByGoalId(goalId = goalId)
            .map(GoalDepositData::toDomain)
    }

    override fun deleteGoalDepositsByGoalId(goalId: Int) {
        goalDepositStorage.deleteGoalDepositsDataByGoalId(goalId = goalId)
    }

    override fun replaceAllGoalDeposits(goalDepositList: List<GoalDeposit>) {
        goalDepositStorage.replaceAllGoalDepositData(goalDepositList.map(GoalDeposit::toEntity))
    }

    override fun getAllGoalDeposits(): List<GoalDeposit> {
        return goalDepositStorage.getAllGoalDepositsData().map(GoalDepositData::toDomain)
    }
}
