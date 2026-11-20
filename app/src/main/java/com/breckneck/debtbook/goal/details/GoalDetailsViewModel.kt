package com.breckneck.debtbook.goal.details

import java.math.BigDecimal

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.Goal
import com.breckneck.deptbook.domain.model.GoalDeposit
import com.breckneck.deptbook.domain.usecase.Goal.DeleteGoal
import com.breckneck.deptbook.domain.usecase.Goal.UpdateGoal
import com.breckneck.deptbook.domain.usecase.GoalDeposit.DeleteGoalDepositsByGoalId
import com.breckneck.deptbook.domain.usecase.GoalDeposit.GetGoalDepositsByGoalId
import com.breckneck.deptbook.domain.usecase.GoalDeposit.SetGoalDeposit
import com.breckneck.deptbook.domain.util.ChangeGoalSavedSumDialogState
import com.breckneck.deptbook.domain.util.ListState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class GoalDetailsViewModel @Inject constructor(
    private val updateGoal: UpdateGoal,
    private val setGoalDeposit: SetGoalDeposit,
    private val getGoalDepositsByGoalId: GetGoalDepositsByGoalId,
    private val deleteGoal: DeleteGoal,
    private val deleteGoalDepositsByGoalId: DeleteGoalDepositsByGoalId
): ViewModel() {

    private val TAG = "GoalDetailsFragmentVM"

    private val _goalDepositList = MutableLiveData<List<GoalDeposit>>()
    val goalDepositList: LiveData<List<GoalDeposit>>
        get() = _goalDepositList
    private val _goalDepositListState = MutableLiveData(ListState.LOADING)
    val goalDepositListState: LiveData<ListState>
        get() = _goalDepositListState
    private var _isGoalDepositListNeedToUpdate = false
    val isGoalDepositListNeedToUpdate: Boolean
        get() = _isGoalDepositListNeedToUpdate
    private val _goalSavedSum = MutableLiveData<BigDecimal>()
    val goalSavedSum: LiveData<BigDecimal>
        get() = _goalSavedSum
    private val _goal = MutableLiveData<Goal>()
    val goal: LiveData<Goal>
        get() = _goal
    private var _isChangeSavedSumDialogOpened = false
    val isChangeSavedSumDialogOpened: Boolean
        get() = _isChangeSavedSumDialogOpened
    private var _changeDialogState: ChangeGoalSavedSumDialogState? = null
    val changeDialogState: ChangeGoalSavedSumDialogState?
        get() = _changeDialogState
    private var _isEditOptionsDialogOpened = false
    val isEditOptionsDialogOpened: Boolean
        get() = _isEditOptionsDialogOpened

    init {
        Log.e(TAG, "Created")
    }

    fun getGoalFromDB(id: Int) {

    }

    fun setGoal(goal: Goal) {
        _goal.value = goal
        _goalSavedSum.value = goal.savedSum
        getGoalDepositList()
    }

    fun getGoalDepositList() {
        _goalDepositListState.value = ListState.LOADING
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    getGoalDepositsByGoalId.execute(goalId = _goal.value!!.id)
                }
                if (list.isEmpty()) {
                    _goalDepositListState.value = ListState.EMPTY
                    _goalDepositList.value = emptyList()
                } else {
                    _goalDepositList.value = list
                    _goalDepositListState.value = ListState.RECEIVED
                }
                _isGoalDepositListNeedToUpdate = false
                Log.e(TAG, "Goal deposit list loaded")
            } catch (e: Exception) {
                Log.e(TAG, e.message.toString())
            }
        }
    }

    val isSaving = MutableLiveData(false)
    val operationError = MutableLiveData(false)

    fun setGoalDeposit(goalDeposit: GoalDeposit) {
        if (isSaving.value == true) return
        isSaving.value = true
        operationError.value = false
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { setGoalDeposit.execute(goalDeposit) }
                val current = requireNotNull(_goal.value)
                val updated = current.copy(savedSum = current.savedSum + goalDeposit.sum)
                _goal.value = updated
                _goalSavedSum.value = updated.savedSum
                getGoalDepositList()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                operationError.value = true
            } finally { isSaving.value = false }
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    deleteGoal.execute(goal = goal)
                    deleteGoalDepositsByGoalId.execute(goalId = goal.id)
                }
                Log.e(TAG, "Goal deleted")
            } catch (e: Exception) {
                Log.e(TAG, e.message.toString())
            }
        }
    }

    fun setGoalListState(state: ListState) {
        _goalDepositListState.value = state
    }

    fun onOpenChangeSavedSumChangeDialog(state: ChangeGoalSavedSumDialogState) {
        _isChangeSavedSumDialogOpened = true
        _changeDialogState = state
    }

    fun onCloseChangeSumChangeDialog() {
        _isChangeSavedSumDialogOpened = false
        _changeDialogState = null
    }

    fun onOpenEditOptionsDialog() {
        _isEditOptionsDialogOpened = true
    }

    fun onCloseEditOptionsDialog() {
        _isEditOptionsDialogOpened = false
    }

    override fun onCleared() {
        super.onCleared()
        Log.e(TAG, "Cleared")
    }
}