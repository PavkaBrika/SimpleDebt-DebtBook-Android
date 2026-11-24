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
                    assertNull(person.photo)
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

    @Test fun migrate19LabelsOpeningSavingsAndKeepsSameDayTopUps() {
        val name = "goal-opening-migration-19"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("database.AppDataBase/19.json")
            .bufferedReader().use { JsonParser.parseReader(it) }.asJsonObject.getAsJsonObject("database")
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                schema.getAsJsonArray("entities").forEach {
                    val table = it.asJsonObject.get("tableName").asString
                    fun create(sql: String) = db.execSQL(sql.replace("\u0024{TABLE_NAME}", table))
                    create(it.asJsonObject.get("createSql").asString)
                    it.asJsonObject.getAsJsonArray("indices")?.forEach { index -> create(index.asJsonObject.get("createSql").asString) }
                }
                db.execSQL("INSERT INTO GoalData VALUES (1, 'With savings', '100', '20', 'RUB', NULL, 1000, NULL, '0', NULL)")
                db.execSQL("INSERT INTO GoalData VALUES (2, 'Without savings', '100', '10', 'RUB', NULL, 1000, NULL, '0', NULL)")
                db.execSQL("INSERT INTO GoalDepositData VALUES (1, '10', 1000, 1), (2, '10', 1001, 1), (3, '10', 1001, 2)")
                db.version = 19
            }
            Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build().use { db ->
                val rows = db.appDao().getAllGoalDeposits().sortedBy { it.id }
                assertEquals(listOf(true, false, false), rows.map { it.isInitial })
            }
        } finally { context.deleteDatabase(name) }
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

    @Test fun excessiveGoalWithdrawalLeavesBalanceAndHistoryUntouched() {
        Room.inMemoryDatabaseBuilder(context, AppDataBase::class.java).build().use { db ->
            val dao = db.appDao()
            val date = Date()
            dao.insertGoal(GoalData(1, "Trip", amount("100"), amount("10"), "RUB", null, date, null))
            dao.addGoalDeposit(GoalDepositData(0, amount("5"), date, 1))
            assertThrows(IllegalArgumentException::class.java) {
                dao.addGoalDeposit(GoalDepositData(0, amount("-15.01"), date, 1))
            }
            assertAmount("15", dao.getGoal(1)!!.savedSum)
            assertAmount("5", dao.getGoalDepositsByGoalId(1).single().sum)
            dao.addGoalDeposit(GoalDepositData(0, amount("-15"), date, 1))
            assertAmount("0", dao.getGoal(1)!!.savedSum)
            assertEquals(2, dao.getGoalDepositsByGoalId(1).size)
        }
    }

    @Test fun failedMigrationLeavesOldSchemaAndRecordsAvailableForRetry() {
        val name = "r1b-failed-migration"
        legacy(name, 14)
        var converted = false
        val failing = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DatabaseMigrations.MIGRATION_14_15.migrate(db)
                converted = true
                error("failure after conversion")
            }
        }
        try {
            Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(failing, DatabaseMigrations.migration15To16("USD"), DatabaseMigrations.MIGRATION_16_17, DatabaseMigrations.MIGRATION_17_18, DatabaseMigrations.MIGRATION_18_19, DatabaseMigrations.MIGRATION_19_20, DatabaseMigrations.MIGRATION_20_21).build().use { db ->
                try { db.appDao().getAllHuman(); fail("migration should fail") } catch (_: IllegalStateException) { }
            }
            assertTrue("Failing conversion was not exercised", converted)
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
    @Test fun migrate15CapturesFinanceCurrencyAndNeverReinterpretsOldInstants() {
        val name = "r1b-migration-15"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("database.AppDataBase/15.json").bufferedReader().use { JsonParser.parseReader(it) }
            .asJsonObject.getAsJsonObject("database").getAsJsonArray("entities")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            schema.forEach { item ->
                db.execSQL(item.asJsonObject.get("createSql").asString.replace("\u0024{TABLE_NAME}", item.asJsonObject.get("tableName").asString))
            }
            db.execSQL("INSERT INTO FinanceCategoryData VALUES (71, 'Category', 1, '#123456', 123)")
            db.execSQL("INSERT INTO FinanceData VALUES (81, '0.01', 1735689600123, 'original', 71)")
            db.version = 15
        }
        try {
            Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(*DatabaseMigrations.forCurrency("EUR")).build().use { db ->
                val row = db.appDao().getAllFinances().single()
                assertEquals("EUR", row.currency)
                assertNull(row.calendarDay)
                assertEquals(1735689600123L, row.date.time)
                db.appDao().insertFinance(entity.FinanceData(82, amount("100"), Date(1735689600123L), null, 71, "JPY", "2026-11-15"))
            }
            Room.databaseBuilder(context, AppDataBase::class.java, name).addMigrations(*DatabaseMigrations.forCurrency("USD")).build().use { db ->
                val rows = db.appDao().getAllFinances().sortedBy { it.id }
                assertEquals(listOf("EUR", "JPY"), rows.map { it.currency })
                assertEquals("2026-11-15", rows[1].calendarDay)
            }
        } finally { context.deleteDatabase(name) }
    }

}

private inline fun <T> AppDataBase.use(block: (AppDataBase) -> T): T = try { block(this) } finally { close() }
