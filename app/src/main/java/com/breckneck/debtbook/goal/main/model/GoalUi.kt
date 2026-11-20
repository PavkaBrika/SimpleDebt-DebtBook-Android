package com.breckneck.debtbook.goal.main.model

data class GoalUi(
    val goalId: Int,
    val name: String,
    val photoPath: String?,
    val savedAmount: String,
    val totalAmount: String,
    val currency: String,
    val isReached: Boolean,
    val remainingAmount: String,
    val deadline: String?,
    val creationDate: String,
    val isOverdue: Boolean,
    val percent: Int,
    val hasSavings: Boolean,
)
