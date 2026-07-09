# Releasing ContentReg (building a shippable APK)

This is the end-to-end guide for turning the source into an installable app people can download
from your website or a GitHub Release. It covers signing, building, distributing, and updating.

> **APK vs AAB:** for **sideloading / your website / GitHub Releases** you want an **APK** — users
> install it directly. An **AAB** (`bundleRelease`) is only for uploading to **Google Play**, which
> re-signs and splits it. This guide targets the APK path; the AAB path is noted at the end.

---

## 1. One-time setup: create a signing key

Every Android app must be signed. The signature ties all future updates to the same identity — a
user can only update an installed app with a build signed by the **same** key. **If you lose this
key, you can never update the app again** (users must uninstall + reinstall). Back it up.

Generate a keystore with the JDK's `keytool` (bundled with Android Studio — find `keytool` under
`…/Android Studio/jbr/bin/`, or use any installed JDK):

```bash
keytool -genkeypair -v \
  -keystore contentreg-release.jks \
  -alias contentreg \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

It will ask for a keystore password, a key password, and your name/org (any values). Keep
`-validity` large (10000 days ≈ 27 years) so the key outlives the app.

Put `contentreg-release.jks` somewhere safe (e.g. the repo root, which is gitignored for `*.jks`).

## 2. One-time setup: point the build at the key

Copy the template and fill in the real values:

```bash
cp keystore.properties.example keystore.properties
```

Edit `keystore.properties`:

```properties
storeFile=contentreg-release.jks     # relative to repo root, or an absolute path
storePassword=your-keystore-password
keyAlias=contentreg
keyPassword=your-key-password
```

`keystore.properties` and `*.jks` are **gitignored** — they never get committed. The Gradle build
reads this file automatically (see `app/build.gradle.kts`). If the file is missing, the project
still builds, but the release APK comes out **unsigned** (uninstallable) — a safety net for clean
clones and CI, not a shippable output.

## 3. Build the release APK

From the repo root:

```bash
./gradlew assembleRelease        # macOS/Linux
.\gradlew.bat assembleRelease    # Windows PowerShell
```

Output:

```
app/build/outputs/apk/release/app-release.apk
```

That file is signed (if step 2 is done), R8-shrunk, and obfuscated — ready to distribute.

> **R8 note:** the release build shrinks + obfuscates (`isMinifyEnabled = true`). If something works
> in debug but crashes only in release, it's almost always a missing R8 keep rule — add it to
> `app/proguard-rules.pro`. Always smoke-test the release APK on a device before publishing.

## 4. Version each release

Before every public build, bump the version in `app/build.gradle.kts`:

```kotlin
versionCode = 2          // integer, MUST increase every release (users can't "update" to an equal/lower code)
versionName = "0.2.0"    // human-facing string shown in the app list
```

`versionCode` is what Android compares for updates; `versionName` is cosmetic.

---

## 5. Distribute

### Option A — GitHub Releases (recommended)
1. Push a tag: `git tag v0.2.0 && git push origin v0.2.0`.
2. On GitHub → Releases → *Draft a new release* → pick the tag → attach
   `app-release.apk` → publish.
3. Share the release page URL. GitHub serves the APK over HTTPS with a stable download link.

### Option B — your tools website
Host `app-release.apk` on your site and link to it. Recommended alongside the link:
- The **SHA-256 checksum** so users can verify the download:
  `shasum -a 256 app-release.apk` (macOS/Linux) or `certutil -hashfile app-release.apk SHA256`
  (Windows). Publish the hash next to the link.
- A short "how to install" note (see below), because sideloaded apps need a manual step.

### What users do to install a sideloaded APK
1. Download the APK on the phone.
2. Tap it → Android prompts to allow installing from this source → enable it for the browser/file
   app → confirm. (Settings → Apps → Special access → *Install unknown apps* on some devices.)
3. Because this app requests **Accessibility**, Android may show an extra "restricted setting"
   step: after install, go to Settings → Accessibility → ContentReg, and if the toggle is greyed
   out, tap the ⋮ menu → *Allow restricted settings*. This is normal for sideloaded accessibility
   apps and worth documenting on your download page.

---

## 6. Updating later
- Bump `versionCode`/`versionName` (step 4), rebuild (step 3), publish (step 5).
- Sign with the **same** keystore — a build signed by a different key won't install over the
  existing app (users would have to uninstall first, losing their settings).

## 7. If you later want Google Play
- Build an **AAB** instead: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
- Bump `targetSdk` to the current Play minimum (35 as of 2025+).
- Complete the **Permissions Declaration Form** for the Accessibility + VPN permissions and the
  Data Safety section (see `docs/decisions/0003-play-compliance.md`). Play approval for an
  accessibility-using app is not guaranteed — the sideload/website path above has no such review.

---

## Quick reference

| Task | Command |
|---|---|
| Signed release APK (website / GitHub) | `./gradlew assembleRelease` |
| Play bundle | `./gradlew bundleRelease` |
| Run unit tests | `./gradlew test` |
| Clean | `./gradlew clean` |
| APK output | `app/build/outputs/apk/release/app-release.apk` |
| De-obfuscation map (keep per release!) | `app/build/outputs/mapping/release/mapping.txt` |
