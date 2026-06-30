package com.contentreg.app.feature2_url.classifier

import android.content.Context
import com.contentreg.app.R
import com.contentreg.app.feature2_url.registry.UrlNormalizer

/**
 * M2.3 — loads the curated explicit-domain blocklist from `res/raw/explicit_blocklist.txt`.
 * Lines starting with `#` and blank lines are ignored; every domain is normalized so it lines up
 * with registry keys and [UrlNormalizer.hostMatchesSet].
 */
object Blocklist {

    fun load(context: Context): Set<String> =
        context.resources.openRawResource(R.raw.explicit_blocklist).bufferedReader().useLines { lines ->
            lines
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .map { UrlNormalizer.normalizeDomain(it) }
                .filter { it.isNotEmpty() }
                .toCollection(LinkedHashSet())
        }
}
