package net.neuralcheck.presentation.viewmodel

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStreamReader

/**
 * Order 2026-09-25 (القرار ٨٦ البند ٤؛ القاعدة 41 — القرار ٨٧). Both
 * [buildHomeLastScan] (`HomeViewModel.fetchLastScan`) and [buildCloudScan]
 * (`ScanHistoryViewModel.load`) read a raw `reports/{scanId}` document —
 * the rule-41 golden sample for that exact read point is
 * `v7-reports-document-sample.json` / `-honor.json` (sample B / Honor B,
 * generated from the real `analyzeDevice` -> Firestore write pipeline,
 * already used by `FirebaseAnalysisServiceCacheGateTest` /
 * `BuildScoringResultFromResponseTest`) and `v6-reports-document-sample.json`
 * for the V6 branch. No hand-typed document fixture is used for these
 * three cases.
 *
 * Before order 2026-09-25 (القرار ٨٦ البند ٤): EVERY V7 report — even
 * sample B, which has a real letter — showed the "?" grade sentinel and a
 * fabricated `0` score on Home/History, because both readers only knew
 * the V6 flat shape.
 *
 * Order 2026-09-26 (القرار ١٠٢، الملحق الثالث، البند ٢) — "الدرجة الكلّية
 * لا تكون حقلاً في أي نموذج عرض تقرؤه الشاشات ... ملخّص الرئيسية، وسجلّ
 * السحابة ... وتبقى في طبقة البيانات وفي Room للتدقيق": [HomeViewModel.LastScan]
 * and [ScanHistoryViewModel.CloudScan] no longer DECLARE a `score`
 * property at all (not just "populated but unread") — [buildHomeLastScan]/
 * [buildCloudScan] no longer read `data["score"]` into either struct.
 * The raw number is untouched where it actually lives for audit purposes
 * (the raw `reports/{scanId}` document's own `score` field, and
 * `ScanScoreEntity.overallScore` in Room) — this order only removed it
 * from these two DISPLAY structs specifically.
 */
class HomeAndHistoryV7SummaryTest {

    private fun loadRawDoc(fileName: String): Map<String, Any?> {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$fileName")
            ?: error("Fixture not found on classpath — expected at app/src/test/resources/contract/$fileName")
        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return Gson().fromJson(text, type)
    }

    private fun fakeContext(): Context {
        val context = mock<Context>()
        whenever(context.getString(R.string.report_grade_unavailable)).thenReturn("GRADE_UNAVAILABLE_TEXT")
        // Order 2026-10-01 (A8/S-13، أمر القرارات): report_v7_harness_error_no_reason
        // removed — production now calls report_v7_harness_error_message_unavailable.
        whenever(context.getString(R.string.report_v7_harness_error_message_unavailable)).thenReturn("HARNESS_MESSAGE_UNAVAILABLE_TEXT")
        whenever(context.getString(R.string.report_v7_no_data_at_all)).thenReturn("NO_DATA_TEXT")
        // Order 2026-10-01 (#10، أمر القرارات): production now calls the
        // dedicated report_v7_overall_status_unavailable here, not the
        // generic report_v7_field_unavailable.
        whenever(context.getString(R.string.report_v7_overall_status_unavailable)).thenReturn("FIELD_UNAVAILABLE_TEXT")
        return context
    }

    /** Order 2026-09-26 (القرار ١٠٢، الملحق الثالث، البند ٢، القاعدة 37):
     *  a runtime, reflection-based proof that a display struct does not
     *  DECLARE a field by this name at all — stronger than, and not
     *  satisfied by, "the screen's source code doesn't read it" (a source
     *  grep can go stale the moment someone re-adds the field; a field
     *  that doesn't exist on the class cannot be re-read by accident). */
    private fun hasField(instance: Any, fieldName: String): Boolean =
        instance::class.java.declaredFields.any { it.name == fieldName }

    /** The real Honor B document with ONE key removed in memory — no new
     *  file under contract/. It is the shape of Honor-style reports written
     *  before the backend added `harnessErrorMessage` (what the previous
     *  version of this very fixture looked like), which still sit in
     *  users' Firestore history. `harnessErrorReason` stays present. */
    private fun honorDocWithoutHarnessErrorMessage(): Map<String, Any?> {
        val doc = loadRawDoc("v7-reports-document-sample-honor.json")
        @Suppress("UNCHECKED_CAST")
        val overall = (doc["aiResponse"] as MutableMap<String, Any?>)["overall"] as MutableMap<String, Any?>
        assertEquals("sanity: the key was there to delete", true, overall.containsKey("harnessErrorMessage"))
        overall.remove("harnessErrorMessage")
        assertEquals("FUNCTIONAL_RESULTS_UNAVAILABLE", overall["harnessErrorReason"])
        return doc
    }

    // ---- HomeViewModel.LastScan (buildHomeLastScan) ----

    @Test
    fun `Home last-scan card shows sample B's real letter, no fabricated score`() {
        val doc = loadRawDoc("v7-reports-document-sample.json")
        val scan = buildHomeLastScan("scan-b", doc, 1717000000000L, fakeContext())
        assertEquals("C", scan.grade)
        assertEquals("", scan.summary)
    }

    @Test
    fun `Home last-scan card shows Honor B's real harnessErrorMessage, never the raw harnessErrorReason`() {
        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29.
        // REPLACES the test of the same slot (itself a 2026-10-01 A8/S-13
        // replacement) which asserted the FALLBACK text
        // "HARNESS_MESSAGE_UNAVAILABLE_TEXT" on the real Honor fixture,
        // because that fixture "has no harnessErrorMessage (predates that
        // backend field)". d5b6c29's Honor B carries the message, so the
        // real fixture now proves the present-message line. The fallback
        // keeps real-material coverage in the next test.
        val doc = loadRawDoc("v7-reports-document-sample-honor.json")
        val scan = buildHomeLastScan("scan-honor", doc, 1717000000000L, fakeContext())
        assertEquals(
            "the grade badge must declare the letter's absence, never \"?\"",
            "GRADE_UNAVAILABLE_TEXT", scan.grade
        )
        assertEquals(
            "the server's own sentence, not the raw harnessErrorReason",
            "تعذّر تجهيز نتائج الاختبارات لهذا الفحص، وهذا ليس حكماً على الجهاز.", scan.summary
        )
    }

    @Test
    fun `Home last-scan card with Honor B's harnessErrorMessage deleted in memory (pre-d5b6c29 shape) shows the unavailable text, never the raw reason`() {
        val doc = honorDocWithoutHarnessErrorMessage()
        val scan = buildHomeLastScan("scan-honor-old", doc, 1717000000000L, fakeContext())
        assertEquals("GRADE_UNAVAILABLE_TEXT", scan.grade)
        assertEquals(
            "the raw harnessErrorReason must never reach this line",
            "HARNESS_MESSAGE_UNAVAILABLE_TEXT", scan.summary
        )
    }

    @Test
    fun `resync 2026-09-26 (القرار ١٠٢، الملحق الثالث، البند ٢) — Home last-scan display model has no score field at all, for a real 78-scoring V6 document`() {
        // Rule 23 — REPLACES the old test of the same slot
        // ("...score field still populated, per rule 20"), which asserted
        // `scan.score == 78`. That premise no longer compiles: `LastScan`
        // does not declare a `score` property any more (not merely
        // "populated but unread by the screen"). This test proves the
        // NEW, stronger claim directly on the struct via reflection (rule
        // 37 — source-text guards don't prove what a MODEL carries), using
        // the same real 78-scoring V6 document as before.
        val doc = loadRawDoc("v6-reports-document-sample.json")
        assertEquals("real fixture sanity check — this document's raw score is 78", 78, (doc["score"] as Number).toInt())
        val scan = buildHomeLastScan("scan-v6", doc, 1717000000000L, fakeContext())
        assertEquals("the letter still flows through, unaffected", "B", scan.grade)
        assertEquals(true, scan.isFlatShapeRecognized)
        assertFalse(
            "HomeViewModel.LastScan must not declare a score field at all",
            hasField(scan, "score")
        )
    }

    // ---- ScanHistoryViewModel.CloudScan (buildCloudScan) ----

    @Test
    fun `Cloud history row shows sample B's real letter, no fabricated score`() {
        val doc = loadRawDoc("v7-reports-document-sample.json")
        val scan = buildCloudScan("scan-b", doc, 1717000000000L, fakeContext())
        assertEquals("C", scan.grade)
        assertEquals("", scan.verdict)
    }

    @Test
    fun `Cloud history row shows Honor B's real harnessErrorMessage, never the raw harnessErrorReason`() {
        // Rule 23 — resynced 2026-10-07 (backend@d5b6c29): REPLACES the
        // fallback-text assertion on the real Honor fixture, same
        // rationale as the Home last-scan sibling above.
        val doc = loadRawDoc("v7-reports-document-sample-honor.json")
        val scan = buildCloudScan("scan-honor", doc, 1717000000000L, fakeContext())
        assertEquals("GRADE_UNAVAILABLE_TEXT", scan.grade)
        assertEquals("تعذّر تجهيز نتائج الاختبارات لهذا الفحص، وهذا ليس حكماً على الجهاز.", scan.verdict)
    }

    @Test
    fun `Cloud history row with Honor B's harnessErrorMessage deleted in memory (pre-d5b6c29 shape) shows the unavailable text, never the raw reason`() {
        val doc = honorDocWithoutHarnessErrorMessage()
        val scan = buildCloudScan("scan-honor-old", doc, 1717000000000L, fakeContext())
        assertEquals("GRADE_UNAVAILABLE_TEXT", scan.grade)
        assertEquals("HARNESS_MESSAGE_UNAVAILABLE_TEXT", scan.verdict)
    }

    @Test
    fun `resync 2026-09-26 (القرار ١٠٢، الملحق الثالث، البند ٢) — Cloud history display model has no score field at all, for a real 78-scoring V6 document`() {
        // Rule 23 — REPLACES the old test of the same slot, same rationale
        // as the Home last-scan sibling test above.
        val doc = loadRawDoc("v6-reports-document-sample.json")
        assertEquals("real fixture sanity check — this document's raw score is 78", 78, (doc["score"] as Number).toInt())
        val scan = buildCloudScan("scan-v6", doc, 1717000000000L, fakeContext())
        assertEquals("the letter still flows through, unaffected", "B", scan.grade)
        assertEquals(true, scan.isFlatShapeRecognized)
        assertFalse(
            "ScanHistoryViewModel.CloudScan must not declare a score field at all",
            hasField(scan, "score")
        )
    }

    // ── Order 2026-09-26 (القرار ١٠٢ البند ٤، القاعدة 30 + 41) ──
    // Every pipeline value AiPipeline.classifyShape recognises, each
    // derived from a REAL golden sample by mutating (or removing) ONLY the
    // `pipeline` field (or, for the "neither signal" cached case, also
    // removing `grade` — still starting from real material, not a
    // hand-built document) — tested through the actual UI-producing
    // function, not classifyShape in isolation (already covered
    // separately, AiPipelineTest.kt). `buildHomeLastScan` carries the full
    // 8-case matrix; `buildCloudScan` (proven identical in AiPipelineTest's
    // underlying classifyShape) is cross-checked only on the two cases
    // most likely to diverge by a copy-paste slip (cached-with-neither,
    // wholly unknown tag) — not the full matrix twice.

    private fun v6Sample() = loadRawDoc("v6-reports-document-sample.json")
    private fun v7Sample() = loadRawDoc("v7-reports-document-sample.json")

    @Test
    fun `pipeline absent (real V6 sample, key removed) - Home shows the real V6 letter`() {
        val doc = v6Sample() - "pipeline"
        val scan = buildHomeLastScan("scan", doc, 0L, fakeContext())
        assertEquals("B", scan.grade)
        assertEquals(true, scan.isFlatShapeRecognized)
    }

    @Test
    fun `pipeline v6_raw_timeseries (real V6 sample, unchanged) - Home shows the real V6 letter`() {
        val scan = buildHomeLastScan("scan", v6Sample(), 0L, fakeContext())
        assertEquals("B", scan.grade)
        assertEquals(true, scan.isFlatShapeRecognized)
    }

    @Test
    fun `pipeline v3_legacy (real V6 sample, tag swapped) - Home shows the real V6 letter`() {
        val doc = v6Sample() + ("pipeline" to net.neuralcheck.data.remote.model.AiPipeline.V3_LEGACY)
        val scan = buildHomeLastScan("scan", doc, 0L, fakeContext())
        assertEquals("B", scan.grade)
        assertEquals(true, scan.isFlatShapeRecognized)
    }

    @Test
    fun `pipeline v5_precomputed (real V6 sample, tag swapped) - Home shows the real V6 letter`() {
        val doc = v6Sample() + ("pipeline" to net.neuralcheck.data.remote.model.AiPipeline.V5_PRECOMPUTED)
        val scan = buildHomeLastScan("scan", doc, 0L, fakeContext())
        assertEquals("B", scan.grade)
        assertEquals(true, scan.isFlatShapeRecognized)
    }

    @Test
    fun `pipeline v7_findings_based (real V7 sample, unchanged) - Home shows the real V7 letter`() {
        val scan = buildHomeLastScan("scan", v7Sample(), 0L, fakeContext())
        assertEquals("C", scan.grade)
        assertEquals(false, scan.isFlatShapeRecognized)
    }

    @Test
    fun `pipeline cached with real V6 content (real V6 sample, tag swapped) - Home shows the real V6 letter`() {
        val doc = v6Sample() + ("pipeline" to net.neuralcheck.data.remote.model.AiPipeline.CACHED)
        val scan = buildHomeLastScan("scan", doc, 0L, fakeContext())
        assertEquals("B", scan.grade)
        assertEquals(true, scan.isFlatShapeRecognized)
    }

    @Test
    fun `pipeline cached with real V7 content (real V7 sample, tag swapped) - Home shows the real V7 letter`() {
        val doc = v7Sample() + ("pipeline" to net.neuralcheck.data.remote.model.AiPipeline.CACHED)
        val scan = buildHomeLastScan("scan", doc, 0L, fakeContext())
        assertEquals("C", scan.grade)
        assertEquals(false, scan.isFlatShapeRecognized)
    }

    @Test
    fun `pipeline cached with neither a V6 nor a V7 signal (real V6 sample, grade removed) - Home declares absence, not a fabricated letter`() {
        val doc = v6Sample() - "grade" + ("pipeline" to net.neuralcheck.data.remote.model.AiPipeline.CACHED)
        val scan = buildHomeLastScan("scan", doc, 0L, fakeContext())
        assertEquals("GRADE_UNAVAILABLE_TEXT", scan.grade)
        assertEquals(false, scan.isFlatShapeRecognized)
    }

    @Test
    fun `wholly unrecognised pipeline tag (real V6 sample, tag swapped, content otherwise intact) - Home still declares absence`() {
        // Proves classifyShape's OWN distinction that CACHED is the only
        // tag routed by content — any OTHER unrecognised tag is UNKNOWN
        // regardless of a real-looking grade still being present.
        val doc = v6Sample() + ("pipeline" to "v8_something_new")
        val scan = buildHomeLastScan("scan", doc, 0L, fakeContext())
        assertEquals("GRADE_UNAVAILABLE_TEXT", scan.grade)
        assertEquals(false, scan.isFlatShapeRecognized)
    }

    // ---- buildCloudScan cross-check (two cases, not the full matrix) ----

    @Test
    fun `Cloud history row - cached with neither signal declares absence too`() {
        val doc = v6Sample() - "grade" + ("pipeline" to net.neuralcheck.data.remote.model.AiPipeline.CACHED)
        val scan = buildCloudScan("scan", doc, 0L, fakeContext())
        assertEquals("GRADE_UNAVAILABLE_TEXT", scan.grade)
    }

    @Test
    fun `Cloud history row - a wholly unrecognised tag declares absence too`() {
        val doc = v6Sample() + ("pipeline" to "v8_something_new")
        val scan = buildCloudScan("scan", doc, 0L, fakeContext())
        assertEquals("GRADE_UNAVAILABLE_TEXT", scan.grade)
    }
}
