package com.breckneck.debtbook.settings.synchronization

import java.math.BigDecimal

import com.breckneck.deptbook.domain.model.AppDataLists
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.model.HumanDomain
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BackupTransferTest {
    private val gson = Gson()
    private fun snapshot(id: Int, amount: BigDecimal) = AppDataLists(
        listOf(HumanDomain(id, "Person $id", amount, "EUR")),
        listOf(DebtDomain(id, amount, id, null, "1 Jan 2026")),
        emptyList(), emptyList(), emptyList(), emptyList(),
    )

    @Test fun `A then restore B then upload writes B from current database`() = runBlocking {
        var current = snapshot(1, "10.0".toBigDecimal().stripTrailingZeros())
        val restored = snapshot(2, "20.25".toBigDecimal().stripTrailingZeros())
        val transfer = BackupTransfer({ current }, { current = it })
        var uploaded = ""
        transfer.upload { uploaded = it }
        assertEquals(1, gson.fromJson(uploaded, AppDataLists::class.java).humanList.single().id)
        transfer.restore { gson.toJson(restored) }
        transfer.upload { uploaded = it }
        assertEquals(restored, gson.fromJson(uploaded, AppDataLists::class.java))
    }

    @Test fun `local changes after opening screen are included in next upload`() = runBlocking {
        var current = snapshot(1, "10.0".toBigDecimal().stripTrailingZeros())
        val transfer = BackupTransfer({ current }, { current = it })
        current = snapshot(1, "12.34".toBigDecimal().stripTrailingZeros())
        transfer.upload { assertEquals(0, ("12.34".toBigDecimal().stripTrailingZeros()).compareTo(gson.fromJson(it, AppDataLists::class.java).debtList.single().sum!!)) }
    }

    @Test fun `failed current snapshot read never overwrites remote backup`() = runBlocking {
        val transfer = BackupTransfer({ throw IOException("read failed") }, { fail("No restore expected") })
        var wroteRemote = false
        try {
            transfer.upload { wroteRemote = true }
            fail("Expected read failure")
        } catch (_: IOException) { }
        assertFalse(wroteRemote)
    }

    @Test fun `malformed or incomplete backup never reaches replacement`() = runBlocking {
        var replaces = 0
        val transfer = BackupTransfer({ snapshot(1, "10.0".toBigDecimal().stripTrailingZeros()) }, { replaces++ })
        val valid = gson.toJson(snapshot(1, "10.0".toBigDecimal().stripTrailingZeros()))
        for (content in listOf("not json", "null", "{}", "[]", valid.replace("\"humanList\":[", "\"humanList\":[null,"),
            valid.replace("\"name\":\"Person 1\"", "\"name\":null"),
            valid.replace(Regex("\"sum\":[^,]+,"), ""))) {
            try {
                transfer.restore { content }
                fail("Invalid backup accepted")
            } catch (_: IllegalArgumentException) { }
              catch (_: com.google.gson.JsonParseException) { }
        }
        assertEquals(0, replaces)
    }

    @Test fun `remote failure preserves local data and a later attempt can succeed`() = runBlocking {
        val original = snapshot(1, "10.0".toBigDecimal().stripTrailingZeros())
        var current = original
        val transfer = BackupTransfer({ current }, { current = it })
        try {
            transfer.restore { throw IOException("offline") }
            fail("Expected network failure")
        } catch (_: IOException) { }
        assertEquals(original, current)
        val next = snapshot(2, "20.0".toBigDecimal().stripTrailingZeros())
        transfer.restore { gson.toJson(next) }
        assertEquals(next, current)
    }
}
