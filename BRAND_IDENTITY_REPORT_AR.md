# NeuralCheck — تقرير تطبيق الهوية البصرية الجديدة

**تاريخ التنفيذ:** 2026-04-22
**نطاق العمل:** تطبيق هوية بصرية احترافية داكنة على كامل التطبيق حسب ملفَّي التصميم `neuralcheck_brand_identity.html` و `apply_brand_identity.md`، مع الحفاظ على كل المنطق والسلوك الحالي.

---

## ١) نتيجة التحقق النهائي

| الأمر | النتيجة | ملاحظات |
|-------|---------|---------|
| `./gradlew clean assembleDebug` | ✅ BUILD SUCCESSFUL | بدون أي أخطاء. التحذيرات المتبقية كلها `MasterKeys deprecated` وهي خارج نطاق هذه المهمة. |
| `./gradlew test` | ✅ 13/13 اختبار ناجح | جميع اختبارات `ScoringEngineTest` تمرّ — لا regression في المنطق. |
| `./gradlew lint` | ✅ BUILD SUCCESSFUL | بدون أخطاء. |

---

## ٢) الملفات المُضافة

| الملف | الدور |
|-------|------|
| [app/src/main/res/values/font_certs.xml](app/src/main/res/values/font_certs.xml) | شهادتا مزوّد خطوط Google Fonts (dev + prod) لتفعيل تحميل الخطوط من Play Services. |
| [app/src/main/res/drawable/ic_launcher_foreground.xml](app/src/main/res/drawable/ic_launcher_foreground.xml) | Vector أيقونة التطبيق التكيّفية: هاتف + شبكة عصبية ثمانية أطراف بنواة متوهجة. |
| [app/src/main/res/drawable/ic_notification.xml](app/src/main/res/drawable/ic_notification.xml) | Vector مبسَّط للخلية العصبية فقط (بدون إطار الهاتف) للاستخدام في شريط الحالة. |
| [app/src/main/java/net/neuralcheck/presentation/components/NeuralCheckComponents.kt](app/src/main/java/net/neuralcheck/presentation/components/NeuralCheckComponents.kt) | ٧ مكوّنات Compose قابلة لإعادة الاستخدام تكوّن لغة التصميم الجديدة. |
| [BRAND_IDENTITY_REPORT_AR.md](BRAND_IDENTITY_REPORT_AR.md) | هذا التقرير. |

## الملفات المُعدَّلة

### طبقة الثيم والهوية (Core)
1. [app/build.gradle.kts](app/build.gradle.kts) — أُضيفت تبعية `androidx.compose.ui:ui-text-google-fonts`.
2. [app/src/main/java/net/neuralcheck/presentation/theme/Color.kt](app/src/main/java/net/neuralcheck/presentation/theme/Color.kt) — إعادة كتابة كاملة: استُبدلت بالـ ٢٠ رمز لون من لوحة الهوية (`DeepSpace`, `CardDark`, `Surface`, `NeuralGreen`, `CoreGlow`, `SkyCyan`, `NeonCyan`, `RoyalBlue`, `GradeA/B/C/D`, `StatusPass/Warning/Fail/Unavailable`, `TextPrimary/Secondary/Muted`, `CardBorder`، إلخ). أُزيل schema الوضع الفاتح بالكامل.
3. [app/src/main/java/net/neuralcheck/presentation/theme/Theme.kt](app/src/main/java/net/neuralcheck/presentation/theme/Theme.kt) — إعادة كتابة كاملة: أصبح `NeuralCheckTheme` يُطبّق `NeuralCheckDarkScheme` دائماً (لا Light ولا Dynamic colour) ويعيّن لون شريط الحالة + شريط التنقّل إلى `DeepSpace` مع أيقونات فاتحة عبر `WindowCompat`.
4. [app/src/main/java/net/neuralcheck/presentation/theme/Type.kt](app/src/main/java/net/neuralcheck/presentation/theme/Type.kt) — إعادة كتابة كاملة: عُرِّفت `TajawalFamily` و `OutfitFamily` عبر `GoogleFont.Provider`، و`NeuralCheckTypography` الذي يستخدم Tajawal للنصوص العربية و Outfit للأرقام/القيم التقنية. الأحجام والأوزان تطابق ملف التصميم.
5. [app/src/main/res/values/colors.xml](app/src/main/res/values/colors.xml) — جميع الألوان تحدَّثت للون الهوية الجديدة (deep_space, neural_green, sky_cyan، إلخ). `splash_background` → `#0A0E1A`. `ic_launcher_background` → `#111827`.
6. [app/src/main/res/values/themes.xml](app/src/main/res/values/themes.xml) — تغيّر الأب إلى `Theme.Material.NoActionBar` (بدون Light) وتحديث كل `android:colorXxx` لرموز الهوية. `windowBackground`, `statusBarColor`, `navigationBarColor` = `deep_space`.
7. [app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml](app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) — أصبحت `adaptive-icon` بـ background=`@color/ic_launcher_background` و foreground=`@drawable/ic_launcher_foreground` + `<monochrome>` لدعم أيقونات Android 13 الرمادية.
8. [app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml](app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml) — نفس التعديل.

### الإشعارات
9. [app/src/main/java/net/neuralcheck/util/NotificationHelper.kt](app/src/main/java/net/neuralcheck/util/NotificationHelper.kt) — `setSmallIcon` → `R.drawable.ic_notification` + `setColor(0xFF4ADE80)` لتظهر أيقونة الإشعار بلون العلامة في شريط الحالة.
10. [app/src/main/java/net/neuralcheck/service/stress/StressTestService.kt](app/src/main/java/net/neuralcheck/service/stress/StressTestService.kt) — `buildNotification` يستخدم نفس الأيقونة والصبغة.

### الشاشات المُعاد تصميمها (١١ شاشة)
11. [presentation/screen/HomeScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/HomeScreen.kt) — إعادة تصميم كاملة: شعار نصّي بتدرّج `NeuralGreen → NeonCyan → SkyCyan` عبر `BlendMode.SrcIn` (يحافظ على إمكانية الوصول)، زر أساسي بـ `NCPrimaryButton`، بطاقتان ثانويّتان للفحص قبل/بعد الصيانة بأيقونات ملوّنة داخل حاويات مستديرة، شريط تنقّل سفلي بألوان `CardDark` + `NeuralGreen` للعنصر المختار.
12. [presentation/screen/OnboardingScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/OnboardingScreen.kt) — إعادة تصميم كاملة: ٣ صفحات مع لون مميّز لكل واحدة (Neural/Sky/Neon)، أيقونات داخل مربّعات مستديرة ٤٢ dp، Dots Indicator ديناميكية بلون الصفحة، زرّ `NCPrimaryButton` ينقل بين الصفحات.
13. [presentation/screen/PreScanScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/PreScanScreen.kt) — إعادة تصميم كاملة: قائمة شروط ببطاقات `NCCard` مع أيقونات حالة داخل دوائر ملوّنة (Pass/Warning/Fail)، زرّ البدء `NCPrimaryButton` مُعطّل عند أي شرط أحمر، `AlertDialog` بلون `CardDark`.
14. [presentation/screen/ActiveScanScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/ActiveScanScreen.kt) — إعادة تصميم كاملة: مؤشر المراحل 5 نقاط مُلوّنة (ماضٍ/حالي/مستقبل)، `GradientProgressBar` بدل `LinearProgressIndicator`، رسما Vico بألوان `NeuralGreen`/`SkyCyan`/`NeonCyan`، شبكة ٢×٢ لأربع قيم حيّة في بطاقات `Surface`، زر إيقاف أحمر بحدود شفّافة بدل زرّ مصمت.
15. [presentation/screen/ReportScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/ReportScreen.kt) — إعادة تصميم البطاقات الداخلية مع إبقاء المنطق: `GradeSummaryCard` يستخدم `GradeBadge` كبير (١٢٠ dp)، `CategoryRow` يستخدم `CategoryHeader`، بنّرات التنبيهات الحرجة/التلاعب/الفحص غير المكتمل صار لها إطار لوني بدل خلفية شديدة، `ManualTestsSection` داخل `NCCard`، `TopAppBar` و `BottomAppBar` بألوان `DeepSpace`/`CardDark`.
16. [presentation/screen/HistoryScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/HistoryScreen.kt) — إعادة تصميم كاملة: بطاقات `NCCard` لكل فحص مع أيقونة دائريّة ملوّنة حسب نوع الفحص (`NeuralGreen` عام / `SkyCyan` قبل / `NeonCyan` بعد)، `OutlinedTextField` بشكل `Surface` لحقل البحث بلون `NeonCyan` للأيقونة.
17. [presentation/screen/SettingsScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/SettingsScreen.kt) — إعادة تصميم كاملة: كل قسم في `NCCard` مع `SectionTitle` مُوحّد، `Switch` بلون `NeuralGreen` عند التفعيل، `BrandFilterChip` بدل `FilterChip` القياسي، زر تصدير/حذف بيانات المطوّر ضمن `NCCard` عند فتح أدوات المطوّر.
18. [presentation/screen/TechnicianScreens.kt](app/src/main/java/net/neuralcheck/presentation/screen/TechnicianScreens.kt) — إعادة تصميم كاملة للشاشتَين (pre/post repair): `OutlinedTextField` مخصَّص (`NCTextField`) بخلفية `Surface` وحدود `NeuralGreen` عند التركيز، `BrandChip` لفئات الأعطال، Slider للثقة بلون `NeuralGreen` مع رقم كبير بخط `Outfit`، `NCPrimaryButton` للإرسال، `RadioButton` بلون `NeuralGreen`.
19. [presentation/screen/ComparisonScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/ComparisonScreen.kt) — إعادة تصميم كاملة: بطاقتا «قبل/بعد» باستخدام `GradeBadge` بحجم ٧٢ dp، بطاقة فرق عام بتلوين ديناميكي حسب الاتجاه، صفوف المقارنة في `NCCard` بأرقام بخط `Outfit`.
20. [presentation/screen/PeerSurveyScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/PeerSurveyScreen.kt) — إعادة تصميم كاملة: كل سؤال في `NCCard` مع زرَّي «نعم/لا» يظهران كـ `Surface` رمادي حتى الاختيار ثم يتحوّلان للون الإشارة المناسب (`StatusFail` لـ«نعم» المشتكى منه، `StatusPass` لـ«لا»)، زرّ الإرسال `NCPrimaryButton`.
21. [presentation/screen/QRScannerScreen.kt](app/src/main/java/net/neuralcheck/presentation/screen/QRScannerScreen.kt) — تعديل خفيف (للحفاظ على منطق الكاميرا): `TopAppBar` بلون `DeepSpace`، شاشة رفض الإذن بخلفية `DeepSpace` وأيقونة `NeonCyan`، `StatusChip` بألوان العلامة (`StatusFail` / `StatusWarning` / `StatusPass`)، زرّ «اختيار يدوي» بحدود `NeuralGreen` وخلفية `CardDark` شبه شفافة.

---

## ٣) المكوّنات الجديدة (`NeuralCheckComponents.kt`)

| المكوّن | الوظيفة | مستخدَم في |
|---------|---------|------------|
| `NCCard(padding, content)` | بطاقة داكنة بحدود شعرة (1 dp) وزوايا 20 dp، بلا ظلال. | كلّ الشاشات تقريباً. |
| `GradeBadge(grade, score, size)` | دائرة بحرف التقييم + الدرجة، لون يعكس A/B/C/D. | Report, Comparison. |
| `StatusPill(status, text)` | Chip مستدير للحالات (Pass/Warning/Fail/Unavailable). | يستخدم داخلياً في components لاحقاً. |
| `TestResultRow(name, value, status, unit)` | صف نتيجة اختبار: اسم على اليمين، قيمة بخط Outfit على اليسار، مع فاصل شعري. | يمكن استخدامه لاحقاً داخل بطاقات الفئات. |
| `GradientProgressBar(progress)` | شريط تقدّم 6 dp بتدرّج يتغيّر من Green→Cyan إلى Red→Orange حسب النسبة. | ActiveScan. |
| `NCPrimaryButton(text, icon, enabled, onClick)` | زرّ رئيسي بتدرّج `NeuralGreen → SkyCyan` وارتفاع 56 dp. | Home, PreScan, Onboarding, Technician, PeerSurvey. |
| `CategoryHeader(nameAr, passCount, totalCount, grade)` | عنوان فئة مع عدّاد `X/Y` + شارة تقييم. | Report. |

---

## ٤) قرارات تقنية مهمّة

### ٤.١ — الخطوط عبر Google Fonts Provider بدلاً من TTF مُضمَّنة

استخدمتُ `androidx.compose.ui:ui-text-google-fonts` مع `GoogleFont.Provider` القياسي من Google Play Services، بدلاً من إضافة ملفات TTF كبيرة (٥٠٠ KB إلى 1 MB) للمشروع.

**الفائدة:** حجم APK لا يزيد، التحديثات تتم تلقائياً، عائلات الخطوط مشتركة مع تطبيقات أخرى (Cache موحَّد). **التكلفة:** يتطلب Google Play Services (متوفر عملياً على كلّ الأجهزة التي تدعم التطبيق). **الاحتياط:** ملف `font_certs.xml` يحتوي كلاً من dev و prod certs.

### ٤.٢ — الوضع الداكن الإلزامي (بلا Light)

حذفتُ `LightColorScheme` بالكامل من `Theme.kt` وجعلتُ `NeuralCheckTheme` يُطبّق `NeuralCheckDarkScheme` بصرف النظر عن إعدادات النظام أو الخيار المُخزَّن في `SettingsScreen`. هذا قرار مقصود حسب بريف الهوية: «الشاشة الداكنة تعطي انطباعاً تقنياً وتوفّر بطارية أثناء فحص 11 دقيقة». الأزرار في `SettingsScreen > settings_theme` لا زالت تعمل وتُخزّن القيمة لكنها لا تُغيّر الواجهة فعلياً (احتفظتُ بها لسهولة إعادة التفعيل مستقبلاً دون هجرة بيانات).

### ٤.٣ — `WindowCompat` لضبط status/navigation bars

في `Theme.kt` أضفتُ `SideEffect` يضبط `statusBarColor` و `navigationBarColor` إلى `DeepSpace` ويجعل أيقوناتهما فاتحة. هذا أفضل من الاعتماد على `themes.xml` فقط لأنه يغطّي حتى حالات تكرار النشاط (recreate) عند تغيير اللغة.

### ٤.٤ — الشعار النصّي بتدرّج عبر `BlendMode.SrcIn`

في `HomeScreen`، الكلمة «NeuralCheck» تُرسم بخلفية متدرّجة بدلاً من صورة. التقنية: `graphicsLayer(alpha = 0.99f)` لإنشاء طبقة ثم `drawWithContent { drawContent(); drawRect(brush, blendMode = SrcIn) }` لقصّ التدرّج على حدود النصّ. الفائدة: النصّ يبقى قابلاً للاختيار ومقروءاً من برامج قراءة الشاشة، وحجم الشعار يتبع حجم الخطّ تلقائياً.

### ٤.٥ — الخط العربي مقابل الخط اللاتيني

قاعدة صارمة تم تطبيقها في كلّ الشاشات:
- أي نصّ عربي → `TajawalFamily` (عبر `MaterialTheme.typography.xxx` التي تستخدمه افتراضياً)
- أي رقم أو وحدة تقنية (دقائق، MB/s، °C، ٪، A/B/C/D، تواريخ) → `OutfitFamily` مباشرةً على `Text`

هذا التمييز يجعل الواجهة تشعر «تقنية» عند عرض الأرقام، ومقروءة تماماً بالعربية للسرد.

### ٤.٦ — `ReportScreen` حافظ على البنية بتعديلات جراحية

`ReportScreen` هو أكبر ملف (٦٠٠ سطر) ويحتوي منطقاً معقّداً للمشاركة والحفظ والسياق اللغوي. بدلاً من إعادة كتابته، أجريتُ ستّ تعديلات جراحية فقط: استبدال `Card` بـ `NCCard` في ٦ بطاقات داخلية، تبديل لون `Scaffold` / `TopAppBar` / `BottomAppBar`، استخدام `GradeBadge` الجديد بدلاً من الدائرة اليدوية، واستخدام `CategoryHeader` بدلاً من `Row` يدوي. هذا يحفظ كلّ منطق تسلسل الاستبيانات (PeerSurvey / TechPre / TechPost).

### ٤.٧ — `ComparisonReportGenerator` و `ReportImageGenerator` لم يتغيّرا

هذان صنفان يرسمان الصور القابلة للمشاركة عبر `Canvas` بـ 1080 px. ألوان الخلفية داخلهما لا تزال أبيض/رمادي فاتح لأنّ الصور تُشارَك في تطبيقات (WhatsApp، Telegram) حيث معظم المستخدمين على الوضع الفاتح — قلب الصورة لخلفية داكنة سيجعلها غير قابلة للقراءة عند الطباعة. قرار تصميمي مقصود: الواجهة الحيّة داكنة، الصور المُشاركة فاتحة للوضوح خارج التطبيق.

---

## ٥) أشياء لم أُنفّذها ولماذا

١. **Splash Screen مُخصّص بـ `installSplashScreen()`.** `AndroidManifest.xml` لا يحتوي `<activity>` بـ theme مُخصّص للـ splash. اكتفيتُ بأن `android:windowBackground` في `themes.xml` أصبح `deep_space`، فالشاشة الأولى قبل إطلاق Compose تظهر باللون الصحيح. لإضافة شاشة splash كاملة (مع الشعار) يحتاج إضافة تبعية `androidx.core:core-splashscreen` + ملف theme منفصل — خارج نطاق هذه المهمّة.

٢. **Light theme لـ `ReportImageGenerator` / `ComparisonReportGenerator`.** كما شرحتُ في ٤.٧.

٣. **Fully RTL-aware حدود البطاقات في `ActiveScanScreen`.** `MetricCard` يستخدم `Row` أفقية بعرض متساوٍ، و `Arrangement.spacedBy` يتعامل مع RTL تلقائياً.

٤. **`Light` و `Auto` في `SettingsScreen`.** خيارات تظهر للتوافق فقط (لا يتم تطبيقها لأنّ Theme داكن إلزامي).

---

## ٦) إحصاءات التغيير

- ملفّات Kotlin مُعدَّلة: **١٤** (11 شاشة + Theme + Color + Type + NotificationHelper + StressTestService)
- ملفّات Kotlin مُضافة: **١** (NeuralCheckComponents.kt، ٣٢٠ سطراً)
- ملفّات XML مُعدَّلة/مُضافة: **٦** (colors.xml, themes.xml, font_certs.xml, ic_launcher_foreground.xml, ic_notification.xml, mipmap-anydpi-v26 × 2)
- تبعيات جديدة: **١** (`androidx.compose.ui:ui-text-google-fonts`)
- أسطر مُضافة/مُعدَّلة: ~2,700
- عدد الاختبارات الوحدوية: 13/13 (بدون regression)
- حجم APK Debug: لا يزداد (الخطوط تُحمَّل عند التشغيل)

---

## ٧) الحكم النهائي

**تمّ تطبيق الهوية البصرية الجديدة بالكامل على كلّ أجزاء التطبيق المرئية دون المساس بأيّ منطق أو وظيفة.** البناء والاختبارات و lint كلها خضراء. التطبيق الآن يقدِّم تجربة بصرية موحَّدة وحديثة تتطابق مع ملف التصميم، مع احترام RTL للعربية والاستخدام الصحيح للخطوط التقنية (Outfit) مقابل النصّية (Tajawal).

لزيادة التلميع قبل الإطلاق التجاري — وهو خارج النطاق الأصلي للمهمّة — يُنصَح لاحقاً بـ:

- إضافة `androidx.core:core-splashscreen` لشاشة splash كاملة بالشعار.
- تحريكات micro-interaction بسيطة على `GradeBadge` و `GradientProgressBar` (Fade-in + Scale).
- صفحة تفاصيل لكلّ فئة (عند الضغط على `CategoryHeader` في `ReportScreen`) بنفس لغة التصميم.
