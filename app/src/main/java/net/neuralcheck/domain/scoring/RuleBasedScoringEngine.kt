package net.neuralcheck.domain.scoring

import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 4-layer rule-based scoring engine implementing ADR 8.3-8.8.
 *
 * Layer 1: Individual test scores (0-100 each)
 * Layer 2: Category scores (weighted average)
 * Layer 3: Axis scores (weighted average of categories)
 * Layer 4: Overall score (weighted average of axes)
 *
 * Plus: critical penalties, anti-tamper adjustments, incomplete scan cap.
 *
 * Exposed as a @Singleton so [AiScoringEngine] (launch flavor) can inject it
 * directly to produce a rule-based baseline before decorating with AI output.
 */
@Singleton
class RuleBasedScoringEngine @Inject constructor(
    private val config: ScoringConfigReader
) : ScoringEngine {

    override suspend fun calculateScore(scanReport: ScanReport): ScoringResult {
        val isIncomplete = !scanReport.scanMetadata.isComplete
        val earlyStopReason = scanReport.scanMetadata.earlyStopReason

        // Layer 1: Individual test scores
        val individualScores = calculateIndividualScores(scanReport.functionalResults)

        // Layer 2: Category scores — T-03 (cooldown rate) is skipped when the
        // cooldown phase never ran (ADR 8.12).
        val excludedTests = if (isIncomplete) setOf("T-03") else emptySet()
        val categoryScores = calculateCategoryScores(individualScores, excludedTests)

        // Layer 3: Axis scores
        val axisScores = calculateAxisScores(categoryScores)

        // Layer 4: Overall score
        var overallScore = calculateOverallScore(axisScores)

        // Critical failures from scored tests + from the early-stop reason.
        val scoredFailures = CriticalPenaltyCalculator.findCriticalFailures(
            individualScores, scanReport.functionalResults, config
        )
        val stopFailures = CriticalPenaltyCalculator.criticalFailuresFromEarlyStop(earlyStopReason)
        val criticalFailures = (scoredFailures + stopFailures).distinct()

        if (criticalFailures.isNotEmpty()) {
            overallScore = CriticalPenaltyCalculator.applyPenalty(overallScore, criticalFailures, config)
        }

        // Apply anti-tamper penalty
        val tamperPenalty = AntiTamperDetector.calculatePenalty(
            scanReport.antiTamperFlags,
            scanReport.scanMetadata.startBatteryTemp
        )
        overallScore -= tamperPenalty

        // Apply incomplete scan cap
        if (isIncomplete) {
            overallScore = minOf(overallScore, config.incompleteScanCap)
        }

        overallScore = overallScore.coerceIn(0f, 100f)

        val gradeInfo = config.getGradeForScore(overallScore)

        return ScoringResult(
            overallScore = overallScore,
            overallGrade = gradeInfo.letter,
            gradeDescription = gradeInfo.descriptionAr,
            gradeDescriptionTr = gradeInfo.descriptionTr,
            axisScores = axisScores,
            categoryScores = categoryScores,
            individualScores = individualScores,
            criticalFailures = criticalFailures,
            antiTamperPenalty = tamperPenalty,
            isIncomplete = isIncomplete,
            scoringMethod = "rule_based_v1"
        )
    }

    // --- Layer 1: Individual Test Scores ---

    private fun calculateIndividualScores(results: List<TestResult>): Map<String, IndividualScore> {
        val scores = mutableMapOf<String, IndividualScore>()
        for (result in results) {
            val score = calculateIndividualScore(result)
            scores[result.testId] = score
        }
        return scores
    }

    private fun calculateIndividualScore(result: TestResult): IndividualScore {
        // If status is UNAVAILABLE or NOT_TESTED, exclude from scoring
        if (result.status == TestStatus.UNAVAILABLE || result.status == TestStatus.NOT_TESTED) {
            return IndividualScore(result.testId, -1f, result.status)
        }

        // If the test already has a score, use it
        if (result.score != null) {
            return IndividualScore(result.testId, result.score.coerceIn(0f, 100f), result.status)
        }

        // Try to compute score from thresholds
        val thresholdType = config.getThresholdType(result.testId)
        val score = when (thresholdType) {
            "interpolated" -> {
                val rawVal = result.rawValue?.toFloatOrNull()
                if (rawVal != null) {
                    interpolateScore(result.testId, rawVal)
                } else {
                    statusToDefaultScore(result.status)
                }
            }
            "binary" -> {
                when (result.status) {
                    TestStatus.PASS -> 100f
                    TestStatus.FAIL -> 0f
                    TestStatus.WARNING -> 50f
                    else -> statusToDefaultScore(result.status)
                }
            }
            "info" -> {
                val cfg = config.getThresholdConfig(result.testId)
                cfg?.get("default_score")?.let {
                    if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asFloat else 100f
                } ?: 100f
            }
            "special" -> statusToDefaultScore(result.status)
            else -> statusToDefaultScore(result.status)
        }

        return IndividualScore(result.testId, score.coerceIn(0f, 100f), result.status)
    }

    private fun interpolateScore(testId: String, rawValue: Float): Float {
        val points = config.getInterpolatedPoints(testId) ?: return 50f
        if (points.isEmpty()) return 50f

        val direction = config.getDirection(testId)
        val sortedPoints = if (direction == "higher_better") {
            points.sortedByDescending { it.first }
        } else {
            points.sortedBy { it.first }
        }

        // Value beyond best threshold → max score
        if (direction == "higher_better") {
            if (rawValue >= sortedPoints.first().first) return sortedPoints.first().second
            if (rawValue <= sortedPoints.last().first) return sortedPoints.last().second
        } else {
            if (rawValue <= sortedPoints.first().first) return sortedPoints.first().second
            if (rawValue >= sortedPoints.last().first) return sortedPoints.last().second
        }

        // Linear interpolation between adjacent points
        for (i in 0 until sortedPoints.size - 1) {
            val (x1, y1) = sortedPoints[i]
            val (x2, y2) = sortedPoints[i + 1]
            val inRange = if (direction == "higher_better") {
                rawValue in x2..x1
            } else {
                rawValue in x1..x2
            }
            if (inRange) {
                val ratio = (rawValue - x1) / (x2 - x1)
                return y1 + ratio * (y2 - y1)
            }
        }
        return 50f
    }

    private fun statusToDefaultScore(status: TestStatus): Float = when (status) {
        TestStatus.PASS -> 100f
        TestStatus.FAIL -> 0f
        TestStatus.WARNING -> 50f
        TestStatus.ERROR -> 25f
        TestStatus.UNAVAILABLE -> -1f  // excluded
        TestStatus.NOT_TESTED -> -1f   // excluded
    }

    // --- Layer 2: Category Scores ---

    private fun calculateCategoryScores(
        individualScores: Map<String, IndividualScore>,
        excludedTests: Set<String> = emptySet()
    ): Map<String, CategoryScore> {
        val categories = config.getAllCategories()
        val categoryScores = mutableMapOf<String, CategoryScore>()

        for (category in categories) {
            val weights = config.getCategoryWeights(category)
            var weightedSum = 0f
            var totalWeight = 0
            var passCount = 0
            var failCount = 0
            var totalCount = 0

            for ((testId, weight) in weights) {
                if (testId in excludedTests) continue
                val score = individualScores[testId] ?: continue
                if (score.score < 0) continue // Excluded (UNAVAILABLE/NOT_TESTED)

                weightedSum += score.score * weight
                totalWeight += weight
                totalCount++
                when (score.status) {
                    TestStatus.PASS -> passCount++
                    TestStatus.FAIL, TestStatus.ERROR -> failCount++
                    else -> {}
                }
            }

            val catScore = if (totalWeight > 0) weightedSum / totalWeight else 0f
            val grade = config.getGradeForScore(catScore)

            categoryScores[category] = CategoryScore(
                name = category,
                nameAr = getCategoryNameAr(category),
                nameTr = getCategoryNameTr(category),
                score = catScore,
                grade = grade.letter,
                passCount = passCount,
                failCount = failCount,
                totalCount = totalCount
            )
        }
        return categoryScores
    }

    // --- Layer 3: Axis Scores ---

    private fun calculateAxisScores(categoryScores: Map<String, CategoryScore>): Map<String, AxisScore> {
        val axes = config.getAllAxes()
        val axisScores = mutableMapOf<String, AxisScore>()

        for (axis in axes) {
            val axisCategories = config.getAxisCategories(axis)
            var weightedSum = 0f
            var totalWeight = 0

            for ((category, weight) in axisCategories) {
                val catScore = categoryScores[category] ?: continue
                weightedSum += catScore.score * weight
                totalWeight += weight
            }

            val axScore = if (totalWeight > 0) weightedSum / totalWeight else 0f
            val grade = config.getGradeForScore(axScore)

            axisScores[axis] = AxisScore(
                name = axis,
                nameAr = config.getAxisNameAr(axis),
                nameTr = config.getAxisNameTr(axis),
                score = axScore,
                grade = grade.letter
            )
        }
        return axisScores
    }

    // --- Layer 4: Overall Score ---

    private fun calculateOverallScore(axisScores: Map<String, AxisScore>): Float {
        val weights = config.getOverallWeights()
        var weightedSum = 0f
        var totalWeight = 0

        for ((axis, weight) in weights) {
            val axScore = axisScores[axis] ?: continue
            weightedSum += axScore.score * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0f
    }

    // --- Helpers ---

    private fun getCategoryNameAr(category: String): String = when (category) {
        "battery" -> "البطارية"
        "cpu" -> "المعالج"
        "gpu" -> "معالج الرسومات"
        "storage" -> "التخزين"
        "thermal" -> "الحرارة"
        "memory" -> "الذاكرة"
        "cellular" -> "الشبكة الخلوية"
        "wireless" -> "الاتصال اللاسلكي"
        "gps" -> "نظام تحديد الموقع"
        "sensors" -> "المستشعرات"
        "camera" -> "الكاميرا"
        "display" -> "الشاشة"
        "security" -> "الأمان"
        // Bug 5 follow-up (2026-05-17): added so the rule-based engine
        // (datacollect flavor) shows nice labels if/when a CategoryScore
        // for these new categories ever gets emitted. scoring_config.json
        // doesn't include them in any axis today, so a CategoryScore for
        // audio/input is unlikely under the current rule-based path —
        // these are forward-compat labels.
        "audio" -> "الصوت"
        "input" -> "الإدخال"
        else -> category
    }

    private fun getCategoryNameTr(category: String): String = when (category) {
        "battery" -> "Pil"
        "cpu" -> "İşlemci"
        "gpu" -> "Grafik İşlemci"
        "storage" -> "Depolama"
        "thermal" -> "Sıcaklık"
        "memory" -> "Bellek"
        "cellular" -> "Hücresel Ağ"
        "wireless" -> "Kablosuz Bağlantı"
        "gps" -> "Konum (GPS)"
        "sensors" -> "Sensörler"
        "camera" -> "Kamera"
        "display" -> "Ekran"
        "security" -> "Güvenlik"
        "audio" -> "Ses"
        "input" -> "Giriş"
        else -> category
    }
}
