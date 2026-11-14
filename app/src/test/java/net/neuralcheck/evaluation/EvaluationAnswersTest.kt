package net.neuralcheck.evaluation

import net.neuralcheck.domain.evaluation.EvaluationAnswers
import net.neuralcheck.domain.evaluation.EvaluationPayloadBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * feat/technician-evaluation — the pure answer state machine the
 * ViewModel delegates to. Covers the two structural guarantees the task
 * demands beyond payload shape:
 *  1. sessionId is snapshotted into the draft at card init and rides
 *     into the FIRST persisted answer (never read from the holder later).
 *  2. Answers accumulate and survive process recreation: the persisted
 *     row IS the state — rebuilding from it and answering further keeps
 *     every earlier judgement (the exact restore path bind() runs).
 */
class EvaluationAnswersTest {

    private fun freshDraft() = EvaluationAnswers.emptyDraft(
        scanId = "scan-9",
        sessionIdSnapshot = "sess-snapshot-42",
        shownAtMs = 1_000L
    )

    // ─────────── sessionId snapshot at first answer ───────────

    @Test
    fun `first answer carries the sessionId snapshotted at card init`() {
        val draft = freshDraft()
        val afterFirstTap = EvaluationAnswers.withGrade(draft, "good", nowMs = 2_000L)
        // This entity is exactly what the first IO upsert writes.
        assertEquals("sess-snapshot-42", afterFirstTap.sessionId)
        assertEquals("the durationMs window starts at first-shown",
            1_000L, afterFirstTap.shownAtMs)
        assertEquals(2_000L, afterFirstTap.lastAnswerAtMs)
    }

    @Test
    fun `session snapshot survives every subsequent answer`() {
        var e = freshDraft()
        e = EvaluationAnswers.withGrade(e, "weak", 2_000L)
        e = EvaluationAnswers.withCategoryRating(e, "battery", "weak", 3_000L)
        e = EvaluationAnswers.withDefectToggled(e, "water_damage", 4_000L)
        assertEquals("sess-snapshot-42", e.sessionId)
        assertEquals("lastAnswerAtMs tracks the LATEST tap", 4_000L, e.lastAnswerAtMs)
    }

    // ─────────── accumulation + process-death restore ───────────

    @Test
    fun `answers accumulate - later answers never erase earlier ones`() {
        var e = freshDraft()
        e = EvaluationAnswers.withGrade(e, "good", 2_000L)
        e = EvaluationAnswers.withCategoryRating(e, "battery", "excellent", 3_000L)
        e = EvaluationAnswers.withCategoryRating(e, "storage", "not_tested", 4_000L)
        e = EvaluationAnswers.withDefectToggled(e, "prior_repair", 5_000L)

        assertEquals("good", e.technicianGradeRaw)
        val ratings = EvaluationAnswers.parseCategoryRatings(e.categoryRatingsJson)
        assertEquals("excellent", ratings["battery"])
        assertEquals("not_tested", ratings["storage"])
        assertEquals(listOf("prior_repair"), EvaluationAnswers.parseDefects(e.visibleDefectsJson))
    }

    @Test
    fun `restore after process death keeps all persisted answers`() {
        // Session 1: technician answers twice; row persisted on each tap.
        var persisted = freshDraft()
        persisted = EvaluationAnswers.withGrade(persisted, "fair", 2_000L)
        persisted = EvaluationAnswers.withCategoryRating(persisted, "memory", "good", 3_000L)

        // Process death + recreation: bind() reads the SAME row back from
        // Room (simulated here by reusing the value object — Room stores
        // exactly these columns) and the technician answers once more.
        val restored = persisted.copy()
        val resumed = EvaluationAnswers.withDefectToggled(restored, "weak_battery", 9_000L)

        assertEquals("fair", resumed.technicianGradeRaw)
        assertEquals("good",
            EvaluationAnswers.parseCategoryRatings(resumed.categoryRatingsJson)["memory"])
        assertEquals(listOf("weak_battery"),
            EvaluationAnswers.parseDefects(resumed.visibleDefectsJson))
        assertEquals("original sessionId snapshot survives restore",
            "sess-snapshot-42", resumed.sessionId)
        assertEquals("original shownAtMs survives restore → honest durationMs",
            1_000L, resumed.shownAtMs)
        // And the payload built from the resumed row carries everything.
        val p = EvaluationPayloadBuilder.build(resumed)!!
        assertEquals("fair", p["technicianGradeRaw"])
        assertEquals(8_000L, p["durationMs"])
    }

    // ─────────── toggle semantics ───────────

    @Test
    fun `tapping the selected grade again returns to did-not-answer`() {
        var e = freshDraft()
        e = EvaluationAnswers.withGrade(e, "good", 2_000L)
        e = EvaluationAnswers.withGrade(e, "good", 3_000L)
        assertNull(e.technicianGradeRaw)
        assertFalse(EvaluationAnswers.hasAnyJudgement(e))
    }

    @Test
    fun `tapping the selected category rating removes the key - not not_tested`() {
        var e = freshDraft()
        e = EvaluationAnswers.withCategoryRating(e, "battery", "weak", 2_000L)
        e = EvaluationAnswers.withCategoryRating(e, "battery", "weak", 3_000L)
        val ratings = EvaluationAnswers.parseCategoryRatings(e.categoryRatingsJson)
        assertFalse("deselection = absence, never not_tested",
            ratings.containsKey("battery"))
    }

    @Test
    fun `none_noticed is mutually exclusive with concrete defects`() {
        var e = freshDraft()
        e = EvaluationAnswers.withDefectToggled(e, "water_damage", 2_000L)
        e = EvaluationAnswers.withDefectToggled(e, "prior_repair", 3_000L)
        e = EvaluationAnswers.withDefectToggled(e, "none_noticed", 4_000L)
        assertEquals(listOf("none_noticed"),
            EvaluationAnswers.parseDefects(e.visibleDefectsJson))
        e = EvaluationAnswers.withDefectToggled(e, "replaced_screen", 5_000L)
        assertEquals(listOf("replaced_screen"),
            EvaluationAnswers.parseDefects(e.visibleDefectsJson))
    }

    @Test
    fun `unknown vocabulary is rejected loudly`() {
        val e = freshDraft()
        runCatching { EvaluationAnswers.withGrade(e, "amazing", 1L) }
            .also { assertTrue(it.isFailure) }
        runCatching { EvaluationAnswers.withCategoryRating(e, "camera", "good", 1L) }
            .also { assertTrue("surface categories must be rejected", it.isFailure) }
        runCatching { EvaluationAnswers.withDefectToggled(e, "bent_frame", 1L) }
            .also { assertTrue(it.isFailure) }
    }
}
