package com.contentreg.app.feature1_doomscroll.reels

/**
 * A per-app rule for detecting the short-video ("reel") surface.
 *
 * - [wholeApp] apps (TikTok) are blocked whenever they are foreground — their entire main
 *   experience is short video, so there is no "other tab" to preserve.
 * - Other apps are blocked only when the reel VIEWER is actually on screen, detected by the presence
 *   of a node whose view-id contains one of [viewIdMarkers] (matched case-insensitively as a
 *   substring). Those markers are chosen so they appear on the Reels/Shorts surface but NOT on the
 *   Home/Search/Profile tabs — that is what lets "the reel tab is blocked, other tabs work".
 *
 * These signatures are app-version-specific and **will need on-device tuning** when the apps ship a
 * redesign; keep them centralized here so tuning is one file.
 */
data class ReelRule(
    val packageName: String,
    val viewIdMarkers: List<String> = emptyList(),
    val wholeApp: Boolean = false,
)

/** The built-in catalog of supported reel surfaces. */
object ReelApps {

    // Instagram Reels — the "clips" viewer/pager is present only while actually watching Reels.
    private val INSTAGRAM = ReelRule(
        packageName = "com.instagram.android",
        viewIdMarkers = listOf("clips_viewer", "clips_video", "reel_viewer"),
    )

    // YouTube Shorts — the shorts player/recycler is present only on the Shorts surface.
    private val YOUTUBE = ReelRule(
        packageName = "com.google.android.youtube",
        viewIdMarkers = listOf("reel_recycler", "reel_player", "reel_watch"),
    )

    // Facebook Reels — best-effort; FB's accessibility tree is sparse, most likely to need tuning.
    private val FACEBOOK = ReelRule(
        packageName = "com.facebook.katana",
        viewIdMarkers = listOf("reels_viewer", "reel_viewer", "video_home_reels"),
    )

    // Snapchat Spotlight — Snapchat's vertical short-video feed (its Reels equivalent). Per-tab, so
    // Chat/Camera/Map/Stories stay usable. Snapchat renders most content on a SurfaceView/canvas and
    // obfuscates its resource ids, so its accessibility tree is unusually sparse — these markers
    // target the Spotlight VIEWER container (pager/recycler), which is a real Android view even when
    // its video content is a Surface, and deliberately NOT the persistent nav-bar "Spotlight" tab
    // button (present on every screen — matching it would over-block the whole app).
    //
    // UNVERIFIED best-effort: obfuscation means these very likely need an on-device Spotlight
    // `uiautomator dump` to confirm. Markers are viewer-specific on purpose so the worst case is
    // under-blocking (Spotlight slips through) rather than false-blocking Chat. If Snapchat's tree
    // exposes no Spotlight-specific id at all, this mechanism simply never fires for it — a genuine
    // limitation of view-id detection against canvas-rendered apps (documented in ADR 0005).
    private val SNAPCHAT = ReelRule(
        packageName = "com.snapchat.android",
        viewIdMarkers = listOf("spotlight_view", "spotlight_recycler", "spotlight_pager"),
    )

    // TikTok — the whole app is short-video (block whenever foreground). Two known package names.
    private val TIKTOK = ReelRule("com.zhiliaoapp.musically", wholeApp = true)
    private val TIKTOK_INTL = ReelRule("com.ss.android.ugc.trill", wholeApp = true)

    val ALL: List<ReelRule> = listOf(INSTAGRAM, YOUTUBE, FACEBOOK, SNAPCHAT, TIKTOK, TIKTOK_INTL)

    val byPackage: Map<String, ReelRule> = ALL.associateBy { it.packageName }

    /** All supported reel-app packages — also the default "enabled" set. */
    val supportedPackages: Set<String> = byPackage.keys
}
