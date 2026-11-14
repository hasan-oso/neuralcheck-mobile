package net.neuralcheck.domain.model

import net.neuralcheck.util.CategoryTranslations

/**
 * Test category enumeration matching ADR test IDs.
 */
/**
 * Order 2026-10-01 (أمر القرارات؛ متابعة المنسّق نفس اليوم): nameAr/nameTr
 * used to be a SECOND hand-typed copy of the same 15-category list
 * [net.neuralcheck.util.CategoryTranslations] holds (found while searching
 * for "كل موضع آخر... يكتب اسم فئة بالعربية" — rule 24).
 *
 * **تصحيح لتقرير الجولة السابقة:** ذلك التقرير ادّعى أن هذه النسخة "حيّة" عبر
 * `ReportScreen.kt`'s الـ"Category scores" وعبر `ReportPdfGenerator
 * .localisedCategoryName`'s fallback — **خطأ**: تحقّقتُ بـ`grep` شامل في
 * `app/src/main` و`app/src/test` ولا قارئ واحد لـ`TestCategory.nameAr`/
 * `.nameTr` في أيّ مكان. ما كنت أراه في `ReportScreen.kt:671` و
 * `ReportPdfGenerator.kt:880` هو `cat.nameAr`/`cat.nameTr` حيث `cat:
 * [net.neuralcheck.domain.scoring.CategoryScore]` — نوع مختلف تماماً، تُملأ
 * حقوله من دالّتين خاصّتين (`getCategoryNameAr`/`getCategoryNameTr`) في
 * `RuleBasedScoringEngine.kt:285-327` — **نسخة ثالثة مستقلّة تماماً**، لم
 * تُكتشف إلا هذه الجولة، ولم تُلمَس هنا (خارج النطاق الصريح المطلوب — ذُكرت
 * في تقرير هذه الجولة للمستخدم). فالحقلان هنا كانا بلا أثر مرئي قطّ،
 * وتعديلهما يدوياً الجولة الماضية (gps/camera/input) لم يُغيِّر أي شيء على
 * الشاشة — حقيقة مهمّة لفهم لماذا توحيدهما هنا بلا مخاطرة إطلاقاً.
 *
 * **التوحيد:** `nameAr`/`nameTr` صارا خاصّيتين محسوبتين (`get() = ...`) لا
 * معاملَي مُنشئ — تُحسبان من [CategoryTranslations] بالمعرِّف [id] في كل
 * استدعاء، فلا نصّ مكرَّر هنا إطلاقاً. **اتجاه القراءة المختار:**
 * [TestCategory] يقرأ من [CategoryTranslations]، لا العكس — لأن
 * [CategoryTranslations] كائن (`object`) مستقلّ لا يعتمد على [TestCategory]
 * بتاتاً (لا حلقة اعتماد أو مشكلة ترتيب تهيئة enum)، وهو السجلّ الحقيقي الذي
 * تقرأ منه شاشة التقرير فعلياً (`AiCategoryRowV7`/`AiCategoryEvaluationSection`
 * وReportPdfGenerator.localisedCategoryName)؛ والعكس (جعل
 * [CategoryTranslations] يُبنى من [TestCategory]) كان سيفرض على
 * [CategoryTranslations] الاعتماد على enum مجاله مختلف (نطاق الاختبارات لا
 * نطاق العرض) ولا يحمل حقل الأيقونة (`icon`) الذي يحتاجه [CategoryTranslations]
 * أصلاً. بما أن nameAr/nameTr أصبحتا خاصّيتين محسوبتين (lazy getters) لا
 * قيمتين تُحسبان وقت إنشاء ثوابت الـenum، فلا مشكلة ترتيب تهيئة JVM/Kotlin
 * الشهيرة لـenum (تهيئة الثوابت قبل companion object) — الاستدعاء يحدث عند
 * كل قراءة فعلية، لا عند تحميل الصنف.
 */
enum class TestCategory(val id: String) {
    BATTERY("battery"),
    CPU("cpu"),
    GPU("gpu"),
    STORAGE("storage"),
    THERMAL("thermal"),
    MEMORY("memory"),
    CELLULAR("cellular"),
    WIRELESS("wireless"),
    GPS("gps"),
    SENSORS("sensors"),
    CAMERA("camera"),
    DISPLAY("display"),
    SECURITY("security"),
    // Bug 5 follow-up (2026-05-17): added so manual tests for speakers /
    // microphones / headphone jack / vibration etc. have an honest home
    // instead of being lumped under CAMERA. See
    // docs/audits/bug_5_manual_tests/catalog-rebucketing.md for the full
    // rebucketing decision log.
    AUDIO("audio"),
    INPUT("input");

    /** Delegates to [CategoryTranslations] — see class doc for why this
     *  direction. No production code currently reads this property (grep-
     *  confirmed, same "documentation-only reference" status [TestSpec]'s
     *  own nameAr/nameTr already carry per its A69 doc comment) — kept for
     *  whatever future caller wants a category's Arabic name off this enum
     *  directly, now guaranteed never to drift from the single source. */
    val nameAr: String get() = CategoryTranslations.nameAr(id)

    /** See [nameAr]. */
    val nameTr: String get() = CategoryTranslations.nameTr(id)

    companion object {
        fun fromId(id: String): TestCategory? = entries.find { it.id == id }
    }
}
