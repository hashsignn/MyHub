package com.contentreg.app.core.sensing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M1.0 — single source of truth for "what app is in the foreground right now".
 *
 * [ForegroundService] writes to this whenever the active window changes; UI and (later) the
 * budget tracker (M1.2) and text reader (M3.0) read from it. It is an `object` because there is
 * exactly one foreground app on the device at any moment, and the AccessibilityService is itself a
 * process-wide singleton — so a single in-memory holder is the natural fit. State here is
 * intentionally *not* persisted: it is live, moment-to-moment signal, rebuilt the instant the
 * service reconnects.
 */
object ForegroundAppTracker {

    /** A foreground-window observation. [packageName] is null before the first event arrives. */
    data class ForegroundApp(
        val packageName: String?,
        val className: String?,
        val timestampMs: Long,
    )

    private val _current = MutableStateFlow(
        ForegroundApp(packageName = null, className = null, timestampMs = 0L),
    )

    /** Observable current foreground app. Collect this from UI to react to window changes. */
    val current: StateFlow<ForegroundApp> = _current.asStateFlow()

    /** Convenience accessor for the latest foreground package name (may be null). */
    val currentPackage: String?
        get() = _current.value.packageName

    /**
     * Called by [ForegroundService] on every `TYPE_WINDOW_STATE_CHANGED`. Ignores repeated events
     * for the same package so collectors only see genuine foreground transitions.
     */
    fun update(packageName: String?, className: String?, timestampMs: Long) {
        if (packageName == null) return
        val previous = _current.value
        if (packageName == previous.packageName) return
        _current.value = ForegroundApp(packageName, className, timestampMs)
    }
}
