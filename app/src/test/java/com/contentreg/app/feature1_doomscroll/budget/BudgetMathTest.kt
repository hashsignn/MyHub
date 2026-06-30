package com.contentreg.app.feature1_doomscroll.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1.2 — unit tests for the budget arithmetic. This is where process-death/window bugs hide, so
 * the pure [BudgetMath] is covered directly on the JVM.
 */
class BudgetMathTest {

    // A fixed point in time that sits cleanly inside one hour window.
    private val hour = BudgetMath.HOUR_MS
    private val baseNow = 1_000L * hour + 5_000L // 5s into some hour window
    private val windowStart = BudgetMath.windowStartFor(baseNow)

    @Test
    fun `window start floors to the hour`() {
        assertEquals(1_000L * hour, windowStart)
        assertEquals(windowStart, BudgetMath.windowStartFor(baseNow + 100L))
    }

    @Test
    fun `accumulates elapsed time only while counting`() {
        val start = BudgetState(usedMs = 0L, windowStartMs = windowStart)
        val after = BudgetMath.advance(start, baseNow, deltaMs = 1_000L, counting = true)
        assertEquals(1_000L, after.usedMs)
        assertEquals(windowStart, after.windowStartMs)
    }

    @Test
    fun `does not accumulate when not counting`() {
        val start = BudgetState(usedMs = 3_000L, windowStartMs = windowStart)
        val after = BudgetMath.advance(start, baseNow, deltaMs = 1_000L, counting = false)
        assertEquals(3_000L, after.usedMs)
    }

    @Test
    fun `negative delta never credits budget`() {
        val start = BudgetState(usedMs = 3_000L, windowStartMs = windowStart)
        val after = BudgetMath.advance(start, baseNow, deltaMs = -10_000L, counting = true)
        assertEquals(3_000L, after.usedMs)
    }

    @Test
    fun `crossing into a new hour resets usage`() {
        val start = BudgetState(usedMs = 250_000L, windowStartMs = windowStart)
        val nextHourNow = baseNow + hour
        val after = BudgetMath.advance(start, nextHourNow, deltaMs = 1_000L, counting = true)
        assertEquals(BudgetMath.windowStartFor(nextHourNow), after.windowStartMs)
        // Reset to 0 for the new window, then the 1s of this tick is added.
        assertEquals(1_000L, after.usedMs)
    }

    @Test
    fun `new-hour reset happens even when not counting`() {
        val start = BudgetState(usedMs = 250_000L, windowStartMs = windowStart)
        val after = BudgetMath.advance(start, baseNow + hour, deltaMs = 1_000L, counting = false)
        assertEquals(0L, after.usedMs)
    }

    @Test
    fun `remaining and exhaustion track the allowance`() {
        val budgetMs = 5L * 60L * 1000L // 5 minutes
        val partway = BudgetState(usedMs = 60_000L, windowStartMs = windowStart)
        assertEquals(240_000L, BudgetMath.remainingMs(partway, budgetMs))
        assertFalse(BudgetMath.isExhausted(partway, budgetMs))

        val spent = BudgetState(usedMs = budgetMs, windowStartMs = windowStart)
        assertEquals(0L, BudgetMath.remainingMs(spent, budgetMs))
        assertTrue(BudgetMath.isExhausted(spent, budgetMs))

        val over = BudgetState(usedMs = budgetMs + 10_000L, windowStartMs = windowStart)
        assertEquals(0L, BudgetMath.remainingMs(over, budgetMs))
        assertTrue(BudgetMath.isExhausted(over, budgetMs))
    }
}
