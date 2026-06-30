package com.contentreg.app.feature2_url.registry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * M2.2 — the local registry of blocked entries, checked first on each request so known-bad entries
 * are never re-evaluated. All keys pass through [UrlNormalizer] so cosmetic variants collapse to
 * one entry.
 *
 * Lookups are mechanism-agnostic: the VPN filter (M2.1) calls [isHostBlocked] with the SNI/DNS
 * hostname; the browser address-bar path (later) calls [isUrlBlocked] with the full URL, which also
 * falls back to a domain check so a domain ban implies all its pages.
 */
class RegistryRepository(private val dao: RegistryDao) {

    val count: Flow<Int> = dao.count()

    /** Live set of normalized blocked domains, for the VPN's in-memory snapshot (M2.1). */
    val blockedDomains: Flow<Set<String>> = dao.blockedDomains().map { it.toSet() }

    /** True if the (normalized) host is registered as a blocked domain. */
    suspend fun isHostBlocked(host: String): Boolean {
        val domain = UrlNormalizer.normalizeDomain(host)
        if (domain.isEmpty()) return false
        return dao.isBlocked(domain)
    }

    /**
     * True if the URL is blocked — either its exact normalized form is registered, or its domain
     * is. A domain ban therefore covers every page on it.
     */
    suspend fun isUrlBlocked(url: String): Boolean {
        val domain = UrlNormalizer.domainFromUrl(url)
        if (domain.isNotEmpty() && dao.isBlocked(domain)) return true
        return dao.isBlocked(UrlNormalizer.normalizeUrl(url))
    }

    /** Registers a blocked domain. No-op if already present. */
    suspend fun addDomain(host: String, source: BlockEntrySource): Boolean {
        val domain = UrlNormalizer.normalizeDomain(host)
        if (domain.isEmpty()) return false
        return dao.insert(
            BlockedEntry(
                normalizedKey = domain,
                type = BlockEntryType.DOMAIN,
                source = source,
                createdAtMs = System.currentTimeMillis(),
            ),
        ) != -1L
    }

    /** Registers a blocked exact URL. No-op if already present. */
    suspend fun addUrl(url: String, source: BlockEntrySource): Boolean {
        val normalized = UrlNormalizer.normalizeUrl(url)
        return dao.insert(
            BlockedEntry(
                normalizedKey = normalized,
                type = BlockEntryType.URL,
                source = source,
                createdAtMs = System.currentTimeMillis(),
            ),
        ) != -1L
    }

    suspend fun removeDomain(host: String) {
        dao.deleteByKey(UrlNormalizer.normalizeDomain(host))
    }

    suspend fun all(): List<BlockedEntry> = dao.all()
}
