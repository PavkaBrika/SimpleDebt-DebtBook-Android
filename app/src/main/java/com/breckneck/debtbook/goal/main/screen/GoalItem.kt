package com.breckneck.debtbook.goal.main.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleOutlinedButton
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.debtbook.goal.ui.GoalActionsMenu
import java.text.NumberFormat

@Composable
internal fun GoalItem(
    goal: GoalUi,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (goal.isReached) {
        CompletedGoalRow(goal, onOpen, onDelete, modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GoalBalanceCard(goal, onOpen = onOpen)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BubbleOutlinedButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, null, Modifier.padding(end = 5.dp).size(18.dp))
                Text(stringResource(R.string.goal_top_up))
            }
            BubbleOutlinedButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, null, Modifier.padding(end = 5.dp).size(18.dp))
                Text(stringResource(R.string.goal_edit_action))
            }
        }
    }
}

@Composable
private fun CompletedGoalRow(goal: GoalUi, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val colors = bubbleColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.paper,
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.history), onClick = onOpen)
                    .heightIn(min = 60.dp).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Check, stringResource(R.string.goal_reached), Modifier.size(20.dp), tint = colors.onMint)
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.goal_balance, goal.savedAmount), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp), tint = colors.muted)
            }
            GoalActionsMenu(onDelete = onDelete)
        }
    }
}

@Composable
internal fun GoalBalanceCard(goal: GoalUi, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val colors = bubbleColors()
    val shape = RoundedCornerShape(28.dp)
    val cardModifier = if (onOpen == null) modifier else modifier.clip(shape).clickable(role = Role.Button, onClick = onOpen)
    val status = when {
        goal.isReached -> stringResource(R.string.goal_reached)
        goal.deadline == null -> null
        goal.isOverdue -> stringResource(R.string.goal_due_overdue, goal.deadline)
        else -> stringResource(R.string.goal_due_by, goal.deadline)
    }
    Surface(cardModifier.fillMaxWidth(), shape = shape, color = colors.mint, contentColor = colors.onMint) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (goal.photoPath != null) {
                AsyncImage(
                    goal.photoPath, stringResource(R.string.goal_s_photo),
                    Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(15.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onOpen != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(22.dp))
                    }
                }
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (goal.isOverdue && !goal.isReached) colors.danger else colors.onMint,
                    )
                }
                Text(
                    goal.savedAmount,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            LinearProgressIndicator(
                progress = { goal.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = colors.onMint,
                trackColor = colors.onMint.copy(alpha = 0.16f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.goal_target_caption, goal.totalAmount),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    NumberFormat.getPercentInstance().format(goal.percent / 100.0),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
