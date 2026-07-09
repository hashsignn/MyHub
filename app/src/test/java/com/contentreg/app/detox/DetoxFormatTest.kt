package com.contentreg.app.detox

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for countdown/banner formatting, including the round-up and negative-clamp edges. */
class DetoxFormatTest {

    @Test
    fun `hms shows mm ss under an hour`() {
        assertEquals("0:59", DetoxFormat.hms(59_000))
        assertEquals("1:01", DetoxFormat.hms(61_000))
    }

    @Test
    fun `hms shows h mm ss at or above an hour`() {
        assertEquals("1:00:00", DetoxFormat.hms(3_600_000))
        assertEquals("1:01:01", DetoxFormat.hms(3_661_000))
    }

    @Test
    fun `hms rounds up sub-second remainder so it never shows 0 early`() {
        assertEquals("0:01", DetoxFormat.hms(1))
    }

    @Test
    fun `hms clamps negative input to zero`() {
        assertEquals("0:00", DetoxFormat.hms(-5_000))
    }

    @Test
    fun `compact shows minutes under an hour`() {
        assertEquals("30m", DetoxFormat.compact(30L * 60_000))
    }

    @Test
    fun `compact shows hours and minutes at or above an hour`() {
        assertEquals("1h 0m", DetoxFormat.compact(3_600_000))
        assertEquals("1h 30m", DetoxFormat.compact(90L * 60_000))
    }

    @Test
    fun `compact rounds up a partial minute`() {
        assertEquals("2m", DetoxFormat.compact(60_001))
    }
}
