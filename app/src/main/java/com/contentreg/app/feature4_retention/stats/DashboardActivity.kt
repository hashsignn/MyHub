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

/**
 * M4.2 — shows engagement stats: daily streak, blocks triggered, and how many sites are in the block
 * registry. Recording "active today" on open keeps the streak live.
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
                    ServiceLocator.registryRepository.count.collect {
                        binding.registryText.text = getString(R.string.dashboard_registry, it)
                    }
                }
            }
        }
    }
}
