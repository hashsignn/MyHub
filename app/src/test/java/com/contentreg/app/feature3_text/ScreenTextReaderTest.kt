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

    // ── findUrlInLiveTree / scanForUrl — raw-tree URL scan with node cap (0b) ────────────────

    /** Pure fake for the [ScreenTextReader.ScanNode] abstraction; records recycle() calls. */
    private class FakeScanNode(
        override val viewId: String? = null,
        override val text: String? = null,
        private val kids: List<FakeScanNode> = emptyList(),
    ) : ScreenTextReader.ScanNode {
        var recycled = 0
            private set
        override val childCount: Int get() = kids.size
        override fun child(index: Int): ScreenTextReader.ScanNode? = kids.getOrNull(index)
        override fun recycle() { recycled++ }
    }

    @Test
    fun `scanForUrl finds a url bar node beyond the 200-node text cap`() {
        // 260 plain leaves, then the address bar — reached at visit ~262, past MAX_NODES (200).
        val kids = buildList {
            repeat(260) { add(FakeScanNode(text = "leaf $it")) }
            add(FakeScanNode(viewId = "com.android.chrome:id/url_bar", text = "reddit.com/r/nsfw"))
        }
        assertTrue("url bar must sit past the text tree cap", kids.size > ScreenTextReader.MAX_NODES)
        assertEquals("reddit.com/r/nsfw", ScreenTextReader.scanForUrl(FakeScanNode(kids = kids)))
    }

    @Test
    fun `scanForUrl returns null when the url bar is past the node cap`() {
        val kids = buildList {
            repeat(10) { add(FakeScanNode(text = "leaf $it")) }
            add(FakeScanNode(viewId = "com.android.chrome:id/url_bar", text = "reddit.com/r/nsfw"))
        }
        val root = FakeScanNode(kids = kids)
        // A cap of 5 stops the walk before the url bar (~visit 12); a generous cap still finds it.
        assertNull(ScreenTextReader.scanForUrl(root, maxNodes = 5))
        assertEquals("reddit.com/r/nsfw", ScreenTextReader.scanForUrl(root, maxNodes = 100))
    }

    @Test
    fun `scanForUrl recycles visited children but never the root`() {
        val a = FakeScanNode(text = "a")
        val b = FakeScanNode(viewId = "com.android.chrome:id/url_bar", text = "example.com")
        val root = FakeScanNode(kids = listOf(a, b))
        assertEquals("example.com", ScreenTextReader.scanForUrl(root))
        assertEquals(1, a.recycled)     // fetched + recycled
        assertEquals(1, b.recycled)     // the matched child is still recycled by its parent frame
        assertEquals(0, root.recycled)  // root is owned by the caller
    }

    @Test
    fun `urlBarText returns trimmed text for a known url bar id`() {
        assertEquals(
            "example.com",
            ScreenTextReader.urlBarText("com.android.chrome:id/url_bar", "  example.com  "),
        )
    }

    @Test
    fun `urlBarText returns null for an unknown view id`() {
        assertNull(ScreenTextReader.urlBarText("com.android.chrome:id/tab_switcher", "example.com"))
    }

    @Test
    fun `urlBarText returns null for too-short or blank text`() {
        assertNull(ScreenTextReader.urlBarText("com.android.chrome:id/url_bar", "ab"))
        assertNull(ScreenTextReader.urlBarText("com.android.chrome:id/url_bar", "   "))
    }
}