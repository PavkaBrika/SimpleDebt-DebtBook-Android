package com.breckneck.debtbook.finance.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
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
        ComposeView(requireContext()).apply {
            id = com.breckneck.debtbook.R.id.financeHistoryView
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val host = requireActivity() as OnClickListener
            setContent {
                DebtBookTheme {
                    FinanceDetailsScreen(vm, host::onBackButtonClick, { host.onEditFinanceClick(it, vm.direction) },
                        { host.onAddCategoryFinance(vm.categoryId, vm.currency, vm.day, vm.direction) })
                }
            }
        }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
