package com.contentreg.app.feature4_retention.stats

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.statsDataStore by preferencesDataStore(name = "stats")

/**
 * M4.2 — persistent engagement stats for the dashboard: how many times the block screen fired and
 * a simple daily-use streak. Lightweight DataStore counters (separate file from settings).
 */
class StatsRepository(private val context: Context) {

    val blocksTriggered: Flow<Long> = context.statsDataStore.data.map { it[KEY_BLOCKS] ?: 0L }

    val streakDays: Flow<Int> = context.statsDataStore.data.map { it[KEY_STREAK] ?: 0 }

    /** Called when the block overlay newly appears. */
    suspend fun incrementBlocks() {
        context.statsDataStore.edit { prefs ->
            prefs[KEY_BLOCKS] = (prefs[KEY_BLOCKS] ?: 0L) + 1L
        }
    }

    /**
     * Records that the app was used today and updates the streak: +1 if yesterday was the last
     * active day, unchanged if already counted today, otherwise reset to 1.
     */
    suspend fun recordActiveToday(nowMs: Long = System.currentTimeMillis()) {
        val today = nowMs / DAY_MS
        context.statsDataStore.edit { prefs ->
            val last = prefs[KEY_LAST_DAY]
            val streak = prefs[KEY_STREAK] ?: 0
            prefs[KEY_STREAK] = when {
                last == null -> 1
                today == last -> streak.coerceAtLeast(1)
                today == last + 1 -> streak + 1
                else -> 1
            }
            prefs[KEY_LAST_DAY] = today
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private val KEY_BLOCKS = longPreferencesKey("blocks_triggered")
        private val KEY_STREAK = intPreferencesKey("streak_days")
        private val KEY_LAST_DAY = longPreferencesKey("last_active_day")
    }
}
