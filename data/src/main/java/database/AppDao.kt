package database

import java.math.BigDecimal
import com.breckneck.deptbook.domain.util.compareTo
import com.breckneck.deptbook.domain.util.RepaymentPolicy
import com.breckneck.deptbook.domain.util.MoneyPolicy
import com.breckneck.deptbook.domain.util.DebtReminderPolicy

import androidx.room.*
import entity.Debt
import entity.FinanceCategoryData
import entity.FinanceData
import entity.GoalData
import entity.GoalDepositData
import entity.Human
import entity.relations.FinanceCategoryDataWithFinanceData
import util.FinanceCategoryStateData


@Dao
interface AppDao {

    //Human
    @Query("SELECT * FROM human")
    fun getAllHuman(): List<Human>

    @Query("DELETE FROM human")
    fun deleteAllHumans()

    @Query("SELECT * FROM human WHERE CAST(sumDebt AS NUMERIC) >= 0")
    fun getPositiveHumans(): List<Human>

    @Query("SELECT * FROM human WHERE CAST(sumDebt AS NUMERIC) <= 0")
    fun getNegativeHumans(): List<Human>

    @Query("SELECT id FROM human ORDER BY id DESC LIMIT 1")
    fun getLastHumanId() : Int

    @Query("SELECT * FROM human WHERE id = :humanId")
    fun getHuman(humanId: Int): Human?

    @Transaction
    fun addSum(humanId: Int, sum: BigDecimal) {
        val human = requireNotNull(getHuman(humanId))
        human.sumDebt = getAllDebtsById(humanId).fold(human.openingAdjustment) { total, debt -> total + debt.toDomain().effectiveSum }
        updateHuman(human)
    }

    @Query("SELECT sumDebt from human WHERE currency = :currency")
    fun getAllDebtsSum(currency: String): List<BigDecimal>

    @Query("SELECT sumDebt from human WHERE id = :humanId")
    fun getHumanSumDebt(humanId: Int): BigDecimal

    @Query("DELETE FROM human WHERE id = :id")
    fun deleteHumanById(id: Int)

    @Insert
    fun insertHuman(human: Human)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllHumans(humanList: List<Human>)

    @Delete
    fun deleteHuman(human: Human)

    @Update
    fun updateHuman(human: Human)

    @Transaction
    fun saveDebt(debt: Debt) {
        DebtReminderPolicy.validate(debt.toDomain())
        val history = getAllDebts()
        if (debt.repaymentOfId != null) {
            val previous = history.firstOrNull { it.operationId == debt.operationId }
            if (previous != null) {
                require(previous.repaymentOfId == debt.repaymentOfId && previous.idHuman == debt.idHuman && previous.sum.compareTo(debt.sum) == 0) { "Repayment request reused" }
                return // Includes cancelled requests: retry must never resurrect a repayment.
            }
            require(!debt.cancelled && debt.id == 0) { "Invalid new repayment" }
            val original = requireNotNull(history.firstOrNull { it.id == debt.repaymentOfId }) { "Missing original debt" }
            val remaining = RepaymentPolicy.remaining(original.toDomain(), history.map { it.toDomain() })
            val currency = requireNotNull(getHuman(original.idHuman)).currency
            require(debt.sum.abs().compareTo(remaining) == 0 || MoneyPolicy.parse(debt.sum.abs().toPlainString(), currency) != null) { "Invalid repayment precision" }
        }
        // A synthetic unused ID allows validation before Room assigns the real primary key.
        val candidate = debt.toDomain().copy(id = if (debt.id == 0) Int.MIN_VALUE else debt.id)
        RepaymentPolicy.validate(history.map { it.toDomain() } + candidate)
        insertDebt(debt)
        debt.repaymentOfId?.let { originalId ->
            val rows = getAllDebts().map { it.toDomain() }
            val original = getAllDebts().first { it.id == originalId }
            if (RepaymentPolicy.remaining(original.toDomain(), rows).signum() == 0) {
                updateDebt(original.copy(reminderMinute = null, reminderToken = null, reminderDelivered = false))
            }
        }
        addSum(debt.idHuman, BigDecimal.ZERO)
    }

    @Transaction
    fun reviseDebt(debt: Debt) {
        val previous = getAllDebts().firstOrNull { it.id == debt.id } ?: error("Missing debt")
        require(previous.repaymentOfId == null && debt.repaymentOfId == null && debt.operationId == null && !debt.cancelled) { "Cancel a repayment instead of editing" }
        if (getAllDebts().any { it.repaymentOfId == previous.id }) {
            require(previous.idHuman == debt.idHuman && previous.sum.compareTo(debt.sum) == 0) { "Debt has repayment history" }
        }
        debt.legacyDate = previous.legacyDate
        // Ordinary amount/comment editing must preserve independently edited due settings.
        updateDebt(debt.copy(dueDay = previous.dueDay, reminderMinute = previous.reminderMinute,
            reminderToken = previous.reminderToken, reminderDelivered = previous.reminderDelivered))
        addSum(previous.idHuman, BigDecimal.ZERO)
        if (previous.idHuman != debt.idHuman) addSum(debt.idHuman, BigDecimal.ZERO)
    }

    @Transaction
    fun removeDebt(debt: Debt) {
        val previous = getAllDebts().firstOrNull { it.id == debt.id } ?: return
        if (previous.repaymentOfId != null) {
            if (previous.cancelled) return
            updateDebt(previous.copy(cancelled = true))
        } else {
            require(getAllDebts().none { it.repaymentOfId == previous.id }) { "Debt has repayment history" }
            deleteDebt(previous)
        }
        addSum(previous.idHuman, BigDecimal.ZERO)
    }

    @Transaction
    fun setDebtDueDate(id: Int, day: String?, minute: Int?, token: String?, now: Long) {
        val history = getAllDebts()
        val previous = requireNotNull(history.firstOrNull { it.id == id })
        require(previous.repaymentOfId == null && previous.sum.signum() != 0)
        val next = previous.copy(dueDay = day, reminderMinute = minute,
            reminderToken = token, reminderDelivered = false)
        DebtReminderPolicy.validate(next.toDomain())
        if (minute != null) {
            require(RepaymentPolicy.remaining(previous.toDomain(), history.map { it.toDomain() }).signum() > 0)
            require(DebtReminderPolicy.trigger(requireNotNull(day), minute) > now) { "Reminder must be in the future" }
        }
        val unchanged = previous.dueDay == day && previous.reminderMinute == minute && previous.reminderToken != null && !previous.reminderDelivered
        updateDebt(if (unchanged) next.copy(reminderToken = previous.reminderToken, reminderDelivered = previous.reminderDelivered) else next)
    }

    //Debt
    @Query("SELECT * FROM debt")
    fun getAllDebts(): List<Debt>

    @Query("DELETE FROM debt")
    fun deleteAllDebts()

    @Query("SELECT * FROM debt WHERE idHuman = :id")
    fun getAllDebtsById(id: Int): List<Debt>

    @Query("DELETE FROM debt WHERE idHuman = :id")
    fun deleteDebtsByHumanId(id: Int)

    @Query("SELECT count(*) FROM debt")
    fun getDebtQuantity(): Int

    @Insert
    fun insertDebt(debt: Debt)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllDebts(debtList: List<Debt>)

    @Delete
    fun deleteDebt(debt: Debt)

    @Update
    fun updateDebt(debt: Debt)

    //Finance
    @Query("SELECT * from financedata")
    fun getAllFinances(): List<FinanceData>

    @Query("SELECT * from financedata WHERE financeCategoryId = :categoryId ORDER BY date DESC")
    suspend fun getFinanceByCategoryId(categoryId: Int): List<FinanceData>

    @Query("DELETE FROM financeData WHERE financeCategoryId = :financeCategoryId")
    fun deleteAllFinancesByCategoryId(financeCategoryId: Int)

    @Query("DELETE FROM financedata")
    fun deleteAllFinances()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllFinances(financeDataList: List<FinanceData>)

    @Insert
    fun insertFinance(financeData: FinanceData)

    @Delete
    suspend fun deleteFinance(financeData: FinanceData)

    @Update
    fun updateFinance(financeData: FinanceData)

    //Finance category
    @Query("SELECT * FROM financecategorydata")
    fun getAllFinanceCategories(): List<FinanceCategoryData>

    @Query("SELECT * FROM financecategorydata WHERE state = :financeCategoryStateData")
    fun getFinanceCategoriesByState(financeCategoryStateData: FinanceCategoryStateData): List<FinanceCategoryData>

    @Transaction
    @Query("SELECT * FROM financecategorydata WHERE state = :financeCategoryStateData")
    fun getFinanceCategoriesWithFinancesByState(financeCategoryStateData: FinanceCategoryStateData): List<FinanceCategoryDataWithFinanceData>

    @Transaction
    @Query("SELECT * FROM financecategorydata")
    fun getAllFinanceCategoriesWithFinances(): List<FinanceCategoryDataWithFinanceData>

    @Query("DELETE FROM financecategorydata")
    fun deleteAllFinanceCategories()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllFinanceCategories(financeCategoryDataList: List<FinanceCategoryData>)

    @Insert
    fun insertFinanceCategory(financeCategoryData: FinanceCategoryData)

    @Delete
    fun deleteFinanceCategory(financeCategoryData: FinanceCategoryData)

    @Update
    fun updateFinanceCategory(financeCategoryData: FinanceCategoryData)

    //GOAL

    @Transaction
    fun updateGoalDetails(goal: GoalData) {
        val totalDeposits = getGoalDepositsByGoalId(goal.id).fold(BigDecimal.ZERO) { total, row -> total + row.sum }
        goal.openingAdjustment = goal.savedSum - totalDeposits
        updateGoal(goal)
    }

    @Query("SELECT * FROM GoalData WHERE id = :id")
    fun getGoal(id: Int): GoalData?

    @Transaction
    fun addGoalDeposit(deposit: GoalDepositData) {
        val goal = requireNotNull(getGoal(deposit.goalId))
        insertGoalDeposit(deposit)
        goal.savedSum = getGoalDepositsByGoalId(goal.id).fold(goal.openingAdjustment) { total, row -> total + row.sum }
        updateGoal(goal)
    }

    @Query("SELECT * FROM GoalData")
    fun getAllGoals(): List<GoalData>

    @Query("DELETE FROM GoalData")
    fun deleteAllGoals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllGoals(goalList: List<GoalData>)

    @Insert
    fun insertGoal(goalData: GoalData)

    @Delete
    fun deleteGoal(goalData: GoalData)

    @Update
    fun updateGoal(goalData: GoalData)

    //GOAL DEPOSIT

    @Query("SELECT * FROM GoalDepositData")
    fun getAllGoalDeposits(): List<GoalDepositData>

    @Query("SELECT * FROM GoalDepositData WHERE goalId = :goalId")
    fun getGoalDepositsByGoalId(goalId: Int): List<GoalDepositData>

    @Query("DELETE FROM GoalDepositData WHERE goalId = :goalId")
    fun deleteGoalDepositsByGoalId(goalId: Int)

    @Query("DELETE FROM GoalDepositData")
    fun deleteAllGoalDeposits()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllGoalDeposits(goalDepositDataList: List<GoalDepositData>)

    @Insert
    fun insertGoalDeposit(goalDepositData: GoalDepositData)

    @Delete
    fun deleteGoalDeposit(goalDepositData: GoalDepositData)

    @Update
    fun updateGoalDeposit(goalDepositData: GoalDepositData)

    // Atomic full-DB restore used by synchronization
    @Transaction
    fun replaceAllAppData(
        humans: List<Human>,
        debts: List<Debt>,
        financeCategories: List<FinanceCategoryData>,
        finances: List<FinanceData>,
        goals: List<GoalData>,
        goalDeposits: List<GoalDepositData>
    ) {
        RepaymentPolicy.validate(debts.map { it.toDomain() })
        // Delete children before parents to respect FK constraints
        deleteAllFinances()
        deleteAllGoalDeposits()
        deleteAllDebts()
        deleteAllHumans()
        deleteAllFinanceCategories()
        deleteAllGoals()
        // Insert parents before children
        insertAllHumans(humans)
        insertAllFinanceCategories(financeCategories)
        insertAllGoals(goals)
        insertAllDebts(debts)
        insertAllFinances(finances)
        insertAllGoalDeposits(goalDeposits)
    }
}
