# neuralcheck-mobile

[English](README.md) · **العربية** · [Türkçe](README.tr.md)

> **حالة المشروع: مؤرشف.** أُوقف تطوير NeuralCheck في نوفمبر 2026 (ضيق الوقت، وتبيّن أنّ جمع البيانات الميدانية المخطَّط له شبه مستحيل). الكود منشور كما هو بترخيص [MIT](LICENSE) بلا ضمان ولا دعم. هذا المستودع **لا يحتوي أسراراً**: إعداد Firebase للعميل متوفّر قوالب `*.example` فقط، ومفتاح توقيع الإصدار لم يكن فيه أصلاً.

تطبيق أندرويد أصلي (Kotlin وJetpack Compose) لمشروع **NeuralCheck**، نظام فحص الهواتف المستعملة. يجري مجموعة كبيرة من الاختبارات الآلية واليدوية مع فحص ضغط مدّته 8 دقائق، ويجمع «بصمة خام» للجهاز ويرسلها إلى الواجهة الخلفية ([neuralcheck-backend](https://github.com/hasan-oso/neuralcheck-backend)) التي تقيّم الهاتف وتعيد تقريراً يُعرض هنا وفي الموقع ([neuralcheck-website](https://github.com/hasan-oso/neuralcheck-website)).

## الميزات

- اختبارات آلية ويدوية في فئات كثيرة (البطارية والمعالج والرسوميات والتخزين والحرارة والذاكرة والحسّاسات والشاشة والصوت والكاميرا والاتصال...).
- فحص ضغط متعدّد المراحل مدّته 8 دقائق بمكتبة أصلية C++ (`libstress.so`، معمارية arm64-v8a فقط).
- مؤشرات كشف التلاعب، وثلاثة أنماط فحص (عام، قبل الإصلاح، بعد الإصلاح مع مقارنة)، ومشاركة التقرير برمز QR.
- نمط المحلّ: يُنشئ صاحب المحلّ رمز جلسة ويفحص هاتف الزبون؛ وينضمّ هاتف الزبون بالرمز (مع شاشة موافقة بالعربية أو التركية).
- واجهة عربية (الافتراضية) وتركية، ودعم RTL، وسمتان داكنة وفاتحة.

## المتطلبات

Android Studio مع JDK 17 أو أحدث (يصلح JBR 21 المرفق)، وAndroid SDK 35، وNDK `27.1.12297006`. القيمة `minSdk` = 28 و`compileSdk`/`targetSdk` = 35. إصدار هذه النسخة: **1.0.15** (versionCode 17).

## الإعداد

1. أنشئ مشروع Firebase خاصاً بك مع تطبيق أندرويد لكل نكهة، ونزّل `google-services.json` الخاص به. المستودع يحوي قوالب فقط: انسخ كل قالب واملأه.

   | النكهة | applicationId | الملفّ الذي تُنشئه (مُتجاهَل في git) |
   |---|---|---|
   | `launch` | `net.neuralcheck` | `app/src/launch/google-services.json` (القالب `.example` بجانبه) |
   | `datacollect` | `net.neuralcheck.datacollect` | `app/src/datacollect/google-services.json` (القالب `.example` بجانبه) |

2. تُوقَّع إصدارات release من ملفّ `keystore.properties` في جذر المستودع (`storeFile` و`storePassword` و`keyAlias` و`keyPassword`؛ انظر `app/build.gradle.kts`). وهو مُتجاهَل في git. وإصدارات debug لا تحتاج مفتاح توقيع.
3. اربط التطبيق بواجهتك الخلفية بنشر [neuralcheck-backend](https://github.com/hasan-oso/neuralcheck-backend) على مشروع Firebase المذكور في الخطوة 1.

## البناء والاختبار

```bash
./gradlew assembleLaunchDebug
./gradlew cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest   # اختبارات JVM (نظّف أولاً وإلا قد يخدم Gradle نتائج مخزَّنة)
```

اختبارات JVM لا تفحص كود C++ ولا ما يعبر JNI؛ ذلك يحتاج جهازاً حقيقياً. والأمر `:app:externalNativeBuildLaunchDebug` يتحقّق فقط أنّ الكود الأصلي يُترجَم.

## الهيكل

```
app/src/main/java/net/neuralcheck/
├── presentation/   شاشات Compose ونماذج العرض (Hilt) والتنقّل والسمات
├── domain/         حالات الاستخدام ومحرّك التقييم ومنطق الموافقة والربط
├── data/           قاعدة Room والمستودعات والخدمات البعيدة (Firebase)
├── hardware/       مجموعات الاختبار (بطارية، معالج، رسوميات، حسّاسات...)
├── service/        خدمة فحص الضغط الأمامية والجامعات والعمّال
└── util/           السجلّات والبصمة والمساعدات
app/src/main/cpp/   مكتبة الضغط الأصلية
app/src/test/       اختبارات JVM وعيّنات ذهبية مشتركة مع الخلفية والموقع
docs/               العقود (FV68 ومواصفة القياس) وملاحظات المعمارية وملاحظات التطوير (بالعربية)
tools/guards/       سكربتات حراسة بـPython
```

## مواد من طرف ثالث

المجلّد `app/src/main/assets/models/` يضمّ ملفّات نماذج MobileNet V3 لـTensorFlow Lite لم ينشئها هذا المشروع؛ راجع ترخيصها الأصلي (Apache-2.0 وقت الكتابة) قبل إعادة توزيعها.

## الترخيص

[MIT](LICENSE).
