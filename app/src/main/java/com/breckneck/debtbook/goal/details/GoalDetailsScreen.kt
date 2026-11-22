package com.breckneck.debtbook.goal.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleListRow
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleSectionTitle
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.components.BubbleValueRow
import com.breckneck.debtbook.core.ui.components.ShimmerListPlaceholder
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.debtbook.goal.main.screen.GoalAdjustmentSheet
import com.breckneck.debtbook.goal.main.screen.GoalBalanceCard
import com.breckneck.debtbook.goal.ui.DeleteGoalSheet
import com.breckneck.debtbook.goal.ui.GoalActionsMenu
import com.breckneck.debtbook.goal.ui.GoalDeleteButton
import org.orbitmvi.orbit.compose.collectAsState

@Composable
internal fun GoalDetailsScreen(vm: GoalDetailsViewModel, hostInsets: PaddingValues, onBack: () -> Unit) {
    val state by vm.collectAsState()
    val goal = state.goal ?: return
    GoalDetailsContent(
        goal = goal,
        state = state,
        hostInsets = hostInsets,
        onBack = onBack,
        onAdd = { vm.openAdjustment(withdraw = false) },
        onSubtract = { vm.openAdjustment(withdraw = true) },
        onEdit = { vm.edit() },
        onDelete = { vm.openDelete() },
        onRetry = { vm.retryHistory() },
    )
    state.adjustment?.let {
        GoalAdjustmentSheet(
            goal = goal,
            adjustment = it,
            withdraw = state.withdraw,
            onSumChange = { value -> vm.changeAmount(value) },
            onConfirm = { vm.confirmAdjustment() },
            onDismiss = { vm.dismissAdjustment() },
        )
    }
    state.deletion?.let { deletion ->
        DeleteGoalSheet(
            name = goal.name,
            deletion = deletion,
            onConfirm = { vm.confirmDelete() },
            onDismiss = { vm.dismissDelete() },
        )
    }
}

@Composable
private fun GoalDetailsContent(
    goal: GoalUi,
    state: GoalDetailsState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSubtract: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    hostInsets: PaddingValues = PaddingValues(0.dp),
) {
    val colors = bubbleColors()
    val secondaryColors = ButtonDefaults.buttonColors(containerColor = colors.mint, contentColor = colors.onMint)
    BubbleScaffold(
        hostInsets = hostInsets,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        topBar = {
            BubbleTopBar(
                title = goal.name,
                onBack = onBack,
                hostInsets = hostInsets,
            ) { diameter ->
                if (goal.isReached) {
                    GoalDeleteButton(goalName = goal.name, onDelete = onDelete, diameter = diameter)
                } else {
                    GoalActionsMenu(onDelete = onDelete, onEdit = onEdit, diameter = diameter)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item { GoalBalanceCard(goal) }
            if (!goal.isReached) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val configuration = LocalConfiguration.current
                        val actionWidth = if (configuration.screenWidthDp < 340 * configuration.fontScale)
                            Modifier.fillMaxWidth() else Modifier.weight(1f)
                        BubbleButton(onClick = onAdd, modifier = actionWidth, colors = secondaryColors) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.goal_top_up), Modifier.padding(start = 6.dp))
                        }
                        BubbleButton(
                            onClick = onSubtract,
                            modifier = actionWidth,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.dangerContainer, contentColor = colors.danger),
                            enabled = goal.hasSavings,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.CallMade, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.goal_withdraw), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            item {
                BubbleGroup(Modifier.padding(top = 12.dp)) {
                    BubbleValueRow(stringResource(R.string.goal_remaining), goal.remainingAmount)
                    HorizontalDivider(color = colors.border)
                    BubbleValueRow(stringResource(R.string.goal_created), goal.creationDate)
                    if (goal.isReached && goal.deadline != null) {
                        HorizontalDivider(color = colors.border)
                        BubbleValueRow(stringResource(R.string.goal_deadline), goal.deadline)
                    }
                }
            }
            item {
                BubbleSectionTitle(stringResource(R.string.history), Modifier.padding(top = 16.dp, bottom = 8.dp))
            }
            if (state.loadFailed) {
                item {
                    Text(stringResource(R.string.something_went_wrong), color = colors.danger)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.form_retry)) }
                }
            } else if (state.history.isEmpty()) {
                item {
                    if (state.isLoading) ShimmerListPlaceholder(rowCount = 2, rowHeight = 42.dp)
                    else Text(stringResource(R.string.goal_empty_history), color = colors.muted)
                }
            }
            itemsIndexed(state.history, key = { _, row -> row.id }) { index, row ->
                GoalHistoryRow(
                    row,
                    first = index == 0,
                    last = index == state.history.lastIndex,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun GoalHistoryRow(row: GoalMovement, first: Boolean, last: Boolean, modifier: Modifier = Modifier) {
    val colors = bubbleColors()
    val top = if (first) 24.dp else 0.dp
    val bottom = if (last) 24.dp else 0.dp
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.paper,
        contentColor = colors.ink,
        shape = RoundedCornerShape(top, top, bottom, bottom),
    ) {
        Column {
            BubbleListRow(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                leading = {
                    Icon(
                        if (row.isDeposit) Icons.AutoMirrored.Outlined.CallReceived else Icons.AutoMirrored.Outlined.CallMade,
                        null,
                        Modifier.size(36.dp).background(if (row.isDeposit) colors.mint else colors.dangerContainer, CircleShape).padding(10.dp),
                        tint = if (row.isDeposit) colors.onMint else colors.danger,
                    )
                },
                trailing = {
                    if (!largeText) {
                        Column(Modifier.weight(0.9f), horizontalAlignment = Alignment.End) { GoalMovementAmount(row) }
                    }
                },
            ) {
                Text(
                    stringResource(when (row.type) {
                        GoalMovementType.INITIAL_SAVINGS -> R.string.already_saved_sum
                        GoalMovementType.DEPOSIT -> R.string.goal_deposit
                        GoalMovementType.WITHDRAWAL -> R.string.goal_withdrawal
                    }),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(row.date, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                if (largeText) GoalMovementAmount(row)
            }
            if (!last) HorizontalDivider(color = colors.border)
        }
    }
}

@Composable
private fun GoalMovementAmount(row: GoalMovement) {
    Text(
        row.amount,
        color = if (row.isDeposit) bubbleColors().ink else bubbleColors().danger,
        fontWeight = FontWeight.SemiBold,
    )
}

@PreviewLightDark
@Composable
private fun GoalDetailsPreview() {
    val goal = GoalUi(
        goalId = 1,
        name = "Trip",
        photoPath = null,
        savedAmount = "36,000 ₽",
        totalAmount = "90,000 ₽",
        currency = "RUB",
        isReached = false,
        remainingAmount = "54,000 ₽",
        deadline = "1 Mar 2027",
        creationDate = "1 Oct 2026",
        isOverdue = false,
        percent = 40,
        savedValue = 36000.toBigDecimal(),
        progress = .4f,
    )
    DebtBookTheme {
        GoalDetailsContent(
            goal = goal,
            state = GoalDetailsState(history = listOf(GoalMovement(1, "+8,500 ₽", "17 Nov 2026", GoalMovementType.DEPOSIT))),
            onBack = {}, onAdd = {}, onSubtract = {}, onEdit = {}, onDelete = {}, onRetry = {},
        )
    }
}
