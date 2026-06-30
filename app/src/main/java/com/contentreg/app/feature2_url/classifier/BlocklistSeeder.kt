package com.contentreg.app.feature2_url.classifier

import android.content.Context
import com.contentreg.app.core.data.prefs.SettingsStore
import com.contentreg.app.feature2_url.registry.BlockEntrySource
import com.contentreg.app.feature2_url.registry.RegistryRepository
import kotlinx.coroutines.flow.first

/**
 * M2.3 — seeds the curated explicit-domain blocklist into the registry on first run.
 *
 * Idempotent and versioned: it only runs when the persisted seed version is behind [SEED_VERSION],
 * so editing `explicit_blocklist.txt` and bumping [SEED_VERSION] re-seeds on the next launch.
 * Registry inserts ignore duplicates, so re-seeding never creates double entries.
 */
object BlocklistSeeder {

    /** Bump this whenever explicit_blocklist.txt changes so devices pick up the new entries. */
    const val SEED_VERSION = 1

    suspend fun seedIfNeeded(
        context: Context,
        registry: RegistryRepository,
        settings: SettingsStore,
    ) {
        if (settings.blocklistSeedVersion.first() >= SEED_VERSION) return
        for (domain in Blocklist.load(context)) {
            registry.addDomain(domain, BlockEntrySource.BLOCKLIST)
        }
        settings.setBlocklistSeedVersion(SEED_VERSION)
    }
}
