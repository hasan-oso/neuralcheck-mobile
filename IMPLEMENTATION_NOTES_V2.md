# IMPLEMENTATION_NOTES_V2.md

أُنشئ امتثالاً لتوجيه المخطّط (السطر 20): «اقرأ المخطّط كاملاً قبل لمس أي ملف. ضع أسئلتك هنا قبل البدء.»

---

## 0. حالة المشروع قبل البدء

التحقّق التقني من البيئة:

| البند | الحالة |
|---|---|
| `app/src/main/cpp/` | ❌ غير موجود (NDK جديد كلّياً على المشروع) |
| `app/build.gradle.kts` يحوي `externalNativeBuild`/`cmake`/`ndk { abiFilters }` | ❌ لا شيء (`Grep "externalNativeBuild|cmake|ndk\s*\{|abiFilters"` → No matches found) |
| Android SDK CMake | ✅ `3.22.1` متوفّر في `<sdk>/cmake/` |
| NDK | ✅ متوفّر، 4 إصدارات: `26.1.10909125`, `27.0.12077973`, `27.1.12297006`, `28.2.13676358` |
| `cmake` في PATH | ❌ غير متاح (لكن يكفي SDK المضمّن، Gradle يجده تلقائياً) |
| `minSdkVersion` | ✅ 28 (من المرحلة 1، يُلبّي حاجة `sched_setaffinity` ومعظم compute shaders) |
| الجهاز المتّصل | `SM-G977N` (S10 5G، Snapdragon 855، Adreno 640، Android 11). يدعم arm64-v8a + OpenGL ES 3.1 + compute. |

**الاستنتاج:** البيئة جاهزة لـ NDK تقنياً، لكن إضافته نقلة معمارية للمشروع (build pipeline، APK size، debugging tooling) — قرار يستحقّ التأكيد قبل التنفيذ.

---

## 1. تأكيد فهم القسم 10 من المخطّط

| البند | فهمي |
|---|---|
| لماذا نُغيّر stress test | الحالي 85% CPU + GPU 6% من الشاشة، يفوّت silent silicon defects |
| لماذا NDK | JVM في Kotlin يولّد JIT scalar، لا يضرب FMA pipelines ولا crypto units. NEON intrinsics اليدوية + crypto extensions ضرورية لقياس قوّة silicon الفعلية |
| Verification mandatory | silent data corruption (bit flip في memory cell، FMA result wrong) أخطر من throttling لأنّه غير مرئي في metrics throughput |
| Adaptive intensity | لا نعتمد SoC name (قد يُخدع بـ /proc/cpuinfo modified). نقيس rate of temp rise كـ proxy للقدرة الحرارية الفعلية |
| لماذا حذف الجداول من البرومبت | النموذج يعرف الأجهزة أكثر من جداولنا اليدوية المتقادمة |
| OOM Killer في memory test | mmap 50% RAM على foreground service قد يُلغى من lmkd. الحلّ: 40% + MADV_DONTNEED |
| ABI | arm64-v8a (واجب) + armeabi-v7a (للأجهزة < 2018) |
| phase حقل صريح | الكود الحالي يُخزّن phase داخل `TimeSeriesSample.phase: Int` — الحقل **موجود فعلياً** ([TimeSeriesFileWriter.kt:215-218](app/src/main/java/net/neuralcheck/data/local/files/TimeSeriesFileWriter.kt:215))، لكن الـ DTO المُرسَل ([AiAnalysisRequest.kt:103-107](app/src/main/java/net/neuralcheck/data/remote/model/AiAnalysisRequest.kt:103)) أيضاً يحويه. **يبدو أنّه ليس index-based فعلاً.** نحتاج توضيح ما المقصود بـ "خطأ تاريخي" في السطر 85 من المخطّط |

---

## 2. أسئلة تقنية مطلوب حسمها **قبل** كتابة كود C++

هذه ليست مسائل تطبيقية — هي قرارات تصميم تؤثّر على بنية الـ verification بأكملها.

### Q2.1 — الـ FP32 verification reference: deterministic كيف؟ (حرج)

المخطّط (السطر 312) يقول:
> «verification: حساب trace(A×B) ومقارنته بالقيمة المرجعية»

ومثال (السطر 749 من البرومبت):
> «NEON FMA: trace(A×B) = 4127583.5 (مرجعي)»

**المشكلة:** trace(A×B) لمصفوفات FP32 يعتمد على ترتيب الجمع. NEON SIMD يجمع 4 floats معاً ثم يتراكم؛ scalar reference يجمع واحداً واحداً. النتيجتان قد تختلفان في آخر `~3 ULPs` بسبب IEEE 754 rounding.

**أسوأ:** `-O3 -ffast-math` يُمكّن المُحسّن من إعادة ترتيب الجمع لاعتباطياً، فحتى binary واحد قد يُنتج نتائج مختلفة بين runs لو CPU governors تتدخّل في تنفيذ بعض threads.

**3 خيارات للحلّ:**

| الخيار | الوصف | المقايضات |
|---|---|---|
| **A. Tolerance-based** | `assert(\|trace - REFERENCE\| < 0.5f * REFERENCE * 1e-6f)` | يقبل ~3 ULPs اختلاف. لكن defect صغير (bit flip في mantissa واحد) قد لا يُكتشف لأنّه ضمن الـ tolerance |
| **B. Integer matrices** | A و B بقيم Int8 صغيرة، trace في Int32. النتيجة بالضبط نفسها على كل CPU ومُحسّن | يضرب integer ALU بدلاً من FMA. الكلّ الـ "NEON FMA matrix multiply" يفقد معناه |
| **C. Row hash** | لكل row من C، نحسب CRC32 على bits الـ 32-bit floats. كل row hash مرجعي. النتيجة hash deterministic إن كان الـ kernel يُنتج نفس الـ floats | يُلزم أن يكون الـ kernel deterministic عبر CPU governors. تحقّق ممكن لكن صعب |

**اقتراحي:** خيار A مع epsilon ضيق (1e-5 من القيمة، أي ~10 ULPs) كحلّ عملي. اعتبار "fail" يعني فعلاً bit flip في الـ silicon، لا rounding درجة عادية. توثيق هذا صراحةً في `reference_values.h`.

**أحتاج تأكيدك على الخيار قبل البدء.**

### Q2.2 — Reference value: محسوب أين؟

المخطّط (السطر 982-989):
```
// Computed by running prime_sieve(10M) on x86 reference
// Input: limit = 10000000
```

prime_sieve مفهوم — النتيجة integer وحتمية. لكن لـ NEON FMA و GPU GEMM verification:

- **خيار 1:** يُحسب على جهاز ARM المعيار مرة واحدة، يُكتب hard-coded في `reference_values.h`. أي جهاز يخالف = defect.
- **خيار 2:** يُحسب في الذاكرة أوّل مرّة على هذا الجهاز فقط، ثم يُستخدم للـ verification في الفحوصات اللاحقة. المشكلة: لو الجهاز معطوب من الـ box، الـ reference الأوّلي بالفعل غلط، وكل اختبار يصبح "صحيح".

**اقتراحي:** خيار 1، احسبها على S10 5G المتّصل حالياً (NEON-enabled، حتمي بحدود tolerance Q2.1). **أحتاج موافقتك.**

### Q2.3 — مدخلات SHA-256: ما الـ pattern؟

المخطّط (السطر 322): «hash buffer 1MB من بيانات معروفة (seed ثابت)»

اقتراحي: `buf[i] = (uint8_t)((i * 0x9E3779B1u) >> 24) ^ 0xA5` (Knuth multiplicative hash، patternless لكن deterministic). hash مرجعي يُحسب مرة على المعمل ويُحفظ.

**هل Pattern آخر مفضّل؟**

### Q2.4 — Compute shader GPU GEMM verification

المخطّط (السطر 424): «GEMM 1024×1024، نقارن `trace(C)` بقيمة CPU-computed reference»

- GPU compute shader threads تجمع بترتيب غير حتمي.
- FP32 trace في GPU يختلف بحوالي tens of ULPs عن CPU reference.
- نفس مشكلة Q2.1، أصعب لأنّ GPU vendor قد يستبدل `+` بـ `mad` بحرّية.

**اقتراحي:** نفس tolerance approach (1e-4 لـ GPU بسبب fmad fused في معظم GPUs).

### Q2.5 — `-ffast-math` و verification

المخطّط (السطر 850) يطلب `-O3 -ffast-math`. لكن `-ffast-math` يُمكّن:
- `-funsafe-math-optimizations` (يكسر associativity)
- `-ffinite-math-only` (يُلغي NaN/Inf checks)

**اقتراحي:** `-O3` فقط لـ files المحتوية على verification (neon_fma.cpp, sha256_neon.cpp, memory_test.cpp). `-ffast-math` فقط لـ stress kernels بدون verification.

### Q2.6 — sched_setaffinity على Samsung One UI

المخطّط (السطر 285) يطلب `pinThreadToBigCluster()`. على Samsung Knox/One UI، الـ syscall قد يُرجع `EPERM` بصمت. لو فشل، الـ thread سيعمل على random core، وقد يضرب little cluster بدلاً من big.

**اقتراحي:** الدالّة تُرجع `Boolean` (نجاح/فشل). Kotlin يفحص ويُسجّل في `IMPLEMENTATION_NOTES`. لو فشل، الـ stress يستمرّ لكن `intensity_factor` يُسجّل ملاحظة.

### Q2.7 — حجم الـ memory test

المخطّط (السطر 333):
```
1. mmap MAP_ANONYMOUS بحجم 50% من available RAM
```
ثم في القسم 8 (السطر 957):
```
استخدم MADV_DONTNEED بعد كل pass لتقليل الضغط
اقرأ MemoryInfo قبل البدء، اضبط الحجم ليكون 40% (هامش أمان)
```

تعارض بسيط (50% vs 40%). **سأستخدم 40%.**

### Q2.8 — الـ "خطأ التاريخي" حول phase index-based

المخطّط (السطر 85):
> «الكود الحالي يستخرج phase index-based (خطأ تاريخي).»

تحقّقي من الكود:
- [TimeSeriesFileWriter.kt:215-233](app/src/main/java/net/neuralcheck/data/local/files/TimeSeriesFileWriter.kt:215): `data class TimeSeriesSample(val ts: Long, val phase: Int, val v: FloatArray)` — phase **حقل صريح**.
- [AiAnalysisRequest.kt:103-107](app/src/main/java/net/neuralcheck/data/remote/model/AiAnalysisRequest.kt:103): `data class TimeSeriesSampleDto(val phase: Int, val ts: Long, val v: List<Float>)` — phase **حقل صريح في الـ DTO المُرسَل**.

**أين الـ "index-based"؟** ربما المقصود:
- `BehaviorFingerprintBuilder.kt:144-146` يفلتر phases برقم الـ enum (`PHASE_IDLE=1`, `PHASE_STRESS=3`, `PHASE_COOL=5`)، متجاهلاً phase 2 (STORAGE) و phase 4 (SUSTAINED). ربما هذا هو المقصود.

**في V2:** سيكون لدينا 8 phases (0-7)، كل واحد محسوب صراحةً في `PhaseAnalysis`. لذا الإشكال يُحلّ تلقائياً.

---

## 3. تقدير الحجم — لماذا أقترح تقسيم Phase 1

تقدير عدد الأسطر للملفات المطلوبة في Phase 1 (NDK Foundation):

| الملف | تقدير الأسطر | المحتوى |
|---|---|---|
| CMakeLists.txt | ~50 | targets, ABIs, optimization flags |
| `cpu_stress.h` | ~50 | API declarations |
| `cpu_stress.cpp` | ~200 | orchestrator: pinning, threads, lifecycle |
| `neon_fma.cpp` | ~250 | NEON intrinsics matrix multiply 256×256 + verification |
| `prime_sieve.cpp` | ~80 | Eratosthenes |
| `sha256_neon.cpp` | ~400 | crypto-extension path + scalar fallback |
| `memory_test.cpp` | ~350 | March C-, Walking Ones, Random+SHA |
| `thermal_reader.cpp` | ~150 | parse /sys/class/thermal/* |
| `verification.cpp` | ~80 | hash comparison utilities |
| `stress_jni.cpp` | ~400 | JNI bridge for 8 functions, exception handling, type marshalling |
| `reference_values.h` | ~60 | hardcoded references + their derivation |
| `StressNative.kt` | ~100 | external declarations + result data classes |
| `app/build.gradle.kts` تعديلات | ~20 | externalNativeBuild block |

**المجموع: ~2,200 سطر جديد** — في commit واحد.

### المخاطر التي يحملها commit واحد كبير

1. **التحقّق المحدود:** "البناء يمرّ" لا يُثبت أن:
   - prime_sieve يُرجع 664,579 (off-by-one في الـ loop bounds شائع جداً)
   - SHA-256 يُنتج hash المرجعي (bit ordering في NEON crypto extensions صعب)
   - NEON FMA verification يمرّ ضمن الـ tolerance المختار
   - March C- يكتشف bit flips فعلاً
   - sched_setaffinity ينجح على S10 5G
2. **rollback أصعب:** لو phase 2 (DTOs) فشل بسبب suspicions في الـ NDK، إعادة الكلّ صعبة.
3. **review أصعب:** 2,200 سطر في PR واحد لا يُراجَع كلّياً.

### اقتراح التقسيم الدقيق لـ Phase 1

| Sub-phase | المحتوى | تقدير الأسطر | معيار النجاح |
|---|---|---|---|
| **1a** | CMakeLists + gradle config + JNI ping function (`ping(): Int → 42`) + StressNative.kt | ~250 | البناء ينجح، APK يحوي `libstress.so` لـ arm64-v8a + armeabi-v7a، الجهاز يُحمّل المكتبة ويُرجع 42 من Logcat |
| **1b** | prime_sieve.cpp + reference + JNI + log الناتج | ~150 | على الجهاز: log = 664579 |
| **1c** | sha256_neon.cpp (crypto + scalar) + reference + JNI | ~500 | على الجهاز: log = hash مرجعي |
| **1d** | neon_fma.cpp + verification (Q2.1 محسومة) | ~300 | على الجهاز: trace ضمن tolerance |
| **1e** | memory_test.cpp (March + Walking + Random) | ~400 | الجهاز يُكمل بدون OOM، بدون false-positive errors |
| **1f** | thermal_reader.cpp + JNI | ~200 | الجهاز يُرجع قراءات حرارية صحيحة |
| **1g** | sched_setaffinity + cluster detection + الـ JNI كامل | ~300 | الجهاز يستجيب صحيحاً، يُسجّل success/EPERM |

**كل sub-phase = commit + اختبار يدوي على S10 5G قبل الانتقال للتالي.**

البديل: المسار السريع كما طلبته. `gradle assembleLaunchDebug` ينجح (الكود يبني)، لكن صحّة الناتج لن تُتحقَّق إلاّ بعد phase 3 من V2 (StressTestService) لأنّه أوّل مكان يستدعي النيتيف فعلاً.

---

## 4. أسئلة هيكلية أصغر

### Q4.1 — `armeabi-v7a` هل ضروري فعلاً؟

minSdk = 28 (Android 9.0). آخر جهاز مهمّ تجارياً بمعمارية 32-bit فقط هو ~2018. كلّ جهاز > 2019 هو arm64-v8a.

**سؤال:** هل نحذف armeabi-v7a لتقليل APK size بـ ~3MB والتطوير أسهل (بدون `__ARM_NEON` checks)؟

### Q4.2 — حجم الـ APK المتوقّع

NDK + cmake + 2 ABIs + libstress.so لكل ABI:
- لكل ABI: ~1.5 MB stripped
- مع 2 ABIs: ~3 MB إضافة على الـ APK

النسخة الحالية: ~75 MB. الـ APK V2: ~78 MB. مقبول.

### Q4.3 — NDK version

أربعة إصدارات متاحة. الأحدث `28.2.13676358` مرفوع جداً. **اقتراحي:** `27.1.12297006` (مستقرّ، يدعم compute shaders، متوافق مع AGP 8.x).

### Q4.4 — Privacy Policy

المخطّط (السطر 994) يذكر أن `deviceProfileKey` يحوي ANDROID_ID + Firebase ID وأن privacy policy يجب أن يذكرها. **هذا الالتزام موجود حالياً؟** لم أتحقّق منه. سأضيفه إلى تنبيهات ما بعد V2 إن لم يكن موثَّقاً.

**جواب (P0.6، 2026-09-20 +03):** لا، لم يكن الالتزام موثَّقاً، والسياسة كانت لا تذكر شيئاً عن ذلك: `layerB_firebaseId` و`layerC_androidId` كانا يُرسَلان **خامّين** ضمن `deviceProfileKey` إلى حين دمج `fix/hash-fingerprint-layers` (P0.1، `DeviceFingerprint.kt`). منذ P0.1 تصير القيمتان مُهشَّرتين (SHA-256، 64 محرفاً) قبل مغادرة الجهاز؛ التفصيل في `docs/MEASUREMENT_SPEC.md`.

---

## 5. ملخّص الأسئلة المطلوب حسمها قبل البدء

أرتّبها بالأهميّة (حرجة → استشارية):

| # | السؤال | لماذا حرج |
|---|---|---|
| Q2.1 | استراتيجية FP32 verification (tolerance vs integer trace vs row hash) | يحدّد كل بنية كود verification |
| Q2.2 | كيف نحسب reference values (S10 5G الحالي vs خارج) | يؤثّر على workflow أوّل تشغيل |
| Q2.5 | `-ffast-math` يُستثنى من files verification؟ | يؤثّر على CMakeLists.txt |
| Q3 | تقسيم Phase 1 إلى 7 sub-phases (الأمان) أو commit واحد كبير (السرعة)؟ | يؤثّر على إيقاع العمل |
| Q4.1 | armeabi-v7a يبقى؟ | يبسّط الكود لو حُذف |
| Q4.3 | NDK version (27.1 أم آخر؟) | إعداد build |
| Q2.3 | SHA-256 input pattern (Knuth hash اقتراحي) | تفصيل صغير |
| Q2.6 | sched_setaffinity فشل = log فقط أم degraded mode؟ | تفصيل تنفيذ |

---

## 6. حالتي الآن

- ✅ قرأت المخطّط كاملاً (10 أقسام، 1067 سطر)
- ✅ تحقّقت من حالة المشروع (لا NDK حالياً، البيئة جاهزة)
- ✅ أنشأت هذا الملف بالأسئلة المعلّقة
- ✅ استلمت إجابات الاستشاري على كل الأسئلة (Q2.1–Q4.4)
- ✅ Sub-phase 1a: build pipeline + JNI ping ← اكتمل code-side، باقي اختبار يدوي على الجهاز
- ⏳ في الانتظار: الجهاز فُصل بعد البناء، أحتاج إعادة توصيل لتشغيل smoke test

---

## 7. سجلّ التنفيذ — Sub-phase 1a

### قرارات الاستشاري المُطبَّقة في هذه المرحلة

| البند | القرار | المُطبَّق في |
|---|---|---|
| Q2.5 (`-ffast-math` ممنوع، `-ffp-contract=off`) | تطبيق على مستوى الـ target بأكمله، ليس per-file، لكي ترث الـ kernels المستقبليّة | [CMakeLists.txt:36-43](app/src/main/cpp/CMakeLists.txt) |
| Q4.1 (arm64-v8a فقط) | حذف armeabi-v7a كاملاً | [build.gradle.kts](app/build.gradle.kts) داخل `defaultConfig.ndk` |
| Q4.3 (NDK 27.1.12297006) | pinned صراحةً عبر `ndkVersion` بدلاً من الاعتماد على default | [build.gradle.kts](app/build.gradle.kts) داخل `android` block |
| Q3 (التقسيم لـ 7 sub-phases) | 1a يحتوي **فقط** ping(); لا kernels، لا reference values | [stress_jni.cpp](app/src/main/cpp/jni/stress_jni.cpp) (39 سطر) |

### الملفات المُنشَأة/المُعدَّلة في 1a

| الملف | الحالة | الأسطر | الغرض |
|---|---|---|---|
| `app/src/main/cpp/CMakeLists.txt` | جديد | 56 | تعريف target `stress`، compile flags، link libs |
| `app/src/main/cpp/jni/stress_jni.cpp` | جديد | 49 | `pingNative()` + `JNI_OnLoad()` فقط |
| `app/src/main/java/net/neuralcheck/util/StressNative.kt` | جديد | 84 | wrapper Kotlin مع `verifyPing()` helper |
| `app/build.gradle.kts` | مُعدَّل | +21 -2 | `ndkVersion` + `abiFilters` + `externalNativeBuild { cmake { ... } }` |
| `app/src/main/java/net/neuralcheck/NeuralCheckApp.kt` | مُعدَّل | +14 -0 | استدعاء `StressNative.verifyPing()` في `onCreate()` للـ smoke test |

**الإجمالي: ~190 سطر جديد، 5 ملفات.** (تقدير سابق كان ~250؛ الفرق = توفير من JNI minimal لا يحوي kernel boilerplate.)

### نتيجة البناء

- ✅ `./gradlew assembleLaunchDebug` نجح في 1m 59s.
- ✅ `libstress.so` (45,944 bytes intermediate، 4,496 bytes في الـ APK بعد strip+compress) موجود في `lib/arm64-v8a/`.
- ✅ ABI واحد فقط (arm64-v8a)، لا armeabi-v7a كما طُلب.
- ⚠️ تحذيرات جديدة: لا (نفس 16 تحذيراً قديماً، لا شيء من الـ NDK).
- ⏳ **اختبار يدوي على S10 5G:** مُعلَّق — الجهاز فُصل بعد البناء (`adb devices` يُرجع قائمة فارغة).

### معيار النجاح المُتوقَّع للاختبار اليدوي

عند توصيل S10 5G وتشغيل التطبيق، يجب أن يظهر في `adb logcat | grep NeuralCheck.Native`:
```
I NeuralCheck.Native: JNI_OnLoad: libstress.so initialised
I NeuralCheck.Native: libstress.so loaded successfully
I NeuralCheck.Native: pingNative: returning 42
I NeuralCheck.Native: verifyPing: OK (got 42)
I NeuralCheck:        StressNative health: loaded=true pingOk=true
```

أي ناتج آخر = كسر في JNI plumbing يجب تشخيصه قبل الانتقال لـ 1b.

### bugs صودفت في 1a

لا شيء. البناء مرّ من المحاولة الأولى. NDK 27.1 + AGP 8.2.2 + cmake 3.22.1 متوافقة بدون warnings.

### قرارات إضافية في 1a خارج المخطّط

1. **`StressNative.verifyPing()`** — wrapper إضافي حول `pingNative()` لـ:
   - تحويل `UnsatisfiedLinkError` إلى `Boolean` بدل crash.
   - Logging موحَّد قبل عودة القيمة.
   - **مُبرَّر:** الاختبار اليدوي يحتاج log واحد سريع، لا boilerplate في كل call site.

2. **`PING_EXPECTED = 42` كـ const في Kotlin** — بدل أرقام سحرية في الكود.

3. **`JNI_OnLoad` في stress_jni.cpp** — يُسجّل تأكيد التحميل قبل أي Kotlin code. مفيد لتشخيص crash-on-init مستقبلاً.

4. **`-ffp-contract=off` على مستوى target بدل per-file** — أكثر defensive؛ kernels المستقبليّة ترث الإعداد بدون أن يضيفه كلّ ملف.

---

## 8. سجلّ التنفيذ — Sub-phase 1b (prime sieve)

### الملفات المُنشَأة/المُعدَّلة

| الملف | الحالة | الأسطر | الغرض |
|---|---|---|---|
| `app/src/main/cpp/stress/prime_sieve.h` | جديد | 30 | declarations لـ `neuralcheck::stress::prime_sieve_count(limit)` |
| `app/src/main/cpp/stress/prime_sieve.cpp` | جديد | 47 | Eratosthenes بـ uint8_t buffer + nothrow allocation |
| `app/src/main/cpp/reference/reference_values.h` | جديد | 60 | `VERIFICATION_REF_VERSION` + `PRIME_SIEVE_10M_REFERENCE = 664579` |
| `app/src/main/cpp/CMakeLists.txt` | مُعدَّل (+8) | — | إضافة `prime_sieve.cpp` للـ sources + `target_include_directories` |
| `app/src/main/cpp/jni/stress_jni.cpp` | مُعدَّل (+22) | — | `Java_..._runPrimeSieveTest` يستدعي الـ C++ helper |
| `app/src/main/java/net/neuralcheck/util/StressNative.kt` | مُعدَّل (+38) | — | `external fun runPrimeSieveTest(): Long` + const `PRIME_SIEVE_10M_EXPECTED` + `verifyPrimeSieve()` wrapper |
| `app/src/main/java/net/neuralcheck/NeuralCheckApp.kt` | مُعدَّل (+5/-3) | — | health probe ينقل لـ background thread + يستدعي `verifyPrimeSieve()` |

**إجمالي 1b: 137 سطر جديد + ~70 سطر تعديل.** متّسق مع التقدير الأصلي (~180 سطر).

### نتيجة البناء

- ✅ `./gradlew assembleLaunchDebug` نجح في 1m 20s.
- ✅ `libstress.so` نما من **4,496 → 306,720 bytes** في الـ APK (في 1a كان pure C، الآن libc++ runtime مستورَد لـ `std::unique_ptr` + `new(nothrow)` — متوقَّع).
- ✅ ABI واحد فقط (arm64-v8a). لا تحذيرات NDK جديدة.
- ⚠️ **اختبار يدوي على S10 5G:** ما زال مُعلَّق (`adb devices` فارغ).

### قرارات تنفيذ في 1b

| القرار | السبب |
|---|---|
| Sieve buffer = `uint8_t[]` (بايت لكل خانة) لا `bitset` | Master plan §3.3 يقول الـ kernel يجب أن يضرب L1/L2 cache. byte-per-cell يستخدم 10 MB ويُحفّز cache subsystem؛ bitset يضغط 8× ويفوّت هذا الأثر. |
| `new (std::nothrow) uint8_t[]` بدل throw | JNI لا يُمرّر C++ exceptions بأمان لـ Kotlin. الـ sentinel `-1` يدلّ على فشل التخصيص ويُترَك للـ caller يقرّر. |
| `target_include_directories(stress PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})` | يسمح بـ `#include "stress/prime_sieve.h"` بدلاً من `"../stress/prime_sieve.h"` الهشّ. سيُستفاد منه في 1c-1g. |
| Health probe في `NeuralCheckApp` انتقل لـ daemon background thread | prime_sieve(10⁷) يأخذ 50-250 ms على ARM64 modern. على main thread = startup latency محسوس. background thread → log يصل بعد ثانية. |
| `runPrimeSieveTest` و wrapper `verifyPrimeSieve` يبقيان بعد 1d | عندما يدخل sieve في الـ parallel CPU stress، الـ production path سيستدعي `prime_sieve_count` مباشرةً (بدون JNI shim)، لكن هذا الـ entry point يبقى لـ unit testing الخوارزميّة منفصلةً عند debug. |

### معيار النجاح المتوقَّع للاختبار اليدوي (1a + 1b)

```
I NeuralCheck.Native: JNI_OnLoad: libstress.so initialised
I NeuralCheck.Native: libstress.so loaded successfully
I NeuralCheck.Native: pingNative: returning 42
I NeuralCheck.Native: verifyPing: OK (got 42)
I NeuralCheck.Native: runPrimeSieveTest: starting (limit=10000000)
I NeuralCheck.Native: runPrimeSieveTest: count=664579 (expected 664579)
I NeuralCheck.Native: verifyPrimeSieve: OK (got 664579)
I NeuralCheck:        StressNative health: loaded=true pingOk=true sieveOk=true
```

### bugs صودفت في 1b

لا شيء. البناء مرّ من المحاولة الأولى. لا تحذيرات compiler، لا linker errors.

---

## 9. سجلّ التنفيذ الكامل — 1a إلى 1h

### الجدول الموحَّد

| # | الكود الجديد | معيار النجاح المتوقَّع | الحالة | bugs |
|---|---|---|---|---|
| 1a | CMakeLists + JNI ping (49 سطر cpp + 84 Kotlin) | log: `pingOk=true` | ✅ يبني | لا |
| 1b | prime_sieve (47+30 cpp + reference) | log: count=664579 | ✅ يبني | لا |
| 1c | sha256 scalar (160+40 cpp + 32-byte reference) | log: hash matches | ✅ يبني | لا |
| 1d | neon_fma + scalar reference + CRC32 (110+50) | log: trace ضمن 1e-7 | ✅ يبني | لا |
| 1e | memory_test (March + Walking + Random+SHA) | log: errors=0 | ✅ يبني | لا |
| 1f | thermal_reader (parse `/sys/class/thermal`) | log: 4 temps | ✅ يبني | `<cstdlib>` مفقود (مُصلَح) |
| 1g | cpu_affinity + cluster detection | log: pin OK أو EPERM | ✅ يبني | Kotlin `return` داخل expression body (مُصلَح) |
| 1h | NativeSmokeTestRunner orchestrator | log: allPassed=true | ✅ يبني | لا |

### الحجم النهائي

| البند | القيمة |
|---|---|
| ملفات C++ source | 7 (.cpp) + 6 (.h) + 1 reference |
| ملفات Kotlin | 2 (StressNative.kt + NativeSmokeTestRunner.kt) |
| `libstress.so` intermediate (debug) | 691,808 bytes (~675 KB) |
| `libstress.so` في APK (debug، compressed) | 330,104 bytes (~322 KB) |
| إجمالي إضافة على APK | ~325 KB compressed |
| تقدير سابق | 200-250 KB (تجاوز بسبب static libc++) |

### نتيجة البناء النهائية

`./gradlew assembleLaunchDebug` نجح في 2m 16s.

### الـ external API الكاملة (في `StressNative`)

```kotlin
// JNI smoke (1a)
external fun pingNative(): Int

// Verification kernels (1b–1e)
external fun runPrimeSieveTest(): Long       // returns 664579 on healthy CPU
external fun runSha256Test(): Boolean        // self-test + 1MiB hash check
external fun runNeonFmaTest(): Boolean       // 256×256 FMA + Tier 1/2 verify
external fun runMemoryTest(): Boolean        // March + Walking + Random+SHA

// System probes (1f, 1g)
external fun readThermalZones(): FloatArray  // [skin, batt, cpu, gpu]
external fun pinThreadToBigCluster(): Boolean
external fun pinThreadToLittleCluster(): Boolean
external fun resetAffinityToAll(): Boolean
external fun detectClusterTopology(): IntArray  // [total, distinct, big, mid, little]
```

### معيار النجاح المتوقَّع للاختبار اليدوي على S10 5G

```
I NeuralCheck.Native: JNI_OnLoad: libstress.so initialised
I NeuralCheck.Native: libstress.so loaded successfully
I NeuralCheck.Native: pingNative: returning 42
I NeuralCheck.Native: verifyPing: OK (got 42)
I NeuralCheck.Native: runPrimeSieveTest: starting (limit=10000000)
I NeuralCheck.Native: runPrimeSieveTest: count=664579 (expected 664579)
I NeuralCheck.Native: verifyPrimeSieve: OK (got 664579)
I NeuralCheck.Native: runSha256Test: starting
I NeuralCheck.Native: runSha256Test: hash matches reference
I NeuralCheck.Native: verifySha256: OK
I NeuralCheck.Native: runNeonFmaTest: starting (256x256 FP32, two-tier verification)
I NeuralCheck.Native: runNeonFmaTest: trace_ref=… trace_neon=… diff=… (threshold=…)
I NeuralCheck.Native: verifyNeonFma: OK
I NeuralCheck.Native: runMemoryTest: starting (32 MiB, March + Walking + Random+SHA)
I NeuralCheck.Native: runMemoryTest:   March C- errors=0
I NeuralCheck.Native: runMemoryTest:   Walking Ones errors=0
I NeuralCheck.Native: runMemoryTest:   Random+SHA OK
I NeuralCheck.Native: runMemoryTest: ALL OK (march=0 walk=0 sha=1)
I NeuralCheck.Native: verifyMemory: OK
I NeuralCheck.Native: readThermalZones: skin=… battery=… cpu=… gpu=…
I NeuralCheck.Native: verifyThermalZones: OK (got [...])
I NeuralCheck.Native: detectClusterTopology: cpus=8 clusters=2 big=4 mid=0 little=4
I NeuralCheck.Native: pinThreadToBigCluster: OK
I NeuralCheck.Native: resetAffinityToAll: OK
I NeuralCheck.Native: verifyAffinity: topology cpus=8 …
I NeuralCheck.Smoke:  runAll: total=…ms allPassed=true ping=✓/…ms primeSieve=✓/…ms …
```

### قرارات تنفيذ مهمّة سُجِّلت في الكود

| القرار | الموقع | الإيجاز |
|---|---|---|
| Q2.1 (Two-tier FP32 verification) | [neon_fma.h:18-38](app/src/main/cpp/stress/neon_fma.h:18) | Tier 1 = scalar reference in-process، tolerance 1e-7. Tier 2 = row CRC32 وقت الفشل. |
| Q2.2 (References hardcoded) | [reference_values.h](app/src/main/cpp/reference/reference_values.h:1) | كل القيم محسوبة Python أو رياضيّاً (π(10⁷))، موثّقة بالـ recipe. |
| Q2.3 (LCG generator) | [sha256.cpp:115-122](app/src/main/cpp/stress/sha256.cpp:115) و [memory_test.cpp:64-71](app/src/main/cpp/stress/memory_test.cpp:64) | seeds مختلفة (0x12345678 vs 0xCAFEBABE) و XOR مختلف (0xA5 vs 0x5A) لتقليل التشابه الإحصائي. |
| Q2.5 (`-ffp-contract=off`) | [CMakeLists.txt:36-43](app/src/main/cpp/CMakeLists.txt:36) | على مستوى target كاملاً، لا per-file. |
| Q2.6 (sched_setaffinity EPERM = degraded) | [cpu_affinity.cpp:84-94](app/src/main/cpp/stress/cpu_affinity.cpp:84) و [StressNative.kt:verifyAffinity](app/src/main/java/net/neuralcheck/util/StressNative.kt:1) | يُرجع `Boolean`، الـ orchestrator يستثنيه من الـ allPassed verdict. |
| Q4.1 (arm64-v8a فقط) | [build.gradle.kts:23-29](app/build.gradle.kts:23) | armeabi-v7a محذوف، NEON متاح دائماً |
| Q4.3 (NDK 27.1.12297006) | [build.gradle.kts:18](app/build.gradle.kts:18) | pinned صراحةً |

### bugs مُكتشَفة ومُصلَحة في 1a-1h

1. **1f:** `std::atoi` يحتاج `<cstdlib>` (لم أُضمّنه في البداية). صحَّحته فوراً، البناء التالي مرّ.
2. **1g:** Kotlin `return` غير مسموح داخل expression body في `verifyAffinity()`. غيّرت إلى block body.

كلاهما compile-time errors، اكتُشفا فوراً، لا تأثير على الـ kernels نفسها.

### تنبيهات للجهاز (لم تُختبَر بعد لأنّ `adb devices` فارغ)

عند توصيل S10 5G:

1. **Random+SHA حسّاس للـ pattern:** لو فشل، اطبع أوّل diff bytes لتشخيص — هل bit flip في single cell أم region كامل؟
2. **NEON FMA trace قد يتجاوز tolerance:** لو حدث، Tier 2 يطبع أوّل 8 صفوف تختلف. ابحث عن نمط (نفس cluster؟ موقع متقارب في الـ matrix؟).
3. **Affinity على Samsung Knox:** متوقَّع EPERM. ليس defect — degraded mode موثَّق.
4. **Thermal zones names على S10 5G:** Samsung تستخدم `skin_thermal`، `battery`، إلخ. السلسلة `classify_type()` في [thermal_reader.cpp:60-78](app/src/main/cpp/stress/thermal_reader.cpp:60) تتعامل معها.

### ما هو خارج Phase 1 (لاحقاً)

- **ARMv8 SHA crypto extensions** (`vsha256hq_u32` family) — الـ scalar SHA يعمل، لكن في الـ stress production نريد الـ extensions لتقليل CPU cost. مرحلة 1c-prime لاحقاً.
- **NDK تطبيع المصفوفات يُمكن تحسينه** بـ block-based access لـ B (cache-friendlier). لكن الكود الحالي يضرب L1/L2 cache كما هو مطلوب — هذا feature، ليس bug.
- **Memory test على full RAM (40%)** — السمكة الحالية 32 MiB ثابتة. الـ production stress phase سيُمرّر حجماً أكبر.

---

## 10. سجلّ Phase 2 — Kotlin DTOs

| الملف | الحالة | الغرض |
|---|---|---|
| `data/local/files/V2Models.kt` | جديد (~120 سطر) | `VerificationSummary`, `ThrottlingEvent`, `V2Phase` enum، `DeviceTier` enum، `V2_VARIABLE_DEFINITIONS` (22 entries) |
| `data/local/files/TimeSeriesFileWriter.kt` | مُعدَّل (+13) | `TimeSeriesFooter` يحوي 6 حقول V2 (intensityFactor, deviceTierMeasured, throttlingEvents, verificationResults, …) — كلّها مع defaults للحفاظ على Gson backward-compat |

### قرارات تنفيذ Phase 2 DTOs

| البند | القرار |
|---|---|
| `phase` field — V1 أم V2 | `TimeSeriesSample.phase` يبقى `Int` واحد. V1 يُستخدم 1..5، V2 يُستخدم 0..7. القيمة المرجعية تُحدّد عبر `TimeSeriesHeader.appVersion`. |
| 22 vs 18 variables | `V2_VARIABLE_DEFINITIONS` تعريف منفصل. `TimeSeriesVariables.definitions` (V1) يبقى. الـ writer يختار حسب context. |
| Footer compatibility | كل V2 fields default. ملف `.ts.json.gz` مكتوب من V1 يُفكَّك بدون أخطاء. |

---

## 11. سجلّ Phase 5 — BehaviorFingerprintBuilder.buildV2

| الملف | الحالة | الغرض |
|---|---|---|
| `data/remote/model/AiAnalysisRequest.kt` | مُعدَّل (+62) | `TimeSeriesStats` بـ ~30 حقلاً جديداً (skinTempPeak, gpuFpsStability, burstVoltageDropAvg, criticalDefectDetected …)، `PhaseAnalysis` data class، `BehaviorFingerprint.phaseAnalysis: List<PhaseAnalysis>?` |
| `data/remote/BehaviorFingerprintBuilder.kt` | مُعدَّل (+345) | `buildV2()` parallel method، `calculateStatsV2()` يقرأ index 5/11/18/19/20/21، `buildPhaseAnalysis()` يُنتج 8 عناصر، helpers (`safeRead`, `computeBurstStats`، إلخ) |

### قرارات تنفيذ Phase 5

| البند | القرار |
|---|---|
| V1 `build()` لم يُمَسّ | `buildV2()` parallel، الموجود يبقى يعمل. |
| V2 stats fields nullable | كل الحقول الجديدة `Float?` / `Int?` / `Boolean?`. التخزين السابق لا ينكسر. |
| `PhaseAnalysis.avgCpuTemp` يستخدم index **5** | فعلياً (وليس index 1 = battery_temp كما كان يُحتمل في V1). الـ builder يقرأ `s.v[IDX_CPU_TEMP]` صراحةً. |
| `safeRead(s, i)` للوصول لـ V2 indices | يُرجع `null` لو `i >= s.v.size` (سامبل V1) أو القيمة `< -0.5f` (sentinel `-1`). يحمي من crashes عند خلط V1 و V2. |
| `tempThermalTimeConstant` و `storageThroughputCurveSlope` | `null` على الجهاز — الـ Cloud Function يحسبها من الـ samples حسب §5.3. |

---

## 12. سجلّ Phase 3 — GPU Rewrite (GpuLoadGeneratorV2)

| الملف | الحالة | الغرض |
|---|---|---|
| `service/stress/v2/GpuLoadGeneratorV2.kt` | جديد (~520 سطر) | OpenGL ES 3.1: FBO 2048×2048، fragment shader (8× textures + 30× ALU)، compute shader GEMM 1024×1024، bandwidth ping-pong (4096² RGBA32F) |

### قرارات Phase 3

| البند | القرار |
|---|---|
| FBO size | 2048×2048 ثابت — verification مرتبط بحجم محدّد. شاشة الجهاز لا تؤثّر. |
| `glFinish` لكل frame → `glFenceSync` كل 30 frame | خفض serialization، GPU يستفيد من الـ pipelining. أصدق FPS reading. |
| Compute shader GEMM verification | trace(C_gpu) vs trace(C_cpu_reference) مع tolerance 1e-4 (Q2.4 — أوسع من CPU 1e-7 لأن `mad/fma fusion` يختلف بين GPU drivers). |
| GpuLoadGenerator V1 يبقى | لم أحذفه. الـ V1 يُستخدم في الـ scan الإنتاجي حالياً. التبديل يُجرى عبر feature flag حين يُتحقَّق V2 على جهاز. |

---

## 13. سجلّ Phase 2 — StressTestServiceV2 (orchestrator)

| الملف | الحالة | الغرض |
|---|---|---|
| `service/stress/v2/AdaptiveIntensityController.kt` | جديد (~110 سطر) | probe لـ 8 ثوانٍ، يقيس `°C/sec`، يُصنّف `flagship_cooling`/`mid_cooling`/`budget_cooling`/`unknown` بـ intensity 1.0/0.8/0.65/0.8 |
| `service/stress/v2/DynamicThermalThrottler.kt` | جديد (~140 سطر) | scheduled executor كل 2s يفحص skin_temp. Levels 1/2/3 عند 45/48/50°C. Level 3 يُنهي الـ phase الحالي. يكتب `ThrottlingEvent` لكل transition. |
| `service/stress/v2/StressTestServiceV2.kt` | جديد (~270 سطر) | `runV2Scan()` ينظّم 8 phases بالـ durations الصحيحة (480 s إجمالي)، يُشغّل CPU kernels في loop، GPU عبر `GpuLoadGeneratorV2`، memory test مرة واحدة، burst pattern (5×6s+3s)، cooldown idle. يُرجع `V2ScanResult` يحوي `VerificationSummary`. |

### قرارات Phase 2 service

| البند | القرار |
|---|---|
| V1 `StressTestService` يبقى | V2 service skeleton فقط. الـ Hilt module + foreground notification + sampler integration ليست part من هذه الجولة. الـ wiring الكامل = sub-phase 2-prime لاحقاً. |
| Per-phase sampling rate | الـ skeleton لا يُولّد samples ([V2Phase.samplingHz] محفوظ كـ metadata فقط للـ wiring القادم). الإنتاج يحتاج تكامل مع `DisciplinedSampler`. |
| Phase 1 Storage stress | placeholder delay فقط — الـ 4 GB sustained write + verification يُضاف بعد التحقق من الـ kernels. |
| Phase 6 Burst pattern | 5 cycles بالضبط، 6s peak (NEON FMA loop) + 3s idle (delay). VRM voltage drop يُقاس بواسطة الـ sampler عند تكامله. |
| Phase 4/5 GPU+CPU concurrency | `gpu.start()` + `runCpuKernelsForDuration()` في نفس الـ phase. كلاهما thread منفصل. |

---

## 14. الحالة النهائية الشاملة

### نتائج البناء

✅ `./gradlew assembleLaunchDebug` نجح في ~1m11s (cached) أو ~2-3m (clean).

### إحصائيات الكود

| النوع | عدد الملفات | تقدير الأسطر |
|---|---|---|
| ملفات C++ (.cpp + .h) | 13 | ~1,800 |
| ملفات Kotlin جديدة | 6 (StressNative + NativeSmokeTestRunner + V2Models + AdaptiveIntensityController + DynamicThermalThrottler + StressTestServiceV2 + GpuLoadGeneratorV2) | ~1,400 |
| ملفات Kotlin مُعدَّلة | 5 (NeuralCheckApp + TimeSeriesFileWriter + BehaviorFingerprintBuilder + AiAnalysisRequest + build.gradle.kts + AndroidManifest.xml) | ~430 إضافة |
| **إجمالي V2 جديد** | **~24 ملف** | **~3,600 سطر** |

### libstress.so

- intermediate (debug, with symbols): ~692 KB
- في APK (compressed, stripped): ~325 KB

### حالة المسارات

| المسار | حالة | ملاحظات |
|---|---|---|
| المسار 1 (NDK Foundation) | ✅ مكتمل | 1a-1h جميعها تبني |
| المسار 2 (Kotlin DTOs) | ✅ مكتمل | V2Models + Footer expansion |
| المسار 2 (StressTestServiceV2) | ✅ skeleton | runV2Scan يُنفّذ 8 phases. الـ Hilt wiring + sampler integration لاحقاً |
| المسار 3 (GPU rewrite) | ✅ مكتمل | GpuLoadGeneratorV2 + 3 kernels (fragment/compute/bandwidth) |
| Phase 5 (BehaviorFingerprintBuilder.buildV2) | ✅ مكتمل | parallel method + 30 stats fields + PhaseAnalysis |
| المسار 4 (Cloud Function) | ⊘ خارج نطاق mobile | Backend |
| المسار 5 (SYSTEM_PROMPT) | ⊘ خارج نطاق mobile | Backend |
| المسار 6 (testing) | ⏳ ينتظر أجهزة | لا adb device متّصل حالياً |

### ما يحتاج تكاملاً لاحقاً (sub-phase 2-prime)

1. **Hilt module** يُسجّل `StressTestServiceV2` كـ `@Singleton` ويحقن `Context`.
2. **Foreground service binding** — V2 يحتاج نفس الـ notification + START_FOREGROUND مثل V1.
3. **Sampler integration** — `DisciplinedSampler` ينتج `TimeSeriesSample.v` بحجم 22 (بدلاً من 18) عند V2 mode. سُتدمج مع `StressNative.readThermalZones()` لإملاء index 18 (skin_temp).
4. **Feature flag** — `BuildConfig.USE_V2_STRESS = false` بادئياً. حين يُتحقّق V2 على جهاز، يصبح `true`.
5. **AiScoringEngine يستدعي `buildV2()` بدلاً من `build()`** عند `USE_V2_STRESS`.

### bugs مُكتشَفة ومُصلَحة في Phase 2/3/5

1. **BehaviorFingerprintBuilder:** `companion object V2Indices` مُكرّر — Kotlin يسمح بـ companion واحد فقط. نقلت الـ V2 indices إلى الـ companion الموجود.

كلاهما compile-time errors. تُصلِحا فوراً.

### ما لم يُختبَر فعلياً

⚠️ **0% من V2 components تعمل على جهاز فعلي.** البناء يمرّ، لكن:
- `GpuLoadGeneratorV2` يحتاج Adreno أو Mali حقيقي للتحقّق من compute shaders
- `AdaptiveIntensityController` تحتاج skin_temp حقيقي يستجيب للضغط
- `DynamicThermalThrottler` يحتاج scenario يبلغ 45°C
- `runV2Scan()` يأخذ 8 دقائق فعلية، لم يُختبَر end-to-end

عند توصيل S10 5G:
```
adb install app/build/outputs/apk/launch/debug/app-launch-debug.apk
adb shell am start -n net.neuralcheck/.MainActivity
adb logcat -c && adb logcat | grep -E 'NeuralCheck.(Native|Smoke|V2)'
```
الـ smoke test (1a-1h) يُشغَّل تلقائياً عبر `NeuralCheckApp.onCreate`. V2 service لا يُشغَّل تلقائياً (لا feature flag بعد).

---

## 15. خلاصة

كل المسارات الـ mobile للـ V2 ضمن النطاق (1، 2، 3، Phase 5 الذي ذكره المستخدم) **مكتملة على مستوى الكود ويبني نظيفاً**. ما يتبقّى:

- **اختبار يدوي على جهاز** — مطلوب قبل وضع V2 في الإنتاج. بدون ذلك، high-confidence claims على correctness غير ممكنة.
- **Hilt + sampler wiring** (~300 سطر إضافي) — يجب أن يلي الاختبار اليدوي، لا قبله. خلاف ذلك سنُدخل V2 في الإنتاج بدون ضمانة أنّه يعمل.
- **Cloud Function + SYSTEM_PROMPT** — backend work، خارج الـ mobile scope.

لا أُلمح إلى أنّ الـ V2 جاهز للإنتاج. هو **جاهز للاختبار على الجهاز**، وهذه خطوة ضرورية بين هنا وبين الإطلاق.
