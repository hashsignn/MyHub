package com.contentreg.app.feature2_url.registry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2.2 — registry behaviour against an in-memory fake DAO. */
class RegistryRepositoryTest {

    private class FakeRegistryDao : RegistryDao {
        val entries = linkedMapOf<String, BlockedEntry>()
        private var nextId = 1L
        override suspend fun isBlocked(key: String): Boolean = entries.containsKey(key)
        override suspend fun find(key: String): BlockedEntry? = entries[key]
        override suspend fun insert(entry: BlockedEntry): Long {
            if (entries.containsKey(entry.normalizedKey)) return -1L
            val id = nextId++
            entries[entry.normalizedKey] = entry.copy(id = id)
            return id
        }
        override suspend fun deleteByKey(key: String) { entries.remove(key) }
        override suspend fun all(): List<BlockedEntry> = entries.values.sortedByDescending { it.createdAtMs }
        override fun count(): Flow<Int> = flowOf(entries.size)
        override fun blockedDomains(): Flow<List<String>> = flowOf(
            entries.values.filter { it.type == BlockEntryType.DOMAIN }.map { it.normalizedKey }
        )
    }

    private fun repo() = RegistryRepository(FakeRegistryDao())

    @Test
    fun `blocked domain matches regardless of www or case`() = runTest {
        val repo = repo()
        assertTrue(repo.addDomain("WWW.Example.com", BlockEntrySource.MANUAL))
        assertTrue(repo.isHostBlocked("example.com"))
        assertTrue(repo.isHostBlocked("Example.com."))
        assertFalse(repo.isHostBlocked("other.com"))
    }

    @Test
    fun `domain block covers all its urls`() = runTest {
        val repo = repo()
        repo.addDomain("example.com", BlockEntrySource.BLOCKLIST)
        assertTrue(repo.isUrlBlocked("https://www.example.com/any/page?x=1"))
    }

    @Test
    fun `exact url block does not block other paths on the same domain`() = runTest {
        val repo = repo()
        repo.addUrl("https://example.com/bad-page", BlockEntrySource.HEURISTIC)
        assertTrue(repo.isUrlBlocked("http://www.example.com/bad-page/"))
        assertFalse(repo.isUrlBlocked("https://example.com/good-page"))
    }

    @Test
    fun `duplicate add is a no-op`() = runTest {
        val repo = repo()
        assertTrue(repo.addDomain("example.com", BlockEntrySource.MANUAL))
        assertFalse(repo.addDomain("example.com", BlockEntrySource.MANUAL))
        assertEquals(1, repo.all().size)
    }

    @Test
    fun `remove unblocks a domain`() = runTest {
        val repo = repo()
        repo.addDomain("example.com", BlockEntrySource.MANUAL)
        repo.removeDomain("www.example.com")
        assertFalse(repo.isHostBlocked("example.com"))
    }
}
