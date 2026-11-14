package net.neuralcheck.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStreamReader

/**
 * Order 2026-09-25 (القرار ٨٦ البند ٤). [readV7OverallSummary] is what
 * [net.neuralcheck.presentation.viewmodel.buildHomeLastScan] /
 * `buildCloudScan` use to read `aiResponse.overall` off a raw
 * `reports/{scanId}` document. Real fixtures (sample B / Honor B) prove
 * the two documented contract shapes (a real letter; harnessError with no
 * letter); hand-built maps below cover branches those two real samples
 * don't exercise (no `aiResponse` at all; noDataAtAll) — clearly NOT
 * golden-sample coverage (rule 41), only real-fixture tests are, but
 * still needed for full branch coverage of this function's own logic.
 */
class V7OverallSummaryReaderTest {

    private fun loadRawDoc(fileName: String): Map<String, Any?> {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$fileName")
            ?: error("Fixture not found on classpath — expected at app/src/test/resources/contract/$fileName")
        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return Gson().fromJson(text, type)
    }

    private fun fakeContext(): Context {
        val context = mock<Context>()
        // Order 2026-10-01 (A8/S-13، أمر القرارات): report_v7_harness_error_no_reason
        // removed — production now calls report_v7_harness_error_message_unavailable
        // instead (and never reads the raw harnessErrorReason for display, see below).
        whenever(context.getString(R.string.report_v7_harness_error_message_unavailable)).thenReturn("HARNESS_MESSAGE_UNAVAILABLE_TEXT")
        whenever(context.getString(R.string.report_v7_no_data_at_all)).thenReturn("NO_DATA_TEXT")
        // Order 2026-10-01 (#10، أمر القرارات): production now calls the
        // dedicated report_v7_overall_status_unavailable here, not the
        // generic report_v7_field_unavailable.
        whenever(context.getString(R.string.report_v7_overall_status_unavailable)).thenReturn("FIELD_UNAVAILABLE_TEXT")
        return context
    }

    @Test
    fun `sample B (real letter) returns the letter and no absence reason`() {
        val doc = loadRawDoc("v7-reports-document-sample.json")
        val summary = readV7OverallSummary(doc, fakeContext())
        assertEquals("C", summary?.letter)
        assertNull(summary?.absenceReasonText)
    }

    @Test
    fun `Honor B (harnessError WITH the real harnessErrorMessage) shows that message, never the raw harnessErrorReason`() {
        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29.
        // REPLACES two tests of the previous slot layout:
        //   (1) "Honor B (harnessError, no harnessErrorMessage) never
        //       surfaces the raw harnessErrorReason", which asserted the
        //       FALLBACK text on the real Honor fixture — pinned to the
        //       old sample, whose `overall` had no harnessErrorMessage
        //       (the comment there said "predates the backend field").
        //       d5b6c29's Honor B carries it, so the real fixture now
        //       proves the PRESENT-message path (the path the old comment
        //       said no real fixture proved);
        //   (2) the hand-built "harnessError true with a real
        //       harnessErrorMessage shows it" test, which existed ONLY
        //       because no real fixture carried the field. A real fixture
        //       does, with harnessErrorReason also present, so the
        //       hand-built map proved nothing more (message wins over
        //       reason) — deleted, not kept beside the real one.
        // The absent-message fallback keeps real-material coverage in the
        // next test (in-memory deletion from this same real document).
        val doc = loadRawDoc("v7-reports-document-sample-honor.json")
        val summary = readV7OverallSummary(doc, fakeContext())
        assertNull(summary?.letter)
        assertEquals(
            "تعذّر تجهيز نتائج الاختبارات لهذا الفحص، وهذا ليس حكماً على الجهاز.",
            summary?.absenceReasonText
        )
        assertEquals(
            "the raw reason is still on the document — it must not be what is shown",
            "FUNCTIONAL_RESULTS_UNAVAILABLE",
            (((doc["aiResponse"] as Map<*, *>)["overall"]) as Map<*, *>)["harnessErrorReason"]
        )
    }

    @Test
    fun `Honor B with harnessErrorMessage deleted in memory (the shape of pre-d5b6c29 Honor reports) falls back to the unavailable text, never the raw reason`() {
        // The real Honor document with ONE key removed in memory — no new
        // file under contract/. It is the shape of reports written before
        // the backend added harnessErrorMessage (this is what the previous
        // version of this very fixture looked like), which still sit in
        // users' Firestore history. harnessErrorReason stays present, so
        // this also keeps the original guard: the raw server code must not
        // reach the UI.
        val doc = loadRawDoc("v7-reports-document-sample-honor.json")
        @Suppress("UNCHECKED_CAST")
        val overall = (doc["aiResponse"] as MutableMap<String, Any?>)["overall"] as MutableMap<String, Any?>
        assertEquals("sanity: the key was there to delete", true, overall.containsKey("harnessErrorMessage"))
        overall.remove("harnessErrorMessage")
        assertEquals("FUNCTIONAL_RESULTS_UNAVAILABLE", overall["harnessErrorReason"])

        val summary = readV7OverallSummary(doc, fakeContext())
        assertNull(summary?.letter)
        assertEquals("HARNESS_MESSAGE_UNAVAILABLE_TEXT", summary?.absenceReasonText)
    }

    @Test
    fun `harnessError true with no harnessErrorMessage falls back to the unavailable text`() {
        // Not a real fixture — exercises readV7OverallSummary's own
        // fallback branch directly, with harnessErrorReason ALSO absent
        // (the harder case: nothing at all to show).
        val doc = mapOf(
            "aiResponse" to mapOf(
                "overall" to mapOf(
                    "letter" to null, "harnessError" to true, "harnessErrorReason" to null,
                    "noDataAtAll" to false, "partialAssessment" to false
                )
            )
        )
        val summary = readV7OverallSummary(doc, fakeContext())
        assertNull(summary?.letter)
        assertEquals("HARNESS_MESSAGE_UNAVAILABLE_TEXT", summary?.absenceReasonText)
    }

    @Test
    fun `noDataAtAll true with no harnessError resolves to the no-data text`() {
        val doc = mapOf(
            "aiResponse" to mapOf(
                "overall" to mapOf(
                    "letter" to null, "harnessError" to false, "noDataAtAll" to true,
                    "partialAssessment" to false
                )
            )
        )
        val summary = readV7OverallSummary(doc, fakeContext())
        assertNull(summary?.letter)
        assertEquals("NO_DATA_TEXT", summary?.absenceReasonText)
    }

    @Test
    fun `no aiResponse block at all returns null (this reader has nothing for a V6 or malformed doc)`() {
        val doc = mapOf("pipeline" to "v6_raw_timeseries")
        assertNull(readV7OverallSummary(doc, fakeContext()))
    }
}
