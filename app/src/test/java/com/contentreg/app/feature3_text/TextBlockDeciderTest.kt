package com.contentreg.app.feature3_text

import com.contentreg.app.feature3_text.classifier.ClassificationReason
import com.contentreg.app.feature3_text.classifier.TextClassification
import org.junit.Assert.assertEquals
import org.junit.Test

/** M3.2 — unit tests for the pure decision logic in [TextBlockDecider.decide]. */
class TextBlockDeciderTest {

    private fun blocking(kw: String = "explicitword") = TextClassification(
        confidence = 0.90f,
        reason = ClassificationReason.EXPLICIT_KEYWORD,
        triggeredKeyword = kw,
        shouldBlock = true,
    )

    private val clean = TextClassification.CLEAN

    // ── Registry fast-path ──────────────────────────────────────────────────────────────────

    @Test
    fun `registry hit blocks immediately without persisting`() {
        val d = TextBlockDecider.decide(
            url = "reddit.com/r/nsfw",
            isUrlInRegistry = true,
            classification = null,
        )
        assertEquals(TextBlockDecider.Decision.BlockOnly, d)
    }

    @Test
    fun `registry hit wins even over a clean classification`() {
        val d = TextBlockDecider.decide(
            url = "reddit.com/r/nsfw",
            isUrlInRegistry = true,
            classification = clean,
        )
        assertEquals(TextBlockDecider.Decision.BlockOnly, d)
    }

    // ── Classifier path ─────────────────────────────────────────────────────────────────────

    @Test
    fun `clean classification with no registry hit is allowed`() {
        val d = TextBlockDecider.decide("example.com", isUrlInRegistry = false, classification = clean)
        assertEquals(TextBlockDecider.Decision.Allow, d)
    }

    @Test
    fun `blocking classification on a path url persists at url granularity`() {
        val d = TextBlockDecider.decide(
            url = "https://reddit.com/r/nsfw",
            isUrlInRegistry = false,
            classification = blocking(),
        )
        assertEquals(
            TextBlockDecider.Decision.BlockAndPersist(
                TextBlockDecider.PersistKind.URL,
                "https://reddit.com/r/nsfw",
            ),
            d,
        )
    }

    @Test
    fun `blocking classification on a bare domain persists at domain granularity`() {
        val d = TextBlockDecider.decide(
            url = "badsite.com",
            isUrlInRegistry = false,
            classification = blocking(),
        )
        assertEquals(
            TextBlockDecider.Decision.BlockAndPersist(
                TextBlockDecider.PersistKind.DOMAIN,
                "badsite.com",
            ),
            d,
        )
    }

    @Test
    fun `blocking classification with no url blocks overlay but persists nothing`() {
        val d = TextBlockDecider.decide(url = null, isUrlInRegistry = false, classification = blocking())
        assertEquals(TextBlockDecider.Decision.BlockOnly, d)
    }
}
