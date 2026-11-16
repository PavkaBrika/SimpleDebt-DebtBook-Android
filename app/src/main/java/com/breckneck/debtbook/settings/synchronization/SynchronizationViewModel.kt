package com.breckneck.debtbook.settings.synchronization

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.debtbook.settings.synchronization.util.BackupDrive
import com.breckneck.deptbook.domain.usecase.AppData.GetAllAppData
import com.breckneck.deptbook.domain.usecase.AppData.ReplaceAllAppData
import com.breckneck.deptbook.domain.usecase.Debt.SetDateUseCase
import com.breckneck.deptbook.domain.usecase.Settings.GetIsAuthorized
import com.breckneck.deptbook.domain.usecase.Settings.GetLastSyncDate
import com.breckneck.deptbook.domain.usecase.Settings.SetIsAuthorized
import com.breckneck.deptbook.domain.usecase.Settings.SetLastSyncDate
import com.breckneck.deptbook.domain.usecase.Settings.SetUserData
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import com.breckneck.deptbook.domain.backup.BackupCodec
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class SynchronizationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getFinanceCurrency: com.breckneck.deptbook.domain.usecase.Settings.GetFinanceCurrency,
    private val getIsAuthorized: GetIsAuthorized,
    private val setIsAuthorized: SetIsAuthorized,
    private val getAllAppData: GetAllAppData,
    private val replaceAllAppData: ReplaceAllAppData,
    private val setUserData: SetUserData,
    private val setDateUseCase: SetDateUseCase,
    private val setLastSyncDate: SetLastSyncDate,
    private val getLastSyncDate: GetLastSyncDate
) : ViewModel() {

    private val backupFiles = BackupFiles(context)
    private val backupTransfer = BackupTransfer(getAllAppData::execute, replaceAllAppData::execute, backupFiles::retainPrevious, backupFiles, getFinanceCurrency::execute)
    enum class RestoreSource { FILE, DRIVE_LATEST, DRIVE_PREVIOUS, BEFORE_RESTORE }
    enum class CompletedOperation { FILE_SAVED, DRIVE_SAVED, RESTORED }
    data class RestorePreview(val createdAt: Long?, val records: Int, val photoCount: Int,
        val legacy: Boolean, val legacyFinanceCurrency: String,
        val source: RestoreSource, val fileName: String? = null)
    data class DriveBackup(val source: RestoreSource, val content: String?, val preview: RestorePreview?)
    private var driveLoadJob: Job? = null
    val driveBackups = MutableLiveData<List<DriveBackup>?>(null)
    val driveReady = MutableLiveData(false)
    val driveConnectionFailed = MutableLiveData(false)
    private var pendingRestore: String? = null
    val restorePreview = MutableLiveData<RestorePreview?>(null)
    val hasPrevious = MutableLiveData(backupFiles.hasPrevious())
    val operationSucceeded = MutableLiveData<CompletedOperation?>(null)
    private val _operationError = MutableLiveData<Exception?>(null)
    val operationError: LiveData<Exception?> = _operationError

    private val _isAuthorized = MutableLiveData<Boolean>()
    val isAuthorized: LiveData<Boolean>
        get() = _isAuthorized
    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String>
        get() = _userName
    private val _emailAddress = MutableLiveData<String>()
    val emailAddress: LiveData<String>
        get() = _emailAddress
    private val _fileId = MutableLiveData<String?>(null)
    val fileId: LiveData<String?>
        get() = _fileId
    private var _driveServiceHelper = MutableLiveData<BackupDrive?>()
    val driveServiceHelper: LiveData<BackupDrive?>
        get() = _driveServiceHelper
    private val _isSynchronizing = MutableLiveData<Boolean>(false)
    val isSynchronizing: LiveData<Boolean>
        get() = _isSynchronizing
    private val _isRestoring = MutableLiveData<Boolean>(false)
    val isRestoring: LiveData<Boolean>
        get() = _isRestoring
    private val _isLogOutDialogOpened = MutableLiveData<Boolean>(false)
    val isLogOutDialogOpened: LiveData<Boolean>
        get() = _isLogOutDialogOpened
    private val _isListModified = MutableLiveData<Boolean>(false)
    val isListModified: LiveData<Boolean>
        get() = _isListModified
    private val _lastSyncDate = MutableLiveData<String>()
    val lastSyncDate: LiveData<String>
        get() = _lastSyncDate

    init {
        getIsAuthorized()
        getLastSyncDate()
    }

    fun setLastSyncDate() {
        val calendar = Calendar.getInstance()
        setLastSyncDate.execute(calendar.timeInMillis)
        val date = setDateUseCase.execute(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        _lastSyncDate.value = com.breckneck.deptbook.domain.util.LedgerDate.display(date)
    }

    private fun getLastSyncDate() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = getLastSyncDate.execute()
        if (calendar.timeInMillis == 0L) {
            _lastSyncDate.value = ""
        } else {
            val date = setDateUseCase.execute(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            _lastSyncDate.value = com.breckneck.deptbook.domain.util.LedgerDate.display(date)
        }
    }

    private fun getIsAuthorized() {
        _isAuthorized.value = getIsAuthorized.execute()
    }

    fun setIsAuthorized(isAuthorized: Boolean) {
        if (!isAuthorized) {
            // Downloaded copies belong to the open restore flow and survive reconnection.
            driveConnectionFailed.value = false
            driveReady.value = false
            _fileId.value = null
            _driveServiceHelper.value = null
        }
        _isAuthorized.value = isAuthorized
        setIsAuthorized.execute(isAuthorized)
    }

    fun setUser(name: String?, email: String?) {
        if (name == null) {
            _userName.value = ""
        } else {
            _userName.value = name!!
        }

        if (email == null) {
            _emailAddress.value = ""
        } else {
            _emailAddress.value = email!!
        }
        setUserData.execute(userName.value!!, emailAddress.value!!)
    }

    fun uploadBackup() {
        if (busy() || driveReady.value != true) return
        val helper = driveServiceHelper.value ?: return
        val existingId = fileId.value
        _isSynchronizing.value = true
        _operationError.value = null
        viewModelScope.launch {
            try {
                backupTransfer.upload { content ->
                    val old = existingId?.let { helper.readFile(it).awaitCompletion().second }.orEmpty()
                    if (old.isNotBlank()) {
                        BackupCodec().decode(old) // Refuse to overwrite an unknown/corrupt existing file.
                        val previousId = helper.queryFiles(PREVIOUS_FILE_NAME).awaitCompletion().files.firstOrNull()?.id
                            ?: helper.createFile(PREVIOUS_FILE_NAME).awaitCompletion()
                        helper.saveFile(previousId, PREVIOUS_FILE_NAME, old).awaitCompletion()
                    }
                    val id = existingId ?: helper.createFile(BACKUP_FILE_NAME).awaitCompletion().also { _fileId.value = it }
                    helper.saveFile(id, BACKUP_FILE_NAME, content).awaitCompletion()
                }
                setLastSyncDate()
                operationSucceeded.value = CompletedOperation.DRIVE_SAVED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _operationError.value = error
            } finally {
                _isSynchronizing.value = false
            }
        }
    }

    private fun transferring() = isSynchronizing.value == true || isRestoring.value == true
    private fun busy() = transferring() || restorePreview.value != null || driveBackups.value != null

    /** Read-only listing. Opening this sheet never replaces local records or a Drive file. */
    fun loadDriveBackups() {
        if (busy() || driveReady.value != true) return
        val helper = driveServiceHelper.value ?: return
        _isRestoring.value = true
        driveBackups.value = emptyList()
        _operationError.value = null
        driveLoadJob = viewModelScope.launch {
            try {
                val copies = mutableListOf<DriveBackup>()
                for ((name, source) in listOf(BACKUP_FILE_NAME to RestoreSource.DRIVE_LATEST,
                    PREVIOUS_FILE_NAME to RestoreSource.DRIVE_PREVIOUS)) {
                    val id = helper.queryFiles(name).awaitCompletion().files.firstOrNull()?.id ?: continue
                    val content = helper.readFile(id).awaitCompletion().second
                    // A sign-in in older versions may have created an empty placeholder.
                    if (content.isBlank()) continue
                    val preview = try { preview(content, source) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    copies += DriveBackup(source, content.takeIf { preview != null }, preview)
                }
                driveBackups.value = copies
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                dismissDriveBackups()
                _operationError.value = error
            } finally { _isRestoring.value = false }
        }
    }

    fun dismissDriveBackups() { driveLoadJob?.cancel(); driveBackups.value = null }

    fun chooseDriveBackup(backup: DriveBackup) {
        if (transferring() || driveBackups.value?.contains(backup) != true || backup.content == null) return
        // The list already validated these bytes. Keep it for back navigation, without another read.
        if (restorePreview.value != null) return
        pendingRestore = backup.content
        restorePreview.value = backup.preview
    }

    fun restoreFile(uri: Uri) = prepareRestore(RestoreSource.FILE, uri) {
        withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8).use {
                it.readTextBounded(BackupCodec.MAX_CHARACTERS)
            }
        }
    }

    fun restorePreviousLocalBackup() = prepareRestore(RestoreSource.BEFORE_RESTORE) {
        withContext(Dispatchers.IO) { backupFiles.readPrevious() }
    }

    private suspend fun preview(content: String, source: RestoreSource, fileName: String? = null): RestorePreview {
        val document = backupTransfer.preview(content)
        return RestorePreview(document.createdAt, document.recordCount, document.photos.size,
            document.legacy, getFinanceCurrency.execute(), source, fileName)
    }

    private fun prepareRestore(source: RestoreSource, uri: Uri? = null, read: suspend () -> String) {
        if (busy()) return
        _isRestoring.value = true
        _operationError.value = null
        viewModelScope.launch {
            try {
                val content = read()
                val name = uri?.let { withContext(Dispatchers.IO) {
                    if (it.scheme == "file") it.lastPathSegment else
                        runCatching {
                            context.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                        }.getOrNull()
                } }
                val document = preview(content, source, name)
                pendingRestore = content
                restorePreview.value = document
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _operationError.value = error }
            finally { _isRestoring.value = false }
        }
    }

    fun dismissRestorePreview() { pendingRestore = null; restorePreview.value = null }

    fun backFromRestore() {
        if (restorePreview.value != null) dismissRestorePreview() else dismissDriveBackups()
    }

    fun confirmRestore() {
        if (transferring()) return
        val content = pendingRestore ?: return
        dismissDriveBackups()
        dismissRestorePreview()
        _isRestoring.value = true
        _operationError.value = null
        viewModelScope.launch {
            try {
                backupTransfer.restore { content }
                _isListModified.value = true
                hasPrevious.value = backupFiles.hasPrevious()
                operationSucceeded.value = CompletedOperation.RESTORED
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _operationError.value = error }
            finally { _isRestoring.value = false }
        }
    }

    fun exportFile(uri: Uri) {
        if (busy()) return
        _isSynchronizing.value = true
        _operationError.value = null
        viewModelScope.launch {
            try {
                backupTransfer.upload { content ->
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                            .bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                    }
                }
                operationSucceeded.value = CompletedOperation.FILE_SAVED
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _operationError.value = error }
            finally { _isSynchronizing.value = false }
        }
    }

    fun dismissOperationError() {
        _operationError.value = null
    }

    fun setFileId(fileId: String?) {
        _fileId.value = fileId
        driveConnectionFailed.value = false
        driveReady.value = true
    }

    fun setDriveServiceHelper(helper: BackupDrive) {
        driveReady.value = false
        driveConnectionFailed.value = false
        _driveServiceHelper.value = helper
    }

    fun failDriveConnection(error: Exception) {
        driveConnectionFailed.value = true
        _operationError.value = error
    }

    fun setIsLogOutDialogOpened(opened: Boolean) {
        _isLogOutDialogOpened.value = opened
    }
    companion object {
        const val BACKUP_FILE_NAME = "DebtBookSync.json"
        const val PREVIOUS_FILE_NAME = "DebtBookBackup.previous.json"
    }
}

private suspend fun <T> Task<T>.awaitCompletion(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(Executor { it.run() }) { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        when {
            task.isCanceled -> continuation.cancel()
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Drive operation failed"))
        }
    }
}
