package net.neuralcheck.util

import com.google.gson.Gson
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Pins the fix for ReportScreen.kt:1243 (`AiVerdictCard(grade = ai.grade
 * ?: "?")`, Phase 3-b). NEW tests — no prior test in this codebase exercised
 * `AiVerdictCard`'s grade handling (no test file named it, confirmed by
 * search before writing this), so nothing here replaces a test that was
 * pinning the old `"?"`-coalescing behaviour; there was none to replace
 * (project rule 23's "if none existed, say it's new").
 */
class AiVerdictPresentationTest {

    @Test
    fun `the four real grades are known in any case`() {
        for (g in listOf("A", "B", "C", "D", "a", "b", "c", "d")) {
            assertTrue(g, AiVerdictPresentation.isKnownGrade(g))
        }
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertTrue(AiVerdictPresentation.isKnownGrade(" B "))
    }

    @Test
    fun `null is not a known grade`() {
        // The actual absence case this class exists for: AiAnalysisResult.grade
        // is null when the backend omits it (AiAnalysisResponse.kt's own doc:
        // "A/B/C/D — null when backend omits"). This must render as an
        // explicit missing state, never fall through to a literal string.
        assertFalse(AiVerdictPresentation.isKnownGrade(null))
    }

    @Test
    fun `a literal question mark is not a known grade`() {
        // The exact sentinel the old `ai.grade ?: "?"` call site used to
        // manufacture. If a future backend payload ever legitimately sends
        // "?" for some reason, it must still be treated as unknown, not as
        // a fifth valid grade.
        assertFalse(AiVerdictPresentation.isKnownGrade("?"))
    }

    @Test
    fun `blank and other garbage are not known grades`() {
        assertFalse(AiVerdictPresentation.isKnownGrade(""))
        assertFalse(AiVerdictPresentation.isKnownGrade("   "))
        assertFalse(AiVerdictPresentation.isKnownGrade("F"))
        assertFalse(AiVerdictPresentation.isKnownGrade("AA"))
    }

    // ── resolveOverallGrade — Phase 3-c ──
    //
    // Pins the fix for AiScoringEngine.kt:393 (`overallGrade = response.grade
    // ?: "?"`, Phase 3-c). NEW tests: no prior test in this codebase
    // exercised ScoringResult.overallGrade's construction at all (confirmed
    // by search before writing this — AiScoringEngine has no test file; its
    // constructor needs Context/Firebase/Room DI with no existing harness),
    // so nothing here replaces a test that pinned the old unconditional
    // `response.grade ?: "?"` behaviour — there was none to replace (rule
    // 23's "if none existed, say it's new"). The bug itself was real: every
    // V7 report's overallGrade printed "?" regardless of the actual letter.

    @Test
    fun `V6 pipeline (explicit marker) reads the flat grade field`() {
        assertEquals(
            "B",
            AiVerdictPresentation.resolveOverallGrade(
                pipeline = AiPipeline.V6_RAW_TIMESERIES, v6Grade = "B", v7Letter = null
            )
        )
    }

    @Test
    fun `null pipeline (legacy, pre-dates the field) also reads the flat grade field`() {
        assertEquals(
            "A",
            AiVerdictPresentation.resolveOverallGrade(pipeline = null, v6Grade = "A", v7Letter = null)
        )
    }

    @Test
    fun `V7 pipeline reads overall_letter, not the flat grade field`() {
        // The exact bug this function fixes: AiAnalysisResponse.grade is
        // documented null for every V7 response, so before this fix the
        // flat field was the ONLY one ever read here.
        assertEquals(
            "C",
            AiVerdictPresentation.resolveOverallGrade(
                pipeline = AiPipeline.V7_FINDINGS_BASED, v6Grade = null, v7Letter = "C"
            )
        )
    }

    @Test
    fun `V7 pipeline ignores a stray non-null flat grade - overall_letter always wins`() {
        // Defensive: the contract says response.grade is never populated
        // for V7, but nothing at the type level enforces that. If it were
        // ever non-null anyway, overall.letter must still be the one
        // source of truth for a V7 report, never silently overridden.
        assertEquals(
            "D",
            AiVerdictPresentation.resolveOverallGrade(
                pipeline = AiPipeline.V7_FINDINGS_BASED, v6Grade = "A", v7Letter = "D"
            )
        )
    }

    @Test
    fun `V7 pipeline with a null overall_letter falls back to the unknown sentinel`() {
        assertEquals(
            "?",
            AiVerdictPresentation.resolveOverallGrade(
                pipeline = AiPipeline.V7_FINDINGS_BASED, v6Grade = null, v7Letter = null
            )
        )
    }

    @Test
    fun `V6 pipeline with a null flat grade falls back to the unknown sentinel`() {
        assertEquals(
            "?",
            AiVerdictPresentation.resolveOverallGrade(
                pipeline = AiPipeline.V6_RAW_TIMESERIES, v6Grade = null, v7Letter = null
            )
        )
    }

    @Test
    fun `a blank letter is treated the same as null, never rendered as a blank grade`() {
        assertEquals(
            "?",
            AiVerdictPresentation.resolveOverallGrade(
                pipeline = AiPipeline.V7_FINDINGS_BASED, v6Grade = null, v7Letter = "   "
            )
        )
    }

    @Test
    fun `golden-sample integration - the real V7 payload resolves to its actual letter, not the unknown sentinel`() {
        // Ties this to the real contract file rather than hand-typed
        // strings, per project rule 5. Sample: pipeline="v7_findings_based",
        // grade=null (absent from the JSON entirely), overall.letter="C".
        val response = InputStreamReader(
            javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")
                ?: error("contract/v7-client-payload-sample.json not found on the test classpath"),
            Charsets.UTF_8
        ).use { reader -> Gson().fromJson(reader, AiAnalysisResponse::class.java) }

        assertEquals(AiPipeline.V7_FINDINGS_BASED, response.pipeline)
        assertEquals(null, response.grade)
        assertEquals(
            "C",
            AiVerdictPresentation.resolveOverallGrade(
                pipeline = response.pipeline, v6Grade = response.grade, v7Letter = response.overall?.letter
            )
        )
    }

    // ── gradeBucket — this phase's fix ──
    //
    // Pins the fix for two call sites that each re-implemented grade
    // classification with their own `when (grade) { "A" -> ...; "B" -> ...;
    // "C" -> ...; else -> <D-shaped thing> }`, so an unrecognised grade
    // (including the "?" sentinel resolveOverallGrade above can itself
    // produce) fell through to the D branch and rendered as a failure:
    //   - ReportScreen.kt's GradeSummaryCard: score number painted GradeD
    //     red.
    //   - ReportPdfGenerator's gradeDescription: PDF caption printed the
    //     "ضعيف — مشاكل جوهرية" (D-grade) description text.
    // NEW tests — no prior test in this codebase exercised either call
    // site's grade-to-colour/caption `when` (confirmed by search: no test
    // file exists for ReportScreen.kt or ReportPdfGenerator.kt at all, and
    // at the time this project had neither Robolectric nor a Compose-test dependency to exercise
    // Composables or PDF Context calls directly (both are test dependencies since 3284cca —
    // see build.gradle.kts's test config), so nothing here replaces a test that pinned the old
    // `else -> D` behaviour; there was none to replace (rule 23's "if none
    // existed, say it's new"). Each test asserts the BUCKET the reader
    // (gradeBucket) produces for the input, not merely that the input was
    // "reached" — GradeSummaryCard/gradeDescription then each map that
    // bucket to their own Colour/string-resource with an exhaustive `when`
    // (no `else`), so the bucket value here is exactly what determines the
    // colour and text those two call sites render.

    @Test
    fun `gradeBucket - real letter grades map to their own bucket, any case`() {
        assertEquals(AiVerdictPresentation.GradeBucket.A, AiVerdictPresentation.gradeBucket("A"))
        assertEquals(AiVerdictPresentation.GradeBucket.A, AiVerdictPresentation.gradeBucket("a"))
        assertEquals(AiVerdictPresentation.GradeBucket.B, AiVerdictPresentation.gradeBucket("B"))
        assertEquals(AiVerdictPresentation.GradeBucket.B, AiVerdictPresentation.gradeBucket("b"))
        assertEquals(AiVerdictPresentation.GradeBucket.C, AiVerdictPresentation.gradeBucket("C"))
        assertEquals(AiVerdictPresentation.GradeBucket.C, AiVerdictPresentation.gradeBucket("c"))
    }

    @Test
    fun `gradeBucket - a real D stays D, not unknown`() {
        // The exact non-regression this fix must not break: a device that
        // genuinely earned a D must still render as D/red, not get swept
        // into the new neutral bucket along with "?".
        assertEquals(AiVerdictPresentation.GradeBucket.D, AiVerdictPresentation.gradeBucket("D"))
        assertEquals(AiVerdictPresentation.GradeBucket.D, AiVerdictPresentation.gradeBucket("d"))
    }

    @Test
    fun `gradeBucket - the literal question mark sentinel is unknown, not D`() {
        // The exact bug: resolveOverallGrade's own "?" fallback, fed back
        // into a grade-colour `when`, used to land on D/red at both fixed
        // call sites.
        assertEquals(AiVerdictPresentation.GradeBucket.UNKNOWN, AiVerdictPresentation.gradeBucket("?"))
    }

    @Test
    fun `gradeBucket - blank and null are unknown, not D`() {
        assertEquals(AiVerdictPresentation.GradeBucket.UNKNOWN, AiVerdictPresentation.gradeBucket(""))
        assertEquals(AiVerdictPresentation.GradeBucket.UNKNOWN, AiVerdictPresentation.gradeBucket("   "))
        assertEquals(AiVerdictPresentation.GradeBucket.UNKNOWN, AiVerdictPresentation.gradeBucket(null))
    }

    @Test
    fun `gradeBucket - an explicit unrecognised grade is unknown, not D`() {
        // A value that is neither absent nor the "?" sentinel — some other
        // string a future backend change could send — must still be
        // treated as unknown, never silently painted as a D-grade failure.
        assertEquals(AiVerdictPresentation.GradeBucket.UNKNOWN, AiVerdictPresentation.gradeBucket("X"))
        assertEquals(AiVerdictPresentation.GradeBucket.UNKNOWN, AiVerdictPresentation.gradeBucket("F"))
        assertEquals(AiVerdictPresentation.GradeBucket.UNKNOWN, AiVerdictPresentation.gradeBucket("AA"))
    }
}
