package com.contentreg.app.feature2_url.classifier

import com.contentreg.app.feature2_url.registry.UrlNormalizer

/** Why a host was classified the way it was — useful for the registry source + debugging. */
enum class ClassificationReason { CURATED_BLOCKLIST, KEYWORD_HEURISTIC, NOT_MATCHED }

data class ClassificationResult(
    val shouldBlock: Boolean,
    val reason: ClassificationReason,
)

/**
 * M2.3 — decides whether a *new, unseen* host should be added to the registry. Deliberately the
 * "simplest thing that works": the curated [blocklist] first, then a conservative keyword check on
 * the domain labels. Entirely local. No model yet — context-aware NLP is Phase 3.
 *
 * Keyword matching is intentionally narrow (whole dot-separated labels only, against unambiguous
 * tokens) to avoid false positives like `essex.com` or `scunthorpe.com`. The curated list is the
 * primary mechanism; keywords just catch obvious new domains.
 */
class UrlClassifier(
    private val blocklist: Set<String>,
    private val keywords: Set<String> = DEFAULT_KEYWORDS,
) {

    fun classifyHost(host: String): ClassificationResult {
        val domain = UrlNormalizer.normalizeDomain(host)
        if (domain.isEmpty()) return ClassificationResult(false, ClassificationReason.NOT_MATCHED)

        if (UrlNormalizer.hostMatchesSet(domain, blocklist)) {
            return ClassificationResult(true, ClassificationReason.CURATED_BLOCKLIST)
        }
        if (matchesKeyword(domain)) {
            return ClassificationResult(true, ClassificationReason.KEYWORD_HEURISTIC)
        }
        return ClassificationResult(false, ClassificationReason.NOT_MATCHED)
    }

    /** True if any dot-separated label of the domain exactly equals a blocked keyword. */
    private fun matchesKeyword(domain: String): Boolean {
        val labels = domain.split('.')
        return labels.any { label -> label in keywords }
    }

    companion object {
        /**
         * Unambiguous explicit tokens. Whole-label matching means `porn.example.com` and
         * `xxx.site.net` are caught, while `essex.com` / `sussex.gov.uk` are not.
         */
        val DEFAULT_KEYWORDS: Set<String> = setOf("porn", "xxx", "xnxx", "hentai", "camsex")
    }
}
