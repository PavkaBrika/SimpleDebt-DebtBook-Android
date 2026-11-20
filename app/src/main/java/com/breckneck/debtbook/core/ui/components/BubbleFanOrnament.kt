package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

@Composable
internal fun BubbleFanOrnament(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val width = size.width * .34f
        val height = size.height * .77f
        repeat(12) { index ->
            rotate(index * 15f) {
                drawOval(
                    color = color,
                    topLeft = Offset(center.x - width / 2, center.y - height / 2),
                    size = Size(width, height),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
}
