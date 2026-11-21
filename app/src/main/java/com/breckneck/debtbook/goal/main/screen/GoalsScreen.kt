package com.breckneck.debtbook.goal.main.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBottomBar
import com.breckneck.debtbook.core.ui.components.EmptyListPlaceholder
import com.breckneck.debtbook.core.ui.components.ShimmerListPlaceholder
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.main.GoalsAction
import com.breckneck.debtbook.goal.main.GoalsState
import com.breckneck.debtbook.goal.main.GoalsViewModel
import com.breckneck.debtbook.goal.ui.DeleteGoalSheet
import com.breckneck.deptbook.domain.util.ListState
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun GoalsScreen(vm: GoalsViewModel, hostInsets: PaddingValues, onTabSelected: (Int) -> Unit) {
    val state by vm.collectAsState()
    GoalsContent(state, hostInsets, vm::onAction, onTabSelected)
}

@Composable
private fun GoalsContent(
    state: GoalsState,
    hostInsets: PaddingValues,
    onAction: (GoalsAction) -> Unit,
    onTabSelected: (Int) -> Unit,
) {
    val colors = bubbleColors()
    val direction = LocalLayoutDirection.current
    val start = hostInsets.calculateStartPadding(direction) + 16.dp
    val end = hostInsets.calculateEndPadding(direction) + 16.dp
    val listScroll = rememberLazyListState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentColor = colors.ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                ExtendedFloatingActionButton(
                    onClick = { onAction(GoalsAction.AddGoalClick) },
                    modifier = Modifier.align(Alignment.End).padding(start = start, end = end, top = 8.dp, bottom = 6.dp),
                    shape = CircleShape,
                    containerColor = colors.action,
                    contentColor = colors.onAction,
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Text(stringResource(R.string.goal_new), Modifier.padding(start = 12.dp))
                }
                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = .9f))),
                    ),
                ) {
                    BubbleBottomBar(
                        destination = R.id.goalsFragment,
                        onDestinationSelected = onTabSelected,
                        modifier = Modifier.padding(
                            start = hostInsets.calculateStartPadding(direction),
                            end = hostInsets.calculateEndPadding(direction),
                        ),
                    )
                    Spacer(Modifier.height(hostInsets.calculateBottomPadding()))
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listScroll,
                contentPadding = PaddingValues(
                    start = start, end = end,
                    top = hostInsets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 8.dp,
                ),
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
                    ListState.EMPTY -> if (!state.loadFailed) item(key = "empty-goals") {
                        EmptyListPlaceholder(
                            stringResource(R.string.goal_empty_list),
                            Icons.Outlined.Savings,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    ListState.RECEIVED -> items(state.goalList, key = { it.goalId }) { goal ->
                        GoalItem(
                            goal,
                            modifier = Modifier.animateItem(),
                            onOpen = { onAction(GoalsAction.GoalClick(goal.goalId)) },
                            onAdd = { onAction(GoalsAction.ShowAddDepositPopup(goal.goalId)) },
                            onEdit = { onAction(GoalsAction.EditGoalClick(goal.goalId)) },
                            onDelete = { onAction(GoalsAction.ConfirmDelete(goal.goalId)) },
                        )
                    }
                }
            }
            Spacer(
                Modifier.fillMaxWidth().height(hostInsets.calculateTopPadding() + 12.dp).background(
                    Brush.verticalGradient(
                        0f to colors.background.copy(alpha = .94f),
                        .45f to colors.background.copy(alpha = .84f),
                        1f to colors.background.copy(alpha = 0f),
                    ),
                ),
            )
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
                deletion = deletion,
                onConfirm = { onAction(GoalsAction.DeleteGoal) },
                onDismiss = { onAction(GoalsAction.DismissDelete) },
            )
        }
    }
}
