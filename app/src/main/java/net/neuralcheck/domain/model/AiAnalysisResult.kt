package net.neuralcheck.domain.model

import net.neuralcheck.data.remote.model.AiCategoryScore
import net.neuralcheck.data.remote.model.AiCategoryV7
import net.neuralcheck.data.remote.model.AiNarrativeV7
import net.neuralcheck.data.remote.model.AiOverallV7
import net.neuralcheck.data.remote.model.AiPipeline
import net.neuralcheck.data.remote.model.BuyerAdvice
import net.neuralcheck.data.remote.model.ChangeAnalysis
import net.neuralcheck.data.remote.model.ExpectedPerformance
import net.neuralcheck.data.remote.model.PredictedProblem

/**
 * Human-readable analysis of a scan, either produced by a remote AI endpoint
 * or synthesised locally from rule-based heuristics in [net.neuralcheck.util.ReportPdfGenerator].
 *
 * Null inside a report means "no AI layer available" — the generator falls back to
 * rule-derived [PredictedIssue]s so the user still sees actionable guidance.
 *
 * The trailing fields (grade, score, predictedProblems, categoryNotes,
 * categoryScores) are only populated when the analysis comes from the
 * backend callable. The rule-based local fallback leaves them null/empty,
 * and existing call sites (ReportPdfGenerator.buildLocalAnalysis) keep
 * working unchanged because the new parameters all have defaults.
 *
 * Every field carries a default so the AiScoringEngine mapping layer can
 * freely coalesce nulls from a partial backend payload — earlier versions
 * required `summary`/`details`/`verdict`/`issues` explicitly and crashed at
 * `<init>` when the backend omitted any of them.
 *
 * `categoryScores` mirrors the raw AI payload so the UI can surface the
 * per-category note alongside the numeric card without having to reach back
 * into the remote model.
 */
data class AiAnalysisResult(
    val summary: String = "",
    val details: String = "",
    val verdict: String = "",
    val issues: List<PredictedIssue> = emptyList(),
    val grade: String? = null,
    val score: Int? = null,
    val predictedProblems: List<PredictedProblem> = emptyList(),
    val categoryNotes: Map<String, String> = emptyMap(),
    val categoryScores: Map<String, AiCategoryScore>? = null,
    // Backend-minted human-readable scan ID (e.g. "NC-A7K9M2"). Optional
    // because a) the server only returns it on AI-scored reports (not the
    // datacollect rule-based path) and b) older reports saved before the
    // backend introduced the field don't have one. Consumers fall back to
    // the raw scanId UUID when this is null.
    val shortId: String? = null,
    /** AI-authored root-cause hypothesis. Null when the backend's confidence
     *  is low or for rule-based local fallback reports. Surfaced as its own
     *  card on screen + PDF, between issues and predicted problems. */
    val rootCause: String? = null,
    /** Per-persona usage advice (casual / gamer / photographer). Whole
     *  block is null when the backend didn't ship one; individual persona
     *  fields can also be null inside the block. */
    val buyerAdvice: BuyerAdvice? = null,
    /** Backend identifier of the model that produced this verdict (e.g.
     *  "gemini-1.5-pro-latest"). Rendered as a small footer credit so we
     *  can correlate verdicts across model upgrades. */
    val aiModel: String? = null,
    /** True when this scan was the user's complimentary free-trial — used
     *  by ReportScreen to hide PDF/QR/Share and show a "subscribe to unlock"
     *  banner instead. Defaults to false so legacy reports keep the full
     *  bottom bar. */
    val isFreeTrial: Boolean = false,
    /** Backend-resolved consumer device name (e.g. "Samsung Galaxy S24
     *  Ultra"). Wins over the local DeviceName map when present because
     *  the server's catalog is fuller and more current. Null on legacy
     *  reports the backend hasn't enriched yet. */
    val deviceMarketingName: String? = null,
    /** V2 — AI's expected-performance reasoning (collapsed in UI by
     *  default). Null when the model declined to commit. */
    val expectedPerformance: ExpectedPerformance? = null,
    /** V2 — comparison vs prior scan when one exists. Null on first
     *  scans. */
    val changeAnalysis: ChangeAnalysis? = null,
    // ── V7 findings-based pipeline (Phase 3-a) ──
    // Straight pass-through from AiAnalysisResponse — same pattern as
    // expectedPerformance/changeAnalysis above (no transformation, the
    // read layer just carries the field through). Deliberately NOT
    // merged into summary/details/verdict/grade/score/categoryScores
    // above: those feed ReportScreen/ReportPdfGenerator, and Phase 3-a
    // (this comment's original phase) read V7 completely WITHOUT building
    // a display on it (see the Phase 3-a report's boundary section) — a V7
    // result rendered as an empty V6-shaped report at that point, the data
    // captured here and round-tripped through the `aiAnalysisJson` Room
    // column (see [isComplete]) but not yet shown anywhere.
    //
    // Status as of Phase 3-c: ReportScreen wired its V7 display in Phase
    // 3-b (`AiAnalysisSectionV7`, reading `overall`/`categories`/
    // `narrative` directly — NOT through `summary`/`details`/`verdict`/
    // `grade`/`categoryScores` above, which V7 still leaves at their
    // defaults). ReportPdfGenerator was excluded from Phase 3-b's scope
    // entirely and wired its own (partial — category/battery-indicator
    // breakdown only, not narrative/overall/gate-reason) V7 display in
    // Phase 3-c; see ReportPdfGenerator.kt's `drawCategoryBreakdownV7`.
    /** Discriminator — see [AiPipeline]. Null for V6/legacy. */
    val pipeline: String? = null,
    val language: String? = null,
    val overall: AiOverallV7? = null,
    val categories: Map<String, AiCategoryV7>? = null,
    val narrative: AiNarrativeV7? = null,
    val narrativeIncomplete: Boolean? = null,
    val narrativeIncompleteFields: List<String>? = null,
    /** Straight pass-through of [net.neuralcheck.data.remote.model.AiAnalysisResponse.categoriesMissingLetterCeilingReason]
     *  — see that field's doc. Also round-trips through the `aiAnalysisJson`
     *  Room column like every other field on this class (see this class's
     *  own doc on [isComplete]/Room rehydration) — kept nullable, not
     *  `Set<String> = emptySet()`, because Gson's Unsafe-constructed
     *  rehydration of an OLDER cached row (written before this field
     *  existed) would leave a non-null-typed field holding a raw JVM
     *  `null` anyway. REVERSED 2026-09-23 (القرار ٧٢، البند ٣): every
     *  reader must now treat `null` here as "presence unknown for every
     *  category", not "nothing known missing" — see
     *  [net.neuralcheck.util.AiV7Presentation.classifyLetterCeilingReason]'s
     *  `declared` parameter doc. */
    val categoriesMissingLetterCeilingReason: Set<String>? = null,
    /** Straight pass-through of [net.neuralcheck.data.remote.model.AiAnalysisResponse.narrativeIncompleteDeclared]
     *  — see that field's doc. Same Room-rehydration caveat as
     *  [categoriesMissingLetterCeilingReason] above: an older cached row
     *  (written before this field existed) rehydrates this as raw JVM
     *  `null` via Gson's Unsafe construction regardless of the Kotlin
     *  default, so every reader must treat `null` as "presence unknown" —
     *  [net.neuralcheck.util.AiV7Presentation.classifyNarrativeIncomplete]'s
     *  `declared` parameter is where that default is actually decided. */
    val narrativeIncompleteDeclared: Boolean? = null,
    /** Straight pass-through of [net.neuralcheck.data.remote.model.AiAnalysisResponse.issuesDeclared]
     *  — see that field's doc. Same Room-rehydration caveat as
     *  [narrativeIncompleteDeclared] above: an older cached row (written
     *  before this field existed) rehydrates this as raw JVM `null`
     *  regardless of the Kotlin default, so a reader must treat `null` as
     *  "presence unknown", never as "declared and empty" — see
     *  [net.neuralcheck.presentation.screen.ReportScreen]'s `AiIssuesSection`
     *  call sites for where that default is decided. `issues` itself
     *  (above) stays a non-null `List<PredictedIssue>` either way — this
     *  flag is what lets a caller tell "declared empty" (a real "nothing
     *  found" answer) apart from "never declared" (no answer at all). */
    val issuesDeclared: Boolean? = null,
    /** Straight pass-through of [net.neuralcheck.data.remote.model.AiAnalysisResponse.harnessErrorDeclared]
     *  — see that field's doc. Same Room-rehydration caveat as
     *  [issuesDeclared] above: an older cached row rehydrates this as raw
     *  JVM `null` regardless of the Kotlin default, so a reader must treat
     *  `null` as "presence unknown", never as "declared false" — see
     *  [net.neuralcheck.util.AiV7Presentation.classifyOverallStatus]'s
     *  `harnessErrorDeclared` parameter for where that default is decided.
     *  Siblings: [noDataAtAllDeclared]/[partialAssessmentDeclared]. */
    val harnessErrorDeclared: Boolean? = null,
    /** Straight pass-through of [net.neuralcheck.data.remote.model.AiAnalysisResponse.noDataAtAllDeclared] — see [harnessErrorDeclared]. */
    val noDataAtAllDeclared: Boolean? = null,
    /** Straight pass-through of [net.neuralcheck.data.remote.model.AiAnalysisResponse.partialAssessmentDeclared] — see [harnessErrorDeclared]. */
    val partialAssessmentDeclared: Boolean? = null
)

/**
 * Domain-level mirror of
 * [net.neuralcheck.data.remote.model.AiAnalysisResponse.isComplete] — same
 * pipeline-branch shape, applied to the already-mapped domain result
 * (fresh from [net.neuralcheck.domain.scoring.AiScoringEngine], or
 * rehydrated from the `aiAnalysisJson` Room column on reopen).
 *
 * Used by ReportViewModel's sparse-write guard so a reopen only persists
 * — and only fast-paths off of — a genuinely renderable result. Before
 * this, the guard checked the same V6-only fields
 * [AiAnalysisResponse.isComplete] used to (A109's bug, one layer down):
 * a V7 result never satisfied it, `aiAnalysisJson` never got written for
 * a V7 scan, and every reopen re-ran the full AI call — the exact same
 * failure mode as A109, at the Room cache instead of the Firestore cache.
 */
fun AiAnalysisResult.isComplete(): Boolean {
    // Order 2026-09-26 (القرار ١٠٢ البند ٢، ثم ملحق القرار ١٠٤ البند ١):
    // was a raw `pipeline == V7_FINDINGS_BASED` / `else` binary check,
    // which silently routed `cached` (root shape depends on content, not
    // the tag) to whichever branch happened to be `else` — fixed under
    // القرار ١٠٢. This function ALSO used to check `overall?.letter` where
    // [net.neuralcheck.data.remote.model.isComplete] (the Response-level
    // twin this function mirrors) checked `overall?.letterSource` — a
    // discrepancy this file's comment used to name as "out of this
    // order's scope". Fixed now: both call sites delegate to
    // [AiPipeline.isComplete], the one shared implementation, and both
    // read `letterSource` — see that function's doc for why (a real
    // `letter: null, letterSource: "HARNESS_ERROR"` golden sample,
    // `v7-reports-document-sample-honor.json`, is a legitimate finished
    // answer this check must not read as incomplete).
    return AiPipeline.isComplete(
        pipeline = pipeline,
        grade = grade,
        hasV7Overall = overall != null,
        overallLetterSource = overall?.letterSource,
        summary = summary,
        details = details,
        verdict = verdict,
        issuesEmpty = issues.isEmpty(),
        categoryScoresEmpty = categoryScores.isNullOrEmpty()
    )
}

data class PredictedIssue(
    val severity: IssueSeverity,
    val title: String,
    val description: String,
    /** V2 — supporting data for the issue. Optional. */
    val evidence: String? = null,
    /** V2 — category tag ("battery", "thermal", etc.). Optional. */
    val category: String? = null
)

/**
 * V2 expanded severity enum. V1 only had DANGER/WARNING/INFO; V2 adds
 * CRITICAL (silent silicon defect, refuse-to-buy signal) and HIGH
 * (between danger and warning). UI maps each to a distinct color +
 * icon — see [net.neuralcheck.presentation.screen.ReportScreen].
 *
 * [fromString] also covers the V7 `issues[].severity` vocabulary
 * ("FAIL"/"WARNING"/"INFO"/"POSITIVE" — see the golden sample,
 * `app/src/test/resources/contract/v7-client-payload-sample.json`) since
 * [net.neuralcheck.domain.scoring.AiScoringEngine] maps `response.issues`
 * into this same [PredictedIssue]/[IssueSeverity] shape unconditionally,
 * for both V6 and V7 responses (issues has no separate V7 shape the way
 * overall/categories/narrative do). Found while wiring the V7 display
 * (Phase 3-b): "fail" fell through the old `else -> INFO` branch, which
 * would have rendered the sample's one real failed test (SEC-04, root
 * detected — the actual finding that caps the whole report's letter at C)
 * with the same blue "informational" styling as a passed test.
 *
 * [UNKNOWN] added 2026-09-24 (جولة جرد القيم الافتراضية، البند ١، القرار
 * ٧٦). Before this, `"info"` reached [INFO] only via the catch-all
 * `else` branch (never an explicit case), and an unrecognised or absent
 * severity string landed on [INFO] too — the calmest, bluest tier — with
 * nothing distinguishing "the backend said this is informational" from
 * "we don't know what the backend said". That is exactly the shape rule
 * 22 forbids: a missing/unrecognised input read as a normal, reassuring
 * value instead of producing its own visible state. Every V7 vocabulary
 * value (`FAIL`/`WARNING`/`INFO`/`POSITIVE`) now has its own explicit
 * `when` branch; only a genuinely unrecognised or absent string falls to
 * [UNKNOWN], which the UI renders distinctly (see
 * [net.neuralcheck.presentation.screen.ReportScreen]'s `AiIssuesSection`
 * and [net.neuralcheck.util.ReportPdfGenerator.severityColors]) instead of
 * silently borrowing [INFO]'s styling.
 */
enum class IssueSeverity {
    CRITICAL, DANGER, HIGH, WARNING, INFO, UNKNOWN;

    companion object {
        fun fromString(s: String): IssueSeverity = when (s.lowercase()) {
            "critical" -> CRITICAL
            "danger" -> DANGER
            "fail" -> DANGER
            "high" -> HIGH
            "warning" -> WARNING
            "info" -> INFO
            // Not merged with "info" above: this app has no decision that
            // POSITIVE should look identical to INFO forever, only that it
            // does today (see ReportScreen's AiIssuesSection doc — no
            // distinct visual treatment exists yet, order 2026-09-24 left
            // that undecided). A separate branch makes that an explicit,
            // revisitable choice rather than an accidental merge.
            "positive" -> INFO
            else -> UNKNOWN
        }
    }
}
