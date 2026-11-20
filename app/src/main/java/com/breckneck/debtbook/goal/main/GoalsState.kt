package com.breckneck.debtbook.goal.main

import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.deptbook.domain.util.ListState

data class GoalsState(
    val goalList: List<GoalUi> = emptyList(),
    val listState: ListState = ListState.LOADING,
    val loadFailed: Boolean = false,
    val adjustment: GoalAdjustment? = null,
    val deletion: GoalDeletion? = null,
)

data class GoalAdjustment(
    val goalId: Int,
    val sumText: String = "",
    val error: Error? = null,
    val isSaving: Boolean = false,
) {
    enum class Error { INVALID_AMOUNT, EXCEEDS_BALANCE, SAVE_FAILED }
}

data class GoalDeletion(
    val goalId: Int,
    val failed: Boolean = false,
    val isDeleting: Boolean = false,
)
