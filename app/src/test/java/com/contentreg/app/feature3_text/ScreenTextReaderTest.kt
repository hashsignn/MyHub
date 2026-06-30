package com.contentreg.app.feature3_text

import org.junit.Assert.*
import org.junit.Test

/** M3.0 — unit tests for the pure node-walking logic in [ScreenTextReader]. */
class ScreenTextReaderTest {

    private fun node(
        text: String? = null,
        contentDesc: String? = null,
        viewId: String? = null,
        vararg children: ScreenTextReader.NodeInfo,
    ) = ScreenTextReader.NodeInfo(text, contentDesc, viewId, children.toList())

    // ── findUrl — address-bar ID matching ─────────────────────────────────────────────────

    @Test
    fun `findUrl returns url from chrome address bar id`() {
        val tree = node(children = arrayOf(
            node(viewId = "com.android.chrome:id/url_bar", text = "reddit.com/r/nsfw"),
            node(text = "Some page content"),
        ))
        assertEquals("reddit.com/r/nsfw", ScreenTextReader.findUrl(tree))
    }

    @Test
    fun `findUrl returns url from firefox address bar id`() {
        val tree = node(children = arrayOf(
            node(viewId = "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                text = "https://example.com/page"),
        ))
        assertEquals("https://example.com/page", ScreenTextReader.findUrl(tree))
    }

    @Test
    fun `findUrl returns url from samsung browser id`() {
        val tree = node(children = arrayOf(
            node(viewId = "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                text = "https://twitter.com/home"),
        ))
        assertEquals("https://twitter.com/home", ScreenTextReader.findUrl(tree))
    }

    // ── findUrl — heuristic fallback ──────────────────────────────────────────────────────

    @Test
    fun `findUrl heuristic matches https url when no known id present`() {
        val tree = node(children = arrayOf(
            node(text = "https://example.com/bad-path"),
            node(text = "Article body text"),
        ))
        assertEquals("https://example.com/bad-path", ScreenTextReader.findUrl(tree))
    }

    @Test
    fun `findUrl heuristic matches www-prefixed url`() {
        val tree = node(children = arrayOf(
            node(text = "www.example.com"),
            node(text = "Content here"),
        ))
        assertEquals("www.example.com", ScreenTextReader.findUrl(tree))
    }

    @Test
    fun `findUrl heuristic ignores multi-word prose with dots`() {
        val tree = node(children = arrayOf(
            node(text = "The quick brown fox. Jumps over the lazy dog."),
        ))
        // Has spaces → not a URL
        assertNull(ScreenTextReader.findUrl(tree))
    }

    @Test
    fun `findUrl id-based beats heuristic even when heuristic node comes first`() {
        val tree = node(children = arrayOf(
            node(text = "https://decoy.com"),   // heuristic would match this first
            node(viewId = "com.android.chrome:id/url_bar", text = "https://real.com/path"),
        ))
        // ID pass runs before heuristic pass, so real.com wins.
        assertEquals("https://real.com/path", ScreenTextReader.findUrl(tree))
    }

    @Test
    fun `findUrl returns null for empty tree`() {
        assertNull(ScreenTextReader.findUrl(node()))
    }

    // ── collectText ───────────────────────────────────────────────────────────────────────

    @Test
    fun `collectText concatenates text from all nodes`() {
        val tree = node(children = arrayOf(
            node(text = "Hello world"),
            node(text = "More content"),
        ))
        val text = ScreenTextReader.collectText(tree)
        assertTrue(text.contains("Hello world"))
        assertTrue(text.contains("More content"))
    }

    @Test
    fun `collectText includes contentDescription`() {
        val tree = node(children = arrayOf(
            node(contentDesc = "Image of something explicit"),
        ))
        assertTrue(ScreenTextReader.collectText(tree).contains("Image of something explicit"))
    }

    @Test
    fun `collectText does not duplicate contentDesc when it equals text`() {
        val tree = node(text = "Submit", contentDesc = "Submit")
        val text = ScreenTextReader.collectText(tree)
        // "Submit" should appear exactly once
        assertEquals(1, text.split("Submit").size - 1)
    }

    @Test
    fun `collectText excludes url bar nodes`() {
        val tree = node(children = arrayOf(
            node(viewId = "com.android.chrome:id/url_bar", text = "reddit.com/r/nsfw"),
            node(text = "Normal page content"),
        ))
        val text = ScreenTextReader.collectText(tree)
        assertFalse(text.contains("reddit.com"))
        assertTrue(text.contains("Normal page content"))
    }

    @Test
    fun `collectText skips single-character nodes`() {
        val tree = node(children = arrayOf(
            node(text = "A"),
            node(text = "Real content here"),
        ))
        val text = ScreenTextReader.collectText(tree)
        // "A " should not appear as a standalone word at start
        assertFalse(text.startsWith("A "))
        assertTrue(text.contains("Real content here"))
    }

    @Test
    fun `collectText caps output at MAX_TEXT_CHARS`() {
        val longText = "word ".repeat(2_000)   // ~10 000 chars
        val tree = node(text = longText)
        val result = ScreenTextReader.collectText(tree)
        assertTrue(result.length <= ScreenTextReader.MAX_TEXT_CHARS + 50)
    }

    @Test
    fun `collectText handles empty tree gracefully`() {
        assertEquals("", ScreenTextReader.collectText(node()))
    }

    // ── ScreenSnapshot.hasContent ─────────────────────────────────────────────────────────

    @Test
    fun `hasContent is false when url and text are both absent`() {
        assertFalse(ScreenSnapshot("pkg", null, "").hasContent)
    }

    @Test
    fun `hasContent is true when url is present`() {
        assertTrue(ScreenSnapshot("pkg", "example.com", "").hasContent)
    }

    @Test
    fun `hasContent is true when pageText is non-empty`() {
        assertTrue(ScreenSnapshot("pkg", null, "some text").hasContent)
    }
}