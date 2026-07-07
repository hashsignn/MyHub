package com.contentreg.app.core.util

import android.content.Context
import android.provider.Settings

/**
 * Detects whether the device has Private DNS (DNS-over-TLS) enabled. When it is on, DNS lookups are
 * encrypted and sent straight to the chosen resolver — they never pass through our local VPN, so the
 * URL/domain filter can't see or block them. The reel-surface and on-screen-text blocking don't rely
 * on DNS and keep working; we surface a warning so the user understands the URL filter's limit.
 */
object PrivateDns {

    /** True when Private DNS is set to "automatic/opportunistic" or a specific hostname (i.e. not off). */
    fun isActive(context: Context): Boolean {
        val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
        // Absent on very old builds → treat as off. "off" → off. Anything else ("opportunistic",
        // "hostname") means it's on and will bypass the filter.
        return mode != null && !mode.equals("off", ignoreCase = true)
    }
}
