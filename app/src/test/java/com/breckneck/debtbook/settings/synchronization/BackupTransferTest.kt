package com.breckneck.debtbook.settings.synchronization

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
    private fun snapshot(id: Int, amount: Double) = AppDataLists(
        listOf(HumanDomain(id, "Person $id", amount, "EUR")),
        listOf(DebtDomain(id, amount, id, null, "1 Jan 2026")),
        emptyList(), emptyList(), emptyList(), emptyList(),
    )

    @Test fun `A then restore B then upload writes B from current database`() = runBlocking {
        var current = snapshot(1, 10.0)
        val restored = snapshot(2, 20.25)
        val transfer = BackupTransfer({ current }, { current = it })
        var uploaded = ""
        transfer.upload { uploaded = it }
        assertEquals(1, gson.fromJson(uploaded, AppDataLists::class.java).humanList.single().id)
        transfer.restore { gson.toJson(restored) }
        transfer.upload { uploaded = it }
        assertEquals(restored, gson.fromJson(uploaded, AppDataLists::class.java))
    }

    @Test fun `local changes after opening screen are included in next upload`() = runBlocking {
        var current = snapshot(1, 10.0)
        val transfer = BackupTransfer({ current }, { current = it })
        current = snapshot(1, 12.34)
        transfer.upload { assertEquals(12.34, gson.fromJson(it, AppDataLists::class.java).debtList.single().sum, 0.0) }
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
        val transfer = BackupTransfer({ snapshot(1, 10.0) }, { replaces++ })
        val valid = gson.toJson(snapshot(1, 10.0))
        for (content in listOf("not json", "null", "{}", "[]", valid.replace("\"humanList\":[", "\"humanList\":[null,"),
            valid.replace("\"name\":\"Person 1\"", "\"name\":null"),
            valid.replace("\"sum\":10.0,", ""))) {
            try {
                transfer.restore { content }
                fail("Invalid backup accepted")
            } catch (_: IllegalArgumentException) { }
              catch (_: com.google.gson.JsonParseException) { }
        }
        assertEquals(0, replaces)
    }

    @Test fun `remote failure preserves local data and a later attempt can succeed`() = runBlocking {
        val original = snapshot(1, 10.0)
        var current = original
        val transfer = BackupTransfer({ current }, { current = it })
        try {
            transfer.restore { throw IOException("offline") }
            fail("Expected network failure")
        } catch (_: IOException) { }
        assertEquals(original, current)
        val next = snapshot(2, 20.0)
        transfer.restore { gson.toJson(next) }
        assertEquals(next, current)
    }
}
