package com.contentreg.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.core.permissions.PermissionRouter
import com.contentreg.app.core.sensing.ForegroundAppTracker
import com.contentreg.app.databinding.ActivityMainBinding
import com.contentreg.app.feature1_doomscroll.ui.SettingsActivity
import com.contentreg.app.feature2_url.FilterVpnService
import com.contentreg.app.feature2_url.registry.BlockEntrySource
import com.contentreg.app.feature4_retention.consent.ConsentActivity
import com.contentreg.app.feature4_retention.consent.ConsentGate
import com.contentreg.app.feature4_retention.onboarding.OnboardingActivity
import com.contentreg.app.feature4_retention.stats.DashboardActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App entry point + a small dev harness: shows permission state, the live foreground package, and
 * the URL-filter controls. On first launch it routes to consent → onboarding.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** M2.1 — launches the system VPN consent dialog; on approval, starts the filter service. */
    private val vpnConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) FilterVpnService.start(this)
            refreshVpnButton()
        }

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
        binding.openOnboardingButton.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        binding.openDashboardButton.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        // Task 1 — consent gate before onboarding on first launch (consent must precede any grant).
        lifecycleScope.launch {
            val store = ServiceLocator.settingsStore
            if (ConsentGate.needsConsent(store.consentVersion.first())) {
                startActivity(Intent(this@MainActivity, ConsentActivity::class.java))
            } else if (!store.onboardingComplete.first()) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
            }
        }

        // M2.1 — URL filter VPN toggle + a quick "block this domain" control for testing.
        binding.vpnToggleButton.setOnClickListener {
            if (FilterVpnService.isRunning.value) {
                FilterVpnService.stop(this)
            } else {
                val consent = PermissionRouter.prepareVpn(this)
                if (consent != null) vpnConsentLauncher.launch(consent) else FilterVpnService.start(this)
            }
        }
        binding.blockDomainButton.setOnClickListener {
            val domain = binding.blockDomainEdit.text?.toString()?.trim().orEmpty()
            if (domain.isNotEmpty()) {
                lifecycleScope.launch {
                    ServiceLocator.registryRepository.addDomain(domain, BlockEntrySource.MANUAL)
                }
                binding.blockDomainEdit.text?.clear()
            }
        }

        // Observe the live foreground app, registry size, and VPN state while this screen is visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ForegroundAppTracker.current.collect { app ->
                        binding.foregroundPackageText.text =
                            app.packageName ?: getString(R.string.m10_foreground_none)
                    }
                }
                launch {
                    ServiceLocator.registryRepository.count.collect { n ->
                        binding.registryCountText.text = getString(R.string.m21_registry_count, n)
                    }
                }
                // Bug #6 — observe live VPN state so the button stays correct even if the OS kills it.
                launch {
                    FilterVpnService.isRunning.collect { running ->
                        binding.vpnToggleButton.setText(
                            if (running) R.string.m21_stop_vpn else R.string.m21_start_vpn,
                        )
                    }
                }
            }
        }
    }

    private fun refreshVpnButton() {
        binding.vpnToggleButton.setText(
            if (FilterVpnService.isRunning.value) R.string.m21_stop_vpn else R.string.m21_start_vpn,
        )
    }

    override fun onResume() {
        super.onResume()
        // Re-check on resume: the user may have toggled a permission in system settings and returned.
        refreshAccessibilityState()
        refreshVpnButton()
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
}
