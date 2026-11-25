package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun BubbleActionsMenu(
    description: String,
    diameter: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingAction: (@Composable () -> Unit)? = null,
    items: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val colors = bubbleColors()
    var expanded by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    Box(modifier) {
        if (leadingAction == null) {
            BubbleIconButton({ expanded = true }, enabled = enabled, diameter = diameter, containerColor = colors.headerBubble) {
                Icon(Icons.Outlined.MoreHoriz, description)
            }
        } else {
            Row(
                Modifier.height(diameter).clip(CircleShape).background(colors.headerBubble)
                    .border(1.dp, colors.border, CircleShape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingAction()
                IconButton({ expanded = true }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.MoreHoriz, description, tint = colors.ink)
                }
            }
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = dismiss,
            shape = RoundedCornerShape(18.dp), containerColor = colors.paper,
        ) { items(dismiss) }
    }
}

@Composable
internal fun BubbleActionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = bubbleColors()
    val ink = if (destructive) colors.danger else colors.ink
    DropdownMenuItem(
        text = { Text(label, color = ink) },
        leadingIcon = { Icon(icon, null, tint = ink) },
        onClick = onClick,
        modifier = modifier,
    )
}
