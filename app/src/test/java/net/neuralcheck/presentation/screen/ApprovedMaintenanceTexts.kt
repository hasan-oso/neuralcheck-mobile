package net.neuralcheck.presentation.screen

/**
 * THE APPROVED TEXTS of the maintenance before/after cycle, rows ص1 to ص21 - a COPY (rule 29), generated from the approval and not typed:
 * `TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md` in the neuralcheck-workspace (`evidence/run-2026-10-18/`), sha256 `96288152d4d6645e6ca537c72cb3ac996455e0dfd46ba8281be055b368ef6697` when it was copied. Approved by Hasan on 2026-10-22: «النصوص ص1-ص21 معتمدة كما كتبتَها، بالعربية والتركية».
 *
 * [Row.ar] is column A of the table: the text between the OUTER guillemets, without the wire value that follows it (`BATTERY` and the like are not text), the inner «» and the long dash as they are.
 * [Row.tr] is the Turkish column. Two things are not literal, and are declared (rule 13): the Turkish of ص10 is «aynı biçim» in the table (= the same form), so it is DERIVED - `derivedTr` - as
 * «%1$s · %2$s · saat %3$s»; and ص12 and ص13 are EXISTING resources (`existing`: `qr_linked_success_fmt`, `btn_confirm`): their Arabic is the approval's, their Turkish is the repository's own, as it stood.
 * The Turkish is approved BEFORE a speaker review (TR-TEXTS-NEED-SPEAKER-REVIEW-2026-10-22.md).
 *
 * This table is NOT read from the resources: it is what the resources, the labels and the screen are tested against (`MaintenancePairApprovedTextsTest`, `MaintenanceCycleScreenTextsTest`).
 */
internal object ApprovedMaintenanceTexts {

    class Row(val code: String, val resource: String, val ar: String, val tr: String, val existing: Boolean, val derivedTr: Boolean)

    const val SOURCE: String = "TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md"
    const val SOURCE_SHA256: String = "96288152d4d6645e6ca537c72cb3ac996455e0dfd46ba8281be055b368ef6697"

    val rows: List<Row> = listOf(
        Row("ص1", "purpose_chips_title", "غرض الفحص", "Tarama amacı", existing = false, derivedTr = false),
        Row("ص2", "purpose_chip_normal", "عادي", "Normal", existing = false, derivedTr = false),
        Row("ص3", "purpose_chip_maintenance_before", "قبل الصيانة", "Bakım öncesi", existing = false, derivedTr = false),
        Row("ص4", "purpose_chip_maintenance_after", "بعد الصيانة", "Bakım sonrası", existing = false, derivedTr = false),
        Row("ص5", "purpose_chips_locked_note", "لا يتغيّر الغرض بعد ربط الهاتف", "Telefon bağlandıktan sonra amaç değiştirilemez", existing = false, derivedTr = false),
        Row("ص6-1", "defect_type_battery", "بطارية — تبديل بطارية", "Batarya — batarya değişimi", existing = false, derivedTr = false),
        Row("ص6-2", "defect_type_charging_port", "منفذ الشحن — المنفذ أو فليكس الشحن", "Şarj girişi — şarj portu veya şarj flex kablosu", existing = false, derivedTr = false),
        Row("ص6-3", "defect_type_thermal", "حرارة وتبريد — تنظيف أو معجون حراري", "Isınma ve soğutma — temizlik veya termal macun", existing = false, derivedTr = false),
        Row("ص6-4", "defect_type_motherboard", "لوحة أم — إعادة تشغيل عشوائي أو سخونة أو شبكة", "Anakart — rastgele yeniden başlatma, ısınma veya ağ", existing = false, derivedTr = false),
        Row("ص6-5", "defect_type_software", "برمجي — فلاش أو إعادة ضبط أو تحديث نظام", "Yazılım — flaş, fabrika ayarlarına sıfırlama veya sistem güncellemesi", existing = false, derivedTr = false),
        Row("ص7", "defect_type_title", "نوع العطل", "Arıza türü", existing = false, derivedTr = false),
        Row("ص8", "defect_type_hint", "اختر العطل الذي ستصلحه؛ إن لم تجده فافحص «عادي»", "Onaracağınız arızayı seçin; bulamazsanız «Normal» tarama yapın", existing = false, derivedTr = false),
        Row("ص9", "pair_pick_prompt", "اختر فحص «قبل الصيانة» لهذا الهاتف", "Bu telefon için «Bakım öncesi» taramasını seçin", existing = false, derivedTr = false),
        Row("ص10", "pair_row_fmt", "%1\$s · %2\$s · الساعة %3\$s", "%1\$s · %2\$s · saat %3\$s", existing = false, derivedTr = true),
        Row("ص11", "pair_empty_fmt", "لا فحوص «قبل الصيانة» مفتوحة من هذا النوع في آخر %1\$d ساعة", "Son %1\$d saatte bu türde açık «Bakım öncesi» taraması yok", existing = false, derivedTr = false),
        Row("ص12", "qr_linked_success_fmt", "تم الربط — فحص بتاريخ %1\$s", "Bağlandı — %1\$s tarihli tarama", existing = true, derivedTr = false),
        Row("ص13", "btn_confirm", "تأكيد", "Onayla", existing = true, derivedTr = false),
        Row("ص20", "pair_no_device_name", "جهاز بلا اسم تجاري", "Ticari adı olmayan cihaz", existing = false, derivedTr = false),
        Row("ص14", "pair_failure_not_available", "هذا الفحص غير متاح للربط", "Bu tarama bağlanamaz", existing = false, derivedTr = false),
        Row("ص15", "pair_failure_already_paired", "هذا الفحص مربوط من قبل", "Bu tarama daha önce bağlanmış", existing = false, derivedTr = false),
        Row("ص16", "pair_failure_expired", "انتهت مدّة هذا الفحص ولا يمكن ربطه", "Bu taramanın süresi doldu, bağlanamaz", existing = false, derivedTr = false),
        Row("ص17", "pair_failure_wrong_defect_type", "نوع العطل في هذا الفحص يختلف عمّا اخترتَ", "Bu taramanın arıza türü seçtiğinizden farklı", existing = false, derivedTr = false),
        Row("ص18", "pair_failure_general", "تعذّر الربط. حاول مرة أخرى", "Bağlantı kurulamadı. Tekrar deneyin", existing = false, derivedTr = false),
        Row("ص19", "pair_failure_not_enabled", "ميزة الصيانة غير مفعَّلة لحسابك", "Bakım özelliği hesabınız için etkin değil", existing = false, derivedTr = false),
        Row("ص21", "purpose_part_not_enabled", "الغرض غير مفعَّل لحسابك", "Tarama amacı hesabınız için etkin değil", existing = false, derivedTr = false),
        // ص22-ص23: NOT in the approval file - added 2026-10-28 by the manager's judgement (the delegation of 2026-10-23) after Hasan's complaint that the «بعد الصيانة» screen carried «قبل الصيانة»'s texts (the
        // hint «اختر العطل الذي ستصلحه…» is the wording of a repair still to be done). The Turkish is NOT speaker-reviewed (TR-TEXTS-NEED-SPEAKER-REVIEW-2026-10-22.md, section of 2026-10-28) and is not to be distributed.
        Row("ص22", "defect_type_title_after", "نوع العطل الذي أُصلح", "Onarılan arıza türü", existing = false, derivedTr = false),
        Row("ص23", "defect_type_hint_after", "اختر العطل الذي أصلحتَه، ثم اختر فحص «قبل الصيانة» لهذا الهاتف", "Onardığınız arızayı seçin, ardından bu telefonun «Bakım öncesi» taramasını seçin", existing = false, derivedTr = false),
    )

    fun row(code: String): Row = rows.single { it.code == code }

    /** The approved text of [code] in Turkish ([tr] true) or Arabic. */
    fun text(code: String, tr: Boolean): String = row(code).let { if (tr) it.tr else it.ar }
}
