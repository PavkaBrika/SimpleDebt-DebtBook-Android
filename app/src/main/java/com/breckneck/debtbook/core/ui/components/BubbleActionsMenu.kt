package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    items: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val colors = bubbleColors()
    var expanded by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    Box(modifier) {
        BubbleIconButton({ expanded = true }, enabled = enabled, diameter = diameter, containerColor = colors.headerBubble) {
            Icon(Icons.Outlined.MoreHoriz, description)
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
