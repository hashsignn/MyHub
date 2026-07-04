package com.contentreg.app.core.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.contentreg.app.R

/**
 * The block screen's view. Its only dynamic state is the subtitle, which explains *why* the screen
 * is blocked (a reel surface vs. a blocked page). Kept separate from [OverlayManager] (which owns the
 * WindowManager attachment) so the view logic stays trivial and reusable.
 */
class BlockOverlayView(context: Context) {

    val root: View = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)

    private val subtitle: TextView = root.findViewById(R.id.overlaySubtitle)

    fun setSubtitle(text: CharSequence) {
        subtitle.text = text
    }
}
