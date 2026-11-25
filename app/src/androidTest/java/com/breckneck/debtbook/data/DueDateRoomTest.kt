package com.breckneck.debtbook.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.deptbook.domain.model.DebtDueChange
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.RepaymentPolicy
import com.google.gson.JsonParser
import database.AppDataBase
import database.DatabaseMigrations
import entity.Debt
import entity.Human
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class DueDateRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun partialFullUndoDueEditingAndRollbackBothDirections() {
        for (sign in listOf(1, -1)) Room.inMemoryDatabaseBuilder(context, AppDataBase::class.java).build().use { db ->
            val dao = db.appDao()
            dao.insertHuman(Human(1, "Due test", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(1, (100 * sign).toBigDecimal(), 1, null, "2026-11-16"))
            assertNull(dao.getAllDebts().single().dueDay)
            dao.setDebtDueDate(1, "2099-12-31", 540, "token-1", System.currentTimeMillis())
            dao.saveDebt(Debt(0, (-20 * sign).toBigDecimal(), 1, null, "2026-11-16", repaymentOfId = 1, operationId = "payment-0000000001"))
            assertEquals("token-1", dao.getAllDebts().first().reminderToken)
            dao.setDebtDueDate(1, "2099-12-30", 600, "token-2", System.currentTimeMillis())
            assertEquals("2099-12-30", dao.getAllDebts().first().dueDay)
            dao.reviseDebt(dao.getAllDebts().first().copy(info = "Edited"))
            assertEquals("token-2", dao.getAllDebts().first().reminderToken)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_due_balance BEFORE UPDATE ON Human BEGIN SELECT RAISE(ABORT, 'test'); END")
            val original = dao.getAllDebts().first()
            try {
                dao.reviseDebt(original.copy(info = "Draft edit"), DebtDueChange("2099-12-29", 600))
                fail("Combined debt edit was not rolled back")
            } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(original, dao.getAllDebts().first())
            val full = Debt(0, (-80 * sign).toBigDecimal(), 1, null, "2026-11-16", repaymentOfId = 1, operationId = "payment-0000000002")
            try { dao.saveDebt(full); fail("write accepted") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals("token-2", dao.getAllDebts().first().reminderToken)
            assertEquals(2, dao.getAllDebts().size)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_due_balance")
            dao.saveDebt(full)
            assertNull(dao.getAllDebts().first().reminderToken)
            assertEquals("2099-12-30", dao.getAllDebts().first().dueDay)
            dao.removeDebt(dao.getAllDebts().last())
            val rows = dao.getAllDebts().map { it.toDomain() }
            assertEquals(0, BigDecimal("80").compareTo(RepaymentPolicy.remaining(rows.first(), rows)))
            assertNull(rows.first().reminderToken)
            dao.setDebtDueDate(1, "2099-12-30", 610, "token-3", System.currentTimeMillis())
            val operationDay = "2026-11-16"
            val afternoon = DebtReminderPolicy.trigger(operationDay, 18 * 60)
            try {
                dao.setDebtDueDate(1, "2026-11-15", null, null, afternoon)
                fail("Due date before the debt was accepted")
            } catch (_: IllegalArgumentException) { }
            assertEquals("2099-12-30", dao.getAllDebts().first().dueDay)

            dao.setDebtDueDate(1, operationDay, DebtReminderPolicy.DEFAULT_MINUTE, "same-day", afternoon)
            assertEquals("same-day", dao.getAllDebts().first().reminderToken)
            val nextDay = DebtReminderPolicy.trigger("2026-11-17", 0)
            dao.setDebtDueDate(1, operationDay, null, null, nextDay)
            assertTrue(DebtReminderPolicy.overdue(dao.getAllDebts().first().toDomain(), BigDecimal("80"), "2026-11-17"))
            try {
                dao.setDebtDueDate(1, operationDay, 540, "token-4", nextDay)
                fail("Past reminder day was accepted")
            } catch (_: IllegalArgumentException) { }
            assertNull(dao.getAllDebts().first().reminderToken)
        }
    }

    @Test fun migration17HasNoAutomaticDatesAndFailureRollsBack() {
        val name = "r22-migration-17"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("database.AppDataBase/17.json")
            .bufferedReader().use { JsonParser.parseReader(it) }.asJsonObject.getAsJsonObject("database").getAsJsonArray("entities")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null).use { db ->
            schema.forEach { item ->
                val table = item.asJsonObject.get("tableName").asString
                db.execSQL(item.asJsonObject.get("createSql").asString.replace("\u0024{TABLE_NAME}", table))
                item.asJsonObject.getAsJsonArray("indices")?.forEach { index ->
                    db.execSQL(index.asJsonObject.get("createSql").asString.replace("\u0024{TABLE_NAME}", table))
                }
            }
            db.execSQL("INSERT INTO Human VALUES (1, 'Legacy', '80', 'EUR', '0')")
            db.execSQL("INSERT INTO Debt VALUES (1, '100', 1, NULL, 'unknown', 'raw', NULL, NULL, 0)")
            db.execSQL("INSERT INTO Debt VALUES (2, '-20', 1, NULL, '2026-11-16', NULL, 1, 'payment-0000000001', 0)")
            db.version = 17
        }
        try {
            var exercised = false
            val failing = object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) { exercised = true; DatabaseMigrations.MIGRATION_17_18.migrate(db); error("fail after ALTER") }
            }
            try { Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(failing, DatabaseMigrations.MIGRATION_18_19, DatabaseMigrations.MIGRATION_19_20, DatabaseMigrations.MIGRATION_20_21).build().use { it.appDao().getAllDebts() }; fail("failure accepted") }
            catch (_: IllegalStateException) { }
            assertTrue(exercised)
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(17, it.version) }
            repeat(2) {
                Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build().use { db ->
                    val rows = db.appDao().getAllDebts()
                    assertEquals(2, rows.size); assertNull(rows.first().dueDay); assertNull(rows.first().reminderMinute)
                    assertEquals("raw", rows.first().legacyDate); assertEquals(1, rows.last().repaymentOfId)
                    assertEquals("payment-0000000001", rows.last().operationId)
                }
            }
        } finally { context.deleteDatabase(name) }
    }
}

private inline fun <T> AppDataBase.use(block: (AppDataBase) -> T): T = try { block(this) } finally { close() }
