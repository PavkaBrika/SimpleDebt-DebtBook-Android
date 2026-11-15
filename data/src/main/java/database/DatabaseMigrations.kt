package database


import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.breckneck.deptbook.domain.util.LedgerDate
import com.breckneck.deptbook.domain.util.MoneyPolicy
import java.math.BigDecimal

object DatabaseMigrations {
    val MIGRATION_5_11 = object : Migration(5 ,11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS 'FinanceCategoryData' ('id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'name' TEXT NOT NULL, 'state' INT NOT NULL, 'color' TEXT NOT NULL, 'image' INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE IF NOT EXISTS 'FinanceData' ('id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'sum' REAL NOT NULL, 'date' INTEGER NOT NULL, 'info' TEXT, 'financeCategoryId' INTEGER NOT NULL, FOREIGN KEY('financeCategoryId') REFERENCES 'FinanceCategoryData'('id') ON UPDATE NO ACTION ON DELETE CASCADE)")
            insertInitialFinanceCategoryData(database = database)
        }
    }

    val MIGRATION_11_12 = object: Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val oldSequence = database.query("SELECT seq FROM sqlite_sequence WHERE name = 'Debt'").use { if (it.moveToFirst()) it.getLong(0) else 0L }
            database.execSQL("CREATE TABLE IF NOT EXISTS 'Debt_new'('id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'sum' REAL NOT NULL, 'idHuman' INTEGER NOT NULL, 'info' TEXT, 'date' TEXT NOT NULL)")
            database.execSQL("INSERT INTO 'Debt_new' SELECT * FROM 'Debt'")
            database.execSQL("DROP TABLE IF EXISTS 'Debt'")
            database.execSQL("ALTER TABLE 'Debt_new' RENAME TO 'Debt'")
            database.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = 'Debt'", arrayOf(oldSequence))
        }
    }

    val MIGRATION_12_13 = object: Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val oldSequence = database.query("SELECT seq FROM sqlite_sequence WHERE name = 'Human'").use { if (it.moveToFirst()) it.getLong(0) else 0L }
            database.execSQL("CREATE TABLE IF NOT EXISTS 'Human_new'('id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'name' TEXT NOT NULL, 'sumDebt' REAL NOT NULL, 'currency' TEXT NOT NULL)")
            database.execSQL("INSERT INTO 'Human_new' SELECT * FROM 'Human'")
            database.execSQL("DROP TABLE IF EXISTS 'Human'")
            database.execSQL("ALTER TABLE 'Human_new' RENAME TO 'Human'")
            database.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = 'Human'", arrayOf(oldSequence))
        }
    }

    val MIGRATION_13_14 = object: Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS 'GoalData' ('id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'name' TEXT NOT NULL, 'sum' REAL NOT NULL, 'savedSum' REAL NOT NULL, 'currency' TEXT NOT NULL, 'photoPath' TEXT, 'creationDate' INTEGER NOT NULL, 'goalDate' INTEGER)")
            database.execSQL("CREATE TABLE IF NOT EXISTS 'GoalDepositData' ('id' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 'sum' REAL NOT NULL, 'date' INTEGER NOT NULL, 'goalId' INTEGER NOT NULL, FOREIGN KEY('goalId') REFERENCES 'GoalData'('id') ON UPDATE NO ACTION ON DELETE CASCADE)")
        }
    }

    fun insertInitialFinanceCategoryData(database: SupportSQLiteDatabase) {
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('1', 'Health', '1', '#EF9A9A', '0x2764')")
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('2', 'Entertainment', '1', '#CE93D8', '0x1F3AC')")
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('3', 'Home', '1', '#9FA8DA', '0x1F3E0')")
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('4', 'Education', '1', '#81D4FA', '0x1F393')")
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('5', 'Gifts', '1', '#80CBC4', '0x1F381')")
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('6', 'Food', '1', '#C5E1A5', '0x1F37D')")
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('7', 'Other', '1', '#FFF59D', '0x1F36D')")

        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('8', 'Salary', '2', '#EF9A9A', '0x1F4B5')")
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('9', 'Gifts', '2', '#CE93D8', '0x1F381')")
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('10', 'Investments', '2', '#CE93D8', '0x1F4BC')")
        database.execSQL("INSERT OR IGNORE INTO 'FinanceCategoryData' ('id', 'name', 'state', 'color', 'image') VALUES ('11', 'Other', '2', '#FFF59D', '0x1F4CB')")
    }
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Read every row before altering tables. Room wraps migration and validation in one transaction.
            val tables = listOf("Human", "Debt", "FinanceData", "GoalData", "GoalDepositData")
            val rows = tables.associateWith { table ->
                database.query("SELECT * FROM `$table`").use { cursor ->
                    buildList<MutableMap<String, Any?>> {
                        while (cursor.moveToNext()) {
                            add(cursor.columnNames.mapIndexed { index, column ->
                                column to when {
                                    cursor.isNull(index) -> null
                                    column in listOf("sum", "sumDebt", "savedSum") -> {
                                        val value = cursor.getDouble(index)
                                        require(value.isFinite()) { "Invalid legacy amount; migration cancelled" }
                                        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
                                    }
                                    column in listOf("id", "idHuman", "financeCategoryId", "goalId", "creationDate", "goalDate") ||
                                        (column == "date" && table != "Debt") -> cursor.getLong(index)
                                    else -> cursor.getString(index)
                                }
                            }.toMap().toMutableMap())
                        }
                    }
                }
            }
            val sequences = database.query("SELECT name, seq FROM sqlite_sequence").use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getLong(1)) }
            }
            fun amount(row: Map<String, Any?>, key: String) = BigDecimal(row[key] as String)
            rows.getValue("Human").forEach { human ->
                val operations = rows.getValue("Debt").filter { it["idHuman"] == human["id"] }
                    .fold(BigDecimal.ZERO) { total, row -> total + amount(row, "sum") }
                human["openingAdjustment"] = MoneyPolicy.stored(amount(human, "sumDebt") - operations)
            }
            rows.getValue("GoalData").forEach { goal ->
                val operations = rows.getValue("GoalDepositData").filter { it["goalId"] == goal["id"] }
                    .fold(BigDecimal.ZERO) { total, row -> total + amount(row, "sum") }
                goal["openingAdjustment"] = MoneyPolicy.stored(amount(goal, "savedSum") - operations)
            }
            rows.getValue("Debt").forEach { debt ->
                val original = debt["date"] as String
                debt["legacyDate"] = original
                debt["date"] = LedgerDate.canonical(original) ?: original
            }
            // Children first prevents ON DELETE CASCADE from discarding data.
            for (table in listOf("FinanceData", "GoalDepositData", "Debt", "Human", "GoalData"))
                database.execSQL("DROP TABLE `$table`")
            database.execSQL("CREATE TABLE Human (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, sumDebt TEXT NOT NULL, currency TEXT NOT NULL, openingAdjustment TEXT NOT NULL)")
            database.execSQL("CREATE TABLE Debt (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sum TEXT NOT NULL, idHuman INTEGER NOT NULL, info TEXT, date TEXT NOT NULL, legacyDate TEXT)")
            database.execSQL("CREATE TABLE FinanceData (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sum TEXT NOT NULL, date INTEGER NOT NULL, info TEXT, financeCategoryId INTEGER NOT NULL, FOREIGN KEY(financeCategoryId) REFERENCES FinanceCategoryData(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            database.execSQL("CREATE TABLE GoalData (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, sum TEXT NOT NULL, savedSum TEXT NOT NULL, currency TEXT NOT NULL, photoPath TEXT, creationDate INTEGER NOT NULL, goalDate INTEGER, openingAdjustment TEXT NOT NULL)")
            database.execSQL("CREATE TABLE GoalDepositData (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sum TEXT NOT NULL, date INTEGER NOT NULL, goalId INTEGER NOT NULL, FOREIGN KEY(goalId) REFERENCES GoalData(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            for (table in listOf("Human", "GoalData", "Debt", "FinanceData", "GoalDepositData")) {
                for (row in rows.getValue(table)) {
                    val columns = row.keys.joinToString(",") { "`$it`" }
                    val placeholders = row.keys.joinToString(",") { "?" }
                    database.execSQL("INSERT INTO `$table` ($columns) VALUES ($placeholders)", row.values.toTypedArray())
                }
                sequences[table]?.let { sequence ->
                    database.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf(table))
                    database.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)", arrayOf<Any>(table, sequence))
                }
            }
        }
    }
    val ALL = arrayOf(MIGRATION_5_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
}
