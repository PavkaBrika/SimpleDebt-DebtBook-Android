package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.theme.bubbleColors

internal val bubbleRootTitleTopPadding = 12.dp
internal val bubbleRootTitleMinHeight = 48.dp

@Composable
internal fun bubbleRootTitleStyle() = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)

@Composable
internal fun BubbleRootTitle(title: String, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = bubbleRootTitleMinHeight), contentAlignment = Alignment.CenterStart) {
        Text(title, Modifier.testTag("bubbleRootTitle").semantics { heading() },
            color = bubbleColors().ink, style = bubbleRootTitleStyle(), maxLines = 1)
    }
}
