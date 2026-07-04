package com.contentreg.app.feature1_doomscroll.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.contentreg.app.R
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.core.data.prefs.SettingsStore
import com.contentreg.app.databinding.ActivitySettingsBinding
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
 * M1.4 — lets the user set the per-hour budget length and pick which installed apps count toward
 * it. Changes persist to [SettingsStore] immediately; the running service picks up the new
 * target-app set via its settings collector.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings get() = ServiceLocator.settingsStore

    /** Currently-selected target packages; mutated as the user toggles, persisted on each change. */
    private val selected = mutableSetOf<String>()

    private lateinit var adapter: AppListAdapter

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

        setupBudgetSlider()
        setupDisguisePicker()
        setupPrivacy()
        setupAdmin()
        setupShortcut()
        setupAppList()
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

    private fun setupDisguisePicker() {
        binding.disguiseDefaultButton.setOnClickListener { applyDisguise(AppDisguise.DEFAULT) }
        binding.disguiseCalcButton.setOnClickListener { applyDisguise(AppDisguise.CALCULATOR) }
        binding.disguiseNotesButton.setOnClickListener { applyDisguise(AppDisguise.NOTES) }
    }

    private fun applyDisguise(disguise: AppDisguise) {
        IconAliasController.apply(this, disguise)
        lifecycleScope.launch { settings.setAppDisguise(disguise.name) }
    }

    private fun setupBudgetSlider() {
        binding.budgetSlider.valueFrom = SettingsStore.MIN_BUDGET_MINUTES.toFloat()
        binding.budgetSlider.valueTo = SettingsStore.MAX_BUDGET_MINUTES.toFloat()

        lifecycleScope.launch {
            val current = settings.budgetMinutes.first()
                .coerceIn(SettingsStore.MIN_BUDGET_MINUTES, SettingsStore.MAX_BUDGET_MINUTES)
            binding.budgetSlider.value = current.toFloat()
            updateBudgetLabel(current)
        }

        binding.budgetSlider.addOnChangeListener { _, value, fromUser ->
            val minutes = value.toInt()
            updateBudgetLabel(minutes)
            if (fromUser) {
                lifecycleScope.launch { settings.setBudgetMinutes(minutes) }
            }
        }
    }

    private fun updateBudgetLabel(minutes: Int) {
        binding.budgetMinutesLabel.text = getString(R.string.settings_budget_value, minutes)
    }

    private fun setupAppList() {
        adapter = AppListAdapter(emptyList()) { packageName, checked ->
            if (checked) selected.add(packageName) else selected.remove(packageName)
            lifecycleScope.launch { settings.setTargetApps(selected.toSet()) }
        }
        binding.appsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.appsRecyclerView.adapter = adapter

        lifecycleScope.launch {
            selected.clear()
            selected.addAll(settings.targetApps.first())
            val rows = withContext(Dispatchers.IO) { loadInstalledApps() }
            adapter.submit(rows)
            binding.appsProgress.visibility = View.GONE
            binding.appsRecyclerView.visibility = View.VISIBLE
        }
    }

    /** Lists installed, launchable apps (visibility granted by the manifest <queries> block). */
    private fun loadInstalledApps(): List<AppRow> {
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .map { appInfo ->
                AppRow(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = null,  // Bug #5: loaded lazily by AppListAdapter to avoid spinner delay
                    checked = appInfo.packageName in selected,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
