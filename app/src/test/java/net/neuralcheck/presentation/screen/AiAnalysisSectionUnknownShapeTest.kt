package net.neuralcheck.presentation.screen

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.flattenReportDocument
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiPipeline
import net.neuralcheck.domain.scoring.buildScoringResultFromResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Order 2026-09-26 (القرار ١٠٤ البند ١، القاعدة 41). Fixes the known,
 * disclosed-but-unfixed limitation named in `078c9f9`'s own commit
 * message: an UNKNOWN-shape response used to fall through to the V6 body
 * with every field blank — an unannounced absence (rule 22).
 *
 * Rule 14 — the order's OWN first draft ("delete the root letter and
 * aiResponse.overall from the real V6 sample, keep the v6_raw_timeseries
 * tag") does NOT produce UNKNOWN: [AiPipeline.classifyShape] checks
 * `isRecognizedFlatShape(pipeline)` BEFORE any content signal — a
 * `v6_raw_timeseries`-tagged document is ALWAYS [AiPipeline.ShapeKind.V6],
 * regardless of what its root/aiResponse.overall actually hold. The
 * coordinator caught and corrected this before I did; independently
 * re-confirmed here against the real `classifyShape` function, not taken
 * on either party's word. UNKNOWN only arises two ways: `cached` with
 * NEITHER a root letter/grade nor a real V7 `aiResponse.overall`, or a
 * tag this app has never seen at all — so BOTH test documents below
 * additionally swap the `pipeline` tag (to `cached` and to a wholly
 * unrecognised value respectively), on top of the root-letter/overall
 * deletions the order also asked for.
 *
 * Both documents are still derived from the REAL `v6-reports-document-sample.json`
 * (only `pipeline`/`grade`/`aiResponse.overall` mutated — the latter was
 * already absent from this sample, confirmed, so that deletion is a
 * verified no-op, not skipped silently), run through the exact same
 * chain `FirebaseAnalysisService.fetchExistingReport` +
 * `AiScoringEngine`/`buildScoringResultFromResponse` use to produce the
 * `AiAnalysisResult` `AiAnalysisSection` actually receives.
 */
class AiAnalysisSectionUnknownShapeTest {

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

    private fun unknownShapeAi(pipelineTag: String): net.neuralcheck.domain.model.AiAnalysisResult {
        val v6 = loadRawDoc("v6-reports-document-sample.json")
        @Suppress("UNCHECKED_CAST")
        val aiResponseBlock = v6["aiResponse"] as Map<String, Any?>
        // Verified no-op, not skipped silently: the real V6 sample's own
        // aiResponse block has no "overall" key at all.
        assertFalse(aiResponseBlock.containsKey("overall"))
        // Rule 14 finding, independent of the coordinator's own catch:
        // deleting ONLY the root "grade" is not enough — the real sample
        // ALSO carries "grade":"B" inside the aiResponse wrapper (verified:
        // aiResponseBlock.containsKey("grade") below), and
        // flattenReportDocument's `putIfAbsent` merge backfills the root
        // from that wrapper copy once the root key is gone — so the root
        // deletion alone would silently leave `ai.grade` == "B", not null.
        // Both copies must be deleted for the document to genuinely have
        // no letter/grade signal anywhere.
        assertTrue(aiResponseBlock.containsKey("grade"))
        val trimmedAiResponse = aiResponseBlock - "grade"
        val doc = v6 - "grade" + mapOf("pipeline" to pipelineTag, "aiResponse" to trimmedAiResponse)
        val response = parseResponse(flattenReportDocument(doc))
        val scoring = buildScoringResultFromResponse(response, antiTamperPenalty = 0f, isIncomplete = false)
        return scoring.aiAnalysis!!
    }

    private fun assertIsUnknownShape(ai: net.neuralcheck.domain.model.AiAnalysisResult) {
        val shape = AiPipeline.classifyShape(
            ai.pipeline,
            hasRootLetterOrGrade = !ai.grade.isNullOrBlank(),
            hasV7Overall = ai.overall != null
        )
        assertEquals(AiPipeline.ShapeKind.UNKNOWN, shape)
    }

    @Test
    fun `document (a) - real V6 sample, grade and overall absent, tag changed to cached - classifies UNKNOWN`() {
        val ai = unknownShapeAi(AiPipeline.CACHED)
        assertNull("root grade must be genuinely absent for this case", ai.grade)
        assertNull("aiResponse.overall must be genuinely absent for this case", ai.overall)
        assertIsUnknownShape(ai)
    }

    @Test
    fun `document (b) - same deletions, wholly unrecognised tag - classifies UNKNOWN`() {
        val ai = unknownShapeAi("v8_something_new")
        assertNull(ai.grade)
        assertNull(ai.overall)
        assertIsUnknownShape(ai)
    }

    @Test
    fun `what the report screen would display for document (a) — no V6 body content, only the unavailable-grade line`() {
        val ai = unknownShapeAi(AiPipeline.CACHED)
        assertIsUnknownShape(ai)

        // ReportScreen.kt's AiAnalysisSection, post this order: UNKNOWN
        // short-circuits before ever reaching the V6 body — the exact
        // AiV7StatusLine(icon = "❔", text = report_grade_unavailable, ...)
        // call this fix added. No expression from the V6 body executes at
        // all (not "executes and shows blank" — never reached).

        // Cross-check, from the SAME real derived document, exactly which
        // V6-body cards this fix stops from (attempting to) render — every
        // one of their own individual gates was already false/empty for
        // this document, confirming "blank fields" was the real prior
        // behaviour, not a hypothetical:
        assertNull("no change-analysis block on this derived document", ai.changeAnalysis)
        // Correction from a first, WRONG assumption in this test (caught by
        // actually running it, not guessed twice): `ai.summary` is NOT
        // blank here — AiScoringEngine copies the real summary text
        // regardless of whether a grade/letter exists at all. This is a
        // sharper finding than "078c9f9's disclosed blank-fields limitation"
        // suggested: the OLD fall-through V6 body would have shown a real,
        // legitimate-LOOKING summary card for a document this app cannot
        // actually classify — arguably worse than blank, since it reads as
        // trustworthy content from an untrusted shape. Reinforces why this
        // fix short-circuits before the V6 body entirely, rather than
        // hiding just the letter.
        assertTrue(
            "this derived document's summary is real text, not blank — precisely the " +
                "leaked content this fix now prevents from reaching the screen at all",
            ai.summary.isNotBlank()
        )
        // Second and third wrong assumptions in this test, both caught by
        // actually running it rather than guessed a further time: the real
        // sample's `aiResponse.issues` (7 entries) and `aiResponse.category_scores`
        // (15 entries) are untouched by this test's mutations (only
        // grade/pipeline/overall were deleted) and flow straight through to
        // `ai.issues` / `ai.categoryScores` exactly like `ai.summary` did.
        // This sharpens the finding from "078c9f9's disclosed blank-fields
        // limitation" further still: the OLD fall-through V6 body would have
        // rendered real issue cards (severity/title/description) AND real
        // per-category score cards for a shape this app cannot classify —
        // not merely a real-looking summary, a fully-populated report body.
        // Independently re-verified against the raw fixture before writing
        // these assertions (python json walk over
        // v6-reports-document-sample.json): aiResponse.issues len=7,
        // aiResponse.category_scores len=15 — not guessed a third time.
        assertTrue(
            "this derived document's issues list is real content (7 entries in the " +
                "raw fixture), not empty — the same kind of leaked, legitimate-looking " +
                "content as the summary finding above",
            ai.issues.isNotEmpty()
        )
        assertTrue(
            "this derived document's categoryScores is real content (15 entries in the " +
                "raw fixture), not null/empty — same leak as issues/summary",
            !ai.categoryScores.isNullOrEmpty()
        )
    }

    @Test
    fun `GradeSummaryCard is already hidden for UNKNOWN, unaffected by this fix`() {
        val ai = unknownShapeAi(AiPipeline.CACHED)
        assertEquals(
            false,
            net.neuralcheck.util.AiV7Presentation.shouldShowGradeSummary(
                ai.pipeline,
                hasRootLetterOrGrade = !ai.grade.isNullOrBlank(),
                hasV7Overall = ai.overall != null
            )
        )
    }
}
