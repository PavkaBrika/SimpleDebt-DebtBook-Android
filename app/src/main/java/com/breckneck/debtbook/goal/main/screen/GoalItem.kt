package com.breckneck.debtbook.goal.main.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.AnimatedAmount
import com.breckneck.debtbook.core.ui.components.AnimatedLinearProgressIndicator
import com.breckneck.debtbook.core.ui.components.BubbleOutlinedButton
import com.breckneck.debtbook.core.ui.components.rememberAnimatedProgress
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.main.model.GoalUi
import com.breckneck.debtbook.goal.ui.GoalDeleteButton
import java.text.NumberFormat
import java.util.Locale

private const val FullProgressHoldMillis = 144
private const val CollapseMillis = 300

@Composable
internal fun GoalItem(
    goal: GoalUi,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = bubbleColors()
    val progress = rememberAnimatedProgress(goal.progress)
    val full by remember(progress) { derivedStateOf { progress.value == 1f } }
    val completion = updateTransition(goal.isReached && full, label = "Goal completion")
    val fold by completion.animateFloat(
        transitionSpec = { tween(CollapseMillis, FullProgressHoldMillis) }, label = "Card collapse",
    ) { if (it) 1f else 0f }
    val check = completion.animateFloat(
        transitionSpec = { tween(160, FullProgressHoldMillis + CollapseMillis) }, label = "Completion check",
    ) { if (it) 1f else 0f }

    Column(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(lerp(28.dp, 20.dp, fold)),
            color = lerp(colors.mint, colors.paper, fold),
            contentColor = colors.onMint,
            border = if (fold == 0f) null else BorderStroke(1.dp, colors.border.copy(alpha = colors.border.alpha * fold)),
        ) {
            completion.AnimatedContent(
                contentAlignment = Alignment.TopStart,
                transitionSpec = {
                    (fadeIn(tween(140, FullProgressHoldMillis + 100)) togetherWith
                        fadeOut(tween(140, FullProgressHoldMillis))).using(
                        SizeTransform { _, _ -> tween(CollapseMillis, FullProgressHoldMillis) },
                    )
                },
            ) { compact ->
                if (compact) {
                    CompletedGoalRow(goal, check, onOpen, onDelete)
                } else {
                    GoalBalanceContent(goal, progress, onOpen)
                }
            }
        }
        completion.AnimatedVisibility(
            visible = { !it },
            exit = fadeOut(tween(140, FullProgressHoldMillis)) + shrinkVertically(
                animationSpec = tween(CollapseMillis, FullProgressHoldMillis), shrinkTowards = Alignment.Top,
            ),
        ) {
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BubbleOutlinedButton(onClick = onAdd, enabled = !goal.isReached) {
                    Icon(Icons.Outlined.Add, null, Modifier.padding(end = 5.dp).size(18.dp))
                    Text(stringResource(R.string.goal_top_up))
                }
                BubbleOutlinedButton(onClick = onEdit, enabled = !goal.isReached) {
                    Icon(Icons.Outlined.Edit, null, Modifier.padding(end = 5.dp).size(18.dp))
                    Text(stringResource(R.string.goal_edit_action))
                }
            }
        }
    }
}

@Composable
private fun CompletedGoalRow(goal: GoalUi, check: State<Float>, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = bubbleColors()
    val showCheck by remember(check) { derivedStateOf { check.value > 0f } }
    Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.history), onClick = onOpen)
                .heightIn(min = 60.dp).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Check,
                if (showCheck) stringResource(R.string.goal_reached) else null,
                Modifier.size(20.dp).drawWithContent {
                    clipRect(right = size.width * check.value) { this@drawWithContent.drawContent() }
                },
                tint = colors.onMint,
            )
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
                Text(stringResource(R.string.goal_balance, goal.savedAmount), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp), tint = colors.muted)
        }
        if (showCheck) GoalDeleteButton(goalName = goal.name, onDelete = onDelete)
        else Spacer(Modifier.size(48.dp))
    }
}

@Composable
internal fun GoalBalanceCard(goal: GoalUi, modifier: Modifier = Modifier) {
    val colors = bubbleColors()
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = colors.mint, contentColor = colors.onMint) {
        GoalBalanceContent(goal, rememberAnimatedProgress(goal.progress), onOpen = null)
    }
}

@Composable
private fun GoalBalanceContent(goal: GoalUi, progress: State<Float>, onOpen: (() -> Unit)?) {
    val colors = bubbleColors()
    val cardModifier = if (onOpen == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onOpen)
    val status = when {
        goal.isReached && onOpen == null -> stringResource(R.string.goal_reached)
        goal.deadline == null -> null
        goal.isOverdue -> stringResource(R.string.goal_due_overdue, goal.deadline)
        else -> stringResource(R.string.goal_due_by, goal.deadline)
    }
    Column(cardModifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            AnimatedAmount(
                value = goal.savedValue,
                formattedText = goal.savedAmount,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        AnimatedLinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            color = colors.onMint,
            trackColor = colors.onMint.copy(alpha = 0.16f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.goal_target_caption, goal.totalAmount),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            GoalProgressPercent(goal, progress)
        }
    }
}

@Composable
private fun GoalProgressPercent(goal: GoalUi, progress: State<Float>) {
    val fraction = progress.value
    // At rest, keep the exact whole percent calculated from the monetary amounts.
    val percent = if (fraction == goal.progress) goal.percent else (fraction * 100).toInt()
    val locale = Locale.getDefault()
    val format = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Text(
        text = remember(format, percent) { format.format(percent / 100.0) },
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}
