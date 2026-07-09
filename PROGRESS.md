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

> ⚠️ **Phase 1 redesigned — reel-blocking replaces the time budget (ADR 0005).** The per-hour time
> budget was removed in favor of **per-surface reel blocking**: the accessibility service detects the
> Reels/Shorts surface (Instagram, YouTube, Facebook; TikTok whole-app) and blocks *only that tab*,
> leaving the app's other tabs usable. `ReelDetector`/`ReelApps` (pure, tested) + a bounded live
> view-id scan drive the reused overlay (now reason-based: REEL + M3 TEXT). Removed: BudgetMath/
> State/Tracker/DAO/Repository, HourWindowResetter, ResetWorker (WorkManager), ScrollMonitor,
> TargetApps, AppListAdapter; Room dropped `budget_state` (v3). Settings gained per-app reel toggles.
> **Detection signatures are app-version-specific and need on-device tuning.** Snapchat Spotlight now
> has a best-effort per-tab rule + settings toggle (UNVERIFIED markers — canvas/SurfaceView rendering
> may expose no usable id; see ADR 0005). M1.0/M1.3 sensing + overlay infra are reused unchanged.

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

## Hardening & compliance (post device-test)

### Privacy, robustness & coverage (Task 0)
| Item | Status | Notes |
|---|---|---|
| 0a Release-log privacy | ✅ | `PrivacyLog.detail{}` gates browsing URLs / blocked domains / matched keywords behind `BuildConfig.DEBUG` (lambda dead-code-eliminated in release); non-sensitive telemetry (lengths, counts, confidence) stays. `buildConfig=true`. |
| 0b Cap live-tree URL scan | ✅ | `ScreenTextReader.URL_SCAN_MAX_NODES=3000`; the raw-tree scan is routed through a testable `ScanNode` abstraction, ending the uncapped main-thread DFS on non-browser apps. Text walk (200 nodes) unchanged. |
| 0c Coverage | ✅ | New tests: `scanForUrl`/`urlBarText` (finds a URL past the 200-node text cap; respects the cap; recycling) and `TextBlockDecider.decide` (registry-hit precedence; URL- vs domain-level persist). |
| 0d Notification icon | ✅ | Monochrome `ic_stat_shield` white-on-transparent small icon (replaces the full-color launcher blob). |
| 0e VPN loop hardening | ✅ | Per-packet parse/build isolated in `handlePacket()` + try/catch so one malformed packet can't tear down the filter loop. |

### Play compliance — prominent disclosure & consent (Task 1)
| Item | Status | Notes |
|---|---|---|
| 1a/1c Consent screen + gating | ✅ | `ConsentActivity` on first launch **before** onboarding; explicit "I understand & agree" (not a passive Continue); onboarding + permission grants gated on `ConsentGate.needsConsent`. Decline/Back closes the app. |
| 1b Disclosure copy | ✅ | Truthful WHAT / WHY / WHERE; honest that DNS lookups reach a resolver as ordinary networking (no false "nothing leaves the device" claim). |
| 1c Persistence + versioning | ✅ | `SettingsStore.consentVersion`; bump `ConsentGate.CURRENT_CONSENT_VERSION` to re-prompt after a material copy change. |
| 1d Re-viewable | ✅ | "View data & privacy disclosure" in Settings (read-only review mode). |
| 1e A11y description | ✅ | `accessibility_service_description` rewritten to accurately describe monitoring + on-device processing. |
| 1f ADR + tests | ✅ | `docs/decisions/0003-play-compliance.md` (disclosure, Permissions Declaration Form — not `isAccessibilityTool`, VpnService, Data Safety). `ConsentGateTest`. |

### Anti-uninstall friction — Device Admin (Task 2)
| Item | Status | Notes |
|---|---|---|
| 2a Admin receiver + flow | ✅ | `ContentRegDeviceAdminReceiver` (no policies; `onDisableRequested` warns) + `AdminController` (activate intent / `isActive` / in-app `deactivate`). While active, Android blocks direct uninstall. |
| 2b Optional onboarding step + UI state | ✅ | Optional `OnboardingStep.ADMIN` (required=false, clearly labeled); Settings shows On/Off + a single activate/deactivate toggle. |
| 2c Stacks with disguise; re-activation | ✅ | Disguised launch still reaches MainActivity; in-app deactivate never traps the user; re-activation works. |
| 2d Rejected a11y back-out trick | ✅ | Documented off-by-default only (brittle + Play-rejection risk); not implemented. |
| 2e ADR | ✅ | `docs/decisions/0002-anti-uninstall.md` — friction ladder (disguise → device admin → Device Owner/ROM) + Play implications. |
| 2f Tests | ✅ | `UninstallProtectionTest` (pure `level`/`toggleAction`); framework bits device-verified. |

> **Reality:** on a normal non-rooted device nothing is truly undeletable (Safe Mode / adb / factory reset win). This is friction, not a wall — a true lock needs Device Owner or the Phase-5 ROM.

### Custom icon — pinned photo shortcut (Task 3)
| Item | Status | Notes |
|---|---|---|
| 3a Photo pick | ✅ | Modern Photo Picker (`PickVisualMedia`) — no storage permission. |
| 3b Pinned shortcut | ✅ | `PhotoShortcutController.requestPin` via `ShortcutManagerCompat` + `IconCompat.createWithBitmap`, launching MainActivity; guarded by `isRequestPinShortcutSupported`, graceful when unsupported. |
| 3c Crop/scale/persist | ✅ | `IconImageStore` center-crops to square, scales to 256px, persists PNG in internal storage for re-pin. |
| 3d Stacks with disguise + admin | ✅ | Shortcut opens MainActivity (same target as the aliases); independent of M4.0 disguise and Task-2 admin. |
| 3e ADR | ✅ | `docs/decisions/0004-custom-icon.md` — runtime app-icon swap is impossible on stock Android; pinned-shortcut-with-photo is the supported approximation; true custom icon is Phase-5 (ROM). |
| 3f Tests | ✅ | `IconImageStoreTest` (pure crop geometry + path); bitmap/pin bits device-verified. |

> **Reality:** a normal app can't repoint its own launcher icon to a gallery photo (icons are static resources; `activity-alias` only switches pre-built ones). The pinned shortcut adds a second, photo-iconed launcher entry — the supported approximation.

---

## Phase 4.5 — Digital Detox, logging & shipping (post-scan)

Additive work after the core features. **⚠️ Written but not yet compiled/run on device — verify in
Android Studio before trusting the ✅.**

### Digital Detox (red panic button)
| Item | Status | Notes |
|---|---|---|
| State + controller | ✅ code | `detox/DetoxState` (active/remaining derived from an absolute end-time → survives reboot) + `DetoxController` over `SettingsStore` (signature, end-time, allow-list). Wired in `ServiceLocator`. |
| Arm flow | ✅ code | `DetoxSetupActivity`: duration (30m–8h) + allow-list of launchable apps (`InstalledApps`) + signature confirm (creates first time, matches after). Explicit "only a charity donation unlocks early" dialog gates start. `DetoxAlarm` = vibrate + tone. |
| Enforcement | ✅ code | On the existing 700ms reel tick, `ForegroundService.evaluateDetox()` covers any app not on the allow-list (+ this app, systemui, launcher) with a full-screen `DetoxOverlayController` window (1s countdown, allowed-app launch buttons, Home, unlock-early). Timer expiry auto-ends + clears once. |
| Charity early-unlock | ✅ code | `DetoxUnlockActivity`: charity links (GiveWell/Red Cross/UNICEF) + signature-confirm exit. Honour system, stated in-app. |
| Home UI | ✅ code | Red panic button + state banner (start ⇄ unlock) in `activity_main`. |
| Settings section | ✅ code | `SettingsActivity` detox section: shows active/remaining + whether a signature is set, and lets you set/change the signature — **locked while a detox is active** (re-checked at commit time) so it can't be used to skip the charity unlock. |
| Tests | ✅ | `DetoxStateTest`, `DetoxFormatTest` (pure). Controller/overlay device-verified (coupled to DataStore/WindowManager). |
| **Known limit** | — | Bypassable by disabling the a11y service / force-stop (can't hard-lock a normal app — accepted). Overlay app-launch relies on the `SYSTEM_ALERT_WINDOW` background-launch exemption. |

### Crash logs (local, user-shareable)
| Item | Status | Notes |
|---|---|---|
| Capture | ✅ code | `CrashReporter` installs an uncaught-exception handler in `App.onCreate` → appends stack traces to a private, 256KB-capped file, then defers to the platform handler. Nothing auto-sent. |
| View / share / clear | ✅ code | `LogsActivity` + `FileProvider` (private `logs/` only). Reachable from home → More. R8 keeps line numbers; de-obfuscate a shared log via `mapping.txt`. |

### Private-DNS warning
| Item | Status | Notes |
|---|---|---|
| Detect + warn | ✅ code | `PrivateDns.isActive()` reads `private_dns_mode`; home shows a warning when on (DoT bypasses the VPN filter; reel/text blocking unaffected). Checked on resume. |

### Consent & release hardening
| Item | Status | Notes |
|---|---|---|
| Consent v3 | ✅ | `ConsentGate.CURRENT_CONSENT_VERSION = 3` (re-prompts). Adds reels-networking (no screen data leaves device), local crash-log, and Digital-Detox charity-unlock disclosures. |
| R8 for release | ✅ code | `isMinifyEnabled` + `isShrinkResources` on; `proguard-rules.pro` keeps enum names (persisted `.name()`) + line numbers. **Smoke-test the release build.** |
| Signing + distribution | ✅ | `signingConfig` via gitignored `keystore.properties` (+ `.example`); `RELEASE.md` documents keystore → signed APK → GitHub Releases / website sideload → updates → AAB/Play path. |

> **Open items after this phase:** compile/run verification of all the above; on-device tuning of
> reel markers (esp. Snapchat) + a real VPN validation pass; `targetSdk 34→35` if Play is a goal;
> an on-device ML model in the `ModelClassifier` seam. Phase 5 (AOSP/ROM) remains deliberately
> deferred.

---

### How to build (Phase 0)
1. Open the repo root in **Android Studio** (it contains `settings.gradle.kts`).
2. Let it sync (downloads AGP 8.6.1, Kotlin 2.0.20, AndroidX deps).
3. Run ▶ on the `app` configuration → the status screen appears (M0.0 done).
4. Run on a physical phone over USB to close out M0.1.
