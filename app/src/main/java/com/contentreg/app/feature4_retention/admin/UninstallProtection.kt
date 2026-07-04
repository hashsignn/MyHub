package com.contentreg.app.feature4_retention.admin

/**
 * Task 2 — pure state/decision helpers for the anti-uninstall **friction ladder**. No Android
 * types, so this is unit-tested directly; the framework side (activation, deactivation) lives in
 * [AdminController].
 *
 * The ladder is friction, not a wall (see ADR 0002): icon disguise makes the app less obvious,
 * device-admin makes it un-deletable until deactivated. Neither survives Safe Mode / adb /
 * factory reset — a true lock needs Device Owner or the Phase-5 ROM.
 */
object UninstallProtection {

    /** Combined protection currently in effect, derived from the two independent, stackable layers. */
    enum class Level { NONE, DISGUISE_ONLY, ADMIN_ONLY, DISGUISE_AND_ADMIN }

    /** What tapping the single protection toggle should do next, given the current admin state. */
    enum class AdminAction { ACTIVATE, DEACTIVATE }

    /** Maps the two stackable layers (icon disguise, device-admin) to a combined [Level]. */
    fun level(disguised: Boolean, adminActive: Boolean): Level = when {
        disguised && adminActive -> Level.DISGUISE_AND_ADMIN
        adminActive -> Level.ADMIN_ONLY
        disguised -> Level.DISGUISE_ONLY
        else -> Level.NONE
    }

    /** A toggle activates when currently off, deactivates when currently on. */
    fun toggleAction(adminActive: Boolean): AdminAction =
        if (adminActive) AdminAction.DEACTIVATE else AdminAction.ACTIVATE
}
