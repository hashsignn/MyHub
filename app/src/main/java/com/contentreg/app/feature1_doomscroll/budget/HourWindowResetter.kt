package com.contentreg.app.feature1_doomscroll.budget

/**
 * M1.4 — timestamp-based hour-boundary reset.
 *
 * The live tick path ([TimeBudgetTracker]) already rolls the window forward via [BudgetMath], so
 * this is the *backup* used when the service isn't running: it inspects the persisted state and, if
 * its window belongs to an earlier hour, writes a fresh zeroed state. Idempotent — safe to call
 * from a periodic worker or on demand.
 */
object HourWindowResetter {

    /** Returns true if a reset was written (the persisted window was stale). */
    suspend fun resetIfStale(
        repository: BudgetRepository,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val state = repository.load() ?: return false
        val currentWindow = BudgetMath.windowStartFor(nowMs)
        if (state.windowStartMs == currentWindow) return false
        repository.save(BudgetState(usedMs = 0L, windowStartMs = currentWindow))
        return true
    }
}
