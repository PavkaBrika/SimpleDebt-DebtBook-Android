package com.breckneck.debtbook.debt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

private val motifPalette = listOf(
    Color(0xFFF4CEBB) to Color(0xFFA74E38), Color(0xFFD5E4F3) to Color(0xFF45678C),
    Color(0xFFE5DDB8) to Color(0xFF76682C), Color(0xFFDCE5B5) to Color(0xFF617337),
    Color(0xFFE7DBED) to Color(0xFF865F92),
)

@Composable
internal fun PersonMotif(personId: Int, modifier: Modifier = Modifier) {
    val index = (personId - 1).mod(motifPalette.size)
    val (paper, ink) = motifPalette[index]
    Canvas(modifier.size(38.dp).clip(CircleShape)) {
        drawRect(paper)
        scale(size.width / 48f, size.height / 48f, Offset.Zero) {
            val pivot = Offset(24f, 24f)
            when (index) {
                0, 3 -> {
                    val petals = if (index == 0) 8 else 9
                    val width = if (index == 0) 10.8f else 4.8f
                    repeat(petals) { petal ->
                        rotate(petal * 360f / petals, pivot) {
                            drawOval(ink, Offset(24f - width / 2, 4.6f), Size(width, 18.8f))
                        }
                    }
                    if (index == 0) drawCircle(paper, 4.6f, pivot)
                }
                1 -> repeat(6) { i ->
                    drawLine(ink, Offset(i * 11f - 10f, 46f), Offset(26f + i * 3, 9f), 3.3f)
                }
                2 -> rotate(32f, pivot) {
                    repeat(3) { drawRect(ink, Offset(8f, 10f + it * 10f), Size(32f, 6f)) }
                }
                else -> drawPath(Path().apply {
                    moveTo(24f, 4f); lineTo(29f, 18f); lineTo(43f, 24f); lineTo(29f, 29f)
                    lineTo(24f, 44f); lineTo(19f, 29f); lineTo(5f, 24f); lineTo(19f, 18f); close()
                }, ink)
            }
        }
    }
}
