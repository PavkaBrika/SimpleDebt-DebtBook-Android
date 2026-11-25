package com.breckneck.debtbook.goal.main.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.navigation.MainTab
import com.breckneck.debtbook.core.ui.components.BubbleScreenTabs
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleRootTitle
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.ShimmerListPlaceholder
import com.breckneck.debtbook.core.ui.components.bubbleRootTitleTopPadding
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.main.GoalsAction
import com.breckneck.debtbook.goal.main.GoalsViewModel
import com.breckneck.debtbook.goal.ui.DeleteGoalSheet
import com.breckneck.deptbook.domain.util.ListState
import org.orbitmvi.orbit.compose.collectAsState

@Composable
internal fun GoalsScreen(
    vm: GoalsViewModel,
    hostInsets: PaddingValues,
    onAdd: () -> Unit,
    onOpen: (Int) -> Unit,
    onEdit: (Int) -> Unit,
) {
    val state by vm.collectAsState()
    val onAction = vm::onAction
    val colors = bubbleColors()
    val listScroll = rememberLazyListState()
    BubbleScaffold(
        hostInsets = hostInsets,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bubbleRootTitleTopPadding, bottom = 8.dp),
        bottomAction = {
            BubbleButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().testTag("addGoalButton"),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text(stringResource(R.string.goal_new), Modifier.padding(start = 8.dp))
            }
        },
        tabs = { BubbleScreenTabs(MainTab.GOALS) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listScroll,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { BubbleRootTitle(stringResource(R.string.goals)) }
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
                    Text(stringResource(R.string.goal_empty_list), Modifier.fillMaxWidth().animateItem().padding(22.dp),
                        color = colors.muted, textAlign = TextAlign.Center)
                }
                ListState.RECEIVED -> items(state.goalList, key = { it.goalId }) { goal ->
                    GoalItem(
                        goal,
                        modifier = Modifier.animateItem(),
                        onOpen = { onOpen(goal.goalId) },
                        onAdd = { onAction(GoalsAction.ShowAddDepositPopup(goal.goalId)) },
                        onEdit = { onEdit(goal.goalId) },
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
                deletion = deletion,
                onConfirm = { onAction(GoalsAction.DeleteGoal) },
                onDismiss = { onAction(GoalsAction.DismissDelete) },
            )
        }
    }
}
