package com.breckneck.debtbook.settings.synchronization

import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.util.Date

class BackupTransferTest {
    private val codec = BackupCodec()
    private fun snapshot(id: Int, amount: String) = AppDataLists(
        listOf(HumanDomain(id, "Person $id", amount.toBigDecimal(), "EUR", BigDecimal.ZERO)),
        listOf(DebtDomain(id, amount.toBigDecimal(), id, null, "2026-01-01")),
        emptyList(), emptyList(), emptyList(), emptyList(),
    )
    @Test fun `A then restore B then upload reads B and retains A`() = runBlocking {
        var current = snapshot(1, "10.01")
        val original = current
        val b = snapshot(2, "20.25")
        var retained = ""
        val transfer = BackupTransfer({ current }, { current = it }, { retained = it })
        transfer.upload { assertEquals(original, codec.decode(it).data) }
        transfer.restore { codec.encode(b) }
        transfer.upload { assertEquals(b, codec.decode(it).data) }
        assertEquals(original, codec.decode(retained).data)
    }
    @Test fun `local changes after screen creation enter the next upload`() = runBlocking {
        var current = snapshot(1, "10.01")
        val transfer = BackupTransfer({ current }, { current = it })
        current = snapshot(1, "12.34")
        transfer.upload { assertEquals(current, codec.decode(it).data) }
    }
    @Test fun `failed snapshot never overwrites remote backup`() = runBlocking {
        val transfer = BackupTransfer({ throw IOException("read failed") }, { fail("No restore") })
        var wrote = false
        try { transfer.upload { wrote = true }; fail() } catch (_: IOException) { }
        assertFalse(wrote)
    }
    @Test fun `bad content is rejected before safety copy or replacement`() = runBlocking {
        var replaces = 0; var retained = 0
        val transfer = BackupTransfer({ snapshot(1, "10.01") }, { replaces++ }, { retained++ })
        val valid = codec.encode(snapshot(1, "10.01"))
        for (content in listOf("not json", "null", "{}", "[]", valid.dropLast(1),
            valid.replace("\"formatVersion\":2", "\"formatVersion\":99"),
            valid.replace("\"idHuman\":1", "\"idHuman\":99"))) {
            try { transfer.restore { content }; fail("Invalid backup accepted") } catch (_: Exception) { }
        }
        assertEquals(0, replaces); assertEquals(0, retained)
    }
    @Test fun `safety copy failure preserves current database`() = runBlocking {
        val original = snapshot(1, "10.01"); var current = original
        val transfer = BackupTransfer({ current }, { current = it }, { throw IOException("disk full") })
        try { transfer.restore { codec.encode(snapshot(2, "20.02")) }; fail() } catch (_: IOException) { }
        assertEquals(original, current)
    }
    @Test fun `remote failure preserves data and retry succeeds`() = runBlocking {
        var current = snapshot(1, "10.01"); val original = current
        val transfer = BackupTransfer({ current }, { current = it })
        try { transfer.restore { throw IOException("offline") }; fail() } catch (_: IOException) { }
        assertEquals(original, current)
        val next = snapshot(2, "20.02")
        transfer.restore { codec.encode(next) }; assertEquals(next, current)
    }
    @Test fun `failed database replacement discards staged photos and keeps safety copy`() = runBlocking {
        val original = snapshot(1, "10.01")
        var retained = ""; val discarded = mutableListOf<String>()
        val photos = object : BackupTransfer.BackupPhotos {
            override fun read(path: String): String? = null
            override fun validate(encoded: String) {}
            override fun write(encoded: String) = "/staged/image"
            override fun discard(path: String) { discarded += path }
        }
        val b = snapshot(2, "20.02").copy(goalList = listOf(Goal(3, "Photo", BigDecimal.TEN, BigDecimal.ZERO, "EUR", null, Date(1), null)))
        val transfer = BackupTransfer({ original }, { throw IOException("DB failed") }, { retained = it }, photos)
        try { transfer.restore { codec.encode(b, mapOf(3 to "aW1hZ2U=")) }; fail() } catch (_: IOException) { }
        assertEquals(listOf("/staged/image"), discarded)
        assertEquals(original, codec.decode(retained).data)
    }
    @Test fun `restore confirmation reads the content that was previewed`() = runBlocking {
        val original = snapshot(1, "10.01"); var current = original
        val transfer = BackupTransfer({ current }, { current = it })
        val content = codec.encode(snapshot(2, "20.02"))
        assertEquals(2, transfer.preview(content).data.humanList.single().id)
        assertEquals(original, current)
        transfer.restore { content }
        assertEquals(2, current.humanList.single().id)
    }
}
