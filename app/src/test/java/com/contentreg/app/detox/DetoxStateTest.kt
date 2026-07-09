package com.contentreg.app.detox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure detox state math (active/remaining derived from an absolute end-time). */
class DetoxStateTest {

    private val now = 1_000_000L

    @Test
    fun `active while end-time is in the future`() {
        assertTrue(DetoxState(endTimeMs = now + 60_000, allowedApps = emptySet()).isActive(now))
    }

    @Test
    fun `not active once end-time has passed`() {
        assertFalse(DetoxState(endTimeMs = now - 1, allowedApps = emptySet()).isActive(now))
    }

    @Test
    fun `end-time exactly now is not active (strictly greater)`() {
        assertFalse(DetoxState(endTimeMs = now, allowedApps = emptySet()).isActive(now))
    }

    @Test
    fun `INACTIVE constant is never active`() {
        assertFalse(DetoxState.INACTIVE.isActive(now))
    }

    @Test
    fun `remaining is the gap to end-time while active`() {
        assertEquals(60_000L, DetoxState(now + 60_000, emptySet()).remainingMs(now))
    }

    @Test
    fun `remaining clamps to zero once expired`() {
        assertEquals(0L, DetoxState(now - 5_000, emptySet()).remainingMs(now))
    }
}
