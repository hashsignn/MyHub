package com.contentreg.app.core.sensing

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.core.overlay.OverlayManager
import com.contentreg.app.core.util.PrivacyLog
import com.contentreg.app.detox.DetoxState
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
import kotlinx.coroutines.isActive
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
    private val lastContentReadMs = ConcurrentHashMap<String, Long>()

    // Which reel apps are actively blocked (synced live from settings). Default: all supported.
    @Volatile private var enabledReelApps: Set<String> = ReelApps.supportedPackages

    // Last reel-block state, so each block is counted once for stats.
    @Volatile private var lastReelBlocked = false

    // Consecutive non-reel reads while a block is up — hysteresis to avoid flicker on a playing reel
    // that momentarily hides its markers between frames.
    @Volatile private var reelClearStreak = 0

    // Digital Detox: live lockdown state (synced from DetoxController) + the packages that are always
    // allowed regardless of the user's picks (this app, system UI, the home launcher).
    @Volatile private var detoxState: DetoxState = DetoxState.INACTIVE
    private var detoxAllowlistBase: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Cancel any coroutines from a prior bind, then start fresh.
        serviceScope.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        textReadJob = null
        lastReelBlocked = false
        Log.i(TAG, "Accessibility service connected; foreground sensing active.")
        detoxAllowlistBase = computeDetoxAllowlistBase()
        startReelSettingsSync()
        startDetoxSync()
        startReelTicker()
        startTextPipeline()
    }

    /** Keep the detox lockdown state in sync so the ticker enforces it without a per-tick read. */
    private fun startDetoxSync() {
        serviceScope.launch {
            ServiceLocator.detoxController.state.collect { detoxState = it }
        }
    }

    /**
     * Packages that stay reachable during a detox no matter what the user picked: this app (so the
     * unlock flow works), system UI, and the home launcher(s) (so "go home" isn't itself blocked).
     */
    private fun computeDetoxAllowlistBase(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = runCatching {
            packageManager.queryIntentActivities(home, 0).mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())
        return buildSet {
            add(applicationContext.packageName)
            add("com.android.systemui")
            addAll(launchers)
        }
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

    /**
     * Periodic reel re-evaluation. A *playing* reel fires content-changed events continuously, which
     * would starve a pure debounce (it never settles and the check is perpetually cancelled), so we
     * re-check on a fixed cadence instead. The scan only walks the tree while a per-tab reel app is
     * foreground; otherwise it just clears the reel reason cheaply.
     */
    private fun startReelTicker() {
        serviceScope.launch {
            while (isActive) {
                evaluateReelBlock()
                delay(REEL_TICK_MS)
            }
        }
    }

    private suspend fun evaluateReelBlock() {
        val eval = withContext(Dispatchers.Main) { scanForeground() }
            ?: return // no windows this tick — leave the overlay state unchanged (avoids flicker)

        val pkg = eval.first
        val rawBlocked = when {
            pkg == null || pkg !in enabledReelApps -> false
            ReelDetector.isWholeAppBlock(pkg) -> true
            else -> ReelDetector.isReelSurface(pkg, eval.second, enabledReelApps)
        }
        val onReelApp = pkg != null && pkg in enabledReelApps

        // Hysteresis on the *clear* side only: a playing reel occasionally drops its detection
        // markers for a single frame, which a raw read would treat as "not a reel" and flap the
        // overlay off for one tick. So once blocking, require REEL_CLEAR_STREAK consecutive negative
        // reads before lifting — but ONLY while still on the reel app. Leaving the reel app lifts
        // immediately, so we never hold the block over an unrelated surface.
        val blocked = when {
            rawBlocked -> { reelClearStreak = 0; true }
            !lastReelBlocked -> false
            !onReelApp -> { reelClearStreak = 0; false }
            else -> {
                reelClearStreak++
                if (reelClearStreak >= REEL_CLEAR_STREAK) { reelClearStreak = 0; false } else true
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

        evaluateDetox(pkg)
    }

    /**
     * Digital Detox enforcement, evaluated on the same tick. While a lockdown is active, any app not
     * on the allow-list is covered by the full-screen detox overlay. When the window expires, the
     * overlay comes down and the stored state is cleared once.
     */
    private suspend fun evaluateDetox(pkg: String?) {
        val ds = detoxState
        val now = System.currentTimeMillis()
        if (ds.isActive(now)) {
            val allowed = pkg == null ||
                pkg in ds.allowedApps ||
                pkg in detoxAllowlistBase
            withContext(Dispatchers.Main) {
                if (allowed) {
                    ServiceLocator.detoxOverlayController.hide()
                } else {
                    ServiceLocator.detoxOverlayController.show(ds.endTimeMs, ds.allowedApps)
                }
            }
        } else {
            withContext(Dispatchers.Main) { ServiceLocator.detoxOverlayController.hide() }
            // A detox that just ran out: clear the persisted window once so state stays tidy.
            if (ds.endTimeMs != 0L && ds.endTimeMs <= now) {
                ServiceLocator.detoxController.end()
            }
        }
    }

    /**
     * Returns the foreground app's package and the view-ids visible across ALL of its windows, or
     * null if there are no windows. We scan every window of the foreground package — not just
     * `getRootInActiveWindow()` — because apps like YouTube keep the reel content in a window that
     * isn't the input-focused one, so the active root alone is empty. Main thread only.
     */
    private fun scanForeground(): Pair<String?, List<String>>? {
        val wins = windows ?: return null
        if (wins.isEmpty()) return null

        // Foreground package = the active window's package (fallback: the active-window root).
        val activePkg = wins.firstOrNull { it.isActive }?.let { w ->
            val r = w.root
            val p = r?.packageName?.toString()
            r?.recycle()
            p
        } ?: getRootInActiveWindow()?.let { r ->
            val p = r.packageName?.toString(); r.recycle(); p
        }

        // Only a per-tab reel app needs the (more expensive) view-id scan.
        if (activePkg == null || activePkg !in enabledReelApps || ReelDetector.isWholeAppBlock(activePkg)) {
            return activePkg to emptyList()
        }
        val ids = ArrayList<String>(128)
        val counter = intArrayOf(0)
        for (w in wins) {
            val root = w.root ?: continue
            if (root.packageName?.toString() == activePkg) {
                root.refresh()
                collectInto(root, ids, counter)
            }
            root.recycle()
        }
        return activePkg to ids
    }

    /**
     * Bounded DFS collecting view-id resource names into [ids]. Capped at [REEL_SCAN_MAX_NODES];
     * recycles the children it fetches but NOT [node] (the caller owns each window root).
     */
    private fun collectInto(node: AccessibilityNodeInfo, ids: MutableList<String>, counter: IntArray) {
        counter[0]++
        node.viewIdResourceName?.let { if (it.isNotEmpty()) ids.add(it) }
        if (counter[0] >= REEL_SCAN_MAX_NODES) return
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectInto(child, ids, counter)
            child.recycle()
            if (counter[0] >= REEL_SCAN_MAX_NODES) return
        }
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
                val previousPackage = ForegroundAppTracker.currentPackage
                ForegroundAppTracker.update(
                    packageName = packageName,
                    className = event.className?.toString(),
                    timestampMs = now,
                )
                PrivacyLog.detail(TAG) { "Foreground app: $packageName" }
                // Leaving the app that a text/URL block was showing on must clear that block
                // immediately — the blocked content is gone. Otherwise the overlay stays stuck on
                // the next screen (e.g. the home launcher, which has too little text to push a fresh
                // snapshot that would re-evaluate it). A11y events arrive on the main thread, so the
                // overlay call is safe here. The debounced read below re-blocks if the new screen is
                // itself bad. (Reel blocks are cleared separately by the periodic ticker.)
                if (packageName != previousPackage) {
                    ServiceLocator.overlayManager.setReason(OverlayManager.BlockReason.TEXT, false)
                }
                // Reel state is re-evaluated by the periodic ticker (a playing reel would starve a
                // per-event debounce); here we only drive the M3 text read.
                scheduleTextRead(packageName)  // M3.0
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Re-read text when the active surface's content settles (in-app tab switch, lazy
                // loads). Reel detection is handled by the ticker, not here.
                val fgPkg = ForegroundAppTracker.currentPackage
                if (fgPkg != null && packageName == fgPkg) {
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
        ServiceLocator.detoxOverlayController.hide()
    }

    companion object {
        private const val TAG = "ForegroundService"
        private const val REEL_TICK_MS = 700L             // periodic reel re-check cadence
        private const val REEL_CLEAR_STREAK = 2           // negative reads before a block lifts (anti-flicker)
        private const val REEL_SCAN_MAX_NODES = 1_500     // bound the view-id scan
        private const val TEXT_READ_DELAY_MS = 800L       // state-changed text debounce
        private const val CONTENT_READ_DELAY_MS = 1_500L  // content-changed text debounce
        private const val CONTENT_READ_MIN_INTERVAL_MS = 3_000L  // per-package text throttle
        private const val MIN_CONTENT_CHARS = 200         // skip loading skeletons / cookie walls
    }
}
