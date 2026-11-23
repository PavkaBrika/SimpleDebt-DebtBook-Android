package com.breckneck.debtbook.finance.create

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.finance.*
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import com.breckneck.deptbook.domain.util.LedgerDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateFinanceScreen(vm: CreateFinanceViewModel, hostInsets: PaddingValues, host: CreateFinanceFragment.OnClickListener) {
    val colors = bubbleColors()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val direction by vm.direction.observeAsState(FinanceCategoryState.EXPENSE)
    val day by vm.day.observeAsState(requireNotNull(vm.day.value))
    val id by vm.categoryId.observeAsState(0)
    val categories by vm.categories.observeAsState(emptyList())
    val selected = categories.find { it.id == id }
    val saveState by vm.saveState.observeAsState()
    val deleting by vm.deleting.observeAsState(false)
    val enabled = saveState != CreateFinanceViewModel.SaveState.SAVING && saveState != CreateFinanceViewModel.SaveState.SAVED && !deleting
    androidx.activity.compose.BackHandler(enabled = !enabled) {}
    val failed by vm.loadFailed.observeAsState(false)
    val deleteFailed by vm.deleteFailed.observeAsState(false)
    var attempted by rememberSaveable { mutableStateOf(false) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val amount = rememberTextFieldState(vm.amount)
    val comment = rememberTextFieldState(vm.comment)
    val expense = direction == FinanceCategoryState.EXPENSE
    val accentContainer = if (expense) colors.dangerContainer else colors.mint
    val accentContent = if (expense) colors.danger else colors.onMint
    val title = if (vm.editing) R.string.finance_edit_entry else if (expense)
        R.string.finance_new_expense else R.string.finance_new_income

    BubbleScaffold(
        hostInsets = hostInsets,
        scrollBehindBarsWithIme = true,
        bottomBarScrim = false,
        topBar = {
            BubbleTopBar(stringResource(title), host::onBackButtonClick, hostInsets, backEnabled = enabled)
        },
        bottomAction = {
            if (saveState == CreateFinanceViewModel.SaveState.ERROR) {
                BubbleGroup(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.save_failed_retry), Modifier.padding(12.dp), color = colors.danger)
                }
            }
            BubbleButton(
                { attempted = true; focus.clearFocus(); vm.save() },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                enabled = enabled,
                colors = financeActionColors(direction),
            ) {
                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                Text(stringResource(R.string.finance_save_entry), Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        FinanceFormScroll(padding) {
            if (!vm.editing) FinanceDirection(direction, enabled = enabled, select = vm::changeDirection)
            val amountError = if (attempted && (amount.text.isEmpty() || vm.parsedAmount() == null))
                stringResource(R.string.repayment_invalid) else null
            BubbleAmountField(
                amount,
                vm.currency,
                vm::changeAmount,
                acceptEdits = enabled,
                errorMessage = amountError,
                containerColor = accentContainer,
                contentColor = accentContent,
            )
            if (amountError != null) Text(amountError, color = colors.danger)

            BubbleGroup {
                BubbleFieldRow(Icons.Outlined.DateRange, stringResource(R.string.finance_entry_date),
                    Modifier.clickable(enabled = enabled, role = Role.Button) { focus.clearFocus(); calendar = true }) {
                    Text(LedgerDate.display(day))
                }
                HorizontalDivider()
                BubbleTextFieldRow(Icons.Outlined.Edit, stringResource(R.string.comment), comment,
                    stringResource(R.string.due_optional), enabled, null, { true }, vm::changeComment)
            }
            if (vm.editing) {
                selected?.let { BubbleGroup { BubbleValueRow(stringResource(R.string.category), it.localizedName()) } }
            } else {
                BubbleSectionTitle(stringResource(R.string.category))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(id == category.id, { if (id != category.id) {
                                vm.categoryId.value = category.id
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                            } },
                            { Text(category.localizedName()) }, enabled = enabled, shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.paper,
                                selectedContainerColor = accentContainer,
                                selectedLabelColor = accentContent,
                            ),
                            leadingIcon = { CategoryBadge(category, Modifier.size(23.dp)) })
                    }
                    AssistChip({ focus.clearFocus(); host.onAddCategoryButtonClick(direction) },
                        { Text(stringResource(R.string.create_new_category)) }, enabled = enabled, shape = CircleShape,
                        leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)) })
                }
                if (attempted && selected == null) Text(stringResource(R.string.you_must_select_category), color = colors.danger)
                if (selected != null) TextButton({ confirmDelete = true }, enabled = enabled) {
                    Text(stringResource(R.string.delete_category), color = colors.danger)
                }
            }
            if (failed) BubbleOutlinedButton(vm::reload) { Text(stringResource(R.string.history_retry)) }
        }
    }
    if (calendar) FinanceDatePicker(day, onSelect = { vm.day.value = it; calendar = false }, onDismiss = { calendar = false })
    if (confirmDelete && selected != null) {
        ConfirmationBottomSheet(stringResource(R.string.delete_category),
            stringResource(R.string.finance_delete_category_all, selected.localizedName()), vm::deleteSelected,
            { confirmDelete = false }, confirmText = stringResource(R.string.delete), dismissText = stringResource(R.string.cancel),
            enabled = !deleting, errorMessage = if (deleteFailed) stringResource(R.string.history_change_failed) else null)
    }
    LaunchedEffect(id) { if (id == 0) confirmDelete = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FinanceFormScroll(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    val defaultScroll = LocalBringIntoViewSpec.current
    val density = LocalDensity.current
    val top = with(density) { padding.calculateTopPadding().toPx() }
    val bottom = with(density) { padding.calculateBottomPadding().toPx() }
    val focusScroll = remember(defaultScroll, top, bottom) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                defaultScroll.calculateScrollDistance(offset - top, size, (containerSize - top - bottom).coerceAtLeast(0f))
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides focusScroll) {
        Column(
            Modifier.fillMaxSize().consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
