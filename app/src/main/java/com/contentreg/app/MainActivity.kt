package com.contentreg.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.core.data.prefs.SettingsStore
import com.contentreg.app.core.permissions.PermissionRouter
import com.contentreg.app.core.sensing.ForegroundAppTracker
import com.contentreg.app.core.sensing.ScrollMonitor
import com.contentreg.app.databinding.ActivityMainBinding
import com.contentreg.app.feature1_doomscroll.budget.BudgetMath
import com.contentreg.app.feature1_doomscroll.ui.SettingsActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

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
        binding.grantOverlayButton.setOnClickListener {
            PermissionRouter.openOverlaySettings(this)
        }
        binding.openSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // M1.3 test controls: exhaust/reset the budget without scrolling for minutes. With the
        // budget exhausted, opening a feed app shows the block overlay.
        binding.testExhaustButton.setOnClickListener {
            lifecycleScope.launch {
                ServiceLocator.timeBudgetTracker.debugSetUsedMs(DEBUG_EXHAUST_MS)
            }
        }
        binding.testResetButton.setOnClickListener {
            lifecycleScope.launch {
                ServiceLocator.timeBudgetTracker.debugSetUsedMs(0L)
            }
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
                launch {
                    // Combine the live budget state with the configured budget length so the
                    // readout reflects both accumulation and the (M1.4-editable) allowance.
                    val tracker = ServiceLocator.timeBudgetTracker
                    combine(
                        tracker.state,
                        ServiceLocator.settingsStore.budgetMinutes,
                    ) { state, minutes -> state to minutes }
                        .collect { (state, minutes) ->
                            val budgetMs = SettingsStore.minutesToMs(minutes)
                            val used = formatDuration(state.usedMs)
                            val total = formatDuration(budgetMs)
                            binding.budgetValueText.text = if (BudgetMath.isExhausted(state, budgetMs)) {
                                getString(R.string.m12_budget_exhausted, used, total)
                            } else {
                                val left = formatDuration(BudgetMath.remainingMs(state, budgetMs))
                                getString(R.string.m12_budget_value, used, total, left)
                            }
                        }
                }
            }
        }
    }

    /** Formats a duration in milliseconds as m:ss for the budget readout. */
    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    override fun onResume() {
        super.onResume()
        // Re-check on resume: the user may have toggled the service in system settings and returned.
        refreshAccessibilityState()
    }

    private fun refreshAccessibilityState() {
        val a11yEnabled = PermissionRouter.isAccessibilityServiceEnabled(this)
        binding.accessibilityStateText.text = getString(
            if (a11yEnabled) R.string.m10_accessibility_on else R.string.m10_accessibility_off,
        )
        binding.enableAccessibilityButton.isEnabled = !a11yEnabled

        val canOverlay = PermissionRouter.canDrawOverlays(this)
        binding.overlayStateText.text = getString(
            if (canOverlay) R.string.m13_overlay_on else R.string.m13_overlay_off,
        )
        binding.grantOverlayButton.isEnabled = !canOverlay
    }

    companion object {
        // Large enough to exhaust any sane budget within the current hour window.
        private const val DEBUG_EXHAUST_MS = 24L * 60L * 60L * 1000L
    }
}

