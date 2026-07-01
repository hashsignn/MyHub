package com.contentreg.app.feature3_text.classifier

/**
 * M3.1 — context-aware keyword rules for on-screen text classification.
 *
 * Design goals (biased toward catching bad content — a false block on a safe page is acceptable,
 * hardcore content slipping through on one "safe" word is not):
 *  1. Explicit adult content (Tier 1) ALWAYS blocks — context is ignored for these words.
 *  2. Ambiguous words (Tier 2/3) are rescued only by STRONG safe context (≥2 safe words nearby),
 *     so a lone "research"/"university" can't wave a real adult page through.
 *  3. Amplifiers AND accumulation across distinct suspicious terms push borderline pages over the
 *     block threshold, catching pages "littered" with moderate terms even without classic amplifiers.
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

    /** Safe-context discount only applies once at least this many safe words are near a trigger. */
    internal const val MIN_SAFE_WORDS_TO_DISCOUNT = 2

    /** Each additional distinct suspicious keyword on the page adds this to the top score (capped). */
    internal const val ACCUMULATION_PER_KEYWORD = 0.10f
    internal const val ACCUMULATION_CAP = 0.30f

    /** A keyword must score at least this on its own to count toward accumulation. */
    internal const val ACCUMULATION_MIN = 0.40f

    internal enum class Tier(val baseConfidence: Float) {
        // EXPLICIT always blocks regardless of context. These words have no legitimate standalone
        // use in normal browsing, so we deliberately accept blocking the rare medical/news/recovery
        // page that quotes them rather than let hardcore content slip past on one "safe" word.
        EXPLICIT(1.00f),
        MODERATE(0.65f),  // explicit when amplified or stacked; rescued only by strong (>=2) safe context
        CONTEXT(0.45f),   // usually legitimate; blocks only when stacked with amplifiers / other terms
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
        "pornfree", "nofap", "support", "awareness", "sobriety",
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
        var distinctSuspicious = 0

        for (rule in RULES) {
            val hits = findHits(words, rule)
            if (hits.isEmpty()) continue

            var ruleConfidence = 0f
            var ruleReason = ClassificationReason.NO_MATCH
            for (hitIndex in hits) {
                val window = contextWindow(words, hitIndex, CONTEXT_WINDOW)
                val (conf, reason) = scoreHit(rule, window)
                if (conf > ruleConfidence) {
                    ruleConfidence = conf
                    ruleReason = reason
                }
            }

            // Count a keyword toward accumulation only if it stayed suspicious on its own (i.e.
            // wasn't fully discounted by safe context), so art/medical terms don't pile up.
            if (ruleConfidence >= ACCUMULATION_MIN) distinctSuspicious++

            if (ruleConfidence > best.confidence) {
                best = TextClassification(
                    confidence = ruleConfidence,
                    reason = ruleReason,
                    triggeredKeyword = rule.keyword,
                    shouldBlock = ruleConfidence >= BLOCK_THRESHOLD,
                )
            }
        }

        if (best.reason == ClassificationReason.NO_MATCH) return TextClassification.CLEAN

        // Accumulation: multiple distinct still-suspicious terms on one page stack toward a block.
        // Never applied to EXPLICIT (already 1.0) and never to fully-discounted safe-context terms.
        if (best.reason != ClassificationReason.EXPLICIT_KEYWORD && distinctSuspicious >= 2) {
            val bonus = ((distinctSuspicious - 1) * ACCUMULATION_PER_KEYWORD).coerceAtMost(ACCUMULATION_CAP)
            val boosted = (best.confidence + bonus).coerceIn(0f, 1f)
            best = best.copy(confidence = boosted, shouldBlock = boosted >= BLOCK_THRESHOLD)
        }
        return best
    }

    /** Confidence + reason for a single keyword hit given its surrounding [window]. */
    private fun scoreHit(rule: Rule, window: List<String>): Pair<Float, ClassificationReason> {
        if (rule.tier == Tier.EXPLICIT) {
            // Always blocks; safe context and amplifiers are irrelevant.
            return Tier.EXPLICIT.baseConfidence to ClassificationReason.EXPLICIT_KEYWORD
        }
        val discount = safeDiscount(window)
        val bonus = amplifierBonus(window)
        val confidence = (rule.tier.baseConfidence + bonus - discount).coerceIn(0f, 1f)
        val reason =
            if (discount > 0f) ClassificationReason.SAFE_CONTEXT else ClassificationReason.CONTEXT_KEYWORD
        return confidence to reason
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

    /**
     * Discount from safe-context words near the trigger: 0.25 each, capped at 0.60 — but only once
     * at least [MIN_SAFE_WORDS_TO_DISCOUNT] are present. A single stray safe word rescues nothing,
     * so real adult pages can't slip through on one coincidental "study"/"university".
     */
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
        if (hits < MIN_SAFE_WORDS_TO_DISCOUNT) return 0f
        return (hits * 0.25f).coerceAtMost(0.60f)
    }

    /** Each amplifier word found near the trigger adds 0.08, capped at 0.20. */
    internal fun amplifierBonus(window: List<String>): Float {
        val hits = AMPLIFIERS.count { amp -> window.any { w -> w == amp } }
        return (hits * 0.08f).coerceAtMost(0.20f)
    }
}