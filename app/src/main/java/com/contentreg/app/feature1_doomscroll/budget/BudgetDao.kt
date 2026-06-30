package com.contentreg.app.feature1_doomscroll.budget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * M1.2 — DAO for the single-row budget table. REPLACE-on-insert makes [upsert] a true upsert for
 * the one fixed row.
 */
@Dao
interface BudgetDao {

    @Query("SELECT * FROM budget_state WHERE id = ${BudgetStateEntity.SINGLETON_ID} LIMIT 1")
    suspend fun get(): BudgetStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetStateEntity)
}
