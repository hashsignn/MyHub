package com.contentreg.app.core.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.contentreg.app.R

/**
 * The block screen's view. Two dynamic parts:
 *  - the subtitle, which explains *why* the screen is blocked (a reel surface vs. a blocked page);
 *  - the style: reel blocks use a translucent scrim (the app underneath stays faintly visible),
 *    while text/URL blocks use a **fully opaque** theme background (white in light, black in dark)
 *    so the blocked page is completely hidden.
 *
 * Kept separate from [OverlayManager] (which owns the WindowManager attachment) so the view logic
 * stays trivial.
 */
class BlockOverlayView(private val context: Context) {

    val root: View = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)

    private val title: TextView = root.findViewById(R.id.overlayTitle)
    private val subtitle: TextView = root.findViewById(R.id.overlaySubtitle)
    private val hint: TextView = root.findViewById(R.id.overlayHint)

    fun setSubtitle(text: CharSequence) {
        subtitle.text = text
    }

    /**
     * [opaque] = true → solid, theme-coloured background with contrasting text (text/URL blocks).
     * [opaque] = false → translucent scrim with light text (reel blocks).
     */
    fun setOpaque(opaque: Boolean) {
        if (opaque) {
            root.setBackgroundColor(color(R.color.block_solid_bg))
            title.setTextColor(color(R.color.block_solid_text))
            subtitle.setTextColor(color(R.color.block_solid_text))
            hint.setTextColor(color(R.color.block_solid_hint))
        } else {
            root.setBackgroundColor(color(R.color.overlay_scrim))
            title.setTextColor(color(R.color.white))
            subtitle.setTextColor(color(R.color.white))
            hint.setTextColor(color(R.color.overlay_text_secondary))
        }
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)
}
