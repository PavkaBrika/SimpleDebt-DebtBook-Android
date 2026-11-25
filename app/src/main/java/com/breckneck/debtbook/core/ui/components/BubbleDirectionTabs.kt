package com.breckneck.debtbook.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.theme.bubbleColors

@Composable
internal fun <T> BubbleDirectionTabs(
    directions: List<T>,
    selected: T,
    indicatorColor: Color,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tabModifier: (T) -> Modifier = { Modifier },
    content: @Composable (T, Boolean) -> Unit,
) {
    val colors = bubbleColors()
    val haptic = LocalHapticFeedback.current
    val indicator by animateColorAsState(indicatorColor, tween(200), label = "Direction color")
    BoxWithConstraints(modifier.fillMaxWidth().clip(CircleShape).background(colors.fill).padding(4.dp)) {
        val width = maxWidth / directions.size
        val offset by animateDpAsState(width * directions.indexOf(selected), tween(200), label = "Direction")
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset { IntOffset(offset.roundToPx(), 0) }.width(width)
                .fillMaxHeight().background(indicator, CircleShape))
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            for (direction in directions) {
                val active = direction == selected
                Box(Modifier.weight(1f).clip(CircleShape)
                    .selectable(active, enabled = enabled, role = Role.Tab) {
                        if (!active) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(direction)
                        }
                    }
                    .then(tabModifier(direction)).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    content(direction, active)
                }
            }
        }
    }
}
