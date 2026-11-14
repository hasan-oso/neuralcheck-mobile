package net.neuralcheck.domain.evaluation

import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity

/**
 * feat/technician-evaluation — builds the submitEvaluation wire payload
 * from a persisted row. Pure + unit-tested.
 *
 * Binding contract rules (feat/session-evaluation-contract):
 *  - scanId + sessionId are mandatory.
 *  - "did not answer" = the key is DROPPED. Never null, never "",
 *    never not_tested. The VALUE "not_tested" is a different attestation
 *    ("أجاب: لم أفحصه") and IS sent inside categoryRatings.
 *  - A payload with zero judgements must not be sent at all (the server
 *    rejects it with invalid-argument) — [build] returns null instead.
 *  - technicianName / ownerId / agreement / errorMagnitude are NEVER
 *    sent. gradeDistance is the server's own computation.
 *  - durationMs = lastAnswerAtMs - shownAtMs (first question shown →
 *    last answer); dropped when not computable.
 */
object EvaluationPayloadBuilder {

    val FORBIDDEN_KEYS = setOf("technicianName", "ownerId", "agreement", "errorMagnitude")

    /**
     * @return the callable payload, or null when the row carries no
     *         judgement (send nothing) or lacks the mandatory ids.
     */
    fun build(e: SessionEvaluationEntity): Map<String, Any>? {
        if (e.scanId.isBlank() || e.sessionId.isBlank()) return null

        val grade = e.technicianGradeRaw?.takeIf { it.isNotBlank() }
        val ratings = EvaluationAnswers.parseCategoryRatings(e.categoryRatingsJson)
        val defects = EvaluationAnswers.parseDefects(e.visibleDefectsJson)

        // Empty-judgement guard: the technician skipped everything →
        // nothing is sent (server would reject with invalid-argument).
        if (grade == null && ratings.isEmpty() && defects.isEmpty()) return null

        val payload = linkedMapOf<String, Any>(
            "scanId" to e.scanId,
            "sessionId" to e.sessionId
        )
        if (grade != null) payload["technicianGradeRaw"] = grade
        if (ratings.isNotEmpty()) payload["categoryRatings"] = ratings
        if (defects.isNotEmpty()) payload["visibleDefects"] = defects

        val duration = e.lastAnswerAtMs?.let { it - e.shownAtMs }
        if (duration != null && duration >= 0) payload["durationMs"] = duration

        return payload
    }
}
