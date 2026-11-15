package com.breckneck.debtbook.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.breckneck.debtbook.settings.synchronization.BackupTransfer
import com.breckneck.deptbook.domain.model.AppDataLists
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.model.HumanDomain
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import repository.AppDataRepositoryImpl

class BackupRoomTest {
    @Test fun restoreThenUploadUsesRestoredRoomDataAndRejectsMalformedBackup() = runBlocking {
        val repository = AppDataRepositoryImpl(ApplicationProvider.getApplicationContext<Context>())
        val original = repository.getAllAppData()
        val gson = Gson()
        val transfer = BackupTransfer(repository::getAllAppData, repository::replaceAllAppData)
        val b = AppDataLists(
            listOf(HumanDomain(900020, "Backup test", 20.25, "EUR")),
            listOf(DebtDomain(900020, 20.25, 900020, null, "14 Nov 2026")),
            emptyList(), emptyList(), emptyList(), emptyList()
        )
        try {
            var uploaded = ""
            transfer.upload { uploaded = it }
            assertEquals(original, gson.fromJson(uploaded, AppDataLists::class.java))
            transfer.restore { gson.toJson(b) }
            transfer.upload { uploaded = it }
            assertEquals(b, gson.fromJson(uploaded, AppDataLists::class.java))
            try {
                transfer.restore { "{}" }
                fail("malformed backup accepted")
            } catch (_: IllegalArgumentException) { }
            assertEquals(b, repository.getAllAppData())
        } finally {
            repository.replaceAllAppData(original)
        }
    }
}
