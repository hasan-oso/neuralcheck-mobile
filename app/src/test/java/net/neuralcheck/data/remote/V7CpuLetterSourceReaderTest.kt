package net.neuralcheck.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.R
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiPipeline
import net.neuralcheck.util.AiV7Presentation
import net.neuralcheck.util.AiVerdictPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStreamReader

/**
 * Order 2026-09-25 (القرار ٩٨ البند ١ / القرار ٩٩ البند ٢، backend@057cf43).
 * Companion to [V7SecurityLetterSourceReaderTest] — same full-path technique
 * (rule 30), a DIFFERENT sample and category. The backend now only writes
 * `LLM_ADJUSTED` when the adjustment actually CHANGES the letter (`49c0e40`);
 * this fixture is the real, current example of that: `cpu`'s deterministic
 * letter is B, test C-06 (thermal throttling) was made to fail, and the
 * accepted adjustment drops the letter to C — a real effect, so it IS
 * surfaced as `LLM_ADJUSTED`, with `adjustmentReason` and (still on the
 * model, per القرار ٩٩ البند ٤ — read but not displayed) `adjustmentEvidence`
 * pointing at `categoryStates.testResults[13].outcome`.
 *
 * The app has no `verifyReport` (C) reader (confirmed again:
 * `git grep -n "verifyReport" app/src/main` — comment-only hits), so only
 * the reports-document shape (B) is exercised, exactly as in the security
 * counterpart.
 *
 * Full real path (identical to `V7SecurityLetterSourceReaderTest`'s, see
 * its doc for the line-by-line trace): raw document →
 * [flattenReportDocument] → the same Gson round-trip
 * `FirebaseAnalysisService.fetchExistingReport` performs → `categories.cpu`
 * → [AiVerdictPresentation.isKnownGrade] / [AiV7Presentation.classifyLetterSource]
 * (`ReportScreen.kt:2635`/`:2646`) → the label/reason text
 * `AiV7LetterSourceRow` would show. Same disclosed gap as that file: when written the module lacked
 * Robolectric/Compose-UI-test infra (both are test dependencies since 3284cca; no test renders this row), so the final two-
 * expression text assembly (label resource id, reason fallback) is
 * reproduced verbatim with exact `ReportScreen.kt` line citations rather
 * than executed through real composition. The evidence-hiding structural
 * guard (grepping both render surfaces for `.adjustmentEvidence`) already
 * lives in `V7SecurityLetterSourceReaderTest` — not duplicated here, since
 * it is generic to both surfaces, not per-sample.
 */
class V7CpuLetterSourceReaderTest {

    private fun loadRawDoc(fileName: String): Map<String, Any?> {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$fileName")
            ?: error("Fixture not found on classpath — expected at app/src/test/resources/contract/$fileName")
        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return Gson().fromJson(text, type)
    }

    private fun parseResponse(flat: Map<String, Any?>): AiAnalysisResponse {
        val gson = Gson()
        return gson.fromJson(gson.toJson(flat), AiAnalysisResponse::class.java)
    }

    private fun fakeContext(): Context {
        val context = mock<Context>()
        // Order 2026-10-01 (A7): "النموذج" ⇐ "الذكاء الاصطناعي" — pinned
        // literal updated to match the new resource value, not just a
        // placeholder (this test pins the real production string, see the
        // assertion below).
        whenever(context.getString(R.string.report_v7_letter_source_llm_adjusted))
            .thenReturn("حرف عدّله الذكاء الاصطناعي")
        return context
    }

    @Test
    fun `cpu-c06-downgrade sample B passes the cache gate and flattens to the real cpu category`() {
        val data = loadRawDoc("v7-reports-document-sample-cpu-c06-downgrade.json")
        assertTrue(
            "a complete V7 doc with overall.letter present must pass fetchExistingReport's " +
                "own cache gate, or the real reader would never reach the parse branch at all",
            isCachedReportComplete(data)
        )
        val response = parseResponse(flattenReportDocument(data))
        assertEquals(AiPipeline.V7_FINDINGS_BASED, response.pipeline)

        val cpu = response.categories!!.getValue("cpu")
        assertEquals("MEASURED", cpu.state)
        assertEquals("C", cpu.letter)
        assertEquals("LLM_ADJUSTED", cpu.letterSource)
        assertEquals(
            "الاختبار C-06 (تقييد الأداء الحراري) فشل فعلياً في فئة المعالج — قياس حقيقي " +
                "على هذا الفحص، ويسند تخفيضاً في هذه الفئة تحديداً.",
            cpu.adjustmentReason
        )
        // Still READ off the model (القرار ٩٩ البند ٤: "لا تغيّر الحقل في
        // النموذج ... فهو يُقرأ ولا يُعرض") — just never displayed, proven
        // by the structural guard in V7SecurityLetterSourceReaderTest.
        assertEquals(listOf("categoryStates.testResults[13].outcome"), cpu.adjustmentEvidence)
    }

    @Test
    fun `cpu's letter-source row shows the LLM_ADJUSTED label and the real reason, letter C, no evidence expression left to mirror`() {
        val data = loadRawDoc("v7-reports-document-sample-cpu-c06-downgrade.json")
        val response = parseResponse(flattenReportDocument(data))
        val cpu = response.categories!!.getValue("cpu")

        // ReportScreen.kt:2635 — AiCategoryRowV7's own `known` gate. C is a
        // real grade, so the letterSource row renders.
        val known = AiVerdictPresentation.isKnownGrade(cpu.letter)
        assertTrue("cpu has a real letter (C) — its letterSource row must render", known)

        // ReportScreen.kt:2646
        val kind = AiV7Presentation.classifyLetterSource(cpu.letterSource)
        assertEquals(AiV7Presentation.LetterSourceKind.LLM_ADJUSTED, kind)

        // ReportScreen.kt:2827-2828 — the label shown beside the 🤖 icon for
        // the LLM_ADJUSTED branch specifically.
        val context = fakeContext()
        val label = context.getString(R.string.report_v7_letter_source_llm_adjusted)
        assertEquals("حرف عدّله الذكاء الاصطناعي", label)

        // ReportScreen.kt:2843 — reason?.takeIf { it.isNotBlank() } ?: fallback.
        // cpu.adjustmentReason is real, non-blank text, so it renders
        // verbatim; the report_v7_adjustment_reason_unavailable fallback is
        // never reached for this sample.
        val displayedReason = cpu.adjustmentReason?.takeIf { it.isNotBlank() }
        assertEquals(
            "الاختبار C-06 (تقييد الأداء الحراري) فشل فعلياً في فئة المعالج — قياس حقيقي " +
                "على هذا الفحص، ويسند تخفيضاً في هذه الفئة تحديداً.",
            displayedReason
        )

        // The letter itself, shown in the grade Surface right above this row
        // (ReportScreen.kt:2673-2683 — `known -> cat.letter!!.trim().uppercase()`).
        assertEquals("C", cpu.letter?.trim()?.uppercase())

        // Order 2026-09-25 (القرار ٩٩ البند ٤) — no evidence-display
        // expression exists to mirror any more, even though this category
        // (unlike security) DOES have a non-null adjustmentEvidence on the
        // model (proven in the test above). "لا يُعرض الدليل" is proven
        // durably by V7SecurityLetterSourceReaderTest's structural guard,
        // which scans both render surfaces generically, not per-sample.
    }
}
