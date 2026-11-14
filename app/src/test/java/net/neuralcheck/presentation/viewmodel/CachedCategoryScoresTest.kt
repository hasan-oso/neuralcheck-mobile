package net.neuralcheck.presentation.viewmodel

import com.google.gson.Gson
import net.neuralcheck.domain.model.AiAnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A110 — the cache-hit re-render path (ReportViewModel.loadScan, the
 * FAST PATH that deserialises `aiAnalysisJson` instead of calling the AI
 * again) built CategoryScore without measured/declaredCount/excludedCount/
 * note, while the live path (AiScoringEngine) always passed all four. A
 * re-opened report from cache silently lost the unmeasured-category
 * contract that a freshly-scored report kept.
 *
 * These tests exercise [categoryScoresFromCachedAi] — the function
 * `ReportViewModel.loadScan` now calls on the cache-hit path — through
 * the same Gson round-trip the ViewModel performs on `aiAnalysisJson`.
 */
class CachedCategoryScoresTest {

    private val gson = Gson()

    @Test
    fun `cached json with the four fields present carries them through`() {
        // Shape a current write produces: AiCategoryScore already has
        // measured/declaredCount/excludedCount/note, so Gson.toJson(ai)
        // includes them, and this is what a re-open reads back.
        val json = """
            {"categoryScores":{"camera":{
                "grade":"?","score":0,"pass_count":0,"total_count":0,
                "note":"لا اختبارات متاحة لهذه الفئة",
                "declared_count":9,"excluded_count":9,"measured":false}}}
        """.trimIndent()
        val ai = gson.fromJson(json, AiAnalysisResult::class.java)

        val cat = categoryScoresFromCachedAi(ai.categoryScores).getValue("camera")

        assertEquals(false, cat.measured)
        assertEquals(9, cat.declaredCount)
        assertEquals(9, cat.excludedCount)
        assertEquals("لا اختبارات متاحة لهذه الفئة", cat.note)
    }

    @Test
    fun `cached json predating the fields yields explicit null, not a default`() {
        // Shape of a row written before AiCategoryScore carried
        // measured/declaredCount/excludedCount at all — the keys are
        // simply absent, as they are on every pre-existing Room row.
        val json = """
            {"categoryScores":{"battery":{
                "grade":"B","score":78,"pass_count":6,"total_count":8,
                "note":"fine"}}}
        """.trimIndent()
        val ai = gson.fromJson(json, AiAnalysisResult::class.java)

        val cat = categoryScoresFromCachedAi(ai.categoryScores).getValue("battery")

        // Rule 22: absence must surface as an explicit unknown state, not
        // be read as measured-and-zero / not-excluded.
        assertNull("legacy row: measured must stay unknown, not false", cat.measured)
        assertNull("legacy row: declaredCount must stay unknown, not 0", cat.declaredCount)
        assertNull("legacy row: excludedCount must stay unknown, not 0", cat.excludedCount)
        assertEquals("fine", cat.note)
    }

    @Test
    fun `no categoryScores at all maps to an empty map, not a crash`() {
        val ai = gson.fromJson("""{"summary":"x"}""", AiAnalysisResult::class.java)
        assertEquals(emptyMap<String, Any>(), categoryScoresFromCachedAi(ai.categoryScores))
    }
}
