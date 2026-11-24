package com.breckneck.debtbook.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.SettingsChoiceRow

@Composable
internal fun <T> DebtOrderSheet(
    title: String,
    order: T,
    choices: List<Pair<T, Int>>,
    onDismiss: () -> Unit,
    onSelect: (T, Boolean) -> Unit,
) {
    val colors = bubbleColors()
    var rememberChoice by rememberSaveable { mutableStateOf(false) }
    BubbleBottomSheet(onDismiss = onDismiss) { enabled, close ->
        BubbleSheetHeader(title, close, enabled = enabled)
        BubbleGroup {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .toggleable(rememberChoice, enabled = enabled, role = Role.Checkbox) { rememberChoice = it }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.remember_choice),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Checkbox(
                    checked = rememberChoice,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = colors.action),
                )
            }
        }
        BubbleGroup(Modifier.selectableGroup()) {
            choices.forEachIndexed { index, (choice, label) ->
                if (index > 0) HorizontalDivider(color = colors.border)
                SettingsChoiceRow(stringResource(label), selected = order == choice, enabled = enabled) {
                    onSelect(choice, rememberChoice)
                    close()
                }
            }
        }
    }
}
