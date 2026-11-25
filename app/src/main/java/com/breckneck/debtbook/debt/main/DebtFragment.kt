package com.breckneck.debtbook.debt.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.currency.onCurrencySelected
import com.breckneck.debtbook.core.currency.openCurrencyPicker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DebtFragment : Fragment() {

    private val vm by viewModels<DebtViewModel>()

    interface OnButtonClickListener {
        fun onHumanClick(idHuman: Int, currency: String, name: String)

        fun onAddButtonClick()
        fun onTabSelected(id: Int)
        fun onDebtSearchChanged(searching: Boolean)

        fun onFilterButtonLongClick()

        fun onTickVibration()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(R.id.humansLayout) { insets ->
            DebtListScreen(
                vm, insets, requireActivity() as OnButtonClickListener,
                onCurrencyClick = { currency ->
                    val available = (vm.overviewCurrencies.value.orEmpty() + currency).filter(String::isNotBlank)
                    openCurrencyPicker(R.id.mainFragment, currency, available)
                },
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.isSearching.observe(viewLifecycleOwner) { searching ->
            (requireActivity() as OnButtonClickListener).onDebtSearchChanged(searching)
        }
        onCurrencySelected(R.id.mainFragment, vm::selectCurrency)
        setFragmentResultListener("mainFragmentKey") { _, result ->
            result.getString("currency")?.let(vm::selectCurrency)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setListStateLoading()
    }
}
