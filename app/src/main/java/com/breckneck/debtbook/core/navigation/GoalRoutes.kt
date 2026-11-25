package com.breckneck.debtbook.core.navigation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.goal.create.CreateGoalsAction
import com.breckneck.debtbook.goal.create.CreateGoalsSideEffect
import com.breckneck.debtbook.goal.create.CreateGoalsViewModel
import com.breckneck.debtbook.goal.create.screen.CreateGoalsScreen
import com.breckneck.debtbook.goal.details.GoalDetailsEffect
import com.breckneck.debtbook.goal.details.GoalDetailsScreen
import com.breckneck.debtbook.goal.details.GoalDetailsViewModel
import com.breckneck.debtbook.goal.main.GoalsAction
import com.breckneck.debtbook.goal.main.GoalsSideEffect
import com.breckneck.debtbook.goal.main.GoalsViewModel
import com.breckneck.debtbook.goal.main.screen.GoalsScreen
import org.orbitmvi.orbit.compose.collectAsState

internal fun NavGraphBuilder.goalDestinations(
    nav: NavHostController,
    insets: @Composable () -> PaddingValues,
    main: MainViewModel,
) {
    destination<Goals>(nav) {
        val vm = hiltViewModel<GoalsViewModel>(entry)
        val view = LocalView.current
        WhileResumed {
            vm.onAction(GoalsAction.RefreshAfterNavigation(null))
            vm.container.sideEffectFlow.collect { effect ->
                when (effect) {
                    GoalsSideEffect.GoalReached ->
                        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
                }
            }
        }
        GoalsScreen(
            vm, insets(),
            onAdd = { openGoal(GoalForm(), main) },
            onOpen = { openGoal(GoalDetails(it), main) },
            onEdit = { openGoal(GoalForm(it), main) },
        )
    }

    destination<GoalDetails>(nav) {
        val vm = hiltViewModel<GoalDetailsViewModel>(entry)
        val view = LocalView.current
        val context = LocalContext.current
        Result<Boolean>(GOAL_EDIT_RESULT) { vm.retryHistory() }
        WhileResumed {
            vm.container.sideEffectFlow.collect { effect ->
                when (effect) {
                    GoalDetailsEffect.WithdrawalRejected ->
                        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.REJECT)
                    is GoalDetailsEffect.BalanceChanged -> {
                        if (effect.goal.savedSum >= effect.goal.sum) {
                            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
                        }
                    }
                    GoalDetailsEffect.Deleted -> back()
                    GoalDetailsEffect.Missing -> {
                        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                        back()
                    }
                }
            }
        }
        val route = entry.toRoute<GoalDetails>()
        GoalDetailsScreen(vm, insets(), ::back) { openGoal(GoalForm(route.goalId), main) }
    }

    destination<GoalForm>(nav) {
        val route = entry.toRoute<GoalForm>()
        val vm = hiltViewModel<CreateGoalsViewModel, CreateGoalsViewModel.Factory>(entry) { it.create(route) }
        val state by vm.collectAsState()
        val context = LocalContext.current
        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                vm.onAction(CreateGoalsAction.ImagePicked(uri))
            }
        }
        Result<String>(CURRENCY_RESULT) { vm.onAction(CreateGoalsAction.CurrencySelected(it)) }
        WhileResumed {
            vm.container.sideEffectFlow.collect { effect ->
                when (effect) {
                    is CreateGoalsSideEffect.NavigateBack -> {
                        if (effect.saved && nav.previousBackStackEntry?.destination?.hasRoute<GoalDetails>() == true) {
                            returnResult(GOAL_EDIT_RESULT, true)
                        } else back()
                    }
                    CreateGoalsSideEffect.MissingGoal -> {
                        Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
                        back()
                    }
                    CreateGoalsSideEffect.LaunchImagePicker -> imagePicker.launch(arrayOf("image/*"))
                }
            }
        }
        if (state.isInitialized) {
            CreateGoalsScreen(vm, insets()) { open(Currencies(it)) }
        } else {
            RouteLoadingScreen(insets(), if (route.goalId > 0) R.string.goal_edit else R.string.goal_new,
                ::back, state.loadFailed, vm::retry)
        }
    }
}

private fun Destination.openGoal(route: Any, main: MainViewModel) {
    if (isCurrent) main.onActionClick()
    open(route)
}
