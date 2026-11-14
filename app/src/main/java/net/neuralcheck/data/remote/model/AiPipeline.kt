package net.neuralcheck.data.remote.model

/**
 * Single exported source for the `pipeline` discriminator the backend
 * stamps on every `analyzeDevice` response and mirrors onto the
 * `reports/{scanId}` Firestore doc as top-level `pipeline`.
 *
 * Every read site that branches on this value — [AiAnalysisResponse.isComplete],
 * [net.neuralcheck.domain.model.AiAnalysisResult] (the same-shaped domain
 * mirror), [net.neuralcheck.data.remote.FirebaseAnalysisService]'s
 * cache-hit gate, and the raw-Firestore reads in
 * `HomeViewModel.fetchLastScan` / `ScanHistoryViewModel.load` — imports
 * these constants instead of repeating the literal string. Project rule:
 * a literal a distinction is built on lives in exactly one place, never
 * copy-pasted per call site (the "v7_findings_based" drift across seven
 * files was the incident that produced this rule).
 *
 * Three states, per the contract:
 *   - [V7_FINDINGS_BASED] — root shape carries `overall` / `categories` /
 *     `narrative` instead of flat `grade`/`score`/`summary`/...
 *   - [V6_RAW_TIMESERIES] — explicit V6 marker; flat shape.
 *   - absent (null) — every report written before the backend started
 *     stamping `pipeline` at all. Also flat shape — treat exactly like
 *     [V6_RAW_TIMESERIES].
 *
 * Values and root-shape source: relayed from `buildDisplayShapeV7.js:121-160`,
 * `v7OperationalFields.js`, and `docs/V7_DISPLAY_SHAPE_CONTRACT.md` in the
 * neuralcheck-backend repo — this app repo cannot read that repo (agent
 * scope boundary), so these are cited by the orchestrating session, not
 * independently verified here. See the Phase 3-a report
 * (NEURALCHECK-FINDINGS.md) for the exact wording that was relayed.
 */
object AiPipeline {
    const val V7_FINDINGS_BASED = "v7_findings_based"
    const val V6_RAW_TIMESERIES = "v6_raw_timeseries"

    /**
     * Order 2026-09-26 (القرار ١٠٢ البند ٢). Two June-era tags, both flat
     * V6 shape (root `grade`/`score`, no `aiResponse.overall`) — منقول من
     * neuralcheck-backend، غير مُتحقَّق من هذا المستودع:
     *   - `v3_legacy`: `runAnalysisInternal.js:253` — `let pipeline =
     *     usePrecomputed ? "v6_raw_timeseries" : "v3_legacy"` — then
     *     `analyzeDevice.js:224-225` writes `grade`/`score` at the root.
     *     Written `488de6e` (2026-05-11) through before `fc4e426`
     *     (2026-09-16) — i.e. by builds that predate this app's own
     *     `usePrecomputed` commit (`f98a1b9`, 2026-05-11 08:21 — `git log
     *     --all -G'usePrecomputed' -- app/src` finds only that one commit
     *     in THIS repo), so `v3_legacy` never came from this app itself —
     *     it stays in the recognized-flat-shape list because its ROOT
     *     SHAPE is V6, not because this app ever wrote it.
     *   - `v5_precomputed`: `callAI.js:131` (`488de6e`/`3fb9f40`,
     *     2026-05-11) — same root `grade`/`score` shape
     *     (`analyzeDevice.js:162-163`). Written only briefly, before
     *     `a47527b` (2026-05-12 18:07).
     */
    const val V3_LEGACY = "v3_legacy"
    const val V5_PRECOMPUTED = "v5_precomputed"

    /**
     * Order 2026-09-26 (القرار ١٠٢ البند ٢). Written by `analyzeDevice.js`
     * (منقول، غير مُتحقَّق من هذا المستودع) whenever the backend serves a
     * cached prior result — copying `grade`/`score` from whatever the
     * SOURCE analysis's pipeline was, with NO constraint on that source's
     * shape (`071cd3f`/`22958cb`; on `master`, `analyzeDevice.js:252-254`
     * writes root `grade`/`score` for every non-V7 tag, `cached` included).
     * Unlike every other tag here, `cached`'s root shape is NOT determined
     * by the tag string alone — [classifyShape] inspects the document's
     * actual content for this one.
     */
    const val CACHED = "cached"

    /**
     * True when [pipeline] is a shape this app's flat V6 readers
     * (the raw-Firestore paths in `HomeViewModel`/`ScanHistoryViewModel`,
     * and the V6 branch of [AiAnalysisResponse.isComplete]) can correctly
     * interpret directly from the TAG ALONE: the explicit V6 marker,
     * [V3_LEGACY]/[V5_PRECOMPUTED] (both root-shape V6, see their own
     * docs), or absent (legacy, pre-dates the field). [CACHED] is
     * deliberately EXCLUDED here — its shape depends on content, not the
     * tag — see [classifyShape]. [V7_FINDINGS_BASED] and any future
     * pipeline name this app doesn't know about yet are NOT a flat shape
     * and must not be read as one.
     */
    fun isRecognizedFlatShape(pipeline: String?): Boolean =
        pipeline == null || pipeline == V6_RAW_TIMESERIES || pipeline == V3_LEGACY || pipeline == V5_PRECOMPUTED

    /** The three outcomes every pipeline-consuming reader in this app must
     *  route through — see [classifyShape]. */
    enum class ShapeKind { V6, V7, UNKNOWN }

    /**
     * Order 2026-09-26 (القرار ١٠٢ البند ٢) — the ONE classification every
     * reader that branches on [pipeline] must apply, replacing a raw
     * `pipeline == V7_FINDINGS_BASED` / `else` binary check. That binary
     * check silently mis-routed [CACHED] (whose root shape depends on the
     * document's ACTUAL content, not the tag name — see [CACHED]'s doc) to
     * whichever branch happened to be `else`.
     *
     * @param hasRootLetterOrGrade true when the document/response carries
     *   a real V6-shaped root signal (`grade`/`letter` non-blank at the
     *   root — each caller extracts this from whichever representation it
     *   holds, raw map or typed object).
     * @param hasV7Overall true when the document/response carries a real
     *   V7 `overall` block (nested under `aiResponse` for a stored
     *   document, or a direct `overall` property on the typed
     *   [net.neuralcheck.data.remote.model.AiAnalysisResponse]).
     *
     * [ShapeKind.UNKNOWN] — a tag this app has never seen (or [CACHED]
     * with NEITHER signal) — must never render as the raw tag text or the
     * bare "?" sentinel; callers declare the absence explicitly (rule 22),
     * reusing an existing "unavailable" string, and log the raw tag for
     * diagnosis rather than displaying it (this app's own established
     * convention — see e.g. `AiV7Presentation.classifyCategoryReason`'s
     * UNKNOWN branch).
     */
    fun classifyShape(
        pipeline: String?,
        hasRootLetterOrGrade: Boolean,
        hasV7Overall: Boolean
    ): ShapeKind = when {
        pipeline == V7_FINDINGS_BASED -> ShapeKind.V7
        isRecognizedFlatShape(pipeline) -> ShapeKind.V6
        pipeline == CACHED -> when {
            hasV7Overall -> ShapeKind.V7
            hasRootLetterOrGrade -> ShapeKind.V6
            else -> ShapeKind.UNKNOWN
        }
        else -> ShapeKind.UNKNOWN
    }

    /**
     * Order 2026-09-26 (ملحق القرار ١٠٤، البند ١) — the ONE completeness
     * check both [net.neuralcheck.domain.model.AiAnalysisResult.isComplete]
     * (Room pre-save gate) and
     * [net.neuralcheck.data.remote.model.isComplete] (post-fetch /
     * Firestore-cache gate) now delegate to, replacing two independently
     * written copies of the same 3-branch `when` that had drifted apart on
     * the V7 branch: the Result-side copy judged completeness by
     * `overall?.letter`, the Response-side copy by `overall?.letterSource`.
     * `letter` is legitimately `null` for a fully-formed, final
     * `HARNESS_ERROR` / `noDataAtAll` V7 answer (see the real
     * `v7-reports-document-sample-honor.json` golden sample: `letter: null,
     * letterSource: "HARNESS_ERROR"`) — `letterSource` is what
     * `buildDisplayShapeV7` assigns on every branch it can take, so its
     * presence, not `letter`'s, is the real "this section finished
     * writing" signal. Both call sites now read `letterSource` — this
     * function takes it directly (never `letter`) so a future call site
     * cannot silently regress to the wrong field.
     *
     * The two V6 branches were NOT a second real discrepancy: re-read
     * directly at both call sites before this change, they already checked
     * the identical 5 signals (`summary`/`details`/`verdict` any non-blank,
     * OR `issues` non-empty, OR `categoryScores` non-empty) — only the
     * null-safety spelling differed, because
     * [net.neuralcheck.domain.model.AiAnalysisResult]'s fields default to
     * non-null (`""`, `emptyList()`) while
     * [net.neuralcheck.data.remote.model.AiAnalysisResponse]'s are
     * nullable. No field became newly "governing" for V6 here — there was
     * nothing to choose between.
     *
     * Callers pass each field pre-extracted rather than the receiver
     * object itself because the two receivers don't share a common
     * `issues`/`categoryScores` element type
     * ([net.neuralcheck.domain.model.PredictedIssue] vs
     * [net.neuralcheck.data.remote.model.AiIssue]) — only the emptiness of
     * each list matters here, so each caller reduces its own list before
     * calling in.
     */
    fun isComplete(
        pipeline: String?,
        grade: String?,
        hasV7Overall: Boolean,
        overallLetterSource: String?,
        summary: String?,
        details: String?,
        verdict: String?,
        issuesEmpty: Boolean,
        categoryScoresEmpty: Boolean
    ): Boolean = when (
        classifyShape(
            pipeline,
            hasRootLetterOrGrade = !grade.isNullOrBlank(),
            hasV7Overall = hasV7Overall
        )
    ) {
        ShapeKind.V7 -> !overallLetterSource.isNullOrBlank()
        ShapeKind.V6 ->
            !summary.isNullOrBlank() || !details.isNullOrBlank() || !verdict.isNullOrBlank() ||
                !issuesEmpty || !categoryScoresEmpty
        ShapeKind.UNKNOWN -> false
    }
}
