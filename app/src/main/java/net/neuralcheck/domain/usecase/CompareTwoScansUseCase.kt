package net.neuralcheck.domain.usecase

import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.data.repository.TestResultRepository
import net.neuralcheck.data.repository.ScoreRepository
import net.neuralcheck.domain.model.TestStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.domain.scoring.CategoryScore
import javax.inject.Inject

/**
 * Compares two scans (pre-repair vs post-repair) and calculates
 * per-test deltas and overall improvement score (ADR 8.19).
 */
class CompareTwoScansUseCase @Inject constructor(
    private val scanRepository: ScanRepository,
    private val testResultRepository: TestResultRepository,
    private val scoreRepository: ScoreRepository
) {
    private val gson = Gson()

    /**
     * Compare two scans by their IDs.
     * @param beforeScanId pre-repair scan UUID
     * @param afterScanId post-repair scan UUID
     * @return ComparisonResult or null if either scan not found
     */
    suspend fun execute(beforeScanId: String, afterScanId: String): ComparisonResult? {
        // Existence check for both scans — return null if either is missing.
        if (scanRepository.getScanById(beforeScanId) == null) return null
        if (scanRepository.getScanById(afterScanId) == null) return null

        val beforeResults = testResultRepository.getResults(beforeScanId)
        val afterResults = testResultRepository.getResults(afterScanId)

        val beforeScore = scoreRepository.getByScanId(beforeScanId)
        val afterScore = scoreRepository.getByScanId(afterScanId)

        // Build per-test comparison
        val testComparisons = mutableListOf<TestComparison>()
        val beforeMap = beforeResults.associateBy { it.testId }
        val afterMap = afterResults.associateBy { it.testId }

        val allTestIds = (beforeMap.keys + afterMap.keys).toSet()
        for (testId in allTestIds.sorted()) {
            val before = beforeMap[testId]
            val after = afterMap[testId]

            val beforeStatus = before?.let { TestStatus.fromString(it.status) }
            val afterStatus = after?.let { TestStatus.fromString(it.status) }
            val beforeScoreVal = before?.score
            val afterScoreVal = after?.score

            val change = when {
                beforeScoreVal != null && afterScoreVal != null -> {
                    val delta = afterScoreVal - beforeScoreVal
                    when {
                        delta > 5f -> ChangeDirection.IMPROVED
                        delta < -5f -> ChangeDirection.DEGRADED
                        else -> ChangeDirection.UNCHANGED
                    }
                }
                beforeStatus != null && afterStatus != null -> {
                    when {
                        afterStatus == TestStatus.PASS && beforeStatus != TestStatus.PASS -> ChangeDirection.IMPROVED
                        afterStatus != TestStatus.PASS && beforeStatus == TestStatus.PASS -> ChangeDirection.DEGRADED
                        else -> ChangeDirection.UNCHANGED
                    }
                }
                else -> ChangeDirection.UNCHANGED
            }

            testComparisons.add(TestComparison(
                testId = testId,
                componentName = before?.componentName ?: after?.componentName ?: testId,
                categoryName = before?.categoryName ?: after?.categoryName ?: "",
                beforeStatus = beforeStatus,
                afterStatus = afterStatus,
                beforeScore = beforeScoreVal,
                afterScore = afterScoreVal,
                scoreDelta = if (beforeScoreVal != null && afterScoreVal != null)
                    afterScoreVal - beforeScoreVal else null,
                change = change
            ))
        }

        // Category-level comparison
        val beforeCategories: Map<String, CategoryScore> = beforeScore?.categoryScoresJson?.let {
            try {
                val type = object : TypeToken<Map<String, CategoryScore>>() {}.type
                gson.fromJson<Map<String, CategoryScore>>(it, type)
            } catch (e: Exception) { null }
        } ?: emptyMap()

        val afterCategories: Map<String, CategoryScore> = afterScore?.categoryScoresJson?.let {
            try {
                val type = object : TypeToken<Map<String, CategoryScore>>() {}.type
                gson.fromJson<Map<String, CategoryScore>>(it, type)
            } catch (e: Exception) { null }
        } ?: emptyMap()

        val categoryComparisons = (beforeCategories.keys + afterCategories.keys).toSet().map { cat ->
            val bScore = beforeCategories[cat]?.score ?: 0f
            val aScore = afterCategories[cat]?.score ?: 0f
            CategoryComparison(
                categoryName = cat,
                beforeScore = bScore,
                afterScore = aScore,
                delta = aScore - bScore
            )
        }

        // Overall — order 2026-09-24 (القرار ٧٦، البند ١): "المقارنة لا
        // تقارن درجة غائبة". `?.overallScore` was already null-safe for a
        // MISSING score ROW (`beforeScore`/`afterScore: ScanScoreEntity?`);
        // `?: 0f` additionally coerced a present row's now-nullable
        // `overallScore` FIELD (a V7 report has a row, just no score) into
        // a fabricated 0 — which would make a real V7 scan look like the
        // worst possible score in a before/after comparison instead of
        // "nothing to compare". Removed: both stay null when absent, and
        // the delta is null (not computed) unless BOTH sides have a real
        // score to compare.
        val overallBefore = beforeScore?.overallScore
        val overallAfter = afterScore?.overallScore

        return ComparisonResult(
            beforeScanId = beforeScanId,
            afterScanId = afterScanId,
            beforeOverallScore = overallBefore,
            afterOverallScore = overallAfter,
            overallDelta = if (overallBefore != null && overallAfter != null) overallAfter - overallBefore else null,
            beforeGrade = beforeScore?.overallGrade ?: "—",
            afterGrade = afterScore?.overallGrade ?: "—",
            testComparisons = testComparisons,
            categoryComparisons = categoryComparisons,
            improvedCount = testComparisons.count { it.change == ChangeDirection.IMPROVED },
            degradedCount = testComparisons.count { it.change == ChangeDirection.DEGRADED },
            unchangedCount = testComparisons.count { it.change == ChangeDirection.UNCHANGED }
        )
    }
}

data class ComparisonResult(
    val beforeScanId: String,
    val afterScanId: String,
    // Nullable per the order 2026-09-24 (القرار ٧٦، البند ١) — "المقارنة لا
    // تقارن درجة غائبة". null means one or both scans has no score to
    // compare (V7), not "no change". Note: this class currently has no
    // caller anywhere in app/src/main (checked) — CompareTwoScansUseCase
    // is unreferenced dead code — so this type change has zero downstream
    // UI impact today, but is fixed the same way the live call sites are.
    val beforeOverallScore: Float?,
    val afterOverallScore: Float?,
    val overallDelta: Float?,
    val beforeGrade: String,
    val afterGrade: String,
    val testComparisons: List<TestComparison>,
    val categoryComparisons: List<CategoryComparison>,
    val improvedCount: Int,
    val degradedCount: Int,
    val unchangedCount: Int
)

data class TestComparison(
    val testId: String,
    val componentName: String,
    val categoryName: String,
    val beforeStatus: TestStatus?,
    val afterStatus: TestStatus?,
    val beforeScore: Float?,
    val afterScore: Float?,
    val scoreDelta: Float?,
    val change: ChangeDirection
)

data class CategoryComparison(
    val categoryName: String,
    val beforeScore: Float,
    val afterScore: Float,
    val delta: Float
)

enum class ChangeDirection {
    IMPROVED,
    DEGRADED,
    UNCHANGED
}
