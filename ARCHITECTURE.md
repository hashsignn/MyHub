# Content-Regulation App — File-Tree Architecture

Maps every file required to build the **features-phase** Android app (Kotlin) onto the
milestones in the roadmap (`README.md`). Each file is tagged with the milestone(s) it serves.

**Stack:** Kotlin · Empty Views Activity (not Compose) · minSdk 26 · Room + DataStore ·
AccessibilityService · VpnService · WindowManager overlays · LiteRT/ONNX (later).

**Package root:** `com.contentreg.app`

> **Note — Phase 1 redesigned (ADR 0005).** The time-budget files below (`budget/`, `ResetWorker`,
> `ScrollMonitor`, `TargetApps`, `AppListAdapter`) were **removed**; Phase 1 now blocks the reel
> surface instead of limiting time. New: `feature1_doomscroll/reels/` (`ReelDetector`, `ReelRules`).
> The overlay is reason-based (reel + text). Treat budget entries in the tree below as historical.

---

## Top-level project

```
ContentRegApp/
├── settings.gradle.kts                  # M0.0  module list, repos
├── build.gradle.kts                     # M0.0  root build config
├── gradle.properties                    # M0.0  JVM/AndroidX flags
├── gradle/
│   └── libs.versions.toml               # M0.0  central dependency versions
├── README.md                            #       roadmap (existing)
├── ARCHITECTURE.md                      #       this file
└── app/
    ├── build.gradle.kts                 # M0.0  app deps: room, datastore, workmanager, litert
    ├── proguard-rules.pro               # M4.x  keep rules for Room/TFLite models
    └── src/
        ├── main/        # ── app code (below)
        ├── test/        # ── JVM unit tests (budget math, classifier rules)
        └── androidTest/ # ── instrumented tests (Room DAO, service smoke)
```

---

## `app/src/main/`

```
src/main/
├── AndroidManifest.xml          # M0.0/M1.0/M1.3/M2.1/M4.0  declares activities,
│                                #   AccessibilityService, VpnService, overlay +
│                                #   accessibility perms, <activity-alias> icons
│
├── java/com/contentreg/app/
│   │
│   ├── App.kt                   # M1.2  Application class; DI/singletons init
│   ├── MainActivity.kt          # M0.0  entry screen → hosts onboarding/dashboard
│   │
│   ├── core/                    # ── shared infra (built in Phase 1, reused by 2 & 3)
│   │   ├── sensing/
│   │   │   ├── ForegroundService.kt      # M1.0  the ONE AccessibilityService;
│   │   │   │                             #   WINDOW_STATE_CHANGED → current package;
│   │   │   │                             #   VIEW_SCROLLED → scroll events (M1.1);
│   │   │   │                             #   node-tree walk → on-screen text (M3.0)
│   │   │   ├── ForegroundAppTracker.kt   # M1.0  resolves active package name
│   │   │   ├── ScrollMonitor.kt          # M1.1  counts scroll activity per target app
│   │   │   └── ScreenTextReader.kt       # M3.0  getRootInActiveWindow() recursion
│   │   │
│   │   ├── overlay/
│   │   │   ├── OverlayManager.kt         # M1.3  add/remove TYPE_APPLICATION_OVERLAY
│   │   │   │                             #   via WindowManager (reused by M3.2)
│   │   │   └── BlockOverlayView.kt       # M1.3  full-screen block UI + reset countdown
│   │   │
│   │   ├── permissions/
│   │   │   ├── PermissionChecker.kt      # M4.1  canDrawOverlays, a11y-enabled, vpn-prepared
│   │   │   └── PermissionRouter.kt       # M1.0/M4.1  intents to system grant screens
│   │   │
│   │   └── data/
│   │       ├── AppDatabase.kt            # M1.2/M2.2  Room database holder
│   │       ├── prefs/
│   │       │   └── SettingsStore.kt      # M1.2/M1.4  DataStore: budget len, target apps
│   │       └── di/
│   │           └── ServiceLocator.kt     # M1.2  wires stores/daos to service & UI
│   │
│   ├── feature1_doomscroll/    # ── Phase 1
│   │   ├── budget/
│   │   │   ├── TimeBudgetTracker.kt      # M1.2  accumulate cross-app scroll-time
│   │   │   ├── BudgetState.kt            # M1.2  data class: usedMs, hourWindow start
│   │   │   ├── BudgetRepositoryRoom.kt   # M1.2  persists state (survives process death)
│   │   │   └── HourWindowResetter.kt     # M1.4  timestamp-based hour-boundary reset
│   │   ├── ResetWorker.kt                # M1.4  WorkManager/AlarmManager backup reset
│   │   └── ui/
│   │       └── SettingsActivity.kt       # M1.4  app multi-select + minutes slider
│   │
│   ├── feature2_url/           # ── Phase 2
│   │   ├── FilterVpnService.kt           # M2.1  VpnService; DNS/hostname allow-deny
│   │   ├── vpn/
│   │   │   ├── DnsPacketHandler.kt       # M2.1  parse DNS, decide block (SNI/hostname)
│   │   │   └── TunReadWriteLoop.kt       # M2.1  TUN read/write (JNI/tun2socks bridge)
│   │   ├── registry/
│   │   │   ├── BlockedEntry.kt           # M2.2  Room @Entity (normalized domain/url)
│   │   │   ├── RegistryDao.kt            # M2.2  lookup-before-classify queries
│   │   │   └── RegistryRepository.kt     # M2.2  instant block on known-bad
│   │   └── classifier/
│   │       └── UrlClassifier.kt          # M2.3  blocklists + keyword/domain heuristics
│   │
│   ├── feature3_text/          # ── Phase 3
│   │   ├── classifier/
│   │   │   ├── ContextClassifier.kt      # M3.1  rules-first; confidence score
│   │   │   ├── KeywordContextRules.kt    # M3.1  word + surrounding-context logic
│   │   │   └── ModelClassifier.kt        # M3.1  LiteRT/ONNX inference (only if needed)
│   │   └── BlockDecider.kt               # M3.2  high-confidence → OverlayManager
│   │
│   └── feature4_retention/     # ── Phase 4
│       ├── IconAliasController.kt        # M4.0  enable/disable <activity-alias> at runtime
│       ├── onboarding/
│       │   ├── OnboardingActivity.kt     # M4.1  step-through perm-granting flow
│       │   └── OnboardingStep.kt         # M4.1  a11y → overlay → vpn consent steps
│       └── stats/
│           ├── DashboardActivity.kt      # M4.2  time saved, blocks, streaks
│           └── StatsRepository.kt        # M4.2  aggregates from budget/registry data
│
└── res/
    ├── xml/
    │   └── accessibility_service_config.xml  # M1.0  service capabilities/event types
    ├── layout/
    │   ├── activity_main.xml             # M0.0
    │   ├── overlay_block.xml             # M1.3  block screen layout
    │   ├── activity_settings.xml         # M1.4
    │   ├── activity_onboarding.xml       # M4.1
    │   └── activity_dashboard.xml        # M4.2
    ├── values/
    │   ├── strings.xml                   # M0.0
    │   ├── colors.xml                    # M0.0
    │   └── themes.xml                    # M0.0
    └── mipmap-*/                         # M0.0/M4.0  default + alias launcher icons
```

---

## Build order ↔ files (critical path)

| Phase | Milestone | First files to create |
|---|---|---|
| 0 | M0.0 | `settings.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`, `activity_main.xml` |
| 0 | M0.1 | *(no new files — physical device run)* |
| 1 | M1.0 | `ForegroundService.kt`, `accessibility_service_config.xml`, `PermissionRouter.kt` |
| 1 | M1.1 | `ScrollMonitor.kt` |
| 1 | M1.2 | `TimeBudgetTracker.kt`, `BudgetState.kt`, `AppDatabase.kt`, `SettingsStore.kt` ← **hardest (process-death state)** |
| 1 | M1.3 | `OverlayManager.kt`, `BlockOverlayView.kt`, `overlay_block.xml` |
| 1 | M1.4 | `HourWindowResetter.kt`, `ResetWorker.kt`, `SettingsActivity.kt` |
| 2 | M2.0 | *(decision — record in README)* |
| 2 | M2.1 | `FilterVpnService.kt`, `DnsPacketHandler.kt`, `TunReadWriteLoop.kt` ← **hardest (packet/DNS)** |
| 2 | M2.2 | `BlockedEntry.kt`, `RegistryDao.kt`, `RegistryRepository.kt` |
| 2 | M2.3 | `UrlClassifier.kt` |
| 3 | M3.0 | `ScreenTextReader.kt` *(extends the Phase-1 service)* |
| 3 | M3.1 | `ContextClassifier.kt`, `KeywordContextRules.kt`, `ModelClassifier.kt` ← **hardest (on-device ML)** |
| 3 | M3.2 | `BlockDecider.kt` *(reuses `OverlayManager`)* |
| 4 | M4.0 | `IconAliasController.kt`, manifest `<activity-alias>` |
| 4 | M4.1 | `OnboardingActivity.kt`, `OnboardingStep.kt` |
| 4 | M4.2 | `DashboardActivity.kt`, `StatsRepository.kt` |

---

## Reuse map (why this layout)

- **`core/sensing/ForegroundService.kt`** is the single AccessibilityService. Features 1, 2(path-read),
  and 3 all read from it — built once in M1.0, never duplicated.
- **`core/overlay/`** is built in M1.3 and reused verbatim by M3.2 (text block) — no second overlay stack.
- **`core/data/`** (Room + DataStore) is shared by the budget (M1.2), the URL registry (M2.2),
  and stats (M4.2) — one database, multiple DAOs.
- **Phase 5 (ROM port)** adds no files here; it relocates `ForegroundService`, `OverlayManager`,
  and `FilterVpnService` from *requested permissions* to *privileged system services* in a forked AOSP tree.
