package com.contentreg.app.core.sensing

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.core.data.prefs.SettingsStore
import com.contentreg.app.feature1_doomscroll.budget.BudgetMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M1.0 — the single AccessibilityService for the whole app.
 *
 * Despite the name (kept to match ARCHITECTURE.md), this is an [AccessibilityService], not an
 * Android foreground `Service`. It is the one sensing layer every feature reads from:
 *  - M1.0: `TYPE_WINDOW_STATE_CHANGED` → which app is in the foreground.
 *  - M1.1: `TYPE_VIEW_SCROLLED` → scroll activity inside target apps.
 *  - M1.2: drives the budget tick loop (the service is the always-alive process while enabled).
 *  - M3.0: walking `getRootInActiveWindow()` → on-screen text (added in Phase 3).
 *
 * It deliberately holds no business logic. It only translates raw accessibility events into the
 * shared in-memory signal ([ForegroundAppTracker]); decisions (budget, blocking) live elsewhere so
 * this class stays cheap and is never the bottleneck on the event hot path.
 */
class ForegroundService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected; foreground sensing active.")
        startSettingsSync()
        startBudgetTicker()
        startBlockController()
    }

    /**
     * M1.4 — keep [ScrollMonitor]'s target-app set in sync with the user's settings, so editing the
     * list in SettingsActivity immediately changes what counts (and what the block guards).
     */
    private fun startSettingsSync() {
        serviceScope.launch {
            ServiceLocator.settingsStore.targetApps.collect { apps ->
                ScrollMonitor.targetPackages = apps
            }
        }
    }

    /**
     * M1.2 — drive the budget clock once per second while the service is alive. The tracker decides
     * each tick whether to actually accumulate (target app foreground + recent scroll). State is
     * loaded from disk first so a restart resumes the same hour's count.
     */
    private fun startBudgetTicker() {
        val tracker = ServiceLocator.timeBudgetTracker
        serviceScope.launch {
            tracker.loadInitial()
            while (isActive) {
                tracker.tick()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    /**
     * M1.3 — shows the block overlay when the budget is exhausted AND a target feed app is
     * foreground; hides it otherwise. Because the trigger includes "foreground is a target app",
     * pressing Home or switching away auto-dismisses the block — it covers the feed, never the
     * whole phone. A 1-second ticker keeps the "resets in" countdown live even while idle.
     */
    private fun startBlockController() {
        val tracker = ServiceLocator.timeBudgetTracker
        val settings = ServiceLocator.settingsStore
        val overlay = ServiceLocator.overlayManager
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(TICK_INTERVAL_MS)
            }
        }

        serviceScope.launch {
            combine(
                tracker.state,
                settings.budgetMinutes,
                ForegroundAppTracker.current,
                ticker,
            ) { state, minutes, foreground, _ ->
                val budgetMs = SettingsStore.minutesToMs(minutes)
                val exhausted = BudgetMath.isExhausted(state, budgetMs)
                val pkg = foreground.packageName
                val onTarget = pkg != null && pkg in ScrollMonitor.targetPackages
                val resetInMs = state.windowStartMs + BudgetMath.HOUR_MS - System.currentTimeMillis()
                BlockDecision(shouldBlock = exhausted && onTarget, resetInMs = resetInMs)
            }.collect { decision ->
                withContext(Dispatchers.Main) {
                    if (decision.shouldBlock) {
                        val wasShowing = overlay.isShowing()
                        overlay.show()
                        overlay.updateCountdown(decision.resetInMs)
                        // Count a block only on the transition from not-showing to showing (M4.2).
                        if (!wasShowing && overlay.isShowing()) {
                            launch { ServiceLocator.statsRepository.incrementBlocks() }
                        }
                    } else {
                        overlay.hide()
                    }
                }
            }
        }
    }

    private data class BlockDecision(val shouldBlock: Boolean, val resetInMs: Long)

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

    override fun onDestroy() {
        super.onDestroy()
        // Stop the tickers and tear down any visible block: with the service gone there is nothing
        // left to keep it in sync. (onDestroy is delivered on the main thread.)
        serviceScope.cancel()
        ServiceLocator.overlayManager.hide()
    }

    companion object {
        private const val TAG = "ForegroundService"
        private const val TICK_INTERVAL_MS = 1_000L
    }
}
