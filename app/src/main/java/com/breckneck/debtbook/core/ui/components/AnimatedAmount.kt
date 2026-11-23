package com.breckneck.debtbook.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import java.math.BigDecimal

private data class AmountLabel(val value: BigDecimal, val text: String)
private const val AmountMotionMillis = 320

/** [value] sets the direction of travel. [formattedText] owns the locale, precision and currency. */
@Composable
fun AnimatedAmount(
    value: BigDecimal,
    formattedText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val amountSemantics = modifier.clearAndSetSemantics { text = AnnotatedString(formattedText) }
    val separator = formattedText.indexOf('\u00a0')
    if (separator <= 0 || separator == formattedText.lastIndex || !formattedText[separator - 1].isDigit()) {
        RollingAmount(value, formattedText, style, amountSemantics)
        return
    }

    BoxWithConstraints(amountSemantics) {
        val textMeasurer = rememberTextMeasurer()
        val layout = textMeasurer.measure(
            formattedText,
            style = style,
            maxLines = 1,
            constraints = Constraints(maxWidth = constraints.maxWidth),
        )
        if (layout.hasVisualOverflow) {
            // Allow wrapped amounts to keep the currency with the final group of digits.
            RollingAmount(value, formattedText, style)
        } else {
            val textDirection = LocalLayoutDirection.current
            val separatorOnRight = layout.getBoundingBox(separator).center.x > layout.getBoundingBox(separator - 1).center.x
            val rowDirection = if (separatorOnRight) LayoutDirection.Ltr else LayoutDirection.Rtl
            CompositionLocalProvider(LocalLayoutDirection provides rowDirection) {
                Row {
                    CompositionLocalProvider(LocalLayoutDirection provides textDirection) {
                        RollingAmount(value, formattedText.substring(0, separator), style, animateWidth = true)
                        Text(formattedText.substring(separator), style = style, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun RollingAmount(
    value: BigDecimal,
    label: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    animateWidth: Boolean = false,
) {
    val transition = updateTransition(AmountLabel(value, label), label = "Amount")
    val previous = transition.currentState
    val target = transition.targetState
    val rolling = previous.value.compareTo(target.value) != 0 && previous.text != target.text

    Box(modifier.clipToBounds()) {
        transition.AnimatedContent(
            contentKey = { it.text },
            transitionSpec = {
                val direction = targetState.value.compareTo(initialState.value)
                val content = if (direction == 0) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    slideInVertically(tween(AmountMotionMillis)) { direction * it } togetherWith
                        slideOutVertically(tween(AmountMotionMillis)) { -direction * it }
                }
                content.using(if (animateWidth) SizeTransform(clip = false) { _, _ -> tween(AmountMotionMillis) } else null)
            },
        ) { label ->
            val animatedText = remember(label, previous, target, rolling) {
                if (rolling) label.text.amountLayer(previous.text, target.text, moving = true)
                else AnnotatedString(label.text)
            }
            Text(animatedText, style = style)
        }
        if (rolling) {
            val stationary = remember(previous, target) {
                target.text.amountLayer(previous.text, target.text, moving = false)
            }
            val stationaryModifier = if (animateWidth) {
                Modifier.matchParentSize().wrapContentSize(Alignment.TopStart, unbounded = true)
            } else Modifier
            Text(stationary, stationaryModifier, style = style)
        }
    }
}

// Keep both layers in the original text layout, including wrapping, grouping and bidirectional text.
private fun String.amountLayer(from: String, to: String, moving: Boolean) = buildAnnotatedString {
    append(this@amountLayer)
    this@amountLayer.forEachIndexed { index, character ->
        val changed = from.getOrNull(index) != to.getOrNull(index)
        val rolls = when {
            character.isDigit() -> from.length != to.length || changed
            character == '+' || character == '-' || character == '−' -> changed
            else -> false
        }
        if (rolls != moving) addStyle(SpanStyle(color = Color.Transparent), index, index + 1)
    }
}
