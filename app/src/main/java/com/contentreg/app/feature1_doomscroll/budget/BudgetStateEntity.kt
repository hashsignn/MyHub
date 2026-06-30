package com.contentreg.app.feature1_doomscroll.budget

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * M1.2 — Room row for [BudgetState]. Single-row table (always [SINGLETON_ID]); the budget is one
 * shared value, so there is never more than one row. Kept separate from the domain [BudgetState]
 * so the domain stays Android-free and testable.
 */
@Entity(tableName = "budget_state")
data class BudgetStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val usedMs: Long,
    val windowStartMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

fun BudgetStateEntity.toDomain(): BudgetState = BudgetState(usedMs = usedMs, windowStartMs = windowStartMs)

fun BudgetState.toEntity(): BudgetStateEntity =
    BudgetStateEntity(id = BudgetStateEntity.SINGLETON_ID, usedMs = usedMs, windowStartMs = windowStartMs)
