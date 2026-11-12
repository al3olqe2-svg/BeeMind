# BeeMind — R2 Home & Expressive Cards

Base: latest inspected R1 `6a10904380bda2abaf7f3b1d350fb591977163a7`.
PR #6 remains open/draft; R2 targets `redesign-r1-foundation-wallpapers`.
No merges are authorized. Version `0.7.1-r2`; versionCode still comes from the
existing GitHub workflow run number, with the signing policy unchanged.

## Creative direction
Atmospheric Editorial — Cinematic Moodboard × Intellectual Luxe. Notes are the
visual priority. Smoked mineral glass sits above R1's processed photography; static
light and moderately varied proportions provide emphasis without animated decoration.
No home heading, note title, creation date or unnecessary functional-trait badges.
The rainbow remains a continuous softly lit gradient, with a 1.5dp core and
3/6/10dp low-opacity outer light. No continuous motion is introduced.

## Components and material
- `HomeMaterials.kt` extends the existing design system with home-specific roles.
  R1 themes, shared editor/detail surfaces and trait picker are retained.
- `HomeCardSurface.kt` renders cached layered rounded rectangles: dense translucent
  base, diagonal mineral gradient, localized radial illumination, dark text veil,
  restrained reflection and an inset 0.8dp edge. Radius remains 14dp; content inset
  is 16dp; grid margins remain 24dp and gutters 12dp.
- Normal smoke stops: `#F22C3442`, `#F51A202C`, `#F0212935` (ARGB). Dense alpha
  isolates text from bright/detail-heavy imagery without requiring per-card blur.
  Text uses R1 `#F2F3F7`, 16sp/27sp, the existing Manrope/Tajawal font and RTL.
- Glow full-surface stops: `#93405E`, `#9A713E`, `#226C62`, `#315E99`, `#824EAF`.
  Rich rose/gold/mint/blue/violet material combines with R1's smooth spectrum edge,
  cyan internal illumination and a bounded external aura. A dark veil keeps text
  readable; color is present throughout the surface rather than only its outline.
- Anger uses a distinct directional composition: burgundy/crimson top, dark core,
  coral illumination concentrated toward the opposite bottom corner. Stops:
  `#872D46`, `#491A30`, `#281D2C`, `#66243C`; light `#FF6D80`.
  It shares geometry/interaction with Glow, not its rainbow material.
- `HomeNoteCard.kt` owns previews, accessible traversal indices and clicks;
  `HomeScreen.kt` only composes the grid and controls. Existing `expressiveHalo`,
  `CapsuleCover`, editor, trait sheet and detail treatment remain unchanged.
- `MiniGlassAdd`: 42dp visible disc inside a 52dp enabled/disabled IconButton,
  23dp plus, 0.8dp edge and 3dp shadow, with the existing Arabic label/test tags.
  The grid's viewport ends 72dp above the bottom, with 24dp internal bottom padding,
  keeping the final card clear of the button and system bars. Loading and new-draft
  resume semantics are retained. The settings target remains 48dp with a 36dp glass backing/21dp glyph to preserve
  its contrast even on an untreated white stress-test background.

## Stable masonry sizing
`homeCardSize(id)` applies explicitly specified FNV-1a, with unsigned 32-bit overflow,
across the ID's UTF-16 code units and uses modulo three. It does not use the list
index, text length, trait, timestamps, process seed or a mutable random generator.
Golden-value tests pin this mapping across future changes. Nothing is stored in Room.
Compact/Medium/Tall minima are **140/185/234dp**, previews **3/5/7 lines**.
A returned badge consumes one preview line instead of enlarging every returned card.
Minimum height scales with fontScale above 1.0; content may grow rather than be
clipped, especially capsule dates or extreme accessibility font sizes. Text wraps
and ellipsizes; the DAO still supplies at most 420 characters, never full diaries.
Existing `masonryColumns()` is retained, including one column at 320dp or larger
fonts. Stable ID keys and LazyVerticalStaggeredGrid preserve lazy rendering/order.

## Temporal presentation and privacy
`HomeCapsuleCover(unlockAt)` accepts only a timestamp, never Note/NoteSummary,
preview or title. The caller branches before composing any text preview; a malformed
locked summary is also tested to ensure no leakage through merged/unmerged semantics.
Dense pearl/slate art glass (`#F149485A`, `#FA252F40`, `#F3393549`) has a thin elliptical
reflection and an outlined lock; only lock status and compact unlock date are shown.
The shared detail cover is untouched. No hidden-content animation or decorative text.
`HomeResurfaceIndicator` is a compact mint (`#B4D9CC`) upward-return marker, present
only for the DAO-projected `resurfaced` flag. It coexists with expressive cards and
locked covers. No scheduling, ordering, consumption or readableOpened logic changes.

## Performance and boundaries
All material brushes/strokes are built by `drawWithCache`; rounded rectangles use
static GPU drawing, without per-card blur, bitmaps, RenderEffect or looping animations.
The aura extends at most 4dp into a 12dp gutter. Clipping/ripple is inside the aura
renderer so the exterior light remains visible. No forced offscreen compositing.
No new dependencies. API 26 remains supported. Wallpaper processing, repositories,
session rotation, blur and preferences are untouched. No Room/schema, application ID,
icon, creation-time, autosave, draft, notification, thread or signing changes.
R3 editor/tools/time-pickers and R4 reading/thread presentation remain deferred.

## Validation
Build success alone is not visual acceptance; executed evidence follows below.
New JVM tests cover deterministic sizes/distribution, edits/reorder/Resurface invariance,
three equal-content heights, cover semantics, narrow/large-font layout, click routing,
draft resume/loading, last-card clearance, rainbow/settings and empty home.
Existing functional/privacy/Room/expressive persistence regressions remain enabled.
The former content-length-height assertion now checks the stable ID-derived ratio;
legacy-title privacy/storage assertions are unchanged.
R2 synthetic local captures cover sea, white/bright, dark, portrait-toned, spectrum/detail,
fallback, 320dp/1.8x fonts and empty home. Mixed fixtures include Normal, Glow, Anger,
locked capsule and unread returned note. The existing R1 real-image pipeline scenes
also render the new cards, including synthetic people and busy checkerboard sources.
The API 29/34 HWUI scene fixture now captures all R2 card types alongside normal cards
and exports `r2-*.png`. Existing native functional and real ten-minute alarm checks remain.

Physical phone acceptance, TalkBack navigation, low-end scroll smoothness and retained-data
signature-compatible upgrade cannot be claimed from JVM/CI. Temporary APKs remain
non-upgrade-guaranteed; never uninstall a data-bearing version to work around signing.

### Executed local verification — 12 November 2026
JDK 17 / Android SDK 35: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug
:app:assembleDebugAndroidTest` passed. **123 tests, zero failures; lint: no issues.**
This includes the 118 existing regression executions plus five new R2 executions.
Initial Robolectric downloads failed because the downloaded JDK did not inherit the
Cloud proxy CA; using the environment's trusted Java certificate store resolved it
without repository or application networking changes.
The actual PNGs were opened and visually inspected: all six mixed-card atmospheres,
large-font/narrow and empty home, equal-length notes and the last-card scroll state;
also R1's actual processed synthetic sea/white/person/checkerboard image scenes with
R2 normal cards. White stress review prompted a dark settings-disc backing, and the
Glow palette was strengthened. The final full rerun passed all four tasks after those last refinements (123/123, lint clean);
its updated sea/white mixed-material PNGs were opened and visually reviewed again.
No local KVM device exists. API 29/34 native verification and final artifact records
are reported in the matching Draft PR/workflow, separately from physical-phone acceptance.
