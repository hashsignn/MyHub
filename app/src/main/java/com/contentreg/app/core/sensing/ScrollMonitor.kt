package com.contentreg.app.core.sensing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M1.1 — counts scroll activity that happens inside target (feed) apps.
 *
 * [ForegroundService] feeds it every `TYPE_VIEW_SCROLLED` event; it discards anything that isn't
 * from a [targetPackages] app, so scrolling a settings screen or a chat doesn't count. Like
 * [ForegroundAppTracker] this is an in-memory `object`: it is live signal, and the budget tracker
 * (M1.2) is what persists durable state derived from it.
 *
 * [isRecentlyScrolling] is the hook M1.2's timer uses: "budget draws down only while a target app
 * is foreground AND the user scrolled recently" — that recency check lives here.
 */
object ScrollMonitor {

    data class ScrollActivity(
        val totalScrollEvents: Long,
        val lastScrollPackage: String?,
        val lastScrollTimestampMs: Long,
    )

    /** Apps whose scrolls count. Defaults to [TargetApps.DEFAULT]; overridden from settings in M1.4. */
    @Volatile
    var targetPackages: Set<String> = TargetApps.DEFAULT

    private val _activity = MutableStateFlow(
        ScrollActivity(totalScrollEvents = 0L, lastScrollPackage = null, lastScrollTimestampMs = 0L),
    )

    /** Observable scroll activity (count + last target app + last timestamp). */
    val activity: StateFlow<ScrollActivity> = _activity.asStateFlow()

    /** Records a scroll event. No-op unless [packageName] is a target app. */
    fun recordScroll(packageName: String?, timestampMs: Long) {
        if (packageName == null || packageName !in targetPackages) return
        val previous = _activity.value
        _activity.value = previous.copy(
            totalScrollEvents = previous.totalScrollEvents + 1,
            lastScrollPackage = packageName,
            lastScrollTimestampMs = timestampMs,
        )
    }

    /**
     * True if a target-app scroll occurred within [windowMs] before [nowMs]. M1.2's timer uses this
     * so the budget only burns while the user is actively scrolling, not merely sitting on a feed.
     */
    fun isRecentlyScrolling(nowMs: Long, windowMs: Long): Boolean {
        val last = _activity.value.lastScrollTimestampMs
        return last != 0L && (nowMs - last) in 0..windowMs
    }
}
