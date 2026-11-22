package com.breckneck.debtbook.finance.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.finance.details.screen.FinanceDetailsScreen
import com.breckneck.deptbook.domain.model.Finance
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FinanceDetailsFragment : Fragment() {
    private val vm by viewModels<FinanceDetailsViewModel>()

    interface OnClickListener {
        fun onEditFinanceClick(finance: Finance, direction: FinanceCategoryState)
        fun onAddCategoryFinance(categoryId: Int, currency: String, day: String, direction: FinanceCategoryState)
        fun onBackButtonClick()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(com.breckneck.debtbook.R.id.financeHistoryView) { insets ->
            val host = requireActivity() as OnClickListener
            FinanceDetailsScreen(vm, insets, host::onBackButtonClick, { host.onEditFinanceClick(it, vm.direction) },
                { host.onAddCategoryFinance(vm.categoryId, vm.currency, vm.day, vm.direction) })
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.history.first { it.categoryDeleted }
                (requireActivity() as OnClickListener).onBackButtonClick()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
