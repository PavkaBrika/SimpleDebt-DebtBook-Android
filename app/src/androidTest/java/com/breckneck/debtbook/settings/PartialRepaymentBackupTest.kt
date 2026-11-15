package com.breckneck.debtbook.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.settings.synchronization.BackupFiles
import com.breckneck.debtbook.settings.synchronization.BackupTransfer
import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.model.*
import database.AppDataBase
import entity.Debt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import repository.AppDataRepositoryImpl
import java.io.File
import java.math.BigDecimal

class PartialRepaymentBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val codec = BackupCodec()
    private fun snapshot() = AppDataLists(
        listOf(HumanDomain(900040, "R2 portable", "80.01".toBigDecimal(), "EUR", "0.01".toBigDecimal())),
        listOf(DebtDomain(900040, "100".toBigDecimal().stripTrailingZeros(), 900040, "Original", "2026-11-16"),
            DebtDomain(900041, "-20".toBigDecimal().stripTrailingZeros(), 900040, null, "2026-11-16", repaymentOfId = 900040, operationId = "request-0000900041"),
            DebtDomain(900042, "-80".toBigDecimal().stripTrailingZeros(), 900040, null, "2026-11-16", repaymentOfId = 900040, operationId = "request-0000900042", cancelled = true)),
        emptyList(), emptyList(), emptyList(), emptyList())

    private fun assertSnapshot(actual: AppDataLists) {
        // BigDecimal scale is a representation detail: Room reads plain decimal strings.
        assertEquals(snapshot(), actual.copy(debtList = actual.debtList.map { it.copy(sum = it.sum.stripTrailingZeros()) }))
    }

    @Test fun restoreReexportAndUndoKeepLinksCancellationsAndRequestIds() = runBlocking {
        val repo = AppDataRepositoryImpl(context)
        val original = repo.getAllAppData()
        val files = BackupFiles(context)
        val transfer = BackupTransfer(repo::getAllAppData, repo::replaceAllAppData, files::retainPrevious, files)
        val content = codec.encode(snapshot())
        try {
            transfer.restore { content }
            assertSnapshot(repo.getAllAppData())
            File(context.filesDir, "r2-portable-backup.json").writeText(content)
            transfer.upload { assertEquals(snapshot(), codec.decode(it).data) }
            val dao = AppDataBase(context).appDao()
            // Replayed requests after restoring another installation remain idempotent.
            dao.saveDebt(Debt(0, "-20".toBigDecimal(), 900040, null, "2026-11-16", repaymentOfId = 900040, operationId = "request-0000900041"))
            dao.saveDebt(Debt(0, "-80".toBigDecimal(), 900040, null, "2026-11-16", repaymentOfId = 900040, operationId = "request-0000900042"))
            assertSnapshot(repo.getAllAppData())
            val previous = files.readPrevious()
            try { transfer.restore { content.replace("\"repaymentOfId\":900040", "\"repaymentOfId\":999999") }; fail("broken link accepted") }
            catch (_: IllegalArgumentException) { }
            assertSnapshot(repo.getAllAppData())
            assertEquals(previous, files.readPrevious())
            val recreated = BackupFiles(context)
            BackupTransfer(repo::getAllAppData, repo::replaceAllAppData, recreated::retainPrevious, recreated)
                .restore { recreated.readPrevious() }
            // Backup strips local photo paths; compare financial rows directly.
            val restored = repo.getAllAppData()
            assertEquals(original.debtList, restored.debtList)
            assertEquals(original.humanList, restored.humanList)
        } finally { repo.replaceAllAppData(original) }
    }

    @Test fun portableRepaymentsSurviveReinstallAndSecondDevice() = runBlocking {
        val uri = InstrumentationRegistry.getArguments().getString("r2FixtureUri")
        org.junit.Assume.assumeNotNull(uri)
        val content = context.contentResolver.openInputStream(Uri.parse(uri))!!.bufferedReader().use { it.readText() }
        val repo = AppDataRepositoryImpl(context)
        val original = repo.getAllAppData()
        val files = BackupFiles(context)
        try {
            val transfer = BackupTransfer(repo::getAllAppData, repo::replaceAllAppData, files::retainPrevious, files)
            transfer.restore { content }
            assertSnapshot(repo.getAllAppData())
            transfer.upload { assertEquals(snapshot(), codec.decode(it).data) }
        } finally { repo.replaceAllAppData(original) }
    }
}
