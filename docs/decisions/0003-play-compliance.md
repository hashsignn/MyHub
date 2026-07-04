# ADR 0003 — Google Play compliance: disclosure, sensitive permissions, data safety (Task 1)

**Status:** Accepted · **Date:** 2026-07-04 · **Task:** 1 (prominent disclosure & consent)

## Context

The app uses three permission surfaces Google Play treats as sensitive:

- **AccessibilityService** — reads the foreground app, scroll events, and on-screen text.
- **SYSTEM_ALERT_WINDOW** — draws the block overlay.
- **VpnService** — local DNS filter for domain blocking.

Play policy requires **prominent in-app disclosure + affirmative consent BEFORE** any sensitive
data access begins, plus store-console declarations and an accurate Data Safety form. This is
independent of the runtime permission prompts, which are not, by themselves, sufficient disclosure.

## Decision

1. **In-app prominent disclosure (implemented).** A `ConsentActivity` is shown on first launch
   **before** onboarding. It states, in plain language: what is accessed (foreground app, on-screen
   text, scrolling, DNS lookups), why, and where the data goes (all classification on-device; page
   text and browsing history are never transmitted). It requires an explicit **"I understand &
   agree"** — not a passive Continue. Consent is versioned (`ConsentGate.CURRENT_CONSENT_VERSION`)
   and persisted (`SettingsStore.consentVersion`); onboarding and permission grants are **gated** on
   it, and it is re-viewable from Settings. Bumping the version re-prompts.

2. **Honest networking claim.** The disclosure does **not** claim "nothing leaves the device." DNS
   lookups are, by nature, sent to a resolver (the device's own or a public one such as 8.8.8.8) as
   ordinary networking — the copy says so explicitly. Only the *classification/blocking* is on-device
   and the app keeps no remote log.

3. **AccessibilityService is declared as monitoring, NOT an accessibility tool.** This app assists
   the user's self-control; it does **not** aid users with disabilities, so it is **not** eligible
   for the `isAccessibilityTool="true"` classification. The Play Console **Permissions Declaration
   Form** must describe the genuine use (usage monitoring + on-screen content classification for
   self-control / parental-control-style content blocking) and link a disclosure/consent video. This
   is the highest-risk review item and may require justification or a policy exception.

4. **VpnService declaration.** Declare the local VPN as an on-device content/DNS filter (no traffic
   proxied off-device beyond ordinary DNS resolution). Provide the "no data collection" justification
   in the console.

## Data Safety form entries (planned)

- **Data collected / shared off-device:** none. No analytics, no accounts, no ads SDKs.
- **On-device only:** foreground app identity, on-screen text, scroll activity, visited domains —
  all processed locally, never uploaded.
- **DNS resolution:** disclosed as ordinary networking (domain names reach a DNS resolver), not
  collected or retained by the app.
- **Data deletion:** all state is local (DataStore + Room); uninstalling removes it.

## Known limits / follow-ups

- Accessibility monitoring apps face heightened Play scrutiny; approval is not guaranteed and may
  need the Permissions Declaration Form + demo video. Distribution outside Play (sideload / MDM /
  the Phase-5 ROM) remains the fallback.
- The disclosure copy is the source of truth; any material change **must** bump
  `CURRENT_CONSENT_VERSION` so existing users re-consent.
