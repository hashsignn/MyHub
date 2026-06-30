package com.contentreg.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.contentreg.app.feature1_doomscroll.budget.BudgetDao
import com.contentreg.app.feature1_doomscroll.budget.BudgetStateEntity
import com.contentreg.app.feature2_url.registry.BlockedEntry
import com.contentreg.app.feature2_url.registry.RegistryDao

/**
 * The app's single Room database.
 *  - v1 (M1.2): durable budget state.
 *  - v2 (M2.2): adds the URL block registry.
 * Stats (M4.2) will add to this same database later.
 */
@Database(
    entities = [BudgetStateEntity::class, BlockedEntry::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun budgetDao(): BudgetDao

    abstract fun registryDao(): RegistryDao

    companion object {
        private const val DB_NAME = "contentreg.db"

        /** M2.2 — adds the registry table without touching the existing budget row. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `blocked_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`normalizedKey` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`createdAtMs` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_blocked_entries_normalizedKey` ON `blocked_entries` (`normalizedKey`)",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
