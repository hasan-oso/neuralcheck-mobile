package net.neuralcheck.evaluation

import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity
import net.neuralcheck.domain.evaluation.EvaluationAnswers
import net.neuralcheck.domain.evaluation.EvaluationPayloadBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * feat/technician-evaluation — binding wire-contract rules
 * (feat/session-evaluation-contract):
 *  - unanswered = key DROPPED (never null / "" / not_tested)
 *  - not_tested is a sent VALUE ("أجاب: لم أفحصه") ≠ absence
 *  - zero-judgement payloads are never built (server rejects them)
 *  - technicianName/ownerId/agreement/errorMagnitude never appear
 */
class EvaluationPayloadBuilderTest {

    private fun row(
        grade: String? = null,
        ratingsJson: String = "{}",
        defectsJson: String = "[]",
        shownAt: Long = 10_000L,
        lastAnswerAt: Long? = 55_000L
    ) = SessionEvaluationEntity(
        scanId = "scan-1",
        sessionId = "sess-1",
        technicianGradeRaw = grade,
        categoryRatingsJson = ratingsJson,
        visibleDefectsJson = defectsJson,
        shownAtMs = shownAt,
        lastAnswerAtMs = lastAnswerAt
    )

    // ─────────── dropping unanswered keys ───────────

    @Test
    fun `grade-only payload carries no category or defect keys`() {
        val p = EvaluationPayloadBuilder.build(row(grade = "good"))!!
        assertEquals("good", p["technicianGradeRaw"])
        assertFalse("unanswered categories must be DROPPED, not sent",
            p.containsKey("categoryRatings"))
        assertFalse("unanswered defects must be DROPPED, not sent",
            p.containsKey("visibleDefects"))
        assertTrue(p.containsKey("scanId"))
        assertTrue(p.containsKey("sessionId"))
    }

    @Test
    fun `categories-only payload drops the grade key entirely`() {
        val p = EvaluationPayloadBuilder.build(
            row(ratingsJson = """{"battery":"weak"}""")
        )!!
        assertFalse("null grade must be an ABSENT key, never null",
            p.containsKey("technicianGradeRaw"))
        @Suppress("UNCHECKED_CAST")
        val ratings = p["categoryRatings"] as Map<String, String>
        assertEquals("weak", ratings["battery"])
        assertEquals(1, ratings.size)
    }

    @Test
    fun `no value in the payload is ever null or empty string`() {
        val p = EvaluationPayloadBuilder.build(
            row(grade = "excellent", defectsJson = """["prior_repair"]""")
        )!!
        p.values.forEach { v ->
            assertNotNull(v)
            if (v is String) assertTrue(v.isNotEmpty())
        }
    }

    // ─────────── not_tested is an answer, absence is not ───────────

    @Test
    fun `not_tested is sent as a value while unanswered categories stay absent`() {
        val p = EvaluationPayloadBuilder.build(
            row(ratingsJson = """{"storage":"not_tested","battery":"good"}""")
        )!!
        @Suppress("UNCHECKED_CAST")
        val ratings = p["categoryRatings"] as Map<String, String>
        assertEquals("أجاب: لم أفحصه — تُرسَل كقيمة", "not_tested", ratings["storage"])
        assertEquals("good", ratings["battery"])
        assertFalse("cpu_performance was never answered → key absent",
            ratings.containsKey("cpu_performance"))
        assertFalse("memory was never answered → key absent",
            ratings.containsKey("memory"))
    }

    // ─────────── empty-judgement guard ───────────

    @Test
    fun `zero-judgement row builds no payload at all`() {
        assertNull(EvaluationPayloadBuilder.build(row()))
    }

    @Test
    fun `missing mandatory ids build no payload`() {
        assertNull(EvaluationPayloadBuilder.build(
            row(grade = "good").copy(sessionId = "")
        ))
        assertNull(EvaluationPayloadBuilder.build(
            row(grade = "good").copy(scanId = "")
        ))
    }

    @Test
    fun `a single defect chip is enough to build a payload`() {
        val p = EvaluationPayloadBuilder.build(
            row(defectsJson = """["none_noticed"]""")
        )
        assertNotNull(p)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("none_noticed"), p!!["visibleDefects"] as List<String>)
    }

    // ─────────── forbidden keys ───────────

    @Test
    fun `forbidden keys never appear in a full payload`() {
        val p = EvaluationPayloadBuilder.build(
            row(
                grade = "fair",
                ratingsJson = """{"battery":"weak","memory":"not_tested"}""",
                defectsJson = """["weak_battery","water_damage"]"""
            )
        )!!
        EvaluationPayloadBuilder.FORBIDDEN_KEYS.forEach { forbidden ->
            assertFalse("$forbidden must NEVER be sent", p.containsKey(forbidden))
        }
        // Exhaustive allowed-key check — anything new must be a conscious choice.
        assertTrue(p.keys.all {
            it in setOf("scanId", "sessionId", "technicianGradeRaw",
                "categoryRatings", "visibleDefects", "durationMs")
        })
    }

    // ─────────── durationMs ───────────

    @Test
    fun `durationMs spans first question shown to last answer`() {
        val p = EvaluationPayloadBuilder.build(
            row(grade = "good", shownAt = 10_000L, lastAnswerAt = 55_000L)
        )!!
        assertEquals(45_000L, p["durationMs"])
    }

    @Test
    fun `durationMs dropped when no answer timestamp exists`() {
        val p = EvaluationPayloadBuilder.build(
            row(grade = "good", lastAnswerAt = null)
        )!!
        assertFalse(p.containsKey("durationMs"))
    }

    // ─────────── vocabulary sanity ───────────

    @Test
    fun `raw verbal scale is sent verbatim - no A B C D mapping client-side`() {
        for (grade in EvaluationAnswers.GRADES) {
            val p = EvaluationPayloadBuilder.build(row(grade = grade))!!
            assertEquals(grade, p["technicianGradeRaw"])
            assertFalse("client must never map to letter grades",
                p.values.any { it in listOf("A", "B", "C", "D") })
        }
    }
}
