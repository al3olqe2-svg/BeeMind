# BeeMind R3 — Editorial Editor & Progressive Trait Interaction

Base: R2 `cee30111054a33e79f68496c9c07ede35562ff4c`, with PR #7 still draft/open.
R3 targets `redesign-r2-home-expressive-cards`; no earlier branch or PR is merged.
Version `0.7.2-r3`; CI run number remains versionCode. Signing policy is unchanged.

## Design and writing
Soft Porcelain is the existing R1 `BeeDesign.editorialBackground` token, exactly
`#F2F3F5`. The editor has no title, card, text toolbar, visible Save or traditional
traits sheet. A 56dp navigation lane leads directly into a 24dp-inset open body.
The existing Manrope/Tajawal writing family and 19sp/33sp body scale remain, with
content-aware RTL, dark porcelain ink and a purple insertion cursor. Placeholder
starts at the writing origin. Detail/reading belongs to R4 and is unchanged.

`EditorAtmosphere` draws cached static radial light fields behind the independent
text component. Glow uses overlapping rose/violet/cyan/mint/warm light across wide
page regions. Anger instead uses offset burgundy/crimson edge shadows and a broad
coral lower field. A graduated porcelain reading veil protects the central lines.
Normal draws only porcelain. No bitmaps, live blur, per-character effects, timers,
looping animations or new production dependencies are introduced.

## Floating tools and state
`FloatingTraitTools` is physically at the bottom left in RTL. Visible discs are
38dp, touch targets 48dp, with a fine border, mineral reflection, short standard
press feedback and a thin plus. Glow and Anger swatches have distinct colored auras.
Tools occupy their own layout space beneath the text viewport, including the final
line; they are not overlays on the cursor. A maximum 45% of the available height is
allocated to the scrollable appointment panel, protecting typing space on small
screens, large fonts and with IME visible.

`ToolExpansionState`: Closed → Primary → Expressive/Reminder/Resurface/Capsule.
Only Primary renders the four categories. A category replaces the other categories,
with an explicit return-to-categories control and a close control. System Back
moves up one level; dialogs dismiss first. With tools closed, IME Back hides the
keyboard before a subsequent Back requests safe exit. The navigation arrow uses
the same hierarchy and otherwise `requestExit()`. Short fade-in uses R1 motion
constants and the platform duration scale; exits remove inactive hit targets at
once. There is no stagger, bounce or continuous movement.

`EditorialWritingField` always uses the ViewModel's existing `TextFieldState`.
Tool/trait changes never create a field state, clear focus, assign text or reset
selection. One safe-insets owner consumes system/IME insets without additive IME
padding on subpanels. The dock stays fixed while the internal text field scrolls.

## Expressive controls and accessibility
Normal/Glow/Anger are the original exclusive enum, persisted through `selectTrait`.
Three material circles show a precise selection edge/check and Arabic selected
semantics. The ambient background changes immediately; home materials are untouched.
Every icon-only tool has an Arabic content description, logical traversal order
and 48dp target. Hidden categories leave the semantics tree. Closed tools show one
small indicator announcing active reminder/return/pending capsule dates. Appointment
summaries/errors use polite live regions. Text and dialogs use scalable sp sizing;
scrolling preserves access to date controls and actions at large font scales.

## Temporal interaction
`AppointmentDraft` holds only UI selection: initial epoch, proposed epoch, anchored
relative reference/count, editable local wall time and validation error. Each category
mounts a separate keyed session from its own current domain value. Going back,
closing or cancelling discards proposals without any repository write.

+5 is anchored at its first tap, then +10/+15/+20, bounded by the existing 525600
minute limit through `ReminderTimes.relative`. Calendar and 24-hour numeric `TimeInput`
are centered Material dialogs. Cancel leaves both the proposal and saved value intact.
Date and time confirmations update the proposal only; final Apply updates ViewModel
metadata only when the value differs. This preserves reminder generation/Done/Snoozed
and resurface consumption when an unchanged value is confirmed. Local conversion
uses `ReminderTimes.local`; past times and DST gaps/overlaps block Apply. Invalid wall
times remain editable rather than silently falling back to a different day. Epoch
precision survives untouched confirmations. Compact dates use device timezone and
existing `d MMMM • HH:mm` formatting.

The panel has clear Arabic Apply/Cancel/Remove actions, not ambiguous checkmarks.
Removal uses `selectReminder(null)` or `selectResurface(null)`, preserving the existing
serialized save, alarm cancellation and repository reconciliation. Reminder info opens
a compact scrollable dialog with the existing `ReminderPermissions` and
`ReminderDetails`: notification/channel settings, exact-alarm permission, battery
restrictions, scheduling/delivery errors, Done and Snooze remain available. Merely
opening it does not edit a reminder. No notification receiver/coordinator code changes.

## Capsule and autosave safety
Only the distinct **تأكيد قفل الكبسولة** action transfers a proposed capsule epoch
into `TemporalEdit`. Its dialog gives the opening date and warns that text is hidden
and the lock cannot be cancelled before then; it also explains the departure/background
commit boundary. Cancel/Back/+5/date/time cannot set a pending seal. Empty notes cannot
confirm a seal. The user can remove a pending seal while still writing.

The original EditorViewModel/Room protocol remains intact: 200ms recovery drafts,
800ms serialized autosave, bounded checkpoints, stable session/note identity,
immutable createdAt, one Mutex and NonCancellable in-flight writes. Pending confirmed
capsules skip debounced note commits. Safe Back/background flush saves all text and
then seals atomically. Text is cleared/navigated only after successful persistence.
Failure retains live text and the durable draft, shows the original error and offers
retry-and-exit. Existing temporarily empty notes keep saved content plus their empty
recovery draft; new empty notes are discarded. Recovery of a confirmed pending seal
retains that decision. Locked notes branch to the existing safe cover before any
text field or tool is composed. No schema, privacy projection or repository changes.

## Validation and remaining acceptance
The existing UI experiences are migrated from Save/traits-sheet interactions to safe
Back and progressive controls, preserving their functional assertions. Added tests
exercise +5 anchoring/bounds, independent sessions, DST correction, category hierarchy,
explicit capsule confirmation/cancellation, pending-seal debounce/failure/retry and
recovered background sealing. Existing long-text/recreation/draft/identity/Room/
notifications/Home/wallpaper regressions remain enabled.

`EditorR3DeviceTest` uses isolated in-memory Room and real Compose text/actions with
native IME/HWUI captures. The API 29/34 device workflow retains existing tests and
cold-process/real ten-minute Snooze checks, adding R3 captures. Native screenshots
are exported to the device evidence artifact. Local JVM screenshots are separate
from real Android and physical-phone acceptance. Exact executed results follow below.

Known limits: abrupt process death before an in-flight draft finishes can lose that
small debounce interval, as before. Physical TalkBack, keyboard vendors and low-end
smoothness still require owner acceptance. No local /dev/kvm is available; native
Android verification runs in GitHub Actions. Temporary debug signing remains
non-upgrade-guaranteed unless certificate compatibility is established; do not remove
an installed data-bearing copy. R4 reading/threads visual work and Stage 6 are deferred.
