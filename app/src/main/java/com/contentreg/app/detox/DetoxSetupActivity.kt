package com.contentreg.app.detox

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.contentreg.app.R
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.databinding.ActivityDetoxSetupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Arms a Digital Detox: choose a duration, tick the apps still allowed, and confirm by typing the
 * signature. On first use the typed phrase becomes the signature; afterwards it must match. Starting
 * requires an explicit "you can only unlock early by donating" confirmation.
 */
class DetoxSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetoxSetupBinding
    private val controller get() = ServiceLocator.detoxController

    /** package -> its checkbox, so we can read the selection when the user commits. */
    private val appChecks = LinkedHashMap<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetoxSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.detox_setup_title)

        loadAllowedAppsList()
        configureSignatureHint()

        binding.startDetoxButton.setOnClickListener { onStartClicked() }
    }

    /** Populate the checkbox list off the main thread (querying every launchable app can be slow). */
    private fun loadAllowedAppsList() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.Default) {
                InstalledApps.launchable(this@DetoxSetupActivity, excludePackage = packageName)
            }
            binding.appsLoadingText.visibility = View.GONE
            apps.forEach { app ->
                val check = CheckBox(this@DetoxSetupActivity).apply {
                    text = app.label
                    textSize = 15f
                    setPadding(paddingLeft, dp(6), paddingRight, dp(6))
                }
                binding.appListContainer.addView(check)
                appChecks[app.packageName] = check
            }
        }
    }

    /** First-time users create a signature; returning users confirm the existing one. */
    private fun configureSignatureHint() {
        lifecycleScope.launch {
            val hasSig = controller.hasSignature.first()
            binding.signatureLayout.hint = getString(
                if (hasSig) R.string.detox_signature_confirm_hint
                else R.string.detox_signature_create_hint,
            )
        }
    }

    private fun onStartClicked() {
        val typed = binding.signatureEdit.text?.toString()?.trim().orEmpty()
        if (typed.isEmpty()) {
            binding.signatureLayout.error = getString(R.string.detox_err_no_signature)
            return
        }
        binding.signatureLayout.error = null

        lifecycleScope.launch {
            if (!controller.hasSignature.first()) {
                controller.setSignature(typed) // first-time: this phrase becomes the signature
            } else if (!controller.matchesSignature(typed)) {
                binding.signatureLayout.error = getString(R.string.detox_err_wrong_signature)
                return@launch
            }
            confirmAndStart()
        }
    }

    /** Final, unmissable reminder that early unlock costs a donation, then arm the detox. */
    private fun confirmAndStart() {
        AlertDialog.Builder(this)
            .setTitle(R.string.detox_setup_title)
            .setMessage(R.string.detox_charity_warning)
            .setPositiveButton(R.string.detox_confirm_yes) { _, _ ->
                val durationMs = selectedDurationMs()
                val allowed = appChecks.filterValues { it.isChecked }.keys.toSet()
                lifecycleScope.launch {
                    controller.start(durationMs, allowed)
                    Toast.makeText(
                        this@DetoxSetupActivity,
                        R.string.detox_started,
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                }
            }
            .setNegativeButton(R.string.detox_cancel, null)
            .show()
    }

    private fun selectedDurationMs(): Long = when (binding.durationGroup.checkedRadioButtonId) {
        R.id.dur30m -> 30L * 60_000L
        R.id.dur2h -> 2L * 60L * 60_000L
        R.id.dur4h -> 4L * 60L * 60_000L
        R.id.dur8h -> 8L * 60L * 60_000L
        else -> 60L * 60_000L // dur1h (default)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
