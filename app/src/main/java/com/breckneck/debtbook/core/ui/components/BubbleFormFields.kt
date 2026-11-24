package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun BubbleFieldRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = bubbleColors()
    Row(
        modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(34.dp).background(colors.background, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.muted)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            content()
        }
        trailing?.invoke()
    }
}

@Composable
internal fun BubbleTextFieldRow(
    icon: ImageVector,
    label: String,
    state: TextFieldState,
    placeholder: String,
    enabled: Boolean,
    errorMessage: String?,
    accepts: (String) -> Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = bubbleColors()
    BasicTextField(
        state = state,
        inputTransformation = validatedInput(accepts, onChange),
        modifier = modifier.fillMaxWidth().semantics { if (errorMessage != null) error(errorMessage) },
        enabled = enabled,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        decorator = { editor ->
            BubbleFieldRow(icon, label, trailing = trailing) {
                Box {
                    if (state.text.isEmpty()) Text(placeholder, color = colors.muted)
                    editor()
                }
                if (errorMessage != null) Text(errorMessage, color = colors.danger, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

internal fun validatedInput(accepts: (String) -> Boolean, onChange: (String) -> Unit): InputTransformation =
    InputTransformation {
        val edited = asCharSequence().toString()
        if (edited != originalText.toString()) {
            if (accepts(edited)) onChange(edited) else revertAllChanges()
        }
    }
