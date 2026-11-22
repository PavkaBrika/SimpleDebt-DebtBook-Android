package com.breckneck.debtbook.debt.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import dagger.hilt.android.AndroidEntryPoint
import com.breckneck.debtbook.core.bubbleScreen

@AndroidEntryPoint
class CreateDebtFragment : Fragment() {
    private val vm by viewModels<CreateDebtViewModel>()

    interface OnButtonClickListener{
        fun DebtDetailsNewHuman(idHuman: Int, currency: String, name: String)
        fun DebtDetailsExistHuman(idHuman: Int, currency: String, name: String)
        fun onBackButtonClick()
        fun onSetButtonClick()
        fun onAddDebt()
        fun onTickVibration()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(R.id.debtDueForm) { insets ->
            val host = requireActivity() as OnButtonClickListener
            CreateDebtScreen(vm, insets, requireArguments(), host::onBackButtonClick, host::onSetButtonClick) { id, currency, name ->
                setFragmentResult("mainFragmentKey", bundleOf("isListModified" to true, "currency" to currency))
                host.onAddDebt()
                if (findNavController().previousBackStackEntry?.destination?.id == R.id.debtDetailsFragment) {
                    findNavController().popBackStack()
                } else host.DebtDetailsExistHuman(id, currency, name)
            }
        }
}
