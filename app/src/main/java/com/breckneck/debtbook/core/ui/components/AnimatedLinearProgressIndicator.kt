package com.breckneck.debtbook.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Displays the initial fraction immediately and interpolates subsequent changes in either direction. */
@Composable
fun AnimatedLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    onProgressSettled: ((Float) -> Unit)? = null,
) {
    val animated = rememberAnimatedProgress(progress, onProgressSettled)
    AnimatedLinearProgressIndicator(animated, modifier, color, trackColor)
}

/** Share the displayed fraction with a label or any other UI that follows the indicator. */
@Composable
fun rememberAnimatedProgress(
    progress: Float,
    onProgressSettled: ((Float) -> Unit)? = null,
    initialProgress: Float = progress,
): State<Float> {
    val target = progress.coerceIn(0f, 1f)
    val animated = remember { Animatable(initialProgress.coerceIn(0f, 1f)) }
    val onSettled by rememberUpdatedState(onProgressSettled)
    LaunchedEffect(target) {
        if (animated.value != target) animated.animateTo(target, tween(420))
        onSettled?.invoke(target)
    }
    return animated.asState()
}

@Composable
fun AnimatedLinearProgressIndicator(
    progress: State<Float>,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    LinearProgressIndicator(
        progress = { progress.value },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
