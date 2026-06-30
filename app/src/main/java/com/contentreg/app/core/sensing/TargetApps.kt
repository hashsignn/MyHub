package com.contentreg.app.core.sensing

/**
 * M1.1 — the set of "feed" apps whose scrolling counts against the budget.
 *
 * This is the built-in default. M1.4 lets the user edit the list (persisted via SettingsStore);
 * at that point [ScrollMonitor.targetPackages] is overridden from settings and this constant is
 * just the first-run seed.
 *
 * v1 deliberately does NOT try to tell "reels vs. articles" apart — that heuristic is unreliable
 * (people scroll non-feed content in these same apps). We count scrolling while a known feed app
 * is foreground, full stop (per the roadmap).
 */
object TargetApps {

    val DEFAULT: Set<String> = setOf(
        "com.instagram.android",        // Instagram
        "com.zhiliaoapp.musically",     // TikTok
        "com.google.android.youtube",   // YouTube
        "com.twitter.android",          // X / Twitter
        "com.reddit.frontpage",         // Reddit
        "com.facebook.katana",          // Facebook
        "com.snapchat.android",         // Snapchat
    )
}
