package com.breckneck.debtbook.settings.synchronization

import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.model.AppDataLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One coordinator for file/Drive operations. Validation always precedes safety copy and replacement. */
internal class BackupTransfer(
    private val readCurrentData: () -> AppDataLists,
    private val replaceCurrentData: (AppDataLists) -> Unit,
    private val retainPrevious: (String) -> Unit = {},
    private val photos: BackupPhotos? = null,
) {
    private val codec = BackupCodec()
    private val mutex = Mutex()

    interface BackupPhotos {
        fun read(path: String): String?
        fun validate(encoded: String)
        fun write(encoded: String): String
        fun discard(path: String)
    }

    private fun snapshot(): String {
        val current = readCurrentData()
        val images = current.goalList.mapNotNull { goal ->
            goal.photoPath?.let { photos?.read(it) }?.let { goal.id to it }
        }.toMap()
        // Local paths never leave this installation. Missing source files cannot be recovered.
        val portable = current.copy(goalList = current.goalList.map { it.copy(photoPath = null) })
        return codec.encode(portable, images)
    }

    suspend fun preview(content: String): BackupCodec.Document = withContext(Dispatchers.IO) {
        codec.decode(content).also { document ->
            require(photos != null || document.photos.isEmpty()) { "Photo storage unavailable" }
            document.photos.values.forEach { photos!!.validate(it) }
        }
    }

    suspend fun upload(writeRemote: suspend (String) -> Unit) = mutex.withLock {
        val content = withContext(Dispatchers.IO) { snapshot() }
        writeRemote(content)
    }

    suspend fun restore(readRemote: suspend () -> String) = mutex.withLock {
        val document = preview(readRemote())
        withContext(Dispatchers.IO) {
            val createdPhotos = mutableListOf<String>()
            try {
                val data = document.data.copy(goalList = document.data.goalList.map { goal ->
                    val path = document.photos[goal.id]?.let { encoded ->
                        photos!!.write(encoded).also { createdPhotos += it }
                    }
                    goal.copy(photoPath = path)
                })
                // Failure to retain a usable previous snapshot cancels restore too.
                retainPrevious(snapshot())
                replaceCurrentData(data) // Repository replaces all tables in one Room transaction.
            } catch (error: Throwable) {
                createdPhotos.forEach { photos?.discard(it) }
                throw error
            }
        }
        document
    }
}
