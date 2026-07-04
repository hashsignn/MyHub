package com.contentreg.app.core.util

import android.util.Log
import com.contentreg.app.BuildConfig

/**
 * Logging helper that keeps **sensitive, user-identifying detail** out of release builds.
 *
 * Browsing URLs, blocked/visited domains, and matched keywords are private data. The app never
 * transmits them, but plain `Log.d(...)` calls would still expose them in logcat on a shipped
 * (release) build. [detail] gates such logs behind [BuildConfig.DEBUG] — a compile-time constant —
 * so in release the guarded block is dead-code-eliminated and the message lambda is never even
 * evaluated (no string is built, nothing is emitted).
 *
 * Non-sensitive telemetry (lengths, counts, booleans, fixed upstream IPs) should keep using the
 * normal [Log] API directly so it remains visible in release for field debugging.
 */
object PrivacyLog {

    /**
     * Logs [message] at DEBUG level **only in debug builds**. The lambda is not invoked in release,
     * so callers may freely interpolate URLs / domains / keywords without leaking them to logcat.
     */
    inline fun detail(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }
}
