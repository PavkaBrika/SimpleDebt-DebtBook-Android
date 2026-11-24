package com.breckneck.debtbook.debt.create

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.DebtSystemPicker
import com.breckneck.debtbook.core.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.currency.onCurrencySelected
import com.breckneck.debtbook.core.currency.openCurrencyPicker

@AndroidEntryPoint
class CreateDebtFragment : Fragment() {
    private val vm by viewModels<CreateDebtViewModel>()
    private val contactContract = ActivityResultContracts.PickContact()
    private val pickContact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data?.takeIf {
            result.resultCode == Activity.RESULT_OK && it.scheme == ContentResolver.SCHEME_CONTENT &&
                it.authority == ContactsContract.AUTHORITY
        }
        val name = try {
            uri?.let {
                requireContext().contentResolver.query(it, arrayOf(Contacts.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), R.string.no_permission, Toast.LENGTH_SHORT).show()
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        (activity as? MainActivity)?.completeDebtPicker(DebtSystemPicker.CONTACT, !name.isNullOrBlank())
        if (!name.isNullOrBlank()) vm.pickedContact.value = name
    }

    private fun openContactPicker() {
        val host = requireActivity() as MainActivity
        try {
            pickContact.launch(host.prepareDebtPickerIntent(DebtSystemPicker.CONTACT, contactContract.createIntent(host, null)))
        } catch (_: ActivityNotFoundException) {
            host.completeDebtPicker(DebtSystemPicker.CONTACT, false)
            Toast.makeText(host, R.string.no_permission, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            host.completeDebtPicker(DebtSystemPicker.CONTACT, false)
            Toast.makeText(host, R.string.no_permission, Toast.LENGTH_SHORT).show()
        }
    }

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
            CreateDebtScreen(vm, insets, requireArguments(), host::onBackButtonClick, host::onSetButtonClick,
                onContactClick = ::openContactPicker,
                onCurrencyClick = { openCurrencyPicker(R.id.newDebtFragment, it) }) { id, currency, name ->
                setFragmentResult("mainFragmentKey", bundleOf("isListModified" to true, "currency" to currency))
                host.onAddDebt()
                if (findNavController().previousBackStackEntry?.destination?.id == R.id.debtDetailsFragment) {
                    findNavController().popBackStack()
                } else host.DebtDetailsExistHuman(id, currency, name)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        onCurrencySelected(R.id.newDebtFragment) { vm.currency.value = it }
    }
}
