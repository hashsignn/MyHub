package com.contentreg.app.feature3_text

import android.util.Log
import com.contentreg.app.core.overlay.OverlayManager
import com.contentreg.app.core.util.PrivacyLog
import com.contentreg.app.feature2_url.registry.BlockEntrySource
import com.contentreg.app.feature2_url.registry.RegistryRepository
import com.contentreg.app.feature2_url.registry.UrlNormalizer
import com.contentreg.app.feature3_text.classifier.ContextClassifier
import com.contentreg.app.feature3_text.classifier.TextClassification
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
        val url = snapshot.url
        val inRegistry = url != null && registry.isUrlBlocked(url)
        // Registry hit is the fast path — skip the classifier entirely.
        val classification = if (inRegistry) null else classifier.classify(snapshot)

        when (val decision = decide(url, inRegistry, classification)) {
            Decision.Allow -> return

            Decision.BlockOnly -> {
                if (inRegistry) {
                    Log.d(TAG, "Registry hit — blocking immediately")
                    PrivacyLog.detail(TAG) { "Registry hit url=$url" }
                } else {
                    Log.i(TAG, "Blocking conf=${fmt(classification?.confidence)}")
                    PrivacyLog.detail(TAG) { "Blocking kw=${classification?.triggeredKeyword} url=$url" }
                }
                withContext(Dispatchers.Main) { overlay.show() }
            }

            is Decision.BlockAndPersist -> {
                Log.i(TAG, "Blocking conf=${fmt(classification?.confidence)}")
                PrivacyLog.detail(TAG) { "Blocking kw=${classification?.triggeredKeyword} url=${decision.url}" }
                withContext(Dispatchers.Main) { overlay.show() }
                scope.launch { persistBlock(decision) }
            }
        }
    }

    /**
     * Persists the block at the granularity chosen by [decide]: URL-level when a path is present
     * (so only the specific page is blocked), or domain-level for a bare domain.
     */
    private suspend fun persistBlock(decision: Decision.BlockAndPersist) {
        val added = when (decision.kind) {
            PersistKind.URL -> registry.addUrl(decision.url, BlockEntrySource.HEURISTIC)
            PersistKind.DOMAIN -> registry.addDomain(decision.url, BlockEntrySource.HEURISTIC)
        }
        Log.d(TAG, "Persisted ${decision.kind} block (new=$added)")
        PrivacyLog.detail(TAG) { "Persisted ${decision.kind} block: ${UrlNormalizer.normalizeUrl(decision.url)}" }
    }

    /** Granularity at which a heuristic block is written to the registry. */
    internal enum class PersistKind { URL, DOMAIN }

    /** The outcome of evaluating one snapshot. */
    internal sealed interface Decision {
        /** Nothing to do — allow the page. */
        object Allow : Decision
        /** Show the block overlay only (registry hit, or a block with no URL to persist). */
        object BlockOnly : Decision
        /** Show the block overlay and persist [url] at [kind] granularity. */
        data class BlockAndPersist(val kind: PersistKind, val url: String) : Decision
    }

    companion object {
        private const val TAG = "TextBlockDecider"

        private fun fmt(conf: Float?): String = "%.2f".format(conf ?: 0f)

        /**
         * Pure decision for one snapshot — no Android, no I/O, so it is unit-tested directly:
         *  - a registry hit blocks immediately (overlay only; the entry is already persisted);
         *  - a classifier verdict of `shouldBlock` blocks, and when a URL is present persists it at
         *    URL granularity if the normalized form has a path, else domain granularity;
         *  - everything else is allowed.
         *
         * [classification] may be null when the caller short-circuited on a registry hit.
         */
        internal fun decide(
            url: String?,
            isUrlInRegistry: Boolean,
            classification: TextClassification?,
        ): Decision {
            if (url != null && isUrlInRegistry) return Decision.BlockOnly
            val c = classification ?: return Decision.Allow
            if (!c.shouldBlock) return Decision.Allow
            if (url == null) return Decision.BlockOnly
            val normalized = UrlNormalizer.normalizeUrl(url)
            val kind = if ('/' in normalized) PersistKind.URL else PersistKind.DOMAIN
            return Decision.BlockAndPersist(kind, url)
        }
    }
}