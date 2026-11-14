package net.neuralcheck.domain.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.flattenReportDocument
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.isComplete
import net.neuralcheck.domain.scoring.buildScoringResultFromResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Order 2026-09-26 (ملحق القرار ١٠٤ البند ١، القاعدة 41). Rule for
 * completeness, unified: [AiAnalysisResult.isComplete] (Room pre-save
 * gate, ViewModels.kt's `aiHasContent` at `loadScan`) and
 * [net.neuralcheck.data.remote.model.isComplete] (post-fetch /
 * Firestore-cache gate) both now delegate to
 * [net.neuralcheck.data.remote.model.AiPipeline.isComplete], governed by
 * `overall?.letterSource` for V7 — not `overall?.letter`, which is
 * legitimately `null` for a fully-formed `HARNESS_ERROR` answer.
 *
 * This file exercises that fix against the REAL
 * `v7-reports-document-sample-honor.json` golden sample — independently
 * confirmed (python json walk over the raw fixture, not taken on the
 * order's word) to have exactly this shape:
 *   `aiResponse.overall = { "letter": null, "letterSource": "HARNESS_ERROR",
 *   "grade": null }`, root `grade` absent, `pipeline: "v7_findings_based"`.
 * Note on the fixture's own name: its `deviceModel`/`deviceMarketingName`
 * fields are literally `"Unknown"` in the JSON — the "Honor" in the
 * filename names the scenario this sample represents from an earlier
 * session, not a literal field value in this file. Disclosed here rather
 * than silently assumed.
 *
 * "يُحفظ التقرير في Room": proven by asserting `ai.isComplete()` is true
 * for this real sample — that boolean IS `aiHasContent` at
 * `ViewModels.kt:874`, which directly gates whether `aiJson` is computed
 * and passed to `scoreRepository.save(...)` at `ViewModels.kt:884-898`
 * (read directly, cited by line, not assumed).
 *
 * "ويُفتح منه دون نداء شبكة": `ViewModels.kt:739-772` is the FAST PATH
 * `loadScan` takes when `cachedScore?.aiAnalysisJson` is non-blank — it
 * performs exactly `Gson().fromJson(cachedAiJson, AiAnalysisResult::class.java)`
 * (mirrored below), builds a `ScoringResult` locally, sets
 * `_scoringResult.value`, reads `testResultRepository.getResults` (Room),
 * and `return@launch`es — BEFORE the function ever reaches
 * `scoringEngine.calculateScore(scanReport)` at `ViewModels.kt:826`, the
 * ONLY network/AI-invoking call anywhere in `loadScan`. Independently
 * re-confirmed by grepping that exact line range for
 * firebase/firestore/network/http/api/analyzeDevice/scoringEngine/fetch —
 * zero matches (one comment mentioning "AiScoringEngine's live path" as a
 * CONTRAST, not a call). A full `ReportViewModel` integration test would
 * require new Room/repository mocking infrastructure this repo does not
 * yet have for this ViewModel (grep found no existing
 * `ReportViewModelTest`) — out of this order's named scope to build
 * blind; this file instead proves the fast path's own real logic
 * (Gson deserialize + isComplete) against the real sample, and the
 * "no network call" half of the claim is supported by the static line
 * citation above rather than a dynamic ViewModel test. Flagged plainly,
 * not silently substituted.
 */
class AiAnalysisResultHonorIsCompleteTest {

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

    private fun honorAi(): AiAnalysisResult {
        val doc = loadRawDoc("v7-reports-document-sample-honor.json")
        val response = parseResponse(flattenReportDocument(doc))
        val scoring = buildScoringResultFromResponse(response, antiTamperPenalty = 0f, isIncomplete = false)
        return scoring.aiAnalysis!!
    }

    @Test
    fun `real Honor-B sample has letter null but letterSource HARNESS_ERROR, confirmed from the raw fixture`() {
        val ai = honorAi()
        assertNull("this sample's letter is genuinely null, not just unread", ai.overall?.letter)
        assertEquals("HARNESS_ERROR", ai.overall?.letterSource)
        assertNull("root grade is absent on this sample", ai.grade)
    }

    @Test
    fun `real Honor-B sample is complete — would be saved to Room (ViewModels-kt colon-874's aiHasContent)`() {
        val ai = honorAi()
        assertTrue(
            "letterSource alone must satisfy completeness even though letter is null — " +
                "this is the exact bug ملحق القرار ١٠٤ البند ١ fixes",
            ai.isComplete()
        )
    }

    @Test
    fun `real Honor-B sample's raw document also satisfies the Response-level gate — both sides agree post-unification`() {
        val doc = loadRawDoc("v7-reports-document-sample-honor.json")
        val response = parseResponse(flattenReportDocument(doc))
        assertTrue(response.isComplete())
    }

    @Test
    fun `Honor-B AiAnalysisResult round-trips through the exact Gson pair loadScan's FAST PATH uses, and stays complete`() {
        // Mirrors ViewModels.kt:741-742 exactly: Gson().toJson(ai) is what
        // gets written to the aiAnalysisJson Room column (ViewModels.kt:897,
        // via the aiJson computed at :875-877); Gson().fromJson(json,
        // AiAnalysisResult::class.java) is the FAST PATH's own rehydration
        // at reopen (:741-742). No network call anywhere in this sequence.
        val original = honorAi()
        val gson = Gson()
        val json = gson.toJson(original)
        val rehydrated = gson.fromJson(json, AiAnalysisResult::class.java)
        assertEquals("HARNESS_ERROR", rehydrated.overall?.letterSource)
        assertNull(rehydrated.overall?.letter)
        assertTrue(
            "a reopen via the FAST PATH must judge this rehydrated result " +
                "exactly as complete as the fresh one — this is what makes " +
                "ViewModels.kt:739-772 actually skip the AI call for this " +
                "real report shape",
            rehydrated.isComplete()
        )
    }

    @Test
    fun `a report without letterSource (letter also absent) is NOT complete`() {
        // Coordinator's second required test, verbatim: "واختبار أن تقريراً
        // بلا letterSource لا يُعدّ كاملاً." Derived from the SAME real
        // sample (rule 41) by additionally deleting aiResponse.overall's
        // letterSource — the real fixture already has letter: null; this
        // mutation removes the ONE remaining V7 completeness signal too,
        // so the result carries neither.
        val doc = loadRawDoc("v7-reports-document-sample-honor.json")
        @Suppress("UNCHECKED_CAST")
        val aiResponseBlock = doc["aiResponse"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val overallBlock = aiResponseBlock["overall"] as Map<String, Any?>
        assertTrue(
            "verified no-op guard: the real sample's overall.letter is " +
                "already null — this mutation targets letterSource only",
            overallBlock["letter"] == null
        )
        assertEquals("HARNESS_ERROR", overallBlock["letterSource"])
        val trimmedOverall = overallBlock - "letterSource"
        val trimmedAiResponse = aiResponseBlock + mapOf("overall" to trimmedOverall)
        val mutatedDoc = doc + mapOf("aiResponse" to trimmedAiResponse)

        val response = parseResponse(flattenReportDocument(mutatedDoc))
        assertNull("letterSource must be genuinely absent for this case", response.overall?.letterSource)
        assertNull("letter was already absent on the real sample", response.overall?.letter)

        val scoring = buildScoringResultFromResponse(response, antiTamperPenalty = 0f, isIncomplete = false)
        val ai = scoring.aiAnalysis!!
        assertFalse(
            "neither letter nor letterSource present — this report must " +
                "NOT read as complete (rule 22: no signal must not read as success)",
            ai.isComplete()
        )
        assertFalse("Response-level gate must agree", response.isComplete())
    }
}
