package com.breckneck.debtbook.goal.main

import com.breckneck.deptbook.domain.usecase.Goal.DeleteGoal
import com.breckneck.deptbook.domain.usecase.Goal.GetAllGoals
import com.breckneck.deptbook.domain.usecase.GoalDeposit.SetGoalDeposit
import com.breckneck.deptbook.domain.util.ListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadFailureCanBeRetried() = runBlocking {
        val read = mock<GetAllGoals>()
        whenever(read.execute()).thenThrow(IllegalStateException("Read failed")).thenReturn(emptyList())
        val vm = GoalsViewModel(read, mock<DeleteGoal>(), mock<SetGoalDeposit>())
        val states = vm.container.stateFlow
        assertEquals(ListState.LOADING, states.value.listState)
        withTimeout(5000) { states.first { it.loadFailed } }
        vm.onAction(GoalsAction.Retry)
        withTimeout(5000) { states.first { !it.loadFailed && it.listState == ListState.EMPTY } }
        Unit
    }
}
