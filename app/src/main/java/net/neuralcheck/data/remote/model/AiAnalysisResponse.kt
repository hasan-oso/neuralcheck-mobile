package net.neuralcheck.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Response returned by the `analyzeDevice` Firebase Callable Cloud Function.
 *
 * The default Gson instance used by [net.neuralcheck.data.remote.FirebaseAnalysisService]
 * does NOT apply any field-naming policy, so Kotlin camelCase maps one-to-one
 * to JSON camelCase for most fields. The two fields the backend returns in
 * snake_case (`predicted_problems`, `category_notes`) are annotated explicitly
 * with [SerializedName] so the mapping lines up regardless of naming policy.
 *
 * Every field is nullable. Reason: Gson instantiates Kotlin data classes via
 * `sun.misc.Unsafe` / Objenesis, bypassing the primary constructor AND any
 * default values declared on it. A field typed as non-null but absent from
 * the JSON arrives as a genuine `null` at the JVM level, and the first
 * downstream call site that passed it to a non-null parameter crashed with
 * `Parameter specified as non-null is null`. Observed in the wild when the
 * backend shipped `category_scores` (new schema) without the older
 * `category_notes` — the mismatch propagated all the way to
 * [net.neuralcheck.domain.model.AiAnalysisResult.<init>].
 *
 * [AiScoringEngine] coalesces every field with `?:` / `.orEmpty()` when it
 * maps into the domain model, so the nullability stays contained to this DTO.
 */
data class AiAnalysisResponse(
    val grade: String? = null,      // A/B/C/D — null when backend omits
    val score: Int? = null,         // 0-100
    val summary: String? = null,
    val details: String? = null,
    val verdict: String? = null,
    val issues: List<AiIssue>? = null,
    @SerializedName("predicted_problems")
    val predictedProblems: List<PredictedProblem>? = null,
    @SerializedName("category_notes")
    val categoryNotes: Map<String, String>? = null,
    // Per-category breakdown the backend can optionally return. The map key is
    // the category identifier (e.g. "battery", "display"); the value carries a
    // short AI-authored note alongside a grade/score + pass/total count so the
    // report can render both the numeric summary and a human paragraph per
    // category. Defaults on [AiCategoryScore] fields absorb partial payloads
    // (e.g. missing `note`) without failing to deserialize.
    @SerializedName("category_scores")
    val categoryScores: Map<String, AiCategoryScore>? = null,
    // Human-readable scan identifier minted by the backend (e.g.
    // "NC-A7K9M2"). Replaces the 36-char UUID in any user-facing surface
    // (report header, QR caption, share text). Null on legacy reports
    // saved before the server started returning the field — UI falls back
    // to the UUID in that case so the display still resolves to *something*
    // unique.
    //
    // Accepts both casings via [SerializedName.alternate] because it's the
    // one field where we observed the server inconsistently shipping
    // `shortId` (camelCase) and `short_id` (snake_case) depending on the
    // Cloud Function path. Letting Gson match either avoids a silent null.
    @SerializedName(value = "shortId", alternate = ["short_id"])
    val shortId: String? = null,
    /**
     * AI-authored root-cause hypothesis — a single short paragraph naming the
     * underlying fault that explains the symptoms (e.g. "ضعف معجون حراري على
     * المعالج"). Distinct from [issues] which is a flat list of observed
     * problems; root_cause is the narrative that ties them together. Optional
     * because the backend only emits it when its confidence is high enough
     * to commit to a single hypothesis.
     */
    @SerializedName(value = "root_cause", alternate = ["rootCause"])
    val rootCause: String? = null,
    /**
     * Per-persona buyer guidance. The backend tailors a one-paragraph
     * recommendation for each of three usage profiles (casual / gamer /
     * photographer) so the buyer can match the device's strengths against
     * how they intend to use it. Any individual persona can be null when
     * the AI doesn't have a useful opinion for that category.
     */
    @SerializedName(value = "buyer_advice", alternate = ["buyerAdvice"])
    val buyerAdvice: BuyerAdvice? = null,
    /**
     * Free-form identifier of the model (e.g. "gemini-1.5-pro-latest") that
     * produced this analysis. Surfaced as a footer credit on the screen and
     * PDF so the user can correlate verdicts across model upgrades. Null on
     * legacy reports written before the backend started shipping the field.
     */
    @SerializedName(value = "ai_model", alternate = ["aiModel"])
    val aiModel: String? = null,
    /**
     * Backend flag — true when this report came from the user's complimentary
     * free-trial scan (the one-time scan offered before any subscription).
     * The launch flavor uses it to gate three things in the report screen:
     *   - The "Save PDF" / "QR" / "Share" buttons in the bottom bar are hidden.
     *   - A banner tells the user to subscribe to unlock those exports.
     *   - [net.neuralcheck.util.ReportPdfGenerator] refuses to generate a PDF
     *     even if a caller bypasses the UI gate (defense-in-depth).
     * Defaults to false / null — pre-existing reports written before the
     * backend started shipping the field render with the full bottom bar.
     */
    @SerializedName(value = "is_free_trial", alternate = ["isFreeTrial"])
    val isFreeTrial: Boolean? = null,
    /**
     * Consumer-facing device name resolved server-side from the build
     * fingerprint (Build.MANUFACTURER + Build.MODEL + supported_abis +
     * SoC info bundled in the fingerprint). The backend has access to a
     * fuller, more frequently-updated catalog than the on-device
     * [net.neuralcheck.util.DeviceName] map, so when this field is
     * populated it should win over the local lookup. Examples:
     * "Samsung Galaxy S24 Ultra", "Xiaomi Redmi Note 12 Pro 5G".
     */
    @SerializedName(value = "device_marketing_name", alternate = ["deviceMarketingName", "deviceName", "device_name"])
    val deviceMarketingName: String? = null,
    /**
     * Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦). Epoch millis of when the
     * backend finished this analysis — root-level field on the stored
     * `reports/{scanId}` document (a plain number, unlike `createdAt`
     * which is a Firestore server-timestamp sentinel — see [createdAtMs]).
     * Only reachable through
     * [net.neuralcheck.data.remote.FirebaseAnalysisService.fetchExistingReport]'s
     * flattened map (the live `analyzeDevice()` RPC response has no use
     * for it — the caller already knows when ITS OWN scan happened from
     * the local [net.neuralcheck.data.local.db.entity.ScanEntity]).
     * Used by [net.neuralcheck.presentation.viewmodel.ReportViewModel.loadScan]'s
     * Room-miss fallback, to render a header timestamp for a report this
     * device has no local scan row for at all.
     *
     * ~~Absent ⇒ that fallback path refuses to render a fabricated
     * timestamp and shows the explicit "could not load" state instead
     * (rule 22).~~ **Order 2026-09-26 (القرار ١٠٢ البند ١) — no longer
     * true, kept struck-through not deleted (rule 8): absence of
     * `analyzedAt` ALONE no longer fails the load. The V6 pipeline never
     * sends `analyzedAt` at all (only V7 does), so every V6 report opened
     * via this fallback used to show "لم يتم العثور على الفحص" — wrongly,
     * since the document is real and complete. The fallback now tries
     * [createdAtMs] next, and shows the report with an explicit
     * "time unavailable" line only if BOTH are absent — never blocking the
     * report itself on a missing timestamp.
     */
    @SerializedName(value = "analyzedAt", alternate = ["analyzed_at"])
    val analyzedAt: Long? = null,
    /**
     * Order 2026-09-26 (القرار ١٠٢ البند ١). Epoch millis derived from the
     * raw document's `createdAt` field — Firestore's own server-write
     * timestamp, present on EVERY `reports/{scanId}` document regardless
     * of pipeline (V6 included, unlike [analyzedAt] which V6 never sends).
     * Deliberately NOT populated by Gson's normal parse (no
     * `@SerializedName` — there is no clean, single JSON shape for it to
     * declare): on the wire from the Android Firestore SDK, `createdAt` is
     * a `com.google.firebase.Timestamp` object, not a plain number or a
     * `{_seconds, _nanoseconds}` map (that shape is specific to Node's
     * Admin SDK serialization, e.g. in a hand-written test fixture — rule
     * 41's own concern: test the shape the reader actually receives).
     * Instead, [net.neuralcheck.data.remote.FirebaseAnalysisService.extractCreatedAtMs]
     * reads `Timestamp` directly off the RAW document map (before it is
     * flattened/JSON-round-tripped for the rest of this class's Gson
     * parse) and this field is populated via `.copy(...)`, the same
     * pattern already used there for [categoriesMissingLetterCeilingReason]
     * and its siblings. Null when the raw value isn't a recognised
     * `Timestamp` (rule 22 — absence surfaces, never a fabricated 0/now).
     */
    val createdAtMs: Long? = null,
    /**
     * V2 master plan §6.3 — what the model expects from this device under
     * V2's heavier stress, articulated BEFORE judgment so the model is
     * forced to commit its prior knowledge to text. The UI surfaces this
     * verbatim (collapsed by default) so the user can see the AI's
     * reasoning frame. Optional — omitted on legacy reports + on AI
     * runs where the model declined to commit.
     */
    @SerializedName(value = "expected_performance", alternate = ["expectedPerformance"])
    val expectedPerformance: ExpectedPerformance? = null,
    /**
     * V2 — comparison against the most-recent prior scan of the SAME
     * device. Populated by the Cloud Function ONLY when a prior scan
     * exists in `reports/{scanId}` for the same `deviceProfileKey.primary`.
     * Null on first scans + on customer-device sessions where prior
     * history isn't accessible.
     */
    @SerializedName(value = "change_analysis", alternate = ["changeAnalysis"])
    val changeAnalysis: ChangeAnalysis? = null,
    // ── V7 findings-based pipeline (Phase 3-a) ──
    // Contract relayed from neuralcheck-backend's buildDisplayShapeV7.js:121-160,
    // v7OperationalFields.js, docs/V7_DISPLAY_SHAPE_CONTRACT.md — this app
    // repo cannot read that repo, so this shape is not independently
    // verified against live JSON. See AiPipeline.kt's doc + the Phase 3-a
    // report for the exact citation and what was assumed vs. stated.
    //
    // Discriminator. Three states — see [AiPipeline]:
    //   AiPipeline.V7_FINDINGS_BASED -> this DTO's V7 fields are the
    //     source of truth; grade/score/summary/... above are NOT
    //     populated for this shape.
    //   AiPipeline.V6_RAW_TIMESERIES, or null (legacy, pre-dates this
    //     field) -> the flat V6 fields above are the source of truth;
    //     the V7 fields below are null.
    val pipeline: String? = null,
    /** Echoed back from the request's `language` param, per the V7 root
     *  contract. Not used by anything in the read layer yet — carried
     *  through so it isn't dropped. */
    val language: String? = null,
    /** V7 — device-level verdict block (finalLetter + coverage +
     *  abstention/partial-assessment provenance). Null on V6/legacy. */
    val overall: AiOverallV7? = null,
    /** V7 — per-category breakdown, keyed by category id, same role as
     *  [categoryScores] but a DIFFERENT shape (state/letter/coverage/
     *  testIds, not grade/score/passCount). Null on V6/legacy. */
    val categories: Map<String, AiCategoryV7>? = null,
    /** V7 — narrative block (summary/details/verdict/buyerAdvice).
     *  Deliberately NOT merged into the flat [summary]/[details]/
     *  [verdict]/[buyerAdvice] fields above — those feed ReportScreen
     *  today and this phase reads V7 without building a display on it
     *  (Phase 3-a scope). Null on V6/legacy. */
    val narrative: AiNarrativeV7? = null,
    val narrativeIncomplete: Boolean? = null,
    val narrativeIncompleteFields: List<String>? = null,
    /**
     * NOT a wire field — never sent by the backend under this name, never
     * populated by Gson's reflective parse of this class. Set explicitly by
     * [net.neuralcheck.data.remote.FirebaseAnalysisService] via `.copy()`
     * right after `gson.fromJson(...)`, from
     * [categoriesMissingLetterCeilingReason] (the top-level function of
     * that name in `V7RawFieldPresence.kt`) run against the SAME raw JSON
     * string just parsed.
     *
     * Why this exists: [AiCategoryV7.letterCeilingReason] is `null` both
     * when the raw category JSON object has the key with an explicit JSON
     * `null` value (contract-documented "no LOW_CATEGORY_COVERAGE gate
     * ceilinged this category" — a real answer) AND when the key is
     * entirely ABSENT from that object (an older stored report, written
     * before the backend added this field — no answer at all). Gson's
     * reflective `AiCategoryV7` adapter cannot tell these apart: it only
     * invokes a field's value for a JSON name that is actually present in
     * the object being read, so an absent key just leaves the Kotlin field
     * at its post-Unsafe-construction default (`null`) — indistinguishable
     * from the explicit-null case by the time it reaches this class. Rule
     * 22/25 (this app's read-layer rule): a missing input must produce an
     * explicit state, never be silently read as the valid "no ceiling"
     * answer. This set is that explicit state, computed independently by
     * re-reading the raw JSON text rather than trying to make the
     * `AiCategoryV7` field-level adapter detect it (a field-level
     * `@JsonAdapter` is never invoked at all for an absent key, so it
     * cannot observe the absence either — only walking the raw
     * [com.google.gson.JsonObject] tree, outside Gson's per-field
     * dispatch, can).
     *
     * Contains the category keys (e.g. "gpu", "storage") whose raw JSON
     * object does NOT carry the `letterCeilingReason` key at all. Null
     * (not computed / not a Gson-parsed response — e.g. a hand-built
     * `AiAnalysisResponse()` in a test, or an older Room-cached
     * [net.neuralcheck.domain.model.AiAnalysisResult] from before this
     * field existed) must be read as "presence unknown for every
     * category", NOT "nothing known to be missing" — REVERSED 2026-09-23
     * (القرار ٧٢، البند ٣) from this field's original doc, once the
     * backend confirmed (neuralcheck-backend @ `e8cd67c`) it always emits
     * this key live, so a null set can now only mean the signal itself was
     * never computed. See [net.neuralcheck.util.AiV7Presentation.classifyLetterCeilingReason]'s
     * `declared` parameter doc for the caller-side default this now
     * requires.
     */
    val categoriesMissingLetterCeilingReason: Set<String>? = null,
    /**
     * NOT a wire field — same pattern as [categoriesMissingLetterCeilingReason]
     * above but for the ROOT `narrativeIncomplete` key, set via `.copy()`
     * from [isNarrativeIncompleteDeclared] (`V7RootFieldPresence.kt`, a
     * deliberately separate file — see its doc) against the same raw JSON
     * string. Order 2026-09-23 (القرار ٧٢) item 2: `narrativeIncomplete`'s
     * ABSENCE must not be read as "complete" (`false`) — [narrativeIncomplete]
     * being Kotlin `null` is ambiguous between "key present, value
     * explicitly null" (which the contract does not actually document as
     * possible — the field is typed `Boolean?` defensively, not because a
     * null-but-present case is known) and "key absent entirely" (an older
     * report). This flag disambiguates. Null (not computed) is read the
     * same as "presence unknown" — see [net.neuralcheck.util.AiV7Presentation.classifyNarrativeIncomplete]'s
     * `declared` parameter for how a caller must default when even this
     * flag itself is unavailable.
     */
    val narrativeIncompleteDeclared: Boolean? = null,
    /**
     * NOT a wire field — same pattern as [narrativeIncompleteDeclared]
     * above but for the ROOT `issues` key, set via `.copy()` from
     * [net.neuralcheck.data.remote.model.areIssuesDeclared] (`V7RootFieldPresence.kt`)
     * against the same raw JSON string. Added 2026-09-24 (جولة جرد القيم
     * الافتراضية، البند ج، القرار ٧٦): [issues] being Kotlin `null` is
     * ambiguous between "key absent entirely" and "key present, value
     * explicitly null" — and [net.neuralcheck.domain.scoring.AiScoringEngine]
     * additionally flattens either case to an empty `List<PredictedIssue>`
     * via `.orEmpty()`, making a genuinely present-but-empty `issues: []`
     * (a real "nothing found" answer) indistinguishable from "this report
     * never carried the key at all" by the time it reaches
     * [net.neuralcheck.domain.model.AiAnalysisResult.issues]. This flag
     * disambiguates. `issues` is shared shape between V6 and V7 (see
     * [net.neuralcheck.domain.model.PredictedIssue]'s class doc), so this
     * applies to reports of either pipeline. Null (not computed — a
     * hand-built [AiAnalysisResponse] in a test, or an older Room-cached
     * [net.neuralcheck.domain.model.AiAnalysisResult] from before this
     * field existed) is read as "presence unknown" by
     * [net.neuralcheck.presentation.screen.ReportScreen]'s `AiIssuesSection`
     * caller.
     */
    val issuesDeclared: Boolean? = null,
    /**
     * NOT a wire field — same pattern as [narrativeIncompleteDeclared]/
     * [issuesDeclared] above but for the NESTED `overall.harnessError` key,
     * set via `.copy()` from [net.neuralcheck.data.remote.model.isHarnessErrorDeclared]
     * (`V7RootFieldPresence.kt`) against the same raw JSON string.
     *
     * Added 2026-09-24 (جولة جرد القيم الافتراضية، البند ح، القرار ٧٦).
     * Backend confirmed (relayed 2026-09-24, neuralcheck-backend @ HEAD —
     * `analyzeDevice.js:170/173/176`, `buildDisplayShapeV7.js:454/460`,
     * `verifyReport`'s `!!pickOverall(...)`; three samples — RPC return,
     * stored doc, verifyReport — all carry `harnessError=false` with no
     * absence) that `harnessError`/`noDataAtAll`/`partialAssessment` are
     * ALWAYS emitted, each coerced through `!!`, on every path this app
     * reads a V7 report from. [AiOverallV7.harnessError] being Kotlin
     * `null` can therefore only mean "this key was absent from the raw
     * `overall` object" (an older Room-cached
     * [net.neuralcheck.domain.model.AiAnalysisResult], a pre-`!!`-coercion
     * backend response, or a malformed/partial doc) — never a legitimate
     * "false" the backend sent. See
     * [net.neuralcheck.util.AiV7Presentation.classifyOverallStatus]'s
     * `harnessErrorDeclared` parameter for the caller-side default this
     * requires. See [harnessErrorDeclared]'s siblings [noDataAtAllDeclared]/
     * [partialAssessmentDeclared] for the other two fields of this same
     * triad.
     */
    val harnessErrorDeclared: Boolean? = null,
    /** Same pattern as [harnessErrorDeclared], for `overall.noDataAtAll`. */
    val noDataAtAllDeclared: Boolean? = null,
    /** Same pattern as [harnessErrorDeclared], for `overall.partialAssessment`. */
    val partialAssessmentDeclared: Boolean? = null
)

/**
 * V7 — root `overall` block.
 *
 * Field types RE-DERIVED from a real golden sample (neuralcheck-backend
 * @ `43dee66`, `functions/test/fixtures/golden/v7-client-payload-sample.json`,
 * copied verbatim into `app/src/test/resources/contract/` — generated
 * through the real backend chain, zero model calls) after the initial
 * textual-contract-only version of this class got 4 of 5 checked
 * assumptions wrong. Corrections made here, each confirmed against the
 * sample, not guessed:
 *   - [coveragePct] was `Int` — the sample's own generator can emit a
 *     non-integer percentage; changed to [Double] like [coverage].
 *   - [partialAssessmentTag] was `String` — the sample shows an OBJECT
 *     (`{label, coveragePct, text}`); now [AiPartialAssessmentTagV7].
 *   - [governingGateReason] was `String` — the sample shows an OBJECT
 *     (`{name, ceilingLetter, source}`); now [AiGoverningGateReasonV7].
 * [zeroMeasuredMessage] / [abstentionReasons] / [harnessErrorReason] are
 * `null` in the sample (README.md marks these "حقيقي" — genuinely null
 * for this device, not a parsing gap) so their String/List<String> types
 * remain UNCONFIRMED by a positive value — kept as the most defensible
 * guess (matches this file's `...Reasons`-plural-as-List<String>
 * convention, e.g. [ChangeAnalysis.changeReasons]), not verified.
 */
data class AiOverallV7(
    /** finalLetter — the letter AFTER any model adjustment. See
     *  [letterSource] for who set it. Sample: "C". */
    val letter: String? = null,
    /** Sample: "GATE:ROOT_DETECTED" — NOT the "DETERMINISTIC"/
     *  "LLM_ADJUSTED" enum documented for the CATEGORY-level field of the
     *  same name; overall's vocabulary is gate-name-based. Stored as a
     *  free string either way — no enum was ever coded here. */
    val letterSource: String? = null,
    /** Sample: 0.4503809523809524. Key confirmed always present. */
    @com.google.gson.annotations.JsonAdapter(LenientDoubleAdapter::class)
    val coverage: Double? = null,
    /** Sample: 45 (an integer VALUE, but the contract's real type is a
     *  general number — see class doc). Key confirmed always present. */
    @com.google.gson.annotations.JsonAdapter(LenientDoubleAdapter::class)
    val coveragePct: Double? = null,
    val partialAssessment: Boolean? = null,
    val partialAssessmentTag: AiPartialAssessmentTagV7? = null,
    val governingGateReason: AiGoverningGateReasonV7? = null,
    val noDataAtAll: Boolean? = null,
    /** `null` in the sample — README.md marks this "حقيقي" (genuinely
     *  null for this device: nothing was zero-measured), not a sample
     *  gap. No positive value observed to confirm the String type. */
    val zeroMeasuredMessage: String? = null,
    /**
     * Order 2026-10-01 (أمر «abstentionReasons»): was `List<String>?` —
     * **wrong type**, confirmed by this round's own step-1 proof (a real
     * `GsonBuilder().create()` parse of an object-shaped response threw
     * `JsonSyntaxException: ... Expected a string but was BEGIN_OBJECT`,
     * failing the ENTIRE report read — see this round's report for the
     * verbatim exception, and
     * [net.neuralcheck.data.remote.model.AbstentionReasonsParsingTest] for
     * the pinned proof, before and after).
     *
     * Real shape — relayed from neuralcheck-backend, UNVERIFIED from this
     * repo (القاعدة ٢٩): `buildDisplayShapeV7.js:353`
     * `out.push({category: cat, reason: categoryAbsenceReason(entry) ||
     * entry.state})` — an array of `{category, reason}` objects, filled
     * only when [zeroMeasuredMessage] is set. **That condition is "no
     * category produced a LETTER", NOT "no category was measured"** (order
     * 2026-10-02 correction of an earlier wrong relay): relayed
     * `categoryStates.js:836-837` (`noDataAtAll = measuredCategoriesMean
     * === null`) and `buildDisplayShapeV7.js:619` (`zeroMeasuredMessage =
     * overall.letter === null && noDataAtAll`). A battery that WAS measured
     * but gets no letter (uncalibrated) is therefore listed, with
     * `NO_CALIBRATED_THRESHOLD (…)` — the most common case. `null` in every
     * golden sample this repo holds (all predate this shape) — no positive
     * value observed here yet.
     */
    val abstentionReasons: List<AiAbstentionReasonV7>? = null,
    val harnessError: Boolean? = null,
    /** Raw internal code (e.g. "FUNCTIONAL_RESULTS_UNAVAILABLE" — see the
     *  Honor B fixture, `v7-reports-document-sample-honor.json`). Order
     *  2026-10-01 (أمر القرارات، A8/S-13): NEVER shown to the buyer — see
     *  [harnessErrorMessage], the field added to replace it on screen. Kept
     *  here unchanged (still read, e.g. for logging) — this field itself is
     *  not removed, only no longer rendered. */
    val harnessErrorReason: String? = null,
    /**
     * Order 2026-10-01 (أمر القرارات، A8/S-13): `overall.harnessErrorMessage`
     * — a human-readable Arabic sentence the backend is adding ALONGSIDE
     * [harnessErrorReason] specifically so the app can stop displaying the
     * raw code (rule 22: "الرمز الخام لا يُعرض على الشاشة"). RELAYED,
     * UNVERIFIED from this repo (القاعدة ٢٩): neuralcheck-backend is not
     * committed yet as of this order — this app repo cannot read that repo
     * to confirm the field's existence or exact name independently. No
     * golden sample in this repo carries a non-null value for this field
     * (the one real harnessError=true fixture, Honor B, predates it) — see
     * [net.neuralcheck.util.AiV7Presentation.harnessErrorDisplayText]'s own
     * doc for how the absent case is handled, and this round's report for
     * why no sample proves the present case.
     */
    val harnessErrorMessage: String? = null
)

/**
 * Order 2026-10-01 (أمر «abstentionReasons»). One element of
 * `overall.abstentionReasons[]` — see [AiOverallV7.abstentionReasons]'s own
 * doc for the full citation and context. [reason] is overloaded by the
 * backend's own `||` fallback (`categoryAbsenceReason(entry) ||
 * entry.state`): usually one of the reason codes
 * [net.neuralcheck.util.AiV7Presentation] exports as constants
 * (`REASON_MANUAL_ATTESTATION_ONLY`, `REASON_NO_HEALTH_BEARING_TESTS_DECLARED`,
 * `REASON_DECLARED_BUT_NONE_EXECUTED`, `REASON_HARNESS_ERROR`,
 * `REASON_NO_BATTERY_INDICATORS_MEASURED`,
 * [net.neuralcheck.util.AiV7Presentation.NO_CALIBRATED_THRESHOLD_REASON]),
 * but falls back to the category's raw `state` string itself
 * (`"UNMEASURED"`, ...) when no specific reason code applied. This object
 * carries no `state` field; none is needed —
 * [net.neuralcheck.util.AiV7Presentation.classifyAbstentionReason] maps the
 * code itself (the user's approved table), and a bare state string falls to
 * "any other code → #15". Both fields are nullable defensively — not
 * confirmed required by any real sample yet (none exists for this shape).
 */
data class AiAbstentionReasonV7(
    val category: String? = null,
    val reason: String? = null
)

/**
 * V7 — `overall.partialAssessmentTag`. Discovered structure (the
 * original textual contract only named the field, not its shape) —
 * every sub-field confirmed present in the golden sample:
 * `{"label": "تقييم جزئي", "coveragePct": 45, "text": "تقييم جزئي — تغطية 45%"}`.
 */
data class AiPartialAssessmentTagV7(
    val label: String? = null,
    @com.google.gson.annotations.JsonAdapter(LenientDoubleAdapter::class)
    val coveragePct: Double? = null,
    val text: String? = null
)

/**
 * V7 — `overall.governingGateReason`. Discovered structure, confirmed in
 * the golden sample: `{"name": "ROOT_DETECTED", "ceilingLetter": "C",
 * "source": "SEC-04=fail"}`.
 *
 * Order 2026-10-07 (A1 + decision S1, relayed by the coordinator from
 * `PLAN-01-ROADMAP.md:2998`/`:2991`): the server now also sends [text] — a
 * ready-to-display Arabic sentence per gate, e.g. `{"name":"ROOT_DETECTED",
 * "ceilingLetter":"C","source":"SEC-04=fail","text":"أعلى حرف C: صلاحيات
 * الجذر (الروت) مكتشَفة على الجهاز."}` (relayed from neuralcheck-backend's
 * golden `v7-reports-document-sample.json`, UNVERIFIED from this repo —
 * القاعدة ٢٩; this repo's golden COPIES predate it and have no `text`).
 * The decision: "لكل بوّابة جملة عربية للعرض… ولا يُعرض الاسم … ولا المصدر …
 * ويبقى الاسم والمصدر في البيانات للّوحة" — so [name] and [source] STAY on
 * this object (the back-office reads them; this app still parses them) but
 * the screen shows ONLY [text], and hides the
 * WHOLE card when it is absent — see
 * [net.neuralcheck.presentation.screen.gateReasonCardDecision]. Nullable:
 * a report from before the server change (including an old Room-cached
 * `aiAnalysisJson`) has none, and `UNMATCHED` may not carry one either.
 * Same `text` field name the category-level [AiLetterCeilingReasonV7]
 * already reads.
 */
data class AiGoverningGateReasonV7(
    val name: String? = null,
    val ceilingLetter: String? = null,
    val source: String? = null,
    val text: String? = null
)

/**
 * V7 — `categories.<category>.letterCeilingReason`. Same three-field shape
 * family as [AiGoverningGateReasonV7] (`name`/`ceilingLetter` in common) but
 * `text` in place of `source`: contract-documented as a ready-to-display
 * HUMAN sentence (built server-side from that category's own
 * executed/declared test counts — "not a fixed template", per the backend
 * contract handoff), NOT the technical `audit.gatesApplied[].source` string
 * [AiGoverningGateReasonV7.source] carries (that one stays server-internal,
 * never relayed to this app for the category level at all).
 *
 * Confirmed against the golden sample (`app/src/test/resources/contract/v7-client-payload-sample.json`):
 * `gpu` -> `{"name":"LOW_CATEGORY_COVERAGE","ceilingLetter":"B","text":"تغطية
 * منخفضة — اختباران من خمسة، وحرف مسقوف: لم يُقَس في هذه الفئة أيّ اختبار
 * معتمد قياساً للحالة."}`; `display` -> same `name`/`ceilingLetter`, DIFFERENT
 * `text` with no "تغطية منخفضة" lead-in (`"ثلاثة اختبارات من ثلاثة — حرف
 * مسقوف: لم يُقَس في هذه الفئة أيّ اختبار معتمد قياساً للحالة."`) — proof the
 * text is genuinely per-category, not a shared constant.
 *
 * See [AiCategoryV7.letterCeilingReason]'s doc for the object-vs-null vs.
 * key-absent distinction this app must not conflate.
 */
data class AiLetterCeilingReasonV7(
    val name: String? = null,
    val ceilingLetter: String? = null,
    val text: String? = null
)

/**
 * V7 — one entry of the root `categories` map. Distinct shape from
 * [AiCategoryScore] (V6) — see [AiAnalysisResponse.categories] doc.
 * [confidence]/[tag]/[indicators]/[socAtStart]/[socAtEnd] are
 * battery-only per the golden sample (every other category in the
 * sample — thermal, cpu, gpu, display, memory, storage, camera, gps,
 * security, sensors, cellular, wireless, audio, input — carries none of
 * these five keys at all; only `battery` does, confidence included even
 * though ITS value is null there).
 */
data class AiCategoryV7(
    val state: String? = null,
    /** finalLetter for this category. */
    val letter: String? = null,
    val letterSource: String? = null,
    /** Null when [letterSource] is "DETERMINISTIC", per the contract —
     *  and every category in the golden sample IS "DETERMINISTIC", so
     *  this field's non-null shape (LLM_ADJUSTED case) is still
     *  unconfirmed by this sample. */
    val adjustmentReason: String? = null,
    /**
     * Retyped from `String?` — a report (relayed from neuralcheck-backend's
     * `buildDisplayShapeV7.js:158`/`analyzeDevice.js:78`, both gating this
     * field with `Array.isArray(...)`, plus an observed real value
     * `["categoryStates.categories.storage.deterministicLetter"]`) shows the
     * true shape is an array of internal-code-path strings, not a bare
     * string. Not independently confirmed here — this app repo cannot read
     * neuralcheck-backend, and the one golden sample in this repo
     * (`v7-client-payload-sample.json`) has no LLM_ADJUSTED category, so
     * every entry's `adjustmentEvidence` in it is `null` and parses fine
     * under either type; the array shape is unexercised by that sample.
     * [LenientStringListAdapter] is defense-in-depth for that residual
     * uncertainty: an unexpected shape degrades to `null` (this field only)
     * instead of throwing `JsonSyntaxException` out of the whole
     * `gson.fromJson` call, which — before this fix — would have dropped
     * the entire [AiAnalysisResponse] the first time the backend actually
     * sent an LLM_ADJUSTED category (the array-shaped `BEGIN_ARRAY` token
     * has no coercion to `String` in Gson, lenient or not).
     */
    @com.google.gson.annotations.JsonAdapter(LenientStringListAdapter::class)
    val adjustmentEvidence: List<String>? = null,
    @com.google.gson.annotations.JsonAdapter(LenientDoubleAdapter::class)
    val coverage: Double? = null,
    val reason: String? = null,
    /**
     * V7. Sourced from neuralcheck-backend contract handoff @ `154f3f2`
     * (`docs/V7_DISPLAY_SHAPE_CONTRACT.md:245`, `v7ContractFieldMap.js`'s
     * `CATEGORY_LETTER_CEILING_REASON_FIELDS`), confirmed against the
     * regenerated golden sample (`app/src/test/resources/contract/v7-client-payload-sample.json`,
     * see its README) — 7 of 15 categories carry a non-null value there
     * (gpu/display/memory/gps/sensors/cellular/wireless), all
     * `name="LOW_CATEGORY_COVERAGE"`, `ceilingLetter="B"`.
     *
     * Distinct from [reason] above (per the contract, deliberately, never
     * both non-null on the same category): [reason] is why a category
     * produced NO letter at all (`letter == null`). This field is why an
     * ALREADY-PRODUCED letter (`letter != null`) was capped by a named
     * gate — currently only ever populated for the `LOW_CATEGORY_COVERAGE`
     * gate specifically (the backend's own documented scope note: other
     * gates that could cap a category letter, e.g. a hypothetical
     * `ROOT_DETECTED` on `security`, do not populate this field, even
     * though `audit.gatesApplied` — not relayed to this app at all, V6/V7
     * `audit` block is server-internal per decision 4/6 — would still show
     * them as governing).
     *
     * [text] is the one field this app must render verbatim per this
     * task's order ("سبب البوّابة يصل العرض بنصّه"، 2026-09-22): no
     * in-app string is built, translated, or derived from [name] — see
     * [net.neuralcheck.util.AiV7Presentation.letterCeilingReasonDisplayText].
     *
     * Null has TWO possible causes an app reading only this field cannot
     * tell apart — see [AiAnalysisResponse.categoriesMissingLetterCeilingReason]'s
     * doc for why a second, independently-computed field exists
     * specifically to distinguish them.
     */
    val letterCeilingReason: AiLetterCeilingReasonV7? = null,
    val testIds: List<String>? = null,
    /** Battery-only. Key confirmed ALWAYS present on the battery entry
     *  (value null in the sample — README.md marks this "حقيقي": no
     *  confidence tag applied here, not a parsing gap). Typed [String]
     *  deliberately since no positive value was observed to confirm a
     *  numeric type — cf. [ExpectedPerformance.knowledgeConfidence]
     *  "high"/"medium"/"low" precedent in this same file. */
    val confidence: String? = null,
    /** Battery-only. Sample: "ESTIMATED". */
    val tag: String? = null,
    /**
     * Battery-only. NOT a list of strings — the golden sample showed a
     * MAP keyed by indicator id (e.g. "BAT-THERMAL-EFF", "BAT-FCC"),
     * each value a HETEROGENEOUS object: the four indicators in the
     * sample share only `id`/`status`/`verdict`/`value`/`unit`/`gate`,
     * and each carries different additional fields beyond that (e.g.
     * BAT-FCC has `designCapacityMah`/`ratioToDesign`; BAT-COULOMB has
     * `consistencyGateNote`/`consistencyK`; BAT-R-APPARENT has
     * `unitCaveat`; even the nested `gate` object's own keys differ per
     * indicator). No fixed per-indicator schema exists to type
     * strictly without either dropping fields that don't fit or
     * inventing a schema the contract never specified. Kept as a
     * generic nested map — Gson's default `Map<String, Any?>` handling
     * (already used elsewhere in this codebase for genuinely
     * heterogeneous payloads, e.g. `FirebaseAnalysisService.fetchExistingReport`'s
     * raw `doc.data`) preserves every field losslessly without a wrong
     * fixed shape.
     */
    val indicators: Map<String, Map<String, Any?>>? = null,
    /** Battery-only. NOT in the original textual contract at all —
     *  observed directly in the golden sample (43, 38). Added so the
     *  read layer doesn't silently drop it now that it's known to
     *  exist. */
    @com.google.gson.annotations.JsonAdapter(LenientIntAdapter::class)
    val socAtStart: Int? = null,
    /** Battery-only, see [socAtStart]. */
    @com.google.gson.annotations.JsonAdapter(LenientIntAdapter::class)
    val socAtEnd: Int? = null
)

/**
 * V7 — root `narrative` block. Reuses [BuyerAdvice] verbatim (contract
 * shows the same casual/gamer/photographer shape nested here).
 */
data class AiNarrativeV7(
    val summary: String? = null,
    val details: String? = null,
    val verdict: String? = null,
    val buyerAdvice: BuyerAdvice? = null
)

/**
 * Encapsulates the AI's expected-performance reasoning. All fields are
 * free-form Arabic/Turkish/English text the model produces in the
 * scan's locale; UI displays them as-is. Each per-category field can
 * be null if the model didn't commit to a prediction for that category.
 */
data class ExpectedPerformance(
    @SerializedName(value = "knowledge_confidence", alternate = ["knowledgeConfidence"])
    val knowledgeConfidence: String? = null,        // "high" / "medium" / "low"
    @SerializedName(value = "cpu_throttle_expected", alternate = ["cpuThrottleExpected"])
    val cpuThrottleExpected: String? = null,
    @SerializedName(value = "cpu_temp_expected", alternate = ["cpuTempExpected"])
    val cpuTempExpected: String? = null,
    @SerializedName(value = "gpu_fps_stability_expected", alternate = ["gpuFpsStabilityExpected"])
    val gpuFpsStabilityExpected: String? = null,
    @SerializedName(value = "memory_expected", alternate = ["memoryExpected"])
    val memoryExpected: String? = null,
    @SerializedName(value = "storage_expected", alternate = ["storageExpected"])
    val storageExpected: String? = null,
    @SerializedName(value = "battery_drain_expected", alternate = ["batteryDrainExpected"])
    val batteryDrainExpected: String? = null
)

/**
 * V2 change-vs-prior-scan analysis. Surfaced as a banner card at the
 * top of ReportScreen when present so a returning user sees "what
 * changed since last time" before the verdict body.
 */
data class ChangeAnalysis(
    @SerializedName(value = "has_significant_change", alternate = ["hasSignificantChange"])
    val hasSignificantChange: Boolean? = null,
    @SerializedName(value = "change_direction", alternate = ["changeDirection"])
    val changeDirection: String? = null,            // "stable" / "improved" / "deteriorated"
    @SerializedName(value = "change_reasons", alternate = ["changeReasons"])
    val changeReasons: List<String>? = null,
    /**
     * Phase 1.15 — set true by the backend's re-evaluation logic when a manual
     * test transitioned fail → pass since the prior scan (a previously-broken
     * component now works = a likely sign the device was serviced).
     * [maintenanceNote] carries an optional AI-authored sentence; when absent
     * the UI shows a localized default ([R.string.report_maintenance_suspected]).
     * Null/false on devices with no prior scan or no such transition.
     * Backend field names: `maintenance_suspected` (bool), `maintenance_note` (string).
     */
    @SerializedName(value = "maintenance_suspected", alternate = ["maintenanceSuspected"])
    val maintenanceSuspected: Boolean? = null,
    @SerializedName(value = "maintenance_note", alternate = ["maintenanceNote"])
    val maintenanceNote: String? = null
)

/**
 * Three usage personas the AI provides tailored advice for. Each field is
 * a short paragraph in the requested locale (or null when the AI can't
 * give a useful answer for that persona — e.g. a phone with a great camera
 * but poor thermals → photographer advice present, gamer advice null).
 */
data class BuyerAdvice(
    val casual: String? = null,
    val gamer: String? = null,
    val photographer: String? = null
)

/**
 * Shared between V6 and V7 — same top-level `issues` key, same core
 * fields (severity/title/description/evidence/category). [id] and
 * [evidenceUnit] are V7 additions, confirmed present on every entry of
 * the golden sample (neuralcheck-backend @ `43dee66`,
 * `functions/test/fixtures/golden/v7-client-payload-sample.json`) —
 * e.g. `{"id":"ISS-S-06", ..., "evidence":"2597", "evidenceUnit":"IOPS"}`.
 * The original textual V7 contract only said "issues [ ... ]" with no
 * item shape, so this app assumed the V6 shape unchanged; the sample
 * proved that wrong for these two fields specifically (everything else
 * matched). Null on every V6 issue, which never populated these keys —
 * added here rather than as a separate V7-only class because nothing
 * else differs between the two shapes.
 */
data class AiIssue(
    /** V2 expanded enum: critical | danger | high | warning | info.
     *  V1 only had danger/warning/info; "critical" maps to the most
     *  severe UI treatment (dark red + warning icon). No enum was ever
     *  coded here — this string field accepts anything the backend sends.
     *  V7's golden sample uses a DIFFERENT, uppercase vocabulary confirmed
     *  by reading the sample directly (not the original textual contract,
     *  which just said "issues [ ... ]"): "FAIL" | "WARNING" | "INFO" |
     *  "POSITIVE". [net.neuralcheck.domain.model.IssueSeverity.fromString]
     *  is the single place that maps both vocabularies onto the same
     *  six-value UI enum — "FAIL" -> DANGER (the sample's one real
     *  failure, SEC-04 root-detected, the finding that caps the whole
     *  report's letter), "POSITIVE" -> INFO. Found and fixed in Phase
     *  3-b: "FAIL" had no explicit mapping before that and fell through
     *  to INFO, which would have shown a root-detection failure with the
     *  same blue "informational" styling as a passed test. 2026-09-24:
     *  the catch-all that used to also land unrecognised/absent strings on
     *  INFO now maps them to a sixth value, UNKNOWN, instead — see
     *  [net.neuralcheck.domain.model.IssueSeverity]'s class doc. */
    val severity: String? = null,
    val title: String? = null,
    val description: String? = null,
    /** V2 — supporting data backing the issue (e.g. "battery drop 8%
     *  in 11 min, voltage min 4280 mV"). Rendered as small muted text
     *  beneath description so the user can see what the AI based the
     *  call on. Optional. */
    val evidence: String? = null,
    /** V2 — category tag ("battery", "thermal", "cpu", "gpu", "memory",
     *  "storage"). UI uses it for icon + color grouping. Optional. */
    val category: String? = null,
    /** V7. Stable per-issue identifier (e.g. "ISS-S-06", "DISC-T-01",
     *  "POS-cpu"). Null on V6 issues. */
    val id: String? = null,
    /** V7. Unit for [evidence] when it's a bare number (e.g. "IOPS" for
     *  evidence="2597"). Null when [evidence] is null or already a
     *  descriptive string, and always null on V6 issues. */
    val evidenceUnit: String? = null
)

data class AiCategoryScore(
    val grade: String = "?",
    val score: Int = 0,
    @SerializedName("pass_count")
    val passCount: Int = 0,
    @SerializedName("total_count")
    val totalCount: Int = 0,
    val note: String = "",
    /** V2 — short text describing what's typical for the device's tier
     *  in this category (e.g. "Snapdragon 855 cooled flagships: 25-35%
     *  throttle expected"). CONDITIONAL: the key is absent entirely when
     *  the model didn't author one, so null means "not sent". Rendered
     *  under the note for MEASURED categories only — an unmeasured
     *  category has no measurement to contextualise. */
    @SerializedName(value = "expected_range", alternate = ["expectedRange"])
    val expectedRange: String? = null,
    // ── Unmeasured-category contract (backend fix, 2026-09) ──
    // All optional: absent in every report written before the backend
    // change, so a cached/old report parses unchanged and the UI falls
    // back to the pre-existing behaviour.
    /** Tests in this category the device reported as not_tested. */
    @SerializedName(value = "not_tested_count", alternate = ["notTestedCount"])
    val notTestedCount: Int? = null,
    /** Tests the device reported as unavailable (no such hardware / API). */
    @SerializedName(value = "unavailable_count", alternate = ["unavailableCount"])
    val unavailableCount: Int? = null,
    @SerializedName(value = "fail_count", alternate = ["failCount"])
    val failCount: Int? = null,
    @SerializedName(value = "warning_count", alternate = ["warningCount"])
    val warningCount: Int? = null,
    /** How many tests this category DECLARES in the catalogue. The measured
     *  extent is [totalCount]; the gap is declaredCount - totalCount. Do NOT
     *  derive the gap by adding notTestedCount + unavailableCount — the
     *  payload does not guarantee those two are disjoint. */
    @SerializedName(value = "declared_count", alternate = ["declaredCount"])
    val declaredCount: Int? = null,
    /** False when NOTHING in this category was actually measured (every
     *  test not_tested/unavailable). The authoritative signal; the UI falls
     *  back to (grade == "?" && totalCount == 0) on older reports. */
    val measured: Boolean? = null,
    /**
     * Tests excluded from the grade — the server's OWN count of the gap
     * between what the category declares and what actually ran. Written
     * on every category: gpu arrives excluded_count=3 with
     * declared_count=5 / total_count=2, and a fully unmeasured camera
     * arrives excluded_count=0.
     *
     * Typed Int? deliberately, NOT Int: Gson instantiates this class
     * through Unsafe and never applies constructor defaults, so a
     * non-null Int would arrive as 0 for an ABSENT key and become
     * indistinguishable from camera's genuine 0. Nullable keeps
     * "absent" (legacy report → derive the gap) separate from
     * "present and zero" (no gap to report).
     */
    @SerializedName(value = "excluded_count", alternate = ["excludedCount"])
    val excludedCount: Int? = null,
    /**
     * CONDITIONAL, true-only: present when the server lowered the model's
     * letter to match the numeric score's range. Provenance for the lab,
     * NOT for the buyer — see [aiGrade] for why we don't surface the
     * pre-reconciliation letter either. Absent (null) on every other
     * category, so test it as `reconciled == true`.
     */
    val reconciled: Boolean? = null,
    /** Provenance of [grade], e.g. "ai" / "derived" / "unmeasured".
     *  Diagnostic only — never rendered. */
    @SerializedName(value = "grade_source", alternate = ["gradeSource"])
    val gradeSource: String? = null,
    /**
     * The model's own letter, preserved for transparency when the server
     * did not adopt it.
     *
     * ⚠ MUST NEVER REACH THE USER. An unmeasured camera arrives with
     * grade="?" and ai_grade="B": the model guessed B while not a single
     * camera test ran. Showing it would contradict the "غير مقيسة" state
     * on the very same card and hand the buyer a letter nothing measured.
     * Kept here for payload fidelity and lab analysis only — the website
     * hides it for the same reason. Guarded by
     * AiGradeNeverRenderedTest.
     */
    @SerializedName(value = "ai_grade", alternate = ["aiGrade"])
    val aiGrade: String? = null
)

/**
 * "Complete" means the response carries enough content for the report
 * screen to eventually render a useful page. Used by the scoring engine
 * to decide whether a Firestore-cached doc can serve a re-open or whether
 * we have to call analyzeDevice fresh.
 *
 * A109: this used to check the flat V6 fields (summary/details/verdict/
 * issues/categoryScores) unconditionally — a V7 response, whose content
 * lives under `overall`/`categories`/`narrative` instead, always failed
 * every one of those checks and read as permanently incomplete, so every
 * reopen of a V7 report re-called the AI (cost with no visible crash).
 *
 * Gate is now read from a declared field per shape — [pipeline] plus ONE
 * required field for that shape — instead of enumerating keys:
 *   - V7 ([AiPipeline.V7_FINDINGS_BASED]): [overall]'s `letterSource` —
 *     NOT `letter`. Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦), correcting
 *     this file's own earlier reasoning: the V7 display-shape contract
 *     (`display.overall.letter`) states `letter` is `null` **exactly**
 *     when `noDataAtAll` or `HARNESS_ERROR` — a legitimate, fully-formed,
 *     final answer, not an unfinished write. `letterSource` is what
 *     `buildDisplayShapeV7` assigns unconditionally on every branch
 *     (`"BAND"` / `"GATE:<name>"` / `"NO_DATA"` / `"HARNESS_ERROR"`), so
 *     its presence — not `letter`'s — is the real signal that this
 *     section finished writing. Checking `letter` made every harnessError
 *     report (a real backend response the user is entitled to see, not a
 *     transient half-write) read as incomplete forever, re-billing an AI
 *     call it could never turn into a `letter` on retry either.
 *   - V6 / legacy (pipeline null or [AiPipeline.V6_RAW_TIMESERIES]): the
 *     ORIGINAL multi-signal check, unchanged. NOT collapsed to a single
 *     field — there is no contract document for V6 (it predates this
 *     app's access to any backend contract) telling us which one field
 *     is authoritative, and this file's own comment above already
 *     recorded that grade+score ALONE were historically insufficient
 *     ("A doc with only grade + score is NOT complete"). Guessing a
 *     single field here risks reproducing A109's exact failure mode in
 *     the other direction — flagged in the Phase 3-a report rather than
 *     guessed at.
 */
fun AiAnalysisResponse.isComplete(): Boolean {
    // Order 2026-09-26 (القرار ١٠٢ البند ٢): was a raw `pipeline ==
    // V7_FINDINGS_BASED` / `else` binary check — `cached` (whose root
    // shape depends on content, not the tag — AiPipeline.CACHED's doc)
    // fell into `else` unconditionally, applying the V6 multi-signal check
    // even to a cached document that actually holds V7 content.
    //
    // Order 2026-09-26 (ملحق القرار ١٠٤ البند ١): now delegates to
    // [AiPipeline.isComplete], the one implementation this function used
    // to duplicate (with [net.neuralcheck.domain.model.AiAnalysisResult.isComplete],
    // which checked `overall?.letter` instead of `overall?.letterSource`
    // here — see that shared function's doc). Behaviour on this side is
    // UNCHANGED — this file already read `letterSource`; only the Result
    // side's field and the duplicated V6 branch were consolidated.
    return AiPipeline.isComplete(
        pipeline = pipeline,
        grade = grade,
        hasV7Overall = overall != null,
        overallLetterSource = overall?.letterSource,
        summary = summary,
        details = details,
        verdict = verdict,
        issuesEmpty = issues.isNullOrEmpty(),
        categoryScoresEmpty = categoryScores.isNullOrEmpty()
    )
}
