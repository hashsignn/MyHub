# ADR 0005 — Block the reel surface, not a time budget (Phase 1 redesign)

**Status:** Accepted · **Date:** 2026-07-05 · **Supersedes:** the M1.2 time-budget model

## Context

The original Phase 1 limited *time* spent in feed apps: a per-hour budget that, once exhausted,
covered the app with a block overlay. In practice the goal is narrower and stronger — stop
**short-video "reel" feeds** (Instagram Reels, YouTube Shorts, Facebook Reels, TikTok) — while
leaving the rest of each app usable. "You get N minutes then everything is blocked" is both too
blunt (blocks Home/DMs/Search too) and too soft (N minutes of reels is still the harmful part).

## Decision

Replace the time budget with **per-surface reel blocking**:

- The one AccessibilityService detects whether the **reel surface** is currently on screen and drives
  the block overlay for exactly that surface. Leaving the reel tab clears the block; other tabs are
  never covered.
- Detection ([`ReelDetector`] / [`ReelApps`]): whole-app rules (TikTok — its entire main experience
  is short video) block on foreground; per-tab apps block only when a **reel-viewer view-id**
  (`clips_viewer`, `reel_recycler`, …) is present in the active window. Those ids appear on the
  Reels/Shorts surface but not on Home/Search/Profile — that is what makes "reel tab blocked, other
  tabs fine" work.
- The overlay is `FLAG_NOT_FOCUSABLE`, so the system **Back** button still reaches the app and the
  user navigates off the reel tab (never trapped). The overlay is now reason-based (REEL + the M3
  TEXT classifier) so both producers share it without fighting.
- Which surfaces are active is user-controlled (`SettingsStore.blockedReelApps`, default = all
  supported), toggled in Settings.

The entire budget stack (BudgetMath/State/Tracker/DAO/Repository, HourWindowResetter, ResetWorker +
WorkManager, ScrollMonitor, TargetApps) is removed, and Room drops `budget_state` (v3 migration).

## Known limits / brittleness

- **Signatures are app-version-specific.** The view-id markers are the app's internal names for the
  reel viewer; a redesign can rename them and silently break detection. They are centralized in
  `ReelApps` for one-file tuning, and **must be verified on-device** per app.
- **Canvas/SurfaceView apps are opaque.** Snapchat renders Spotlight on a SurfaceView and obfuscates
  its ids, so its tree is unusually sparse. A **best-effort per-tab rule** now targets the Spotlight
  viewer container (`spotlight_view`/`_recycler`/`_pager`), chosen to be viewer-specific so the worst
  case is under-blocking rather than false-blocking Chat — but the markers are **UNVERIFIED** and need
  an on-device Spotlight dump to confirm. If no Spotlight-specific id is exposed at all, detection
  simply never fires for Snapchat: the inherent limit of view-id detection against canvas apps.
- **Facebook is best-effort.** FB's accessibility tree is sparse; its markers are the most likely to
  need on-device tuning.
- **Latency.** Detection runs on a fixed ~700 ms ticker (a *playing* reel fires content-changed events
  continuously, which would starve a pure debounce), with clear-side hysteresis so a single dropped
  marker frame doesn't flap the overlay. The block appears a fraction of a second after the reel
  surface opens — acceptable, and the bounded scan avoids spinning the CPU.

## Testing

- `ReelDetector` (the decision from a package + collected view-ids) is pure and unit-tested.
- The live node-tree view-id scan and the actual overlay behavior are **device-verified**.
