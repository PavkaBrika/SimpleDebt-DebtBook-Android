package com.breckneck.debtbook.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
fun ConfirmationBottomSheet(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(R.string.yes),
    dismissText: String = stringResource(R.string.No),
    enabled: Boolean = true,
    errorMessage: String? = null,
) {
    BubbleBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        dismissEnabled = enabled,
        contentPadding = PaddingValues(20.dp),
    ) {
        ConfirmationContent(title, message, confirmText, dismissText, onConfirm, onDismiss, enabled, errorMessage)
    }
}

@Composable
internal fun ConfirmationContent(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    errorMessage: String? = null,
) {
    val colors = bubbleColors()
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(message)
    if (errorMessage != null) Text(errorMessage, color = colors.danger)
    BubbleButton(
        onClick = onConfirm, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.paper),
    ) { Text(confirmText) }
    BubbleButton(
        onClick = onDismiss, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = colors.mint, contentColor = colors.onMint),
    ) { Text(dismissText) }
}

@Preview(name = "Confirmation — light")
@Preview(name = "Confirmation — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfirmationBottomSheetPreview() {
    DebtBookTheme(dynamicColor = false) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConfirmationContent(
                title = "Are you sure?",
                message = "Delete this debt? This action cannot be undone.",
                confirmText = "Yes",
                dismissText = "No",
                onConfirm = {},
                onDismiss = {}
            )
        }
    }
}
