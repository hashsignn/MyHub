package com.contentreg.app.feature4_retention.stats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.contentreg.app.R
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.databinding.ActivityDashboardBinding
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * M4.2 — shows engagement stats: daily streak, feed blocks triggered, feed time used this hour, and
 * how many sites are in the block registry. Recording "active today" on open keeps the streak live.
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.dashboard_title)

        lifecycleScope.launch { ServiceLocator.statsRepository.recordActiveToday() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ServiceLocator.statsRepository.streakDays.collect {
                        binding.streakText.text = getString(R.string.dashboard_streak, it)
                    }
                }
                launch {
                    ServiceLocator.statsRepository.blocksTriggered.collect {
                        binding.blocksText.text = getString(R.string.dashboard_blocks, it)
                    }
                }
                launch {
                    ServiceLocator.timeBudgetTracker.state.collect { state ->
                        binding.usageText.text =
                            getString(R.string.dashboard_usage, formatDuration(state.usedMs))
                    }
                }
                launch {
                    ServiceLocator.registryRepository.count.collect {
                        binding.registryText.text = getString(R.string.dashboard_registry, it)
                    }
                }
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000L
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }
}
