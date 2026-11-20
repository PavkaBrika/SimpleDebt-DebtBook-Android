package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun BubbleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = bubbleColors().action,
        contentColor = bubbleColors().onAction,
    ),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = colors,
        content = content,
    )
}

@Composable
internal fun BubbleOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = bubbleColors()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.paper, contentColor = colors.muted),
        content = content,
    )
}

@Composable
internal fun BubbleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = bubbleColors()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colors.paper,
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.border),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp), content = content)
    }
}
