package com.contentreg.app.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.contentreg.app.feature1_doomscroll.reels.ReelApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * User-configurable settings, persisted via DataStore (lightweight key-value, async, survives
 * process death).
 */
class SettingsStore(private val context: Context) {

    /**
     * Which reel apps are actively blocked. Defaults to [ReelApps.supportedPackages]; the user can
     * disable individual surfaces in settings. An empty set means nothing is reel-blocked.
     */
    val blockedReelApps: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCKED_REEL_APPS] ?: ReelApps.supportedPackages
    }

    suspend fun setBlockedReelApps(packages: Set<String>) {
        context.dataStore.edit { prefs -> prefs[KEY_BLOCKED_REEL_APPS] = packages }
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

    /**
     * Which version of the prominent-disclosure text the user has consented to (Task 1). 0 = none.
     * Compared against
     * [com.contentreg.app.feature4_retention.consent.ConsentGate.CURRENT_CONSENT_VERSION] so bumping
     * the disclosure copy re-prompts for fresh consent.
     */
    val consentVersion: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CONSENT_VERSION] ?: 0
    }

    suspend fun setConsentVersion(version: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_CONSENT_VERSION] = version }
    }

    companion object {
        private val KEY_BLOCKED_REEL_APPS = stringSetPreferencesKey("blocked_reel_apps")
        private val KEY_BLOCKLIST_SEED_VERSION = intPreferencesKey("blocklist_seed_version")
        private val KEY_APP_DISGUISE = stringPreferencesKey("app_disguise")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_CONSENT_VERSION = intPreferencesKey("consent_version")
    }
}
