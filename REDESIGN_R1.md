# BeeMind — Redesign R1: Foundation & Wallpapers

Base: `stage-5.5-minimalist-ux` / `dd9db05276ccbc91a180f93ddf5b8a0ff3ff80d6`.
PR #5 was open/draft at inspection. R1 targets that branch, without merging it.
This supersedes only the old Dark Cosmic Cyberpunk **visual** instructions in
PRD/AGENTS. All existing data, offline, timing, capsule and autosave contracts remain.

## Implemented design foundation
- Atmospheric Editorial: `BeeDesign` explicitly separates editorial foregrounds,
  surfaces and borders from dark atmospheric roles, glass treatments, controls,
  errors/disabled states, overlays, spectrum, Glow and Anger brushes.
- Editorial background is **Soft Porcelain #F2F3F5**. Material 3 has a light scheme
  for writing, reading, sheets and wallpaper settings; home has a scoped dark scheme.
  System status/navigation icon appearance follows the current destination.
- The existing OFL merged Manrope/Tajawal family remains. Text uses sp units,
  content-aware RTL direction, wrapping and the existing long-reading scale.
- 14dp cards, 20dp panels, 12dp buttons and shared spacing/touch tokens. No masonry
  geometry or preview-limit changes. The 22dp settings glyph has a 48dp touch target
  above the rainbow; add control, capsule redaction and trait functions remain.
- Short shared motion tokens; existing Compose tweens honor the platform animator
  duration scale. No looping effects or new animated image layers.

## Wallpaper architecture
- `WallpaperRepository`: application-private `files/wallpapers/<UUID>.image` originals.
  One atomic SharedPreferences JSON record stores ordered IDs, current/pinned ID and
  blur. A mutex serializes load/import/selection/preferences/removal on IO. A StateFlow
  publishes successful writes. Room and its schema/migrations are untouched.
- Android `PickMultipleVisualMedia` accepts one or multiple images (up to 20 per
  selection); AndroidX falls back to the system document picker on older devices.
  No broad storage/media permission or persistent external URI is needed. Copies are
  completed and validated before entering the collection. Partial/invalid copies are
  removed; partial batch success is reported. Originals in the gallery are never deleted.
- Imports are capped at 40 MiB each and 150 megapixels decoded dimensions, with
  sampled validation. Errors retain the existing collection; users can retry. Unknown
  temporary/orphan files are reclaimed on repository initialization. Missing files are
  filtered and dangling selections/pins repaired; an empty set uses the local gradient.
- `WallpaperSessions` is application-scoped. MainActivity start/stop boundaries trigger
  round-robin selection once per foreground session. The current ID is persistent;
  the next entry advances without consecutive repetition for 2+ images. Navigation,
  recomposition and scrolling do not trigger this code. Configuration stops and the
  external picker round trip do not mark a new session. A genuinely new process may
  select once. No timers, speed preferences or background jobs exist.
- Pinning selects that UUID immediately and suspends rotation across process restarts.
  Unpinning resumes the cycle on the next entry. Deleting the pinned/current image
  clears that reference and selects a remaining image, or the fallback.

## Rendering and performance
- The only added dependency is AndroidX ExifInterface 1.4.0: the platform EXIF
  implementation has known older-Android bugs flagged by lint. It handles orientation
  without a full image-loading library.
- `WallpaperImages` decodes bounds before pixels, samples with a power-of-two factor,
  handles EXIF orientation, crops to viewport aspect and caps the processed image's long
  edge at 720px. Thumbnails crop to 640×280px for clear gallery previews; all sources decode to at
  most 1440px before cropping. This avoids magnifying a tiny portrait thumbnail
  across a wide settings row.
- A platform-independent three-pass separable box blur approximates a Gaussian on API
  26+, without RenderEffect or RenderScript. Slider range 20–100, default 65. The range
  maps to practical radius 6–20 at bounded resolution, with smooth filtered scaling.
- Saturation/highlight-range compression, a cool atmospheric tint and vertical edge
  shading soften wildly different inputs. Only displayed derivatives change; stored
  image bytes remain untouched. No processing occurs inside note cards.
- One IO worker and a byte-sized 12 MiB LRU cache bound work/memory. Compose requests
  are keyed by image/blur/aspect; a 100ms debounce avoids processing every slider tick.
  The previous rendered result remains visible while its replacement loads; a cache
  hit is available on the first frame when returning home, avoiding a fallback flash. Expensive
  work is never performed by HomeScreen or synchronously on the main thread.
- Settings previews blur interactively, saves on gesture/accessibility completion, and
  uses the same rendering pipeline as home. The editor/detail never receive wallpapers.

## Validation
Exact executed results are recorded in the PR and final delivery report.
- `WallpaperRepositoryTest`: empty/single/multiple, cycles, durable pin/unpin/blur,
  deletion, corrupt/unreadable/partial inputs, large sampled decode, original-byte
  preservation, missing-file recovery, session/configuration/picker gate (API 26/34).
- `WallpaperExperienceTest`: real Compose settings/navigation/recreation, Arabic notes,
  blur accessibility action, and synthetic sea/bright/dark/portrait/detail/fallback captures.
- `WallpaperDeviceTest`: Android-native import/processing/persistence/pin/delete checks
  on the existing API 29/34 emulator matrix.
- `WallpaperVisualDeviceTest`: six synthetic home captures through actual Android HWUI.
  These and the original temporal/threads/editor/cold-process reminder/Snooze/Done
  harness run in CI. No personal images are committed.
- Existing unit/regression/privacy/migration tests remain enabled. Build, unit tests,
  lint and Android test APK compilation are required. APK publishing/signature reports
  retain the existing workflow. Artifact version is `0.7.0-r1`; CI's monotonic run number
  remains the versionCode source.

## Boundaries and acceptance
R1 only. Final glass cards, masonry sizing, expressive refinements and final add-button
sizing belong to R2; final editor/reading redesigns and further stages are deferred.
No icon/resource/applicationId/Room/autosave/reminder/thread behavior changes.

Physical phone, real gallery/provider variations, TalkBack, subjective blur/smoothness
on limited devices, OS background/process eviction and install-over-existing-signature
acceptance require owner testing. A debug APK remains temporary and **non-upgrade-
guaranteed** unless signatures are compared. Never uninstall an existing data-bearing
installation to resolve a mismatch. Persistent signing is deferred. R1 PR stays unmerged.

### Executed local verification — 12 November 2026
`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`
completed successfully under JDK 17 / SDK 35. **118 tests, 0 failures; lint: no issues.**
The 24 added test executions include API 26/34 repository/processing checks and two
Compose experiences. Existing 94 regression tests remain successful. The EXIF test
verifies rotation and byte preservation; thumbnail checks bound allocation and retain
original colors. Six synthetic home scenes plus settings/pinned-gallery screenshots
were generated and visually reviewed locally. Actual Android API 29/34 HWUI and timing
results are delivered with the matching PR workflow; this local result is not a claim
of physical-device testing. No local Android emulator/KVM is available in this executor.
