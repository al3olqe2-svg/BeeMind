# Stage 4 — Resurface & Time Capsule

Stage 3 PR #2 was open, mergeable, and green at `2f668b2`. The owner's Stage 4 instruction explicitly accepted Stage 3 and authorized its merge. It was merged normally into main at `48ca9d6`; Stage 4 branches from that commit. Stage 4 must remain unmerged until phone acceptance. No Threads/Stage 5 work is included.

## Temporal policy and storage

Room 4 → 5 uses additive ALTER TABLE migration. Existing IDs, original dates, expressive traits, reminder generations/status/delivery and recovery drafts survive. Exported schema 5 is committed. New nullable note columns are `resurfaceAt`, `resurfaceSeenAt`, `unlockAt`; new draft columns preserve pending temporal edits. No destructive migration, new service, alarm, account, network permission or encryption.

Dates are absolute epoch milliseconds. Material date/time inputs resolve the phone's current zone, rejecting past times, DST gaps and ambiguous overlaps through the existing ReminderTimes policy. Repository uses an injectable java.time.Clock and rechecks future times transactionally. Display uses the current local zone without reinterpreting the saved instant.

A new/corrected Resurface cycle resets its seen marker. Due and unread is derived from dueAt ≤ now and seenAt == null. SQL orders those rows first by resurfaceAt DESC, then createdAt DESC and id ASC; all other rows retain createdAt DESC / id ASC. Reads/projections/scrolls never mark seen. A resumed readable details screen sends an explicit opened event; repository conditionally updates only the seen marker, refusing locked content. Locked visits preserve pending due returns. Editing/cancelling temporal metadata never rewrites text or createdAt.

Capsule sealing saves content, expressive/reminder changes, temporal state and draft cleanup in one transaction. Empty notes and elapsed times fail without losing the draft. The editor clears its retained text only after successful persistence and replaces its content with the cover immediately. A locked note cannot be saved or recovered into an editor through an ID or draft route. Locked projections have null title and empty body/preview; SQL does not project original preview text for locked rows. The cover accepts only unlockAt, with no sensitive accessibility description. Home/detail state caches reset when unsubscribed to avoid replaying a pre-seal snapshot. All reminder notifications and their public versions/PendingIntents are deliberately content-free for ordinary notes and capsules alike, including notifications already displayed before sealing. Reminder Done/Snooze remain the Stage 3 actions; locked reminder date edits/cancellation use metadata-only operations.

Time Capsule controls readability, independently of Resurface priority. A return due before unlock stays unread even when the cover is visited, then remains promoted when content unlocks. Equal deadlines unlock/promote together. Glow/Anger coexist with both through the existing exclusive expressive-trait field and card border/halo.

The Repository evaluates one boundary timer per active data subscription (never per card), rescheduling on persisted deadline changes. A shared lifecycle refresh and Android time/timezone broadcasts re-evaluate immediately; a 30-second bounded clock check handles clock movement without reloading/sorting on every check. Data emissions occur on boundary crossings or explicit invalidation. No requirement for internet or a running process at the deadline; saved instants reconstruct state on next access. This is an app UI time lock, not encryption or protection from changing the device clock/database access.

## Validation

JVM tests cover deterministic due ordering, read-only consumption, cycle restoration, schedule/cancel, preserved original dates/content, expired confirmation rollback, empty capsule rejection, safe locked card/detail/editor/draft models, all three relative deadline orderings with Glow/Anger, metadata-only locked edits, Reminder coexistence/privacy, nearest-deadline Flow refresh, and transactional migration from exported schemas 1–4.

Compose tests exercise review → seal → locked details → locked masonry → Activity recreation → live unlock, with unmerged semantics checks and screenshots. Android instrumentation uses an isolated disposable SQLite database migrated from schema 4, closes/reopens it, and verifies locked visits, unread return persistence, unlock, original content/date, expressive trait and reminder identity/status.

CI builds APK and instrumentation APK, runs JVM/Robolectric tests and strict lint. PR/manual runs execute the new native SQLite test on API 29 and 34 before the retained Stage 3 cold-process notification/real ten-minute Snooze harness. The harness uses disposable emulator fixtures only.

Actual final commands, results and artifact evidence are recorded with the delivery PR after execution; this document does not imply an unrun check passed. No physical-phone testing is claimed.

## Signing and phone acceptance

Temporary signing remains authorized. Version 0.5.0; CI versionCode is the actual increasing workflow run number (local fallback 30, above the previous distributed build 29). The APK artifact includes SHA-256 and signing certificate evidence. Independently generated temporary keys may prevent installing over Build 29. Do not uninstall a copy containing important notes: uninstall can delete local data. No safe retained-data upgrade is claimed across incompatible certificates.

Phone acceptance is still required. Test the five Stage 4 scenarios in the delivery report; do not merge Stage 4 or begin Stage 5 before the owner's explicit acceptance.
