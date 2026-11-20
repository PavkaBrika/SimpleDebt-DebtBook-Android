package com.breckneck.debtbook.goal.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.ShimmerListPlaceholder
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.debtbook.goal.main.screen.GoalAdjustmentSheet
import com.breckneck.debtbook.goal.main.screen.GoalBalanceCard
import com.breckneck.debtbook.goal.ui.DeleteGoalSheet
import com.breckneck.debtbook.goal.ui.GoalButton
import com.breckneck.debtbook.goal.ui.GoalHeader
import org.orbitmvi.orbit.compose.collectAsState

@Composable
internal fun GoalDetailsScreen(vm: GoalDetailsViewModel, onBack: () -> Unit) {
    val state by vm.collectAsState()
    val goal = state.goal ?: return
    GoalDetailsContent(
        goal = goal,
        state = state,
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
    state.deletion?.let {
        DeleteGoalSheet(
            name = goal.name,
            isDeleting = it.isDeleting,
            failed = it.failed,
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
) {
    val colors = bubbleColors()
    val secondaryColors = ButtonDefaults.buttonColors(containerColor = colors.mint, contentColor = colors.onMint)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentColor = colors.ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GoalHeader(goal.name, onBack, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                GoalActionsMenu(goal.isReached, onEdit, onDelete)
            }
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item { GoalBalanceCard(goal) }
            if (!goal.isReached) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        GoalButton(onClick = onAdd, modifier = Modifier.weight(1f), colors = secondaryColors) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.goal_top_up), Modifier.padding(start = 6.dp))
                        }
                        GoalButton(onClick = onSubtract, modifier = Modifier.weight(1f), colors = secondaryColors, enabled = goal.hasSavings) {
                            Icon(Icons.AutoMirrored.Outlined.CallMade, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.goal_withdraw), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    color = colors.paper,
                    contentColor = colors.ink,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Column {
                        GoalFact(stringResource(R.string.goal_remaining), goal.remainingAmount)
                        HorizontalDivider(color = colors.border)
                        GoalFact(stringResource(R.string.goal_created), goal.creationDate)
                        if (goal.isReached && goal.deadline != null) {
                            HorizontalDivider(color = colors.border)
                            GoalFact(stringResource(R.string.goal_deadline), goal.deadline)
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.history),
                    Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
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
                GoalHistoryRow(row, first = index == 0, last = index == state.history.lastIndex)
            }
        }
    }
}

@Composable
private fun GoalActionsMenu(isReached: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = bubbleColors()
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(shape = CircleShape, color = colors.paper, border = BorderStroke(1.dp, colors.border)) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.goal_actions), tint = colors.ink)
            }
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp), containerColor = colors.paper,
        ) {
            if (!isReached) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.goal_edit), color = colors.ink) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = colors.ink) },
                    onClick = {
                        expanded = false
                        onEdit()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.goal_delete), color = colors.danger) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = colors.danger) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun GoalFact(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun GoalHistoryRow(row: GoalMovement, first: Boolean, last: Boolean) {
    val colors = bubbleColors()
    val top = if (first) 24.dp else 0.dp
    val bottom = if (last) 24.dp else 0.dp
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.paper,
        contentColor = colors.ink,
        shape = RoundedCornerShape(top, top, bottom, bottom),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (row.isDeposit) Icons.AutoMirrored.Outlined.CallReceived else Icons.AutoMirrored.Outlined.CallMade,
                    null,
                    Modifier.size(36.dp).background(colors.mint, CircleShape).padding(10.dp),
                    tint = colors.onMint,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (row.isDeposit) R.string.goal_deposit else R.string.goal_withdrawal),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(row.date, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    if (largeText) GoalMovementAmount(row)
                }
                if (!largeText) {
                    Column(Modifier.weight(0.9f), horizontalAlignment = Alignment.End) { GoalMovementAmount(row) }
                }
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
    val goal = GoalUi(1, "Trip", null, "36,000 ₽", "90,000 ₽", "RUB", false, "54,000 ₽", "1 Mar 2027", "1 Oct 2026", false, 40, true)
    DebtBookTheme {
        GoalDetailsContent(
            goal = goal,
            state = GoalDetailsState(history = listOf(GoalMovement(1, "+8,500 ₽", "17 Nov 2026", true))),
            onBack = {}, onAdd = {}, onSubtract = {}, onEdit = {}, onDelete = {}, onRetry = {},
        )
    }
}
