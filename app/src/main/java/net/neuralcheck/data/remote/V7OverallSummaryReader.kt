package net.neuralcheck.data.remote

import android.content.Context
import net.neuralcheck.R
import net.neuralcheck.util.AiV7Presentation

/**
 * Order 2026-09-25 (القرار ٨٦ البند ٤). Reads `aiResponse.overall` off a
 * raw `reports/{scanId}` Firestore document map for the two compact
 * "flat" summary readers that never went through the Gson
 * [net.neuralcheck.data.remote.model.AiAnalysisResponse] pipeline —
 * `HomeViewModel.fetchLastScan` and `ScanHistoryViewModel.load`. Both used
 * to read top-level `grade`/`score` (V6-only fields) unconditionally, so
 * every V7 report — even one with a real letter — showed the "?" grade
 * sentinel and a fabricated `0` score (`AiPipeline.isRecognizedFlatShape`
 * correctly flagged the doc as unrecognised, but the callers then filled
 * the gap with placeholders instead of surfacing it).
 *
 * `overall` lives under `aiResponse.overall` in the stored document, not
 * at the document root — same shape confirmed by this round's
 * `isCachedReportComplete`/`flattenReportDocument` fix
 * (`FirebaseAnalysisService.kt`).
 *
 * `declared` vs `value` distinction reuses [AiV7Presentation.classifyOverallStatus]
 * directly rather than re-deriving the same ranking here — a raw `Map`
 * makes "declared" trivial to check correctly (`containsKey`), unlike the
 * Gson-DTO path where a key's absence and an explicit JSON `null` can
 * both surface as Kotlin `null` (see `isHarnessErrorDeclared`'s doc for
 * why that path needs a JSON-text scan instead).
 */
data class V7OverallSummary(
    /** The real letter, non-blank, when the report has one. */
    val letter: String?,
    /**
     * Resolved display text for why [letter] is null — using the SAME
     * strings [net.neuralcheck.presentation.screen.ReportScreen]'s
     * `AiV7OverallCard` already shows for the identical states, not new
     * copy. Null exactly when [letter] is non-null (nothing to explain).
     */
    val absenceReasonText: String?
)

/**
 * Returns null when [doc] carries no `aiResponse.overall` object at all
 * (e.g. a V6 document, or a malformed/legacy V7 write) — callers must
 * treat that as "this reader has nothing for V7", not as noDataAtAll or
 * any other in-band answer (rule 22).
 */
fun readV7OverallSummary(doc: Map<String, Any?>, context: Context): V7OverallSummary? {
    val overall = (doc["aiResponse"] as? Map<*, *>)?.get("overall") as? Map<*, *> ?: return null

    val letter = (overall["letter"] as? String)?.takeIf { it.isNotBlank() }
    if (letter != null) return V7OverallSummary(letter = letter, absenceReasonText = null)

    val status = AiV7Presentation.classifyOverallStatus(
        harnessError = overall["harnessError"] as? Boolean,
        noDataAtAll = overall["noDataAtAll"] as? Boolean,
        partialAssessment = overall["partialAssessment"] as? Boolean,
        harnessErrorDeclared = overall.containsKey("harnessError"),
        noDataAtAllDeclared = overall.containsKey("noDataAtAll"),
        partialAssessmentDeclared = overall.containsKey("partialAssessment")
    )
    val reasonText = when (status) {
        // Order 2026-10-01 (أمر القرارات، A8/S-13): was
        // overall["harnessErrorReason"] shown VERBATIM here too (same bug
        // as AiV7OverallCard in ReportScreen.kt) — a raw internal code, rule
        // 22's "الرمز الخام لا يُعرض على الشاشة". harnessErrorReason is no
        // longer read in this branch at all.
        AiV7Presentation.OverallStatusKind.HARNESS_ERROR ->
            AiV7Presentation.harnessErrorDisplayText(overall["harnessErrorMessage"] as? String)
                ?: context.getString(R.string.report_v7_harness_error_message_unavailable)
        AiV7Presentation.OverallStatusKind.NO_DATA_AT_ALL ->
            context.getString(R.string.report_v7_no_data_at_all)
        // Contract: `letter` is null ONLY for noDataAtAll or HARNESS_ERROR —
        // a PARTIAL assessment always carries a real letter. Reaching
        // PARTIAL/NOT_DECLARED/NORMAL here means letter is null WITHOUT one
        // of the two documented reasons — an unexpected/malformed document
        // this reader cannot explain. Order 2026-10-01 (أمر القرارات، #10 —
        // "وV7OverallSummaryReader.kt:79 (بطاقة الرئيسية) بالصيغة نفسها"):
        // same dedicated string as AiV7OverallCard's own NOT_DECLARED branch
        // in ReportScreen.kt (one resource, two call sites, rule 24) — was
        // the generic report_v7_field_unavailable, which named no subject.
        AiV7Presentation.OverallStatusKind.PARTIAL,
        AiV7Presentation.OverallStatusKind.NOT_DECLARED,
        AiV7Presentation.OverallStatusKind.NORMAL ->
            context.getString(R.string.report_v7_overall_status_unavailable)
    }
    return V7OverallSummary(letter = null, absenceReasonText = reasonText)
}
