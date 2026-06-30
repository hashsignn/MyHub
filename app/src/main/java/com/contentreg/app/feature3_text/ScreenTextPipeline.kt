package com.contentreg.app.feature3_text

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * M3.0 — in-process bus that carries [ScreenSnapshot] values from [ForegroundService] to any
 * downstream consumer. Mirrors the [ForegroundAppTracker] / [ScrollMonitor] pattern: the
 * accessibility service pushes in via [push]; feature logic (M3.1/M3.2) collects out.
 *
 * Buffer of 1 with DROP_OLDEST ensures a slow classifier never stalls the service thread.
 */
object ScreenTextPipeline {

    private val _snapshots = MutableSharedFlow<ScreenSnapshot>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val snapshots: SharedFlow<ScreenSnapshot> = _snapshots

    /** Called only from [ForegroundService] after each debounced text read. */
    internal fun push(snapshot: ScreenSnapshot) {
        _snapshots.tryEmit(snapshot)
    }
}