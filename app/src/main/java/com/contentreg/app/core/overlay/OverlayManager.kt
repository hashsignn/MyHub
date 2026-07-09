package com.contentreg.app.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.contentreg.app.R
import java.util.EnumSet

/**
 * Owns the single block-overlay window (a `TYPE_APPLICATION_OVERLAY` view via [WindowManager]).
 *
 * The overlay can be requested for more than one independent reason — a reel surface being open
 * ([BlockReason.REEL]) and the on-screen-text classifier ([BlockReason.TEXT]). Each producer toggles
 * *its own* reason via [setReason]; the overlay is visible while **any** reason is active, and shows
 * the subtitle of the highest-priority active reason. This avoids the two producers fighting over a
 * single show/hide flag.
 *
 * Threading: all methods touch the view hierarchy / WindowManager and MUST be called on the main
 * thread (the accessibility service dispatches to Main before calling in).
 */
class OverlayManager(private val context: Context) {

    /** Why the overlay is showing. Ordered by priority (REEL wins the subtitle if both are active). */
    enum class BlockReason { REEL, TEXT }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlay: BlockOverlayView? = null
    private val activeReasons = EnumSet.noneOf(BlockReason::class.java)

    fun isShowing(): Boolean = overlay != null

    /** Turns one block [reason] on or off, then reconciles the overlay's visibility. */
    fun setReason(reason: BlockReason, active: Boolean) {
        val changed = if (active) activeReasons.add(reason) else activeReasons.remove(reason)
        if (changed) render()
    }

    /** Clears every reason and removes the overlay (service teardown). */
    fun clearAll() {
        if (activeReasons.isEmpty() && overlay == null) return
        activeReasons.clear()
        render()
    }

    private fun render() {
        if (activeReasons.isEmpty()) {
            removeOverlay()
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "block requested but overlay permission not granted.")
            return
        }
        val view = overlay ?: addOverlay() ?: return
        view.setSubtitle(context.getString(subtitleRes()))
        // Reel blocks stay translucent; text/URL blocks are fully opaque (theme black/white) so the
        // blocked page is completely hidden. Reel wins when both are active (matches subtitleRes()).
        view.setOpaque(opaque = BlockReason.REEL !in activeReasons)
    }

    private fun addOverlay(): BlockOverlayView? {
        val view = BlockOverlayView(context)
        return runCatching { windowManager.addView(view.root, buildLayoutParams()); view }
            .onSuccess { overlay = it }
            .onFailure { Log.e(TAG, "Failed to add overlay", it) }
            .getOrNull()
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        runCatching { windowManager.removeView(view.root) }
            .onFailure { Log.e(TAG, "Failed to remove overlay", it) }
        overlay = null
    }

    private fun subtitleRes(): Int = when {
        BlockReason.REEL in activeReasons -> R.string.overlay_reel_subtitle
        else -> R.string.overlay_text_subtitle
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        @Suppress("DEPRECATION") // TYPE_APPLICATION_OVERLAY is API 26+; minSdk is 26.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Not focusable, so the system Back key still reaches the app underneath and the user can
            // navigate off the reel tab; still touchable, so taps meant for the reel are swallowed.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
    }

    companion object {
        private const val TAG = "OverlayManager"
    }
}
