package com.breckneck.debtbook.goal.main

import androidx.lifecycle.ViewModel
import com.breckneck.debtbook.goal.main.mapper.toUi
import com.breckneck.deptbook.domain.model.Goal
import com.breckneck.deptbook.domain.model.GoalDeposit
import com.breckneck.deptbook.domain.usecase.Goal.DeleteGoal
import com.breckneck.deptbook.domain.usecase.Goal.GetAllGoals
import com.breckneck.deptbook.domain.usecase.GoalDeposit.SetGoalDeposit
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.ListState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.container
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val getAllGoals: GetAllGoals,
    private val deleteGoal: DeleteGoal,
    private val setGoalDeposit: SetGoalDeposit,
) : ViewModel(), ContainerHost<GoalsState, GoalsSideEffect> {

    private var domainGoals: List<Goal> = emptyList()

    override val container = container<GoalsState, GoalsSideEffect>(
        initialState = GoalsState(),
        onCreate = { loadGoals() },
    )

    fun onAction(action: GoalsAction) = when (action) {
        is GoalsAction.ShowAddDepositPopup -> intent {
            reduce { state.copy(adjustment = GoalAdjustment(action.goalId)) }
        }
        GoalsAction.DismissAddDepositPopup -> intent {
            when (state.adjustment?.phase) {
                GoalSheetPhase.SAVED -> reduce { state.copy(goalList = domainGoals.map { it.toUi() }, adjustment = null) }
                GoalSheetPhase.READY -> reduce { state.copy(adjustment = null) }
                else -> Unit
            }
        }
        is GoalsAction.UpdateDepositSum -> intent {
            val adjustment = state.adjustment ?: return@intent
            val goal = findGoal(adjustment.goalId) ?: return@intent
            if (adjustment.phase == GoalSheetPhase.READY && DecimalInput.isEditable(action.text, goal.currency)) {
                reduce { state.copy(adjustment = adjustment.copy(sumText = action.text, error = null)) }
            }
        }
        GoalsAction.AddGoalDeposit -> addGoalDeposit()
        is GoalsAction.ConfirmDelete -> intent { reduce { state.copy(deletion = GoalDeletion(action.goalId)) } }
        GoalsAction.DismissDelete -> finishDeleteSheet()
        GoalsAction.DeleteGoal -> deleteGoal()
        GoalsAction.Retry -> loadGoals()
        is GoalsAction.RefreshAfterNavigation -> intent {
            val goal = action.updatedGoal
            if (goal != null && findGoal(goal.id) != null) {
                domainGoals = domainGoals.map { if (it.id == goal.id) goal else it }
                reduce { state.copy(goalList = domainGoals.map { it.toUi() }) }
            } else {
                readGoals()
            }
        }
    }

    private fun findGoal(id: Int): Goal? = domainGoals.firstOrNull { it.id == id }

    private fun loadGoals() = intent { readGoals() }

    private suspend fun Syntax<GoalsState, GoalsSideEffect>.readGoals() {
        reduce {
            state.copy(
                loadFailed = false,
                listState = if (state.goalList.isEmpty()) ListState.LOADING else state.listState,
            )
        }
        try {
            val goals = withContext(Dispatchers.IO) { getAllGoals.execute() }
            domainGoals = goals
            reduce {
                state.copy(
                    goalList = goals.map { it.toUi() },
                    listState = if (goals.isEmpty()) ListState.EMPTY else ListState.RECEIVED,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reduce { state.copy(loadFailed = true) }
        }
    }

    private fun addGoalDeposit() = intent {
        val adjustment = state.adjustment?.takeIf { it.phase == GoalSheetPhase.READY } ?: return@intent
        val goal = findGoal(adjustment.goalId) ?: return@intent
        val amount = DecimalInput.parse(adjustment.sumText, goal.currency)
        if (amount == null || amount.signum() <= 0) {
            reduce { state.copy(adjustment = adjustment.copy(error = GoalAdjustment.Error.INVALID_AMOUNT)) }
            return@intent
        }
        reduce { state.copy(adjustment = adjustment.copy(phase = GoalSheetPhase.SAVING, error = null)) }
        try {
            withContext(Dispatchers.IO) {
                setGoalDeposit.execute(GoalDeposit(sum = amount, date = Date(), goalId = goal.id))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reduce { state.copy(adjustment = adjustment.copy(error = GoalAdjustment.Error.SAVE_FAILED)) }
            return@intent
        }
        val updated = goal.copy(savedSum = goal.savedSum + amount)
        if (goal.savedSum < goal.sum && updated.savedSum >= goal.sum) postSideEffect(GoalsSideEffect.GoalReached)
        domainGoals = domainGoals.map { if (it.id == updated.id) updated else it }
        reduce { state.copy(adjustment = adjustment.copy(phase = GoalSheetPhase.SAVED, error = null)) }
    }

    private fun deleteGoal() = intent {
        val deletion = state.deletion?.takeIf { it.phase == GoalSheetPhase.READY } ?: return@intent
        val goal = findGoal(deletion.goalId) ?: return@intent
        reduce { state.copy(deletion = deletion.copy(phase = GoalSheetPhase.SAVING, failed = false)) }
        try {
            withContext(Dispatchers.IO) { deleteGoal.execute(goal) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reduce { state.copy(deletion = deletion.copy(failed = true)) }
            return@intent
        }
        reduce { state.copy(deletion = deletion.copy(phase = GoalSheetPhase.SAVED, failed = false)) }
    }

    private fun finishDeleteSheet() = intent {
        val deletion = state.deletion ?: return@intent
        when (deletion.phase) {
            GoalSheetPhase.SAVING -> return@intent
            GoalSheetPhase.READY -> {
                reduce { state.copy(deletion = null) }
                return@intent
            }
            GoalSheetPhase.SAVED -> domainGoals = domainGoals.filterNot { it.id == deletion.goalId }
        }
        reduce {
            state.copy(
                goalList = domainGoals.map { it.toUi() },
                listState = if (domainGoals.isEmpty()) ListState.EMPTY else ListState.RECEIVED,
                deletion = null,
            )
        }
    }
}
