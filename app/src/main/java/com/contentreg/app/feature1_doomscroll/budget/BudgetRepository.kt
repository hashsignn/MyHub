package com.contentreg.app.feature1_doomscroll.budget

/**
 * M1.2 — persistence boundary for the budget state. An interface so [TimeBudgetTracker] can be
 * unit-tested against an in-memory fake without Room.
 */
interface BudgetRepository {

    /** Loads the persisted budget state, or null if nothing has been saved yet. */
    suspend fun load(): BudgetState?

    /** Persists [state] so it survives process death. */
    suspend fun save(state: BudgetState)
}

/** Room-backed [BudgetRepository] (M1.2). */
class BudgetRepositoryRoom(private val dao: BudgetDao) : BudgetRepository {

    override suspend fun load(): BudgetState? = dao.get()?.toDomain()

    override suspend fun save(state: BudgetState) = dao.upsert(state.toEntity())
}
