package net.neuralcheck.presentation.screen

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.flattenReportDocument
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.util.CategoryPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Order 2026-09-26 (القرار ١٠٢، الملحق الرابع، البند ١، القاعدة 41):
 * "اختبار بعيّنة V6 الحقيقية … يثبت غياب النسبة وبقاء الحرف والتغطية."
 *
 * Uses the REAL `v6-reports-document-sample.json` golden sample, parsed
 * through the exact same [flattenReportDocument] + Gson chain
 * `FirebaseAnalysisService.fetchExistingReport` uses (real `battery`
 * category: grade "A", pass_count 9, total_count 9, score 100,
 * excluded_count 1, declared_count 10), not a hand-built `AiCategoryScore`.
 *
 * Proves both:
 *   (a) [AiCategoryDisplayModel] does not declare `passCount`/`score` at
 *       all — the same compile-time-absence guarantee already used for
 *       `HomeViewModel.LastScan`/`ScanHistoryViewModel.CloudScan`
 *       (القرار ١٠٢، الملحق الثالث، البند ٢) — via reflection.
 *   (b) the actual DECISION `AiCategoryEvaluationSection` makes from it —
 *       no pass-rate percentage to show, the letter shows, the coverage
 *       line shows — reproducing (verbatim, cited by line) the same
 *       `CategoryPresentation.notRunCount`/`isUnmeasured` calls that
 *       function makes, not just field reachability (rule 30). This
 *       function is itself `@Composable` and is not called directly
 *       here (this test does not use Robolectric, a test dependency since 3284cca) — same disclosed gap as this suite's other
 *       Composable-adjacent tests.
 */
class AiCategoryDisplayModelTest {

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

    private fun realBatteryCategory(): net.neuralcheck.data.remote.model.AiCategoryScore {
        val doc = loadRawDoc("v6-reports-document-sample.json")
        val response = parseResponse(flattenReportDocument(doc))
        return response.categoryScores!!.getValue("battery")
    }

    @Test
    fun `real V6 sample battery category sanity check — grade A, pass_count 9, score 100`() {
        // Confirms the fixture values this whole test relies on, from a
        // real run, before asserting anything about the display model.
        val battery = realBatteryCategory()
        assertEquals("A", battery.grade)
        assertEquals(9, battery.passCount)
        assertEquals(100, battery.score)
        assertEquals(9, battery.totalCount)
        assertEquals(1, battery.excludedCount)
        assertEquals(10, battery.declaredCount)
    }

    @Test
    fun `AiCategoryDisplayModel does not declare a passCount or score field at all`() {
        val model = realBatteryCategory().toDisplayModel()
        val fieldNames = model::class.java.declaredFields.map { it.name }
        assertFalse("AiCategoryDisplayModel must not declare passCount", fieldNames.contains("passCount"))
        assertFalse("AiCategoryDisplayModel must not declare score", fieldNames.contains("score"))
    }

    @Test
    fun `AiCategoryDisplayModel carries the real letter, coverage-related counts, and note`() {
        val model = realBatteryCategory().toDisplayModel()
        assertEquals("A", model.grade)
        assertEquals(9, model.totalCount)
        assertEquals(10, model.declaredCount)
        assertEquals(1, model.excludedCount)
        assertTrue(model.note.isNotBlank())
    }

    @Test
    fun `what AiCategoryEvaluationSection would actually display — no pass-rate percentage, letter and coverage line both present`() {
        val model = realBatteryCategory().toDisplayModel()

        // ReportScreen.kt's AiCategoryEvaluationSection, post this order:
        // no `CategoryPresentation.passPercent` call exists any more for
        // this row at all — there is no `passCount` on `model` to even
        // call it with. This is the "غياب النسبة" the order asks to prove.
        val fieldNames = model::class.java.declaredFields.map { it.name }
        assertFalse(fieldNames.contains("passCount"))

        // The letter badge — unaffected, still real (ReportScreen.kt's
        // `cat.grade` reference, now via `model.grade`).
        assertEquals("A", model.grade)

        // The coverage line — unaffected, still real (ReportScreen.kt's
        // `CategoryPresentation.notRunCount(...)` call, reproduced here
        // against the same real fixture values `model` carries).
        val notRun = CategoryPresentation.notRunCount(
            excludedCount = model.excludedCount,
            declaredCount = model.declaredCount,
            totalCount = model.totalCount
        )
        assertEquals(
            "the real V6 sample's battery category must still show a coverage line (declared 10, ran 9)",
            1, notRun
        )
    }

    @Test
    fun `an unmeasured category still resolves via CategoryPresentation-isUnmeasured off the display model`() {
        // Structural proof that AiCategoryDisplayModel carries everything
        // `CategoryPresentation.isUnmeasured` needs (measured/grade/totalCount)
        // — the other classification AiCategoryEvaluationSection makes
        // from this model, unaffected by removing passCount/score.
        val model = realBatteryCategory().toDisplayModel()
        val unmeasured = CategoryPresentation.isUnmeasured(
            measured = model.measured,
            grade = model.grade,
            totalCount = model.totalCount
        )
        assertEquals(false, unmeasured)
    }
}
