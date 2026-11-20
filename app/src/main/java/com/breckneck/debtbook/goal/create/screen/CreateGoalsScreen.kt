package com.breckneck.debtbook.goal.create.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
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
                // Keep cursor and edit updates synchronous while Orbit records the draft.
                var name by remember {
                    mutableStateOf(TextFieldValue(goal.name))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        val changed = it.text != name.text
                        if (!changed || it.text.length <= CreateGoalsFormValidation.MAX_NAME_LENGTH) {
                            name = it
                            if (changed) onAction(CreateGoalsAction.NameChanged(it.text))
                        }
                    },
                    label = { Text(stringResource(R.string.name)) },
                    enabled = enabled,
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = if (state.nameError == null) null else { { Text(stringResource(R.string.youmustentername)) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                GoalFormAmount(
                    value = goal.sum,
                    onChange = { onAction(CreateGoalsAction.SumChanged(it)) },
                    label = stringResource(R.string.goal_target_amount),
                    currency = goal.currency,
                    enabled = enabled,
                    action = ImeAction.Next,
                    error = state.sumError?.let { stringResource(R.string.goal_amount_invalid) },
                )
                GoalFormAmount(
                    value = goal.savedSum,
                    onChange = { onAction(CreateGoalsAction.SavedSumChanged(it)) },
                    label = stringResource(R.string.already_saved_sum),
                    currency = goal.currency,
                    enabled = enabled,
                    action = ImeAction.Done,
                    error = when (state.savedSumError) {
                        SavedSumError.GREATER_THAN_SUM -> stringResource(R.string.goal_invalid_saved_amount)
                        SavedSumError.INVALID, SavedSumError.NEGATIVE -> stringResource(R.string.goal_amount_invalid)
                        null -> null
                    },
                )
                TextButton(
                    onClick = { onAction(CreateGoalsAction.CurrencyClick) },
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.a11y_currency_picker, goal.currencyDisplayName))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onAction(CreateGoalsAction.DateClick) }, enabled = enabled) {
                        Text(goal.goalDate?.toDMYFormat()?.let { "${stringResource(R.string.goal_deadline)} · $it" }
                            ?: stringResource(R.string.goal_date_optional))
                    }
                    if (goal.goalDate != null) {
                        TextButton(onClick = { onAction(CreateGoalsAction.ClearDate) }, enabled = enabled) {
                            Text(stringResource(R.string.goal_clear_date))
                        }
                    }
                }
                if (state.dateInvalid) Text(stringResource(R.string.goal_date_future), color = colors.danger)
            }
            GoalFormGroup {
                if (goal.hasImage) {
                    AsyncImage(
                        model = goal.imagePath ?: goal.imageUri,
                        contentDescription = stringResource(R.string.goal_s_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(152.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onAction(CreateGoalsAction.PhotoCardClick) }, enabled = enabled) {
                        Text(stringResource(if (goal.hasImage) R.string.goal_photo_replace else R.string.goal_photo_choose))
                    }
                    if (goal.hasImage) {
                        TextButton(onClick = { onAction(CreateGoalsAction.DeleteImage) }, enabled = enabled) {
                            Text(stringResource(R.string.goal_photo_remove))
                        }
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun GoalFormAmount(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    currency: String,
    enabled: Boolean,
    action: ImeAction,
    error: String?,
) {
    var input by remember { mutableStateOf(TextFieldValue(value)) }
    OutlinedTextField(
        value = input,
        onValueChange = {
            val changed = it.text != input.text
            if (!changed || DecimalInput.isEditable(it.text, currency)) {
                input = it
                if (changed) onChange(it.text)
            }
        },
        enabled = enabled,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = action),
        modifier = Modifier.fillMaxWidth(),
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
