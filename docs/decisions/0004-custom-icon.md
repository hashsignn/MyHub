# ADR 0004 — Custom app icon: pinned photo shortcut, not a runtime icon swap (Task 3)

**Status:** Accepted · **Date:** 2026-07-04 · **Task:** 3 · **Relates to:** M4.0, ADR 0002

## Context

The retention idea (M4.0) is to let the user set a meaningful image — e.g. a photo of someone they
love — as the app's icon, so they're reluctant to delete it.

**Technical reality:** on stock Android a normal app **cannot** set an arbitrary runtime image as its
own launcher icon. Launcher icons are static resources baked into the APK. The only runtime control
is `activity-alias` enable/disable, which switches between **pre-built** icons (that's exactly what
M4.0's Default/Calculator/Notes disguise does). There is no API to point the app's launcher entry at
a gallery photo. Attempting it is impossible and was explicitly **not** pursued.

## Decision

Deliver the intended outcome via a **pinned home-screen shortcut** with a custom bitmap icon:

- **Pick** the image with the modern **Photo Picker** (`ActivityResultContracts.PickVisualMedia`) —
  no broad storage permission (`READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE`) required.
- **Crop/scale/persist**: center-crop to a square, scale to a 256px icon, and save it to
  app-internal storage (`IconImageStore`) so the shortcut can be **re-pinned** later without
  re-picking.
- **Pin** via `ShortcutManagerCompat.requestPinShortcut()` with `IconCompat.createWithBitmap(...)`
  and a user-supplied label, launching `MainActivity`. Guarded by `isRequestPinShortcutSupported()`;
  if the launcher doesn't support pinning, the user is told (graceful no-op).

The shortcut opens `MainActivity`, the same target the disguise aliases use — so a disguised launch,
the photo shortcut, and the Task-2 device-admin all **stack** and coexist.

## Consequences / limits

- This adds a **second** icon on the home screen (the shortcut); it does not replace the app's
  drawer icon. That is the platform limit, not a bug. Combined with the M4.0 disguise (drawer icon
  looks like "Calculator") the effect is close to a custom icon.
- Pinning requires launcher support; most stock launchers support it, some third-party ones don't.
- A **true** custom app icon (replacing the launcher entry with an arbitrary image) remains a
  **Phase-5 (ROM)** capability, where the app is a system component.

## Testing

- Pure helpers (`IconImageStore.centerCropSquare`, `iconFile`) are unit-tested.
- The bitmap decode/scale/compress and the pin request itself are Android-only and device-verified.
