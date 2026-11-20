package com.breckneck.debtbook.goal.create.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.common.toDMYFormat
import com.breckneck.debtbook.core.ui.components.SettingsPickerBottomSheet
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.create.CreateGoalsAction
import com.breckneck.debtbook.goal.create.CreateGoalsFormValidation
import com.breckneck.debtbook.goal.create.CreateGoalsState
import com.breckneck.debtbook.goal.create.CreateGoalsViewModel
import com.breckneck.debtbook.goal.create.model.CreateGoalUi
import com.breckneck.debtbook.goal.create.model.SavedSumError
import com.breckneck.debtbook.goal.ui.GoalButton
import com.breckneck.debtbook.goal.ui.GoalHeader
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.LedgerDate
import java.util.Date
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun CreateGoalsScreen(vm: CreateGoalsViewModel) {
    val state by vm.collectAsState()
    CreateGoalsContent(state, vm::onAction)
}

@Composable
internal fun CreateGoalsContent(state: CreateGoalsState, onAction: (CreateGoalsAction) -> Unit) {
    val colors = bubbleColors()
    val goal = state.goal
    val enabled = state.isInitialized && !state.isSaving
    val title = stringResource(if (state.isEditMode) R.string.goal_edit else R.string.goal_new)
    val scroll = rememberScrollState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hasInputError = state.nameError != null || state.sumError != null || state.savedSumError != null
    LaunchedEffect(hasInputError) {
        if (hasInputError) scroll.animateScrollTo(0)
    }
    BackHandler(enabled = state.isSaving) {}
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentColor = colors.ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GoalHeader(
                title = title,
                onBack = { onAction(CreateGoalsAction.BackClick) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                backEnabled = !state.isSaving,
            )
        },
        bottomBar = {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (state.saveFailed || state.photoFailed) {
                    Text(
                        stringResource(if (state.photoFailed) R.string.goal_photo_error else R.string.save_failed_retry),
                        Modifier.padding(bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        color = colors.danger,
                    )
                }
                GoalButton(
                    onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        onAction(CreateGoalsAction.SaveClick)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.goal_save), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    ) { padding ->
        if (!state.isInitialized) return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoalFormGroup {
                GoalTextRow(
                    icon = Icons.Outlined.Flag,
                    label = stringResource(R.string.name),
                    value = goal.name,
                    placeholder = stringResource(R.string.goal_name_hint),
                    enabled = enabled,
                    errorMessage = state.nameError?.let { stringResource(R.string.youmustentername) },
                    accepts = { it.length <= CreateGoalsFormValidation.MAX_NAME_LENGTH },
                    onChange = { onAction(CreateGoalsAction.NameChanged(it)) },
                )
                HorizontalDivider(color = colors.border)
                GoalTextRow(
                    icon = Icons.Outlined.Layers,
                    label = stringResource(R.string.goal_target_amount),
                    value = goal.sum,
                    placeholder = "0",
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    errorMessage = state.sumError?.let { stringResource(R.string.goal_amount_invalid) },
                    accepts = { DecimalInput.isEditable(it, goal.currency) },
                    onChange = { onAction(CreateGoalsAction.SumChanged(it)) },
                )
                HorizontalDivider(color = colors.border)
                GoalTextRow(
                    icon = Icons.AutoMirrored.Outlined.CallReceived,
                    label = stringResource(R.string.already_saved_sum),
                    value = goal.savedSum,
                    placeholder = "0",
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    errorMessage = when (state.savedSumError) {
                        SavedSumError.GREATER_THAN_SUM -> stringResource(R.string.goal_invalid_saved_amount)
                        SavedSumError.INVALID, SavedSumError.NEGATIVE -> stringResource(R.string.goal_amount_invalid)
                        null -> null
                    },
                    accepts = { DecimalInput.isEditable(it, goal.currency) },
                    onChange = { onAction(CreateGoalsAction.SavedSumChanged(it)) },
                )
                HorizontalDivider(color = colors.border)
                GoalFieldRow(
                    Icons.Outlined.PieChartOutline, stringResource(R.string.goal_currency),
                    Modifier.clickable(enabled = enabled, role = Role.Button) { onAction(CreateGoalsAction.CurrencyClick) },
                    trailing = { Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) },
                ) { Text(goal.currency) }
                HorizontalDivider(color = colors.border)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalFieldRow(
                        Icons.Outlined.CalendarMonth, stringResource(R.string.goal_date_optional),
                        Modifier.weight(1f).clickable(enabled = enabled, role = Role.Button) { onAction(CreateGoalsAction.DateClick) },
                    ) {
                        Text(goal.goalDate?.toDMYFormat() ?: stringResource(R.string.goal_choose_date),
                            color = if (goal.goalDate == null) colors.muted else colors.ink)
                    }
                    if (goal.goalDate != null) {
                        IconButton(onClick = { onAction(CreateGoalsAction.ClearDate) }, enabled = enabled) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.goal_clear_date))
                        }
                    }
                }
                if (state.dateInvalid) Text(stringResource(R.string.goal_date_future), Modifier.padding(12.dp), color = colors.danger)
            }
            GoalFormGroup {
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button) {
                        onAction(CreateGoalsAction.PhotoCardClick)
                    }.padding(12.dp),
                ) {
                    if (goal.hasImage) {
                        AsyncImage(
                            model = goal.imagePath ?: goal.imageUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(15.dp)),
                        )
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (goal.hasImage) R.string.goal_photo_replace else R.string.goal_photo_choose))
                    }
                }
                if (goal.hasImage) {
                    HorizontalDivider(color = colors.border)
                    TextButton(onClick = { onAction(CreateGoalsAction.DeleteImage) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.goal_photo_remove), color = colors.danger)
                    }
                }
            }
        }
    }
    if (state.isCurrencySheetVisible) {
        SettingsPickerBottomSheet(
            title = stringResource(R.string.select_currency),
            options = state.currencyNames,
            selectedIndex = state.selectedCurrencyIndex,
            onItemSelected = { onAction(CreateGoalsAction.CurrencySelected(it)) },
            onDismiss = { onAction(CreateGoalsAction.DismissCurrencySheet) },
        )
    }
    if (state.isDatePickerVisible) {
        GoalDatePickerDialog(
            currentDate = goal.goalDate,
            onDateSelected = { onAction(CreateGoalsAction.DateSelected(it)) },
            onDismiss = { onAction(CreateGoalsAction.DismissDatePicker) },
        )
    }
}

@Composable
private fun GoalFormGroup(content: @Composable () -> Unit) {
    val colors = bubbleColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.paper,
        contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column { content() }
    }
}

@Composable
private fun GoalFieldRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = bubbleColors()
    Row(
        modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(34.dp).background(colors.background, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.muted)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            content()
        }
        trailing()
    }
}

@Composable
private fun GoalTextRow(
    icon: ImageVector,
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    errorMessage: String?,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    accepts: (String) -> Boolean,
    onChange: (String) -> Unit,
) {
    val colors = bubbleColors()
    // Keep selection and composing spans synchronous while Orbit records the draft.
    var input by remember { mutableStateOf(TextFieldValue(value)) }
    BasicTextField(
        value = input,
        onValueChange = {
            val changed = it.text != input.text
            if (!changed || accepts(it.text)) {
                input = it
                if (changed) onChange(it.text)
            }
        },
        modifier = Modifier.fillMaxWidth().semantics { if (errorMessage != null) error(errorMessage) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        decorationBox = { editor ->
            GoalFieldRow(icon, label) {
                Box {
                    if (input.text.isEmpty()) Text(placeholder, color = colors.muted)
                    editor()
                }
                if (errorMessage != null) Text(errorMessage, color = colors.danger, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDatePickerDialog(currentDate: Date?, onDateSelected: (Date) -> Unit, onDismiss: () -> Unit) {
    val today = LedgerDate.pickerMillis(LedgerDate.today())
    val selectableDates = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis > today
        }
    }
    val smallWindow = LocalConfiguration.current.let { it.screenWidthDp < 380 || it.screenHeightDp < 560 || it.fontScale > 1.2f }
    val selected = currentDate?.let { LedgerDate.pickerMillis(LedgerDate.fromDate(it)) }?.takeIf { it > today }
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = selected,
        initialDisplayMode = if (smallWindow) DisplayMode.Input else DisplayMode.Picker,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = picker.selectedDateMillis?.let { it > today } == true,
                onClick = { picker.selectedDateMillis?.let { onDateSelected(LedgerDate.fromPickerMillis(it)) } },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = picker, showModeToggle = !smallWindow)
    }
}

@PreviewLightDark
@Composable
private fun CreateGoalsPreview() {
    DebtBookTheme {
        CreateGoalsContent(
            CreateGoalsState.initial().copy(
                goal = CreateGoalUi(currency = "RUB", currencyDisplayName = "RUB"),
                isInitialized = true,
            ),
            onAction = {},
        )
    }
}
