package com.breckneck.debtbook.goal.create.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.breckneck.debtbook.R
import com.breckneck.debtbook.common.toDMYFormat
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleFieldRow
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleTextFieldRow
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.components.currencyLabel
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.goal.create.CreateGoalsAction
import com.breckneck.debtbook.goal.create.CreateGoalsFormValidation
import com.breckneck.debtbook.goal.create.CreateGoalsState
import com.breckneck.debtbook.goal.create.CreateGoalsViewModel
import com.breckneck.debtbook.goal.create.model.CreateGoalUi
import com.breckneck.debtbook.goal.create.model.SavedSumError
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.LedgerDate
import java.util.Date
import org.orbitmvi.orbit.compose.collectAsState

@Composable
internal fun CreateGoalsScreen(vm: CreateGoalsViewModel, hostInsets: PaddingValues, onCurrencyClick: (String) -> Unit) {
    val state by vm.collectAsState()
    CreateGoalsContent(state, vm::onAction, hostInsets, onCurrencyClick)
}

@Composable
internal fun CreateGoalsContent(
    state: CreateGoalsState,
    onAction: (CreateGoalsAction) -> Unit,
    hostInsets: PaddingValues = PaddingValues(0.dp),
    onCurrencyClick: (String) -> Unit = {},
) {
    val colors = bubbleColors()
    val goal = state.goal
    val enabled = state.isInitialized && !state.isSaving
    val title = stringResource(if (state.isEditMode) R.string.goal_edit else R.string.goal_new)
    val scroll = rememberScrollState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun openPicker(action: CreateGoalsAction) {
        focus.clearFocus()
        keyboard?.hide()
        onAction(action)
    }
    val hasInputError = state.nameError != null || state.sumError != null || state.savedSumError != null
    LaunchedEffect(hasInputError) {
        if (hasInputError) scroll.animateScrollTo(0)
    }
    BackHandler(enabled = state.isSaving) {}
    BubbleScaffold(
        hostInsets = hostInsets,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        topBar = {
            BubbleTopBar(
                title = title,
                onBack = { onAction(CreateGoalsAction.BackClick) },
                hostInsets = hostInsets,
                backEnabled = !state.isSaving,
            )
        },
        bottomAction = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (state.saveFailed || state.photoFailed) {
                    Text(
                        stringResource(if (state.photoFailed) R.string.goal_photo_error else R.string.save_failed_retry),
                        Modifier.padding(bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        color = colors.danger,
                    )
                }
                BubbleButton(
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
        if (!state.isInitialized) return@BubbleScaffold
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BubbleGroup {
                BubbleTextFieldRow(
                    icon = Icons.Outlined.Flag,
                    label = stringResource(R.string.name),
                    state = rememberTextFieldState(initialText = goal.name),
                    placeholder = stringResource(R.string.goal_name_hint),
                    enabled = enabled,
                    errorMessage = state.nameError?.let { stringResource(R.string.youmustentername) },
                    accepts = { it.length <= CreateGoalsFormValidation.MAX_NAME_LENGTH },
                    onChange = { onAction(CreateGoalsAction.NameChanged(it)) },
                )
                HorizontalDivider(color = colors.border)
                BubbleTextFieldRow(
                    icon = Icons.Outlined.Layers,
                    label = stringResource(R.string.goal_target_amount),
                    state = rememberTextFieldState(initialText = goal.sum),
                    placeholder = "0",
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    errorMessage = state.sumError?.let { stringResource(R.string.goal_amount_invalid) },
                    accepts = { DecimalInput.isEditable(it, goal.currency) },
                    onChange = { onAction(CreateGoalsAction.SumChanged(it)) },
                )
                HorizontalDivider(color = colors.border)
                BubbleTextFieldRow(
                    icon = Icons.AutoMirrored.Outlined.CallReceived,
                    label = stringResource(R.string.already_saved_sum),
                    state = rememberTextFieldState(initialText = goal.savedSum),
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
                val currencyDescription = stringResource(R.string.a11y_currency_picker, currencyLabel(goal.currency))
                BubbleFieldRow(
                    Icons.Outlined.PieChartOutline, stringResource(R.string.goal_currency),
                    Modifier.clickable(enabled = enabled, role = Role.Button) {
                        focus.clearFocus()
                        keyboard?.hide()
                        onCurrencyClick(goal.currency)
                    }
                        .semantics { contentDescription = currencyDescription },
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp)) },
                ) { Text(goal.currency) }
                HorizontalDivider(color = colors.border)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BubbleFieldRow(
                        Icons.Outlined.CalendarMonth, stringResource(R.string.goal_date_optional),
                        Modifier.weight(1f).clickable(enabled = enabled, role = Role.Button) { openPicker(CreateGoalsAction.DateClick) },
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
            BubbleGroup {
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button) {
                        openPicker(CreateGoalsAction.PhotoCardClick)
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
    if (state.isDatePickerVisible) {
        GoalDatePickerDialog(
            currentDate = goal.goalDate,
            onDateSelected = { onAction(CreateGoalsAction.DateSelected(it)) },
            onDismiss = { onAction(CreateGoalsAction.DismissDatePicker) },
        )
    }
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
                goal = CreateGoalUi(currency = "RUB"),
                isInitialized = true,
            ),
            onAction = {},
        )
    }
}
