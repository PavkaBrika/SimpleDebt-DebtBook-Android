package com.breckneck.debtbook.goal.main

import com.breckneck.deptbook.domain.model.Goal

sealed interface GoalsAction {
    data class ShowAddDepositPopup(val goalId: Int) : GoalsAction
    data object DismissAddDepositPopup : GoalsAction
    data class UpdateDepositSum(val text: String) : GoalsAction
    data object AddGoalDeposit : GoalsAction
    data class ConfirmDelete(val goalId: Int) : GoalsAction
    data object DismissDelete : GoalsAction
    data object DeleteGoal : GoalsAction
    data object Retry : GoalsAction
    data class RefreshAfterNavigation(val updatedGoal: Goal?) : GoalsAction
}
