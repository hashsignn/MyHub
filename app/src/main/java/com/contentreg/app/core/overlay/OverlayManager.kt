package com.contentreg.app.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager

/**
 * M1.3 — owns the single block overlay window: adds/removes a `TYPE_APPLICATION_OVERLAY` view via
 * [WindowManager]. Reused verbatim by M3.2 (text-triggered block), so there is exactly one overlay
 * stack in the app.
 *
 * Threading: [show]/[hide]/[updateCountdown] touch the view hierarchy and WindowManager, so they
 * must be called on the main thread. The caller (ForegroundService's block controller) does that.
 */
class OverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlay: BlockOverlayView? = null

    fun isShowing(): Boolean = overlay != null

    /** Adds the block overlay if not already shown. No-op without overlay permission. */
    fun show() {
        if (overlay != null) return
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "show() ignored: overlay permission not granted.")
            return
        }
        val view = BlockOverlayView(context)
        runCatching { windowManager.addView(view.root, buildLayoutParams()) }
            .onSuccess { overlay = view }
            .onFailure { Log.e(TAG, "Failed to add overlay", it) }
    }

    /** Removes the block overlay if shown. */
    fun hide() {
        val view = overlay ?: return
        runCatching { windowManager.removeView(view.root) }
            .onFailure { Log.e(TAG, "Failed to remove overlay", it) }
        overlay = null
    }

    /** Updates the "resets in" countdown while the overlay is visible. */
    fun updateCountdown(remainingMs: Long) {
        overlay?.setRemainingUntilReset(remainingMs)
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
            // Not focusable (don't steal key input from the system) but still touchable, so the
            // overlay swallows taps meant for the feed underneath. Cover the whole screen.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
    }

    companion object {
        private const val TAG = "OverlayManager"
    }
}
