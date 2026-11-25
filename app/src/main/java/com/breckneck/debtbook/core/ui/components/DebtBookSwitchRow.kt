package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.core.ui.theme.spacing

@Composable
fun DebtBookSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val spacing = MaterialTheme.spacing
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier.fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = spacing.space16, vertical = spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
internal fun bubbleSwitchColors() = bubbleColors().let { colors ->
    SwitchDefaults.colors(
        checkedThumbColor = colors.onAction,
        checkedTrackColor = colors.action,
        uncheckedThumbColor = colors.muted,
        uncheckedTrackColor = colors.fill,
        uncheckedBorderColor = colors.border,
    )
}
