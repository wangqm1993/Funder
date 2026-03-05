package com.example.funder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FundHoldingEntity::class, WatchlistEntity::class],
    version = 3,
    exportSchema = false
)
abstract class FundDatabase : RoomDatabase() {
    abstract fun fundDao(): FundDao
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fund_holdings ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS watchlist " +
                    "(fundCode TEXT NOT NULL PRIMARY KEY, " +
                    "fundName TEXT NOT NULL, " +
                    "createTime INTEGER NOT NULL)"
                )
            }
        }
    }
}
