package com.contentreg.app.feature1_doomscroll.budget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M1.2 — accumulates scroll-time across all target apps against one shared budget, and persists it
 * so the count survives process death.
 *
 * It owns no clock or threads: a caller (the AccessibilityService) calls [tick] on a fixed cadence,
 * and the dependencies are injected as lambdas so the whole class is unit-testable with fakes:
 *  - [foregroundProvider]: the current foreground package (from `ForegroundAppTracker`).
 *  - [scrollingProvider]: whether a target-app scroll happened recently (from `ScrollMonitor`).
 *  - [targetProvider]: the set of apps that count.
 *  - [clock]: current time in ms (swappable in tests).
 *
 * Authoritative state lives in memory ([state]) and is written to [repository] only when it
 * changes — so disk I/O happens roughly once per second while actively scrolling a feed, and not
 * at all while idle.
 */
class TimeBudgetTracker(
    private val repository: BudgetRepository,
    private val foregroundProvider: () -> String?,
    private val scrollingProvider: (nowMs: Long) -> Boolean,
    private val targetProvider: () -> Set<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow(BudgetState(usedMs = 0L, windowStartMs = BudgetMath.windowStartFor(clock())))
    val state: StateFlow<BudgetState> = _state.asStateFlow()

    /** 0 = no tick yet. Holds the timestamp of the previous [tick] so we can measure elapsed time. */
    private var lastTickMs: Long = 0L

    /**
     * Loads any persisted state. Call once before the first [tick]. If the persisted window is a
     * past hour, the first [tick] will roll it forward and reset usage.
     */
    suspend fun loadInitial() {
        repository.load()?.let { _state.value = it }
        lastTickMs = clock()
    }

    /**
     * One step of the budget clock. Adds the time since the previous tick to the budget *iff* a
     * target app is foreground and the user scrolled recently. Persists only on change.
     */
    suspend fun tick() {
        val now = clock()
        val delta = if (lastTickMs == 0L) 0L else now - lastTickMs
        lastTickMs = now

        val foreground = foregroundProvider()
        val counting = foreground != null &&
            foreground in targetProvider() &&
            scrollingProvider(now)

        val updated = BudgetMath.advance(_state.value, now, delta, counting)
        if (updated != _state.value) {
            _state.value = updated
            repository.save(updated)
        }
    }

    fun remainingMs(budgetMs: Long): Long = BudgetMath.remainingMs(_state.value, budgetMs)

    fun isExhausted(budgetMs: Long): Boolean = BudgetMath.isExhausted(_state.value, budgetMs)
}
