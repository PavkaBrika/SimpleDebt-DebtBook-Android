package com.breckneck.debtbook.goal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteGoalSheet(
    name: String,
    isDeleting: Boolean,
    failed: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = bubbleColors()
    val busy = rememberUpdatedState(isDeleting)
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy.value },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        sheetState = sheet,
        containerColor = colors.background,
        contentColor = colors.ink,
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.goal_delete_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.goal_delete_message, name))
            if (failed) Text(stringResource(R.string.something_went_wrong), color = colors.danger)
            BubbleButton(
                onClick = onConfirm, enabled = !isDeleting, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.paper),
            ) { Text(stringResource(R.string.delete)) }
            BubbleButton(
                onClick = onDismiss, enabled = !isDeleting, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.mint, contentColor = colors.onMint),
            ) { Text(stringResource(R.string.cancel)) }
        }
    }
}
