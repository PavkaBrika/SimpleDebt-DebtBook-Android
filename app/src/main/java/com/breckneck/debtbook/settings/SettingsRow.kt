package com.breckneck.debtbook.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.components.BubbleListRow
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun SettingsRow(
    title: String,
    detail: String? = null,
    enabled: Boolean = true,
    checked: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    val action = when {
        onClick == null -> Modifier
        checked != null -> Modifier.toggleable(checked, enabled, Role.Switch) { onClick() }
        else -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    BubbleListRow(
        modifier = action.alpha(if (enabled) 1f else .55f)
            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        trailing = {
            if (checked != null) Switch(checked, onCheckedChange = null, enabled = enabled)
            else if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = bubbleColors().muted)
        },
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = bubbleColors().muted)
    }
}
