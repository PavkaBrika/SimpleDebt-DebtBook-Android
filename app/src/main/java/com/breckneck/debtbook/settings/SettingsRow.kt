package com.breckneck.debtbook.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.components.BubbleListRow
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun SettingsRow(
    title: String,
    detail: String? = null,
    enabled: Boolean = true,
    checked: Boolean? = null,
    value: String? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val action = when {
        onClick == null -> Modifier
        checked != null -> Modifier.toggleable(checked, enabled, Role.Switch) { onClick() }
        else -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val valueWidth = maxWidth * .36f
        Column(action.fillMaxWidth()) {
            BubbleListRow(
                modifier = Modifier.alpha(if (enabled) 1f else .55f)
                    .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                trailing = {
                    if (value != null) Text(value, Modifier.widthIn(max = valueWidth), color = bubbleColors().muted,
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                    if (checked != null) Switch(checked, onCheckedChange = null, enabled = enabled)
                    else if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = bubbleColors().muted)
                },
            ) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = bubbleColors().muted)
            }
            if (supportingContent != null) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                    supportingContent()
                }
            }
        }
    }
}
