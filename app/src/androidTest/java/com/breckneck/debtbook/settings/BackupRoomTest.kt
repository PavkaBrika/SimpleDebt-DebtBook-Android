package com.breckneck.debtbook.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.settings.synchronization.BackupFiles
import com.breckneck.debtbook.settings.synchronization.BackupTransfer
import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.model.*
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import repository.AppDataRepositoryImpl
import java.io.File
import java.math.BigDecimal
import java.util.Date

class BackupRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val codec = BackupCodec()
    private val repository get() = AppDataRepositoryImpl(context)
    private fun snapshot(id: Int, photo: String? = null) = AppDataLists(
        listOf(HumanDomain(id, "Backup person", "123456789012345678.91".toBigDecimal(), "EUR", BigDecimal.ZERO)),
        listOf(DebtDomain(id, "123456789012345678.91".toBigDecimal(), id, "Debt", "2026-11-15", "15 Nov 2026")),
        listOf(Finance(id, "0.01".toBigDecimal(), Date(1794700800123L), "Finance", id, "EUR", "2026-11-15")),
        listOf(FinanceCategory(id, "Category", FinanceCategoryState.INCOME, "#123456", 123)),
        listOf(Goal(id, "Portable photo", "100.01".toBigDecimal(), "20.21".toBigDecimal(), "JPY", photo, Date(1794700800123L), Date(1794787200123L), "20.01".toBigDecimal(), "2026-11-16")),
        listOf(GoalDeposit(id, "0.2".toBigDecimal(), Date(1794700800123L), id)),
    )
    @Test fun restoreThenUploadUsesRestoredRoomDataAndRejectsMalformedBackup() = runBlocking {
        val repo = repository; val original = repo.getAllAppData()
        var previous = ""
        val transfer = BackupTransfer(repo::getAllAppData, repo::replaceAllAppData, { previous = it })
        val a = snapshot(900020); val b = snapshot(900021)
        try {
            repo.replaceAllAppData(a)
            transfer.upload { assertEquals(a, codec.decode(it).data) }
            transfer.restore { codec.encode(b) }
            assertEquals(a, codec.decode(previous).data)
            transfer.upload { assertEquals(b, codec.decode(it).data) }
            for (content in listOf("{}", codec.encode(a).replace("\"formatVersion\":${BackupCodec.VERSION}", "\"formatVersion\":99"))) {
                try { transfer.restore { content }; fail("malformed backup accepted") } catch (_: IllegalArgumentException) { }
                assertEquals(b, repo.getAllAppData())
            }
            transfer.restore { previous }
            assertEquals(a, repo.getAllAppData())
        } finally { repo.replaceAllAppData(original) }
    }

    @Test fun photosTravelAsBytesAndSafetyCopySurvivesCoordinatorRecreation() = runBlocking {
        val repo = repository; val original = repo.getAllAppData()
        val files = BackupFiles(context)
        val source = File(context.cacheDir, "r1b-source-photo.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val expectedPhoto = source.readBytes()
        val a = snapshot(900022)
        val b = snapshot(900023, source.absolutePath)
        val transfer = BackupTransfer(repo::getAllAppData, repo::replaceAllAppData, files::retainPrevious, files)
        var restoredPath: String? = null
        try {
            repo.replaceAllAppData(b)
            var portable = ""
            transfer.upload { portable = it }
            assertFalse(portable.contains(source.absolutePath))
            assertEquals(1, codec.decode(portable).photos.size)
            // Retain a portable artifact for reinstall / second-device verification.
            File(context.getExternalFilesDir(null), "r1b-portable-backup.json").writeText(portable)
            repo.replaceAllAppData(a)
            source.delete()
            transfer.restore { portable }
            val restored = repo.getAllAppData()
            restoredPath = restored.goalList.single().photoPath!!
            assertArrayEquals(expectedPhoto, File(restoredPath).readBytes())
            assertEquals(b.copy(goalList = b.goalList.map { it.copy(photoPath = restoredPath) }), restored)
            val recreatedFiles = BackupFiles(context)
            assertTrue(recreatedFiles.hasPrevious())
            val recreated = BackupTransfer(repo::getAllAppData, repo::replaceAllAppData, recreatedFiles::retainPrevious, recreatedFiles)
            recreated.restore { recreatedFiles.readPrevious() }
            assertEquals(a, repo.getAllAppData())
        } finally {
            repo.replaceAllAppData(original)
            source.delete(); restoredPath?.let { files.discard(it) }
        }
    }

    @Test fun portableFixtureCanBeReadAfterReinstallOrOnAnotherDevice() = runBlocking {
        val fixtureUri = InstrumentationRegistry.getArguments().getString("r1bFixtureUri")
        org.junit.Assume.assumeNotNull(fixtureUri)
        val content = context.contentResolver.openInputStream(Uri.parse(fixtureUri))!!.bufferedReader().use { it.readText() }
        val repo = repository; val original = repo.getAllAppData(); val files = BackupFiles(context)
        var photo: String? = null
        try {
            val transfer = BackupTransfer(repo::getAllAppData, repo::replaceAllAppData, files::retainPrevious, files)
            transfer.restore { content }
            val restored = repo.getAllAppData()
            assertEquals(900023, restored.humanList.single().id)
            assertEquals("123456789012345678.91".toBigDecimal(), restored.debtList.single().sum)
            assertEquals("2026-11-15", restored.debtList.single().date)
            assertEquals(1794700800123L, restored.financeList.single().date.time)
            assertEquals("EUR", restored.financeList.single().currency)
            assertEquals("2026-11-15", restored.financeList.single().calendarDay)
            assertEquals("2026-11-16", restored.goalList.single().goalDay)
            photo = restored.goalList.single().photoPath!!
            assertTrue(File(photo).isFile)
            assertEquals(codec.decode(content).photos.values.single(), files.read(photo))
            transfer.upload { assertEquals(codec.decode(content).data, codec.decode(it).data) }
        } finally { repo.replaceAllAppData(original); photo?.let(files::discard) }
    }
}
