package com.contentreg.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.contentreg.app.databinding.ActivityMainBinding

/**
 * M0.0 — app entry point.
 *
 * For Phase 0 this is just the "blank app runs on a real phone" milestone: it inflates a
 * single screen and shows a status line. As later phases land, this activity grows into the
 * host for the onboarding flow (M4.1) and the stats dashboard (M4.2).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusText.text = getString(R.string.phase0_status)
    }
}
