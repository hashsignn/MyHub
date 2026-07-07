package com.contentreg.app.detox

/**
 * Immutable snapshot of the Digital Detox lockdown.
 *
 * A detox confines the user to a chosen set of apps for a fixed wall-clock window. State is derived
 * entirely from an absolute end-time so it survives process death and reboots without cheating the
 * clock: whatever time is left when the app comes back is the time still owed.
 *
 * @param endTimeMs   epoch-millis when the lockdown ends; 0 (or past) means no detox is running.
 * @param allowedApps packages the user may still open during the lockdown (extras beyond the
 *                    always-allowed set — the launcher, system UI and this app itself — which the
 *                    enforcement layer permits implicitly).
 */
data class DetoxState(
    val endTimeMs: Long,
    val allowedApps: Set<String>,
) {
    /** True while the lockdown window is still open relative to [now]. */
    fun isActive(now: Long = System.currentTimeMillis()): Boolean = endTimeMs > now

    /** Milliseconds remaining, clamped at 0. */
    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        (endTimeMs - now).coerceAtLeast(0L)

    companion object {
        val INACTIVE = DetoxState(endTimeMs = 0L, allowedApps = emptySet())
    }
}
