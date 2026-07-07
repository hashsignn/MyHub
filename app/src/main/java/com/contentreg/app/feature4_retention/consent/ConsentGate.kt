package com.contentreg.app.feature4_retention.consent

/**
 * Task 1 — pure gating logic for the prominent-disclosure consent screen. No Android types, so it
 * is unit-tested directly.
 *
 * Consent is **versioned**: [CURRENT_CONSENT_VERSION] is bumped whenever the disclosure text changes
 * materially. Because the stored version is then older than the current one, [needsConsent] becomes
 * true again and the screen is re-shown so the user re-agrees to the new terms — the Play
 * requirement that consent cover the disclosure the user actually saw.
 */
object ConsentGate {

    /** Bump this whenever the disclosure copy (strings.consent_body) changes materially. */
    // v2: reel-blocking replaced the time-budget wording in the disclosure.
    // v3: added reels-networking, local crash-log and Digital Detox disclosures.
    const val CURRENT_CONSENT_VERSION = 3

    /**
     * True when consent must be (re)collected: the stored version is absent (0) or older than the
     * current disclosure. A stored version equal to or newer than current means already consented.
     */
    fun needsConsent(storedVersion: Int): Boolean = storedVersion < CURRENT_CONSENT_VERSION
}
