package com.contentreg.app.feature4_retention.consent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 1 — unit tests for the pure consent-gating logic in [ConsentGate]. */
class ConsentGateTest {

    @Test
    fun `fresh install with no stored consent needs consent`() {
        assertTrue(ConsentGate.needsConsent(0))
    }

    @Test
    fun `stored version below current re-prompts (material disclosure change)`() {
        assertTrue(ConsentGate.needsConsent(ConsentGate.CURRENT_CONSENT_VERSION - 1))
    }

    @Test
    fun `stored version equal to current does not need consent`() {
        assertFalse(ConsentGate.needsConsent(ConsentGate.CURRENT_CONSENT_VERSION))
    }

    @Test
    fun `stored version above current does not need consent`() {
        assertFalse(ConsentGate.needsConsent(ConsentGate.CURRENT_CONSENT_VERSION + 1))
    }
}
