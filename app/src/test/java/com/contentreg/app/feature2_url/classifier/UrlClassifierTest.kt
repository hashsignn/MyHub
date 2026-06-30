package com.contentreg.app.feature2_url.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2.3 — curated-list + conservative-keyword classification (no NLP yet). */
class UrlClassifierTest {

    private val classifier = UrlClassifier(blocklist = setOf("pornhub.com", "xvideos.com"))

    @Test
    fun `curated domain and its subdomains block`() {
        assertTrue(classifier.classifyHost("pornhub.com").shouldBlock)
        assertTrue(classifier.classifyHost("www.pornhub.com").shouldBlock)
        assertTrue(classifier.classifyHost("cdn.media.pornhub.com").shouldBlock)
        assertEquals(
            ClassificationReason.CURATED_BLOCKLIST,
            classifier.classifyHost("pornhub.com").reason,
        )
    }

    @Test
    fun `keyword heuristic catches an unlisted explicit domain (whole-label only)`() {
        val r = classifier.classifyHost("porn.example.com")
        assertTrue(r.shouldBlock)
        assertEquals(ClassificationReason.KEYWORD_HEURISTIC, r.reason)
        assertTrue(classifier.classifyHost("xxx.somehost.net").shouldBlock)
        // Conservative by design: a keyword embedded in a larger label is NOT matched,
        // so the curated list / manual adds handle e.g. "freeporn.com".
        assertFalse(classifier.classifyHost("freeporn.com").shouldBlock)
    }

    @Test
    fun `benign domains pass, including tricky substrings`() {
        assertFalse(classifier.classifyHost("google.com").shouldBlock)
        assertFalse(classifier.classifyHost("wikipedia.org").shouldBlock)
        // Whole-label matching avoids false positives on substrings of keywords.
        assertFalse(classifier.classifyHost("essex.gov.uk").shouldBlock)
        assertFalse(classifier.classifyHost("scunthorpe.com").shouldBlock)
    }

    @Test
    fun `empty host does not block`() {
        assertFalse(classifier.classifyHost("").shouldBlock)
    }
}
