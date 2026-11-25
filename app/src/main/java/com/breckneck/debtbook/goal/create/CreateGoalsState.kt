package com.breckneck.debtbook.goal.create


import com.breckneck.debtbook.goal.create.model.CreateGoalUi
import com.breckneck.debtbook.goal.create.model.NameError
import com.breckneck.debtbook.goal.create.model.SavedSumError
import com.breckneck.debtbook.goal.create.model.SumError

data class CreateGoalsState(
    val goal: CreateGoalUi,
    val nameError: NameError?,
    val sumError: SumError?,
    val savedSumError: SavedSumError?,
    val isDatePickerVisible: Boolean,
    val isEditMode: Boolean,
    val isInitialized: Boolean = false,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val photoFailed: Boolean = false,
    val dateInvalid: Boolean = false,
) {
    companion object {
        fun initial() = CreateGoalsState(
            goal = CreateGoalUi(),
            nameError = null,
            sumError = null,
            savedSumError = null,
            isDatePickerVisible = false,
            isEditMode = false,
        )
    }
}
