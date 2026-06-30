package com.contentreg.app.feature1_doomscroll.budget

/**
 * M1.2 — pure budget arithmetic. No Android, no I/O, no clock of its own: everything is passed in,
 * so this is fully unit-testable and is the single place the "where bugs hide" logic lives.
 *
 * Hour windows are aligned to epoch-hour boundaries ([windowStartFor]). That means the budget
 * refreshes every hour on the hour (UTC). M1.4 may refine this to the local-clock hour and make
 * the window length configurable; the rest of the app only ever talks to this object.
 */
object BudgetMath {

    const val HOUR_MS: Long = 60L * 60L * 1000L

    /** Start (epoch ms) of the hour window that [nowMs] falls into. */
    fun windowStartFor(nowMs: Long): Long = nowMs - (nowMs % HOUR_MS)

    /**
     * Advances [state] by [deltaMs] of elapsed wall-clock time.
     *
     * First, the window is rolled forward if [nowMs] has crossed into a new hour (resetting
     * [BudgetState.usedMs] to 0). Then, only if [counting] is true (a target app is foreground AND
     * the user scrolled recently), the elapsed time is added. [deltaMs] is clamped to be
     * non-negative so a backwards clock jump can never *credit* budget.
     */
    fun advance(state: BudgetState, nowMs: Long, deltaMs: Long, counting: Boolean): BudgetState {
        val window = windowStartFor(nowMs)
        val rolled = if (window != state.windowStartMs) {
            BudgetState(usedMs = 0L, windowStartMs = window)
        } else {
            state
        }
        if (!counting) return rolled
        val safeDelta = deltaMs.coerceAtLeast(0L)
        return rolled.copy(usedMs = rolled.usedMs + safeDelta)
    }

    /** Remaining budget in ms given a [budgetMs] allowance, never negative. */
    fun remainingMs(state: BudgetState, budgetMs: Long): Long =
        (budgetMs - state.usedMs).coerceAtLeast(0L)

    /** True once consumption has reached/passed the [budgetMs] allowance (→ block, M1.3). */
    fun isExhausted(state: BudgetState, budgetMs: Long): Boolean = state.usedMs >= budgetMs
}
