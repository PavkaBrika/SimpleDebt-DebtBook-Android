package com.breckneck.debtbook.debt.details

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

internal const val EXCEL_MIME = "application/vnd.ms-excel"

internal fun Context.writeDebtWorkbook(name: String, write: (OutputStream) -> Unit): Uri {
    val filename = name.replace(Regex("""[\p{Cntrl}/\\:*?"<>|]"""), "_").take(64) + "_debts.xls"
    if (Build.VERSION.SDK_INT >= 29) {
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val metadata = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, EXCEL_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/DebtBookFiles")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(contentResolver.insert(files, metadata))
        try {
            requireNotNull(contentResolver.openOutputStream(uri)).use(write)
            metadata.clear()
            metadata.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(contentResolver.update(uri, metadata, null, null) == 1)
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
        return uri
    }
    val folder = File(requireNotNull(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)), "DebtBookFiles")
    check(folder.isDirectory || folder.mkdirs())
    val file = File.createTempFile(filename.removeSuffix(".xls") + "-", ".xls", folder)
    try {
        file.outputStream().use(write)
    } catch (error: Exception) {
        file.delete()
        throw error
    }
    return FileProvider.getUriForFile(this, "$packageName.provider", file)
}
