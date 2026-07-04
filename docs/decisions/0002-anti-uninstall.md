# ADR 0002 — Anti-uninstall: friction, not a wall (Task 2)

**Status:** Accepted · **Date:** 2026-07-04 · **Task:** 2 (anti-uninstall friction)

## Context

A self-control app is only useful if a moment of weakness can't trivially undo it. But on a
**normal (non-rooted, unmanaged) consumer device an app cannot be made truly undeletable** — Safe
Mode (which disables third-party admins and services), `adb uninstall`, and factory reset always
win. Promising "undeletable" would be dishonest and unshippable. So the goal is **friction**: raise
the effort/awareness cost of removal, not claim an impossible wall.

## Decision — the friction ladder

1. **Icon disguise (M4.0, already shipped).** An `activity-alias` presents the app as e.g.
   "Calculator" / "Notes" so it's less obvious to find and remove. A disguised launch still routes
   to `MainActivity` (the alias `targetActivity`), so disguise and the layers below stack cleanly.
2. **Device Admin (this task).** `ContentRegDeviceAdminReceiver` + `AdminController`. While the
   admin is **active**, Android blocks the normal Uninstall path until the user deactivates it.
   - Activation is **optional and user-initiated** — an optional step in onboarding and a toggle in
     Settings. Never forced.
   - `onDisableRequested()` returns a clear warning the system shows before deactivation.
   - The receiver requests **no policies** (no lock/wipe/password). Being an active admin is all
     that's needed to gate uninstall; requesting more would be intrusive and hurt Play review.
   - In-app **deactivate** (`removeActiveAdmin`) is always available so the user is never trapped,
     and **re-activation** works normally afterward.
3. **True lock — out of scope here.** Actually preventing removal requires **Device Owner**
   (provisioned via `adb`/QR on a fresh device, one owner per device) or the **Phase-5 forked ROM**,
   where these components ship as privileged system services. Documented as the ceiling; not built
   in the features phase.

## Explicitly rejected

- **"Accessibility watches the uninstall/settings screen and backs out"** — auto-dismissing the
  system uninstall or device-admin screens via the a11y service. Brittle across OEM UIs and OS
  versions, hostile to the user, and a **Play-policy rejection risk** (interfering with system
  settings / using accessibility for non-accessibility coercion). Left as a documented,
  **off-by-default, non-default** option only; not implemented.

## Play-policy implications

- Device Admin apps get extra scrutiny and the API is de-emphasized in favor of Android Enterprise;
  a self-control uninstall-gate is a legitimate but non-standard use — expect to justify it.
- Because activation is optional, clearly explained, non-policy, and reversible in-app, it stays on
  the defensible side. If Play still objects, this feature degrades gracefully (disguise remains)
  and full enforcement moves to the ROM.

## Testing

- Pure helpers (`UninstallProtection.level`, `toggleAction`) are unit-tested.
- The framework bits (activation intent, `isAdminActive`, `removeActiveAdmin`, the
  `onDisableRequested` warning, and the actual uninstall block) are **device-verified** — they
  cannot run under JVM unit tests.
