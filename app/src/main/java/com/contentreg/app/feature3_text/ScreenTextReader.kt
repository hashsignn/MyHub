package com.contentreg.app.feature3_text

import android.view.accessibility.AccessibilityNodeInfo

/**
 * M3.0 — converts an [AccessibilityNodeInfo] tree into a [ScreenSnapshot].
 *
 * Android coupling is isolated to [read] and [toNodeInfo]: they convert the live tree into a
 * pure [NodeInfo] value (no Android types) and then delegate to [findUrl] and [collectText],
 * which are plain Kotlin and fully unit-testable without the framework.
 *
 * Limits keep the walk cheap: at most [MAX_NODES] nodes visited, [MAX_DEPTH] levels deep, and
 * [MAX_TEXT_CHARS] characters of output. The most relevant content is always near the root.
 */
object ScreenTextReader {

    internal const val MAX_NODES = 200
    internal const val MAX_TEXT_CHARS = 5_000
    private const val MAX_DEPTH = 15
    private const val URL_MIN_LEN = 4
    private const val URL_MAX_LEN = 2_048

    /** Pure representation of one node — no Android dependency, suitable for unit tests. */
    internal data class NodeInfo(
        val text: String?,
        val contentDesc: String?,
        val viewId: String?,
        val children: List<NodeInfo>,
    )

    // ── Public API ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a [ScreenSnapshot] from the root returned by [getRootInActiveWindow].
     * Call on the main/accessibility thread. Recycles [root] before returning — do not
     * access [root] after this call.
     */
    fun read(root: AccessibilityNodeInfo?, packageName: String): ScreenSnapshot {
        root ?: return ScreenSnapshot(packageName, null, "")
        // Scan the full live tree for a known URL-bar ID first — Chrome puts the url_bar node
        // at position ~1218 in DFS order (web content comes first), well beyond MAX_NODES=200.
        // A focused live scan reaches it without building the full node tree.
        val liveUrl = findUrlInLiveTree(root)
        val counter = intArrayOf(0)
        val tree = toNodeInfo(root, depth = 0, counter = counter)
        root.recycle()
        return ScreenSnapshot(
            packageName = packageName,
            url = liveUrl ?: findUrl(tree),  // live scan covers ID lookup; tree covers heuristic
            pageText = collectText(tree),
        )
    }

    /**
     * DFS over the raw [AccessibilityNodeInfo] tree with no node-count cap, returning the first
     * URL-bar text found. Recycles child nodes as it goes. Does NOT recycle [node] itself —
     * the caller ([read]) owns the root's lifecycle.
     */
    private fun findUrlInLiveTree(node: AccessibilityNodeInfo): String? {
        val id = node.viewIdResourceName
        if (id != null && id in URL_BAR_IDS) {
            val t = node.text?.toString()?.trim()
            if (!t.isNullOrBlank() && t.length in URL_MIN_LEN..URL_MAX_LEN) return t
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findUrlInLiveTree(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    // ── Framework → pure data ────────────────────────────────────────────────────────────

    private fun toNodeInfo(node: AccessibilityNodeInfo, depth: Int, counter: IntArray): NodeInfo {
        counter[0]++
        val children = if (depth < MAX_DEPTH && counter[0] < MAX_NODES) {
            (0 until node.childCount).mapNotNull { i ->
                val child = node.getChild(i) ?: return@mapNotNull null
                val info = toNodeInfo(child, depth + 1, counter)
                child.recycle()
                info
            }
        } else {
            emptyList()
        }
        return NodeInfo(
            text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            contentDesc = node.contentDescription?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() },
            viewId = node.viewIdResourceName,
            children = children,
        )
    }

    // ── Pure functions (unit-testable) ────────────────────────────────────────────────────

    /**
     * Pass 1: DFS for a known address-bar view ID — exact and reliable.
     * Pass 2: shallow heuristic scan (first 30 nodes) for text that looks like a URL.
     */
    internal fun findUrl(root: NodeInfo): String? = findUrlById(root) ?: findUrlByHeuristic(root)

    private fun findUrlById(node: NodeInfo): String? {
        if (node.viewId != null && node.viewId in URL_BAR_IDS) {
            val t = node.text
            if (!t.isNullOrBlank() && t.length in URL_MIN_LEN..URL_MAX_LEN) return t
        }
        for (child in node.children) {
            val r = findUrlById(child)
            if (r != null) return r
        }
        return null
    }

    private fun findUrlByHeuristic(root: NodeInfo): String? {
        val seen = intArrayOf(0)
        fun search(node: NodeInfo): String? {
            if (seen[0]++ > 30) return null
            val t = node.text
            if (t != null && t.length in URL_MIN_LEN..URL_MAX_LEN && !t.contains('\n') &&
                (t.startsWith("http") || t.startsWith("www.") || t.contains("://"))
            ) return t
            for (child in node.children) {
                val r = search(child)
                if (r != null) return r
            }
            return null
        }
        return search(root)
    }

    /**
     * Concatenates text and contentDescription from all nodes, skipping address-bar nodes
     * (their text is the URL, not page content). Single-character strings are ignored.
     */
    internal fun collectText(root: NodeInfo): String {
        val sb = StringBuilder()
        fun append(s: String) {
            val room = MAX_TEXT_CHARS - sb.length
            if (room <= 0) return
            sb.append(s.take(room)).append(' ')
        }
        fun walk(node: NodeInfo) {
            if (sb.length >= MAX_TEXT_CHARS) return
            val isUrlBar = node.viewId != null && node.viewId in URL_BAR_IDS
            if (!isUrlBar) {
                node.text?.takeIf { it.length > 1 }?.let { append(it) }
                // Include contentDescription only when it differs from text (avoids duplicates).
                node.contentDesc?.takeIf { it.length > 1 && it != node.text }?.let { append(it) }
            }
            node.children.forEach { walk(it) }
        }
        walk(root)
        return sb.toString().trim()
    }

    // ── Known address-bar view IDs ────────────────────────────────────────────────────────

    internal val URL_BAR_IDS: Set<String> = setOf(
        "com.android.chrome:id/url_bar",
        "com.google.android.apps.chrome:id/url_bar",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.microsoft.emmx:id/url_bar",
        "com.brave.browser:id/url_bar",
        "com.opera.browser:id/url_field",
        "com.kiwibrowser.browser:id/url_bar",
        "com.vivaldi.browser:id/url_bar",
    )
}