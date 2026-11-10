# Stage 5 — Threads & Integration QA

Stage 4 was owner-approved and merged normally from PR #3 at `d1efe50f9054421ff628019bd82d04d54aa7441c`, after verifying mergeability and successful head Build 40 (`f142f64`). This branch starts from that updated main. **Stage 5 remains unmerged pending the owner's phone tests and explicit acceptance. Stage 6 has not started.**

## Behavior and design

Details have a quiet neon connection rail, a «الخيوط» section, bounded linked-note cards, direct navigation and individual unlink actions. «ربط فكرة» opens a full-height local picker with Arabic previews, current-link state, immediate persisted feedback, unlink, and search explicitly limited to available previews. It excludes the current note. There is no standalone graph or animation loop. Links constitute the Threads trait; no separate potentially inconsistent activation flag is needed.

Navigation uses the existing `note/{noteId}` Navigation destination and lifecycle/source-entry guards. Every deliberate tap opens one destination; Back returns to the previous note, including a manually followed cycle. No recursive graph traversal or automatic link creation occurs. Only IDs enter navigation state. Existing editor/save/draft paths are retained; Threads is managed from saved details, so leaving unsaved editor content still uses the existing save/draft/discard protection.

## Persistence and privacy

Room **6**, additive **5 → 6** migration: `note_links(lowId, highId)`, composite primary key, foreign keys to both notes with `ON DELETE CASCADE`, index on `highId` (the composite key indexes `lowId`). SQLite BINARY comparison canonicalizes endpoints inside the insert; a single row represents A ↔ B. Database insert/update triggers reject self/reversed edges, on both migration and fresh production creation. Duplicate inserts are idempotent; repository existence checks and each link mutation run in a Room transaction. No existing note/draft/reminder row is rewritten by this migration or by linking.

Linked lists use indexed endpoint unions and a single bounded joined projection, not a query per card. The picker subscribes to candidates only while open. Previews use the **same SQL projection as Stage 4 home** (160 title / 420 body Unicode characters, with SQL redaction before leaving Room), driven by the existing temporal clock/refresh rules. Search runs only on that redacted model. Locked preview/rendering paths additionally use a generic capsule label and allowed unlock date. Details always use the existing safe repository and locked cover. Merely listing/linking a due return does not mark it read; opening readable details consumes it through the existing lifecycle handler. Thread changes never call alarm scheduling or alter reminder generations/status, creation times, content, expressive traits or temporal metadata.

## Verification scope

Automated coverage added for concurrent inverse duplicate requests, raw SQL constraint rejection, self/orphan rejection, unlink/relink, cycles, cascade deletion, both-end Flow updates, time-boundary unlock, locked search/redaction, independent reminder Done/Snooze/cancellation, retained drafts and unchanged long content. Exported schemas 1–5 migrate to 6; the version-5 fixture compares **every old column** before/after migration and database reopen. Native device fixture independently exercises schema-5 migration, process-independent database reopen, all six traits, cycles and cascade on real SQLite.

Compose tests exercise the real app/Room/Navigation: linking both ends, source exclusion, return consumption only on readable opening via Threads, cyclic navigation/Back, Activity recreation, unlink, locked picker/search/semantics/details, and 320dp at 150% font size. Screenshots are retained in validation artifacts. A synthetic 200-long-note / 199-edge fixture checks bounded projections and both endpoint indexes; its timings are diagnostic JVM evidence, **not measured handset frame performance**. Physical TalkBack, OEM keyboard/background behavior and handset smoothness still require owner feedback. Existing note CRUD, long text, draft recovery, masonry, expressive exclusivity, temporal and native reminder regressions remain enabled.

## Phone acceptance — five checks

1. اربط ملاحظتين من «الخيوط ← ربط فكرة» وتحقق من ظهور الرابط من الطرفين.
2. افتح الملاحظة المرتبطة ثم ارجع إلى السابقة؛ جرّب الانتقال بين ثلاث ملاحظات مترابطة.
3. أزل رابطًا ثم أعده؛ احذف إحدى الملاحظات وتحقق من بقاء الأخرى وإزالة علاقتها بالمحذوفة.
4. أغلق التطبيق وافتحه وتحقق من استمرار الروابط؛ اربط كبسولة مغلقة وتأكد أن الاختيار والبحث والمعاينة وفتحها لا تكشف عنوانها أو نصها.
5. اربط ملاحظة Glow أو Anger مع تذكير؛ تحقق من بقائها وسلامة التذكير والإنجاز/التأجيل.

## Signing and boundaries

Persistent signing remains owner-deferred. CI artifacts are explicitly `TEMPORARY-NON-UPGRADE-GUARANTEED`. Schema migration/reopen tests do not establish APK signature compatibility. Do not uninstall an installation containing important notes to bypass a signature mismatch: uninstall can erase local data. No export, encryption, internet, cloud services, new V2 features or Stage 6 changes are included. Capsule privacy is the existing application UI time lock, not encryption or protection against clock/database manipulation.
