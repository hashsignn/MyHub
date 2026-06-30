package com.contentreg.app.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * M1.2 — user-configurable settings, persisted via DataStore (lightweight key-value, async,
 * survives process death). For now it holds the budget length; M1.4 adds the editable target-app
 * set here too.
 */
class SettingsStore(private val context: Context) {

    /** The per-hour budget in minutes (default [DEFAULT_BUDGET_MINUTES]). */
    val budgetMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_BUDGET_MINUTES] ?: DEFAULT_BUDGET_MINUTES
    }

    suspend fun setBudgetMinutes(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_BUDGET_MINUTES] = minutes.coerceAtLeast(1) }
    }

    companion object {
        const val DEFAULT_BUDGET_MINUTES = 5
        private val KEY_BUDGET_MINUTES = intPreferencesKey("budget_minutes")

        /** Convenience: minutes → milliseconds. */
        fun minutesToMs(minutes: Int): Long = minutes.toLong() * 60L * 1000L
    }
}
