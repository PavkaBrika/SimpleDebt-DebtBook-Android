package repository


import android.content.Context
import com.breckneck.deptbook.domain.model.AppDataLists
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.model.Goal
import com.breckneck.deptbook.domain.model.GoalDeposit
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.repository.AppDataRepository
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import database.AppDataBase
import entity.Debt
import entity.toEntity
import entity.FinanceCategoryData
import entity.FinanceData
import entity.GoalData
import entity.GoalDepositData
import entity.Human
import util.FinanceCategoryStateData

class AppDataRepositoryImpl(context: Context) : AppDataRepository {

    private val db = AppDataBase(context = context)

    override fun getAllAppData(): AppDataLists = db.runInTransaction<AppDataLists> {
        val dao = db.appDao()
        AppDataLists(
            humanList = dao.getAllHuman().map {
                HumanDomain(id = it.id, name = it.name, sumDebt = it.sumDebt, currency = it.currency, openingAdjustment = it.openingAdjustment)
            },
            debtList = dao.getAllDebts().map {
                it.toDomain()
            },
            financeCategoryList = dao.getAllFinanceCategories().map {
                FinanceCategory(
                    id = it.id,
                    name = it.name,
                    state = when (it.state) {
                        FinanceCategoryStateData.INCOME -> FinanceCategoryState.INCOME
                        FinanceCategoryStateData.EXPENSE -> FinanceCategoryState.EXPENSE
                    },
                    color = it.color,
                    image = it.image
                )
            },
            financeList = dao.getAllFinances().map {
                Finance(id = it.id, sum = it.sum, date = it.date, info = it.info, financeCategoryId = it.financeCategoryId, currency = it.currency, calendarDay = it.calendarDay)
            },
            goalList = dao.getAllGoals().map {
                Goal(id = it.id, name = it.name, sum = it.sum, savedSum = it.savedSum, currency = it.currency, photoPath = it.photoPath, creationDate = it.creationDate, goalDate = it.goalDate, openingAdjustment = it.openingAdjustment, goalDay = it.goalDay)
            },
            goalDepositList = dao.getAllGoalDeposits().map {
                GoalDeposit(id = it.id, sum = it.sum, date = it.date, goalId = it.goalId)
            }
        )
    }

    override fun replaceAllAppData(appDataLists: AppDataLists) {
        db.appDao().replaceAllAppData(
            humans = appDataLists.humanList.map {
                Human(id = it.id, name = it.name, sumDebt = it.sumDebt, currency = it.currency, openingAdjustment = it.openingAdjustment)
            },
            debts = appDataLists.debtList.map { debt ->
                com.breckneck.deptbook.domain.util.DebtReminderPolicy.validate(debt)
                val expired = debt.reminderMinute?.let { minute ->
                    com.breckneck.deptbook.domain.util.DebtReminderPolicy.trigger(requireNotNull(debt.dueDay), minute) <= System.currentTimeMillis()
                } ?: false
                val closed = debt.repaymentOfId == null && com.breckneck.deptbook.domain.util.RepaymentPolicy.remaining(debt, appDataLists.debtList).signum() <= 0
                (if (expired || closed) debt.copy(reminderMinute = null, reminderToken = null, reminderDelivered = false) else debt).toEntity()
            },
            financeCategories = appDataLists.financeCategoryList.map {
                FinanceCategoryData(
                    id = it.id,
                    name = it.name,
                    state = when (it.state) {
                        FinanceCategoryState.INCOME -> FinanceCategoryStateData.INCOME
                        FinanceCategoryState.EXPENSE -> FinanceCategoryStateData.EXPENSE
                    },
                    color = it.color,
                    image = it.image
                )
            },
            finances = appDataLists.financeList.map {
                FinanceData(id = it.id, sum = it.sum, date = it.date, info = it.info, financeCategoryId = it.financeCategoryId, currency = it.currency, calendarDay = it.calendarDay)
            },
            goals = appDataLists.goalList.map {
                GoalData(id = it.id, name = it.name, sum = it.sum, savedSum = it.savedSum, currency = it.currency, photoPath = it.photoPath, creationDate = it.creationDate, goalDate = it.goalDate, openingAdjustment = it.openingAdjustment, goalDay = it.goalDay)
            },
            goalDeposits = appDataLists.goalDepositList.map {
                GoalDepositData(id = it.id, sum = it.sum, date = it.date, goalId = it.goalId)
            }
        )
        database.ReminderRestoreHook.afterRestore?.invoke()
    }
}
