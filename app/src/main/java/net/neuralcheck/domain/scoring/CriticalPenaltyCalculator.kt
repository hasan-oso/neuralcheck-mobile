package net.neuralcheck.domain.scoring

import net.neuralcheck.domain.model.TestResult

/**
 * Calculates critical failure penalties (ADR 8.9).
 *
 * The canonical list of critical checks plus the per-check predicate live in
 * scoring_config.json ("critical_checks.checks[].condition"). Supported
 * condition syntax:
 *   - "score == 0"      : individual score is zero
 *   - "score <= N"      : individual score at or below N
 *   - "raw_value > N"   : parsed rawValue strictly greater than N
 *   - "raw_value >= N"  : parsed rawValue at or above N
 *
 * The raw-value predicates are important because interpolation can produce
 * non-zero scores near the failure edge; the ADR defines the critical
 * threshold on the raw measurement, not the interpolated score.
 */
object CriticalPenaltyCalculator {

    fun findCriticalFailures(
        individualScores: Map<String, IndividualScore>,
        results: List<TestResult>,
        config: ScoringConfigReader
    ): List<String> {
        val resultsById = results.associateBy { it.testId }
        val failures = mutableListOf<String>()

        for (check in config.getCriticalChecks()) {
            val score = individualScores[check.id] ?: continue
            val rawValue = resultsById[check.id]?.rawValue?.toFloatOrNull()
            if (evaluateCondition(check.condition, score.score, rawValue)) {
                failures.add(check.id)
            }
        }
        return failures
    }

    /**
     * Given `earlyStopReason` from the service, derive any additional critical
     * failures that should be force-injected. Per ADR 8.12 the stop reason
     * itself counts as a critical failure of the corresponding test.
     */
    fun criticalFailuresFromEarlyStop(earlyStopReason: String?): List<String> {
        if (earlyStopReason.isNullOrBlank()) return emptyList()
        return when (earlyStopReason) {
            "battery_temp_critical", "cpu_temp_critical" -> listOf("T-01")
            "excessive_drain" -> listOf("B-06")
            else -> emptyList()
        }
    }

    fun applyPenalty(
        overallScore: Float,
        criticalFailures: List<String>,
        config: ScoringConfigReader
    ): Float {
        if (criticalFailures.isEmpty()) return overallScore

        val penaltyPerFailure = config.criticalPenaltyPerFailure
        val maxPenalty = config.maxCriticalPenalty
        val gradeCap = config.criticalGradeCap

        val totalPenalty = minOf(criticalFailures.size * penaltyPerFailure, maxPenalty)
        val penalizedScore = overallScore - totalPenalty

        // Apply grade cap — score cannot exceed criticalGradeCap if any critical failure exists
        return minOf(penalizedScore, gradeCap).coerceAtLeast(0f)
    }

    private val SCORE_EQ = Regex("""^\s*score\s*==\s*(-?\d+(?:\.\d+)?)\s*$""")
    private val SCORE_LE = Regex("""^\s*score\s*<=\s*(-?\d+(?:\.\d+)?)\s*$""")
    private val RAW_GT = Regex("""^\s*raw_value\s*>\s*(-?\d+(?:\.\d+)?)\s*$""")
    private val RAW_GE = Regex("""^\s*raw_value\s*>=\s*(-?\d+(?:\.\d+)?)\s*$""")

    internal fun evaluateCondition(condition: String, score: Float, rawValue: Float?): Boolean {
        SCORE_EQ.matchEntire(condition)?.let {
            return score == it.groupValues[1].toFloat()
        }
        SCORE_LE.matchEntire(condition)?.let {
            return score <= it.groupValues[1].toFloat()
        }
        RAW_GT.matchEntire(condition)?.let {
            val threshold = it.groupValues[1].toFloat()
            return rawValue != null && rawValue > threshold
        }
        RAW_GE.matchEntire(condition)?.let {
            val threshold = it.groupValues[1].toFloat()
            return rawValue != null && rawValue >= threshold
        }
        // Fallback (unrecognised predicate): treat zero score as critical, the
        // same default the engine used before condition parsing was added.
        return score == 0f
    }
}
