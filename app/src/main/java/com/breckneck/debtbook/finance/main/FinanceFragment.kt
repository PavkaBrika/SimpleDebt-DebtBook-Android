package com.breckneck.debtbook.finance.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.currency.onCurrencySelected
import com.breckneck.debtbook.core.currency.openCurrencyPicker
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FinanceFragment : Fragment() {
    private val vm by viewModels<FinanceViewModel>()

    interface OnButtonClickListener {
        fun onAddFinanceButtonClick(financeCategoryState: FinanceCategoryState, day: String, currency: String)
        fun onFinanceCategoryClick(categoryName: String, categoryId: Int, financeCategoryState: FinanceCategoryState, currency: String, day: String)
        fun onTabSelected(id: Int)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(R.id.financeSummaryView, tabDestination = R.id.financeFragment) { insets ->
            val host = requireActivity() as OnButtonClickListener
            FinanceScreen(vm, insets, host) { openCurrencyPicker(R.id.financeFragment, it) }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        onCurrencySelected(R.id.financeFragment, vm::setCurrency)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
