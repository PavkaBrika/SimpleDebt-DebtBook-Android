package com.breckneck.debtbook.debt.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.currency.onCurrencySelected
import com.breckneck.debtbook.core.currency.openCurrencyPicker
import com.breckneck.deptbook.domain.model.HumanDomain
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
                rename = ::openChangeDebtNameDialog,
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
        if (vm.isChangeDebtNameDialogOpened.value == true) vm.changedHuman.value?.let { openChangeDebtNameDialog(it) }
    }

    override fun onStart() {
        super.onStart()
        vm.setListStateLoading()
    }

    private fun openChangeDebtNameDialog(humanDomain: HumanDomain) {
        val dialog = BottomSheetDialog(requireActivity())
        dialog.setContentView(R.layout.dialog_change_debt_name)
        vm.onChangeDebtNameDialogOpen(humanDomain)
        val humanNameTextInput: TextInputLayout = dialog.findViewById(R.id.humanNameTextInput)!!
        val humanNameEditText: TextInputEditText = dialog.findViewById(R.id.humanNameEditText)!!
        humanNameEditText.setText(humanDomain.name)
        dialog.findViewById<Button>(R.id.confirmButton)!!.setOnClickListener {
            val changedName = humanNameEditText.text.toString().trim()
            if (changedName.isEmpty())
                humanNameTextInput.error = getString(R.string.youmustentername)
            else {
                if (changedName == humanDomain.name)
                    dialog.dismiss()
                else {
                    vm.updateHuman(humanDomain.copy(name = changedName))
                    dialog.dismiss()
                }
            }
        }

        dialog.findViewById<Button>(R.id.cancelButton)!!.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            vm.onChangeDebtNameDialogClose()
        }
        dialog.setOnCancelListener {
            vm.onChangeDebtNameDialogClose()
        }

        dialog.show()

    }

}
