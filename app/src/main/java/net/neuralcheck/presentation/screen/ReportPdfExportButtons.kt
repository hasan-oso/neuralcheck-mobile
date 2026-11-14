package net.neuralcheck.presentation.screen

/**
 * Single source of truth (rule 24 — a literal a distinction is built on
 * lives in one exported place) for whether ReportScreen's two PDF-export
 * buttons — "مشاركة التقرير" (Share, [net.neuralcheck.util.shareReport])
 * and "حفظ" (Save, [net.neuralcheck.util.saveReport]) — render in the
 * report's bottom bar. Order 2026-09-23 (القرار ٧٣، البند ٢): "الإخفاء
 * براية واحدة، في التزام منفصل".
 *
 * Deliberately does NOT cover the QR-code button in the same bottom bar
 * (`showQrDialog = true`) — that button doesn't produce a PDF, and the
 * order names only the two PDF-producing buttons ("زرّا PDF"). QR stays
 * governed only by the pre-existing `!isFreeTrial` condition wrapping the
 * whole bottom bar (`ReportScreen.kt`) — unchanged by this flag.
 *
 * Test ceiling per the order's own text: "أقصاه أن المكوّن يقرأ الراية؛
 * والرسم يُحسم على الجهاز" — a JVM test can confirm this object's current
 * value and that `ReportScreen.kt` references it (see
 * `ReportPdfExportButtonsTest`), not that Compose actually omits the
 * buttons on a rendered screen (when written, the module lacked Robolectric/Compose UI
 * test infrastructure; both are test dependencies since 3284cca, and no test renders
 * this screen - same limit as every other V7-display proof of that session).
 */
object ReportPdfExportButtons {
    /** Current build value: the Share and Save buttons do NOT render. */
    const val HIDDEN = true
}
