package com.contentreg.app.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.contentreg.app.core.sensing.TargetApps
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

    /**
     * The set of app packages whose scrolling counts (M1.4). Defaults to [TargetApps.DEFAULT] until
     * the user edits the list in settings. An empty set is a valid choice (nothing counts).
     */
    val targetApps: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_TARGET_APPS] ?: TargetApps.DEFAULT
    }

    suspend fun setTargetApps(packages: Set<String>) {
        context.dataStore.edit { prefs -> prefs[KEY_TARGET_APPS] = packages }
    }

    /** Which version of the curated blocklist has been seeded into the registry (M2.3). */
    val blocklistSeedVersion: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCKLIST_SEED_VERSION] ?: 0
    }

    suspend fun setBlocklistSeedVersion(version: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_BLOCKLIST_SEED_VERSION] = version }
    }

    /** Which launcher disguise is active (M4.0). Stored as the enum name; default DEFAULT. */
    val appDisguise: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_DISGUISE] ?: "DEFAULT"
    }

    suspend fun setAppDisguise(name: String) {
        context.dataStore.edit { prefs -> prefs[KEY_APP_DISGUISE] = name }
    }

    /** Whether the permission onboarding flow has been completed once (M4.1). */
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDING_COMPLETE] = complete }
    }

    companion object {
        const val DEFAULT_BUDGET_MINUTES = 5
        const val MIN_BUDGET_MINUTES = 1
        const val MAX_BUDGET_MINUTES = 60
        private val KEY_BUDGET_MINUTES = intPreferencesKey("budget_minutes")
        private val KEY_TARGET_APPS = stringSetPreferencesKey("target_apps")
        private val KEY_BLOCKLIST_SEED_VERSION = intPreferencesKey("blocklist_seed_version")
        private val KEY_APP_DISGUISE = stringPreferencesKey("app_disguise")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

        /** Convenience: minutes → milliseconds. */
        fun minutesToMs(minutes: Int): Long = minutes.toLong() * 60L * 1000L
    }
}
