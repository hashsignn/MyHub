package com.contentreg.app.feature1_doomscroll.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.contentreg.app.R
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.databinding.ActivitySettingsBinding
import com.contentreg.app.detox.DetoxFormat
import com.contentreg.app.feature4_retention.AppDisguise
import com.contentreg.app.feature4_retention.IconAliasController
import com.contentreg.app.feature4_retention.admin.AdminController
import com.contentreg.app.feature4_retention.admin.UninstallProtection
import com.contentreg.app.feature4_retention.consent.ConsentActivity
import com.contentreg.app.feature4_retention.shortcut.IconImageStore
import com.contentreg.app.feature4_retention.shortcut.PhotoShortcutController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings: which short-video (reel) surfaces to block, app disguise, uninstall protection, a
 * custom-photo home-screen shortcut, and re-viewing the privacy disclosure. Changes persist to
 * [com.contentreg.app.core.data.prefs.SettingsStore] immediately; the running service picks up the
 * reel-app set via its settings collector.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings get() = ServiceLocator.settingsStore

    /** Each reel toggle → the package(s) it controls (TikTok has two known package names). */
    private val reelToggles: List<Pair<CheckBox, Set<String>>> by lazy {
        listOf(
            binding.reelInstagram to setOf("com.instagram.android"),
            binding.reelYoutube to setOf("com.google.android.youtube"),
            binding.reelFacebook to setOf("com.facebook.katana"),
            binding.reelSnapchat to setOf("com.snapchat.android"),
            binding.reelTiktok to setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        )
    }

    /** Task 3 — modern Photo Picker (no storage permission); result pins a custom-icon shortcut. */
    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onPhotoPicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_title)

        setupReels()
        setupDetox()
        setupDisguisePicker()
        setupPrivacy()
        setupAdmin()
        setupShortcut()
    }

    /** Digital Detox — show status and manage the signature (locked while a detox is active). */
    private fun setupDetox() {
        binding.detoxSaveSignatureButton.setOnClickListener { onSaveSignature() }
        refreshDetoxUi()
    }

    private fun refreshDetoxUi() {
        lifecycleScope.launch {
            val controller = ServiceLocator.detoxController
            val state = controller.snapshot()
            val hasSignature = controller.hasSignature.first()
            val active = state.isActive()

            binding.detoxSettingsStatus.text = if (active) {
                getString(
                    R.string.settings_detox_active,
                    DetoxFormat.compact(state.remainingMs()),
                    state.allowedApps.size,
                )
            } else {
                getString(R.string.settings_detox_inactive)
            }
            binding.detoxSignatureStatus.setText(
                if (hasSignature) R.string.detox_sig_set else R.string.detox_sig_unset,
            )

            // Lock signature editing during a detox — otherwise "change signature → unlock" would
            // sidestep the charity early-unlock entirely.
            binding.detoxSignatureLockNote.visibility = if (active) View.VISIBLE else View.GONE
            binding.detoxCurrentSignatureEdit.isEnabled = !active
            binding.detoxNewSignatureEdit.isEnabled = !active
            binding.detoxSaveSignatureButton.isEnabled = !active
            // The "current signature" field only matters when replacing an existing one.
            binding.detoxCurrentSignatureEdit.visibility =
                if (!active && hasSignature) View.VISIBLE else View.GONE
        }
    }

    private fun onSaveSignature() {
        val newSignature = binding.detoxNewSignatureEdit.text?.toString()?.trim().orEmpty()
        val currentInput = binding.detoxCurrentSignatureEdit.text?.toString()?.trim().orEmpty()
        lifecycleScope.launch {
            val controller = ServiceLocator.detoxController
            // Re-check at commit time: never change the signature while a detox is running.
            if (controller.snapshot().isActive()) {
                toast(getString(R.string.detox_sig_locked))
                refreshDetoxUi()
                return@launch
            }
            if (newSignature.isEmpty()) {
                toast(getString(R.string.detox_sig_need_new))
                return@launch
            }
            if (controller.hasSignature.first() && !controller.matchesSignature(currentInput)) {
                toast(getString(R.string.detox_err_wrong_signature))
                return@launch
            }
            controller.setSignature(newSignature)
            binding.detoxCurrentSignatureEdit.text?.clear()
            binding.detoxNewSignatureEdit.text?.clear()
            toast(getString(R.string.detox_sig_saved))
            refreshDetoxUi()
        }
    }

    /** Which short-video surfaces are blocked; synced live into the accessibility service. */
    private fun setupReels() {
        lifecycleScope.launch {
            val enabled = settings.blockedReelApps.first()
            reelToggles.forEach { (checkbox, packages) ->
                checkbox.isChecked = packages.any { it in enabled }
                // Attach the listener AFTER setting the initial state so it doesn't fire spuriously.
                checkbox.setOnCheckedChangeListener { _, _ -> persistReels() }
            }
        }
    }

    private fun persistReels() {
        val enabled = reelToggles.filter { it.first.isChecked }.flatMap { it.second }.toSet()
        lifecycleScope.launch { settings.setBlockedReelApps(enabled) }
    }

    private fun setupDisguisePicker() {
        binding.disguiseDefaultButton.setOnClickListener { applyDisguise(AppDisguise.DEFAULT) }
        binding.disguiseCalcButton.setOnClickListener { applyDisguise(AppDisguise.CALCULATOR) }
        binding.disguiseNotesButton.setOnClickListener { applyDisguise(AppDisguise.NOTES) }
    }

    private fun applyDisguise(disguise: AppDisguise) {
        IconAliasController.apply(this, disguise)
        lifecycleScope.launch { settings.setAppDisguise(disguise.name) }
    }

    /** Task 1 — re-view the prominent-disclosure text on demand (read-only). */
    private fun setupPrivacy() {
        binding.viewDisclosureButton.setOnClickListener {
            startActivity(
                Intent(this, ConsentActivity::class.java)
                    .putExtra(ConsentActivity.EXTRA_REVIEW_MODE, true),
            )
        }
    }

    /** Task 2 — optional Device-Admin uninstall protection: reflect state + toggle on/off. */
    private fun setupAdmin() {
        binding.adminToggleButton.setOnClickListener {
            when (UninstallProtection.toggleAction(AdminController.isActive(this))) {
                UninstallProtection.AdminAction.ACTIVATE ->
                    startActivity(AdminController.buildActivationIntent(this))
                UninstallProtection.AdminAction.DEACTIVATE -> {
                    AdminController.deactivate(this)
                    refreshAdminUi()
                }
            }
        }
        refreshAdminUi()
    }

    private fun refreshAdminUi() {
        val active = AdminController.isActive(this)
        binding.adminStatusText.setText(if (active) R.string.admin_status_on else R.string.admin_status_off)
        binding.adminToggleButton.setText(if (active) R.string.admin_turn_off else R.string.admin_turn_on)
    }

    override fun onResume() {
        super.onResume()
        // Re-read admin state after returning from the system activation screen.
        refreshAdminUi()
        // Detox may have been armed/ended elsewhere (home screen, overlay) since we loaded.
        refreshDetoxUi()
    }

    /** Task 3 — pick a photo and pin a custom-icon home-screen shortcut that opens the app. */
    private fun setupShortcut() {
        binding.addShortcutButton.setOnClickListener {
            if (!PhotoShortcutController.isSupported(this)) {
                toast(getString(R.string.shortcut_unsupported))
            } else {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
    }

    private fun onPhotoPicked(uri: Uri) {
        val label = binding.shortcutLabelEdit.text?.toString()?.trim()?.ifEmpty { null }
            ?: getString(R.string.shortcut_default_label)
        lifecycleScope.launch {
            // Decode + crop/scale/persist off the main thread; pin request on the main thread.
            val icon = withContext(Dispatchers.IO) {
                val source = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                source?.let { IconImageStore.save(this@SettingsActivity, it) }
            }
            if (icon == null) {
                toast(getString(R.string.shortcut_error))
            } else {
                val requested = PhotoShortcutController.requestPin(this@SettingsActivity, icon, label)
                toast(getString(if (requested) R.string.shortcut_requested else R.string.shortcut_unsupported))
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
