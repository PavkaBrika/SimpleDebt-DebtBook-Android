package com.breckneck.debtbook.finance.create_category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.breckneck.debtbook.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.breckneck.debtbook.core.bubbleScreen

@AndroidEntryPoint
class CreateFinanceCategoryFragment : Fragment() {
    private val vm by viewModels<CreateFinanceCategoryViewModel>()

    interface OnClickListener {
        fun onBackButtonClick()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(R.id.financeCategoryView) { insets ->
            val host = requireActivity() as OnClickListener
            CreateFinanceCategoryScreen(vm, insets, host::onBackButtonClick)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.container.sideEffectFlow.collect { effect ->
                    when (effect) {
                        is CreateFinanceCategorySideEffect.CategorySaved -> {
                            setFragmentResult("createFinanceFragmentKey", bundleOf("categoryId" to effect.id))
                            (requireActivity() as OnClickListener).onBackButtonClick()
                        }
                    }
                }
            }
        }
    }
}
