package com.breckneck.debtbook.settings.rating

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleBottomSheet
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleSheetHeader
import com.breckneck.debtbook.core.ui.theme.bubbleColors

internal data class AppRatingState(
    val fromSettings: Boolean,
    val rating: Int = 0,
    val writingReview: Boolean = false,
    val reviewText: String = "",
)

@Composable
internal fun AppRatingSheet(
    state: AppRatingState,
    onSelect: (Int) -> Unit,
    onReviewChange: (String) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = bubbleColors()
    BubbleBottomSheet(onDismiss = onDismiss) { enabled, close ->
        val title = if (state.writingReview) R.string.rating_review_title else R.string.rate_app
        BubbleSheetHeader(stringResource(title), close, enabled = enabled)

        if (state.writingReview) {
            Text(
                stringResource(R.string.rating_score, state.rating),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            OutlinedTextField(
                value = state.reviewText,
                onValueChange = onReviewChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(R.string.rating_review_hint)) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.paper,
                    unfocusedContainerColor = colors.paper,
                    unfocusedBorderColor = colors.border,
                ),
            )
        } else {
            AppRatingStars(state.rating, onSelect, enabled)
        }

        BubbleButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && state.rating > 0,
        ) {
            val label = when {
                state.writingReview -> R.string.rating_open_email
                state.rating >= 4 -> R.string.rating_open_store
                else -> R.string.continue_b
            }
            Text(stringResource(label))
        }
    }
}

@Composable
private fun AppRatingStars(rating: Int, onSelect: (Int) -> Unit, enabled: Boolean) {
    val colors = bubbleColors()
    BubbleGroup {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(4.dp)) {
            for (star in 1..5) {
                val label = stringResource(R.string.settings_rating, star)
                val filled = star <= rating
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .selectable(
                            selected = star == rating,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(star) },
                        )
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (filled) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (filled) colors.action else colors.muted,
                    )
                }
            }
        }
    }
}
