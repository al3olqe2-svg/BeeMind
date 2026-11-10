# BeeMind 0.4.0 — Stage 3

Stage 2 was reviewed, its CI was successful, and PR #1 was merged normally at
`537b7be4ebdb3c2067d04e3b7fe246f6964ddc71`. Stage 3 branches from that main.
This document describes implementation policy; phone acceptance remains pending.

## State and consistency

Room 4 is authoritative. Migration 3→4 adds one reminder row per note (primary key
`noteId`, cascading foreign key) and two nullable/flag draft fields. No note text,
trait, creation timestamp, or chronological ordering changes in the migration.
The same row can be reused for a new reminder after Done; there is no history or
recurrence engine. Status is SCHEDULED, SNOOZED, DONE, or CANCELLED; delivery is
PENDING, CLAIMED, DELIVERED, BLOCKED, FAILED, or MISSED. `scheduling` records EXACT,
INEXACT, PENDING, or FAILED. The UI exposes delivery/permission failures separately
from scheduling and completion.

Text, expressive trait, reminder intention, and draft removal commit in one Room
transaction. A draft never schedules an alarm. An unchanged reminder edit is
`edited=false`, so saving text cannot undo a concurrent Done/Snooze action.
Saved relative choices are fixed instants from the selection time (including
seconds in the preview); +5 extends that same reference. Save revalidates the
future instant. Local calendar choices reject DST gaps AND overlaps with a clear
error; users choose an unambiguous time instead. Instants are epoch milliseconds;
timezone changes affect display, not the chosen instant.

Every reminder mutation replaces a UUID generation. Android intent data includes
note ID, generation and action, and all intents are explicit and immutable. Done
and Snooze use conditional SQL matching the generation and active status; a
successful transition immediately invalidates repeat and stale broadcasts.
Receivers, editor saves, deletion and recovery share one process mutex, with Room
transactions/conditional updates providing durable validation. There are no
additional app processes, foreground services, timers or network dependencies.

After Room commits, Android operations execute. Failures preserve the committed
intention for recovery and display scheduling errors. Recovery runs on resume,
boot after normal unlock, time/timezone changes, app replacement and exact-alarm
permission grant. Re-scheduling the same generation replaces the same Android
alarm. Old-generation broadcasts are ignored even if cancellation was interrupted.
Recovery also removes active Android notifications whose note/generation is no
longer current. A cancelled alarm left by a crash can wake the receiver once, but
cannot deliver a notification for that obsolete generation.

A durable CLAIMED state precedes notification posting; DELIVERED follows a successful
Android notify call, which does not prove a user saw it. If the process crashes
in that gap, recovery records MISSED and cancels any remaining notification rather
than alerting again. Exactly-once delivery across Room/Android is impossible:
this favors avoiding repeated alerts over retrying an ambiguous crash. Rejected
or failed posting remains BLOCKED/FAILED for recovery. No private note text is
logged or displayed in notifications, including lock-screen public versions.

## Recovery and Android limits

- Exact `setExactAndAllowWhileIdle` is used when authorized. Otherwise
  `setAndAllowWhileIdle` is used and the UI warns of delay. SecurityException falls
  back as well; fallback failure is visible as scheduling FAILED.
- POST_NOTIFICATIONS is requested in the reminder setup context on Android 13+.
  App notification permission AND channel importance are checked before posting.
  The UI links to notification/channel settings and optional exact alarm access.
- When due and allowed, recovery sends up to **three** delayed reminders from the
  last **24 hours**, newest first, once. Older/excess due reminders become MISSED
  and remain manageable in their notes. BLOCKED reminders can be delivered once
  if permission returns within that window. DONE/CANCELLED never revive.
- Android revoking exact-alarm access kills the app and removes exact alarms.
  BeeMind can re-establish fallback on its next allowed recovery event/open;
  it cannot run immediately while Android has stopped it.
- Closing the Activity or ordinary process termination does not remove scheduled
  alarms. Boot recovery occurs after unlock for credential-protected Room storage.
  This implementation does not read private data before first unlock.
- Force Stop prevents alarms/broadcast delivery until Android allows the app to
  run again, normally after the user opens it. No bypass is promised. OEM battery
  policies and Doze quotas can delay alarms, including successive short alarms.
  Turning off a phone cannot deliver notifications; due items use the policy above.
- Receiver work is asynchronous and bounded to eight seconds. Very large queues,
  interrupted work or storage errors may require the next app open to reconcile.
  No claim of absolute timing, exactly-once delivery, or guaranteed background
  execution under Android/OEM restrictions is made.

Official Android documentation consulted:
[Alarms](https://developer.android.com/develop/background-work/services/alarms),
[Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## اختبار الهاتف — بدون حاسوب أو ADB

القبول لم يُنفّذ على هاتف المالك بعد. نفّذ هذه القائمة وأرسل أرقام الاختبارات
الناجحة/الفاشلة، مع إصدار Android وطراز الهاتف عند وجود مشكلة.

1. أنشئ ملاحظة ← «تذكير» ← «5 د». راجع الموعد النهائي، اعتمده ثم احفظ
   الملاحظة. اسمح بالإشعارات. افتح «إتاحة التنبيهات الدقيقة» واسمح بها إذا رغبت.
2. أنشئ ملاحظة ثانية بموعد «غدًا» أو تاريخ آخر ووقت محدد. راجع الوقت المحفوظ.
   جرّب وقتًا ماضيًا وتأكد من رفضه؛ جرّب +5 ودقائق مخصصة أيضًا.
3. لملاحظة اختبار جديدة اختر موعدًا دون حفظ. اخرج للشاشة الرئيسية للهاتف، ثم
   استعد المسودة من BeeMind: تظهر الإعدادات ولا يوجد إشعار للمسودة وحدها.
   وفي ملاحظة محفوظة غيّر الموعد ثم تجاهل التعديلات: الموعد السابق يظل ساريًا.
4. أغلق واجهة BeeMind أو أزلها من التطبيقات الأخيرة، وانتظر التذكير الأول.
   يجب وصول إشعار واحد. الضغط على جسمه يفتح الملاحظة الصحيحة.
5. لتذكير آخر، وسّع الإشعار واضغط «إنجاز». افتح الملاحظة: تبقى كاملة وتظهر
   حالة الإنجاز؛ لا يصدر إشعار جديد لذلك الموعد.
6. اضغط «تأجيل 10 دقائق» عند وصول إشعار. يجب اختفاء الحالي وظهور موعد جديد
   بعد عشر دقائق من الضغط. كرر التأجيل مرتين إضافيتين دون تكرار متزامن.
7. اضبط تذكيرًا، ثم عدّل موعده واحفظه قبل حلوله. انتظر الموعدين: لا يصل تنبيه
   الموعد القديم، ويصل تنبيه الموعد الجديد مرة واحدة.
8. اختبر «إزالة التذكير» داخل التفاصيل، واختبر حذف ملاحظة أخرى قبل الموعد:
   لا يصل أي تنبيه قديم. تأكد أن إزالة السمة لا تحذف النص أو Glow/Anger.
9. أنشئ عدة ملاحظات واضغط الحفظ بسرعة متكررة؛ تأكد من وجود ملاحظة واحدة
   وتذكير واحد لكل ملاحظة ومن عدم تكرار الإشعارات عند فتح التطبيق مجددًا.
10. اضبط موعدًا مستقبليًا، أعد تشغيل الهاتف، افتح قفله مرة، ثم اترك BeeMind
    مغلقًا وانتظر الموعد. لا يلزم فتح التطبيق بعد الإقلاع العادي.
11. إعدادات Android ← التطبيقات ← BeeMind ← الإشعارات: عطّلها (ثم جرّب
    تعطيل قناة التذكيرات وحدها). افتح الملاحظة وتحقق من التحذير؛ بعد الموعد
    تظهر الحالة المستحقة. أعد السماح وافتح التطبيق: تنبيه متأخر واحد ضمن السياسة.
12. إعدادات Android ← وصول خاص للتطبيقات ← التنبيهات والتذكيرات ← BeeMind:
    عطّل الدقة، ثم افتح التطبيق. يجب ظهور شرح التأخير، ويُستخدم الموعد التقريبي؛
    لا تعتبر التأخير فشلًا في الدقة المضمونة لأنها غير متاحة هنا.
13. راجع ملاحظاتك السابقة وGlow وAnger والنص العربي الطويل والمسودات، وأعد
    فتح التطبيق. يجب ثبات تاريخ الإنشاء وترتيب Masonry والنصوص والسمات.
14. إن أمكن: اقفل الشاشة وانتظر إشعارًا، وتحقق من خصوصية المحتوى وفق إعدادات
    شاشة القفل. كرر مع وضع توفير البطارية وسجّل أي تأخير. جرّب إطفاء الهاتف
    عبر الموعد ثم تشغيله: لا يتكرر التنبيه المتأخر مع كل فتح للتطبيق.

يمكن اختبار Force Stop اختياريًا من إعدادات التطبيق: لا يُتوقع تنبيه أثناء
الإيقاف القسري؛ افتح BeeMind بعدها ليستعيد حالته. هذا يختلف عن إغلاق الواجهة.

التوقيع الثابت مؤجل. APK اختبار مؤقت؛ التحديث فوق نسخة مختلفة التوقيع غير
مضمون. لا تحذف التطبيق لحل مشكلة التثبيت دون الانتباه إلى أن ذلك قد يحذف
جميع بياناتك المحلية. لا يوجد تصدير بيانات في هذه المرحلة.

## Local validation — 10 November 2026

With JDK 17 / pinned Android SDK 35:
`./gradlew --no-daemon --continue :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
passed: **52 tests, zero failures/errors/skips**, lint “No issues found”. Existing
privacy tests now assert the exact three-permission allowlist rather than zero
permissions. Existing Compose save tests wait for the completed details navigation
before reading Room, so Robolectric's paused main looper can finish asynchronous
save continuations; all prior content, trait and timestamp assertions remain.

Coverage includes real schema 1/2/3 upgrades to 4 with synthetic existing Arabic
notes/drafts, disk reopen, Room flow ordering, reminder-only draft recovery,
failed-save retention, concurrent actions, stale generations, three repeated
snoozes, delivery denial/catch-up limits/crash claims, Android exact/inexact alarms,
real receiver broadcasts in Robolectric, channel/privacy/intent identity, Compose
save/recreation/Done and small-screen date/time dialogs. Native screenshots were
reviewed. Local APK signature and package/version/permissions were inspected.

`adb devices -l` showed no devices; no emulator/system image or KVM was available.
Connected instrumentation, physical notification timing, real reboot/Doze/Force
Stop behavior, TalkBack/keyboard, and installation over the owner's signed APK
were **not performed**. GitHub workflow/artifact evidence is reported separately
in the PR and delivery report; a local build alone is not CI or phone acceptance.


## Critical reliability investigation — owner phone rejection

Build 13 is **not accepted**. Owner reports relative/calendar background delivery
and Snooze failed, while notification opening and Done worked. Do not infer phone
success from the earlier 52 tests.

Review confirmed explicit immutable PendingIntents, manifest receivers and saved
Room generations were present. Alarm delivery is implemented independently of
Activity/ViewModel. Existing tests manually sent broadcasts; they did not establish
native AlarmManager delivery with the process absent. Also, foreground recovery
could post overdue items with the same generic delivered label, obscuring whether
the alarm receiver ever ran. No device log or exact OEM/permission configuration
has yet established a unique cause for the owner's failure.

The repair adds foreground-priority alarm/action broadcasts, a short partial CPU
wake lease acquired before asynchronous dispatch (10-second hard bound, released
on success/error), and explicit Android background-restriction detection separately
from notification/exact access. The lease is especially relevant to notification
actions, which do not inherit AlarmManager's wake lock. Stock Android's pending
alarm broadcast already has a system wake lock: absence of an additional app lock
alone is **not claimed as a proven explanation for every original alarm failure**.
There is no service, unrestricted battery exemption or persistent wake lock.

Local Room operational metadata records current-generation ALARM versus RECOVERY
delivery and Snooze scheduling mode. It contains only a UUID, event and time;
no note text or analytics. Recovery-only delivery is visibly identified, rather
than being presented as proof of background success. Receiver logs preserve the
exception type without private text. This uses the existing metadata table, so
Room remains version 4 and all migrations/notes/traits/creation order are unchanged.

A new disposable-emulator harness targets API 29 and 34: seed synthetic reminders,
initially schedule them, close the Activity, kill the ordinary process (not Force
Stop), turn off the screen, inspect persisted delivery without opening the app,
press real notification actions, and wait an actual ten minutes for Snooze in
forced Doze. It keeps verification outside Instrumentation because Android
force-stops the target when Instrumentation finishes. Reports include alarms,
notification state and logs from synthetic data only. Harness failures are not
counted as successful app delivery; final run results are recorded in the PR.

### إعادة الاختبار على الهاتف — ثلاث خطوات

1. فعّل إشعارات BeeMind، وأتح التنبيه الدقيق إن كان Android يعرض إذنه. أنشئ
   تذكيرًا بعد 5 دقائق وآخر بتاريخ اليوم ووقت قريب محدد، احفظهما ثم اذهب للشاشة
   الرئيسية واقفل الهاتف دون «فرض الإيقاف». سجّل وصول كل إشعار قبل فتح BeeMind.
2. من الإشعار اضغط «تأجيل 10 دقائق»، واترك BeeMind مغلقًا حتى يصل التنبيه
   الجديد. اضغط «إنجاز»، ثم افتح الملاحظة وتأكد أنها باقية وحالتها منجزة.
3. جرّب تعديل موعد وإزالة تذكير قبل حلوله؛ راقب غياب التنبيهات القديمة والمكررة
   وسلامة Glow/Anger والمسودات. عند أي إخفاق، افتح التفاصيل وأرسل عبارة مصدر
   التسليم/القيود الظاهرة مع طراز الهاتف وإصدار Android والأوقات الفعلية.

Signing is still temporary and update compatibility is unverified. Do not
uninstall an existing installation containing important local notes to bypass a
signature mismatch. Phone acceptance remains pending, PR #2 must stay unmerged.
