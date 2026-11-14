package net.neuralcheck.presentation.viewmodel

import com.google.gson.Gson
import net.neuralcheck.data.remote.model.AiCategoryV7
import net.neuralcheck.data.remote.model.AiLetterCeilingReasonV7
import net.neuralcheck.domain.model.AiAnalysisResult
import net.neuralcheck.util.AiV7Presentation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Order 2026-09-23 (القرار ٧٣، البند ٤) — measures, does not assume,
 * whether `categoriesMissingLetterCeilingReason` and each category's
 * `letterCeilingReason`-classified state survive the REAL Room round-trip:
 * `Gson().toJson(ai)` exactly as `ViewModels.kt:742` (loadScan's save path)
 * writes `ScanScoreEntity.aiAnalysisJson`, then `Gson().fromJson(...,
 * AiAnalysisResult::class.java)` exactly as `ViewModels.kt:605-608`
 * (loadScan's fast-path cache read) reads it back. Same plain `Gson()` —
 * no custom TypeAdapter, no `serializeNulls()` — this is NOT a copy of
 * that logic re-implemented differently, it is the identical two-line
 * pattern, so a drift in ViewModels.kt's own Gson configuration would
 * still be caught here if this file used the same construction (it does).
 *
 * Two reports, per the order:
 *   - `emptyMissingListReport`: `categoriesMissingLetterCeilingReason =
 *     emptySet()` — the REAL shape every current-backend report has
 *     (confirmed: neuralcheck-backend @ e8cd67c always emits the key per
 *     category, so nothing is ever actually missing — see this task's
 *     report for that citation).
 *   - `nonEmptyMissingListReport`: a non-empty missing list — SYNTHETIC,
 *     since a real report never produces one today; labeled as such, not
 *     presented as observed.
 *
 * For each, every category's classification via
 * [AiV7Presentation.classifyLetterCeilingReason] — computed with the exact
 * same `declared` expression `ReportScreen.kt`'s `AiAnalysisSectionV7`
 * uses (`missingList ?: cats.keys`) — is compared BEFORE serialization
 * against AFTER deserialization. Per the order: if any category's state
 * changes, this test must fail loudly (rule 22/14 — "توقّف وأبلغ"), not be
 * adjusted to pass.
 */
class AiAnalysisResultRoomRoundTripTest {

    private val gson = Gson()

    /** Mirrors `ReportScreen.kt`'s `AiAnalysisSectionV7` exactly:
     *  `ai.categoriesMissingLetterCeilingReason ?: cats.keys`. */
    private fun classifyEachCategory(ai: AiAnalysisResult): Map<String, AiV7Presentation.LetterCeilingReasonKind> {
        val cats = ai.categories ?: return emptyMap()
        val missing = ai.categoriesMissingLetterCeilingReason ?: cats.keys
        return cats.mapValues { (key, cat) ->
            val declared = key !in missing
            AiV7Presentation.classifyLetterCeilingReason(declared, cat.letterCeilingReason)
        }
    }

    private fun buildCategories(): Map<String, AiCategoryV7> = mapOf(
        // NO_CEILING: key present in the report (not in any missing list),
        // letterCeilingReason itself null — the real "no gate ceilinged
        // this category" answer.
        "cpu" to AiCategoryV7(state = "MEASURED", letter = "A", letterCeilingReason = null),
        // PRESENT_WITH_TEXT: a real non-null ceiling object with text.
        "gpu" to AiCategoryV7(
            state = "PARTIAL",
            letter = "B",
            letterCeilingReason = AiLetterCeilingReasonV7(
                name = "LOW_CATEGORY_COVERAGE",
                ceilingLetter = "B",
                text = "تغطية منخفضة — اختباران من خمسة، وكلاهما اختبار تعريف لا أداء"
            )
        ),
        // PRESENT_NO_TEXT: a real object, but its own text is null — the
        // one state with no real sample anywhere (see V7GoldenSampleTest's
        // sibling test), kept here for round-trip coverage completeness.
        "storage" to AiCategoryV7(
            state = "MEASURED",
            letter = "C",
            letterCeilingReason = AiLetterCeilingReasonV7(name = "LOW_CATEGORY_COVERAGE", ceilingLetter = "C", text = null)
        )
    )

    @Test
    fun `report with an empty missing-list - the real current-backend shape - every category's classified state survives the Room round-trip unchanged`() {
        val before = AiAnalysisResult(
            pipeline = "v7_findings_based",
            categories = buildCategories(),
            categoriesMissingLetterCeilingReason = emptySet()
        )
        val beforeStates = classifyEachCategory(before)

        // The exact ViewModels.kt:742 write / :605-608 read pattern.
        val json = gson.toJson(before)
        val after = gson.fromJson(json, AiAnalysisResult::class.java)
        val afterStates = classifyEachCategory(after)

        assertEquals(
            "categoriesMissingLetterCeilingReason must round-trip as the same empty (not null) Set",
            emptySet<String>(),
            after.categoriesMissingLetterCeilingReason
        )
        assertEquals(
            "every category's classified letterCeilingReason state must be IDENTICAL before and after the Room round-trip — a change here means Gson's null-field-skipping (see class doc) broke the presence signal, and this test must not be adjusted to pass; stop and report the line",
            beforeStates,
            afterStates
        )
        // Expected per the order's own hypothesis, MEASURED not assumed:
        // NO_CEILING (cpu) / PRESENT_WITH_TEXT (gpu) / PRESENT_NO_TEXT
        // (storage) — the actual classify() calls above are the real
        // measurement; this is just naming what beforeStates/afterStates
        // are expected to already agree on for readability.
        assertEquals(AiV7Presentation.LetterCeilingReasonKind.NO_CEILING, afterStates["cpu"])
        assertEquals(AiV7Presentation.LetterCeilingReasonKind.PRESENT_WITH_TEXT, afterStates["gpu"])
        assertEquals(AiV7Presentation.LetterCeilingReasonKind.PRESENT_NO_TEXT, afterStates["storage"])
    }

    @Test
    fun `SYNTHETIC - report with a non-empty missing-list - no real backend report produces this today - every category's classified state still survives the Room round-trip unchanged`() {
        // SYNTHETIC per the order — a real current-backend report never
        // has a non-empty categoriesMissingLetterCeilingReason (e8cd67c
        // always emits the key per category). Built only to exercise this
        // second shape's round-trip, not presented as observed.
        val before = AiAnalysisResult(
            pipeline = "v7_findings_based",
            categories = buildCategories(),
            categoriesMissingLetterCeilingReason = setOf("cpu") // synthetic: cpu marked undeclared
        )
        val beforeStates = classifyEachCategory(before)

        val json = gson.toJson(before)
        val after = gson.fromJson(json, AiAnalysisResult::class.java)
        val afterStates = classifyEachCategory(after)

        assertEquals(
            "categoriesMissingLetterCeilingReason must round-trip with the same single entry",
            setOf("cpu"),
            after.categoriesMissingLetterCeilingReason
        )
        assertEquals(
            "every category's classified letterCeilingReason state must be IDENTICAL before and after the Room round-trip",
            beforeStates,
            afterStates
        )
        assertEquals(AiV7Presentation.LetterCeilingReasonKind.NOT_DECLARED, afterStates["cpu"])
        assertEquals(AiV7Presentation.LetterCeilingReasonKind.PRESENT_WITH_TEXT, afterStates["gpu"])
        assertEquals(AiV7Presentation.LetterCeilingReasonKind.PRESENT_NO_TEXT, afterStates["storage"])
    }
}
