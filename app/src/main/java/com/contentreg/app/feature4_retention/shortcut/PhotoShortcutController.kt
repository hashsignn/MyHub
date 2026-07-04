package com.contentreg.app.feature4_retention.shortcut

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.contentreg.app.MainActivity

/**
 * Task 3 — pins a HOME-SCREEN SHORTCUT whose icon is a user-chosen photo and whose label is custom,
 * launching [MainActivity].
 *
 * This is the **supported approximation** of a custom app icon. Android does not let a normal app
 * swap its own launcher icon to an arbitrary runtime image (icons are static resources; the M4.0
 * `activity-alias` only switches between pre-built icons). A pinned shortcut, however, can carry an
 * arbitrary bitmap — so this delivers the same user-facing "a photo I chose launches the app"
 * outcome. See ADR 0004.
 */
object PhotoShortcutController {

    private const val SHORTCUT_ID = "custom_photo_shortcut"

    /** Whether the current launcher supports pin-shortcut requests (some third-party ones don't). */
    fun isSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Asks the launcher to pin a shortcut with [icon] and [label] that opens [MainActivity]. Returns
     * false (no-op) when the launcher doesn't support pinning. The launcher shows its own confirm UI.
     */
    fun requestPin(context: Context, icon: Bitmap, label: String): Boolean {
        if (!isSupported(context)) return false
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithBitmap(icon))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
