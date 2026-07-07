package com.contentreg.app.detox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.contentreg.app.R
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.databinding.ActivityDetoxUnlockBinding
import kotlinx.coroutines.launch

/**
 * The only early exit from a detox: donate to a charity (honour system), then type the signature to
 * end the lockdown. Reached from the lockdown overlay's "Unlock early" button.
 */
class DetoxUnlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetoxUnlockBinding
    private val controller get() = ServiceLocator.detoxController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetoxUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.detox_unlock_title)

        binding.charityGiveWell.setOnClickListener { openUrl(GIVEWELL) }
        binding.charityRedCross.setOnClickListener { openUrl(RED_CROSS) }
        binding.charityUnicef.setOnClickListener { openUrl(UNICEF) }

        binding.unlockButton.setOnClickListener { onUnlockClicked() }
    }

    private fun onUnlockClicked() {
        val typed = binding.unlockSignatureEdit.text?.toString()?.trim().orEmpty()
        lifecycleScope.launch {
            if (controller.matchesSignature(typed)) {
                controller.end()
                Toast.makeText(this@DetoxUnlockActivity, R.string.detox_unlock_done, Toast.LENGTH_SHORT)
                    .show()
                finish()
            } else {
                binding.unlockSignatureLayout.error = getString(R.string.detox_err_wrong_signature)
            }
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(intent) }
    }

    companion object {
        private const val GIVEWELL = "https://www.givewell.org/"
        private const val RED_CROSS = "https://www.redcross.org/donate/donation.html"
        private const val UNICEF = "https://www.unicef.org/"
    }
}
