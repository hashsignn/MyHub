package com.contentreg.app.feature3_text.classifier

import android.util.Log
import com.contentreg.app.core.util.PrivacyLog
import com.contentreg.app.feature3_text.ScreenSnapshot

/**
 * M3.1 — two-stage context-aware classifier for on-screen text.
 *
 * Stage 1: [KeywordContextRules] — deterministic, auditable, zero-latency.
 *   - confidence ≥ 0.80 → block immediately, skip model.
 *   - confidence < 0.40 → clean, skip model.
 *   - confidence in [0.40, 0.80) → hand off to Stage 2 if model is available.
 *
 * Stage 2: [ModelClassifier] stub (not yet implemented) — called only in the uncertain band.
 *   Returns CLEAN until a real model is loaded.
 *
 * The URL is prepended to the text before scoring so explicit path components (e.g. "/r/nsfw")
 * contribute to confidence even when page text is sparse.
 */
class ContextClassifier(
    private val model: ModelClassifier = ModelClassifier(),
) {

    fun classify(snapshot: ScreenSnapshot): TextClassification {
        if (!snapshot.hasContent) return TextClassification.CLEAN

        val text = buildString {
            snapshot.url?.let { append(it).append(' ') }
            append(snapshot.pageText)
        }.lowercase()

        val rulesResult = KeywordContextRules.score(text)
        // Confidence is non-identifying; the matched keyword + package are debug-only.
        Log.d(TAG, "Rules: conf=${"%.2f".format(rulesResult.confidence)}")
        PrivacyLog.detail(TAG) { "Rules: kw=${rulesResult.triggeredKeyword} pkg=${snapshot.packageName}" }

        if (rulesResult.confidence >= KeywordContextRules.BLOCK_THRESHOLD) return rulesResult
        if (rulesResult.confidence < MODEL_HANDOFF_THRESHOLD) return rulesResult

        if (model.isAvailable) {
            val modelResult = model.classify(snapshot)
            if (modelResult.confidence > rulesResult.confidence) return modelResult
        }
        return rulesResult
    }

    companion object {
        private const val TAG = "ContextClassifier"
        private const val MODEL_HANDOFF_THRESHOLD = 0.40f
    }
}