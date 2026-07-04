package com.contentreg.app.feature4_retention.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.contentreg.app.R
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.core.permissions.PermissionRouter
import com.contentreg.app.databinding.ActivityOnboardingBinding
import com.contentreg.app.databinding.ItemOnboardingStepBinding
import com.contentreg.app.feature2_url.FilterVpnService
import com.contentreg.app.feature4_retention.consent.ConsentActivity
import com.contentreg.app.feature4_retention.consent.ConsentGate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * M4.1 — walks the user through granting Accessibility + Overlay (+ optional VPN consent). The app
 * does nothing useful until the required permissions are granted, so this is shown on first run.
 * Each step's status refreshes on resume (the grant screens are system-controlled, so the user
 * leaves and returns).
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val rows = mutableListOf<Pair<OnboardingStep, ItemOnboardingStepBinding>>()

    private val vpnConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) FilterVpnService.start(this)
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.onb_heading)

        OnboardingStep.entries.forEach { step ->
            val row = ItemOnboardingStepBinding.inflate(layoutInflater, binding.stepsContainer, true)
            row.stepTitle.setText(step.titleRes)
            row.stepDesc.setText(step.descRes)
            row.stepButton.setOnClickListener { launchGrant(step) }
            rows += step to row
        }

        binding.finishButton.setOnClickListener {
            lifecycleScope.launch {
                ServiceLocator.settingsStore.setOnboardingComplete(true)
                finish()
            }
        }

        // Task 1 — belt-and-suspenders consent gate: the permission-grant steps must be unreachable
        // without consent. If this screen is ever reached un-consented, bounce to the disclosure.
        lifecycleScope.launch {
            if (ConsentGate.needsConsent(ServiceLocator.settingsStore.consentVersion.first())) {
                startActivity(Intent(this@OnboardingActivity, ConsentActivity::class.java))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun launchGrant(step: OnboardingStep) {
        when (step) {
            OnboardingStep.ACCESSIBILITY -> PermissionRouter.openAccessibilitySettings(this)
            OnboardingStep.OVERLAY -> PermissionRouter.openOverlaySettings(this)
            OnboardingStep.VPN -> {
                val consent = PermissionRouter.prepareVpn(this)
                if (consent != null) vpnConsentLauncher.launch(consent) else FilterVpnService.start(this)
            }
        }
    }

    private fun refresh() {
        var allRequiredGranted = true
        rows.forEach { (step, row) ->
            val granted = step.isGranted(this)
            row.stepStatus.setText(if (granted) R.string.onb_granted else R.string.onb_not_granted)
            row.stepButton.isEnabled = !granted
            if (step.required && !granted) allRequiredGranted = false
        }
        binding.finishButton.isEnabled = allRequiredGranted
    }
}
