package com.contentreg.app.feature2_url.registry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What kind of key a [BlockedEntry] holds. */
enum class BlockEntryType { DOMAIN, URL }

/** Where an entry came from — useful later for auditing/undo and for the classifier (M2.3). */
enum class BlockEntrySource { BLOCKLIST, HEURISTIC, MANUAL }

/**
 * M2.2 — one blocked entry in the local registry. [normalizedKey] is produced by [UrlNormalizer]
 * (a bare domain for [BlockEntryType.DOMAIN], a normalized full URL for [BlockEntryType.URL]) and
 * is uniquely indexed so a known-bad entry is matched instantly without re-classifying.
 */
@Entity(
    tableName = "blocked_entries",
    indices = [Index(value = ["normalizedKey"], unique = true)],
)
data class BlockedEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val normalizedKey: String,
    val type: BlockEntryType,
    val source: BlockEntrySource,
    val createdAtMs: Long,
)
