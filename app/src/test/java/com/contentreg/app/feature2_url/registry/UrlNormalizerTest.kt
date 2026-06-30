package com.contentreg.app.feature2_url.registry

import org.junit.Assert.assertEquals
import org.junit.Test

/** M2.2 — the normalization that makes lookup-before-classify reliable. */
class UrlNormalizerTest {

    @Test
    fun `domain normalization lowercases, trims, drops www and trailing dot`() {
        assertEquals("example.com", UrlNormalizer.normalizeDomain("  WWW.Example.com.  "))
        assertEquals("example.com", UrlNormalizer.normalizeDomain("example.com"))
        assertEquals("sub.example.com", UrlNormalizer.normalizeDomain("Sub.Example.com"))
    }

    @Test
    fun `domain extracted from assorted url shapes`() {
        assertEquals("example.com", UrlNormalizer.domainFromUrl("https://www.example.com/path?q=1#frag"))
        assertEquals("example.com", UrlNormalizer.domainFromUrl("http://example.com:8080/x"))
        assertEquals("example.com", UrlNormalizer.domainFromUrl("example.com/a/b"))
        assertEquals("example.com", UrlNormalizer.domainFromUrl("https://user:pass@example.com/a"))
        assertEquals("", UrlNormalizer.domainFromUrl(""))
    }

    @Test
    fun `url normalization drops scheme, www, trailing slash, fragment; keeps query`() {
        // Scheme dropped (http/https unify), www stripped, trailing slash + fragment removed.
        assertEquals(
            "example.com/page",
            UrlNormalizer.normalizeUrl("HTTP://WWW.Example.com/page/#section"),
        )
        // Query is preserved.
        assertEquals("example.com/p?a=1", UrlNormalizer.normalizeUrl("example.com/p?a=1"))
        // Root path collapses to just the domain.
        assertEquals("example.com", UrlNormalizer.normalizeUrl("https://example.com/"))
    }
}
