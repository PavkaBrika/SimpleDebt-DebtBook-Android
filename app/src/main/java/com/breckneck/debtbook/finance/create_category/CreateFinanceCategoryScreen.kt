package com.breckneck.debtbook.finance.create_category

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleFieldRow
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleSectionTitle
import com.breckneck.debtbook.core.ui.components.BubbleTextFieldRow
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.components.SettingsPickerBottomSheet
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.core.ui.theme.spacing
import com.breckneck.debtbook.finance.create_category.model.emojiGroups
import com.breckneck.deptbook.domain.util.categoryColorList
import org.orbitmvi.orbit.compose.collectAsState

private val errorEnterAnimation: EnterTransition = expandVertically() + fadeIn(tween(200))
private val errorExitAnimation: ExitTransition = shrinkVertically() + fadeOut(tween(150))


@Composable
fun CreateFinanceCategoryScreen(
    vm: CreateFinanceCategoryViewModel,
    hostInsets: PaddingValues,
    onBackClick: () -> Unit
) {
    val state by vm.collectAsState()
    androidx.activity.compose.BackHandler(enabled = state.saving) {}

    CreateFinanceCategoryContent(
        state = state,
        hostInsets = hostInsets,
        onBackClick = onBackClick,
        onNameChange = vm::onNameChange,
        onImageSelected = vm::onImageSelected,
        onColorSelected = vm::onColorSelected,
        onSaveClick = vm::onSaveClick,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateFinanceCategoryContent(
    state: CreateFinanceCategoryState,
    onBackClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onImageSelected: (image: Int) -> Unit,
    onColorSelected: (color: String) -> Unit,
    onSaveClick: () -> Unit,
    hostInsets: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = MaterialTheme.spacing
    val nameError = if (state.isNameErrorVisible) stringResource(R.string.youmustentername) else null
    val imageError = if (state.isImageErrorVisible) stringResource(R.string.you_must_select_image) else null
    val colorError = if (state.isColorErrorVisible) stringResource(R.string.you_must_select_color) else null

    var group by rememberSaveable { mutableIntStateOf(0) }
    var choosingGroup by rememberSaveable { mutableStateOf(false) }

    BubbleScaffold(
        hostInsets = hostInsets,
        contentPadding = PaddingValues(0.dp),
        topBar = { BubbleTopBar(stringResource(R.string.create_new_category), onBackClick, hostInsets, backEnabled = !state.saving) },
        bottomAction = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (state.saveFailed) Text(stringResource(R.string.save_failed_retry), color = bubbleColors().danger)
                BubbleButton(onSaveClick, Modifier.fillMaxWidth(), enabled = !state.saving) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.finance_save_category), Modifier.padding(start = 8.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
        ) {
            CategoryPreview(
                name = state.categoryName,
                selectedImage = state.selectedImage,
                selectedColor = state.selectedColor
            )

            BubbleGroup(Modifier.padding(horizontal = spacing.space16)) {
                BubbleTextFieldRow(Icons.Outlined.Edit, stringResource(R.string.name), rememberTextFieldState(state.categoryName),
                    "", !state.saving, nameError, { it.length <= 60 }, onNameChange)
            }

            Column(Modifier.padding(horizontal = spacing.space16), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BubbleSectionTitle(stringResource(R.string.image), Modifier.padding(top = 16.dp))
                BubbleFieldRow(Icons.Outlined.Flag, stringResource(R.string.finance_icon_set),
                    Modifier.clickable(enabled = !state.saving) { choosingGroup = true },
                    trailing = { Icon(Icons.Outlined.ExpandMore, null) }) { Text(stringResource(emojiGroups[group].labelResId)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    emojiGroups[group].emojis.forEach { image ->
                        CategoryImageItem(image, state.selectedImage == image, { onImageSelected(image) })
                    }
                }
                SectionError(error = imageError, startPadding = 0.dp)
                BubbleSectionTitle(stringResource(R.string.color), Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    categoryColorList.forEachIndexed { index, color ->
                        CategoryColorItem(
                            colorHex = color,
                            label = stringResource(R.string.finance_color, index + 1),
                            isSelected = state.selectedColor == color,
                            onClick = { onColorSelected(color) }
                        )
                    }
                }

                SectionError(error = colorError, startPadding = 0.dp)

                Spacer(modifier = Modifier.height(spacing.space24))
            }
        }
    }
    if (choosingGroup) SettingsPickerBottomSheet(stringResource(R.string.finance_icon_set),
        emojiGroups.map { stringResource(it.labelResId) }, group, onItemSelected = { group = it; choosingGroup = false },
        onDismiss = { choosingGroup = false })
}

@Composable
private fun SectionError(
    error: String?,
    startPadding: Dp
) {
    val spacing = MaterialTheme.spacing
    AnimatedVisibility(
        visible = error != null,
        enter = errorEnterAnimation,
        exit = errorExitAnimation
    ) {
        Text(
            text = error.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = startPadding, top = spacing.space4)
        )
    }
}

@Composable
private fun CategoryPreview(
    name: String,
    selectedImage: Int?,
    selectedColor: String?
) {
    val colors = bubbleColors()
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(38.dp).background(selectedColor?.let { Color(it.toColorInt()) } ?: colors.fill, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(selectedImage?.let { String(Character.toChars(it)) } ?: "◇", color = colors.ink)
        }
        Text(name.ifBlank { stringResource(R.string.category) }, style = MaterialTheme.typography.titleMedium, color = colors.ink)
    }
}

@Composable
private fun CategoryImageItem(
    image: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emojiString = remember(image) { String(Character.toChars(image)) }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = tween(200),
        label = "imageScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(200),
        label = "imageBorder"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .scale(scale),
        shape = CircleShape,
        color = if (isSelected)
            bubbleColors().mint
        else
            bubbleColors().paper,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = emojiString,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryColorItem(
    colorHex: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val parsedColor = remember(colorHex) { Color(colorHex.toColorInt()) }
    val checkTint = remember(parsedColor) {
        if (parsedColor.luminance() > 0.4f) Color.Black else Color.White
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = tween(200),
        label = "colorScale"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp).semantics { contentDescription = label }
            .scale(scale),
        shape = CircleShape,
        color = parsedColor,
        border = if (isSelected) BorderStroke(
            width = 2.5.dp,
            color = MaterialTheme.colorScheme.onSurface
        ) else null
    ) {
        if (isSelected) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = checkTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(name = "Empty (light)")
@Preview(name = "Empty (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateFinanceCategoryPreviewEmpty() {
    DebtBookTheme(dynamicColor = false) {
        CreateFinanceCategoryContent(
            state = CreateFinanceCategoryState.initial(),
            onBackClick = {},
            onNameChange = {},
            onImageSelected = {},
            onColorSelected = {},
            onSaveClick = {}
        )
    }
}

@Preview(name = "Filled (light)")
@Preview(name = "Filled (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateFinanceCategoryPreviewFilled() {
    DebtBookTheme(dynamicColor = false) {
        CreateFinanceCategoryContent(
            state = CreateFinanceCategoryState.initial().copy(
                categoryName = "Food",
                selectedImage = 0x1F37D,
                selectedColor = "#A5D6A7"
            ),
            onBackClick = {},
            onNameChange = {},
            onImageSelected = {},
            onColorSelected = {},
            onSaveClick = {}
        )
    }
}

@Preview(name = "Validation error")
@Composable
private fun CreateFinanceCategoryPreviewError() {
    DebtBookTheme(dynamicColor = false) {
        CreateFinanceCategoryContent(
            state = CreateFinanceCategoryState.initial().copy(
                isNameErrorVisible = true,
                isImageErrorVisible = true,
                isColorErrorVisible = true
            ),
            onBackClick = {},
            onNameChange = {},
            onImageSelected = {},
            onColorSelected = {},
            onSaveClick = {}
        )
    }
}
