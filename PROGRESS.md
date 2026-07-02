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
| M2.1 Local VPN skeleton | ✅ (skeleton) | No-root **DNS-filtering** `FilterVpnService`: routes only a virtual DNS IP through the TUN, parses each query, returns NXDOMAIN for registry domains (subdomain-aware) or forwards upstream (1.1.1.1) via a `protect()`-ed socket. `DnsPacketHandler` (IPv4/UDP/DNS parse + checksums), `TunReadWriteLoop`, consent via `PermissionRouter.prepareVpn`. MainActivity: VPN toggle + "block domain" test field + registry count. Tests: `DnsPacketHandlerTest`. **Packet framing needs on-device validation; TCP/IP, IPv6, DoH out of scope (see ADR 0001).** Robustness: forwards through an **ordered upstream chain** (device resolver → 8.8.8.8 → 1.1.1.1) with a 2s per-server timeout, so one unreachable resolver no longer leaves the browser with no DNS (fixes emulator Chrome hang). Done-when: a blocked domain fails to resolve while the VPN is active. |
| M2.2 Registry store | ✅ | Room v2 (`blocked_entries`, migration 1→2, enum converters) + `RegistryDao` (indexed EXISTS lookup) + `RegistryRepository` (`isHostBlocked`/`isUrlBlocked` with domain-covers-URL fallback) + pure `UrlNormalizer` (scheme-dropping so http/https unify). Built **before** M2.1 since both the VPN filter and classifier consult it. Tests: `UrlNormalizerTest`, `RegistryRepositoryTest`. Done-when: a registered entry blocks instantly without re-classifying. |
| M2.3 Classifier | ✅ | Curated **102-domain explicit blocklist** (`res/raw/explicit_blocklist.txt`) seeded into the registry on first run (`BlocklistSeeder`, versioned + idempotent). `UrlClassifier` = curated list + conservative whole-label keyword heuristics (no NLP). VPN consults registry first, else classifies unseen hosts and persists blocks. MainActivity "block domain" field for manual adds. Tests: `UrlClassifierTest`. Done-when: a bad domain is classified + written to the registry; benign ones aren't. |

> **Phase 2 complete.** Mechanism decided (M2.0), registry (M2.2), DNS-filter VPN skeleton (M2.1, needs device validation), curated blocklist + classifier (M2.3). NLP-based context classification is intentionally deferred to **Phase 3, which we'll do last (after Phase 4)**.

## Phase 3 — On-screen text + context blocking
| Milestone | Status | Notes |
|---|---|---|
| M3.0 Read on-screen text | ✅ | `ScreenTextReader` walks `getRootInActiveWindow()` into a pure `NodeInfo` tree (max 200 nodes, depth 15, 5 000 chars). Extracts URL from known browser address-bar view IDs (Chrome, Firefox, Samsung, Edge, Brave, Opera, Kiwi, Vivaldi) with heuristic fallback. `ScreenSnapshot` (packageName, url, pageText) carried by `ScreenTextPipeline` (SharedFlow/DROP_OLDEST). `ForegroundService` debounces text reads 800 ms on `TYPE_WINDOW_STATE_CHANGED`. Reuses Phase 1 AccessibilityService — no second service. Unit tests: 10. Device verified: snapshots logged in Chrome. |
| M3.1 Context-aware classification | ✅ (robustness-tuned) | `KeywordContextRules`: tiers **EXPLICIT (always blocks — context ignored), MODERATE 0.65, CONTEXT 0.45**, prefix-match, ±12-token window. Safe-context discount (−0.25/hit, cap −0.60) applies **only with ≥2 safe words** so one stray word can't wave through hardcore content. **Cross-keyword accumulation** (+0.10/distinct suspicious term, cap +0.30) catches pages littered with moderate terms. Amplifiers +0.08/hit cap +0.20. Threshold 0.80. `ContextClassifier` + `ModelClassifier` stub seam. Bias: **block aggressively; false-block on a safe page is acceptable, hardcore slipping through is not.** Tradeoff: a recovery/medical/news page that literally contains Tier-1 words now blocks. Unit tests updated for the new behavior. |
| M3.2 Trigger block on high confidence | ✅ | `TextBlockDecider` subscribes to pipeline: registry fast-path (URL already blocked → overlay immediately); classifier slow-path (conf ≥ 0.80 → overlay + persist). URL-level persist when path present (reddit.com/r/nsfw blocked, reddit.com stays open); domain-level for bare hosts. Reuses `OverlayManager` from M1.3 — no second overlay. Source = `HEURISTIC`. |

> **Phase 3 complete.** All 80 unit tests pass. Pipeline: M3.0 reads screen → M3.1 classifies with context awareness → M3.2 reuses M1.3 overlay and writes URL-level registry entries. `ModelClassifier` stub seam ready for LiteRT/ONNX when needed.

## Bug fixes (post-Phase 4 device test)
| Bug | Severity | Fix commit | Notes |
|---|---|---|---|
| #1/#3 TYPE_WINDOW_CONTENT_CHANGED + cookie dialogs | HIGH | 27a8fbe | Added content-changed event type; 1.5s debounce + 3s throttle; skip snapshots < 200 chars |
| #2 Duplicate coroutines on AccessibilityService re-bind | MEDIUM | ec64786 | serviceScope cancel+recreate at top of onServiceConnected(); textReadJob nulled |
| #4 EXPLICIT keyword overridden by single safe word | HIGH | cb6f13d | Already fixed in upstream commit; MIN_SAFE_WORDS_TO_DISCOUNT=2, EXPLICIT bypasses safeDiscount() |
| #5 Settings app list ~4s spinner | LOW | 0ea993e | AppRow.icon nullable; icons loaded lazily via view.post() with per-adapter cache + tag guard |
| #6 VPN dies silently, no recovery | MEDIUM | 04411ed | startForeground(specialUse), onRevoke(), isRunning→StateFlow, MainActivity observes live state |
| url=null in Chrome M3.0 snapshots | — | 46adda1 | url_bar at node 1218 > MAX_NODES=200; findUrlInLiveTree() does unbounded DFS before toNodeInfo(), confirmed url=domain on device |

## Phase 4 — Retention & onboarding
| Milestone | Status | Notes |
|---|---|---|
| M4.0 Custom icon + label | ✅ | 3 `<activity-alias>` launchers (Default / Calculator / Notes, each own icon+label); `IconAliasController` enables exactly one at runtime. MainActivity's own launcher filter removed. Picker in Settings; choice persisted (`SettingsStore.appDisguise`). |
| M4.1 Permission onboarding flow | ✅ | `OnboardingActivity` + `OnboardingStep` (a11y → overlay → VPN, required vs optional). Live status refresh on resume; Finish enabled when required grants present; auto-shown once on first run (`onboardingComplete`). |
| M4.2 Stats / dashboard | ✅ | `StatsRepository` (own DataStore): feed blocks triggered (incremented on overlay-show transition) + daily streak (`recordActiveToday`). `DashboardActivity` shows streak, blocks, feed time used this hour, registry size. |

> **Phase 4 complete.** Only Phase 3 (on-screen text + NLP) remains — intentionally last, per plan.

---

### How to build (Phase 0)
1. Open the repo root in **Android Studio** (it contains `settings.gradle.kts`).
2. Let it sync (downloads AGP 8.6.1, Kotlin 2.0.20, AndroidX deps).
3. Run ▶ on the `app` configuration → the status screen appears (M0.0 done).
4. Run on a physical phone over USB to close out M0.1.
