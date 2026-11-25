package com.breckneck.debtbook.settings.synchronization

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.net.toUri
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.navigation.LocalRouteActive
import com.breckneck.debtbook.core.navigation.RouteBackHandler
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.openSettingsIntent
import com.breckneck.debtbook.settings.synchronization.util.DriveServiceHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun BackupRoute(
    vm: SynchronizationViewModel,
    insets: PaddingValues,
    activity: MainActivity,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = remember { BackupFeedback(activity, snackbar, scope) }
    val driveAvailable = remember(activity) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportFile)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::restoreFile)
    }
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data == null) vm.finishDriveSignIn()
        else GoogleSignIn.getSignedInAccountFromIntent(data).addOnCompleteListener { task ->
            try {
                if (task.isSuccessful) {
                    vm.connectDrive(activity, task.result)
                    vm.setIsAuthorized(true)
                } else {
                    val error = task.exception
                    if (error != null && (error as? ApiException)?.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                        vm.failDriveConnection(error)
                    }
                }
            } finally {
                vm.finishDriveSignIn()
            }
        }
    }
    fun requestSignIn() {
        if (!vm.startDriveSignIn()) return
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestProfile().requestScopes(Scope(DriveScopes.DRIVE_APPDATA)).build()
        try {
            signIn.launch(GoogleSignIn.getClient(activity, options).signInIntent)
        } catch (error: ActivityNotFoundException) {
            vm.finishDriveSignIn()
            vm.failDriveConnection(error)
        }
    }
    fun reconnect() {
        vm.dismissOperationError()
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        if (account == null) requestSignIn() else vm.connectDrive(activity, account)
    }
    val authorize = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) reconnect()
    }

    LaunchedEffect(vm) {
        if (vm.isAuthorized.value == true && vm.driveServiceHelper.value == null && vm.driveSignInPending.value != true) {
            val account = GoogleSignIn.getLastSignedInAccount(activity)
            if (driveAvailable && account != null) vm.connectDrive(activity, account) else vm.setIsAuthorized(false)
        }
    }

    val saving by vm.savingTo.observeAsState()
    val restoring by vm.isRestoring.observeAsState(false)
    val backups by vm.driveBackups.observeAsState()
    RouteBackHandler(enabled = (saving != null || restoring) && backups == null) {
        feedback.show(R.string.backup_wait)
    }
    val operation by vm.operationSucceeded.observeAsState()
    val error by vm.operationError.observeAsState()
    val active = LocalRouteActive.current
    LaunchedEffect(operation, active) {
        if (!active) return@LaunchedEffect
        val message = when (operation) {
            null -> return@LaunchedEffect
            SynchronizationViewModel.CompletedOperation.FILE_SAVED -> R.string.backup_file_saved
            SynchronizationViewModel.CompletedOperation.DRIVE_SAVED -> R.string.backup_drive_saved
            // Root destinations reload their repositories when they return to RESUMED.
            SynchronizationViewModel.CompletedOperation.RESTORED -> R.string.backup_records_restored
        }
        feedback.show(message)
        vm.operationSucceeded.value = null
    }
    LaunchedEffect(error, active) {
        if (active && error != null) {
            snackbar.currentSnackbarData
                ?.takeIf { it.visuals.message == activity.getString(R.string.backup_wait) }?.dismiss()
            val recoverable = error as? UserRecoverableAuthIOException
            if (recoverable != null) {
                vm.dismissOperationError()
                authorize.launch(recoverable.intent)
            }
        }
    }

    Surface(color = bubbleColors().background, contentColor = bubbleColors().ink) {
        BackupScreen(
            vm, insets, driveAvailable, snackbar,
            onBack = { if (!vm.blocksExit) onBack() },
            onExport = { export.launch("SimpleDebt-backup.json") },
            onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onSignIn = ::requestSignIn,
            onReconnect = ::reconnect,
            onSignOut = {
                GoogleSignIn.getClient(activity, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                    .addOnSuccessListener { vm.setIsAuthorized(false) }
                    .addOnFailureListener(vm::failDriveConnection)
            },
            onPrivacy = {
                activity.openSettingsIntent(Intent(Intent.ACTION_VIEW, activity.getString(R.string.privacy_policy_url).toUri())) {
                    feedback.show(R.string.settings_no_handler)
                }
            },
        )
    }
}

private class BackupFeedback(
    private val activity: Activity,
    private val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun show(@StringRes message: Int) {
        val text = activity.getString(message)
        if (snackbar.currentSnackbarData?.visuals?.message == text) return
        job?.cancel()
        job = scope.launch { snackbar.showSnackbar(text, withDismissAction = true) }
    }
}

private fun SynchronizationViewModel.connectDrive(activity: Activity, account: GoogleSignInAccount) {
    val credential = GoogleAccountCredential.usingOAuth2(activity.applicationContext, listOf(DriveScopes.DRIVE_APPDATA))
    credential.selectedAccount = account.account
    setUser(account.displayName, account.email)
    val drive = Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
        .setApplicationName(BuildConfig.APPLICATION_ID).build()
    val helper = DriveServiceHelper(drive)
    setDriveServiceHelper(helper)
    helper.queryFiles(SynchronizationViewModel.BACKUP_FILE_NAME)
        .addOnSuccessListener { if (driveServiceHelper.value === helper) setFileId(it.files.firstOrNull()?.id) }
        .addOnFailureListener { if (driveServiceHelper.value === helper) failDriveConnection(it) }
}
