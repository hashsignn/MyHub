# Content-Regulation App — Technical Build Roadmap (Features Phase)
 
**Scope of this document:** the *features phase only* — a normal Android app written in Kotlin, installed and granted strong permissions by the user. This is **not** the custom-ROM phase. The ROM port is a separate, later effort that reuses the validated code from this phase. Nothing here covers product vision, market, or positioning — this is the technical build path and its milestones.
 
**Three features being built (in this order):**
1. Doomscroll detection + combined cross-app time budget + block overlay
2. URL registry + blocking
3. On-screen text reading + context-aware blocking via overlay
**Core principle of the order:** each milestone produces something that *runs and is testable on a real phone* before the next one starts. Features 2 and 3 both depend on infrastructure built in Feature 1. Do not start them in parallel.
 
---
 
## Phase 0 — Environment (the admission cost)
 
This phase produces no product. Its only job is a working build pipeline. It is the single biggest drop-off point, so treat "a blank app runs on a real phone" as a real milestone.
 
### M0.0 — Android Studio installed, emulator runs "Hello World"
- **Goal:** prove the toolchain works end to end.
- **Steps:** install Android Studio on Windows directly (not WSL — the emulator conflicts with WSL). New Project → *Empty Views Activity* (not Compose, for now) → language **Kotlin** → minSdk ~26. Hit Run (▶), see a blank/Hello-World app launch in the emulator.
- **Done when:** the app launches in the emulator with no errors.
- **Difficulty:** Easy (but slow — large downloads).
### M0.1 — Real device + USB debugging
- **Goal:** run your app on physical hardware. Accessibility and overlay behavior cannot be fully tested on the emulator.
- **Steps:** get *any* cheap secondhand Android phone (model irrelevant at this stage — do **not** buy the Pixel yet; that's for the ROM phase). On the phone: Settings → About → tap Build Number 7× to unlock Developer Options → enable USB Debugging. Connect, accept the debug prompt, select the device in Android Studio, Run.
- **Done when:** your app launches on the physical phone.
- **Difficulty:** Easy.
---
 
## Phase 1 — Doomscroll feature (the foundation)
 
This is the backbone. The Accessibility Service built here is the same sensing layer that Features 2 and 3 read from. Build it well.
 
### M1.0 — Foreground app detection
- **Goal:** the app knows which app is currently in the foreground.
- **Approach:** create an **AccessibilityService**. Declare it in the manifest with a config XML, register a settings screen that sends the user to grant the Accessibility permission. Listen for `TYPE_WINDOW_STATE_CHANGED` events; read the package name of the active window.
- **Key APIs:** `AccessibilityService`, `AccessibilityServiceInfo`, `AccessibilityEvent.getPackageName()`, `getRootInActiveWindow()`.
- **Done when:** open Instagram → your app logs/shows "com.instagram.android" (or a debug toast).
- **Difficulty:** Moderate (the permission flow and manifest config trip people up more than the code does).
### M1.1 — Scroll detection
- **Goal:** detect that the user is scrolling inside a target app.
- **Approach:** listen for `TYPE_VIEW_SCROLLED` events while a target app is foreground. For v1, do **not** try to distinguish "reel vs article" — that heuristic is fuzzy and unreliable (people scroll non-feed content inside the same apps). Instead: count scroll activity *while a known feed app is foreground*, full stop.
- **Key APIs:** `AccessibilityEvent.TYPE_VIEW_SCROLLED`.
- **Done when:** scrolling in a target app produces detectable scroll events in your logs; scrolling elsewhere does not count.
- **Difficulty:** Moderate.
### M1.2 — Combined time budget + persistence
- **Goal:** accumulate scroll-time *across all target apps* against one shared budget (e.g. 5 min/hour), surviving app restarts.
- **Approach:** maintain a timer that runs while (target app foreground AND recent scroll activity). Persist accumulated time + the current hour window so a restart or screen-off doesn't reset it. Use **DataStore** or **Room (SQLite)** for persistence.
- **Key APIs:** `DataStore`/`Room`, a foreground `Service` or coroutine timer, system clock for hour windows.
- **Done when:** scrolling in Instagram then TikTok draws down the *same* budget; killing and reopening your app preserves the count.
- **Difficulty:** Moderate–Hard (state that survives process death is where bugs hide).
### M1.3 — Block overlay
- **Goal:** when the budget hits zero, draw a full-screen block over whatever app is open.
- **Approach:** request the **overlay permission** (`SYSTEM_ALERT_WINDOW` / `Settings.canDrawOverlays`). When budget is exhausted, add a `TYPE_APPLICATION_OVERLAY` view via `WindowManager`. The overlay should be dismissible only per your rules (e.g. shows time remaining until reset).
- **Key APIs:** `Settings.canDrawOverlays`, `WindowManager`, `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
- **Done when:** exhausting the budget covers the feed app with your screen.
- **Difficulty:** Moderate.
### M1.4 — Reset logic + settings
- **Goal:** budget refreshes each hour; user can configure which apps count and the budget length.
- **Approach:** track timestamps and reset the budget at the hour boundary (lightweight: compute from timestamps on each event rather than relying on a background alarm; optionally `WorkManager`/`AlarmManager` as backup). Add a simple settings screen (app multi-select, minutes slider).
- **Key APIs:** `WorkManager`/`AlarmManager`, `PackageManager` (to list installed apps), a settings UI.
- **Done when:** budget visibly resets each hour; settings changes take effect.
- **Difficulty:** Moderate.
> **End of Phase 1 = your first shippable prototype.** Everything below is additive.
 
---
 
## Phase 2 — URL registry + blocking
 
Read this honest constraint **before** designing this feature, because it changes what's possible.
 
### ⚠️ Technical reality: "exact URL, not whole site" is hard on HTTPS
At the **network layer** (a local VPN intercepting traffic), for an HTTPS site you can see the **hostname** (via the TLS SNI field) but **not the path or query** — those are encrypted. So a network-level filter can block `example.com` but *cannot* see `example.com/specific-page`. Per-*path* blocking is only possible via:
- **(a)** reading the full URL from the browser's address bar through the **Accessibility Service** (works only in browsers, only while visible, and is brittle across browser UIs), or
- **(b)** a **browser extension** inside one specific browser you ship or support.
Network-level (VPN/DNS) gives you robust **domain** blocking with no browser dependency. Decide explicitly which you want; you likely need a hybrid (domain-level via VPN + path-level via address-bar read where available). Also note emerging **ECH/Encrypted SNI** will eventually hide even the hostname from network-level filtering.
 
### M2.0 — Choose the mechanism
- **Goal:** commit to domain-level (VpnService), path-level (Accessibility address-bar read), or hybrid.
- **Done when:** the mechanism is decided and written down with its known limits.
- **Difficulty:** Decision, not code.
### M2.1 — Local VPN skeleton (domain-level path)
- **Goal:** a local on-device VPN that can inspect outbound DNS/connections. No root, no cloud — traffic never leaves the phone.
- **Approach:** implement `VpnService`; route DNS through your handler; allow/deny by hostname. Study how NetGuard/Blokada-style local filters are structured (open-source references exist).
- **Key APIs:** `VpnService`, `VpnService.Builder`, packet/DNS handling.
- **Done when:** a test domain in your block list fails to resolve/connect while the VPN is active.
- **Difficulty:** Hard (packet handling is the steepest code in the whole features phase).
### M2.2 — The registry (local store)
- **Goal:** a hidden local store of blocked entries (domains and/or exact URLs), checked *first* on each request so known-bad entries are never re-evaluated.
- **Approach:** **Room** (SQLite) table keyed by normalized URL/domain. Lookup-before-classify path.
- **Done when:** a registered entry is blocked instantly without re-running classification.
- **Difficulty:** Moderate.
### M2.3 — The classifier (what gets added to the registry)
- **Goal:** decide whether a *new, unseen* URL/page should be added to the registry.
- **Approach:** start with the simplest thing that works (curated blocklists + keyword/domain heuristics) before any model. Context-aware text classification (Feature 3) can feed this later. Keep it local.
- **Done when:** a new bad URL gets classified and written to the registry; a benign one does not.
- **Difficulty:** Moderate now (heuristics) → Hard later (on-device model).
---
 
## Phase 3 — On-screen text reading + context-aware blocking
 
### ⚠️ Technical reality: real-time on-device NLP has a cost
Reading and classifying on-screen text *continuously* is the same family of problem as live image scanning — lighter, but still a battery/heat and latency concern if run on every screen update. Budget it: classify on meaningful changes, not every frame; keep the model small.
 
### M3.0 — Read on-screen text
- **Goal:** extract the text currently visible in any app.
- **Approach:** walk the Accessibility node tree (`getRootInActiveWindow()` → recurse children → collect `text`/`contentDescription`). This reuses the Phase 1 service.
- **Key APIs:** `AccessibilityNodeInfo`, tree traversal.
- **Done when:** your app can dump the visible text of an arbitrary screen.
- **Difficulty:** Moderate (some apps render text in ways the tree doesn't expose — e.g. as images or in WebViews; flag these gaps).
### M3.1 — Context-aware classification
- **Goal:** the same word passes in medical/rehab/crime-reporting context but blocks otherwise.
- **Approach:** a **local** text classifier. Start with keyword + surrounding-context rules; graduate to a small quantized model run via **TensorFlow Lite** or **ONNX Runtime Mobile** if rules prove insufficient. Output a confidence score.
- **Key APIs:** TensorFlow Lite / ONNX Runtime Mobile (only if/when needed).
- **Done when:** the same flagged term yields block in one context and pass in another, on-device.
- **Difficulty:** Hard (genuine ML work; do the rules-first version before committing to a model).
### M3.2 — Trigger block on high confidence
- **Goal:** high-confidence inappropriate text → block overlay (reuses M1.3).
- **Done when:** a high-confidence screen is covered by the overlay; low-confidence is left alone.
- **Difficulty:** Easy (the overlay already exists).
---
 
## Phase 4 — Retention & onboarding polish
 
### M4.0 — Custom icon + label
- **Goal:** let the user replace the app icon/name (e.g. a photo of someone they love) so they're reluctant to remove it.
- **Approach:** **activity-alias** entries in the manifest, enabled/disabled at runtime, each with its own icon/label; or a launcher-icon picker. No special permission needed.
- **Difficulty:** Easy.
### M4.1 — Permission onboarding flow
- **Goal:** smoothly walk the user through granting Accessibility + Overlay (+ VPN consent) — the app is useless until these are granted.
- **Difficulty:** Moderate (mostly UX; the permission screens are system-controlled).
### M4.2 — Stats / dashboard
- **Goal:** show scroll-time saved, blocks triggered, streaks — the feedback loop that keeps the user engaged.
- **Difficulty:** Easy–Moderate.
---
 
## Phase 5 — Hardening (bridge to the ROM phase)
 
Not built now. Noted so the order is clear: once Features 1–3 are validated as a normal app, the same logic is ported into a forked AOSP build, where the components that a normal app must *request* (overlay, accessibility, VPN) instead ship as privileged system services, and the live-image-scanning layer (deliberately excluded from the features phase) becomes feasible by hooking the system compositor. Porting *validated, working* code into that hard environment is far safer than debugging unproven features and system-security policy at the same time.
 
---
 
## Difficulty map (at a glance)
 
| Phase | Hardest milestone | Why |
|---|---|---|
| 0 | M0.0 | Toolchain setup, not code — but the top drop-off point |
| 1 | M1.2 | State that survives process death |
| 2 | M2.1 | Local VPN packet/DNS handling |
| 3 | M3.1 | Genuine on-device ML |
 
## Immediate next action
Complete **M0.0** (Android Studio installed, blank app runs in the emulator). Until that runs, no feature code can be tested. Once it runs, M1.0 (foreground-app detector) is the first real feature code.
 
