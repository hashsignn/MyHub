package com.contentreg.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.contentreg.app.feature1_doomscroll.budget.BudgetDao
import com.contentreg.app.feature1_doomscroll.budget.BudgetStateEntity

/**
 * M1.2 — the app's single Room database. Holds the durable budget row now; the URL registry
 * (M2.2) and stats (M4.2) add their own entities/DAOs to this same database later.
 */
@Database(
    entities = [BudgetStateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun budgetDao(): BudgetDao

    companion object {
        private const val DB_NAME = "contentreg.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .build()
    }
}
