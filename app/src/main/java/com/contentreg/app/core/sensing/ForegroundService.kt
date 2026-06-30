package com.contentreg.app.core.sensing

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * M1.0 — the single AccessibilityService for the whole app.
 *
 * Despite the name (kept to match ARCHITECTURE.md), this is an [AccessibilityService], not an
 * Android foreground `Service`. It is the one sensing layer every feature reads from:
 *  - M1.0: `TYPE_WINDOW_STATE_CHANGED` → which app is in the foreground.
 *  - M1.1: `TYPE_VIEW_SCROLLED` → scroll activity inside target apps (added next).
 *  - M3.0: walking `getRootInActiveWindow()` → on-screen text (added in Phase 3).
 *
 * It deliberately holds no business logic. It only translates raw accessibility events into the
 * shared in-memory signal ([ForegroundAppTracker]); decisions (budget, blocking) live elsewhere so
 * this class stays cheap and is never the bottleneck on the event hot path.
 */
class ForegroundService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected; foreground sensing active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        // Ignore our own UI so we never count our own windows/scrolls as activity.
        if (packageName == applicationContext.packageName) return

        val now = System.currentTimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // M1.0 — foreground-app detection.
                ForegroundAppTracker.update(
                    packageName = packageName,
                    className = event.className?.toString(),
                    timestampMs = now,
                )
                Log.d(TAG, "Foreground app: $packageName")
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // M1.1 — scroll detection. ScrollMonitor drops anything not from a target app.
                ScrollMonitor.recordScroll(packageName, now)
            }
        }
    }

    override fun onInterrupt() {
        // Required override. Nothing to interrupt — we hold no long-running feedback.
    }

    companion object {
        private const val TAG = "ForegroundService"
    }
}
