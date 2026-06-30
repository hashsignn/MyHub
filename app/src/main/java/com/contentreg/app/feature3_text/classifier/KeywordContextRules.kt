package com.contentreg.app.feature3_text.classifier

/**
 * M3.1 — context-aware keyword rules for on-screen text classification.
 *
 * Design goals:
 *  1. Explicit adult content is caught reliably (Tier 1, base confidence 0.90).
 *  2. Ambiguous words pass when they appear in medical, rehab, crime/news, or academic context.
 *  3. Amplifier words (video, watch, stream…) push borderline hits over the block threshold.
 *  4. All matching is lowercase, whole-word (or prefix where noted), within a context window.
 *
 * No ML model is used here. The rules are intentionally auditable — see [RULES], [SAFE_CONTEXT_WORDS],
 * and [AMPLIFIERS] below.
 */
object KeywordContextRules {

    /** Minimum confidence to block. [ContextClassifier] compares against this. */
    const val BLOCK_THRESHOLD = 0.80f

    /** Words within this many positions of a trigger keyword count as context. */
    internal const val CONTEXT_WINDOW = 12

    internal enum class Tier(val baseConfidence: Float) {
        EXPLICIT(0.90f),  // unambiguous adult content
        MODERATE(0.65f),  // explicit when amplified; may be art / medical otherwise
        CONTEXT(0.45f),   // legitimate in most contexts; blocks only with multiple amplifiers
    }

    internal data class Rule(
        val keyword: String,
        val tier: Tier,
        /** True if the keyword should match as a prefix (e.g. "masturbat" → "masturbate/ion"). */
        val prefixMatch: Boolean = false,
    )

    // ── Keyword tiers ─────────────────────────────────────────────────────────────────────

    internal val RULES: List<Rule> = listOf(
        // Tier 1 — explicitly adult; no legitimate standalone use in normal browsing
        Rule("porn",        Tier.EXPLICIT),
        Rule("pornography", Tier.EXPLICIT),
        Rule("xxx",         Tier.EXPLICIT),
        Rule("onlyfans",    Tier.EXPLICIT),
        Rule("hentai",      Tier.EXPLICIT),
        Rule("cumshot",     Tier.EXPLICIT),
        Rule("blowjob",     Tier.EXPLICIT),
        Rule("handjob",     Tier.EXPLICIT),
        Rule("creampie",    Tier.EXPLICIT),
        Rule("gangbang",    Tier.EXPLICIT),
        Rule("camgirl",     Tier.EXPLICIT),
        Rule("sexcam",      Tier.EXPLICIT),
        Rule("masturbat",   Tier.EXPLICIT, prefixMatch = true),  // masturbate / masturbation
        Rule("ejaculat",    Tier.EXPLICIT, prefixMatch = true),  // ejaculate / ejaculation
        // Tier 2 — explicit when amplified; legitimate in art, health, nature contexts
        Rule("nude",        Tier.MODERATE),
        Rule("naked",       Tier.MODERATE),
        Rule("topless",     Tier.MODERATE),
        Rule("nsfw",        Tier.MODERATE),
        Rule("erotic",      Tier.MODERATE),
        Rule("lewd",        Tier.MODERATE),
        Rule("horny",       Tier.MODERATE),
        Rule("orgasm",      Tier.MODERATE),
        Rule("stripper",    Tier.MODERATE),
        // Tier 3 — context-dependent; almost always legitimate
        Rule("sex",         Tier.CONTEXT),
        Rule("sexy",        Tier.CONTEXT),
        Rule("lingerie",    Tier.CONTEXT),
        Rule("fetish",      Tier.CONTEXT),
    )

    // ── Safe-context words (discount confidence when near keyword) ────────────────────────

    internal val SAFE_CONTEXT_WORDS: List<String> = listOf(
        // Medical / health / education
        "therapy", "therapist", "treatment", "medical", "clinic", "doctor",
        "health", "education", "biology", "anatomy", "psychology", "psychiatry",
        "counseling", "research", "study", "statistics", "science", "journal",
        // Rehab / awareness / self-improvement
        "recovery", "addiction", "abstinence", "sober", "quitting", "overcome",
        "anti-porn", "pornfree", "nofap", "support", "awareness", "sobriety",
        // Crime / news / legal reporting
        "arrested", "charged", "convicted", "illegal", "crime", "criminal",
        "trafficking", "abuse", "victim", "survivor", "rescue", "lawsuit",
        "legislation", "policy", "banned", "prohibited", "offender", "sentence",
        // Art / nature / wildlife
        "painting", "sculpture", "museum", "photography", "portrait", "artistic",
        "mole rat", "wildlife", "nature", "documentary", "species",
        // Academic framing
        "academic", "university", "definition", "analysis", "according to",
        "wikipedia", "history of",
    )

    // ── Amplifier words (raise confidence when near keyword) ─────────────────────────────

    internal val AMPLIFIERS: List<String> = listOf(
        "video", "watch", "stream", "live", "free", "hot", "amateur",
        "hardcore", "compilation", "gallery", "cam", "webcam", "download",
        "leaked", "private", "exclusive", "teen", "milf", "babe", "clip",
    )

    // ── Scoring ───────────────────────────────────────────────────────────────────────────

    /**
     * Scores [text] (expected lowercase) against all rules and returns the highest-confidence
     * result. Returns [TextClassification.CLEAN] when nothing is flagged.
     */
    fun score(text: String): TextClassification {
        if (text.isBlank()) return TextClassification.CLEAN
        val words = tokenize(text)
        var best = TextClassification.CLEAN

        for (rule in RULES) {
            for (hitIndex in findHits(words, rule)) {
                val window = contextWindow(words, hitIndex, CONTEXT_WINDOW)
                val discount = safeDiscount(window)
                val bonus = amplifierBonus(window)
                val confidence = (rule.tier.baseConfidence + bonus - discount).coerceIn(0f, 1f)
                val reason = when {
                    discount > 0.20f           -> ClassificationReason.SAFE_CONTEXT
                    rule.tier == Tier.EXPLICIT -> ClassificationReason.EXPLICIT_KEYWORD
                    else                       -> ClassificationReason.CONTEXT_KEYWORD
                }
                if (confidence > best.confidence) {
                    best = TextClassification(
                        confidence = confidence,
                        reason = reason,
                        triggeredKeyword = rule.keyword,
                        shouldBlock = confidence >= BLOCK_THRESHOLD,
                    )
                }
            }
        }
        return best
    }

    // ── Internal helpers (internal for unit tests) ────────────────────────────────────────

    internal fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[\\s\\p{Punct}]+")).filter { it.isNotEmpty() }

    /** Indices in [words] where [rule] matches (whole-word or prefix). */
    internal fun findHits(words: List<String>, rule: Rule): List<Int> {
        val kw = rule.keyword.lowercase()
        val kwParts = kw.split(' ')
        val results = mutableListOf<Int>()

        if (kwParts.size == 1) {
            words.forEachIndexed { i, w ->
                val match = if (rule.prefixMatch) w.startsWith(kw) else w == kw
                if (match) results += i
            }
        } else {
            // Multi-word keyword: require consecutive match.
            for (i in 0..words.size - kwParts.size) {
                if (words.subList(i, i + kwParts.size) == kwParts) results += i
            }
        }
        return results
    }

    internal fun contextWindow(words: List<String>, center: Int, window: Int): List<String> {
        val from = (center - window).coerceAtLeast(0)
        val to = (center + window + 1).coerceAtMost(words.size)
        return words.subList(from, to)
    }

    /** Each safe-context word found near the trigger reduces confidence by 0.25, capped at 0.60. */
    internal fun safeDiscount(window: List<String>): Float {
        val windowText = window.joinToString(" ")
        var hits = 0
        for (safe in SAFE_CONTEXT_WORDS) {
            val safeParts = safe.lowercase().split(' ')
            val found = if (safeParts.size == 1) {
                window.any { it == safeParts[0] || it.startsWith(safeParts[0]) }
            } else {
                safeParts.joinToString(" ") in windowText
            }
            if (found) hits++
        }
        return (hits * 0.25f).coerceAtMost(0.60f)
    }

    /** Each amplifier word found near the trigger adds 0.08, capped at 0.20. */
    internal fun amplifierBonus(window: List<String>): Float {
        val hits = AMPLIFIERS.count { amp -> window.any { w -> w == amp } }
        return (hits * 0.08f).coerceAtMost(0.20f)
    }
}