package com.contentreg.app.feature2_url.registry

/**
 * M2.2 — pure normalization for registry keys. No Android types, so it is unit-tested directly.
 *
 * Normalization is what makes "lookup-before-classify" reliable: `HTTP://WWW.Example.com/`,
 * `example.com`, and `https://example.com` must all resolve to the same domain key, or the same
 * bad site would get re-classified under cosmetic variations.
 */
object UrlNormalizer {

    /**
     * Normalizes a hostname: lowercased, trimmed, trailing dot removed, and a leading `www.`
     * stripped so `www.example.com` and `example.com` share one key.
     */
    fun normalizeDomain(host: String): String {
        var h = host.trim().lowercase()
        h = h.removeSuffix(".")
        if (h.startsWith("www.")) h = h.removePrefix("www.")
        return h
    }

    /**
     * True if [host] — or any of its parent domains — is present in [normalizedSet]. So a set
     * containing `example.com` matches `example.com` and `cdn.example.com`. [normalizedSet] must
     * already hold normalized domains.
     */
    fun hostMatchesSet(host: String, normalizedSet: Set<String>): Boolean {
        if (normalizedSet.isEmpty()) return false
        var current = normalizeDomain(host)
        while (current.isNotEmpty()) {
            if (current in normalizedSet) return true
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
        }
        return false
    }

    /**
     * Extracts the registrable host from a URL or bare host string. Returns "" if none can be
     * found. Handles an optional scheme, userinfo, port, path, query and fragment.
     */
    fun domainFromUrl(url: String): String {
        var s = url.trim()
        val scheme = s.indexOf("://")
        if (scheme >= 0) s = s.substring(scheme + 3)
        // Strip path/query/fragment.
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        // Strip userinfo (user:pass@host) and port.
        s = s.substringAfterLast('@').substringBefore(':')
        return normalizeDomain(s)
    }

    /**
     * Normalizes a full URL for exact-path keys: `host/path?query`. The **scheme is dropped** so
     * `http://` and `https://` of the same page collapse to one key (http must not bypass an https
     * block). Host is www-stripped and lowercased, port and fragment dropped, and a single trailing
     * slash trimmed. Query is kept (it can identify a specific page). A root URL therefore reduces
     * to just the domain.
     */
    fun normalizeUrl(url: String): String {
        val raw = url.trim()
        val schemeIdx = raw.indexOf("://")
        val rest = if (schemeIdx >= 0) raw.substring(schemeIdx + 3) else raw

        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }
        val authority = if (authorityEnd >= 0) rest.substring(0, authorityEnd) else rest
        var pathAndQuery = if (authorityEnd >= 0) rest.substring(authorityEnd) else ""

        val host = normalizeDomain(authority.substringAfterLast('@').substringBefore(':'))

        // Drop fragment, trim a single trailing slash on the path portion.
        pathAndQuery = pathAndQuery.substringBefore('#')
        if (pathAndQuery.endsWith("/")) pathAndQuery = pathAndQuery.dropLast(1)

        return "$host$pathAndQuery"
    }
}
