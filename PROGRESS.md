# Build Progress

Tracks the actual implementation status against the roadmap in `README.md`.
Kept in sync as each milestone lands. Legend: ✅ done · 🚧 in progress · ⬜ not started.

## Phase 0 — Environment
| Milestone | Status | Notes |
|---|---|---|
| M0.0 Project scaffold builds | ✅ | Gradle project at repo root (`rootProject.name = "ContentRegApp"`), Kotlin + Views, minSdk 26, compileSdk/targetSdk 34. Wrapper = Gradle 8.9. `MainActivity` shows a status screen. |
| M0.1 Real device + USB debugging | ⬜ | Manual step on the user's machine — no code. Run the M0.0 app on a physical phone. |

## Phase 1 — Doomscroll feature
| Milestone | Status | Notes |
|---|---|---|
| M1.0 Foreground app detection | ✅ | `ForegroundService` (AccessibilityService) + `accessibility_service_config.xml` + `ForegroundAppTracker` (StateFlow) + `PermissionRouter`. MainActivity shows live foreground package + a11y on/off and routes to system settings. Done-when: open Instagram → screen shows `com.instagram.android`. |
| M1.1 Scroll detection | ✅ | `ScrollMonitor` counts `TYPE_VIEW_SCROLLED` events from `TargetApps.DEFAULT` (feed apps) only; exposes a StateFlow + `isRecentlyScrolling()` for M1.2's timer. `ForegroundService` routes scroll events; config adds `typeViewScrolled`. MainActivity shows a live scroll counter. Done-when: scrolling a feed app increments the count; scrolling elsewhere doesn't. |
| M1.2 Combined budget + persistence | ✅ | Pure `BudgetMath` + `BudgetState` (JVM-testable) → Room (`AppDatabase`/`BudgetStateEntity`/`BudgetDao`/`BudgetRepositoryRoom`) + DataStore (`SettingsStore`, budget minutes) → `TimeBudgetTracker` (1s tick in `ForegroundService`, accumulates only while target-app foreground + recently scrolling, persists on change) → wired via `App` + `ServiceLocator`. MainActivity shows used/total/left. Unit tests: `BudgetMathTest`, `TimeBudgetTrackerTest` (incl. process-death restore). Note: added `BudgetMath`/entity/DAO/`BudgetRepository` interface beyond the original ARCHITECTURE list for testability + clean persistence boundary. Done-when: Instagram→TikTok draw down one budget; kill+reopen preserves the count. |
| M1.3 Block overlay | ✅ | `OverlayManager` (adds/removes `TYPE_APPLICATION_OVERLAY` full-screen, touch-blocking) + `BlockOverlayView` (`overlay_block.xml`, live "resets in mm:ss"). Block controller in `ForegroundService` combines budget+foreground+1s ticker → shows only when **exhausted AND a target app is foreground** (Home/switch-away auto-hides; never traps the phone). `PermissionRouter` adds overlay grant/route; MainActivity shows overlay perm + Test exhaust/reset buttons (`debugSetUsedMs`). Done-when: exhausting the budget covers the feed app. |
| M1.4 Reset logic + settings | ✅ | Timestamp reset already live in `BudgetMath`; `HourWindowResetter` + `ResetWorker` (WorkManager, hourly) are the backup when the service is dead. `SettingsActivity` = minutes slider (1–60) + installed-app multi-select (`AppListAdapter`, `<queries>` for visibility), persisted to `SettingsStore` (`budgetMinutes`, `targetApps`). `ForegroundService` syncs `ScrollMonitor.targetPackages` from settings live. MainActivity has a Settings button. Done-when: budget resets each hour; settings changes take effect immediately. |

> **Phase 1 complete — first shippable prototype.** Sensing (M1.0/M1.1) → budget+persistence (M1.2) → block overlay (M1.3) → reset+settings (M1.4). Everything below is additive.

## Phase 2 — URL registry + blocking
| Milestone | Status | Notes |
|---|---|---|
| M2.0 Choose mechanism | ✅ | **Hybrid** chosen (domain via VpnService + path via a11y address-bar later). Written up with limits in `docs/decisions/0001-url-blocking-mechanism.md`. |
| M2.1 Local VPN skeleton | ✅ (skeleton) | No-root **DNS-filtering** `FilterVpnService`: routes only a virtual DNS IP through the TUN, parses each query, returns NXDOMAIN for registry domains (subdomain-aware) or forwards upstream (1.1.1.1) via a `protect()`-ed socket. `DnsPacketHandler` (IPv4/UDP/DNS parse + checksums), `TunReadWriteLoop`, consent via `PermissionRouter.prepareVpn`. MainActivity: VPN toggle + "block domain" test field + registry count. Tests: `DnsPacketHandlerTest`. **Packet framing needs on-device validation; TCP/IP, IPv6, DoH out of scope (see ADR 0001).** Done-when: a blocked domain fails to resolve while the VPN is active. |
| M2.2 Registry store | ✅ | Room v2 (`blocked_entries`, migration 1→2, enum converters) + `RegistryDao` (indexed EXISTS lookup) + `RegistryRepository` (`isHostBlocked`/`isUrlBlocked` with domain-covers-URL fallback) + pure `UrlNormalizer` (scheme-dropping so http/https unify). Built **before** M2.1 since both the VPN filter and classifier consult it. Tests: `UrlNormalizerTest`, `RegistryRepositoryTest`. Done-when: a registered entry blocks instantly without re-classifying. |
| M2.3 Classifier | ⬜ | Blocklists + heuristics first. |

## Phase 3 — On-screen text + context blocking
| Milestone | Status | Notes |
|---|---|---|
| M3.0 Read on-screen text | ⬜ | Node-tree walk; reuses Phase 1 service. |
| M3.1 Context-aware classification | ⬜ | Rules first; small model later. |
| M3.2 Trigger block on high confidence | ⬜ | Reuses M1.3 overlay. |

## Phase 4 — Retention & onboarding
| Milestone | Status | Notes |
|---|---|---|
| M4.0 Custom icon + label | ⬜ | `<activity-alias>` runtime switching. |
| M4.1 Permission onboarding flow | ⬜ | Step-through a11y → overlay → VPN consent. |
| M4.2 Stats / dashboard | ⬜ | Time saved, blocks, streaks. |

---

### How to build (Phase 0)
1. Open the repo root in **Android Studio** (it contains `settings.gradle.kts`).
2. Let it sync (downloads AGP 8.6.1, Kotlin 2.0.20, AndroidX deps).
3. Run ▶ on the `app` configuration → the status screen appears (M0.0 done).
4. Run on a physical phone over USB to close out M0.1.
