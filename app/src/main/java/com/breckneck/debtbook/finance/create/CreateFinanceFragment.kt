package com.breckneck.debtbook.finance.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateFinanceFragment : Fragment() {
    private val vm by viewModels<CreateFinanceViewModel>()

    interface OnClickListener {
        fun onBackButtonClick()
        fun onAddCategoryButtonClick(financeCategoryState: FinanceCategoryState)
        fun onTickVibration()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val host = requireActivity() as OnClickListener
            setContent { DebtBookTheme { CreateFinanceScreen(vm, host) } }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.reload()
        setFragmentResultListener("createFinanceFragmentKey") { _, result ->
            vm.categoryId.value = result.getInt("categoryId")
            vm.reload()
        }
        vm.saveState.observe(viewLifecycleOwner) {
            if (it == CreateFinanceViewModel.SaveState.SAVED) {
                setFragmentResult("financeDetailsKey", bundleOf("isListModified" to true))
                (requireActivity() as OnClickListener).onBackButtonClick()
            }
        }
    }
}
