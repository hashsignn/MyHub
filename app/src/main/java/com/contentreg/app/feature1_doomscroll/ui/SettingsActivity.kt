package com.contentreg.app.feature1_doomscroll.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.contentreg.app.R
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.core.data.prefs.SettingsStore
import com.contentreg.app.databinding.ActivitySettingsBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_title)

        setupBudgetSlider()
        setupAppList()
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
                    icon = pm.getApplicationIcon(appInfo),
                    checked = appInfo.packageName in selected,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
