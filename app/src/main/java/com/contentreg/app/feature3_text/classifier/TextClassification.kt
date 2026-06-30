package com.contentreg.app.feature3_text.classifier

/** Why the classifier reached its verdict — useful for logging and choosing the registry source. */
enum class ClassificationReason {
    EXPLICIT_KEYWORD,  // Tier-1 word found, no safe context
    CONTEXT_KEYWORD,   // Tier-2/3 word found, pushed over threshold by amplifiers
    SAFE_CONTEXT,      // Keyword found but safe-context words were nearby — not blocked
    NO_MATCH,          // Nothing flagged
}

/**
 * M3.1 — result from [ContextClassifier].
 *
 * [confidence] is in [0, 1]. [shouldBlock] is true iff confidence ≥ [KeywordContextRules.BLOCK_THRESHOLD].
 */
data class TextClassification(
    val confidence: Float,
    val reason: ClassificationReason,
    val triggeredKeyword: String?,
    val shouldBlock: Boolean,
) {
    companion object {
        val CLEAN = TextClassification(0f, ClassificationReason.NO_MATCH, null, false)
    }
}