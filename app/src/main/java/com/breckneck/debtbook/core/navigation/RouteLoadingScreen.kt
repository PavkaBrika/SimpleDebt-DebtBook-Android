package com.breckneck.debtbook.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleOutlinedButton
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleTopBar

@Composable
internal fun RouteLoadingScreen(
    insets: PaddingValues,
    @StringRes title: Int,
    onBack: () -> Unit,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    BubbleScaffold(
        hostInsets = insets,
        topBar = { BubbleTopBar(stringResource(title), onBack, insets) },
    ) { padding ->
        if (failed) {
            BubbleOutlinedButton(onRetry, Modifier.padding(padding)) { Text(stringResource(R.string.history_retry)) }
        } else {
            CircularProgressIndicator(Modifier.padding(padding))
        }
    }
}
