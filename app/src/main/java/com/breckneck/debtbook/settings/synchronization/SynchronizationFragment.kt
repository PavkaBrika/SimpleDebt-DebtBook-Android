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
import android.widget.FrameLayout
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
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
    private var restoreContent: FrameLayout? = null
    private var driveCopiesPage: View? = null
    private var restorePreviewPage: View? = null
    private var renderedDriveCopies: List<SynchronizationViewModel.DriveBackup>? = null
    private var saveDriveSheet: BottomSheetDialog? = null
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
        if (vm.isLogOutDialogOpened.value == true)
            openLogOutDialog()

        view.findViewById<View>(R.id.exportBackupFileButton).setOnClickListener {
            exportFileLauncher.launch("SimpleDebt-backup.json")
        }
        view.findViewById<View>(R.id.importBackupFileButton).setOnClickListener {
            importFileLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        view.findViewById<View>(R.id.previousLocalBackupButton).setOnClickListener { vm.restorePreviousLocalBackup() }
        vm.driveBackups.observe(viewLifecycleOwner) { renderRestoreSheet() }
        vm.restorePreview.observe(viewLifecycleOwner) { renderRestoreSheet() }
        vm.operationSucceeded.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                val message = when (result) {
                    SynchronizationViewModel.CompletedOperation.FILE_SAVED -> R.string.backup_file_saved
                    SynchronizationViewModel.CompletedOperation.DRIVE_SAVED -> R.string.backup_drive_saved
                    SynchronizationViewModel.CompletedOperation.RESTORED -> R.string.backup_records_restored
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                vm.operationSucceeded.value = null
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
            if (vm.driveConnectionFailed.value == true) {
                val account = GoogleSignIn.getLastSignedInAccount(requireContext())
                if (account != null) onSuccessSignIn(account) else requestGoogleSignIn()
            } else openSaveDriveSheet()
        }

        val restoreButtonLayout: ConstraintLayout = view.findViewById(R.id.restoreButtonLayout)
        restoreButtonLayout.setOnClickListener {
            vm.loadDriveBackups()
        }

        val restoreTextView: TextView = view.findViewById(R.id.restoreTextView)
        val synchronizeTextView: TextView = view.findViewById(R.id.synchronizeTextView)
        val transferBack = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                Toast.makeText(requireContext(), R.string.backup_wait, Toast.LENGTH_SHORT).show()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, transferBack)
        fun updateTransferControls() {
            val uploading = vm.isSynchronizing.value == true
            val restoring = vm.isRestoring.value == true
            val busy = uploading || restoring
            val ready = vm.driveReady.value == true && vm.driveServiceHelper.value != null
            val choosing = vm.restorePreview.value != null || vm.driveBackups.value != null
            val enabled = ready && !busy && !choosing
            val connectionFailed = vm.driveConnectionFailed.value == true
            synchronizeTextView.setText(if (connectionFailed) R.string.backup_drive_retry else R.string.synchronize)
            synchronizationStatusTextView.setText(when {
                connectionFailed -> R.string.backup_drive_connection_failed
                !ready -> R.string.backup_drive_connecting
                else -> R.string.backup_drive_save_hint
            })
            restoreTextView.setText(R.string.restore_data)
            synchronizeButtonLayout.isEnabled = !busy && !choosing && (ready || connectionFailed)
            restoreButtonLayout.isEnabled = enabled
            view.findViewById<View>(R.id.exportBackupFileButton).isEnabled = !busy && !choosing
            view.findViewById<View>(R.id.importBackupFileButton).isEnabled = !busy && !choosing
            view.findViewById<View>(R.id.previousLocalBackupButton).apply {
                isEnabled = !busy && !choosing
            }
            view.findViewById<View>(R.id.previousLocalBackupCard).visibility =
                if (vm.hasPrevious.value == true) View.VISIBLE else View.GONE
            backButton.isEnabled = !busy
            googleButtonCardView.isEnabled = !busy && !choosing
            synchronizationProgressBar.visibility = if (busy && vm.driveBackups.value == null) View.VISIBLE else View.GONE
            transferBack.isEnabled = busy && vm.driveBackups.value == null
            renderDriveCopies()
        }
        vm.hasPrevious.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.driveConnectionFailed.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.driveReady.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.driveServiceHelper.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.isSynchronizing.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.isRestoring.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.restorePreview.observe(viewLifecycleOwner) { updateTransferControls() }
        vm.driveBackups.observe(viewLifecycleOwner) { updateTransferControls() }
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
            .addOnSuccessListener { vm.setFileId(it.files.firstOrNull()?.id) }
            .addOnFailureListener { vm.failDriveConnection(it) }
    }

    private fun requestGoogleLogOut() {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .build()
        val client = GoogleSignIn.getClient(requireActivity(), signInOptions)

        client.signOut().addOnCompleteListener {
            vm.dismissDriveBackups()
            vm.setIsAuthorized(isAuthorized = false)

        }
    }

    private fun renderRestoreSheet() {
        val preview = vm.restorePreview.value
        when {
            preview != null -> openRestoreDataDialog(preview)
            vm.driveBackups.value != null -> {
                openDriveCopiesSheet()
                renderDriveCopies()
            }
            else -> dismissRestoreSheet()
        }
    }

    /** One sheet, with list and details pages. Detached list views keep their scroll position. */
    private fun showRestorePage(page: View) {
        val sheet = restoreDialog ?: BottomSheetDialog(requireContext()).also { dialog ->
            val ownerActivity = requireActivity()
            restoreContent = FrameLayout(requireContext())
            dialog.setContentView(restoreContent!!)
            dialog.setOnCancelListener {
                if (!ownerActivity.isChangingConfigurations) vm.backFromRestore()
            }
            restoreDialog = dialog
        }
        val container = requireNotNull(restoreContent)
        if (container.getChildAt(0) !== page) {
            container.removeAllViews()
            container.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        sheet.show()
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    private fun openRestoreDataDialog(preview: SynchronizationViewModel.RestorePreview) {
        val page = restorePreviewPage ?: layoutInflater.inflate(R.layout.dialog_restore_backup, null).also { restorePreviewPage = it }
        page.findViewById<TextView>(R.id.dialogTitle).setText(
            if (preview.source == SynchronizationViewModel.RestoreSource.BEFORE_RESTORE)
                R.string.backup_restore_previous_local else R.string.backup_confirm_restore_title)
        val source = preview.fileName ?: getString(sourceTitle(preview.source))
        val legacy = if (preview.legacy) "\n\n" + getString(R.string.backup_legacy_warning,
            java.util.TimeZone.getDefault().displayName, preview.legacyFinanceCurrency) else ""
        page.findViewById<TextView>(R.id.dialogMessage).text =
            source + "\n\n" + previewDetails(preview) + legacy + "\n\n" +
                getString(R.string.recover_all_data_from_the_cloud_this_will_delete_the_current_application_data)
        page.findViewById<Button>(R.id.okButton).setOnClickListener { vm.confirmRestore() }
        page.findViewById<Button>(R.id.cancelButton).apply {
            setText(if (vm.driveBackups.value != null) R.string.backup_back_to_copies else R.string.cancel)
            setOnClickListener { vm.backFromRestore() }
        }
        showRestorePage(page)
    }

    private fun sourceTitle(source: SynchronizationViewModel.RestoreSource): Int = when (source) {
        SynchronizationViewModel.RestoreSource.FILE -> R.string.backup_import_file
        SynchronizationViewModel.RestoreSource.DRIVE_LATEST -> R.string.backup_drive_latest
        SynchronizationViewModel.RestoreSource.DRIVE_PREVIOUS -> R.string.backup_drive_previous
        SynchronizationViewModel.RestoreSource.BEFORE_RESTORE -> R.string.backup_before_restore
    }

    private fun previewDetails(preview: SynchronizationViewModel.RestorePreview): String {
        val date = preview.createdAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) }
            ?: getString(R.string.backup_unknown_date)
        return getString(R.string.backup_preview, date, preview.records, preview.photoCount)
    }

    private fun openSaveDriveSheet() {
        if (saveDriveSheet?.isShowing == true) return
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(R.layout.dialog_restore_backup)
        sheet.findViewById<TextView>(R.id.dialogTitle)!!.setText(R.string.synchronize)
        sheet.findViewById<TextView>(R.id.dialogMessage)!!.text = getString(R.string.backup_drive_save_confirm, vm.emailAddress.value.orEmpty())
        sheet.findViewById<Button>(R.id.okButton)!!.apply {
            setText(R.string.synchronize)
            setOnClickListener { sheet.dismiss(); vm.uploadBackup() }
        }
        sheet.findViewById<Button>(R.id.cancelButton)!!.setOnClickListener { sheet.dismiss() }
        saveDriveSheet = sheet
        sheet.show()
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    private fun openDriveCopiesSheet() {
        val page = driveCopiesPage ?: layoutInflater.inflate(R.layout.dialog_drive_backups, null).also {
            it.findViewById<Button>(R.id.cancelButton).setOnClickListener { vm.dismissDriveBackups() }
            driveCopiesPage = it
        }
        showRestorePage(page)
    }

    private fun renderDriveCopies() {
        val page = driveCopiesPage ?: return
        val copies = vm.driveBackups.value ?: return
        val loading = vm.isRestoring.value == true
        page.findViewById<ProgressBar>(R.id.cloudCopiesProgress).visibility = if (loading) View.VISIBLE else View.GONE
        page.findViewById<TextView>(R.id.cloudCopiesEmpty).visibility = if (!loading && copies.isEmpty()) View.VISIBLE else View.GONE
        val list = page.findViewById<LinearLayout>(R.id.cloudBackupList)
        if (renderedDriveCopies !== copies) {
            list.removeAllViews()
            copies.forEach { copy ->
                val row = layoutInflater.inflate(R.layout.item_backup_choice, list, false)
                row.findViewById<TextView>(R.id.copyTitle).setText(sourceTitle(copy.source))
                row.findViewById<TextView>(R.id.copyDetails).text = copy.preview?.let(::previewDetails)
                    ?: getString(R.string.backup_copy_unreadable)
                row.alpha = if (copy.preview != null) 1f else 0.6f
                row.setOnClickListener { vm.chooseDriveBackup(copy) }
                list.addView(row)
            }
            renderedDriveCopies = copies
        }
        copies.forEachIndexed { index, copy -> list.getChildAt(index).isEnabled = !loading && copy.preview != null }
    }

    private fun dismissRestoreSheet() {
        restoreDialog?.setOnCancelListener(null)
        restoreDialog?.dismiss()
        restoreDialog = null
        restoreContent = null
        driveCopiesPage = null
        restorePreviewPage = null
        renderedDriveCopies = null
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
        dismissRestoreSheet()
        saveDriveSheet?.dismiss()
        saveDriveSheet = null
        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()
        setFragmentResult("settingsFragmentKey", bundleOf("isAuthorized" to vm.isAuthorized.value, "isListModified" to vm.isListModified.value))
    }

}