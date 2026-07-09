package com.contentreg.app.feature3_text

/**
 * The package a text/URL block is currently shown for, or null if no text block is active.
 *
 * [TextBlockDecider] writes it when it raises/clears the TEXT overlay reason; the accessibility
 * service's periodic ticker reads it to clear a stale block once the user has actually left that
 * app (using the reliable active-window package, not the noisy per-event stream). In-memory
 * hand-off, same pattern as [com.contentreg.app.core.sensing.ForegroundAppTracker].
 */
object TextBlockState {
    @Volatile
    var blockedPackage: String? = null
}
