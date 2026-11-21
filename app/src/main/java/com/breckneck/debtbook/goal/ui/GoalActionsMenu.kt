package com.breckneck.debtbook.goal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleIconButton
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun GoalActionsMenu(
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    diameter: Dp,
) {
    val colors = bubbleColors()
    var expanded by remember { mutableStateOf(false) }
    Box {
        BubbleIconButton(onClick = { expanded = true }, diameter = diameter, containerColor = colors.headerBubble) {
            Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.goal_actions))
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp), containerColor = colors.paper,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.goal_edit), color = colors.ink) },
                leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = colors.ink) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
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
