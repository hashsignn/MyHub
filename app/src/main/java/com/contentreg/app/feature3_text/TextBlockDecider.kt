package com.contentreg.app.feature3_text

import android.util.Log
import com.contentreg.app.core.overlay.OverlayManager
import com.contentreg.app.feature2_url.registry.BlockEntrySource
import com.contentreg.app.feature2_url.registry.RegistryRepository
import com.contentreg.app.feature2_url.registry.UrlNormalizer
import com.contentreg.app.feature3_text.classifier.ContextClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M3.2 — the final stage of the on-screen text pipeline.
 *
 * Subscribes to [ScreenTextPipeline], and for each [ScreenSnapshot]:
 *  1. Fast path: if the URL is already in the registry → show overlay immediately, skip classifier.
 *  2. Slow path: run [ContextClassifier]; if confidence ≥ threshold → show overlay AND persist.
 *
 * Persistence is URL-level (not domain-level) when a path is present, so only the specific page
 * is blocked on future visits (e.g. reddit.com/r/nsfw blocked, reddit.com stays open).
 * A bare-domain URL (no path) blocks the whole domain — appropriate for dedicated adult sites.
 *
 * Reuses the existing [OverlayManager] from M1.3; no second overlay is created.
 */
class TextBlockDecider(
    private val registry: RegistryRepository,
    private val overlay: OverlayManager,
    private val classifier: ContextClassifier = ContextClassifier(),
    private val scope: CoroutineScope,
) {

    fun start() {
        ScreenTextPipeline.snapshots
            .onEach { snapshot -> evaluate(snapshot) }
            .launchIn(scope)
    }

    private suspend fun evaluate(snapshot: ScreenSnapshot) {
        // Fast path: URL already registered → block without re-classifying.
        if (snapshot.url != null && registry.isUrlBlocked(snapshot.url)) {
            Log.d(TAG, "Registry hit: ${snapshot.url} — blocking immediately")
            withContext(Dispatchers.Main) { overlay.show() }
            return
        }

        val result = classifier.classify(snapshot)
        if (!result.shouldBlock) return

        Log.i(TAG, "Blocking: kw=${result.triggeredKeyword} " +
            "conf=${"%.2f".format(result.confidence)} url=${snapshot.url}")

        withContext(Dispatchers.Main) { overlay.show() }

        val url = snapshot.url ?: return
        scope.launch { persistBlock(url) }
    }

    /**
     * Persists a URL-level block when a path is present, or a domain-level block for bare domains
     * (no slash after the host). This preserves access to benign paths on the same host.
     */
    private suspend fun persistBlock(url: String) {
        val normalized = UrlNormalizer.normalizeUrl(url)
        // A bare domain has no '/' after stripping the scheme + www.
        val haspath = '/' in normalized
        if (haspath) {
            val added = registry.addUrl(url, BlockEntrySource.HEURISTIC)
            Log.d(TAG, "Persisted URL block: $normalized (new=$added)")
        } else {
            val added = registry.addDomain(url, BlockEntrySource.HEURISTIC)
            Log.d(TAG, "Persisted domain block: $normalized (new=$added)")
        }
    }

    companion object {
        private const val TAG = "TextBlockDecider"
    }
}