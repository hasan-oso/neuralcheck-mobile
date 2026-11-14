# NeuralCheck — محلل الأجهزة الذكي

## نظرة عامة
تطبيق أندرويد أصلي (Kotlin) للفحص الشامل للهواتف المستعملة.
85 اختباراً آلياً + 19 يدوياً + فحص ضغط 8 دقائق + تقارير تفصيلية مع تقييم ذكي.

## ميزات التطبيق
- **85 اختباراً آلياً + 19 اختباراً يدوياً** عبر 13 فئة (بطارية، معالج، رسومات، تخزين، حرارة، ذاكرة، شبكة، GPS، مستشعرات، كاميرا، شاشة، أمان)
- **فحص ضغط 8 مراحل** (8 دقائق: تهيئة → تخزين → ذاكرة → معالج → رسومات → مدمج → انفجارات → تبريد)
- **نظام تقييم 4 طبقات** (فردي → فئة → محور → إجمالي = A/B/C/D)
- **كشف مؤشرات التلاعب** (حرارة منخفضة، إعادة تشغيل حديثة، فصل الشاحن)
- **3 أنماط فحص** (عام، قبل الإصلاح، بعد الإصلاح مع مقارنة)
- **تقارير قابلة للمشاركة** مع رمز QR
- **مزامنة سحابية** تلقائية عبر Firebase

## البناء
```bash
./gradlew assembleDebug
```

### Java: الطريقة المؤقّتة لكل أمر
متغيّر `JAVA_HOME` في البيئة قد يشير إلى JDK محذوف، فلا يُعتمد عليه. والمعتمد (قرار المستخدم، 2026-10-06 و2026-10-08)
أن يُشغَّل Gradle بـJava المرفقة مع Android Studio (jbr 21) **داخل الأمر وحده، دون تغيير البيئة**:
لا `setx`، ولا تعديل لـ`JAVA_HOME` ولا `GRADLE_USER_HOME` في إعدادات الجهاز. السطر (Git Bash؛ القوس الفرعي
يُبقي مجلّد الجلسة كما هو، والتعيين يخصّ هذا الأمر وحده):

```bash
( cd <مجلّد المستودع> && export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" && export PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH" && ./gradlew --no-daemon <المهمّة> )
```

المسار هو مسار تثبيت Android Studio المعتاد؛ وإن ثُبّت في موضع آخر فغيّره في السطر نفسه لا في البيئة.

## تشغيل الاختبارات
```bash
./gradlew test
```

اختبارات الوحدة (JVM) مع تنظيف نتائجها السابقة، بالسطر أعلاه:
`cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest`. وبلا التنظيف قد يخدم Gradle نتائج مخزَّنة لا تُشغَّل من جديد.

### المكتبة الأصلية (C++) وحدها، بلا APK
للتحقّق من أنّ كود C++ يُترجَم، بالسطر أعلاه:
`:app:externalNativeBuildLaunchDebug` — يبني `libstress.so` لـarm64-v8a فقط، ولا يُنتج APK. اختبارات JVM لا تُشغّل
كود C++: ما يمرّ بـJNI لا يُفحص فعلياً إلا على جهاز.

## الهيكلية
MVVM + Clean Architecture (3 طبقات)

```
├── presentation/   ← Jetpack Compose + Material Design 3
│   ├── screen/     ← 11 شاشة (Home, ActiveScan, Report, Comparison, ...)
│   ├── viewmodel/  ← 9 نماذج عرض Hilt
│   ├── navigation/ ← NavHost مع 11 مسار
│   └── theme/      ← M3 + RTL + Dark/Light
│
├── domain/         ← Use Cases + ScoringEngine
│   ├── model/      ← ScanReport, TestResult, AntiTamperFlag
│   ├── scoring/    ← RuleBasedScoringEngine (4 layers)
│   └── usecase/    ← ExecuteStressTest, GenerateReport, Compare
│
├── data/           ← Room DB + GZIP JSON time-series
│   ├── local/      ← Database, DAOs, Entities, Prefs
│   └── repository/ ← ScanRepo, TestResultRepo, ScoreRepo, ...
│
├── hardware/       ← 12 test suites (Battery, CPU, GPU, ...)
│   └── runner/     ← FunctionalTestRunner (parallel executor)
│
├── service/        ← Foreground Service + Workers
│   ├── stress/     ← StressTestService (5 phases)
│   ├── collector/  ← SystemFileReader, SensorCollector
│   └── sync/       ← SyncWorker + SyncScheduler
│
├── di/             ← Hilt modules (App, DB, Repo, Engine)
└── util/           ← Report generators, QR, Notifications
```

## التقنيات المستخدمة
| التقنية | الاستخدام |
|---|---|
| Kotlin | اللغة الأساسية |
| Jetpack Compose | واجهة المستخدم |
| Material Design 3 | نظام التصميم |
| Hilt | حقن التبعيات |
| Room | قاعدة البيانات المحلية |
| WorkManager | المزامنة الخلفية |
| Camera2 | اختبارات الكاميرا |
| OpenGL ES 3.0 | اختبار حمل GPU |
| ZXing | رموز QR |
| Firebase | التخزين السحابي |
| Vico | الرسوم البيانية |

## نظام التقييم
4 طبقات من التقييم كما هو محدد في ADR القسم 8:

1. **الطبقة 1**: تقييم فردي لكل اختبار (0-100) مع استيفاء خطي
2. **الطبقة 2**: تقييم الفئة (متوسط موزون للاختبارات)
3. **الطبقة 3**: تقييم المحور (أداء 30%، بطارية 35%، اتصال 20%، أمان 15%)
4. **الطبقة 4**: التقييم الإجمالي (A/B/C/D)

الإعدادات في: `assets/scoring_config.json` (قابلة للتعديل بدون تغيير الكود)

### الدرجات
| الدرجة | النطاق | الوصف |
|---|---|---|
| **A** | 85-100 | ممتاز — لا مشاكل جوهرية |
| **B** | 70-84 | جيد — علامات استخدام طبيعية |
| **C** | 50-69 | مقبول — مشاكل ملحوظة |
| **D** | 0-49 | ضعيف — مشاكل جوهرية |

## اللغات المدعومة
- العربية (افتراضي)
- التركية

## الأمان
- جميع البيانات المحلية مشفرة عبر `EncryptedSharedPreferences`
- حدود أمان حرارية (بطارية > 45°C، معالج > 90°C → إيقاف فوري)
- بصمة جهاز مجهولة — الطبقات المعرِّفة (Firebase Installation ID وANDROID_ID) تُهشَّر (SHA-256) منذ P0.1

## المتطلبات
- Android 8.0 (API 26) أو أحدث
- الأفضل: Android 12+ (API 31+) لميزات SoC المتقدمة

## الترخيص
حقوق النشر محفوظة.
