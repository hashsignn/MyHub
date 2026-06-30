package com.contentreg.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.contentreg.app.core.permissions.PermissionRouter
import com.contentreg.app.core.sensing.ForegroundAppTracker
import com.contentreg.app.core.sensing.ScrollMonitor
import com.contentreg.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * M0.0 — app entry point. M1.0 — also the test harness for foreground sensing:
 * shows whether the accessibility service is enabled, routes the user to enable it, and displays
 * the live foreground package so the "open Instagram → see com.instagram.android" milestone is
 * verifiable on-device. Grows into onboarding (M4.1) / dashboard (M4.2) later.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusText.text = getString(R.string.phase0_status)
        binding.enableAccessibilityButton.setOnClickListener {
            PermissionRouter.openAccessibilitySettings(this)
        }

        // Observe the live foreground app + scroll activity while this screen is visible.
        // repeatOnLifecycle ties the collectors to STARTED so they stop when backgrounded.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ForegroundAppTracker.current.collect { app ->
                        binding.foregroundPackageText.text =
                            app.packageName ?: getString(R.string.m10_foreground_none)
                    }
                }
                launch {
                    ScrollMonitor.activity.collect { activity ->
                        binding.scrollCountText.text = if (activity.totalScrollEvents == 0L) {
                            getString(R.string.m11_scroll_none)
                        } else {
                            getString(
                                R.string.m11_scroll_value,
                                activity.totalScrollEvents,
                                activity.lastScrollPackage,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check on resume: the user may have toggled the service in system settings and returned.
        refreshAccessibilityState()
    }

    private fun refreshAccessibilityState() {
        val enabled = PermissionRouter.isAccessibilityServiceEnabled(this)
        binding.accessibilityStateText.text = getString(
            if (enabled) R.string.m10_accessibility_on else R.string.m10_accessibility_off,
        )
        binding.enableAccessibilityButton.isEnabled = !enabled
    }
}
