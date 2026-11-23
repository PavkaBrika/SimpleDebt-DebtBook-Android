package com.breckneck.debtbook.core.currency

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen

private const val CALLER = "currencyCaller"
private const val CODE = "currencyCode"
private const val OPTIONS = "availableCurrencies"

private fun currencyResultKey(@IdRes caller: Int) = "currencyPicker:$caller"

internal fun Fragment.openCurrencyPicker(@IdRes caller: Int, selected: String, available: List<String>? = null) {
    val nav = findNavController()
    if (nav.currentDestination?.id != caller) return
    val args = Bundle().apply {
        putInt(CALLER, caller)
        putString(CODE, selected)
        available?.let { putStringArrayList(OPTIONS, ArrayList(it)) }
    }
    nav.navigate(R.id.action_openCurrencyPicker, args)
}

internal fun Fragment.onCurrencySelected(@IdRes caller: Int, onSelected: (String) -> Unit) {
    setFragmentResultListener(currencyResultKey(caller)) { _, result ->
        result.getString(CODE)?.let(onSelected)
    }
}

class CurrencyPickerFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(R.id.currencyPickerComposeView) { insets ->
            val args = requireArguments()
            CurrencyPickerScreen(
                title = stringResource(R.string.select_currency),
                selectedCode = args.getString(CODE).orEmpty(),
                availableCodes = args.getStringArrayList(OPTIONS),
                hostInsets = insets,
                onBack = { close() },
                onCurrencySelected = { close(it) },
            )
        }

    private fun close(code: String? = null) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.currencyPickerFragment) return
        if (code != null) {
            val caller = requireArguments().getInt(CALLER)
            setFragmentResult(currencyResultKey(caller), Bundle().apply { putString(CODE, code) })
        }
        nav.popBackStack()
    }
}
