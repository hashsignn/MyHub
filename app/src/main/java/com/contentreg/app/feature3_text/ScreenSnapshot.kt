package com.contentreg.app.feature3_text

/**
 * M3.0 — a point-in-time view of what the user sees: the current URL (from the browser
 * address bar) and a text excerpt of visible page content, both extracted from the
 * accessibility node tree by [ScreenTextReader].
 *
 * Either field can be absent:
 *  - [url] is null when no recognisable address bar is found (native apps, system UI).
 *  - [pageText] is empty for Canvas/SurfaceView-rendered apps opaque to accessibility.
 *
 * Consumed by [ContextClassifier] (M3.1) and [TextBlockDecider] (M3.2).
 */
data class ScreenSnapshot(
    val packageName: String,
    /** Raw address-bar text; may be a bare domain, full URL, or a fragment. */
    val url: String?,
    /** Visible text concatenated from node tree, capped at 5 000 chars. */
    val pageText: String,
) {
    /** True when there is something worth classifying. */
    val hasContent: Boolean get() = url != null || pageText.isNotEmpty()
}