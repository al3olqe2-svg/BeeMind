# Stage 5.5 — Minimalist UX Refinement

Base: owner-approved Stage 5 PR #4, merged into main as
`60cb68ad51a9a08f739a17aad655a92bb4002fa1`. This stage remains unmerged pending
owner phone acceptance. No Stage 6 changes.

## UI and storage contracts
- Home: static layered continuous rainbow light, responsive existing masonry, a 64dp
  circular neon + with an accessible Arabic label and its own bottom layout space.
  No title, welcome text, counters, metadata or empty-state marketing.
- Editor: one body field; titles are neither requested nor generated. Legacy titles
  remain private data, unchanged. Quiet Save and Back, one traits sheet; full Reminder
  actions remain there. Existing notes and all other tables are retained. Room stays v6.
- Reading: complete lazy body chunks and Edit/Delete/Threads; no trait/date/title bars.
  Links open in a sheet from the linking button. Locked capsules show only a safe cover
  and a timing-management action; editing remains disabled until unlock.
- One common appointment sheet for all three time traits: cumulative +5 (anchored at
  the first tap), calendar, clock, final deadline, confirm/remove and real validation.
  Reopening and confirming an unchanged date does not rewrite its schedule/generation.
- Date display is `d MMMM • HH:mm`, Arabic month, current device timezone. Epoch values
  and calendar's year navigation are intact, including DST ambiguity rejection.

## Autosave protocol
A 200ms typing debounce writes a persistent recovery draft (with a 2s maximum wait
during uninterrupted typing), followed by a note commit
at 800ms. A stable session UUID is also a new note ID. A single mutex serializes every
draft/commit, with in-flight draft writes and commits protected against debounce cancellation. A newer
live snapshot is checked after a commit, so typing during IO isn't overwritten. Only
actual changes write note content; `createdAt` stays immutable. Text-only commits do
not reconcile all AlarmManager schedules.

Back and explicit Save capture live fields, freeze departure input, flush the recovery
draft and await the atomic Room note commit. Save returns home; Back returns to the
previous route. Background/Activity pause flushes without debounce. Activity recreation
retains ViewModel text, and process recovery reads Room drafts without large Bundles.
An empty new note is discarded; an existing note temporarily cleared retains its saved
content plus the empty recovery draft. Failed writes retain text, show an error and
prevent successful-save navigation. There is no success snackbar or save/discard dialog.
Pending capsules do not seal mid-typing; the appointment confirmation states the lock,
and Save/Back/background applies it, clearing retained text only after a successful seal.

As with any debounced local editor, an abrupt kill/power loss before the latest draft
write completes can lose the in-flight debounce interval. Lifecycle flushes and atomic
transactions cover normal departures and recovery of every completed write; no claim
of synchronous disk durability at each keystroke is made.

## Validation
See the final PR description and matching GitHub Actions run for exact results.
Native API 29/34 CI runs TemporalDeviceTest, ThreadsDeviceTest, EditorDeviceTest and
the existing cold-process alarm/real ten-minute Snooze/Done harness. JVM tests cover
Room migrations, all traits, UI navigation, recreation, scheduling and compact dates.
Physical phone/TalkBack/smoothness acceptance remains the owner's test.
API 34 CI pins official Emulator 35.6.11 (build 13610412): 37.2.13 showed a host
RenderThread segfault while composing API 34 scenes, with ample host memory.
API 34 uses the guest software renderer to avoid the crashing host shader path;
API 29 retains Emulator 37.2.13 (build 16740729), previously qualified end to end.
The Android API 29/34 images, cold-process alarm tests and assertions are unchanged.
Manual diagnostic-only runs are explicitly labeled and are not full verification.

Signing is temporary and non-upgrade-guaranteed. Do not uninstall important data;
uninstall may erase local notes. No private key or account/cloud service was added.
