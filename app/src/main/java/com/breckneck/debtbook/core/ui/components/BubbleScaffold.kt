package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.theme.bubbleColors

/** The scrollable fills the window. Apply the supplied padding inside its scroll range. */
@Composable
internal fun BubbleScaffold(
    hostInsets: PaddingValues,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    topBar: (@Composable () -> Unit)? = null,
    bottomAction: @Composable () -> Unit = {},
    tabs: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    scrollBehindBarsWithIme: Boolean = false,
    bottomBarScrim: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = bubbleColors()
    val direction = LocalLayoutDirection.current
    val start = hostInsets.calculateStartPadding(direction)
    val end = hostInsets.calculateEndPadding(direction)
    val insetContentForIme = !scrollBehindBarsWithIme && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = colors.background,
        contentColor = colors.ink,
        contentWindowInsets = WindowInsets(0),
        topBar = { topBar?.invoke() },
        snackbarHost = snackbarHost,
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                if (tabs != null) {
                    Column(Modifier.padding(start = start, end = end)) { bottomAction() }
                }
                val scrim = if (bottomBarScrim) {
                    Modifier.background(
                        Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = .9f))),
                    )
                } else {
                    Modifier
                }
                Column(
                    Modifier.fillMaxWidth().then(scrim)
                        .padding(start = start, end = end, bottom = hostInsets.calculateBottomPadding()),
                ) {
                    if (tabs == null) bottomAction() else tabs()
                }
            }
        },
    ) { bars ->
        val top = if (topBar == null) hostInsets.calculateTopPadding() else bars.calculateTopPadding()
        val bottom = bars.calculateBottomPadding()
        Box(Modifier.fillMaxSize()) {
            // Give focus scrolling the unobstructed viewport while editing a field.
            Box(Modifier.fillMaxSize().then(if (insetContentForIme) Modifier.padding(top = top, bottom = bottom) else Modifier)) {
                content(PaddingValues(
                    start = start + contentPadding.calculateStartPadding(direction),
                    top = contentPadding.calculateTopPadding() + if (insetContentForIme) 0.dp else top,
                    end = end + contentPadding.calculateEndPadding(direction),
                    bottom = contentPadding.calculateBottomPadding() + if (insetContentForIme) 0.dp else bottom,
                ))
            }
            if (topBar == null) {
                Spacer(
                    Modifier.fillMaxWidth().height(top + 12.dp).background(
                        Brush.verticalGradient(
                            0f to colors.background.copy(alpha = .94f),
                            .45f to colors.background.copy(alpha = .84f),
                            1f to colors.background.copy(alpha = 0f),
                        ),
                    ),
                )
            }
        }
    }
}
