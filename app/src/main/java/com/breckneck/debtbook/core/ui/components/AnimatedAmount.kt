package com.breckneck.debtbook.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import java.math.BigDecimal

private data class AmountLabel(val value: BigDecimal, val text: String)

/** [value] sets the direction of travel. [formattedText] owns the locale, precision and currency. */
@Composable
fun AnimatedAmount(
    value: BigDecimal,
    formattedText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val transition = updateTransition(AmountLabel(value, formattedText), label = "Amount")
    val previous = transition.currentState
    val target = transition.targetState
    val rolling = previous.value.compareTo(target.value) != 0 && previous.text != target.text

    Box(modifier.clipToBounds().clearAndSetSemantics { text = AnnotatedString(formattedText) }) {
        transition.AnimatedContent(
            contentKey = { it.text },
            transitionSpec = {
                val direction = targetState.value.compareTo(initialState.value)
                if (direction == 0) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (slideInVertically(tween(320)) { direction * it } togetherWith
                        slideOutVertically(tween(320)) { -direction * it }).using(null)
                }
            },
        ) { label ->
            val digits = remember(label, previous, target, rolling) {
                if (rolling) label.text.digitLayer(previous.text, target.text, moving = true)
                else AnnotatedString(label.text)
            }
            Text(digits, style = style)
        }
        if (rolling) {
            val stationary = remember(previous, target) {
                target.text.digitLayer(previous.text, target.text, moving = false)
            }
            Text(stationary, style = style)
        }
    }
}

// Keep both layers in the original text layout, including wrapping, grouping and bidirectional text.
private fun String.digitLayer(from: String, to: String, moving: Boolean) = buildAnnotatedString {
    append(this@digitLayer)
    this@digitLayer.forEachIndexed { index, character ->
        val changed = character.isDigit() && (from.length != to.length || from.getOrNull(index) != to.getOrNull(index))
        if (changed != moving) addStyle(SpanStyle(color = Color.Transparent), index, index + 1)
    }
}
