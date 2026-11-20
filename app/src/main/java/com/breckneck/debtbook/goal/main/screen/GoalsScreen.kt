package com.breckneck.debtbook.goal.main.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.EmptyListPlaceholder
import com.breckneck.debtbook.core.ui.components.ShimmerListPlaceholder
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.main.GoalsAction
import com.breckneck.debtbook.goal.main.GoalsState
import com.breckneck.debtbook.goal.main.GoalsViewModel
import com.breckneck.debtbook.goal.ui.DeleteGoalSheet
import com.breckneck.debtbook.goal.ui.GoalButton
import com.breckneck.deptbook.domain.util.ListState
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun GoalsScreen(vm: GoalsViewModel) {
    val state by vm.collectAsState()
    GoalsContent(state, vm::onAction)
}

@Composable
private fun GoalsContent(state: GoalsState, onAction: (GoalsAction) -> Unit) {
    val colors = bubbleColors()
    val listScroll = rememberLazyListState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentColor = colors.ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            GoalButton(
                onClick = { onAction(GoalsAction.AddGoalClick) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text(stringResource(R.string.goal_new), Modifier.padding(start = 8.dp))
            }
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            state = listScroll,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.goals),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (state.loadFailed) {
                item {
                    Column {
                        Text(stringResource(R.string.something_went_wrong), color = colors.danger)
                        TextButton(onClick = { onAction(GoalsAction.Retry) }) { Text(stringResource(R.string.form_retry)) }
                    }
                }
            }
            when (state.listState) {
                ListState.LOADING -> if (!state.loadFailed) item { ShimmerListPlaceholder(rowCount = 2, rowHeight = 156.dp) }
                ListState.EMPTY -> if (!state.loadFailed) item {
                    EmptyListPlaceholder(stringResource(R.string.goal_empty_list), Icons.Outlined.Savings)
                }
                ListState.RECEIVED -> items(state.goalList, key = { it.goalId }) { goal ->
                    GoalItem(
                        goal,
                        onOpen = { onAction(GoalsAction.GoalClick(goal.goalId)) },
                        onAdd = { onAction(GoalsAction.ShowAddDepositPopup(goal.goalId)) },
                        onEdit = { onAction(GoalsAction.EditGoalClick(goal.goalId)) },
                        onDelete = { onAction(GoalsAction.ConfirmDelete(goal.goalId)) },
                    )
                }
            }
        }
    }

    state.adjustment?.let { adjustment ->
        state.goalList.firstOrNull { it.goalId == adjustment.goalId }?.let { goal ->
            GoalAdjustmentSheet(
                goal = goal,
                adjustment = adjustment,
                onSumChange = { onAction(GoalsAction.UpdateDepositSum(it)) },
                onConfirm = { onAction(GoalsAction.AddGoalDeposit) },
                onDismiss = { onAction(GoalsAction.DismissAddDepositPopup) },
            )
        }
    }
    state.deletion?.let { deletion ->
        state.goalList.firstOrNull { it.goalId == deletion.goalId }?.let { goal ->
            DeleteGoalSheet(
                name = goal.name,
                isDeleting = deletion.isDeleting,
                failed = deletion.failed,
                onConfirm = { onAction(GoalsAction.DeleteGoal) },
                onDismiss = { onAction(GoalsAction.DismissDelete) },
            )
        }
    }
}
