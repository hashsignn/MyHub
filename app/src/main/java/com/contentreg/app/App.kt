package com.contentreg.app

import android.app.Application
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.feature1_doomscroll.ResetWorker
import com.contentreg.app.feature2_url.classifier.BlocklistSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * M1.2 — Application class. Initializes [ServiceLocator] before the AccessibilityService or any
 * Activity touches the shared singletons (database, settings, budget tracker).
 * M1.4 — schedules the periodic backup budget reset.
 * M2.3 — seeds the curated explicit blocklist into the registry on first run.
 */
class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ResetWorker.schedule(this)
        appScope.launch {
            BlocklistSeeder.seedIfNeeded(
                context = this@App,
                registry = ServiceLocator.registryRepository,
                settings = ServiceLocator.settingsStore,
            )
        }
    }
}
