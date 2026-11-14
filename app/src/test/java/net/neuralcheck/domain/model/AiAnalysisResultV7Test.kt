package net.neuralcheck.domain.model

import com.google.gson.Gson
import net.neuralcheck.data.remote.model.AiOverallV7
import net.neuralcheck.data.remote.model.AiPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3-a — domain-level mirror of AiAnalysisResponseV7Test's isComplete
 * coverage, plus the Room round-trip: [AiAnalysisResult] with V7 fields
 * set must survive a Gson round-trip through the same
 * `Gson().toJson(ai)` / `Gson().fromJson(json, AiAnalysisResult::class.java)`
 * pair `ReportViewModel.loadScan` uses for the `aiAnalysisJson` Room
 * column (see ViewModels.kt, the FAST PATH + the sparse-write guard).
 *
 * NEW tests — [AiAnalysisResult.isComplete] and its `pipeline`/`overall`/
 * `categories`/`narrative` fields did not exist before this phase, so
 * nothing here replaces prior-behaviour coverage (rule 23).
 */
class AiAnalysisResultV7Test {

    private val gson = Gson()

    @Test
    fun `V6 result with narrative content is complete, exactly as the prior inline check was`() {
        val ai = AiAnalysisResult(summary = "s")
        assertTrue(ai.isComplete())
    }

    @Test
    fun `V6 result with grade and score only is NOT complete, exactly as the prior inline check was`() {
        val ai = AiAnalysisResult(grade = "B", score = 70)
        assertFalse(ai.isComplete())
    }

    // Order 2026-09-26 (ملحق القرار ١٠٤ البند ١، القاعدة 23) — REPLACES
    // this test's prior body, which asserted completeness off
    // `AiOverallV7(letter = "B")` alone (no `letterSource`). That encoded
    // the exact discrepancy this order fixes: [AiAnalysisResult.isComplete]
    // used to govern V7 by `overall?.letter`, while the Response-level twin
    // already (correctly) governed by `overall?.letterSource`. Both now
    // delegate to the shared [AiPipeline.isComplete], which reads
    // `letterSource` — so a `letter`-only object like the old fixture
    // would now (correctly) read as NOT complete; this test is updated to
    // assert the real governing field instead of being left to fail.
    @Test
    fun `V7 result is complete when overall letterSource is present`() {
        val ai = AiAnalysisResult(
            pipeline = AiPipeline.V7_FINDINGS_BASED,
            overall = AiOverallV7(letterSource = "LLM_ADJUSTED")
        )
        assertTrue(ai.isComplete())
    }

    // NEW test (not a replacement — the old test above never covered this
    // negative case): pins the exact bug ملحق القرار ١٠٤ البند ١ fixes.
    // A real golden sample with this exact shape (`letter: null,
    // letterSource: "HARNESS_ERROR"`) exists at
    // `v7-reports-document-sample-honor.json` — covered end-to-end through
    // the real parsing chain in AiAnalysisResultHonorIsCompleteTest.
    @Test
    fun `V7 result with letter present but letterSource absent is NOT complete`() {
        val ai = AiAnalysisResult(
            pipeline = AiPipeline.V7_FINDINGS_BASED,
            overall = AiOverallV7(letter = "B")
        )
        assertFalse(
            "letter alone must not satisfy completeness — letterSource is " +
                "the real \"finished writing\" signal (AiPipeline.isComplete's doc)",
            ai.isComplete()
        )
    }

    @Test
    fun `V7 result with V6-shaped content but no overall letter is NOT complete`() {
        // The bug this fixes: before, AiScoringEngine never populated
        // summary/details/verdict for a V7 response (those come from
        // response.narrative, not response.summary — see AiScoringEngine's
        // pass-through), so a V7 AiAnalysisResult was ALWAYS summary="",
        // details="", verdict="", issues=[] — the old inline check
        // (summary.isNotBlank() || ...) could never be satisfied by a V7
        // result even when the V7 payload had full narrative content in
        // its OWN shape. This pins that a V7 result is judged on
        // overall.letter, not the V6 fields it never populates.
        val ai = AiAnalysisResult(pipeline = AiPipeline.V7_FINDINGS_BASED)
        assertFalse(ai.isComplete())
    }

    // Order 2026-09-26 (ملحق القرار ١٠٤ البند ١، القاعدة 23) — REPLACES
    // this fixture's prior `AiOverallV7(letter = "B", coveragePct = 82.0)`
    // (no `letterSource`): after the letterSource-unification fix,
    // `rehydrated.isComplete()` below would flip to false with the old
    // fixture, since `letter` no longer governs V7 completeness.
    // `letterSource` added so this test still proves what it always meant
    // to prove — a real V7 result's Room round-trip stays complete.
    @Test
    fun `a full V7-shaped AiAnalysisResult round-trips through Gson unchanged`() {
        val original = AiAnalysisResult(
            pipeline = AiPipeline.V7_FINDINGS_BASED,
            language = "ar",
            overall = AiOverallV7(letter = "B", letterSource = "LLM_ADJUSTED", coveragePct = 82.0),
            shortId = "NC-RT1"
        )
        val json = gson.toJson(original)
        val rehydrated = gson.fromJson(json, AiAnalysisResult::class.java)
        assertEquals(original, rehydrated)
        assertTrue(
            "a rehydrated V7 result must still pass the same completeness " +
                "gate a fresh one does — this is what makes the Room " +
                "round-trip (ReportViewModel's FAST PATH) actually work " +
                "for V7 scans",
            rehydrated.isComplete()
        )
    }

    @Test
    fun `a legacy-shaped cached json (no V7 fields at all) still deserialises safely`() {
        // Simulates a row written by THIS codebase before this phase —
        // aiAnalysisJson with none of the new V7 fields present, but WITH
        // the pre-existing non-nullable-typed fields present (issues=[]).
        // Gson instantiates AiAnalysisResult via Unsafe/Objenesis (no
        // accessible no-arg constructor — Kotlin default-value params
        // don't produce one without @JvmOverloads), so an ABSENT key
        // leaves that field genuinely null at the JVM level regardless of
        // its Kotlin default — see AiAnalysisResponse.kt's doc comment,
        // which documents the identical mechanism for the DTO. Omitting
        // "issues" here would tickle THAT pre-existing gap (a real one,
        // spotted while writing this test, flagged separately as
        // out-of-scope for Phase 3-a) rather than testing what this test
        // is actually about: V7-field absence tolerance.
        val legacyJson = """{"summary":"s","details":"d","verdict":"v","issues":[]}"""
        val rehydrated = gson.fromJson(legacyJson, AiAnalysisResult::class.java)
        assertEquals(null, rehydrated.pipeline)
        assertEquals(null, rehydrated.overall)
        assertTrue(rehydrated.isComplete())
    }
}
