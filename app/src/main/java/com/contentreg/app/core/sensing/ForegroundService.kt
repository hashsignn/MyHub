package com.contentreg.app.core.sensing

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.core.overlay.OverlayManager
import com.contentreg.app.core.util.PrivacyLog
import com.contentreg.app.feature1_doomscroll.reels.ReelApps
import com.contentreg.app.feature1_doomscroll.reels.ReelDetector
import com.contentreg.app.feature3_text.ScreenTextPipeline
import com.contentreg.app.feature3_text.ScreenTextReader
import com.contentreg.app.feature3_text.TextBlockDecider
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single AccessibilityService for the whole app — the one sensing layer every feature reads from.
 *
 *  - Foreground-app detection (`TYPE_WINDOW_STATE_CHANGED`) → [ForegroundAppTracker].
 *  - Reel-surface blocking: on window/content changes it checks whether the current app's short-video
 *    ("reel") surface is showing ([ReelDetector]) and drives the block overlay for exactly that
 *    surface — so the Reels/Shorts tab is covered while the app's other tabs stay usable.
 *  - On-screen text (M3): a debounced node-tree read feeds [ScreenTextPipeline] → [TextBlockDecider].
 *
 * It holds no business logic beyond translating events into signals and running the bounded reel
 * check; the decisions live in the detectors so the event hot path stays cheap.
 */
class ForegroundService : AccessibilityService() {

    // var so onServiceConnected() can cancel+recreate on every re-bind (Chrome/YouTube can re-bind
    // without an onDestroy(), which would otherwise stack duplicate collectors).
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var textReadJob: Job? = null
    private var reelCheckJob: Job? = null
    private val lastContentReadMs = ConcurrentHashMap<String, Long>()

    // Which reel apps are actively blocked (synced live from settings). Default: all supported.
    @Volatile private var enabledReelApps: Set<String> = ReelApps.supportedPackages

    // Last reel-block state, so each block is counted once for stats.
    @Volatile private var lastReelBlocked = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Cancel any coroutines from a prior bind, then start fresh.
        serviceScope.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        textReadJob = null
        reelCheckJob = null
        lastReelBlocked = false
        Log.i(TAG, "Accessibility service connected; foreground sensing active.")
        startReelSettingsSync()
        startTextPipeline()
    }

    /** Keep the enabled reel-app set in sync with settings so toggles take effect immediately. */
    private fun startReelSettingsSync() {
        serviceScope.launch {
            ServiceLocator.settingsStore.blockedReelApps.collect { enabledReelApps = it }
        }
    }

    /** M3.0 — subscribe [TextBlockDecider] to the screen-text pipeline (reuses the same overlay). */
    private fun startTextPipeline() {
        TextBlockDecider(
            registry = ServiceLocator.registryRepository,
            overlay = ServiceLocator.overlayManager,
            scope = serviceScope,
        ).start()
    }

    // ── Reel blocking ─────────────────────────────────────────────────────────────────────────

    /** Debounced so a burst of content-changed events collapses into one bounded tree scan. */
    private fun scheduleReelCheck() {
        reelCheckJob?.cancel()
        reelCheckJob = serviceScope.launch {
            delay(REEL_CHECK_DELAY_MS)
            evaluateReelBlock()
        }
    }

    private suspend fun evaluateReelBlock() {
        val pkg = ForegroundAppTracker.currentPackage
        val blocked = when {
            pkg == null || pkg !in enabledReelApps -> false
            ReelDetector.isWholeAppBlock(pkg) -> true
            else -> {
                val ids = withContext(Dispatchers.Main) { collectViewIds(getRootInActiveWindow()) }
                ReelDetector.isReelSurface(pkg, ids, enabledReelApps)
            }
        }
        withContext(Dispatchers.Main) {
            ServiceLocator.overlayManager.setReason(OverlayManager.BlockReason.REEL, blocked)
        }
        if (blocked && !lastReelBlocked) {
            serviceScope.launch { ServiceLocator.statsRepository.incrementBlocks() }
            PrivacyLog.detail(TAG) { "Reel block shown for $pkg" }
        }
        lastReelBlocked = blocked
    }

    /**
     * Bounded DFS collecting view-id resource names from the active window, for reel detection.
     * Capped at [REEL_SCAN_MAX_NODES]; recycles the children it fetches. Main thread only.
     */
    private fun collectViewIds(root: AccessibilityNodeInfo?): List<String> {
        root ?: return emptyList()
        val ids = ArrayList<String>(64)
        val counter = intArrayOf(0)
        fun walk(node: AccessibilityNodeInfo) {
            counter[0]++
            node.viewIdResourceName?.let { if (it.isNotEmpty()) ids.add(it) }
            if (counter[0] >= REEL_SCAN_MAX_NODES) return
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
                if (counter[0] >= REEL_SCAN_MAX_NODES) return
            }
        }
        walk(root)
        root.recycle()
        return ids
    }

    // ── M3.0 on-screen text read ───────────────────────────────────────────────────────────────

    private fun scheduleTextRead(packageName: String) {
        textReadJob?.cancel()
        textReadJob = serviceScope.launch {
            delay(TEXT_READ_DELAY_MS)
            doTextRead(packageName)
        }
    }

    private fun scheduleContentRead(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastContentReadMs.getOrDefault(packageName, 0L) < CONTENT_READ_MIN_INTERVAL_MS) return
        textReadJob?.cancel()
        textReadJob = serviceScope.launch {
            delay(CONTENT_READ_DELAY_MS)
            doTextRead(packageName)
        }
    }

    private suspend fun doTextRead(packageName: String) {
        val snapshot = withContext(Dispatchers.Main) {
            ScreenTextReader.read(getRootInActiveWindow(), packageName)
        }
        val usable = snapshot.url != null || snapshot.pageText.length >= MIN_CONTENT_CHARS
        if (usable) {
            Log.d(TAG, "M3.0 snapshot: pkg=$packageName urlPresent=${snapshot.url != null} " +
                "textLen=${snapshot.pageText.length}")
            PrivacyLog.detail(TAG) { "M3.0 snapshot url=${snapshot.url}" }
            lastContentReadMs[packageName] = System.currentTimeMillis()
            ScreenTextPipeline.push(snapshot)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        // Ignore our own UI so we never react to our own windows.
        if (packageName == applicationContext.packageName) return

        val now = System.currentTimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                ForegroundAppTracker.update(
                    packageName = packageName,
                    className = event.className?.toString(),
                    timestampMs = now,
                )
                PrivacyLog.detail(TAG) { "Foreground app: $packageName" }
                scheduleReelCheck()       // re-evaluate the reel overlay on every tab/app switch
                scheduleTextRead(packageName)  // M3.0
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Re-check when the active surface's content settles (in-app tab switch, lazy loads).
                val fgPkg = ForegroundAppTracker.currentPackage
                if (fgPkg != null && packageName == fgPkg) {
                    scheduleReelCheck()
                    scheduleContentRead(packageName)  // M3.0
                }
            }
        }
    }

    override fun onInterrupt() {
        // Required override. Nothing to interrupt.
    }

    override fun onDestroy() {
        super.onDestroy()
        // With the service gone there is nothing left to keep the overlay in sync — tear it down.
        serviceScope.cancel()
        ServiceLocator.overlayManager.clearAll()
    }

    companion object {
        private const val TAG = "ForegroundService"
        private const val REEL_CHECK_DELAY_MS = 250L      // debounce reel checks
        private const val REEL_SCAN_MAX_NODES = 1_500     // bound the view-id scan
        private const val TEXT_READ_DELAY_MS = 800L       // state-changed text debounce
        private const val CONTENT_READ_DELAY_MS = 1_500L  // content-changed text debounce
        private const val CONTENT_READ_MIN_INTERVAL_MS = 3_000L  // per-package text throttle
        private const val MIN_CONTENT_CHARS = 200         // skip loading skeletons / cookie walls
    }
}
