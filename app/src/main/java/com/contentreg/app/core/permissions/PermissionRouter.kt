package com.contentreg.app.core.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.contentreg.app.core.sensing.ForegroundService

/**
 * M1.0 — routes the user to the system screens that grant the permissions this app needs, and
 * checks whether those grants are in place.
 *
 * Accessibility (and later overlay / VPN) permissions cannot be granted with a runtime dialog;
 * they live on system-controlled Settings screens. All this class does is send the right intent
 * and report current state — the onboarding UX that wraps it lands in M4.1.
 */
object PermissionRouter {

    /** Opens the system Accessibility settings so the user can enable [ForegroundService]. */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * True when this app's [ForegroundService] is enabled in Accessibility settings.
     *
     * Reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` (a colon-separated list of
     * `package/ServiceClass` entries) rather than trusting a flag we set ourselves — the user can
     * toggle the service off in system settings at any time, behind our back.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ForegroundService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
        for (component in splitter) {
            if (component.equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    /** M1.3 — true when the user has granted "draw over other apps" (overlay) permission. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Opens the system "Display over other apps" screen for this app (M1.3). */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
