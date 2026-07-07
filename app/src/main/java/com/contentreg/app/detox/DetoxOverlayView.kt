package com.contentreg.app.detox

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.contentreg.app.R
import com.google.android.material.button.MaterialButton

/**
 * The Digital Detox lockdown screen. Shows the live countdown and one button per allowed app; the
 * countdown text and app buttons are the only dynamic parts. [DetoxOverlayController] owns the
 * WindowManager attachment.
 */
class DetoxOverlayView(
    private val context: Context,
    private val onLaunchApp: (String) -> Unit,
    private val onUnlock: () -> Unit,
    private val onHome: () -> Unit,
) {
    val root: View = LayoutInflater.from(context).inflate(R.layout.overlay_detox, null)

    private val countdown: TextView = root.findViewById(R.id.detoxCountdown)
    private val allowedContainer: LinearLayout = root.findViewById(R.id.detoxAllowedContainer)
    private val noAllowed: TextView = root.findViewById(R.id.detoxNoAllowed)

    init {
        root.findViewById<MaterialButton>(R.id.detoxHomeButton).setOnClickListener { onHome() }
        root.findViewById<MaterialButton>(R.id.detoxUnlockButton).setOnClickListener { onUnlock() }
    }

    fun setCountdown(text: CharSequence) {
        countdown.text = text
    }

    /** Rebuilds the allowed-app buttons. Called only when the allow-list actually changes. */
    fun bindAllowedApps(apps: List<AppInfo>) {
        allowedContainer.removeAllViews()
        noAllowed.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        apps.forEach { app ->
            val button = MaterialButton(context).apply {
                text = app.label
                setOnClickListener { onLaunchApp(app.packageName) }
            }
            allowedContainer.addView(button)
        }
    }
}
