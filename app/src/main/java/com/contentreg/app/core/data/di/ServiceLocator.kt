package com.contentreg.app.core.data.di

import android.content.Context
import com.contentreg.app.core.data.AppDatabase
import com.contentreg.app.core.data.prefs.SettingsStore
import com.contentreg.app.core.overlay.OverlayManager
import com.contentreg.app.detox.DetoxController
import com.contentreg.app.feature2_url.registry.RegistryRepository
import com.contentreg.app.feature4_retention.stats.StatsRepository

/**
 * Tiny manual dependency container. Both the AccessibilityService and the UI need the same
 * singleton stores; a full DI framework is overkill for this app, so we wire lazily here. [init]
 * must be called once from [com.contentreg.app.App.onCreate] before anything touches these.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val detoxController: DetoxController by lazy { DetoxController(settingsStore) }

    val overlayManager: OverlayManager by lazy { OverlayManager(appContext) }

    val registryRepository: RegistryRepository by lazy { RegistryRepository(database.registryDao()) }

    val statsRepository: StatsRepository by lazy { StatsRepository(appContext) }
}
