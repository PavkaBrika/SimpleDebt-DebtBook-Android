package com.breckneck.debtbook.goal.details

import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.R
import com.breckneck.debtbook.common.launchOnLifecycleStarted
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.deptbook.domain.model.Goal
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GoalDetailsFragment : Fragment() {
    private val vm by viewModels<GoalDetailsViewModel>()

    interface OnClickListener {
        val goalsContentInsets: PaddingValues
        fun onEditGoalButtonClick(goal: Goal)
        fun onBackButtonClick()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            id = R.id.goalDetailsComposeView
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val host = requireActivity() as OnClickListener
            setContent {
                DebtBookTheme {
                    GoalDetailsScreen(vm, host.goalsContentInsets, onBack = host::onBackButtonClick)
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        parentFragmentManager.setFragmentResultListener("goalDetailsFragmentKey", viewLifecycleOwner) { _, bundle ->
            if (bundle.getBoolean("isGoalEdited")) {
                BundleCompat.getSerializable(bundle, "goal", Goal::class.java)?.let(vm::onEdited)
            }
        }
        launchOnLifecycleStarted {
            vm.container.sideEffectFlow.collect { effect ->
                when (effect) {
                    is GoalDetailsEffect.Edit -> (activity as? OnClickListener)?.onEditGoalButtonClick(effect.goal)
                    GoalDetailsEffect.WithdrawalRejected -> ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.REJECT)
                    is GoalDetailsEffect.BalanceChanged -> {
                        if (effect.goal.savedSum >= effect.goal.sum) ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
                        setFragmentResult("goalsFragmentKey", bundleOf("isListModified" to true, "goal" to effect.goal))
                    }
                    GoalDetailsEffect.Deleted -> {
                        setFragmentResult("goalsFragmentKey", bundleOf("isListModified" to true))
                        (activity as? OnClickListener)?.onBackButtonClick()
                    }
                }
            }
        }
    }
}
