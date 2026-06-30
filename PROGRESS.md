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
| M1.4 Reset logic + settings | ⬜ | Hour-boundary reset; app multi-select + minutes slider. |

## Phase 2 — URL registry + blocking
| Milestone | Status | Notes |
|---|---|---|
| M2.0 Choose mechanism | ⬜ | Domain (VPN) / path (a11y address-bar) / hybrid — decision. |
| M2.1 Local VPN skeleton | ⬜ | `VpnService`; DNS/hostname allow-deny. |
| M2.2 Registry store | ⬜ | Room table; lookup-before-classify. |
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
