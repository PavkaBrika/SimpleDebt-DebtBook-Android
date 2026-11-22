package com.breckneck.debtbook.settings.synchronization

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.settings.openSettingsIntent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.settings.synchronization.util.DriveServiceHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SynchronizationFragment : Fragment() {
    interface SynchronizationInterface {
        fun onBackButtonClick()
    }

    private val vm by viewModels<SynchronizationViewModel>()
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportFile)
    }
    private val import = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::restoreFile)
    }
    private val signIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.let { data ->
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .addOnSuccessListener { account ->
                    connect(account)
                    vm.setIsAuthorized(true)
                }.addOnFailureListener { error ->
                    if ((error as? ApiException)?.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                        vm.failDriveConnection(error)
                    }
                }
        }
    }
    private val authorize = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) reconnect()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext()) == ConnectionResult.SUCCESS
        if (vm.isAuthorized.value == true && vm.driveServiceHelper.value == null) {
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            if (available && account != null) connect(account) else vm.setIsAuthorized(false)
        }
        return bubbleScreen(R.id.backupComposeView) { insets ->
            Surface(color = bubbleColors().background, contentColor = bubbleColors().ink) {
                BackupScreen(vm, insets, available,
                    onBack = { if (!vm.blocksExit) (requireActivity() as SynchronizationInterface).onBackButtonClick() },
                    onExport = { export.launch("SimpleDebt-backup.json") },
                    onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onSignIn = ::requestSignIn, onReconnect = ::reconnect, onSignOut = ::requestSignOut,
                    onPrivacy = { requireContext().openSettingsIntent(Intent(Intent.ACTION_VIEW, getString(R.string.privacy_policy_url).toUri())) },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val transferBack = object : OnBackPressedCallback(vm.blocksExit) {
            override fun handleOnBackPressed() {
                Toast.makeText(requireContext(), R.string.backup_wait, Toast.LENGTH_SHORT).show()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, transferBack)
        vm.isSynchronizing.observe(viewLifecycleOwner) { transferBack.isEnabled = vm.blocksExit }
        vm.isRestoring.observe(viewLifecycleOwner) { transferBack.isEnabled = vm.blocksExit }
        vm.driveBackups.observe(viewLifecycleOwner) { transferBack.isEnabled = vm.blocksExit }
        vm.operationSucceeded.observe(viewLifecycleOwner) { operation ->
            val message = when (operation) {
                null -> return@observe
                SynchronizationViewModel.CompletedOperation.FILE_SAVED -> R.string.backup_file_saved
                SynchronizationViewModel.CompletedOperation.DRIVE_SAVED -> R.string.backup_drive_saved
                SynchronizationViewModel.CompletedOperation.RESTORED -> {
                    val changed = bundleOf("isListModified" to true)
                    for (key in listOf("settingsFragmentKey", "mainFragmentKey", "goalsFragmentKey", "financeFragmentKey")) {
                        setFragmentResult(key, changed)
                    }
                    R.string.backup_records_restored
                }
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            vm.operationSucceeded.value = null
        }
        vm.operationError.observe(viewLifecycleOwner) { error ->
            if (error is UserRecoverableAuthIOException) {
                vm.dismissOperationError()
                authorize.launch(error.intent)
            }
        }
    }

    private fun requestSignIn() {
        vm.dismissOperationError()
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestProfile().requestScopes(Scope(DriveScopes.DRIVE_APPDATA)).build()
        signIn.launch(GoogleSignIn.getClient(requireActivity(), options).signInIntent)
    }

    private fun reconnect() {
        vm.dismissOperationError()
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account == null) requestSignIn() else connect(account)
    }

    private fun connect(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(requireContext(), listOf(DriveScopes.DRIVE_APPDATA))
        credential.selectedAccount = account.account
        vm.setUser(account.displayName, account.email)
        val drive = Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
            .setApplicationName(BuildConfig.APPLICATION_ID).build()
        val helper = DriveServiceHelper(drive)
        vm.setDriveServiceHelper(helper)
        helper.queryFiles(SynchronizationViewModel.BACKUP_FILE_NAME)
            .addOnSuccessListener { if (vm.driveServiceHelper.value === helper) vm.setFileId(it.files.firstOrNull()?.id) }
            .addOnFailureListener { if (vm.driveServiceHelper.value === helper) vm.failDriveConnection(it) }
    }

    private fun requestSignOut() {
        GoogleSignIn.getClient(requireActivity(), GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            .addOnSuccessListener { vm.setIsAuthorized(false) }
            .addOnFailureListener(vm::failDriveConnection)
    }
}
