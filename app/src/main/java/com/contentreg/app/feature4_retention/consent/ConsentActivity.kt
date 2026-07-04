package com.contentreg.app.feature4_retention.consent

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.contentreg.app.R
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.databinding.ActivityConsentBinding
import com.contentreg.app.feature4_retention.onboarding.OnboardingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Task 1 — prominent disclosure + affirmative consent.
 *
 * Shown BEFORE any sensitive access can begin (Accessibility monitoring, on-screen text reading,
 * VpnService). On first launch the app routes here ahead of onboarding, and onboarding itself
 * bounces back here until consent is given — so the permission-grant steps are unreachable without
 * an explicit "I understand & agree".
 *
 * Two modes:
 *  - normal: "I understand & agree" persists consent and continues to onboarding; "Not now" (or
 *    Back) closes the app without granting anything.
 *  - review ([EXTRA_REVIEW_MODE]): opened from Settings to re-read the disclosure; shows only a
 *    Close button and changes nothing.
 */
class ConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reviewMode = intent.getBooleanExtra(EXTRA_REVIEW_MODE, false)
        if (reviewMode) {
            binding.agreeButton.visibility = View.GONE
            binding.declineButton.setText(R.string.consent_close)
            binding.declineButton.setOnClickListener { finish() }
        } else {
            binding.agreeButton.setOnClickListener { onAgree() }
            // "Not now" and the system Back button both mean "did not consent" → close the app so
            // no sensitive access can be reached.
            binding.declineButton.setOnClickListener { finishAffinity() }
            onBackPressedDispatcher.addCallback(this) { finishAffinity() }
        }
    }

    private fun onAgree() {
        binding.agreeButton.isEnabled = false
        lifecycleScope.launch {
            val store = ServiceLocator.settingsStore
            store.setConsentVersion(ConsentGate.CURRENT_CONSENT_VERSION)
            // Continue straight into permission onboarding if it hasn't been completed yet.
            if (!store.onboardingComplete.first()) {
                startActivity(Intent(this@ConsentActivity, OnboardingActivity::class.java))
            }
            finish()
        }
    }

    companion object {
        /** When true, opens read-only for re-viewing from Settings (no consent is written). */
        const val EXTRA_REVIEW_MODE = "review_mode"
    }
}
