package com.breckneck.debtbook.finance.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateFinanceFragment : Fragment() {
    private val vm by viewModels<CreateFinanceViewModel>()

    interface OnClickListener {
        fun onBackButtonClick()
        fun onAddCategoryButtonClick(financeCategoryState: FinanceCategoryState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(com.breckneck.debtbook.R.id.financeFormView) { insets ->
            val host = requireActivity() as OnClickListener
            CreateFinanceScreen(vm, insets, host)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.reload()
        setFragmentResultListener("createFinanceFragmentKey") { _, result ->
            vm.categoryId.value = result.getInt("categoryId")
            vm.reload()
        }
        vm.saveState.observe(viewLifecycleOwner) {
            if (it == CreateFinanceViewModel.SaveState.SAVED) {
                (requireActivity() as OnClickListener).onBackButtonClick()
            }
        }
    }
}
