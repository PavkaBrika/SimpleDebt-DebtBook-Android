package com.breckneck.debtbook.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import database.AppDataBase
import database.DatabaseMigrations
import entity.Debt
import entity.Human
import entity.GoalData
import entity.GoalDepositData
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Date

class ExactDataRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun amount(text: String) = BigDecimal(text)
    private fun assertAmount(text: String, actual: BigDecimal) = assertEquals(0, amount(text).compareTo(actual))

    private fun legacy(name: String, version: Int) {
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("database.AppDataBase/14.json").bufferedReader().use { JsonParser.parseReader(it) }
            .asJsonObject.getAsJsonObject("database").getAsJsonArray("entities")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null).use { db ->
            schema.forEach { item ->
                val table = item.asJsonObject.get("tableName").asString
                if (version < 11 && table in listOf("FinanceData", "FinanceCategoryData")) return@forEach
                if (version < 14 && table in listOf("GoalData", "GoalDepositData")) return@forEach
                db.execSQL(item.asJsonObject.get("createSql").asString.replace("\u0024{TABLE_NAME}", table))
            }
            db.execSQL("INSERT INTO Human VALUES (31, 'Legacy EUR', 100.01, 'EUR')")
            db.execSQL("INSERT INTO Human VALUES (32, 'Legacy JPY', -42, 'JPY')")
            db.execSQL("INSERT INTO Debt VALUES (41, 0.1, 31, 'original', '5 февр. 2024 г.')")
            db.execSQL("INSERT INTO Debt VALUES (42, 0.2, 31, NULL, '01/02/2024')")
            db.execSQL("UPDATE sqlite_sequence SET seq = 500 WHERE name = 'Debt'")
            if (version >= 11) {
                db.execSQL("INSERT INTO FinanceCategoryData VALUES (71, 'Category', 1, '#123456', 123)")
                db.execSQL("INSERT INTO FinanceData VALUES (81, 0.30000000000000004, 1735689600000, 'finance', 71)")
            }
            if (version >= 14) {
                db.execSQL("INSERT INTO GoalData VALUES (91, 'Goal', 1000.01, 30.1, 'JPY', '/old/photo.jpg', 1735689600000, NULL)")
                db.execSQL("INSERT INTO GoalDepositData VALUES (101, 10.01, 1735689600000, 91)")
            }
            db.version = version
        }
    }

    @Test fun migrateSupportedVersionsPreservesRowsRelationsValuesAndReopens() {
        for (version in listOf(5, 11, 12, 13, 14)) {
            val name = "r1b-migration-$version"
            legacy(name, version)
            fun open() = Room.databaseBuilder(context, AppDataBase::class.java, name)
                .addMigrations(*DatabaseMigrations.ALL).build()
            try {
                open().use { db ->
                    val dao = db.appDao()
                    val person = dao.getHuman(31)!!
                    assertEquals("EUR", person.currency)
                    assertAmount("100.01", person.sumDebt)
                    assertAmount("99.71", person.openingAdjustment)
                    assertAmount("-42", dao.getHuman(32)!!.sumDebt)
                    val debts = dao.getAllDebtsById(31).sortedBy { it.id }
                    assertEquals(listOf(41, 42), debts.map { it.id })
                    assertEquals("2024-02-05", debts[0].date)
                    assertEquals("5 февр. 2024 г.", debts[0].legacyDate)
                    assertEquals("01/02/2024", debts[1].date)
                    assertEquals("original", debts[0].info)
                    if (version >= 11) {
                        assertAmount("0.30000000000000004", dao.getAllFinances().single().sum)
                        assertEquals(71, dao.getAllFinances().single().financeCategoryId)
                        assertEquals(1735689600000L, dao.getAllFinances().single().date.time)
                    }
                    if (version >= 14) {
                        val goal = dao.getGoal(91)!!
                        assertAmount("20.09", goal.openingAdjustment)
                        assertEquals("/old/photo.jpg", goal.photoPath)
                        dao.addGoalDeposit(GoalDepositData(0, amount("0.2"), Date(1735689600000L), 91))
                        assertAmount("30.3", dao.getGoal(91)!!.savedSum)
                    }
                    dao.saveDebt(Debt(0, amount("0.2"), 31, null, "2026-11-15"))
                    assertAmount("100.21", dao.getHumanSumDebt(31))
                    assertTrue(dao.getAllDebtsById(31).maxOf { it.id } > 500)
                }
                open().use { assertAmount("100.21", it.appDao().getHumanSumDebt(31)) }
            } finally { context.deleteDatabase(name) }
        }
    }

    @Test fun exactCreateEditRepeatedDeleteAndInterruptedTransaction() {
        Room.inMemoryDatabaseBuilder(context, AppDataBase::class.java).build().use { db ->
            val dao = db.appDao()
            dao.insertHuman(Human(1, "Large", BigDecimal.ZERO, "EUR"))
            dao.saveDebt(Debt(1, amount("999999999999999999.98"), 1, null, "2026-11-15"))
            dao.saveDebt(Debt(2, amount("0.01"), 1, null, "2026-11-15"))
            assertAmount("999999999999999999.99", dao.getHumanSumDebt(1))
            dao.reviseDebt(Debt(1, amount("-0.1"), 1, null, "2026-11-15"))
            assertAmount("-0.09", dao.getHumanSumDebt(1))
            val debt = dao.getAllDebtsById(1).single { it.id == 2 }
            dao.removeDebt(debt); dao.removeDebt(debt)
            assertAmount("-0.1", dao.getHumanSumDebt(1))
            try {
                db.runInTransaction {
                    dao.saveDebt(Debt(0, amount("0.2"), 1, null, "2026-11-15"))
                    error("interrupt before commit")
                }
            } catch (_: IllegalStateException) { }
            assertEquals(1, dao.getAllDebtsById(1).size)
            assertAmount("-0.1", dao.getHumanSumDebt(1))
        }
    }

    @Test fun failedMigrationLeavesOldSchemaAndRecordsAvailableForRetry() {
        val name = "r1b-failed-migration"
        legacy(name, 14)
        val failing = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DatabaseMigrations.MIGRATION_14_15.migrate(db)
                error("failure after conversion")
            }
        }
        try {
            Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(failing).build().use { db ->
                try { db.appDao().getAllHuman(); fail("migration should fail") } catch (_: IllegalStateException) { }
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertEquals(14, db.version)
                db.rawQuery("SELECT sumDebt, typeof(sumDebt) FROM Human WHERE id = 31", null).use {
                    assertTrue(it.moveToFirst()); assertEquals("real", it.getString(1)); assertEquals(100.01, it.getDouble(0), 0.0)
                }
            }
            Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build().use {
                assertAmount("100.01", it.appDao().getHumanSumDebt(31))
            }
        } finally { context.deleteDatabase(name) }
    }
}

private inline fun <T> AppDataBase.use(block: (AppDataBase) -> T): T = try { block(this) } finally { close() }
