package com.breckneck.debtbook.goal.ui

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleActionItem
import com.breckneck.debtbook.core.ui.components.BubbleActionsMenu
import com.breckneck.debtbook.core.ui.components.BubbleIconButton
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun GoalActionsMenu(
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    diameter: Dp,
) {
    BubbleActionsMenu(stringResource(R.string.goal_actions), diameter) { dismiss ->
        BubbleActionItem(stringResource(R.string.goal_edit), Icons.Outlined.Edit, { dismiss(); onEdit() })
        BubbleActionItem(stringResource(R.string.goal_delete), Icons.Outlined.DeleteOutline,
            { dismiss(); onDelete() }, destructive = true)
    }
}

@Composable
internal fun GoalDeleteButton(goalName: String, onDelete: () -> Unit, diameter: Dp? = null) {
    val icon: @Composable () -> Unit = {
        Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.goal_delete_named, goalName),
            tint = bubbleColors().danger,
        )
    }
    if (diameter == null) {
        IconButton(onClick = onDelete, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), content = icon)
    } else {
        BubbleIconButton(
            onClick = onDelete,
            diameter = diameter,
            containerColor = bubbleColors().headerBubble,
            content = icon,
        )
    }
}
