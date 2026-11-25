package com.breckneck.debtbook.goal.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.breckneck.debtbook.core.navigation.GoalDetails
import com.breckneck.deptbook.domain.usecase.Goal.GetAllGoals
import com.breckneck.debtbook.common.toDMYFormat
import com.breckneck.debtbook.goal.main.GoalAdjustment
import com.breckneck.debtbook.goal.main.GoalDeletion
import com.breckneck.debtbook.goal.main.GoalSheetPhase
import com.breckneck.debtbook.goal.main.mapper.formatGoalMoney
import com.breckneck.debtbook.goal.main.mapper.toUi
import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.deptbook.domain.model.Goal
import com.breckneck.deptbook.domain.model.GoalDeposit
import com.breckneck.deptbook.domain.usecase.Goal.DeleteGoal
import com.breckneck.deptbook.domain.usecase.GoalDeposit.GetGoalDepositsByGoalId
import com.breckneck.deptbook.domain.usecase.GoalDeposit.SetGoalDeposit
import com.breckneck.deptbook.domain.util.DecimalInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.container
import java.util.Date
import javax.inject.Inject

data class GoalDetailsState(
    val goal: GoalUi? = null,
    val history: List<GoalMovement> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val adjustment: GoalAdjustment? = null,
    val withdraw: Boolean = false,
    val deletion: GoalDeletion? = null,
)

enum class GoalMovementType { INITIAL_SAVINGS, DEPOSIT, WITHDRAWAL }

data class GoalMovement(val id: Int, val amount: String, val date: String, val type: GoalMovementType) {
    val isDeposit get() = type != GoalMovementType.WITHDRAWAL
}

sealed interface GoalDetailsEffect {
    data class BalanceChanged(val goal: Goal) : GoalDetailsEffect
    data object WithdrawalRejected : GoalDetailsEffect
    data object Deleted : GoalDetailsEffect
    data object Missing : GoalDetailsEffect
}

@HiltViewModel
class GoalDetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val getGoals: GetAllGoals,
    private val setGoalDeposit: SetGoalDeposit,
    private val getGoalDepositsByGoalId: GetGoalDepositsByGoalId,
    private val deleteGoal: DeleteGoal,
) : ViewModel(), ContainerHost<GoalDetailsState, GoalDetailsEffect> {

    private val goalId = savedState.toRoute<GoalDetails>().goalId
    private lateinit var currentGoal: Goal
    private var deposits: List<GoalDeposit> = emptyList()
    private var historyLoad = 0

    override val container = container<GoalDetailsState, GoalDetailsEffect>(
        initialState = GoalDetailsState(),
        onCreate = { retryHistory() },
    )

    fun retryHistory() = intent { loadHistory() }

    fun openAdjustment(withdraw: Boolean) = intent {
        if (currentGoal.savedSum < currentGoal.sum) {
            reduce { state.copy(adjustment = GoalAdjustment(currentGoal.id), withdraw = withdraw) }
        }
    }

    fun dismissAdjustment() = intent {
        val phase = state.adjustment?.phase ?: return@intent
        if (phase == GoalSheetPhase.SAVING) return@intent
        reduce { state.copy(goal = currentGoal.toUi(), adjustment = null) }
        if (phase == GoalSheetPhase.SAVED) loadHistory()
    }

    fun changeAmount(text: String) = intent {
        val adjustment = state.adjustment?.takeIf { it.phase == GoalSheetPhase.READY } ?: return@intent
        if (DecimalInput.isEditable(text, currentGoal.currency)) {
            reduce { state.copy(adjustment = adjustment.copy(sumText = text, error = null)) }
        }
    }

    fun confirmAdjustment() = intent {
        val adjustment = state.adjustment?.takeIf { it.phase == GoalSheetPhase.READY } ?: return@intent
        val goal = currentGoal
        val amount = DecimalInput.parse(adjustment.sumText, goal.currency)
        val error = when {
            amount == null || amount.signum() <= 0 -> GoalAdjustment.Error.INVALID_AMOUNT
            state.withdraw && amount > goal.savedSum -> GoalAdjustment.Error.EXCEEDS_BALANCE
            else -> null
        }
        if (error != null) {
            if (error == GoalAdjustment.Error.EXCEEDS_BALANCE) postSideEffect(GoalDetailsEffect.WithdrawalRejected)
            reduce { state.copy(adjustment = adjustment.copy(error = error)) }
            return@intent
        }

        val amountChange = checkNotNull(amount).let { if (state.withdraw) -it else it }
        reduce { state.copy(adjustment = adjustment.copy(phase = GoalSheetPhase.SAVING, error = null)) }
        try {
            withContext(Dispatchers.IO) {
                setGoalDeposit.execute(GoalDeposit(sum = amountChange, date = Date(), goalId = goal.id))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reduce { state.copy(adjustment = adjustment.copy(error = GoalAdjustment.Error.SAVE_FAILED)) }
            return@intent
        }
        val updated = goal.copy(savedSum = goal.savedSum + amountChange)
        currentGoal = updated
        reduce { state.copy(adjustment = adjustment.copy(phase = GoalSheetPhase.SAVED, error = null)) }
        postSideEffect(GoalDetailsEffect.BalanceChanged(updated))
    }

    fun openDelete() = intent { reduce { state.copy(deletion = GoalDeletion(currentGoal.id)) } }

    fun dismissDelete() = intent {
        val phase = state.deletion?.phase ?: return@intent
        if (phase == GoalSheetPhase.SAVING) return@intent
        reduce { state.copy(deletion = null) }
        if (phase == GoalSheetPhase.SAVED) postSideEffect(GoalDetailsEffect.Deleted)
    }

    fun confirmDelete() = intent {
        val deletion = state.deletion?.takeIf { it.phase == GoalSheetPhase.READY } ?: return@intent
        reduce { state.copy(deletion = deletion.copy(phase = GoalSheetPhase.SAVING, failed = false)) }
        try {
            withContext(Dispatchers.IO) { deleteGoal.execute(currentGoal) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reduce { state.copy(deletion = deletion.copy(failed = true)) }
            return@intent
        }
        reduce { state.copy(deletion = deletion.copy(phase = GoalSheetPhase.SAVED, failed = false)) }
    }

    private suspend fun Syntax<GoalDetailsState, GoalDetailsEffect>.loadHistory() {
        val request = ++historyLoad
        reduce { state.copy(isLoading = true, loadFailed = false) }
        try {
            val records = withContext(Dispatchers.IO) {
                val goal = getGoals.execute().firstOrNull { it.id == goalId }
                goal?.let { it to getGoalDepositsByGoalId.execute(goalId) }
            }
            if (request != historyLoad) return
            if (records == null) {
                postSideEffect(GoalDetailsEffect.Missing)
                return
            }
            val (goal, rows) = records
            currentGoal = goal
            deposits = rows.sortedWith(compareByDescending<GoalDeposit> { it.date }.thenByDescending { it.id })
            reduce { state.copy(goal = goal.toUi(), history = formatHistory(goal.currency), isLoading = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (request == historyLoad) reduce { state.copy(isLoading = false, loadFailed = true) }
        }
    }

    private fun formatHistory(currency: String): List<GoalMovement> = deposits.map {
        val type = when {
            it.isInitial -> GoalMovementType.INITIAL_SAVINGS
            it.sum.signum() > 0 -> GoalMovementType.DEPOSIT
            else -> GoalMovementType.WITHDRAWAL
        }
        GoalMovement(it.id, it.sum.formatGoalMoney(currency, showPlus = true), it.date.toDMYFormat(), type)
    }
}
