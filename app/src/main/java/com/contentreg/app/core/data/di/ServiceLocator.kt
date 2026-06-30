package com.contentreg.app.core.data.di

import android.content.Context
import com.contentreg.app.core.data.AppDatabase
import com.contentreg.app.core.data.prefs.SettingsStore
import com.contentreg.app.core.overlay.OverlayManager
import com.contentreg.app.core.sensing.ForegroundAppTracker
import com.contentreg.app.core.sensing.ScrollMonitor
import com.contentreg.app.feature1_doomscroll.budget.BudgetRepository
import com.contentreg.app.feature1_doomscroll.budget.BudgetRepositoryRoom
import com.contentreg.app.feature1_doomscroll.budget.TimeBudgetTracker

/**
 * M1.2 — tiny manual dependency container. Both the AccessibilityService and the UI need the same
 * singleton [TimeBudgetTracker] / stores; a full DI framework is overkill for this app, so we wire
 * lazily here. [init] must be called once from [com.contentreg.app.App.onCreate] before anything
 * touches these.
 */
object ServiceLocator {

    /** How recently a scroll must have happened for the budget to keep burning (M1.2). */
    const val RECENT_SCROLL_WINDOW_MS = 2_000L

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val overlayManager: OverlayManager by lazy { OverlayManager(appContext) }

    val budgetRepository: BudgetRepository by lazy { BudgetRepositoryRoom(database.budgetDao()) }

    val timeBudgetTracker: TimeBudgetTracker by lazy {
        TimeBudgetTracker(
            repository = budgetRepository,
            foregroundProvider = { ForegroundAppTracker.currentPackage },
            scrollingProvider = { now -> ScrollMonitor.isRecentlyScrolling(now, RECENT_SCROLL_WINDOW_MS) },
            targetProvider = { ScrollMonitor.targetPackages },
        )
    }
}
