# BeeMind — Instructions for Codex Agents

This file governs all Codex work in the `BeeMind` repository. Read `PRD.md` first. The product owner communicates in Arabic; report progress, blockers, test steps, and results in clear, concise Arabic. Preserve English technical identifiers in code.

## 1. Mission & working agreement
- Build the **real, offline, personal Android application** defined by `PRD.md`, not a mockup or a shallow proof of concept.
- Work on **only the stage explicitly requested in the current user prompt**. Never silently implement later stages or rewrite the roadmap.
- A stage is not approved until the owner tests its APK and explicitly accepts it. Don't claim physical-device verification unless the owner performed it.
- Prefer a small, complete, testable vertical slice over many unfinished screens. Keep the implementation elegant, maintainable, and appropriately scoped for one developer.
- If a requirement is ambiguous, choose the simplest behavior consistent with the PRD, document the assumption, and continue unless it risks data loss or makes the requested stage impossible.

## 2. Technology contract
- Native **Kotlin**, **Jetpack Compose**, Material 3 where useful; **Room** for persistent local records; Coroutines/Flow + ViewModel + Repository pattern.
- Android Gradle Plugin, Kotlin/Compose, Room, and Gradle versions must be mutually compatible. Verify compatibility before selecting versions; commit **Gradle Wrapper** and version declarations. Avoid dynamic dependencies or unreviewed experimental APIs.
- Proposed `minSdk = 26`; keep `targetSdk` compatible with current Android requirements and document it. Never lower targets just to evade permissions.
- **No Expo, Expo Go, React Native, Firebase, network backend, analytics SDK, ads, accounts, or internet access requirement.** Do not request `INTERNET` permission without explicit permission from the owner.
- Use one application ID (if the repository has none, propose `com.beemind.personal`) and **never change it after the first installable accepted APK**.
- Keep a straightforward package structure (`data`, `domain`, `ui`, `notifications` as needed). Avoid needless multi-module architecture, service locators, or dependency injection frameworks unless justified.

## 3. Data integrity first
- Room is the single source of truth. Persist notes, functional traits, reminder state, and thread links; do not use in-memory demo lists as the shipped data layer.
- `createdAt` is immutable. Editing updates `updatedAt`; resurfacing must not mutate `createdAt` or duplicate the note.
- Model functional traits independently and enforce `Glow XOR Anger` for the expressive trait.
- Use stable IDs, uniqueness constraints for note links, foreign-key integrity, and transactions where multiple records change together.
- **Never use destructive database migration**, wholesale table drops, forced app-data reset, or unapproved schema replacement. Write and test migration steps when schema changes.
- Do not expose locked Time Capsule text or title through card previews, linked-note lists, search (if added later), notification content, or accessibility descriptions.
- Avoid automatic cloud backup of private notes; configure platform backup/data-extraction rules appropriately for supported Android versions. Explain that uninstalling may remove local data; an export tool is not in V1 scope.

## 4. Timing & notification correctness
- Reminder is **one-shot**, with notification actions **Done** and **Snooze 10 minutes**. Snoozing reschedules one subsequent alarm from the action time; it is repeatable until Done, but is not calendar recurrence.
- Prefer `AlarmManager` for user-facing scheduled alerts; assess exact-alarm permission requirements (Android 12+), call `canScheduleExactAlarms()` where relevant, handle denial transparently and use a documented inexact fallback rather than lying about precision.
- Handle notification runtime permission on Android 13+, notification channels, explicit `PendingIntent` identity and mutability flags, valid notification actions, and idempotent receiver behavior. Avoid duplicate/ghost alarms.
- Cancel/reschedule alerts when a reminder is edited, completed, removed, or its note deleted. Restore relevant future alarms after device reboot and after exact-alarm permission becomes available, subject to platform limitations.
- A Resurface due item should rise to the top **when the app next evaluates due state**; it doesn't need a background notification. It stays promoted until read, then returns to created-date order.
- A Time Capsule unlocks on/after its specified timestamp when the app next evaluates due state. Before that time, its content remains hidden in every UI path. This is a UI reveal feature, **not encryption**.
- Test the app and services with app in foreground/background, process killed, device restarted, disabled permissions, and device time/date changes. State precisely what cannot be simulated in CI.

## 5. UX quality bar
- **Arabic-first RTL**, accessible font scaling, screen-reader content descriptions, approximately 48dp touch targets, and reduced-motion support.
- **Dark Only Cosmic Cyberpunk UI** (approved Stage 1.5), neon purple as primary action color, and **continuous, smoothly blended rainbow gradients**, never separate colored stripes or an indiscriminate rainbow on all surfaces.
- Pinterest-like **staggered/masonry** notes grid with different card heights, ordered by creation date descending except unread due resurfaced notes at the top; preserve predictable reading/navigation order.
- Long text must edit, display, and save correctly. Use bounded previews in grid, full text in details, and empty/loading/error states; avoid unnecessary animations that harm scrolling.
- Keep a coherent palette, typographic scale, spacing, corner shapes, and meaningful trait cues. Optimize readability and identity together. No screenshots or static fake interactive controls masquerading as features.

## 6. GitHub workflow, build, and signing
- Maintain a GitHub Actions workflow that runs on relevant pushes and manual dispatch, installs an appropriate JDK/Android toolchain, and runs at minimum Gradle compile + unit tests + lint (or documents a justified stage-specific omission).
- Publish a clearly named **installable APK** as a GitHub Actions artifact after a successful build, along with a short version/build identifier and preferably SHA-256. Do not publish the artifact when prerequisite tasks fail.
- **Critical:** GitHub runners are ephemeral; their default generated debug signing keys are not guaranteed to match across runs. Establish a **persistent test signing keystore** before calling builds safely updatable in place. Prefer a keystore stored securely via GitHub Actions secrets (e.g. base64 keystore + passwords/alias), never commit private signing material or passwords. Do not log secrets.
- If signing secrets have not been provisioned, clearly identify this as a Stage 0 blocker, explain the *smallest exact setup steps*, and do not claim that two independently built APKs will update one another. A temporary APK, if produced, must be explicitly labeled **non-upgrade-guaranteed**.
- Keep `applicationId` stable, increase `versionCode` for each distributed update, and test install-over-existing-version **with retained Room data**. Never change signing key without disclosing the reinstall/data consequences.
- Use only known-compatible versions of GitHub actions (`checkout`, `setup-java`, `upload-artifact`, etc.). Keep workflow simple and reproducible.
- Never assume GitHub has actually built a commit unless a real run has succeeded and logs/artifact can be verified. Report CI and device tests separately.

## 7. Definition of Done for every requested stage
1. Implement only the explicitly requested scope, without placeholder functionality that falsely suggests completion.
2. Run available `assembleDebug`/equivalent, unit tests, static checks, and lint where possible; log exact commands and pass/fail status. If CI must verify a tool unavailable locally, say so.
3. Provide the commit/changed-file summary, build/workflow status, the precise GitHub artifact name/location when verified, and 3–6 **manual Android test steps** focused on this stage.
4. State any known limitations, permission behavior, migrations, and signing risks. Don't call a stage "finished" if acceptance tests fail.
5. **STOP and await the owner's APK test/explicit approval** before beginning the next stage. A new prompt or request is required to proceed.

## 8. Forbidden shortcuts
- No Expo Go or changing technology stack without permission.
- No simulated notifications, fake saved notes, silent no-op buttons, or screenshot-only UI instead of working behavior.
- No wholesale reimplementation to fix a localized bug; favor focused edits and regression tests.
- No deleting previous user data, changing app ID, rotating keys, or replacing Room schema destructively.
- No excessive permissions, telemetry, remote storage, or new features not in the approved PRD.
- No claims like "tested on your device" or "available in GitHub Artifacts" without evidence.

## 9. Stage roadmap (execute one at a time)
- **Stage 0:** repository assessment, app foundation, Gradle compatibility, Room foundation, stable signing/CI artifact path, first installable APK.
- **Stage 1:** note CRUD + persistent Arabic editor + chronological masonry home.
- **Stage 2:** expressive traits Glow / Anger and their single-choice rule.
- **Stage 3:** real one-time reminders + notification actions, permissions, cancellation, reboot checks.
- **Stage 4:** Resurface and Time Capsule, including reveal and redaction edge cases.
- **Stage 5:** bidirectional Threads + integrated regression, performance, accessibility, and update-in-place verification.

**Operating rule:** When the owner asks for Stage N, deliver Stage N only, with verifiable APK evidence and a compact Arabic testing checklist. Do not advance without explicit approval.

## Approved owner decision — 10 November 2026
Persistent APK signing is deferred. Temporary debug APKs may be delivered for testing; signature compatibility and retained-data upgrades must be verified before claiming safe in-place updates. Never uninstall automatically or commit private keys. This supersedes the earlier Stage 0 signing blocker.
