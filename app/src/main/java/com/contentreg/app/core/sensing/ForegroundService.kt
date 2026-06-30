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
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Ignore our own UI and transient system windows so we track real foreground apps only.
        if (packageName == applicationContext.packageName) return

        ForegroundAppTracker.update(
            packageName = packageName,
            className = event.className?.toString(),
            timestampMs = System.currentTimeMillis(),
        )
        Log.d(TAG, "Foreground app: $packageName")
    }

    override fun onInterrupt() {
        // Required override. Nothing to interrupt — we hold no long-running feedback.
    }

    companion object {
        private const val TAG = "ForegroundService"
    }
}
