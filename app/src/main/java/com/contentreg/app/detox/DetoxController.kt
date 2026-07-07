package com.contentreg.app.detox

import com.contentreg.app.core.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The single source of truth for the Digital Detox lockdown. Wraps [SettingsStore] so the UI, the
 * accessibility enforcement layer and the unlock flow all read/write the same persisted state.
 *
 * The controller holds no timers of its own: "active" is a pure function of the stored end-time vs
 * the wall clock, so nothing has to be running for the lockdown to expire correctly — the next read
 * simply sees it as over.
 */
class DetoxController(private val settings: SettingsStore) {

    /** Live detox state (end-time + allow-list) for the UI and enforcement collectors. */
    val state: Flow<DetoxState> =
        combine(settings.detoxEndTimeMs, settings.detoxAllowedApps) { endTimeMs, allowedApps ->
            DetoxState(endTimeMs, allowedApps)
        }

    /** True while a lockdown window is currently open. */
    val isActive: Flow<Boolean> = state.map { it.isActive() }

    /** The signature phrase the user set (empty if they haven't set one yet). */
    val signature: Flow<String> = settings.detoxSignature

    /** Whether a signature has been configured — required before a detox can be armed. */
    val hasSignature: Flow<Boolean> = settings.detoxSignature.map { it.isNotBlank() }

    /** Sets (or replaces) the confirmation signature. Compared with [matchesSignature]. */
    suspend fun setSignature(signature: String) {
        settings.setDetoxSignature(signature.trim())
    }

    /** Whitespace- and case-insensitive match against the stored signature (never matches when empty). */
    suspend fun matchesSignature(input: String): Boolean {
        val stored = settings.detoxSignature.first()
        return stored.isNotBlank() && stored.equals(input.trim(), ignoreCase = true)
    }

    /**
     * Arms a detox for [durationMs] from now, confining the user to [allowedApps]. Caller is
     * responsible for having already confirmed the signature.
     */
    suspend fun start(durationMs: Long, allowedApps: Set<String>) {
        val end = System.currentTimeMillis() + durationMs.coerceAtLeast(0L)
        settings.setDetox(endTimeMs = end, allowedApps = allowedApps)
    }

    /** Ends the current detox immediately (charity early-unlock, or timer expiry cleanup). */
    suspend fun end() {
        settings.setDetox(endTimeMs = 0L, allowedApps = emptySet())
    }

    /** One-shot snapshot for callers that can't collect the flow (e.g. an Activity's onCreate). */
    suspend fun snapshot(): DetoxState = state.first()
}
