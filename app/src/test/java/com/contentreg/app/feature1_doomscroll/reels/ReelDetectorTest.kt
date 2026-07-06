package com.contentreg.app.feature1_doomscroll.reels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure reel-surface detection in [ReelDetector]. */
class ReelDetectorTest {

    private val all = ReelApps.supportedPackages

    // ── Instagram (per-surface) ─────────────────────────────────────────────────────────────

    @Test
    fun `instagram reels viewer is detected as a reel surface`() {
        val ids = listOf(
            "com.instagram.android:id/action_bar",
            "com.instagram.android:id/clips_viewer_view_pager",
        )
        assertTrue(ReelDetector.isReelSurface("com.instagram.android", ids, all))
    }

    @Test
    fun `instagram home feed is not a reel surface`() {
        val ids = listOf(
            "com.instagram.android:id/feed_tab",
            "com.instagram.android:id/row_feed_photo",
            "com.instagram.android:id/tab_bar", // the "Reels" tab BUTTON exists, but not the viewer
        )
        assertFalse(ReelDetector.isReelSurface("com.instagram.android", ids, all))
    }

    // ── YouTube (per-surface) ───────────────────────────────────────────────────────────────

    @Test
    fun `youtube shorts player is detected`() {
        val ids = listOf("com.google.android.youtube:id/reel_recycler")
        assertTrue(ReelDetector.isReelSurface("com.google.android.youtube", ids, all))
    }

    @Test
    fun `youtube home is not a reel surface`() {
        val ids = listOf("com.google.android.youtube:id/watch_player")
        assertFalse(ReelDetector.isReelSurface("com.google.android.youtube", ids, all))
    }

    // ── Snapchat (per-surface, best-effort / unverified markers) ────────────────────────────

    @Test
    fun `snapchat spotlight viewer is detected as a reel surface`() {
        val ids = listOf("com.snapchat.android:id/spotlight_view_pager")
        assertTrue(ReelDetector.isReelSurface("com.snapchat.android", ids, all))
    }

    @Test
    fun `snapchat non-spotlight surfaces are not reel surfaces`() {
        // Chat, and crucially the persistent nav-bar Spotlight tab BUTTON, must not trigger a block —
        // otherwise the whole app would be blocked, not just the Spotlight feed.
        val ids = listOf(
            "com.snapchat.android:id/ngs_navigation_bar",
            "com.snapchat.android:id/spotlight_tab_icon", // nav button, present on every screen
            "com.snapchat.android:id/chat_list",
        )
        assertFalse(ReelDetector.isReelSurface("com.snapchat.android", ids, all))
    }

    // ── TikTok (whole-app) ──────────────────────────────────────────────────────────────────

    @Test
    fun `tiktok blocks whenever foreground regardless of view ids`() {
        assertTrue(ReelDetector.isReelSurface("com.zhiliaoapp.musically", emptyList(), all))
        assertTrue(ReelDetector.isReelSurface("com.ss.android.ugc.trill", emptyList(), all))
        assertTrue(ReelDetector.isWholeAppBlock("com.zhiliaoapp.musically"))
    }

    // ── Enabled-set gating ──────────────────────────────────────────────────────────────────

    @Test
    fun `disabled reel app is not blocked even on its reel surface`() {
        val ids = listOf("com.instagram.android:id/clips_viewer_view_pager")
        val enabled = all - "com.instagram.android"
        assertFalse(ReelDetector.isReelSurface("com.instagram.android", ids, enabled))
    }

    // ── Non-reel apps ───────────────────────────────────────────────────────────────────────

    @Test
    fun `unsupported app is never a reel surface`() {
        assertFalse(ReelDetector.isReelSurface("com.android.chrome", listOf("clips_viewer"), all))
        assertFalse(ReelDetector.isSupported("com.android.chrome"))
    }

    @Test
    fun `marker match is case-insensitive`() {
        val ids = listOf("com.instagram.android:id/CLIPS_VIEWER_pager")
        assertTrue(ReelDetector.isReelSurface("com.instagram.android", ids, all))
    }
}
