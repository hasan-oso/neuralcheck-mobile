# عقد fieldsVersion 68 — وثيقة، لا كود

| | |
|---|---|
| **الحالة** | **fv68 مكتمل — 2026-10-08 +03، في الالتزام الأخير من الدفعة ٣ (الجزء ٣)؛ الختم يبقى 68 ولم يُبنَ APK؛ وما لم يدخله وما يحتاج قراراً في §٧-و «قيود معروفة».** **ثم أُدخل ت-02 (عدد الدورات من `EXTRA_CYCLE_COUNT`) داخل fv68 بقرار المستخدم، §٧-ز، 2026-10-08: الختم يبقى 68، ولا APK.** **محسوم بقرارَي المستخدم 2026-10-08 +03** (V8 الدفعة ٣: الخطوة ٠، ثم الجزء الأوّل). الوثيقة تسبق أيّ كود: يتحقّق منها المنسّق ثم يصدر أمر التنفيذ. **س-27 إلى س-35 محسومة** بقرار الجزء الأوّل (§٥)، و**س-19 وس-28 مصحَّحان** به. **مفتوح بعد:** س-22 (فحص خادم) وشرط ت-20 «لا يمسّ حمل المعالج المقيس» (D نُفِّذ في §٧-ج؛ والشرط صار بقرار 2026-10-08 «مجموع زمن D في المرحلة ≤ 1% من زمنها»، يُقاس على S10 وS21 في فحص الجهاز — §٧-د). **حالة التنفيذ: §٧ (الدفعة ٣ ج: الحامل · الأسماء والقارئ · تخطيط العيّنة) و§٧-ب (الجزء ٢-أ: القوائم · الأزواج · الافتراضيات · حرارة المعالج · L-02 · الإذن · الإعلانات) و§٧-ج (الجزء ٢-ب: منتِجا التحقّق · D للمعالج · الملفّ القديم · صفّ 10 · مؤشّرات البطارية · `not_written` · التركي) و§٧-د (الجزء ٢-ج: قرارات المستخدم على ٢-ب) و§٧-هـ (شرط الذاكرة الحرّة: القراءة (ب)) و§٧-و (الجزء ٣: العيّنة الذهبية لحمولة التطبيق · حارس القاعدة 39 · إعلان fv68 مكتملاً).** ما لم يحسمه النصّ يبقى سؤالاً موسوماً في §٥ (القاعدة 13) |
| **fv69 (2026-10-10 +03)** | **الدفعة ١٠ (خطوات الفنّي) تنتقل إلى fv69، لا fv68:** الحالات الأربع و`manualResultsHeader.hiddenByModel` (N-17) وشبكة اللمس M-02 (N-19 المعدَّل) وتغطية الإضاءة SN-04 (N-20) و«انتظر». قسمها **§٧-ح** (بنصّ قرارات المستخدم الثلاثة 2026-10-10). **الختم 69 وُضع في أوّل التزام يغيّر الشكل (`8132327`)**؛ ولا يُبنى APK (أمر الدفعة). سطر «fv68 مكتمل» في §٧-و يبقى صادقاً لـ fv68 ويُحيل إلى §٧-ح |
| **fv70 (2026-10-13 +03)** | **إصلاح D (فحص المعالج بالجواب المعروف) ينتقل إلى fv70:** غربال D حتى 1,000,000 (المرجع 78,498) بدل 10,000,000، وسقف زمن بحسب القاعدة «(الزمن حتى الآن + أطول تشغيل سابق) > 1% من المرحلة ⇒ `skipped:time_budget`»، و`sieveLimit` لكل تشغيل، والحالة الإجمالية `partial:time_budget`، وبيان أن D لم يعد يمرّ على الذاكرة الرئيسية. قسمها **§٧-ط** (بنصّ قرار المستخدم 2026-10-13). **P6-13 ينتقل إلى fv71** (قرار المستخدم 2026-10-13؛ منقول، غير متحقَّق). **الختم 70 يوضع في أوّل التزام يغيّر الشكل**؛ ولا يُبنى APK (أمر الدفعة) |
| **fv71 (2026-10-15 +03)** | **عقد حمولة، لا كود: M-02 الشبكة الجديدة بالخامّ (الخيار ب) + أزرار الصوت M-09/M-10 بأحداث المفاتيح مع الخامّ + P6-13 (توحيد الظروف: `powerSaveState` و`brightnessLock` وحدّا الشحن 30–85%).** قسمه **§٧-ي** (بقرارات المستخدم بنصّها، منقولة). **الختم يبقى 70** حتى أوّل التزام كود يغيّر الشكل (أمر لاحق)؛ ونشر قرّاء الخادم يسبق أيّ تطبيق يرسل fv71؛ ولا APK. **`partial:time_budget` ليست من fv71 (هي حالة D في fv70).** الأسماء `[م]` وأسئلة E18–E22 (مقترحي وسببه) تنتظر الاعتماد |
| **المستودع** | NeuralChek — التطبيق، منتِج الحقول |
| **الفرع · HEAD** | `master` · `d87f2d9` عند كتابة قرار الجزء الأوّل. الكود مطابق لما قيست عليه أسطر §١ (`ebb4b3b`): `git diff --name-only ebb4b3b HEAD` ⇒ `docs/FV68_CONTRACT.md` وحدها [أ-١٣]. **وهذه الجملة تصدق قبل أول التزام كود من الدفعة ٣ ج فقط؛ بعده تنجرف أسطر §١ وتُقرأ بـ`git grep -n`** |
| **التاريخ** | المسوّدة 2026-10-08 +03 · الحسم الأوّل 2026-10-08 +03 · قرار الجزء الأوّل (س-27..س-35، التصحيحان، D للمعالج) 2026-10-08 +03 |
| **قاعدة الختم (من المنسّق، قاعدة هذه الدفعة)** | حادثة A70 كانت تغيير شكل بختم قديم. فـ`fieldsVersion` يصير **68 في أوّل التزام يغيّر شكل الحمولة أو الملف**، لا في آخر الدفعة. **وجملة العقد: «fv68 مكتمل عند الالتزام الأخير من الدفعة ٣؛ ولا يُبنى APK للإصدار قبله».** **تحقّقت 2026-10-08: الالتزام الأخير من الجزء ٣ أعلن الاكتمال (تعليق الختم وكتلة التاريخ في `app/build.gradle.kts`)؛ وبناء الإصدار قرار المستخدم بعده.** **ثمّ أُدخل ت-02 داخله (§٧-ز، 2026-10-08)؛ الختم لم يتغيّر.** أي أن كل التزام بين الأوّل والأخير يحمل الختم 68 وهو ناقص بالتصميم — فلا بناء إصدار منه (ت-19) |
| **الأمر** | المسوّدة: V8 الدفعة ٢ «عقد fv68 وثيقةً» — بند الخطة (PLAN-05 §١٠، منقول): «عقد fv68 (§٧-أ) مكتوباً قبل أيّ قارئ (القاعدة 7)، وتعداد كل قارئ له (القاعدة 25)». هذه النسخة: V8 الدفعة ٣ الخطوة ٠ — «كتابة قرارات المستخدم في docs/FV68_CONTRACT.md قبل أيّ كود» |
| **ما لا تفعله الوثيقة** | التزام الوثيقة لا يُغيّر `fieldsVersion` (يبقى 67 حتى أول التزام كود يغيّر الشكل — قاعدة الختم أعلاه)، ولا يعدّل كوداً أو اختباراً، ولا يضيف نصّاً لواجهة المستخدم (القرار ٩٠) |
| **ما لا تغطّيه** | M5 (زمن إطار الرسوميات بسياج) ليس في fv68 هذه ولا قناته. M8 وM9 خارج الدفعة بنصّ المستخدم. **وخارج هذه الوثيقة كلّياً:** حروف الفئات (الخادم)، وتقييما «الحرارة أثناء الاستعمال» و«الأداء في الألعاب» (عقد التقييم، الدفعة ١١) |

---

## أسماء تحتاج إقرار المنسّق

**حالة الإقرار (2026-10-08، أمر الدفعة ٣ الجزء الأوّل — منقول عن المنسّق، لا من ملفّ في هذا المستودع):** «N-01..N-07، N-09..N-15، N-17، N-19..N-23 مُقرّة؛ وN-08 مُقرّ (س-33 أ)؛ وN-16 مُقرّ (س-29 أ)؛ وN-18 لا يُرسله التطبيق (س-32 ب)». وبقرار المستخدم نفسه: «والأسماء الثلاثة N-08 وN-16 وN-18 تُقرّ تبعاً لذلك». فالجدول أدناه **مُقرّ كلّه عدا N-18** الذي **لا يُنفَّذ في التطبيق**: التطبيق لا يرسل مفتاح `not_measured` ولا `measurements.notMeasuredReason` — يرسل الخامّ (القوائم، أو «لا قراءات») والخادم يصنّف «غير مقيس» (س-32). وهذا قراءة المنسّق لعبارة «تبعاً لذلك» [ن]. وما وُسم `[م]` في بطاقات §٢ باسمٍ من هذا الجدول يُقرأ مُقرّاً؛ وما وُسم `[م]` وليس في الجدول (مثل أسماء أسباب `not_run:<سبب>` وسقف 200 وأسماء مفاتيح `channelReadFailures`) يبقى مقترحاً حتى يُقرّه المنسّق، ويُذكر في تقرير كل التزام يكتبه.

**الجدول أدناه مكتوب قبل الإقرار؛ وعمود «الاسم المقترح [م]» يُقرأ «الاسم» بعده.** ما حسمه النصّ بالاسم (مثل `charge_counter_raw` · `cpu_freq_policy{N}_khz` · `thermalStatusEvents[]` · `batteryPairs[]` وعناصرها · `cpuPolicies[]` وعناصرها · `hiddenByModel` · `unavailable` · `receiverRegistered`) **ليس** في الجدول. الأسماء تُكتب على السلك (ملف وحمولة) فهي بيانات لا نصّ واجهة.

| ر | الاسم المقترح [م] | الموضع | ما يحمله | بطاقة |
|---|---|---|---|---|
| N-01 | `"as_declared_by_device"` (وحدة) و`BatteryManager.getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER)` (مصدر) | `variables[]` مدخل `charge_counter_raw` | وحدة «كما أعلنها الجهاز» [ح] تحتاج سلسلة محدّدة؛ والمصدر نصّ إعلان | ت-01 |
| N-02 | `thermalHeadroomReadings[]` بعنصر `{t, headroom, state}` + `thermalHeadroomState` | `footer` | قائمة قراءات headroom كل 10 ث؛ `state` نصّ نتيجة النداء (`ok` · `nan` · `exception:<نصّ>` · `api_below_30`)؛ والحالة العامّة للقناة | ت-03 |
| N-03 | `thermalStatusState` | `footer` | حالة قائمة الأحداث (`ok` · `api_below_29` · `listener_registration_failed:<نصّ>`) | ت-04 |
| N-04 | إطار `t` في قائمتي الحرارة = ms منذ بدء الفحص كـ`v[0]` | `thermalHeadroomReadings[].t` · `thermalStatusEvents[].t` | الحسم ثبّت `t` النسبي لـ`batteryPairs` وحدها | ت-03 · ت-04 |
| N-05 | `batteryPairsState` بحقول `receiverRegistered` · `broadcastCount` · `droppedByCap` · `invalidReads` | `footer` (شقيق لـ`batteryPairs[]`) | حالة الغياب الصريحة للأزواج؛ النصّ حسم المفهومين الأولين دون مفاتيح الحاوية ولا الأخيرين | ت-06 |
| N-06 | `unreadableReason` على عنصر `cpuPolicies[]` | `footer.cpuPolicies[]` | سبب غياب `cpuinfoMaxFreqKhz` لسياسة بعينها (نصّ الخطأ، القاعدة 21) | ت-05 |
| N-07 | `channelDiscovery.reasons` (خريطة: اسم القناة ← نصّ الخطأ) | `footer.channelDiscovery` | سبب الحجب/الغياب نصّاً؛ والتطبيق يُعلن السبب والخادم يُسمّي الحالة [ح]. وتبقى مفاتيح `blockedBySelinux` و`missing` كما هي | ت-05 · §٢.٠ |
| N-08 | `cpuTempFinalC` و`cpuTempFinalSource` (بدل `maxCpuTemp`) | `footer` + `TimeSeriesFooterDto` | قيمة نهاية الفحص بما هي + فرعها (`live_read_at_finalize` · `sampler_max_cpu` · `absent:<سبب>`). **القيمة اليوم ليست «آخر قراءة» دائماً — انظر س-33** | ت-10 |
| N-09 | `storageVerifyState` · `storageVerifyBytesCompared` · `storageVerifyDurationMs` · `storageVerifyMethod` | `verificationResults` | حقول تحقّق التخزين الجديدة؛ و`storageWriteVerifyErrors`/`storageReadVerifyErrors` تصيران `Int?` | ت-16 |
| N-10 | `memoryVerifyState` · `memoryVerifyBytes` (+ `memoryMarchErrors` · `memoryWalkingOnesErrors` — **يُشحنان** بقرار س-30 «العدد الخامّ يُشحن») | `verificationResults` | حقول تحقّق الذاكرة الجديدة؛ أمّا `memoryMarchPassed` · `memoryWalkingOnesPassed` · `memoryRandomVerifyErrors` · `memoryTestedBytes` فمن أسماء الخادم المنقولة لا مقترحة | ت-17 |
| N-11 | `gpuComputeGemmState` · `criticalDefectInputsNotRun[]` | `verificationResults` | حالتا الصفّين 1 و2 من جرد س-23 | ت-18 |
| N-12 | `workersPinState` (`not_attempted:ai_workload_path` · `attempted`) | `verificationResults` | الصفّ 4 من جرد س-23 | ت-13 · ت-18 |
| N-13 | `stillnessState` (`measured` · `insufficient_accel_samples`) | `footer.scanStillness` | الصفّ 6 من جرد س-23 | ت-18 |
| N-14 | `preScanWarningsState` (`read` · `read_failed:<نصّ>`) | `footer` | الصفّ 7 من جرد س-23 | ت-18 |
| N-15 | `visibleSatellites` · `usedSatellites` · `timeToFirstFixMs` · `satelliteStatusCallbacks` · `locationUpdateCount` | نتيجة L-02 | رقمان [ح] + أسماء الغياب الصريح اللازمة | ت-11 |
| N-16 | `measurements` (خريطة اختيارية) | `FunctionalResultDto` و`ManualResultDto` | حامل الخام المركّب لنتائج الاختبارات. **حامله في التخزين غير محسوم — س-29** | ت-11 · ت-15 |
| N-17 | `manualResultsHeader` بحقل `hiddenByModel` | `BehaviorFingerprint` (شقيق لـ`manualResults`) | «قائمة hiddenByModel في رأس النتائج اليدوية» [ح]؛ و`manualResults` قائمة فلا تحمل رأساً بلا تغيير شكلها | ت-15 |
| N-18 | مفتاح الحالة `not_measured` وسبب في `measurements.notMeasuredReason` | نتيجة SN-04 · SN-08 · SN-09 | حالة «غير مقيس» المنفصلة عن `not_tested` [ح]. **س-32 (ب) محسوم: لا يُرسله التطبيق** — الخادم يصنّف «غير مقيس» من الخامّ؛ فالاسم لا يُنفَّذ هنا ولا يُضاف إلى `TestStatus.fromString` بسببه | ت-15 |
| N-19 | `gridCols` · `gridRows` · `touchedCells` · `totalCells` · `elapsedMs` · `endReason` · `maxSimultaneousPointers` | `measurements` لـM-02 | الخام الذي حسمه النصّ بوصفه لا بأسمائه؛ و`endReason`: `auto_complete` · `user_marked_not_working` · `user_marked_not_tested` · `dismissed` | ت-15 |
| N-20 | `readingsBeforeCover[]` · `readingsDuringCover[]` بعنصر `{t, values}` | `measurements` لـSN-04 | «قائمة القراءات بطابع قبل التغطية وأثناءها» [ح] | ت-15 |
| N-21 | نصّا `variables[].source` لـ`v[0]` و`gpu_fps`، ووحدة `MiB/s` لـ`v[8]` و`v[9]`، و`verificationResults.storageThroughputSegmentsUnit` | `variables[]` · `verificationResults` | إعلانات تطابق القيمة (ت-12) ووحدات معلنة صراحة (ت-19) | ت-12 · ت-19 |
| N-22 | ترتيب `v[11..]`: `charge_counter_raw` ثم `cpu_freq_policy{N}_khz` بـN تصاعدياً، وتُدرج القناة فقط إن اجتازت الاكتشاف | `variables[]` | ترتيب الإلحاق (النصّ حسم «ثم الإلحاق» ولم يحسم الترتيب) | §٢.١ |
| N-23 | `transitionalAggregates[]` (قائمة أسماء المجمَّعات) | `footer` | إعلان «للعرض الانتقالي» [ح س-20] — النصّ حسم الإعلان ولم يحسم موضعه ولا صيغته | ت-03 · ت-04 |

---

## ما بقي مفتوحاً

* **س-22** — مفتوح بنصّ المستخدم (فحص خادم، خارج حدودي).
* **ت-20 (حرف D للمعالج)** — القرار الشرطي مسجَّل في بطاقته: يدخل الدفعة ٣ إن كان بناء المنتِج ≤ يوم واحد، وإلّا فالمعالج بثلاثة حروف A/B/C في V8؛ **والمستخدم يقرّر بعد رؤية الكلفة** (تقدير ٣-ب في تقرير الوكيل، لا كود).
* **س-27 … س-35 — محسومة** بقرار المستخدم 2026-10-08 (الجزء الأوّل)، نصّها في §٥ بعد كل سؤال بوسم «محسوم»: فشل قراءة قناة مُدرجة في `v[]` · الوحدة والخام في أزواج البطارية (س-28 مصحَّح) · حامل الخام المركّب · جدولة تحقّق الذاكرة والتخزين و`random verify` · `criticalDefectDetected` · «غير مقيس» · حرارة المعالج النهائية · «المحرّك لم يعمل» مقابل «0 أقمار» · سقف نصّ السبب. **وما نشأ عنها من تفاصيل لم يحسمها النصّ** (أسماء مفاتيح `channelReadFailures` وسقفها، أسباب `not_run:<سبب>`…) يبقى `[م]` ويُذكر عند التنفيذ.
* **ما وُسم «ينتظر تأكيداً»** داخل بطاقات §٢: لا قرار فيه.

## تعارضات الحسم مع الكود (وُجدت أثناء الكتابة)

كلٌّ مفصّل في بطاقته؛ هنا فهرسها:

1. **ت-17 / س-30:** `run_memory_stress` ترجع `random_verify_errors = 0` ثابتاً (`production_stress.cpp:474`) وصفراً شاملاً عند فشل التخصيص (`:426` · `:464`) — وصلُها كما هي ينتج `memoryRandomVerifyErrors = 0` افتراضاً يقرأ الغياب نجاحاً، وهو ما يمنعه س-23.
2. **ت-10 / س-33:** «القيمة اليوم آخر قراءة لا أقصى» صحيحة لفرع واحد من ثلاثة في `selectFooterMaxTemp` (`StressTestService.kt:137-145`)؛ الفرع الثاني أقصى حقيقي.
3. **ت-19 (مُصحَّح لاحقاً — انظر حالة التعارضات أدناه):** «بيانات 67 تُقرأ MiB/s» — كل العيّنات الذهبية السبع المختومة 67 بدأت قبل التزام A70 (`91d5394`، 2026-09-20 20:55:49 +03)، فلا يحمل أيٌّ منها A70، أي ÷10⁶ [أ-١٥، استدلال من التواريخ لا من قيم `v[8]`]. القرار قرار المستخدم؛ والمقيس مرفق ببطاقته.
4. **ت-01 / §٤:** اسم القناة المحسوم `charge_counter_raw` يخالف الاسم الذي يبحث عنه الخادم (`charge_counter_uah`، `findings.js:1055-1060` [ن]) — قارئ يُحدَّث قبل القبول.
5. **ت-06 / س-28:** `voltageMv` و`levelPct` اسمان بوحدة، والخام على بعض الأجهزة بوحدة أخرى (µV على ColorOS: `BatteryChannelSanitizer.kt:30`؛ ومستوى مقطوع: `BatteryReader.kt:432`).
6. **ت-15 / س-29:** لا حامل للخام المركّب: `TestResult` و`FunctionalResultEntity` لا يحملان إلا `rawValue: String?`، و`ManualResultDto` لا يحمل خاماً.
7. **س-01 / س-27:** «لا غياب داخل v[]» لا يُجيب عمّا يُكتب حين تفشل قراءة قناة أُدرجت عند الاكتشاف بعد بدء الفحص؛ و`v[1..10]` «كما هي» تُبقي حرّاسها `0` و`-1` اليوم.
8. **س-18:** «(تصنيف الخادم)» في نصّ «لا قراءات بلا حركة يصير غير مقيس لا WARNING» لا يحدّد من يعيّن الحالة — س-32.

**حالة التعارضات بعد قرار الجزء الأوّل (2026-10-08):** (1) حُلّ بقرار: «تحقّق عشوائي حقيقي في C++، وفشل الحجز حالة صريحة لا {0,0,0,0}» (س-30) · (2) حُلّ بقرار: حقل واحد لحرارة المعالج ومصدره معلن (س-33) · (3) **صُحِّح قرار س-19** نفسه: «1.0.9 و1.0.10 تُقرأ MiB/s، و1.0.8-v6beta تُعلن «الوحدة غير محدَّدة» إلا بمميّز في الحمولة» — والمقيس مرفق ببطاقة ت-19 · (4) يبقى: قارئ الخادم يُحدَّث (خارج هذا المستودع) · (5) حُلّ بقرار: `voltageRaw` و`levelRaw` مع `levelScale`، والخادم يستنتج الوحدة (س-28 مصحَّح) · (6) حُلّ بقرار: عمود Room جديد (س-29 أ) · (7) **يبقى قيداً معروفاً غير مُصلَح بنصّ الحسم:** حرّاس `v[1..10]` (`0` و`-1`)؛ والجزء المحسوم: آخر قيمة صالحة + `channelReadFailures` (س-27) · (8) حُلّ بقرار: الخادم يصنّف «غير مقيس» (س-32 ب).

---

## ٠ — كيف تُقرأ

كل سطر أو خليّة يحمل وسماً واحداً على الأقل:

| الوسم | المعنى |
|---|---|
| `[ك]` | مقروء من كود هذا المستودع عند `ebb4b3b` (الكود نفسه عند `6ae3d33`، أ-١٣)، بملف:سطر. **السطر ينجرف والقيمة هي المرجع** — `git grep -n` يعيده |
| `[أ]` | عدد أو نتيجة من مخرَج أمر مذكور في الملحق أ |
| `[و]` | وثيقة داخل هذا المستودع (تصف الكود وقد تنجرف عنه) |
| `[ن]` | **منقول** من نصّ الأمر أو الخطة — غير متحقَّق من هذا المستودع |
| `[م]` | **مقترح** من وكيل التطبيق، ينتظر الإقرار — **ليس قراراً** (الأسماء المقترحة مجموعة في رأس الوثيقة). **استثناء بعد 2026-10-08:** أسماء N-01..N-23 مُقرّة عدا N-18 (حالتها في رأس الوثيقة) فيُقرأ وسمها `[م]` في البطاقات مُقرّاً؛ وما عداها من `[م]` يبقى مقترحاً |
| `[ح]` | **محسوم بقرار المستخدم 2026-10-08** — نصّه حرفياً في §٥ تحت السؤال المعنيّ (وما اعتمده من اقتراحات المنسّق) |
| `[ي]` | عدّ يدوي، لا أداة له |

«محسوم» تعني فقط ما ورد بنصّه في أمر المستخدم: جدول PLAN-05 §٧-أ وملحقاه ب وج، **وقراراه 2026-10-08** (نصّاهما حرفياً في §٥). كل ما عداه `[م]` أو سؤال في §٥ (القاعدة 13: لا تخمين يملأ فراغاً).

الترميز: `ت-xx` تغيير في fv68 · `ق-xx` قارئ · `س-xx` سؤال (محسوم أو مفتوح، §٥) · `N-xx` اسم يحتاج إقرار المنسّق (رأس الوثيقة).

---

## ١ — الحال اليوم (fieldsVersion 67 عند `ebb4b3b`)

### ١.١ — الختم والتخطيطان

| البند | القيمة | المصدر |
|---|---|---|
| الختم | `val fieldsVersion = 67 // 67: reading cleanup (4 semantic, 0 schema)` | `app/build.gradle.kts:351` [ك] |
| تمريره للنكهتين | `buildConfigField("int", "FIELDS_VERSION", "$fieldsVersion")` | `:381` (launch) و`:403` (datacollect) [ك] |
| كتابته في التذييل | `fieldsVersion = net.neuralcheck.BuildConfig.FIELDS_VERSION` | `StressTestService.kt:1651` [ك] |
| `namespace` · `applicationId` | `net.neuralcheck` · `net.neuralcheck` (launch) / `net.neuralcheck.datacollect` | `build.gradle.kts:62` · `:354` · `:385` [ك] |
| نكهة `launch` | `USE_V2_STRESS=true` ← **11 قناة** `v[0..10]` | `:371` [ك] |
| نكهة `datacollect` | `USE_V2_STRESS=false` ← **18 قناة** (تخطيط قديم)، **والختم نفسه 67** | `:396` [ك] |
| اختيار قائمة `variables[]` | `if (v2Mode) V2_VARIABLE_DEFINITIONS else TimeSeriesVariables.definitions` | `TimeSeriesFileWriter.kt:373-374` [ك] |
| تعريف V2 | 11 مدخلاً · `V2_SAMPLE_VAR_COUNT = 11` | `V2Models.kt:647-676` [ك][أ] |
| تعريف V1 | 18 مدخلاً (فهرس 0-17) | `TimeSeriesFileWriter.kt:761-782` [ك][أ] |
| من يتفرّع على `fieldsVersion` في التطبيق | **لا أحد**: ثلاثة مواضع فقط — كتابته (`:1651`) وتمريره (`BehaviorFingerprintBuilder.kt:233`) وسطر سجلّ (`TimeSeriesFileWriter.kt:449`) | `git grep` [أ] |

> **اسم القناة هو المميِّز لا الفهرس.** النكهتان تحملان الختم نفسه بتخطيطين مختلفين: `v[5]` في `launch` هو `cpu_freq_max_khz` وفي `datacollect` هو `cpu_temp_c`؛ و`v[11]` في `datacollect` هو `gpu_temp_c` (V1) بينما يصير في fv68 على `launch` قناة عدّاد الشحن. أيّ قارئ يقرأ بالفهرس وحده يخلط المتغيّرين (انظر س-08).

### ١.٢ — فخّ الختم: تغييرات مُلتزَمة بعد ختم 67 ولم تُختَم

الختم 67 وُضع في `379e8eb` (2026-09-14). منذ ذلك الالتزام: **80** التزاماً على `app/src/main` [أ]. ما يمسّ عقد الحقول منها:

| الالتزام | التاريخ | الأثر على العقد | نوعه |
|---|---|---|---|
| `91d5394` (A70) | 2026-09-20 | `v[8]` و`verificationResults.storageThroughputSegments[].mbps` من **عشري** (÷10⁶) إلى **ثنائي** (÷2²⁰). نصّ الكود صريح: «This file does not bump FIELDS_VERSION» (`StressTestServiceV2.kt:524` و`:528`؛ `V2Models.kt:12-19` و`:656-664`) | دلالي |
| `7a963dd` (G2/P2.3) | 2026-09-20 | إضافة `footer.phaseBoundaries` (ملف فقط، انظر ١.١٠)؛ وإزالة حساب G-03/G-04/G-05 على الجهاز — فتلك النتائج لا تصدر بعدها | إضافة + دلالي |
| `0675291` (A46) | 2026-09-20 | وسم مرحلة العيّنات الأولى صار صحيحاً (كان `1` الافتراضي بدل `0`) | دلالي |
| `3ab4efb` | 2026-09-20 | `device.deviceProfileKey.layerB_firebaseId` و`layerC_androidId` صارا SHA-256 بدل الخام | دلالي |
| `d5f2ca2` · `eee78ed` · `1524704` | 2026-10-01 | L-02 فرع الفشل → `not_tested` بقيمة `N/A`؛ SEC-08 → `unavailable` بدل PASS كاذب؛ M-06 رفض الميكروفون → `not_tested` | دلالي (نتائج) |

**الأثر:** `fieldsVersion = 67` لم يعد يُعرّف سلوكاً واحداً. وأيّ APK يُبنى من `HEAD` — ومنه `62507f4` (`versionName 1.0.10` · `versionCode 12`، 2026-10-07) — يحمل هذه التغييرات بختم 67. القارئ الذي يفرّع على `<= 67` (منقول: `findings.js:554` و`:701` و`:895` [ن]) سيقرأ `v[8]` الثنائي كأنه عشري (العامل بين الوحدتين 1.048576 بنصّ `V2Models.kt:656-658`). **هذا قيد معروف غير مُصلَح** حتى يُنفَّذ ختم 68 — حُسم في س-19 (2026-10-08) ويُنفَّذ في ت-19.

> `docs/MEASUREMENT_SPEC.md:791` و`:797` [و] ما زالا يصفان `v[8]` عشرياً — الوثيقة كُتبت في `4c01d17` (2026-09-20) ولم تُحدَّث بعد A70.

### ١.٣ — شكل العيّنة والساعات الثلاث

```
{ "ts": <Long>, "phase": <Int>, "v": [ <Float> × 11 ] }      launch  (V2)
{ "ts": <Long>, "phase": <Int>, "v": [ <Float> × 18 ] }      datacollect (V1)
```

* `TimeSeriesSample(ts: Long, phase: Int, v: FloatArray)` — `TimeSeriesFileWriter.kt:511-514` [ك]. تعليقاه «1-5» و«18 values» قديمان.
* `v` **مصفوفة `float` أوّلية**: لا تحمل `null`. وتُسلسَل بـ`GsonBuilder().serializeNulls().create()` (`TimeSeriesFileWriter.kt:47`) وتُرسَل بـ`GsonBuilder().create()` (`FirebaseAnalysisService.kt:82`، `:122`) — **كلاهما بلا `serializeSpecialFloatingPointValues()`** [ك][أ]. نصّ Gson 2.10.1: `checkValidFloatingPoint` يرمي `IllegalArgumentException` على `NaN` و`Infinity` (`Gson.java:446-451`، من `gson-2.10.1-sources.jar` في `~/.gradle`) [أ]. فـ**`NaN` لا يصلح تمثيلاً للغياب في `v[]`** كما يقول نصّ الخطة «NaN = غير مدعوم» (PLAN-04 :80-82 [ن]): في الملف تُبتلَع العيّنة في `flush()` (تُحسب `flushFailures` ويبقى المخزن وتُعاد المحاولة إلى الأبد، `TimeSeriesFileWriter.kt:128-155`)، وعند `finalize` يعود `null` فلا ملف سلاسل زمنية؛ وفي الحمولة يفشل `gson.toJson(fingerprint)` فلا يبلغ الطلبُ الخادم. هذا يحكم س-01.
* **ثلاث ساعات** لا يربطها شيء في الترويسة: `ts` ساعة الجدار (`System.currentTimeMillis()`، `DisciplinedSampler.kt:372`)؛ `v[0]` رتيبة **نسبية** إلى `startTime` الممرَّر إلى `start()` (`DisciplinedSampler.kt:322`)؛ `header.startTimestamp` ساعة الجدار عند بدء الخدمة (`StressTestService.kt:741-752`). و`phaseBoundaries[].startMs/endMs` في إطار `v[0]` (`V2Models.kt:489-504`). **لا أساس `elapsedRealtime` مطلق في الترويسة** ← أيّ طابع جديد بـ`elapsedRealtime` (ت-06) لا يُربط بـ`v[0]` بلا قرار (س-10).
* وسم `phase` يأتي من `currentPhase` الذي يضبطه `phaseDriver` باستطلاع كل **500 ms** (`StressTestService.kt:991-1090`، `delay(500L)` عند `:1071` و`:1114`) ← قد يتأخّر الوسم حتى نصف ثانية عند كل انتقال. `footer.phaseBoundaries` هو المرجع الدقيق، **لكنه في الملف فقط** (١.١٠).

### ١.٤ — القنوات `v[0..10]` (نكهة `launch`)

الأعمدة: **المُعلَن** = `variables[].source` حرفياً (`V2Models.kt:647-674`) · **الفعلي** = ما يكتبه `DisciplinedSampler.buildSample` (`:291-375`) · **الغياب اليوم** = ما يظهر في `v[i]` حين لا قياس.

| # | الاسم · الوحدة | المُعلَن | الفعلي | الكادنس/المراحل | الغياب اليوم |
|---|---|---|---|---|---|
| 0 | `timestamp_ms` · ms | `SystemClock.elapsedRealtime()` | `elapsedRealtime() - startTimeMs` (`:322`) — **نسبي** | كل عيّنة، كل المراحل | لا غياب |
| 1 | `battery_temp_c` · °C | `BatteryManager EXTRA_TEMPERATURE / 10` | `getIntExtra(EXTRA_TEMPERATURE, 0) / 10f` (`BatteryReader.kt:400`) | تتغيّر عند بثّ `ACTION_BATTERY_CHANGED` لا عند كل عيّنة [و: `MEASUREMENT_SPEC.md:784`] | **`0`** (لا بثّ أو لا مفتاح) |
| 2 | `battery_voltage_mv` · mV | `EXTRA_VOLTAGE` | بعد المعقِّم: `raw > 100000` ⇒ ÷1000 (`BatteryChannelSanitizer.kt:30,54-56`) | كالسابق | **`0`** |
| 3 | `battery_current_ua` · µA | `BATTERY_PROPERTY_CURRENT_NOW (vendor-cadence variation)` | **مطبَّع لا خام**: × معامل الجهاز × قلب إشارة مُستنتَج (`BatteryReader.kt:291-312` و`:366-410`)؛ `Int.MIN_VALUE` ⇒ `0` | قراءة مباشرة كل عيّنة | **`0`** (لا يُميَّز من صفر حقيقي إلا بعدّاد `dataQuality.batteryInvalidCurrentReadings`) |
| 4 | `battery_level_pct` · % | `EXTRA_LEVEL / EXTRA_SCALE × 100` | قسمة صحيحة `(level*100)/scale` (`BatteryReader.kt:428-433`) — **تقريب بالقطع** | بثّ | **`-1`** |
| 5 | `cpu_freq_max_khz` · kHz | `max(scaling_cur_freq) across clusters` | أكبر قيمة بين `cpu[n-1]` و`cpu0` و`cpu[n/2]` إن `n > 4` (`:310-313`) مع بديل عقد سياسات `policy{0,4,6,7}` (`SystemFileReader.kt:219-239`) — **ثلاث نوى لا ثلاثة عناقيد** | كل عيّنة | **`-1`** |
| 6 | `cpu_usage_pct` · % | `/proc/stat (with process-fallback when blocked)` | على الأجهزة التجارية غالباً **حصّة التطبيق** لا حِمل النظام (`V2Models.kt:131-151`)؛ أول قيمة `0f` بلا فرق سابق (`SystemFileReader.kt:547`) | كل عيّنة | **`0`** أول قيمة · **`-1`** عند استثناء |
| 7 | `package_power_mw` · mW | `abs(voltage_mv × current_µA) / 1e6` | محسوب من اللقطة نفسها لـ`v[2]`/`v[3]` (`BatteryChannelSanitizer.kt:69-71`) | تابع | **`-1`** (جهد ≤ 0 أو تيار = 0) |
| 8 | `storage_throughput_mbps` · MB/s | `instant I/O rate during STORAGE_STRESS` | **ثنائي MiB/s** منذ A70، يُنشَر لكل مقطع 256 MiB ويُصفَّر عند نهاية المرحلة | المرحلة 1 | **`0`** خارج المرحلة وبعد بلوغ سقف الكتابة |
| 9 | `memory_bandwidth_mbps` · MB/s | `live rolling MB/s (~1 Hz) during MEMORY_STRESS; 0 outside` | ثنائي MiB/s (`AiMemoryWorkload.kt:157` يقسم على `1024.0 * 1024.0`) | المرحلة 2 بنشر ~1 Hz | **`0`** خارج المرحلة |
| 10 | `gpu_fps` · fps | `OpenGL offscreen frame counter, 1 s sliding window (phases 4 + 5 only)` | معدّل آخر **4 استعلامات** للعدّاد لا نافذة زمنية (`GpuFpsTracker.kt:25-66`)؛ يُصفَّر ما خارج `[0.1, 3000]` | قيمة غير صفرية في 4 و5 + **ذيل** | **`0`** |

ملاحظات على الفروق بين المُعلَن والفعلي:

1. **`v[0]`**: الإعلان يوهم بقيمة مطلقة؛ القيمة ms منذ بدء الفحص. (س-21)
2. **`v[10]`**: «1 s» صحيحة عند 4 Hz فقط؛ عند 10 Hz (المرحلة 6) النافذة ≈ 0.4 s، وعند 2 Hz (0 و7) ≈ 2 s. «phases 4 + 5 only» غير دقيقة: النافذة لا تُفرَّغ عند نهاية المرحلة 5 (`reset()` يُستدعى عند **بدء** مرحلة GPU فقط: `StressTestServiceV2.kt:787` و`:893`) ومرجع المولِّد V2 يبقى معيّناً إلى `shutdown()` (`:1541`) فتظهر قيم غير صفرية لعيّنات بعد نهاية المرحلة 5. وأي عيّنة تُوسَم بمرحلة سابقة بسبب تأخّر الوسم (500 ms) يمكن أن تحمل قيمة المرحلة التالية. **هذا تفسير من قراءة الكود لما نقله المنسّق (3-5 تحديثات في المرحلة 6، وتحديث واحد في المرحلة 3 في فحصين) — غير مقيس على جهاز.**
3. **`v[5]`**: الاسم «max across clusters» يوهم بقراءة لكل عنقود؛ الفعلي ثلاث **نوى** بترتيب الفهرس وبديل سياسات بتخمين رؤوس العناقيد.
4. **`v[3]`/`v[7]`** لا يمثّلان الخام: القاعدة 20 تمنع «قلب إشارة مبنيّ على استنتاج» في التطبيق، والجدول (§٧-أ) لا يتناوله (س-24).
5. **`v[8]`/`v[9]` صفر خارج مرحلتيهما = غياب نشر لا قياس** [و: `MEASUREMENT_SPEC.md:798`، `:1059`].

### ١.٥ — المراحل والكادنس

| المرحلة | المعرّف | المدّة (ث) | المعدّل (Hz) | العيّنات المتوقَّعة |
|---|---|---|---|---|
| INIT_PROFILING | 0 | 30 | 2 | 60 |
| STORAGE_STRESS | 1 | 75 | 1 | 75 |
| MEMORY_STRESS | 2 | 60 | 1 | 60 |
| CPU_SUSTAINED | 3 | 75 | 4 | 300 |
| GPU_SUSTAINED | 4 | 75 | 4 | 300 |
| COMBINED_PEAK | 5 | 60 | 4 | 240 |
| BURST_PATTERN | 6 | 45 | 10 | 450 |
| COOLDOWN | 7 | 60 | 2 | 120 |
| **المجموع** | | **480** | | **1605** |

(`V2Models.kt:521-535`؛ المجموع من `counts.js` [أ]؛ وفي العيّنات الذهبية `samplesCount` = 1604 و1606 [أ]). `footer.expectedSamples` = 1605 (`StressTestService.kt:1687`). المعدّل يُضبط بـ`sampler.switchToHz(phase.samplingHz)` من `phaseDriver` (`:1081-1082`).

**أثر على القنوات الجديدة:** `v[12]` المخطَّط كل **10 ث** يقع في ≈ 1 من 40 عيّنة في المراحل 3-5 (4 Hz)، وفي ≈ 1 من 100 في المرحلة 6 (10 Hz)، وفي ≈ 1 من 10 في المرحلة 1 (1 Hz) — حسابياً من الجدول أعلاه [ي]. فكل ما عدا ذلك «لم يُقرأ في هذه النقطة» (س-03).

### ١.٦ — `header` (12 مفتاحاً)

`TimeSeriesHeader` — `TimeSeriesFileWriter.kt:496-509` [ك][أ]. يُبنى عند `StressTestService.kt:741-753`.

| المفتاح | النوع | الغياب اليوم |
|---|---|---|
| `scanId` · `deviceFingerprint` · `scanType` · `appVersion` · `deviceModel` | String | لا غياب |
| `androidApiLevel` | Int | لا غياب |
| `cpuCores` | Int | `1` عند فشل `availableProcessors()` (`getOrDefault(1)`) — **حارس** |
| `totalRamMb` | Long | `0` عند فشل `ActivityManager` (`:733-740`) — **حارس** |
| `startTimestamp` | Long | لا غياب |
| `batteryLevelStart` | Int | `-1` إن لم يصل بثّ البطارية |
| `batteryTempStart` | Float | `0.0` إن لم يصل بثّ |
| `isPowerSaveMode` | Boolean | `false` افتراضي |

> فرع «لا ملف سلاسل زمنية» في `BehaviorFingerprintBuilder.kt:105-142` يكتب ترويسة **بأصفار وسلاسل فارغة** وتذييلاً بأصفار (`maxBatteryTemp = 0f` · `maxCpuTemp = 0f` · `intensityFactor = 0f` …) — أصفار تشبه القياس. الغياب هنا يُعلَن بـ`isComplete=false` و`totalSamples=0` و`fieldsVersion=null` فقط.

### ١.٧ — `footer` (34 مفتاحاً)

`TimeSeriesFooter` — `TimeSeriesFileWriter.kt:531-745`؛ يُبنى عند `StressTestService.kt:1619-1700` [ك][أ]. عمود **ح** = هل يبلغ حمولة `analyzeDevice` عبر `TimeSeriesFooterDto` (31 مفتاحاً) — الفرق ثلاثة مفاتيح: `phaseBoundaries` · `preScanWarnings` · `calibration` [أ].

| المفتاح (سطر) | ح | الغياب اليوم |
|---|---|---|
| `endTimestamp` (532) · `totalSamples` (533) · `isComplete` (534) | نعم | لا غياب |
| `earlyStopReason` (535) | نعم | `null` = لم يتوقّف مبكراً (ليس غياب قياس) |
| `batteryLevelEnd` (536) | نعم | `-1` إن لم يصل بثّ |
| `maxBatteryTemp` (537) | نعم | يسقط إلى لقطة النهاية ثم حرارة البداية **بلا إعلان** (`StressTestService.kt:1499-1500`) |
| `maxCpuTemp` (538) | نعم | **`-1f`**. وحين تتوفّر قراءة حيّة عند الإنهاء تكون هي القيمة لا الأقصى (`selectFooterMaxTemp` `:137-145`، `liveCpu > 0f -> liveCpu`) ← الاسم «max» يخالف الدلالة |
| `thermalHeadroomAvg` (545) · `thermalHeadroomPeak` (547) | نعم | `null` حين لا قراءة موجبة منتهية؛ **الصفر الحقيقي يُقصى** (`SamplerStats.kt:239`: `headroom > 0f`) |
| `thermalStatusMax` (549) | نعم | `null` |
| `tempSource` (551) | نعم | `"none"` |
| `intensityFactor` (559) · `deviceTierMeasured` (565) | نعم | ثوابت (`1.0` و`flagship_cooling`) ومُعلَنة في `unmeasuredDefaults` |
| `batteryCurrentScalingApplied` (570) | نعم | `"unresolved"` أو وسم `default_<vendor>` |
| `tempRiseRateInProbe` (572) | نعم | `0.0` على V1 ومُعلَن |
| `throttlingEvents` (574) | نعم | `[]` |
| `verificationResults` (576) | نعم | انظر ١.٨ |
| `phaseCompletionStatus` (578) | نعم | `{}` |
| `phaseBoundaries` (597) | لا | `[]` على V1 |
| `stressEffectiveness` (604) | نعم (قائمة بيضاء `stressEffectivenessToMap`، تُسقط `peakBigFreqKhzInStressPhases` و`c4RatioStressWindow`) | افتراضيات صفرية/`false` |
| `batteryHealth` (612) | نعم | `null` إن فشلت اللقطة؛ انظر ١.٩ |
| `dataQuality` (617) | نعم | انظر ١.١١ |
| `knownCapacityClassification` (625) | نعم | `"unknown"` (`StressTestService.kt:1553`) |
| `fieldsVersion` (638) | نعم (`takeIf > 0`) | `0` ⇒ `null` في الحمولة |
| `footerDiagnostics` (655) | نعم | 8 مفاتيح `"ok"`/`"fail:<صنف>"` [أ] — صنف الاستثناء لا نصّه (القاعدة 21) |
| `channelDiscovery` (662) | نعم | `null` على V1 أو فشل المسبار |
| `thermalSensorAvailability` (668) | نعم | `null` بلا مسبار |
| `scanStillness` (675) | نعم | **`isStill=true` مع `accelStdDev=-1`** حين < 2 عيّنة تسارع (`:1599-1606`) — الغياب يُقرأ «ثابت» |
| `preScanWarnings` (689) | لا | **`[]` = «نظيف» عند فشل القراءة** (`getOrDefault(emptyList())` `:1668`) |
| `calibration` (705) | لا | `{"applied": false}` |
| `unmeasuredDefaults` (716) | نعم | `null` = بناء ≤ 64. اليوم `["deviceTierMeasured","intensityFactor"]` فقط (`:1678-1682`) — **لا يعدّد** `storage*VerifyErrors` ولا `storageSlcCacheSizeMb` ولا `workersPinned*` وهي ثوابت أيضاً |
| `expectedSamples` (726) | نعم | `null` على V1 |
| `samplesSha256` (736) | نعم | `null` إن فشل الحساب |
| `flushFailureCount` (744) | نعم | `null` ≤ 64 |


### ١.٨ — `verificationResults` (26 مفتاحاً)

`VerificationSummary` — `V2Models.kt:357-475` [ك][أ]؛ يُبنى عند `StressTestServiceV2.kt:1063-1104`؛ تُمرَّر **كلها** (26) عبر قائمة بيضاء بأسماء ثابتة `footerVerificationToMap` (`BehaviorFingerprintBuilder.kt:286-345`) — أيّ مفتاح جديد لا يظهر في الحمولة ما لم يُضَف إليها سطراً (القاعدة 25).

| المفتاح (سطر في `V2Models.kt`) | الافتراضي | المنتِج وما يقوله الكود | ملاحظة غياب |
|---|---|---|---|
| `gpuComputeGemmCorrect` (368) | `true` | `verifGpuComputeOk = true` يُخفَض فقط عند الفشل (`StressTestServiceV2.kt:310`، `:852`، `:942`) | مرحلة لم تُنفَّذ (قطع المرحلتين 4/5 عند `governorCappingDetected`) ⇒ **«صحيح»** |
| `gpuFragmentHashMatches` (369) · `gpuFragmentHashTotal` (370) | `0` · `0` | `verifGpuFragMatches/Total` (`:311-312`) | صفر لا يميّز «لم يُنفَّذ» من «0 من 0» |
| `gpuRenderCompletionMsCold` (381) · `…Warm` (382) | `-1` | `gpuRenderColdMs/WarmMs` | **`-1`** حارس مُعلَن في التعليق |
| `memoryTestedBytes` (387) | `0` | من `bytesTouched` (`:616`) | `0` = لم تُنفَّذ/لم تُحسب |
| `storageWriteVerifyErrors` (389) · `storageReadVerifyErrors` (390) | `0` | **`= 0` حرفية** عند `StressTestServiceV2.kt:1070-1071` — لا منتِج: لا قراءة راجعة ولا مقارنة في طور التخزين (`:425-595` [ك]) | صفر **ثابت لا قياس** |
| `storageSlcCacheSizeMb` (391) | `0` | **`= 0` حرفية** `:1072` («backend computes from segments») | صفر ثابت |
| `storageThroughputSegments` (396) | `[]` | `storageSegments` | `[]` = لا مقاطع |
| `criticalDefectDetected` (398) | `false` | `= !verifGpuComputeOk` (`:1081`). التعليق قبله (`:1077-1080`) يذكر أخطاء التخزين لكن الشرط لا يقرؤها | يرث غياب `gpuComputeGemmCorrect` ⇒ `false` |
| `workersPinnedBig/Mid/Little` (407-409) · `workersPinFailedBig/Mid/Little` (410-412) | `0` | تُكتب **فقط** من `accumulateCpuOutcome` (`:379-394`)، الذي لا يُستدعى إلا من احتياطي NEON (`:407`) ومن المسار القديم (`:756`، `:1015`) | على مسار الذكاء (`USE_AI_WORKLOAD_CPU = true`، `:94`) **لا محاولة تثبيت أصلاً** ⇒ الصفر = «لم يُحاوَل» |
| `aiInferenceIterationsTotal` (420) · `aiInferenceWorkers` (423) · `aiInferenceAvgMs` (427) | `0` | `:1095-1097` | `0` |
| `aiActiveDelegate` (433) | `"NONE"` | `:1098` | سلسلة حالة |
| `aiDelegateInitError` (441) | `null` | يُمرَّر `?: ""` (`BehaviorFingerprintBuilder.kt:312`) | **`null` ⇒ `""`** في الحمولة |
| `cpuStressFallbackUsed` (447) | `false` | | |
| `gpuFenceTimeoutCount` (458) | `0` | | `0` يعني «اكتملت كل الدفعات» لكن يظهر أيضاً حين لم تُنفَّذ المرحلة |
| `storageBytesWritten` (464) · `storageWriteDurationMs` (474) | `-1` | `:1075-1076` | **`-1`** حارس مُعلَن |

### ١.٩ — `batteryHealth` (13 مفتاحاً)

`Map<String, Any?>` يُبنى عند `StressTestService.kt:1505-1534` من `BatteryIndicators.compute` (`BatteryIndicators.kt:110-170`) [ك][أ]؛ مفاتيح العيّنة الذهبية: 13 [أ].

| المفتاح | النوع | الغياب اليوم |
|---|---|---|
| `designCapacityMah` | Int | **`0`** إن لم يُعثر (`designMah = ... ?: 0`، `BatteryIndicators.kt:158`) ومعه `designCapacitySource="unavailable"` |
| `designCapacitySource` | String | `"unavailable"` / `"no_battery_manager"` |
| `cycleCount` | Int? | `null` |
| `cycleCountSource` | String | اليوم: `"sysfs_cycle_count"` · `"battery_property_cycle_count_api_34"` · `"unavailable"` · `"no_battery_manager"` (`BatteryReader.kt:507-509`، `:556-571`) |
| `dischargeRateUnderLoadMa` | Double | **`0.0`** (لا عيّنات غير صفرية في المراحل 3-5) |
| `batteryTempStartC` · `batteryTempMaxC` | Double | قيمة بديلة (انظر `maxBatteryTemp`) |
| `batteryTempRiseC` | Double | `0.0` |
| `voltageStabilityMv` | Double | **`0.0`** حين < 2 عيّنة (`BatteryIndicators.kt:142-143`) |
| `levelDropPct` | Int | `0` |
| `dischargeVsDesignRatioMah` | Double | **`0.0`** حين `designCapacityMah` غائب |
| `chargeCounterUah` | Long? | `null`؛ **والصفر الحقيقي يصير `null`** (`takeIf { != MIN && != 0L }`، `BatteryReader.kt:578-579`) |
| `energyCounterNwh` | Long? | كالسابق (`:583-584`) |

القراءة الحالية لعدد الدورات (ت-02): `getIntProperty(8)` عند `BatteryReader.kt:568` فقط إن `SDK_INT >= 34` وبعد فشل sysfs؛ **`if (c > 0)`** (`:569`) فيسقط الصفر الحقيقي (بطارية جديدة) إلى `"unavailable"`. والاختبار B-08 يقرأ مساراً آخر ويقبل الصفر (`BatteryTests.kt:428-447`: `cycles >= 0`) — **قارئان بحكمين** على عدد واحد.

> ثلاثة من المفاتيح الستّة السلوكية (`dischargeRateUnderLoadMa` متوسّط، `voltageStabilityMv` انحراف معياري، `batteryTempMaxC` ذروة) **تجميعات على الجهاز** — ما تمنعه القاعدة 20 (تنعيم/جمع/اختيار ذروة). الجدول لا يتناولها (س-20).

### ١.١٠ — ثلاثة مسارات للبيانات، لا اثنان

| المسار | ماذا يحمل | `variables[]` | `samples` | التذييل |
|---|---|---|---|---|
| (أ) **الملف** `<scanId>.ts.json.gz` — يُرفع إلى `scans/{fingerprint}/{scanId}.ts.json.gz` (`FirebaseSyncService.kt:50-56`) ويُصدَّر كما هو في ZIP (`ExportDataUseCase.kt:71-75`) | كل شيء | `{index, name, unit, source}` | `{ts, phase, v}` | **34 مفتاحاً** |
| (ب) **حمولة `analyzeDevice`** — `BehaviorFingerprintBuilder` ← DTO ← `gson.toJson` ← `Map` ← callable (`FirebaseAnalysisService.kt:122-131`) | قائمة بيضاء | `{name, unit, source}` **بلا `index`** (`AiAnalysisRequest.kt:75`، `BehaviorFingerprintBuilder.kt:169-171`) | `{phase, ts, v}` | **31 مفتاحاً** |
| (ج) **مستند `reports/{scanId}`** الذي يخزّنه الخادم | `rawFingerprint.timeSeries.{header, variables, footer, samplesCount}` بلا `samples` | `{index, name, source, unit}` (الفهرس يضيفه من لا أعرفه) | غائبة | **33 مفتاحاً** في العيّنة الذهبية — يحمل **`calibration` و`preScanWarnings` اللذين ليسا في DTO** [أ] |

التعليق في `AiAnalysisRequest.kt:23-25` يقول «Both paths carry identical content» — **غير صحيح** بالأمر المقيس: ثلاثة مفاتيح تذييل في الملف ليست في الحمولة (`phaseBoundaries` · `preScanWarnings` · `calibration`) [أ]. ومن أين يأتي المفتاحان الأخيران إلى مستند (ج) لا يُحسم من هذا المستودع (س-22). **أيّ مفتاح fv68 جديد في التذييل يلزمه ثلاثة تحديثات**: `TimeSeriesFooter` (الملف)، `TimeSeriesFooterDto` + سطر في `BehaviorFingerprintBuilder` (الحمولة)، وقارئ الخادم.

### ١.١١ — جرد القيم الافتراضية والحارسة على حقول العقد (القاعدة 22، الملحق)

الحرف يُحسب في الخادم؛ **هل تصل هذه القيم إلى حرف أو نصّ معروض: غير معلوم من هذا المستودع [ن]** (ما عدا ما ذكر الأمر صراحةً: `storage*VerifyErrors` شرط حرف التخزين). السبب المكتوب هو ما في تعليق الكود، وما لا سبب مكتوباً له فمرشّح للإزالة بنصّ القاعدة.

| المسار:السطر | القيمة | السبب المكتوب | مقترح [م] |
|---|---|---|---|
| `V2Models.kt:368` | `gpuComputeGemmCorrect = true` | «Default values represent "not yet measured" / "no defects"» (`:350-356`) — **غياب القياس يُقرأ نجاحاً** | حالة صريحة (ت-09 تنطبق عليه بالقياس) — س-23 |
| `StressTestServiceV2.kt:310` → `:1081` | `verifGpuComputeOk = true` ⇒ `criticalDefectDetected = false` | لا سبب مكتوب | كالسابق |
| `StressTestServiceV2.kt:1070-1072` | `storage*VerifyErrors = 0` · `storageSlcCacheSizeMb = 0` | للثالث فقط: «backend computes from segments» | ت-08 · ت-09 |
| `V2Models.kt:407-412` | `workersPinned*/PinFailed* = 0` | لا (التعليق يصف الفائدة لا الصفر) | ت-13 |
| `StressTestService.kt:137-145` | `maxCpuTemp = -1f` | «-1 sentinel = unavailable» | ت-10 |
| `StressTestService.kt:1599-1606` | `isStill = true` عند < 2 عيّنة تسارع | «no evidence of motion» (`:1596-1598`) | خارج الجدول (س-20) |
| `StressTestService.kt:1668` | `preScanWarnings = []` عند فشل القراءة | لا سبب مكتوب | خارج الجدول (س-23) |
| `StressTestService.kt:1851-1862` | `cpuTempCoveragePct = 0` · `gpuTemp… = 0` · `skinTemp… = 0` · `cpuTempPrimarySource = "moved_to_footer_v5"` | «V5: thermal channels no longer in v[] — coverage % collapses to 0» | ثوابت تُقدَّم كقياس — خارج الجدول |
| `BatteryIndicators.kt:132-143,158` | `0.0` للمتوسّط والانحراف وعدم وجود السعة | «well-defined 0.0 rather than NaN/Inf» | خارج الجدول (س-20) |
| `BatteryReader.kt:556,569,578-579` | `cycleCountSource="unavailable"` + إسقاط الصفر | لا | ت-02 | **[حُلّ 2026-10-08: §٧-ز]**
| `BehaviorFingerprintBuilder.kt:105-142` | فرع «لا ملف»: أصفار وسلاسل فارغة | «structurally-empty payload» (`:97-102`) | خارج الجدول |
| `BehaviorFingerprintBuilder.kt:312` | `aiDelegateInitError ?: ""` | لا | خارج الجدول |
| `TimeSeriesFileWriter.kt:559,565,570,572` | ثوابت `intensityFactor` · `deviceTierMeasured` · `batteryCurrentScalingApplied="unresolved"` · `tempRiseRateInProbe` | **مُعلَنة** بـ`unmeasuredDefaults` (`:1678-1682`) | استثناء سليم — يُوسَّع ليشمل الثوابت الأخرى |
| `BatteryCycleCount.kt` (`EXTRA_DEFAULT`) | `getIntExtra(EXTRA_CYCLE_COUNT, Int.MIN_VALUE)` | **القيمة الافتراضية الوحيدة المضافة في ت-02** (2026-10-08): `Int.MIN_VALUE` ليست عدداً — هي علامة «غير مدعوم» التي تعيدها المنصّة للقيم الصحيحة (اصطلاح `getIntProperty`، ويحترمه `BatteryReader` لـCURRENT_NOW)؛ ولا تُبلَغ إلا بعد أن يقول `hasExtra` إن الإضافة موجودة، وإضافة تساويها حالة `extra_unsupported_by_device` لا عدد | أُعدِّدت بسببها (الملحق) |

### ١.١٢ — تحقّق النقاط المنقولة (الأمر، البند ١)

الستّة المُصدَّرة من الإنتاج ليست في مستودعي. ما قستُه: **7** تقارير ذهبية في `app/src/test/resources/contract/` (6 V7 + 1 V6، لجهازين: Samsung S10 5G وHonor MTN-NX1) [أ]. هي نسخ منقولة من الباك اند (`README.md` في المجلّد نفسه)، ولا أعدّها الستّة نفسها.

| النقطة المنقولة | الحكم | الدليل |
|---|---|---|
| `v[0]` مُعلَن `SystemClock.elapsedRealtime()` وقيمته الأولى 67 نسبية | **الإعلان مؤكَّد والنسبية مؤكَّدة من الكود**؛ «67» غير متحقَّق (لا مصفوفة `samples` في المستودع) | `V2Models.kt:648` · `DisciplinedSampler.kt:322` [ك] |
| `gpu_fps` مُعلَن «…(phases 4 + 5 only)»، وفيه 3-5 تحديثات في BURST وواحد في CPU_SUSTAINED | **الإعلان مؤكَّد؛ الأعداد غير متحقَّقة**؛ وللتفسير آلية من الكود (١.٤ ملاحظة 2) غير مقيسة على جهاز | `V2Models.kt:673` · `GpuFpsTracker.kt:25-66` [ك] |
| `footer.maxCpuTemp = -1` مع `thermalSensorAvailability.cpuTemp = "missing"` | **مؤكَّد في 7/7** [أ]. **وإضافة:** حين تتوفّر قراءة فالقيمة قراءة حيّة عند الإنهاء لا أقصى المدّة | `StressTestService.kt:137-145` [ك] |
| `storageSlcCacheSizeMb = 0` و`storage*VerifyErrors = 0`: قياس أم افتراضي؟ | **افتراضي: ثلاثتها حرفية `= 0` ولا منتِج لأخطاء التحقّق.** 0 في 7/7 [أ] | `StressTestServiceV2.kt:1070-1072` [ك] |
| `cycleCountSource = "unavailable"`؛ القراءة عبر `getIntProperty(8)`؛ الخاصّية 8 ليست في `BATTERY_PROPERTY` العامّة (1-6)؛ والطريق `EXTRA_CYCLE_COUNT` منذ API 34 | **كلّه مؤكَّد.** `unavailable` في 7/7 [أ]. في `android.jar` (33/34/35): `BATTERY_PROPERTY_*` = 1…6 (`CHARGE_COUNTER`=1، `CURRENT_NOW`=2، `CURRENT_AVERAGE`=3، `CAPACITY`=4، `ENERGY_COUNTER`=5، `STATUS`=6)، و`EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT"` موجود في 34 و35 وغائب في 33 [أ]. **تحفّظ:** `android.jar` يُسقط الواجهات `@hide`، فغياب الثابت لا يُنفي وجود المعرّف 8 مخفياً؛ المؤكَّد أنه **ليس ثابتاً عاماً** | `BatteryReader.kt:568` · الملحق أ-٣ |
| `workersPinned* / PinFailed* = 0` في الستّة | **مؤكَّد 0 في 7/7** [أ]، **وسببه من الكود: لم يُحاوَل التثبيت** على مسار الذكاء (١.٨). و`cpuStressFallbackUsed=false` في 7/7 [أ] (فلم يعمل احتياطي NEON) | `StressTestServiceV2.kt:379-407,756,1015` [ك] |
| L-02 يستمع 30 ث بلا طلب موقع؛ 0 (fail) في خمسة وN/A في واحد | **السلوك مؤكَّد من الكود**: `registerGnssStatusCallback` + `delay(30_000L)` بلا `requestLocationUpdates`؛ `total == 0` ⇒ `FAIL` بقيمة `"0"`؛ وفشل التسجيل/رفض الإذن ⇒ `NOT_TESTED` بقيمة `"N/A"`. `requestSingleUpdate` موجود في L-03 فقط وبعد L-02. **الأعداد (5+1) غير متحقَّقة** | `GpsTests.kt:62-165` · `:217` · `:25-29` [ك] |
| SN-08/SN-09: `ACTIVITY_RECOGNITION` غير معلَن ولا مطلوب | **مؤكَّد: صفر ذكر في كل `app/src`** [أ]؛ و`ScanRuntimePermission` لا يحمله | `ScanPermissions.kt:32-86` · `SensorTests.kt:39-40` [ك] |
| «لم أختبر» و«غير متوفر في هذا الجهاز» كلاهما `not_tested` | **مؤكَّد:** زرّ `manual_mark_unavailable` ينادي `onResult("not_tested")` كزرّ `manual_not_tested_btn` | `ManualTestDialogs.kt:796-797` · `:999-1000` · `:1108-1109` · `:1143-1144` · `:1292-1293` [ك] |
| تعليق PLAN-04 M4: «أكثر من مرّة كل 10 ث يُرجع NaN» | **متعارض مع تعليق الكود** الذي يقول إن المنصّة «throws IllegalStateException if called more than once a second» (`SystemFileReader.kt:195-197`) — **لم أتحقّق من أيٍّ منهما**. الكود يلتقط الاستثناء فقط (يثبّت `headroomUnsupported`)، ولا يعامل `NaN` الراجع حالةً: يخزّنه في `lastHeadroom` ويعيده (`:287-289`) | `SystemFileReader.kt:280-297` [ك] |

### ١.١٣ — الأذونات ونتائج الاختبارات التي تمسّ العقد

| البند | الحال اليوم |
|---|---|
| الأذونات المعلَنة | `CAMERA` · `ACCESS_FINE_LOCATION` · `ACCESS_COARSE_LOCATION` · `READ_PHONE_STATE` · `RECORD_AUDIO` · … (`AndroidManifest.xml:6-35`، `:43`)؛ **لا `ACTIVITY_RECOGNITION`** [أ] |
| المطلوبة وقت التشغيل قبل الفحص | أربعة فقط: `CAMERA` · `MICROPHONE` · `LOCATION` (=FINE) · `PHONE_STATE` (`ScanPermissions.kt:32-86`). كل واحد منها يحمل قائمة `autoNotTestedIds` = ما يصير `not_tested` عند الرفض — **مصدر العقد لـ«ما يغيب عند رفض الإذن»** |
| L-02 | `rawValue = "$used / $total"` نصّاً (`GpsTests.kt:163`)؛ الحالة `PASS` إن `used >= 4`، `WARNING` إن `>= 1`، وإلا `FAIL`؛ `score = used/8*100`. القيم الحارسة: `"N/A"` (رفض إذن أو فشل تسجيل)، `"0"` (لا `LocationManager` → `UNAVAILABLE`، أو الموقع معطَّل → `FAIL`، أو `total == 0` → `FAIL`). `DTO` لا يحمل أعداداً منفصلة: `FunctionalResultDto{testId, componentName, categoryName, rawValue, rawValueUnit, status, notes}` (`AiAnalysisRequest.kt:187-195`) |
| SN-08 · SN-09 | `testSensor(...)`: تسجيل مستمع 3 ث (`SensorTests.kt:93`)؛ بلا قراءات ⇒ `WARNING` (`:99-101`)؛ كلها أصفار ⇒ `WARNING` (`:102-103`). **حساسا الخطوات لا يُنتجان قراءات بلا حركة جسدية حتى مع الإذن** [م] |
| SN-04 (الإضاءة) | الاختبار الآلي نفسه: متوسّط قراءات 3 ث نصّاً في `rawValue`؛ **صفر في كلها ⇒ `WARNING`** (`SensorTests.kt:35`، `:102-103`) — لا شيء عن التغطية |
| المفردات المغلقة للحالة | `TestStatus`: `PASS` · `FAIL` · `WARNING` · `UNAVAILABLE` · `NOT_TESTED` · `ERROR` (`ScanReport.kt:40-47`). **`fromString` يُسقط كل نصّ مجهول إلى `ERROR`** (`ScanReport.kt:50-57`) ← أيّ مفتاح حالة جديد يخزَّن في Room ويُقرأ بهذه الدالّة يصير `error` حتى تُحدَّث |
| الإجابات اليدوية | `setResult` يكتب `status` نصّاً + `score` (`pass`→100، `fail`→0، غيره `null`) + `rawValue = null` + `notes = "manual"` (`ManualTestsViewModel.kt:105-123`)؛ `commitAndContinue` يملأ غير المجاب `not_tested` (`:132-165`). على الحمولة: `ManualResultDto{testId, status, categoryName}` — **لا خام** (`AiAnalysisRequest.kt:221-225`) |
| شبكة اللمس M-02 | `8 × 15 = 120` خلية (`ManualTestDialogs.kt:232-233`). الحالة الموصى بها `pass` ≥ 95% · `warning` ≥ 80% (`:239-243`) **للنصّ فقط**؛ المستخدم يختار بين ثلاث أزرار (`pass`/`fail`/`not_tested`). **لا يُشحن عدد الخلايا ولا النسبة** |

---

## ٢ — تغييرات fv68 (المواصفة النهائية بعد حسم 2026-10-08)

> كل سطر وسمه `[ح]` **محسوم 2026-10-08** بنصّ في §٥ (نصّ المستخدم أو اقتراح المنسّق الذي اعتمده بقوله «س-01 إلى س-24 الباقية: اقتراحك معتمد»). كل `[م]` اقتراح ينتظر الإقرار (الأسماء مجموعة في رأس الوثيقة). وما لا وسم عليه فمنقول من جدول PLAN-05 §٧-أ أو مقروء من الكود بوسمه.

### ٢.٠ — قواعد مشتركة لكل حقل جديد

1. **الخام لا القرار** (القاعدة 20، منقولة في الأمر): «التحويل القابل للعكس بلا فقد — لا ضرر فيه. وغير القابل للعكس — تنعيم، تقريب، جمع، اختيار ذروة، قلب إشارة مبنيّ على استنتاج — ممنوع في التطبيق». كل حقل جديد يُخزَّن كما تعطيه الواجهة، والقرار على الخادم.
2. **الغياب حالة صريحة** (القاعدة 22، منقولة): «كل مُدخل مفقود أو حقل غير موجود يُنتج حالة صريحة تظهر في أعلى مستوى، لا قيمة افتراضية ولا صفراً ولا نجاحاً». وكيف تُكتب على السلك بعد الحسم:

   | الموضع | القاعدة [ح س-01 · س-02] |
   |---|---|
   | داخل `v[]` | **لا NaN ولا null ولا حارس جديد.** القناة البطيئة (headroom والحالة الحرارية) **ليست في `v[]`**: قائمة بطابع في التذييل. والقناة المحجوبة **لا تُدرج في `variables[]`** فلا عمود لها، وحالتها في `channelDiscovery` |
   | في التذييل | الحقل القابل للغياب **يُرافقه دائماً حقل حالة حاضر**. السبب من الكود: الحمولة تُسقط المفاتيح ذات القيمة `null` — `GsonBuilder().create()` بلا `serializeNulls` (`FirebaseAnalysisService.kt:82` [ك])، و`JsonWriter.nullValue()` «skip the name and the value» (`gson-2.10.1`، `JsonWriter.java:449-456` [أ-١٤]) — بينما الملف يكتب `null` صراحةً (`TimeSeriesFileWriter.kt:47` [ك]). فالغياب في الحمولة **غياب مفتاح**، ولا يُعلَن إلا بمفتاح الحالة المرافق |
   | من يُسمّي الحالة | **التطبيق يُعلن السبب نصّاً، والخادم يُسمّي الحالة** [ح س-02]. فأسماء `UNSUPPORTED` / `BLOCKED` / `NOT_READ` أدناه مفردة مفاهيمية للوثيقة لا مفاتيح على السلك |
   | عنصر داخل قائمة | العنصر إمّا كامل أو يحمل `state`؛ لا عنصر ناقص بصمت |

   | الحالة (مفاهيمية) | تعني | مثال |
   |---|---|---|
   | غير مدعوم `UNSUPPORTED` | الواجهة غير موجودة على هذا الجهاز/المستوى أو أعادت «غير مدعوم» بقيمتها | `Long.MIN_VALUE` من `getLongProperty`؛ `SDK_INT < 30` لـ`getThermalHeadroom` |
   | محجوب `BLOCKED` | الواجهة موجودة والوصول مرفوض (`EACCES` / `SecurityException`) | `/sys/.../cpufreq` على بعض الطُرُز |
   | لم يُقرأ في هذه النقطة `NOT_READ` | القناة سليمة لكنها لم تُقرأ عند هذه العيّنة بتصميمها | **لم يعد له موضع في `v[]`** بعد الحسم (القنوات البطيئة صارت قوائم) |

3. **التمييز بنصّ الخطأ لا بصنف الاستثناء** (القاعدة 21): اليوم `readLongFromFile` يبتلع كل استثناء ويعيد `-1L` (`SystemFileReader.kt:733-739`)، فلا يبلغ `ChannelDiscoveryProbe` تصنيف `blockedBySelinux` من قراءة sysfs أبداً؛ وقائمة `blockedBySelinux` فارغة في **7/7** عيّنة ذهبية [أ]. التمييز `EACCES` مقابل `ENOENT` يلزمه حفظ **نصّ** الخطأ حتى `channelDiscovery.reasons` (N-07)؛ والمسبار يصنّف اليوم `BLOCKED` بـ`SecurityException` وحدها ويُسقط كل ما عداها إلى `MISSING` (`ChannelDiscoveryProbe.kt:188-199`). **والمفتاح `blockedBySelinux` اسمٌ يُنسب الحجب إلى SELinux قبل أن يُعرَف سببه**؛ يبقى الاسم للتوافق [م] ويحمل السببَ `reasons`.
4. **اسم القناة مصدر واحد مُصدَّر** (القاعدة 24): اليوم أسماء القنوات مكرّرة نصّاً في **49** سطراً عبر **5** ملفات (`ChannelDiscoveryProbe.kt` 24 · `V2Models.kt` 14 · `TimeSeriesFileWriter.kt` 9 · `AiAnalysisRequest.kt` 1 · `BatteryTests.kt` 1) [أ]. الأسماء الجديدة تُعرَّف مرّة في مصدر واحد يستورده المسبار — **وبعد الحسم يصير التعريف لكل فحص لا ثابتاً** (انظر ٢.١).
5. **كل حقل تذييل جديد يلزمه** ثلاثة مواضع (١.١٠): `TimeSeriesFooter` (الملف) · `TimeSeriesFooterDto` + سطر في `BehaviorFingerprintBuilder` (الحمولة) · قارئ الخادم. **وكل حقل جديد في نتائج الاختبارات** يلزمه `FunctionalResultDto`/`ManualResultDto` + `BehaviorFingerprintBuilder` + (حاملٌ للتخزين: س-29).

### ٢.١ — المصفوفة المحسومة: جدول PLAN-05 §٧-أ وأثر حسم 2026-10-08 على كل صفّ

| ت | الحقل (PLAN-05، منقول) | المواصفة النهائية | بطاقة |
|---|---|---|---|
| ت-01 | `v[11]` عدّاد الشحن لكل عيّنة | قناة `charge_counter_raw`، خام «كما أعلنها الجهاز» [ح س-09]، كل عيّنة | ٢.٢ ت-01 |
| ت-02 | الدورات من `EXTRA_CYCLE_COUNT` | لا فهرس جديد؛ `cycleCount`/`cycleCountSource` | ت-02 |
| ت-03 | `v[12]` headroom كل 10 ث | **ليس قناة**: `footer.thermalHeadroomReadings[]` قراءة كل 10 ث [ح س-01 · س-03] | ت-03 |
| ت-04 | `v[13]` الحالة الحرارية + المستمع | **ليس قناة**: `footer.thermalStatusEvents[] = {t, status}`، أوّلها قراءة عند التسجيل [ح س-04] | ت-04 |
| ت-05 | `v[14..16]` تردّد كل عنقود + `cpuinfo_max_freq` | **ليست `v[14..16]` بأسماء little/mid/big**: قناة لكل سياسة `cpu_freq_policy{N}_khz` + `footer.cpuPolicies[]` [ح س-05 · س-06] | ت-05 |
| ت-06 | `footer.batteryPairs[]` | عناصر `{t, voltageRaw, currentRaw, chargeCounterRaw, levelRaw, levelScale, tempRaw}` [ح س-28 المصحَّح، يلغي `voltageMv`/`levelPct` من س-10]، سقف 200، + حالة غياب صريحة [ح س-10] | ت-06 |
| ت-07 | عدّادات أخطاء الذاكرة | منتِج حقيقي معتمد [ح س-11] | ت-17 |
| ت-08 | حذف `storageSlcCacheSizeMb` | حذف | ت-08 |
| ت-09 | حالة صريحة لـ`storage*VerifyErrors` | منتِج حقيقي معتمد [ح س-12] | ت-16 |
| ت-10 | `maxCpuTemp` غياب صريح | يُسمّى بما هو + غياب صريح بسبب [ح س-13] | ت-10 |
| ت-11 | L-02 | `requestLocationUpdates` على GPS طوال نافذة العدّ + رقمان + `timeToFirstFixMs` [ح س-14] | ت-11 |
| ت-12 | تصحيح إعلانَي `v[0]` و`gpu_fps` | يُصحَّح الإعلان + إفراغ نافذة `gpu_fps` عند نهاية المرحلة 5 [ح س-21] | ت-12 |
| ت-13 | `workersPinned*` / `PinFailed*` | حالة صريحة (جرد س-23) | ت-13 |
| ت-14 | الأذونات | `ACTIVITY_RECOGNITION` مشروط بـAPI ≥ 29 [ح س-18] | ت-14 |
| ت-15 | ما يمسّ شكل النتائج | الحالات الأربع · شبكة اللمس · تغطية الإضاءة · «انتظر» [ح س-15 إلى س-17 · س-26] | ت-15 |
| ت-16 | *(جديدة من قرار 2026-10-08)* | تحقّق التخزين — منتِج حقيقي | ت-16 |
| ت-17 | *(جديدة)* | تحقّق الذاكرة — منتِج حقيقي | ت-17 |
| ت-18 | *(جديدة)* | إلغاء الافتراضيات التي تقرأ الغياب نجاحاً | ت-18 |
| ت-19 | *(جديدة)* | الختم والوحدات | ت-19 |

**التخطيط النهائي لـ`v[]`** (نكهة `launch`، `USE_V2_STRESS = true`):

```
v[0..10]   كما هي [ح]: لا يتغيّر فهرس ولا معنى. يتغيّر نصّ source لـv[0] وgpu_fps (ت-12)
           ووحدة v[8] وv[9] المعلنة (ت-19). وتبقى حرّاسها 0 و-1 كما في ١.٤ (س-27)
v[11..]    إلحاق فقط، لكل جهاز، بترتيب ثابت [م N-22]:
             charge_counter_raw         (إن اجتاز الاكتشاف)
             cpu_freq_policy{N}_khz     (لكل سياسة اجتازت، N تصاعدياً)
خارج v[]   thermal_headroom وthermal_status: قائمتان في التذييل (ت-03 وت-04)
```

* **الطول 11 + (عدد ما اجتاز الاكتشاف) — ليس ثابتاً بين الأجهزة.** `V2_SAMPLE_VAR_COUNT = 11` (`V2Models.kt:676`) و`FloatArray(V2_SAMPLE_VAR_COUNT)` (`DisciplinedSampler.kt:317-318`) يتوقّفان عن كونهما ثابتاً؛ وبعد الحسم **لا يميّز الفهرس بعد 10، الاسمُ هو المميِّز** (وهذا حال `datacollect` أصلاً — ١.١).
* **التسلسل [ك]:** الاكتشاف يعمل مرّة عند بدء الفحص (`StressTestService.kt:785-798`) **قبل** `sampler.start` (`:887`) وبعد `fileWriter.startNewFile` (`:755`)؛ والكاتب يختار قائمة `variables` عند `finalize` لا عند البدء (`TimeSeriesFileWriter.kt:373-374`) فلا مانع بنيوي من تمرير قائمة لكل فحص. **يلزم: اكتشاف ← بناء `variables[]` ← مصفوفة الحجم نفسه في المنتِج والكاتب والمسبار.**
* **أثر على الخادم:** `findings.js:1046-1051` يقرأ الفهارس 1..5 و10 بثبات — **آمنة** لأنها ≤ 10 (منقول، §٤.٢) [ن]. و`resolveChannelNames` تستكمل الأسماء من `FALLBACK_CHANNEL_NAMES_BY_INDEX` (`findings.js:57-62` [ن]) — **لا مدخل فيها للفهارس ≥ 11 بأسماء الأجهزة المتغيّرة**، فالاسم يجب أن يأتي دائماً من `variables[].name`.
* **أثر على `runConsistencyTest.js`:** شرط `v.length < 18` (`:43-80` [ن]) كان آمناً عند 17 ثابتاً؛ الطول الآن 11 + 1 + (عدد السياسات): **يبلغ 18 عند ست سياسات مع عدّاد الشحن (أو سبع بدونه)** [ي: 11 + 1 + 6] فيدخل فرع الضجيج بخريطة V1 القديمة. تحديثه إلى القراءة بالاسم قبل القبول (§٤.٢).

### ٢.٢ — بطاقات الحقول

#### ت-01 — `v[11]` عدّاد الشحن لكل عيّنة

| البند | القيمة |
|---|---|
| الاسم [ح س-09] | `charge_counter_raw` (بدل `charge_counter_uah` الذي كان مقترحاً في المسوّدة) |
| الوحدة · المصدر | «كما أعلنها الجهاز» [ح]؛ نصّ الوحدة المعلنة في `variables[].unit` ونصّ المصدر في `.source`: N-01 [م] |
| النوع | `Float` خام بلا تحويل؛ الأعداد الصحيحة دقيقة حتى 16 777 216 (2²⁴) [ي]. أعلى منها (> 16.7 Ah) يضيع الـµAh الأخير |
| الواجهة · API | `BatteryManager.getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER)` — المعرّف 1 في `android.jar` 33/34/35 [أ-٣]؛ API 21 [ن]؛ «تُقرأ في نداء العيّنة ذاته مع CURRENT_NOW» (PLAN-04 :42-43 [ن]) |
| الكادنس · المراحل | كل عيّنة · كل المراحل |
| الإدراج [ح س-01] | **تُدرج القناة فقط إن اجتازت الاكتشاف**؛ غير مدعومة (`Long.MIN_VALUE`) أو محجوبة ⇒ لا عمود، وحالتها وسببها في `channelDiscovery`. **ممنوع نسخ `snapshotHealth`** الذي يُسقط `0L` أيضاً (`BatteryReader.kt:578-579`) |
| الصفر عند الاكتشاف | المسبار اليوم يعدّ `0L` **مفقوداً** (`ChannelDiscoveryProbe.kt:173-177`، `v != 0L`)، وفي القناة الصفر قيمة. **محسوم [ح س-27 د]:** `charge_counter` بقيمة 0 عند الاكتشاف **يُدرج** (0 قيمة، والخادم يحكم)؛ ويُحدَّث المسبار ليقبل `0L` (ما عدا `Long.MIN_VALUE`) |
| فشل قراءة منتصف الفحص | **محسوم [ح س-27]:** آخر قيمة صالحة تبقى في `v[]` (المصفوفة رقمية) + `footer.channelReadFailures` يعلن فهارس العيّنات الفاشلة لكل قناة (بسقف وعدد)، ويستثنيها كل قارئ (القاعدة 25) |
| ما يحسبه الخادم | صنف العدّاد `STEPPED/CONTINUOUS` وراية `DESIGN_PINNED` — لا شيء منهما في التطبيق (القاعدة 20). **والوحدة** تستنتجها الخادم من السعة التصميمية والمستوى [ح س-09]: Honor MTN-NX1 `chargeCounterUah = 5596` · تصميم 8300 mAh · مستوى 75→72% [أ] ← القيمة تطابق **mAh لا µAh** [ي: استدلال، لا إعلان من الجهاز]؛ Samsung S10 5G `1711600` · تصميم 4400 · 43→38% ← µAh [أ] |
| المسبار (القاعدة 24) | اسم قناته اليوم `charge_counter` (`ChannelDiscoveryProbe.kt:45`، `:173`) — يصير `charge_counter_raw` من المصدر المشترك. **والاسم `charge_counter_uah` غير موجود في `app/src`** (0 سطر [أ-١٣]) |
| **قارئ الخادم** | **تعارض مع الاسم المحسوم:** `findings.js:1055-1060` يبحث `charge_counter_uah` بالاسم [ن، §٤.٢]. اسم محسوم جديد ⇒ القارئ يُحدَّث قبل قبول fv68 |
| ينتجه · يقرؤه | `DisciplinedSampler.buildSample` + تعريف `variables[]` لكل فحص · الخادم بالاسم |

#### ت-02 — الدورات من `EXTRA_CYCLE_COUNT`

| البند | القيمة |
|---|---|
| المفتاح | `footer.batteryHealth.cycleCount` (Int?) و`cycleCountSource` — **موجودان؛ لا فهرس جديد** |
| الواجهة · API | `intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, …)` من `lastBatteryIntent` — الثابت `"android.os.extra.CYCLE_COUNT"` في `android.jar` 34 و35 وغائب في 33 [أ-٣]. بلا إذن (PLAN-04 :58-61 [ن]) |
| الكادنس | مرّة واحدة؛ «عدد صحيح مرّة واحدة (التذييل و B‑08)» [ن] — اليوم **قارئان بحكمين** (١.٩) فيصيران قارئاً واحداً |
| `cycleCountSource` | المخطَّط (PLAN-04 [ن]): `extra_api34` · `sysfs:<path>` · `unavailable`. اليوم: `sysfs_cycle_count` · `battery_property_cycle_count_api_34` · `unavailable` · `no_battery_manager` [ك]. **fv68 يغيّر مفردات هذا الحقل النصّي**. **الخادم لا يقرؤه** (منقول، §٤.٢: «cycleCountSource لا يُقرأ») فتغيير المفردات لا يكسر قارئاً معروفاً؛ يبقى `docs/architecture/BATTERY_INDICATORS.md` (§٣) |
| الغياب | `cycleCount = null` + `cycleCountSource` يحمل السبب (القاعدة 22). الاسمان `CAPABILITY_BLOCKED`/`NOT_MEASURED` ليسا في كود التطبيق [أ] — **التطبيق يعلن السبب والخادم يُسمّي الحالة** [ح س-02] |
| الصفر | يُسقَط اليوم (`c > 0`، `BatteryReader.kt:569`) ← **يُقبل في fv68** لأنه عدد صحيح صحيح (بطارية جديدة) [م] — **ينتظر تأكيداً**. **حُسم (2026-10-13): يُشحن كما قُرئ، والخادم يصنّفه — §٧-ز، «تصنيف الخادم لصفر الدورات»** |
| مسارات sysfs | «لا تعمل على untrusted_app: صفر نجاح من 30 عقدة» (PLAN-04 :57 [ن]) وتؤيّده نتيجة تشخيص التطبيق (`2e31aa8`: «record that none are readable» [ك]) |
| التنفيذ | **منفَّذ 2026-10-08 (§٧-ز).** قارئ واحد `BatteryCycleCount.read` للتذييل و`B-08`: `EXTRA_CYCLE_COUNT` على API ≥ 34 ثم ملفّ sysfs ثم غياب معلن؛ `cycleCountSource` = `extra_api34` · `sysfs:<path>` · `unavailable`؛ الغياب `cycleCountState` = `not_read:<سبب sysfs>_and_<سبب الإضافة>`؛ و`getIntProperty(8)` حُذفت (javap: ليست عامّة) |

#### ت-03 — قائمة headroom (كانت `v[12]`)

| البند | القيمة |
|---|---|
| الموضع [ح س-01 · س-03] | `footer.thermalHeadroomReadings[]` — **ليست قناة في `v[]`** (القناة البطيئة تخرج من `v[]` إلى قائمة قراءات بطابع في التذييل). اسم القائمة وعنصرها: N-02 [م] |
| العنصر [م] | `{t, headroom, state}` — `t` ms منذ بدء الفحص كـ`v[0]` (N-04)؛ `headroom` `Float?` **الخام كما أعاده `getThermalHeadroom(0)`** (≥ 1.0 تقييد، `SystemFileReader.kt:269-270`)؛ `state` نصّ نتيجة النداء: `ok` · `nan` · `exception:<نصّ الخطأ>` · `api_below_30` |
| الكادنس [ح] | **قراءة تُسجَّل كل 10 ث** (أوّلها عند t≈0). **حارس الحاكم يبقى كما هو في fv68** (يقرأ كل ثانية، `StressTestServiceV2.kt:1471`)، والمجمِّع (`SamplerStats.updateThermalSidecar`) والمسبار كما هما؛ الثلاثة يتشاركون `readThermalHeadroom` وذاكرتها ≥ 1.1 ث (`SystemFileReader.kt:285`) — فقراءة القائمة تمرّ من الدالّة نفسها ولا تضيف نداءً إلى المنصّة بالضرورة [ي: استدلال من الكود، غير مقيس] |
| NaN | **لا يُكتب رقماً**: `NaN` يُسقط الكتابة والإرسال (§١.٣). نتيجة النداء `NaN` تصير `headroom` غائباً + `state = nan`. **التعارض** بين نصّ M4 («أكثر من مرّة كل 10 ث يُرجع NaN») وتعليق الكود («throws IllegalStateException if called more than once a second»، `SystemFileReader.kt:195-197`) **يُقاس على الجهاز في جولة الفحص** [ح س-03] — لم يُقَس بعد؛ والقائمة تُعلن أيّ النتيجتين وقعت بحالتها |
| الغياب الدائم | `headroomUnsupported` يُثبَّت عند **أول** `Throwable` لباقي الفحص ونصّ الخطأ **يُسجَّل فقط** (`SystemFileReader.kt:290-296`) ← القاعدة 21: نصّ الخطأ يُحفظ في `state`. وحالة القناة العامّة `thermalHeadroomState` (N-02) |
| ما يبقى من التذييل | `thermalHeadroomAvg` و`thermalHeadroomPeak` يبقيان مع الخام ويُعلَن أنهما **للعرض الانتقالي**، وتُحذفان حين ينقل الخادم قرّاءه إلى الخام (قرار لاحق) [ح س-20] |

#### ت-04 — `footer.thermalStatusEvents[]` (كانت `v[13]`)

| البند | القيمة |
|---|---|
| الموضع [ح س-04] | `footer.thermalStatusEvents[] = {t, status}` — **ليست قناة**. أوّل عنصر قراءة `getCurrentThermalStatus()` عند التسجيل، ثم عنصر لكل نداء `OnThermalStatusChangedListener` |
| القيم | `status` عدد صحيح 0..6 خام (`NONE=0 … SHUTDOWN=6`، تعليق `SystemFileReader.kt:302-303`)؛ `t` ms منذ بدء الفحص (N-04) |
| الواجهة · API | `PowerManager.getCurrentThermalStatus()` (API 29؛ الكود يفحص `Q` عند `:311`) + `addThermalStatusListener(Executor, OnThermalStatusChangedListener)` — موجودان في `android.jar` 30 و35 [أ-٣] |
| الغياب | `thermalStatusState` (N-03): `ok` · `api_below_29` · `listener_registration_failed:<نصّ>`. والحارس `-1` في `readThermalStatus` (`:310-318`) يبقى للمنطق الداخلي ولا يدخل القائمة |
| مصادر القيمة اليوم | `SystemFileReader.readThermalStatus` (المجمِّع) · `DynamicThermalThrottler.kt:137` و`:204` (يقرأ `pm.currentThermalStatus` مباشرة ويقرّر الإنهاء عند `>= 4` `:216`) · `ThermalFallback.kt:228` — ثلاثة مصادر مستقلة لقيمة واحدة، والمستمع رابعها؛ **لا يتغيّر أيّ منها في fv68** |
| ما يبقى | `thermalStatusMax` (`SamplerStats.kt:244-246`) للعرض الانتقالي [ح س-20] |

#### ت-05 — قنوات `cpu_freq_policy{N}_khz` و`footer.cpuPolicies[]`

| البند | القيمة |
|---|---|
| القنوات [ح س-05] | **قناة لكل سياسة باسمها `cpu_freq_policy{N}_khz`، بعدد سياسات الجهاز.** لا تسمية little/mid/big في التطبيق: التصنيف استنتاج على الخادم (القاعدة 20). kHz · `Float` · المصدر `/sys/devices/system/cpu/cpufreq/policy{N}/scaling_cur_freq` [م: نصّ source] |
| الإدراج [ح س-01] | تُدرج السياسة فقط إن قُرئ `scaling_cur_freq` عند الاكتشاف؛ المحجوبة لا تُدرج وسببها في `channelDiscovery.reasons` (N-07) |
| **تعداد السياسات** | **لا كود له اليوم:** `related_cpus` صفر سطر في `app/src` [أ-١٣]، ولا قراءة لمجلّد `cpufreq/policy*`؛ والقراءة الحالية بالنواة أو بمرشّحين مخمَّنين `0 · 4 · 6 · 7` (`SystemFileReader.kt:219-239`). **هل يسمح SELinux بتعداد المجلّد على كل طراز: غير معلوم من هذا المستودع** — يُقاس على الجهاز |
| `v[5]` | يبقى كما هو (أكبر ثلاث نوى، ١.٤)؛ والتعليقات والثوابت `IDX_CPU_FREQ_LITTLE/MID = -1` في `StressEffectivenessCalculator.kt:123-124` تُحدَّث |
| التذييل [ح س-06] | `footer.cpuPolicies[] = {policy, relatedCpus, cpuinfoMaxFreqKhz}` كما اقترح الوكيل. `policy` = N في اسم القناة؛ `relatedCpus` نصّ الملف كما هو [م: خام، لا تفكيك]؛ `cpuinfoMaxFreqKhz` `Long?` وغيابه يحمل `unreadableReason` (N-06). اليوم `cpuinfo_max_freq` يُقرأ لكل **نواة** (`SocNominalFrequencies.kt:105-123` يتوقّف عند أول ملف لا `exists()`) ويبقى في `SamplerStats` (`:168-170`) ولا يبلغ التذييل |
| الفهرس والاسم | `policy{N}` لا يتقاطع مع طراز بسياستين أو ثماني: العدد متغيّر والاسم يعلن N |
| الكلفة | قراءات sysfs إضافية بعدد السياسات في كل عيّنة (حتى 10 Hz في المرحلة 6) — **غير مقيس** |

#### توسيع `channelDiscovery` [ح س-02]

| البند | القيمة |
|---|---|
| المحسوم [ح س-02] | «توسيع channelDiscovery بقنوات fv68، مع إصلاح تمييز blocked/missing بنصّ الخطأ (القاعدة 21). التطبيق يُعلن السبب، والخادم يسمّي الحالة (القاعدة 20)» |
| الحال [ك] | `ChannelDiscoveryReport{attempted, available, blockedBySelinux, missing, summary}` (`V2Models.kt:578-584`)؛ **12** قناة مفحوصة في `CHANNELS` (`ChannelDiscoveryProbe.kt:34-47`، عدّ بأداة [أ-١٣])؛ تبلغ الحمولة بخريطة من خمسة مفاتيح (`BehaviorFingerprintBuilder.kt:239-247`)؛ و`thermalSensorAvailability` مشتقّ منه (`V2Models.kt:639-643`: ما ليس في `available` ولا `blockedBySelinux` ⇒ `"missing"`) |
| القنوات المضافة [م] | `charge_counter` تصير `charge_counter_raw` (ت-01)؛ وتُضاف `cpu_freq_policy{N}_khz` لكل سياسة عُدّت (ت-05)؛ وتبقى `thermal_headroom` و`thermal_status` مفحوصتين رغم خروجهما من `v[]` (حالتاهما تكمّلان `thermalHeadroomState` و`thermalStatusState`) |
| التمييز بنصّ الخطأ [ح] | تدخل القناة `blockedBySelinux` أو `missing` **بنصّ رسالة الخطأ** لا بصنف الاستثناء: `EACCES (Permission denied)` مقابل `ENOENT (No such file or directory)` — نصّ `FileNotFoundException` على Android [ن]، وهو مثال القاعدة 21. اليوم الصنف وحده يحكم (`catch` عند `:188` `SecurityException` ⇒ blocked، و`:195` غيره ⇒ missing)، و`readLongFromFile` يبتلع النصّ (`SystemFileReader.kt:733-739`) فلا يبلغ المسبار |
| السبب نصّاً [ح · م N-07] | `channelDiscovery.reasons`: خريطة اسم القناة ← نصّ الخطأ لكل قناة غير متاحة؛ سقف طوله س-35 |
| ما لا يتغيّر | المفاتيح الخمسة القائمة؛ ويبقى `blockedBySelinux` اسماً قديماً للتوافق (يُسمّي السبب قبل معرفته — ٢.٠ بند 3) |
| قارئ التطبيق | `thermalSensorAvailability` (`V2Models.kt:639-643`) يُسقط كل ما ليس blocked إلى `missing` — يتبع التمييز الجديد دون تغيير بنيته |

#### ت-06 — `footer.batteryPairs[]`

| البند | القيمة |
|---|---|
| المفتاح [ح س-10 · مصحَّح بـس-28: العناصر `{t, voltageRaw, currentRaw, chargeCounterRaw, levelRaw, levelScale, tempRaw}` لا `voltageMv`/`levelPct`] | `footer.batteryPairs[]` بعناصر **`{t, voltageMv, currentRaw, chargeCounterRaw, levelPct, tempRaw}`**، **سقف 200 عنصر**؛ `t` نسبي كـ`v[0]` (ms منذ بدء الفحص)؛ **التيّار خامّ غير مطبَّع** |
| الآلية | المستقبِل المسجَّل أصلاً لـ`ACTION_BATTERY_CHANGED` (`BatteryReader.kt:109-112` يحفظ `lastBatteryIntent` فقط، تسجيله `:144`). عند كل بثّ: تُقرأ فوراً `CURRENT_NOW` و`CHARGE_COUNTER` مع `EXTRA_VOLTAGE` و`EXTRA_LEVEL` و`EXTRA_TEMPERATURE` (PLAN-04 :64-65 [ن]) |
| قيد على التنفيذ [م] | قراءة `CURRENT_NOW` داخل المستقبِل **لا تُغذّي** عدّادات تثبيت المعامل (`uaEvidenceCount`/`maEvidenceCount`، `BatteryReader.kt:211-266`) ولا عدّاد `invalidCurrentReadings` — وإلّا فسدت أدلّة المعامل بنداءات ليست من العيّنة؛ تعليق v65 في `:339-365` يشرح العطب نفسه |
| الغياب [ح] | `batteryPairsState` (N-05): **`receiverRegistered`** (هل سُجّل المستقبِل) و**عدد البثّات** — حالة صريحة تميّز «المستقبِل لم يُسجَّل» (`receiverRegistered = false`) من «سُجّل ولم يصل بثّ» (عدد البثّات 0) من «وصل n بثّاً». `[]` وحدها لا تكفي. وما بعد 200: `droppedByCap` عدّاد [م]؛ والمحفوظ **أوّل 200** [م] (ليس ذروة ولا انتقاءً) |
| عنصر بقيمة غير صالحة | `Int.MIN_VALUE`/`Long.MIN_VALUE` (علامة الفشل، `BatteryChannelSanitizer.kt` `isInvalidCurrent`/`isInvalidLong`) **لا تُكتب رقماً**: يُحذف الحقل من العنصر ويُعَدّ في `invalidReads` [م] |
| العدد والمدى | **يُشتقّان من القائمة ولا يُكتبان** [م] (القاعدة 20؛ النصّ المنقول «أزواج (t, mV, µA)؛ عدد الأزواج؛ مدى التيار بينها» يعدّهما مما «يُسجَّل» — ينتظر تأكيداً) |
| الأسماء الخام | **محسوم [ح س-28 المصحَّح]:** `voltageRaw` · `levelRaw` مع `levelScale` — **تُلغي** `voltageMv` و`levelPct` في عناصر الأزواج؛ والخادم يستنتج الوحدة |
| عدد البثّات في 480 ث | **غير مقيس** (لا أداة له الآن) |

#### ت-07 — عدّادات أخطاء الذاكرة

انتقلت إلى بطاقة **ت-17** (المنتِج الحقيقي، حسم س-11).

#### ت-08 — حذف `storageSlcCacheSizeMb`

محسوم: حذف المفتاح، «افتراض 0 — G7» (PLAN-04 :96 [ن])؛ وكاشف الانهيار يحسبه الخادم من `storageThroughputSegments` فلا يُضاف في التطبيق (القاعدة 20). مواضع الحذف: `V2Models.kt:391` · `StressTestServiceV2.kt:1072` · `BehaviorFingerprintBuilder.kt:318` [ك]. الخادم لا يقرؤه في `computeScanSignature.js:45-53` (القائمة المنقولة بلا اسمه) [ن]. **الأثر على القرّاء:** المفتاح موجود (=0) في ≤ 67 ومحذوف في ≥ 68 — قارئ يشترط وجوده يتعطّل (القاعدة 25). ويُحذف اسمه من أيّ تعداد في `unmeasuredDefaults` إن ورد.

#### ت-09 — `storage*VerifyErrors`

انتقلت إلى بطاقة **ت-16** (المنتِج الحقيقي، حسم س-12).

#### ت-10 — `maxCpuTemp`

| البند | القيمة |
|---|---|
| المحسوم [ح س-13] | «غياب صريح بسبب، والقيمة اليوم «آخر قراءة» لا «أقصى» فتُسمّى بما هي. ويُحدَّث findings.js:912-915 في التزام القرّاء» |
| الحال [ك] | `Float` غير nullable في `TimeSeriesFooter` وفي `TimeSeriesFooterDto`؛ فرع «لا ملف» يكتب `0f` (`BehaviorFingerprintBuilder.kt:128`). الغياب يُعلَن مرّتين: رقم `-1` ونصّ `thermalSensorAvailability.cpuTemp = "missing"` — **في 7/7** [أ] |
| **ما يقوله الكود عن «آخر قراءة»** | `selectFooterMaxTemp` (`StressTestService.kt:137-145`) **سلسلة من ثلاثة فروع**: (1) `liveCpu > 0f` ⇒ قراءة حيّة تؤخذ عند الإنهاء بعد التبريد؛ (2) وإلا `snapshotTempSource == "cpu" && snapshotMaxTempC > 0f` ⇒ **`maxTempC` من `SamplerStats` — أقصى حقيقي لا آخر قراءة**؛ (3) وإلا `-1f`. فـ«آخر قراءة» صحيحة للفرع (1) وحده — **س-33** |
| المواصفة [م N-08] | يغادر المفتاح `maxCpuTemp` في ≥ 68 ويحلّ محلّه: `cpuTempFinalC` (`Float?`) + `cpuTempFinalSource` (نصّ حاضر دائماً): `live_read_at_finalize` · `sampler_max_cpu` · `absent:<السبب>`. في الملف `null` وفي الحمولة يغيب المفتاح ويبقى `cpuTempFinalSource` يعلن السبب. وفرع «لا ملف» يكتب `absent:no_time_series_file` بدل `0f` |
| القرّاء | `findings.js:912-915` يقرأ `maxCpuTemp === -1` حالةَ `NO_CPU_TEMP_SENSOR` [ن، §٤.٢] ← يُحدَّث؛ ويقرأ ≤ 67 المفتاح القديم. و`GenerateReportUseCase.kt:63` (`summary.maxCpuTemp > -1f`) يقرأ ملخّصاً من `v[]` لا من التذييل (`TimeSeriesFileReader.kt:159-217`) فلا يتأثّر |
| T-01 | يستعمل ناتج الدالّة نفسها (`StressTestService.kt:1343`) ولا يتغيّر بتسمية المفتاح — **محسوم [ح س-33]:** حقل واحد ومصدره معلن، وسلوك T-01 المشترك لا يتغيّر |

#### ت-11 — L-02

| البند | القيمة |
|---|---|
| المحسوم [ح س-14] | **رقمان: مرئية ومستعمَلة، وtimeToFirstFixMs أو غيابه. وطلب requestLocationUpdates على GPS طوال نافذة العدّ، والترتيب يبقى** (L-02 قبل L-03) |
| الحال [ك] | `GnssStatus.Callback` فقط (`GpsTests.kt:105-115`، التسجيل `:124`) ثم `delay(30_000L)` (`GpsTests.kt:131`) — لا شيء يُشغّل محرّك GNSS؛ L-03 يطلب `requestSingleUpdate` (`:217`) **بعده**. الإذن `ACCESS_FINE_LOCATION` معلَن ومطلوب اليوم (`ScanPermissions.kt:77-81`) |
| الآلية [م] | `requestLocationUpdates(GPS_PROVIDER, …)` بمستمع طوال نافذة الثلاثين ثانية، وإلغاؤه في `finally` مع إلغاء استدعاء الأقمار؛ `SecurityException` عند سحب الإذن بين الفحص والنداء تُعلَن حالة لا استثناءً يسقط الاختبارات اللاحقة (كما يفعل التعليق في `GpsTests.kt:118-122`) |
| الأرقام [م N-15] | `visibleSatellites` = **آخر** `satelliteCount` و`usedSatellites` = **آخر** عدد `usedInFix` — **آخر استدعاء لا الأقصى** (`GpsTests.kt:105-115` يكتب `set` لا `max`) — و`satelliteStatusCallbacks` عدد الاستدعاءات؛ `timeToFirstFixMs` من `GnssStatus.Callback.onFirstFix(ttffMillis)` [ن: واجهة Android، غير مقيس هنا] ويغيب مع سبب إن لم يقع fix في النافذة؛ `locationUpdateCount` عدد التحديثات |
| الغياب اليوم | `"N/A"` (رفض إذن/فشل تسجيل → `not_tested`)، `"0"` (لا `LocationManager` → `unavailable`، أو الموقع معطَّل → `fail`، أو لا تحديثات → `fail`). `rawValue = "$used / $total"` نصّاً (`:163`) |
| الحامل | `FunctionalResultDto` لا يحمل أعداداً منفصلة (`{testId, componentName, categoryName, rawValue, rawValueUnit, status, notes}`، `AiAnalysisRequest.kt:187-195`) ← `measurements` (N-16) وحامله **محسوم [ح س-29 أ]:** عمود Room جديد (ترحيل 7 ⇒ 8) + حقل في `TestResult` + `measurements` في الـDTO، للآلي واليدوي معاً |
| الفرق بين «المحرّك لم يعمل» و«0 أقمار» | **محسوم [ح س-34]:** التطبيق يرسل الأرقام (`satelliteStatusCallbacks` وغيرها) والخادم يسمّي الحالة؛ ويبقى `rawValue` نصّياً للتوافق [م] |

#### ت-12 — تصحيح إعلانَي `v[0]` و`gpu_fps` وإفراغ نافذة `gpu_fps`

| البند | القيمة |
|---|---|
| المحسوم [ح س-21] | **يُصحَّح الإعلان، وتُفرَّغ نافذة gpu_fps عند نهاية المرحلة 5 (تغيير صغير)** |
| الموضع الوحيد للإعلان | `V2Models.kt:648` و`:673` — يتدفّق إلى الملف والحمولة تلقائياً [ك]. النصّان **بيانات تُكتب في `variables[].source` على السلك** لا نصّ واجهة (القرار ٩٠ يخصّ الثاني) |
| نصّ `v[0]` [م N-21] | `SystemClock.elapsedRealtime() minus scan start (ms since scan start)` |
| نصّ `gpu_fps` [م N-21] | `OpenGL offscreen frame counter; rate over the last 4 sampler queries (about 1 s at 4 Hz); window cleared at the start of phases 4 and 5 and at the end of phase 5; 0 outside phases 4 and 5` — **يتوقّف نصّه الأخير على تنفيذ إفراغ النافذة**؛ وإلّا فالنصّ الأطول في المسوّدة (`… and for up to 4 samples after the load stops`) هو الصادق |
| إفراغ النافذة [ك+ي] | `GpuFpsTracker.reset()` يُستدعى اليوم عند **بدء** مرحلتي 4 و5 فقط (`StressTestServiceV2.kt:787` · `:893`)؛ ومولِّد المرحلة 5 يُوقَف عند `:1020` (`gpuActive.stop()`)، فبعد التوقّف تدخل نافذة الاستعلامات الأربعة شرائح صفرية واحدةً بعد أخرى وتبقى القيمة غير صفرية حتى تُزاح الشرائح الأربع (`FpsWindow.push`، `GpuFpsTracker.kt:47-66`) — تفسير الذيل غير الصفري الذي نقله المنسّق (3-5 تحديثات في المرحلة 6) [ن، غير مقيس]. المطلوب: `GpuFpsTracker.reset()` مباشرةً بعد `gpuActive.stop()` عند `:1020` [م: الموضع] |
| قارئ `source` | تعليق DTO: «Mirrors VariableDefinition.source … kept identical so backend can route variables by source verbatim» (`AiAnalysisRequest.kt:77-79`). **جواب المنسّق (منقول، `neuralcheck-backend@19fc7bc`): لا قراءة لـ`.source` في `findings.js`؛ `resolveChannelNames` تقرأ `v.index` و`v.name`** [ن] ← تغيير النصّ لا يكسر ذلك القارئ |
| الذهبية | العيّنات الذهبية تحمل النصّ القديم (`v6-reports-document-sample.json:1078` و`:1128`، وفي الست V7 الأخرى) فتُعاد توليدها في التزام التغيير نفسه (القاعدة 39) |

#### ت-13 — `workersPinned*` / `PinFailed*`

| البند | القيمة |
|---|---|
| **نتيجة الفحص** | **نعم، لم يُحاوَل.** الحقول الستّة تُكتب من `accumulateCpuOutcome` فقط (`StressTestServiceV2.kt:379-394`)، المُستدعاة من احتياطي NEON (`:407`) ومن مسار قديم (`:756`، `:1015`). على مسار الذكاء (`USE_AI_WORKLOAD_CPU = true`، `:94`) لا استدعاء ⇒ `0` = «لم يُحاوَل» لا «نجح صفر». مؤيَّد بـ`cpuStressFallbackUsed=false` في 7/7 [أ] |
| المواصفة بعد س-23 | **حالة صريحة بدل الصفر** [ح س-23 · أمر المنسّق: «كل بند في جردك §١.١١ بقيمته الجديدة (حالة صريحة)»]: تبقى المفاتيح الستّة وتصير `Int?` — `null` حين لم يُحاوَل — ويرافقها `workersPinState` (N-12): `not_attempted:ai_workload_path` · `attempted` |
| ما لم يُحسم | حذف المفاتيح الستّة أو محاولة التثبيت على مسار الذكاء — لم يحسمهما النصّ؛ **الحالة الصريحة هي ما يطلبه س-23 فقط** |

#### ت-14 — الأذونات (§٧-ب)

| البند | القيمة |
|---|---|
| المحسوم [ح س-18] | **`ACTIVITY_RECOGNITION` يُضاف، ومشروطاً بـAPI ≥ 29.** والموقع معلَن ومطلوب اليوم؛ الناقص طلب موقع فعلي أثناء L-02 (ت-11) |
| الحال | `ACTIVITY_RECOGNITION`: صفر ذكر في `app/src` [أ-١١]. الموقع: `ACCESS_FINE_LOCATION` و`ACCESS_COARSE_LOCATION` معلَنان (`AndroidManifest.xml:7-8`) والمطلوب وقت التشغيل `FINE` فقط (`ScanPermissions.kt:77-81`). `minSdk = 28` (`build.gradle.kts:73`) |
| (١) الإعلان | `uses-permission ACTIVITY_RECOGNITION` في `AndroidManifest.xml` [م]؛ الإذن موجود من API 29 [ن: توثيق Android] |
| (٢) `ScanRuntimePermission` | مدخل خامس `ACTIVITY_RECOGNITION` بـ`autoNotTestedIds = [SN-08, SN-09]` [م]. التعليق في `ScanPermissions.kt:14-30` يشترط أن تُقرأ القوائم من **سلوك الكود الفعلي** لا من `TestSpec` — فالمدخل لا يصحّ حتى يُفعَل ذلك السلوك: **`SensorTests.testSensor` لا يفحص إذناً اليوم** (`SensorTests.kt:56-106`؛ والمستشعران في `:39-40`) ← يلزم فحص `checkSelfPermission` على API ≥ 29 ثم `NOT_TESTED` عند الرفض. وقارئاه `PreScanScreen.kt:94`، `:440-561` و`ScanRuntimePermissionTest.kt` |
| (٣) الطلب مشروط بالمستوى | على API < 29 يُستثنى من حزمة البوّابة `ScanRuntimePermission.ALL` (`entries.toList()`، `ScanPermissions.kt:90`) [م]؛ ولا إذن يُطلب ولا قائمة تُعرض |
| نصّ الواجهة | `labelRes` يحتاج سلسلة جديدة؛ **لا نصّ جديد بالتركية/الإنجليزية (القرار ٩٠)**: `lint` يعطّل `MissingTranslation` (`build.gradle.kts:494`) فيكفي العربي في `values/` [ك] — ينتظر تأكيد المنسّق أن القرار ٩٠ يتّسع لذلك |
| SN-08/SN-09 بلا حركة [ح] | «لا قراءات بلا حركة» يصير **«غير مقيس» لا WARNING (تصنيف الخادم)** — من يعيّن الحالة: **س-32**. الحال اليوم: لا قراءات ⇒ `WARNING` (`SensorTests.kt:99-101`) |

#### ت-15 — ما يمسّ شكل النتائج (الدفعة ١٠، §٧-ج)

المطلوب هنا **شكل النتيجة** لا الواجهة. حامل الخام المركّب لكل ما يلي — `measurements` (N-16، مُقرّ) — **محسوم [ح س-29 أ]:** عمود Room جديد (7 ⇒ 8).

**(أ) الحالات الأربع [ح س-15].** المفاتيح: **`pass` · `fail` · `not_tested` · `unavailable`**. ما لا يملكه الطراز **لا سجلّ له**، وقائمة **`hiddenByModel`** في رأس النتائج اليدوية.

| البند | القيمة |
|---|---|
| الحال [ك] | المخزَّن في الإجابات اليدوية ثلاثة: `pass` · `fail` · `not_tested`. زرّ «غير متوفر في هذا الجهاز» ينادي `onResult("not_tested")` في خمسة مواضع (`ManualTestDialogs.kt:796-797` · `:999-1000` · `:1108-1109` · `:1143-1144` · `:1292-1293`؛ العدّ 5 [أ-١٣]) |
| المطلوب | تلك المواضع الخمسة تنادي `onResult("unavailable")`؛ و«لم أختبر» تبقى `not_tested` |
| `TestStatus.fromString` | **يعرف `"unavailable"` أصلاً** (`ScanReport.kt:53`) — فالمفتاح الرابع لا يسقط إلى `ERROR`. ما يحتاج التحديث في الالتزام نفسه [ح] هو المفتاح الجديد `not_measured` (ب/ج أدناه) وكل ما سواه يسقط إلى `ERROR` (`:56`). عموم قرّاء المفردة: ق-18 (§٤.١) |
| الدرجة | `setResult` يكتب `score = null` لغير `pass`/`fail` (`ManualTestsViewModel.kt:105-115`) — فـ`unavailable` بلا درجة [ك] |
| `hiddenByModel` [م N-17] | `manualResults` قائمة (`AiAnalysisRequest.kt:27-34`) فلا رأس لها؛ يُضاف شقيق `manualResultsHeader.hiddenByModel` (قائمة `testId`). وما أُخفي **يُستثنى من `allIds`** (`ManualTestsScreen.kt:144`) وإلّا ملأه `commitAndContinue` بـ`not_tested` (`ManualTestsViewModel.kt:147-148`) |

**(ب) شبكة اللمس M-02 [ح س-16].** «يُشحن الخامّ: الأبعاد، والملموس من الكلّي، والزمن، وسبب الإنهاء، وأقصى أصابع متزامنة. والعتبات على الخادم».

| البند | القيمة |
|---|---|
| الحال [ك] | شبكة 8×15 = 120 خلية (`ManualTestDialogs.kt:232-233`)؛ العتبات 95/80 للنصّ فقط (`:239-243`)؛ لا يُشحن عدد الخلايا ولا النسبة |
| الخام المشحون [م N-19] | `gridCols` · `gridRows` · `touchedCells` · `totalCells` · `elapsedMs` (من أول لمسة؟ أم من فتح الشبكة؟ **ينتظر تأكيداً**) · `endReason` · `maxSimultaneousPointers` **[حُسم 2026-10-10 بقرار المستخدم: `elapsedMs` من أوّل لمسة لا من فتح الشاشة، و«الزمن حتى أوّل لمسة» حقل منفصل `timeToFirstTouchMs` — §٧-ح]** |
| `endReason` [م] | `auto_complete` (نجاح تلقائي عند اكتمال الخلايا) · `user_marked_not_working` · `user_marked_not_tested` · `dismissed` — ويميّز النجاحَ التلقائي من اليدوي (س-16 الأصلية) |
| ما يتغيّر في التطبيق | العتبتان 95/80 لا تدخلان العقد؛ المحسوم أن **الخادم** يضع العتبات على الخام. `maxSimultaneousPointers` يتطلّب عدّ `event.changes.count { pressed }` في معالج اللمس (`ManualTestDialogs.kt:278-295`) [م] |

**(ج) تغطية الإضاءة SN-04 [ح س-17].** «قائمة القراءات بطابع قبل التغطية وأثناءها. و«غير مقيس» حالة بسبب، لا not_tested».

| البند | القيمة |
|---|---|
| الحال [ك] | `testSensor` عامّ لكل المستشعرات: نافذة 3 ث (`SensorTests.kt:93`)، متوسّط القراءات نصّاً في `rawValue` (`:104-106`)، صفر في كلها ⇒ `WARNING` (`:102-103`)؛ لا طابع زمني للقراءة |
| الخام المشحون [م N-20] | `readingsBeforeCover[]` و`readingsDuringCover[]` بعناصر `{t, values}` — `t` من `event.timestamp` نسبةً إلى بدء الاختبار [م]، و`values` نسخة `event.values` |
| الحالة [مُلغاة بقرار س-32 ب: لا يرسل التطبيق `not_measured`؛ الخادم يصنّف «غير مقيس» من الخامّ] | `not_measured` + `measurements.notMeasuredReason` (N-18) [م]: «الصفر بلا تغطية» حالة بسبب ولو خرج الصفر قراءة حقيقية؛ **ليست `not_tested`** [ح]. من يعيّنها: س-32 |

**(د) «انتظر» [ح س-26].** «انتظر» بدل الخطأ عند «قيد المعالجة» (409)، ومعه إبقاء `sessionId` عند إعادة المحاولة. **لا يمسّ شكل النتائج ولا حمولة fv68** — `sessionId` مفتاح طلب موجود (`FirebaseAnalysisService.kt:140-141`) ولا يُضاف مفتاح. ينتمي إلى دفعة الواجهة لا إلى هذا العقد.

#### ت-16 — تحقّق التخزين: منتِج حقيقي (يحلّ محلّ ت-09) [ح س-12]

«س-11 وس-12 ود-٠-هـ: منتِج حقيقي لتحقّق التخزين والذاكرة. معتمد.»

| البند | القيمة |
|---|---|
| الحال [ك] | `storageWriteVerifyErrors = 0` و`storageReadVerifyErrors = 0` حرفيان (`StressTestServiceV2.kt:1070-1071`)، لا قراءة راجعة ولا مقارنة في طور التخزين (`:425-595`) ← **صفر ثابت لا قياس**. بوّابة `STORAGE_VERIFY_ERRORS` في `categoryStates.js:410` **حالتها `NOT_WIRED`** (منقول، §٤.٢) [ن] — شرط حرف التخزين |
| الآلية المنقولة | PLAN-04 M6 :95 [ن]: «القراءة التتابعية بـopen(O_DIRECT)… على ملف ≥ 256 MiB … +2 ث». `O_DIRECT` صفر سطر في `app/src` [أ-١٣] — لا كود له اليوم |
| ما في الكود ويحكم التصميم [ك] | (أ) نمط الكتابة **محدَّد قابل للإعادة**: مقطع 1 MiB `ByteArray(1024 * 1024) { (it and 0xFF).toByte() }` يتكرّر (`:486`) فالمقارنة ممكنة بلا تخزين نسخة؛ (ب) الملف يُحذف في `finally` (`:563`) — **القراءة الراجعة تسبق الحذف**؛ (ج) سقف البايتات 1–4 GiB **لا يتغيّر** (قرار «DECIDED 2026-09-14 — DO NOT REOPEN WITHOUT NEW NUMBERS» في التعليق `StressTestServiceV2.kt:436-468`) — القراءة لا تكتب فلا تمسّ عمر NAND [ي: استدلال]؛ (د) الكتابة تنتهي عند السقف أو `deadline` وما بقي من المرحلة يُنتظَر (`:582-586`) ← القراءة الراجعة تعمل في الوقت المتبقّي، وإن لم يبقَ ⇒ «لم يجرِ» |
| الحقول [م N-09] | `storageWriteVerifyErrors: Int?` (أخطاء الكتابة: استثناء I/O أو كتابة قصيرة أو فشل `sync`) · `storageReadVerifyErrors: Int?` (عدد الكتل 1 MiB المخالفة عند القراءة الراجعة) · **`storageVerifyState`** · `storageVerifyBytesCompared: Long?` · `storageVerifyDurationMs: Long?` · `storageVerifyMethod` (`o_direct_sequential`). الخام: عدد، لا حكم (القاعدة 20) |
| حالة «لم يجرِ» [أمر المنسّق: «وحالة «لم يجرِ»» · ح س-23] | `storageVerifyState = "not_run:<سبب>"` — أسباب [م]: `no_time_left` · `o_direct_open_failed:<نصّ الخطأ>` · `write_failed:<نصّ>` · `file_smaller_than_256MiB`. **حين لا يجري: العدّادان غائبان (`null`) لا `0`**. والصفر مقيس حقيقي **فقط** مع `storageVerifyState = "ran"` |
| `storageSlcCacheSizeMb` | يُحذف (ت-08) |
| القرّاء | `computeScanSignature.js:50-51` **`storageWriteVerifyErrors: vr.storageWriteVerifyErrors \|\| 0`** — يقرأ الغياب صفراً (القاعدة 22) [ن] ← يُحدَّث؛ `reevaluationDecision.js:30-31`؛ `categoryStates.js:410`. وفي التطبيق `footerVerificationToMap` يلزمه سطر لكل مفتاح جديد (القاعدة 25) |
| الكلفة | «+2 ث» (M6 [ن]) — **غير مقيس**؛ وأثر `O_DIRECT` على أجهزة بكتل غير 4 KiB غير معلوم |
| الجدولة داخل المرحلة | **محسوم [ح س-30]:** قراءة راجعة لآخر 256 MiB مكتوبة بنمط حتمي بالإزاحة، و`O_DIRECT` مع رجوع معلن إلى `DONTNEED` (`storageVerifyMethod`)؛ والعدد الخامّ يُشحن والخادم يشتقّ `…Passed` |

#### ت-17 — تحقّق الذاكرة: منتِج حقيقي (يحلّ محلّ ت-07) [ح س-11]

| البند | القيمة |
|---|---|
| المحسوم [ح س-11] وتفصيله في أمر المنسّق | وصل `StressNative.runMemoryStress` (march / walking-1s / أخطاء عشوائية، `StressTestServiceV2.kt:627-635`) بالمسار الفعلي **داخل مرحلة الذاكرة الثابتة 60 ث** |
| الحال [ك] | الاستدعاء اليوم في فرع `else` الذي لا يعمل إلا حين `USE_AI_WORKLOAD_MEMORY == false` أو لا مُنفِّذ ذكاء (`:599`؛ وقيمة الثابت `true` عند `:107`؛ الفرع `:619-643`)؛ مسار الذكاء `aiMemoryWorkload.run` لا يقارن شيئاً بقيمة متوقَّعة. حقول النجاح حُذفت من `VerificationSummary` في 1.7.B (`V2Models.kt:358-365`) ولا تمرّرها `footerVerificationToMap`. افتراضات `verifMemMarchOk = true` و`verifMemWalkOk = true` و`verifMemRandomErrors = 0` (`:307-309`) تقرأ الغياب نجاحاً |
| أسماء الخادم [ن] | «الأسماء التي يراقبها الخادم اليوم ولا تصل — A76» (PLAN-04 :124): **`memoryMarchPassed` · `memoryWalkingOnesPassed` · `memoryRandomVerifyErrors`** (`computeScanSignature.js:45-53`)، و`memoryTestedBytes` موجود. الموضع `verificationResults`. الأنواع: `Boolean?` · `Boolean?` · `Int?` — **لا `Boolean` افتراضي `true`** |
| **ما تُنتجه النواة فعلاً** [ك] | `runMemoryStress` تُرجع خمسة أعداد: `march_errors` · `walking_ones_errors` · `random_verify_errors` · `tested_bytes` منقسماً (`stress_jni.cpp:138-160`، `StressNative.kt:196-210`). **`random_verify_errors` ثابت `0`** — التعليق: «random_verify skipped — reference is size-specific» (`production_stress.cpp:474`)، فمرجع SHA مرتبط بحجم 32 MiB (`MEMORY_RANDOM_SHA_BUFFER_SIZE`؛ `runMemoryTest` تعمل على 32 MiB وحدها: `stress_jni.cpp:239-274`). و**فشل `sysinfo` أو التخصيص يُرجع `{0,0,0,0}`** (`production_stress.cpp:426` · `:464`) — صفر أخطاء وصفر بايت، لا يميَّز من النجاح إلا بـ`tested_bytes == 0` |
| **التعارض مع الأمر — حُلّ [ح س-11 · س-30]:** «تحقّق عشوائي حقيقي في C++، وفشل الحجز حالة صريحة لا {0,0,0,0}» | (النصّ الأصلي للتعارض:) وصلُ النواة كما هي ينتج `memoryRandomVerifyErrors = 0` **افتراضاً** وهو ما يمنعه س-23؛ و`{0,0,0,0}` يصير نجاحاً إن قُرئ بلا فحص `tested_bytes`. **لا أخمّن حلّاً** — س-30: (أ) يُكتب تحقّق عشوائي مستقلّ عن الحجم، أو (ب) يُعلَن `memoryRandomVerifyErrors` «لم يجرِ» حتى يُكتب |
| الحقول [م N-10] | `memoryVerifyState` (`ran` · `not_run:<سبب>`: `allocation_failed` · `sysinfo_failed` · `no_time_left` · `random_verify_not_implemented`…)؛ `memoryVerifyBytes: Long?` = حجم المخزن الذي مرّت عليه الاختبارات (`tested_bytes`)؛ و`memoryTestedBytes` **يبقى بدلالته** (بايتات لمس عمل الذكاء، مقام عرض النطاق) — **لا يُدمجان** إذ لهما مصدران مختلفان على المسار الفعلي. **الخام الأرقى:** `Passed` (= `errors == 0`) يفقد عدد الأخطاء فتُقترح `memoryMarchErrors` و`memoryWalkingOnesErrors` اختياريتين (القاعدة 20) — **محسوم [ح س-30]: «العدد الخامّ يُشحن»** فيُشحنان |
| «لم يجرِ» | `memoryVerifyState = "not_run:<سبب>"` والحقول الثلاثة غائبة (`null`) لا `true` ولا `0` |
| الجدولة [ك] | **محسوم [ح س-30]:** النواة أوّلاً بسقف زمني داخل الستّين ثانية ثم عمل الذكاء للباقي، ونافذتها معلنة في `phaseBoundaries`. (الحال [ك]:) النواة **غير مقيّدة بزمن** (مخزن بين 512 MiB و8 GiB حسب الذاكرة الحرّة، `production_stress.cpp:446-455`)؛ تعليق الكود يقول إن جهازاً بـ8 GB أنهى March + Walking في 25 ث (`StressTestServiceV2.kt:667-669`) — **ادّعاء غير مقيس**. وعمل الذكاء يملأ اليوم الستّين ثانية (`aiMemoryWorkload.run(V2Phase.MEMORY_STRESS.durationMs, …)`) وينشر `v[9]` حيّاً ~1 Hz. **ترتيب العمليتين داخل 60 ث (تتابع أم تزامن؟) غير محسوم** — **س-30** |
| `memBandwidthMBps (÷10⁶)` | في نصّ M11 (:124) لا في الجدول المحسوم؛ ولا حقل بهذا الاسم في الكود، و`v[9]` ثنائي (`AiMemoryWorkload.kt:157`). **لا يُضاف** — لم يحسمه النصّ [م] |
| القرّاء | `computeScanSignature.js:45-53` يراقب الأسماء [ن]؛ و`footerVerificationToMap` (`BehaviorFingerprintBuilder.kt:286-345`) يلزمه سطر لكل مفتاح؛ `VerificationSummary` تعود إليه الحقول |
| ما يُقاس | زمن النواة على الأجهزة (عدّه المنسّق جولة الفحص الميداني) — **غير مقيس** |

#### ت-18 — إلغاء الافتراضيات التي تقرأ الغياب نجاحاً (س-23)

«س-23: إلغاء كل قيمة افتراضية تقرأ الغياب نجاحاً. معتمد (القاعدة 22).» وأمر المنسّق: «كل بند في جردك §١.١١ بقيمته الجديدة (حالة صريحة)». **البنود الثلاثة عشر هي صفوف §١.١١ بالترتيب** (العدّ 13 من الجدول [أ-١٣]). العمود «يقرأ نجاحاً» حكمي من الكود: «نعم» = القيمة الافتراضية تُقرأ سلامةً/نجاحاً؛ «لا» = حارس أو ثابت يُقدَّم قياساً (مُدرَج لأن الأمر يطلب كل صفوف الجرد).

| # | المسار:السطر | اليوم | يقرأ نجاحاً | القيمة الجديدة (حالة صريحة) [م] |
|---|---|---|---|---|
| 1 | `V2Models.kt:368` | `gpuComputeGemmCorrect = true` | نعم | `Boolean?` — `null` حين لم تُنفَّذ مرحلتا 4/5 (قطع عند `governorCappingDetected` أو غيره) + `gpuComputeGemmState` (N-11): `passed` · `failed` · `not_run:<سبب>` |
| 2 | `StressTestServiceV2.kt:310` → `:1081` | `verifGpuComputeOk = true` ⇒ `criticalDefectDetected = false` | نعم | `criticalDefectDetected` `Boolean?` — `null` حين يغيب أيّ مُدخل؛ و`criticalDefectInputsNotRun[]` (N-11) تسمّي ما لم يجرِ. **ما يدخل فيه من أخطاء التخزين والذاكرة: محسوم [ح س-31]** — مجمَّع انتقالي معلَن في `transitionalAggregates` والحكم من الخامّ في الخادم؛ لا يُوسَّع شرطه هنا (التعليق `:1077-1080` يذكر التخزين لكن الشرط لا يقرؤه) |
| 3 | `StressTestServiceV2.kt:1070-1072` | `storage*VerifyErrors = 0` · `storageSlcCacheSizeMb = 0` | نعم (الأوّلان) | الأوّلان: ت-16 (`null` + `storageVerifyState`)؛ الثالث: يُحذف (ت-08) |
| 4 | `V2Models.kt:407-412` | `workersPinned*/PinFailed* = 0` | نعم (`PinFailed*`) | ت-13: `Int?` + `workersPinState` |
| 5 | `StressTestService.kt:137-145` | `maxCpuTemp = -1f` | لا (حارس) | ت-10: `cpuTempFinalC?` + `cpuTempFinalSource` |
| 6 | `StressTestService.kt:1599-1606` | `isStill = true` عند < 2 عيّنة تسارع، `accelStdDev = -1` | نعم | `isStill: Boolean?` `null` و`accelStdDev: Float?` `null` + `stillnessState` (N-13): `measured` · `insufficient_accel_samples` |
| 7 | `StressTestService.kt:1668` | `preScanWarnings = []` عند فشل القراءة | نعم («نظيف») | `List?` `null` + `preScanWarningsState` (N-14): `read` · `read_failed:<نصّ>`. **المفتاح ليس في `TimeSeriesFooterDto`** (١.١٠) فيبقى في الملف حتى يُضاف إلى الحمولة (س-22) |
| 8 | `StressTestService.kt:1851-1862` | `cpuTempCoveragePct = 0` · `gpuTemp… = 0` · `skinTemp… = 0` · `cpuTempPrimarySource = "moved_to_footer_v5"` | لا (ثوابت تُقدَّم قياساً) | تُدرج أسماؤها في `unmeasuredDefaults` (الآلية المُعلَنة القائمة) بدل أن تُقرأ أصفاراً مقيسة؛ **ولا حذف** — الحذف تغيير شكل `dataQuality` لم يُحسم |
| 9 | `BatteryIndicators.kt:132-143,158` | `0.0` لمتوسّط/انحراف/نسبة تصميم عند < 2 عيّنة أو غياب السعة؛ `designMah = … ?: 0` | نعم (`voltageStabilityMv = 0.0` = «مستقرّ») | `null` لكل مؤشّر لم يُحسب، ومفتاح `<الاسم>State` حاضر بالسبب؛ و`designCapacityMah` `Int?` `null` (يرافقه `designCapacitySource="unavailable"` أصلاً) |
| 10 | `BatteryReader.kt:556,569,578-579` | `cycleCountSource="unavailable"` + إسقاط `0` · `chargeCounterUah`/`energyCounterNwh` يُسقط `0L` | لا | ت-02 (الصفر مقبول، ينتظر تأكيداً) و`chargeCounterUah`/`energyCounterNwh` الصفر الحقيقي قيمة [م] |
| 11 | `BehaviorFingerprintBuilder.kt:105-142` | فرع «لا ملف»: ترويسة وتذييل بأصفار وسلاسل فارغة (`maxBatteryTemp = 0f` · `intensityFactor = 0f` …) | لا (تشبه القياس) | حقول قابلة للغياب + `timeSeriesState = "no_file:<سبب>"` [م]؛ يتطلّب جعل حقول التذييل في `TimeSeriesFooterDto` قابلة للغياب (اليوم `Float` غير nullable) |
| 12 | `BehaviorFingerprintBuilder.kt:312` | `aiDelegateInitError ?: ""` | نعم (`""` = لا خطأ) | يبقى `null` (يُحذف مفتاحه) ويُقرأ «لا خطأ» من `aiActiveDelegate` لا من سلسلة فارغة [م] |
| 13 | `TimeSeriesFileWriter.kt:559,565,570,572` | ثوابت مُعلَنة بـ`unmeasuredDefaults` (`intensityFactor` · `deviceTierMeasured` · `batteryCurrentScalingApplied="unresolved"` · `tempRiseRateInProbe`) | لا (مُعلَنة) | استثناء سليم يبقى؛ **تُوسَّع القائمة** (`:1678-1682`) لتعدّ كل ثابت بالتصميم يبقى في fv68، ولا تعدّ ما صار حالةً صريحة |

**الصفّان 6 و7 كانا خارج الجدول الأصلي** (§١.١١ وسمهما «خارج الجدول»): دخلا بحسم س-23 لا بنصّه الحرفيّ [م — ينتظر تأكيداً]. وما بقي بلا قيمة جديدة صريحة هنا **لا يُنفَّذ** (القاعدة 13).

#### ت-19 — الختم والوحدات (س-19)

«س-19: تغييرات ما بعد الختم 67 تدخل fv68 رسمياً، والخادم يقرأ بيانات 67 بوحدة MiB/s. معتمد.»

| البند | القيمة |
|---|---|
| الختم | `val fieldsVersion = 68` في `app/build.gradle.kts:351` + سطر في كتلة التاريخ (`:128-350`)؛ يتدفّق إلى النكهتين (`:381` · `:403`). **متى [قاعدة الختم، المنسّق]: في أوّل التزام يغيّر شكل الحمولة أو الملف، لا في آخر الدفعة** (حادثة A70: تغيير شكل بختم قديم). **جملة العقد:** «fv68 مكتمل عند الالتزام الأخير من الدفعة ٣؛ ولا يُبنى APK للإصدار قبله». فالالتزامات الوسيطة تحمل الختم 68 على شكل ناقص بالتصميم |
| ما يضمّه الرسميّ [ح] | **كل** تغييرات ما بعد ختم 67: عدّها 80 التزاماً على `app/src/main` [أ-١٠]، والمعدود منها على العقد في §١.٢ — A70 (`91d5394`، `v[8]` والمقاطع ÷2²⁰) · G2/P2.3 (`7a963dd`: `footer.phaseBoundaries` وإزالة G-03/G-04/G-05 من الجهاز) · A46 (`0675291`: وسم المرحلة الأولى) · `3ab4efb` (طبقتا `deviceProfileKey` SHA-256) · `d5f2ca2` · `eee78ed` · `1524704` (L-02 `not_tested`/`N/A` · SEC-08 `unavailable` · M-06 `not_tested`). فلا يبقى ختم 67 يغطّي سلوكين |
| كل وحدة معلنة صراحة [ح] | `variables[8].unit` و`variables[9].unit` اليوم `"MB/s"` والقيمة ثنائية ÷2²⁰ (`V2Models.kt:656-668`) ← **`"MiB/s"`** [م N-21]. `verificationResults.storageThroughputSegments[].mbps` لا وحدة لها في الحقل ← مفتاح مرافق `storageThroughputSegmentsUnit = "MiB/s"` [م N-21]. وحدات القنوات الجديدة: kHz (`cpu_freq_policy{N}_khz`) و«كما أعلنها الجهاز» (`charge_counter_raw`) |
| قراءة بيانات 67 — **مُلغاة بالتصحيح أدناه** | ~~الخادم يقرأ بيانات 67 بوحدة MiB/s لـ`v[8]` والمقاطع~~ (قرار 2026-10-08 الأوّل). **حلّ محلّها التصحيح التالي** |
| **قراءة الوحدة — قرار مصحَّح [ح، 2026-10-08 الجزء الأوّل]** | نصّ المستخدم: «س-19 المصحَّح: 1.0.9 و1.0.10 تُقرأ MiB/s، و1.0.8-v6beta تُعلن «الوحدة غير محدَّدة» إلا بمميّز في الحمولة. معتمد.» ← الخادم يقرأ **بحسب `header.appVersion`**: `1.0.9` و`1.0.10` ⇒ MiB/s (÷2²⁰)؛ `1.0.8-v6beta` ⇒ **تُعلن «الوحدة غير محدَّدة»** (لا ÷10⁶ ولا ÷2²⁰) **ما لم يكن في الحمولة مميّز** يحسمها. **ما لا يحسمه النصّ:** ما هو «المميّز في الحمولة» — الاختبار على الخادم لا يمكن أن يكون `fieldsVersion` (يحمل الختم 67 قبل A70 وبعده)؛ **س-36 [م]: وحدة `MiB/s` المعلنة صراحة في `variables[8].unit` (N-21) هي المميّز المقترح** — القارئ يرى `"MiB/s"` ⇒ ثنائي؛ `"MB/s"` أو لا حقل ⇒ غير محدَّدة. **أقترح ولا أحسم.** |
| **المقيس لدعم التصحيح** (من الأداة، 2026-10-08) | `git log -S'versionName = "1.0.9' -- app/build.gradle.kts` ⇒ `bd077ce` 2026-09-26 00:55:06 +03 («versionName 1.0.8-v6beta -> 1.0.9»)؛ `git log -S'versionName = "1.0.10'` ⇒ `62507f4` 2026-10-07 18:36:43 +03؛ A70 `91d5394` 2026-09-20 20:55:49 +03 وهو سلف `HEAD` (`git merge-base --is-ancestor`). **فكل بناء ≥ 1.0.9 يحمل A70 (ثنائي)؛ وبناء `1.0.8-v6beta` قد يكون قبل A70 (عشري) أو بعده (ثنائي) بالاسم نفسه** — وهذا أصل «الوحدة غير محدَّدة» [أ-١٧] |
| **المقيس عن هذا الحسم** [أ-١٥] | (١) التزام A70 `91d5394` في **2026-09-20 20:55:49 +03**؛ (٢) `versionName`/`versionCode` عند `379e8eb` (ختم 67) وعند `91d5394` **كلاهما** `1.0.8-v6beta` · 10 — فالترويسة لا تفرّق قبل A70 من بعده؛ (٣) **العيّنات الذهبية السبع المختومة 67 كلّها بدأت قبل ذلك الالتزام** (من 2026-09-19T22:02Z إلى 2026-09-20T06:11Z)، فلا يحمل أيٌّ منها A70، أي ÷10⁶ لا ÷2²⁰ [ي: استدلال من التواريخ، لم تُفحص قيم القطع]؛ (٤) العامل بين الوحدتين 1.048576 (`V2Models.kt:656-658`). **أي أن مجموعة «67» تحمل الوحدتين**؛ القراءة الموحّدة MiB/s كانت قرار المستخدم الأوّل وتُخطئ بذلك العامل على ما سبق A70 — **وقد صحّحها المستخدم 2026-10-08 (الجزء الأوّل)** إلى القراءة بحسب `appVersion` (الصفّ أعلاه). والمقيس هنا بقي صحيحاً وهو أساس التصحيح |
| `datacollect` [ح س-08] | **خارج التسليم، لا تتبنّى fv68 الآن.** ختم 68 يُحمل على 18 قناة V1 **بلا مفاتيح fv68 الجديدة** (القرّاء بالاسم يكفونها). فغياب مفتاح fv68 في عيّنة مختومة 68 **ممكن**؛ وعلى كل قارئ أن يُعلنه (القاعدة 25) لا أن يقرأه قياساً |
| القرّاء | `docs/MEASUREMENT_SPEC.md:791,797` يصفان `v[8]` عشرياً ويُصحَّحان؛ و`TimeSeriesFileWriter.kt:83` وصف |

#### ت-20 — حرف D للمعالج [قرار 2026-10-08، الجزء الأوّل]

| البند | القيمة |
|---|---|
| القرار (حرفياً) | «- تُحذف كلمة «انهيار»: الهاتف الذي ينطفئ أثناء الفحص لا يُصدر تقريراً أصلاً. - D للمعالج من خطأ في نتائج الحساب وحده (نتيجة معروفة مسبقاً لا تطابق)، بمنتِج حقيقي على المسار الفعلي. فإن كان بناؤه ≤ يوم واحد يدخل الدفعة ٣، وإلّا فالمعالج بثلاثة حروف A/B/C في V8. وتذكر لي الكلفة قبل أن تبنيه.» |
| الحالة | **مفتوح بشرط — لا كود.** المنتِج يدخل الدفعة ٣ إن كان بناؤه ≤ يوم واحد؛ وإلّا فالمعالج بثلاثة حروف A/B/C في V8. **المستخدم يقرّر بعد رؤية الكلفة**؛ وتقدير الوكيل (الخطوة ٣-ب: ما أصغر منتِج حقيقي وأين يجري وكلفته بالساعات) في تقرير الجزء الأوّل لا في هذه البطاقة |
| ما يعنيه «من خطأ في نتائج الحساب وحده» | الشرط الوحيد لـD هو **عدم تطابق نتيجة حسابية مع نتيجة معروفة مسبقاً** بمنتِج على **المسار الفعلي** (مسار الذكاء `USE_AI_WORKLOAD_CPU`، ت-13) لا على نوى احتياطية لا تعمل في الإنتاج. لا يدخل الحرف انطفاء الجهاز ولا انهيار العملية: فهما لا يُصدران تقريراً أصلاً |
| كلمة «انهيار» في التطبيق | `git grep -n 'انهيار' -- app/src` (2026-10-08) ⇒ **سطر واحد**: `DestructiveMigrationTracker.kt:10` — تعليق عن ترحيل قاعدة البيانات لا صلة له بحرف المعالج [أ-١٨]. **فلا نصّ في هذا المستودع يُحذف بسبب هذا البند**؛ وإن ورد في وصف حرف D فعند الخادم أو الموقع (خارج حدود هذا المستودع) — يُنقل إلى وكيلَي المستودعين |
| الأثر على العقد | إن اعتُمد المنتِج: حقول `verificationResults` الحسابية الموجودة اسماً عند الخادم (`cpuFmaErrors` · `cpuPrimeSieveCorrect` [ن، §٤.٢ — `computeScanSignature.js:45-53`]) تصل بحالة صريحة لا افتراضاً (القاعدة 22) |
| لا يُنفَّذ | قبل قرار المستخدم بعد الكلفة (القاعدة 13) |

### ٢.٣ — خارج fv68: M5 والفهرس 17

M5 (زمن إطار الرسوميات بسياج) **ليس في fv68 هذه ولا قناته** (بنصّ الأمر). **ولا يُحجز الفهرس 17** [ح س-07]: لم يعد للفهرس بعد 10 معنى ثابت أصلاً (٢.١)، فتُلحَق M5 لاحقاً **بالاسم** في آخر التخطيط عند `fieldsVersion` لاحق. وأثره على `runConsistencyTest.js` (`v.length < 18`) لا يرتبط بـM5 بعد الآن بل بعدد السياسات (٢.١، آخر النقاط).

---

## ٣ — شروط قبول fv68 (مراجعة التنفيذ لاحقاً، لا الآن)

لا تُنفَّذ هذه الشروط في هذه الخطوة (وثيقة فقط). وهي القائمة التي يُقاس عليها كل التزام لاحق. تقدير الدفعة ٣ بنصّ المستخدم بعد اعتماد منتِجَي التخزين والذاكرة: «7–11 يوماً» [ن].

1. **حسم §٥ كلّه أو ما يخصّ الحقل المنفَّذ.** ما لا يُحسم لا يُنفَّذ (القاعدة 13). الحسم 2026-10-08 غطّى س-01 إلى س-26 عدا **س-22**، وقرار الجزء الأوّل حسم **س-27 إلى س-35** (وصحّح س-19 وس-28)؛ فكانت هذه الأسئلة تحجب بنودها: س-27 (كتابة `v[]` وكل قناة جديدة) · س-28 (ت-06) · س-29 (ت-11 وت-15) · س-30 (ت-16 وت-17) · س-31 (`criticalDefectDetected` في ت-18) · س-32 (SN-04/08/09) · س-33 (ت-10) · س-34 (ت-11) · س-35 (كل حقل سببٍ نصّي).
2. **قرّاء §٤ تُحدَّث قبل قبول الختم** (القاعدة 25) **والفخّ الذهبي في المستودعات الثلاثة يُعاد توليده في التزام التغيير نفسه** (القاعدة 39) — جزء من الالتزام لا بند بعده. والقائمة الكاملة بقرّاء المستودعات الثلاثة في §٤.
3. **الختم:** `fieldsVersion = 68` + سطر في كتلة التاريخ (`build.gradle.kts:128-350`) + بطاقة ت-19 (ما يُضمَّن والوحدات).
4. **التعريفات (القاعدة 24):** مصدر واحد مُصدَّر لـ`variables[]` لكل فحص: تعريفات `v[0..10]` الثابتة + تعريفات القنوات المشروطة (`charge_counter_raw` و`cpu_freq_policy{N}_khz`) تُبنى من نتيجة الاكتشاف؛ ويستورد `ChannelDiscoveryProbe` الأسماء منه بدل نسخته الثانية. **`V2_SAMPLE_VAR_COUNT` لا يبقى ثابتاً للتخطيط الكامل** (٢.١): يبقى للعدد الأساسي (11) فقط، ويُحسب الطول لكل فحص [م].
5. **الكتابة:** لا `NaN` ولا `null` في `v[]` أبداً (§١.٣)؛ وكل غياب في التذييل **بمفتاح حالة حاضر** لأن الحمولة تُسقط المفتاح ذا القيمة `null` (٢.٠). و**س-27 قبل أيّ سطر يكتب قناةً جديدة في `v[]`** — **محسوم 2026-10-08 (الجزء الأوّل):** آخر قيمة صالحة + `channelReadFailures`.
6. **الاختبارات تُستبدَل لا تُضاف** (القاعدة 23): ما يثبّت 11 قناة — `FieldsVersion66FieldsTest.kt:53` (`assertEquals(11, V2_VARIABLE_DEFINITIONS.size)`) · `TimeSeriesCooldownTest.kt:204-206` (يكشف V5 بـ`variables.size == 11`) · وثوابت `FloatArray(11)` في `TimeSeriesCooldownTest.kt:182` · `TimeSeriesFileWriterIntegrityTest.kt:71` · `BatteryIndicatorsTest.kt:35` · `HeatRiseRateTest.kt:25` · `StressTestServiceTest.kt:417` [أ]. **وما يثبّت السلوك الذي يُلغيه الحسم:** `StressTestServiceTest.kt:32-139` و`:175` (يثبّتان أن `selectFooterMaxTemp` يُرجع `-1f` عند الغياب — ت-10)؛ `ScanRuntimePermissionTest.kt` (قائمة `ALL` و`autoNotTestedIds` — ت-14)؛ `BehaviorFingerprintBuilderManualCategoryTest.kt` (شكل `manualResults`) [أ-١٦]. كل واحد منها يُستبدَل في التزام الإصلاح لا يبقى بجانبه اختبار جديد.
7. **القارئ يُختبر بالشكل الذي يستقبله فعلاً** (القاعدتان 37 و41) — **عيّنة ذهبية لحمولة التطبيق، معتمدة [ح س-25]** («نعم (القاعدتان 37 و41)، في التزام تغيير العقد (القاعدة 39)»):
   * **لا توجد اليوم** عيّنة للحمولة التي ينتجها التطبيق (`BehaviorFingerprintBuilder` يُختبر بعيّنات اصطناعية: `BehaviorFingerprintBuilderManualCategoryTest.kt`) [أ]؛ والعيّنات الموجودة في `app/src/test/resources/contract/` ردود/مستندات الخادم (`v7-client-payload-sample*.json` اسمها «client payload» وهي ما يُرسله الخادم **إلى** التطبيق لا العكس) [أ-١٣].
   * **اسمها [م]:** `app/src/test/resources/derived/fv68-app-request-payload-derived-from-<مصدر>.json` — في المجلّد المشتقّ القائم (`app/src/test/resources/derived/`) وباسم يقول إنها مشتقّة، فلا تختلط بنسخ الخادم ولا تُعدَّل بيد (قيد المستخدم 2026-09-30 [ن]، بنصّه: «ما يُشتقّ منها يُكتب في ملفّ جديد، باسم يقول إنه مشتقّ، مع قائمة تعديلاته»).
   * **كيف تُولَّد من المنتِج الحقيقي:** **(١) مرحلة JVM داخل التزام تغيير العقد** — اختبار يمرّ بالسلسلة الحقيقية دون استبدال أيّ طبقة (القاعدة 37): `TimeSeriesFileWriter` الحقيقي (يعمل في JVM بـ`Context` مُحاكى لـ`filesDir` فقط، كما في `TimeSeriesFileWriterIntegrityTest.kt:50-52` [ك]) يكتب `.ts.json.gz` من مصفوفات `TimeSeriesSample` بأطوال أجهزة مختلفة (مثلاً 11 و13 و16 قناة) وتذييل بكل مفاتيح fv68 ← `TimeSeriesFileReader` الحقيقي ← `BehaviorFingerprintBuilder.build` الحقيقي ← **التسلسل نفسه الذي تنفّذه `FirebaseAnalysisService`** (`gson.toJson(fingerprint)` ثم `Map<String, Any>`، `:82` و`:122-131`) — وهذا الجزء الأخير **`private val gson` داخل الخدمة** فيلزم استخراجه دالّةً يستدعيها الاختبار والخدمة معاً [م] حتى لا يُعاد تنفيذه في الاختبار (القاعدة 37). **[نُفِّذ 2026-10-08، `fb82136`: `AnalysisRequestSerializer.kt` — `gson` و`fingerprintToMap`؛ تستدعيها `FirebaseAnalysisService.kt:121` والمولِّد (§٧-و)، ويفشل اختبار إن عادت الخدمة إلى نسخة خاصة بها.]** **(٢) مرحلة الجهاز ضمن جولة الفحص الميداني الواحدة (القاعدة 19):** ملف `.ts.json.gz` حقيقي من فحص على بناء fv68 يُنسخ **دون تعديل** إلى `contract/` (القيد نفسه: «الردود والفحوص الحقيقية المسجَّلة لا تُعدَّل») ويمرّ بالسلسلة نفسها فينتج عيّنة مشتقّة ثانية؛ **ويقارن اختبارٌ مجموعَ مفاتيح العيّنتين** — الاصطناعية يجب أن تطابق ما يُنتجه المنتِج الحقيقي (القاعدة 41)، وأيّ مفتاح في إحداهما دون الأخرى يُخفق الاختبار.
   * **حدّ المرحلة (١):** ما لا يعمل في JVM — `DisciplinedSampler` و`StressTestService.completeScan` (يحتاجان `Context` و`BatteryManager` وsysfs) — لا تُنتجه المرحلة (١)؛ فمفاتيح التذييل التي يبنيها `completeScan` تأتي في المرحلة (١) من بناء يدوي لـ`TimeSeriesFooter` قد ينجرف عنه. **هذا الانجراف بعينه ما تكشفه المرحلة (٢)**، فتبقى القاعدة 41 غير مستوفاة حتى تُجرى.
   * **تُنسخ العيّنة إلى `neuralcheck-backend` وحده** (نسخة حرفية بـsha256، `contract/README.md`)، ويحمل اختبار قارئه عليها. **[معدَّل 2026-10-08 (الجزء ٣) بقرار المستخدم. النصّ الذي كان: «تُنسخ العيّنة إلى `neuralcheck-backend` و`wepsite` كما تُنسخ عيّنات المستندات اليوم (نسخة حرفية بـsha256، `contract/README.md`)، ويحمل كلٌّ منهما اختبار قارئه عليها.»]** قرار المستخدم بنصّه (من `PLAN-01-ROADMAP.md` في مساحة العمل، منقول في أمر الجزء ٣ — غير متحقَّق من هذا المستودع): «الجزء ٣: بما أن الموقع لا يقرأ أيّ حقل من حمولة التطبيق (عيّناته تقارير خادم، وfunctionalResults في app/verify/page.js تعليق لا قراءة)، فالعيّنة الذهبية لحمولة التطبيق تُنسخ إلى الخادم وحده. ويُثبت ذلك بالأداة في تقرير الجزء ٣. وعيّنات الموقع تتحدّث حين يتغيّر شكل تقرير الخادم في الدفعة ٧.» **فلا يُنسخ شيء إلى `wepsite` من هذا المستودع**، والإثبات بالأداة على الموقع يكتبه المنسّق (التطبيق لا يقرأ `wepsite`).
   * **نتائج الاختبارات:** `functionalResults` و`manualResults` تدخل العيّنة نفسها (هي جزء من `BehaviorFingerprint`)؛ فلا عيّنة منفصلة لها.
8. **`TimeSeriesFileReader.calculateSummary` يُغيَّر ليقرأ بالاسم** لا بـ`variables.size == 11` (`TimeSeriesFileReader.kt:159`)؛ وإلا عند أيّ طول غير 11 يُعامَل الملف تخطيطاً قديماً (V1 بـ18 قناة) فيقرأ `v[5]` (= `cpu_freq_max_khz`) **درجة حرارة** و`v[9]` (= `memory_bandwidth_mbps`) استخدام معالج (`:162-217`). يستدعيه `GenerateReportUseCase.kt:56-90` ويخزّن ناتجه في `ScanReport.timeSeriesSummary`؛ وبالبحث (`git grep` عن `timeSeriesSummary` و`maxTempReached` في `app/src/main`) لا قارئ لحقوله خارج بنائه [أ] — فالأثر الظاهر للمستخدم اليوم محدود، لكن القارئ يثبّته `TimeSeriesCooldownTest` ويجب ألّا يقول كذباً. **وبعد الحسم يكبر السبب:** الطول لم يعد 11 أو 18 بل 11 + ما اجتاز الاكتشاف.
9. **القياس قبل/بعد** (القاعدة 18) — **كلّه غير مقيس الآن** ويُجمَّع في **جولة فحص ميداني واحدة** (القاعدة 19: «يُجمَّع ولا يُجزَّأ»، يُتحقَّق فيها من عدّة بنود دفعةً واحدة): (أ) حجم الحمولة (`~270 KB` بنصّ التعليق `AiAnalysisRequest.kt:18`، غير مقيس هنا) بعد القنوات الجديدة والقوائم؛ (ب) زمن العيّنة بعد قراءات sysfs الإضافية بعدد السياسات و`getLongProperty`؛ (ج) **تعارض M4 مع تعليق الكود** في `getThermalHeadroom` (س-03)؛ (د) **عدد بثّات البطارية في 480 ث** وهل يبلغ سقف الـ200؛ (هـ) زمن تحقّق التخزين («+2 ث» منقول) وزمن نواة الذاكرة داخل الستّين ثانية؛ (و) L-02 بـ`requestLocationUpdates` (عدد الاستدعاءات وزمن أول fix)؛ (ز) شبكة اللمس والإضاءة (الخام الجديد)؛ (ح) هل يسمح SELinux بتعداد `cpufreq/policy*` على كل طراز؛ (ط) تفريغ نافذة `gpu_fps` عند نهاية المرحلة 5 (هل اختفى الذيل)؛ (ي) **التحديث فوق 1.0.10 على جهاز حقيقي** — ترحيل Room 7 ⇒ 8 على بيانات حقيقية [ح س-29].
10. **الوثائق:** `docs/MEASUREMENT_SPEC.md` (§٣.١ وما بعده؛ ويصحَّح فيه `v[8]` العشري، ويصف القنوات الجديدة والقوائم) و`docs/architecture/BATTERY_INDICATORS.md` (مفردات `cycleCountSource`)؛ و`docs/FV68_CONTRACT.md` نفسها تُحدَّث بنتائج الجولة الميدانية.

---

## ٤ — القرّاء (القاعدة 25)

> القاعدة 25 (منقولة): «كل طبقة تقرأ شكلاً خارجياً بأسماء ثابتة — قائمة بيضاء، تفكيك، مخطَّط — تُعلن ما طلبته ولم تجده. ولا يُقبل شكل جديد إلا بتحديث كل قارئ له، مُعدَّداً في العقد».
>
> **كل قارئ فيما يلي يُحدَّث قبل قبول fv68، والفخّ الذهبي في المستودعات الثلاثة يُعاد توليده في التزام التغيير نفسه (القاعدة 39).** وكل قارئ جديد أو معدَّل يُختبر بعيّنة ذهبية مولَّدة من السلسلة الحقيقية عند نقطة قراءته (القاعدتان 37 و41) — §٣ بند 7.

### ٤.١ — في هذا المستودع [ك][أ]

الأوامر ومخرجاتها في الملحق أ-٦. ملخّصها: **R1** (قراءة بالفهرس أو العدد) = 55 سطراً في 10 ملفات · **R2** (ملفات تسمّي أنواع العقد) = 20 ملفاً (14 بعد استبعاد أسطر التعليق) · **R3** (قراءة أعضاء التذييل/الترويسة) = 46 سطراً: 43 في `BehaviorFingerprintBuilder.kt` و3 في `TimeSeriesFileWriter.kt` — أي أن **القارئ الوحيد لأعضاء التذييل والترويسة في التطبيق هو `BehaviorFingerprintBuilder`**.

| ق | الموضع | ما يقرؤه وكيف | أثر fv68 بعد الحسم | يُحدَّث ← |
|---|---|---|---|---|
| ق-01 | `TimeSeriesFileReader.kt:159-217` (13 سطراً في R1) | `variables.size == 11` ثم `v[0]` `v[1]` `v[4]` `v[5]` `v[6]`/`v[9]` `v[10]` بـ`getOrNull(...)` فتُسقَط الفجوات **صامتةً** | **يُكسَر** عند أيّ طول غير 11: يُعامَل الملف تخطيطاً قديماً (§٣ بند 8). ولا يعلن ما لم يجده | يقرأ بالاسم ويُعلن الغائب |
| ق-02 | `BehaviorFingerprintBuilder.kt:103-345` | قائمة بيضاء: ترويسة (12) · `variables` (تنسخ `name, unit, source` فقط) · عيّنات · تذييل (31) · `verificationResults` (26 سطر `map[...]`) · `stressEffectivenessToMap` · `dataQualityToMap` | **المفتاح غير المُضاف يُسقَط صامتاً.** سطر لكل مفتاح جديد/محذوف: `thermalHeadroomReadings` · `thermalHeadroomState` · `thermalStatusEvents` · `thermalStatusState` · `cpuPolicies` · `batteryPairs` · `batteryPairsState` · `cpuTempFinalC`/`cpuTempFinalSource` (بدل `maxCpuTemp`) · مفاتيح التخزين والذاكرة (ت-16 وت-17) · حالات ت-18 · `channelDiscovery.reasons` في `mapOf` عند `:239-247` · `storageThroughputSegmentsUnit`؛ **وحذف** `storageSlcCacheSizeMb` (`:318`)؛ وفرع «لا ملف» `:105-142` | سطر لكل مفتاح (ت-02، 03، 04، 05، 06، 08، 10، 13، 16، 17، 18، 19) |
| ق-03 | `AiAnalysisRequest.kt:60-165` · `:187-195` · `:221-225` · `:27-34` | DTOs: `TimeSeriesHeaderDto` · `VariableDto` · `TimeSeriesSampleDto` · `TimeSeriesFooterDto` · `FunctionalResultDto` · `ManualResultDto` · `BehaviorFingerprint` | حقول التذييل الجديدة اختيارية؛ **حقول `Float` غير nullable تصير قابلة للغياب** (`maxCpuTemp` وفرع «لا ملف»)؛ `measurements` في `FunctionalResultDto` و`ManualResultDto` (N-16)؛ `manualResultsHeader` في `BehaviorFingerprint` (N-17). وتعليق «Both paths carry identical content» (`:23-25`) غير صحيح ويُصحَّح | حقول/أنواع |
| ق-04 | `FirebaseAnalysisService.kt:82`، `:122-131` | `gson.toJson(fingerprint)` ثم `Map<String, Any>` ثم callable | **الغياب في الحمولة = غياب مفتاح** (Gson يتخطّى `null`، أ-١٤) فلا يُعلَن إلا بمفتاح حالة. **ويحوّل Gson كل عدد في `Map<String,Any>` إلى `Double`** (سلوك Gson المعتاد [ن]، غير مقيس هنا). وخطوة التسلسل تُستخرج دالّةً مشتركة لاختبار العيّنة الذهبية (§٣ بند 7) | الدالّة المستخرجة |
| ق-05 | `TimeSeriesFileWriter.kt:47`، `:373-374`، `:761-782` | تسلسل الملف؛ اختيار `variables`؛ V1 | `variables` لكل فحص (٢.١). `samplesSha256` يُحسب على نصّ العيّنة المسلسَل (`:354-361`) ولا يتأثّر بتمثيل الغياب لأن `v[]` بلا غياب [ح س-01] | القائمة لكل فحص |
| ق-06 | `SamplerStats.kt:267-312` (5 أسطر) | `v[0]` `v[1]` `v[4]` `v[5]` `v[10]` بالفهرس | الفهارس ≤ 10 لا تتحرّك. ويتأثّر `v[10]` بإفراغ النافذة (ت-12) | — / ت-12 |
| ق-07 | `StressEffectivenessCalculator.kt:121-126` وما بعده (19 سطراً) | `IDX 0, 5, 6, 7`؛ و`IDX_CPU_FREQ_LITTLE/MID = -1` («not in v[]») | لا كسر؛ تعليقات الثوابت قديمة بعد القنوات `cpu_freq_policy{N}_khz` | تعليقات/ثوابت |
| ق-08 | `BatteryIndicators.kt:102-139`، `:181` (8 أسطر) · `StressTestService.kt:280` | `v[1]` `v[2]` `v[3]` ؛ `batteryTempIndex()` | لا تأثير في القراءة؛ والقيم `0.0` عند غياب المُدخل تصير غياباً صريحاً (ت-18 صفّ 9) | صفّ 9 |
| ق-09 | `StressTestService.kt:1854` · `:1599-1606` · `:1668` · `:1851-1862` | `v[5]` لتغطية التردّد؛ `isStill` · `preScanWarnings` · ثوابت `dataQuality` | ت-18 صفوف 6 و7 و8 | ت-18 |
| ق-10 | `DisciplinedSampler.kt:317-337` | المنتِج + `arraySize = V2_SAMPLE_VAR_COUNT` | +قنوات مشروطة بالاكتشاف؛ **الحجم لكل فحص** | ت-01، 05 + §٣ بند 4 |
| ق-11 | `V2Models.kt:647-676` · `:578-584` · `:341-475` | التعريفات والعدد · `ChannelDiscoveryReport` · `VerificationSummary` | تصحيح `v[0]` و`v[10]` وحدتي `v[8]` و`v[9]`؛ `reasons` في `ChannelDiscoveryReport`؛ حقول `VerificationSummary` الجديدة وحذف `storageSlcCacheSizeMb` | ت-12، 16، 17، 18، 19 |
| ق-12 | `ChannelDiscoveryProbe.kt:34-199` | قائمة أسماء (نسخة ثانية) + تصنيف `available/blocked/missing` | القناة `charge_counter` تصير `charge_counter_raw` وتُضاف قنوات السياسات؛ **التمييز بنصّ الخطأ** (`catch` عند `:188` و`:195`)؛ و`reasons` | القاعدتان 21 و24 |
| ق-13 | `StressTestService.kt:1619-1700` · `:1505-1534` · `:137-145` · `:1343` · `:785-798` | منتِج التذييل وbatteryHealth وmaxCpuTemp وتشغيل الاكتشاف | بناء القوائم الجديدة، وبناء `variables[]` بعد الاكتشاف (`:785-798` قبل `:887`) | ت-02، 03، 04، 05، 06، 10 |
| ق-14 | `StressTestServiceV2.kt:1063-1104` · `:425-595` · `:599-680` · `:1020` · `:307-309` | منتِج verification · طور التخزين · طور الذاكرة · نهاية المرحلة 5 | القراءة الراجعة وتحقّق الذاكرة وحالات `gpu`/`workersPin` وإفراغ `GpuFpsTracker` | ت-12، 13، 16، 17، 18 |
| ق-15 | `GenerateReportUseCase.kt:56-90` | يستدعي `calculateSummary` ويخزّن الناتج | يتأثّر بـق-01 | مع ق-01 |
| ق-16 | `FirebaseSyncService.kt:50-56` · `ExportDataUseCase.kt:71-75` · `AiScoringEngine.kt:79`، `:127-128` | يرفع/يصدّر/يقرأ الملف ويسجّل الأعداد | لا يقرأ المحتوى؛ يحمل fv68 إلى الخارج | — |
| ق-17 | `ScanPermissions.kt:32-90` · `PreScanScreen.kt:94`، `:440-561` · `ScanRuntimePermissionTest.kt` · `AndroidManifest.xml` | عقد «ما يصير `not_tested` عند الرفض» | مدخل خامس، مشروط بـAPI ≥ 29؛ و`uses-permission` جديد | ت-14 |
| ق-18 | مفردة الحالة: `ScanReport.kt:40-57` · `GenerateReportUseCase.kt:47` · `ViewModels.kt:938` · `CompareTwoScansUseCase.kt:50-51` · `ReportScreen.kt:1022-1025` · `NeuralCheckComponents.kt:343` · `FunctionalResultEntity.kt:24` (تعليق) · `ManualTestsViewModel.kt:105-165` | تفكيك نصّ الحالة (والمجهول ⇒ `ERROR`) | `unavailable` يصل من الإجابات اليدوية؛ و`not_measured` مفتاح جديد يسقط إلى `ERROR` ما لم يُضَف إلى `fromString` في الالتزام نفسه [ح س-15] | ت-15 |
| ق-19 | `scripts/read_footer.py` (**غير متتبَّع في git**: `git ls-files scripts` فارغ و`git status` يعرض `?? scripts/` [أ]) | قارئ بالأسماء (`fieldsVersion` · `totalSamples` · … ) ويعرض الغائب «— (absent)» | لا كسر بالإضافة؛ يستحقّ إضافة مفاتيح fv68 | اختياري |
| ق-20 | `BatteryReader.kt:109-112` · `:144` · `:211-266` · `:377-388` · `:428-433` · `:578-584` | المستقبِل وقراءة التيار والجهد والمستوى والعدّاد | قراءة الأزواج في `onReceive` بلا تغذية أدلّة المعامل؛ التيار الخام قبل `normaliseCurrent`؛ الصفر الحقيقي للعدّادين | ت-02، 06 |
| ق-21 | `SystemFileReader.kt:219-239` · `:280-318` · `:733-739` | قراءات التردّد والتحدّر الحراري و`readLongFromFile` | نصّ الخطأ يصل إلى المسبار؛ تعداد `cpufreq/policy*`؛ نصّ خطأ headroom بدل التثبيت الصامت | القاعدة 21؛ ت-03، 04، 05 |
| ق-22 | `GpsTests.kt:62-165` · `:217` | L-02 | `requestLocationUpdates` + الأرقام + `timeToFirstFixMs` | ت-11 |
| ق-23 | `SensorTests.kt:56-106` · `:39-40` | `testSensor` العامّ (SN-04 وSN-08 وSN-09 منه) | فحص إذن SN-08/09؛ وقوائم SN-04؛ و«غير مقيس» | ت-14، 15 |
| ق-24 | `ManualTestDialogs.kt:228-300` · `:796-797` · `:999-1000` · `:1108-1109` · `:1143-1144` · `:1292-1293` · `ManualTestsViewModel.kt:105-165` · `ManualTestsScreen.kt:144` | شبكة اللمس والأزرار الخمسة و`setResult` و`allIds` | `unavailable` بدل `not_tested`؛ خام الشبكة؛ استثناء المخفيّ من `allIds` | ت-15 |
| ق-25 | `ScanReport.kt:86-96` · `FunctionalResultEntity.kt` · `NeuralCheckDatabase.kt:31-32` · `BehaviorFingerprintBuilder.kt:56-91` · `:504-512` | حامل نتيجة الاختبار وتحويلها إلى DTO | **حامل الخام المركّب** — إن اختير عمود Room فترحيل 7 ← 8؛ وإلا JSON في `rawValue` | س-29 |
| ق-26 | `GpuFpsTracker.kt:103-125` · `StressTestServiceV2.kt:787` · `:893` · `:1020` | نافذة `gpu_fps` وإفراغها | استدعاء `reset()` بعد `gpuActive.stop()` عند نهاية المرحلة 5 | ت-12 |

**اختبارات** (11 ملف اختبار Kotlin تسمّي أنواع العقد [أ]، منها ما يثبّت 11/18 قناة — تُستبدَل لا تُضاف، القاعدة 23؛ وما يثبّت السلوك الذي يُلغيه الحسم في §٣ بند 6):

`BehaviorFingerprintBuilderManualCategoryTest` · `DeviceInfoStorageFieldsTest` · `FieldsVersion66FieldsTest` (**`:53`**) · `TimeSeriesCooldownTest` (**`:182` `:196` `:204-206` `:225`**) · `TimeSeriesFileWriterIntegrityTest` (**`:71`**) · `BatteryIndicatorsTest` (**`:35`**) · `HeatRiseRateTest` (**`:25`**) · `CpuUsageProvenanceTest` · `GpuFpsWindowTest` · `StressTestServiceTest` (**`:417`**، وما يثبّت `-1f` في `:32-139` · `:175`) · `SaturationReasonTextTest`.

**عيّنات ذهبية في هذا المستودع** (7 ملفات `*-reports-document-sample*.json` + 4 `v7-client-payload-sample*.json` في `app/src/test/resources/contract/` [أ]) — نسخ منقولة من الباك اند لا يُعدَّل منها شيء هنا (`README.md` في المجلّد نفسه). **لا يقرأ أيّ اختبار منها `rawFingerprint.timeSeries`** [أ] (القرّاء: ردود التحليل فقط)؛ فهي تتغيّر بإعادة التوليد هناك ثم النسخ. **وعيّنة حمولة التطبيق (§٣ بند 7) جديدة** وتُولَّد هنا.

**وثائق**: `docs/MEASUREMENT_SPEC.md` (يصف `fieldsVersion = 67`) · `docs/architecture/BATTERY_INDICATORS.md` (`cycleCountSource`). وثائق `docs/audits/*` تاريخية.

### ٤.٢ — منقول، غير متحقَّق من هذا المستودع: `neuralcheck-backend`

> **منقول، غير متحقَّق من هذا المستودع.** المصدر: (أ) تقرير كشّاف للقراءة 2026-10-08 (منقول في أمر الدفعة ٢)؛ (ب) **أجوبة المنسّق على فحوص §٤.٥، 2026-10-08، منقولة بمصادرها من `neuralcheck-backend@19fc7bc`**. **✓** = تحقّق المنسّق من السطر بنفسه. **أنا لم أقرأ أيّاً منها.**

**أجوبة فحوص §٤.٥ (منقولة)** — كل سطر موسوم بما يستلزمه:

| # | الجواب المنقول | الأثر على fv68 |
|---|---|---|
| 1 | لا قراءة لـ`.source` في `findings.js`؛ `resolveChannelNames` تقرأ `v.index` و`v.name` وتستكمل من `FALLBACK_CHANNEL_NAMES_BY_INDEX` (`findings.js:57-62`) | تغيير نصّ `source` لـ`v[0]` و`gpu_fps` لا يكسر قارئاً. **والاستكمال بالفهرس** لا مدخل له للفهارس ≥ 11 بأسماء متغيّرة — الاسم من `variables[].name` دائماً |
| 2 | `calibration`/`preScanWarnings`: **غير محسوم**؛ `rehydrateReportSamples.js:60-64` «نسخة Storage قد تحمل footer أغنى من نسخة المستند (calibration، earlyStopReason، preScanWarnings…)»، و`:130-135` يقرأ `timeSeriesStoragePath` | **س-22 يبقى مفتوحاً.** وتلميحه: مفاتيح الملف قد تبلغ المستند عبر نسخة Storage لا الحمولة وحدها |
| 3 | `findings.js:912-915` يقرأ `maxCpuTemp === -1` حالةَ `NO_CPU_TEMP_SENSOR`؛ `computeScanSignature.js:50-51` `storageWriteVerifyErrors: vr.storageWriteVerifyErrors \|\| 0` (يقرأ الغياب صفراً)؛ `reevaluationDecision.js:30-31`؛ وبوّابة `STORAGE_VERIFY_ERRORS` في `categoryStates.js:410` حالتها `NOT_WIRED` | يُحدَّث `:912-915` في التزام القرّاء [ح س-13]؛ و`\|\| 0` يخالف القاعدة 22 ويُصحَّح؛ والبوّابة تُوصَل بوصول منتِج التخزين (ت-16) |
| 4 | `cycleCountSource` لا يُقرأ؛ `cycleCount` الرقم: `computeScanSignature.js:93`، `reevaluationDecision.js:90-93` | تغيير مفردات `cycleCountSource` آمن للخادم (ت-02) |
| 5 | `thermalHeadroomPeak`/`thermalStatusMax`: `findings.js:789-790`؛ `buildV7Payload.js:387-388`؛ `runConsistencyTest.js:173-177` (Avg وPeak) | تبقى (للعرض الانتقالي) [ح س-20]؛ وتُحذف حين ينقل الخادم قرّاءه إلى الخام (قرار لاحق) |
| 6 | `findings.js:1046-1051` يقرأ القنوات الأساسية بفهرس ثابت `colByIndex(1..5)` و`(10)`، و`charge_counter_uah` بالاسم (`:1055-1060`). ⟵ لا يتحرّك فهرس من 1 إلى 10 في fv68، والإضافة إلحاق فقط | **الفهارس 1..10 آمنة.** والاسم `charge_counter_uah` **يتعارض** مع المحسوم `charge_counter_raw` ⇒ القارئ يُحدَّث (ت-01) |

**قرّاء الخادم من تقرير الكشّاف (منقول)** — تُحدَّث قبل قبول fv68:

* ✓ `functions/src/admin/runConsistencyTest.js:43-80` (دالّة منشورة): `applySampleNoise` «Apply per-sample noise to v[1..17]» بتخطيط قديم من 18 قناة (`v[5]` cpu_temp_c، `v[6,7,8]` cpu_freq_*، `v[9]` cpu_usage_pct، `v[10]` gpu_fps، `v[11]` gpu_temp_c، `v[12-14]` accel، `v[15-17]` gyro) و`if (!Array.isArray(v) || v.length < 18) continue;` — فعلى 11 قناة لا ضجيج إطلاقاً، وعلى 18 قناة ضجيج على قنوات خاطئة. وفيه أيضاً `:96` `s.v?.[5]`، `:119` `v[9]`، `:126` `v[10]`، `:135` `v[6]`، `:150` `v[4]`، `:160` `v[2]`، `:167` `v[3]`. **بعد الحسم الطول متغيّر (11 + ما اجتاز)؛ يبلغ 18 عند ست سياسات** (٢.١) ← يُحدَّث إلى القراءة بالاسم.
* ✓ `functions/src/analysis/findings.js`: فروع `fieldsVersion === null || fieldsVersion <= 67` عند `:554` و`:701` و`:895` — **يتأثّر بحسم س-19**: بيانات 67 تُقرأ MiB/s لـ`v[8]` والمقاطع [ح]، فتتغيّر دلالة هذه الفروع **[مُصحَّح 2026-10-08 الجزء الأوّل: القراءة بحسب `appVersion` — `1.0.9`/`1.0.10` ⇒ MiB/s؛ `1.0.8-v6beta` ⇒ «الوحدة غير محدَّدة» إلا بمميّز في الحمولة؛ انظر ت-19]**؛ وبحث `charge_counter_uah` بالاسم عند `:1055-1060`؛ وقراءات بالفهرس `colByIndex(1..5)` و`(10)` عند `:1046-1051`.
* `analysis/sampleInputsFromRaw.js:45-49` (`v[0..4]` بالفهرس) — آمن.
* `signatures/computeScanSignature.js:45-53` (`verificationResults`: `cpuFmaErrors`، `cpuPrimeSieveCorrect`، `memoryMarchPassed`، `memoryWalkingOnesPassed`، `memoryRandomVerifyErrors`، `storageWriteVerifyErrors`، `storageReadVerifyErrors`، `gpuComputeGemmCorrect`، `criticalDefectDetected`) و`:92-97` (`batteryHealth.cycleCount …`). **كلّها تُقرأ الآن بعد أن تصير قابلة للغياب/ذات حالة (ت-16 وت-17 وت-18)**.
* `analysis/footerIntegrity.js:83` (`fieldsVersion >= TRUE_MAX_BATTERY_TEMP_SINCE`).
* `analysis/analyzeDevice.js:641-643` (`footer.fieldsVersion`).
* `analysis/callAI.js:191-262`.
* `analysis/buildV7Payload.js:327` و`:447-450`.
* `evaluations/evaluationContract.js:126`.
* قرّاء تمرير/تخزين: `lightenFingerprintForStorage.js` · `rehydrateReportSamples.js` · `serverRawSamplesStorage.js` — **يلزم أن تمرّر المفاتيح الجديدة لا أن تُسقطها** (القاعدة 25).
* `analysis/precompute.js` و`src/lab` (غير موصولين، بخريطة قنوات قديمة من 22 قناة).

**ما يلزم الخادم أن يقرأه حديثاً** (من هذا العقد، لا من الكشّاف): `variables[]` بطول متغيّر بين الأجهزة · `footer.thermalHeadroomReadings[]` و`thermalStatusEvents[]` وحالتاهما · `footer.cpuPolicies[]` · `footer.batteryPairs[]` و`batteryPairsState` · `footer.cpuTempFinalC`/`cpuTempFinalSource` · `channelDiscovery.reasons` · حقول `verificationResults` للتخزين والذاكرة وحالاتها · حالات `gpuComputeGemmState`/`workersPinState`/`stillnessState`/`preScanWarningsState` · `measurements` على نتائج الاختبارات · `manualResultsHeader.hiddenByModel` · `status = unavailable` من الإجابات اليدوية و`not_measured` · وحدة `MiB/s` المعلنة.

### ٤.٣ — منقول، غير متحقَّق من هذا المستودع: `wepsite`

صفر قراءة كود لـ`samples` / `variables` / `footer` / `header` / `fieldsVersion` في `app` و`lib` و`components`؛ عيّناته الذهبية في `contract/*.json` [ن]. **فأثر fv68 على الموقع هو عيّناته الذهبية فقط:** تُعاد توليدها/نسخها في التزام تغيير العقد (القاعدة 39)، وعيّنة حمولة التطبيق الجديدة (§٣ بند 7) **لا تُنسخ إليه** [معدَّل 2026-10-08 بقرار المستخدم، انظر س-25 و§٣ بند 7: «تُنسخ إلى الخادم وحده»].

### ٤.٤ — ما يستنتجه التطبيق من المنقول (استنتاج، لا فحص)

| الاستنتاج | مبنيّ على | حالته |
|---|---|---|
| `findings.js` يبحث `charge_counter_uah` بالاسم ⇒ اسم `v[11]` جزء من العقد (القاعدة 24) | `:1055-1060` ✓ + ت-01 | **الاسم المحسوم `charge_counter_raw` يخالفه** ⇒ يُحدَّث القارئ لا الاسم (الاسم قرار المستخدم) |
| `fieldsVersion <= 67` في `findings.js` يقرأ كل ما ختمه 67 «قديماً» | `:554` `:701` `:895` + §١.٢ | A70 (ثنائي) مختوم 67 ⇒ يُقرأ عشرياً اليوم؛ **حسم س-19 الأوّل قلب ذلك**: 67 يُقرأ MiB/s، فيُخطئ ÷10⁶ لما قبل A70 (ت-19) — **ثم صحّحه المستخدم 2026-10-08**: القراءة بحسب `appVersion` (ت-19) |
| `v.length < 18` في `runConsistencyTest.js` | `:43-80` + عدد السياسات | الطول 11 + (1) + السياسات؛ 18 عند ست سياسات (٢.١) |
| `computeScanSignature` يراقب أسماء ذاكرة حُذفت من التطبيق في 1.7.B | `:45-53` + `V2Models.kt:358-365` | تعود بمنتِج حقيقي (ت-17) |
| `colByIndex(1..5)` و`(10)` | `:1046-1051` + هذا الملف | الفهارس 0-10 لا تتغيّر في fv68 ⇒ آمنة؛ ما يتغيّر `source` لـ`v[0]` و`v[10]` ووحدتا `v[8]`/`v[9]` — والخادم لا يقرأ `source` (جواب 1) |

### ٤.٥ — فحوص تلزم المنسّق (خارج حدودي)

صيغة الطلب المتّفق عليها: «أحتاج فحص عقد بيانات في `neuralcheck-backend`: <السؤال> — خارج حدودي».

**أُجيبت (2026-10-08، §٤.٢):** 1 (`.source`) · 3 (`maxCpuTemp` و`storage*VerifyErrors`) · 4 (`cycleCountSource`) · 5 (`thermalHeadroom*`/`thermalStatusMax`) · 6 (الفهرس مقابل الاسم).

**لم تُحسم:** 2 — «من أين يأتي `calibration` و`preScanWarnings` في `rawFingerprint.timeSeries.footer` لمستند `reports`؟» = **س-22**.

**فحوص جديدة ناشئة عن الحسم** (لم تُطلب من قبل):

7. هل يفترض أيّ قارئ في `neuralcheck-backend` — غير `findings.js:1046-1051` و`resolveChannelNames` و`runConsistencyTest.js` — أن `variables[]` بطول وترتيب واحدين بين الأجهزة؟ (ت-01، ت-05)
8. كيف يصنّف الخادم `status = "unavailable"` في `manualResults` (اليوم لا يصل `unavailable` عبر `manualResults` أصلاً لأن الزرّ يرسل `not_tested`؛ ويصل عبر `functionalResults` من الاختبارات الآلية)، وهل يعرف `not_measured` و`manualResultsHeader` و`measurements`؟ (ت-15)
9. هل تُسقط `lightenFingerprintForStorage.js` أو `rehydrateReportSamples.js` أو `ensureReportShape` المفاتيح/الحالات الجديدة قبل الحفظ أو الردّ؟ (القاعدتان 25 و37)

---

## ٥ — الأسئلة: ما حُسم في 2026-10-08 وما بقي مفتوحاً

**نصّ قرار المستخدم (حرفياً، 2026-10-08)**، منقول في أمر الدفعة ٣ الخطوة ٠:

> «- س-11 وس-12 ود-٠-هـ: منتِج حقيقي لتحقّق التخزين والذاكرة. معتمد، والدفعة ٣ تصير 7–11 يوماً.
> - س-19: تغييرات ما بعد الختم 67 تدخل fv68 رسمياً، والخادم يقرأ بيانات 67 بوحدة MiB/s. معتمد.
> - س-23: إلغاء كل قيمة افتراضية تقرأ الغياب نجاحاً. معتمد (القاعدة 22).
> - س-07: لا يُحجز الفهرس 17. معتمد.
> - س-01 إلى س-24 الباقية: اقتراحك معتمد. لا غياب داخل مصفوفة العيّنة، والقراءات البطيئة (headroom والحالة الحرارية) قوائم أحداث بطوابع في التذييل، والقنوات المحجوبة لا تُدرج وتُعلن حالتها في channelDiscovery.
> - س-26: «انتظر» كما في PLAN-05 §٧-ج.»

**نصّ قرار المستخدم الثاني (حرفياً، 2026-10-08، الدفعة ٣ الجزء الأوّل)**، منقول في أمر الدفعة ٣ الجزء الأوّل (**منقول، غير متحقَّق من ملفّ في هذا المستودع**):

> «## حرف D للمعالج
> - تُحذف كلمة «انهيار»: الهاتف الذي ينطفئ أثناء الفحص لا يُصدر تقريراً أصلاً.
> - D للمعالج من خطأ في نتائج الحساب وحده (نتيجة معروفة مسبقاً لا تطابق)، بمنتِج حقيقي على المسار الفعلي. فإن كان بناؤه ≤ يوم واحد يدخل الدفعة ٣، وإلّا فالمعالج بثلاثة حروف A/B/C في V8. وتذكر لي الكلفة قبل أن تبنيه.
> ## التصحيحان
> - س-19 المصحَّح: 1.0.9 و1.0.10 تُقرأ MiB/s، و1.0.8-v6beta تُعلن «الوحدة غير محدَّدة» إلا بمميّز في الحمولة. معتمد.
> - س-28 المصحَّح: voltageRaw، وlevelRaw مع levelScale، والخادم يستنتج الوحدة. معتمد.
> ## منتِج تحقّق الذاكرة
> - تحقّق عشوائي حقيقي في C++، وفشل الحجز حالة صريحة لا {0,0,0,0}. معتمد، داخل س-11.
> ## س-27 إلى س-35: اقتراحاتك معتمدة كلّها
> - س-27: آخر قيمة صالحة في المصفوفة + قائمة العيّنات الفاشلة لكل قناة في التذييل. بشرط: كل قارئ في الخادم يستثنيها، باختبار لكل قارئ (القاعدة 25).
> - س-29: عمود جديد في Room، وترحيل 7 ⇐ 8، باختبار ترحيل. ويُفحص التحديث فوق 1.0.10 على جهاز حقيقي في فحص الجهاز القادم.
> - س-30: الذاكرة اختبارها الأصلي أوّلاً بسقف زمني داخل الستّين ثانية، ثم الحمل للباقي. والتخزين قراءة راجعة لآخر 256 MiB. والعدد الخامّ يُشحن، والخادم يحكم.
> - س-31: criticalDefectDetected مجمَّع انتقالي معلَن، والحكم من الخامّ في الخادم.
> - س-32: التطبيق يرسل الخامّ، والخادم يصنّف «غير مقيس».
> - س-33: حقل واحد لحرارة المعالج، ومصدره معلن.
> - س-34: L-02: التطبيق يرسل الأرقام، والخادم يسمّي الحالة.
> - س-35: سقف 200 محرف لنصّ السبب، ولا تثبيت لـ«غير مدعوم»: كل قراءة بحالتها، والنداء كل 10 ثوانٍ.
> - والأسماء الثلاثة N-08 وN-16 وN-18 تُقرّ تبعاً لذلك.»

وكل «اقتراحك» لـس-27..س-35 في هذا القرار = اقتراح المنسّق، **منقول بنصّه في أمر الجزء الأوّل** (غير متحقَّق من ملفّ في هذا المستودع — القاعدة 29)، وينقل أدناه تحت كل سؤال بوسم «محسوم 2026-10-08 (الجزء الأوّل)».

و«اقتراحك» = اقتراح المنسّق لكل سؤال، **هذا نصّه حرفياً** (من `FV68-DECISIONS-2026-10-08.md` في مساحة العمل، **منقول في الأمر، لم أقرأ الملفّ** — القاعدة 27): يُنقل أدناه تحت كل سؤال بوسم «محسوم 2026-10-08» ونصّ الحسم بين «». ونصّ السؤال نفسه **كما كتبته المسوّدة دون تعديل**. **س-22 مفتوح.** وما نشأ عن الحسم يلحق بعد س-26 (س-27 إلى س-35) بوسم «ناشئ».

**س-01 — تمثيل الغياب داخل `v[]`.** `v` مصفوفة `float` أوّلية و`NaN` يُسقط الكتابة والإرسال (§١.٣)؛ نصّ الخطة «NaN = غير مدعوم» لا يُكتب. *لا أحسمه لأنه يغيّر نوعاً في الملف ويمسّ كل قارئ.* الخيارات: (أ) عنصر يقبل `null` (`Array<Float?>`/`List<Float?>`) فتُكتب `null` داخل المصفوفة — يغيّر `TimeSeriesSample.v` و`TimeSeriesSampleDto.v` وكل قارئ يفترض أرقاماً صرفة؛ (ب) قناة رقم + قناة حالة لكل قناة تحتاجها (تضاعف الأعمدة)؛ (ج) لا غياب داخل `v[]` أصلاً: القناة تحمل آخر قيمة معلومة، وقائمة قراءات/أحداث في التذييل تحمل الزمن والحالة (ترتبط بـس-04)؛ (د) تفعيل `serializeSpecialFloatingPointValues()` — ينتج `NaN` حرفياً وهو **ليس JSON صالحاً**؛ لم أتحقّق كيف يتعامل معه callable ولا `JSON.parse` في الخادم.

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «لا غياب داخل v[]: القناة البطيئة تخرج من v[] إلى قائمة قراءات بطابع في التذييل (headroom، الحالة الحرارية). والقناة المحجوبة لا تُدرج في variables، وحالتها في channelDiscovery. فلا NaN ولا null في المصفوفة»  
> الأثر: ٢.٠ و٢.١ وت-03 وت-04. المتبقّي: **س-27**.

**س-02 — شكل الحالة الصريحة وأسماؤها** (غير مدعوم / محجوب / لم يُقرأ / لم يُحاوَل / لم يُقَس). *لا أحسمه لأنه مفردة جديدة يقرؤها الخادم.* السابقة الموجودة: `channelDiscovery{attempted, available, blockedBySelinux, missing}` · `thermalSensorAvailability{…: "available"/"blocked_by_selinux"/"missing"}` · `unmeasuredDefaults[]` · `footerDiagnostics{…: "ok"/"fail:<صنف>"}`. الخيارات: توسيع `channelDiscovery` بقنوات fv68 (مع إصلاح تمييز blocked/missing بنصّ الخطأ — القاعدة 21)، أو خريطة جديدة `channelStates`، أو حقل `*State` مرافق لكل حقل. وهل تُستعمل أسماء الخطة `CAPABILITY_BLOCKED`/`NOT_MEASURED` على السلك أم يعلن التطبيق السبب ويُسمّي الخادم الحالة؟ (الاسمان **ليسا** في كود التطبيق [أ]؛ وقارئ `AbstentionReasonKind` في `classifyAbstentionReason` (`AiV7Presentation.kt:270-277`) يُسقط ما لا يعرفه إلى `UNKNOWN` — اتجاه الخادم→التطبيق، خارج عقد fv68 لكن القاعدة 25 تذكره.)

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «توسيع channelDiscovery بقنوات fv68، مع إصلاح تمييز blocked/missing بنصّ الخطأ (القاعدة 21). التطبيق يُعلن السبب، والخادم يسمّي الحالة (القاعدة 20)»  
> الأثر: ٢.٠ بند 2 و3 وN-07. أسماء الحالات الثلاثة مفاهيمية فلا تُكتب على السلك.

**س-03 — headroom كل 10 ث.** بين قراءتين: يُكرَّر آخر قيمة (يُخفي القِدَم) أم يُترك «لم يُقرأ»؟ وهل يبقى الاستدعاء كل ≥ 1.1 ث للمجمِّع والحارس والمسبار، أم تُجمَع الثلاثة على 10 ث (الحارس يقرأ كل ثانية، `StressTestServiceV2.kt:1471`)؟ *لا أحسمه: يمسّ حارس تقييد الحاكم الذي يقرّر قطع المراحل 4/5/6.*

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «تُسجَّل قراءة كل 10 ث في القائمة، وحارس الحاكم يبقى كما هو في fv68. والتعارض بين نصّ M4 (NaN) وتعليق الكود (استثناء) يُقاس على الجهاز في جولة الفحص»  
> الأثر: ت-03. المتبقّي: القياس الميداني (§٣ بند 9) — **لم يُقَس بعد**.

**س-04 — الحالة الحرارية: قناة أم أحداث.** الجدول يسمّيها قناة `v[13]` وغرضها «خطّ زمني». المستمع لا يستدعي إلا عند التغيّر؛ فقيمة القناة قبل أول حدث تحتاج تعريفاً (قراءة `getCurrentThermalStatus()` عند التسجيل؟ وهل تُحسب «مقروءة»؟). البديل: `thermalStatusEvents[] = {t, status}` في التذييل ولا قناة.

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «قائمة أحداث thermalStatusEvents[] {t, status}، أوّلها قراءة عند التسجيل»  
> الأثر: ت-04. المتبقّي: إطار `t` وحالة القائمة — N-03 وN-04.

**س-05 — ثلاث خانات `v[14..16]` مقابل عدد السياسات.** طراز بسياستين أو أربع: أين تقع؟ **أيّ سياسة هي «mid»؟** — قرار استنتاجي في التطبيق (القاعدة 20). الخيارات: خانات ثابتة بترتيب `cpuinfo_max_freq` تصاعدياً مع «غائب صريح» للناقص، أو قائمة بحجم متغيّر في التذييل بلا قنوات. (`SocNominalFrequencies.getPerClusterMaxKhz` يعيد `Triple` بثلاث خانات اليوم، `:140`.)

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «قناة لكل سياسة باسمها (cpu_freq_policy{N}_khz)، بعدد سياسات الجهاز. لا تسمية little/mid/big في التطبيق: التصنيف استنتاج على الخادم (القاعدة 20)»  
> الأثر: ت-05 و٢.١. المتبقّي: ترتيب الإلحاق N-22؛ وتعداد `policy*` على الجهاز.

**س-06 — اسم وعناصر `cpuinfo_max_freq` لكل سياسة.** غير مسمّى في الأمر. مقترح [م]: `footer.cpuPolicies[] = {policy, relatedCpus, cpuinfoMaxFreqKhz}`.

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «footer.cpuPolicies[] = {policy, relatedCpus, cpuinfoMaxFreqKhz} كما اقترح الوكيل»  
> الأثر: ت-05. المتبقّي: غياب `cpuinfoMaxFreqKhz` لسياسة بعينها — N-06.

**س-07 — الفهرس 17.** يبقى محجوزاً لـM5 (الطول 17 الآن، 18 لاحقاً) أم يُلحَق M5 في `fieldsVersion` لاحق بفهرس يُحدَّد حينها؟ الأثر على `runConsistencyTest.js` (`v.length < 18`) [ن] — §٢.٣.

> **محسوم 2026-10-08** — نصّ المستخدم: «لا يُحجز الفهرس 17. معتمد.»  
> الأثر: ٢.٣.

**س-08 — نكهة `datacollect`.** ختم واحد للنكهتين بتخطيطين. هل تتبنّى قنوات fv68؟ إن لا، فالمميِّز هو **اسم القناة** ويلزم أن يقرأ كل قارئ بالاسم؛ و`TimeSeriesFileReader.kt:159` يقرأ بالعدد، والخادم يقرأ بالفهرس في مواضع (`colByIndex` [ن]).

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «خارج التسليم، لا تتبنّى fv68 الآن. والقرّاء بالاسم يكفونها»  
> الأثر: ت-19 (ختم 68 مشترك، وغياب مفاتيح fv68 في عيّنة `datacollect` مختومة 68).

**س-09 — `v[11]`: الاسم والوحدة.** `charge_counter_uah` يطلبه الخادم بالاسم، لكن Honor يعطي قيمة بحجم mAh (ت-01). الخيارات: اسم يعلن الخام (`charge_counter_raw`) ووحدة «كما أعلنها الجهاز» ويستنتج الخادم الوحدة من السعة التصميمية والمستوى؛ أو إبقاء `_uah` (فتكذب الوحدة على Honor). وحدّ دقّة `Float` فوق 16 777 216.

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «charge_counter_raw بوحدة «كما أعلنها الجهاز»، والخادم يستنتج (Honor بحجم mAh)»  
> الأثر: ت-01. المتبقّي: قارئ الخادم `findings.js:1055-1060` يبحث الاسم القديم (§٤.٢)؛ و**س-28** لعناصر الأزواج.

**س-10 — `batteryPairs[]`.** (أ) إطار `t`: نسبي كـ`v[0]` أم `elapsedRealtime` مطلق (ويلزم أساس في الترويسة)؟ (ب) `current` خام أم مطبَّع؟ (ج) عناصر الزوج (`chargeCounter` و`level` و`temperature` من نصّ M3)؟ (د) سقف العدد؟ (هـ) هل يُكتب العدد والمدى أم يُشتقّان؟ (و) تمثيل «لم يصل بثّ» (`[]` وحده لا يكفي).

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «t نسبي كـv[0]؛ والتيّار خامّ غير مطبَّع؛ والعناصر {t, voltageMv, currentRaw, chargeCounterRaw, levelPct, tempRaw}؛ وسقف 200 عنصر؛ وreceiverRegistered + عدد البثّات حالةً صريحة للغياب»  
> الأثر: ت-06. المتبقّي: **س-28** (الوحدة مقابل الخام)، ومفاتيح حاوية الحالة — N-05.

**س-11 — الذاكرة.** (أ) هل يُكتب **منتِج** تحقّق (نمط كتابة/قراءة بمجموع اختباري) على مسار الذكاء ضمن fv68، أم تُعلَن «لم يُقَس» حتى يُكتب؟ (ب) `memBandwidthMBps (÷10⁶)` — حقل جديد أم وصف لـ`v[9]` (÷2²⁰)؟ (ج) رجوع المفاتيح الثلاثة إلى `verificationResults` وإضافتها إلى القائمة البيضاء.

> **محسوم 2026-10-08** — نصّ المستخدم: «س-11 وس-12 ود-٠-هـ: منتِج حقيقي لتحقّق التخزين والذاكرة. معتمد، والدفعة ٣ تصير 7–11 يوماً.»  
> الأثر: ت-17. المتبقّي: **س-30** (الجدولة و`random verify`)، **س-31**؛ و`memBandwidthMBps (÷10⁶)` لم يحسمه النصّ فلا يُضاف.

> **محسوم 2026-10-08 (الجزء الأوّل) — منتِج تحقّق الذاكرة** — نصّ المستخدم: «تحقّق عشوائي حقيقي في C++، وفشل الحجز حالة صريحة لا {0,0,0,0}. معتمد، داخل س-11.»
> الأثر: ت-17. **يحسم التعارض الأوّل في فهرس التعارضات:** النواة تُرجع اليوم `random_verify_errors = 0` ثابتاً (`production_stress.cpp:474`)، وصفراً شاملاً عند فشل `sysinfo`/التخصيص (`:426` · `:464`)؛ فيُكتب تحقّق عشوائي حقيقي مستقلّ عن الحجم في C++، وفشل الحجز يصير **حالة صريحة** تصل إلى `memoryVerifyState = "not_run:<سبب>"` لا أصفاراً. التنفيذ في الأجزاء التالية من الدفعة ٣ (ليس في هذا الجزء).

**س-12 — `storage*VerifyErrors`.** هل يُكتب منتِج (قراءة راجعة + مقارنة) ضمن fv68؟ وإلا فـ«لم يُقَس» دائماً، والخادم يقرّر شرط الحرف.

> **محسوم 2026-10-08** — نصّ المستخدم: «س-11 وس-12 ود-٠-هـ: منتِج حقيقي لتحقّق التخزين والذاكرة. معتمد، والدفعة ٣ تصير 7–11 يوماً.»  
> الأثر: ت-16. المتبقّي: **س-30** (القراءة الراجعة داخل المرحلة)، **س-31**.

**س-13 — `maxCpuTemp`.** (أ) كيف يُعلَن الغياب: `Float?` مع `null` + سبب، أم حذف المفتاح، أم حقل حالة؟ (يغيّر نوعاً في الملف والـDTO.) (ب) هل تُصحَّح دلالة «max» (القيمة اليوم قراءة حيّة عند الإنهاء إن وُجدت)؟

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «غياب صريح بسبب، والقيمة اليوم «آخر قراءة» لا «أقصى» فتُسمّى بما هي. ويُحدَّث findings.js:912-915 في التزام القرّاء»  
> الأثر: ت-10 وN-08. **تعارض مع الكود:** القيمة «آخر قراءة» لفرع واحد من ثلاثة (`StressTestService.kt:137-145`) — **س-33**.

**س-14 — شكل نتيجة L-02.** (أ) عددان رقميان (مرئية/مستعمَلة) أم نصّ؟ (ب) طلب الموقع: `requestLocationUpdates` على `GPS_PROVIDER` طوال العدّ أم حتى أول fix؟ وهل يُسجَّل زمن أول fix وعدد التحديثات؟ (ج) ما يُعدّ «المحرّك لم يعمل» مقابل «0 أقمار مقاسة»؟ (د) ترتيب L-02 قبل L-03 يبقى؟

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «رقمان: مرئية ومستعمَلة، وtimeToFirstFixMs أو غيابه. وطلب requestLocationUpdates على GPS طوال نافذة العدّ، والترتيب يبقى»  
> الأثر: ت-11. المتبقّي: **س-29** (الحامل)، **س-34** (البند «ج» من السؤال).

**س-15 — مفاتيح الحالات الأربع.** المخزَّن اليوم: `pass` · `fail` · `not_tested`. مقترح [م]: `pass` · `fail` · `not_tested` · `unavailable` (المفردة الموجودة في `TestStatus`). **مخاطر:** (أ) دلالة `unavailable` في الاختبارات الآلية «الحسّاس غير موجود» — لا أعرف كيف يعدّها الخادم؛ (ب) `TestStatus.fromString` يُسقط المجهول إلى `ERROR`؛ (ج) «إخفاء ما لا يملكه الطراز»: هل «مُخفى» حالة في العقد أم غياب سجلّ؟

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «pass / fail / not_tested / unavailable. وما لا يملكه الطراز لا سجلّ له، وقائمة hiddenByModel في رأس النتائج اليدوية. ويُحدَّث TestStatus.fromString في الالتزام نفسه»  
> الأثر: ت-15 (أ). ملاحظة من الكود: `fromString` يعرف `"unavailable"` أصلاً (`ScanReport.kt:53`)؛ فالتحديث للمفتاح الجديد `not_measured`. المتبقّي: **س-29**، N-17.

**س-16 — شبكة اللمس.** ما يُشحن من الخام (أبعاد الشبكة · الخلايا الملموسة/الكلّية · زمن الإكمال · سبب الإنهاء)؟ وكيف يُميَّز النجاح التلقائي من اليدوي؟ وعتبتا 95/80 قرار في التطبيق (القاعدة 20).

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «يُشحن الخامّ: الأبعاد، والملموس من الكلّي، والزمن، وسبب الإنهاء، وأقصى أصابع متزامنة. والعتبات على الخادم»  
> الأثر: ت-15 (ب). المتبقّي: **س-29**، وأسماء N-19.

**س-17 — تغطية الإضاءة.** كيف يعرف التطبيق أن التغطية حدثت (قراءتان: قبل/بعد؟)؟ ما يُشحن (قائمة القراءات لا المتوسّط)؟ ومفتاح «غير مقيس»: هل هو `not_tested` نفسه (الذي يعني اليوم «لم يُشغَّل»)؟

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «قائمة القراءات بطابع قبل التغطية وأثناءها. و«غير مقيس» حالة بسبب، لا not_tested»  
> الأثر: ت-15 (ج). المتبقّي: **س-32**، N-18 وN-20.

**س-18 — `ACTIVITY_RECOGNITION`.** (أ) إضافته إلى `ScanRuntimePermission` بـ`[SN-08, SN-09]`؛ (ب) الطلب مشروط بـ`SDK_INT >= 29`؛ (ج) حتى مع الإذن يعطي SN-08/09 `WARNING` «لا قراءات» بلا حركة (`SensorTests.kt:99-101`) — هل يصير «غير مقيس»؟

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «يُضاف، ومشروطاً بـAPI ≥ 29. و«لا قراءات بلا حركة» يصير «غير مقيس» لا WARNING (تصنيف الخادم)»  
> الأثر: ت-14. المتبقّي: **س-32** — عبارة «(تصنيف الخادم)» لا تحدّد من يعيّن الحالة.

**س-19 — الختم والتغييرات المعلّقة.** (أ) هل تُضمَّن A70 وG2/P2.3 وA46 وتجزئة الطبقتين وحالات L-02/SEC-08/M-06 في وصف fv68 رسمياً؟ (ب) APK يُبنى من `HEAD` (1.0.10 · versionCode 12 · `62507f4`) يحمل A70 بختم 67 — **قيد معروف غير مُصلَح**: الكود نفسه يقول إن الختم «a build.gradle.kts decision plus a backend/systemPrompt change» (`StressTestServiceV2.kt:524-527`)، فالقرار للمستخدم.

> **محسوم 2026-10-08** — نصّ المستخدم: «تغييرات ما بعد الختم 67 تدخل fv68 رسمياً، والخادم يقرأ بيانات 67 بوحدة MiB/s. معتمد.»  
> الأثر: ت-19. **وفيها المقيس عن العيّنات الذهبية (أ-١٥):** السبع المختومة 67 بدأت قبل التزام A70 فهي ÷10⁶.

> **مصحَّح 2026-10-08 (الجزء الأوّل)** — نصّ المستخدم: «س-19 المصحَّح: 1.0.9 و1.0.10 تُقرأ MiB/s، و1.0.8-v6beta تُعلن «الوحدة غير محدَّدة» إلا بمميّز في الحمولة. معتمد.»
> يحلّ محلّ بند «الخادم يقرأ بيانات 67 بوحدة MiB/s» وحده؛ وما سواه من س-19 (دخول تغييرات ما بعد الختم 67 في fv68 رسمياً) يبقى كما حُسم. الأثر: ت-19. **والمميّز في الحمولة لم يحدّده النصّ — س-36.**

**س-20 — مجمَّعات على الجهاز تبقى.** `thermalHeadroomAvg/Peak` · `thermalStatusMax` · `batteryHealth.{dischargeRateUnderLoadMa, voltageStabilityMv, batteryTempMaxC, …}` · `maxBatteryTemp` · `stressEffectiveness.*` · `dataQuality.*` — ما يمنعه نصّ القاعدة 20 (جمع/ذروة/تنعيم). الجدول لا يحذف شيئاً منها: تبقى مع الخام (تكرار) أم تُحذف عند وصوله؟

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «تبقى في fv68 مع الخام، ويُعلَن أنها للعرض الانتقالي. وتُحذف حين ينقل الخادم قرّاءه إلى الخام (قرار لاحق)»  
> الأثر: ت-03 وت-04. موضع الإعلان لم يحسمه النصّ — N-23. وقرّاؤها في الخادم: `findings.js:789-790` · `buildV7Payload.js:387-388` · `runConsistencyTest.js:173-177` (§٤.٢، جواب 5).

**س-21 — إعلانا `v[0]` و`gpu_fps`.** (أ) النصّ (مقترحان في ت-12)؛ (ب) هل يُصحَّح السلوك (نافذة زمنية؛ إفراغ عند نهاية المرحلة 5) أم الإعلان وحده؟ (ج) هل يوجّه الخادم بنصّ `source`؟

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «يُصحَّح الإعلان، وتُفرَّغ نافذة gpu_fps عند نهاية المرحلة 5 (تغيير صغير)»  
> الأثر: ت-12. والخادم لا يقرأ `.source` (§٤.٢، جواب 1).

**س-22 — مستند `reports`.** مصدر `calibration` و`preScanWarnings` فيه (§١.١٠) — فحص خارج حدودي.

> **مفتوح** — بنصّ المستخدم/المنسّق (2026-10-08): «يبقى مفتوحاً (فحص خادم)». جواب المنسّق المنقول (§٤.٢، جواب 2): `rehydrateReportSamples.js:60-64` «نسخة Storage قد تحمل footer أغنى من نسخة المستند (calibration، earlyStopReason، preScanWarnings…)»، و`:130-135` يقرأ `timeSeriesStoragePath` — **غير محسوم**.

**س-23 — افتراضيات تقرأ الغياب نجاحاً.** `gpuComputeGemmCorrect = true` · `criticalDefectDetected` · `isStill = true` · `preScanWarnings = []` عند فشل القراءة — خارج الجدول لكنها من نوع A76 نفسه (القاعدة 22، الملحق). هل تدخل fv68؟

> **محسوم 2026-10-08** — نصّ المستخدم: «إلغاء كل قيمة افتراضية تقرأ الغياب نجاحاً. معتمد (القاعدة 22).»  
> الأثر: ت-18 (13 بنداً، صفوف §١.١١). الصفّان 6 و7 خارج جدول PLAN-05 فينتظران تأكيداً. وما لم يُسمَّ في النصّ بقيمة جديدة صريحة لا يُنفَّذ.

**س-24 — تطبيع `v[3]`.** قلب الإشارة وضرب المعامل استنتاجان في التطبيق (القاعدة 20: «قلب إشارة مبنيّ على استنتاج — ممنوع»)؛ والجدول لا يطلب خام التيار. هل تُضاف قناة خام أم يُشحن الخام داخل `batteryPairs`؟

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «الخامّ في batteryPairs (س-10)، وv[3] يبقى كما هو مع batteryCurrentScalingApplied المعلَن. وقناة خامّ كاملة قرار لاحق»  
> الأثر: ت-06 (`currentRaw`). **ما يبقى في `v[3]`:** `batteryCurrentScalingApplied` يعلن المضاعف وقلب الإشارة (`x1_no_flip` · `x1000_no_flip` · `x1000_flip` · `x1_flip` · `default_<vendor>`، `BatteryReader.kt:328-334`) **كما استقرّ عند نهاية الفحص**؛ والعيّنات قبل لحظة التثبيت طُبِّعت بالاستدلال السابق وموضع التثبيت معلَن في `dataQuality.batteryScalingLockAtSample` (`StressTestService.kt:1888`، `BehaviorFingerprintBuilder.kt:422`) [ك]. فالوسم المعلَن لا يصف كل عيّنات `v[3]`، والخام في `batteryPairs` (حتى 200 عنصراً) يعوّض جزئياً.

**س-25 — عيّنة ذهبية لحمولة التطبيق.** غير موجودة اليوم (§٣ بند 7). هل تُضاف مع fv68 وتُنسخ إلى المستودعين (القاعدتان 37 و41)؟

> **محسوم 2026-10-08** — اقتراح المنسّق المعتمد بقول المستخدم «س-01 إلى س-24 الباقية: اقتراحك معتمد»: «نعم (القاعدتان 37 و41)، في التزام تغيير العقد (القاعدة 39)»  
> الأثر: §٣ بند 7 (التسمية والتوليد من المنتِج الحقيقي).  
> **تعديل 2026-10-08 (الجزء ٣) — «تُنسخ إلى المستودعين» صارت «إلى الخادم وحده».** قرار المستخدم بنصّه (من `PLAN-01-ROADMAP.md` في مساحة العمل، منقول في أمر الجزء ٣ — غير متحقَّق من هذا المستودع): «الجزء ٣: بما أن الموقع لا يقرأ أيّ حقل من حمولة التطبيق (عيّناته تقارير خادم، وfunctionalResults في app/verify/page.js تعليق لا قراءة)، فالعيّنة الذهبية لحمولة التطبيق تُنسخ إلى الخادم وحده. ويُثبت ذلك بالأداة في تقرير الجزء ٣. وعيّنات الموقع تتحدّث حين يتغيّر شكل تقرير الخادم في الدفعة ٧.»  
> الأثر: لا نسخ إلى `wepsite`؛ ونسخ الخادم يقوم به وكيله بعد التحقّق من sha256 (§٧-و). والإثبات بالأداة على الموقع يكتبه المنسّق لا هذا المستودع. وبقية س-25 كما هي: نعم، القاعدتان 37 و41. **أمّا «في التزام تغيير العقد (القاعدة 39)» فلم يتحقّق كما كُتب** — انظر §٧-و، «الفروق».

**س-26 — «انتظر».** وردت في نطاق التسليم «(شبكة اللمس، وتغطية الإضاءة، والحالات الأربع، و«انتظر»)» ولا تفصيل لها في الأمر ولا في الخطة المنقولة إليّ. ما هي، وهل تمسّ شكل النتائج؟

> **محسوم 2026-10-08** — نصّ المستخدم («انتظر» كما في PLAN-05 §٧-ج) وتفصيل المنسّق المنقول: ««انتظر» بدل الخطأ عند «قيد المعالجة» (409)، ومعه إبقاء sessionId عند إعادة المحاولة. لا يمسّ شكل النتائج.»  
> الأثر: ت-15 (د). التفصيل منقول من الخطة (PLAN-05 §٧-ج، «سقط من أمري السابق إليك، والنقص من المنسّق») — غير متحقَّق من هذا المستودع إلا أن `sessionId` مفتاح طلب قائم (`FirebaseAnalysisService.kt:140-141`).

**س-27 — ناشئ (عن س-01): فشل قراءة قناة مُدرجة داخل `v[]` أثناء الفحص.** النصّ المحسوم: «لا غياب داخل v[] … فلا NaN ولا null في المصفوفة». *لا أحسمه لأنه يغيّر ما يُكتب في كل عيّنة.* الإدراج يُقرَّر مرّة عند الاكتشاف؛ لكن قراءة عيّنة بعينها قد تفشل بعده (`Long.MIN_VALUE` من `getLongProperty`، استثناء sysfs) والمصفوفة `FloatArray` تُملأ أصفاراً افتراضياً (`DisciplinedSampler.kt:318`). **(أ)** تكرار آخر قيمة معلومة — يُخفي القِدَم ويقرأ الغياب قياساً (القاعدة 22). **(ب)** إسقاط العيّنة كلّها — يخرّب انتظام الكادنس وعدّ `expectedSamples`. **(ج)** إخراج القناة من `v[]` إلى قائمة أحداث (ما حُسم للقنوات البطيئة) — يُعيد ما حُسم أن يبقى قناةً. **(د)** *الصفر عند الاكتشاف:* يعدّ المسبار `0L` لـ`charge_counter` مفقوداً (`ChannelDiscoveryProbe.kt:173-177`) وهو في القناة قيمة؛ فهل يُدرَج على صفر؟ **(هـ)** *حراس `v[1..10]` الحالية:* `0` و`-1` في ١.٤ تبقى «كما هي» بنصّ الأمر، فتبقى تقرأ الغياب قياساً — **قيد معروف غير مُصلَح** بنصّ الحسم.

> **محسوم 2026-10-08 (الجزء الأوّل)** — نصّ المستخدم: «س-27: آخر قيمة صالحة في المصفوفة + قائمة العيّنات الفاشلة لكل قناة في التذييل. بشرط: كل قارئ في الخادم يستثنيها، باختبار لكل قارئ (القاعدة 25).»
> اقتراح المنسّق المعتمد: «القيمة في v[] آخر قيمة صالحة (تبقى المصفوفة رقمية)، ومعها في التذييل channelReadFailures{اسم القناة: [فهارس العيّنات]} بسقف وعدد ⟵ الغياب معلن في أعلى مستوى، وكل قارئ يستثني الفهارس (القاعدة 25). وcharge_counter بقيمة 0 عند الاكتشاف يُدرج (0 قيمة، والخادم يحكم). وحرّاس v[1..10] قيد معروف بنصّ الحسم».
> الأثر: ت-01، ت-05، §٣ بند 4-5، §٤. **(د)** `charge_counter` بصفر عند الاكتشاف **يُدرج** (المسبار اليوم يعدّه مفقوداً: `ChannelDiscoveryProbe.kt:173-177`). **(هـ)** حرّاس `v[1..10]` (`0` و`-1`) **قيد معروف غير مُصلَح بنصّ الحسم**. **شرط المستخدم على الخادم:** كل قارئ في الخادم يستثني الفهارس الفاشلة، باختبار لكل قارئ (القاعدة 25) — **خارج هذا المستودع**؛ يُنقل إلى وكيل الباك اند بعد أن يُعرَّف الشكل هنا. وقارئ التطبيق (`TimeSeriesFileReader.calculateSummary`) يستثنيها باختبار.
> **تفاصيل لم يحسمها النصّ [م]:** (١) شكل `channelReadFailures`: خريطة اسم القناة ← قائمة فهارس مرتّبة (فهرس العيّنة = موضعها 0-based في `samples[]`)، و**سقف** 200 فهرس للقناة الواحدة، و**عدد** إجمالي للفشل بما جاوز السقف في خريطة شقيقة `channelReadFailureCounts` (اسم القناة ← Int) — ليعرف القارئ أن القائمة مبتورة (`count > indices.size`)؛ (٢) قيمة العيّنات قبل أول قراءة صالحة = القيمة التي اجتازت بها القناة الاكتشاف؛ (٣) غياب `channelReadFailures` (`null`/مفتاح غائب) ≠ `{}`: الأوّل «لم يُحسب» والثاني «لا فشل» — ويرافقه حقل حالة حاضر.

**س-28 — ناشئ (عن س-10): الوحدة مقابل الخام في عناصر `batteryPairs`.** حُسم `voltageMv` و`levelPct` و`tempRaw` وحُسم التيار «خامّاً غير مطبَّع». **(أ)** الجهد: `EXTRA_VOLTAGE` على بعض الأجهزة µV (Oppo/Realme ColorOS)، فالمعقِّم يقسمه ÷1000 عند > 100 000 (`BatteryChannelSanitizer.kt:30` · `:54-56`)؛ والقسمة صحيحة تقطع ما دون mV وتُنفَّذ بعتبة استنتاجية. فـ`voltageMv` الخام قد يكذب بوحدته، والمعقَّم يخالف «الخام». **(ب)** المستوى: `(level * 100) / scale` قسمة صحيحة مقطوعة (`BatteryReader.kt:432`)؛ و`levelPct` الخام يتطلّب `EXTRA_LEVEL` و`EXTRA_SCALE` معاً. الخيارات: **(١)** الاسمان كما حُسما والقيمة بعد التعقيم + عدّاد `voltageUvConvertedCount` يُعلَن في `batteryPairsState`؛ **(٢)** يُسمّى الخام `voltageRaw` و`levelRaw` + `levelScale`؛ **(٣)** الخام تحت الاسم المحسوم مع وحدة معلنة لكل جهاز. *لا أحسمه: الاسم محسوم والوحدة تخالفه.*

> **محسوم 2026-10-08 (الجزء الأوّل) — مصحَّح** — نصّ المستخدم: «س-28 المصحَّح: voltageRaw، وlevelRaw مع levelScale، والخادم يستنتج الوحدة. معتمد.» (الخيار (٢) من السؤال)
> عناصر `batteryPairs[]` تصير `{t, voltageRaw, currentRaw, chargeCounterRaw, levelRaw, levelScale, tempRaw}` — **تُلغي** الاسمين `voltageMv` و`levelPct` المحسومين في س-10. الخام: `voltageRaw` = `EXTRA_VOLTAGE` كما وصل بلا قسمة ÷1000 ولا عتبة؛ `levelRaw` = `EXTRA_LEVEL` و`levelScale` = `EXTRA_SCALE` بلا القسمة الصحيحة المقطوعة؛ والخادم يستنتج الوحدة. الأثر: ت-06، ق-20. وأسماء الحقلين `voltageRaw` و`levelRaw` و`levelScale` محسومة بنصّ القرار.

**س-29 — ناشئ (عن س-14 وس-15 وس-16 وس-17): حامل الخام المركّب في نتائج الاختبارات.** ما يلزم شحنه (رقما L-02 · خام الشبكة · قوائم الإضاءة · `endReason`) مركّب. الحاملان اليوم: `TestResult` و`FunctionalResultEntity` فيهما `rawValue: String?` و`rawValueUnit` وحدهما (`ScanReport.kt:86-96`، `FunctionalResultEntity.kt`)؛ و`ManualResultDto{testId, status, categoryName}` بلا خام (`AiAnalysisRequest.kt:221-225`)؛ و`setResult` يكتب `rawValue = null` (`ManualTestsViewModel.kt:105-123`). **(أ)** عمود جديد في Room (الإصدار 7 ← 8، `exportSchema = true`: `NeuralCheckDatabase.kt:31-32`) + حقل في `TestResult` + `measurements` في الـDTO — الأنظف، ويكلّف ترحيل قاعدة بيانات. **(ب)** JSON داخل `rawValue` — بلا ترحيل، لكن `rawValue` نصّ يقرؤه غيره (`BehaviorFingerprintBuilder.kt:460-498` يقرأ `rawValue` لـS-02) ويخلط نصّاً للعرض بخام آلي. **(ج)** جدول/عمود منفصل للخام فقط. وهل يشمل الاختبارات الآلية (L-02 وSN-04) واليدوية (M-02) معاً؟

> **محسوم 2026-10-08 (الجزء الأوّل)** — نصّ المستخدم: «س-29: عمود جديد في Room، وترحيل 7 ⇐ 8، باختبار ترحيل. ويُفحص التحديث فوق 1.0.10 على جهاز حقيقي في فحص الجهاز القادم.»
> اقتراح المنسّق المعتمد: «(أ) عمود جديد في Room (7 ⟵ 8) + حقل في TestResult + measurements في الـDTO، للآلي واليدوي معاً».
> الأثر: ق-25، N-16، ت-11، ت-15. **بند جولة الفحص الميداني (§٣ بند 9 ي):** التحديث فوق 1.0.10 على جهاز حقيقي. الحامل وحده يُنفَّذ أوّلاً (الدفعة ٣ ج-1)؛ لا تغيير سلوك اختبار فيه.

**س-30 — ناشئ (عن س-11 وس-12): جدولة تحقّق الذاكرة والتخزين داخل المرحلتين الثابتتين، ومنتِج `random verify`.** *لا أحسمه لأن النصّ حسم «منتِج حقيقي داخل مرحلة الذاكرة الثابتة 60 ث» ولم يحسم كيف.* **(أ)** الذاكرة: النواة غير مقيّدة بزمن (مخزن بين 512 MiB و8 GiB، `production_stress.cpp:446-455`) وعمل الذكاء يملأ الستّين ثانية وينشر `v[9]` حيّاً: تتابع (يقتطع من الستّين ثانية وأيّهما أولاً؟) أم تزامن (يلوّث `v[9]` الحيّ)؟ **(ب)** `memoryRandomVerifyErrors`: النواة تُرجع `0` ثابتاً (`production_stress.cpp:474`) لأن مرجع SHA مرتبط بحجم 32 MiB — يُكتب تحقّق عشوائي مستقلّ عن الحجم (يمسّ C++)، أم يُعلَن «لم يجرِ» حتى يُكتب؟ **(ج)** التخزين: أيّ جزء يُقرأ (≥ 256 MiB، الأوّل أم الأخير؟)، وماذا عن أجهزة ترفض `O_DIRECT`؟ **(د)** هل تُشحن عدّادات الأخطاء الخام (`memoryMarchErrors` · `memoryWalkingOnesErrors`) أم تكفي أسماء الخادم المنقولة `…Passed`؟ (`Passed` يفقد العدد — القاعدة 20.)

> **محسوم 2026-10-08 (الجزء الأوّل)** — نصّ المستخدم: «س-30: الذاكرة اختبارها الأصلي أوّلاً بسقف زمني داخل الستّين ثانية، ثم الحمل للباقي. والتخزين قراءة راجعة لآخر 256 MiB. والعدد الخامّ يُشحن، والخادم يحكم.»
> اقتراح المنسّق المعتمد: «الذاكرة: النواة أوّلاً بسقف زمني داخل الستّين ثانية، ثم عمل الذكاء للباقي، ونافذتها معلنة في phaseBoundaries. وrandom verify مستقلّ عن الحجم يُكتب في C++. التخزين: قراءة راجعة لآخر 256 MiB مكتوبة بنمط حتمي بالإزاحة، وO_DIRECT مع رجوع معلن إلى DONTNEED (storageVerifyMethod). ويُشحن عدد الأخطاء الخام، والخادم يشتقّ …Passed (القاعدة 20)».
> الأثر: ت-16، ت-17، N-09، N-10. **«العدد الخامّ يُشحن»** يحسم الحقلين الاختياريين في N-10: `memoryMarchErrors` و`memoryWalkingOnesErrors` يُشحنان، و`memoryMarchPassed` وأخواتاه `…Passed` يشتقّها الخادم لا التطبيق (يُراجَع ما أُدرج من أسمائها في `computeScanSignature.js` [ن] — خارج هذا المستودع). التنفيذ في أجزاء لاحقة.

**س-31 — ناشئ (عن س-12 وس-23): ما يدخل `criticalDefectDetected`.** التعليق يقول إنه يشمل أخطاء التخزين (`StressTestServiceV2.kt:1077-1080`) والشرط `!verifGpuComputeOk` وحده (`:1081`). بعد وجود منتِجين حقيقيين: هل تدخل أخطاء التخزين والذاكرة؟ وهل يبقى الحكم على الجهاز (القاعدة 20) أم يُحذف ويقرّره الخادم من الخام؟ (س-20 لا يُدرجه ضمن المجمَّعات الباقية.)

> **محسوم 2026-10-08 (الجزء الأوّل)** — نصّ المستخدم: «س-31: criticalDefectDetected مجمَّع انتقالي معلَن، والحكم من الخامّ في الخادم.»
> اقتراح المنسّق المعتمد: «مجمَّع انتقالي يُدرج في transitionalAggregates، والخادم يحكم من الخامّ».
> الأثر: ت-18 صفّ 2، N-23. `criticalDefectDetected` يبقى (`Boolean?`) ويُدرج اسمه في `footer.transitionalAggregates[]` (N-23). لا يُوسَّع شرطه في التطبيق بأخطاء التخزين والذاكرة.

**س-32 — ناشئ (عن س-17 وس-18): من يعيّن «غير مقيس».** س-17: «غير مقيس» حالة بسبب لا `not_tested`؛ س-18: «لا قراءات بلا حركة يصير غير مقيس لا WARNING (تصنيف الخادم)». الحالتان اليوم يعيّنهما التطبيق (`SensorTests.kt:99-103`). **(أ)** يرسل التطبيق المفتاح `not_measured` بسببه (تصنيف في التطبيق)، أم **(ب)** يرسل الخام (قوائم / لا قراءات) ويصنّفه الخادم (القاعدة 20) فيبقى مفتاح التطبيق كما هو؟ يغيّر ذلك من يقرأ المفتاح الجديد وما يلزم `TestStatus.fromString`.

> **محسوم 2026-10-08 (الجزء الأوّل)** — نصّ المستخدم: «س-32: التطبيق يرسل الخامّ، والخادم يصنّف «غير مقيس».»
> اقتراح المنسّق المعتمد: «(ب) التطبيق يرسل الخامّ (القوائم، أو «لا قراءات»)، والخادم يصنّف. فلا مفتاح not_measured من التطبيق». قراءة المنسّق لـ«تبعاً لذلك» في أمر الجزء الأوّل: N-18 لا يُرسله التطبيق.
> الأثر: ت-15 (ج)، ت-14، N-18. **لا يُضاف `not_measured` إلى `TestStatus.fromString` بسبب هذا القرار.** وSN-08/SN-09: «لا قراءات» تُرسل خاماً (قائمة فارغة معلنة)، لا `WARNING` يُحمَّل معنى «غير مقيس».

**س-33 — ناشئ (عن س-13): دلالة `maxCpuTemp` وتسميته.** النصّ: «القيمة اليوم «آخر قراءة» لا «أقصى» فتُسمّى بما هي». الكود: ثلاثة فروع (`StressTestService.kt:137-145`) — قراءة حيّة عند الإنهاء، وإلا `maxTempC` الأقصى من `SamplerStats` بمصدر `cpu`، وإلا `-1f`؛ ويستعملها T-01 أيضاً (`:1343`). **(أ)** حقل واحد + مصدر معلن (المقترح N-08)؛ **(ب)** حقلان: قراءة النهاية وأقصى العيّنات (الأقصى تجميع على الجهاز، القاعدة 20)؛ **(ج)** قراءة النهاية وحدها وإسقاط فرع الأقصى — يغيّر سلوك T-01 المشترك.

> **محسوم 2026-10-08 (الجزء الأوّل)** — نصّ المستخدم: «س-33: حقل واحد لحرارة المعالج، ومصدره معلن.»
> اقتراح المنسّق المعتمد: «(أ): حقل واحد ومصدره معلن (N-08). وسلوك T-01 المشترك لا يتغيّر».
> الأثر: ت-10، N-08 (مُقرّ). `cpuTempFinalC` + `cpuTempFinalSource` (`live_read_at_finalize` · `sampler_max_cpu` · `absent:<السبب>`).

**س-34 — ناشئ (عن س-14): «المحرّك لم يعمل» مقابل «0 أقمار مقاسة».** البند «ج» من س-14 لم يحسمه نصّ المستخدم. اليوم `total == 0` ⇒ `FAIL` لعدم وصول أيّ استدعاء حالة ولعدم رؤية أقمار معاً (`GpsTests.kt:147-152`). مع `satelliteStatusCallbacks` (N-15) يمكن الفصل؛ والسؤال: ما تُسمّيه كل حالة في `status`، ومن يسمّيها.

> **محسوم 2026-10-08 (الجزء الأوّل)** — نصّ المستخدم: «س-34: L-02: التطبيق يرسل الأرقام، والخادم يسمّي الحالة.»
> اقتراح المنسّق المعتمد: «التطبيق يرسل الأرقام (satelliteStatusCallbacks وغيرها)، والخادم يسمّي».
> الأثر: ت-11، N-15، ت-15. لا اسم حالة جديداً من التطبيق لـ«المحرّك لم يعمل» مقابل «0 أقمار».

**س-35 — ناشئ (عن س-02): سقف نصّ السبب وثبات الغياب.** كل «سبب» نصّ خطأ (القاعدة 21): ما سقف طوله؟ وهل يُنقّى من مسارات الجهاز؟ وكذلك: `headroomUnsupported` يُثبَّت عند أول استثناء لباقي الفحص (`SystemFileReader.kt:290-296`) فاستثناء عابر يُنتج «غير مدعوم» طوال الفحص — هل يبقى التثبيت أم تُعاد المحاولة؟

> **محسوم 2026-10-08 (الجزء الأوّل)** — نصّ المستخدم: «س-35: سقف 200 محرف لنصّ السبب، ولا تثبيت لـ«غير مدعوم»: كل قراءة بحالتها، والنداء كل 10 ثوانٍ.»
> اقتراح المنسّق المعتمد: «سقف 200 محرف… ولا تثبيت: كل قراءة تحمل حالتها (N-02)، ويُعاد النداء كل 10 ث».
> الأثر: N-02، N-07، ت-03، ت-05. **السقف يسري على كل حقل «سبب» نصّي** (`channelDiscovery.reasons` · `cpuPolicies[].unreadableReason` · `*State` بنصّ `…:<نصّ>`). **التنقية من مسارات الجهاز لم يحسمها النصّ [م]:** يُقتطع الطول فقط ولا يُغيَّر النصّ (القاعدة 20) — يُوثَّق عند التنفيذ.

**س-36 — ناشئ (عن تصحيح س-19): ما «المميّز في الحمولة» الذي يحسم وحدة `1.0.8-v6beta`؟** النصّ المصحَّح: «1.0.8-v6beta تُعلن «الوحدة غير محدَّدة» إلا بمميّز في الحمولة». *لا أحسمه: النصّ يسمّي وجود مميّز ولا يسمّيه.* المقيس من هذا المستودع [أ-١٥ · أ-١٧]: `versionName` = `1.0.8-v6beta` و`versionCode` = 10 **قبل A70 وبعده** بالقيمة نفسها؛ و`footer.fieldsVersion` = 67 كذلك؛ و`variables[8].unit` = `"MB/s"` في الحالتين. فـ**لا أرى في بيانات `1.0.8-v6beta` القائمة مميّزاً من هذه الحقول** — فتُعلن «غير محدَّدة» دائماً ما لم يحمل الخادم مميّزاً من خارجها (تاريخ `header.startTimestamp` مقابل 2026-09-20 17:55:49 UTC وقت A70 هو الأقرب [م، استدلال]). ولما بعد fv68: `variables[8].unit = "MiB/s"` (N-21) مميّز صريح يُكتب. **ينتظر قرار المنسّق/المستخدم.**

---

## ٦ — ما لا تغطّيه الوثيقة

* لا قراءة لأي ملف في `neuralcheck-backend` أو `wepsite` (§٤.٢-٤.٣ منقول).
* لا قياس على جهاز: أعداد التحديثات في المرحلتين 3 و6 لـ`gpu_fps`، عدد بثّات البطارية في 480 ث، حجم الحمولة بعد +6 قنوات، زمن العيّنة بعد القراءات الجديدة — كلها **غير مقيسة**.
* الستّة المُصدَّرة من الإنتاج ليست عندي؛ العيّنات الذهبية السبع نسخ من الباك اند.
* لم أفحص السلوك الفعلي لـ`getThermalHeadroom` على جهاز (تعارض التعليقين في §١.١٢).
* لم أُعدّل `docs/MEASUREMENT_SPEC.md` ولا غيره (وثيقة واحدة بأمر المستخدم)؛ وتعارضه مع A70 مسجَّل في §١.٢.
* **هذه النسخة:** لا كود ولا اختبار ولا بناء ولا عيّنة ذهبية مولَّدة؛ وصف التوليد في §٣ بند 7 فقط.
* **لم أقرأ** `FV68-DECISIONS-2026-10-08.md` (في مساحة العمل، لا في مستودعي — القاعدة 27)؛ اقتراحات المنسّق منقولة في الأمر بنصّها وهي ما نُقل أعلاه.
* لم أُصنّف الأسئلة الناشئة س-27 إلى س-35 قراراتٍ؛ ولم أخمّن جواباً لأيّ منها (القاعدة 13). **[كان هذا في نسخة الحسم الأولى؛ حسمها المستخدم 2026-10-08 (الجزء الأوّل) — §٥. وما نفّذه الوكيل منها وما أضافه بلا نصّ: §٧.]**
* خارج الوثيقة بنصّ الأمر: حروف الفئات (الخادم)، وتقييما «الحرارة أثناء الاستعمال» و«الأداء في الألعاب» (عقد التقييم، الدفعة ١١).

---

## ٧ — حالة التنفيذ (الدفعة ٣ ج، 2026-10-08 +03)

مصدر هذا القسم: `git log` وملفّات XML لمخرَج `cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest` (مجموع الأعداد بسكربت يجمع `tests` و`failures` و`errors` و`skipped` من `app/build/test-results/testLaunchDebugUnitTest/TEST-*.xml`). **الختم 68 وُضع في أول التزام يغيّر الشكل (`2afd392`)، وfv68 غير مكتمل بالتصميم حتى الالتزام الأخير من الدفعة ٣؛ ولا يُبنى APK للإصدار قبله.**

| الخطوة | الالتزام | ما غيّره | ملفّات XML · اختبارات (كلّها 0 فشل · 0 خطأ · 0 تخطٍّ) |
|---|---|---|---|
| الأساس (كود `d87f2d9`) | `5920290` (وثيقة) | — | 91 · 712 |
| ٣-ج-١ الحامل | `2afd392` | عمود `functional_results.measurements` (Room 7 ⇒ 8) · `TestResult.measurements` · `measurements` في `FunctionalResultDto`/`ManualResultDto` · `MeasurementsJson` · `ReasonText` · `ALL_MIGRATIONS` · **الختم 68** | 96 · 734 |
| ٣-ج-٢أ الأسماء والقارئ | `02368ff` | `ChannelNames` مصدراً واحداً · `TimeSeriesFileReader.calculateSummary` يقرأ بالاسم ويعلن `unresolvedColumns` · استبدال اختبارات 11 قناة | 97 · 745 |
| ٣-ج-٢ب تخطيط العيّنة | `bc70908` | `charge_counter_raw` و`cpu_freq_policy{N}_khz` بعد `v[0..10]` · `footer.cpuPolicies[]` · `footer.channelReadFailures` + `channelReadFailureCounts` · `channelDiscovery.reasons` | 103 · 797 |

**ما نُفِّذ من البطاقات (وما لم يُنفَّذ):**

* **ت-01 `charge_counter_raw`** — منفَّذ: الاسم والوحدة `as_declared_by_device` والمصدر `BatteryManager.getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER)` (N-01)، قراءة خام، **الصفر عند الاكتشاف يُدرج** (س-27 د)، والفشل أثناء الفحص ⇒ آخر قيمة صالحة + فهرس في `channelReadFailures`. **غير منفَّذ:** قارئ الخادم `findings.js:1055-1060` (يبحث `charge_counter_uah`) — خارج هذا المستودع.
* **ت-05 `cpu_freq_policy{N}_khz` و`cpuPolicies[]`** — منفَّذان. **التعداد بقراءة ملفّات كل مرشّح `0 until cpuCount`، لا بسرد المجلّد** (`CpuPolicyDiscovery`): ENOENT على كل ملفّات المرشّح ⇒ لا سياسة ولا أثر؛ EACCES أو أيّ نصّ غير ENOENT ⇒ السياسة موجودة وتُذكر بنصّ سببها. **هل يسمح SELinux بسرد `cpufreq` على كل طراز: ما زال غير معلوم** (لم يُعتمد عليه). **كلفة القراءات الإضافية في كل عيّنة وعدد السياسات الفعلي على الأجهزة: غير مقيس** — يُجمع في جولة الفحص الميداني.
* **توسيع `channelDiscovery` (س-02)** — منفَّذ: `reasons` (N-07) بنصّ الخطأ بسقف 200 محرف (س-35)، والتمييز blocked/missing **بنصّ الخطأ** (`FailureText`) لا بصنف الاستثناء، ويبقى `SecurityException` ⇒ blocked بصنفه (لا يوحّد حالات). المفتاحان `blockedBySelinux` و`missing` كما هما.
* **ت-15 الحامل (س-29 أ)** — منفَّذ: الحامل فقط؛ لا شيء يكتب `measurements` بعد (المفتاح غائب من كل حمولة حتى يكتبه التزام لاحق).
* **ت-19 الختم** — منفَّذ: `fieldsVersion = 68` وكتلة التاريخ. **غير منفَّذ بعد:** وحدة `MiB/s` المعلنة لـ`v[8]` و`v[9]` · `storageThroughputSegmentsUnit` · نصّا `source` لـ`v[0]` و`gpu_fps` وإفراغ نافذة `gpu_fps` (ت-12) · تصحيح `MEASUREMENT_SPEC.md`.
* **القاعدة 24** — الأسماء مصدر واحد `ChannelNames` يستعمله المسبار والتعريفان V2 وV1 والقارئ. **ما بقي مكرَّراً:** قائمة V1 الاسمية في `app/src/main/assets/scoring_config.json:219-236` (أصل JSON لا كود)، وأسماء الفروع في `StressEffectivenessCalculator` وغيره تُقرأ بفهرس ثابت — انظر القيود.
* **القاعدة 25 (قرّاء التطبيق)** — `TimeSeriesFileReader.calculateSummary` بالاسم؛ و`BehaviorFingerprintBuilder` يمرّر المفاتيح الجديدة؛ وبقية القرّاء (`SamplerStats` · `StressEffectivenessCalculator` · `BatteryIndicators`) تقرأ `v[0..7]` و`v[10]` بفهرس ثابت **على عيّنات من الذاكرة أنتجها البناء نفسه** (لا من ملفّ) — والفهارس مجمَّدة بالعقد ويثبّتها `V2BaseLayoutTest` (اسم كل فهرس أساسي، و`BatteryIndicators.batteryTempIndex()`). هذا **ليس** «قراءة بالاسم»؛ هو فهرس ثابت محمي باختبار.

**ما نفّذه الوكيل ولم يحسمه النصّ `[م]` — ينتظر إقرار المنسّق:**

1. شكل `channelReadFailures`: خريطة اسم القناة ⇒ قائمة فهارس العيّنات (موضعها 0-based في `samples[]`، تصاعدياً)، **كل قناة مفتاح** (قائمة فارغة = لا فشل؛ المفتاح الغائب = «غير متتبَّع»)، **سقف 200 فهرس للقناة**، و**الاسم الشقيق `channelReadFailureCounts`** (اسم القناة ⇒ العدد الكلي، يتجاوز السقف). هذا الاسم الأخير جديد من الوكيل.
2. قيمة العيّنات قبل أول قراءة صالحة = القيمة التي اجتازت بها القناة الاكتشاف (البذرة).
3. قيمة غير منتهية (`NaN`/`Infinity`) أو قارئ يرمي استثناءً ⇒ **فشل** يُسجَّل بالفهرس (لا يدخل `NaN` المصفوفة: Gson يرفضه فيسقط الملفّ والحمولة).
4. `unreadableReason` على عنصر `cpuPolicies[]` يحمل أيضاً سبب غياب `relatedCpus` (لا `cpuinfoMaxFreqKhz` وحده)، النصوص مضمومة بـ« | »، بسقف 200، وكل نصّ يسمّي ملفّه.
5. المفتاحان `measurementsUnencodable` و`measurementsUnreadable` (في خريطة `measurements` نفسها) كإعلان صريح لفشل الترميز/الفكّ بدل `null` أو استثناء يُسقط نتائج الفحص كلّها (`StressTestService` يحفظ كل النتائج داخل `try` واحد).
6. نصوص الأسباب الإنجليزية التقنية على السلك (مثل «readCpuTemp returned … the reader does not expose its error text») — بيانات لا نصّ واجهة؛ والمفاتيح `reasons` و`cpuPolicies` أسماء مقرَّة (N-07 وN-06).
7. تصنيف `channelDiscovery` للنصّ الذي لا يسمّي EACCES ولا ENOENT: يدخل القائمة القديمة `missing` (لا تعبّر القائمتان عن «غير ذلك») ويبقى نصّه كاملاً في `reasons`.

**القيود المعروفة غير المُصلَحة (من هذا الجزء):**

* **القاعدة 21 لقنوات المسبار القديمة:** القارئات التي تبتلع أخطاءها داخلياً — `SystemFileReader.kt:341` (`readCpuTemp`) و`:362` (`readGpuTemp`) و`:427` (`readCpuUsage`) و`:282` (`readThermalHeadroom`) و`:312` (`readThermalStatus`) و`:221` (`readCpuFrequency`، ومنه `readLongFromFile` `:735`) — لا تعطي نصّاً؛ فسبب قناتها في `reasons` يقول صراحةً إن القارئ لا يكشف نصّ خطئه بدل اختلاق سبب. القنوات الجديدة (`charge_counter_raw` و`cpu_freq_policy{N}_khz`) تعطي نصّ الخطأ. **لم يُصلَح لأن إصلاحه إعادة كتابة تلك القارئات.**
* **القارئات بفهرس ثابت** (الفقرة أعلاه): محمية باختبار، لا بالاسم.
* **حرّاس `v[1..10]`** (`0` و`-1`) باقية «كما هي» بنصّ الحسم (س-27 هـ).
* **سباق قائم قبل هذا الجزء:** `DisciplinedSampler.restart()` يستدعي `shutdownNow()` ثم يبدأ منفِّذاً جديداً دون انتظار؛ قد تتداخل عيّنتان لحظةً فيتكرّر فهرس في `channelReadFailures` (أثره: فهرس مكرَّر أو منزاح بواحد في القائمة، لا فقد قيمة). غير مقيس.
* **الخادم:** شرط س-27 («كل قارئ في الخادم يستثني الفهارس، باختبار لكل قارئ») **لم يُنفَّذ — خارج هذا المستودع.** ويلزم الخادم أن يقرأ `variables[]` بطول متغيّر وأن يحدّث `findings.js:1055-1060` و`runConsistencyTest.js:43-80` (§٤.٢).
* **JVM وحده:** سلسلة الجولة الكاملة اختُبرت دون استبدال طبقة (عيّنة ← كاتب حقيقي ← قارئ حقيقي ← بانٍ حقيقي ← Gson الإنتاج)، لكن **`DisciplinedSampler` و`StressTestService` الحقيقيين لم يعملا** (يحتاجان جهازاً). مرحلة الجهاز من §٣ بند 7 لم تُجرَ، فالقاعدة 41 غير مستوفاة بعد.

### ٧-ب — الجزء ٢-أ (ما لا يحتاج ترجمة C++، 2026-10-08 +03)

مصدر الأعداد: ملفّات XML لمخرَج `cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest` (سكربت يجمع `tests` و`failures` و`errors` و`skipped` من `app/build/test-results/testLaunchDebugUnitTest/TEST-*.xml`)، بعد كل التزام. الأساس `947ad83`: 103 ملفّاً · 797 اختباراً. **الختم 68 كما هو؛ fv68 غير مكتمل بالتصميم حتى آخر التزام من الدفعة ٣.**

| الالتزام | ما غيّره | ملفّات XML · اختبارات (كلّها 0 فشل · 0 خطأ · 0 تخطٍّ) |
|---|---|---|
| `c9f0992` | قوائم الحرارة: `thermalHeadroomReadings[]` (نداء كل 10 ث، حالة كل قراءة، بلا تثبيت) و`thermalStatusEvents[]` (مستمع، أوّلها قراءة عند التسجيل) وحالتا القناتين (ت-03، ت-04، N-02..N-04، س-35) | 105 · 822 |
| `051aa8b` | أزواج البطارية الخامّ `batteryPairs[]` (`voltageRaw`/`currentRaw`/`chargeCounterRaw`/`levelRaw`/`levelScale`/`tempRaw`) وسقف 200 `batteryPairsState` (ت-06، N-05، س-28 المصحَّح) | 107 · 839 |
| `5e887f5` | `gpuComputeGemmCorrect` (`Boolean?`) و`gpuComputeGemmState` و`criticalDefectDetected` مجمَّعاً انتقالياً و`criticalDefectInputsNotRun[]` و`transitionalAggregates[]` (ت-18 صفّان 1-2، N-11، N-23) | 109 · 853 |
| `07407fe` | `workersPinned*`/`workersPinFailed*` تغيب حتى تقع محاولة تثبيت و`workersPinState` (ت-13، ت-18 صفّ 4، N-12) | 109 · 860 |
| `d7a930c` | `stillnessState` داخل `scanStillness` و`preScanWarningsState` (ت-18 صفّان 6-7، N-13، N-14) | 110 · 870 |
| `282370b` | `storage*VerifyErrors` تغيب حتى يجري تحقّق و`storageVerifyState`؛ حذف `storageSlcCacheSizeMb` (ت-18 صفّ 3، ت-08) | 110 · 875 |
| `23c66bd` | `unmeasuredDefaults` يعدّ ثوابت `dataQuality`؛ `aiDelegateInitError` غائب لا `""` (ت-18 صفوف 8، 12، 13) | 111 · 880 |
| `c4c1447` | مؤشّرات `batteryHealth` التي لا تُحسب تغيب مع `<الاسم>State` (ت-18 صفّ 9) | 112 · 890 |
| `55ebb0f` | حمولة بلا سلسلة زمنية: حقول غائبة و`timeSeriesState` (ت-18 صفّ 11) | 113 · 896 |
| `d1a062c` | `cpuTempFinalC` + `cpuTempFinalSource` بدل `maxCpuTemp` (ت-10، N-08) | 114 · 901 |
| `78ddbe3` | L-02: `requestLocationUpdates` على GPS طوال النافذة والأرقام الخامّ في `measurements` (ت-11، N-15) | 115 · 908 |
| `24a2d60` | `ACTIVITY_RECOGNITION` (API ≥ 29)؛ SN-08/SN-09 `NOT_TESTED` بلا إذن ويشحنان قراءاتهما الخامّ (ت-14) | 116 · 919 |
| `4a250c1` | إعلانات `v[0]` و`gpu_fps` ووحدة `MiB/s` لـ`v[8]`/`v[9]` و`storageThroughputSegmentsUnit`؛ تفريغ نافذة `gpu_fps` عند نهاية المرحلة 5؛ تصحيح `MEASUREMENT_SPEC.md` (ت-12، ت-19) | 117 · 926 |
| `f49c430` | اختبار فقط: اختبار L-02 يثبّت «آخر استدعاء لا الأقصى» | 117 · 926 |

**ما لم يُنفَّذ من البطاقات المعنيّة (وسببه):** ت-18 صفّ 10 (`cycleCount` وصفر `chargeCounterUah`/`energyCounterNwh`): وسمه الحسم «ينتظر تأكيداً» · ت-02 (مفردات `cycleCountSource`) خارج الأمر · SN-04 وشبكة اللمس والحالات الأربع (الدفعة ١٠) · منتِجا التحقّق (ينتظران إذن ترجمة C++) · منتِج D للمعالج (ينتظر قرار الكلفة) · العيّنة الذهبية لحمولة التطبيق (آخر الدفعة ٣) · باقي تحديث `MEASUREMENT_SPEC.md` (القنوات والقوائم الجديدة؛ صُحِّح السطران 791 و797 وما يقول الشيء نفسه).

**ما نفّذه الوكيل ولم يحسمه النصّ `[م]` — ينتظر إقرار المنسّق (أسماء وقيم جديدة، لا تسمية منها في N-01..N-23):**

> **أُقرّت البنود التسعة كلّها بنصّ المستخدم 2026-10-08:** «الأسماء الجديدة كلّها معتمدة كما اقترحها الوكيل: حالات قوائم الحرارة، وأسباب «لم يجرِ»، ومدخلات transitionalAggregates، وحالات مؤشّرات البطارية، وtimeSeriesState، ومفاتيح L-02، وreadings[] لـSN-08/09.» (§٧-ج). وما نصّه التالي أدناه يُقرأ مُقرّاً.

1. حالات القوائم: `non_finite:<قيمة>` (حالة قراءة headroom)، و`thermalHeadroomState` = `ok` · `api_below_30` · `no_reading_taken` · `start_failed:<نصّ>`، و`thermalStatusState` = `initial_read_failed:<نصّ>` (يضاف إلى الثلاث المقرّة)؛ و`footerDiagnostics.thermalLists` = `not_started` و`footerDiagnostics.batteryPairs` = `ok` | `not_recorded`.
2. أسباب `not_run:<سبب>`: `governor_capped` · `no_attempt_completed` · `no_v2_result` (لـ`gpuComputeGemmState` ولـ`storageVerifyState`)، و`not_run:producer_not_implemented` مؤقّتاً لـ`storageVerifyState` حتى يصل المنتِج؛ واسم المُدخل `gpuComputeGemm` في `criticalDefectInputsNotRun`؛ و`workersPinState` = `not_attempted:cpu_phases_not_run` · `not_attempted:no_v2_result` · `pin_result_unreadable:size=<n>`.
3. `transitionalAggregates[]`: صيغة `<كائن>.<حقل>` للمتداخل، وثلاثة مدخلات قرأها الوكيل من «…» في س-20: `batteryHealth.batteryTempRiseC` · `batteryHealth.levelDropPct` · `batteryHealth.dischargeVsDesignRatioMah`؛ ومدخلات `unmeasuredDefaults` الأربعة `dataQuality.cpuTempCoveragePct` / `gpuTempCoveragePct` / `skinTempCoveragePct` / `cpuTempPrimarySource`.
4. مؤشّرات البطارية: مفاتيح `dischargeRateUnderLoadMaState` و`voltageStabilityMvState` و`dischargeVsDesignRatioMahState` (`computed` | `not_computed:<سبب>`) وأسبابها `no_current_reading_in_load_window` · `fewer_than_2_voltage_readings_in_load_window` · `design_capacity_unavailable` · `battery_level_unavailable`.
5. `timeSeriesState` (دائم الحضور في `timeSeries`): `file` · `footer_backup` · `no_file:<سبب>`؛ وفي «لا ملف» يبقى `totalSamples = 0` و`isComplete = false` (إعلان قديم؛ لم يُزَل لأن ردّ الخادم على غيابه غير مرئي من هذا المستودع).
6. `cpuTempFinalSource`: نصّ السبب بعد `absent:` (يحدّد N-08 البادئة وحدها)؛ و`maxCpuTemp` يبقى حقلاً قديماً قابلاً للغياب في الصنف والـDTO لقراءة ملفّات ≤ 67 فقط ولا يكتبه الكاتب.
7. L-02: `gpsProviderEnabled` · `gnssCallbackState` (`registered` | `registration_failed:<نصّ>`) · `locationUpdatesState` (`requested` | `request_failed:<نصّ>` | `not_requested:gnss_callback_registration_failed`) · `timeToFirstFixState` (`measured` | `absent:no_first_fix_in_window`)؛ وفاصل التحديثات 1 ث (قرار الوكيل، لم يحدّده النصّ).
8. SN-08/SN-09: مفتاحا `windowMs` و`readings[]` بعنصر `{t, values}` (`t` ms منذ تسجيل المستمع)؛ والحالة والنصّ بلا تغيير («لا قراءات» تبقى `WARNING`) — إن أراد المنسّق حالة أخرى من التطبيق فذلك قرار جديد.
9. نصّ واجهة جديد واحد (بالعربية وحدها، القرار ٩٠): `scan_permission_label_activity_recognition` = «النشاط البدني» (تسمية الإذن في إشعار الأذونات المرفوضة).

**القيود المعروفة غير المُصلَحة (من هذا الجزء):**

* **حارس الحاكم والمجمِّع والمسبار ما زالت تستعمل `readThermalHeadroom` بذاكرتها 1.1 ث وتثبيتها عند أول استثناء** (`SystemFileReader.kt:306` `headroomUnsupported = true`؛ النداء كل ثانية `StressTestServiceV2.kt:1531`) — بنصّ س-03 («الحارس كما هو»). والمجمِّع يقرأ منها `thermalHeadroomAvg/Peak` الانتقاليّين. **وهل تحدّد المنصّة معدّل النداء عبر القارئين معاً: غير مقيس** — نداء القائمة (كل 10 ث) منفصل، وإن أعادت المنصّة `NaN` لنداء تصادم مع الحارس فالعنصر يقول `nan`.
* **صف 10 لم يُنفَّذ:** `BatteryReader.kt:650` و`:660` (`> 0` يُسقط صفر `cycleCount`) و`:670` و`:675` (`!= 0L` يُسقط صفر `chargeCounterUah`/`energyCounterNwh`) — ينتظر تأكيداً بنصّ الحسم.
* **`levelDropPct` و`batteryTempRiseC`** يبقيان على قصّهما إلى 0 (`BatteryIndicators.kt:181-187`): الصفّ 9 يسمّي المتوسّط والانحراف ونسبة التصميم وحدها.
* **`PreScanWarningsHolder`** (`PreScanWarningsHolder.kt:37` و`:43`) لا يميّز «لم يضع الفحصُ تحذيراً» من «لم يضع أحد شيئاً»: `read` مع `[]` قد تعني أيّاً منهما؛ تمييزهما يحتاج حالة ثالثة لا يملكها N-14.
* **تحليل مصفوفة نتيجة NEON بتخطيطات أقدم** `StressTestServiceV2.kt:1425-1430` (`if (results.size >= 9/13) … else 0`) ما زال يضع `0` لحقل غائب لكنه لا يُقرأ إلا حين يكتمل الذيل (`pinTelemetryComplete`)، فلا يبلغ الحمولة.
* **رأس `readFooterBackup`** (`TimeSeriesFileReader.kt:68-70`: `androidApiLevel = 0`، `cpuCores = 0`، …) ما زال أصفاراً في الكائن الداخلي؛ الحمولة تعلنه `footer_backup` وتترك حقول رأسه غائبة، لكن مستهلكي الكائن الآخرين (`GenerateReportUseCase`) لم يُفحصوا في هذا الجزء.
* **Gson يبني `TimeSeriesFooter` بلا قيم Kotlin الافتراضية:** مفتاح غائب من ملفّ قديم يترك حقل قائمة غير قابل للغياب `null` فيسقط البناء عند `throttlingEvents.map` — لوحظ ولم يُغيَّر.
* **قائمة `variables[]` لنكهة `datacollect` (V1)** تحمل نصّ `v[0]` القديم (`TimeSeriesFileWriter.kt:927`) — بقرار س-08.
* **حرّاس `v[1..10]`** (`0`/`-1`) كما هي بنصّ الحسم (س-27 هـ).
* **الخادم:** كل ما ورد من مفاتيح وحالات جديدة في هذا الجزء لا قارئ له بعد في `neuralcheck-backend` (خارج المستودع)، وشرط س-27 («كل قارئ يستثني الفهارس الفاشلة») ما زال بلا تنفيذ هناك.
* **الجهاز:** `ThermalListCollector` و`BatteryReader` (المستقبِل) و`DisciplinedSampler` و`StressTestService` الحقيقيّة لم تعمل — الاختبارات تمرّ بالسلسلة الحقيقية دون استبدال طبقة في JVM، وتستبدل `PowerManager`/`LocationManager`/`SensorManager` بمحاكاة؛ فالقاعدة 41 غير مستوفاة حتى مرحلة الجهاز (§٣ بند 7).

**ما بقي من الدفعة ٣** (أجزاء لاحقة): منتِجا التحقّق (ت-16 التخزين، ت-17 الذاكرة بتحقّق عشوائي في C++) · ت-18 صفّ 10 · أرقام شبكة اللمس والإضاءة والحالات الأربع و«انتظر» (ت-15، الدفعة ١٠) · العيّنة الذهبية لحمولة التطبيق (§٣ بند 7) · باقي `docs/MEASUREMENT_SPEC.md` (القنوات والقوائم الجديدة) · ت-20 (حرف D للمعالج، بعد قرار الكلفة).

### ٧-ج — الجزء ٢-ب (منتِجات التحقّق الثلاثة · الملفّ القديم · ما أضافه الملحقان، 2026-10-08 +03)

> **حُسم بعد كتابة هذا القسم بقرارات المستخدم (الجزء ٢-ج، §٧-د):** قصّ فرق الحرارة السالب (أُزيل) · فشل `fsync` الذي لا يُشحن (يُشحن) · `phaseBoundaries` في الحمولة (لا) · جهاز تبلغ كتابته الموعد (يبقى `no_time_left` ويُعدّ) · طرق مقارنة D الثلاث (حلّ محلّها معيار ≤ 1%) · **وأُقرّت الأسماء [م] الواردة أدناه والثابتان (30 ث و256 MiB) بنصّ المستخدم 2026-10-08.** ما كُتب أدناه حالُ ٢-ب لحظة كتابته.

مصدر الأعداد: ملفّات XML لمخرَج `cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest` (سكربت يجمع `tests` و`failures` و`errors` و`skipped` من `app/build/test-results/testLaunchDebugUnitTest/TEST-*.xml`)، بعد كل التزام، بسطر Java المرفقة مع Android Studio (jbr 21.0.9) داخل الأمر وحده (README). الأساس `8939f7d`: 117 ملفّاً · 926 اختباراً. **الختم 68 كما هو؛ fv68 غير مكتمل بالتصميم حتى آخر التزام من الدفعة ٣؛ ولا APK.**

| الالتزام | ما غيّره | ملفّات XML · اختبارات (كلّها 0 فشل · 0 خطأ · 0 تخطٍّ) |
|---|---|---|
| `4998437` | منتِج الذاكرة في C++ (ت-17): بلاطات ٢٥٦ ميبي‑بايت · March C- وWalking Ones وتحقّق عشوائي مستقلّ عن الحجم · سقف زمني · النواة أوّلاً ثم حمل الذكاء · النافذتان معلنتان · إزالة `run_memory_stress` | 119 · 948 |
| `4f77ecd` | منتِج التخزين (ت-16): نمط يعتمد الإزاحة · قراءة راجعة لآخر ٢٥٦ ميبي‑بايت بـ`O_DIRECT` مع رجوع معلن إلى `DONTNEED` | 121 · 976 |
| `afac56f` | منتِج D للمعالج (ت-20): الغربال وSHA-256 · ثلاثة فحوص لكل مرحلة · `cpuKnownAnswerState` و`cpuKnownAnswerRuns[]` | 123 · 998 |
| `872a3d8` | الملفّ القديم (fv67 عبر بانٍ fv68): لا سقوط · الختم والإصدار من الملفّ · مفاتيح fv68 غائبة ومُعلَنة في `fieldsNotInFile` · اختبار على عيّنة fv67 ذهبية | 125 · 1014 |
| `dbc2df1` | ت-18 صفّ 10: الدورات وعدّادا الشحن والطاقة كما قُرئت والصفر معها بحالة معلنة | 126 · 1025 |
| `bbf470c` | `batteryTempRiseC` المفقودة غائبة بحالة · `levelDropPct` بإشارته بلا قصّ | 126 · 1033 |
| `6200f4e` | `PreScanWarningsHolder`: الحالة الثالثة `not_written` | 127 · 1039 |
| `73e494c` | «Fiziksel aktivite» لتسمية إذن `ACTIVITY_RECOGNITION` | 127 · 1039 |
| `8b3bfb2` | `README.md`: سطر Java المؤقّت لكل أمر ومهمّتا الاختبار و`.so` | 127 · 1039 |

**ترجمة C++ (قول المستخدم 4: إذن ببناء `.so` لا APK).** الأمر بالسطر المعتمد: `./gradlew --no-daemon :app:externalNativeBuildLaunchDebug` — **BUILD SUCCESSFUL** عند كل التزام فيه C++ (ثلاث مرّات، 10 إلى 12 ث)، ويُنتج `app/build/intermediates/cxx/Debug/*/obj/arm64-v8a/libstress.so` وحده. وقائمة `app/build/outputs/apk` (بأحجامها وأوقاتها) **مطابقة قبل التشغيل وبعده**: `app-launch-debug.apk` (٢٠٢٦‑٠٩‑١٦) و`app-launch-release.apk` (٢٠٢٦‑١٠‑٠٧) وملفّا `output-metadata.json` — ما رأيتُه قبل أول بناء. و`nm -D` على `libstress.so` يُظهر المداخل الجديدة (`runMemoryVerify` · `setNativeVerifyCancel` · `prepareStoragePattern` · `stampStoragePattern` · `runStorageVerify` · `runSieveKnownAnswer` · `runSha256KnownAnswer`). وفحصٌ نحويّ إضافيّ بمترجم NDK نفسه (`clang++ --target=aarch64-linux-android28 -fsyntax-only -Wall -Wextra`) على كل ملفّ C++ تغيّر خرج **بلا تحذير** (لا يُنتج شيئاً). **لا `assemble*` ولا `bundle*`.**

**ما لا تفحصه اختبارات JVM:** كود C++ كلّه (النواة والقراءة الراجعة والغربال وSHA) وطبقة JNI؛ والاختبارات تستبدلها بمحاكاة (`FakeNativeVerification`) فهي تثبت منطق Kotlin حولها وتصنيف نتائجها، لا الطبقة التي قد ترفض (قاعدة 37). **مترجم المضيف:** لا `g++` ولا `gcc` ولا `clang` على `PATH` (مسار `scoop\apps\gcc` فيه محذوف)؛ و`cl.exe` موجود تحت Visual Studio 2022 *Preview* (`MSVC 14.44.35207`) لا على `PATH`؛ ومترجم NDK يستهدف أندرويد وحده. **لم أبنِ به.** اقتراح: يصلح لاختبار الدوالّ الخالصة بلا JNI `memory_test.cpp` و`prime_sieve.cpp` و`sha256.cpp` و`known_answer.cpp`، لكن `native_common.h` يستعمل `clock_gettime` (غير موجود في MSVC) و`storage_verify.cpp` يخلط النمط الخالص بـ`open`/`O_DIRECT`/`pread` — فيلزم فصل النمط في ملفّ خالص وطبقة ساعة صغيرة قبل أن يُبنى هدف اختبار مضيف.

#### البطاقات

* **ت-17 منتِج الذاكرة — منفَّذ.** `memory_verify.cpp` (جديد) يعيد حجم المخزن بالصيغة نفسها (نسبة من الذاكرة، حدّ `freeram − 500 MiB`، حدّ أدنى ٥١٢ MiB، أعلى ٨ GiB) بـ`mmap` (فيبقى `errno`)، ثم يشغّل البطّارية كلّها **بلاطةً بلاطةً** تحت سقف زمني وراية إلغاء. **وفشل `sysinfo` أو الحجز حالة بـ`errno` لا `{0,0,0,0}`** (قول المستخدم 1). **التحقّق العشوائي** نمط يعتمد فهرس الكلمة (دالّة splitmix64 على عدّاد): لا حجم ولا مرجع تجزئة (قول المستخدم 2؛ و`random_verify_not_implemented` لم يكن في الكود قطّ فلا شيء يُزال). العدّادات 64-بت (ثمانية قراءات walking-ones على بلاطة واحدة = 2³¹ بايتاً). **سقف النواة الزمني ٣٠ ث [م]** (نصف المرحلة؛ أدلّته في KDoc الثابت `MemoryVerifyRunner.TIME_CAP_MS`: ادّعاء تعليق قديم غير مقيس بأن جهازاً بـ8 GB أنهى March+Walking في 25 ث، وأن التحقّق العشوائي يضيف مرورين، وأن حمل الذكاء بعده ينشر `v[9]` بـ1 Hz). **بلوغه حالة معلنة** `partial:time_cap_reached` مع `memoryVerifyBytes < memoryVerifyBufferBytes` — لا نجاحاً للكلّ (قاعدة 22). **حجم البلاطة ٢٥٦ MiB [م].** **النافذتان:** `PhaseBoundary.windows` داخل مدخل المرحلة نفسه (مدخل واحد لكل مرحلة، فمن يبحث بـ`phaseId` يجد واحداً)، **وأيضاً** `verificationResults.memoryPhaseWindows` — لأنّ `footer.phaseBoundaries` **في الملفّ وحده** (`TimeSeriesFooterDto` لا يحمله، §١.٧)، فإعلانه فيه وحده لا يبلغ الخادم. **ما غيّر في الحمل المقيس:** نافذة النواة (حتى ٣٠ ث) تقرأ فيها `v[9]` صفراً (لا ناشر) ويتغيّر نمط الحمل على الذاكرة في تلك النافذة؛ **والنواة لم تكن تعمل في الإنتاج منذ 1.6.D** (`USE_AI_WORKLOAD_MEMORY = true`) — فمخاطرة قتل التطبيق بذاكرة قد تبلغ ٨ GiB، وزمنها الفعلي وبلوغها السقف على أجهزة ٨-١٦ GB: **غير مقيسة**.
* **ت-16 منتِج التخزين — منفَّذ.** `storage_verify.cpp`: كل بايت في كتلة ١ MiB = `j & 0xFF` عدا أوّل ١٦ بايتاً من كل صفحة ٤ KiB = إزاحتها المطلقة وتجزئتها، فالملف دالّة في إزاحته (نمط يتكرّر كل ١ MiB لا يفرّق الكتلة ٧ من ٨). القراءة بـ`O_DIRECT` (ثابته على arm64 `0200000` لا `040000` العامّ — لذا لا تُكتب في Kotlin) على مخزن محاذى ٤ KiB، بعد `posix_fadvise(DONTNEED)` **على المسارين**؛ **رفض `O_DIRECT` (في الفتح أو في أول قراءة) رجوع معلن لا فشل** (`storageVerifyMethod = dontneed_sequential:<o_direct_open_failed | o_direct_read_failed>:errno=…`). `EIO` وكتلة مفقودة بعد نهاية الملف تُحسب أخطاء. **والإجابات المطلوبة:** (١) *هل يغيّر النمط الحتمي كلفة الكتابة فيمسّ `v[8]`؟* — الإضافة نداء JNI واحد و٢٥٦×١٦ بايتاً لكل ميبي‑بايت (حسابياً ضئيل أمام كتابة ميبي‑بايت إلى الفلاش) ولا تغيّر حلقة الكتابة غير ذلك؛ **أثرها على `v[8]` غير مقيس** (ويبقى محتوى الكتل ٩٩٫٦٪ كما كان). وبلا المكتبة الأصلية تُملأ الكتلة بمحتواها القديم حرفاً بحرف وتتعطّل القراءة (`not_run:pattern_unavailable`). (٢) *كم تضيف القراءة إلى زمن المرحلة وأين تقع؟* — **لا تضيف**: تقع بعد انتهاء الكتابة وإغلاق الملف وقبل حذفه، في الوقت المتبقّي من الـ٧٥ ث (التي تُنتظَر على أي حال)؛ و`v[8]` يُصفَّر قبلها و`storageWriteDurationMs` لا يشملها؛ وزمنها الفعلي لكل جهاز: **غير مقيس** (حدّها: ما بقي من المرحلة − ٥٠٠ ms، وأدناه ٢ ث وإلّا `not_run:no_time_left`). (٣) *ماذا على جهاز يرفض `O_DIRECT`؟* — يرجع إلى قراءة مخزَّنة بعد `DONTNEED` ويُعلَن في `storageVerifyMethod`. **وتحفّظ من معرفة بالنواة لا قياس:** ملفّ مشفَّر (FBE) بلا تشفير مضمَّن قد يقبل فتح `O_DIRECT` ثم يخدم القراءة من الذاكرة المخبّأة؛ لا يُعرف ذلك من التطبيق، ولهذا يُطبَّق `DONTNEED` أوّلاً — **أيّ الأجهزة ترفض أو تتجاهل: غير معلوم حتى فحص الجهاز.** **قيد:** جهاز بلغت كتابته الموعد (٧٥ ث) لا يبقى له وقت (`not_run:no_time_left`)؛ حجز نافذة للقراءة يقصّر الكتابة فيمسّ `v[8]` و`storageBytesWritten` — قرار المستخدم. **وقيد:** فشل `fsync` في حلقة كتابة لم تُنفَّذ قراءتها لا يُشحن (نصّ العقد يربط العدّادين بالحالة).
* **ت-20 منتِج D للمعالج — منفَّذ** كما صمّمه تقدير ٣-ب: كوروتين **شقيق** عند موضعَي `aiCpuWorkload.run` (`StressTestServiceV2.kt` ‏:833 و:1096 عند `afac56f`؛ كانا :715 و:974 عند `8939f7d`) لا داخل `AiCpuWorkload`؛ ثلاثة فحوص لكل مرحلة عند ٢٥٪ و٦٠٪ و٩٢٪، كلٌّ بطابع بإطار `v[0]`؛ المدخلان `runPrimeSieveTest` و`runSha256Test` (يُحفظان للاختبار الذاتي) بنسختين تُرجعان حالة: `runSieveKnownAnswer` و`runSha256KnownAnswer`، والمرجعان `reference_values.h` (664,579 و`SHA256_REF_HASH`). **لكل سبب فشل SHA حالة** (`self_test_failed` · `alloc_failed` · `mismatch`)، **والعدد الخامّ (`sievePrimeCount`) يُشحن**، **ولا منطقيّ مجمَّع ولا «passed»**، ولا يعود `cpuPrimeSieveCorrect` ولا `cpuFmaErrors`. تعمل الفحوص على `Dispatchers.IO` (حمل الذكاء يشغل خيوط Default)، وتُلغى وتُنتظَر عند انتهاء المرحلة. **أسماء `cpuKnownAnswerState` و`cpuKnownAnswerRuns[]` معتمدة** (المستخدم 2026-10-08)؛ **حقول العنصر وقيم الحالة مقترح المنسّق كما هو، لم أغيّر منها شيئاً**؛ المضاف [م]: `native_call_failed:<نصّ>` و`native_result_unreadable:size=<n>` و`native_status_unknown:<n>` وأسباب الحالة `no_check_reached` و`ai_cpu_workload_path_not_selected` و`phase_not_reached`. **شرط «لا يمسّ الحمل المقيس» لم يُثبَت** — الأثر **غير مقيس** (حساب المنسّق ≤ 0.17٪ تقدير). **ولم أبنِ مفتاح تعطيل.** طرق المقارنة «قبل/بعد» في فحص الجهاز القادم، وكلفة كلٍّ منها:
  1. **داخل الفحص الواحد (بلا بناء ولا فحص إضافي):** مقارنة عيّنات `v[6]` (الاستخدام) و`v[5]` (التردد) و`v[7]` (القدرة) التي تقع في نافذة كل فحص (`t` إلى `t + sieveDurationMs + sha256DurationMs`) بجارتها. **كلفتها:** تحليل على الخادم أو سكربت؛ **تقيس الإزاحة المباشرة لا «سقف» المعالج المستدام**، ونافذة الفحص (≈ ٠٫١–٠٫٣ ث، **من تعليقات الكود لا قياس**) قصيرة أمام ٤ Hz (٠٫٢٥ ث) فقد تفوتها العيّنات.
  2. **مقارنة بفحصٍ سابق للجهاز نفسه** (S10 على 1.0.10 قبل التحديث، وهو مقرَّر أصلاً لاختبار الملفّ القديم): **كلفتها صفر فحص إضافي**، لكنها **تخلط** D بكل تغيير آخر في fv68 — **ومنها نواة الذاكرة التي تسبق المرحلة ٣ وتغيّر حرارة بدايتها** — فلا تعزل D؛ تصلح حدّاً أعلى فقط.
  3. **بناءان يختلفان بـD وحدها** (ثابت بناء يُعلَن في الحمولة بحالة `cpuKnownAnswerState = not_run:disabled_in_build`، لا مفتاح تشغيل): **بناء إضافي واحد** وفحوص متعدّدة لكل بناء مع تبريد بينها لفصل الضجيج (ثمانية دقائق لكل فحص + تبريد بالساعات؛ **غير مقيس**)؛ **هي الوحيدة التي تعزل D** وتطابق «قبل وبعد» حرفياً. **اقتراحي:** (١) أوّلاً لأنها مجّانية، و(٣) إن أراد المستخدم الإثبات الحرفي أو أظهرت (١) أثراً.
* **الملفّ القديم (القرار ٦ في الملحق ١ + الملحق ٢) — منفَّذ ومثبَت باختبار على عيّنة ذهبية fv67.** **التشخيص:** الحمولة تأخذ الختم من **تذييل الملفّ** (`BehaviorFingerprintBuilder.kt` ‏:276 `fieldsVersion = f.fieldsVersion.takeIf { it > 0 }`) والإصدار من **ترويسته** (:193 `appVersion = h.appVersion`؛ و`AiAnalysisRequest.kt:87` يعلن الحقل فقط)؛ و`BuildConfig.FIELDS_VERSION` في `app/src/main` عند `StressTestService.kt` وحده (كتابة الملفّ: :1762 عند `8939f7d`، :1766 عند `73e494c`)؛ و`BuildConfig.VERSION_NAME` عند `StressTestService.kt:756` و`:787` (كيان الفحص وترويسة الملفّ لحظة الفحص) و`ExportDataUseCase.kt:87` (غلاف `metadata.json` في الأرشيف المُصدَّر — ملفّات `.ts.json.gz` تُنسخ حرفاً) وشاشتا «حول». **أي: الحال يوافق القرار** (لا موضع يضع ختم البناء أو إصداره على بيانات ملفّ قديم)، ويثبّته `LegacyFv67FileTest` و`BuildStampSitesTest`. **ما وُجد أثناء المسح (يخالف وصف المشكلة في الأمر ويضيف إليه):** (أ) **السقوط عند `throttlingEvents.map` لا يتكرّر بملفّ fv67** — الملفّ نفسه يحمله دائماً؛ ويتكرّر بملفّ ينقصه المفتاح (أقدم) وهو **المفتاح الوحيد من مفاتيح التذييل** الذي أسقط غيابه البانيَ؛ وغياب ترويسة أو متغيّرات أو عيّنات أو تذييل كاملاً أسقطه أيضاً. (ب) **ثغرة أكبر بلا غياب أي مفتاح:** Gson يبني `VerificationSummary` بمُنشئه الخالي فتبلغ الخادمَ حقولُ fv68 **بقيم Kotlin الافتراضية** ادّعاءاتٍ كاذبة لفحصٍ جرى (`not_run:no_v2_result`، و`criticalDefectInputsNotRun: [gpuComputeGemm]` بجانب `criticalDefectDetected` من الملفّ نفسه، و`storageThroughputSegmentsUnit: MiB/s` على ملفّ 1.0.8-v6beta ما قبل A70). **الإصلاح:** غياب يبقى غياباً — مفاتيح `FileShape` و`VerificationWireNames.SINCE_68` تُحذف من حمولة ملفّ بختم < 68 وتُسمّى في **`footer.fieldsNotInFile[]` [م]** (قاعدة 25: القارئ يعلن ما طلبه ولم يجده)، والقارئ يرفض الملفّ الناقص أحد أجزائه الأربعة (فيصل إلى نسخة التذييل الاحتياطية ثم `no_file`). **الاختبار:** `derived/fv67-golden-1.0.8-v6beta-from-neuralcheck-backend-19fc7bc-34dff2b8.ts.json` نسخة مطابقة بايتاً لـ`neuralcheck-backend@19fc7bc:functions/test/fixtures/golden/34dff2b8.ts.json` (sha256 `e451306b31e74b6c77d6513989341cfe7a5f0408ed2119bcc085bf251b1720d3`، أُعيد حسابه بعد النسخ وفي الالتزام نفسه)؛ **موسوم «ينتظر الفحص الحقيقي»**؛ فروقه عن كاتب `d87f2d9` وكل تعديل صنعه الاختبار في رأس الاختبار. **ما لم يُتحقَّق:** هل هو ملفّ الجهاز نفسه أم نسخة الخادم من `timeSeries` (مفاتيحه مرتّبة أبجدياً وتذييله بلا `phaseBoundaries`)؛ و`samplesSha256` فيه لا يطابق العيّنات (استُبدلت في الخادم) **والقارئ لا يتحقّق منه — يمرّره حرفياً**. **والفحص الحقيقي** في فحص الجهاز القادم: S10 على 1.0.10 ⟵ فحص لا يُحلَّل ⟵ تحديث إلى 1.0.11 دون حذف ⟵ إعادة التحليل. **قيد:** حقل أوّليّ ينقص ملفّاً أقدم من fv65 (`intensityFactor` و`tempRiseRateInProbe`) يعود من Gson صفراً — لا يُكتشف بعد التحليل؛ لم يُصلَح.
* **ت-18 صفّ 10 — منفَّذ** (قول المستخدم: «الدورات وعدّاد الشحن والطاقة تُشحن كما قُرئت، والصفر معها، بحالة معلنة. والخادم يحكم»): `cycleCountState` و`chargeCounterUahState` و`energyCounterNwhState` = `read` | `not_read:<سبب>` [م] (`unsupported_by_device` للقيمة الحارسة `MIN_VALUE` · `call_failed:<نصّ>` · `no_battery_manager` · `sysfs_not_readable_or_absent_and_api_below_34` · `no_sysfs_reader_and_api_below_34`). مسّ `BatteryReader.snapshotHealth` (كان `> 0` و`!= 0L`).
* **`batteryTempRiseC` و`levelDropPct` — منفَّذان** (قول المستخدم: «المفقودة تغيب بحالة معلنة، لا 0.0. وlevelDropPct يُشحن بإشارته بلا قصّ»): `batteryTempRiseCState` و`levelDropPctState` (`computed` | `not_computed:<سبب>` [م]) وغياب `batteryTempStartC` و`batteryTempMaxC` حين لم تُقرأ (لا يدخل NaN الخريطة فيسقط Gson)، والخدمة تمرّر NaN لدرجة لم تُقرأ. **وبقي ولم يُحسم:** قصّ الفرق **السالب** في الحرارة إلى صفر. **ونسبة التصميم** `dischargeVsDesignRatioMah` تتبع الفرق المُشار (سالبة لبطارية شُحنت) — مجمَّع انتقالي.
* **`PreScanWarningsHolder` — منفَّذ:** `not_written` لـ`preScanWarningsState` (N-14)؛ الحاوية تتذكّر هل نودي `set` (والشاشة تناديه بقائمة فارغة عند فحص نظيف).
* **النصّ التركي — منفَّذ:** `scan_permission_label_activity_recognition` = «Fiziksel aktivite» في `values-tr`. **والعربي «النشاط البدني» معتمد.** **الإنجليزي: «Physical activity» — لا يُنشأ `values-en`** (يغيّر لغة الواجهة على أجهزة اللغة الإنجليزية)؛ **يُسجَّل هنا إلى أن تُضاف الإنجليزية كاملةً إلى التطبيق** (قول المستخدم 2026-10-08).

#### الأسماء: ما أُقِرّ بنصّ المستخدم 2026-10-08 وما بقي [م]

* **أُقرّت كلّها (قول المستخدم، 2026-10-08):** «الأسماء الجديدة كلّها معتمدة كما اقترحها الوكيل: حالات قوائم الحرارة، وأسباب «لم يجرِ»، ومدخلات transitionalAggregates، وحالات مؤشّرات البطارية، وtimeSeriesState، ومفاتيح L-02، وreadings[] لـSN-08/09.» — أي بنود قائمة §٧-ب التسعة (1 حالات القوائم · 2 أسباب `not_run` · 3 `transitionalAggregates` · 4 مفاتيح مؤشّرات البطارية · 5 `timeSeriesState` · 6 `cpuTempFinalSource` بعد `absent:` · 7 مفاتيح L-02 · 8 `windowMs`/`readings[]` · 9 نصّ الواجهة الجديد).
* **`cpuKnownAnswerState` و`cpuKnownAnswerRuns[]`:** معتمدان — يُرفع عنهما وسم [م] (قول المستخدم: «معتمدان. ولا يُعاد cpuPrimeSieveCorrect ولا cpuFmaErrors بدلالة جديدة»). **حقول العنصر وقيم الحالة: مقترح المنسّق كما هو (لم أغيّره).**
* **أسماء N-09 وN-10 المقرَّة** استُعملت كما هي: `storageVerifyState` · `storageVerifyBytesCompared` · `storageVerifyDurationMs` · `storageVerifyMethod` · `memoryVerifyState` · `memoryVerifyBytes` · `memoryMarchErrors` · `memoryWalkingOnesErrors`؛ و`memoryRandomVerifyErrors` اسم الخادم المنقول.
* **[م] في هذا الجزء (تنتظر الإقرار):** `memoryVerifyBufferBytes` · `memoryVerifyTimeCapMs` · `memoryVerifyDurationMs` · `memoryPhaseWindows[]` (و`PhaseBoundary.windows`، والاسمان `memory_kernel` و`memory_workload`) · `storageVerifyBytesPlanned` · `fieldsNotInFile[]` · مجموعة حالات `partial:<سبب>` (`time_cap_reached` · `cancelled` · `read_failed:<errno>`) · أسباب `not_run:` الجديدة (`sysinfo_failed` · `allocation_failed` · `no_time_left` · `write_failed` · `file_smaller_than_256MiB` · `pattern_unavailable` · `read_open_failed` · `read_failed` · `native_call_failed` · `native_result_unreadable` · `native_status_unknown` · `invalid_argument` · `phase_not_reached`) · قيم `storageVerifyMethod` (`o_direct_sequential` · `dontneed_sequential:<…>`) · حالات القراءات الثلاث وأسبابها · `batteryTempRiseCState` و`levelDropPctState` وأسبابهما · الثوابت: سقف النواة ٣٠ ث وبلاطة ٢٥٦ MiB وحدّ القراءة الأدنى ٢ ث وهامشها ٥٠٠ ms.

#### نقاط وجدتها وتخالف الأمر أو العقد (قاعدتا 14 و29)

* **`phaseBoundaries` في الملفّ وحده:** نصّ الحسم «ونافذتها معلنة في phaseBoundaries» (س-30، العقد `:1013`) لا يبلغ الخادم وحده، لأن `TimeSeriesFooterDto` لا يحمل `phaseBoundaries` (§١.٧، أ-٥). نفّذتُ الإعلان فيه **وفي** `verificationResults.memoryPhaseWindows`. **القرار للمنسّق/المستخدم:** إضافة `phaseBoundaries` إلى الحمولة (تغيير شكل — قاعدة 25، وكل قارئ).
* **الاقتباسان 1 و2 في الأمر مطابقان للعقد** (`:627` و`:841` و`:912` للأوّل — وفي `:841` و`:912` ذيل «معتمد، داخل س-11» الذي يخلو منه `:627`؛ و`:845` و`:1012` للثاني، واقتراح المنسّق `:1013`). لم أجد فرقاً في المعنى.
* **السقوط المذكور في بند د** لا يتكرّر بملفّ fv67 (انظر بطاقة الملفّ القديم)، وما وجدتُه أوسع منه.

#### القيود المعروفة غير المُصلَحة (من هذا الجزء)

* **جهاز يبلغ كتابة التخزين موعد الـ٧٥ ث لا يُقرأ ملفّه** (`not_run:no_time_left`)؛ وحجز نافذة للقراءة قرار المستخدم.
* **`fsync` الفاشل في حلقة كتابة لم تُقرأ راجعةً لا يُشحن.**
* **قصّ فرق الحرارة السالب إلى صفر** باقٍ (`batteryTempRiseC`).
* **حقل أوّليّ ينقص ملفّاً أقدم من fv65** يعود صفراً من Gson.
* **قارئات الخادم** لكل ما ورد هنا (`neuralcheck-backend`، خارج المستودع): لا قارئ للمفاتيح الجديدة ولا لـ`fieldsNotInFile`؛ وشرط س-27 (استثناء الفهارس الفاشلة) بلا تنفيذ؛ وقرار المستخدم «`vr.cpuFmaErrors || 0` يُستبدل بقراءة الغياب غياباً، في الدفعة ٤» على الخادم.
* **قيود §٧-ب الباقية:** قارئات فهرس ثابت (محميّة باختبار)، حرّاس `v[1..10]`، حارس الحاكم والمجمِّع وتثبيت `readThermalHeadroom`، والقارئات التي تبتلع أخطاءها (قاعدة 21)، وسباق `DisciplinedSampler.restart()`، وتحليل مصفوفة نتيجة NEON بتخطيطات أقدم، ورأس `readFooterBackup`. **الذي حُلّ منها في هذا الجزء:** صفّ 10، و`batteryTempRiseC`/`levelDropPct`، والحالة الثالثة لـ`PreScanWarningsHolder`، وملاحظة Gson (§٧-ب: «لوحظ ولم يُغيَّر»).
* **القاعدة 41:** `DisciplinedSampler` و`StressTestService` الحقيقيان لم يعملا؛ والمنتِجات الثلاثة تمرّ بمحاكاة لطبقة JNI في JVM (قاعدة 37)؛ والعيّنة الذهبية للحمولة (§٣ بند 7) لم تُولَّد — الجزء ٣.

#### للجهاز (لا يُقاس إلا في فحص الجهاز القادم)

* **الذاكرة:** زمن النواة على الأجهزة، وبلوغها سقف ٣٠ ث على ٨–١٦ GB، وهل تقتل أندرويد التطبيق بذاكرة تبلغ ٨ GiB، وأثر نافذة النواة على `v[9]` وعلى حرارة المرحلة والقدرة.
* **التخزين:** كلفة الختم على `v[8]`، وزمن القراءة الراجعة، وأيّ الأجهزة ترفض `O_DIRECT` أو تقبله وتخدم من الذاكرة المخبّأة، وهل ينتهي وقت أي جهاز قبل القراءة.
* **D:** كلفة الفحوص على الحمل المقيس (مقارنة قبل/بعد — الطرق الثلاث أعلاه)، ومدّة الغربال وSHA تحت الحمل.
* **الملفّ القديم:** S10 على 1.0.10 ⟵ فحص لا يُحلَّل ⟵ 1.0.11 فوقه ⟵ إعادة التحليل.
* **الجدولة:** أن ثلاثة فحوص D تقع فعلاً عند ٢٥٪ و٦٠٪ و٩٢٪ على جهاز حقيقي، وأن `AiCpuWorkload` لا يتأثّر.

**ما بقي من الدفعة ٣** (أجزاء لاحقة): العيّنة الذهبية لحمولة التطبيق وختم fv68 مكتملاً (الجزء ٣) · SN-04 وشبكة اللمس والحالات الأربع و«انتظر» (الدفعة ١٠) · قرّاء الخادم (الدفعة ٤) · الإنجليزية الكاملة.

### ٧-د — الجزء ٢-ج (قرارات المستخدم على تقرير ٢-ب، 2026-10-08 +03)

مصدر الأعداد: ملفّات XML لمخرَج `cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest` بعد كل التزام (بسطر Java المرفقة مع Android Studio داخل الأمر وحده). الأساس `a94c40c` (على GitHub): 127 ملفّاً · 1039 اختباراً. **الختم 68 كما هو؛ fv68 غير مكتمل بالتصميم حتى آخر التزام من الدفعة ٣؛ ولا APK.**

| الالتزام | ما غيّره | ملفّات XML · اختبارات (كلّها 0 فشل · 0 خطأ · 0 تخطٍّ) |
|---|---|---|
| `ef9610f` | البند 3: ارتفاع حرارة البطارية السالب بإشارته بلا قصّ (`BatteryIndicators`) | 127 · 1040 |
| `55c0952` | البند 4: `storageWriteVerifyErrors` يُشحن متى جرت الكتابة | 127 · 1042 |
| `d2d2867` | البند 6: نهاية كل تشغيل لـD (`tEnd`) ومجموع زمنه في كل مرحلة | 127 · 1049 |

**القرارات الثمانية (نصّها من `PLAN-01-ROADMAP.md`، منقول في أمر المنسّق):**

1. **الذاكرة: حُسم بالقراءة (ب) — §٧-هـ.** (النصّ أدناه، وبعده القراءتان كما عُرضتا قبل القرار.) «الذاكرة: إن كانت الذاكرة الحرّة أقلّ من الحدّ الأدنى للمخزن، لا يجري الاختبار ويُعلن not_run:insufficient_free_memory. والباقي كما هو، ويُقاس في فحص الجهاز. ولا تحرير للقطع بعد اختبارها. معتمد.» — **القراءتان الممكنتان لـ«الحدّ الأدنى للمخزن»** (`memory_verify.cpp`: `available_safe = free − 500 MiB`، `target = min(total × ramPercent، available_safe)`، ثم سقف 8 GiB، ثم أرضيّة 512 MiB): **(أ)** الحرّة < 512 MiB؛ **(ب)** `available_safe` < 512 MiB أي الحرّة < 1012 MiB. **كان البند معلَّقاً حتى اختار المستخدم (ب)** (نصّ قراره في §٧-هـ). (وما نصّه «لا تحرير للقطع بعد اختبارها» هو سلوك النواة اليوم: كل بلاطة تبقى مربوطة حتى النهاية — قاعدة اختيار الصفحات الفيزيائية المختلفة في `memory_verify.cpp`.)
2. **التخزين:** «not_run:no_time_left يبقى، ويُعدّ في فحص الجهاز كم جهازاً يقع فيه. ولا وقت محجوز للقراءة. معتمد.» — **لا تغيير كود** (يُعدّ في فحص الجهاز: عدد الأجهزة التي تبلغ كتابتها موعد الـ75 ث).
3. **«ارتفاع حرارة البطارية السالب يُشحن بإشارته بلا قصّ (BatteryIndicators.kt:202). معتمد.»** — منفَّذ (`ef9610f`): `.coerceAtLeast(0.0)` أُزيل؛ الاختبار الذي ثبّت القصّ استُبدل (قاعدة 23) بآخر يثبّت `-2.0` لبداية 30 وأقصى 28.
4. **«أخطاء الكتابة (ومنها fsync) تُشحن متى جرت الكتابة، جرت القراءة أم لا. معتمد.»** — منفَّذ (`55c0952`): الدالّة `StorageWriteErrors.count(writeAttempted, failedSyncs, writeExceptions)` = `null` إن لم تجرِ كتابة، وإلّا (مزامنات فاشلة + كتابة رمت استثناءً). وإن لم تجرِ كتابة غاب الحقل و`storageVerifyState` يقول لماذا (`write_failed:<نصّ>` عند فشل فتح الملفّ، `cancelled`، `phase_not_reached`). الاختبار الذي ثبّت غيابه مع كل قراءة لم تُنفَّذ استُبدل.
5. **«لا phaseBoundaries في الحمولة الآن، ويكفي memoryPhaseWindows. معتمد.»** — **لا تغيير كود**: `footer.phaseBoundaries` يبقى في الملفّ وحده (§١.٧)، و`verificationResults.memoryPhaseWindows` هو ما يبلغ الخادم؛ فيُحسم بهذا ما فُتح في §٧-ج («القرار للمنسّق/المستخدم»).
6. **«إثبات أن D لا يمسّ الحمل المقيس، بدل (أ) و(ب) و(ج): يُشحن خامّاً زمن كل تشغيل لـD (بداية ونهاية بالمللي ثانية)، ومجموعه في كل مرحلة. والقبول: مجموع زمن D في المرحلة ≤ 1% من زمنها. فلا يتجاوز أثره على السقف المستدام ذلك. ويُقاس في فحص الجهاز على الـS10 والـS21. فإن تجاوز 1% على أيّ منهما، يُعرض عليّ قبل التسليم.»** — منفَّذ شحنُ الخام (`d2d2867`)؛ **ويحلّ محلّ طرق المقارنة الثلاث في §٧-ج** (داخل الفحص، بفحص سابق، ببناءين). التفصيل أدناه.
7. **«الأسماء الجديدة [م] من ٢-ب معتمدة كما هي، ومنها الثابتان (30 ثانية، و256 MiB).»** — **تُوسَم كلّها «مُقرّة 2026-10-08» بهذا النصّ:** القائمة [م] في §٧-ج («الأسماء: ما أُقِرّ… وما بقي [م]»، بنده الأخير): `memoryVerifyBufferBytes` · `memoryVerifyTimeCapMs` · `memoryVerifyDurationMs` · `memoryPhaseWindows[]` (و`PhaseBoundary.windows`، `memory_kernel`/`memory_workload`) · `storageVerifyBytesPlanned` · `fieldsNotInFile[]` · حالات `partial:*` وأسباب `not_run:` الجديدة · قيم `storageVerifyMethod` · حالات القراءات الثلاث وأسبابها · `batteryTempRiseCState` و`levelDropPctState` وأسبابهما · الثوابت: سقف النواة **30 ث** وبلاطة **256 MiB** وحدّ القراءة الأدنى 2 ث وهامشها 500 ms. (واستُثنيت من هذا القرار الأسماء الجديدة في ٢-ج أدناه — لم تُعرض بعد.)
8. **«فحص C++ بـ -fsyntax-only داخل الإذن من الآن.»** — لا أثر على الكود.

#### البند 6 — زمن D
* **`cpuKnownAnswerRuns[].tEnd` [م]:** ms منذ بدء الفحص بإطار `v[0]`، لحظة **نهاية** التشغيل؛ والبداية هي `t` الموجود (`CpuKnownAnswerScheduler`). **التشغيل من بدايته إلى نهايته شاملاً الغربال وSHA ونداءات JNI بينهما** — لا `sieveDurationMs` و`sha256DurationMs` وحدهما (يبقيان). والتشغيل الذي قطعته المرحلة نهايته حيث توقّف (نداء الدالّة لا يُقطع)؛ **والتشغيل `not_reached` بلا `t` ولا `tEnd`: غياب لا صفر.**
* **`verificationResults.cpuKnownAnswerTotalMsByPhase` [م]:** `{ "3": ms, "5": ms }` = مجموع (`tEnd − t`) لتشغيلات كل مرحلة التي لها الطرفان؛ ومرحلة لم يبدأ فيها تشغيل لا مدخل لها. **مجمَّع في التطبيق من خام مشحون ← يُدرَج اسمه في `transitionalAggregates` (`verificationResults.cpuKnownAnswerTotalMsByPhase`) — وهذا الإدراج إضافة من المنسّق لا من نصّ القرار.** ويُحذف من حمولة ملفّ بختم < 68 (`VerificationWireNames.SINCE_68`).
* **«القبول ≤ 1%» لا يُحسب في التطبيق** (لا حقل نسبة ولا حكم؛ اختبار يثبّت ذلك). الحكم للخادم ولفحص الجهاز.
* **زمن المرحلة يُشتقّ من العيّنات (`phase` و`ts`/`v[0]`)، ولا حقل له.** هل تكفي العيّنات؟ **نعم لمعيار 1%**، قياساً على العيّنة الذهبية fv67 الوحيدة المتاحة (`derived/fv67-golden-…-34dff2b8.ts.json`، S10 5G على 1.0.8-v6beta): امتدّ `v[0]` للعيّنات الموسومة بالمرحلة 3 على **74.978 ث** (اسمياً 75) وللمرحلة 5 على **59.750 ث** (اسمياً 60)، بفاصل وسيط 250 ms؛ و`ts` و`v[0]` يعطيان الامتداد نفسه؛ و`v[0]` رتيب. فزمن المرحلة يُستعاد بخطأ نحو 0.3 ث من 60–75 ث، وهو أدقّ مما يحتاجه معيار 1% (0.6–0.75 ث). **التحفّظ:** الوسم يُضبط باستطلاع 500 ms فقد تحمل عيّنتان عند كل حدّ وسم المرحلة الجارة (مئات ms لا نسبة)؛ والنوافذ الدقيقة (`phaseBoundaries`) في الملفّ وحده؛ والمقياس عيّنة واحدة من جهاز واحد (في تلك العيّنة نفسها امتدّ وسم المرحلة 1 على 104.9 ث لأن العيّنة سابقة لإصلاح A46 — وصف مرحلة لا تعني D).
* **حسابياً، لا قياساً** (من تعليقات الكود: غربال ≈ 90 ms في الخمول و50–250 ms نموذجياً، وSHA 1 MiB مفرد الخيط 15–40 ms): 3 فحوص لكل مرحلة ≈ 0.2–0.9 ث، أي **0.3–1.2٪ من المرحلة 3 (75 ث) و0.3–1.5٪ من المرحلة 5 (60 ث)**، والغربال تحت حمل كل الأنوية قد يكون أبطأ من رقم الخمول. **فمعيار 1% ليس بعيداً عن الطرف البطيء، وفحص الجهاز هو الذي يقرّر.**
* **للجهاز:** مجموع زمن D في كل مرحلة ÷ زمنها على **S10 وS21** (اللذين سمّاهما المستخدم)؛ **وتجاوز 1% على أيّ منهما يُعرض على المستخدم قبل التسليم.**

#### حالة البنود المعروفة بعد ٢-ج
* **حُلّ:** قصّ فرق الحرارة السالب (البند 3) · فشل `fsync` الذي لا يُشحن (البند 4) · «`phaseBoundaries` في الحمولة» (البند 5) · جهاز تبلغ كتابته الموعد (البند 2: يبقى `no_time_left` ويُعدّ).
* **حُسم لاحقاً:** البند 1 (الذاكرة) بالقراءة (ب) — §٧-هـ.
* **باقٍ:** كل قيود §٧-ج الأخرى (حقل أوّليّ ينقص ملفّاً أقدم من fv65، قارئات الخادم، القاعدة 41 على الجهاز).
* **الأسماء الجديدة في ٢-ج — مُقرّة 2026-10-08** بنصّ المستخدم: «cpuKnownAnswerRuns[].tEnd وcpuKnownAnswerTotalMsByPhase (خريطة {"3": ms, "5": ms}) معتمدان، وإدراج المجموع في transitionalAggregates معتمد.» أي: `tEnd` (عنصر `cpuKnownAnswerRuns[]`) · `cpuKnownAnswerTotalMsByPhase` · وإدراج اسمه في `transitionalAggregates` (كان إضافة المنسّق). **ومعيار D ≤ 1٪ يُحسم في فحص الجهاز؛ لا عمل فيه الآن** (قول المستخدم).

### ٧-هـ — شرط الذاكرة الحرّة (البند «أ» المعلَّق من ٢-ج، 2026-10-08 +03)

| الالتزام | ما غيّره | ملفّات XML · اختبارات (0 فشل · 0 خطأ · 0 تخطٍّ) |
|---|---|---|
| `34466e4` | الشرط في `memory_verify.h`/`.cpp` · الحالة الجديدة في JNI (تخطيط الإصدار 2) · الترجمة والسلك في Kotlin · اختبار التصنيف الكامل · حارس المصدر | 128 · 1059 |

**قرار المستخدم بنصّه (من `PLAN-01-ROADMAP.md`، منقول في أمر المنسّق):**
> «الذاكرة: القراءة (ب). لا يجري الاختبار إن كانت الحرّة − 500 MiB < 512 MiB، أي الحرّة < 1012 MiB، فيُعلن not_run:insufficient_free_memory. ويُكتب الشرط في الكود والعقد كما يُحسب، لا بصياغة نصّية.»

**الشرط كما يُحسب** (`app/src/main/cpp/stress/memory_verify.h` — مصدر واحد لكل ثابت، قاعدة 24؛ لا نسخة منهما في Kotlin):

```
kMemoryVerifyReservedBytes = 500 MiB      // ما يُترك للنظام
kMemoryVerifyFloorBytes    = 512 MiB      // أصغر مخزن يجري عليه الاختبار

available_safe(free)  = free > 500 MiB ? free - 500 MiB : 0
insufficient(free)    = available_safe(free) < 512 MiB
                      ⟺ free < 1012 MiB
```

* `free` = `freeram × mem_unit` من `sysinfo()` بالبايت.
* **يُقيَّم بعد نجاح `sysinfo` وقبل أي `mmap`**؛ وإن تحقّق فالحالة الرمز الجديد `kMemoryVerifyInsufficientFreeMemory` (=6) ولا يُحجز شيء، وفي `memoryVerifyState`: **`not_run:insufficient_free_memory`** (لا `errno`: لا نداء نظام فشل).
* **ولم يتغيّر غيره:** السقف 8 GiB، والأرضيّة 512 MiB، والنسبة (`percent_ram`: 1..80٪ من الذاكرة الكلّية)، وإعادة المحاولة بنصف الحجم عند فشل `mmap`، ولا تحرير للقطع بعد اختبارها (تبقى مربوطة حتى النهاية كي تأتي كل بلاطة بصفحات فيزيائية مختلفة).
* **الحالة التي نبّه إليها الأمر:** إن كان `available_safe ≥ 512 MiB` و`target_from_total < 512 MiB`، فالأرضيّة ترفع المخزن إلى 512 MiB، وهو ضمن `available_safe` (لأن `available_safe ≥ 512 MiB` بشرط الدخول)؛ **فهذه الحالة تجري كما اليوم.** لم أجد في الكود ما يخالف ذلك.
* **الحدّ يُثبته المترجم لا اختبار JVM** (لا يُشغَّل C++ في JVM): `static_assert` في الترويسة لـ`free` = 0 و500 MiB و1011 MiB (غير كافٍ) و1012 MiB و8 GiB (يجري). وضابط سلبيّ (تأكيد خاطئ عمداً في ملفّ مؤقّت) **يفشل ترجمةً**، فالتأكيدات حيّة لا فارغة.
* **حارس مصدر** (`MemoryFloorSourceTest`، يقرأ الترويسة نصّاً): الثابتان 500 و512 MiB معرَّفان مرّة واحدة؛ الشرط هو الحساب أعلاه؛ مُستعمَل قبل أول `mmap`؛ لا نسخة من الرقمين في `memory_verify.cpp` ولا في `MemoryVerifyOutcome.kt`/`NativeVerification.kt`.

**الخام (قاعدة 20):** `verificationResults.memoryVerifyFreeBytes` [مُقرّ 2026-10-08] = الذاكرة الحرّة كما قرأها `sysinfo`، بالبايت، **تُشحن متى نجح `sysinfo` جرى الاختبار أم لا** (وكذلك مع `partial` و`allocation_failed`)، **وتغيب إن فشل `sysinfo`** (العلامة الداخلية «لم تُقرأ» في مصفوفة JNI هي `-1` ولا تبلغ الحمولة رقماً). **`available_safe` لا يُشحن** — مشتقّ من الخام والثابت.

**JNI:** تخطيط `runMemoryVerify` صار **الإصدار 2** بـ14 خانة (`[13]` = الحرّة)؛ وكان الإصدار 1 بـ13. القارئ يقبل الإصدار 2 بطول 14 وحده، ومصفوفة الإصدار 1 «غير مقروءة» (اختبار)، لا تُقرأ بخانة ناقصة.

**Kotlin:** `MemoryVerifyLayout.STATUS_INSUFFICIENT_FREE_MEMORY` ← `not_run:insufficient_free_memory`. **اختبار التصنيف يمرّ بكل رموز الحالة** (`MemoryVerifyLayout.ALL_STATUSES`، 7 رموز) ويفشل إن أُضيف رمز بلا حالة (قاعدة 30)، ورمز خارج القائمة `native_status_unknown`. **والسلك:** الحالة و`memoryVerifyFreeBytes` تصلان الحمولة (كاتب حقيقي ← قارئ حقيقي ← بانٍ ← Gson)؛ **وفي ملفّ fv67** يغيبان ويُسمَّيان في `fieldsNotInFile` (`verificationResults.memoryVerifyFreeBytes` و`…memoryVerifyState`) — لأن `VerificationWireNames.SINCE_68` صار يعدّ المفتاح.

**الاسم `memoryVerifyFreeBytes` — مُقرّ 2026-10-08** (كان [م]: اقتراح المنسّق، لم يُقرّ بعد). قرار المستخدم بنصّه (من `PLAN-01-ROADMAP.md`، منقول في أمر الجزء ٣ — غير متحقَّق من هذا المستودع): «memoryVerifyFreeBytes معتمد: الحرّة من sysinfo بالبايت، تُشحن كلّما نجحت القراءة، جرى الاختبار أم لم يجرِ. والحدّ الآمن لا يُشحن.»

**ترجمة C++:** المهمّة `:app:externalNativeBuildLaunchDebug` (بسطر Java المعتمد) — `BUILD SUCCESSFUL in 13s`، `libstress.so` 1,256,560 بايت؛ والفحص النحويّ `clang++ --target=aarch64-linux-android28 -std=c++17 -fsyntax-only -Wall -Wextra` على `memory_verify.cpp` و`stress_jni.cpp`: بلا مخرَج. وقائمة `app/build/outputs/apk` مطابقة قبل وبعد (لا APK).

**للجهاز:** الشرط نفسه يُثبَت على الجهاز (لا C++ في JVM): **هل يقع أيّ جهاز في الحالة؟** وكم كانت الحرّة (`memoryVerifyFreeBytes`) عنده — قيمة حقيقية لا تقدير. وعلى الأجهزة التي تجري: الحرّة وقت الاختبار وما اختاره الشرط من حجم (`memoryVerifyBufferBytes`).

### ٧-و — الجزء ٣ (العيّنة الذهبية لحمولة التطبيق · حارس القاعدة 39 · إعلان fv68، 2026-10-08 +03)

| الالتزام | ما غيّره | ملفّات XML · اختبارات (0 فشل · 0 خطأ · 0 تخطٍّ) |
|---|---|---|
| `fb82136` | استخراج التسلسل: `AnalysisRequestSerializer` (`gson` و`fingerprintToMap`) تستعمله `FirebaseAnalysisService` والمولِّد والاختبارات؛ واختبارات كانت تنسخ `GsonBuilder().create()` صارت تأخذه منها | 128 · 1059 |
| `acb773d` | المولِّد `AppRequestPayloadGolden.kt` · العيّنات الثلاث ومفاتيحها وقسم README · حارس القاعدة 39 `AppRequestPayloadGoldenTest` (17 اختباراً) · `GoldenJson.kt` · `.gitattributes` · `ChannelDiscoveryProbe.CHANNELS` صارت `internal` | 129 · 1076 |
| `2729f95` | الوثيقة وحدها (هذا القسم وتعديلات س-25 و§٣ بند 7 و§٤.٣ وإقرار `memoryVerifyFreeBytes`) | لا كود |
| الأخير (sha في `git log`) | إعلان fv68 مكتملاً: تعليق الختم وكتلة التاريخ في `app/build.gradle.kts` (انتقل سطر الختم من `:384` إلى `:430` لأن الكتلة طالت) ورأس هذه الوثيقة؛ الختم يبقى 68 | في رسالته |

مصدر الأعداد: ملفّات XML لمخرَج `cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest` (سكربت يجمع `tests` و`failures` و`errors` و`skipped`)؛ وقائمة `app/build/outputs/apk` مطابقة قبل وبعد (لا APK).

**قراران بنصّهما (من `PLAN-01-ROADMAP.md`، منقولان في أمر الجزء ٣ — غير متحقَّق منهما من هذا المستودع):**
> «memoryVerifyFreeBytes معتمد: الحرّة من sysinfo بالبايت، تُشحن كلّما نجحت القراءة، جرى الاختبار أم لم يجرِ. والحدّ الآمن لا يُشحن.»
> «الجزء ٣: بما أن الموقع لا يقرأ أيّ حقل من حمولة التطبيق (عيّناته تقارير خادم، وfunctionalResults في app/verify/page.js تعليق لا قراءة)، فالعيّنة الذهبية لحمولة التطبيق تُنسخ إلى الخادم وحده. ويُثبت ذلك بالأداة في تقرير الجزء ٣. وعيّنات الموقع تتحدّث حين يتغيّر شكل تقرير الخادم في الدفعة ٧.»

**السلسلة** (القاعدة 37، دون استبدال أيّ طبقة): `TimeSeriesFileWriter` الحقيقي (`Context` مُحاكى لـ`filesDir` وحده) ← `TimeSeriesFileReader` الحقيقي ← `BehaviorFingerprintBuilder.build` الحقيقي ← `AnalysisRequestSerializer.fingerprintToMap` (`AnalysisRequestSerializer.kt:32` الـGson و`:42-45` التسطيح، تستدعيها `FirebaseAnalysisService.kt:121`). والعيّنة هي قيمة `data.fingerprint` لا `data` كلّها.

**العيّنات الثلاث وسبب كلٍّ** (تخطيط قنوات مختلف يقرؤه الخادم بالاسم في الدفعة ٤؛ مثال العقد 11 و13 و16 — اتّبعته):

| الملفّ (بعد `fv68-app-request-payload-derived-from-fv67-golden-34dff2b8-`) | الأعمدة | السبب | أسطر ملفّ المفاتيح |
|---|---|---|---|
| `11ch-nothing-admitted.json` | 11 | الاكتشاف لم يُدرج قناة: `cpuPolicies` = `[]` (جرى ولم يجد) لا مفتاحاً غائباً، و`channelReadFailures` = `{}` | 277 |
| `13ch-selinux-blocked-policy.json` | 13 | **قناة محجوبة** (سياسة 4، EACCES): الغياب يظهر في `channelDiscovery` (`blockedBySelinux` و`reasons`) و`cpuPolicies[].unreadableReason` لا في `v[]` | 293 |
| `16ch-four-policies-truncated-failures.json` | 16 | أطول تخطيط؛ قائمة فشل مقطوعة عند 200 وعدّها 230؛ فحص مُوقَف (`isComplete` false، 959 من 1606 صفّاً) | 292 |

المفاتيح الموحَّدة: 312 مساراً، المشترك بين الثلاث 266 (مخرَج سكربت التحليل). **sha256:**

| الملفّ | sha256 |
|---|---|
| `…-11ch-nothing-admitted.json` | `58e88edd36c7f9ebcc9941d225e3befb93ea45c78cedf97d653487eabaac4cd0` |
| `…-11ch-nothing-admitted.keys.txt` | `4399e5bf2232ea4d889a8a4567b4af8e47de501102fccee9a72561ebeb02596a` |
| `…-13ch-selinux-blocked-policy.json` | `0076f1eff184b16742f7e22406ed695bb47a9fa006fb5356419ed61fb0b38792` |
| `…-13ch-selinux-blocked-policy.keys.txt` | `4eb5cccae30021eadd3faaf8b5c026b47af79be784ea9afa708e4eae510bf0b0` |
| `…-16ch-four-policies-truncated-failures.json` | `f05a9a602a3dbc185744590b2c5aaa48cb5e3706d04f2d7486638d51eee0aeeb` |
| `…-16ch-four-policies-truncated-failures.keys.txt` | `5b34e9c00f61bf9e35cba92888e9b8035420636884e1a21b439a7c76639d999a` |

**[تحديث 2026-10-08 (ت-02، §٧-ز): sha256 ملفّي 13ch و16ch ومفتاحيهما أعلاه صارت قديمة وحلّت محلّها القيم في §٧-ز؛ و11ch لم يتغيّر بايتاً.]**

**القيم:** الصفوف الخامّ من التسجيل الحقيقي المشتقّ منه (`derived/fv67-golden-…-34dff2b8.ts.json`، sha256 `e451306b…1720d3`)؛ وكل قيمة ليست من منتِجها الحقيقي في قائمة التعديلات في `derived/README.md` (بما فيها: وحدة `v[8]` و`v[9]` من MB/s إلى MiB/s، وتصفير `v[9]` داخل نافذة نواة الذاكرة، و`appVersion`، والأعمدة الخاصّة بكل جهاز). **المنتِجات الحقيقية المستعملة:** `ChannelDiscoveryProbe.assemble` و`CpuPolicyDiscovery.discover` و`ExtraChannelSampler` و`ThermalListCollector` و`BatteryPairRecorder` و`StillnessClassifier` و`BatteryIndicators` و`MemoryVerifyOutcome.fromNative` و`StorageVerifyOutcome.fromNative` و`StorageWriteErrors` و`GpuGemmOutcome` و`CriticalDefectOutcome` و`WorkersPinOutcome` و`CpuKnownAnswerScheduler` (وقت افتراضي) و`StressTestService.selectFooterCpuTemp` و`UnmeasuredDefaults` و`TransitionalAggregates` و`PreScanWarningsStates` و`ThermalSensorAvailability` — على مُدخلات حقيقية أو مصنوعة معلَنة. **ومقياس مستقلّ للسلسلة:** `BatteryIndicators` على الصفوف الحقيقية يعيد أرقام `batteryHealth` التي كتبها الجهاز الحقيقي (اختبار `batteryHealth recomputed from the real rows equals what the real device wrote`).

**مفاتيح التذييل التي يبنيها `completeScan` ويُبنى لها `TimeSeriesFooter` يدوياً هنا** (حدّ المرحلة ١ بنصّ البند 7؛ ما تكشف انجرافه المرحلة ٢): `totalSamples` · `endTimestamp` · `isComplete` · `earlyStopReason` · `batteryLevelEnd` · `maxBatteryTemp` · `thermalHeadroomAvg` · `thermalHeadroomPeak` · `thermalStatusMax` · `tempSource` · `intensityFactor` · `deviceTierMeasured` · `tempRiseRateInProbe` · `batteryCurrentScalingApplied` · `throttlingEvents` · `verificationResults` · `phaseCompletionStatus` · `stressEffectiveness` · `batteryHealth` · `dataQuality` · `knownCapacityClassification` · `footerDiagnostics` · `channelDiscovery` · `thermalSensorAvailability` · `scanStillness` · `unmeasuredDefaults` · `expectedSamples` · `flushFailureCount` · `cpuPolicies` · `channelReadFailures` · `channelReadFailureCounts` · `thermalHeadroomReadings` · `thermalHeadroomState` · `thermalStatusEvents` · `thermalStatusState` · `batteryPairs` · `batteryPairsState` · `transitionalAggregates` · `cpuTempFinalC` · `cpuTempFinalSource` · `preScanWarningsState` · `fieldsVersion` (42 مفتاحاً)؛ وما يخرج من الكاتب والبانٍ لا يدوياً: `samplesSha256` (الكاتب) و`fieldsNotInFile` (البانٍ). وخارج الحمولة (ملفّ فقط) يُسلَّم للكاتب يدوياً: `phaseBoundaries` و`preScanWarnings`. الجدول بمصدر قيمة كل مفتاح في `derived/README.md`، ويفرض اختبارٌ أن يسمّي كل مفتاح تذييل في العيّنات.

**حارس القاعدة 39** (`AppRequestPayloadGoldenTest`): (١) كل عيّنة ملتزَمة تُقارَن بما تبنيه السلسلة اليوم **مساراً بمسار** — أيّ تغيير في شكل أو قيمة يُخفق ويسمّي المسار، وأيّ تغيير لاحق في العقد يُعيد التوليد في التزامه (`NC_REGENERATE_GOLDEN=1`، الأمر في README وتعليق الصنف)؛ (٢) البايتات هي النصّ المعتمد فـsha256 في README هو sha256 النسخة؛ (٣) ملفّ المفاتيح هو مفاتيح عيّنته بالضبط؛ (٤) README يحمل sha256 كل ملفّ؛ (٥) **مفتاح يستطيع البانٍ كتابته (أو حقل DTO يملؤه أو مفتاح خريطة `toWireMap` للفحوص المعرفية) ولا تُظهره عيّنة يُخفق الاختبار** (القاعدة 25) ما لم يُعلَن غيابه بسببه في `declaredAbsent`: `device.buildFingerprint/buildBoard/buildHardware/buildBootloader` (`android.os.Build.*` فارغة في JVM) · `timeSeries.footer.maxCpuTemp` (من ملفّ ≤ 67 فقط) · `phase3CpuFreqRatioMid`/`Little` (دائماً null في المخطّط V5، `StressEffectivenessCalculator.kt:80-82` و`:123-124`)؛ واختبار يفشل إن بقي استثناء لم يعد غائباً؛ (٦) اختبار أن كل كلاس DTO داخل الحمولة مشمول بالحارس؛ (٧) الخدمة تستدعي الدالّة المشتركة ولا تبني Gson ثانياً.
**ضوابط سلبية جرت وعُكست:** مفتاح بقيمة `null` أُضيف إلى البانٍ ← فشل اختبار التغطية مسمّياً `zzGoldenGuardProbe`؛ وبايت واحد غُيِّر في عيّنة ملتزَمة ← فشل اختبار المسار مسمّياً `$.timeSeries.footer.verificationResults.cpuKnownAnswerRuns[0].sievePrimeCount` وفشل اختبار البايتات واختبار sha256 في README. ثمّ عُكس التعديلان والعيّنات مطابقة sha256 لما التزم (`0076f1ef…`).

**الفروق عمّا كُتب في العقد (القاعدة 29):**
1. **النسخ إلى الخادم وحده** (قرار المستخدم أعلاه) — عُدِّل §٣ بند 7 وس-25 و§٤.٣.
2. **«في التزام تغيير العقد (القاعدة 39)»**: العيّنة لم تُولَّد عند كل تغيير شكل في الأجزاء السابقة؛ وُلِّدت في الجزء ٣ بعد انتهاء تغييرات الشكل، لا في التزام يغيّره. فالفخّ الذهبي في هذا المستودع لم يكن موجوداً بين `2afd392` و`acb773d`؛ ومن `acb773d` فصاعداً الحارس يُخفق أيّ تغيير شكل لا يُعيد التوليد. **ولم يُعَد توليد الفخّ في المستودعين الآخرين** (خارج حدودي) — الخادم ينسخ العيّنة بعد التحقّق، والموقع لا يستلمها بقرار المستخدم.
3. **اسم الملفّ:** العقد `fv68-app-request-payload-derived-from-<مصدر>.json`؛ المصدر هنا `fv67-golden-34dff2b8-<معرّف التخطيط>` [م] (المعرّف يميّز التخطيط، فالمصدر الخام واحد)؛ وملفّ المفاتيح `….keys.txt` [م] (طلب الأمر).
4. **`.gitattributes`** جديد (لم يرد في العقد): يمنع تحويل نهايات الأسطر في العيّنات فلا يتغيّر sha256 النسخة عند اختلاف الإعداد.
5. **`ChannelDiscoveryProbe.CHANNELS`** صارت `internal` ليمرّ المولِّد على القائمة الإنتاجية لا على نسخة (القاعدة 24).
6. **ثلاث عيّنات تحمل أيضاً سيناريوهات غير التخطيط** (توقيف مبكّر، حالات التحقّق الجزئي، STUB/NEON، عدّ البثّ يبلغ سقف 200، حالات pre-scan الثلاث، مصادر حرارة المعالج الثلاثة) كي يُظهر الحارس كل مفتاح مشروط؛ وكلّها مصنوعة ومعلَنة في README — فهي ليست «فحصاً حقيقياً واحداً».
7. **حدّ المرحلة (١) كما في العقد:** المفاتيح اليدوية أعلاه لا تُثبت أن `completeScan` يبنيها هكذا؛ **القاعدة 41 غير مستوفاة لها حتى المرحلة (٢).**

**قيود معروفة لم تُصلَح:**
* **`stressEffectiveness.peakBigFreqKhzInStressPhases` و`c4RatioStressWindow` يُكتبان في الملفّ (`V2Models.kt:332,344`؛ وهما في التسجيل الحقيقي) ولا يشحنهما `BehaviorFingerprintBuilder.stressEffectivenessToMap`** — فالعيّنات لا تحملهما (القائمة البيضاء في البانٍ). **لا قرار بشحنهما ولا بإسقاطهما في العقد؛ يحتاج قرار المستخدم** (`c4RatioStressWindow` نسبة محسوبة في الجهاز؛ والخام `peakBigFreqKhzInStressPhases`). **[حُسم 2026-10-08 بقرار المستخدم، §٧-ز: يبقيان في الملفّ وحده، ويُعلن ذلك في العقد. معتمد.]**
* **ت-02 (`EXTRA_CYCLE_COUNT` مصدراً ومفردات `cycleCountSource` `extra_api34` · `sysfs:<path>` · `unavailable`) لم يُنفَّذ في أيّ جزء** (`git grep -n -e EXTRA_CYCLE_COUNT -e extra_api34 -- app/src/main` فارغ، خروجه 1)؛ بطاقته ت-02 تقول «fv68 يغيّر مفردات هذا الحقل»، و§٧-ب سمّاه «خارج الأمر»، ولا أمر لاحق نفّذه. والذي نُفِّذ من ت-02: قبول الصفر مع الحالة (ت-18 صفّ 10). **[حُلّ 2026-10-08: نُفِّذ داخل fv68 بقرار المستخدم — §٧-ز.]**
* **ما حدّده العقد للدفعة ١٠ ولم يدخل fv68:** الحالات الأربع و`manualResultsHeader.hiddenByModel` (N-17) وشبكة اللمس وتغطية الإضاءة و«انتظر» (ت-15) وSN-04 (§٧-د). **[انتقلت إلى fv69، بقرار المستخدم 2026-10-10: «إصدار الحقول: fv69 في أوّل التزام يغيّر الشكل، كما قرّرت.» — §٧-ح. وfv68 يبقى مكتملاً بما نُفِّذ فيه؛ هذه البنود لم تدخله ولن تدخله.]**

**للجهاز (المرحلة ٢ من §٣ بند 7، في جولة الفحص الميداني الواحدة، القاعدة 19):** ملفّ `.ts.json.gz` حقيقي من فحص على بناء fv68 يُنسخ **دون تعديل** إلى `app/src/test/resources/contract/` ويمرّ بالقارئ ← البانٍ ← `AnalysisRequestSerializer`؛ ويُضاف اختبار يقارن مفاتيح عيّنتَي الجهاز والاصطناعية (`*.keys.txt`) — ما في إحداهما دون الأخرى يُخفق. هذا وحده ما يكشف انجراف المفاتيح اليدوية أعلاه عمّا يبنيه `completeScan`، وما يُنظر فيه بالاسم: هل تحمل بثّات البطارية حقيقةً 200 وأيّ عدّ، وهل `cpuTempFinalSource` من الفروع الثلاثة، وهل تظهر `fieldsNotInFile` فارغة في ملفّ fv68 حقيقي.

**أسماء [م] لهذا الجزء (تنتظر إقرار المنسّق):** `AnalysisRequestSerializer` (و`gson`/`fingerprintToMap`) · `NC_REGENERATE_GOLDEN` · معرّفات التخطيط `11ch-nothing-admitted` و`13ch-selinux-blocked-policy` و`16ch-four-policies-truncated-failures` · امتداد `.keys.txt` · `GoldenJson` · الكلمة `declaredAbsent`. **[أُقرّت 2026-10-08 بقرار المستخدم (أمر ت-02): `AnalysisRequestSerializer` و`NC_REGENERATE_GOLDEN` ومعرّفات التخطيط و`.keys.txt` و`GoldenJson` و`declaredAbsent`.]**

### ٧-ز — ت-02 داخل fv68 (عدد الدورات من `EXTRA_CYCLE_COUNT`، 2026-10-08 +03)

| الالتزام | ما غيّره | ملفّات XML · اختبارات (0 فشل · 0 خطأ · 0 تخطٍّ) |
|---|---|---|
| `e7fa26e` | القارئ الواحد `BatteryCycleCount` (التذييل و`B-08`) · حذف `getIntProperty(8)` · مفردات `cycleCountSource` · اختبار التصنيف الكامل · العيّنات الذهبية تُعاد (13ch و16ch) | 130 · 1095 |
| الأخير | الوثائق (هذا القسم، `BATTERY_INDICATORS.md`، `MEASUREMENT_SPEC.md`، رأس العقد) وتعليق الختم؛ الختم يبقى 68 | في رسالته |

**قراران للمستخدم بنصّهما** (من `PLAN-01-ROADMAP.md`، منقولان في أمر ت-02 — غير متحقَّق منهما من هذا المستودع؛ والفجوة `[…]` في نصّ الأمر نفسه):
> «ت-02: يُنفَّذ الآن في fv68، لا يُنقل.  
> - السبب: تشخيصك في المرحلة ٠ يقول إن getIntProperty(8) ليست خاصّية عامّة (javap على android-35)، وإن الدورات unavailable في الستّة ومنها الـS21 (API 34). […]  
> - وعدد الدورات هو المعلومة الصادقة الوحيدة عن البطارية التي يعرضها V8 (الخبير §٦).  
> - التنفيذ: EXTRA_CYCLE_COUNT من بثّ البطارية على API ≥ 34، وcycleCountSource بمفرداته، وغيابه حالة معلنة. ثم تُحدَّث العيّنة الذهبية، وتُنسخ إلى الخادم بالتحقّق من sha256.»
> «peakBigFreqKhzInStressPhases وc4RatioStressWindow يبقيان في الملفّ وحده، ويُعلن ذلك في العقد. معتمد.»

**الإعلان المطلوب:** `stressEffectiveness.peakBigFreqKhzInStressPhases` و`c4RatioStressWindow` يُكتبان في الملفّ (`V2Models.kt`، وهما في التسجيل الحقيقي) ولا يشحنهما `BehaviorFingerprintBuilder.stressEffectivenessToMap` — **بقرار المستخدم، لا سهواً**. وأُقرّت الأسماء الداخلية للجزء ٣: `AnalysisRequestSerializer` و`NC_REGENERATE_GOLDEN` ومعرّفات التخطيط و`.keys.txt` و`GoldenJson` و`declaredAbsent`.

**javap — الأمر ومخرَجه** (القاعدة 16؛ `javap -constants -public -classpath <home>/AppData/Local/Android/Sdk/platforms/android-<N>/android.jar android.os.BatteryManager`، JDK الذي يرافق Android Studio):

| android.jar | `BATTERY_PROPERTY_*` | `EXTRA_CYCLE_COUNT` |
|---|---|---|
| 33 | `CAPACITY=4` `CHARGE_COUNTER=1` `CURRENT_AVERAGE=3` `CURRENT_NOW=2` `ENERGY_COUNTER=5` `STATUS=6` | **غائب** |
| 34 | الستّ نفسها | `"android.os.extra.CYCLE_COUNT"` |
| 35 | الستّ نفسها | `"android.os.extra.CYCLE_COUNT"` |
| 36 | الستّ نفسها | `"android.os.extra.CYCLE_COUNT"` |

فلا ثابت `BATTERY_PROPERTY_*` بعد 6 في أيّ منها — لا 7 ولا 8 — **والتعليق `BatteryReader.kt:682-684` («Officially exposed via BatteryManager#BATTERY_PROPERTY_CYCLE_COUNT since Android 14») كان خطأً** وصُحِّح (مع تعليقَي الواجهة `BATTERY_PROPERTY_STATE_OF_HEALTH = 7` و`BATTERY_PROPERTY_CYCLE_COUNT = ID 8`). **الخاصّية 8 ليست عامّة، فلم يكن ما يوقف الحذف.** المنقول من التشخيص (`V8-PHASE0-DIAGNOSIS-2026-10-08.md:387-388`) عن android-35 صحيح بالنصّ الذي رأيتُه؛ و«الدورات `unavailable` في الستّة» (مدخلات المختبر) منقول، غير متحقَّق من هذا المستودع — وكود ما قبل التعديل كان يقرأ sysfs ثم `getIntProperty(8)` وحدهما — لا الإضافة — وهذا يوافق ظهور `unavailable` في الستّة (استدلال من الكود، لا قياس على جهاز).

**ما نُفِّذ:**
* **قارئ واحد** `BatteryCycleCount.read(intent, sdkInt, readSysfs)` (`service/collector/BatteryCycleCount.kt`) يستعمله التذييل (`BatteryReader.snapshotHealth`) و`B-08` (`BatteryTests.testCycleCount`): **(١) `EXTRA_CYCLE_COUNT` من بثّ البطارية على API ≥ 34، (٢) ملفّ sysfs، (٣) غياب معلن.** أوّل خطوة تُعطي عدداً تفوز، **والصفر يُشحن كما قُرئ**.
* **`cycleCountSource`** = `extra_api34` · `sysfs:<path>` · `unavailable` (مفردات البطاقة). **زالت** `sysfs_cycle_count` و`battery_property_cycle_count_api_34`، و`no_battery_manager` صارت **حالة** (`cycleCountState = not_read:no_battery_manager`) لا مصدراً.
* **الغياب** `cycleCountState = not_read:<سبب sysfs>_and_<سبب الإضافة>` على مجموعتين مغلقتين: الإضافة: `api_below_34` · `no_battery_broadcast` · `extra_absent_from_broadcast` · `extra_unsupported_by_device` · `extra_negative_value=<n>` · `extra_read_failed=<نصّ الاستثناء>`؛ وsysfs: `sysfs_not_readable_or_absent` · `no_sysfs_reader` · `sysfs_negative_value=<n>`. والسلسلتان اللتان كان التذييل يكتبهما تحت API 34 بقيتا بحروفهما (`not_read:sysfs_not_readable_or_absent_and_api_below_34` و`…no_sysfs_reader_and_api_below_34`) — فعيّنة S10 (API 30) **لم تتغيّر بايتاً**.
* **القيمة الافتراضية لغياب الإضافة** (البند 3 من الأمر، القاعدة 22 الملحق): `BatteryCycleCount.EXTRA_DEFAULT = Int.MIN_VALUE`، **سببها:** هي علامة «غير مدعوم» التي تعيدها المنصّة للقيم الصحيحة، فليست عدداً؛ ولا تُبلَغ إلا بعد أن يقول `hasExtra` إن الإضافة موجودة، فغياب الإضافة (`extra_absent_from_broadcast`) يتميّز بـ`hasExtra` لا بالقيمة الافتراضية. وإضافة سالبة غير العلامة ليست عدداً (`extra_negative_value=<n>`، الخام في النصّ). أُدرجت في جرد §١.١١.
* **تصنيف الخادم لصفر الدورات (2026-10-13 +03) — نصّ عقد وحده، لا تغيير في كود الدورات.** **منقول من أمر المنسّق، غير مُتحقَّق منه من هذا المستودع** (الخادم في `neuralcheck-backend`؛ وقد قال وكيله إن موضعه §٧-ز؛ وتحقّقتُ: مفردات `cycleCountSource` و`cycleCountState` النهائية معرَّفة في §٧-ز («ما نُفِّذ»)، وبطاقة ت-02 في §٢ تحمل التخطيط الأصلي فأُضيف فيها مؤشّر). **التطبيق يشحن الخامّ كما هو** (`cycleCount` و`cycleCountSource` و`cycleCountState`، فلا يُسقط التطبيق الصفر ولا يصنّفه — القاعدة 20). **والخادم يصنّف:** إن كانت حالة الدورات `read` ومصدرها `extra_api34` وقيمتها `=== 0` ⟵ `unsupported:zero_from_extra_api34`، لكل العلامات؛ و`B-08` لا يذهب إلى النموذج، والبصمة تحفظ الحالة. **نصّ المستخدم بحرفه** (2026-10-13، منقول): «غير موثَّق من Samsung، وإعدادات الـS21 لا تعرض الدورات، ومرجع AOSP يرسل 0 حين لا عدّاد». **مصادر الباحث 2026-10-11** (منقولة، غير متحقَّقة من هذا المستودع): A1 `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/core/java/android/os/BatteryManager.java` · A2 `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/services/core/java/com/android/server/BatteryService.java` · A3 `https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/android14-release/health/aidl/android/hardware/health/HealthInfo.aidl` · A4 `https://android.googlesource.com/platform/system/core/+/refs/heads/android14-release/healthd/BatteryMonitor.cpp`. **ووصفه في الخادم:** `docs/FV68_READERS_INVENTORY.md` §٥ في `neuralcheck-backend` عند `c2e5e49` (منقول، لم أقرأه). **أثره على القيد (أ) أدناه:** ما يضعه البثّ على جهاز لا يدعم الإضافة يبقى غير مقيس على جهاز، لكن الخادم صار يحكم بأن صفراً من `extra_api34` ليس عدداً حقيقياً، فلا يُقرأ `cycleCount = 0` بحالة `read` عدداً صحيحاً.
* **مسار sysfs واحد** مقروء (`SystemFileReader.BATTERY_CYCLE_COUNT_PATH`) كما كان يقرأ التذييل و`B-08`؛ **قائمة مسارات التشخيص** في `BatteryTests.CYCLE_COUNT_PROBE_PATHS` ليست مصادر (تحوي `battery_health` و`asoc` و`aging`، وليست أعداد دورات) فلم أوسّع القراءة إليها — انظر «الفروق».
* **اختبارات (القواعد 23 و30 و37):** `BatteryCycleCountTest` يمرّ بكل التوليفات (3 مستويات API × 7 بثّات × 5 إجابات sysfs = 105) ويؤكد أن المصدر والحالة من المفردات المغلقة وأن العدد يوجد حين تكون الحالة `read` وحدها؛ وأن الإضافة لا تُقرأ أصلاً تحت API 34؛ وحارس مصدر: لا `getIntProperty(8)` ولا قارئ ثانٍ ولا `EXTRA_CYCLE_COUNT` خارج `BatteryCycleCount`. **الطبقة المستبدَلة:** `Intent` المنصّة مُحاكى (في android.jar لاختبارات الوحدة `Intent` هيكل لا يحمل إضافات)؛ القارئ وتصنيفه حقيقيان، وما يحمله البثّ على جهاز حقيقي للجهاز. **واستُبدلت** اختبارات `BatteryReaderHealthTest` التي ثبّتت السلوك القديم (sysfs أولاً، `getIntProperty(8)`، المفردات القديمة) ولم يُضَف بجانبها.
* **العيّنات الذهبية أُعيدت في الالتزام نفسه** (القاعدة 39): المولِّد يبني حقول الدورات بالقارئ الحقيقي على `Intent` مُحاكى وإجابة sysfs مصنوعة؛ 11ch (API 30) `unavailable` وحالة API<34 — **بايتاً كما كانت**؛ 13ch (API 34) تقرأ 187 من الإضافة (`extra_api34`)؛ 16ch (API 34، بلا إضافة في البثّ) تقرأ 212 من sysfs. ومستوى API في الترويسة (34) وما يلزمه (`device.apiLevel` و`androidVersion` وSDK المنصّة الحرارية) **مصنوع** في هذين الملفّين (الأصل الحقيقي API 30). **ضابط سلبي جرى وعُكس:** وُضعت عيّنة 13ch السابقة فأخفق الحارس مسمّياً `device.apiLevel` و`device.androidVersion` و`batteryHealth.cycleCount` و`…Source` و`…State`.

**sha256 الجديدة** (`app/src/test/resources/derived/`):

| الملفّ | sha256 |
|---|---|
| `…-11ch-nothing-admitted.json` | `58e88edd36c7f9ebcc9941d225e3befb93ea45c78cedf97d653487eabaac4cd0` (لم يتغيّر) |
| `…-11ch-nothing-admitted.keys.txt` | `4399e5bf2232ea4d889a8a4567b4af8e47de501102fccee9a72561ebeb02596a` (لم يتغيّر) |
| `…-13ch-selinux-blocked-policy.json` | `a8f5d498d84aafac686b6d932ca028b4011752d351661ea73584c45e5e8f046f` |
| `…-13ch-selinux-blocked-policy.keys.txt` | `bfcaea2d0aab68e3d2ac3d0a0a9b9752087b2e8683a01f1d73aeb27abb9bff71` |
| `…-16ch-four-policies-truncated-failures.json` | `3b30649a1d6c3bd1d2e6f629a7614be6dd9f7c918ad10e7d6e1cb173851c1248` |
| `…-16ch-four-policies-truncated-failures.keys.txt` | `67a5baf073b579e8275f972fb928a4f391b371dcc46c53e30240ef1f60107122` |

**الفروق عن بطاقة ت-02 وعن الأمر (القاعدة 29):**
1. **«مسارات sysfs» (جمع):** مسار واحد مقروء، لا قائمة. القائمة التشخيصية فيها ملفّات ليست أعداد دورات، والبطاقة نفسها تنقل «صفر نجاح من 30 عقدة» (PLAN-04، منقول)؛ والقارئ يمرّ على `SYSFS_PATHS` فتوسيعه سطر بيانات إن أُريد.
2. **ترتيب القراءة تغيّر** عمّا كان في الكود (sysfs ثم الخاصّية) إلى الإضافة ثم sysfs — بنصّ الأمر.
3. **`cycleCountSource` يخرج بقيم جديدة تحت المفتاح نفسه** (لا مفتاح جديد). العقد §٤.٢ ينقل أن الخادم لا يقرؤه (غير متحقَّق من هذا المستودع).
4. **`no_battery_manager` لم يعد قيمة مصدر** بل حالة؛ البطاقة تُعدّد ثلاث قيم مصدر فقط.
5. **`B-08` يتغيّر سلوكه ظاهراً** على API ≥ 34: يقرأ من الإضافة فقد يصير له درجة بدل `UNAVAILABLE`. لم أغيّر عتباته ولا ملاحظاته.
6. **تعليقان آخران للواجهة** (`BATTERY_PROPERTY_STATE_OF_HEALTH = 7` و`CYCLE_COUNT = 8`) صُحّحا مع تعليق الأمر — javap يخالفهما أيضاً.

**قيود معروفة لم تُصلَح:** (أ) **ما يضعه البثّ الحقيقي في الإضافة على جهاز حقيقي لم يُقَس** — هل يضع صفراً حين لا يدعم الجهاز؟ لا شيء في هذا المستودع يقول؛ والخادم هو الذي يحكم بما يعنيه الصفر (القاعدة 20)، **فإن كان بعض الأجهزة يعيد 0 بدل علامة «غير مدعوم» فسيظهر `cycleCount = 0` بحالة `read`** — هذا ما يُقاس على الجهاز؛ **وحُسم تصنيفه عند الخادم 2026-10-13 (البند «تصنيف الخادم لصفر الدورات» أعلاه): `unsupported:zero_from_extra_api34`**؛ (ب) مسارات sysfs التشخيصية لا تُقرأ.

**للجهاز (المرحلة ٢، جولة واحدة):** **عدد الدورات من `EXTRA_CYCLE_COUNT` على الـS21 (API 34):** قيمة `footer.batteryHealth.cycleCount` و`cycleCountSource` (`extra_api34` المتوقَّع) وهل تطابق ما يعرضه الجهاز نفسه؛ وهل يطابق `B-08` التذييل (يجب)؛ **وما يحدث على الـS10 (API 30):** `unavailable` و`not_read:sysfs_not_readable_or_absent_and_api_below_34` (كما التسجيل الحقيقي). ويُنظر في صفر حقيقي مقابل صفر «غير مدعوم» إن ظهر.

**أسماء [م] لهذا الجزء (تنتظر إقرار المنسّق):** `BatteryCycleCount` · `CycleCountSources` · `CycleCountReading` · `EXTRA_DEFAULT` · `FIRST_API_WITH_EXTRA` · `SYSFS_PATHS` · أسباب الغياب `no_battery_broadcast` و`extra_absent_from_broadcast` و`extra_unsupported_by_device` و`extra_negative_value=` و`extra_read_failed=` و`sysfs_negative_value=` والصيغة `<سبب sysfs>_and_<سبب الإضافة>` · `SystemFileReader.readBatteryCycleCountAt` و`BATTERY_CYCLE_COUNT_PATH`.

### ٧-ح — fv69: الدفعة ١٠ (خطوات الفنّي)، 2026-10-10 +03

**هذا القسم يسبق الكود (القاعدة 7): يُلتزَم وحده، ثم تأتي الالتزامات التي تنفّذه.** مصدره أمر المنسّق 2026-10-10 («دفعة PLAN-05 رقم ١٠، خطوات الفنّي»؛ أساس `99c0548`) وبطاقة ت-15 أعلاه (س-15 وس-16 وس-17 وس-26 وس-29 (أ) وس-32 (ب)). **منقول، غير متحقَّق من هذا المستودع:** نصّ الأمر نفسه (محفوظ في مساحة العمل: `evidence/v8-phase0/order-app-engineer-batch10-tech-steps-2026-10-10.md`)، وجدول الخطة (`PLAN-05-EVIDENCE-REPORT.md:519-522`). **قاعدة هذه الدفعة (الأمر):** «وإن اختلف الجدول عن العقد فالعقد يحكم، وتذكر الفرق».

**قرارات المستخدم لهذه الدفعة، بنصّها (2026-10-10، منقولة في الأمر):**
1. «إصدار الحقول: fv69 في أوّل التزام يغيّر الشكل، كما قرّرت.»
2. «شبكة اللمس: العدّ من أوّل لمسة، لا من فتح الشاشة. ويُسجَّل «الزمن حتى أوّل لمسة» حقلاً منفصلاً.»
3. «SN-04 …: يبقى ما يرسله التطبيق اليوم كما هو، ويُضاف الخامّ، فلا يتغيّر حقل قائم ولا قارئ جديد قبل المحطّة ١. والصفر بلا تغطية يبقى «تحذيراً» في العرض حتى يصنّفه الخادم من الخامّ في دفعات التقرير. ويُكتب في العقد ما يُرسَل في كل حالة (تغطية، وبلا تغطية، وصفر)، لتُسدّ الثغرة.»

**الختم:** `FIELDS_VERSION` = **69** في أوّل التزام يغيّر شكل الحمولة من هذه الدفعة (التزام الحالات الأربع). التزامات الشكل التالية تحمل 69 وهي ناقصة بالتصميم حتى الأخير؛ **ولا APK** (ت-19: لا بناء إصدار قبل الالتزام الأخير). **الملفّ الزمني (`.ts.json.gz`) لا يتغيّر في هذه الدفعة**: ما يتغيّر هو حمولة الطلب (`functionalResults` / `manualResults` / جذر `BehaviorFingerprint`) والختم في التذييل.

#### ما يتغيّر في الحمولة (fv68 ← fv69)

| الموضع | fv68 | fv69 | الأسماء |
|---|---|---|---|
| `manualResults[].status` للإجابات اليدوية | `pass` · `fail` · `not_tested`؛ زرّ «غير متوفر في هذا الجهاز» يكتب `not_tested` (خمسة مواضع) | **`pass` · `fail` · `not_tested` · `unavailable`**؛ المواضع الخمسة تكتب `unavailable`، و«لم أختبر» تبقى `not_tested`. لا درجة لـ`unavailable` (`score = null`). `TestStatus.fromString("unavailable")` يعرفها أصلاً فلا تسقط إلى `ERROR` | `unavailable` (محسوم س-15) |
| جذر الحمولة | لا رأس للنتائج اليدوية | **`manualResultsHeader: { hiddenByModel: [testId…] }`** شقيق لـ`manualResults`. القائمة **حاضرة دائماً** من مسار الإنتاج (فارغة = لم يُخفَ شيء)، وغائبة المفتاح في بناء ≤ fv68 (= لا معرفة) | N-17 (مُقرّ) |
| `manualResults[M-02].measurements` | غائب (الحامل موجود ولا شيء يكتبه) | الخامّ أدناه | N-19 (مُقرّ؛ **مُعدَّل**: دلالة `elapsedMs` + حقل `timeToFirstTouchMs` جديد [م]) |
| `functionalResults[SN-04].measurements` | غائب (`null` لغير المستشعرات الخطوية) | الخامّ أدناه | N-20 (مُقرّ) |
| «انتظر» | — | **لا يمسّ الحمولة ولا الشكل** (ت-15 (د)) | — |

##### (أ) الحالات الأربع و`hiddenByModel`

* **المفاتيح:** `pass` · `fail` · `not_tested` · `unavailable`، مصدرها المُصدَّر الواحد `ManualStatuses` [م] (القاعدة 24). `unavailable` = **الفنّي يُعلن أن الطراز لا يملك المكوّن**؛ `not_tested` = **لم يختبر**. الفرق كان مطموساً في fv68 (كلاهما `not_tested`).
* **العرض:** صفّ الاختبار الذي يحمل `unavailable` يعرض سطراً «غير متوفر في هذا الجهاز» (نصّ `manual_mark_unavailable` القائم) لا شريحة رابعة — الشرائح الثلاث (يعمل · لا يعمل · لم أختبر) لا تعرضها، فلولا السطر لبدا الاختبار بلا إجابة.
* **ما لا يملكه الطراز لا سجلّ له** [ح س-15]: ما أُخفي لا يُعرض في قائمة الأسئلة، **ويُستثنى من `allIds`** فلا يملؤه `commitAndContinue` بـ`not_tested`، ويُسمّى في `manualResultsHeader.hiddenByModel`.
* **⚠ ما لم يحسمه النصّ — القاعدة 13:** **أيّ اختبار يُخفى، وبأيّ إشارة** (إعلان ميزات النظام `FEATURE_*`؟ فهرس طرازات؟ غيره؟). الأمر يقول «كما في العقد» والعقد (ت-15 (أ) وس-15 وN-17) يحدّد **القائمة وموضعها والاستثناء من `allIds`** ولا يحدّد **المعيار**. **المُنفَّذ: الآلية كاملة وجدول القواعد `ManualTestVisibility.RULES` فارغ** — فتخرج `hiddenByModel = []` دائماً، وهذا إعلان صادق («لم يُخفَ شيء»)، لا قيمة افتراضية تسدّ غياباً. **ملء الجدول ينتظر قرار المستخدم** (صفوف مقترحة في تقرير التسليم، لا في الكود).
* **القارئ على الخادم:** `manualResultsHeader.hiddenByModel` و`status = unavailable` للإجابات اليدوية قارئان جديدان (§٤.٢) — **يكتبهما وكيل الخادم بعد هذه الالتزامات** (أمر منفصل، بنصّ الأمر). العيّنات الذهبية على fv69 مصدرهما.

##### (ب) شبكة اللمس M-02 [ح س-16 + قرار المستخدم 2]

**التدفّق (يستبدل مرحلة «انتهيت» ثم المراجعة بالعتبتين 95/80):** شبكة 8 × 15 = 120 خلية (الأبعاد لا تتغيّر؛ جدول الخطة «مربّعات صغيرة» **لا يُنفَّذ**: العقد لا يحدّد حجماً جديداً، والأبعاد خامّ مشحون فتغييرها لاحقاً سطر واحد) ← تتلوّن الخلية عند أول لمسة فوقها (إصبع واحد أو أكثر) ← **عند اكتمال الخلايا كلّها: نجاح تلقائي** (`pass`) والحوار يُغلق ← **بعد 15 ثانية من فتح الحوار** يظهر زرّا «لا يعمل» (`fail`) و«لم أختبر» (`not_tested`) بنصَّيهما القائمين. **لا `warning`، ولا عتبات في التطبيق** (95/80 زالتا من الكود: لا نصّ توصية ولا خريطة مناطق ميتة بعد «انتهيت»). **الخامّ يُشحَن والعتبات على الخادم** [ح س-16].

* **«بعد 15 ثانية» من فتح الحوار لا من أول لمسة** [م — جدول الخطة لا يحدّد المرجع]: شاشة ميتة لا تعطي أول لمسة أبداً، فلو عُدَّت من أول لمسة لما ظهر زرّ للفنّي قطّ. الثابت `TECHNICIAN_BUTTONS_AFTER_MS = 15_000` [م].
* **الخامّ** (`measurements`، كلّ مفتاح خامّ لا حكم فيه، القاعدة 20):

| المفتاح | النوع | الدلالة |
|---|---|---|
| `gridCols` · `gridRows` | عدد صحيح | 8 و15 |
| `totalCells` | عدد صحيح | 120 |
| `touchedCells` | عدد صحيح | الخلايا الملموسة عند النهاية (قد تكون 0) |
| `elapsedMs` | عدد صحيح | **من أوّل لمسة إلى نهاية التشغيل** (القرار 2) — نهاية = اكتمال الخلايا، أو ضغط الفنّي، أو الإغلاق. **غائب** إن لم تقع لمسة (لا أول لمسة فلا مرجع؛ و`touchedCells = 0` يعلن السبب) |
| `timeToFirstTouchMs` [م] | عدد صحيح | **من فتح الحوار إلى أول لمسة** (القرار 2: حقل منفصل). **غائب** إن لم تقع لمسة |
| `endReason` | نصّ | `auto_complete` · `user_marked_not_working` · `user_marked_not_tested` · `dismissed` (N-19) |
| `maxSimultaneousPointers` | عدد صحيح | أقصى `event.changes.count { pressed }` رُصد (0 إن لم تقع لمسة) |

* **`endReason` ↔ الحالة:** `auto_complete` ⇒ `pass` · `user_marked_not_working` ⇒ `fail` · `user_marked_not_tested` ⇒ `not_tested` · **`dismissed`** (رجوع/إغلاق بلا زرّ) ⇒ **لا حالة جديدة**: إن سبقته إجابة بقيت حالتها (لا يمحو الإغلاقُ إجابةً)، وإلا `not_tested`؛ والخامّ يُكتب **فقط إن وقعت لمسة واحدة على الأقلّ** (فتح عرضي بلا لمس لا يمحو خامّ تشغيل سابق) [م — العقد يعدّد `dismissed` ولا يحدّد ما يُكتب عنده].
* **الإعلان:** `fail` **إعلان الفنّي** لا حكم التطبيق؛ والتطبيق لا يرسل `warning` لـM-02 (لم يكن يرسله يدوياً أصلاً: العتبتان كانتا نصّاً فقط). ولا يصل M-02 `unavailable` (ليس من المواضع الخمسة).

##### (ج) تغطية الإضاءة SN-04 [ح س-17 + س-32 (ب) + قرار المستخدم 3]

**لا يرسل التطبيق `not_measured` ولا `notMeasuredReason`** (س-32 ب؛ N-18 لا يُنفَّذ). **ما يرسله التطبيق اليوم يبقى كما هو بحروفه** (القرار 3): الحالة والنصّ والملاحظة تُحسب كما حسبها `SensorTests.testSensor` منذ fv68 على **كل قراءات الاختبار** (النافذتين معاً)، ويُضاف الخامّ وحده. **«الصفر بلا تغطية» يبقى `WARNING` في التطبيق** حتى يصنّفه الخادم «غير مقيس» من الخامّ في دفعات التقرير.

* **التدفّق:** مستمع واحد على `TYPE_LIGHT` طوال الاختبار ← `LIGHT_BEFORE_COVER_MS = 1000` [م] قراءة بلا طلب ← **يُطلب من الفنّي «غطِّ أعلى الشاشة بيدك»** (لافتة على شاشة الفحص، تُرفع عند نهاية الاختبار حتماً) ← `LIGHT_DURING_COVER_MS = 3000` [م] ← إلغاء التسجيل. **التطبيق لا يعرف إن غطّى الفنّي ولا متى** ولا يقارن النافذتين (س-32 ب)؛ فـ`readingsDuringCover` = **القراءات من لحظة طلب التغطية إلى النهاية** (تتضمّن زمن ردّ فعل الفنّي)، والخادم يقرّر من القيم و`t`. (جدول الخطة «~3 ث [اجتهاد]»: الاختبار 4 ث؛ تُضبط الثوابت بعد الجهاز.)
* **الخامّ** (`measurements` لـSN-04، **القائمتان حاضرتان دائماً؛ فارغة = لا قراءات في تلك النافذة**، القاعدة 22):

| المفتاح | الدلالة |
|---|---|
| `windowMs` [م] | طول النافذة الكلّي (4000) |
| `coverPromptAtMs` [م] | `t` عند طلب التغطية، وهو **فاصل القائمتين** (قراءة بطابع < الفاصل ⇒ قبل، ≥ الفاصل ⇒ أثناء) |
| `readingsBeforeCover[]` | عناصر `{t, values}` (N-20): `t` = ms منذ تسجيل المستمع (`event.timestamp` − لحظة التسجيل، ساعة `elapsedRealtimeNanos` نفسها)، `values` نسخة `event.values` |
| `readingsDuringCover[]` | الشكل نفسه |

**جدول ما يُرسَل في كل حالة (يسدّ الثغرة في `:600` — القرار 3).** الحساب من `SensorTests.testSensor` (`val allZero` و`val status = if (allZero)…`): `R` = كل القراءات في النافذتين بترتيب الوصول.

| الحالة | ما فعله الفنّي | `status` | `rawValue` · `notes` | `measurements` |
|---|---|---|---|---|
| (١) **تغطية بتغيّر القراءة** | غطّى؛ نزلت القيم | `PASS` (`R` فيها قيمة ≠ 0) | متوسّط كل مكوّن على `R` كلّها، `%.2f` بـ`Locale.US`، مفصولاً بـ«، » · `"<n> readings"` | القائمتان بقراءاتهما؛ قيم `readingsDuringCover` أدنى من `readingsBeforeCover` |
| (٢) **بلا تغطية** | لم يغطِّ (أو لم ينتبه للافتة) | **`PASS` نفسه** — التطبيق لا يميّزها من (١) | كما في (١) | القائمتان بقيم متقاربة بين النافذتين؛ **الخادم هو من يصنّفها «غير مقيس»** |
| (٣) **صفر في كل القراءات** | غطّى كلّ الوقت، أو غرفة معتمة، أو مستشعر عالق | **`WARNING`** (`allZero`) | `"0.00"` (لكل مكوّن) · `"<n> readings"` | القائمتان بأصفار كلّها |
| (٤) **لا قراءات** | المستشعر لم يُرسل حدثاً | **`WARNING`** | نصّ `value_no_readings_ar` · لا ملاحظة | **القائمتان `[]`** معلنتان (لا غياب مفتاح) |

* **ما لا يتغيّر:** `status` و`rawValue` و`rawValueUnit` و`notes` في كل الحالات كما كانت؛ والتسلسل `UNAVAILABLE` لغياب المستشعر أو فشل التسجيل (بلا `measurements`) **لا يتغيّر**. فلا حقل قائم يتغيّر ولا قارئ جديد قبل المحطّة ١ (القرار 3) — **القارئ الوحيد الجديد هو قارئ الخادم للقائمتين، ويُكتب بعد التزامات هذه الدفعة.**

##### (د) «انتظر» [ح س-26]

* **لا يمسّ الحمولة** (ت-15 (د)): `sessionId` مفتاح طلب قائم (`FirebaseAnalysisService.analyzeDevice`: `data["sessionId"] = sessionId`) ولا يُضاف مفتاح.
* **السلوك (سلوك عميل فقط):** عند رفض `analyzeDevice` بالرمز `ALREADY_EXISTS` (= «قيد المعالجة»، 409) **تُعرض بطاقة «انتظر» بدل بطاقة الخطأ** مع «إعادة المحاولة» (إعادة `loadScan` التي تبدأ بقراءة التقرير من Firestore، فإن انتهى الطلب الأوّل على الخادم قُرئ تقريره). و**لا يُمسح `sessionId` من `ScanSessionHolder` في هذه الحالة** (كان يُمسح بعد كل نداء: نجاحاً وفشلاً — `AiScoringEngine`) فيصل الطلب المُعاد بالجلسة نفسها.
* **منقول، غير متحقَّق من هذا المستودع:** أن `ALREADY_EXISTS` تعني «قيد المعالجة» وأنها رمز `analyzeDevice.js:463` (مصدر المنقول: تعليق `AiScoringEngine.kt` «القرار ١٠٤ الملحق الرابع البند ١» وتعليق `ViewModels.kt` على `SingleFlightGuard`، وكلاهما نقل عن `neuralcheck-backend`). **التمييز بالرمز لا بنصّ الرسالة** لأن الرمز بروتوكول (حالة HTTP) لا صنف استثناء محلّي، ويشترط عدم كون `cause` استثناء شبكة (الشرط نفسه في `analysisErrorMessage`).
* **ما لا يُصلَح هنا (قيد معروف):** فشل غير «قيد المعالجة» (شبكة، مهلة…) **يبقى يمسح `sessionId`** — إعادة المحاولة بعده على جهاز عميل بلا مستخدم تفتقد الجلسة. أمر هذه الدفعة حدّد «انتظر» وحدها؛ وتعميم الإبقاء على كل فشل يترك الجلسة معلّقة في الذاكرة لفحص لاحق (يحتاج قرار المستخدم).

#### النصوص الجديدة (القرار ٩٠: العربية والتركية — معتمدة 2026-10-10)

**الاعتماد (قرار المستخدم 2026-10-10، بنصّه، منقول في أمر المنسّق):** «نصوص التطبيق الثلاثة (لافتة التغطية، و«انتظر» وعنوانها ونصّها، عربياً وتركياً): معتمدة.» **كان الوسم «مقترحة للاعتماد»؛ صار «معتمدة 2026-10-10».** والنصوص الثلاثة بمفاتيحها أدناه، وهي بعينها ما في `values/strings.xml` و`values-tr/strings.xml` (`report_ai_wait_title`: «انتظر» / «Bekleyin»).

سلاسل جديدة في `values/` و`values-tr/` (والجدول بنصّيها في تقرير التسليم): `scan_cover_light_sensor_prompt` (لافتة «غطِّ أعلى الشاشة بيدك») · `report_ai_wait_title` · `report_ai_wait_body`. **ولا نصّ جديد لأزرار الشبكة:** يُعاد استعمال `manual_not_works` و`manual_not_tested_btn` القائمتين؛ ولسطر «غير متوفر» في صفّ الاختبار يُعاد استعمال `manual_mark_unavailable`.

#### الأسماء [م] التي أضافتها هذه الدفعة (تنتظر إقرار المنسّق)

`timeToFirstTouchMs` · `windowMs` و`coverPromptAtMs` (SN-04) · `ManualStatuses` · `ManualTestVisibility` (`RULES`، `hiddenByModel`) · `TouchGridRecorder` · `CoverPromptChannel` · ثوابت `TECHNICIAN_BUTTONS_AFTER_MS` (15000) و`LIGHT_BEFORE_COVER_MS` (1000) و`LIGHT_DURING_COVER_MS` (3000) · `ScoringResult.analysisInProgress` · `isAnalysisInProgress` و`shouldClearSessionAfterAnalysis` · `SensorTests.LightCoverKeys` و`lightCoverMeasurements` · `TouchGridRecorder.Keys` و`EndReasons` و`recordOnDismiss`. **أُقرّت سابقاً:** `manualResultsHeader.hiddenByModel` (N-17) · `gridCols`/`gridRows`/`touchedCells`/`totalCells`/`elapsedMs`/`endReason`/`maxSimultaneousPointers` (N-19) · `readingsBeforeCover`/`readingsDuringCover` بعنصر `{t, values}` (N-20) · `unavailable`.

#### حالة التنفيذ

مصدر الأعداد: ملفّات XML لمخرَج `cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest` (سكربت يجمع `tests` و`failures` و`errors` و`skipped` من `app/build/test-results/testLaunchDebugUnitTest/TEST-*.xml`، بـjbr 21.0.9 داخل الأمر وحده) بعد كل التزام. الأساس `99c0548`: 133 ملفّاً · 1105 اختباراً. **الختم 69 وُضع في أول التزام يغيّر الشكل (`8132327`)؛ وfv69 غير مكتمل بالتصميم حتى الالتزام الأخير من الدفعة؛ ولا يُبنى APK قبله.**

| الخطوة | الالتزام | ما غيّره | ملفّات XML · اختبارات (كلّها 0 فشل · 0 خطأ · 0 تخطٍّ) |
|---|---|---|---|
| الأساس | `99c0548` | — | 133 · 1105 |
| العقد (هذا القسم) | `b67a3a3` | وثيقة | بلا كود |
| الحالات الأربع + `hiddenByModel` | `8132327` | `ManualStatuses` · المواضع الخمسة تكتب `unavailable` · `ManualResultsHeaderDto` · `ManualTestVisibility` (الجدول فارغ) · **الختم 69** · العيّنات الذهبية أُعيدت وسُمّيت fv69 | 137 · 1125 |
| شبكة اللمس M-02 | `2205d0f` | `TouchGridRecorder` · حوار بمرحلة واحدة (إكمال تلقائي، وبعد 15 ث زرّا الفنّي) · الخامّ مع الإجابة · العيّنات أُعيدت | 138 · 1141 |
| تغطية الإضاءة SN-04 | `fed5ed3` | قائمتان خامّ + لافتة «غطِّ أعلى الشاشة بيدك» عبر `CoverPromptChannel` · الحالة كما كانت · العيّنات أُعيدت | 139 · 1153 |
| «انتظر» | `7e77d93` | `ScoringResult.analysisInProgress` · بطاقة «انتظر» · `sessionId` يبقى في هذه الحالة · لا يمسّ الحمولة | 140 · 1161 |
| الوثائق | `هذا الالتزام` | هذا القسم (الحالة والفروق والجرد) | 140 · 1161 (لا كود: الأعداد كما في الالتزام السابق) |

**sha256 العيّنات الذهبية** (`app/src/test/resources/derived/`، الاسم بعد `fv69-app-request-payload-derived-from-fv67-golden-34dff2b8-`؛ القبل = ملفّات fv68 عند `99c0548`):

| الملفّ | sha256 قبل (fv68، `99c0548`) | sha256 بعد (fv69، هذا الالتزام) |
|---|---|---|
| `…-11ch-nothing-admitted.json` | `58e88edd36c7f9ebcc9941d225e3befb93ea45c78cedf97d653487eabaac4cd0` | `2e8b03444a8e29ffa37a06aca94e87f25130aeae2fdb412b290816cbd9425672` |
| `…-11ch-nothing-admitted.keys.txt` | `4399e5bf2232ea4d889a8a4567b4af8e47de501102fccee9a72561ebeb02596a` | `5c75fb73f12e395f678762d806f1be8ffcbe5163da2bed3509a99979dc31a9b8` |
| `…-13ch-selinux-blocked-policy.json` | `a8f5d498d84aafac686b6d932ca028b4011752d351661ea73584c45e5e8f046f` | `0d40ab66dd37034cc16a1c89b53a9729b63255caae36c35ab38f8375be1be1a9` |
| `…-13ch-selinux-blocked-policy.keys.txt` | `bfcaea2d0aab68e3d2ac3d0a0a9b9752087b2e8683a01f1d73aeb27abb9bff71` | `e60894a7ed099162b26975ce2366bf5d4125df22fff7aa6b202ea5cd50749dd6` |
| `…-16ch-four-policies-truncated-failures.json` | `3b30649a1d6c3bd1d2e6f629a7614be6dd9f7c918ad10e7d6e1cb173851c1248` | `b42e8211fef8e1920b841fd2c962b23ef990f31a60f4d2365695c65aaa74b041` |
| `…-16ch-four-policies-truncated-failures.keys.txt` | `67a5baf073b579e8275f972fb928a4f391b371dcc46c53e30240ef1f60107122` | `86d1675fc49d49df46387bc73cb3da1e3123897abf0222eb17bd76651ba8ed6b` |

##### الفروق عمّا في الأمر والعقد (القاعدة 29)

1. **أسطر الأمر انجرفت:** «`SensorTests.kt:102-103` (صفر في كلها ⇒ WARNING)» صارت `SensorTests.kt:273-274` (أضافت دفعة fv68 كود SN-08/09 فوقها)، والقيمة هي المرجع. و«`ViewModels.kt:700-716`»: تعليق `SingleFlightGuard` عن `already-exists` — صحيح عند الأساس (السطر `:709`)، وصار `:714` بعد إضافة قناة اللافتة؛ وما يقوله عن الخادم **منقول، غير متحقَّق** (لا قراءة لـ`analyzeDevice.js` من مستودعي).
2. **جدول الخطة «مربّعات صغيرة» لم يُنفَّذ:** العقد لا يحدّد حجماً جديداً؛ بقيت 8 × 15 (`GRID_COLS` و`GRID_ROWS` ثابتان، والأبعاد خامّ مشحون).
3. **زرّا الشبكة بنصَّي القائمين** «لا يعمل» و«لم أختبر» (`manual_not_works` و`manual_not_tested_btn`) لا «لا تعمل» الواردة في الجدول المنقول؛ فلا نصّ جديد لهما.
4. **«بعد 15 ثانية»** تُعدّ من **فتح الحوار** لا من أول لمسة (الجدول لا يحدّد المرجع، وشاشة ميتة لا تعطي أول لمسة).
5. **اختبار SN-04 يستغرق 4 ث** (1 ث قبل الطلب + 3 ث بعده) لا «~3 ث» [اجتهاد] الواردة في الجدول المنقول: زمن ردّ فعل الفنّي. الثابتان `LIGHT_BEFORE_COVER_MS` و`LIGHT_DURING_COVER_MS` يُضبطان بعد الجهاز.
6. **الحقلان `windowMs` و`coverPromptAtMs` في SN-04** [م]: لم يردا في N-20 المقرّ؛ أُضيفا لأن القائمتين تُقسمان عند الطلب والخادم لا يستطيع اشتقاق الفاصل من القوائم وحدها إن كانت إحداهما فارغة.
7. **`dismissed`:** العقد يعدّده ولا يحدّد ما يُكتب عنده؛ **المُنفَّذ** (م): الخامّ يُكتب إن وقعت لمسة واحدة على الأقلّ، مع الإجابة الموجودة (أو `not_tested`)، فلا يمحو الإغلاقُ إجابةً ولا يمحو فتحٌ عرضي خامَّ تشغيل سابق.
8. **`hiddenByModel`: الآلية كاملة والجدول فارغ** — انظر «⚠ ما لم يحسمه النصّ» في (أ).
9. **`sessionId` يبقى في حالة «قيد المعالجة» وحدها** — أمر الدفعة حدّد «انتظر»؛ وفشل غيرها يبقى يمسحه (قيد معروف أدناه).

##### جرد القيم الافتراضية والفروع الافتراضية الجديدة (القاعدة 22، الملحق)

| المسار:السطر | القيمة | السبب المكتوب | الحكم |
|---|---|---|---|
| `ManualTestVisibility.kt:45` | `RULES = emptyList()` ⇒ `hiddenByModel = []` | القرار لا يحدّد أيّ اختبار يُخفى ولا بأيّ إشارة (القاعدة 13) | يبقى: إعلان صادق «لم يُخفَ شيء» لا يسدّ غياباً؛ يُملأ بقرار المستخدم |
| `ManualTestVisibility.kt:57-58` | قاعدة ترمي ⇒ لا يُخفى اختبارها | اختبار يُسأل بلا داعٍ يكلّف لمسة، والمخفيّ يكلّف السجلّ؛ ويُسجَّل `Log.w` | يبقى (لا قاعدة اليوم) |
| `BehaviorFingerprintBuilder.kt:72` | `manualResultsHeader = null` ⇒ المفتاح غائب | «المستدعي لم يزوّد رأساً» = لا معرفة، لا «لا إخفاء» | يبقى؛ مسار الإنتاج (`AiScoringEngine`) يمرّر الرأس دائماً |
| `TouchGridRecorder.kt:46` | `currentStatus ?: NOT_TESTED` عند `dismissed` | العقد يعدّد `dismissed` ولا يحدّد حالة؛ وغير المُجاب يُملأ `not_tested` عند المتابعة أصلاً | يبقى [م] — يحتاج إقراراً |
| `TouchGridRecorder.kt:133` | `completedAtMs ?: nowMs()` | احتياط: `auto_complete` لا يُطلب إلا بعد الاكتمال فلا يبلغه عملياً | يبقى |
| `CoverPromptChannel.kt:23` | `MutableStateFlow(false)` | «لا طلب قائم» حالة ابتدائية لا قياس | يبقى |
| `ScoringResult.kt:52` | `analysisInProgress = false` | الحالة العادية (لا «قيد المعالجة») | يبقى |
| `SensorTests.kt:224` | `coverPromptAtNanos = 0L` | يُكتب قبل أي استعمال (بعد `delay`)؛ وإلغاء الاختبار لا يبلغ بناء الخامّ | يبقى |
| `ManualStatuses.kt:36` | `scoreOf` ⇒ `null` لغير pass/fail | لا درجة لما ليس إجابة صالحة/خاطئة — الغياب يبقى غياباً | يبقى |

##### قيود معروفة لم تُصلَح (من هذه الدفعة)

* **`ManualTestVisibility.RULES` فارغ:** لا اختبار يُخفى اليوم. **ينتظر قرار المستخدم:** أيّ الاختبارات تُخفى وبأيّ إشارة. *(مقترحات في تقرير التسليم، غير منفَّذة.)*
* **فشل غير «قيد المعالجة» (شبكة، مهلة، خطأ خادم) يبقى يمسح `sessionId`** (`AiScoringEngine`، `.also`): إعادة المحاولة بعده على جهاز عميل بلا مستخدم تفتقد الجلسة. تعميم الإبقاء يترك الجلسة معلّقة في الذاكرة لفحص لاحق — قرار المستخدم.
* **«انتظر» بلا استطلاع تلقائي:** زرّ «إعادة المحاولة» وحده؛ لم يطلب الأمر مؤقّتاً.
* **أثر هيئة الفنّي على القياس غير مقيس:** لافتة SN-04 تظهر أثناء الفحص (مرحلة INIT في V2؛ **استدلال** من تسلسل `SensorTests.runAll`: SN-01 إلى SN-03 تسبقه بـ3 ث لكل منها — غير مقيس)، ويد الفنّي قرب الجهاز؛ لم يُقَس أثرها على `scanStillness` ولا على أي قناة.
* **ثوابت SN-04 (1 ث + 3 ث) و15 ث للأزرار غير مضبوطة على جهاز.**
* **`pass` / `fail` / `not_tested` حرفياً في بقية الحوارات** (`ManualTestDialogs.kt`، `ReportScreen.kt` الشرائح الثلاث): بقيت نصوصاً خاماً خارج `ManualStatuses` لأن الأمر لم يطلب توحيدها (القاعدة 24 تخصّ ما يقوم عليه تمييز؛ المكرَّر موجود قبل الدفعة).

##### للجهاز (جولة الفحص الميداني الواحدة، القاعدة 19)

* **S10 وS21:** شبكة اللمس: هل يكتمل التلوين تلقائياً بإصبع/أكثر؟ زمن `elapsedMs` و`timeToFirstTouchMs` المعقول؟ وهل تظهر الأزرار بعد 15 ث؟ وماذا يحدث عند الرجوع (dismissed)؟
* **تغطية الإضاءة:** هل تظهر اللافتة في الوقت (قرابة 1 ث بعد بدء الاختبار) وتختفي؟ وهل القراءات تنزل فعلاً بعد التغطية (قائمة `readingsDuringCover`)؟ وكم حدثاً يعطي مستشعر الضوء في 1 ث (قد لا يعطي إلا الحدث الأوّل)؟
* **«انتظر»:** يصعب إحداثه (طلب مكرَّر بالتزامن) — غير مقيس.

### ٧-ط — fv70: إصلاح D (فحص المعالج بالجواب المعروف)، 2026-10-13 +03

**هذا القسم يسبق الكود (القاعدة 7): يُلتزَم وحده، ثم تأتي الالتزامات التي تنفّذه.** مصدره أمر المنسّق 2026-10-13 (أساس `c22251f`، فرع `master`). **منقول، غير متحقَّق من هذا المستودع:** نصّ قرار المستخدم أدناه؛ وقياس فحص الجهاز الثالث (`evidence/device-check-03/device-check-03-after-2026-10-13T0830.json` في مساحة العمل — لا أقرؤه من هنا)؛ وقيمتا π كما نقلهما المنسّق (وأعدتُ حسابهما بسكربت Node في المجلّد المؤقّت، لا في المستودع: π(10⁶) = 78,498 وπ(10⁷) = 664,579)؛ ووقائع الخادم (وكيل الخادم عند `5dc318b`: لا مقارنة لـ`sievePrimeCount` بثابت، ولا افتراض ثلاثة تشغيلات). **المتحقَّق من هذا المستودع:** كل `ملفّ:سطر` أدناه مقروء عند `c22251f`، والسطر ينجرف بعد أول التزام كود والقيمة هي المرجع.

**لماذا (منقول، غير متحقَّق):** في قياس الفحص الثالث زمن D في المرحلة الواحدة 470 و642 ms على الـS10، و911 و872 ms على الـS21، والغربال 88–94% منه؛ ونِسَبه من زمن المرحلة 0.6268% و1.0745% و1.2149% و1.4596% — ومعيار القبول 1% (§٧-د البند 6). فالغربال إلى 10,000,000 هو ما يكلّف، والسقف أدناه يحمي المعيار حين يُبطئ الجهاز.

**قرار المستخدم 2026-10-13، بنصّه (منقول في الأمر؛ الموضع المحذوف «…» من الفقرة 2 كما وصلني):**
> 1. D:
>    - الغربال 1,000,000 (78,498 عدداً أوّلياً)، والتشغيلات الثلاثة باقية.
>    - السقف: القاعدة الأقوى. قبل كل تشغيل: إن كان (الزمن حتى الآن + أطول تشغيل سابق في المرحلة) يتجاوز 1% من زمنها المخطَّط، يُترك ويُسجَّل skipped:time_budget. والتشغيل الأوّل يجري دائماً.
>    - sieveLimit حقلاً خامّاً في كل تشغيل.
>    - في fv70 مع P6-13 إن دخلا 1.0.12 معاً. وقرّاء الخادم يعرفون skipped:time_budget قبل أن يُرسله أيّ تطبيق (القاعدة 25)، فهو نشر خادم قبل 1.0.12 أو معه.
>    - ويُكتب في العقد أن D لم يعد يمرّ على الذاكرة الرئيسية، وأن اختبار الذاكرة وحده يغطّيها، ومتى لا يجري.
> 2. الـS21 أبطأ: … و(ب) رقم النواة في بداية كل تشغيل ونهايته حقلاً خامّاً في fv70، إن كانت كلفته أقلّ من نصف يوم، وإلا يُؤجَّل.

**قرار لاحق للمستخدم، 2026-10-13 +03، بنصّه (منقول من سؤال المنسّق؛ منقول، غير متحقَّق من هذا المستودع — لا أقرأ مصدره):** «Not in 1.0.12; fv71 (Recommended)». **فسطر «في fv70 مع P6-13 إن دخلا 1.0.12 معاً» أعلاه تجاوزه هذا القرار: P6-13 لا يدخل fv70 ولا 1.0.12، وينتقل إلى fv71.**

**نطاق هذا القسم:** fv70 = D وحده (ورقم النواة فقط لو كان تقدير كلفته أقلّ من نصف يوم — وهو ليس كذلك، §ز). **P6-13 ليس في fv70 ولا مكان له تحت الختم 70: ينتقل إلى fv71.** فيُختم fv70 هنا ولا يُفتح لما بعده. ولا APK ولا رفع. ولا نصّ جديد يصل مستخدماً (القرار ٩٠).

**الختم:** `FIELDS_VERSION` = **70** في **أوّل التزام يغيّر الشكل** (التزام الغربال و`sieveLimit`)؛ والتزامات الشكل التالية تحمله وهي ناقصة بالتصميم حتى الأخير (ت-19). **الملفّ الزمني (`.ts.json.gz`) يتغيّر شكله في عنصر `cpuKnownAnswerRuns[]` وحدَه** (حقل `sieveLimit` وقيمة الحالة الجديدة؛ والمفاتيح الثلاثة العليا لـD في `VerificationWireNames.SINCE_68` كما هي، فلا مفتاح علوي جديد ولا `SINCE_70`).

#### ما يتغيّر في الحمولة (fv69 ← fv70)

| الموضع | fv69 | fv70 | الأسماء |
|---|---|---|---|
| غربال D | حتى N = 10,000,000؛ المرجع 664,579؛ مصفوفة 10,000,001 بايت = 9.5367 MiB (`known_answer.cpp:17,23`) | حتى **N = 1,000,000**؛ المرجع **78,498**؛ مصفوفة 1,000,001 بايت = 0.9537 MiB. **لـD وحده:** `runPrimeSieveTest` (`stress_jni.cpp:57-67` · `StressNative.kt:39,328` · `production_stress.cpp:396-397`) يبقى على 10,000,000 | ثابتان جديدان في `reference_values.h`: `PRIME_SIEVE_1M_LIMIT` و`PRIME_SIEVE_1M_REFERENCE`؛ والقائمان (`PRIME_SIEVE_10M_*`) بلا مسّ |
| `cpuKnownAnswerRuns[].sievePrimeCount` | العدد الخامّ (المرجع 664,579) | العدد الخامّ (المرجع 78,498)، ويبقى خامّاً: الحكم للخادم | — |
| `cpuKnownAnswerRuns[]` — حقل جديد | — | **`sieveLimit`**: N الذي شغّله الغربال فعلاً (Long)، من الطبقة الأصلية (§د) | `sieveLimit` (اسم من نصّ المستخدم) |
| `cpuKnownAnswerRuns[].sieveStatus` و`.sha256Status` | `match` · `mismatch` · `alloc_failed` · `self_test_failed` (SHA) · `cancelled` · `not_reached` · `native_call_failed:<نصّ>` · `native_result_unreadable:size=<n>` · `native_status_unknown:<n>` | **+ `skipped:time_budget`** (حرفياً، بنصّ القرار) | `skipped:time_budget` |
| `cpuKnownAnswerState` | `ran` · `not_run:<سبب>` | **+ `partial:time_budget`** | `partial:time_budget` [م] |
| سقف الزمن | لا سقف: ثلاثة تشغيلات في كل مرحلة دائماً | قاعدة §ب | `BUDGET_PERCENT_OF_PHASE` (=1) في `CpuKnownAnswerNames` |
| تخطيط JNI للغربال (`runSieveKnownAnswer`) | الإصدار 1، 5 خانات | **الإصدار 2، 6 خانات**: `[5]` = N. وتخطيط SHA يبقى الإصدار 1 (4 خانات) | `KnownAnswerLayout.SIEVE_VERSION` و`SHA_VERSION` (يحلّان محلّ `VERSION` الواحد) |

لا يتغيّر: `cpuKnownAnswerRuns[].t` و`tEnd` و`sieveDurationMs` و`sha256DurationMs` و`phase` و`plannedAtPct`، ولا `cpuKnownAnswerTotalMsByPhase` (والتشغيل المتروك بلا `t` ولا `tEnd` فلا يدخل مجموعاً). ولا يتغيّر SHA-256 (مخزن 1 MiB).

#### (أ) شكل التشغيل المتروك على السلك

* **حالة صريحة واحدة: `skipped:time_budget`، تُكتب في `sieveStatus` وفي `sha256Status` معاً** — كما يكتب `not_reached` النصفين معاً (`CpuKnownAnswer.kt:107,111`)، فيبقى لكل عنصر حالتان دائماً ولا يُقرأ غياب حالة ولا حقل حالة جديد. **اخترتُ هذا الموضع** (الأمر ترك الاختيار بين «حالتا الغربال وSHA» و«حالة للتشغيل»)؛ وسببه أن القارئ يقرأ الحالتين اليوم، وأن المتروك لم يبدأ نصفاه كلاهما.
* **مفاتيح التشغيل المتروك:** `phase` · `plannedAtPct` · `sieveStatus` · `sha256Status` فقط. **غائبة:** `t` · `tEnd` · `sievePrimeCount` · `sieveDurationMs` · `sha256DurationMs` · `sieveLimit` — **غياب لا صفر** (القاعدة 22): لا عدد ولا زمن ولا N لتشغيل لم يجرِ.
  ```json
  {"phase": 3, "plannedAtPct": 60, "sieveStatus": "skipped:time_budget", "sha256Status": "skipped:time_budget"}
  ```
* **الفرق عن الحالات القائمة:** `not_reached` = المرحلة انتهت قبل موعد التشغيل (لم يُطلب أصلاً) · `skipped:time_budget` = جاء الموعد وقالت قاعدة الزمن لا · `cancelled` = بدأ وقطعته المرحلة.
* **العنصر يبقى في القائمة:** كل مرحلة تسجّل ثلاثة عناصر دائماً (`register`، `CpuKnownAnswerScheduler.kt:59-65`)، فالمتروك عنصر بحالته لا عنصر غائب؛ وقارئ لا يفترض ثلاثة تشغيلات منفَّذة (وقائع الخادم المنقولة أعلاه).

#### (ب) السقف بالضبط (قاعدة المستخدم)

* **`P`** = `phaseDurationMs` الذي يمرّ إلى `runPhase`: مدّة المرحلة **المخطَّطة** `phase.durationMs` (`StressTestServiceV2.kt:282`) = **75,000** للمرحلة 3 و**60,000** للمرحلة 5 (`V2Models.kt:638,640`)؛ فـ1% = 750 و600 ms.
* **التقييم** لتشغيل الموضع k ≥ 2 في المرحلة، **عند موعده المخطَّط** (بعد انتظاره، قبل أيّ نداء أصلي):
  * `spent` = مجموع (`tEnd − t`) لتشغيلات **هذه المرحلة** التي بدأت؛ و`longest` = أطولها.
  * **يُترك إن كان `(spent + longest) × 100 > P × 1`** (حساب صحيح بلا فاصلة عائمة). **المساواة لا تُترك**، والزائد يُترك.
* **التشغيل الأوّل يجري دائماً:** لا تشغيل قبله فلا يُقيَّم شيء، ولا يُستدعى التقييم له.
* **الترك يشمل هذا التشغيل وكل ما بعده في المرحلة:** كلٌّ منها يُسجَّل `skipped:time_budget` **في لحظة القرار** (لا ينتظر مواعيده؛ لأن شيئاً لا يتغيّر بعد القرار: لا تشغيل جديد يضيف زمناً).
* **المرحلتان مستقلّتان:** الحساب لكل مرحلة وحدها؛ وترك تشغيلات المرحلة 3 لا يمسّ المرحلة 5.
* **`not_reached` يبقى كما هو:** المرحلة التي تنتهي (فتُلغى الكوروتين) قبل موعد تشغيل تقطعه أثناء الانتظار، قبل أن يصل التقييم؛ فيبقى `not_reached`.
* **تشغيل بلا `tEnd` (كيف يدخل الحساب):** حلقة المرحلة تعطي **كل** تشغيل بدأ `tEnd` (`CpuKnownAnswerScheduler.kt:118,136,158,165` عند `c22251f`) — **حتى فشل النداء الأصلي** (`native_call_failed:*`): يكمل التشغيل إلى SHA ثم `tEnd`، ويدخل الحساب بزمنه الحقيقي (قصير عادةً). فتشغيل بلا `tEnd` لا يبلغ التقييم في هذه الحلقة. **لكن الدالّة معرَّفة له** (القاعدة 22: لا غياب يُقرأ بالقياس): يُحسب زمنه **(الآن − `t`)** — ما أنفقه فعلاً حتى لحظة القرار، لا صفراً. «الآن» = `elapsedRealtime() − scanStart` (إطار `v[0]`).
* **مصدر واحد للقاعدة:** `CpuKnownAnswerBudget.exceeded(previous, nowMs, phaseDurationMs)` دالّة نقيّة في `CpuKnownAnswer.kt`، والنسبة `BUDGET_PERCENT_OF_PHASE` ثابت واحد في `CpuKnownAnswerNames`؛ والمجدوِل يستدعيها ولا يحسب بنفسه.

| تشغيلات سابقة في المرحلة (زمن كل منها) | `spent + longest` | على المرحلة 3 (حدّ 750) | القرار |
|---|---|---|---|
| لا شيء (التشغيل الأوّل) | — | — | يجري دائماً |
| 375 | 750 | = الحدّ | الثاني يجري |
| 376 | 752 | > الحدّ | الثاني والثالث يُتركان |
| 300 ثم 300 | 600 + 300 = 900 | > الحدّ | الثالث وحده يُترك |
| 250 ثم 250 | 500 + 250 = 750 | = الحدّ | الثالث يجري |

(أرقام الجدول مدخلات اختبار، لا قياس جهاز.)

#### (ج) الحالة الإجمالية `cpuKnownAnswerState`

| القيمة | الشرط |
|---|---|
| `ran` | نصف فحص واحد على الأقل أبلغ (`match` · `mismatch` · `alloc_failed` · `self_test_failed`، أي `EXECUTED_STATUSES`) **ولا تشغيل متروك** |
| **`partial:time_budget`** [م] | نصف فحص واحد على الأقل أبلغ **وتشغيل واحد على الأقل `skipped:time_budget`**. على نمط `partial:<reason>` القائم (`VerificationStates.kt:72-74`): **ما جرى حقيقي، وليس نجاحاً للمجموع** |
| `not_run:<سبب>` | لم يبلغ أيّ نصف (كما كان: `phase_not_reached` · `ai_cpu_workload_path_not_selected` · `no_check_reached` · `native_call_failed:<نصّ>`) — **ولو وُجد متروك** |

أثره على القارئ: من يقرأ `state === 'ran'` يقرأ الجزئية غير مكتملة (الاتجاه الآمن)، ومن يقرأ بادئة `not_run:` لا يلتقطها؛ **وعلى كل قارئ أن يعرفها قبل أن يرسلها أيّ تطبيق** (القاعدة 25).

#### (د) `sieveLimit`

* **المصدر:** `PRIME_SIEVE_1M_LIMIT` في `app/src/main/cpp/reference/reference_values.h`، مصدراً واحداً (القاعدة 24): تقرؤه `sieve_known_answer()` فيشغّل به الغربال ويُرجعه في خانة JNI `[5]`؛ **ولا نسخة من الرقم في Kotlin** (اختبار حارس يقرأ الترويسة نصّاً). فالقيمة المشحونة هي N **الذي شغّله الغربال**، لا N الذي يظنّه التطبيق.
* **غياب `sieveLimit`:** (1) تشغيل لم يبدأ (`not_reached` · `skipped:time_budget`)؛ (2) نداء الغربال رمى (`native_call_failed:*`) أو أعاد مصفوفة غير مقروءة (`native_result_unreadable:size=<n>`) — لا خانة تُقرأ. **حاضر** في كل مصفوفة مقروءة: `match` · `mismatch` · `alloc_failed` (N الذي حاول) · `native_status_unknown:<n>`.
* **قارئ الخادم:** المرجع دالّة في N (π(N))، فيُقرأ المرجع **بحسب `sieveLimit`** لا بثابت: 1,000,000 ← 78,498؛ وغيره غير معروف للتطبيق. **ملفّ مختوم 68 أو 69 لا يحمل `sieveLimit`** (كتبته نسخة لم تكن تشحنه)، وN فيه = 10,000,000 (`known_answer.cpp:17` عند `c22251f`) ومرجعه 664,579. فالغياب في ختم < 70 معروف السبب؛ والغياب في ختم ≥ 70 على تشغيل حالته مقروءة (`match` · `mismatch`) شذوذ.

#### (هـ) التغطية: D والذاكرة الرئيسية (نصّ القرار)

**D لم يعد يمرّ على الذاكرة الرئيسية، واختبار الذاكرة وحده يغطّيها.**

* مصفوفة الغربال صارت 1,000,001 بايت (0.9537 MiB) بعد أن كانت 10,000,001 بايت (9.5367 MiB) (حساب لا قياس). وأيّ مستوى من الذاكرة المخبّأة يتّسع لها على أيّ جهاز **غير مقيس** — النصّ قرار المستخدم، وليس خلاصة قياس هنا. SHA-256 مخزنه 1 MiB كما كان.
* **فعطل الذاكرة الرئيسية يراه اختبار الذاكرة (`memoryVerifyState` وحقوله) وحده.**

**متى لا يجري اختبار الذاكرة (فلا يغطّي شيءٌ الذاكرة الرئيسية في ذلك الفحص)** — قيم `memoryVerifyState` التي لا تعني تغطية كاملة، بأسمائها من الكود عند `c22251f`:

| `memoryVerifyState` | المعنى | المصدر |
|---|---|---|
| `not_run:no_v2_result` | لا نتيجة V2 أصلاً (نكهة V1، أو فشلت خدمة V2 قبل أن تعيد نتيجة): ملخّص افتراضي | `MemoryVerifyOutcome.kt:156` |
| `not_run:phase_not_reached` | انتهى الفحص قبل أن تبلغ المرحلة 2 (`MEMORY_STRESS`) هذا المنتِج | `StressTestServiceV2.kt:1219-1222` |
| `not_run:no_time_left` | أقلّ من 2,000 ms (`MIN_USEFUL_BUDGET_MS`) بقيت من المرحلة | `NativeVerification.kt:140-143` |
| `not_run:insufficient_free_memory` | الذاكرة الحرّة < 1012 MiB (§٧-هـ؛ `available_safe = free − 500 MiB < 512 MiB`)؛ **ظهرت على الـS10 في فحص الجهاز الثالث** (منقول من الأمر، غير متحقَّق من هذا المستودع) | `MemoryVerifyOutcome.kt:235-238` |
| `not_run:sysinfo_failed:<errno>` | فشل `sysinfo()` فلم تُعرف الذاكرة الحرّة | `MemoryVerifyOutcome.kt:222-225` |
| `not_run:allocation_failed:requested_bytes=<n>, <errno>` | فشل `mmap` حتى بعد المحاولة بنصف الحجم | `MemoryVerifyOutcome.kt:226-229` |
| `not_run:invalid_argument:time_cap_ms=<n>` | نُوديَ المنتِج بحجّة لا تصلح | `MemoryVerifyOutcome.kt:230-233` |
| `not_run:native_call_failed:<نصّ>` | رمى نداء JNI (مثلاً لم تُحمَّل المكتبة) | `MemoryVerifyOutcome.kt:171-175` |
| `not_run:native_result_unreadable:size=<n>` | المصفوفة الأصلية ليست التخطيط الذي يقرؤه هذا البناء | `MemoryVerifyOutcome.kt:186-191` |
| `not_run:native_status_unknown:<n>` | رمز حالة أصلي لا يعرفه هذا البناء | `MemoryVerifyOutcome.kt:239-242` |
| `partial:time_cap_reached` · `partial:cancelled` | جرى ولم يغطّ المخزن كلّه: `memoryVerifyBytes` أقلّ من المخطَّط (`memoryVerifyBufferBytes`) | `MemoryVerifyOutcome.kt:208-211` |

وفي هذه الحالات كلّها **لا يغطّي D ولا غيره الذاكرة الرئيسية**؛ فمن يقرأ تقريراً حالةُ ذاكرته `not_run:*` أو `partial:*` لا يستنتج من سلامة D شيئاً عن الذاكرة الرئيسية. **وD نفسه لا يجري** حين `cpuKnownAnswerState` = `not_run:*` (§ج)، وحين يُترك تشغيل يبقى ما جرى حقيقياً ولا يُغطّى ما تُرك.

#### (و) القرّاء (القاعدة 25) — في هذا المستودع

| الموضع (عند `c22251f`) | ما يقرؤه | أثر fv70 |
|---|---|---|
| `CpuKnownAnswer.kt:160-181` `SieveReading.fromNative` | مصفوفة الغربال بالفهرس | الإصدار 2 و6 خانات؛ `[5]` = N؛ **مصفوفة الإصدار 1 «غير مقروءة»** لا تُقرأ بخانة ناقصة |
| `CpuKnownAnswer.kt:189-209` `Sha256Reading.fromNative` | مصفوفة SHA | لا يتغيّر (الإصدار 1) إلا اسم ثابت الإصدار |
| `CpuKnownAnswer.kt:115-127` `CpuKnownAnswerRun.toWireMap` | ما يُكتب على السلك لكل عنصر | + `sieveLimit` متى كان له قيمة |
| `CpuKnownAnswer.kt:222-233` `CpuKnownAnswerState.of` | الحالات النصّية ← `ran` / `not_run:*` | + `partial:time_budget`؛ `skipped:time_budget` ليس في `EXECUTED_STATUSES` |
| `CpuKnownAnswer.kt:245-255` `CpuKnownAnswerTotals.byPhase` | `t` و`tEnd` | لا يتغيّر: المتروك بلا الطرفين فلا يدخل المجموع |
| `CpuKnownAnswerScheduler.kt:78-168` `runPhase` | المنتِج | قاعدة §ب قبل كل تشغيل بعد الأوّل |
| `BehaviorFingerprintBuilder.kt:548-552` | يكتب `cpuKnownAnswerState` و`Runs` و`TotalMsByPhase` | لا سطر جديد: العنصر يكتب نفسه بـ`toWireMap` |
| `VerificationWireNames.kt:80-82` (`SINCE_68`) | المفاتيح العليا لـD | لا مفتاح علوي جديد |
| `AppRequestPayloadGolden.kt` (و`AppRequestPayloadGoldenTest.kt`) | العيّنات الذهبية الثلاث | تُعاد توليدها وتُسمّى fv70 في التزام كل تغيير شكل (القاعدة 39)، **ومنها تشغيل `skipped:time_budget` من المنتِج الحقيقي** على ساعة مصنوعة |
| `FakeNativeVerification` · `CpuKnownAnswerSchedulerTest` · `CpuKnownAnswerWireTest` | بدائل الطبقة الأصلية والاختبارات | تنتج الآن مصفوفة الغربال بالإصدار 2 (القاعدة 41: العيّنة الاصطناعية تطابق ما يُنتجه المنتِج الحقيقي) |

**في `neuralcheck-backend` (منقول، غير متحقَّق من هذا المستودع):** ما ينقصه قبل أن يُرسل أيّ تطبيق fv70 يكتبه **وكيل الخادم بالأسماء الحرفية في تقرير التسليم**: `skipped:time_budget` (في `sieveStatus` و`sha256Status`) · `partial:time_budget` (في `cpuKnownAnswerState`) · `sieveLimit` (عنصر `cpuKnownAnswerRuns[]`) · ومرجع الغربال بحسب `sieveLimit`. **وهو نشر خادم قبل 1.0.12 أو معه** (بنصّ القرار). **وفي `wepsite`:** لا قارئ (§٤.٣).

#### (ز) ما لم يدخل fv70 في هذا الأمر

* **رقم النواة (القرار 2 (ب)): لم يُنفَّذ — الكلفة فوق نصف يوم.** (التقدير ومكوّناته أدناه؛ المسوّدة المقترحة إن قرّر المستخدم غير ذلك في تقرير التسليم.)

  | المكوّن | ساعات (تقدير عمل، غير مقيس) |
  |---|---|
  | الطبقة الأصلية: قراءة رقم النواة بـ`syscall(__NR_getcpu)` لأن `sched_getcpu` من API 29 و`minSdk = 28` (`app/build.gradle.kts:74`)؛ حقلا البداية والنهاية في بنيتَي الغربال وSHA ومصفوفتي JNI | 0.75 |
  | Kotlin: القراءة والتصنيف وحقول التشغيل وسلكه، وتمريرها في المسارات الأربعة التي تغلق فيها `runPhase` تشغيلاً (`CpuKnownAnswerScheduler.kt:112-121`، `:134-138`، `:152-161`، `:163-166`) | 1.5 |
  | الحالة المعلنة عند تعذّر القراءة (القاعدة 22): حالة لكل تشغيل وأسبابها، لا قيمة سالبة تُشحن | 0.5 |
  | الاختبارات: المصفوفتان، وسلك العنصر، والمجدوِل (البداية والنهاية والتشغيل المقطوع)، والعيّنات الذهبية | 2.0 |
  | العقد و`MEASUREMENT_SPEC.md` | 0.75 |
  | **المجموع** | **5.5** (نطاق 4.5–6.5) |

  **وفوق ذلك خطر لا تقيسه JVM:** نداء نظام يردّه مرشّح seccomp **يقتل العملية** (SIGSYS) لا يعيد `errno` — فلا «حالة معلنة» تحميه؛ ولا أعرف يقيناً أنه مسموح على كل جهاز، ولا قياس بلا جهاز. وتصميم مصغَّر بلا حالة معلنة ≈ 3 ساعات لكنه يخالف القاعدة 22 (الغياب بلا حالة). **فيُؤجَّل، بنصّ القرار («وإلا يُؤجَّل»)،** ويُضاف إلى fv70 قبل البناء إن قرّر المستخدم.
* **P6-13:** ينتقل إلى **fv71**، لا fv70 (قرار المستخدم 2026-10-13، النصّ في أوّل القسم؛ منقول، غير متحقَّق). لا حقل منه ولا مكان محجوز له في هذا القسم.
* **`runPrimeSieveTest`** (`production_stress.cpp:396-397`، `StressNative.kt:39,328`): يبقى على 10,000,000؛ ليس D.

#### للجهاز (جولة الفحص الميداني الواحدة، القاعدة 19)

* **S10 وS21:** مجموع زمن D في كل مرحلة ÷ زمنها (`cpuKnownAnswerTotalMsByPhase` على زمن المرحلة من العيّنات) ≤ 1%؛ وكم تشغيلاً `skipped:time_budget` على كل جهاز؛ و`sieveLimit` = 1,000,000 و`sievePrimeCount` = 78,498 على التشغيلات المنفَّذة؛ وأيّ جهاز تبلغه الحالة `partial:time_budget`.
* **الذاكرة:** قيم `memoryVerifyState` على الأجهزة كلّها، وكم جهازاً في كل `not_run:*` (§هـ) — لأن اختبار الذاكرة صار الوحيد الذي يغطّي الذاكرة الرئيسية.

#### حالة التنفيذ

مصدر الأعداد: ملفّات XML لمخرَج `cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest` (سكربت يجمع `tests` و`failures` و`errors` و`skipped` من `app/build/test-results/testLaunchDebugUnitTest/TEST-*.xml`، بـjbr `openjdk version "21.0.9" 2025-10-21` داخل الأمر وحده) بعد كل التزام. الأساس `c22251f`: 150 ملفّاً · 1269 اختباراً. **الختم 70 وُضع في أول التزام يغيّر الشكل (`0a86915`)؛ وfv70 غير مكتمل بالتصميم حتى الالتزام الأخير؛ ولا يُبنى APK قبله، ولا بُني APK في هذه الدفعة.**

| الخطوة | الالتزام | ما غيّره | ملفّات XML · اختبارات (كلّها 0 فشل · 0 خطأ · 0 تخطٍّ) |
|---|---|---|---|
| الأساس | `c22251f` | — | 150 · 1269 |
| العقد (هذا القسم) | `64719a9` | وثيقة | بلا كود |
| الغربال 1,000,000 + `sieveLimit` + **الختم 70** | `0a86915` | ثابتا `PRIME_SIEVE_1M_*` · بنية `SieveKnownAnswer.limit` · تخطيط JNI للغربال 2 (6 خانات) · `KnownAnswerLayout.SIEVE_VERSION`/`SHA_VERSION` · `SieveReading.limit` · `CpuKnownAnswerRun.sieveLimit` · العيّنات الذهبية أُعيدت وسُمّيت fv70 (و`.gitattributes`) · `KnownAnswerReferenceTest` | 151 · 1281 |
| سقف الزمن | `962e130` | `CpuKnownAnswerBudget` · `runPhase` تسأله وتترك · `skipped:time_budget` · `partial:time_budget` · تشغيل متروك في العيّنة 11 عموداً من المنتِج الحقيقي | 152 · 1308 |
| الوثائق | هذا الالتزام | هذا القسم + `MEASUREMENT_SPEC.md` | لا كود (الأعداد كما في الالتزام السابق) |

**الطبقة الأصلية (بالإذن القائم: بناء `.so` وحده، لا APK):**
* الفحص النحويّ: `clang++ --target=aarch64-linux-android28 -std=c++17 -fsyntax-only -Wall -Wextra -I app/src/main/cpp` (NDK `27.1.12297006`) على `stress/known_answer.cpp` و`jni/stress_jni.cpp` ⇒ بلا مخرَج، رمز الخروج 0؛ **وضابط سلبي** (ثابت مكتوب خطأً في نسخة مؤقّتة خارج المستودع) ⇒ `error: use of undeclared identifier` ورمز 1، فالفحص حيّ لا فارغ.
* `./gradlew --no-daemon :app:externalNativeBuildLaunchDebug` (بسطر jbr داخل الأمر) ⇒ `BUILD SUCCESSFUL in 11s`؛ `libstress.so` (`obj/arm64-v8a`) 1,256,696 بايت (كان 1,256,560 في §٧-هـ)، ويحمل سلسلة السجلّ الجديدة `runSieveKnownAnswer: status=%d limit=%lld` (بحث نصّي في الملفّ).
* **لا APK:** قائمة `app/build/outputs/apk` (4 ملفّات: APKان قديمان وملفّا `output-metadata.json`، بأحجامهم وأوقاتهم) **مطابقة قبل البناء وبعده** (`diff` بلا فرق).
* **ما لا يثبته هذا كلّه (القاعدة 37):** أن النواة المترجمة تعدّ 78,498 فعلاً. `KnownAnswerReferenceTest` يثبت على JVM أن المرجع هو π(الحدّ) بغربال عادي، وأن الطبقة الأصلية تُرجع N في الخانة 5؛ والعدّ الفعلي على الجهاز.

**sha256 العيّنات الذهبية** (`app/src/test/resources/derived/`، الاسم بعد `fv70-app-request-payload-derived-from-fv67-golden-34dff2b8-`؛ القبل = ملفّات fv69 عند `c22251f` مأخوذة بـ`git show c22251f:<path> | sha256sum`، والبعد = الملفّات المرحَّلة في `962e130`):

| الملفّ | sha256 قبل (fv69، `c22251f`) | sha256 بعد (fv70، `962e130`) |
|---|---|---|
| `…11ch-nothing-admitted.json` | `2e8b03444a8e29ffa37a06aca94e87f25130aeae2fdb412b290816cbd9425672` | `6d5e06fb0275e4174412f04bef66e312ba6d614e18e498d72d663f912d38d538` |
| `…11ch-nothing-admitted.keys.txt` | `5c75fb73f12e395f678762d806f1be8ffcbe5163da2bed3509a99979dc31a9b8` | `a1eaa4810d703b7a8f4aff6d5b61548717b1483ea90068a7db36968aaea08742` |
| `…13ch-selinux-blocked-policy.json` | `0d40ab66dd37034cc16a1c89b53a9729b63255caae36c35ab38f8375be1be1a9` | `3ccfd1d377dbb80bb32e385df2fbf0a87fb9e57bcec82fb6fbe958c55f329ff3` |
| `…13ch-selinux-blocked-policy.keys.txt` | `e60894a7ed099162b26975ce2366bf5d4125df22fff7aa6b202ea5cd50749dd6` | `096bbe4b1b0fa451a4bd8cf68b64a266434132b967e68e39d30041eb849c214c` |
| `…16ch-four-policies-truncated-failures.json` | `b42e8211fef8e1920b841fd2c962b23ef990f31a60f4d2365695c65aaa74b041` | `c413f9820b2a39b524750c91cdb1fe5fff2dc11867b0ad502b717784d9cd88cd` |
| `…16ch-four-policies-truncated-failures.keys.txt` | `86d1675fc49d49df46387bc73cb3da1e3123897abf0222eb17bd76651ba8ed6b` | `32a8c4e2f282c448e2e987195de6c8a5e986f78933a3fe4cd9c5b2656ed8e02d` |

(بين `0a86915` و`962e130` تغيّر ملفّ واحد: `11ch…json` من `6db90e0e…` إلى `6d5e06fb…`؛ وملفّات المفاتيح لم تتغيّر لأن كل مفتاح ظاهر في عيّنة أخرى.)

**تحديث دفعة الواجهة 1.0.12 (القسم الأوّل، 2026-10-13): هذا الجدول سجلّ D لا غير** — ما غيّره إصلاح D بين `c22251f` و`962e130`، وهو صادق بهذا المعنى فلم يُعدَّل. **والعيّنات الستّ أُعيد توليدها بعده** في `80ca8c3` (صفّ L-02 من منتِجه الحقيقي وصفّا SN-08/SN-09 جديدان، بلا تغيير ختم: fv70 ولا مفتاح جديد على السلك)، فلا تُقرأ أرقام `بعد` أعلاه مرجعاً للملفّات الحالية: **sha256 الحالية في `app/src/test/resources/derived/README.md` — كتلة `golden-sha256` في آخره وجدول «before and after» في قسم «Batch 1.0.12»** — ومنها يُنسخ إلى الخادم. **القرار وسببه:** أحَلتُ ولم أُحدّث الجدول، لأن تحديثه كان سيُنتج جدولاً يزعم أن D غيّر ما غيّره 1.0.12 (فيفسد ما يقوله عن D)، ولأن نسخة ثانية من الأرقام في العقد تنجرف عن README الذي يتحقّق منه الاختبار كل تشغيل (القاعدتان 28 و29).

##### الفروق عمّا في الأمر والعقد (القاعدة 29)

1. **«في fv70 مع P6-13 إن دخلا 1.0.12 معاً»** (نصّ القرار الأوّل) **تجاوزه قرار المستخدم 2026-10-13** («Not in 1.0.12; fv71 (Recommended)»؛ منقول، غير متحقَّق): P6-13 في fv71. لا مكان له في fv70.
2. **رقم النواة (القرار 2 (ب)) لم يُنفَّذ:** التقدير 5.5 ساعة (نطاق 4.5–6.5) فوق نصف يوم، وفيه خطر seccomp لا تقيسه JVM (§ز). بنصّ القرار «وإلا يُؤجَّل».
3. **موضع `skipped:time_budget`:** الأمر ترك الاختيار («حالتا الغربال وSHA أم حالة للتشغيل»)؛ **اخترتُ حالتي الغربال وSHA معاً** (§أ).
4. **«تشغيل بلا `tEnd` (فشل أصلي)»:** في الكود كل تشغيل بدأ يأخذ `tEnd` حتى لو فشل نداؤه الأصلي (يكمل إلى SHA)؛ فتشغيل بلا `tEnd` لا يبلغ التقييم في الحلقة. الدالّة معرَّفة له مع ذلك: (الآن − `t`) (§ب). الاختبار: `CpuKnownAnswerBudgetTest`.
5. **الحالة الإجمالية `partial:time_budget` اسم [م]** (الأمر قال «ومعه الحالة الإجمالية» ولم يسمِّها)، على نمط `partial:<سبب>` القائم.
6. **«الزمن حتى الآن» في نصّ القرار قُرئ كزمن D نفسه في المرحلة** (مجموع `tEnd − t`)، لا زمن المرحلة المنقضي؛ وهذه قراءة المنسّق في الأمر («مجموع `tEnd − t` لتشغيلات المرحلة المكتملة + أطولها»): لو كان المقصود زمن المرحلة المنقضي لتُرك كل تشغيل بعد الأوّل دائماً (عند 25% من المرحلة الزمن المنقضي وحده أكبر من 1%).
7. **القرّاء «بالأداة»:** `git grep -n "sievePrimeCount\|sieveStatus\|sha256Status\|SIEVE_STATUS\|STATUS_NOT_REACHED\|EXECUTED_STATUSES\|cpuKnownAnswer\|CpuKnownAnswerRun\b" -- app/src/main` ⇒ لا قارئ لحالات D في `main` غير `CpuKnownAnswerState.of` و`CpuKnownAnswerTotals.byPhase` (وكلاهما في §و) وكاتب `toWireMap` و`BehaviorFingerprintBuilder.kt` (يكتب الثلاثة بلا تغيير) و`V2Models.kt` (الحقول).

##### جرد القيم الافتراضية والفروع الافتراضية الجديدة (القاعدة 22، الملحق)

| المسار:السطر (عند `962e130`) | القيمة | السبب المكتوب | الحكم |
|---|---|---|---|
| `CpuKnownAnswer.kt:330` `if (!anyBegan) return false` | لا تشغيل سابق بدأ ⇒ لا يُتجاوز السقف | نصّ المستخدم: «والتشغيل الأوّل يجري دائماً» | يبقى؛ ليس سدّاً لغياب بل نصّ القاعدة |
| `CpuKnownAnswer.kt:324` `(r.tEnd ?: nowMs) - start` | تشغيل بدأ بلا نهاية يُحسب (الآن − t) | القاعدة 22: لا يُقرأ الغياب صفراً (§ب) | يبقى؛ لا يحدث في الحلقة (كل مسار يعطي `tEnd`) |
| `CpuKnownAnswer.kt:141` `sieveLimit: Long? = null` | غائب | تشغيل لم يبدأ أو لا قراءة للغربال: لا N | يبقى؛ الغياب هو القيمة ولا يسدّه شيء |
| `CpuKnownAnswer.kt:217` `val limit = result[SIEVE_IDX_LIMIT]` | القيمة كما جاءت، بلا قصّ ولا افتراض | الخام يُشحن كما هو (القاعدة 20) | يبقى |
| `CpuKnownAnswer.kt:286` فرع `partial:` | يُبلَّغ فقط إن أبلغ نصف | لا `partial` من لا شيء: بلا نصف مُبلَّغ يبقى `not_run:*` | يبقى |
| `CpuKnownAnswerScheduler.kt:82` `skip(...)` | يكتب الحالتين فقط ويترك غيرهما غائباً | المتروك لم يبدأ نصفاه | يبقى |
| `CpuKnownAnswer.kt:171` `SHA_VERSION = 1L` | تخطيط SHA لم يتغيّر | الأمر لا يمسّ SHA | يبقى |

##### قيود معروفة لم تُصلَح (من هذه الدفعة)

* **رقم النواة لم يُنفَّذ** (فوق نصف يوم): فحص «لماذا الـS21 أبطأ» لا يرى على أيّ نواة جرى D. ينتظر قرار المستخدم (التقدير في §ز).
* **زمن D بعد غربال 1,000,000 غير مقيس:** القياس المنقول (§٧-ط أوّله) لغربال 10,000,000؛ لا أعرف ما يصير عليه مجموع D في المرحلة على S10 وS21، ولا هل يُترك تشغيل. يُقاس في فحص الجهاز القادم (§للجهاز).
* **`VERIFICATION_REF_VERSION` في `reference_values.h` (=1) لا يقرؤه أحد** (`git grep -n VERIFICATION_REF_VERSION -- app` ⇒ سطران في الملفّ نفسه: التعليق والتعريف)، والتعليق يقول إنه يُكتب في التذييل ويُرفع مع كل مرجع جديد؛ **لم يُرفع** لمرجع D الجديد لأنه لا يُقرأ. لم أمسّه (خارج الأمر).
* **سقف الزمن لا يعرف الحمل المقيس:** يحسب زمن D وحده (مدّة التشغيل بإطار `v[0]`)، لا أثره الحقيقي على الحمل المستدام؛ ومعيار القبول يُحسم على الجهاز كما قرّر المستخدم 2026-10-08.
* **الأسماء [م] الجديدة تنتظر إقرار المنسّق:** `partial:time_budget` (الحالة الإجمالية) · `CpuKnownAnswerBudget` · `BUDGET_PERCENT_OF_PHASE` · `KnownAnswerLayout.SIEVE_VERSION`/`SHA_VERSION`/`SIEVE_IDX_LIMIT` · `PRIME_SIEVE_1M_LIMIT`/`PRIME_SIEVE_1M_REFERENCE`. **أُقرّت بنصّ المستخدم:** `sieveLimit` · `skipped:time_budget`.

### ٧-ي — fv71: M-02 الشبكة الجديدة بالخامّ · أزرار الصوت M-09/M-10 · P6-13 (توحيد الظروف)، 2026-10-15 +03

**هذا القسم يسبق الكود (القاعدة 7): يُلتزَم وحده؛ لا كود لـfv71 فيه، ولا رفع لـ`fieldsVersion` (يبقى 70: `app/build.gradle.kts:486` [ك] عند `94e0035`).** مصدره أمر المنسّق 2026-10-15 (البند ٤ من دفعته الأولى؛ أساس `94e0035`، فرع `master`) والمستخلص الملحق به (`evidence/station2-plan/p623-contract-brief-2026-10-15.md`: §٠ و§C وأسئلة E18–E22). **منقول، غير متحقَّق من هذا المستودع [ن]:** المستخلص نفسه وما يُحيل إليه في مساحة العمل (M2 تصميم M-02 · M2U قرار المستخدم بعده · VK تشخيص أزرار الصوت · VKU قراره · P خطة PLAN-06 · S2 خطة المحطّة ٢)، **وأرقام الأسطر المرافقة (`M2:184`، `VK:193`، `P:1431`…) هي أسطر ملفّات مساحة العمل لا أسطر هذا المستودع**؛ وأحجام M-02 بالبايت منقولة «بلا مصدر محفوظ» (M2:15). **المتحقَّق منه [ك]:** كل `ملفّ:سطر` وسمه `[ك]` أدناه مقروء عند `94e0035`، والسطر ينجرف بعد أول التزام كود والقيمة هي المرجع (`git grep -n`). **الوسوم:** `[ك]` كود · `[ن]` منقول · `[م]` مقترح من وكيل التطبيق، ينتظر الإقرار — وأقصد به حرفياً «مقترح، للاعتماد».

**تحديث 2026-10-18 (A-FV71C، بقيّة الوسوم) — ما اعتُمد وما لم يُعتمد:** اعتمد المستخدم بنصّه 2026-10-15 ~18:22 +03 (`evidence/station2-plan/user-approval-texts-fv71-2026-10-15.md:25-29`؛ منقول، غير متحقَّق من هذا المستودع [ن]): «E18 (سقف 15,000 عيّنة معلَن)، وE19، وE20، وE21، وE22 (M-09/M-10: «يعمل» يفتح الحوار والكشف تلقائي، و«يعمل» اليدوي باقٍ بـ user_marked_working، وendReason يميّز الطريقين): معتمدة. الأسماء N71-01 إلى N71-08: معتمدة. والتصحيحات الثلاثة في المستخلص: صحّحها في المصدر. ونصوص E21 الستّة (الشبكة الجديدة): يقترحها الوكيل في أمرها، وتأتيني للاعتماد.» **فيُقرأ وسم `[م]` معتمداً على:** E18 · E19 · E20 · E21 (١–٤) · E22 · الأسماء N71-01..08. **ويبقى مقترحاً (لا يظهر اعتماده في ملفّي الاعتماد 2026-10-15):** نصوص E21 الستّة (البند ٥: لا نصّ منها في هذه الوثيقة ولا في `strings.xml`) · بنود «نقاط وجدتها» ٣–٧ (التصحيحات الثلاثة المعتمدة هي ما سمّاه `evidence/station2-plan/decisions-needed-after-batch1-2026-10-15.md:83`: `gridBasis` حدّه `< 71`، و`endReason` معدَّدة، و`partial:time_budget` من fv70 — وهي مكتوبة في هذا القسم أصلاً) · أسماء التصميم M2:184-189 · وكل `[م]` على نوع أو غياب أو قيمة معدَّدة لم يُسمَّ في E18–E22 ولا N71-01..08. **لم يتغيّر هنا اسم حقل ولا قيمة ولا دلالة؛ الوسوم وحدها.**

**قرارات المستخدم لهذه الحمولة، بنصّها (منقولة في المستخلص §C؛ [ن]):**
1. M-02: «Station-2 build, fv71 (Recommended)» و«(b) Raw touch path; server detects (Recommended)» (M2U:28 · M2U:32).
2. أزرار الصوت: «Station 2, fv71, with raw (Recommended)» (VKU:20).
3. P6-13: «Not in 1.0.12; fv71 (Recommended)» (`evidence/build-1.0.12/user-answers-1.0.12-package-2026-10-13.md:19`؛ P:1054).
4. الموضع: «The fv71 payload goes as a new section of docs/FV68_CONTRACT.md in NeuralChek, where fv68 to fv70 already live. The app agent writes it, added as item 4 of its first batch (about 0.5 day, my estimate). It is copied to the backend before the fv71 readers start» (`evidence/station2-plan/user-answers-p623-open-questions-2026-10-15.md` البند ١).
5. الختم والترتيب: «fv70 مختوم» (VK:208) · fv71 «عند أوّل التزام يغيّر الشكل» (VK:207) · «ونشر قرّاء الخادم قبل أيّ تطبيق يرسل fv71» (S2:81).

**نطاق هذا القسم:**
* **داخل:** (١) M-02 الشبكة الجديدة بالخامّ (الخيار ب) — ف-01 إلى ف-03 · (٢) أزرار الصوت M-09/M-10 بأحداث المفاتيح مع الخامّ — ف-04 · (٣) P6-13 توحيد الظروف (`powerSaveState` و`brightnessLock` وحدّا الشحن 30–85%) — ف-05.
* **خارج fv71:**
  * **`partial:time_budget` ليس من fv71** (تصحيح المنسّق، القاعدة 14): هي الحالة الإجمالية `cpuKnownAnswerState` لفحص المعالج بالجواب المعروف (D)، وهي **fv70** [ك: §٧-ط (ج) أعلاه؛ `CpuKnownAnswer.kt:92` و`:264`؛ `V2Models.kt:473`]، وقرّاؤها منشورون [ن]. ولا «حالة ميزانية زمن» في تصميم M-02: زمنه هدفُ زمن الفنّي «≤ 30–40 ث» لا حقل [ن]؛ وجمع الاثنين في صفّ M-02 منقول عن S2:40 لا عن التصميم.
  * **رقم نواة D** (مشروط في fv71، S2:109 · S2A:12 [ن]): ينتظر قراءة ج٧، **ولا أسماء حقول له في المصادر المقروءة** [ن]؛ وتقديره 5.5 ساعة مع خطر seccomp في §٧-ط (ز) [ك]. فلا يدخل هذا القسم ولا أسمّي له حقلاً.
  * **حقول P6-01** (مكوّنات بصمة العتاد بحالاتها، وحالة Widevine): خارج fv71 باقتراحي في E20 أدناه (معتمد، المستخدم 2026-10-15 ~18:22 +03، `evidence/station2-plan/user-approval-texts-fv71-2026-10-15.md:26`).
* **الختم:** `fieldsVersion` **يبقى 70**. **71 يوضع في أوّل التزام كود يغيّر الشكل** في أمر لاحق؛ والتزامات الشكل التالية تحمله وهي ناقصة بالتصميم حتى الأخير (ت-19)؛ **ولا APK**. و**نشر قرّاء الخادم يسبق أيّ تطبيق يرسل fv71** [ن] (القاعدة 25).

**الحال اليوم (fv70 عند `94e0035`) [ك]:**
* **M-02:** شبكة **ثابتة** 8 × 15 (`TouchGridRecorder.kt:35-36`) على سطح ملء الشاشة؛ خامّ من ثمانية مفاتيح (`TouchGridRecorder.Keys`، `:65`) يبنيه `measurements(endReason)` (`:130`) — **لا مسار لمس ولا أبعاد سطح ولا كثافة ولا حواف**. عنوان الحوار وسطر العدّ والزرّان بعد 15 ث **طبقات فوق الشبكة** (`ManualTestDialogs.kt:332` للأعلى و`:355` للأسفل): العنوان شفّاف للمس (تعليق الكود: «taps fall through to the grid beneath»)، **أمّا الزرّان فيلتقطان اللمس** — فالخلايا تحتهما لا يصلها لمس. قيم `endReason` الأربع **معدَّدة** في `TouchGridRecorder.EndReasons` (`:56`): `auto_complete` · `user_marked_not_working` · `user_marked_not_tested` · `dismissed`.
* **M-09/M-10:** `VolumeButtonTestDialog` (`ManualTestDialogs.kt:752`) يستطلع `STREAM_MUSIC` كل 120 م.ث (`:785`؛ تعليق الدالّة يقول 150) بعد ضبط المستوى إلى نصف المدى (`:773`) ويكتب `pass` حين يتحرّك المستوى (`:788`) — **بلا `measurements` وبلا أحداث مفاتيح**. وM-09/M-10 ليسا في `ManualChipActions.PASS_OPENS_DIALOG` (`ManualChipActions.kt:36` = M-02 وحده). **تحديث 2026-10-18 (التزام 5f90ce1، أمر المستخدم 2026-10-18):** المجموعة فارغة اليوم (`ManualChipActions.kt:40`: `val PASS_OPENS_DIALOG: Set<String> = emptySet()`) — أُفرغت من M-02 بقراره «تأشير «يعمل» لا يفتح الاختبار تلقائياً، بل يؤشّر الفنّي بلا فتحه. باقي الفحوص اليدوية لا تُمسّ.»؛ فنقرة «يعمل» على M-02 تكتب `pass` بلا فتح ولا تمحو خامّاً محفوظاً (`PASS_KEEPS_STORED_RAW` · `rawToStore`، `:47` و`:73`)؛ وM-09/M-10 ليسا فيها أيضاً.
* **P6-13:** `PRE_FLIGHT_MIN_BATTERY_PCT = 25` (`StressTestService.kt:243`) تقرؤه أربعة مواضع (`StressTestService.kt:285` و`:587` · `ActiveScanScreen.kt:580` · `PreScanViewModel.kt:382-384`)؛ **ولا حدّ أعلى**؛ والبوّابة **متسامحة مع المجهول** (`isPreflightBatteryBlocked` `:285`: مستوى غير معروف يمرّ). وضع توفير الطاقة: `powerManager?.isPowerSaveMode == true` وفشل القراءة (أو غياب الخدمة) ⇒ **`false`** (`StressTestService.kt:663-667`)، و`true` يوقف الفحص (`POWER_SAVE_MODE`) فلا يصل `true` إلى الحمولة؛ يُخزَّن `Boolean` في ترويسة الملفّ (`TimeSeriesFileWriter.kt:541`، `= false` افتراضاً) ومنه `isPowerSaveMode: Boolean?` (`AiAnalysisRequest.kt:92`) و`powerSaveMode: Boolean?` في جذر البصمة (`:42`؛ `BehaviorFingerprintBuilder.kt:114-115`). **لا كود لسطوع الشاشة** ولا لـWidevine (`git grep -n -i "brightness" -- app/src/main` و`git grep -n -i "widevine\|MediaDrm" -- app/src/main` ⇒ لا نتائج).

#### ما يتغيّر في الحمولة (fv70 ← fv71)

| الموضع | fv70 | fv71 | الأسماء |
|---|---|---|---|
| `manualResults[M-02].measurements` | ثمانية مفاتيح؛ شبكة 8 × 15 ثابتة | **باقية بدلالتها:** `gridCols` `gridRows` `totalCells` `touchedCells` `elapsedMs` `timeToFirstTouchMs` `endReason` `maxSimultaneousPointers`؛ **جديدة:** السطح والشاشة والكثافة والخليّة والحالة النهائية والحواف (ف-02)، و**الخامّ** `strokes[]` وما معه (ف-03) | أسماء التصميم M2 (ف-02، ف-03)؛ وما أقترحه موسوم `[م]` |
| `manualResults[M-09].measurements` · `[M-10]` | غائب (`null`) | الخامّ في ف-04 (`expectedKeyCode` · `events[]` · …) | أسماء VK:193-203 `[م]` + اقتراحاتي |
| `footer` (تذييل الملفّ ثم الحمولة) | لا `brightnessLock` | `brightnessLock` (ف-05) | `[م]` |
| ترويسة الملفّ وجذر البصمة | `isPowerSaveMode` / `powerSaveMode`: `Boolean` | + `powerSaveState` بجوارهما (ف-05) | `powerSaveState` (P:1433) |
| جذر الحمولة/التذييل | لا إعلان لنافذة الشحن | `batteryWindowPct` (ف-05) | `[م]` |

**لا يتغيّر:** `batteryLevelStart` خامّاً كما اليوم (P:1430 [ن]؛ `TimeSeriesFileWriter.kt:539` [ك])؛ حقول `manualResults[].status` (الحالات الأربع)؛ `manualResultsHeader`؛ وكل ما في fv70 (الغربال وحالات D).

#### ف-01 — M-02: ما يبقى بدلالته وما يتغيّر في دلالته

| المفتاح | النوع · الدلالة | الغياب (القاعدة 22) | ما يُعلنه القارئ إن لم يجده (القاعدة 25) | الوسم |
|---|---|---|---|---|
| `gridCols` · `gridRows` | عدد صحيح؛ **لم يعودا 8 و15 دائماً**: يُشتقّان من `surface*Px` و`cellTargetMm` (الخليّة مستهدفة بالميلّيمتر) | حاضران دائماً في أيّ خامّ يُكتب | غيابهما في ختم ≥ 71 ⇒ شذوذ | M2:182 [ن] |
| `totalCells` | عدد صحيح؛ **العلاقة الملزمة:** `totalCells = gridCols × gridRows − عدد cutoutCells` (M2:190) | حاضر دائماً | عدم تحقّق العلاقة ⇒ شذوذ يُعلنه القارئ | M2:182، 190 [ن] |
| `touchedCells` | عدد صحيح، **0 قيمة حقيقية** («لم تُلمَس خليّة»)؛ لا يُكتب خامّ أصلاً إن لم تقع لمسة (§ف-03 «الإغلاق») | حاضر دائماً في أيّ خامّ يُكتب | — | M2:182 |
| `elapsedMs` | من **أوّل لمسة** إلى نهاية التشغيل، بساعة `elapsedRealtime` (`TouchGridRecorder` تُمرَّر لها `SystemClock.elapsedRealtime` [ك: `TouchGridTestDialog`، `ManualTestDialogs.kt:235`]) | **غائب** إن لم تقع لمسة (لا صفر) [ك: `TouchGridRecorder.kt:130-146`] | — | M2:182 · fv69 §٧-ح (ب) |
| `timeToFirstTouchMs` | من فتح الحوار إلى أوّل لمسة | **غائب** إن لم تقع لمسة | — | كما سبق |
| `endReason` | نصّ؛ **القيم الأربع نفسها** المعدَّدة في `TouchGridRecorder.EndReasons` (`:56`) [ك]: `auto_complete` · `user_marked_not_working` · `user_marked_not_tested` · `dismissed`. **دلالة `auto_complete` تتغيّر:** كل خلايا الشبكة **عدا `cutoutCells`** مُلوَّنة (لا «كل 120») | حاضر دائماً | قيمة غير هذه الأربع ⇒ `UNRECOGNIZED` (M2:243-260 [ن]) | M2:182 [ن] · E19 |
| `maxSimultaneousPointers` | عدد صحيح (0 إن لم تقع لمسة) | حاضر دائماً | — | M2:182 |

**الفاصل بين شبكتين (ملاحظة قارئ):** `gridBasis` يحسبه القارئ على الخادم لا التطبيق (M2:243-260 [ن]): **`FIXED_8X15` لكل ختم < 71، و`MM_TARGET` لكل ختم ≥ 71.** المستخلص يكتب «`FIXED_8X15` حتى fv69»؛ **والكود يخالفه**: M-02 عند `94e0035` والختم 70 ما زال 8 × 15 (`TouchGridRecorder.kt:35-36`؛ fv70 لم يمسّ M-02) — فالحدّ `< 71` لا `≤ 69` (القاعدة 14؛ انظر «نقاط وجدتها»). «لا تجوز مقارنة رقمين من إصدارين (`touchedCells` · `totalCells`) إلا بعد قراءة `gridBasis`» (M2:264 [ن]).

#### ف-02 — M-02: الحقول الجديدة (أسماء التصميم M2:184-189؛ كلّها `measurements` لـM-02)

**اسم كل حقل أدناه هو اسم وكيل التصميم في وثيقة معتمدة [ن]، لا اسم موجود في الكود.** وما أضيفه أنا (النوع حيث لم يُحدَّد، وحالات الغياب، وما يُعلنه القارئ) موسوم `[م]`.

| المجموعة | الحقول | النوع والدلالة | الغياب · ما يُعلنه القارئ |
|---|---|---|---|
| السطح (M2:184) | `surfaceWidthPx` `surfaceHeightPx` `surfaceOriginXPx` `surfaceOriginYPx` | عدد صحيح بالبكسل: مقاسات السطح الذي تُحسب عليه الخلايا (ما يتلقّى اللمس) ومنشؤه بإطار النافذة | **حاضرة دائماً** في أيّ خامّ يُكتب (لا لمس بلا سطح مُقاس). غياب أيٍّ منها في ختم ≥ 71 ⇒ شذوذ |
| الشاشة (M2:185) | `displayWidthPx` `displayHeightPx` `modePhysicalWidthPx` `modePhysicalHeightPx` | عدد صحيح؛ مقاس العرض الحالي ومقاس الوضع الفيزيائي (`Display.Mode`) | غياب بسبب فشل قراءة ⇒ مفتاح غائب **وسببه في `absentFieldReasons`** (N71-01)؛ والقارئ يُعلن «غير معروف» ولا يستنتج النسبة |
| الكثافة (M2:186) | `xdpi` `ydpi` `densityDpi` `pxPerMm` `densitySource` `densityCheck` | `xdpi`/`ydpi`/`pxPerMm` عشرية، `densityDpi` عدد صحيح، `pxPerMm` اشتقاق عكوس من الخامّ (القاعدة 20: التحويل العكوس بلا فقد مسموح). `densitySource` نصّ ∈ `XDPI` · `XDPI_MODE_SCALED` (M2:52) · `DENSITY_DPI_FALLBACK` (M2:54) | `densityCheck`: **قائمة نصوص** بالشروط التي فشلت، **حاضرة دائماً**، `[]` = لم يفشل شرط [م: M2:54 يربطها بالاحتياط ولا يحدّد حضورها مع غيره؛ اقتراحي الحضور الدائم كي لا يُقرأ الغياب نجاحاً]؛ **أسماء الشروط لا أخترعها هنا** (غير منقولة إليّ): تُعدَّد في أمر البناء من M2:52-54 من **مصدر واحد مُصدَّر** (القاعدة 24). مُدخلات الكثافة غائبة ⇒ الخادم `UNDETERMINED` (M2:243-260 [ن]) |
| الخليّة (M2:187) | `cellTargetMm` `cellWidthPx` `cellHeightPx` | `cellTargetMm` عشرية (الهدف بالميلّيمتر؛ قيمته ثابت في التطبيق تُنقل في أمر البناء من التصميم)، والعرض والارتفاع بالبكسل الفعليين (قد يختلفان بالتقريب) | حاضرة دائماً |
| الحالة النهائية (M2:188) | `untouchedCells` `cutoutCells` | **قائمتا فهارس خلايا**: `index = row × gridCols + col` [م — فهرس الخلية في `TouchGridRecorder` هو هذا (`touched[row * GRID_COLS + col]`، `TouchGridRecorder.kt:114`)؛ M2:188 لا يحدّد عنصر القائمة] | **`untouchedCells` حاضرة دائماً، و`[]` عند الاكتمال** (M2:188)؛ `cutoutCells` حاضرة دائماً و`[]` = لا خليّة مقصاة. الغياب في ختم ≥ 71 ⇒ شذوذ |
| الحواف والإيماءات (M2:189) | `gestureInsetsPx` `mandatoryGestureInsetsPx` | كائن `{left, top, right, bottom}` أعداد صحيحة (`WindowInsets`، API 29+؛ `minSdk = 28` [ك: `app/build.gradle.kts:74`]) | على API < 29 ⇒ غائب **وسببه `api_below_29` في `absentFieldReasons`** [م] |
| | `exclusionRectsPx` | قائمة `{left, top, right, bottom}`؛ مستطيلات استثناء الإيماءات التي أعلنتها النافذة (API 29+) | `[]` = لا استثناء؛ على API < 29 ⇒ غائب بسببه [م] |
| | `systemBarsHidden` | `true`/`false`: هل شريطا النظام مخفيّان أثناء التشغيل | فشل القراءة ⇒ غائب بسببه [م] — **لا `false` افتراضي** |
| | `cutoutRectsPx` | قائمة مستطيلات `DisplayCutout.getBoundingRects()` (API 28+)؛ `[]` = لا ثقب | فشل القراءة ⇒ غائب بسببه [م] |
| | `roundedCornerRadiiPx` **أو** `roundedCornersState` | الأولى: `{topLeft, topRight, bottomRight, bottomLeft}` أعداد صحيحة (API 31+)؛ **زاوية بلا استدارة تُكتب `0`** [م — `getRoundedCorner` تعيد `null` للدلالة على «لا استدارة» وهذا جواب معرَّف لا غياب]. الثانية: نصّ حالة حين لا تُقرأ الأولى | **أحدهما حاضر دائماً**: `roundedCornersState = not_available_api` على API < 31 (M2:161)؛ وغير ذلك `read_failed:<نصّ الخطأ>` [م، القاعدة 21 + سقف `ReasonText`] |

**`absentFieldReasons` (N71-01) [م]:** خريطة **اسم الحقل ← سبب غيابه** على `measurements` لـM-02، **حاضرة فقط حين غاب حقل واحد على الأقلّ**؛ قيمها: `api_below_<n>` · `read_failed:<نصّ الخطأ>` (نصّ الرسالة لا صنف الاستثناء، القاعدة 21، مقصوصاً بـ`ReasonText` [ك: `util/ReasonText`]) · `not_reported_by_device`. **على نمط `channelDiscovery.reasons` (N-07)** — اسم واحد جديد يغطّي كل حقل اختياريّ بحسب API بدل اسم حالة لكل مجموعة. القاعدة: **مفتاح غائب بلا مدخل في `absentFieldReasons` ⇒ شذوذ يُعلنه القارئ** (لا يُقرأ «غير متاح»).

#### ف-03 — M-02: الخامّ (الخيار ب) · `displayedSuspects` · الحدّ

**الموضع:** `measurements.strokes[]` وما معه (M2:206-214). **بلا ضغط** (M2:231). لا يُكتب خامّ أصلاً لتشغيل بلا لمسة (**الإغلاق:** `TouchGridRecorder.recordOnDismiss`، `:51` [ك]، لا يتغيّر: الفتح العرضي لا يمحو خامّ تشغيل سابق).

| المفتاح | النوع · الدلالة | الغياب · القارئ |
|---|---|---|
| `strokes[]` | قائمة خطوط (إصبع/قلم من لمسة إلى رفعها)؛ كل عنصر أدناه | **حاضرة دائماً** في خامّ ختم ≥ 71 (قد تكون `[]` حين تقع لمسة واحدة لم تُسجَّل نقطة منها — لا يحدث عملياً)؛ **غيابها في الإصدار الجديد ⇒ `ABSENT` مع شذوذ، وفي ختم < 71 ⇒ `NOT_IN_FIELDS_VERSION`** (M2:243-260 [ن]) |
| `strokes[].pointerId` | عدد صحيح (معرّف المؤشّر كما سلّمه النظام) | حاضر دائماً |
| `strokes[].toolType` | نصّ ∈ `touch` · `stylus` · `mouse` · `eraser` · `unknown` [م: M2 لا يحدّد النوع؛ أسماء `PointerType` في Compose] — **من قائمة واحدة مُصدَّرة** (القاعدة 24) | قيمة غير هذه ⇒ `unknown:<n>` يكتبها التطبيق حين تأتي قيمة غير معروفة [م]، ويُعلنها القارئ |
| `strokes[].downAtMs` | عدد صحيح؛ لحظة الهبوط **منسوبة إلى فتح الحوار**، بقاعدة `uptimeMillis` (M2:206-214) | حاضر دائماً |
| `strokes[].end` | نصّ ∈ `up` · `cancel` · `cancel_flagged` · `open_at_end` (M2:206-214) — `open_at_end`: لا يزال الإصبع هابطاً عند نهاية التشغيل. **دلالة `cancel_flagged` لا تصلني في المستخلص (يعدّد القيمة ولا يشرحها): لا أشرحها هنا** — تُنقل في أمر البناء من M2 | قيمة غير هذه الأربع ⇒ يُعلنها القارئ غير معروفة |
| `strokes[].dtMs[]` | أعداد صحيحة؛ [م] `dtMs[i]` = ms منذ العيّنة السابقة **من الخطّ نفسه**، و`dtMs[0] = 0` (المرجع المطلق في `downAtMs`) | **طول `dtMs` = طول `x` = طول `y` دائماً**؛ خلافه ⇒ شذوذ (M2:243-260 [ن]) |
| `strokes[].x[]` · `.y[]` | عشرية بالبكسل «كما سلّمها النظام» (M2:206-214). [م] **الإطار: إحداثيّات السطح المحلّية** (منشؤه `surfaceOrigin*Px` بإطار النافذة)، و**كل عيّنة سلّمها النظام بما فيها التاريخية المجمَّعة** (`historical`)، وإلّا فقد المسار نقاطاً بين الأحداث المجمَّعة | **أعداد منتهية فقط** (`NaN`/`Infinity` تُسقِط تشفير الخريطة كلّها في `MeasurementsJson.encode` فتصير `measurementsUnencodable` [ك: `MeasurementsJson.kt:39`])؛ عيّنة غير منتهية **لا تُكتب** وتُعدّ في `pathCap.pointsDropped` [م] |
| `strokes[].touchMajorPx[]` | عشرية اختيارية؛ «والأفضل مع `touchMajor`» (M2:234) | **غائبة** إن لم يتيحها مسار الالتقاط (واجهة Compose `PointerInputChange` لا تعرضها [م — معرفة بالواجهة، لا قياس]) ⇒ مفتاح غائب **وسببه تحت المفتاح `strokes.touchMajorPx` في `absentFieldReasons`**؛ لا تُملأ بأصفار |
| `unbufferedDispatch` | منطقي: هل طُلب التسليم بلا تخزين مؤقّت (M2:213) | فشل الطلب ⇒ غائب بسببه [م] |
| `resampledFlagState` | نصّ حالة؛ **قيمه لا تصلني** (M2:213 يسمّيه ولا يعدّد) — تُنقل في أمر البناء | غائب ⇒ شذوذ في ختم ≥ 71 |
| `displayedSuspects[]` | **سجلّ عرض لا حكم على الجهاز** (M2:214): ما رآه الفنّي من علامات؛ الحكم يحسبه الخادم من الخامّ. **العنصر [م، E19]:** `{kind, strokeIndex, fromPointIndex, toPointIndex}`: `kind` ∈ `gap_jump` · `coord_jump` (M2:105-106) · `edge_exit` (M2:133)؛ `strokeIndex` فهرس في `strokes[]`؛ الفهرسان في مصفوفات `x`/`y`/`dtMs` (`edge_exit` فيه الفهرسان متساويان = آخر عيّنة) | **المفتاح غائب ⇒ لم تُعرض علامات** (الإغلاق التلقائي، E21-٢)؛ **حاضر و`[]` ⇒ عُرضت ولم تُرَ مواضع**. ولا قيم مشتقّة في العنصر (`gapMs` · `distMm` · `speedBeforeMmS` · `zone` — تلك عنصر الخيار (أ) غير المختار، M2:195)، فالخادم يشتقّها من الخامّ |
| `pathCap` | `{maxPoints, reached, pointsDropped}` [م، E18]: السقف المطبَّق، وهل بُلغ، وكم عيّنة لم تُسجَّل | **حاضر دائماً** في ختم ≥ 71؛ `reached = false` و`pointsDropped = 0` في الحالة العادية (صفر حقيقي مصحوب بـ`reached`) |

**الحدود (E18):** الأحجام «من 32 إلى نحو 290 ك.ب» (M2:30، M2:220-223) و`REPORT_RAW_MAX_BYTES = 900000` (`reportRawStore.js:51` عند `5dc318b`؛ M2:228) وحدّ مستند Firestore «1 MiB» (M2:229) **منقولة، بلا مصدر محفوظ [ن]** (M2:15). **لا أستشهد بها رقماً؛ أبني على ما قسته:** `wc -c` على عيّنات حمولة الطلب الذهبية fv70 في `app/src/test/resources/derived/` ⇒ 212,936 · 243,343 · 208,224 بايتاً (`…11ch-nothing-admitted.json` · `…13ch-selinux-blocked-policy.json` · `…16ch-four-policies-truncated-failures.json`). **ولا أعرف** هل الحارس 900,000 على الحمولة كلّها أم على حقل منها، ولا ما يفعله الخادم إن تجاوزه الخامّ — **سؤال لفحص الخادم (خارج حدودي)**.

#### ف-04 — أزرار الصوت M-09/M-10: أحداث المفاتيح مع الخامّ

**الموضع:** `manualResults[M-09|M-10].measurements` (VK:192)، **الساعة `SystemClock.uptimeMillis`** (VK:192). **الخامّ لا يحمل حكماً (القاعدة 20): العتبات على الخادم** (VK:203). **أسماء VK:193-202 كانت «مقترحة، تنتظر إقرار المنسّق» (VK:191)؛ واعتُمد E22 (انظر القرار أدناه) وهو يوصي بإقرارها كما هي — أُوردها موسومة `[م]` كما كُتبت، وحالتها في «أسماء [م]» آخر القسم.**

| المفتاح | النوع · الدلالة | الغياب · ما يُعلنه القارئ |
|---|---|---|
| `expectedKeyCode` | عدد صحيح: `24` (`KEYCODE_VOLUME_UP`) أو `25` (`KEYCODE_VOLUME_DOWN`) | حاضر دائماً |
| `events[]` | **سقف 64** [م: VK:193-202]؛ العنصر `{tMs, keyCode, action, repeat, deviceId, source}`: `tMs` = `eventTime` − لحظة فتح النافذة (أعداد صحيحة)، `action` ∈ `down` · `up`، `repeat` = `repeatCount`، `deviceId` عدد صحيح، `source` = بتّات `InputDevice` | **حاضرة دائماً**؛ **`[]` لا تعني «لم يضغط»**: قد تعني «لم يصل الحدث إلى حوار Compose» (VKU:21؛ VK:188: «not yet tried on a device»)، ولذلك يوجد `detectionSource` أدناه. لا يقرأ القارئ `[]` بمفردها نجاحاً ولا فشلاً |
| `eventsTotal` | عدد صحيح: العدد **قبل** السقف | حاضر دائماً؛ `eventsTotal > 64` ⇒ قُصَّت القائمة، ويُعلنه القارئ |
| `timeToFirstExpectedDownMs` | عدد صحيح | **«غائب إن لم يحدث ضغط مطلوب (قاعدة 22: لا صفر)»** (VK:193-202) |
| `expectedDownCount` · `otherKeyDownCount` | عدد صحيح؛ **صفر حقيقي = لم تصل أحداث بهذا النوع** | حاضران دائماً؛ يُقرآن مع `detectionSource` لا بمفردهما (انظر `events[]`) |
| `holdMs` | عدد صحيح | **غائب إن لم يصل `up`** بعد ضغط مطلوب (VK:193-202) |
| `windowMs` | عدد صحيح: زمن بقاء النافذة | حاضر دائماً |
| `endReason` | نصّ ∈ `auto_detected` · `user_marked_working` · `user_marked_not_working` · `user_marked_not_tested` · `dismissed` (VK:193-202) | حاضر دائماً؛ قيمة غير هذه ⇒ يُعلنها القارئ غير معروفة |
| `flags` (اختياري، VK:203) | `FLAG_FROM_SYSTEM` على العنصر أو الخامّ | غائب إن لم يُقرأ |
| **`detectionSource`** (N71-04) [م] | نصّ ∈ `key_event` · `volume_level_change`: **كيف اكتُشف الضغط** حين `endReason = auto_detected`. **سببه:** الكشف اليوم **بتغيّر مستوى `STREAM_MUSIC`** (`ManualTestDialogs.kt:785-788`) وسبب اختياره في الكود «`AudioService` قد يلتقط المفتاح قبل أن يراه Compose»، فقد يصل الكشف ولا حدث مفتاح في `events[]` | **غائب** إن لم يكن `endReason = auto_detected` (لا قيمة افتراضية) |
| **`volumeStreamMax` · `volumeLevelAtOpen` · `volumeLevelAtEnd`** (N71-05) [م] | أعداد صحيحة: أقصى مستوى للتيّار؛ المستوى بعد الضبط إلى نصف المدى (`:773`)؛ المستوى عند النهاية — **مُدخلات قرار الكشف بتغيّر المستوى** (القاعدة 20: يُخزَّن ما يُحسم به قرار في التطبيق) | **غائبة** إن فشلت قراءتها أو لم يكن `maxVol ≥ 2` (الضبط لا يجري، `:773`)؛ لا صفر |

**القرار (E22، أدناه): معتمد، المستخدم 2026-10-15 ~18:22 +03 (`evidence/station2-plan/user-approval-texts-fv71-2026-10-15.md:26`):** إلحاق M-09/M-10 بـ`PASS_OPENS_DIALOG` وإبقاء زرّ «يعمل» داخل الحوار بـ`user_marked_working` (VK:189 «القرار لك»). **الحالة الراهنة (2026-10-18، عند 5f90ce1):** `PASS_OPENS_DIALOG` فارغة (انظر «الحال اليوم» أعلاه)، فلم يُلحَق M-09/M-10 بها بعد؛ والإلحاق بند A-VOL. **سؤال مفتوح للمستخدم: هل يسري «لا يفتح» (أمره 2026-10-18 على M-02: «تأشير «يعمل» لا يفتح الاختبار تلقائياً، بل يؤشّر الفنّي بلا فتحه») على M-09/M-10 أيضاً؟** لا يُنفَّذ؛ ويبقى E22 كما اعتُمد (يفتح الحوار) حتى يجيب. **تحديث 2026-10-21: حُسم السؤال المفتوح — «يعمل» على M-09/M-10 لا يفتح الحوار (قرار المستخدم)، فحُذفت `PASS_OPENS_DIALOG` أصلاً وصار هذا السطر تاريخاً؛ انظر «تحديث 2026-10-21» قبل الملحق أ.**

#### ف-05 — P6-13: توحيد الظروف

| البند | العقد | الغياب · القارئ |
|---|---|---|
| `batteryLevelStart` | **خامّاً كما اليوم** (P:1430 [ن]): `Int` في ترويسة الملفّ بحارس `-1` للمجهول (`BatteryIndicators.kt:219` `levelsKnown = batteryLevelStart >= 0` [ك])، و`Int?` في البصمة (`null` حين الملفّ مستردّ من نسخة التذييل الاحتياطية، `BehaviorFingerprintBuilder.kt:142` و`:186`) | لا يتغيّر |
| **حدّا الشحن 30–85%** (P:1431 [ن]: «من مصدر واحد مُصدَّر، يقرؤه الخادم والتطبيق»، و`PRE_FLIGHT_MIN_BATTERY_PCT` «تُستبدل لا تُنسخ») | **لا اسم للمصدر في التصميم** (P:1431). **اقتراحي (N71-06) [م]:** في التطبيق كائن واحد `ScanBatteryWindow` فيه `MIN_PCT = 30` و`MAX_PCT = 85` **يحلّ محلّ** `PRE_FLIGHT_MIN_BATTERY_PCT` في مواضعه الأربعة المعدَّدة أعلاه (ولا يبقى الاسم القديم)؛ وتُشتقّ منه نطاقات `PreScanViewModel` (الأحمر دون الحدّ الأدنى، والأصفر ضمن 10 نقاط). **ولتكون «مصدراً واحداً يقرؤه الخادم» عبر مستودعين لا نسخةً:** الحمولة **تُعلن النافذة التي طبّقها التطبيق** — `batteryWindowPct: {minPct, maxPct}` (N71-07) [م] في التذييل — فلا يحمل الخادم نسخة من الرقمين. (الخيار الآخر: يثبّت الخادم 30/85 ويحيل العقد إليهما — أرفضه: نسخة تنجرف، القاعدة 24؛ وقرار مطبَّق في التطبيق يجمّد خطأه في البيانات، القاعدة 20) | **غائب ⇒ ختم < 71**، والحدّ فيه **25 ولا حدّ أعلى** [ك: `StressTestService.kt:243`]؛ غائب في ختم ≥ 71 ⇒ شذوذ |
| `powerSaveState` | نصّ ∈ `off` · `on` · `read_failed` (P:1433 [ن]). **الموضع [م]:** بجوار `isPowerSaveMode` في ترويسة الملفّ وبجوار `powerSaveMode` في جذر البصمة (§الحال اليوم)؛ والمنطقيّان **يبقيان** لقارئيهما القدامى، ويُشتقّان في ختم ≥ 71: `on` ⇒ `true`، `off` ⇒ `false`، **`read_failed` ⇒ غائب (لا `false`)** — اليوم فشل القراءة يُكتب `false` (`StressTestService.kt:663-667`) وهذا هو الغياب المقروء قياساً (القاعدة 22) | **غائب ⇒ ختم < 71** (القارئ يقرأ المنطقيّ القديم)؛ غائب في ≥ 71 ⇒ شذوذ. **ما يصل فعلاً:** `on` يوقف الفحص اليوم و«`read_failed` لا يبدأ الفحص [اجتهاد]» (P:1433) — فالقيمة المشحونة `off` وحدها ما دامت البوّابتان؛ و`on`/`read_failed` في الحمولة لا يحدثان إلا إن خُفّفت البوّابة (انظر «نقاط وجدتها»). **النصّ الذي يراه المستخدم عند `read_failed` غير موجود ولا أنشئه (القرار ٩٠)** |
| `brightnessLock` | في التذييل (P:1436 [ن]): `{state, value, method}` — «والقيمة والطريقة والنجاح». **قيم `state` وحقل «الطريقة»: لا اسم لها في التصميم؛ اقتراحي (N71-08) [م]:** `state` ∈ `applied` · `apply_failed:<نصّ الخطأ>` (القاعدة 21) · `not_attempted:<سبب>`؛ `value` عشرية في [0, 1] = السطوع الذي طُلب (وحدة واحدة؛ وإن كانت الطريقة `system_setting` بوحدة 0–255 فالتحويل عكوس ÷255)؛ `method` ∈ `window_attribute` · `system_setting`. «النجاح» = `state` (لا حقل منطقيّ ثانٍ) | `value` و`method` **غائبان** حين `state = not_attempted:*` (لا صفر)؛ `brightnessLock` كلّه **غائب ⇒ ختم < 71**، وغائب في ≥ 71 ⇒ شذوذ. **لا أضيف قراءة وضع السطوع التلقائي ولا قراءة ارتجاع:** المصدر لم يطلبهما |

**ما يبقى خارج الحمولة:** سلوك التطبيق خارج النافذة 30–85% (يمنع البدء كما يفعل تحت الحدّ اليوم؟) ونصّه المعروض: **غير محسوم في المصادر المنقولة**، والنصّ الجديد يحتاج اعتماداً (القرار ٩٠) — يُحسم في أمر البناء. **ولا نصّ جديد في هذا القسم.**

#### القرّاء (القاعدة 25) — في هذا المستودع [ك] وما يلزم خارجه [ن]

**في هذا المستودع — ما يُحدَّث أو يُضاف في التزامات التنفيذ اللاحقة (لا الآن):**

| الموضع | ما يقرؤه أو يكتبه | أثر fv71 |
|---|---|---|
| `TouchGridRecorder.kt` (`Keys` `:65` · `measurements` `:130` · `statusFor` `:149`) | منتِج خامّ M-02 | يتوسّع لمسار اللمس وأبعاد السطح؛ **مصدر مُصدَّر واحد** لأسماء المفاتيح الجديدة (القاعدة 24) وللقيم المعدَّدة (`toolType` · `end` · `densitySource`…) |
| `ManualTestDialogs.kt` `TouchGridTestDialog` (`:229`) | يغذّي المنتِج بالنقاط | يقيس السطح والحواف والكثافة؛ **الأزرار والعدّاد خارج السطح** (E21-٤) | **[تحديث 2026-10-21: «خارج السطح» لم يعد قائماً — الحوار بملء الشاشة والشبكة تغطي نافذته كلّها، والعدّاد والزرّان فوقها لا خارجها؛ انظر «تحديث 2026-10-21» قبل الملحق أ]**
| `ManualTestDialogs.kt` `VolumeButtonTestDialog` (`:752`) | يكتب `pass` عند تغيّر المستوى | يلتقط أحداث المفاتيح ويبني خامّ ف-04 |
| `ManualChipActions.kt:36` `PASS_OPENS_DIALOG` | الصفوف التي «يعمل» فيها يفتح الحوار | + M-09 وM-10 (E22 معتمد 2026-10-15). **عند 5f90ce1 (2026-10-18):** المجموعة فارغة؛ «يعمل» على M-02 يكتب `pass` بلا فتح (أمر المستخدم 2026-10-18) ولا تمحو النقرةُ فوق `pass` مخزَّن خامّه (`ManualChipActions.PASS_KEEPS_STORED_RAW`)؛ والإلحاق ينتظر A-VOL **[تحديث 2026-10-21: المجموعة والإجراء «يفتح الحوار» حُذفا؛ «يعمل» على كل الصفوف يكتب جوابه ولا يفتح شيئاً، وM-09/M-10 دخلا `TAP_KEEPS_STORED_RAW` مع M-02]** |
| `ManualTestsViewModel.kt:111-131` `setResult` · `MeasurementsJson.kt` | يخزّن الخامّ في عمود `measurements` (TEXT) ويحوّله | حجم الخامّ (E18)؛ قاعدة «الكتابة الأخيرة تستبدل الجواب وخامّه» تُستبدل (E21-٣ معتمد 2026-10-15). **عند 5f90ce1 (2026-10-18):** نُفِّذ لنقرة «يعمل» على M-02 فوق `pass` مخزَّن وحدها (`TestResultRepository.withStoredRawKept` في كاتبَي `ManualTestsViewModel.setResult` و`ReportViewModel.saveManualResults`)؛ وبقية الشرائح (لا يعمل · لم أختبر · غير متوفر) ما زالت تستبدل الخامّ — يُوسَّع في A-M02 |
| `StressTestService.kt:243,285,587` · `ActiveScanScreen.kt:580` · `PreScanViewModel.kt:373-384` | `PRE_FLIGHT_MIN_BATTERY_PCT` | يُستبدل بـ`ScanBatteryWindow` (ف-05) |
| `StressTestService.kt:663-667` · `TimeSeriesFileWriter.kt:541` · `AiAnalysisRequest.kt:42,92` · `BehaviorFingerprintBuilder.kt:114,197` | `isPowerSaveMode` / `powerSaveMode` | + `powerSaveState`؛ فشل القراءة لا يُكتب `false` |
| `TimeSeriesFooter` ← `TimeSeriesFooterDto` (`BehaviorFingerprintBuilder.kt:223`) | تذييل الملفّ ثم الحمولة | + `brightnessLock` + `batteryWindowPct` — **وتُراجَع قائمة مفاتيح التذييل المقارنة** (أ-٥: صنف ↔ DTO ↔ ذهبية) كي لا يُسقَط المفتاح بين الصنف والـDTO كما أُسقط `preScanWarnings` (أ-١٢) |
| `AppRequestPayloadGolden.kt:1095-1105` · `AppRequestPayloadGoldenTest.kt:363` | العيّنات الذهبية لحمولة التطبيق | **تُعاد توليدها وتُسمّى fv71 في التزام كل تغيير شكل (القاعدة 39) من المنتِج الحقيقي (القاعدة 41)**، وتشمل: M-02 بكل حالات الغياب (API < 29 · API < 31 · بلا ثقب · بثقب) وبـ`pathCap.reached` صحيحاً وخاطئاً · M-09/M-10 بكل قيم `endReason` الخمس وبـ`detectionSource` بقيمتيه · P6-13 بكل `powerSaveState` وكل `brightnessLock.state` |
| `ManualTestsViewModelFourStatesTest.kt:105` | يثبّت «الكتابة الأخيرة تستبدل الخامّ» | **يُستبدل** (القاعدة 23) — E21-٣ معتمد (2026-10-15)؛ لا يُضاف إليه اختبار |

**في `neuralcheck-backend` (منقول، غير متحقَّق من هذا المستودع [ن]؛ M2:243-269):** `fv68Reader.js` (`readTouchGrid` `:1106-1164`) يتوسّع؛ ووحدة نقيّة جديدة `functions/src/analysis/touchPath.js` [م]؛ وأسماء `fv68Names.js` (`TOUCH_GRID_MEASUREMENT_KEYS` `:267` و`TOUCH_PATH_*` [م])؛ يحسب معدّل العيّنات (الوسيط والمئين 95) والمواضع المشتبهة مع `detectorVersion` [م] و`TOUCH_GRID_CELLS_DISAGREE_WITH_PATH` [م] وأقرب بُعد من كل حافّة و`gridBasis`؛ الغياب: `strokes` غائب في الجديد ⇒ `ABSENT` مع شذوذ وفي السابق ⇒ `NOT_IN_FIELDS_VERSION`، وطول `dtMs`/`x`/`y` غير متساوٍ ⇒ شذوذ، ومُدخلات الكثافة غائبة ⇒ `UNDETERMINED`، و`endReason` مجهول ⇒ `UNRECOGNIZED`؛ «القراءات تُعاد مع النتيجة ولا تدخل الحرف اليوم» (M2:260). **وقرّاء `touchedCells`/`totalCells` يُعدَّدون في العقد (M2:265-268)** — لا أنقل قائمتهم (غير منقولة إليّ). **وما تضيفه هذه البطاقات على الخادم:** قراءة `absentFieldReasons` و`pathCap` و`displayedSuspects` (بالعنصر المقترح) و`detectionSource` و`volume*` وأحداث المفاتيح بسقفها و`powerSaveState` و`brightnessLock` و`batteryWindowPct`؛ وأن **تمرّر** طبقات التمرير/التخزين المفاتيح الجديدة لا أن تُسقطها (§٤.٢: `lightenFingerprintForStorage.js` · `rehydrateReportSamples.js` · `serverRawSamplesStorage.js`). **وكلفة قارئ الخادم 0.5–1 [م، S2:42؛ الوحدة غير مذكورة في المستخلص].** **في `wepsite`:** لا قارئ؛ عيّنة الحمولة الجديدة **تُنسخ إلى الخادم وحده** (§٤.٣ [معدَّل 2026-10-08 بقرار المستخدم]).

#### الأسئلة المفتوحة في المستخلص التي تخصّ fv71 — مقترحي وسببه؛ **معتمدة** (المستخدم 2026-10-15 ~18:22 +03، `evidence/station2-plan/user-approval-texts-fv71-2026-10-15.md:26`) عدا نصوص E21 الستّة (البند ٥)

* **E18 — M-02: لا سقف لعدد العيّنات، ولا حكم إن تجاوز الخامّ حارس 900,000 بايت.** **[معتمد، 2026-10-15: «E18 (سقف 15,000 عيّنة معلَن)»]** **مقترحي:** سقف على **عدد النقاط الكلّي** في التطبيق، `pathCap.maxPoints = 15,000` [م] ثابتاً مُصدَّراً، يتوقّف عنده **تسجيل نقاط جديدة** (يستمرّ التشغيل وتستمرّ الخلايا في العدّ، والخطّ الجاري يُغلَق حيث وُقف) ويُعلَن بـ`pathCap` (ف-03) — **لا قصّ صامت**. **السبب:** (١) الخامّ يُكتب في عمود TEXT وتُقرأ الصفوف كاملة؛ حدّ نافذة المؤشّر في أندرويد 2 MB للصفّ (معرفة بالمنصّة، غير مقيسة هنا)؛ (٢) حمولة الطلب الذهبية fv70 الأكبر **243,343 بايتاً** (`wc -c`)، فإن انطبق الحارس على الحمولة كلّها يبقى للخامّ ≤ ≈ 656,000؛ وبسقف ≈ 450,000 يبقى هامش ≈ 200,000 للأجهزة الأكبر؛ (٣) حساب [غير مقيس]: ≈ 30 بايتاً للنقطة (`dtMs` + `x` + `y` + `touchMajorPx` + فواصل) × 15,000 ≈ 450,000 — **يُقاس على عيّنة من المنتِج الحقيقي عند التنفيذ ويُعدَّل الرقم إن اختلف**؛ (٤) الأحجام المنقولة (32–290 ك.ب) لا أستند إليها. **وما بعد السقف:** حكم الخادم إن بلغ الحارس يحتاج فحصاً في `reportRawStore.js` (خارج حدودي).
* **E19 — بنية `displayedSuspects` وقيم `endReason`.** **[معتمد، 2026-10-15]** **`displayedSuspects`:** العنصر `{kind, strokeIndex, fromPointIndex, toPointIndex}` ولا قيم مشتقّة (ف-03)؛ **السبب:** الخيار (ب) يجعل الخادم يشتقّ من الخامّ (M2:214)، فسجلّ العرض يحتاج فقط **ما عُرض وأين من المسار**، والفهارس تربطه بالخامّ بلا نسخة ثانية من الإحداثيات تنجرف. **`endReason`: ليست مفتوحة في هذا المستودع** — القيم الأربع معدَّدة في `TouchGridRecorder.EndReasons` (`:56`) وفي §٧-ح (ب)؛ المستخلص يصف «الأربع نفسها» غير معدَّدة وهي معدَّدة هنا (انظر «نقاط وجدتها»). **تتغيّر دلالة `auto_complete` فقط** (ف-01).
* **E20 — هل تدخل fv71 حقولُ P6-01 (مكوّنات بصمة العتاد بحالاتها، وحالة Widevine)؟** **[معتمد، 2026-10-15]** **مقترحي: لا.** **السبب:** (١) لا كود في التطبيق عند `94e0035` ينتج Widevine ولا غيره من هذه الحقول (`git grep -n -i "widevine\|MediaDrm" -- app/src/main` ⇒ لا نتائج)، فكتابة عقد لمنتِج غير موجود تسبق أمره؛ (٢) قرارات المستخدم الثلاثة لـfv71 (M-02 · الصوت · P6-13) لا تذكرها، و§١٦ (P:1579) وضعها في «fv69» ولم يُنقل أيٌّ منها صراحةً إلى fv71 في المصادر [ن] — fv69 مختوم ولم يحملها؛ (٣) تمسّ حقول البصمة (`deviceFingerprint`) لا `measurements` ولا التذييل، فقارئها وفخّها الذهبي غير ما هنا. **فيُكتب لها قسمها حين يُطلب منتِجها** (وبعد أن يقرّر المستخدم ختمها).
* **E21 — M-02: البنود الخمسة «يُعرض مع أمر البناء» (P:924).** **[معتمد البنود ١–٤، 2026-10-15؛ البند ٥ (النصوص الستّة) لم يُعتمد: يقترحها الوكيل في أمرها وتأتي للاعتماد]**
  1. **الحواف: خلايا إلزامية أم شريط؟ — مقترحي: خلايا إلزامية.** الشبكة تغطّي السطح كلّه، وخلايا الحافّة جزء من الاكتمال؛ **ولا يُقصى من المقام إلا `cutoutCells`** (العلاقة الملزمة M2:190). **السبب:** الإقصاء قرار يُتّخذ في التطبيق (حواف الإيماءات مثلاً) فيجمّد خطأه (القاعدة 20) ويُخفي عطب حافّة الرقمنة الذي يبحث عنه `edge_exit`؛ فيُخزَّن الخام (`untouchedCells` + `gestureInsetsPx` + `mandatoryGestureInsetsPx`) ويقرّر الخادم. **الثمن:** على هاتف بإيماءات قد يعسر لمس خلايا الحواف بالسحب (النقر مباشرة ممكن) — فيُنهي الفنّي التشغيل بزرّيه بعد 15 ث، ويقول الخامّ أيّ خلايا بقيت.
  2. **العلامات بعد الإغلاق التلقائي — مقترحي: يبقى الإغلاق التلقائي عند الاكتمال بلا علامات** (قرار fv69 «عند اكتمال الخلايا كلّها: نجاح تلقائي والحوار يُغلق» [ك: §٧-ح (ب)]). تُعرض العلامات فقط في التشغيل الذي ينتهي بزرّ الفنّي أو الإغلاق؛ وبعد الاكتمال التلقائي **يغيب `displayedSuspects`** (لا `[]`). **السبب:** الخيار (ب) يشتقّ الخادم المواضع من الخامّ فلا حاجة لعرضها لحكم الجهاز؛ وتغيير تدفّق نجاح تلقائي مقرَّر قرار مستخدم لا قرار عقد.
  3. **«لا يعمل» تمحو خامّ تشغيل مكتمل (M2:171) — مقترحي: لا تمحوه.** نقرة شريحة (يعمل ⇒ يفتح الحوار · لا يعمل · لم أختبر · غير متوفر) **تكتب الجواب فقط وتُبقي الخامّ الموجود**؛ والخامّ يحمل `endReason` ليقول كيف انتهى ذلك التشغيل، والحالة على الصفّ تقول جواب الفنّي الأخير؛ **ولا يستنتج القارئ الحالة من `endReason`** (قد يختلفان). **السبب:** القاعدة 20 (الخام يُخزَّن لا يُمحى) والمحو اليوم أثر جانبي لـ`setResult` (`ManualTestsViewModel.kt:111-114`: «الكتابة الأخيرة تستبدل الجواب وخامّه») والاختبار الذي يثبّته (`ManualTestsViewModelFourStatesTest.kt:105`) **يُستبدل** عند التنفيذ لا يُضاف إليه. **ما يبقى مفتوحاً:** تشغيل جديد يكتب خامّه بدل القديم (لا يُراكم تشغيلين).
  4. **الزرّان فوق الصفوف السفلى — مقترحي: يخرجان من السطح.** شريط سفلي (العدّاد والزرّان) **خارج** سطح اللمس فلا يغطّي خلية؛ و`surfaceHeightPx` يقول الارتفاع الفعلي للشبكة. **السبب:** اليوم الزرّان والعدّاد طبقات فوق شبكة ملء الشاشة (`ManualTestDialogs.kt:355`) والزرّان يلتقطان اللمس، فخلايا الصفوف السفلى تحتهما لا يصلها لمس (ولا يبقى لها إلا فتحة الثماني dp بين الزرّين) — ويُنتج التصميمُ نفسه `untouchedCells` كاذبة. **الثمن:** شبكة أقصر بارتفاع الشريط، **غير مقيس**.
  5. **النصوص الستّة — لا أقترحها:** المستخلص يسمّيها ولا يعدّدها، ومصدرها (P:924) خارج مستودعي. **تُنقل في أمر البناء بنصّها المعتمد** (القرار ٩٠: لا نصّ جديد بلا اعتماد). **حكم المستخدم 2026-10-15 ~18:22 +03: «ونصوص E21 الستّة (الشبكة الجديدة): يقترحها الوكيل في أمرها، وتأتيني للاعتماد» — فهي غير معتمدة؛ وأمر A-M02 (2026-10-18) يقترحها في تقرير الوكيل ولا يكتب منها قيمة في `strings.xml`.**
* **E22 — أزرار الصوت.** **[معتمد، المستخدم 2026-10-15 ~18:22 +03: «E22 (M-09/M-10: «يعمل» يفتح الحوار والكشف تلقائي، و«يعمل» اليدوي باقٍ بـ user_marked_working، وendReason يميّز الطريقين): معتمدة». سؤال مفتوح للمستخدم، 2026-10-18: هل يسري «لا يفتح» على M-09/M-10 أيضاً؟ — انظر ف-04. أسماء VK:193-202 المذكورة هنا: اعتماد E22 كلّه يشملها بحسب قراءتي؛ نصّ المستخدم لا يعدّدها.]** **الأسماء:** أوصي بإقرار أسماء VK:193-202 كما هي، **مع** إضافتي `detectionSource` و`volumeStreamMax`/`volumeLevelAtOpen`/`volumeLevelAtEnd` (ف-04) لأن الكشف اليوم بتغيّر المستوى لا بحدث مفتاح [ك]. **`PASS_OPENS_DIALOG` وزرّ «يعمل» — مقترحي:** **يُلحَق M-09 وM-10 بـ`PASS_OPENS_DIALOG`** (السبب نفسه الذي أُلحق به M-02: «يعمل» بنقرة تكتب `pass` بلا ضغطة خلفها، فلا خامّ يسندها)؛ **ويبقى زرّ «يعمل» داخل الحوار** (تجاوز الفنّي) **لكن** يكتب `endReason = user_marked_working` فيُقرأ ادّعاءً لا كشفاً (`expectedDownCount` و`detectionSource` يقولان إن كان ثمّة ضغط مرصود). **السبب:** وصول حدث المفتاح إلى حوار Compose «not yet tried on a device» (VKU:21؛ VK:188) — فحذف الزرّ الآن يجعل `pass` مستحيلاً على جهاز لا يصله الحدث ولا يتحرّك مستواه، أي `fail` كاذباً؛ والتجاوز المعلَن أصدق من الاستحالة. **تحديث 2026-10-21: الشقّ «`PASS_OPENS_DIALOG` وزرّ «يعمل» — مقترحي» (إلحاق M-09 وM-10 بـ`PASS_OPENS_DIALOG`) أُلغي بقرار المستخدم؛ وزرّ «يعمل» داخل الحوار بـ`user_marked_working` باقٍ.**

#### نقاط وجدتها وتخالف الأمر أو المستخلص (قاعدتا 14 و29)

1. **`gridBasis` «حتى fv69»:** الكود والختم 70 يُبقيان M-02 8 × 15 (`TouchGridRecorder.kt:35-36`؛ fv70 لم يمسّ M-02): الحدّ **`< 71`**. القارئ على الخادم يقرأ **الختم** لا رقماً ثابتاً.
2. **`endReason` «غير معدَّدة»:** معدَّدة (`TouchGridRecorder.EndReasons`، `:56`؛ وN-19 في رأس الوثيقة؛ و§٧-ح (ب)).
3. **`powerSaveState`: ما يصل فعلاً إلى الحمولة:** `on` يوقف الفحص (`POWER_SAVE_MODE`، `StressTestService.kt:670`) و«`read_failed` لا يبدأ الفحص [اجتهاد]» (P:1433)، فقيمة الحقل في أيّ فحص مكتمل **`off` دائماً** — حقل يقول «الشرط تحقّق» لا قيمة متغيّرة. هذا إعلان صادق وليس قيمة افتراضية، لكنه يعني أن `on`/`read_failed` **لن يظهرا** إلا إن خُفّفت البوّابة، ولا أقرّر ذلك.
4. **بوّابة الشحن متسامحة مع المجهول** (`isPreflightBatteryBlocked`، `StressTestService.kt:285`): مستوى غير معروف يمرّ. P6-13 يضع النافذة 30–85% ولا يقول ما يُفعل بالمجهول؛ **أُبقيه كما هو** ويبقى `batteryLevelStart` يعلن المجهول (`-1` في الملفّ، غائباً في البصمة) — **ولا أقرّر تغييره** (قرار منتج؛ والنصّ الجديد لا يُضاف بلا اعتماد).
5. **ساعتان في خامّ M-02:** `elapsedMs` و`timeToFirstTouchMs` بـ`elapsedRealtime`، و`downAtMs` وأحداث المفاتيح بـ`uptimeMillis` (M2:182 · M2:206-214 · VK:192). **لا فرق أثناء حوار تفاعلي** (الفرق نوم عميق)، لكن **لا يُطرح رقمان من ساعتين مختلفتين** في أيّ قارئ.
6. **إطار `x`/`y`:** المستخلص يقول «كما سلّمها النظام» ولا يحدّد الإطار (سطح محلّي أم نافذة أم شاشة): **اقترحتُ السطح المحلّي** مع `surfaceOrigin*Px` (ف-03).
7. **`cancel_flagged` و`resampledFlagState` و`unbufferedDispatch` وأسماء شروط `densityCheck` وعنصر `untouchedCells`:** المستخلص يسمّيها ولا يشرحها أو يعدّد قيمها؛ **لم أخترع لها دلالة** — موسومة في بطاقاتها وتُنقل من M2 في أمر البناء.

#### أسماء [م]: N71-01..08 **معتمدة** (المستخدم 2026-10-15 ~18:22 +03: «الأسماء N71-01 إلى N71-08: معتمدة»، `evidence/station2-plan/user-approval-texts-fv71-2026-10-15.md:27`)

| ر | الاسم | الموضع |
|---|---|---|
| N71-01 | `absentFieldReasons` | `measurements` لـM-02 |
| N71-02 | `pathCap` `{maxPoints, reached, pointsDropped}` (و`maxPoints = 15,000`) | `measurements` لـM-02 |
| N71-03 | عنصر `displayedSuspects[]`: `strokeIndex` · `fromPointIndex` · `toPointIndex` (و`kind` من M2) | `measurements` لـM-02 |
| N71-04 | `detectionSource` (`key_event` · `volume_level_change`) | `measurements` لـM-09/M-10 |
| N71-05 | `volumeStreamMax` · `volumeLevelAtOpen` · `volumeLevelAtEnd` | `measurements` لـM-09/M-10 |
| N71-06 | `ScanBatteryWindow` (`MIN_PCT = 30`، `MAX_PCT = 85`) — اسم كائن في التطبيق، لا على السلك | التطبيق |
| N71-07 | `batteryWindowPct {minPct, maxPct}` | التذييل |
| N71-08 | `brightnessLock.state` (`applied` · `apply_failed:<نصّ>` · `not_attempted:<سبب>`) و`.value` و`.method` (`window_attribute` · `system_setting`) | التذييل |
| — | **أسماء التصميم (M2:184-189) وأسماء VK:193-203:** تُقرأ بوسمها عند كاتبها «مقترح للاعتماد» (M2:24؛ VK:191)؛ لا أعيد اقتراحها. **حالة 2026-10-18:** أسماء VK:193-202 يشملها اعتماد E22 بحسب قراءتي (انظر ف-04)؛ وأسماء التصميم M2:184-189 لا يظهر اعتمادها في ملفّي الاعتماد 2026-10-15 فتبقى كما هي | `measurements` لـM-02 وM-09/M-10 |

#### للجهاز (جولة الفحص الميداني الواحدة، القاعدة 19 — لا يُطلب بعد كل تعديل)

* **أزرار الصوت:** هل يصل حدث المفتاح إلى حوار Compose على S10 وS21 وHonor؟ وكم جهازاً يكتشف بتغيّر المستوى وحده (`detectionSource = volume_level_change` و`events = []`)؟
* **M-02:** حجم الخامّ الفعلي (بايت) لتشغيل كامل من 30–40 ث على كل جهاز، وهل بلغ أحدها `pathCap`؛ وقيم `densitySource` و`densityCheck` على الأجهزة؛ وأيّ الحقول غابت في `absentFieldReasons`.
* **P6-13:** هل يُطبَّق قفل السطوع (`brightnessLock.state`) على كل طراز، بأيّ طريقة؟

#### حالة التنفيذ

لا كود في هذا القسم. الأساس `94e0035`؛ السويت عنده: 167 مجموعة · 1438 اختباراً · 0 فشل (debug) و167 · 1438 · 0 (release)، من XML (`cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest` ثم الإصدار، بـjbr `openjdk version "21.0.9" 2025-10-21` داخل الأمر). **الختم 70 لم يتغيّر؛ ولا APK.** أوامر التنفيذ اللاحقة (M-02 · الصوت · P6-13) لم تصدر بعد، ويلزمها: **اعتماد الأسماء `[م]` أعلاه والإجابة عن E18–E22 وعن بنود «نقاط وجدتها»، ثم نشر قرّاء الخادم** — قبل أيّ التزام يضع الختم 71.

**تحديث 2026-10-18 (A-FV71C):** اعتُمدت E18–E22 والأسماء N71-01..08 (2026-10-15، النصّ في رأس هذا القسم)؛ ولم يُجَب بعدُ عن بنود «نقاط وجدتها» ٣–٧ ولا عن نصوص E21 الستّة ولا عن سؤال E22 المفتوح (هل يسري «لا يفتح» على M-09/M-10). أمر التنفيذ (A-M02 · A-VOL · A-P6-13) صدر 2026-10-18؛ **ولا APK**. **للقارئ على الخادم (القاعدة 22) — حالة M-02 صارت ممكنة في الختم 70 من الكود عند 5f90ce1 (2026-10-18)، والختم 70 لم يتغيّر فلا يفصل ختمٌ بين ما قبلها وما بعدها:** (١) `status = pass` **بلا `measurements`**: نقرة «يعمل» على M-02 تكتب `pass` بلا فتح ولا خامّ («a technician's tap (no raw)»، `ManualChipActions.kt:18-19`)؛ (٢) `status = pass` **بخامّ محفوظ**: النقرة فوق `pass` مخزَّن تُبقي خامّ ذلك الجواب (`rawToStore`، `ManualChipActions.kt:73`) — فقد يُقرأ `pass` مع `endReason` غير `auto_complete` (كإغلاق الشبكة بعد لمسة وهي تحمل جواباً مخزَّناً، `TouchGridRecorder.recordOnDismiss` `:51`). فلا يُستنتج `pass` ⇒ `auto_complete`، ولا `pass` بلا `measurements` ⇒ شذوذ في الختم 70 (الغياب هنا جواب فنّي بلا تشغيل، لا فقد حقل).

#### حالة التنفيذ 2026-10-18 — ما قرّره التنفيذ ويحتاجه قارئ الخادم (القاعدة 25)

نُفِّذت الصفوف الثلاثة في التطبيق، **بلا APK**: E21-٣ كاملاً (eee9f8c) · A-M02 (7ae6bfe، وفيه **وُضع الختم 71** في `app/build.gradle.kts`) · A-VOL (7d38eb2) · A-P6-13 (12d8545). العيّنات الذهبية fv71 في `app/src/test/resources/derived/fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-{11ch-nothing-admitted,13ch-selinux-blocked-policy,16ch-four-policies-truncated-failures}.json` (و`.keys.txt`، وsha256 في README المجلّد)؛ وهي تستبدل عيّنات fv70 بما صنعه المنتِج الحقيقي من هذا العقد. الوسم `[ق]` أدناه: **قرار تقني للتطبيق** ليس في الأسماء المعتمدة نصّاً (يُراجَع ولا يُعامَل معتمداً)؛ و«معلّق» = لا مواصفة في المصادر ولم يُخمَّن.

**M-02 (ف-01 إلى ف-03):**
* **القيم المعدَّدة.** `densitySource` ∈ `XDPI` · `XDPI_MODE_SCALED` · `DENSITY_DPI_FALLBACK`. **عناصر `densityCheck` [ق]:** `xdpi_or_ydpi_not_positive` · `xdpi_ydpi_differ_over_5pct` · `phone_width_outside_55_85mm` · `both_candidates_plausible` · `display_width_unknown` — و`[]` ما لم يُستعمل `DENSITY_DPI_FALLBACK`. **`resampledFlagState` [ق، معلّق: مواصفة غائبة]:** `not_available_api` (API < 35) · `not_read_by_app` (API ≥ 35)، حاضر دائماً.
* **الشبكة.** `gridCols` = round(`surfaceWidthPx` ÷ (6 مم × `pxPerMm`)) وكذلك `gridRows` (أدنى 1، أقصى 200 حارسَ ذاكرة)؛ فهرس الخليّة `row × gridCols + col`؛ `cutoutCells` الخلايا التي يغطّيها مستطيل القطع بمساحة موجبة (المستطيل في إطار النافذة مطروحاً منه `surfaceOrigin*Px`)؛ `totalCells = gridCols × gridRows − |cutoutCells|`؛ `untouchedCells` تستثني `cutoutCells`. تُضاء كل خليّة يعبرها خطّ الإصبع بين عيّنتين متتاليتين، **إلا عبر قطعة وُسمت قفزة** (فيها خليّة النهاية وحدها)، ولا تُضاء خليّة قطع.
* **الأطر كما سلّمها النظام.** `strokes[].x`/`y` في إطار **السطح المحلّي**؛ `cutoutRectsPx` ومقاسات الإيماءات في إطار **النافذة**؛ `exclusionRectsPx` في إطار **الـView**؛ والتحويل بـ`surfaceOrigin*Px`.
* **`strokes[]`.** `dtMs[0] = 0` وغيره فرق الزمن عن عيّنة الخطّ **المسجَّلة** السابقة (لا سالب)؛ عيّنة غير منتهية لا تُكتب وتُعدّ في `pathCap.pointsDropped`؛ `downAtMs` = زمن حدث الهبوط (`uptimeMillis`) − فتح الحوار؛ `end` ∈ `up` · `cancel` · `cancel_flagged` (`FLAG_CANCELED`) · `open_at_end`. **`touchMajorPx`** يُحذف المفتاح من خطٍّ غابت منه عيّنة، وسببه `not_reported_by_device` تحت `strokes.touchMajorPx` (لا أصفار).
* **`displayedSuspects`.** `kind` ∈ `gap_jump` · `coord_jump` · `edge_exit`؛ **غائب بعد `auto_complete`** وحاضر (قد يكون `[]`) بعد زرّ أو إغلاق. `edge_exit` فيه الفهرسان متساويان (آخر عيّنة) ولا يُسجَّل إلا عند `up` وبحسب اتّجاه الحركة من آخر عيّنة في مكان آخر؛ وكشف «انقطاع برفع» **ليس في التطبيق** (يشتقّه الخادم من الخامّ).
* **`pathCap`.** `reached` = بلغت العيّنات المسجَّلة `maxPoints` (15,000) ولو لم تُسقَط عيّنة. حجم 15,000 نقطة بالمنتِج الحقيقي: 443,706 بايتاً (`TouchGridRecorderTest`).
* **`absentFieldReasons`.** مفاتيحه أسماء الحقول الغائبة (`displayWidthPx` · `displayHeightPx` · `modePhysicalWidthPx` · `modePhysicalHeightPx` · `gestureInsetsPx` · `mandatoryGestureInsetsPx` · `exclusionRectsPx` · `systemBarsHidden` · `cutoutRectsPx` · `unbufferedDispatch`) و`strokes.touchMajorPx`؛ قيمها `api_below_29` · `read_failed:<نصّ الخطأ>` · `not_reported_by_device`؛ والمفتاح كلّه غائب حين لا يغيب شيء. `unbufferedDispatch` = `true` إن نجح الطلب وإلا غائب بسببه.
* **E21-٣ نُفِّذ لشرائح M-02 الأربع** (يعمل · لا يعمل · لم أختبر · غير متوفر) فوق أيّ جواب مخزَّن: الجواب وحده يُكتب ويبقى الخامّ المخزَّن؛ فقد يُقرأ `pass` مع `endReason` غير `auto_complete` ولا يُستنتج أحدهما من الآخر؛ وتشغيل جديد يستبدل الخامّ (لا يُراكم تشغيلين). والثابت `TAP_KEEPS_STORED_RAW` (كان `PASS_KEEPS_STORED_RAW`) = M-02 وحدها.

**M-09/M-10 (ف-04):**
* `expectedDownCount` و`otherKeyDownCount` يعدّان **ضغطات** (`down` بـ`repeat = 0`)؛ تكرارات المفتاح المضغوط في `events[]` بـ`repeat > 0` فقط. المفاتيح المسجَّلة عائلة الصوت (24 · 25 · 164 كتم)؛ وغيرها (رجوع، طاقة…) يُهمَل. `flags` الاختياري (VK:203) لا يُقرأ.
* `holdMs` من أوّل ضغطة مطلوبة إلى أوّل `up` للمفتاح نفسه بعدها (غائب إن لم يصل)؛ يبقى الحوار 600 م.ث بعد الكشف لتسجيل `up` [ق]. `detectionSource` يُكتب مع `auto_detected` وحدها. **الاستطلاع باقٍ** (لا يُستهلك حدث المفتاح): قد يأتي `volume_level_change` بـ`events = []`.
* `endReason = dismissed` يُكتب بجواب الاختبار الحالي (`not_tested` إن لم يكن) حين وصل حدث مفتاح صوت واحد على الأقلّ، وإلا لا يُكتب شيء. `PASS_OPENS_DIALOG` = {M-09، M-10}. **سؤال مفتوح للمستخدم:** هل يسري «لا يفتح» (M7(ب)) على M-09/M-10؟ — لم يُنفَّذ. **تحديث 2026-10-21: السؤال حُسم — «لا يفتح» يسري على M-09/M-10، وأُلغيت `PASS_OPENS_DIALOG`.**

**P6-13 (ف-05):**
* `batteryWindowPct` = `{minPct: 30, maxPct: 85}` من `ScanBatteryWindow` وحدها. فوق 85 يرفض التطبيق الفحص برمز `BATTERY_TOO_HIGH:<مستوى>` ولا نصّ معتمداً له بعدُ. **معلّق: مواصفة غائبة** — سلوك المستوى المجهول (يمرّ كما كان).
* `powerSaveState` ∈ `off` · `on` · `read_failed` في ترويسة الملفّ وجذر البصمة، والمنطقيّ `isPowerSaveMode`/`powerSaveMode` **غائب** عند `read_failed`. `read_failed` لا يمنع البدء. **معلّق: مواصفة غائبة** (P:1433 «[اجتهاد]»).
* `brightnessLock` = `{state, value?, method?}`؛ **قيمة السطوع غير محسومة في أيّ مصدر**، فهي `null` وحالة الإنتاج اليوم `{state: "not_attempted:value_not_decided"}` بلا `value` ولا `method`؛ و`not_attempted:no_scan_screen_reported` حين لا شاشة فحص أبلغت. ملفّ بختم < 71 بلا هذه المفاتيح، ويسمّيها `fieldsNotInFile` (`batteryWindowPct` · `brightnessLock`).

**تحديث 2026-10-21 (أمر وكيل التطبيق ٦ — بعد جولة جهاز المستخدم على APK f1a38be؛ `fieldsVersion` يبقى 71، لا اسم حقل ولا قيمة ولا دلالة تغيّرت، ولا APK):**
* **قرار المستخدم 2026-10-21 (منقول من نصّ الأمر، غير متحقَّق من هذا المستودع [ن]):** «M-02: الشبكة 12×20 = 240 (من صورة جهاز حسن)، وحجم الخلية وعددها مقبولان. العيب: لا تغطي الشاشة. شريط العنوان ظاهر، والشبكة تنتهي عند ~83% من الارتفاع، ثم شريط أسود فيه العدّاد، ثم شريط يظهر منه زرّ «متابعة إلى التقرير» من الشاشة خلف الحوار. المطلوب: الحوار بملء الشاشة، والشبكة تغطي كامل مساحة اللمس المتاحة للتطبيق، بلا خلية يتعذّر لمسها بسبب شريط أو أزرار.» و«M-09 وM-10 (رفع وخفض الصوت): «يعمل» لا يفتح الحوار، كما M-02. قرار M7(ب) يسري عليهما.»
* **M-02 — سبب العيب `[ك]`:** حوار Compose ذو `usePlatformDefaultWidth = false` يقيس محتواه على `Configuration.screenWidthDp × screenHeightDp` (المساحة بلا تجهيزات النظام) ثم يضبط النافذة على ما قاسه المحتوى (`window.setLayout(child.measuredWidth, child.measuredHeight)`)؛ فتتوسّط نافذةً أصغر من الشاشة ويظهر ما خلفها فوقها وتحتها (`androidx.compose.ui:ui-android:1.6.6`، `AndroidDialog.android.kt:230-266` في ملفّ المصادر). وهذا يبطل ما طلبه الحوار من `MATCH_PARENT` والوضع الغامر ووضع القصّ عند أوّل قياس. **الإصلاح:** `ImmersiveFullScreenDialog` — `ComponentDialog` بسمة ملء الشاشة و`ComposeView` محتواه، ونافذته `MATCH_PARENT` بلا حدود ولا تقليم وبأشرطة النظام مخفيّة.
* **M-02 — ما يصل الخادم `[ك]`:** `surfaceWidthPx` و`surfaceHeightPx` هما بُعدا نافذة الحوار كلّها (لا 83% منها)، و`surfaceOriginXPx`/`YPx` الأصل في إطار النافذة (صفر في الغالب)؛ و`gridCols`/`gridRows` تخرجان من تلك المساحة بالقاعدة نفسها (6 مم). **الخلايا المستثناة `cutoutCells` هي خلايا قصّ الكاميرا وحدها كما كانت** — لا يستثني الزرّان ولا العدّاد خليةً (لا مفتاح جديد). و`totalCells = gridCols × gridRows − |cutoutCells|` كما هو. **العدّاد والعنوان نصٌّ فوق الشبكة بلا لمس (اللمسة عليه لمسة على الخلية تحته)؛ والزرّان بعد 15 ث فوق الشبكة بصندوق أقلّ من خلية ارتفاعاً مركَزَين على حدّ بين صفّين (`TouchGridOverlay`)، فلا خلية مطلوبة تُغطّى كاملةً، وتبقى لكل خلية يلمسها الزرّ 60% من ارتفاعها حرّةً**؛ وهذا يحلّ محلّ E21-٤ («الأزرار والعدّاد في شريط تحت السطح»). **للقارئ على الخادم (القاعدة 25):** أبعاد الشبكة التي تزيد صفوفها عمّا كانت (12×20 على جهاز حسن) مقبولة بعقد المنتِج نفسه — الأساس من الختم 71 (`MM_TARGET`) لا من 8×15 — ولا يتغيّر فيها إلا العدد.
* **M-09/M-10 — ما يصل الخادم `[ك]`:** «يعمل» على الصفّ **لا يفتح الحوار**: يكتب `pass` بلا `measurements` وحدها، وكل شريحة من الأربع (يعمل · لا يعمل · لم أختبر · غير متوفر) تكتب جوابها وتُبقي الخامّ المخزَّن إن وُجد (`ManualChipActions.TAP_KEEPS_STORED_RAW` = M-02 وM-09 وM-10). فقد يُقرأ `pass` على M-09/M-10 **بلا `measurements`** (حالة `WITHOUT_RAW` المعلنة في القارئ، لا شذوذ)، أو بخامّ محفوظ `endReason` فيه غير الجواب الحالي (كخامّ `auto_detected` تحت جواب `fail` نقره الفنّي) — فلا يُستنتج جواب من خامّ ولا خامّ من جواب (القاعدة 20). «يعمل» **داخل** حوار الصوت (`user_marked_working`) هو زرّ الحوار ولم يتغيّر.

**تحديث 2026-10-21 (أمر وكيل التطبيق ٨ — M-02 «إطار + X»، ف-02/ف-03؛ `fieldsVersion` يبقى 71، لا اسم حقل قائم تغيّر ولا دلالته إلا ما في العقد أدناه، ولا APK):**
* **قرار المستخدم 2026-10-21 (منقول من نصّ الأمر، غير متحقَّق من هذا المستودع [ن]):** «M-02: استبدل الشبكة الحالية باختبار لمس من نوع «إطار + X» بقرار حسن. وصفه: «يفحص الزوايا فقط وحرف X في المنتصف». ولقطة هاتفه (اختبار Touch في *#0*# على الأرجح) تُظهر إطاراً من الخلايا على محيط الشاشة كله وقطرين متقاطعين في المنتصف. الهدف: تخفيف عبء الفنّي (الحركات الكثيرة) مع بقاء كشف أعطال الأطراف والزوايا. القرار في حجم الخلايا وعددها على الإطار والقطرين لك، وسطر لقرارك. ثوابت من E21: ملء الشاشة بالمضيف الغامر، إغلاق تلقائي عند اكتمال الخلايا المطلوبة، «يعمل» لا يفتح الحوار، وكل الشرائح الأربع تكتب جوابها وتُبقي الخامّ. لا نصّ جديد. الزرّان بعد 15 ث لا يغطّيان أي خلية مطلوبة. الكاميرا: استثنِ خلايا فتحتها الفعلية بأصغر هامش ممكن. […] اكتب في التقرير صراحةً ما لا يغطّيه التصميم الجديد (المناطق الداخلية بين الإطار والقطرين).»
* **عقد السلك — منقول بحرفه** من `evidence/run-2026-10-18/wire-m02-frame-x-2026-10-21.md` في مساحة العمل (المنسّق، 2026-10-21 04:40 +03؛ **منقول، غير متحقَّق من هذا المستودع [ن]**، وكذلك ما يذكره عن `neuralcheck-backend@05275ba` من أسطر `fv68Reader.js`). هذا هو العقد الذي يكتبه التطبيق ويقرؤه الخادم؛ ما يخالفه الكود يُبلَّغ به (القاعدة 14):

  > # عقد السلك — M-02 «إطار + X» (fv71) — 2026-10-21 04:40 +03 — المنسّق
  > **المصدر الواحد لأمرَي التطبيق والخادم (القاعدتان 7 و24).** يُلحَق على ف-02/ف-03 من `docs/FV68_CONTRACT.md` في NeuralChek (المصدر الحاكم، وكيل التطبيق يكتب ملحقه المؤرَّخ) ونسخته في neuralcheck-backend `docs/FV71_CONTRACT_SECTION.md` (يُنسخ بعد التزام التطبيق، القاعدة 7). ما يخالفه الكود: توقّف وأبلغ (14).
  >
  > ## لماذا عقد جديد (فحص المنسّق، قراءة، neuralcheck-backend@05275ba)
  > - الخادم يقرأ `gridCols` و`gridRows` و`totalCells` و`touchedCells` و`untouchedCells` و`cutoutCells` من `manualResults[M-02].measurements` (`functions/src/analysis/fv68Reader.js:1225-1256`). `gridBasis` من الختم وحده: `MM_TARGET` لكل ختم ≥ 71 (`:1233-1240`) فلا تُفرَض 8×15.
  > - **العلاقة الملزمة:** `totalCells = gridCols × gridRows − |cutoutCells|`؛ كسرها يُعلَن شذوذ `TOUCH_GRID_TOTAL_CELLS_RELATION_BROKEN` (`fv68Reader.js:1609-1613`)، و`touched + |untouchedCells| = totalCells` وإلا `TOUCH_GRID_TOUCHED_UNTOUCHED_SUM_MISMATCH` (`:1614-1616`)، واكتمال `auto_complete` يقتضي `untouched = ∅` و`touched = total` (`:1617-1622`)، والفهارس `0 ≤ i < cols×rows` (`:1599-1606`).
  > - **«إطار + X» مجموعةٌ مطلوبة جزئية من الشبكة**: خلايا الداخل بين الإطار والقطرين غير مطلوبة. لا تُمثَّل بالعقد الحالي إلا بإدخالها في `cutoutCells` (فيقرؤها الخادم قصّاً، وهو غير صحيح: القصّ للكاميرا) ⟵ **تغيير صريح في العقد والخادم** (القاعدتان 22 و25).
  >
  > ## الإضافة (كلّها في `manualResults[M-02].measurements`، بجوار المفاتيح الحالية؛ لا يُغيَّر اسم مفتاح قائم ولا دلالته إلا ما يُذكر)
  > | المفتاح | النوع | المعنى |
  > |---|---|---|
  > | `gridPattern` | نصّ، قيمتان: `ALL_CELLS` · `FRAME_AND_DIAGONALS` | نمط المجموعة المطلوبة. التطبيق الجديد يكتب `FRAME_AND_DIAGONALS` دائماً. |
  > | `notRequiredCells` | قائمة أعداد صحيحة (فهرس الخليّة `row × gridCols + col`) | الخلايا **غير المطلوبة** بحكم النمط (داخل الشبكة بين الإطار والقطرين) و**ليست** في `cutoutCells`. فارغة `[]` لـ`ALL_CELLS`. |
  >
  > **العلاقات الجديدة (تحلّ محلّ العلاقتين القديمتين حيث تختلف):**
  > 1. `cutoutCells` و`notRequiredCells` **منفصلتان** (لا فهرس في كليهما).
  > 2. `totalCells = gridCols × gridRows − |cutoutCells| − |notRequiredCells|` = عدد الخلايا **المطلوبة** (غير المقصوصة وغير المستثناة بالنمط).
  > 3. `touchedCells` = عدد الخلايا **المطلوبة** التي لُمست (≥ عيّنة واحدة داخلها)؛ لمسة في خلية غير مطلوبة **لا تُحسب** هنا (والخامّ لا يُمسّ: عيّنات الضربات كلّها تُكتب كما هي، القاعدة 20).
  > 4. `untouchedCells` = الخلايا **المطلوبة** التي لم تُلمَس عند الإغلاق (لا تحوي مقصوصة ولا غير مطلوبة)؛ `touched + |untouchedCells| = totalCells`.
  > 5. `endReason = auto_complete` ⟺ لُمست كل الخلايا المطلوبة (`untouched = ∅`)؛ كما كان.
  > 6. **تعريف النمط `FRAME_AND_DIAGONALS`** (يعرّفه التطبيق، ولا ينسخ الخادم هندسته: القاعدة 24؛ يتحقّق الخادم من حدّ أدنى بسيط فقط): **الإطار** = كل خلايا الصفّ الأول والصفّ الأخير والعمود الأول والعمود الأخير؛ **القطران** = الخلايا التي يقطعها كل من القطرين الواصلين بين زوايا الشبكة الأربع (مسار متّصل بالجيرة الثمانية من زاوية إلى الزاوية المقابلة) **[حلّ محلّه البند 4 من «الفقرة 2 المؤرَّخة 2026-10-21» أدناه: القطران من الزاوية الداخلية للإطار إلى المقابلة، لا بين زوايا الشبكة الأربع]**. المطلوب = الإطار ∪ القطران ناقصاً `cutoutCells`. **حدّ الخادم الأدنى:** لا خلية إطار (من الحواف الأربع) في `notRequiredCells` (وإلا شذوذ `TOUCH_GRID_PATTERN_FRAME_CELL_NOT_REQUIRED`).
  > 7. **غياب المفتاحين** تحت ختم ≥ 71 (بُنى بناء الجولة 324578f وما قبله؛ لم يخرج من جهاز حسن): حالة صريحة `gridPatternState = NOT_DECLARED` وتبقى العلاقتان القديمتان (`notRequiredCells` ≡ ∅)؛ **ليس شذوذاً** (القاعدة 22)، ويُعدّ `gridPattern` عندها `ALL_CELLS` ضمنياً بحالة لا بقيمة. قيمة `gridPattern` خارج القيمتين ⟵ `INVALID_VALUE` وشذوذ.
  > 8. **تحت ختم < 71:** المفتاحان الجديدان حضورهما شذوذ (القاعدة 25) كما في مفاتيح fv71 الأخرى (`SCAN_CONDITIONS_KEYS_PRESENT_BEFORE_FV71` النمط نفسه، سمِّ شذوذاً مناسباً في `fv68Names.js` بمصدر واحد).
  >
  > **ما لا يتغيّر:** أسماء المفاتيح القائمة، `gridBasis` (من الختم)، `endReason` وقيمه الأربع، المفاتيح الخامّ (`strokes` وما معها)، `displayedSuspects`، `fieldsVersion` (يبقى 71: قرار المنسّق، الإصدار لم يُشحن للمحلّ؛ لا رفع بلا تغيير شكل **يخصّ إصداراً شُحن**).
  >
  > ## الكاميرا (`cutoutCells`)
  > خلايا فتحة الكاميرا الفعلية بأصغر هامش ممكن (تقرير 2026-10-21 من المستخدم: على جهاز حسن كان المستثنى 4 أعمدة × صفّين أعلى اليمين ≈ 23×11 مم، وهو أكبر من الفتحة؛ يُجاب من الكود: قصّ النظام أم هامش؟). `cutoutCells` بدلالتها القديمة (قصّ العرض) لا غير.
  >
  > ## عيّنات ذهبية (القاعدتان 39 و41)
  > التطبيق يولّد عيّنة/عيّنات M-02 الذهبية بالشكل الجديد من مُنتِجه الحقيقي، والخادم يقرؤها في اختباراته (لا عيّنة اصطناعية تخالف المُنتِج). يبلّغ وكيل التطبيق بمسارها وsha256 لتُنقل إلى الخادم.
* **ما قرّره وكيل التطبيق [ك]** (كل بند يحيل إلى مصدره في هذا المستودع؛ والسطر يجرف فالقيمة هي المرجع):
  1. **حجم الخلية يبقى 6 مم** (`TouchGridContract.CELL_TARGET_MM`)، شبكة واحدة `cols × rows` بخلايا متساوية، `cellTargetMm` في الخامّ كما كان. الحجّة: (أ) ما يمشيه الفنّي يحدّده الإطار والقطران لا الخلية: طول المسار عبر مراكز الخلايا ≈ 735 · 764 · 559 · 569 مم على الأسطح الأربعة مقابل ≈ 1705 · 1844 · 1115 · 1291 مم لمسح كل الصفوف (مخرَج `TouchGridPatternTest`، سطور `FRAME-X`)، فتكبير الخلية لا يخفّض الحركات؛ (ب) ما يشتريه التكبير تسامحٌ في تتبّع القطر (نصف خلية على الأكثر عن الخطّ)، وثمنه دقّة خلايا الأطراف وقيمةٌ يقرؤها الخادم (`cellTargetMm` — هل يثبّتها: غير متحقَّق من هذا المستودع [ن])؛ (ج) تتبّع القطر بالإصبع بند جهاز لم يُجرَّب (القاعدة 19)، والقيمة ثابت واحد يُغيَّر بعد جولة الجهاز بلا تغيير عقد.
  2. **تعريف القطر الدقيق — خوارزمية المسار** (`TouchGridPattern`، المصدر الواحد، القاعدة 24؛ تُصدَّر دوالّه للاختبارات وللعيّنات الذهبية): المحور الأطول من الشبكة «رئيسيّ» بـ`M` خلية (الصفوف إن كان `rows ≥ cols` وإلا الأعمدة)، والآخر «ثانويّ» بـ`m`. يقطع الخطّ الواصل بين الزاويتين العلويّة اليسرى والسفلية اليمنى، عند منتصف الخلية رقم `i` على المحور الرئيسيّ (`i = 0 … M−1`)، الإحداثيَّ الثانويّ `y = (i + ½) × m / M`، فتُؤخذ الخلية الثانويّة `floor(y)`؛ وإن كان `y` عدداً صحيحاً (يمرّ الخطّ بالحافّة المشتركة بين خليتين تماماً) تُؤخذ الخليتان `y − 1` و`y`. فللقطر `M` خلية ويزيد واحدة عن كل عبور تامّ. والقطر الثاني نظير الأوّل أفقياً (مرآة يمين-يسار). **لماذا لا كل خلية يمسّها الخطّ:** خلية يقصّ الخطّ ركنها فقط تُلزم الفنّي بمتابعة الخطّ بالمليمتر؛ وخلية منتصف الخطوة هي ما يعبره إصبعٌ يرسم الخطّ. **المثبَت بالاختبار على كل شبكة حتى 70×70:** الزاويتان فيه، سلسلة متّصلة بالجيرة الثمانية، كل خلية فيه يمرّ بها الخطّ المستقيم (حساب صحيح بلا قسمة)، النمط متماثل يميناً-يساراً وأعلى-أسفل، لا خلية خارج الشبكة. **الإطار** = كل خلايا الصفّ الأول والأخير والعمود الأول والأخير. **النمط** = الإطار ∪ القطران، و**المطلوب** = النمط ناقصاً `cutoutCells`؛ `notRequiredCells` = الشبكة ناقصاً النمط ناقصاً `cutoutCells` (مرتّبة، بلا تكرار).
  3. **الرسم والمواضع:** خلايا الإطار والقطرين وحدها مرسومة (مضيئة خضراء أو داكنة؛ وخلية قصّ من النمط بمحيط أبيض)، والمثلّثات الأربعة خلفية داكنة بلا خلايا. **العدّاد والعنوان والتلميح والزرّان** على خلايا `notRequiredCells` وحدها، بمستطيلات من خلايا كاملة تُقرَّب **نحو الداخل** إلى بكسلات صحيحة، فلا يتقاطع مستطيلٌ منها مع مستطيل أيّ خلية مطلوبة ولو بكسلاً (`TouchGridOverlay.place`؛ يختبره `requiredCellsMetBy` بالمقارنة الدقيقة لا بالعيّنات). **الزرّان:** في أعرض مقطع من الخلايا الحرّة على صفّين متراكبين داخل مثلّث واحد (التعادل للصفوف الأدنى: المثلّث السفلي على هاتف قائم)، كلٌّ بارتفاع خليتين (12 مم) وعرض `(المقطع − 1) / 2` خلية بعمود حرّ بينهما، وإن لم يكن في أيّ مثلّث مقطعٌ بخمس خلايا يبقى الزرّان على الشريط السفلي القديم (`buttonRowWithoutGrid`) لأنهما يُنهيان التشغيل فلا يُترَكان (هي الحالة الوحيدة التي قد يغطّيان فيها خلية مطلوبة، وفي مسح 320 حجماً من 55×66 مم إلى لوح 200 مم، قائماً ومقلوباً، لم يجد الزرّان موضعاً في عشرة أحجام: نسبة 1.2 بضلع 55–62 مم، وهاتف بضلع قصير 55 مم مقلوباً بنسبة 1.33 و1.5 — شبكات بـ9–12 صفّاً؛ مخرَج `TouchGridFullScreenLayoutTest`، سطر `FRAME-X-FIT`). **النصّ:** أكبر مستطيل حرّ بعرض ≥ 4 خلايا خارج الزرّين (الأعلى عند التساوي). العدّاد شفّاف اللمس كما كان: لمسة عليه تصل إلى السطح ولا تُضيء خلية (تحته خلية حرّة) وتُسجَّل خطّاً في الخامّ.
  4. **الكاميرا — `cutoutCells` بدلالتها القديمة وبلا هامش:** خلايا يتداخل مساحةً مع `DisplayCutout.boundingRects` كما سلّمها النظام (`TouchGridSurfaceView.kt:75-76`) ولا يضيف الكود هامشاً (`TouchGridLayout.compute`، الحلقة على `cutouts`: `floor(l / cw)` و`ceil(rr / cw) − 1`)؛ لمس الحافّة وحدها ليس تداخلاً. يثبّته الاختبار عند ±1 بكسل. **جواب سؤال المستخدم (هل العمودان الأربعة × الصفّان قصّ النظام أم هامش الكود؟):** الكود لا يضيف هامشاً — فما أخرج 4 أعمدة × صفّين هو **المستطيل الذي أعلنه النظام + تقريب إلى خلايا كاملة**؛ وحسابياً لا يلمس مستطيلٌ أربعة أعمدة بخلايا 6 مم إلا إن زاد عرضه عن خليتين كاملتين (> 12 مم)، أي أنّ ما أعلنه النظام لهذا الهاتف أعرض من ثقب كاميرا عادي. **غير مقيس على الجهاز:** القيمة الفعلية في `cutoutRectsPx` من خامّ ذلك التشغيل (لا أصل إليها).
  5. **ما يصل الخادم:** `gridPattern = "FRAME_AND_DIAGONALS"` و`notRequiredCells` دائماً في كل خامّ يُكتب، و`totalCells` و`touchedCells` و`untouchedCells` و`endReason = auto_complete` بالعلاقات 1–5 أعلاه؛ الخامّ `strokes[]` بكل عيّنة بما فيها لمسات الخلايا غير المطلوبة بلا تنعيم (القاعدة 20). لا مفتاح آخر جديد.
* **ما لا يغطّيه «إطار + X» (صراحةً):** المثلّثات الأربعة بين الإطار والقطرين: لا خلية فيها مطلوبة، فعطل اللمس في وسطها **لا يكشفه اكتمال الخلايا** (ولا يكشفه الخامّ إلا إن مرّ فيها إصبعٌ، وهو غير مطلوب). يبقى مكشوفاً: الأطراف والزوايا (الإطار) والقطران. نسبة مساحة المناطق غير المطلوبة (عدد الخلايا غير المطلوبة ÷ كل الخلايا، بلا كاميرا): 1080×2400@400 **61.1%** · 1440×3200@515 **61.5%** · 1080×1920@440 **55.6%** · 1200×1600@400 **61.5%**.
* **الأرقام (من الحساب، `TouchGridPatternTest`؛ بلا كاميرا):** **[جدول الخلية 6 مم هذا حلّ محلّه جدول البند 8 من «الفقرة 2 المؤرَّخة 2026-10-21» أدناه]**

  | السطح | الشبكة | كل الخلايا | الإطار | القطران بعد الإطار | **المطلوب** | غير المطلوب |
  |---|---|---|---|---|---|---|
  | 1080×2400@400 | 11×25 | 275 | 68 | 39 | **107** | 168 |
  | 1440×3200@515 | 12×26 | 312 | 72 | 48 | **120** | 192 |
  | 1080×1920@440 | 10×18 | 180 | 52 | 28 | **80** | 100 |
  | 1200×1600@400 | 13×17 | 221 | 56 | 29 | **85** | 136 |

  **زمن الفنّي على الجهاز: غير مقيس.**
* **قارئ الخادم (القاعدة 25) — منقول، غير متحقَّق [ن]:** المفتاحان `gridPattern` و`notRequiredCells` جديدان؛ العلاقتان القديمتان تنطبقان على ما قبلهما فقط (`gridPatternState = NOT_DECLARED`)؛ والعيّنات الذهبية الثلاث `app/src/test/resources/derived/fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-*.json` أُعيد توليدها من المنتِج الحقيقي في هذا الالتزام (بصماتها في README ذلك المجلّد وفي تقرير الأمر) لتُنقل إلى الخادم (القاعدة 7).

* **M-02 — الفقرة 2 المؤرَّخة 2026-10-21 (شكل اختبار اللمس في قائمة سامسونج) `[ك]`** — أمر المستخدم (منقول من نصّ الأمر، غير متحقَّق من هذا المستودع [ن]): «تعديل شكل M-02 ليطابق شكل اختبار اللمس في قائمة سامسونج … اللون سليم، والفرق في الشكل والتصميم … خلايا مربعة، عددها وحجمها من حساب مقاس الشاشة (نحو 18 عموداً) … إطار خلية واحدة + قطران متصلان من الزاوية الداخلية إلى الزاوية المقابلة بلا فراغ عند الأطراف، بلا كتلة وسطى مستقلة … لا نصّ فوق الخلايا. إن احتاج العنوان والعدّاد مكاناً فليكن في الشريط العلوي الفارغ … فتحة الكاميرا تبقى مستثناة … الهدف عدم تغيير `gridPattern`». **لا مفتاح جديد ولا قيمة جديدة ولا تغيير لدلالة مفتاح قائم في السلك** (المفتاحان `gridPattern` و`notRequiredCells` وقيمتا `gridPattern` كما في الفقرة 1؛ الخادم لا يتغيّر). ما يتغيّر هو **هندسة** `FRAME_AND_DIAGONALS` عند التطبيق وحدها (والخادم لا ينسخ الهندسة: القاعدة 24) ودلالتان للسطح يُنصّ عليهما:
  1. **الخلية مربّعة وحجمها 4 مم** (`TouchGridContract.CELL_TARGET_MM`، كانت 6): `cols = round(عرض النافذة / (4 مم × pxPerMm))`، والخلية `عرض النافذة / cols` في المحورين، و`cellTargetMm` في الخامّ 4.0. الأعمدة والصفوف من حساب الشاشة لا من رقم ثابت (نحو 18 عموداً على هاتف عرضه 72 مم).
  2. **الشريط العلوي الفارغ:** `rows` = أكبر عدد صحيح من الخلايا يتّسع تحت شريط لا يقلّ عن خليتين (`TouchGridContract.TOP_STRIP_CELLS = 2`): `rows = floor((ارتفاع النافذة − 2 × الخلية) / الخلية)`، والشريط هو ما بقي فوق الشبكة (من خليتين إلى أقلّ من ثلاث؛ ≈5% من ارتفاع هاتف طويل)، فتنتهي الشبكة عند الحافّة السفلى للنافذة تماماً. **سطح اللمس هو منطقة الشبكة لا النافذة:** `surfaceWidthPx` = عرض النافذة، `surfaceHeightPx` = `rows × الخلية` (مقرَّباً إلى بكسل)، و`surfaceOriginYPx` = أصل النافذة + ارتفاع الشريط؛ فصارت `cellWidthPx` و`cellHeightPx` متساويتين (فرق ≤ نصف بكسل على `rows`). **هذا يحلّ محلّ سطر «M-02 — ما يصل الخادم» أعلاه (بُعدا نافذة الحوار كلّها)** في أمر واحد: أنّ سطح اللمس يساوي الشبكة، وهو شرط قارئ الخادم (`touchPath.js`: خلية النقطة = `floor(x / cellWidthPx)` و`floor(y / cellHeightPx)` في إطار السطح، وحافّة الشبكة هي حافّة السطح). **لمسة في الشريط تقع خارج السطح** (`y` سالب في إطار السطح): تُكتب في `strokes[]` كما سلّمها النظام (القاعدة 20) ولا تُضيء خلية، وبقاعدة الخادم نفسها لعيّنة خارج السطح: لا تزور خلية، والقطعة التي أحد طرفيها خارج السطح لا تزور إلا خلية طرفها الذي على السطح (`TouchGridRecorder.addSample`، يقابله `recomputeCells` في الخادم).
  3. **الإطار:** كما كان — خلايا الصفّ الأول والأخير والعمود الأول والأخير، خلية واحدة سماكةً.
  4. **القطران (خوارزمية المسار، تحلّ محلّ البند 2 من «ما قرّره وكيل التطبيق» أعلاه؛ `TouchGridPattern`، المصدر الواحد، القاعدة 24):** الداخل مستطيل `w = cols − 2` عموداً × `h = rows − 2` صفاً (لا قطر إن نقص أحدهما عن 1). القطر الأول يصل **الزاوية الداخلية العليا اليسرى للإطار** (زاوية الخلية (1, 1)) **بالزاوية الداخلية السفلية اليمنى** (زاوية الخلية (rows − 2, cols − 2))، فيبدأ وينتهي عند الإطار ولا يبقى فراغ عند الطرفين؛ والثاني نظيره أفقياً (مرآة يمين-يسار). في الصفّ الداخلي `i` (من 0 إلى h − 1) يغطّي الخطّ `x` من `i × w / h` إلى `(i + 1) × w / h`، فتُؤخذ **كل الأعمدة التي يدخل الخطّ مساحتها المفتوحة**: من `floor(i × w / h)` إلى `ceil((i + 1) × w / h) − 1`. أي أنّ القطر مجموعة **كل خلية يمرّ بها الخطّ** (إصبعٌ يرسم الخطّ يُضيء بالضبط هذه الخلايا بعبور الشبكة)، لا الخلية الواحدة في كل صفّ كما كان؛ وسلسلته متّصلة بالأضلاع المشتركة إلا عند نقطة شبكيّة يقطعها الخطّ بالضبط (عددها `gcd(w, h) − 1`، ولا واحدة إن لم يشتركا في عامل)، حيث تتماسّ خليتان بركن واحد. **لا كتلة وسطى مستقلة:** الوسط هو تقاطع القطرين وحده. المثبَت بالاختبار على كل شبكة حتى 70×70: المجموعة مساوية **اختبار الأعداد** `h·j < (i+1)·w ∧ h·(j+1) > i·w` (لا يشارك الكود في خطوة)، ومساوية ما يُضيئه عبور الشبكة (`TouchGridLayout.cellsCrossed`) لخطّ بين الزاويتين الداخليتين، وسلسلة، ومتماثلة يميناً ويساراً وأعلى وأسفل.
  5. **النصّ والزرّان:** العنوان والعدّاد (في سطر واحد) والتلميح تحته في الشريط العلوي الفارغ وحده، في أعرض مقطع منه لا تغطّيه فتحة الكاميرا (`TouchGridOverlay.textBox`)، بلا لمس؛ فلا نصّ فوق خلية. **الزرّان** (بعد 15 ث) على خلايا `notRequiredCells` وحدها كما كان، بارتفاع 3 خلايا (12 مم) وعرض لا يقلّ عن 3 خلايا وعمودين حرّين بينهما. **لا نصّ جديد إلا تلميحاً واحداً مقترحاً غير معتمد** (انظر أدناه).
  6. **الكاميرا:** `cutoutCells` بدلالتها كما كانت (خلايا يتداخل مساحةً مع `DisplayCutout.boundingRects` بلا هامش)، لكنّ الخلايا تُحسب على **الشبكة**: **فتحة تقع كلّها في الشريط لا تقصّ خلية**. عدد الخلايا المستثناة على جهاز حسن بالشكل الجديد: **غير مقيس** (قيمة `cutoutRectsPx` لم تصل).
  7. **ما يصل الخادم:** العلاقات 1–5 من الفقرة 1 كما هي بلا تغيير؛ والحدّ الأدنى الذي يتحقّق منه الخادم (لا خلية إطار في `notRequiredCells`) يبقى صادقاً لأنّ الإطار كامل. ختم `fieldsVersion` يبقى 71 (الإصدار لم يُشحن للمحلّ).
  8. **الأرقام (من الحساب، المرجع المستقلّ `tools-m02-shape-reference.js`، مخرَجه `evidence/run-2026-10-18/m02-shape-reference-2026-10-21.txt` في مساحة العمل؛ بلا كاميرا):**

     | السطح | الشبكة | الشريط (بكسل · %) | الإطار | القطران بعد الإطار | **المطلوب** | غير المطلوب |
     |---|---|---|---|---|---|---|
     | 1440×3040@502 | 18×36 = 648 | 160 · 5.26% | 104 | 96 | **200** | 448 |
     | 1080×2400@400 | 17×35 = 595 | 176 · 7.33% | 100 | 87 | **187** | 408 |
     | 1440×3200@515 | 18×38 = 684 | 160 · 5.00% | 108 | 96 | **204** | 480 |
     | 1080×1920@440 | 16×26 = 416 | 165 · 8.59% | 80 | 72 | **152** | 264 |
     | 1200×1600@400 | 19×23 = 437 | 147 · 9.19% | 80 | 71 | **151** | 286 |

     أقلّ حركات تغطّي كل المطلوب (حركة = قطعة مستقيمة من مسار الإصبع): **ستّ ضربات** برفع الإصبع بينها (أربعة جوانب على خطوط منتصف الإطار وقطران بالضبط من زاوية داخلية إلى أخرى)؛ وبلا رفع **7 حركات حدٌّ أدنى** (أربعة رؤوس فرديّة الدرجة في رسم الإطار والقطرين تُلزم تكرار ضلع واحد) تتحقّق إن وُضع الإصبع على حدود الخلايا بدقّة، و**9 حركات** بلا اتّكال على الحدود؛ مرجع الأعداد المخرَج المسمّى أعلاه. **الزمن على الجهاز: غير مقيس.** وأنّ كل خلية يلمسها إصبع عادي: **غير مقيس على جهاز** (الخلية 4 مم أصغر من وسادة الإصبع، `design-m02-touch-grid-2026-10-13.md:72-73`؛ القرار قرار المستخدم وهو بند جهاز في الجولة).
  9. **ما لا يغطّيه الشكل (صراحةً):** المثلّثات الأربعة بين الإطار والقطرين كما في الفقرة 1 (نسبتها من الشبكة 63.5%–70.2% في الأسطح الخمسة أعلاه)، **والشريط العلوي** (≈5%–9% من الارتفاع) خارج سطح اللمس فلا يكشف عطلاً في أعلى الشاشة، وفيه الكاميرا في الغالب.
  10. **التلميح المقترح — مقترح، غير معتمد من حسن:** «مرّر إصبعك على حواف الشاشة ثم على القطرين» (تركيّته: «Parmağınızı ekranın kenarlarında, ardından iki köşegen üzerinde gezdirin»)، في `manual_touch_hint_v2` (التلميح في الحوار) و`manual_touch_response_instruction` (نصّ الصفّ)، بدل نصّيهما القديمين («مرّر إصبعك على كل الشاشة لتلوين كل المربعات» و«ارسم بإصبعك على كامل الشاشة») اللذين لا يطابقان الشكل. العدّاد والعنوان وزرّا «لا يعمل» و«لم أختبر» بنصوصها المعتمدة.
## الملحق أ — أوامر التحقّق ومخرجاتها

كل عدد في هذه الوثيقة وسمه `[أ]` يعود إلى أحد هذه الأوامر (شُغِّلت عند `ebb4b3b`، 2026-10-08؛ والكود عند `6ae3d33` مطابق — أ-١٣). **الأعداد من المخرَج لا من الذاكرة.**

**أ-١ — حالة المستودع.** `git rev-parse --abbrev-ref HEAD` ⇒ `master` · `git log -1 --format=%h` ⇒ `ebb4b3b` · `git status --untracked-files=no --short` ⇒ (فارغ).

**أ-٢ — الختم والمعرّفات.** `grep -n -E 'fieldsVersion|FIELDS_VERSION|namespace =|applicationId' app/build.gradle.kts` ⇒ `:62 namespace` · `:351 val fieldsVersion = 67 // 67: reading cleanup (4 semantic, 0 schema)` · `:354 applicationId = "net.neuralcheck"` · `:381` و`:403` `buildConfigField("int", "FIELDS_VERSION", "$fieldsVersion")` · `:385 applicationId = "net.neuralcheck.datacollect"`. و`grep -n USE_V2_STRESS app/build.gradle.kts` ⇒ `:371 true` (launch) · `:396 false` (datacollect).

**أ-٣ — ثوابت `android.jar`.** قراءة جدول ثوابت `android/os/BatteryManager.class` في `…/Sdk/platforms/android-{33,34,35}/android.jar` (محلّل ملفات class صغير في المجلّد المؤقّت، لا في المستودع):

```
android-33: BATTERY_PROPERTY_CAPACITY=4 CHARGE_COUNTER=1 CURRENT_AVERAGE=3 CURRENT_NOW=2 ENERGY_COUNTER=5 STATUS=6 ; EXTRA_VOLTAGE="voltage"
android-34: (نفسها) + EXTRA_CHARGING_STATUS="android.os.extra.CHARGING_STATUS" + EXTRA_CYCLE_COUNT="android.os.extra.CYCLE_COUNT"
android-35: (نفسها كـ34)
```
و`PowerManager`: `getThermalHeadroom(I)F` · `getCurrentThermalStatus()I` · `addThermalStatusListener(...)` موجودة في `android-30`؛ و`getThermalHeadroomThresholds()` في `android-35` فقط. و`BatteryManager.getIntProperty(I)I` / `getLongProperty(I)J` موجودتان.

**أ-٤ — أعداد المخطَّط** (`counts.js`، يقرأ الأنواع بالانتظام من المصدر):
```
TimeSeriesHeader props: 12 · TimeSeriesFooter props: 34 · VerificationSummary props: 26
DataQuality props: 17 · StressEffectiveness props: 26
V2_VARIABLE_DEFINITIONS entries: 11 · V1 TimeSeriesVariables entries: 18
phases: 0:INIT_PROFILING:30s@2Hz | 1:STORAGE_STRESS:75s@1Hz | 2:MEMORY_STRESS:60s@1Hz | 3:CPU_SUSTAINED:75s@4Hz | 4:GPU_SUSTAINED:75s@4Hz | 5:COMBINED_PEAK:60s@4Hz | 6:BURST_PATTERN:45s@10Hz | 7:COOLDOWN:60s@2Hz
sum duration s: 480 | expectedSamples: 1605
golden(honor) footer.batteryHealth keys: 13 | verificationResults keys: 26 | footer keys: 33
VerificationSummary props not in golden: (none) | golden not in props: (none)
```

**أ-٥ — التذييل: صنف ↔ DTO ↔ ذهبية** (`footerdiff.js`):
```
TimeSeriesFooter props: 34 | TimeSeriesFooterDto props: 31 | golden(honor) footer keys: 33
in TimeSeriesFooter but not in DTO: phaseBoundaries,preScanWarnings,calibration
in DTO but not in TimeSeriesFooter: (none)
golden keys not in DTO: calibration,preScanWarnings
DTO props absent from golden: (none)
TimeSeriesFooter props absent from golden: phaseBoundaries
```

**أ-٦ — القرّاء.**
* **R1:** `git grep -n -E '\.v\[[0-9]+\]|\bv\[[0-9]+\]|\.v\.getOrNull|\bIDX_[A-Z_]+|\bV_IDX_[A-Z_]+|V2_SAMPLE_VAR_COUNT|variables\.size|\.v\.size' -- 'app/src/main/*.kt'` بعد استبعاد أسطر التعليق ⇒ **55** سطراً: `StressEffectivenessCalculator.kt` 19 · `TimeSeriesFileReader.kt` 13 · `BatteryIndicators.kt` 8 · `SamplerStats.kt` 5 · `StressTestServiceV2.kt` 3 · `DisciplinedSampler.kt` 2 · `StressTestService.kt` 2 · `V2Models.kt` 1 · `BehaviorFingerprintBuilder.kt` 1 · `AiScoringEngine.kt` 1. (بعضها تعريف أو سجلّ لا قراءة.)
* **R2:** `git grep -l -E 'TimeSeriesData|TimeSeriesFooter|TimeSeriesSample|TimeSeriesHeader|TimeSeriesFileReader|TimeSeriesFileWriter|RawTimeSeriesPayload|TimeSeriesFooterDto|TimeSeriesHeaderDto|TimeSeriesSampleDto|VariableDto|VariableDefinition|VerificationSummary|BatteryHealthSnapshot' -- 'app/src/main/*.kt'` ⇒ **20** ملفاً، و**14** بعد استبعاد أسطر التعليق (الستّة التي تُذكر في تعليق فقط منها `AdaptiveIntensityController.kt:85` و`AiCpuWorkload.kt:215`).
* **R3:** `git grep -n -E '\b(f|footer|…|header|timeSeries\.header)\.(totalSamples|endTimestamp|…|scanType)\b' -- 'app/src/main/*.kt'` (قائمة أعضاء التذييل والترويسة كاملة) بعد استبعاد التعليق ⇒ **46** سطراً: `BehaviorFingerprintBuilder.kt` 43 · `TimeSeriesFileWriter.kt` 3.
* **`fieldsVersion`:** `git grep -n 'fieldsVersion\|FIELDS_VERSION' -- 'app/src/main/*.kt'` بعد استبعاد التعليق ⇒ 7 أسطر: ثلاثة كتابة/تمرير/سجلّ (`StressTestService.kt:1651` · `BehaviorFingerprintBuilder.kt:233` · `TimeSeriesFileWriter.kt:449`) وتعريفان (`TimeSeriesFileWriter.kt:638` · `AiAnalysisRequest.kt:136`) وسطران يحملان تعليقاً فقط (`TimeSeriesFileWriter.kt:83` · `BatteryReader.kt:279`).
* **اختبارات:** `git grep -l -E 'TimeSeriesData|TimeSeriesFooter|…|GpuFpsTracker' -- 'app/src/test/*.kt'` ⇒ **11** ملفاً (القائمة في §٤.١).

**أ-٧ — العيّنات الذهبية** (`app/src/test/resources/contract/*reports-document*`، 7 ملفات): في الـ7 جميعاً: `fieldsVersion = 67` · `footer.maxCpuTemp = -1` · `thermalSensorAvailability.cpuTemp = "missing"` · `storageSlcCacheSizeMb = 0` · `storageWriteVerifyErrors = 0` · `storageReadVerifyErrors = 0` · `cycleCountSource = "unavailable"` · `workersPinned*/PinFailed* = 0` · `cpuStressFallbackUsed = false` · `channelDiscovery.blockedBySelinux = []` · `unmeasuredDefaults = ["deviceTierMeasured","intensityFactor"]` · `variables[0].source = "SystemClock.elapsedRealtime()"` · `variables.length = 11`. Honor: `chargeCounterUah = 5596` · `designCapacityMah = 8300` · `batteryLevelStart/End = 75/72` · API 36. S10 5G (6 ملفات): `1711600` (أو `1936000`) · `4400` · API 30.

**أ-٨ — نصّ Gson 2.10.1** (`gson-2.10.1-sources.jar` في `~/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1/…`): `Gson.java:419-452` — `floatAdapter(false)` يستدعي `checkValidFloatingPoint(floatValue)` الذي يرمي `IllegalArgumentException` على `NaN`/`Infinity` برسالة «…is not a valid double value as per JSON specification. To override this behavior, use GsonBuilder.serializeSpecialFloatingPointValues() method.».

**أ-٩ — القاعدة 24.** `git grep -n -E '"(timestamp_ms|battery_temp_c|…|skin_temp_c_real)"' -- 'app/src/main/*.kt'` (18 اسم قناة) ⇒ **49** سطراً: `ChannelDiscoveryProbe.kt` 24 · `V2Models.kt` 14 · `TimeSeriesFileWriter.kt` 9 · `AiAnalysisRequest.kt` 1 · `BatteryTests.kt` 1.

**أ-١٠ — الالتزامات منذ ختم 67.** `git log -S'val fieldsVersion = 67' -- app/build.gradle.kts` ⇒ `379e8eb` (2026-09-14) · `git rev-list --count 379e8eb..HEAD -- app/src/main` ⇒ **80**.

**أ-١١ — `ACTIVITY_RECOGNITION`.** `git grep -n 'ACTIVITY_RECOGNITION' -- app/src` ⇒ لا نتائج.

**أ-١٢ — الفرق بين `TimeSeriesFooterDto` والملف.** `git log -S"preScanWarnings" -- AiAnalysisRequest.kt BehaviorFingerprintBuilder.kt` ⇒ لا نتائج (لم يُضَف المفتاح إلى DTO قطّ)؛ وكذلك `calibration` و`phaseBoundaries`.


**أ-١٣ — هذه النسخة (2026-10-08): الحالة والعدّات الجديدة.**
```
git rev-parse --abbrev-ref HEAD                      ⇒ master
git log -1 --format=%h                               ⇒ 6ae3d33
git status --untracked-files=no --short              ⇒ (فارغ قبل الكتابة)
git diff --name-only ebb4b3b HEAD                    ⇒ docs/FV68_CONTRACT.md
git grep -n 'O_DIRECT' -- app/src | wc -l            ⇒ 0
git grep -n 'charge_counter_uah' -- app/src | wc -l  ⇒ 0
git grep -n 'related_cpus' -- app/src | wc -l        ⇒ 0
git show 6ae3d33:docs/FV68_CONTRACT.md | sed -n 239,253p | grep -c '^| `'   ⇒ 13   (صفوف جرد §١.١١)
git show 6ae3d33:docs/FV68_CONTRACT.md | grep -c '^\*\*س-'                    ⇒ 26
grep -c 'manual_mark_unavailable' app/src/main/java/net/neuralcheck/presentation/screen/ManualTestDialogs.kt  ⇒ 5
awk '/private val CHANNELS = listOf\(/{f=1;next} f&&/^    \)/{f=0} f' app/src/main/java/net/neuralcheck/service/stress/v2/ChannelDiscoveryProbe.kt | grep -c '"'   ⇒ 12   (قنوات المسبار)
```
وعيّنات `contract/` الأربع `v7-client-payload-sample*.json` اسمها «client payload» وهي ما يُرسله الخادم **إلى** التطبيق (`contract/README.md`: «نُسخ حرفياً من `neuralcheck-backend` … `generate-v7-client-payload-sample.js`»، بسلسلة `computeFindings → … → buildV7ResponseForStorage`) لا حمولة التطبيق.

**أ-١٤ — Gson: الحمولة تُسقط `null` والملف يكتبه.** `gson-2.10.1-sources.jar` (في `~/.gradle`): `JsonWriter.java:449-461` — `nullValue()`: `if (deferredName != null) { if (serializeNulls) { writeDeferredName(); } else { deferredName = null; return this; // skip the name and the value } }`. والبناة: `FirebaseAnalysisService.kt:82` `GsonBuilder().create()` (بلا `serializeNulls`) · `TimeSeriesFileWriter.kt:47` `GsonBuilder().serializeNulls().create()`.

**أ-١٥ — A70 والعيّنات الذهبية السبع المختومة 67.**
```
git log -1 --format='%h %ad' --date=iso 91d5394      ⇒ 91d5394 2026-09-20 20:55:49 +0300   (fix(v2): unify v[8]/v[9] throughput units to binary MiB/s (A70))
git show 379e8eb:app/build.gradle.kts | grep -n -E 'versionCode|versionName'  ⇒ 36: versionCode = 10 · 37: versionName = "1.0.8-v6beta"   (ختم 67، 2026-09-14)
git show 91d5394:app/build.gradle.kts | grep -n -E 'versionCode|versionName'  ⇒ (النتيجة نفسها)
node (قراءة rawFingerprint.timeSeries.header.{appVersion,startTimestamp} وfooter.fieldsVersion من *reports-document*.json السبعة):
  appVersion = 1.0.8-v6beta · fieldsVersion = 67 في السبعة؛ startTimestamp من 2026-09-19T22:02:36Z (Honor) إلى 2026-09-20T06:11:40Z (zero-measured)
```
**الحاصل:** كل بدايات الفحوص السبعة تسبق 2026-09-20 17:55:49 UTC (= وقت التزام A70)، فلا يمكن أن يكون أيٌّ منها من بناء يحمل A70؛ والترويسة (`appVersion`) لا تفرّق بين ما قبل A70 وما بعده. العامل بين الوحدتين 1.048576 (`V2Models.kt:656-658`).

**أ-١٦ — اختبارات تثبّت سلوكاً يُلغيه الحسم.**
```
git grep -n -c -E 'storageWriteVerifyErrors|storageReadVerifyErrors|storageSlcCacheSizeMb|maxCpuTemp|gpuComputeGemmCorrect|criticalDefectDetected|workersPinned|workersPinFailed|isStill|preScanWarnings|selectFooterMaxTemp|aiDelegateInitError' -- 'app/src/test/*.kt'
  ⇒ TimeSeriesCooldownTest.kt:1 · TimeSeriesFileWriterIntegrityTest.kt:1 · StillnessClassifierTest.kt:12 · StressTestServiceTest.kt:12
git grep -n -c -E 'TestStatus.fromString|"not_tested"|manual_mark_unavailable|ScanRuntimePermission|autoNotTestedIds' -- 'app/src/test/*.kt'
  ⇒ BehaviorFingerprintBuilderManualCategoryTest.kt:2 · EvaluationAnswersTest.kt:2 · EvaluationPayloadBuilderTest.kt:3 · ScanRuntimePermissionTest.kt:12 · ManualTestDialogsPermissionTest.kt:2
```
(المطابقة عامّة؛ ما استُشهد به في §٣ بند 6 هو ما فُحصت أسطره: `StressTestServiceTest.kt:32-139` · `:175` تثبّتان `-1f`.)
**أ-١٧ — إصدارات الأسماء مقابل A70 (2026-10-08، الدفعة ٣ الجزء الأوّل).**
```
git log --format='%h %ad %s' --date=iso -S'versionName = "1.0.9' -- app/build.gradle.kts   ⇒ 62507f4 2026-10-07 18:36:43 +0300 (1.0.10، سطر الثابت يتغيّر) · bd077ce 2026-09-26 00:55:06 +0300 (versionName 1.0.8-v6beta -> 1.0.9, versionCode unchanged)
git log --format='%h %ad %s' --date=iso -S'versionName = "1.0.10' -- app/build.gradle.kts  ⇒ 62507f4 2026-10-07 18:36:43 +0300
git log -1 --format='%h %ad' --date=iso 91d5394 ⇒ 91d5394 2026-09-20 20:55:49 +0300 ; git merge-base --is-ancestor 91d5394 HEAD ⇒ A70-in-HEAD
```
**الحاصل:** كل بناء ≥ `1.0.9` يحمل A70؛ و`1.0.8-v6beta` اسم واحد قبل A70 وبعده (بين 2026-09-20 و2026-09-26 على الأقلّ).

**أ-١٨ — كلمة «انهيار».** `git grep -n 'انهيار' -- app/src` ⇒ `app/src/main/java/net/neuralcheck/data/local/prefs/DestructiveMigrationTracker.kt:10` (سطر واحد؛ تعليق عن الترحيل). لا نصّ لحرف D في التطبيق.
