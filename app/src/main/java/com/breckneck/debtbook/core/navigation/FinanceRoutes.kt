package com.breckneck.debtbook.core.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.finance.create.CreateFinanceScreen
import com.breckneck.debtbook.finance.create.CreateFinanceViewModel
import com.breckneck.debtbook.finance.create_category.CreateFinanceCategoryScreen
import com.breckneck.debtbook.finance.create_category.CreateFinanceCategorySideEffect
import com.breckneck.debtbook.finance.create_category.CreateFinanceCategoryViewModel
import com.breckneck.debtbook.finance.details.FinanceDetailsViewModel
import com.breckneck.debtbook.finance.details.screen.FinanceDetailsScreen
import com.breckneck.debtbook.finance.main.FinanceScreen
import com.breckneck.debtbook.finance.main.FinanceViewModel
import com.breckneck.deptbook.domain.util.LedgerDate
import kotlinx.coroutines.flow.first

internal fun NavGraphBuilder.financeDestinations(
    nav: NavHostController,
    insets: @Composable () -> PaddingValues,
    main: MainViewModel,
) {
    destination<Finances>(nav) {
        val vm = hiltViewModel<FinanceViewModel>(entry)
        LaunchedEffect(vm) { vm.refresh() }
        WhileResumed {
            entry.savedStateHandle.remove<String>(CURRENCY_RESULT)?.let(vm::setCurrency)
            vm.refresh()
        }
        FinanceScreen(
            vm, insets(),
            onAdd = { direction, day, currency ->
                if (isCurrent) main.onActionClick()
                open(FinanceForm(direction.name, currency, day))
            },
            onCategory = { name, id, direction, currency, day ->
                if (isCurrent) main.onActionClick()
                open(FinanceHistory(id, name, direction.name, currency, day))
            },
            onCurrencyClick = { open(Currencies(it)) },
        )
    }

    destination<FinanceHistory>(nav) {
        val vm = hiltViewModel<FinanceDetailsViewModel>(entry)
        LaunchedEffect(vm) { vm.refresh() }
        WhileResumed {
            vm.refresh()
            vm.history.first { it.categoryDeleted }
            back()
        }
        FinanceDetailsScreen(
            vm, insets(), ::back,
            { finance ->
                if (isCurrent) main.onActionClick()
                open(FinanceForm(
                    vm.direction.name, finance.currency, finance.calendarDay ?: LedgerDate.fromDate(finance.date),
                    finance.financeCategoryId, finance.id,
                ))
            },
            {
                if (isCurrent) main.onActionClick()
                open(FinanceForm(vm.direction.name, vm.currency, vm.day, vm.categoryId))
            },
        )
    }

    destination<FinanceForm>(nav) {
        val vm = hiltViewModel<CreateFinanceViewModel, CreateFinanceViewModel.Factory>(entry) {
            it.create(entry.toRoute())
        }
        val original by vm.originalState.observeAsState()
        val saved by vm.saveState.observeAsState()
        val context = LocalContext.current
        val active = LocalRouteActive.current
        Result<Int>(CATEGORY_RESULT) {
            vm.categoryId.value = it
            vm.reload()
        }
        LaunchedEffect(vm) { vm.reload() }
        LaunchedEffect(saved, original, active) {
            if (active && original == CreateFinanceViewModel.OriginalState.MISSING) {
                Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                back()
            } else if (active && saved == CreateFinanceViewModel.SaveState.SAVED) {
                back()
            }
        }

        when (original) {
            CreateFinanceViewModel.OriginalState.READY ->
                CreateFinanceScreen(vm, insets(), ::back) { direction ->
                    if (isCurrent) main.onActionClick()
                    open(FinanceCategoryForm(direction.name))
                }
            else -> RouteLoadingScreen(
                insets(), R.string.finance_edit_entry, ::back,
                failed = original == CreateFinanceViewModel.OriginalState.FAILED,
                onRetry = vm::loadOriginal,
            )
        }
    }

    destination<FinanceCategoryForm>(nav) {
        val vm = hiltViewModel<CreateFinanceCategoryViewModel>(entry)
        WhileResumed {
            vm.container.sideEffectFlow.collect { effect ->
                when (effect) {
                    is CreateFinanceCategorySideEffect.CategorySaved -> returnResult(CATEGORY_RESULT, effect.id)
                }
            }
        }
        CreateFinanceCategoryScreen(vm, insets(), ::back)
    }
}
