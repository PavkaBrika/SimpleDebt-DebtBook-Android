package com.breckneck.debtbook.settings.synchronization

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.google.android.gms.common.api.Scope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList
import dagger.hilt.android.AndroidEntryPoint
import java.util.Collections

@AndroidEntryPoint
class SynchronizationFragment : Fragment() {

    interface SynchronizationInterface {
        fun onBackButtonClick()
    }

    private val TAG = "Sync fragment"

    private val vm by viewModels<SynchronizationViewModel>()
    private val fileName = SynchronizationViewModel.BACKUP_FILE_NAME
    private var synchronizationInterface: SynchronizationInterface? = null
    private lateinit var synchronizationProgressBar: ProgressBar
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    private var restoreDialog: BottomSheetDialog? = null
    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportFile)
    }
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::restoreFile)
    }
    private lateinit var driveAuthLauncher: ActivityResultLauncher<Intent>


    override fun onAttach(context: Context) {
        super.onAttach(context)
        synchronizationInterface = context as SynchronizationInterface
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (data == null) {
                synchronizationProgressBar.visibility = View.GONE
                return@registerForActivityResult
            }
            handleSignInResult(data)
        }
        driveAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
                if (googleAccount != null) onSuccessSignIn(googleAccount)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_synchronization, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backButton: ImageView = view.findViewById(R.id.backButton)
        backButton.setOnClickListener {
            synchronizationInterface!!.onBackButtonClick()
        }
        val setSettingsButton: FloatingActionButton = view.findViewById(R.id.setFinanceButton)
        setSettingsButton.setOnClickListener {
            synchronizationInterface!!.onBackButtonClick()
        }

        if (vm.isLogOutDialogOpened.value == true)
            openLogOutDialog()

        view.findViewById<Button>(R.id.exportBackupFileButton).setOnClickListener {
            exportFileLauncher.launch("SimpleDebt-backup.json")
        }
        view.findViewById<Button>(R.id.importBackupFileButton).setOnClickListener {
            importFileLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        view.findViewById<Button>(R.id.previousLocalBackupButton).setOnClickListener { vm.restorePreviousLocalBackup() }
        view.findViewById<Button>(R.id.previousCloudBackupButton).setOnClickListener { vm.restorePreviousCloudBackup() }
        vm.restorePreview.observe(viewLifecycleOwner) { preview ->
            if (preview != null && restoreDialog?.isShowing != true) openRestoreDataDialog(preview)
        }
        vm.operationSucceeded.observe(viewLifecycleOwner) { succeeded ->
            if (succeeded) {
                Toast.makeText(requireContext(), R.string.backup_operation_complete, Toast.LENGTH_LONG).show()
                vm.operationSucceeded.value = false
            }
        }
        val authorizationLayout: LinearLayout = view.findViewById(R.id.authorizationLayout)
        val accountLayout: LinearLayout = view.findViewById(R.id.accountLayout)
        vm.isAuthorized.observe(viewLifecycleOwner) { isAuthorized ->
            if (isAuthorized) {
                authorizationLayout.visibility = View.GONE
                accountLayout.visibility = View.VISIBLE
            } else {
                authorizationLayout.visibility = View.VISIBLE
                accountLayout.visibility = View.GONE
            }
        }
        if ((vm.isAuthorized.value == true) && (vm.driveServiceHelper.value == null)) {
            try {
                val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
                onSuccessSignIn(googleAccount = googleAccount!!)
            } catch (e: Exception) {
                vm.setIsAuthorized(false)
            }
        }

        val synchronizationStatusTextView: TextView = view.findViewById(R.id.synchronizationStatusTextView)
        vm.lastSyncDate.observe(viewLifecycleOwner) { date ->
            if (date == "")
                synchronizationStatusTextView.text = getString(R.string.the_data_has_not_yet_been_synchronized)
            else
                synchronizationStatusTextView.text = "${getString(R.string.last_synchronization)} $date"
        }

        val userNameTextView: TextView = view.findViewById(R.id.userNameTextView)
        vm.userName.observe(viewLifecycleOwner) { userName ->
            userNameTextView.text = userName
        }

        val userEmailAddressTextView: TextView = view.findViewById(R.id.userEmailAddressTextView)
        vm.emailAddress.observe(viewLifecycleOwner) { address ->
            userEmailAddressTextView.text = address
        }

        val googleButtonCardView: CardView = view.findViewById(R.id.googleButtonCardView)
        val driveAvailable = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(requireContext()) == com.google.android.gms.common.ConnectionResult.SUCCESS
        if (!driveAvailable) {
            googleButtonCardView.visibility = View.GONE
            view.findViewById<TextView>(R.id.driveHintTextView).setText(R.string.backup_drive_unavailable)
            vm.setIsAuthorized(false)
        }
        synchronizationProgressBar = view.findViewById(R.id.synchronizationProgressBar)
        googleButtonCardView.setOnClickListener {
            requestGoogleSignIn()
            synchronizationProgressBar.visibility = View.VISIBLE
        }

        val synchronizeButtonLayout: ConstraintLayout = view.findViewById(R.id.synchronizeButtonLayout)
        synchronizeButtonLayout.setOnClickListener {
            vm.uploadBackup()
        }

        val restoreButtonLayout: ConstraintLayout = view.findViewById(R.id.restoreButtonLayout)
        restoreButtonLayout.setOnClickListener {
            vm.restoreBackup()
        }

        val restoreTextView: TextView = view.findViewById(R.id.restoreTextView)
        val synchronizeTextView: TextView = view.findViewById(R.id.synchronizeTextView)
        fun updateTransferControls() {
            val uploading = vm.isSynchronizing.value == true
            val restoring = vm.isRestoring.value == true
            val ready = vm.fileId.value != null && vm.driveServiceHelper.value != null
            val enabled = ready && !uploading && !restoring
            synchronizeTextView.setText(if (uploading) R.string.loading else R.string.synchronize)
            restoreTextView.setText(if (restoring) R.string.loading else R.string.restore_data)
            synchronizeButtonLayout.isEnabled = enabled
            restoreButtonLayout.isEnabled = enabled
            view.findViewById<Button>(R.id.exportBackupFileButton).isEnabled = !uploading && !restoring
            view.findViewById<Button>(R.id.importBackupFileButton).isEnabled = !uploading && !restoring
            view.findViewById<Button>(R.id.previousCloudBackupButton).isEnabled = enabled
            view.findViewById<Button>(R.id.previousLocalBackupButton).isEnabled = !uploading && !restoring && vm.hasPrevious.value == true
        }
        vm.hasPrevious.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.fileId.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.driveServiceHelper.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.isSynchronizing.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.isRestoring.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.operationError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                vm.dismissOperationError()
                if (error is UserRecoverableAuthIOException) {
                    driveAuthLauncher.launch(error.intent)
                } else {
                    Toast.makeText(requireContext(), R.string.backup_transfer_failed, Toast.LENGTH_LONG).show()
                }
            }
        }

        val googleLogOutButtonLayout: ConstraintLayout =
            view.findViewById(R.id.googleLogoutButtonLayout)
        fun updateLogOutControl() {
            googleLogOutButtonLayout.isEnabled = vm.isSynchronizing.value != true && vm.isRestoring.value != true
        }
        vm.isSynchronizing.observe(viewLifecycleOwner) { updateLogOutControl() }
        vm.isRestoring.observe(viewLifecycleOwner) { updateLogOutControl() }
        googleLogOutButtonLayout.setOnClickListener {
            openLogOutDialog()
        }

        val privacyAgreementTextView = view.findViewById<TextView>(R.id.privacyAgreementTextView)
        setPrivacyPolicyText(privacyAgreementTextView)
    }

    private fun setPrivacyPolicyText(privacyAgreementTextView: TextView) {
        val privacyPolicy = getString(R.string.privacy_policy_in_agreement)
        val completeString = getString(R.string.by_authorizing_you_agree_to_the, privacyPolicy)
        val startIndex = completeString.indexOf(privacyPolicy)
        val endIndex = completeString.length
        val spannableStringBuilder = SpannableStringBuilder(completeString)
        val clickOnPrivacy = object : ClickableSpan() {
            override fun onClick(p0: View) {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://simpledebtbook-privacy-policy.ucoz.net/")
                )
                startActivity(browserIntent)
            }
        }
        spannableStringBuilder.setSpan(clickOnPrivacy, startIndex, endIndex, 0)
        privacyAgreementTextView.text = spannableStringBuilder
        privacyAgreementTextView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun requestGoogleSignIn() {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        val client = GoogleSignIn.getClient(requireActivity(), signInOptions)
        signInLauncher.launch(client.signInIntent)
    }

    private fun handleSignInResult(result: Intent) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
            .addOnSuccessListener { googleAccount ->
                onSuccessSignIn(googleAccount = googleAccount)
                vm.setIsAuthorized(isAuthorized = true)
                setFragmentResult("settingsFragmentKey", bundleOf("isAuthorized" to true))
            }
            .addOnFailureListener {
                Log.e(TAG, "Sign-in failed", it)
                Toast.makeText(requireActivity(), getString(R.string.something_went_wrong), Toast.LENGTH_SHORT)
                    .show()
            }
            .addOnCompleteListener {
                synchronizationProgressBar.visibility = View.GONE
            }
    }

    private fun onSuccessSignIn(googleAccount: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            requireActivity(), Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = googleAccount.account
        vm.setUser(name = googleAccount.displayName, email = googleAccount.email)
        val driveService =
            Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
                .setApplicationName(BuildConfig.APPLICATION_ID)
                .build()

        vm.setDriveServiceHelper(DriveServiceHelper(driveService))
        vm.driveServiceHelper.value!!.queryFiles(fileName)
            .addOnSuccessListener { findOrCreateFile(it) }
            .addOnFailureListener {
                if (it is UserRecoverableAuthIOException) {
                    driveAuthLauncher.launch(it.intent)
                } else {
                    Log.e(TAG, "Failed to query Drive files", it)
                    Toast.makeText(requireActivity(), getString(R.string.something_went_wrong), Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun requestGoogleLogOut() {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .build()
        val client = GoogleSignIn.getClient(requireActivity(), signInOptions)

        client.signOut().addOnCompleteListener {
            vm.setIsAuthorized(isAuthorized = false)

        }
    }

    private fun findOrCreateFile(it: FileList) {
        if (it.files.size == 0) {
            createNewFile()
        } else {
            vm.setFileId(it.files.first().id)
        }
    }

    private fun createNewFile() {
        vm.driveServiceHelper.value!!.createFile(fileName)
            .addOnSuccessListener {
                vm.setFileId(it)
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to create file on Drive", it)
                Toast.makeText(
                    requireActivity(),
                    getString(R.string.something_went_wrong),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun openRestoreDataDialog(preview: SynchronizationViewModel.RestorePreview) {
        val ownerActivity = requireActivity()
        val bottomSheetDialog = BottomSheetDialog(ownerActivity)
        bottomSheetDialog.setContentView(R.layout.dialog_restore_backup)

        restoreDialog = bottomSheetDialog
        val date = preview.createdAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) }
            ?: getString(R.string.backup_unknown_date)
        val details = getString(R.string.backup_preview, date, preview.records, preview.photoCount, preview.financeCurrencies)
        val legacy = if (preview.legacy) "\n\n" + getString(R.string.backup_legacy_warning, java.util.TimeZone.getDefault().id, preview.legacyFinanceCurrency) else ""
        bottomSheetDialog.findViewById<TextView>(R.id.dialogMessage)!!.text =
            details + legacy + "\n\n" + getString(R.string.recover_all_data_from_the_cloud_this_will_delete_the_current_application_data)

        bottomSheetDialog.findViewById<Button>(R.id.okButton)!!.setOnClickListener {
            vm.confirmRestore()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.findViewById<Button>(R.id.cancelButton)!!.setOnClickListener {
            vm.dismissRestorePreview()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setOnCancelListener {
            if (!ownerActivity.isChangingConfigurations) vm.dismissRestorePreview()
        }

        bottomSheetDialog.show()
        bottomSheetDialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    private fun openLogOutDialog() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(R.layout.dialog_are_you_sure)

        bottomSheetDialog.findViewById<TextView>(R.id.dialogMessage)!!.text =
            getString(R.string.do_you_really_want_to_log_out)

        bottomSheetDialog.findViewById<Button>(R.id.okButton)!!.setOnClickListener {
            requestGoogleLogOut()
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.findViewById<Button>(R.id.cancelButton)!!.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setOnDismissListener {
            vm.setIsLogOutDialogOpened(false)
        }

        bottomSheetDialog.setOnCancelListener {
            vm.setIsLogOutDialogOpened(false)
        }

        vm.setIsLogOutDialogOpened(true)
        bottomSheetDialog.show()
    }

    override fun onDestroyView() {
        restoreDialog?.setOnDismissListener(null)
        restoreDialog?.setOnCancelListener(null)
        restoreDialog?.dismiss()
        restoreDialog = null
        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()
        setFragmentResult("settingsFragmentKey", bundleOf("isAuthorized" to vm.isAuthorized.value, "isListModified" to vm.isListModified.value))
    }

}