package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BubbleBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    hideRequested: Boolean = false,
    content: @Composable (enabled: Boolean, close: () -> Unit) -> Unit,
) {
    val colors = bubbleColors()
    var closeRequested by rememberSaveable { mutableStateOf(false) }
    val closing = closeRequested || hideRequested
    val canDismiss by rememberUpdatedState(dismissEnabled || hideRequested)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || canDismiss },
    )
    DebtBookBottomSheet(
        onDismiss = { if (canDismiss) onDismiss() },
        hideRequested = closing,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = colors.background,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content(dismissEnabled && !closing) { closeRequested = true } }
    }
}

@Composable
internal fun BubbleSheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        BubbleIconButton(onClick = onClose, enabled = enabled, diameter = 42.dp) {
            Icon(Icons.Outlined.Close, stringResource(R.string.cancel), Modifier.size(20.dp))
        }
    }
}
