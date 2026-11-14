package net.neuralcheck.util

import net.neuralcheck.data.remote.model.AiCategoryLetterSource
import net.neuralcheck.data.remote.model.AiLetterCeilingReasonV7
import net.neuralcheck.data.remote.model.AiPipeline

/**
 * Presentation rules for the V7 findings-based report (Phase 3-b), shared
 * by `ReportScreen`'s V7 section. Mirrors [CategoryPresentation]'s role for
 * V6: pure + Android-free classification logic pulled out of the
 * `@Composable` functions so it is unit-testable without drawing them. When
 * written, the module lacked Robolectric/Compose test infrastructure (see the
 * Phase 3-b report's "ما لم أفعله" section); both are test dependencies
 * since 3284cca, and no test renders ReportScreen's V7 section.
 *
 * Every classifier below has an explicit UNKNOWN/none bucket instead of an
 * `else` that quietly reuses one of the named states. Project rule: a
 * missing or unrecognized input must produce a state that surfaces at the
 * top level, never a default, zero, or "looks like success" (the A76/A87/
 * A97 incidents this rule cites all trace back to a silent `else` doing
 * exactly that).
 */
object AiV7Presentation {

    /**
     * Order 2026-09-25 (القرار ٨٦ البند ٣ / القرار ٨٧ البند ٤). Exact
     * literal `categories.<cat>.reason` value the backend emits when a
     * category cannot be graded because its threshold calibration
     * (PLAN-03) hasn't shipped yet — source cited by the coordinator:
     * `categoryGradingLock.js:44-45` in neuralcheck-backend. Confirmed
     * byte-identical in `categories.battery.reason` on BOTH real fixtures
     * this repo holds (`app/src/test/resources/contract/v7-reports-document-sample.json`
     * and `-honor.json`). ONE exported constant (rule 24) — every reader
     * in this repo that recognises this reason code compares against this,
     * never a re-typed copy of the literal.
     */
    const val NO_CALIBRATED_THRESHOLD_REASON =
        "NO_CALIBRATED_THRESHOLD (PLAN-03 لم يُنجَز — انظر batteryLayer.js)"

    // ── The other reason codes, ONE exported constant each (rule 24) ──
    // Order 2026-10-02 (أمر «abstentionReasons» v2): until now these were
    // inline string literals inside [classifyCategoryReason]; the
    // abstentionReasons list ([classifyAbstentionReason]) matches the same
    // codes DIRECTLY (the user's approved table maps the code itself, no
    // state needed), so each literal now lives here once and BOTH functions
    // compare against it — no second typed-out copy of a literal a display
    // distinction depends on. [classifyCategoryReason]'s behaviour is
    // unchanged (same comparisons, same results — its tests are untouched).
    // Relayed from neuralcheck-backend, UNVERIFIED from this repo (القاعدة
    // ٢٩): `buildDisplayShapeV7.js:122-124` (`categoryAbsenceReason`) and the
    // coordinator's enumeration by running the backend code.

    /** `categories.<k>.reason` / `abstentionReasons[].reason`. */
    const val REASON_MANUAL_ATTESTATION_ONLY = "MANUAL_ATTESTATION_ONLY"
    const val REASON_NO_HEALTH_BEARING_TESTS_DECLARED = "NO_HEALTH_BEARING_TESTS_DECLARED"
    const val REASON_DECLARED_BUT_NONE_EXECUTED = "DECLARED_BUT_NONE_EXECUTED"

    /** Battery only per the backend enumeration, but matched by code here,
     *  not by category — the user's table maps the code. NOTE: spelled like
     *  the category STATE `"HARNESS_ERROR"` (see [classifyState]) but a
     *  different concept; this constant is for `reason ==` comparisons only. */
    const val REASON_HARNESS_ERROR = "HARNESS_ERROR"

    /** The battery's own final fallback in `categoryAbsenceReason` (no
     *  letter, and neither `noLetterReason` nor `reason` set). Order
     *  2026-10-06: recognised by BOTH [classifyAbstentionReason] (the
     *  abstentionReasons list) and [classifyCategoryReason] (the category
     *  row, on screen and in the PDF) — same constant, same text
     *  (`report_v7_reason_battery_unmeasured`), no copy. Before 2026-10-06
     *  the row classified it UNKNOWN ("a dead value per the coordinator"),
     *  so the same code showed battery-unmeasured in the list and
     *  `report_v7_state_unknown` on the row — the user ruled that
     *  inconsistent. */
    const val REASON_NO_BATTERY_INDICATORS_MEASURED = "NO_BATTERY_INDICATORS_MEASURED"

    // ── overall.letterSource is documented as gate-name free text
    // ("GATE:ROOT_DETECTED"), NOT this DETERMINISTIC/LLM_ADJUSTED enum —
    // see AiOverallV7.letterSource's own doc. This classifier is for the
    // CATEGORY-level field of the same name only. ──
    enum class LetterSourceKind { DETERMINISTIC, LLM_ADJUSTED, UNKNOWN }

    /**
     * Classifies one category's `letterSource`. Anything other than the two
     * contract-documented strings — including null, blank, a typo, or a
     * future third value the backend adds later — is UNKNOWN. UNKNOWN is
     * deliberately NOT folded into DETERMINISTIC: "no adjustment happened"
     * (DETERMINISTIC) and "we don't know who/what set this letter"
     * (UNKNOWN) are different findings, and a buyer-facing report must not
     * present the second as the reassuring first one.
     */
    fun classifyLetterSource(letterSource: String?): LetterSourceKind = when (letterSource) {
        AiCategoryLetterSource.DETERMINISTIC -> LetterSourceKind.DETERMINISTIC
        AiCategoryLetterSource.LLM_ADJUSTED -> LetterSourceKind.LLM_ADJUSTED
        else -> LetterSourceKind.UNKNOWN
    }

    // ── categories.*.state is an explicit server-authoritative field (a
    // strictly better signal than V6's measured/grade/totalCount inference
    // in CategoryPresentation.isUnmeasured) — but it still needs its own
    // explicit "not one of the documented values" bucket. ──
    //
    // Phase 3-c: ATTESTED_ONLY / HARNESS_ERROR / NO_DATA added to the
    // recognized set. RELAYED, UNVERIFIED — sourced from `categoryStates.js`
    // in neuralcheck-backend, which this app repo cannot read (agent scope
    // boundary; see the Phase 3-c order). No category in the golden sample
    // (`app/src/test/resources/contract/v7-client-payload-sample.json`)
    // uses any of the three — pinned by artificial tests only, same
    // convention as [LetterSourceKind.LLM_ADJUSTED] below. Recognizing them
    // by name (instead of leaving them to fall into UNKNOWN) matters
    // because "a real value this app has never displayed" and "a value
    // nobody on either side has ever heard of" are different findings —
    // this project's rule 30 requires the distinction to be testable, not
    // just the fact that classifyState doesn't crash on them.
    enum class CategoryStateKind { MEASURED, PARTIAL, UNMEASURED, ATTESTED_ONLY, HARNESS_ERROR, NO_DATA, UNKNOWN }

    fun classifyState(state: String?): CategoryStateKind = when (state) {
        "MEASURED" -> CategoryStateKind.MEASURED
        "PARTIAL" -> CategoryStateKind.PARTIAL
        "UNMEASURED" -> CategoryStateKind.UNMEASURED
        "ATTESTED_ONLY" -> CategoryStateKind.ATTESTED_ONLY
        "HARNESS_ERROR" -> CategoryStateKind.HARNESS_ERROR
        "NO_DATA" -> CategoryStateKind.NO_DATA
        else -> CategoryStateKind.UNKNOWN
    }

    /**
     * Order 2026-09-25 (القرار ٨٤ البند ١ / القرار ٨٦ البند ٣ / القرار ٨٧
     * البند ٤). Classifies a category's `(state, reason)` pair into which
     * dedicated text (if any) should replace the generic per-state text —
     * REPLACES showing `reason`'s raw server code on screen at all (rule
     * 22: "الرمز الخام لا يُعرض على الشاشة").
     *
     * [NO_HEALTH_BEARING_TESTS_DECLARED] / [DECLARED_BUT_NONE_EXECUTED]:
     * apply regardless of [state] ("أيّ حالة" per the order) — checked
     * before any state-specific branch.
     *
     * [BATTERY_UNMEASURED] is also returned, regardless of [state], for
     * [REASON_NO_BATTERY_INDICATORS_MEASURED] (order 2026-10-06, the
     * user's decision: "صفّ الفئة لـ NO_BATTERY_INDICATORS_MEASURED يأخذ
     * النصّ المعتمد نفسه الذي في القائمة، من الثوابت المشتركة نفسها") —
     * the same text the abstentionReasons list gives that code, matched
     * against the same constant. Its other source, `(UNMEASURED,
     * NO_CALIBRATED_THRESHOLD_REASON)`, is unchanged below.
     *
     * [BATTERY_UNCALIBRATED] widens 2026-09-25's earlier
     * `isBatteryUncalibratedPartial` (PARTIAL-only) to also cover MEASURED,
     * per this table's own "(PARTIAL أو MEASURED، NO_CALIBRATED_THRESHOLD)"
     * row — that function is superseded and removed, not left alongside
     * this one (would drift on the next reason added).
     *
     * [HIDDEN]: a blank/absent [reason] (nothing to explain — the existing,
     * correct behaviour), OR one of the two pairs the order says to
     * suppress explicitly — (HARNESS_ERROR, "HARNESS_ERROR") and
     * (ATTESTED_ONLY, "MANUAL_ATTESTATION_ONLY") — because the STATE text
     * alone already says everything the reason would repeat. Callers must
     * fall back to the plain per-[CategoryStateKind] text for this case,
     * not show nothing (except when [state] is MEASURED, where there was
     * never anything to show).
     *
     * [UNKNOWN]: a non-blank [reason] that matches none of the above for
     * this [state]. (Until 2026-10-06 this paragraph said it "includes
     * `NO_BATTERY_INDICATORS_MEASURED`, a dead value per the coordinator" —
     * wrong: the server emits that code as the battery's own final
     * fallback in `categoryAbsenceReason`, `buildDisplayShapeV7.js:122-124`
     * (relayed, unverified, القاعدة ٢٩); it is a recognised reason now, see
     * above.) Callers must show
     * [net.neuralcheck.R.string.report_v7_state_unknown] (order 2026-09-25،
     * القرار ٨٧ البند ٥ — the EXISTING string, not a new "حالة غير معروفة"
     * one) and log the raw code, never display it.
     */
    enum class CategoryReasonKind {
        NO_HEALTH_BEARING_TESTS_DECLARED,
        DECLARED_BUT_NONE_EXECUTED,
        BATTERY_UNCALIBRATED,
        BATTERY_UNMEASURED,
        HARNESS_ERROR_UNMEASURED,
        HIDDEN,
        UNKNOWN
    }

    fun classifyCategoryReason(state: String?, reason: String?): CategoryReasonKind {
        if (reason.isNullOrBlank()) return CategoryReasonKind.HIDDEN
        val stateKind = classifyState(state)
        return when {
            reason == REASON_NO_HEALTH_BEARING_TESTS_DECLARED -> CategoryReasonKind.NO_HEALTH_BEARING_TESTS_DECLARED
            reason == REASON_DECLARED_BUT_NONE_EXECUTED -> CategoryReasonKind.DECLARED_BUT_NONE_EXECUTED
            reason == REASON_NO_BATTERY_INDICATORS_MEASURED -> CategoryReasonKind.BATTERY_UNMEASURED
            reason == NO_CALIBRATED_THRESHOLD_REASON &&
                (stateKind == CategoryStateKind.PARTIAL || stateKind == CategoryStateKind.MEASURED) ->
                CategoryReasonKind.BATTERY_UNCALIBRATED
            reason == NO_CALIBRATED_THRESHOLD_REASON && stateKind == CategoryStateKind.UNMEASURED ->
                CategoryReasonKind.BATTERY_UNMEASURED
            reason == REASON_HARNESS_ERROR && stateKind == CategoryStateKind.UNMEASURED ->
                CategoryReasonKind.HARNESS_ERROR_UNMEASURED
            reason == REASON_HARNESS_ERROR && stateKind == CategoryStateKind.HARNESS_ERROR -> CategoryReasonKind.HIDDEN
            reason == REASON_MANUAL_ATTESTATION_ONLY && stateKind == CategoryStateKind.ATTESTED_ONLY -> CategoryReasonKind.HIDDEN
            else -> CategoryReasonKind.UNKNOWN
        }
    }

    /**
     * What an `overall.abstentionReasons[].reason` code means for display.
     * Its own enum — NOT [CategoryReasonKind] — on purpose: that enum's
     * HIDDEN means "say nothing extra, a separate state line on the same
     * category row already covers it", the OPPOSITE of what
     * MANUAL_ATTESTATION_ONLY needs in this list (the mapped text is the
     * only thing shown), and adding an ATTESTED value to it would force a
     * dead branch into the exhaustive `when (reasonKind)` the per-category
     * row (ReportScreen.kt) and the PDF (ReportPdfGenerator.kt) both use.
     * Holds kinds only; every code literal is the single constant above.
     */
    enum class AbstentionReasonKind {
        ATTESTED_ONLY,
        NO_HEALTH_BEARING_TESTS_DECLARED,
        DECLARED_BUT_NONE_EXECUTED,
        BATTERY_UNCALIBRATED,
        HARNESS_ERROR_UNMEASURED,
        BATTERY_UNMEASURED,
        UNKNOWN
    }

    /**
     * Order 2026-10-02 (أمر «abstentionReasons» v2 — **replaces** the
     * 2026-10-01 version, which called [classifyCategoryReason] with a fake
     * `state = "UNMEASURED"`). The user's approved table maps the CODE
     * itself (one row per code, no state involved), so this matches the
     * code DIRECTLY against the shared constants (rule 24) — it does not
     * call [classifyCategoryReason] at all. Why not keep calling it: the
     * table's rows would need a DIFFERENT borrowed state per row
     * (ATTESTED_ONLY for the attestation code, PARTIAL/MEASURED for the
     * calibration code, UNMEASURED for the harness code) — a hidden
     * second table of fake states. (Until 2026-10-06 the sentence continued
     * "and [REASON_NO_BATTERY_INDICATORS_MEASURED] is not a code that
     * function recognises at all" — no longer true: [classifyCategoryReason]
     * recognises it now, state-independently, against the same constant.)
     *
     * The table (user's approval 2026-10-01, relayed by the coordinator —
     * the source file is in his workspace, unverified from this repo):
     *
     * | reason | kind (→ text key, chosen at the call site) |
     * |---|---|
     * | [REASON_MANUAL_ATTESTATION_ONLY] | ATTESTED_ONLY (`report_v7_state_attested_only`) |
     * | [REASON_NO_HEALTH_BEARING_TESTS_DECLARED] | NO_HEALTH_BEARING_TESTS_DECLARED (`report_v7_reason_no_health_bearing_declared`) |
     * | [REASON_DECLARED_BUT_NONE_EXECUTED] | DECLARED_BUT_NONE_EXECUTED (`report_v7_reason_declared_none_executed`, #15) |
     * | [NO_CALIBRATED_THRESHOLD_REASON] | BATTERY_UNCALIBRATED (`report_v7_reason_battery_uncalibrated`, #16) |
     * | [REASON_HARNESS_ERROR] | HARNESS_ERROR_UNMEASURED (`report_v7_reason_harness_error_unmeasured`) |
     * | [REASON_NO_BATTERY_INDICATORS_MEASURED] | BATTERY_UNMEASURED (`report_v7_reason_battery_unmeasured`) |
     * | anything else (incl. a bare category `state` string such as
     *   `"UNMEASURED"` — the backend's `reason || state` fallback — and
     *   null/blank) | UNKNOWN (→ #15) |
     *
     * **Correction this version carries:** the 2026-10-01 version assumed
     * the list only appears when NO category was measured, so it could not
     * reach the "uncalibrated" row and showed the calibration code as "لم
     * تُقَس صحّة البطارية" — a FALSE statement, since that battery WAS
     * measured. The server's own condition is that no category produced a
     * LETTER (`categoryStates.js:836-837`: `noDataAtAll = measuredCategoriesMean
     * === null`; `buildDisplayShapeV7.js:619`: `zeroMeasuredMessage =
     * overall.letter === null && noDataAtAll`) — relayed, unverified — and a
     * battery measured without a letter (uncalibrated) is listed with the
     * calibration code, the most common case.
     *
     * [NO_CALIBRATED_THRESHOLD_REASON] is matched by exact equality, like
     * [classifyCategoryReason]: if the server rewrites the tail of that
     * string, it falls to UNKNOWN (→ #15) in BOTH places consistently.
     *
     * UNKNOWN is the caller's to map to #15 — NOT `report_v7_state_unknown`
     * (where the per-category ROW sends its own unrecognised reason, a
     * different user decision for a different list).
     */
    fun classifyAbstentionReason(reason: String?): AbstentionReasonKind = when (reason) {
        REASON_MANUAL_ATTESTATION_ONLY -> AbstentionReasonKind.ATTESTED_ONLY
        REASON_NO_HEALTH_BEARING_TESTS_DECLARED -> AbstentionReasonKind.NO_HEALTH_BEARING_TESTS_DECLARED
        REASON_DECLARED_BUT_NONE_EXECUTED -> AbstentionReasonKind.DECLARED_BUT_NONE_EXECUTED
        NO_CALIBRATED_THRESHOLD_REASON -> AbstentionReasonKind.BATTERY_UNCALIBRATED
        REASON_HARNESS_ERROR -> AbstentionReasonKind.HARNESS_ERROR_UNMEASURED
        REASON_NO_BATTERY_INDICATORS_MEASURED -> AbstentionReasonKind.BATTERY_UNMEASURED
        else -> AbstentionReasonKind.UNKNOWN
    }

    // ── indicators.*.status / indicators.*.verdict (Phase 3-c) ──
    //
    // `categories.battery.indicators.*` — RELAYED, UNVERIFIED, sourced from
    // `batteryLayer.js` in neuralcheck-backend (out of this repo's bounds,
    // same as categoryStates.js above). The golden sample's four battery
    // indicators (BAT-THERMAL-EFF / BAT-R-APPARENT / BAT-FCC / BAT-COULOMB)
    // are all `status="MEASURED"`, `verdict="THRESHOLD_NOT_CALIBRATED"` —
    // every OTHER value, including a plausible future "ABSTAINED" status or
    // a calibrated pass/fail verdict once PLAN-03 ships real thresholds, is
    // recognized-but-unconfirmed (ABSTAINED) or UNKNOWN (any verdict other
    // than the one documented value) until an actual sample shows it.

    /** `indicators.*.status`. */
    enum class IndicatorStatusKind { MEASURED, ABSTAINED, UNKNOWN }

    fun classifyIndicatorStatus(status: String?): IndicatorStatusKind = when (status) {
        "MEASURED" -> IndicatorStatusKind.MEASURED
        "ABSTAINED" -> IndicatorStatusKind.ABSTAINED
        else -> IndicatorStatusKind.UNKNOWN
    }

    /**
     * `indicators.*.verdict`. Only ONE value is documented at all
     * (`THRESHOLD_NOT_CALIBRATED`, confirmed by all four golden-sample
     * battery indicators) — there is no second named bucket the way
     * [LetterSourceKind] or [CategoryStateKind] have one; every other
     * string, named or not, is UNKNOWN until independently confirmed by a
     * real payload (rule 30: don't fold an unconfirmed future value into a
     * named bucket just because its name looks plausible).
     */
    enum class IndicatorVerdictKind { THRESHOLD_NOT_CALIBRATED, UNKNOWN }

    fun classifyIndicatorVerdict(verdict: String?): IndicatorVerdictKind = when (verdict) {
        "THRESHOLD_NOT_CALIBRATED" -> IndicatorVerdictKind.THRESHOLD_NOT_CALIBRATED
        else -> IndicatorVerdictKind.UNKNOWN
    }

    /**
     * One battery indicator's common fields, classified, plus every OTHER
     * key on its raw map rendered generically.
     *
     * [net.neuralcheck.data.remote.model.AiCategoryV7.indicators] is a heterogeneous map — the four
     * golden-sample indicators share only `id`/`status`/`verdict`/`value`/
     * `unit`/`gate`, and each carries different additional fields beyond
     * that (`BAT-FCC` has `designCapacityMah`/`ratioToDesign`;
     * `BAT-COULOMB` has `consistencyGateNote`/`consistencyK`;
     * `BAT-R-APPARENT` has `unitCaveat`; even `gate`'s own keys differ per
     * indicator — see that class's doc). This project's rule forbids
     * inventing a fixed per-indicator schema to read them, so [otherFields]
     * carries every key beyond the five explicitly-typed ones (`gate`
     * included) as generic key/value pairs, in the map's own iteration
     * order — named and shown, never guessed at or silently dropped (the
     * Phase 3-c order's own wording: "وإن تعذّر عرض مفتاح بلا معرفة شكله،
     * يُسمّى ويُطلَب لا يُخمَّن").
     */
    data class IndicatorReading(
        val id: String?,
        val statusKind: IndicatorStatusKind,
        val statusRaw: String?,
        val verdictKind: IndicatorVerdictKind,
        val verdictRaw: String?,
        val value: Double?,
        val unit: String?,
        val otherFields: List<Pair<String, String>>
    )

    private val INDICATOR_COMMON_KEYS = setOf("id", "status", "verdict", "value", "unit")

    /**
     * Reads one entry of `categories.battery.indicators` (Gson's default
     * `Map<String, Any?>` decoding of a JSON object: numbers arrive as
     * [Double], nested objects as [Map], arrays as [List] — see
     * [net.neuralcheck.data.remote.model.AiCategoryV7.indicators]'s doc for why this stays untyped rather
     * than a fixed data class per indicator).
     */
    fun readIndicator(raw: Map<String, Any?>): IndicatorReading {
        val id = raw["id"] as? String
        val status = raw["status"] as? String
        val verdict = raw["verdict"] as? String
        val value = (raw["value"] as? Number)?.toDouble()
        val unit = raw["unit"] as? String
        val other = raw.entries
            .filter { it.key !in INDICATOR_COMMON_KEYS }
            .map { (k, v) -> k to formatIndicatorValue(v) }
        return IndicatorReading(
            id = id,
            statusKind = classifyIndicatorStatus(status),
            statusRaw = status,
            verdictKind = classifyIndicatorVerdict(verdict),
            verdictRaw = verdict,
            value = value,
            unit = unit,
            otherFields = other
        )
    }

    /**
     * Generic, non-inventing rendering of one raw indicator field value for
     * [IndicatorReading.otherFields] (and for a nested `gate` object's own
     * entries, recursively — `gate` differs in
     * shape per indicator, so it is walked the same generic way as any
     * other unrecognized key rather than special-cased).
     */
    fun formatIndicatorValue(value: Any?): String = when (value) {
        null -> "—"
        is Double -> formatIndicatorNumber(value)
        is Boolean -> value.toString()
        is String -> value
        is Map<*, *> -> value.entries.joinToString(", ") { (k, v) -> "$k=${formatIndicatorValue(v)}" }
        is List<*> -> value.joinToString(", ") { formatIndicatorValue(it) }
        else -> value.toString()
    }

    /**
     * Display rounding only (never applied to stored/raw values — this
     * function's callers are all presentation code, see [formatIndicatorValue]
     * and [net.neuralcheck.util.ReportPdfGenerator]'s indicator row). Whole
     * numbers print with no decimal point (`"3"`, not `"3.00"` — several
     * indicator fields that are conceptually integers, e.g. `intervalCount`,
     * arrive as [Double] through the generic map decoding); everything else
     * gets 2 decimals.
     */
    fun formatIndicatorNumber(value: Double): String {
        val decimals = if (!value.isNaN() && !value.isInfinite() && value == Math.floor(value)) 0 else 2
        return NumberFormat.format(value.toFloat(), decimals)
    }

    /**
     * Coverage percentage for display, rounded to the nearest whole
     * percent. Prefers [coveragePct] — documented "Key confirmed always
     * present" in the golden sample, already on the 0-100 scale — and
     * falls back to [coverage] (the 0-1 fraction form) only when
     * [coveragePct] itself is absent. Returns null — an explicit "no
     * coverage value" case — only when BOTH are absent; the caller must
     * render that as a missing state, never as "0%" (0% is a different,
     * real answer: coverage was measured and is exactly zero).
     */
    fun coveragePercent(coveragePct: Double?, coverage: Double?): Int? {
        if (coveragePct != null) return Math.round(coveragePct).toInt()
        if (coverage != null) return Math.round(coverage * 100.0).toInt()
        return null
    }

    // ── overall-level status: harnessError / noDataAtAll / partialAssessment
    // are not mutually exclusive in the type system (all three are
    // independent nullable Booleans), but they ARE mutually exclusive as
    // findings a buyer should read as ONE top banner, ranked by severity:
    // a harness failure means the run itself broke (worse than "ran fine
    // but only covered part of the device"), which in turn is worse than
    // an ordinary complete run. This function is the single place that
    // ranking is decided, instead of re-deriving it inline at each
    // Composable call site. ──
    //
    // NOT_DECLARED added 2026-09-24 (جولة جرد القيم الافتراضية، البند ح،
    // القرار ٧٦). Before this, `else -> NORMAL` silently read EVERY case
    // where all three flags were null as "ran fine, nothing to report" —
    // indistinguishable from a genuinely complete run. Backend confirmed
    // (relayed 2026-09-24, neuralcheck-backend @ HEAD — analyzeDevice.js:
    // 170/173/176 each coercing through `!!`, buildDisplayShapeV7.js:454/460,
    // verifyReport's `!!pickOverall(...)`; three live samples, none absent)
    // all three fields are ALWAYS emitted on every path this app reads a V7
    // report from, so a null flag can now only mean "never declared" (an
    // older Room-cached result, a pre-`!!`-coercion response, or a
    // malformed/partial doc) — not a real "false" the backend sent.
    enum class OverallStatusKind { HARNESS_ERROR, NO_DATA_AT_ALL, PARTIAL, NOT_DECLARED, NORMAL }

    /**
     * Ranks the three overall-level abstention flags into one status.
     * Each flag is read as `== true` (not `!= false`, per the A76
     * incident this project's rules cite: a missing/null flag must not be
     * read as the affirmative case) so a null on any of them is treated
     * exactly like false — falls through to the next, lower-severity
     * check — rather than silently triggering the abstention banner.
     *
     * @param harnessErrorDeclared true when the raw report's `overall`
     *   object carried the `harnessError` key at all — see
     *   [net.neuralcheck.data.remote.model.isHarnessErrorDeclared]'s doc.
     *   REVERSED-default precedent applies (2026-09-24، جولة جرد القيم
     *   الافتراضية، البند ح, mirroring 0887730/البند د): a caller reading an
     *   [net.neuralcheck.domain.model.AiAnalysisResult] whose
     *   `harnessErrorDeclared` is itself null (not computed — an older
     *   Room-cached row) must pass `false`, never `true` — "presence
     *   unknown" must not be silently read as "confirmed present". Same for
     *   [noDataAtAllDeclared]/[partialAssessmentDeclared].
     * @param noDataAtAllDeclared see [harnessErrorDeclared].
     * @param partialAssessmentDeclared see [harnessErrorDeclared].
     */
    fun classifyOverallStatus(
        harnessError: Boolean?,
        noDataAtAll: Boolean?,
        partialAssessment: Boolean?,
        harnessErrorDeclared: Boolean,
        noDataAtAllDeclared: Boolean,
        partialAssessmentDeclared: Boolean
    ): OverallStatusKind = when {
        harnessError == true -> OverallStatusKind.HARNESS_ERROR
        noDataAtAll == true -> OverallStatusKind.NO_DATA_AT_ALL
        partialAssessment == true -> OverallStatusKind.PARTIAL
        // A `true` value above is a direct, unambiguous positive signal
        // regardless of any field's own declared status (Gson only sets a
        // Boolean to `true` when the JSON literally holds `true`). Only once
        // every flag has failed to fire `true` do we ask whether NORMAL is
        // actually a safe conclusion: it is only if all three keys were
        // confirmed present in the raw report (so their being non-true
        // really does mean "false"). If any one of them was never declared,
        // this app cannot tell "backend said false" from "backend never
        // said" — NOT_DECLARED, not NORMAL.
        !harnessErrorDeclared || !noDataAtAllDeclared || !partialAssessmentDeclared ->
            OverallStatusKind.NOT_DECLARED
        else -> OverallStatusKind.NORMAL
    }

    // ── categories.*.letterCeilingReason (task: "letterCeilingReason في
    // طبقة القراءة والعرض", 2026-09-23) ──
    //
    // Four states, not two — [AiCategoryV7.letterCeilingReason] being
    // Kotlin-`null` is ambiguous by itself (see that field's doc): it means
    // EITHER "this category's raw JSON object explicitly carries the key
    // with value null" (a real, contract-documented answer: no
    // LOW_CATEGORY_COVERAGE gate ceilinged this category) OR "the key was
    // never in the object at all" (an older stored report, predating this
    // field — no answer). [declared] — sourced from
    // [net.neuralcheck.data.remote.model.AiAnalysisResponse.categoriesMissingLetterCeilingReason],
    // NOT derivable from [reason] alone — disambiguates the two. A missing
    // input must produce its own explicit state (rule 22/25), never be
    // silently folded into the valid "no ceiling" answer.
    enum class LetterCeilingReasonKind { NOT_DECLARED, NO_CEILING, PRESENT_NO_TEXT, PRESENT_WITH_TEXT }

    /**
     * Classifies one category's `letterCeilingReason` for display.
     *
     * @param declared true when the category's raw JSON object carried the
     *   `letterCeilingReason` key at all (regardless of value) — i.e. this
     *   category key is NOT a member of
     *   [net.neuralcheck.data.remote.model.AiAnalysisResponse.categoriesMissingLetterCeilingReason].
     *
     *   ⚠ REVERSED 2026-09-23 (القرار ٧٢، البند ٣) from this parameter's
     *   original guidance. Original: a caller with no `categoriesMissingLetterCeilingReason`
     *   set at all (null) should pass `true` for every category ("nothing
     *   known missing"). That default is now WRONG per the backend's
     *   confirmed answer (neuralcheck-backend @ `e8cd67c`,
     *   `analyzeDevice.js:109-114`/`buildDisplayShapeV7.js:288`,
     *   `test/v7AbsenceVsNullFieldsGuard.test.js` 56/56): the backend
     *   ALWAYS emits this key on every category, so a LIVE report's set is
     *   never null — a null set now means ONLY "this signal was never
     *   computed for this result" (an older Room-cached
     *   [net.neuralcheck.domain.model.AiAnalysisResult] from before this
     *   field existed), and that must classify [LetterCeilingReasonKind.NOT_DECLARED],
     *   not [LetterCeilingReasonKind.NO_CEILING]. Callers must therefore
     *   pass `false` here for every category when the whole set is
     *   unavailable — see [net.neuralcheck.presentation.screen.ReportScreen]'s
     *   `AiAnalysisSectionV7` (`?: cats.keys` fallback, reversed from the
     *   prior `?: emptySet()`).
     * @param reason the parsed field itself — meaningful only when [declared].
     */
    fun classifyLetterCeilingReason(
        declared: Boolean,
        reason: AiLetterCeilingReasonV7?
    ): LetterCeilingReasonKind = when {
        !declared -> LetterCeilingReasonKind.NOT_DECLARED
        reason == null -> LetterCeilingReasonKind.NO_CEILING
        reason.text.isNullOrBlank() -> LetterCeilingReasonKind.PRESENT_NO_TEXT
        else -> LetterCeilingReasonKind.PRESENT_WITH_TEXT
    }

    /**
     * The exact text [net.neuralcheck.presentation.screen.ReportScreen]'s
     * `AiCategoryRowV7` renders next to the category letter FOR THE
     * PRESENT_WITH_TEXT CASE ONLY, or `null` for the other three states.
     * Pulled out as its own pure function — same reasoning as every other
     * classifier in this file (see class doc) — so a JVM unit test can
     * prove what the Composable would render without drawing it (no test
     * renders ReportScreen's V7 section; Robolectric and `ui-test-junit4` are
     * test dependencies since 3284cca).
     *
     * Order-of-truth for [PRESENT_WITH_TEXT]: [AiLetterCeilingReasonV7.text]
     * VERBATIM, per the 2026-09-22 order ("لا يُبنى نصّ في التطبيق، ولا
     * يُترجم، ولا يُشتقّ من name") — never [AiLetterCeilingReasonV7.name],
     * never a locale-specific paraphrase.
     *
     * [NO_CEILING] and [PRESENT_NO_TEXT] return `null` — genuine "nothing
     * to report" answers, no honest sentence to invent for either.
     *
     * [NOT_DECLARED] ALSO returns `null` HERE, but is NOT silent on screen
     * overall — order 2026-09-23 (القرار ٧٢) item 3: the caller
     * (`AiCategoryRowV7`) renders the EXISTING generic
     * `report_v7_field_unavailable` string resource for [NOT_DECLARED] as
     * a SEPARATE `Text()` call, specifically so that state is announced
     * rather than silently collapsed into [NO_CEILING]'s "no ceiling"
     * meaning (an older Room-cached result whose
     * `categoriesMissingLetterCeilingReason` signal was never computed
     * must not be read as "this category definitely has no ceiling"). This
     * function itself stays Android-resource-free by design (see class
     * doc) — it is not the single source of what gets shown for every
     * state, only for the one state with real per-category text.
     */
    fun letterCeilingReasonDisplayText(declared: Boolean, reason: AiLetterCeilingReasonV7?): String? =
        when (classifyLetterCeilingReason(declared, reason)) {
            LetterCeilingReasonKind.PRESENT_WITH_TEXT -> reason?.text
            LetterCeilingReasonKind.NOT_DECLARED,
            LetterCeilingReasonKind.NO_CEILING,
            LetterCeilingReasonKind.PRESENT_NO_TEXT -> null
        }

    /**
     * Order 2026-10-07 (A1 + decision S1): the sentence the overall gate
     * card shows — `overall.governingGateReason.text` VERBATIM (same rule
     * as [letterCeilingReasonDisplayText]: "لا يُبنى نصّ في التطبيق، ولا
     * يُترجم، ولا يُشتقّ من name"), or `null` when it is null/blank.
     *
     * Takes ONLY the `text` string, not the
     * [net.neuralcheck.data.remote.model.AiGoverningGateReasonV7] object —
     * deliberately, the same way [harnessErrorDisplayText] takes only the
     * message: a caller holding just the sentence cannot fall back to
     * `name` or `source` (decision S1: "ولا يُعرض الاسم … ولا المصدر"), and
     * a rename of either can never reach the screen through this path.
     */
    fun governingGateSentenceOrNull(text: String?): String? = text?.takeIf { it.isNotBlank() }

    // ── root narrativeIncomplete (order 2026-09-23، القرار ٧٢، البند ٢) ──
    //
    // Three states: absent from the raw report entirely (an older report —
    // must NOT be read as "complete"), present and false (complete),
    // present and true (show the banner). [declared] is sourced from
    // [net.neuralcheck.data.remote.model.AiAnalysisResponse.narrativeIncompleteDeclared] —
    // same absent-vs-null reasoning as [classifyLetterCeilingReason]'s
    // `declared` parameter, computed by a SEPARATE presence check
    // (`V7RootFieldPresence.kt`'s `isNarrativeIncompleteDeclared`, not the
    // categories-level one, which order item 3 froze pending a backend
    // answer — this is new code for a different field, not a change to
    // that frozen mechanism).
    enum class NarrativeIncompleteKind { NOT_DECLARED, COMPLETE, INCOMPLETE }

    /**
     * @param declared true when the report's raw JSON carried the
     *   `narrativeIncomplete` key at all. Callers reading an
     *   [net.neuralcheck.domain.model.AiAnalysisResult] whose
     *   `narrativeIncompleteDeclared` is itself null (not computed — an
     *   older Room-cached row, or a hand-built test object) should pass
     *   `false`.
     *
     *   ⚠ REVERSED 2026-09-24 (جولة جرد القيم الافتراضية، البند د) from
     *   this parameter's original guidance ("pass `true`: nothing is KNOWN
     *   to be missing"). That default silently classified every
     *   pre-existing Room row as COMPLETE — the exact same shape of bug
     *   [classifyLetterCeilingReason]'s own `declared` parameter doc
     *   describes being reversed for, immediately below, on 2026-09-23
     *   (القرار ٧٢، البند ٣: `?: emptySet()` -> `?: cats.keys`). `false`
     *   now routes that case to [NarrativeIncompleteKind.NOT_DECLARED],
     *   which [net.neuralcheck.presentation.screen.ReportScreen]'s
     *   `AiAnalysisSectionV7` renders as its own explicit line (the
     *   existing generic `report_v7_field_unavailable` string), not
     *   silently as [NarrativeIncompleteKind.COMPLETE].
     */
    fun classifyNarrativeIncomplete(declared: Boolean, narrativeIncomplete: Boolean?): NarrativeIncompleteKind = when {
        !declared -> NarrativeIncompleteKind.NOT_DECLARED
        narrativeIncomplete == true -> NarrativeIncompleteKind.INCOMPLETE
        else -> NarrativeIncompleteKind.COMPLETE
    }

    /**
     * Whether [net.neuralcheck.presentation.screen.ReportScreen]'s V7
     * section should show the fixed `report_v7_narrative_incomplete`
     * string resource banner. Pulled out as its own pure function — same
     * reasoning as [letterCeilingReasonDisplayText] — so a JVM unit test
     * can prove the Composable's decision without drawing it (no test
     * renders ReportScreen's V7 section; Robolectric is a test dependency since
     * 3284cca). Unlike [letterCeilingReasonDisplayText] this returns
     * a Boolean, not text: the banner's wording is server-independent,
     * user-specified fixed copy (a single Android string resource), never
     * built or chosen per-report.
     */
    fun shouldShowNarrativeIncompleteBanner(declared: Boolean, narrativeIncomplete: Boolean?): Boolean =
        classifyNarrativeIncomplete(declared, narrativeIncomplete) == NarrativeIncompleteKind.INCOMPLETE

    // ── overall grade/percentage suppression on V7 (order 2026-09-24،
    // القرار ٧٦، البند ١) ──
    //
    // "لا تُعرض الدرجة ولا النسبة على V7 في أي شاشة، ومنها GradeSummaryCard."
    // V7 responses never carry `response.score` — AiScoringEngine.kt:395
    // currently coerces that absence to 0f (a fabricated "0%" a buyer would
    // otherwise see), and that coercion is NOT fixed by this function: the
    // proper fix (making the absence a real null end-to-end) is BLOCKED
    // pending a Room schema question (`ScanScoreEntity.overallScore` is
    // `Float` non-null, no exported schema for the distributed
    // versionCode-10 database exists in this repo to build/test a safe
    // migration against — see this task's report). This function only
    // decides whether to SHOW the (still not-yet-correctable) value —
    // independent of whether it's ever a real null.

    /**
     * Whether [net.neuralcheck.presentation.screen.ReportScreen] should
     * render [net.neuralcheck.presentation.screen.GradeSummaryCard]'s
     * grade badge for this report's pipeline. Pulled out as its own pure
     * function — same reasoning as every other classifier in this file —
     * so a JVM unit test can prove the Composable's decision without
     * drawing it (Robolectric and Compose test infrastructure are test dependencies since 3284cca).
     *
     * Order 2026-09-26 (القرار ١٠٢ البند ٢): now routed through
     * [AiPipeline.classifyShape] instead of a raw `pipeline !=
     * V7_FINDINGS_BASED` check — `cached` (root shape depends on content,
     * not the tag) used to always show this card, even when its actual
     * content was V7 (no letter for this card's `grade: String` parameter
     * to display meaningfully) or neither shape at all. Shows only for a
     * real V6 shape now; hidden for V7 (unchanged behaviour) AND for an
     * unrecognised/UNKNOWN shape (nothing meaningful to show — showing an
     * empty badge would be worse than showing nothing).
     */
    fun shouldShowGradeSummary(pipeline: String?, hasRootLetterOrGrade: Boolean, hasV7Overall: Boolean): Boolean =
        AiPipeline.classifyShape(pipeline, hasRootLetterOrGrade, hasV7Overall) == AiPipeline.ShapeKind.V6

    /**
     * Order 2026-10-01 (أمر القرارات، A8/S-13). The HARNESS_ERROR branch in
     * both ReportScreen.kt's `AiV7OverallCard` and
     * [net.neuralcheck.data.remote.V7OverallSummaryReader] used to show
     * `overall.harnessErrorReason` (a raw internal code, e.g.
     * "FUNCTIONAL_RESULTS_UNAVAILABLE" — see the Honor B fixture) VERBATIM
     * to the buyer — exactly rule 22's "الرمز الخام لا يُعرض على الشاشة".
     * [harnessErrorMessage] is the new human-readable field the backend is
     * adding specifically to replace it (RELAYED, UNVERIFIED — see
     * [net.neuralcheck.data.remote.model.AiOverallV7.harnessErrorMessage]'s
     * own doc). This function is the ONE place that decides what to show:
     * [harnessErrorReason] is deliberately not a parameter here at all — a
     * caller cannot accidentally fall back to it.
     *
     * Returns `null` when [harnessErrorMessage] is blank/absent — callers
     * must show their own "unavailable" text for that case (this round:
     * [net.neuralcheck.R.string.report_v7_harness_error_message_unavailable]
     * — PROPOSED, not finalized by the user yet, per this round's order:
     * "فاقترح ما يُعرض حين يغيب، ولا تعدّه نهائياً"), never the raw
     * [harnessErrorReason] and never silence.
     */
    fun harnessErrorDisplayText(harnessErrorMessage: String?): String? =
        harnessErrorMessage?.takeIf { it.isNotBlank() }
}
