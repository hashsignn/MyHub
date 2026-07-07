package com.contentreg.app.detox

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.WindowManager
import com.contentreg.app.R

/**
 * Owns the single full-screen Digital Detox lockdown window (`TYPE_APPLICATION_OVERLAY`).
 *
 * The accessibility service decides *when* the lockdown should be visible (a blocked app is
 * foreground) and calls [show]/[hide]; this class just draws it, launches allowed apps, and runs a
 * one-second countdown ticker. All methods must be called on the main thread.
 */
class DetoxOverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // Material components (buttons in the overlay) need a Material-themed context; the app context's
    // theme isn't guaranteed to be one, so wrap it explicitly.
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_ContentRegApp)

    private var view: DetoxOverlayView? = null
    private var endTimeMs: Long = 0L
    private var boundAllowed: Set<String> = emptySet()

    private val tick = object : Runnable {
        override fun run() {
            view?.setCountdown(DetoxFormat.hms(endTimeMs - System.currentTimeMillis()))
            handler.postDelayed(this, 1_000L)
        }
    }

    fun isShowing(): Boolean = view != null

    /** Shows (or updates) the lockdown for the given window and allow-list. */
    fun show(endTimeMs: Long, allowedApps: Set<String>) {
        this.endTimeMs = endTimeMs
        if (view == null) {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "detox lockdown requested but overlay permission not granted.")
                return
            }
            val v = DetoxOverlayView(
                context = themedContext,
                onLaunchApp = ::launchApp,
                onUnlock = ::openUnlock,
                onHome = ::goHome,
            )
            val ok = runCatching { windowManager.addView(v.root, buildLayoutParams()) }
                .onFailure { Log.e(TAG, "Failed to add detox overlay", it) }
                .isSuccess
            if (!ok) return
            view = v
            boundAllowed = emptySet()
        }
        // Only rebuild the app buttons when the allow-list actually changes (label lookup isn't free).
        if (allowedApps != boundAllowed) {
            view?.bindAllowedApps(InstalledApps.infoFor(context, allowedApps))
            boundAllowed = allowedApps
        }
        handler.removeCallbacks(tick)
        handler.post(tick) // updates countdown immediately, then every second
    }

    fun hide() {
        handler.removeCallbacks(tick)
        val v = view ?: return
        runCatching { windowManager.removeView(v.root) }
            .onFailure { Log.e(TAG, "Failed to remove detox overlay", it) }
        view = null
        boundAllowed = emptySet()
    }

    private fun launchApp(pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "Failed to launch $pkg", it) }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(home) }
    }

    private fun openUnlock() {
        val intent = Intent(context, DetoxUnlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Not focusable so we don't steal the keyboard/back, but touchable so its own buttons work
            // and taps can't reach the blocked app underneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
    }

    companion object {
        private const val TAG = "DetoxOverlay"
    }
}
