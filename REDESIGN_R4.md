# BeeMind R4 — Final Reading & Refined Glass

Base: R3 `340dd59b0440ee799402404544838df03d173ee6`, PR #8 open/draft.
Branch `redesign-r4-final-reading-polish` targets R3. No PR is merged.
Version `0.7.3-r4`; workflow run number remains versionCode; signing is unchanged.

## Final creative direction
Atmospheric Editorial — Refined Glass Edition. Home is translucent mineral glass
above visible photography; editor and reading share Soft Porcelain `#F2F3F5` and
static expressive light fields. Existing merged Manrope/Tajawal typography remains.
No launcher icon, application ID, Room schema, note ordering or Stage 6 changes.

## Reading screen architecture
`DetailScreen` composes the existing `EditorAtmosphere`, a transparent Scaffold and
a fixed 56dp action lane. Back is at RTL start; Edit, Link and More occupy the other
end. Glyphs are small in 48dp targets. More contains Delete; the existing ViewModel
confirmation/busy/error/retry and successful-delete navigation remain. Locked notes
omit Edit entirely. No auto-hide behavior or wallpaper appears in reading.

`ReadingArticle` retains `readingChunks()` (4,000 UTF-16 units, newline preference,
no surrogate splitting), SelectionContainer, a single LazyColumn and stable body keys.
Text stays body-only, dark, 19sp/33sp, content-aware RTL, inset 24dp. There is no
extracted title/date/trait strip, nor inserted decoration between text chunks.
The old Resurface `readableOpened()` lifecycle hook/retry stays guarded by !locked;
scrolling never writes Room. Lazy list state belongs to its navigation entry.
Selection uses Compose's lazy-text selection behavior; offscreen text is not all
materialized just to support selection.

## Inline Threads architecture
The footer subscribes to `DetailViewModel.linkedNotes`, the existing Room Flow of
bounded `ThreadNote`/`NoteSummary` projections. Only nonempty results add a 36dp
breathing space, fine divider, small «أفكار مرتبطة» label and vertically lazy cards.
Each card uses preview only, at most three lines, a subtle expressive edge and a link
or lock glyph. Links are outside text selection and never inserted between chunks.
Opening follows existing lifecycle/source-entry navigation guards;
Back restores the previous entry rather than replacing the article.

Link opens a centered management dialog capped at 520dp, with a compact lazy vertical
list. Open/remove, disabled busy state, saving label and failure feedback remain.
Add uses the existing searchable full-screen picker and existing bilateral relation
rules. Flow updates make successful edits visible immediately; failed mutations do
not optimistically remove cards. Picker subscriptions end when management leaves.

## Thread privacy
`matchesThreadSearch()` still searches only the safe projection and substitutes
«كبسولة مغلقة» for locked notes. Inline and management previews branch on locked
before touching preview/title, including malformed summary inputs. No full linked
body, extra link text copy or schema change exists. Locked reading never composes
ReadingArticle or its footer. Search/semantics never receive locked content.

## Time Capsule reading cover
The timestamp-only shared `CapsuleCover` now has pearl/slate glass gradients, fine
metallic reflection, a quiet outlined lock and opening date. It accepts no note text.
Existing allowed appointment controls and lock rules are retained. Home's separate
R2 timestamp-only cover remains. Lock is a UI reveal rule, not encryption.

## Wallpaper blur recalibration
Slider range is 0–100. The reference radius is `6 * (value/100)^2` at 720px; rendering
scales it to the derivative resolution and rounds nonzero radii to at least one pixel.
Zero skips blur entirely. 100 equals R1's minimum integer radius 6, versus R1's old
maximum 20. Color compression was removed; atmospheric overlays are substantially
lighter and introduce no diffusion. Stored original bytes remain unchanged.

Output follows viewport pixels within 720–1920px long-edge bounds; small-heap devices
cap at 1080px. Sources are dimension-checked and power-of-two sampled to at most
2048px, with EXIF orientation and centered crop preserved. Thumbnails remain 640×280.
Processing is serialized on IO, keyed/cached, with 120ms preview debounce and previous
frame retained while replacements arrive. Save occurs on slider gesture completion.

## Old-to-new blur migration
The atomic JSON preference record now writes `blurVersion: 2`. Existing records with
a blur and no v2 marker clamp intentionally to 100: every valid old radius is at
least the new maximum. Fresh settings default to 35. Migration commits before
publishing loaded state; write failure can retry. IDs/order/pin/current/files are
retained. Later values, including zero, survive recreation/restart without remapping.
Wallpaper session/rotation behavior is unchanged.

## Translucent glass material
`WallpaperGlassHost` computes one independent 720px diffusion derivative (radius 16)
for the viewport, independent of the background slider. A composition local shares
it with visible cards. `frostedWallpaper()` clips its drawing to the existing 14dp
shape and offsets the viewport image using window coordinates. Scrolling updates
sampling position; it never restarts the crop at each card or decodes per card.
The same CPU raster path works on API 26 and newer; no RenderEffect dependency.

Normal material changes from ~94–96% opaque dark stops to ~38–47% pearl/slate stops,
a graduated light reading veil, restrained specular light, fine rim and soft shadow.
Dark ink and a dark green return marker protect contrast. Background colors and
composition remain visible through the diffuse layer. Without a custom image, the
same translucent material sits above the existing atmosphere gradient.

## Glow & Anger enhancement
Glow uses brighter rose/gold/mint/cyan/violet glass, strong internal cyan light and
spectrum rim. Anger has directional ruby/crimson/burgundy/rose composition and coral
light concentrated opposite the upper edge. It is not a red spectrum clone.
External aura stroke alphas rise from .045/.09/.23 to .22/.38/.68; internal light
from .30 to .62. Three bounded layers retain an edge rather than pulsing. Aura extends
4dp into existing 12dp gutters, so geometry and neighboring text stay protected.
The FNV-1a sizing, 140/185/234dp minima, 3/5/7-line previews and order are untouched.

## Editor glass controls
`MicroTool` now uses transparent mineral stops, internal diffusion/shadow, an
asymmetric specular highlight and illuminated rim above a soft elevation. Saturated
Glow/Anger swatches have stronger small auras. Icons/checks draw last and selected
controls retain an explicit check/edge plus Arabic state semantics. The shared
component also refines +5/date/clock/back/close; editor navigation uses it too.
The R3 tool hierarchy, IME layout, temporal drafts/confirmations and TextFieldState
ownership are unchanged. Dialog/action text stays on readable editorial surfaces.

## Performance and accessibility
Cached brushes and bounded strokes; no perpetual animation, per-frame bitmap creation,
per-card blur, extra full linked-note loads or forced offscreen compositing. LRU is
byte-sized and adapts between 8 and 24MiB to heap size. One mutex serializes bitmap
work. Raster paths retain API 26 compatibility. Touch targets remain 48dp; scalable
fonts, mixed RTL text and explicit Arabic content descriptions remain. Hidden tools
and locked Edit are absent from semantics. Existing short transitions honor platform
animation scale. Physical TalkBack/low-end smoothness require phone verification.

## Visual QA and automated results
Executed results and opened screenshot evidence are recorded below after verification.
The fixtures use a test-only public Unsplash coastal photograph plus synthetic
sea/light/dark/portrait/detail images, never private images. Fixture provenance is
recorded in `app/src/test/resources/README.md`.
Native API 29/34 CI retains prior temporal, reminder, real ten-minute Snooze, Done,
autosave/recovery, Room migrations, wallpaper sessions and FNV regressions, adding
`ReadingR4DeviceTest` and shared-backdrop home rendering.

## Known limitations
No local KVM device is attached; native tests execute in GitHub Actions. Synthetic
portrait fixtures are illustrations, not real photos; phone photo/provider variations
and physical TalkBack/limited-GPU performance remain owner acceptance. Temporary
signing stays non-upgrade-guaranteed unless certificates match; never remove an
installed data-bearing copy to fix a signature mismatch. No claim of encryption,
cloud backup, export or Stage 6 implementation is made.
