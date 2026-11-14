package net.neuralcheck.domain.scoring

import net.neuralcheck.domain.model.AiAnalysisResult
import net.neuralcheck.domain.model.TestStatus

/**
 * Complete scoring result with 4-layer breakdown (ADR section 8).
 *
 * `aiAnalysis` and `error` are optional and only populated by the AiScoringEngine
 * (launch flavor). RuleBasedScoringEngine leaves them null — the PDF generator
 * already knows to fall back to local heuristics in that case.
 */
data class ScoringResult(
    /** 0.0-100.0. Null means "no score to show" — currently every V7
     *  findings-based response (`aiAnalysis?.pipeline ==
     *  AiPipeline.V7_FINDINGS_BASED`), which never carries `response.score`
     *  at all (order 2026-09-24، القرار ٧٦، البند ١): "لا يوجد بديل مباشر
     *  — لا يُعرض إطلاقاً (القرار ٦)". Was `Float` non-null with
     *  [net.neuralcheck.domain.scoring.AiScoringEngine] coercing the
     *  absence to `0f` — a buyer-visible fabricated "0%"
     *  (ReportScreen.kt's `GradeSummaryCard`) fixed by this type. Every
     *  reader was checked before this change (14+ call sites across
     *  domain/usecase, presentation/screen, presentation/viewmodel,
     *  service/sync — see this task's report for the tool-extracted
     *  list); [net.neuralcheck.data.local.db.entity.ScanScoreEntity.overallScore]
     *  mirrors this nullability (Room migration MIGRATION_5_6). */
    val overallScore: Float?,
    val overallGrade: String,              // "A", "B", "C", "D"
    val gradeDescription: String,          // Arabic description
    val gradeDescriptionTr: String = "",   // Turkish description
    val axisScores: Map<String, AxisScore>,
    val categoryScores: Map<String, CategoryScore>,
    val individualScores: Map<String, IndividualScore>,
    val criticalFailures: List<String>,    // Test IDs that failed critically
    val antiTamperPenalty: Float,
    val isIncomplete: Boolean,
    val scoringMethod: String,             // "rule_based_v1" or "ai_v1"
    val aiAnalysis: AiAnalysisResult? = null,
    // Backend-minted human-readable scan ID (e.g. "NC-A7K9M2"). Duplicated
    // here from [aiAnalysis.shortId] so consumers reading from the top-level
    // result (e.g. legacy report code paths or future caches) don't have to
    // unwrap the nested AI struct. Always reflects the same value as
    // `aiAnalysis?.shortId` for AI-scored results, and stays null for the
    // rule-based engine which doesn't talk to the backend.
    val shortId: String? = null,
    val error: String? = null,
    /**
     * fieldsVersion 69 (S-26, contract docs/FV68_CONTRACT.md section 7-h (d)): the server answered
     * `ALREADY_EXISTS` — it is STILL PROCESSING this scan's analysis. Not an error (so [error] is null):
     * the report shows «انتظر» with a retry instead of the error card. false = this is not that state.
     */
    val analysisInProgress: Boolean = false
)

data class AxisScore(
    val name: String,
    val nameAr: String,
    val nameTr: String = "",
    val score: Float,
    val grade: String
)

/**
 * `@JvmOverloads` + a default on every parameter (including [name]/
 * [grade]/[score]/[passCount]/[failCount]/[totalCount], which every real
 * Kotlin call site already supplies explicitly and always should) is NOT
 * about giving Kotlin callers permission to omit them — it exists so this
 * class gets a genuine public no-arg Java constructor.
 *
 * Empirically confirmed (not by reasoning about Gson's internals) that
 * this class specifically needed it: this project's Gson deserializes
 * this class via `Unsafe`/Objenesis whenever it has NO accessible no-arg
 * constructor — which used to be true here (six required parameters,
 * no `@JvmOverloads`) — and that path skips the constructor entirely, so
 * a JSON key absent from the source leaves that field genuinely null,
 * regardless of its Kotlin type or intended default. A JSON fixture
 * missing `name`/`grade` reproduced a real NullPointerException on
 * `.isNotBlank()` before this fix (net.neuralcheck.domain.model.
 * AiAnalysisResultGsonNullBypassTest, "domain CategoryScore survives name
 * and grade access..."). The two sibling classes flagged for the same
 * risk — AiAnalysisResult and AiCategoryScore (data/remote/model) — were
 * checked the same way and did NOT reproduce it (every one of their
 * constructor parameters already had a default, unlike this class before
 * this fix); see that same test file for the full empirical record.
 */
data class CategoryScore @JvmOverloads constructor(
    // Default is reachable ONLY through Gson's no-arg path above — every
    // real construction call site (AiScoringEngine.kt, RuleBasedScoringEngine.kt,
    // ViewModels.kt) passes this explicitly.
    val name: String = "",
    val nameAr: String = "",
    val nameTr: String = "",
    val score: Float = 0f,
    val grade: String = "?",
    val passCount: Int = 0,
    val failCount: Int = 0,
    val totalCount: Int = 0,
    // ── Unmeasured-category contract (backend fix, 2026-09) ──
    // Optional so the rule-based engine and every legacy consumer are
    // unaffected; populated only on the AI path, where the backend now
    // distinguishes "measured and failed" from "never measured".
    /** False when NOTHING in this category was measured. Null = unknown
     *  (rule-based engine / report written before the backend change). */
    val measured: Boolean? = null,
    /** Tests the catalogue declares for this category. NOTE: [totalCount]
     *  is what EXECUTED, not what was declared — gpu reads 2/2 while
     *  declaring 5. Null when unknown. */
    val declaredCount: Int? = null,
    /** The server's own count of tests excluded from the grade — the
     *  authoritative gap. Null on payloads predating the field, where the
     *  gap is derived from declaredCount - totalCount instead. */
    val excludedCount: Int? = null,
    /** Server-authored note for this category (reason it wasn't measured,
     *  or the AI's remark). Empty when the source engine has none. */
    val note: String = ""
)

data class IndividualScore(
    val testId: String,
    val score: Float,
    val status: TestStatus
)
