package com.contentreg.app.core.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.contentreg.app.R
import java.util.Locale

/**
 * M1.3 — the block screen's view + its only piece of dynamic state, the "resets in mm:ss"
 * countdown. Kept separate from [OverlayManager] (which owns the WindowManager attachment) so the
 * view logic is simple and reusable; M3.2 shows the same overlay for a different reason.
 */
class BlockOverlayView(context: Context) {

    val root: View = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)

    private val countdown: TextView = root.findViewById(R.id.overlayCountdownText)

    /** Updates the countdown to the next hourly reset. Negative values clamp to 0. */
    fun setRemainingUntilReset(remainingMs: Long) {
        val totalSeconds = (remainingMs.coerceAtLeast(0L)) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        countdown.text = root.context.getString(
            R.string.overlay_block_reset,
            String.format(Locale.US, "%d:%02d", minutes, seconds),
        )
    }
}
