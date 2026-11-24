package com.breckneck.debtbook.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import com.breckneck.debtbook.auth.screen.PinEntryScreen
import com.breckneck.debtbook.core.MainViewModel
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.deptbook.domain.util.PINCodeAction
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PinManagementFragment : Fragment() {
    private val vm by viewModels<AuthorizationViewModel>()
    private val main by activityViewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.setPINCodeAction(allowedPINAction(
            requested = requireArguments().getString(ACTION_ARGUMENT),
            pinEnabled = vm.isPINCodeEnabled.value == true,
            trustedCaller = true,
        ))
        if (vm.pinCodeAction.value != PINCodeAction.CHECK) vm.loadStoredPIN(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        bubbleScreen(R.id.pinManagementComposeView) { insets ->
            val title = when (vm.pinCodeAction.value) {
                PINCodeAction.ENABLE -> R.string.enable_pin
                PINCodeAction.CHANGE -> R.string.change_pin_code
                PINCodeAction.DISABLE -> R.string.disable_pin
                PINCodeAction.CHECK -> return@bubbleScreen
            }
            PinEntryScreen(
                vm = vm,
                hostInsets = insets,
                title = stringResource(title),
                onBack = ::goBack,
                onAuthenticated = {
                    main.getIsPINCodeEnabled()
                    goBack()
                },
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.addFlags(FLAG_SECURE)
    }

    override fun onDestroyView() {
        // Keep the outgoing PIN view secure until its exit animation has finished.
        if (findNavController().currentDestination?.id != R.id.pinManagementFragment) {
            requireActivity().window.clearFlags(FLAG_SECURE)
        }
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        // Settings may have completed before Android restored an older navigation state.
        if (vm.pinCodeAction.value == PINCodeAction.CHECK) goBack()
    }

    private fun goBack() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.pinManagementFragment) nav.popBackStack()
    }

    internal companion object {
        const val ACTION_ARGUMENT = "pinAction"
    }
}
