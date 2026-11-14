package net.neuralcheck.util

/**
 * Single source of truth for the 15 diagnostic category labels used in the
 * launch-flavor report screen and the exported PDF.
 *
 * The backend (`analyzeDevice` Cloud Function) returns `category_scores` keyed
 * by lowercase English identifier (e.g. `"battery"`, `"cpu"`, `"gps"`). The
 * same keys are used on-device by [net.neuralcheck.domain.model.TestCategory]
 * (stored in `FunctionalResultEntity.categoryName` as lowercase English) —
 * so one lookup table serves both the fingerprint we upload and the display
 * we render.
 *
 * Kept as a plain Kotlin object (not a Compose `stringResource` lookup) so it
 * can be consumed from three places without passing a `Context` around:
 *   • `ReportScreen` (composable)
 *   • `ReportPdfGenerator` (Canvas / StaticLayout)
 *   • `AiScoringEngine` (no UI surface, but enriches pass/total from the same
 *     category identifiers)
 *
 * Icons are single emoji code points so the default system font can render
 * them inside both Compose `Text` and Android `TextPaint` — we deliberately
 * don't use multi-codepoint ZWJ sequences that sometimes fail to render in
 * the PdfDocument canvas on older devices.
 */
object CategoryTranslations {

    data class CategoryLabel(
        val ar: String,
        val tr: String,
        val icon: String
    )

    /**
     * Order 2026-10-01 (أمر القرارات، قسم أسماء الفئات): the Arabic column
     * for all 15 keys is a COPY of the single list this order gives
     * verbatim — "ويُكتب في كلٍّ منها أنها نسخة من قائمة واحدة مرجعها هذا
     * الأمر" (rule 24: a literal value a display distinction depends on
     * lives in ONE place; this map is that one place — see also the
     * strings.xml `category_*` duplicates flagged, NOT changed, in this
     * round's report).
     *
     * All four Turkish names below (gpu, gps, camera, input) are DECIDED —
     * approved later the same day, replacing this round's earlier
     * proposals: "gpu «Grafik İşlemci»، وgps «Konum (GPS)»، وinput «Dokunma
     * ve Tuşlar»: معتمدة، ومعها camera «Kamera»." (نصّ المستخدم حرفياً).
     * gpu's Turkish is unchanged from before this order — only
     * camera/gps/input actually change. camera's Arabic/Turkish no longer
     * carry CM-08/CM-10's old "biometric" meaning (fingerprint/face health
     * is a separate finding now, not part of this label).
     */
    private val labels: Map<String, CategoryLabel> = mapOf(
        "battery"  to CategoryLabel("البطارية", "Pil", "🔋"),
        "cpu"      to CategoryLabel("المعالج", "İşlemci", "⚡"),
        // gpu: unchanged in both languages by this order.
        "gpu"      to CategoryLabel("معالج الرسومات", "Grafik İşlemci", "🎮"),
        "thermal"  to CategoryLabel("الحرارة", "Isı", "🌡️"),
        "memory"   to CategoryLabel("الذاكرة", "Bellek", "🧠"),
        "storage"  to CategoryLabel("التخزين", "Depolama", "💾"),
        "cellular" to CategoryLabel("الشبكة الخلوية", "Hücresel Ağ", "📡"),
        "wireless" to CategoryLabel("الاتصال اللاسلكي", "Kablosuz Bağlantı", "📶"),
        // gps: "نظام تحديد الموقع" ⇐ "الموقع (GPS)" — Turkish "Konum (GPS)"
        // معتمَد.
        "gps"      to CategoryLabel("الموقع (GPS)", "Konum (GPS)", "📍"),
        "sensors"  to CategoryLabel("المستشعرات", "Sensörler", "🧭"),
        // camera: "الكاميرا والحيوية" ⇐ "الكاميرا" — Turkish "Kamera"
        // معتمَد.
        "camera"   to CategoryLabel("الكاميرا", "Kamera", "📷"),
        "display"  to CategoryLabel("الشاشة", "Ekran", "🖥️"),
        "security" to CategoryLabel("الأمان", "Güvenlik", "🔒"),
        // Bug 5 rebucketing (TestCategory AUDIO/INPUT, 2026-05-17): manual
        // speaker/mic/jack tests live under "audio" and touch/buttons/
        // vibration under "input". Without these two entries labelFor()
        // falls back to the raw English key — users saw "audio" / "input"
        // verbatim in the report screen and the exported PDF. Names match
        // TestCategory.nameAr/nameTr exactly so both surfaces agree.
        "audio"    to CategoryLabel("الصوت", "Ses", "🔊"),
        // input: "الإدخال" ⇐ "اللمس والأزرار" — Turkish "Dokunma ve Tuşlar"
        // معتمَد.
        "input"    to CategoryLabel("اللمس والأزرار", "Dokunma ve Tuşlar", "⌨️")
    )

    /**
     * Looks up the label for [key]. The key is normalized to lowercase so we
     * tolerate the backend returning either "battery" or "Battery". When a
     * key is genuinely unknown (a brand-new category the app has not yet
     * shipped support for) we fall back to the raw key as the visible name
     * so the user still sees *something* they can ask about — an empty label
     * would just look like a rendering bug.
     */
    fun labelFor(key: String): CategoryLabel {
        val normalized = key.trim().lowercase()
        return labels[normalized] ?: CategoryLabel(
            ar = key,
            tr = key,
            icon = "•"
        )
    }

    /** Icon-only lookup — kept for call sites that already have a localized name. */
    fun iconFor(key: String): String = labelFor(key).icon

    /** Returns the Arabic name for [key], falling back to the raw key. */
    fun nameAr(key: String): String = labelFor(key).ar

    /** Returns the Turkish name for [key], falling back to the raw key. */
    fun nameTr(key: String): String = labelFor(key).tr

    /** Returns the name in the app's current locale. */
    fun localizedName(key: String, languageCode: String): String {
        val label = labelFor(key)
        return if (languageCode == "tr" && label.tr.isNotBlank()) label.tr else label.ar
    }
}
