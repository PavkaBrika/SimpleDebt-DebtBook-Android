package com.breckneck.debtbook.goal.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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

data class GoalMovement(val id: Int, val amount: String, val date: String, val isDeposit: Boolean)

sealed interface GoalDetailsEffect {
    data class Edit(val goal: Goal) : GoalDetailsEffect
    data class BalanceChanged(val goal: Goal) : GoalDetailsEffect
    data object Deleted : GoalDetailsEffect
}

@HiltViewModel
class GoalDetailsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val setGoalDeposit: SetGoalDeposit,
    private val getGoalDepositsByGoalId: GetGoalDepositsByGoalId,
    private val deleteGoal: DeleteGoal,
) : ViewModel(), ContainerHost<GoalDetailsState, GoalDetailsEffect> {

    private var currentGoal: Goal = checkNotNull(savedState["goal"])
    private var deposits: List<GoalDeposit> = emptyList()
    private var historyLoad = 0

    override val container = container<GoalDetailsState, GoalDetailsEffect>(
        initialState = GoalDetailsState(goal = currentGoal.toUi()),
        onCreate = { retryHistory() },
    )

    fun retryHistory() = intent { loadHistory() }

    fun onEdited(goal: Goal) = intent {
        if (currentGoal.id == goal.id) {
            currentGoal = goal
            savedState["goal"] = goal
            reduce { state.copy(goal = goal.toUi(), history = formatHistory(goal.currency)) }
        }
    }

    fun edit() = intent {
        if (currentGoal.savedSum < currentGoal.sum) postSideEffect(GoalDetailsEffect.Edit(currentGoal))
    }

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
        savedState["goal"] = updated
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
            val goalId = currentGoal.id
            val rows = withContext(Dispatchers.IO) { getGoalDepositsByGoalId.execute(goalId) }
            if (request != historyLoad) return
            deposits = rows.sortedWith(compareByDescending<GoalDeposit> { it.date }.thenByDescending { it.id })
            reduce { state.copy(history = formatHistory(currentGoal.currency), isLoading = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (request == historyLoad) reduce { state.copy(isLoading = false, loadFailed = true) }
        }
    }

    private fun formatHistory(currency: String): List<GoalMovement> = deposits.map {
        GoalMovement(it.id, it.sum.formatGoalMoney(currency, showPlus = true), it.date.toDMYFormat(), it.sum.signum() > 0)
    }
}
