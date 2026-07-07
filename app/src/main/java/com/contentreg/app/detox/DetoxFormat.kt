package com.contentreg.app.detox

/** Formatting helpers for detox durations, shared by the overlay countdown and the home banner. */
object DetoxFormat {

    /** A ticking clock, e.g. "1:23:45" or "12:07". */
    fun hms(remainingMs: Long): String {
        val totalSec = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L // round up so it never shows 0 early
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** A coarse label for banners, e.g. "1h 23m" or "12m". */
    fun compact(remainingMs: Long): String {
        val totalMin = (remainingMs.coerceAtLeast(0L) + 59_999L) / 60_000L // round up to the minute
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
