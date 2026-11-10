# BeeMind — Stage 0

تطبيق Android أصلي يعمل دون اتصال. هذه المرحلة شاشة افتتاحية فقط؛ لا ملاحظات أو سمات أو أزرار غير منفذة. لا صلاحيات مطلوبة.

## البناء

- JDK 17، Gradle 8.11.1 (Wrapper مع SHA-256)، AGP 8.9.2.
- Kotlin وCompose compiler 2.1.20، KSP 2.1.20-1.0.32، Compose BOM 2025.04.01، Room 2.7.1.
- `minSdk 26`، `compileSdk/targetSdk 35`، SDK Build Tools 35.0.0. AGP 8.9 يدعم API 35 ويتطلب Gradle 8.11.1 وJDK 17؛ إضافة Compose compiler بنفس إصدار Kotlin. كل الإصدارات مثبتة في `gradle/libs.versions.toml`.
- مراجع التوافق: https://developer.android.com/build/releases/past-releases/agp-8-9-0-release-notes و https://developer.android.com/jetpack/androidx/releases/room#2.7.1 و https://kotlinlang.org/docs/compose-compiler-migration-guide.html .

ثبت Android SDK ثم ضع مساره في `local.properties` (غير متتبع):

```properties
sdk.dir=/your/android-sdk
```

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

فحوص اقتراح إصدارات أحدث فقط (`GradleDependency` و`AndroidGradlePluginVersion`) مستثناة من Lint لأن الإصدارات مثبتة وتُحدّث معًا بعد مراجعة التوافق؛ تنبيه `OldTargetApi` معلوماتي لأن Stage 0 يوزّع APK خارج المتجر ويستهدف API 35 المثبت مع الأدوات المختبرة؛ لا تُدّعى مطابقة متطلبات نشر Google Play الحديثة. تبقى فحوص توافق API (`NewApi`) وغيرها فعالة وتُعامل تحذيراتها كأخطاء. ترقية target إلى أحدث منصة تتطلب مراجعة سلوكها واختبارها قبل اعتمادها.

تحتاج أدوات البناء فقط اتصالًا لتنزيل الاعتماديات. التطبيق نفسه بلا إنترنت. الوحدة الاختبارية تحمي عقد الخصوصية وRTL وقواعد النسخ الاحتياطي؛ لا تدعي اختبار الهاتف أو Room أثناء التشغيل.

## البنية والبيانات

وحدة `app` واحدة، `data/local` لقاعدة Room وDAO، `domain` لحد Repository، `data` لتنفيذه، `ui/theme` للتصميم، و`ui/welcome` للشاشة الوحيدة ضمن NavHost. لا ViewModel بلا حالة أو منطق يحتاجه؛ يضاف مع الميزات المعتمدة. حقن الاعتماديات يدوي عبر المنشئ دون إطار إضافي.

Room مهيأ بقاعدة `beemind.db` إصدار 1 وجدول داخلي فارغ `app_metadata` فقط، وفتح كسول دون بيانات أولية. لا جداول ميزات المراحل المقبلة ولا عمليات CRUD للملاحظات. مخطط Room مُصدَّر ومتعقب. يجب إضافة ترحيلات غير مدمرة عند تطوير المخطط؛ لا `fallbackToDestructiveMigration`.

معرف التطبيق ثابت: `com.beemind.personal`. النسخ الاحتياطي السحابي ونقل بيانات التطبيق مستبعدان. حذف التطبيق قد يحذف بياناته المحلية؛ لا أداة تصدير في V1.

## GitHub Actions وAPK

من تبويب **Actions → Android Stage 0 → تشغيل ناجح → Artifacts** حمّل ZIP باسم:
`BeeMind-0.1.0-build-N-persistent-test` أو `BeeMind-0.1.0-build-N-TEMPORARY-NON-UPGRADE-GUARANTEED`.

يتضمن APK وSHA-256 وبصمة شهادة التوقيع. الرفع بعد نجاح التجميع وUnit Tests وLint وفحص التوقيع فقط. مدة الاحتفاظ 30 يومًا. يعمل عند push وpull request وبـ **Run workflow** يدويًا بعد وصول workflow للفرع الافتراضي. تشغيل fork PR لا يحصل على أسرار التوقيع وينتج مؤقتًا. لا تستخدم APK الخاص به كتحديث معتمد.

`versionName=0.1.0` و`versionCode=github.run_number`؛ لا تعِد إنشاء workflow بعداده من الصفر دون ضبط رمز أعلى من آخر نسخة موزعة. إعادة تشغيل run نفسه تعيد رقم البناء ذاته؛ لتوزيع تحديث جديد شغّل Run workflow جديدًا. محليًا الرمز الافتراضي 1 ويمكن تمرير `BEEMIND_VERSION_CODE`.

## إعداد مفتاح اختبار ثابت — على جهازك الموثوق فقط

إذا لم تضف الأسرار الأربعة ينتج APK مؤقت بمفتاح debug خاص ببيئة البناء. **تحديثه لاحقًا فوق النسخة نفسها غير مضمون**، بما في ذلك أول انتقال للمفتاح الثابت. لا تبدأ تخزين بيانات حقيقية عليه. لا تُغيّر المفتاح بعد اعتماد أول نسخة ثابتة.

1. أنشئ مفتاحًا خارج المستودع باستخدام JDK (يسألك عن كلمة مرور؛ لا ترسلها في محادثة):

   ```sh
   keytool -genkeypair -keystore beemind-test.jks -storetype JKS -alias beemind-test -keyalg RSA -keysize 3072 -validity 10000
   ```

   عند طلب كلمة مرور المفتاح، اضغط Enter لاستخدام كلمة مرور المخزن نفسها. احتفظ بالمفتاح وكلمة المرور في نسختين احتياطيتين آمنتين خارج Git. فقدهما قد يمنع تحديث التطبيق مع الاحتفاظ ببياناته.

2. حوّل الملف إلى Base64 (Linux):

   ```sh
   base64 -w 0 beemind-test.jks > beemind-test.base64.txt
   ```

   على macOS: `base64 -i beemind-test.jks | tr -d '\n' > beemind-test.base64.txt`. الملف الناتج سر أيضًا.

3. افتح المستودع → **Settings → Secrets and variables → Actions → New repository secret** وأضف:

   | الاسم | القيمة |
   |---|---|
   | `BEEMIND_KEYSTORE_BASE64` | محتوى ملف Base64 كاملًا |
   | `BEEMIND_STORE_PASSWORD` | كلمة مرور المخزن |
   | `BEEMIND_KEY_ALIAS` | `beemind-test` |
   | `BEEMIND_KEY_PASSWORD` | كلمة مرور المفتاح (نفس كلمة مرور المخزن بالخطوة السابقة) |

4. شغّل **Actions → Android Stage 0 → Run workflow**. يجب أن يحمل Artifact اللاحقة `persistent-test`. تحقق من تطابق بصمة شهادة SHA-256 في ملف certificate مع كل إصدار ثابت. امسح ملف Base64 المؤقت بأمان من جهازك بعد الحفظ. لا ترفع أي مفتاح أو كلمة مرور إلى Git.
5. ثبات التحديث يتطلب المفتاح نفسه ومعرف التطبيق نفسه ورقم بناء أعلى. لا يتوفر إثبات الاحتفاظ بملاحظات Room في Stage 0 لعدم تنفيذها بعد؛ يختبر لاحقًا ضمن المرحلة المناسبة. لا تحذف نسخة تحتوي بيانات لاختبار تحديث.

## اختبار الهاتف — Stage 0 فقط

1. فك ZIP وثبّت APK بعد السماح بالتثبيت من المصدر المستخدم. Android 8 أو أحدث.
2. افتح BeeMind وتحقق من الشاشة العربية والاتجاه RTL والتدرج السلس، ومن عدم وجود أزرار ملاحظات أو سمات.
3. فعّل وضع الطيران، أغلق التطبيق وافتحه: ينبغي أن تظهر الشاشة دون اتصال أو طلب أذونات.
4. كبّر خط النظام وحجم العرض، وجرّب الوضع الأفقي؛ يجب أن تظل النصوص مقروءة ويمكن تمرير المحتوى عند ضيق المساحة. اختبر TalkBack.
5. بعد إعداد المفتاح الثابت، ثبّت أول APK ثابت ثم ابنِ تشغيلًا جديدًا وثبّته فوقه دون حذف التطبيق. قارِن بصمتي الشهادة. اختبار الاحتفاظ ببيانات الملاحظات مؤجل حتى وجودها.

التوقف عند Stage 0 إلزامي حتى اختبار المالك للـAPK والموافقة الصريحة على المرحلة التالية.
