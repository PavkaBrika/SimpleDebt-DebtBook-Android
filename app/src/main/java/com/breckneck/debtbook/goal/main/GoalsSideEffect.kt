package com.breckneck.debtbook.goal.main

sealed interface GoalsSideEffect {
    data object GoalReached : GoalsSideEffect
}
