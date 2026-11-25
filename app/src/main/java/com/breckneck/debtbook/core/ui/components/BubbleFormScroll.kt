package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BubbleFormScroll(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val defaultScroll = LocalBringIntoViewSpec.current
    val density = LocalDensity.current
    val top = with(density) { padding.calculateTopPadding().toPx() }
    val bottom = with(density) { padding.calculateBottomPadding().toPx() }
    val focusScroll = remember(defaultScroll, top, bottom) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                defaultScroll.calculateScrollDistance(offset - top, size, (containerSize - top - bottom).coerceAtLeast(0f))
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides focusScroll) {
        Column(
            modifier.fillMaxSize().consumeWindowInsets(padding).verticalScroll(scroll).padding(padding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
