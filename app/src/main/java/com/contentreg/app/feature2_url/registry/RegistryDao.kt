package com.contentreg.app.feature2_url.registry

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * M2.2 — registry queries. [isBlocked] is the hot path used on every request (lookup-before-
 * classify); it is a cheap indexed EXISTS check.
 */
@Dao
interface RegistryDao {

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_entries WHERE normalizedKey = :key)")
    suspend fun isBlocked(key: String): Boolean

    @Query("SELECT * FROM blocked_entries WHERE normalizedKey = :key LIMIT 1")
    suspend fun find(key: String): BlockedEntry?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: BlockedEntry): Long

    @Query("DELETE FROM blocked_entries WHERE normalizedKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT * FROM blocked_entries ORDER BY createdAtMs DESC")
    suspend fun all(): List<BlockedEntry>

    @Query("SELECT COUNT(*) FROM blocked_entries")
    fun count(): Flow<Int>

    /** Normalized keys of DOMAIN-type entries, observed so the VPN refreshes its snapshot live. */
    @Query("SELECT normalizedKey FROM blocked_entries WHERE type = 'DOMAIN'")
    fun blockedDomains(): Flow<List<String>>
}
