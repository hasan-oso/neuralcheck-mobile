# عيّنة عقد V7 — منقولة، لا مُولَّدة هنا

`v7-client-payload-sample.json` **نُسخ حرفياً** من `neuralcheck-backend`:
`functions/test/fixtures/golden/v7-client-payload-sample.json` @ `77b710a` (2026-09-24 +03،
كانت `154f3f2`، 2026-09-23 +03، وقبلها `43dee66`، 2026-09-21 +03).

وُلِّد هناك بـ`functions/scripts/generate-v7-client-payload-sample.js` عبر السلسلة الحقيقية:
`computeFindings → computeCategoryStates → applyV7Adjustments → buildDisplayShapeV7`
`→ buildV7ResponseForStorage → V7_OPERATIONAL_FIELDS` — **بصفر نداء نموذج**.

**لا يُعدَّل هنا.** هو نسخة من دليل يعيش في المستودع الآخر؛ تعديله هنا يُنتج عقدين.
وإن لزم تحديثه، يُعاد توليده هناك ويُنسخ ثانيةً.

**حالة الالتزام في `neuralcheck-backend` وقت هذا النسخ غير مُتحقَّق منها من هذا
المستودع** (وكيل التطبيق لا يقرأ `neuralcheck-backend` مباشرة — القاعدة 2).
نُسخت هنا من نسخة عمل منقولة (`contract-handoff-77b710a`)، وتحقَّق وكيل
التطبيق بنفسه أن sha256 المحلي (`08eb32001a6830c5...`) يطابق `SHA256SUMS`
المرفق مع تلك الحزمة قبل الاستبدال. المصدر السابق كان `contract-handoff-154f3f2`
(sha256 بدأ بـ`81e1353a745379d0`) — **تُستبدَل حين يُلتزَم توليدها الفعلي في
`neuralcheck-backend`**، ووقتها يُحدَّث هذا الملفّ بسطر الالتزام هناك.

## التحديث 2026-09-23 — `letterCeilingReason` + `socAtWindowStart`/`socAtWindowEnd`

مقارنةً بمسار `43dee66` السابق، مسار بمسار:

- **+29 مسار `letterCeilingReason`** على `categories.<cat>` (15 فئة + الكائن
  المتداخل حين != null) — 7 منها كائن غير فارغ (`gpu`, `display`, `memory`,
  `gps`, `sensors`, `cellular`, `wireless`)، كلّها
  `{"name":"LOW_CATEGORY_COVERAGE","ceilingLetter":"B","text":"..."}` بنصّ
  مختلف لكل فئة (مبنيّ من executedCount/declaredCount الفعليَّين لها، لا
  قالباً ثابتاً — مثال: `gpu.text` يبدأ بـ"تغطية منخفضة"، `display.text` لا
  يبدأ بها رغم نفس `ceilingLetter`). البقية (8 فئات) `letterCeilingReason:
  null` — المفتاح **حاضر**، لا غائب؛ مُميَّز عن عيّنة `llm-adjusted` أدناه
  حيث المفتاح **غائب فعلياً** عن كل فئة (لم تُعَد توليدها بهذا التحديث).
- **+8 مسارات** `categories.battery.indicators.<id>.socAtWindowStart` /
  `socAtWindowEnd` (على المؤشّرات الأربعة) — تقع داخل الكائن المُبهَم
  (OPAQUE) أصلاً حسب `v7ContractFieldMap.js`، فلا تغيير مطلوب في أيّ حارس.
- حرف السبع فئات أعلاه تغيّر (كان `A`، صار `B`) — بوّابة `LOW_CATEGORY_COVERAGE`
  الجديدة تُسقِّفها. **بقيّة الحقول مطابقة حرفياً** لـ`43dee66` — قورنت
  مساراً بمسار (`overall`, `language`, `isFreeTrial`, `aiModel`,
  `deviceMarketingName`, `narrativeIncomplete[Fields]`, `battery.*` عدا
  socAtWindow*، `cpu`/`security`/`storage`/`camera`/`audio`/`input`،
  `issues[].id/evidence/evidenceUnit` — 31 قضية بنفس المعرّفات) — فقط
  `shortId` (عشوائي كل توليد) و`narrative`/`issues[].description`'s نصّها
  اختلفا (نصّ نموذج مُعاد توليده، لا بيانات قياس).
- `shortId` تغيّر: `NC-KB7ETC` ← `NC-UPC82Y` ← `NC-23857C` (الأخير من تحديث
  2026-09-24 أدناه).

## التحديث 2026-09-24 — صياغة `letterCeilingReason.text` فقط

مقارنةً بمسار `154f3f2` السابق (74 سطر diff، مُتحقَّق منه بـ`diff -u` مباشرةً،
لا نقلاً عن وصف)، **مساران فقط** تغيّرا في هذا الملفّ:

- **نصّ `categories.<cat>.letterCeilingReason.text`** لنفس السبع فئات
  المذكورة أعلاه (`gpu`, `display`, `memory`, `gps`, `sensors`, `cellular`,
  `wireless`) — الجزء الأول من كل نصّ (المبنيّ من executedCount/declaredCount)
  بقي كما هو، واستُبدل الجزء الأخير في كلٍّ بالصياغة الموحّدة: «حرف مسقوف: لم
  يُقَس في هذه الفئة أيّ اختبار معتمد قياساً للحالة.» — مصدرها في الخادم
  `functions/src/analysis/buildDisplayShapeV7.js:234` (منقول من نصّ أمر
  المنسّق، غير مُتحقَّق من هذا المستودع). `name`/`ceilingLetter` لكل فئة لم
  يتغيّرا.
- `shortId` تغيّر (عشوائي كل توليد، لا دلالة له).

**لا تغيير آخر** — بقيّة الملفّ (`overall`, `language`, `battery.*`,
`socAtWindowStart`/`socAtWindowEnd`, بقية الفئات الثمانية، `issues[]`,
`narrative`, إلخ) مطابق حرفياً لـ`154f3f2`.

## حدود العيّنة — تُقرأ قبل الاعتماد عليها

| الحقل | القيمة | النوع |
|---|---|---|
| `narrative.buyerAdvice.{casual,gamer,photographer}` | ثلاثة نصوص حقيقية مختلفة (لم تعد `null`) | **✅ أُغلق فجوة `43dee66`** — كانت `null`×3 هناك ("نقص عيّنة"، المصدر أقدم من القرار ٢٤). التوليد الجديد @ `154f3f2` يحمل نصّاً حقيقياً مختلفاً لكل شخصية. لا تُعِد افتراض `null` عند قراءة هذه العيّنة |
| `deviceMarketingName` | `"Unknown"` | **نقص عيّنة** — `fingerprint` بلا `device.model` |
| `narrativeIncomplete` · `narrativeIncompleteFields` | `false` · `[]` | **⚠️ حدّ العيّنة** — `resolveV7Report` لم يُستدعَ، والمصدر الحيّ يحمل `guard.ok=false`. **لا تُعتمَد مرجعاً لسلوكهما تحت رفض الحارس** |
| `battery.confidence` · `battery.letter` · `thermal.letter` · `zeroMeasuredMessage` · `abstentionReasons` · `harnessErrorReason` · `{cpu,thermal,security}.adjustmentReason` | `null` | **حقيقي** — كلٌّ بسببه المُسمّى في `NEURALCHECK-FINDINGS.md` |
| `{cpu,thermal,security,storage,camera,audio,input}.letterCeilingReason` | `null` (المفتاح **حاضر**) | **حقيقي** — لا بوّابة `LOW_CATEGORY_COVERAGE` سقّفت هذه الثمانية في هذا التشغيل |

## `v7-client-payload-sample-llm-adjusted.json` — لم تُعَد توليدها، **بلا** `letterCeilingReason`

~~لم تُلمَس في هذا التحديث — لا تزال من التوليد السابق لهذا التغيير. **كل
فئة فيها (بما فيها `storage`/`gpu`) تفتقد مفتاح `letterCeilingReason`
كلّياً** — غائب، لا `null` — وهذا استُغِلّ عمداً في
`AiCategoryLetterCeilingReasonTest` كمادة حقيقية (لا مُصطنَعة) لحالة
"مستند أقدم" (القاعدتان 22/25)، بدل بناء تركيبة يدوية لهذه الحالة تحديداً.~~

**تحديث 2026-09-25 +03 (القرار ٩٢ البند ١، القاعدة 39) — هذا القسم لم يعد
صحيحاً؛ أُبقي مشطوباً لا محذوفاً (القاعدة 8).** الباك اند أعاد توليد هذا
الملفّ فعلياً @ `neuralcheck-backend#4d6c488` — استُبدل بنسخة من حزمة
`contract-handoff-4d6c488`، بعد تحقّق sha256 مباشرةً مقابل `SHA256SUMS`
المرفق: `9811881a4704f796fa...`.

**العيّنة صارت تحمل `letterCeilingReason` على الفئات الخمس عشرة كلّها**
(مُحقَّق بمقارنة بنيوية مباشرة، لا نقلاً عن أداة المنسّق فقط): سبع فئات
(`cellular`, `display`, `gps`, `gpu`, `memory`, `sensors`, `wireless`)
تحمل كائناً حقيقياً (`{"name":"LOW_CATEGORY_COVERAGE","ceilingLetter":"B","text":"..."}`،
نصّ مختلف لكل فئة)، وحرف كلٍّ منها هبط من `A` إلى `B` بسببها؛ والثمانية
الباقية (`audio`, `battery`, `camera`, `cpu`, `input`, `security`,
`storage`, `thermal`) تحمل المفتاح **حاضراً بقيمة `null`** — إجابة حقيقية
"لم تُسقِّف أي بوّابة هذه الفئة"، لا الغياب القديم. **تعديل `storage`
(`LLM_ADJUSTED`، `C→D`) — وهو سبب وجود هذا الملفّ أصلاً — بقي كما هو
تماماً**، لم تلمسه إعادة التوليد.

**الفرق الكامل — مُحقَّق بأداة (Python `json.load` + مقارنة حقلاً حقلاً)،
لا نقلاً عن رسالة المنسّق وحدها:** الأداة أعادت **60 فرقاً**، لا 43 كما
ورد — الفارق يتحلّل إلى: 37 فرقاً في نصّ سردي حرّ (31 `issues[].description`
+ 3 `narrative.buyerAdvice.*` + 3 من `narrative.{summary,details,verdict}`،
كلّها نصّ نموذج مُعاد توليده بلا دلالة قياس، ولا يختبره أي اختبار في هذا
المستودع بمحتواه)، وفرق `shortId` واحد (`NC-GXAR44`←`NC-C4VMN3`، عشوائي لا
دلالة له)، والباقي 22 فرقاً بنيوياً (15 إضافة `letterCeilingReason` + 7
تغييرات حرف) يطابق ما ذكره المنسّق وصفياً (لا عدداً) — رقم "43" على الأرجح
عدّ بمسارات أدقّ (كل حقل داخل الكائن الجديد سطراً مستقلاً) لا بمفاتيح
أعلى مستوى؛ لم أحسم طريقة عدّهم بالضبط.

**قرّاء هذا الملفّ في هذا المستودع (القاعدة 34، فُحصوا قبل الاستبدال):**
`V7GoldenSampleLlmAdjustedTest.kt` (يحمّله ويؤكّد عليه مباشرة) و
`AiV7PresentationTest.kt` (حالة واحدة، `storage.letterCeilingReason`). كلا
الملفّين حُدِّثا (القاعدة 23 — استُبدل التوقّع القديم، لم يُضَف توقّع
ثانٍ) ليطابقا الشكل الحقيقي الجديد — راجع تعليقات "resync 2026-09-25" في
كليهما للتفصيل الكامل.

## `v7-reports-document-sample.json` / `-honor.json` — إضافة 2026-09-24 +03 (القرار ٨٢ البند ٦)

**شكل مختلف عن الاثنتين أعلاه.** `v7-client-payload-sample*.json` تمثّلان
ما يصل **الإرجاع المباشر** لاستدعاء `analyzeDevice` (RPC) — شكل مسطَّح،
`overall`/`categories`/`pipeline` على الجذر مباشرة. هاتان الاثنتان الجديدتان
تمثّلان **مستند Firestore المخزون** `reports/{scanId}` كما يقرؤه
`FirebaseAnalysisService.fetchExistingReport` — الشكل **متداخل**:
`overall`/`categories`/`issues`/`narrative`/`language`/`narrativeIncomplete*`
كلّها تحت مفتاح `aiResponse`، لا على الجذر. الجذر نفسه يحمل حقولاً أخرى فقط:
`pipeline`/`shortId`/`isFreeTrial`/`aiModel`/`deviceMarketingName`/`analyzedAt`/
`deviceModel`/`userId`/`createdAt`/... — هذا الفرق بالضبط هو سبب عطب
`isCachedReportComplete` (كانت تقرأ `data["overall"]` من الجذر، فتُخفق دائماً
على أي مستند V7 حقيقي) الذي أُصلح في هذه الجولة.

نُسخا حرفياً من حزمة تسليم المنسّق `contract-handoff-77b710a`
(`C:\Users\Efendi.Tec\neuralcheck-workspace` — خارج المستودعات الثلاثة، مساحة
عمل المنسّق). **تحقَّق وكيل التطبيق بنفسه** أن sha256 المحلي لكلا الملفّين
يطابق `SHA256SUMS` المرفق مع تلك الحزمة قبل النسخ:
`416cc8c7ec78936e...` (`v7-reports-document-sample.json`) و
`dcef4a7322c0acbb...` (`v7-reports-document-sample-honor.json`).

- **`v7-reports-document-sample.json` ("B"):** تقرير V7 كامل بحرف حقيقي —
  `aiResponse.overall = {letter: "C", letterSource: "GATE:ROOT_DETECTED",
  harnessError: false, ...}`.
- **`v7-reports-document-sample-honor.json` ("Honor B"):** تقرير V7 كامل
  **بلا حرف**، `harnessError` مُعلَن —
  `aiResponse.overall = {letter: null, letterSource: "HARNESS_ERROR",
  harnessError: true, harnessErrorReason: "FUNCTIONAL_RESULTS_UNAVAILABLE",
  ...}`. هذا هو المثال الذي يثبت أن غياب `letter` لا يعني تقريراً ناقصاً —
  `letterSource` حاضر وصريح في كلتا العيّنتين.

**⚠️ تحذير المنسّق المنقول حرفياً — لا تُثبَّت قيمة معرّف:** «والباك اند يعيد
توليدهما الآن بقيم بديلة للمعرّفات، فتُزامَن نسخك مرّة أخرى في آخر الجولة.
فلا تثبّت اختباراً على قيمة معرّف.» لذلك كل اختبار في هذا المستودع يقرأ
هاتين العيّنتين يفحص **الشكل والدلالة** (`pipeline`, `overall.letterSource`,
`overall.harnessError`, وجود/غياب `letter`) لا قيم `shortId`/`userId`/
`deviceFingerprint`/أي معرّف آخر بعينه.

### إعادة مزامنة 2026-09-24 +03 — `neuralcheck-backend@2cab4d6`

الباك اند أعاد توليد العيّنتين فعلاً (كما حذّر المنسّق أعلاه). استُبدل
الملفّان بنسختيهما من حزمة التسليم الجديدة `contract-handoff-2cab4d6`، بعد
تحقّق sha256 مباشرةً مقابل `SHA256SUMS` المرفق معها:
`a048f9e9df761f203e8...` (`v7-reports-document-sample.json`) و
`389a88c042e6f4a453d...` (`v7-reports-document-sample-honor.json`).
`v7-client-payload-sample.json`/`-llm-adjusted.json` **لم يتغيّرا** — بصمتاهما
تطابقان ما كان موجوداً هنا مسبقاً (`08eb32001a6830c5...` و
`7a3ad9542424...`)، فلم يُعَد نسخهما.

**الفرق بين النسخة القديمة والجديدة — مُحقَّق بمقارنة بنيوية مباشرة (Python
`json.load` + مقارنة حقلاً حقلاً)، لا نقلاً عن أداة المنسّق فقط:** ستّة حقول
تغيّرت في كلٍّ من العيّنتين، لا خمسة كما ورد في رسالة المنسّق — الفرق
السادس غير المذكور هناك هو `shortId` (`sample`: `NC-Z9PGCW`←`NC-ZZKMW2`؛
`honor`: `NC-HWXR8L`←`NC-3JGAHW`). الخمسة الأخرى مطابقة لما ذكره المنسّق:
`deviceFingerprintSha`، `timeSeriesStoragePath`، `rawFingerprint.timeSeries.header.scanId`،
`rawFingerprint.timeSeries.header.deviceFingerprint`،
`rawFingerprint.timeSeries.footer.samplesSha256`. **كلّها معرّفات — لا فرق
في الشكل ولا في أي حقل دلالي** (`pipeline`/`overall.*`/`categories.*`/
`issues`/`narrative`/إلخ مطابقة حرفياً)، فادّعاء المنسّق الجوهري («ولا فرق
في الشكل») صحيح رغم اختلاف العدد. لا اختبار في هذا المستودع كان يثبّت أياً
من الستّة (تحقّق بـ`grep` لـ`shortId`/`"NC-"` في ملفّات الاختبار المرتبطة —
صفر نتيجة)، فلا اختبار احتاج تعديلاً بسبب هذه المزامنة.

## `v6-reports-document-sample.json` — إضافة 2026-09-25 +03 (القرار ٨٦ البند ٤ / القاعدة 41)

نُسخ من نفس حزمة `contract-handoff-2cab4d6` (كانت موجودة فيها منذ البداية،
لم تُنسخ وقتها لأن تلك الجولة لم تحتَجها) — تحقّق sha256 مباشرةً:
`9f128d367e2d9a7a3a21c51d8d9a10ba2ec0c8203523b451f3c0ab419f38ad0d`، مطابقة
لِـ`SHA256SUMS` المرفق. يمثّل مستند `reports/{scanId}` **V6** كما يقرؤه
`HomeViewModel.fetchLastScan`/`ScanHistoryViewModel.load` (`pipeline:
"v6_raw_timeseries"`, `grade: "B"`, `score: 78`, `deviceModel: "Samsung
Galaxy S10 5G"`, `summary`/`verdict` غائبان). يُستعمل في
`HomeAndHistoryV7SummaryTest.kt` لإثبات أن مسار V6 (بعد إصلاح مسار V7 في
نفس الملفّين) لم يتغيّر.

**ملاحظة تسمية:** طلب المستخدم لاحقاً (القرار ٨٧ البند ٤) اختبار بطاقة
البطارية بـ"عيّنة S10 (التغطية 100%)" — **هذه ليست `v6-reports-document-sample.json`
أعلاه رغم اسم الجهاز المشترك (Samsung Galaxy S10 5G في كلتا الوثيقتين
مصادفةً).** "S10 (100% تغطية)" تشير إلى `v7-reports-document-sample.json`
(العيّنة B) — حقل `aiResponse.categories.battery.coverage` فيها **1** (أي
100%)، لا `overall.coveragePct` العام (45%). تحقّق مباشر عند هذه الجولة.

## إعادة مزامنة ثانية 2026-09-25 +03 — `neuralcheck-backend@cc4097c`

استُبدل `v7-reports-document-sample.json`/`-honor.json` بنسختيهما من حزمة
`contract-handoff-cc4097c`، بعد تحقّق sha256 مباشرةً مقابل `SHA256SUMS`
المرفق: `254bd8b0edc188aa...` (`sample`) و`39442a5e01210e87...` (`honor`).
`v6-reports-document-sample.json` **لم يتغيّر** (بصمته مطابقة لِما كانت
عليه: `9f128d367e2d9a7a...`)، فلم يُعَد نسخه.

**الفرق — مُحقَّق بمقارنة بنيوية مباشرة (Python)، لا نقلاً عن رسالة
المنسّق فقط:** **ثلاثة حقول تغيّرت في كلٍّ من العيّنتين، لا حقلان كما ورد
في رسالة المنسّق** («حقلان جديدان في الجذر، هما serverSamplesStoragePath
وserverSamplesSaved، ولا غير»). الحقلان المذكوران صحيحان (كلاهما إضافة
جديدة في الجذر، غائبان تماماً في النسخة القديمة) — **والفرق الثالث
غير المذكور هو `shortId`** (`sample`: `NC-ZZKMW2`←`NC-JYSNZU`؛ `honor`:
`NC-3JGAHW`←`NC-K7AVWP`)، وهو معرّف عشوائي التوليد لا دلالة له، ونفس نمط
الانحراف الموثَّق في إعادتَي المزامنة السابقتين أعلاه (حيث نُقل "5" والصحيح
"6"). لا اختبار في هذا المستودع كان يثبّت `shortId`.

**عقد الحقلين الجديدين** — `V7_DISPLAY_SHAPE_CONTRACT.md` §١٣ في نفس
الحزمة: `reports/{scanId}.serverSamplesStoragePath` (نصّ أو `null`) و
`.serverSamplesSaved` (منطقي)، مستوى جذر المستند (شقيقا `pipeline`/
`analyzedAt`)، لا داخل `aiResponse`. **"القارّئ الوحيد المؤكَّد اليوم: لا
أحد"** — منقول من العقد حرفياً.

**أثر الحقلين على قرّاء هذا المستودع — مُتحقَّق بتشغيل فعلي، لا افتراضاً:**
شغّلت `./gradlew testLaunchDebugUnitTest` بعد الاستبدال مباشرة — **بلا أي
تعديل كود** — والنتيجة مطابقة قبل الاستبدال وبعده (BUILD SUCCESSFUL، نفس
عدد الاختبارات). هذا يثبت تجريبياً أن:
- `FirebaseAnalysisService.fetchExistingReport` (يُمرّر عبر `flattenReportDocument`
  ثمّ `Gson().fromJson(..., AiAnalysisResponse::class.java)`): Gson الافتراضي
  (`GsonBuilder().create()`، بلا وضع صارم) يتجاهل صامتاً أي مفتاح JSON لا
  يقابله حقل في الصفّ الهدف — الحقلان الجديدان ببساطة لا يُستهلَكان، بلا
  استثناء وبلا أثر.
- `readV7OverallSummary`/`buildHomeLastScan`/`buildCloudScan` (تقرأ
  `Map<String, Any?>` خاماً بمفاتيح مُسمّاة صراحة: `pipeline`، `aiResponse`،
  `deviceModel`، `analyzedAt`، `grade`، `score`، إلخ): لا سطر واحد فيها
  يذكر `serverSamplesStoragePath` أو `serverSamplesSaved`، فوجودهما في
  الخريطة الخام حاضر وغير مقروء — بلا أثر أيضاً.

## إعادة مزامنة ثالثة 2026-09-25 +03 — `neuralcheck-backend@90fb816` (القرار ٩٥ البند ٣)

استُبدلت ثلاث عيّنات بنسخها من حزمة `contract-handoff-90fb816`، بعد تحقّق
sha256 مباشرةً مقابل `SHA256SUMS` المرفق مع تلك الحزمة، **قبل النسخ وبعده
كلاهما** (الأمر نفسه نصّ: «فطابق البصمة قبل النسخ»):

| الملفّ | القديم (`HEAD` وقت النسخ، `ef0f43b`) | الجديد (بعد، مطابق `SHA256SUMS`) |
|---|---|---|
| `v7-client-payload-sample.json` | `08eb32001a6830c5...` | `1598d0d0bf2f5a2c...` |
| `v7-reports-document-sample.json` | `254bd8b0edc188aa...` | `a6b298e89850a473...` |
| `v7-client-payload-sample-llm-adjusted.json` | `9811881a4704f796...` | `0d529cecd3bff878...` |
| `v7-reports-document-sample-security-downgrade.json` (**جديد**) | — | `38e5aa88a8dfbfc9...` |

**الفرق — مُحقَّق بمقارنة بنيوية مباشرة (Python `json.load` + مقارنة حقلاً
حقلاً مقابل `git show HEAD:<path>`، لا نقلاً عن رسالة المنسّق فقط)، وأُعيد
تشغيل الأداة عند كتابة هذا القسم لا الاعتماد على تشغيل سابق:**

- **`v7-client-payload-sample.json`: 24 فرقاً خاماً** — 23 منها
  `issues[N].title` (الصياغة القديمة `"غير مقيس: <testId> (<REASON>)"` →
  الجديدة `"غير مقيس: <testId>"` بلا القوسين، مصدرها `categoryStates.js:664`
  بالتزام `90fb816` — نقلاً عن أمر المنسّق، غير مُتحقَّق من هذا المستودع)
  و**فرق `shortId` واحد** (`NC-23857C`←`NC-SEZDC6`، عشوائي لا دلالة له).
  المنسّق عدّ هذا **"فرق واحد، مختلف 1"** — يطابق تماماً بعد استثناء
  `shortId` (كما ينصّ أمره دوماً) واحتساب مصفوفة `issues[]` **وحدة عدّ
  واحدة** لا 23 سطراً مستقلاً — نفس اصطلاح العدّ الموثَّق في
  `golden-sync-exclusion-line.md`.
- **`v7-reports-document-sample.json`: 24 فرقاً خاماً** — نفس النمط
  بالضبط تحت `aiResponse.issues[N].title` (23 فرقاً) + `shortId` واحد
  (`NC-JYSNZU`←`NC-VZGEMZ`). المنسّق: **"فرق واحد، مختلف 1"** — يطابق
  بنفس الاصطلاح.
- **`v7-client-payload-sample-llm-adjusted.json`: 28 فرقاً خاماً** — 23
  فرق عنوان `issues[]` (وحدة واحدة) + **4 حقول** `categories.storage.*` +
  `shortId` واحد (`NC-C4VMN3`←`NC-XDFWAK`، مستبعد). حقول `storage` الأربعة،
  بقيمها الفعلية من هذا التشغيل:
  - `letter`: `"D"` → `"C"`
  - `letterSource`: `"LLM_ADJUSTED"` → `"DETERMINISTIC"`
  - `adjustmentEvidence`: `["categoryStates.categories.storage.deterministicLetter"]`
    → `null` — **دليل ذاتي المرجع بالضبط** (فئة `storage` تستشهد بحرفها
    الحتمي **هي نفسها** كدليل تعديلها) — قاعدة هذا المستودع رقم 33 تصف هذه
    الحالة بعينها. هذا ما رفضه `90fb816` (`c6d7dbf` نقلاً عن أمر المنسّق).
  - `adjustmentReason`: كان يبدأ بـ`"[عيّنة مُصطنَعة محلياً — لا نداء
    نموذج، وُلِّدت بسكربت محلي بلا اتّصال بأي مزوّد ذكاء اصطناعي..."` — **هذا
    يؤكّد مباشرة، من نصّ العيّنة القديمة نفسها لا من نقل**، أن قيمة
    `LLM_ADJUSTED` القديمة لـ`storage` لم تكن نتاج نداء نموذج حقيقي أصلاً؛
    صار `null` الآن (لا تعديل مقبول، فلا سبب يُذكر).
  المنسّق عدّ هذا **"خمسة فروق، مختلف 5"** — يطابق تماماً: 1 (وحدة
  `issues[]`) + 4 (حقول `storage` الأربعة)، مستبعداً `shortId`.

**`v7-reports-document-sample-security-downgrade.json` ("B") — عيّنة
جديدة كلّياً، لا مقارنة سابقة لها:** تمثّل تعديل حرف **مقبولاً** وحقيقياً
(بخلاف `storage` أعلاه، المرفوض الآن) — مدخل اختبار SEC-04 فشل فعلياً،
وردّ نموذج مصنوع موسوم بوضوح كعيّنة، والكود الحقيقي في الخادم قَبِله وكتبه.
مُحقَّق من محتواها الفعلي (تشغيل Python مباشر على الملفّ بعد النسخ، لا نقلاً
عن وصف الأمر فقط):

```
security.state = PARTIAL
security.letter = D
security.letterSource = LLM_ADJUSTED
security.adjustmentReason = اختبار الجذر SEC-04 فشل فعلياً في فئة الأمان — دليل
  مقيس فعلاً على هذا الفحص، لا نصّاً مُختلَقاً، ويسند تخفيضاً في هذه الفئة تحديداً.
security.adjustmentEvidence = ['categoryStates.testResults[61].outcome']
security.coverage = 0.9
overall.letter = C  (غير متأثّر — هذه عيّنة لفحص فئة security وحدها)
overall.letterSource = GATE:ROOT_DETECTED
```

**⚠️ ملاحظة منقولة من أمر المنسّق حرفياً، ومُتحقَّقة هنا مباشرة من الحقول
أعلاه:** «والحرف الحتمي لـ`security` كان `D` أصلاً، فالتعديل «طُبِّق» بلا
تغيّر في الحرف (`buildV7Payload.js:680-697` — نقلاً، غير مُتحقَّق من هذا
المستودع). وهذا معروض على المستخدم، فلا تُصلح شيئاً في القارئ لأجله.» —
لم يُغيَّر أي قارئ بسبب هذه الملاحظة، طاعةً للنصّ.

**`v7-verifyReport-response-sample-security-downgrade.json` ("C") — لم
تُنسَخ عمداً.** التطبيق لا ينادي `verifyReport` مطلقاً — تحقَّق بأداة عند
كتابة هذا القسم:
`git grep -n "verifyReport" -- app/src/main app/src/test` يعيد 3 نتائج،
كلّها تعليقات توثيقية (`AiAnalysisResponse.kt:274-275`,
`V7RootFieldPresence.kt:60-61`, `AiV7Presentation.kt:310`) — لا نداء واحد.
و`grep -rn "httpsCallable"` في `app/src/main` يسرد كل نداءات RPC الحقيقية
(`analyzeDevice`, `createScanSession`, `joinScanSession`, `submitEvaluation`,
`checkSubscription`, `registerUser`, `redeemPromoCode`, `requestPayment`) —
لا `verifyReport` بينها. القاعدة 41: القارئ يُختبر بالشكل الذي يستقبله
فعلاً — ولا قارئ لـ"C" في هذا المستودع، فلا عيّنة له هنا.

**القاعدتان 23 و34 — الاختبارات التي استُبدل توقّعها، لا أُضيف إليها:**
- `V7GoldenSampleTest.kt` — سطر تأكيد `shortId` وحده (كان `NC-23857C`).
- `V7GoldenSampleLlmAdjustedTest.kt`:
  - سطر تأكيد `shortId` (كان `NC-C4VMN3`).
  - استُبدل الاختبار الذي كان يُثبت أن `storage` تصل `LLM_ADJUSTED` بحرف
    `D` ودليل ذاتي المرجع، باختبار جديد بنفس الاسم/الموضع يُثبت الشكل
    الحقيقي الآن: `DETERMINISTIC`، الحرف `C`، و`adjustmentReason`/
    `adjustmentEvidence` كلاهما `null` — انظر تعليق "resync 2026-09-25" في
    الملفّ نفسه.
  - سطر تأكيد وحيد داخل اختبار "seven categories A→B" كان يفترض حرف
    `storage` القديم `D`، صار `C`.
  فُحصت بقيّة الاختبارات في هذا الملفّ (letterCeilingReason وما حولها) عبر
  الأداة (diff بنيوي) فتأكّد أنها غير متأثّرة بهذه المزامنة تحديداً — لم
  تُعدَّل.
- فُحص كل ملفّ اختبار آخر يرجع لأسماء العيّنات الثلاث القديمة (`git grep
  -rl` لأسماء الملفّات عبر `app/src/test`، 9 ملفّات) بحثاً عن نصّ العنوان
  القديم لـDISC، أو قيمة `shortId` قديمة، أو تأكيداً خاصاً بـ`storage` —
  لا شيء آخر وُجد، فلم يُعدَّل شيء آخر.

**قرّاء `v7-reports-document-sample-security-downgrade.json` في هذا
المستودع:** ملفّ اختبار واحد جديد بهذه الجولة —
`V7SecurityLetterSourceReaderTest.kt` — انظر البند التالي (القرار ٩٥ البند ٤).

## إعادة مزامنة رابعة 2026-09-25 +03 — `neuralcheck-backend@057cf43` (القرار ٩٨ البند ١ / القرار ٩٩)

استُبدل `v7-reports-document-sample-security-downgrade.json`، وأُضيف
`v7-reports-document-sample-cpu-c06-downgrade.json` (جديد)، من حزمة
`contract-handoff-057cf43`، بعد تحقّق `sha256sum -c SHA256SUMS` مباشرةً
(الأربعة ملفّات في الحزمة، بما فيها الشكل C الذي لم يُنسَخ — كلّها OK):

| الملفّ | القديم | الجديد |
|---|---|---|
| `v7-reports-document-sample-security-downgrade.json` | `38e5aa88a8dfbfc9...` | `d7938333644a276d...` |
| `v7-reports-document-sample-cpu-c06-downgrade.json` (**جديد**) | — | `ae57c136031dbdd3...` |

**السبب — القرار ٩٨ البند ١ (`neuralcheck-backend@49c0e40`، نقلاً عن أمر
المنسّق، غير مُتحقَّق من هذا المستودع): `LLM_ADJUSTED` لا يُكتب إلا إذا
تغيّر الحرف فعلاً.** حرف `security` الحتمي كان `D` أصلاً — فتعديله «المقبول»
لم يكن له أثر، ولم يعد يُكتب كتعديل إطلاقاً.

**الفرق — مُحقَّق بمقارنة بنيوية مباشرة (Python `json.load` + مقارنة حقلاً
حقلاً)، أُعيد تشغيلها عند كتابة هذا القسم:** **7 فروق خامّة** في
`security-downgrade.json`:
- `aiResponse.categories.security.letterSource`: `"LLM_ADJUSTED"` → `"DETERMINISTIC"`
- `aiResponse.categories.security.adjustmentReason`: النصّ الفعلي القديم → `null`
- `aiResponse.categories.security.adjustmentEvidence`: `["categoryStates.testResults[61].outcome"]` → `null`
- `aiAudit.adjustments[1].noEffectReason`: غائب → `"ACCEPTED_NO_EFFECT"`
- `aiAudit.regeneration.rounds[0].durationMs`: `9` → `8` (ضجيج توقيت، لا دلالة)
- `aiDurationMs`: `9` → `8` (نفس الضجيج، جذر المستند)
- `shortId`: `"NC-CTBYNZ"` → `"NC-QHE7JL"` (مستبعد)

المنسّق عدّ هذا **"ستّ فروق"** (باستثناء `shortId`) — يطابق تماماً: 6 من
أصل 7. **`aiAudit`/`aiDurationMs` غير مقروءين في هذا التطبيق** — تحقّق
مستقلّ بالأداة: `git grep -n "aiAudit" app/src/main` و`git grep -n
"aiDurationMs" app/src/main` كلاهما صفر نتيجة — فلا أثر لثلاثة من الستّة
على أي قارئ في هذا المستودع؛ الثلاثة المتبقّية (`letterSource`/
`adjustmentReason`/`adjustmentEvidence`) هي كل ما يمسّ التطبيق فعلياً.

**`v7-reports-document-sample-cpu-c06-downgrade.json` ("B") — عيّنة
جديدة، مثال حقيقي حالي لتعديل مقبول بأثر فعلي (بخلاف `security` أعلاه):**
مُحقَّق من محتواها الفعلي (تشغيل Python مباشر بعد النسخ):

```
cpu.state = MEASURED
cpu.letter = C
cpu.letterSource = LLM_ADJUSTED
cpu.adjustmentReason = الاختبار C-06 (تقييد الأداء الحراري) فشل فعلياً في فئة
  المعالج — قياس حقيقي على هذا الفحص، ويسند تخفيضاً في هذه الفئة تحديداً.
cpu.adjustmentEvidence = ['categoryStates.testResults[13].outcome']
cpu.coverage = 1
overall.letter = C
pipeline = v7_findings_based
```

الحرف الحتمي لـ`cpu` هو `B`؛ اختبار C-06 (فخّ S10، أصله `pass`) عُدِّل وحده
إلى `fail`، فهبط الحرف المعروض إلى `C` — تعديل بأثر حقيقي، على خلاف حالة
`security` أعلاه، فيُكتب `LLM_ADJUSTED` فعلاً.

**`v7-verifyReport-response-sample-{security,cpu-c06}-downgrade.json`
("C") — لم تُنسَخا عمداً،** لنفس السبب المُوثَّق في القسم السابق: لا نداء
`verifyReport` في هذا التطبيق (`git grep -n "verifyReport" -- app/src/main
app/src/test` — تعليقات فقط، صفر نداء؛ أُعيد التحقّق عند كتابة هذا القسم).

**القاعدتان 23 و34 — الاختبارات المستبدَلة، لا المُضافة إليها:**
- `V7SecurityLetterSourceReaderTest.kt` — كلا اختباريه استُبدلا بالكامل:
  الأول (`security-downgrade sample B passes...`) كان يثبّت `LLM_ADJUSTED`
  وسبباً ودليلاً حقيقيَّين، صار يثبّت `DETERMINISTIC` و`null`/`null`؛
  الثاني (`security's letter-source row...`) كان يثبّت وسم "حرف عدّله
  النموذج" + السبب المعروض، صار يثبّت وسم "حرف محسوب مباشرة من نتائج
  القياس" وحده، بلا سبب ولا دليل (`if (kind == LLM_ADJUSTED)` في
  `ReportScreen.kt:2843` لا يُنفَّذ إطلاقاً لفئة `DETERMINISTIC`). اختبارا
  الحرس البنائي (evidence render-surface guard) غير متأثّرين، لم يُعدَّلا.
- `V7GoldenSampleLlmAdjustedTest.kt` — توثيق الصنف (لا اختبار) كان يشير
  إلى `security` كمثال `LLM_ADJUSTED` حقيقي مقبول؛ صُحِّح (مشطوب لا محذوف،
  القاعدة 8) للإشارة إلى `cpu`/العيّنة الجديدة بدلاً منه.
- ملفّ جديد `V7CpuLetterSourceReaderTest.kt` — تغطية جديدة (لا استبدال)
  للمسار الكامل على عيّنة `cpu-c06-downgrade`: الوسم LLM_ADJUSTED، السبب
  الحقيقي معروضاً، الحرف C، ولا تعبير عرض للدليل (تحقّقه القاعدة 23
  السابقة في ملفّ security، لا مكرَّراً هنا).

## إعادة مزامنة 2026-10-09 +03 — `neuralcheck-backend@6fa408d` (نقل الخامّ من مستند التقرير)

استُبدلت العيّنات الستّ لمستند `reports/{scanId}` — `v7-reports-document-sample.json` و`-honor.json`
و`-security-downgrade.json` و`-cpu-c06-downgrade.json` و`-zero-measured.json` و`-zero-measured-battery-harness-error.json`
— **نسخاً حرفياً، بايتاً ببايت،** من نسخ المنسّق المستخرجة بـ`git show 6fa408d:functions/test/fixtures/golden/<الاسم>` في
`C:\Users\Efendi.Tec\neuralcheck-workspace\evidence\v8-phase0\reports-document-golden-6fa408d\`.
**المصدر: `neuralcheck-backend@6fa408d`. وحالة ذلك الالتزام وما فيه منقولان من أمر المنسّق، غير مُتحقَّق منهما من هذا
المستودع** (وكيل التطبيق لا يقرأ `neuralcheck-backend`). المُتحقَّق هنا: أن sha256 المنسوخ يطابق القيم أدناه، وأن الملفّات
مطابقة بايتاً (`cmp`) لنسخ المنسّق.

**السبب — قرار المستخدم 2026-10-09 (BATCH3-CLOSE والقرارات الإحدى عشرة، منقول في الأمر، غير مُتحقَّق منه من هذا المستودع):**
«يخرج من المستند المقروء الخامّ والمعرّفات الثلاثة وaiAudit». ونفّذه الخادم في `6fa408d`: حُذفت من مستند `reports` المفاتيح
`rawFingerprint` و`sessionId` و`deviceFingerprintSha` و`timeSeriesStoragePath` و`aiAudit`، **وبقي `userId`.** ثم أُعيد توليد
العيّنات الستّ هناك بمولّداتها (القاعدة 39)؛ ونسخها هنا يُبقي القارئ مختبَراً بالشكل الذي يستقبله (القاعدة 41).

| الملفّ | القديم (sha256) | الجديد (sha256) |
|---|---|---|
| `v7-reports-document-sample.json` | `a33bd13a0a887640c8f5f0b7bbf56d68fe67ffcf0183177bd59256698f1449f0` | `9a8666d81d57401f4c661eeab1e7bc2bcf0b2f4819ccda7ca3a599719a6b919a` |
| `v7-reports-document-sample-honor.json` | `2d361f8ffb69f0647a74b3460c38cee3086faf8279f59e886a86e860195aca54` | `30abe980dba6cba5c85d2ae83dc5326cb035eaf8cc3752e17ae4a877c7351b12` |
| `v7-reports-document-sample-security-downgrade.json` | `cf922206e85c5376ffcbc3c019b843beef1ad9099a452a2558431002b33697fc` | `80c0b01eda9891e7b93e42a0c17c5903bac8e0245740a238de5c1e678540b9cf` |
| `v7-reports-document-sample-cpu-c06-downgrade.json` | `c7076ce04742ea5dd6f61a35932ed07d4699208af1004efd8f4e8cb919ae9ee5` | `356301933601b34914513a93415ced8061faf7e4a59d41380404753a59665579` |
| `v7-reports-document-sample-zero-measured.json` | `9436390a350453c0aded2e9c6ddbb2599fc42f21b8ca4a1f8f17292d90d6353d` | `cdc7c45aada991c2c33dd37ddfa4275054fc92cdf2bc5efd9447d81e778e0625` |
| `v7-reports-document-sample-zero-measured-battery-harness-error.json` | `d193b36a9d76e2041878d17b568c0ddcd7fc7c4b6fb3c0a7c715fb0205833fd3` | `945b64191ae42fbacab53a3c65178851dfe4d31e3126f5616fbdfdae5a3b2213` |

**الفرق القديم ← الجديد — مُحقَّق بمقارنة بنيوية مباشرة (Python `json.load` + مقارنة حقلاً حقلاً) على الملفّات الستّة:** في كلٍّ منها
**خمسة مفاتيح من الجذر زالت** (`aiAudit` و`rawFingerprint` و`sessionId` و`deviceFingerprintSha` و`timeSeriesStoragePath`) وبمسارات
محذوفة 229 في `sample` و213 في `honor` و245 في `security-downgrade` و244 في `cpu-c06-downgrade` و230 في كلٍّ من `zero-measured`
و`zero-measured-battery-harness-error`، **ولم يُضَف مسار واحد.** وبعد إسقاط الخمسة من القديم لا يبقى فرق قيمة إلا:
`shortId` في الستّة (عشوائي كل توليد) و`aiDurationMs` في `cpu-c06-downgrade` و`security-downgrade` و`v5Metrics.bodyBuildMs` في
`security-downgrade` (ضجيج توقيت). **`userId` باقٍ.**

**القرّاء في هذا المستودع (فُحصوا قبل الالتزام، القاعدة 34):** `cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest` على الشجرة
بعد النسخ — **0 فشل**، فلا اختبار كان يثبّت وجود أحد المفاتيح الخمسة. و`git grep -n -e rawFingerprint -e aiAudit
-e deviceFingerprintSha -e timeSeriesStoragePath -- app/src` (خارج `test/resources`) لا يعيد إلا ثلاثة أسطر **تعليقات** في
`V7SecurityLetterSourceReaderTest.kt` (`:94-95`) و`V7FullDocumentReadCoverageTest.kt` (`:56`) — لا قراءة. و`sessionId` في
`app/src/main` طلبٌ يُرسَل (`FirebaseAnalysisService.kt:135-136`) وكيان محلّي، لا قراءة من مستند التقرير. لم أعدّل شيئاً من
تلك التعليقات (أمر المنسّق: لا مساس بغير هذه الملفّات).

**الأقسام أعلاه التي تذكر هذه المفاتيح** (إعادات المزامنة السابقة: `rawFingerprint.timeSeries.header.scanId` و`aiAudit.adjustments`
وغيرها) **تصف نسخاً سابقة من العيّنات وصارت قديمة بهذه المزامنة؛ أُبقيت لا محذوفة (القاعدة 8).**

## إعادة مزامنة 2026-10-13 +03 — `neuralcheck-backend@c2e5e49` (`92ec041`: `evaluationRulesVersion`، القاعدة 39)

استُبدلت العيّنات الستّ لمستند `reports/{scanId}` — `v7-reports-document-sample.json` و`-honor.json` و`-security-downgrade.json`
و`-cpu-c06-downgrade.json` و`-zero-measured.json` و`-zero-measured-battery-harness-error.json` — **نسخاً حرفياً، بايتاً ببايت،** من
مجلّد المنسّق `reports-doc-samples-c2e5e49/` (ومعه `SHA256SUMS.txt`). **المصدر: `neuralcheck-backend@c2e5e49`، وتغيير الخادم فيه (`92ec041`)
منقولان من أمر المنسّق، غير مُتحقَّقين من هذا المستودع** (وكيل التطبيق لا يقرأ `neuralcheck-backend`). المُتحقَّق هنا: أن sha256 كل ملفّ منسوخ
يطابق `SHA256SUMS.txt` قبل الالتزام وبعده (`git show HEAD:<path> | sha256sum`)، وأن الملفّات مطابقة بايتاً (`cmp`) لنسخ المنسّق، وأنها LF بلا `\r`.

**الفرق القديم ← الجديد — مُحقَّق بـ`diff` مباشر على الملفّات الستّة:** في كلٍّ منها سطر واحد زاد، وسطر الجذر هو
`"evaluationRulesVersion": "rules-2026-10-13-zero-cycles-unsupported"` (السطر 10)؛ **لا مسار آخر تغيّر ولا قيمة.** (وهو المسار الذي كانت أداة المنسّق
`tools-golden-sync-check.js` تعدّه ناقصاً في نسخنا، «باستثناء shortId في الجذر».)

| الملفّ | القديم (sha256، نسخة `6fa408d`) | الجديد (sha256) |
|---|---|---|
| `v7-reports-document-sample.json` | `9a8666d81d57401f4c661eeab1e7bc2bcf0b2f4819ccda7ca3a599719a6b919a` | `b05cc9aafee213731b104f6c9d254fc6f8d964c4b167b8de841493ed270dd706` |
| `v7-reports-document-sample-honor.json` | `30abe980dba6cba5c85d2ae83dc5326cb035eaf8cc3752e17ae4a877c7351b12` | `fdbea3e975ddeed611ab6491d66e934a60c67fb0787dd7a33b76557d033c1ec6` |
| `v7-reports-document-sample-security-downgrade.json` | `80c0b01eda9891e7b93e42a0c17c5903bac8e0245740a238de5c1e678540b9cf` | `e06162ed538ec93c58d454080bd7795a4bca1340121308aaa8b2282d26fbab6d` |
| `v7-reports-document-sample-cpu-c06-downgrade.json` | `356301933601b34914513a93415ced8061faf7e4a59d41380404753a59665579` | `b4f162fd0ca843e22d98c478f5ac1fb3339189a1f43533278b8972e2b42dc7be` |
| `v7-reports-document-sample-zero-measured.json` | `cdc7c45aada991c2c33dd37ddfa4275054fc92cdf2bc5efd9447d81e778e0625` | `c990ae19a9f52d78f41994f8cd7187baf5e1a24ee7d419d441c80221bcee4d1a` |
| `v7-reports-document-sample-zero-measured-battery-harness-error.json` | `945b64191ae42fbacab53a3c65178851dfe4d31e3126f5616fbdfdae5a3b2213` | `769100459edd49b6a15022ac4ac6ccb1f2c79f5e8110a8ec5bbe1d199e785c24` |

**الحقل الجديد لا يقرؤه التطبيق** (قرار المستخدم 2026-10-13، منقول في الأمر، غير مُتحقَّق منه من هذا المستودع: «not displayed»). **لا قارئ أُضيف له.**
والغياب معلَن لا صامت (القاعدة 25): اختبار `ReportsDocumentEvaluationRulesVersionTest` يثبّت أن العيّنات الستّ تحمل المفتاح على الجذر
نصّاً غير فارغاً، وأن لا شيء في `app/src/main` يذكره (قراءة المصدر بلا تعليقات) — فإن أُضيف قارئ يوماً فبقرار، وبتعديل ذلك الاختبار.
ولا اختبار في هذا المستودع يحرس مفاتيح جذر مستند التقرير بقائمة (فُحص: `V7FullDocumentReadCoverageTest` يقرأ عيّنة الحمولة
`v7-client-payload-sample.json` لا مستند التقرير، وقائمتها `ROOT_FIELDS` لم تتغيّر)؛ ولم يتغيّر أيّ اختبار آخر — المجموعة كاملةً بلا فشل.

## نسخة عقد ربط «قبل/بعد صيانة» وعيّنات ردود الخادم — إضافة 2026-10-22 +03 (أمر المستخدم 2026-10-22؛ القواعد 7 و22 و24 و25 و29 و41)

**نسختان منقولتان حرفياً من `neuralcheck-backend`، لا مُولَّدتان هنا، ولا تُعدَّلان.** وكيل التطبيق لا يكتب في ذلك المستودع ولا يشغّل فيه شيئاً؛ قرأ بـ`git -C <backend> show <sha>:<path>` فقط.

### ١) نصّ العقد — `protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-f3c1bab.md`
من `docs/PROTOCOL_CONTRACT.md` عند الالتزام `f3c1bab796601d325496d5fc62342e9405621c00` (2026-10-22T19:16:59+03:00، التزام ق36، آخر التزامات الخادم من الأمر نفسه وقت القراءة؛ `master` و`origin/master` هناك كلاهما عنده بـ`git rev-parse`؛
**كونه منشوراً غير مُدَّعى**). **ثلاثة مقاطع متصلة** (الأسطر في المصدر)، **بالمراسي نفسها التي قُصّت بها نسخة b0a0f66** (تحرّكت الأرقام لا المراسي): A: 116-242 (من سطر `### A2 ` إلى آخر سطر غير فارغ قبل `### A4 `: القسم A2 مع إلحاق
2026-10-22 وملاحظة ق31، والقسم A3 المعاد كتابته مع ملاحظات «بُني» S3-S10)، B: 1113-1164 (من صفّ `SCAN_PURPOSE`/`NORMAL` إلى صفّ `PAIR_CALL_REFUSAL`/`OWNER_MISMATCH`)، C: 1267-1271 (من صفّ `PURPOSE_REFUSAL`/`SESSION_NOT_PENDING` إلى صفّ
`PURPOSE_REFUSAL`/`PART_NOT_ENABLED`). **المراسي أُعيد بها قصّ b0a0f66 أولاً فأعطت نطاقاته (116-238 و1108-1159 و1259-1263) وبصماته نفسها** (تحقّق في سكربت البناء قبل الكتابة). بصمات المقاطع (sha256 للأسطر بـLF، مطابقة لما في رأس النسخة):
A `d71e9eba275c8e71b80df286826d38126c482733008793bb6e44b5a9300ada75` (**تغيّرت**؛ كانت `1bd639af6f71df42b60bfec151a06f7d141523d668fe084d583984fc291320b5`) · B `abd16fc754bb0dae9cb0dd1c174640524d9e5560da4e80403b07aaa51b93b489` (**لم تتغيّر**) · C `681ac4f38715a9c91ba933cc9a6fe310302ea11f2733ce1c69534572bf4b6f6a` (**لم تتغيّر**)؛ والملفّ المصدر كلّه
عند ذلك الالتزام `224f4aef8717109799aa2b8cb6e6d9601f074c9c75e8cb49aa12b58e0590125d`؛ **والنسخة هنا كاملةً** (الرأس الإنجليزي + المقاطع الثلاثة) `773a8a47fc45f6078bb392c1bb0eca3d9f3b1adea9a8b991d142f506498d2c45`.
`.gitattributes` يضعها `-text` فلا يمسّها تحويل نهايات الأسطر.

**سلسلة الاستبدالات (السابقة في تاريخ git):** نسخة `662ac7ad485a75e7f251bd9ec4ab644bb83c9d83` (S1، عند `e070441`، كاملةً `1d04451334d78e2434fc24a86f148d8c33816f8d4e7d36d926a00173beafb8fa`) ← نسخة
`0b89db1fdfb17f2f70a386fb0e317d23b1a77dcf` (S7، عند `fed7d12`، كاملةً `39fac7886a8f81f8c84bd93ac49fa117c8946a56997c839443d03b238becc296`) ← نسخة `b76c791e6634f085d7b330c6a9f901533811a28b` (S8، عند `a96aca8`، كاملةً
`138277ff24e72719b2a19b9e3094c0a5fa10bb2c05179c9af1447df2673f161e`، مصدرها كلّه `971c0b34ba811c386586c0d84a6a4768e9e88e5419f73d1005d2746a9ec72fbb`) ← نسخة `b0a0f664aad7c9e08d1367c3c4d0eec81dfe1796` (ق31، عند `b440e9c`، كاملةً
`3ce47bc006b562800969a2ce96d23e3e4095a4f6313b3026da239c4064cd078f`، مصدرها كلّه `d2eed9b559afb73a42dca8d9eb32c35c179cc665effc9871b4bde48b81c007df`) ← هذه (ق36).
**الفرق 662ac7a ← 0b89db1:** فقرة أُعيدت كتابتها وستّ ملاحظات «بُني»؛ لا صفّ جدول ولا شكل سلك. **الفرق 0b89db1 ← b76c791 — قرأتُه بـ`git diff 0b89db1 b76c791 -- docs/PROTOCOL_CONTRACT.md` (77 سطراً، 12 إضافة و8 حذف):**
(١) `pair` على «قبل» صار `{state, openedAt, ownerId}` — حقل **خادمي** في `scanProtocol` لا على السلك؛ (٢) استعلام القائمة صار مساواتين (`pair.ownerId` و`pair.state`) والسقف `OPEN_PAIR_SCAN_CAP` **لكل محلّ**
(كان للمشروع كلّه)؛ (٣) **رمز رفض جديد واحد للتأكيد: `PAIR_OWNER_MISMATCH`** (`pair.ownerId` على «قبل» ≠ `userId` في تقريرها؛ في القائمة يُستبعد المرشَّح لا يُرفض الطلب) — **صفّ جديد واحد في جدول المفردات
(`PAIR_CALL_REFUSAL`/`OWNER_MISMATCH`)** فتغيّرت بصمة المقطع B وبقيت بصمة C؛ (٤) `PAIR_BEFORE_UNREADABLE` يغطّي الآن `ownerId` غائباً أو غير نصّ؛ (٥) ملاحظة `PAIR_OWNER_ABSENT` سطر سجلّ خادمي لا ردّ؛ (٦) قرار ١٢ في البند ٨ وملاحظة «بُني S8».
**ما يخصّ التطبيق: رمز واحد جديد على السلك (`PAIR_OWNER_MISMATCH`) فقط؛ لا شكل طلب ولا ردّ تغيّر.**

**الفرق b76c791 ← b0a0f66 — قرأتُه بـ`git diff b76c791 b0a0f66 -- docs/PROTOCOL_CONTRACT.md` (ملفّ واحد، 12 سطراً مغيَّراً: 7 إضافة و5 حذف):**
(١) **`setSessionPurpose` يرفض الآن محلّاً ينقصه الجزء الذي يحتاجه غرضه** — `purpose ≠ normal` إن كان `protocolParts.purposes !== true`، و`maintenance_*` إن كان `protocolParts.batteryBeforeAfter !== true` أيضاً (`=== true` وحدها تفعّل؛
الجزء الناقص الأوّل هو المسمّى في سجلّ الخادم) — **برمز واحد جديد `PURPOSE_PART_NOT_ENABLED`** (`failed-precondition`، والرسالة رمزه التقني، والجزء الناقص في سجلّ الخادم لا في جسم الردّ)؛ (٢) **صفّ جديد واحد في جدول المفردات
(`PURPOSE_REFUSAL`/`PART_NOT_ENABLED`، وسم `proposal` A2)** فتغيّرت بصمة المقطع C (من أربعة صفوف إلى خمسة) وبقيت بصمة B؛ (٣) الفحص **بعد** فحوص الجلسة (وجود، ملكية، حالة، انقضاء) و**قبل** أيّ كتابة، و`normal` يبقى مقبولاً كما كان
بلا قراءة لمستند المالك، ومستند المالك الغائب حالة صريحة بالرمز نفسه؛ (٤) بند «معلّق ٢» حُسم بقرار حسن (ق31) وبند «ما لم يُعالَج هنا» صار «ما عُولج: ق31»؛ (٥) ملاحظة «بُني (ق31)» (الاختبارات التي كُتبت قبل العلاج وفشلت عليه،
والمشاهد التي استُبدلت)؛ (٦) سطر واحد في A3 يقول إن علاج ثغرة `setSessionPurpose` بُني. **هل تغيّر صفّ سلك يخصّ التطبيق؟ نعم: رمز رفض واحد جديد على السلك (`PURPOSE_PART_NOT_ENABLED`) وصفّه الواحد في جدول المفردات؛ وما يترتّب عليه في العرض
(الرقائق وانتقاء النوع للصيانة لا تُرسم إلا بالجزأين معاً). لا شكل طلب ولا ردّ لأيّ من الدوالّ الثلاث تغيّر** (ردّ `setSessionPurpose` الناجح بقي `{sessionId, purpose, purposeSource}`).

**الفرق b0a0f66 ← f3c1bab — قرأتُه بـ`git diff b0a0f66 f3c1bab -- docs/PROTOCOL_CONTRACT.md` (ملفّ واحد، 16 سطراً مغيَّراً: 12 إضافة و4 حذف، في 8 كتل):**
(١) **حقل جديد خادمي واحد: `scanProtocol/{afterScanId}.linkModelCheck`** — `null`، أو إحدى ثلاث: `LINK_MODEL_MATCH` · `LINK_MODEL_MISMATCH` · `LINK_MODEL_ABSENT` (مجموعة المفردات `LINK_MODEL_CHECK`، ثلاثة صفوف وسمها `proposal` A3). **لا يصل العميل ولا السلك:**
ليس في أيّ طلب ولا ردّ ولا `verifyReport` ولا `reports` ولا تقرير العميل/المورّد (يقول العقد: «خادمي (لا يصل العميل)»)؛ (٢) **ست من الكتل الثماني داخل المقطع A** (نصّ فقط: `scanProtocol` بستّة عشر حقلاً؛ ملاحظة الظهور وصفّ الحقل في جدول الحقول الخادمية؛
بند جديد في A3 «فحص الطراز عند الاكتمال» حيث يصير `DIFFERENT_MODEL` ملاحظةً خادمية بلا رفض ولا نصّ للفنّي؛ بند المؤجَّل والقرار ٨ أُعيدت صياغتهما؛ ملاحظة «بُني (S10)»)، **والكتلتان الباقيتان خارج المقاطع الثلاثة** (ملاحظة عن `unlink`/`relink` عند المشرف،
والصفوف الثلاثة الجديدة للمفردات وهي تقع **بعد** آخر صفّ في المقطع B مباشرةً، فلا تحملها النسخة ولا يقارنها التطبيق بشيء: لا تعداد في التطبيق لحقل لا يصله)؛ (٣) **لا شكل طلب ولا ردّ تغيّر في الدوالّ الثلاث**؛ والمقطعان B وC بلا تغيير. **هل تغيّر صفّ سلك يخصّ
التطبيق؟ لا.** (الفرق الحرفي: الأمر نفسه في مستودع الخادم.)

**من يقرؤها:** `MaintenancePairContractCopyTest` يستخرج منها صفوف المفردات (بين علامتَي `VOCAB-ROWS`) ويقارنها بالتعدادات في الكود: `DefectType` و`ScanPurposeChoice` و`PairRefusal` (سبعة عشر) ورفوض `setSessionPurpose`؛
فقيمة تنجرف عن الجدول تُخفق هناك لا على هاتف فنّي (القاعدة 24). ويقارن بصمة كل مقطع بما في الرأس، ويقرأ من نصّ المقطع A قاعدة الجزأين (فقرة «ما عُولج: ق31») فيقارنها بأسماء الجزأين في `ProtocolPart`.

### ٢) عيّنات الردود الذهبية — `maintenance-pair-wire-samples-neuralcheck-backend-b0a0f66.json`
نُسخت **بايتاً ببايت** من `functions/test/fixtures/golden/maintenance-pair-wire-samples-2026-10-22.json` عند `b0a0f66` (الالتزام الذي أعاد توليدها `b0a0f66` نفسه، ق31؛ وقبله `b76c791` S8، وأضافها أوّلاً `f026943` S7). **sha256
`d0ad3015c8a845fbf682f8a38919e61388ce82e2fef02120a1b77b9a38cc9422`** (107623 بايتاً) — تحقّقتُ بنفسي أنّ `git show b0a0f66:<path> | sha256sum` يعطيها نفسها، وأنّ الملفّ هنا مطابق بـ`cmp` لما استُخرج، وأنّه LF بلا `\r` (`-text`
في `.gitattributes`). **54 عيّنة: 16 لـ`listOpenMaintenanceBefores` (7 ردود و9 رفوض)، 28 لـ`confirmMaintenanceAfterLink` (3 ردود و25 رفضاً)، 10 لـ`setSessionPurpose` (ردّ واحد و9 رفوض).** (كانت 50 عند b76c791 بصمتها
`6c74c05261fac81d14e998bd3cf0998635464e63fab6e27bcc8433db0296d52a` — 16/28/6؛ و44 عند 0b89db1 بصمتها `80a7db384d32905a3652266a524deefda4300c2899c643fcfc251f3760bef3f5`.) **الفرق مُقاس بمقارنة JSON مباشرة بين الملفّين: 4 عيّنات جديدة
وصفر محذوفة؛ الخمسون القديمة لم يتغيّر منها شيء إطلاقاً (لا `body` ولا `seed` ولا `request`)، وترتيبها كما هو، و`_provenance` وبقية مفاتيح الجذر كما هي؛ وبمقارنة النصّ: 206 سطراً زائداً وصفر محذوف.** الجديدة، كلها لـ`setSessionPurpose`:
`setSessionPurpose.refused.part-not-enabled.purposes` (بذرة المالك `protocolParts: {batteryBeforeAfter: true}`، والطلب `maintenance_before`/`BATTERY`) · `setSessionPurpose.refused.part-not-enabled.battery-before-after` (`{purposes: true}`،
`maintenance_after`/`BATTERY`) · `setSessionPurpose.refused.part-not-enabled.owner-absent` (لا مستند مالك في البذرة) — الثلاث بـ`PURPOSE_PART_NOT_ENABLED` و`FAILED_PRECONDITION` — و`setSessionPurpose.success.normal-without-parts`
(مالك بلا `protocolParts` أصلاً، والطلب `normal`، فقبله الخادم: `{sessionId, purpose: "normal", purposeSource: "selected"}`). **الرمز الجديد الوحيد في العيّنات `PURPOSE_PART_NOT_ENABLED`.**
**عند f3c1bab (ق36) — مقارنتي أنا:** `git show f3c1bab:functions/test/fixtures/golden/maintenance-pair-wire-samples-2026-10-22.json | sha256sum` تعطي `d0ad3015c8a845fbf682f8a38919e61388ce82e2fef02120a1b77b9a38cc9422` نفسها، وبـ`cmp` الملفّ المستخرج مطابق بايتاً
لما في هذا المستودع؛ فالعيّنات الأربع والخمسون هي هي، **واسم الملفّ يبقى باسم الالتزام الذي نُسخ عنده (b0a0f66)** ولا حاجة لنسخ ثانٍ. (الحقل الخادمي الجديد `linkModelCheck` لا يحمله ردّ في العيّنات، ويثبّت ذلك `MaintenancePairWireSamplesTest`.)
كل عيّنة: `id` · `function` · `note` · `request` · `seed` · `httpStatus` · `body`، و`body` هو جسم السلك: `{result}` أو
`{error: {status, message, details: {reasonCode}}}`. **ما هو حقيقيّ وما هو بديل — بنصّ `_provenance` في الملفّ نفسه (لم يتغيّر بين النسخ):** الدالّتان بمعالجَيهما الكاملين وغلاف `onCall` الحقيقي حقيقيّات؛ `db` في الذاكرة بمستندات اصطناعية وساعة
مجمَّدة (2026-10-22T12:00:00Z) بديلان؛ **لا Firestore ولا محاكٍ ولا إنتاج** (غير مُتحقَّق: سلوك الدالّتين على Firestore الحقيقي، وكون الاستعلام بمساواتين يُخدَم بدمج فهرسين مفردين — يقول العقد نفسه إنه لم يُتحقَّق منه على الإنتاج).
**حقل `request` في كل عيّنة هو ما بناه مولّد الخادم ليشغّل الدالّة، لا عيّنة طلب من التطبيق** (عيّنات طلبات التطبيق في `derived/`).

**من يقرؤها:** `MaintenancePairWireSamplesTest` على **كلّ** الأربع والخمسين، عبر الخدمة الحقيقية و`PairLinkClient` (والرفض يُسلَّم لمحلّل الـSDK نفسه بجسم الخادم نفسه): كل ردّ قائمة يُقرأ بساعاته ومعرّفاته بترتيبها ووقته `scannedAtMs`
المطابق لـ`pair.openedAt` في بذرته وحالة اسمه (وقد صار `pair.ownerId` في البذرة = معرّف المستدعي)؛ كل تأكيد يُقرأ تأكيداً لطلبه هو، والطلب الذي يبنيه التطبيق يساوي ما شغّلته عيّنة الخادم؛ كل رفض يُقرأ بالرمز الذي يحمله ويذهب لنصّ ذلك
الرمز (`PAIR_OWNER_MISMATCH` إلى نصّ ص14 «هذا الفحص غير متاح للربط» بالرمز في السجلّ بلا نصّ جديد: قرار المنسّق؛ و**`PURPOSE_PART_NOT_ENABLED` إلى حالته الخاصة `PurposeFailure.PartNotEnabled` ونصّها ص21** «الغرض غير مفعَّل لحسابك»، المقترح
وغير المعتمد وغير المكتوب في `strings.xml`، بالرمز في السجلّ)؛ ومفتاح غاب أو صار `null` أو بنوع آخر في أيّ ردّ يجعله غير مقروء (لا قيمة افتراضية)؛ ولا طلب من الستّة المرفوضة لذات الطلب يستطيع التطبيق بناءه؛ **والطلبات الثلاثة المرفوضة لأجزاء
المحلّ يستطيع التطبيق بناءها ولا يرسلها: لكل منها أعطى الاختبار الخدمةَ الحقيقية `protocolParts` المحلّ نفسه الذي بذرته عيّنة الخادم (أو غيابها حين لا مستند مالك) فلم يغادر شيء الهاتف وسُجِّل سبب التجاهل بالاسم**؛ وردّ `normal` الناجح يُقرأ
قبولاً للغرض ويُسجَّل بمفاتيحه (`purpose.purposeSource.sessionId`)؛ ولا ردّ في العيّنات يحمل مفتاح `ownerId` (هو حقل خادمي لا مفتاح سلك). **لا تُعدَّل عيّنة ليمرّ قارئ.**

### فروق مُبلَّغ بها (القاعدة 29)، لا مُرقَّعة
١. **ردّ القائمة يسمّي الوقت `scannedAtMs` (عدداً صحيحاً بالمللي ثانية) لا `scannedAt`، و`deviceName` كائن `{state, model, names, reason}` لا نصّاً** — كما في العقد وفي العيّنات؛ والأمر وWIRE-SPEC يكتبان `scannedAt` ونصّاً. التطبيق يقرأ كما في العقد، ومفتاح `scannedAt` يُعلَن في السجلّ مفتاحاً غير معروف ولا يُقرأ وقتاً.
٢. **رموز `PAIR_*` الإضافية (سبعة)** في A3 البند ٨-٤ لم يسمّها الأمر؛ التطبيق يسمّيها كلها (`PairRefusal`) ومعها `PAIR_OWNER_MISMATCH` (S8).
٣. **وجدت عيّنات الـ44 فجوة في قارئ التطبيق:** `SET_PURPOSE_INVALID` (ردّ الخادم على غرض ليس من الثلاثة، ومنها `battery_before`) كان يُقرأ «غير معروف» ويضيع رمزه من السجلّ؛ سُمّي في `SessionErrorCode` (والتطبيق لا يرسل غرضاً كهذا أصلاً).
٤. فيما عدا ما يلي **لا فرق** بين القارئين وأيّ عيّنة من الأربع والخمسين؛ والرمز الجديد في S8 (`PAIR_OWNER_MISMATCH`) سُمّي في `PairRefusal` بقرار المنسّق، والجديد في ق31 (`PURPOSE_PART_NOT_ENABLED`) سُمّي في `SessionErrorCode`
   (وهو مصنَّف بـ`details.reasonCode` وحده: القاعدة 21) وله حالة `PurposeFailure.PartNotEnabled` ونصّ ص21؛ **وبلا التسمية يُقرأ الرمز «غير معروف» (`ReasonCodeRead.Unrecognized`) ويُخفق اختبار `every reasonCode the samples carry is one the app names`** (طُبّقت طفرة بحذف التسمية فأخفق).
٥. **الخادم أوسع من التطبيق في `normal`:** عيّنة `setSessionPurpose.success.normal-without-parts` يقبل فيها الخادم `normal` لمالك بلا `protocolParts` أصلاً (العقد: «`normal` يبقى مقبولاً كما كان»)، والتطبيق لا يرسم لمحلّ كهذا رقائق أصلاً
   (`PurposeUi.NotOffered`) فلا يرسل `normal` له. **ليس عطلاً:** الشرط الأضيق في التطبيق هو أمر المنسّق (رقاقة «عادي» بـ`purposes` وحده)، والخادم لا يشترط للأصل إلا الأوسع.
٦. **الجزء الناقص لا يصل التطبيق:** سجلّ الخادم يسمّي الجزء الذي نقص (`part=purposes|batteryBeforeAfter`) أمّا جسم الردّ فلا يحمل إلا الرمز؛ فنصّ ص21 عامّ («الغرض غير مفعَّل لحسابك») لا يسمّي الجزء، والتطبيق يعرف الجزأين من ردّ الإنشاء
   ويسجّلهما في سطر `session-protocol-parts` (`purposes=` و`batteryBeforeAfter=`) فيُرى الناقص منهما في سجلّ الهاتف.
٧. **حقل خادمي جديد لا قارئ له في التطبيق (ق36):** `scanProtocol.linkModelCheck` (و`LINK_MODEL_*` الثلاث) في العقد عند f3c1bab «خادمي (لا يصل العميل)». التطبيق لا يقرؤه ولا يسمّيه: لا تعداد ولا نصّ، ويثبّت ذلك
   `MaintenancePairVisibilityTest` (لا ملفّ في `app/src/main` يذكر الحقل ولا قيمه، ولا حقل في ردّ التحليل بهذا الاسم)، و`MaintenancePairWireSamplesTest` (لا ردّ في العيّنات يحمله)، و`MaintenancePairContractCopyTest` (النسخة تقول إنه خادمي).

## نسختا عقد عند c289a2d — إضافة 2026-10-23 +03 (أمر المنسّق «GO APK FINAL»؛ القواعد 7 و22 و24 و25 و29 و41)

**منقولتان حرفياً من `neuralcheck-backend`، لا مُولَّدتان هنا، ولا تُعدَّلان.** قرأتُ بـ`git -C <backend> show c289a2d:<path>` فقط (لا كتابة في ذلك المستودع). **c289a2d = `c289a2dc3ec4b52624398737b7a6e838483ca0be`** (2026-10-23T03:46:12+03:00، «consent: CONSENT_TEXT_VERSION من 1 إلى 2»؛ `master` و`origin/master` هناك كلاهما عنده بـ`git rev-parse`)؛ **كونه منشوراً غير مُدَّعى**. الملفّ المصدر كلّه عند ذلك الالتزام: sha256 `c5e207ef1790c780079165be5f026296997535be54f11c574e09dbf3dabbc6b9` (507555 بايتاً، بلا `\r`؛ تحقّقتُ من الطرفين: بصمة `git show … | sha256sum` وبصمة الملفّ المكتوب).

### ١) `protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md` — يحلّ محلّ نسخة f3c1bab (في تاريخ git عند `d65c2d4`)
**المقاطع الثلاثة نفسها بالمراسي نفسها، وبالبصمات نفسها حرفاً بحرف:** A (A2 وA3) `d71e9eba275c8e71b80df286826d38126c482733008793bb6e44b5a9300ada75` — الأسطر 116-242 (لم تتحرّك) · B `abd16fc754bb0dae9cb0dd1c174640524d9e5560da4e80403b07aaa51b93b489` — 1154-1205 (كانت 1113-1164) · C `681ac4f38715a9c91ba933cc9a6fe310302ea11f2733ce1c69534572bf4b6f6a` — 1308-1312 (كانت 1267-1271). تحقّقتُ من كلٍّ بـ`git show c289a2d:docs/PROTOCOL_CONTRACT.md | sed -n '<from>,<to>p' | sha256sum` فأعطت البصمة نفسها المكتوبة في الرأس. والنسخة كاملةً (الرأس الإنجليزي + المقاطع) `a3c24f714a338de2dab04a93296f43c0959fcb9aade322c12f66596742d66afd`. `.gitattributes` يضعها `-text`.

### ٢) `protocol-contract-A8-consent-neuralcheck-backend-c289a2d.md` — **جديدة** (لم يكن للتطبيق نسخة من A8: قارئاته كانت مربوطة بعقد منقول في مساحة العمل)
ثلاثة مقاطع بمراسيها: D قسم A8 كلّه (من سطر `### A8 ` إلى آخر سطر غير فارغ قبل `### A9 `) الأسطر 377-430 `daa9eb7927831ffeb01e2482782e0453a36950826dfccc6ad8bd22c6dbb5e104` · E صفوف `CONSENT_STATE` و`CONSENT_LOCALE` 1328-1333 `6292765757bccfdd883c22c5d7cf87f34c21782196ae2a1077ef2aaa0484e611` · V صفّ `CONSENT_TEXT_VERSION` 1376 `a176b57775d58d1670789bf0ca9badcf3e4bfee5cb66938c7c28124c894f5c73`؛ كلٌّ مطابق لـ`git show … | sed -n | sha256sum`. والنسخة كاملةً `f8bcc6eb18648540126b3db6657cc747b96f046823a0d0e12e709997682d4f66`. **يقرؤها `ConsentContractCopyTest`**: شكل ردّ الانضمام (`consentEnabled`) ومفتاح الحقل الذي يقرؤه، وطلب `recordScanConsent` وردّه، والرفوض التسعة، ولغتا `ar`/`tr`، والحالة `accepted`، وصفّ النسخة `2` مقابل `ConsentTextVersion.CURRENT`، وأنّ التطبيق لا يتهجّى `inherited` ولا `declined` ولا `not_collected` في أيّ ملفّ رئيس.

### ٣) الفرق f3c1bab ← c289a2d — قرأتُه بـ`git diff f3c1bab c289a2d -- docs/PROTOCOL_CONTRACT.md` (ملفّ واحد، 99 سطراً في المخرَج، 47 إضافة و5 حذف، في 6 كتل (hunks)؛ المخرَج كاملاً في `scratchpad/app-delegated/contract-diff-f3c1bab-c289a2d.txt`)
(١) **A4 — ق10:** إعفاء فحص «بعد» (`maintenance_after`) من الخصم في مرحلة الجمع بآلية `PROTOCOL_EXEMPT` القائمة؛ **خادمي بحت** (لا حقل ولا رمز على السلك)؛ لا قارئ له في التطبيق. (٢) **A8 — ق40:** رفض `recordScanConsent` لمحلّ بلا جزء `consent` برمز `CONSENT_PART_NOT_ENABLED` (`failed-precondition`) — **يمسّ قارئاً عندي**: `ConsentRefusal.PART_NOT_ENABLED` ⟵ `ConsentUi.NotApplicable` («لا ينطبق»). (٣) **A8 — ق15:** `joinScanSession` يردّ `consentEnabled` منطقياً دائماً — **يمسّ قارئاً**: `JoinSessionResult.consentEnabled` ⟵ `ConsentPartRead` ⟵ `afterJoinRoute`. (٤) **A8 — ق14:** الجلسة الابنة ترث موافقة أمّها: `scanProtocol.consent.state = "inherited"` — **حقل خادمي لا يصل العميل**؛ لا قارئ في التطبيق (يثبّته الاختبار). (٥) **A8 — `CONSENT_TEXT_VERSION` من 1 إلى 2** والنسخة 1 تبقى مقبولة — **يمسّ قارئاً**: `ConsentTextVersion.CURRENT = 2`. (٦) **A10 — S11:** أداة `set-maintenance-settings.js` خادمية بلا سلك؛ لا أثر. (٧) المفردات: صفّ جديد `CONSENT_STATE`/`INHERITED` (`proposal`، A8) وصفّ `CONSENT_TEXT_VERSION` من `1` إلى `2`. **لا شيء من ذلك داخل المقاطع A وB وC.** ولا شكل طلب ولا ردّ تغيّر في الدوالّ الثلاث للصيانة.

### ٤) العيّنات — أُعيد نسخها وتحقّقتُ بالبصمة الكاملة؛ **لم يتغيّر مخرَج واحد**
- `maintenance-pair-wire-samples-neuralcheck-backend-b0a0f66.json`: `git show c289a2d:functions/test/fixtures/golden/maintenance-pair-wire-samples-2026-10-22.json` يعطي sha256 **`d0ad3015c8a845fbf682f8a38919e61388ce82e2fef02120a1b77b9a38cc9422`** (107623 بايتاً) نفسها، وبـ`cmp` بين الملفّ المُعاد استخراجه وما في المستودع: مطابق بايتاً. فالعيّنات الأربع والخمسون هي هي، **واسم الملفّ يبقى باسم الالتزام الذي نُسخ عنده (b0a0f66)**.
- `derived/maintenance-pair-request-*.json` (خمس): sha256 كاملة مطابقة لما في الخادم عند c289a2d، و`cmp` مطابق: `confirm-maintenance-after-link` `4481f33e…5375d1` · `list-open-maintenance-befores` `661fdbab…90cf2f7` · `set-session-purpose-maintenance-after-charging-port` `1cd639ca…23bb684bb` · `set-session-purpose-maintenance-before-battery` `cc058918…c1683e` · `set-session-purpose-normal-no-defect-type` `04ab287a…8e74bcb` (البصمات الكاملة في `scratchpad/app-delegated/goldens-compare-c289a2d.txt`).
- وبقيّة ما هو منقول في هذا المجلّد من الخادم (`v6-reports-document-sample.json` وعيّنات V7 الستّ لمستند `reports` والأربع لحمولة العميل): **كلّها مطابقة بالبصمة الكاملة لما في الخادم عند c289a2d** (11 ملفّاً: SAME)، رغم أنّ التزامات `fix(testKinds)` و`fix(categoryStates)` و`fix(reevaluationDecision)` (ق١ ق٢ ق٣ من `V7_DISPLAY_SHAPE_CONTRACT §١٦`) بين f3c1bab وc289a2d؛ أي أنّ عيّنات الخادم الذهبية لم تُعَد توليدها، ولا أعرف (غير متحقَّق) هل يتغيّر مخرَج الخادم الحيّ لفحص يدوي حالته غير معروفة: هذا خارج حدودي ولا عيّنة له عندي.

## إعادة مزامنة 2026-10-27 +03 — `neuralcheck-backend@05015b4` (`19a2836`: رفع ختم `evaluationRulesVersion`، ق٢، قرار حسن 2026-10-23)

استُبدلت العيّنات الستّ لمستند `reports/{scanId}` — `v7-reports-document-sample.json` و`-honor.json` و`-security-downgrade.json`
و`-cpu-c06-downgrade.json` و`-zero-measured.json` و`-zero-measured-battery-harness-error.json` — **نسخاً حرفياً، بايتاً ببايت،** من الخادم:
`git -C <neuralcheck-backend> show 05015b4:functions/test/fixtures/golden/<الاسم>` إلى هذا المجلّد بالاسم نفسه، بلا تحويل نهايات أسطر. **المصدر:
`neuralcheck-backend@05015b4`** (`05015b4ff69293105228a255da5d4e8927e598c7`، 2026-10-27T10:09:24+03:00، وهو `master` هناك عند القراءة بـ`git rev-parse`)؛
**كونه منشوراً غير مُدَّعى.** التزام رفع الختم فيه `19a2836` (`19a283652b8473e2f748a876371707352a96be05`، 2026-10-27T10:08:55+03:00)، والقيمة في
`functions/src/analysis/evaluationRulesVersion.js:39` (قرأتها بـ`git show`): `const EVALUATION_RULES_VERSION = 'rules-2026-10-23-hardware-absent-coverage';`.

**السبب** (قرار حسن 2026-10-23 كما نقله أمر المنسّق، ونصّ التزام الخادم `19a2836` يسمّي القرار نفسه): ختم قواعد التقييم يُرفع قبل النشر ليميّز التقارير قبل ق٢ وبعده.
وصفّ الخادم في `docs/PROTOCOL_CONTRACT.md` عند `05015b4` (قرأتُه بـ`git diff c289a2d 05015b4`، سطر واحد) يقول: «ق٢ يغيّر تعريف التغطية؛ V7 §١٥». وقول رسالة `19a2836` إنّ لا ردّ من `analyzeDevice` ولا `verifyReport` يحمل اسم الحقل ولا قيمته **منقول من رسالة الالتزام، غير متحقَّق منه من هذا المستودع** (والتطبيق على أيّ حال لا يقرؤه).

**المُتحقَّق هنا من blob الخادم نفسه (لا من نقل):** (١) sha256 لكل blob في `05015b4` يطابق القيمة التي نقلها أمر المنسّق عن تقرير كاتب الخادم (ستّ مطابقات)؛ (٢) sha256 كل ملفّ بعد النسخ يطابق blob الخادم؛
(٣) بعد `git add` معرّف الـblob في الفهرس (`git ls-files -s`) يساوي معرّف blob الخادم (`git rev-parse 05015b4:<path>`) في الستّة، أي بايتاً ببايت؛ (٤) لا `\r` في أيّ منها.
**الفرق القديم ← الجديد — مُحقَّق بـ`git diff -U0` مباشر:** في كلٍّ من الستّة سطر واحد تغيّر، والسطر 10 من الجذر،
`"evaluationRulesVersion": "rules-2026-10-13-zero-cycles-unsupported"` ← `"evaluationRulesVersion": "rules-2026-10-23-hardware-absent-coverage"`؛ **لا مسار آخر تغيّر ولا قيمة** (والحجم +1 بايت في كلٍّ منها: القيمة الجديدة أطول بحرف).

| الملفّ | القديم (sha256، نسخة c289a2d، بايتاً) | الجديد (sha256، نسخة 05015b4، بايتاً) |
|---|---|---|
| `v7-reports-document-sample.json` | `b05cc9aafee213731b104f6c9d254fc6f8d964c4b167b8de841493ed270dd706` (30473) | `7868c2968a793701e2462188a1f4cbed5a110c17dc4a523e81d80e5f4cc4c1a6` (30474) |
| `v7-reports-document-sample-honor.json` | `fdbea3e975ddeed611ab6491d66e934a60c67fb0787dd7a33b76557d033c1ec6` (9200) | `2ff7c3c8abcfb09600e48fb97cc3c91e5179dd033fcf6edad828c68b02d15c06` (9201) |
| `v7-reports-document-sample-security-downgrade.json` | `e06162ed538ec93c58d454080bd7795a4bca1340121308aaa8b2282d26fbab6d` (28403) | `9291df36b1473af98c6ea977c8071d356331cda0c7f7d59f311ad5c6b6e8fd8f` (28404) |
| `v7-reports-document-sample-cpu-c06-downgrade.json` | `b4f162fd0ca843e22d98c478f5ac1fb3339189a1f43533278b8972e2b42dc7be` (28454) | `1382a97ba93923014c66b5fa6c6a0a9cde5427e826acc62666e27ac7cfa6bca7` (28455) |
| `v7-reports-document-sample-zero-measured.json` | `c990ae19a9f52d78f41994f8cd7187baf5e1a24ee7d419d441c80221bcee4d1a` (27116) | `f1c1502152e8c5771eceac9e4bd2af291bee7688e49724c7be7c7ef97faa80fa` (27117) |
| `v7-reports-document-sample-zero-measured-battery-harness-error.json` | `769100459edd49b6a15022ac4ac6ccb1f2c79f5e8110a8ec5bbe1d199e785c24` (24241) | `e9d505ef56f72e0b892cd192586e9d76c5f9dfe8c37d8f036e1787c6a34735cb` (24242) |

(القديم هو الجديد في قسم 2026-10-13 أعلاه، وقد بقيت نسخ c289a2d مطابقة بالبصمة الكاملة لخادم c289a2d وقت قسم 2026-10-23.)

**ما لم يُنسَخ لأنه لم يتغيّر في الخادم** (قورن كل ملفّ في هذا المستودع باسمه بـ`05015b4:functions/test/fixtures/golden/<الاسم>` بالبصمة الكاملة، بسكربت مقارنة قراءةٍ فقط في scratchpad الجلسة، غير مُلتزَم): مطابق بايتاً — عيّنات الحمولة الأربع
(`v7-client-payload-sample*.json`)، و`v6-reports-document-sample.json`، وملفّ عيّنات ردود ربط الصيانة (54 عيّنة، المنسوخ باسم الالتزام b0a0f66، بصمته `d0ad3015…`)، والخمسة `derived/maintenance-pair-request-*.json`، والستّة `derived/fv71-app-request-payload-derived-from-*` (ثلاثة `.json` وثلاثة `.keys.txt`):
**17 مطابقاً و6 مختلفاً (هي الستّ أعلاه) و5 بلا نظير بالاسم في مجلّد الخادم** (`derived/fv67-golden-…`، والثلاثة `derived/technician-form-request-*`، و`derived/v7-categories-from-device-exports-20261001.json`: مشتقّات من التطبيق أو نسخ بأسماء أخرى، لا تُقارَن بالاسم).
والمقاطع الستّة من نسختَي العقد (`protocol-contract-A2-A3-…-c289a2d.md` وA8): أُعيد قصّها بالمراسي نفسها من `05015b4:docs/PROTOCOL_CONTRACT.md` فجاءت بصمات المقاطع A وB وC وD وE وV **هي هي** حرفاً بحرف، فلم يُعَد نسخها ولا تغيّر اسمها
(الملفّ كلّه تغيّر عند الخادم من `c5e207ef…` إلى `d64b4d2e98ab764e95a77e5b666b1be87d63c0eafc9d7c82139a79a791c41d77`، وفرقه سطر واحد خارج المقاطع: صفّ `evaluationRulesVersion` في جدول المفردات). وفي مجلّد العيّنات الذهبية بالخادم لم يتغيّر بين c289a2d و05015b4 غير هذه الستّ و`README.md` عنده.

**الحقل لا يقرؤه التطبيق ولا قيمته:** `git grep -n -i -e evaluationRulesVersion -e EVALUATION_RULES_VERSION -e rules-2026 -- app/src/main` يعيد لا شيء (خروج 1). واختبار `ReportsDocumentEvaluationRulesVersionTest` لا يثبّت القيمة
(وجود المفتاح ونوعه النصّي وعدم فراغه، ولا ذكر له في `app/src/main`)، فلم يتغيّر، ولا اختبار غيره يحمل القيمة القديمة. **لا قارئ أُضيف.** السطران 468 و477 من قسم 2026-10-13 يصفان الحال حينذاك وأُبقيا لا محذوفين (القاعدة 8).
