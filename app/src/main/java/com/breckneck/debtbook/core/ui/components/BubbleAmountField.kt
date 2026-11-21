package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.deptbook.domain.util.DecimalInput

@Composable
internal fun BubbleAmountField(
    state: TextFieldState,
    currency: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    containerColor: Color = bubbleColors().mint,
    contentColor: Color = bubbleColors().onMint,
    currencyContent: @Composable () -> Unit = {
        Text(currency, fontSize = 16.sp, color = contentColor, fontWeight = FontWeight.Bold)
    },
) {
    val amountStyle = MaterialTheme.typography.displaySmall.copy(color = contentColor, fontWeight = FontWeight.SemiBold)
    BasicTextField(
        state = state,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().background(containerColor, RoundedCornerShape(25.dp))
            .semantics { if (errorMessage != null) error(errorMessage) },
        inputTransformation = validatedInput({ DecimalInput.isEditable(it, currency) }, onValueChange),
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = amountStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        cursorBrush = SolidColor(contentColor),
        decorator = { editor ->
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(stringResource(R.string.sum), color = contentColor, style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (state.text.isEmpty()) Text("0", style = amountStyle, color = contentColor.copy(alpha = .65f))
                        editor()
                    }
                    currencyContent()
                }
            }
        },
    )
}
