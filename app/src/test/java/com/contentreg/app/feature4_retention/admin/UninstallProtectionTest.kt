package com.contentreg.app.feature4_retention.admin

import org.junit.Assert.assertEquals
import org.junit.Test

/** Task 2 — unit tests for the pure friction-ladder helpers in [UninstallProtection]. */
class UninstallProtectionTest {

    @Test
    fun `level is NONE when both layers are off`() {
        assertEquals(
            UninstallProtection.Level.NONE,
            UninstallProtection.level(disguised = false, adminActive = false),
        )
    }

    @Test
    fun `level is DISGUISE_ONLY when only disguised`() {
        assertEquals(
            UninstallProtection.Level.DISGUISE_ONLY,
            UninstallProtection.level(disguised = true, adminActive = false),
        )
    }

    @Test
    fun `level is ADMIN_ONLY when only admin active`() {
        assertEquals(
            UninstallProtection.Level.ADMIN_ONLY,
            UninstallProtection.level(disguised = false, adminActive = true),
        )
    }

    @Test
    fun `level is DISGUISE_AND_ADMIN when both layers on`() {
        assertEquals(
            UninstallProtection.Level.DISGUISE_AND_ADMIN,
            UninstallProtection.level(disguised = true, adminActive = true),
        )
    }

    @Test
    fun `toggle activates when off and deactivates when on`() {
        assertEquals(
            UninstallProtection.AdminAction.ACTIVATE,
            UninstallProtection.toggleAction(adminActive = false),
        )
        assertEquals(
            UninstallProtection.AdminAction.DEACTIVATE,
            UninstallProtection.toggleAction(adminActive = true),
        )
    }
}
