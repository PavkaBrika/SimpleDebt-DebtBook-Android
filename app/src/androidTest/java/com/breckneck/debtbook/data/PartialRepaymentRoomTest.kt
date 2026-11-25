package com.breckneck.debtbook.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.deptbook.domain.backup.BackupCodec
import com.breckneck.deptbook.domain.util.RepaymentPolicy
import com.google.gson.JsonParser
import database.AppDataBase
import database.DatabaseMigrations
import entity.Debt
import entity.Human
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PartialRepaymentRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun value(text: String) = BigDecimal(text)
    private fun assertAmount(text: String, actual: BigDecimal) = assertEquals(0, value(text).compareTo(actual))
    private fun repayment(amount: String, key: String = "request-000000001") =
        Debt(0, value(amount), 1, null, "2026-11-16", repaymentOfId = 1, operationId = key)
    private fun rejected(block: () -> Unit) {
        try { block(); fail("invalid write accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun multipleRepaymentsCloseCancelAndRetryBothDirections() {
        for (sign in listOf(1, -1)) {
            Room.inMemoryDatabaseBuilder(context, AppDataBase::class.java).build().use { db ->
                val dao = db.appDao()
                dao.insertHuman(Human(1, "Person", value("7.01"), "EUR"))
                dao.saveDebt(Debt(1, value("100") * sign.toBigDecimal(), 1, null, "old unknown day"))
                dao.saveDebt(repayment((-20 * sign).toString()))
                dao.saveDebt(repayment((-20 * sign).toString()))
                assertEquals(2, dao.getAllDebts().size)
                assertAmount((value("7.01") + value("80") * sign.toBigDecimal()).toPlainString(), dao.getHumanSumDebt(1))
                rejected { dao.saveDebt(repayment((-81 * sign).toString(), "request-000000002")) }
                rejected { dao.reviseDebt(dao.getAllDebts().first().copy(sum = value("50"))) }
                rejected { dao.removeDebt(dao.getAllDebts().first()) }
                dao.saveDebt(repayment((-80 * sign).toString(), "request-000000003"))
                assertAmount("7.01", dao.getHumanSumDebt(1))
                val first = dao.getAllDebts().first { it.operationId == "request-000000001" }
                dao.removeDebt(first); dao.removeDebt(first)
                dao.saveDebt(repayment((-20 * sign).toString())) // Retry cancelled request.
                assertEquals(3, dao.getAllDebts().size)
                assertTrue(dao.getAllDebts().single { it.id == first.id }.cancelled)
                assertAmount((value("7.01") + value("20") * sign.toBigDecimal()).toPlainString(), dao.getHumanSumDebt(1))
            }
        }
    }

    @Test fun concurrentRequestsAndFailedBalanceWriteNeverCorruptLedger() {
        Room.inMemoryDatabaseBuilder(context, AppDataBase::class.java).build().use { db ->
            val dao = db.appDao()
            dao.insertHuman(Human(1, "Person", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(1, value("100"), 1, null, "2026-11-16"))
            val pool = Executors.newFixedThreadPool(2)
            try {
                val jobs = (1..2).map { index -> pool.submit<Boolean> {
                    try { dao.saveDebt(repayment("-60", "request-00000000$index")); true } catch (_: IllegalArgumentException) { false }
                } }
                assertEquals(1, jobs.count { it.get(10, TimeUnit.SECONDS) })
            } finally { pool.shutdownNow() }
            assertAmount("40", dao.getHumanSumDebt(1))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_balance BEFORE UPDATE ON Human BEGIN SELECT RAISE(ABORT, 'test save failure'); END")
            val request = repayment("-20", "request-000000009")
            try { dao.saveDebt(request); fail("write should fail") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(2, dao.getAllDebts().size)
            assertAmount("40", dao.getHumanSumDebt(1))
            val payment = dao.getAllDebts().single { it.repaymentOfId != null }
            try { dao.removeDebt(payment); fail("cancel should fail") } catch (_: android.database.sqlite.SQLiteException) { }
            assertFalse(dao.getAllDebts().single { it.id == payment.id }.cancelled)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_balance")
            dao.saveDebt(request)
            assertAmount("20", dao.getHumanSumDebt(1))
        }
    }

    @Test fun migration16KeepsUnlinkedHistoryAndReopeningKeepsRequestIdentity() {
        val name = "r2-migration-16"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("database.AppDataBase/16.json")
            .bufferedReader().use { JsonParser.parseReader(it) }.asJsonObject.getAsJsonObject("database").getAsJsonArray("entities")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null).use { db ->
            schema.forEach { item -> db.execSQL(item.asJsonObject.get("createSql").asString.replace("\u0024{TABLE_NAME}", item.asJsonObject.get("tableName").asString)) }
            db.execSQL("INSERT INTO Human VALUES (1, 'Legacy', '87.01', 'EUR', '7.01')")
            db.execSQL("INSERT INTO Debt VALUES (1, '100', 1, 'Original', 'unknown', 'raw')")
            db.execSQL("INSERT INTO Debt VALUES (2, '-20', 1, 'Old independent operation', 'unknown', NULL)")
            db.version = 16
        }
        fun open() = Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
        try {
            val failing = object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    DatabaseMigrations.MIGRATION_16_17.migrate(db)
                    error("Failure after adding repayment schema")
                }
            }
            Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(failing, DatabaseMigrations.MIGRATION_17_18, DatabaseMigrations.MIGRATION_18_19, DatabaseMigrations.MIGRATION_19_20, DatabaseMigrations.MIGRATION_20_21).build().use { db ->
                try { db.appDao().getAllDebts(); fail("Migration should fail") } catch (_: IllegalStateException) { }
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertEquals(16, db.version)
                db.rawQuery("SELECT sumDebt FROM Human WHERE id = 1", null).use { assertTrue(it.moveToFirst()); assertEquals("87.01", it.getString(0)) }
                db.rawQuery("SELECT * FROM Debt", null).use { assertEquals(-1, it.getColumnIndex("repaymentOfId")) }
            }
            open().use { db ->
                val dao = db.appDao()
                assertTrue(dao.getAllDebts().all { it.repaymentOfId == null && !it.cancelled && it.operationId == null && !it.recordedAsLoan })
                assertEquals("raw", dao.getAllDebts().first().legacyDate)
                dao.saveDebt(repayment("-20"))
                assertAmount("67.01", dao.getHumanSumDebt(1))
                assertAmount("80", RepaymentPolicy.remaining(dao.getAllDebts().first().toDomain(), dao.getAllDebts().map { it.toDomain() }))
            }
            open().use { db ->
                db.appDao().saveDebt(repayment("-20"))
                assertEquals(3, db.appDao().getAllDebts().size)
                assertAmount("67.01", db.appDao().getHumanSumDebt(1))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun exactLegacyRemainderCanBeFullyRepaidWithoutRounding() {
        Room.inMemoryDatabaseBuilder(context, AppDataBase::class.java).build().use { db ->
            val dao = db.appDao()
            dao.insertHuman(Human(1, "Person", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(1, value("0.30000000000000004"), 1, null, "unknown"))
            rejected { dao.saveDebt(repayment("-0.001")) }
            dao.saveDebt(repayment("-0.30000000000000004"))
            assertAmount("0", dao.getHumanSumDebt(1))
        }
    }

    @Test fun restoringUsesTheOriginalPaymentAndRejectsAnExcessWithoutChangingTheBalance() {
        for (sign in listOf(1, -1)) {
            Room.inMemoryDatabaseBuilder(context, AppDataBase::class.java).build().use { db ->
                val dao = db.appDao()
                dao.insertHuman(Human(1, "Person", BigDecimal.ZERO, "EUR"))
                dao.saveDebt(Debt(1, (100 * sign).toBigDecimal(), 1, null, "2026-11-16"))
                dao.saveDebt(repayment((-60 * sign).toString()))
                val first = dao.getAllDebts().single { it.repaymentOfId != null }
                dao.removeDebt(first)
                dao.saveDebt(repayment((-50 * sign).toString(), "request-000000002"))
                val second = dao.getAllDebts().single { it.operationId == "request-000000002" }
                rejected { dao.restoreRepayment(first.id, 2) }
                rejected { dao.restoreRepayment(first.id, 1) }
                assertTrue(dao.getAllDebts().single { it.id == first.id }.cancelled)
                assertAmount((50 * sign).toString(), dao.getHumanSumDebt(1))

                dao.removeDebt(second)
                dao.saveDebt(repayment((-40 * sign).toString(), "request-000000003"))
                val alarm = "1234567890-original-alarm"
                dao.setDebtDueDate(1, "2099-11-16", 540, alarm, System.currentTimeMillis())
                db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE UPDATE ON Human BEGIN SELECT RAISE(ABORT, 'test restore failure'); END")
                try { dao.restoreRepayment(first.id, 1); fail("restore should roll back") }
                catch (_: android.database.sqlite.SQLiteException) { }
                assertTrue(dao.getAllDebts().single { it.id == first.id }.cancelled)
                assertEquals(alarm, dao.getAllDebts().single { it.id == 1 }.reminderToken)
                assertAmount((60 * sign).toString(), dao.getHumanSumDebt(1))
                db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_restore")

                assertTrue(dao.restoreRepayment(first.id, 1))
                assertFalse(dao.restoreRepayment(first.id, 1))
                assertEquals(first, dao.getAllDebts().single { it.id == first.id })
                assertEquals(4, dao.getAllDebts().size)
                assertNull(dao.getAllDebts().single { it.id == 1 }.reminderToken)
                assertAmount("0", dao.getHumanSumDebt(1))
            }
        }
    }
}

private fun <T> AppDataBase.use(block: (AppDataBase) -> T): T = try { block(this) } finally { close() }
