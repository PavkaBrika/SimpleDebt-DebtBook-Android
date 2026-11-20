package com.breckneck.debtbook.settings.synchronization

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.Base64
import com.breckneck.deptbook.domain.backup.BackupCodec
import java.io.File
import java.util.UUID

/** Private safety copy survives process death. File export is required before uninstalling. */
internal class BackupFiles(context: Context) : BackupTransfer.BackupPhotos {
    private val directory = File(context.noBackupFilesDir, "recovery").apply { mkdirs() }
    private val previous = AtomicFile(File(directory, "before-restore.json"))
    private val photosDirectory = File(context.filesDir, "restored-photos").apply { mkdirs() }
    fun hasPrevious(): Boolean = previous.baseFile.exists()
    fun readPrevious(): String = previous.openRead().bufferedReader(Charsets.UTF_8).use {
        val text = it.readTextBounded(BackupCodec.MAX_CHARACTERS)
        BackupCodec().decode(text)
        text
    }
    fun retainPrevious(content: String) {
        BackupCodec().decode(content)
        val output = previous.startWrite()
        try { output.write(content.toByteArray(Charsets.UTF_8)); previous.finishWrite(output) }
        catch (error: Throwable) { previous.failWrite(output); throw error }
    }
    override fun read(path: String): String? {
        val file = File(path)
        if (!file.isFile) return null
        require(file.length() <= MAX_PHOTO_BYTES) { "Photo is too large" }
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP).also(::validate)
    }
    private fun bytes(encoded: String): ByteArray {
        require(encoded.length <= BackupCodec.MAX_PHOTO_CHARACTERS)
        return Base64.decode(encoded, Base64.NO_WRAP).also { bytes ->
            require(bytes.size <= MAX_PHOTO_BYTES)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outWidth in 1..16384 && options.outHeight in 1..16384) { "Invalid photo" }
        }
    }
    override fun validate(encoded: String) { bytes(encoded) }
    override fun write(encoded: String): String {
        val data = bytes(encoded)
        val file = File(photosDirectory, "${UUID.randomUUID()}.img")
        try { file.outputStream().use { it.write(data); it.fd.sync() } }
        catch (error: Throwable) { file.delete(); throw error }
        return file.absolutePath
    }
    override fun discard(path: String) {
        val file = File(path)
        if (file.parentFile?.canonicalFile == photosDirectory.canonicalFile) file.delete()
    }
    companion object { private const val MAX_PHOTO_BYTES = 6 * 1024 * 1024 }
}

internal fun java.io.Reader.readTextBounded(limit: Int): String {
    val result = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(result.length + count <= limit) { "Backup too large" }
        result.append(buffer, 0, count)
    }
    return result.toString()
}
