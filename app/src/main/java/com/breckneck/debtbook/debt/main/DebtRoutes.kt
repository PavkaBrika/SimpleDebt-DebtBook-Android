package com.breckneck.debtbook.debt.main

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.DebtSystemPicker
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.core.navigation.CURRENCY_RESULT
import com.breckneck.debtbook.core.navigation.Currencies
import com.breckneck.debtbook.core.navigation.DEBT_LIST_CURRENCY
import com.breckneck.debtbook.core.navigation.DEBT_PERSON_RESULT
import com.breckneck.debtbook.core.navigation.DebtForm
import com.breckneck.debtbook.core.navigation.DebtPerson
import com.breckneck.debtbook.core.navigation.Debts
import com.breckneck.debtbook.core.navigation.EditDebtPerson
import com.breckneck.debtbook.core.navigation.destination
import com.breckneck.debtbook.debt.create.CreateDebtScreen
import com.breckneck.debtbook.debt.create.CreateDebtViewModel
import com.breckneck.debtbook.debt.details.DebtPersonRoute
import com.breckneck.debtbook.debt.details.EditDebtPersonScreen

internal fun NavGraphBuilder.debtDestinations(
    nav: NavHostController,
    insets: @Composable () -> PaddingValues,
    activity: MainActivity,
    main: MainViewModel,
) {
    destination<Debts>(nav) {
        val vm = hiltViewModel<DebtViewModel>(entry)
        Result<String>(CURRENCY_RESULT, vm::selectCurrencyFromPicker)
        WhileResumed {
            entry.savedStateHandle.remove<String>(DEBT_LIST_CURRENCY)?.let(vm::selectCurrency)
            vm.setListStateLoading()
        }
        DebtListScreen(
            vm = vm,
            hostInsets = insets(),
            visible = isResumed() && activity.canAccessApp(),
            onCurrencyClick = { code ->
                val available = (vm.overviewCurrencies.value.orEmpty() + code).filter(String::isNotBlank).distinct()
                open(Currencies(code, available))
            },
            onPerson = { id, currency, name ->
                if (isCurrent) main.onActionClick()
                open(DebtPerson(id, currency, name))
            },
            onAdd = {
                if (isCurrent) main.onActionClick()
                open(DebtForm())
            },
            onFilterLongClick = activity::onFilterButtonLongClick,
            onTick = activity::startTickVibration,
        )
    }

    destination<DebtForm>(nav) {
        val route = entry.toRoute<DebtForm>()
        val vm = hiltViewModel<CreateDebtViewModel, CreateDebtViewModel.Factory>(entry) { it.create(route) }
        Result<String>(CURRENCY_RESULT, vm::selectCurrency)
        val openContactPicker = rememberContactPicker(activity) { vm.pickedContact.value = it }
        CreateDebtScreen(
            vm, insets(), route, ::back, activity::startTickVibration,
            onContactClick = openContactPicker,
            onCurrencyClick = { open(Currencies(it)) },
        ) { id, currency, name, positive ->
            if (!isCurrent) return@CreateDebtScreen
            nav.getBackStackEntry<Debts>().savedStateHandle[DEBT_LIST_CURRENCY] = currency
            main.onAddDebt()
            if (nav.previousBackStackEntry?.destination?.hasRoute<DebtPerson>() == true) {
                returnResult(DEBT_PERSON_RESULT, if (route.idDebt <= 0) if (positive) 1 else -1 else 0)
            } else {
                open(DebtPerson(id, currency, name)) { popUpTo<Debts>() }
                main.onActionClick()
            }
        }
    }

    destination<DebtPerson>(nav, waitForContent = true) { DebtPersonRoute(insets(), activity, main) }

    destination<EditDebtPerson>(nav) {
        EditDebtPersonScreen(hiltViewModel(entry), insets(), ::back) {
            returnResult(DEBT_PERSON_RESULT, 0)
        }
    }
}

@Composable
private fun rememberContactPicker(host: MainActivity, onContact: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data?.takeIf {
            result.resultCode == Activity.RESULT_OK && it.scheme == ContentResolver.SCHEME_CONTENT &&
                it.authority == ContactsContract.AUTHORITY
        }
        val name = try {
            uri?.let {
                host.contentResolver.query(it, arrayOf(Contacts.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }
        } catch (_: SecurityException) {
            Toast.makeText(host, R.string.no_permission, Toast.LENGTH_SHORT).show()
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        host.completeDebtPicker(DebtSystemPicker.CONTACT, !name.isNullOrBlank())
        if (!name.isNullOrBlank()) onContact(name)
    }
    return {
        try {
            val intent = ActivityResultContracts.PickContact().createIntent(host, null)
            launcher.launch(host.prepareDebtPickerIntent(DebtSystemPicker.CONTACT, intent))
        } catch (_: ActivityNotFoundException) {
            host.completeDebtPicker(DebtSystemPicker.CONTACT, false)
            Toast.makeText(host, R.string.no_permission, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            host.completeDebtPicker(DebtSystemPicker.CONTACT, false)
            Toast.makeText(host, R.string.no_permission, Toast.LENGTH_SHORT).show()
        }
    }
}
