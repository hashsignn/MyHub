package com.contentreg.app

import android.app.Application
import com.contentreg.app.core.data.di.ServiceLocator

/**
 * M1.2 — Application class. Its only job right now is to initialize [ServiceLocator] before the
 * AccessibilityService or any Activity touches the shared singletons (the database, settings, and
 * budget tracker).
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
