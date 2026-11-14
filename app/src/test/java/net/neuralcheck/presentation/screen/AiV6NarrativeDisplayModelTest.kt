package net.neuralcheck.presentation.screen

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.flattenReportDocument
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.domain.scoring.buildScoringResultFromResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Order 2026-09-26 (القرار ١٠٢، الملحق السابع — يلغي "لا تُغيَّر فرع V6"
 * في الملحق السادس البند ١؛ نفس التعليل والقيود: "اختبار بعيّنة V6
 * الحقيقية (القاعدة 41): الحقلان غائبان عن نموذج العرض، والحرف والفئات
 * والملاحظات حاضرة").
 *
 * Uses the REAL `v6-reports-document-sample.json` golden sample, parsed
 * through the same [flattenReportDocument] + Gson chain
 * `FirebaseAnalysisService.fetchExistingReport` uses (the "server path"
 * this order names), then through [buildScoringResultFromResponse] — the
 * same function that produces the `AiAnalysisResult` this app persists to
 * Room's `aiAnalysisJson` column (the "Room path" this order also names)
 * — so both of the order's two named read paths funnel through the exact
 * same `AiAnalysisResult` shape this test exercises.
 */
class AiV6NarrativeDisplayModelTest {

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

    private fun realV6AiAnalysisResult(): net.neuralcheck.domain.model.AiAnalysisResult {
        val doc = loadRawDoc("v6-reports-document-sample.json")
        val response = parseResponse(flattenReportDocument(doc))
        val scoring = buildScoringResultFromResponse(response, antiTamperPenalty = 0f, isIncomplete = false)
        return scoring.aiAnalysis!!
    }

    @Test
    fun `real V6 sample sanity check — details and verdict both carry real text today, in the data layer`() {
        // AiAnalysisResult itself is untouched, so these are still here.
        val ai = realV6AiAnalysisResult()
        assertTrue(ai.details.isNotBlank())
        assertTrue(ai.verdict.isNotBlank())
        assertEquals("B", ai.grade)
    }

    @Test
    fun `AiV6NarrativeDisplayModel does not declare a details or verdict field at all`() {
        val model = realV6AiAnalysisResult().toV6NarrativeDisplayModel()
        val fieldNames = model::class.java.declaredFields.map { it.name }
        assertFalse("AiV6NarrativeDisplayModel must not declare details", fieldNames.contains("details"))
        assertFalse("AiV6NarrativeDisplayModel must not declare verdict", fieldNames.contains("verdict"))
    }

    @Test
    fun `AiV6NarrativeDisplayModel still carries the real summary`() {
        val ai = realV6AiAnalysisResult()
        val model = ai.toV6NarrativeDisplayModel()
        assertTrue(model.summary.isNotBlank())
        assertEquals(ai.summary, model.summary)
    }

    @Test
    fun `what the V6 body would actually display - summary card renders, no details or verdict card, no empty card, letter and categories unaffected`() {
        val ai = realV6AiAnalysisResult()
        val model = ai.toV6NarrativeDisplayModel()

        // ReportScreen.kt's AiAnalysisSection (V6 body), post this order:
        // the summary card still renders from the display model.
        assertTrue(model.summary.isNotBlank())

        // No details/verdict field exists to even attempt reading — no
        // card is ever constructed (not constructed-then-hidden).
        val fieldNames = model::class.java.declaredFields.map { it.name }
        assertFalse(fieldNames.contains("details"))
        assertFalse(fieldNames.contains("verdict"))

        // The letter ("الحرف") is unaffected — it never went through this
        // display model at all (grade wasn't carried, since its only
        // consumer was the now-removed verdict card); it still resolves
        // from `ai.grade` directly wherever else this report reads it
        // (e.g. GradeSummaryCard's `scoring.overallGrade`).
        assertEquals("B", ai.grade)

        // The categories ("الفئات") are unaffected — AiCategoryEvaluationSection
        // reads `ai.categoryScores` directly, untouched by this change.
        assertTrue("the real V6 sample must still carry category scores", !ai.categoryScores.isNullOrEmpty())
    }
}
