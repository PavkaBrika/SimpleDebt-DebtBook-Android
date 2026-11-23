package com.breckneck.debtbook.debt.main

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.currency.onCurrencySelected
import com.breckneck.debtbook.core.currency.openCurrencyPicker
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.util.*
import com.breckneck.deptbook.domain.util.Filter
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

        fun onFilterButtonLongClick()

        fun onTickVibration()
    }

    var buttonClickListener: OnButtonClickListener? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        buttonClickListener = context as OnButtonClickListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(R.id.humansLayout) { insets ->
            DebtListScreen(
                vm, insets, requireActivity() as OnButtonClickListener,
                sort = ::showHumanSortDialog,
                rename = ::openChangeDebtNameDialog,
                onCurrencyClick = { currency ->
                    val available = (vm.overviewCurrencies.value.orEmpty() + currency).filter(String::isNotBlank)
                    openCurrencyPicker(R.id.mainFragment, currency, available)
                },
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        onCurrencySelected(R.id.mainFragment, vm::selectCurrency)
        setFragmentResultListener("mainFragmentKey") { _, result ->
            result.getString("currency")?.let(vm::selectCurrency)
            vm.setListStateLoading()
        }
        if (vm.isSortDialogOpened.value == true) showHumanSortDialog()
        if (vm.isChangeDebtNameDialogOpened.value == true) vm.changedHuman.value?.let { openChangeDebtNameDialog(it) }
    }

    override fun onResume() {
        super.onResume()
        vm.setListStateLoading()
    }

    private fun showHumanSortDialog() {
        val bottomSheetDialogFilter = BottomSheetDialog(requireContext())
        bottomSheetDialogFilter.setContentView(R.layout.dialog_sort)
        bottomSheetDialogFilter.findViewById<RadioButton>(R.id.orderSumRadioButton)!!.setText(R.string.order_by_balance)
        bottomSheetDialogFilter.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        vm.onHumanSortDialogOpen()

        var sortHumansAttribute = vm.humanOrder.value!!.first
        when (sortHumansAttribute) {
            HumanOrderAttribute.Date ->
                bottomSheetDialogFilter.findViewById<RadioButton>(R.id.orderCreationDateRadioButton)!!.isChecked =
                    true

            HumanOrderAttribute.Sum ->
                bottomSheetDialogFilter.findViewById<RadioButton>(R.id.orderSumRadioButton)!!.isChecked =
                    true
        }

        var sortByIncrease = vm.humanOrder.value!!.second
        val sortImageView = bottomSheetDialogFilter.findViewById<ImageView>(R.id.sortImageView)
        if (sortByIncrease)
            sortImageView!!.rotationY = ROTATE_DEGREE_HUMAN_IMAGE_VIEW_BY_INCREASE
        else
            sortImageView!!.rotationY = ROTATE_DEGREE_HUMAN_IMAGE_VIEW_BY_DECREASE

        bottomSheetDialogFilter.findViewById<CardView>(R.id.sortButtonCard)!!.setOnClickListener {
            buttonClickListener!!.onTickVibration()
            if (sortImageView.rotationY == ROTATE_DEGREE_HUMAN_IMAGE_VIEW_BY_INCREASE) {
                sortImageView.rotationY = ROTATE_DEGREE_HUMAN_IMAGE_VIEW_BY_DECREASE
                sortByIncrease = false
            } else {
                sortImageView.rotationY = ROTATE_DEGREE_HUMAN_IMAGE_VIEW_BY_INCREASE
                sortByIncrease = true
            }
        }

        var humansFilter: Filter = vm.humanFilter.value!!

        when (vm.humanFilter.value!!) {
            Filter.ALL -> bottomSheetDialogFilter.findViewById<RadioButton>(R.id.showAllRadioButton)!!.isChecked =
                true

            Filter.NEGATIVE -> bottomSheetDialogFilter.findViewById<RadioButton>(R.id.showNegativeRadioButton)!!.isChecked =
                true

            Filter.POSITIVE -> bottomSheetDialogFilter.findViewById<RadioButton>(R.id.showPositiveRadioButton)!!.isChecked =
                true
        }

        //TODO MIGRATE DB TO MAKE THIS VISIBLE
        bottomSheetDialogFilter.findViewById<RadioButton>(R.id.orderDateRadioButton)!!.visibility = View.GONE

        bottomSheetDialogFilter.findViewById<Button>(R.id.confirmButton)!!.setOnClickListener {
            when (bottomSheetDialogFilter.findViewById<RadioGroup>(R.id.filterRadioGroup)!!.checkedRadioButtonId) {
                R.id.showAllRadioButton -> {
                    humansFilter = Filter.ALL
                }

                R.id.showPositiveRadioButton -> {
                    humansFilter = Filter.POSITIVE
                }

                R.id.showNegativeRadioButton -> {
                    humansFilter = Filter.NEGATIVE
                }
            }

            when (bottomSheetDialogFilter.findViewById<RadioGroup>(R.id.orderRadioGroup)!!.checkedRadioButtonId) {
                R.id.orderCreationDateRadioButton -> {
                    sortHumansAttribute = HumanOrderAttribute.Date
                }

                R.id.orderSumRadioButton -> {
                    sortHumansAttribute = HumanOrderAttribute.Sum
                }
            }

            val order = Pair(sortHumansAttribute, sortByIncrease)
            vm.onSetHumanSort(filter = humansFilter, order = order)

            if (bottomSheetDialogFilter.findViewById<CheckBox>(R.id.rememberChoiceCheckBox)!!.isChecked)
                vm.saveHumanOrder(order = order)

            bottomSheetDialogFilter.dismiss()
        }

        bottomSheetDialogFilter.findViewById<Button>(R.id.cancelButton)!!.setOnClickListener {
            bottomSheetDialogFilter.dismiss()
        }
        bottomSheetDialogFilter.setOnDismissListener {
            vm.onHumanSortDialogClose()
        }
        bottomSheetDialogFilter.setOnCancelListener {
            vm.onHumanSortDialogClose()
        }
        bottomSheetDialogFilter.show()
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
