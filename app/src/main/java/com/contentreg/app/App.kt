package com.contentreg.app

import android.app.Application
import com.contentreg.app.core.data.di.ServiceLocator
import com.contentreg.app.feature1_doomscroll.ResetWorker

/**
 * M1.2 — Application class. Initializes [ServiceLocator] before the AccessibilityService or any
 * Activity touches the shared singletons (database, settings, budget tracker).
 * M1.4 — also schedules the periodic backup budget reset.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ResetWorker.schedule(this)
    }
}
