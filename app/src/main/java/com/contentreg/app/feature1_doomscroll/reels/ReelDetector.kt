package com.contentreg.app.feature1_doomscroll.reels

/**
 * Pure reel-surface detection — no Android types, so it is unit-tested directly. The live node-tree
 * scan that collects the visible view-ids lives in the accessibility service (device-verified); this
 * object only makes the decision from the collected ids.
 */
object ReelDetector {

    /**
     * True when [packageName] is an **enabled** reel app AND its reel surface is currently showing:
     *  - a whole-app rule (TikTok) blocks whenever the app is foreground;
     *  - otherwise it blocks only when [viewIds] contains a node id matching a reel-viewer marker,
     *    so Home/Search/Profile (which lack those ids) are left alone.
     *
     * [enabled] is the user-controlled subset of [ReelApps.supportedPackages] to act on.
     */
    fun isReelSurface(
        packageName: String,
        viewIds: Collection<String>,
        enabled: Set<String>,
    ): Boolean {
        if (packageName !in enabled) return false
        val rule = ReelApps.byPackage[packageName] ?: return false
        if (rule.wholeApp) return true
        if (rule.viewIdMarkers.isEmpty()) return false
        val lowerIds = viewIds.mapNotNull { id -> id.lowercase().ifBlank { null } }
        return rule.viewIdMarkers.any { marker -> lowerIds.any { it.contains(marker) } }
    }

    /** True if this package has a rule at all (independent of the enabled set). */
    fun isSupported(packageName: String): Boolean = packageName in ReelApps.supportedPackages

    /** True if this package blocks on foreground alone (no tree scan needed). */
    fun isWholeAppBlock(packageName: String): Boolean =
        ReelApps.byPackage[packageName]?.wholeApp == true
}
