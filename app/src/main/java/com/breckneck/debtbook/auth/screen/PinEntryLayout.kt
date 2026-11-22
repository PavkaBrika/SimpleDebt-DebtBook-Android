package com.breckneck.debtbook.auth.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.deptbook.domain.util.PINCodeEnterState

@Composable
internal fun PinEntryLayout(
    hostInsets: PaddingValues,
    @StringRes prompt: Int,
    pin: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    pinState: PINCodeEnterState = PINCodeEnterState.FIRST,
    topBar: (@Composable () -> Unit)? = null,
    header: @Composable () -> Unit = {},
    supportingContent: @Composable () -> Unit = {},
    keypadModifier: Modifier = Modifier,
    onFingerprint: (() -> Unit)? = null,
) {
    val colors = bubbleColors()
    BubbleScaffold(hostInsets = hostInsets, topBar = topBar) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = PinEntryArrangement,
            ) {
                Box(Modifier.fillMaxWidth()) { header() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(prompt),
                        Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (pinState == PINCodeEnterState.INCORRECT) colors.danger else colors.ink,
                        textAlign = TextAlign.Center,
                    )
                    PINCodeSection(Modifier.widthIn(max = 144.dp).fillMaxWidth(), pin, pinState)
                    supportingContent()
                }
                ButtonsSection(
                    modifier = keypadModifier.widthIn(max = 360.dp),
                    onDigitButtonClick = onDigit,
                    onBackspaceButtonClick = onBackspace,
                    onFingerprintButtonClick = onFingerprint,
                )
            }
        }
    }
}

/** The PIN prompt sits near the upper third, with the keypad at the bottom of the scroll area. */
private object PinEntryArrangement : Arrangement.Vertical {
    override val spacing = 16.dp

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val gap = spacing.roundToPx()
        val keypadTop = totalSize - sizes[2]
        val minPromptTop = sizes[0] + gap
        val maxPromptTop = maxOf(minPromptTop, keypadTop - sizes[1] - gap)
        outPositions[0] = 0
        outPositions[1] = (totalSize / 3 - gap).coerceIn(minPromptTop, maxPromptTop)
        outPositions[2] = keypadTop
    }
}

@Composable
internal fun PinEntryError(message: String) {
    Text(
        message,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = bubbleColors().danger,
        textAlign = TextAlign.Center,
    )
}
