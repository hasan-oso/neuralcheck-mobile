package net.neuralcheck.util

import com.google.gson.Gson
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiLetterCeilingReasonV7
import net.neuralcheck.data.remote.model.AiPipeline
import net.neuralcheck.data.remote.model.categoriesMissingLetterCeilingReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Phase 3-b — V7 report-display presentation rules. NEW tests: nothing in
 * this codebase read `categories.*.letterSource`, `overall.coverage(Pct)`,
 * or the `harnessError`/`noDataAtAll`/`partialAssessment` triad before this
 * phase (Phase 3-a captured the fields but deliberately built no display —
 * see AiAnalysisResult.kt's doc on the V7 block), so there is no prior test
 * to replace (rule 23).
 *
 * `letterSource` VALUES used below for the DETERMINISTIC case come from the
 * real golden sample (`app/src/test/resources/contract/
 * v7-client-payload-sample.json` — every one of its 15 categories is
 * DETERMINISTIC, confirmed by reading that file directly, not by memory).
 *
 * The LLM_ADJUSTED case has NO sample coverage — the contract README says
 * so explicitly ("adjustmentReason ... this field's non-null shape
 * (LLM_ADJUSTED case) is still unconfirmed by this sample" — see
 * AiCategoryV7's KDoc). The tests below marked ARTIFICIAL build that input
 * by hand; they are not claimed to be sample-derived anywhere in this file
 * or its test names.
 */
class AiV7PresentationTest {

    // ── classifyLetterSource ──

    @Test
    fun `DETERMINISTIC is recognised - the sample's value for every one of its 15 categories`() {
        assertEquals(
            AiV7Presentation.LetterSourceKind.DETERMINISTIC,
            AiV7Presentation.classifyLetterSource("DETERMINISTIC")
        )
    }

    @Test
    fun `ARTIFICIAL - LLM_ADJUSTED is recognised even though no golden-sample category is ever this value`() {
        // Built by hand: the golden sample has zero LLM_ADJUSTED categories
        // (README.md, "adjustmentReason ... حقيقي" entries are all null/
        // DETERMINISTIC). This pins the OTHER branch of the two-value
        // contract so it isn't first exercised by a real payload in prod.
        assertEquals(
            AiV7Presentation.LetterSourceKind.LLM_ADJUSTED,
            AiV7Presentation.classifyLetterSource("LLM_ADJUSTED")
        )
    }

    @Test
    fun `null letterSource is UNKNOWN, not silently DETERMINISTIC`() {
        // A category missing this required-by-contract field must not be
        // presented as "no adjustment happened" — that is a specific claim
        // ("DETERMINISTIC") the app has no basis to make about it.
        assertEquals(
            AiV7Presentation.LetterSourceKind.UNKNOWN,
            AiV7Presentation.classifyLetterSource(null)
        )
    }

    @Test
    fun `an unrecognised letterSource string is UNKNOWN, not folded into either known state`() {
        assertEquals(
            AiV7Presentation.LetterSourceKind.UNKNOWN,
            AiV7Presentation.classifyLetterSource("deterministic") // wrong case
        )
        assertEquals(
            AiV7Presentation.LetterSourceKind.UNKNOWN,
            AiV7Presentation.classifyLetterSource("SOME_FUTURE_VALUE")
        )
    }

    // ── classifyState ──

    @Test
    fun `the three documented states from the golden sample classify correctly`() {
        // cpu/display/memory/wireless = MEASURED; battery/gpu/gps/security/
        // sensors/cellular = PARTIAL; thermal/camera/audio/input = UNMEASURED
        // — all 15 categories in the sample land in exactly these three.
        assertEquals(AiV7Presentation.CategoryStateKind.MEASURED, AiV7Presentation.classifyState("MEASURED"))
        assertEquals(AiV7Presentation.CategoryStateKind.PARTIAL, AiV7Presentation.classifyState("PARTIAL"))
        assertEquals(AiV7Presentation.CategoryStateKind.UNMEASURED, AiV7Presentation.classifyState("UNMEASURED"))
    }

    @Test
    fun `a null or unrecognised state is UNKNOWN, not silently MEASURED`() {
        // The dangerous silent default here would be treating an absent
        // state as MEASURED (the "everything is fine" reading) — this pins
        // that neither null nor a typo/future value can land there.
        assertEquals(AiV7Presentation.CategoryStateKind.UNKNOWN, AiV7Presentation.classifyState(null))
        assertEquals(AiV7Presentation.CategoryStateKind.UNKNOWN, AiV7Presentation.classifyState("measured"))
        assertEquals(AiV7Presentation.CategoryStateKind.UNKNOWN, AiV7Presentation.classifyState("BROKEN"))
    }

    // ── classifyState — Phase 3-c: ATTESTED_ONLY / HARNESS_ERROR / NO_DATA ──
    //
    // Rule 30: every value of an enumerated field is tested by what the
    // reader PRODUCES from it, not just by what arrived in one sample. The
    // golden sample uses none of these three (its 15 categories are all
    // MEASURED/PARTIAL/UNMEASURED) — RELAYED, UNVERIFIED from
    // categoryStates.js in neuralcheck-backend, out of this repo's bounds.
    // Pinned here by name so a real occurrence classifies precisely
    // instead of collapsing into UNKNOWN, which this project's rules treat
    // as a materially different finding ("never heard of this string" vs.
    // "a real, named value with no display built for it yet").

    @Test
    fun `ATTESTED_ONLY classifies to its own bucket, not UNKNOWN or UNMEASURED`() {
        assertEquals(AiV7Presentation.CategoryStateKind.ATTESTED_ONLY, AiV7Presentation.classifyState("ATTESTED_ONLY"))
    }

    @Test
    fun `HARNESS_ERROR classifies to its own bucket, not UNKNOWN or UNMEASURED`() {
        assertEquals(AiV7Presentation.CategoryStateKind.HARNESS_ERROR, AiV7Presentation.classifyState("HARNESS_ERROR"))
    }

    @Test
    fun `NO_DATA classifies to its own bucket, not UNKNOWN or UNMEASURED`() {
        assertEquals(AiV7Presentation.CategoryStateKind.NO_DATA, AiV7Presentation.classifyState("NO_DATA"))
    }

    @Test
    fun `a genuinely novel state string still lands in UNKNOWN, not one of the newly-added buckets`() {
        // The literal example the Phase 3-c order gives: a value that must
        // not be folded into any listed state.
        assertEquals(AiV7Presentation.CategoryStateKind.UNKNOWN, AiV7Presentation.classifyState("SOMETHING_NEW"))
    }

    // ── classifyIndicatorStatus (indicators.*.status) — Phase 3-c ──
    //
    // RELAYED, UNVERIFIED from batteryLayer.js in neuralcheck-backend, out
    // of this repo's bounds. The golden sample's four battery indicators
    // are all "MEASURED" — "ABSTAINED" has no sample coverage and is
    // pinned by an artificial value, same convention as
    // `LetterSourceKind.LLM_ADJUSTED` above.

    @Test
    fun `MEASURED is the golden sample's value for all four battery indicators`() {
        assertEquals(AiV7Presentation.IndicatorStatusKind.MEASURED, AiV7Presentation.classifyIndicatorStatus("MEASURED"))
    }

    @Test
    fun `ARTIFICIAL - ABSTAINED is recognised even though no golden-sample indicator is ever this value`() {
        assertEquals(AiV7Presentation.IndicatorStatusKind.ABSTAINED, AiV7Presentation.classifyIndicatorStatus("ABSTAINED"))
    }

    @Test
    fun `a null or unrecognised indicator status is UNKNOWN, not silently MEASURED`() {
        assertEquals(AiV7Presentation.IndicatorStatusKind.UNKNOWN, AiV7Presentation.classifyIndicatorStatus(null))
        assertEquals(AiV7Presentation.IndicatorStatusKind.UNKNOWN, AiV7Presentation.classifyIndicatorStatus("measured"))
        assertEquals(AiV7Presentation.IndicatorStatusKind.UNKNOWN, AiV7Presentation.classifyIndicatorStatus("SOMETHING_NEW"))
    }

    // ── classifyIndicatorVerdict (indicators.*.verdict) — Phase 3-c ──
    //
    // Only ONE value is documented at all: THRESHOLD_NOT_CALIBRATED,
    // confirmed by all four golden-sample battery indicators. There is no
    // second named bucket — every other string, including a plausible
    // future calibrated verdict, is UNKNOWN until an actual sample shows it.

    @Test
    fun `THRESHOLD_NOT_CALIBRATED is the golden sample's value for all four battery indicators`() {
        assertEquals(
            AiV7Presentation.IndicatorVerdictKind.THRESHOLD_NOT_CALIBRATED,
            AiV7Presentation.classifyIndicatorVerdict("THRESHOLD_NOT_CALIBRATED")
        )
    }

    @Test
    fun `a null or any other verdict string is UNKNOWN - there is no second named bucket yet`() {
        assertEquals(AiV7Presentation.IndicatorVerdictKind.UNKNOWN, AiV7Presentation.classifyIndicatorVerdict(null))
        assertEquals(AiV7Presentation.IndicatorVerdictKind.UNKNOWN, AiV7Presentation.classifyIndicatorVerdict("PASS"))
        assertEquals(AiV7Presentation.IndicatorVerdictKind.UNKNOWN, AiV7Presentation.classifyIndicatorVerdict("threshold_not_calibrated"))
    }

    // ── readIndicator / formatIndicatorValue / formatIndicatorNumber ──
    //
    // Rule 30: these test what the reader PRODUCES from an indicator's raw
    // map (classification + generic field rendering), not just that Gson
    // parsed the map correctly (V7GoldenSampleTest already covers arrival).

    @Test
    fun `readIndicator extracts the five common fields and classifies status and verdict`() {
        val raw: Map<String, Any?> = mapOf(
            "id" to "BAT-THERMAL-EFF",
            "status" to "MEASURED",
            "verdict" to "THRESHOLD_NOT_CALIBRATED",
            "value" to 14.810216559054968,
            "unit" to "°C/Wh"
        )
        val reading = AiV7Presentation.readIndicator(raw)
        assertEquals("BAT-THERMAL-EFF", reading.id)
        assertEquals(AiV7Presentation.IndicatorStatusKind.MEASURED, reading.statusKind)
        assertEquals("MEASURED", reading.statusRaw)
        assertEquals(AiV7Presentation.IndicatorVerdictKind.THRESHOLD_NOT_CALIBRATED, reading.verdictKind)
        assertEquals(14.810216559054968, reading.value!!, 1e-9)
        assertEquals("°C/Wh", reading.unit)
        assertTrue("no keys beyond the five common ones were supplied", reading.otherFields.isEmpty())
    }

    @Test
    fun `readIndicator renders every key beyond the five common ones generically, never dropping or hand-picking`() {
        // BAT-FCC shape from the golden sample: designCapacityMah/
        // ratioToDesign beyond the common five, plus a nested gate object
        // whose OWN keys differ from every other indicator's gate.
        val raw: Map<String, Any?> = mapOf(
            "id" to "BAT-FCC",
            "status" to "MEASURED",
            "verdict" to "THRESHOLD_NOT_CALIBRATED",
            "value" to 3782.7523472222224,
            "unit" to "mAh",
            "intervalCount" to 3.0,
            "gate" to mapOf("intervalCount" to 3.0, "required" to 2.0, "passes" to true),
            "designCapacityMah" to 4400.0,
            "ratioToDesign" to 0.8597164425505051
        )
        val reading = AiV7Presentation.readIndicator(raw)
        val asMap = reading.otherFields.toMap()
        // Every non-common key survives, named exactly as it arrived.
        assertEquals(setOf("intervalCount", "gate", "designCapacityMah", "ratioToDesign"), asMap.keys)
        // A whole-number Double prints without a trailing ".0" (display
        // rounding only, per formatIndicatorNumber's doc).
        assertEquals("3", asMap["intervalCount"])
        assertEquals("4400", asMap["designCapacityMah"])
        // The differently-shaped nested `gate` object is walked generically
        // (never a fixed per-indicator schema), each sub-key visible.
        assertTrue(asMap["gate"]!!.contains("intervalCount=3"))
        assertTrue(asMap["gate"]!!.contains("required=2"))
        assertTrue(asMap["gate"]!!.contains("passes=true"))
    }

    @Test
    fun `readIndicator on a missing or wrongly-typed common field classifies UNKNOWN, never guesses`() {
        val raw: Map<String, Any?> = mapOf("id" to "BAT-COULOMB", "value" to 139.9)
        val reading = AiV7Presentation.readIndicator(raw)
        assertEquals(AiV7Presentation.IndicatorStatusKind.UNKNOWN, reading.statusKind)
        assertEquals(AiV7Presentation.IndicatorVerdictKind.UNKNOWN, reading.verdictKind)
        assertEquals(null, reading.statusRaw)
        assertEquals(null, reading.unit)
    }

    @Test
    fun `formatIndicatorValue renders null, boolean, string, nested map and list generically`() {
        assertEquals("—", AiV7Presentation.formatIndicatorValue(null))
        assertEquals("true", AiV7Presentation.formatIndicatorValue(true))
        assertEquals("false", AiV7Presentation.formatIndicatorValue(false))
        assertEquals("some text", AiV7Presentation.formatIndicatorValue("some text"))
        assertEquals(
            "required=5, passes=true",
            AiV7Presentation.formatIndicatorValue(mapOf("required" to 5.0, "passes" to true))
        )
        assertEquals("1, 2, 3", AiV7Presentation.formatIndicatorValue(listOf(1.0, 2.0, 3.0)))
    }

    @Test
    fun `formatIndicatorNumber drops the decimal point for whole numbers and keeps two decimals otherwise`() {
        assertEquals("3", AiV7Presentation.formatIndicatorNumber(3.0))
        assertEquals("4400", AiV7Presentation.formatIndicatorNumber(4400.0))
        assertEquals("14.81", AiV7Presentation.formatIndicatorNumber(14.810216559054968))
        assertEquals("0.86", AiV7Presentation.formatIndicatorNumber(0.8597164425505051))
    }

    @Test
    fun `golden-sample integration - all four real battery indicators classify MEASURED and THRESHOLD_NOT_CALIBRATED`() {
        // Ties classifyIndicatorStatus/Verdict to the real contract file
        // rather than hand-typed strings, per project rule 5.
        val battery = loadSampleCategories()["battery"]!!
        val indicators = battery.indicators!!
        assertEquals(4, indicators.size)
        for ((id, raw) in indicators) {
            val reading = AiV7Presentation.readIndicator(raw)
            assertEquals("$id.status should classify MEASURED", AiV7Presentation.IndicatorStatusKind.MEASURED, reading.statusKind)
            assertEquals(
                "$id.verdict should classify THRESHOLD_NOT_CALIBRATED",
                AiV7Presentation.IndicatorVerdictKind.THRESHOLD_NOT_CALIBRATED,
                reading.verdictKind
            )
            assertEquals(id, reading.id)
        }
        assertEquals(43, battery.socAtStart)
        assertEquals(38, battery.socAtEnd)
    }

    // ── coveragePercent ──

    @Test
    fun `coveragePct wins when both are present - the overall block's own 45-vs-0_45 case`() {
        // Sample: overall.coveragePct = 45, overall.coverage = 0.4503809523809524.
        // Rounding the fraction form would give 45 too here, so this alone
        // doesn't prove precedence - the next test does.
        assertEquals(45, AiV7Presentation.coveragePercent(coveragePct = 45.0, coverage = 0.4503809523809524))
    }

    @Test
    fun `coveragePct wins over coverage even when they disagree`() {
        assertEquals(70, AiV7Presentation.coveragePercent(coveragePct = 70.0, coverage = 0.10))
    }

    @Test
    fun `coverage fraction is used only when coveragePct is absent`() {
        // gpu in the sample: coverage = 0.4 (no coveragePct on the category
        // shape at all - that field only exists on the overall block).
        assertEquals(40, AiV7Presentation.coveragePercent(coveragePct = null, coverage = 0.4))
    }

    @Test
    fun `both absent yields null, never a fabricated zero percent`() {
        assertNull(AiV7Presentation.coveragePercent(coveragePct = null, coverage = null))
    }

    @Test
    fun `a genuine zero coverage still reports zero percent`() {
        // thermal in the sample: coverage = 0, DECLARED_BUT_NONE_EXECUTED.
        // Measured-and-zero is a real, different answer from "no value at
        // all" and must survive.
        assertEquals(0, AiV7Presentation.coveragePercent(coveragePct = null, coverage = 0.0))
    }

    @Test
    fun `rounding is nearest not truncated`() {
        // gps: coverage = 0.3333333333333333 -> 33, not 33.33 or 34-by-ceiling.
        assertEquals(33, AiV7Presentation.coveragePercent(coveragePct = null, coverage = 0.3333333333333333))
        // cellular: 0.7142857142857143 -> 71.
        assertEquals(71, AiV7Presentation.coveragePercent(coveragePct = null, coverage = 0.7142857142857143))
    }

    // ── classifyOverallStatus ──
    //
    // 2026-09-24 (جولة جرد القيم الافتراضية، البند ح، القرار ٧٦): the
    // function signature grew three required `*Declared` parameters. Every
    // pre-existing test below is updated to pass them (`true` wherever the
    // scenario represents a live, backend-confirmed report — which is every
    // scenario these particular tests modeled, since the golden sample and
    // all hand-built true/false combinations here represent a report where
    // the flags genuinely ARE declared). Per rule 23, `all three false or
    // null is NORMAL`'s SECOND assertion (`null,null,null -> NORMAL`) is
    // REPLACED, not kept alongside a new test — it pinned exactly the
    // behaviour this order removes (an undeclared triad silently read as a
    // normal, complete run).

    @Test
    fun `the golden sample's overall block is PARTIAL - partialAssessment true but harnessError and noDataAtAll false`() {
        // Sample: harnessError=false, noDataAtAll=false, partialAssessment=true,
        // all three declared (confirmed present in the raw report).
        assertEquals(
            AiV7Presentation.OverallStatusKind.PARTIAL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = true,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
    }

    @Test
    fun `all three false and declared is NORMAL - a real, confirmed complete run`() {
        assertEquals(
            AiV7Presentation.OverallStatusKind.NORMAL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = false,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
    }

    @Test
    fun `all three null and undeclared is NOT_DECLARED, never NORMAL`() {
        // Replaces the pre-2026-09-24 second assertion of `all three false
        // or null is NORMAL` (rule 23) — backend confirmed (relayed
        // 2026-09-24) all three fields are ALWAYS emitted on a live report,
        // so an undeclared triad can only be an older Room-cached result or
        // a malformed doc, never a genuine "nothing to report" answer.
        assertEquals(
            AiV7Presentation.OverallStatusKind.NOT_DECLARED,
            AiV7Presentation.classifyOverallStatus(
                harnessError = null, noDataAtAll = null, partialAssessment = null,
                harnessErrorDeclared = false, noDataAtAllDeclared = false, partialAssessmentDeclared = false
            )
        )
    }

    @Test
    fun `just one of the three undeclared, the other two confirmed false, is still NOT_DECLARED`() {
        // This app cannot tell "backend said false" from "backend never
        // said" for the one undeclared field, so it cannot conclude NORMAL
        // even though the other two are confirmed false.
        assertEquals(
            AiV7Presentation.OverallStatusKind.NOT_DECLARED,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = null,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = false
            )
        )
    }

    @Test
    fun `a true value wins regardless of the OTHER fields' declared status`() {
        // A `true` is a direct positive signal (Gson only sets it from a
        // literal JSON `true`) — it must fire its category even when a
        // sibling field's declared signal is false, not be pre-empted by
        // NOT_DECLARED.
        assertEquals(
            AiV7Presentation.OverallStatusKind.PARTIAL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = true,
                harnessErrorDeclared = false, noDataAtAllDeclared = false, partialAssessmentDeclared = false
            )
        )
    }

    @Test
    fun `harnessError outranks noDataAtAll and partialAssessment when more than one is true`() {
        assertEquals(
            AiV7Presentation.OverallStatusKind.HARNESS_ERROR,
            AiV7Presentation.classifyOverallStatus(
                harnessError = true, noDataAtAll = true, partialAssessment = true,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
    }

    @Test
    fun `noDataAtAll outranks partialAssessment when both are true`() {
        assertEquals(
            AiV7Presentation.OverallStatusKind.NO_DATA_AT_ALL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = true, partialAssessment = true,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
    }

    // ── the three states per field (true / false / undeclared), per rule
    // 30 and the coordinator's explicit instruction for this order's item ح
    // — each of the triad's three fields tested independently while holding
    // the other two at a fixed, declared-false baseline. ──

    @Test
    fun `harnessError - true, false, undeclared - three distinct outcomes`() {
        assertEquals(
            AiV7Presentation.OverallStatusKind.HARNESS_ERROR,
            AiV7Presentation.classifyOverallStatus(
                harnessError = true, noDataAtAll = false, partialAssessment = false,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
        assertEquals(
            AiV7Presentation.OverallStatusKind.NORMAL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = false,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
        assertEquals(
            AiV7Presentation.OverallStatusKind.NOT_DECLARED,
            AiV7Presentation.classifyOverallStatus(
                harnessError = null, noDataAtAll = false, partialAssessment = false,
                harnessErrorDeclared = false, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
    }

    @Test
    fun `noDataAtAll - true, false, undeclared - three distinct outcomes`() {
        assertEquals(
            AiV7Presentation.OverallStatusKind.NO_DATA_AT_ALL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = true, partialAssessment = false,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
        assertEquals(
            AiV7Presentation.OverallStatusKind.NORMAL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = false,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
        assertEquals(
            AiV7Presentation.OverallStatusKind.NOT_DECLARED,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = null, partialAssessment = false,
                harnessErrorDeclared = true, noDataAtAllDeclared = false, partialAssessmentDeclared = true
            )
        )
    }

    @Test
    fun `partialAssessment - true, false, undeclared - three distinct outcomes`() {
        assertEquals(
            AiV7Presentation.OverallStatusKind.PARTIAL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = true,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
        assertEquals(
            AiV7Presentation.OverallStatusKind.NORMAL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = false,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
        assertEquals(
            AiV7Presentation.OverallStatusKind.NOT_DECLARED,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = null,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = false
            )
        )
    }

    // ── golden-sample integration: same loader V7GoldenSampleTest uses ──

    private fun loadSampleCategories() = InputStreamReader(
        javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")
            ?: error("contract/v7-client-payload-sample.json not found on the test classpath"),
        Charsets.UTF_8
    ).use { reader -> Gson().fromJson(reader, AiAnalysisResponse::class.java) }.categories!!

    @Test
    fun `every one of the golden sample's 15 categories classifies as DETERMINISTIC`() {
        // Ties classifyLetterSource to the real contract file rather than a
        // hand-typed string, per project rule 5 (a measurement/contract
        // claim must cite a named measured file, not memory). Confirms the
        // README's "كل فئة في العيّنة DETERMINISTIC" claim through this
        // module's own classifier, not by re-reading the README.
        val categories = loadSampleCategories()
        assertEquals(15, categories.size)
        for ((key, cat) in categories) {
            assertEquals(
                "$key.letterSource should classify DETERMINISTIC",
                AiV7Presentation.LetterSourceKind.DETERMINISTIC,
                AiV7Presentation.classifyLetterSource(cat.letterSource)
            )
        }
    }

    @Test
    fun `the golden sample's overall coveragePct classifies to 46 through the real parsed object`() {
        // Rule 23 — resynced 2026-09-30 from neuralcheck-backend@c7446a4:
        // overall.coveragePct was 45.0 (rounds to 45), now 45.9 (rounds to
        // 46 via Math.round) — see AiV7Presentation.kt's coveragePercent.
        val response = InputStreamReader(
            javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")!!,
            Charsets.UTF_8
        ).use { reader -> Gson().fromJson(reader, AiAnalysisResponse::class.java) }
        val overall = response.overall!!
        assertEquals(46, AiV7Presentation.coveragePercent(overall.coveragePct, overall.coverage))
        // 2026-09-24: also confirms the sample declares all three triad keys
        // (backend's confirmed-always-emitted claim, verified against THIS
        // repo's own copy of the golden sample, not just trusted from the
        // relayed answer) — the real json string, not hand-typed booleans.
        val rawJson = InputStreamReader(
            javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")!!,
            Charsets.UTF_8
        ).use { it.readText() }
        assertTrue("sample must declare overall.harnessError", net.neuralcheck.data.remote.model.isHarnessErrorDeclared(rawJson))
        assertTrue("sample must declare overall.noDataAtAll", net.neuralcheck.data.remote.model.isNoDataAtAllDeclared(rawJson))
        assertTrue("sample must declare overall.partialAssessment", net.neuralcheck.data.remote.model.isPartialAssessmentDeclared(rawJson))
        assertTrue(
            "overall.harnessError/noDataAtAll are false and partialAssessment is true in the sample",
            AiV7Presentation.classifyOverallStatus(
                overall.harnessError, overall.noDataAtAll, overall.partialAssessment,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            ) == AiV7Presentation.OverallStatusKind.PARTIAL
        )
    }

    @Test
    fun `a null flag is read exactly like false, never like true - the A76 shape`() {
        // Regression guard for the "!== false reads a missing field as
        // success" incident this project's rules cite (A76): a null on any
        // one flag must fall through to the next check, not trigger it —
        // exercised here with both sibling flags DECLARED (a scenario a
        // hand-built AiOverallV7 could still produce even though the
        // backend itself never sends a declared-but-null triad field today).
        // The undeclared/NOT_DECLARED cases this test used to also assert
        // (`null,null,null -> NORMAL`) are REPLACED, not kept here — see
        // `all three null and undeclared is NOT_DECLARED, never NORMAL` and
        // the three per-field tests above (rule 23).
        assertEquals(
            AiV7Presentation.OverallStatusKind.PARTIAL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = null, noDataAtAll = null, partialAssessment = true,
                harnessErrorDeclared = true, noDataAtAllDeclared = true, partialAssessmentDeclared = true
            )
        )
    }

    // ── classifyLetterCeilingReason / letterCeilingReasonDisplayText ──
    //
    // Task order 2026-09-23: "letterCeilingReason في طبقة القراءة والعرض".
    // Five states required by that order, each below sourced from real
    // sample material where one exists rather than hand-built, per this
    // file's own convention (see class doc) — only the fifth
    // ("كائن نصّه null") has no real sample anywhere in this repo and is
    // built by hand, labeled ARTIFICIAL.
    //
    // letterCeilingReasonDisplayText is the EXACT function
    // AiCategoryRowV7 (ReportScreen.kt) calls to decide what to render next
    // to the category letter — asserting its return value here is the
    // furthest a test that does not render can prove "what
    // the component displays" (see class doc: no test renders the component;
    // Robolectric and the Compose test rule are test dependencies since 3284cca). It does NOT prove the Text() composable
    // actually paints that string on a rendered screen — that would need
    // Robolectric/Compose UI testing, which this module's
    // build.gradle.kts testImplementation set did not have when this was written (it has both since 3284cca); see this task's report for
    // the explicit statement of that limit.

    @Test
    fun `gpu golden-sample case - LOW_CATEGORY_COVERAGE with a 'تغطية منخفضة' lead-in text`() {
        val gpu = loadSampleCategories().getValue("gpu")
        assertEquals(
            AiV7Presentation.LetterCeilingReasonKind.PRESENT_WITH_TEXT,
            AiV7Presentation.classifyLetterCeilingReason(declared = true, reason = gpu.letterCeilingReason)
        )
        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29:
        // the sentence's tail changed. Was "... وحرف مسقوف: لم يُقَس في هذه
        // الفئة أيّ اختبار معتمد قياساً للحالة." (REPLACED, not kept beside
        // the new one) — is "... وأعلى حرف لهذه الفئة B في هذا الإصدار:
        // اختباراتها تتأكّد من وجود المكوّنات وعملها، ولا تقيس أداءها بعد."
        assertEquals(
            "تغطية منخفضة — اختباران من خمسة، وأعلى حرف لهذه الفئة B في هذا الإصدار: اختباراتها تتأكّد من وجود المكوّنات وعملها، ولا تقيس أداءها بعد.",
            AiV7Presentation.letterCeilingReasonDisplayText(declared = true, reason = gpu.letterCeilingReason)
        )
    }

    @Test
    fun `display golden-sample case - LOW_CATEGORY_COVERAGE with a text that does NOT share gpu's lead-in`() {
        val display = loadSampleCategories().getValue("display")
        assertEquals(
            AiV7Presentation.LetterCeilingReasonKind.PRESENT_WITH_TEXT,
            AiV7Presentation.classifyLetterCeilingReason(declared = true, reason = display.letterCeilingReason)
        )
        val text = AiV7Presentation.letterCeilingReasonDisplayText(declared = true, reason = display.letterCeilingReason)
        // Rule 23 — resynced 2026-10-07 (backend@d5b6c29): same tail change
        // as the gpu test above; the old "حرف مسقوف: لم يُقَس ..." tail is gone.
        assertEquals(
            "ثلاثة اختبارات من ثلاثة — أعلى حرف لهذه الفئة B في هذا الإصدار: اختباراتها تتأكّد من وجود المكوّنات وعملها، ولا تقيس أداءها بعد.",
            text
        )
        assertTrue(!text!!.startsWith("تغطية منخفضة"))
    }

    @Test
    fun `cpu golden-sample case - key present, value null - NO_CEILING, nothing rendered`() {
        val cpu = loadSampleCategories().getValue("cpu")
        assertEquals(
            "cpu.letterCeilingReason must parse null (real value: no gate ceilinged it)",
            null,
            cpu.letterCeilingReason
        )
        assertEquals(
            AiV7Presentation.LetterCeilingReasonKind.NO_CEILING,
            AiV7Presentation.classifyLetterCeilingReason(declared = true, reason = cpu.letterCeilingReason)
        )
        assertNull(AiV7Presentation.letterCeilingReasonDisplayText(declared = true, reason = cpu.letterCeilingReason))
    }

    @Test
    fun `llm-adjusted sample case (resync 2026-10-07) - storage now carries a real LOW_CATEGORY_COVERAGE object - PRESENT_WITH_TEXT, the server sentence shown`() {
        // Rule 23 — REPLACES the test of the same slot (resync 2026-09-25,
        // القرار ٩٢ البند ١), which asserted that this fixture's storage
        // DECLARED the key as null (`NO_CEILING`, nothing rendered). That
        // was the 4d6c488/c7446a4-era content: the d5b6c29 sample's storage
        // is letter B under a LOW_CATEGORY_COVERAGE ceiling, so the same
        // fixture now exercises the OPPOSITE branch. The "declared null →
        // NO_CEILING" case keeps its real coverage in this file's cpu test
        // above (v7-client-payload-sample.json, cpu declares the key null)
        // — one real sample per branch, none re-pinned to a stale value.
        // The "key genuinely absent" branch keeps its hand-built coverage
        // (`the whole list absent (null) ...`), unchanged.
        val stream = javaClass.classLoader!!.getResourceAsStream(
            "contract/v7-client-payload-sample-llm-adjusted.json"
        )!!
        val rawJson = InputStreamReader(stream, Charsets.UTF_8).readText()
        val storage = Gson().fromJson(rawJson, AiAnalysisResponse::class.java).categories!!.getValue("storage")
        val declared = "storage" !in categoriesMissingLetterCeilingReason(rawJson)

        assertEquals("storage declares the key (present, an object) — not missing", true, declared)
        assertEquals(
            AiV7Presentation.LetterCeilingReasonKind.PRESENT_WITH_TEXT,
            AiV7Presentation.classifyLetterCeilingReason(declared = declared, reason = storage.letterCeilingReason)
        )
        assertEquals(
            "اختباران من اثنين — أعلى حرف لهذه الفئة B في هذا الإصدار: اختباراتها تتأكّد من وجود المكوّنات وعملها، ولا تقيس أداءها بعد.",
            AiV7Presentation.letterCeilingReasonDisplayText(declared = declared, reason = storage.letterCeilingReason)
        )
    }

    @Test
    fun `ARTIFICIAL - object present but text is null - PRESENT_NO_TEXT, no invented fallback text`() {
        // No real sample anywhere in this repo exercises an object with a
        // null `text` — every one of the 7 non-null golden-sample cases has
        // real text (see the gpu/display tests above). Hand-built,
        // labeled, per this file's own convention (class doc).
        val reason = AiLetterCeilingReasonV7(name = "LOW_CATEGORY_COVERAGE", ceilingLetter = "B", text = null)
        assertEquals(
            AiV7Presentation.LetterCeilingReasonKind.PRESENT_NO_TEXT,
            AiV7Presentation.classifyLetterCeilingReason(declared = true, reason = reason)
        )
        assertNull(
            "must NOT fall back to `name` or any built string — rule: text is shown verbatim or not at all",
            AiV7Presentation.letterCeilingReasonDisplayText(declared = true, reason = reason)
        )
    }

    @Test
    fun `a blank (not null) text is also treated as PRESENT_NO_TEXT, never rendered as empty text`() {
        val reason = AiLetterCeilingReasonV7(name = "LOW_CATEGORY_COVERAGE", ceilingLetter = "B", text = "   ")
        assertEquals(
            AiV7Presentation.LetterCeilingReasonKind.PRESENT_NO_TEXT,
            AiV7Presentation.classifyLetterCeilingReason(declared = true, reason = reason)
        )
        assertNull(AiV7Presentation.letterCeilingReasonDisplayText(declared = true, reason = reason))
    }

    // ── classifyNarrativeIncomplete / shouldShowNarrativeIncompleteBanner ──
    // Order 2026-09-23 (القرار ٧٢) item 2. Ceiling of this test suite per
    // the order's own text: "أقصاه أن المكوّن يستدعي الدالّة" — this proves
    // the pure function's three states; that ReportScreen.kt's
    // AiAnalysisSectionV7 calls it with the right arguments is confirmed by
    // reading that call site (it does — see ReportScreen.kt), not by a
    // Compose render (none exists; Robolectric and Compose test infrastructure
    // are test dependencies since 3284cca). Actual on-screen rendering is a device check
    // (order item 6), out of this test's scope.

    @Test
    fun `declared and true - INCOMPLETE, banner shown`() {
        assertEquals(
            AiV7Presentation.NarrativeIncompleteKind.INCOMPLETE,
            AiV7Presentation.classifyNarrativeIncomplete(declared = true, narrativeIncomplete = true)
        )
        assertTrue(AiV7Presentation.shouldShowNarrativeIncompleteBanner(declared = true, narrativeIncomplete = true))
    }

    @Test
    fun `declared and false - COMPLETE, no banner`() {
        assertEquals(
            AiV7Presentation.NarrativeIncompleteKind.COMPLETE,
            AiV7Presentation.classifyNarrativeIncomplete(declared = true, narrativeIncomplete = false)
        )
        assertEquals(false, AiV7Presentation.shouldShowNarrativeIncompleteBanner(declared = true, narrativeIncomplete = false))
    }

    @Test
    fun `not declared - key absent from the raw report - NOT_DECLARED, never read as complete, no banner`() {
        assertEquals(
            AiV7Presentation.NarrativeIncompleteKind.NOT_DECLARED,
            AiV7Presentation.classifyNarrativeIncomplete(declared = false, narrativeIncomplete = null)
        )
        assertEquals(false, AiV7Presentation.shouldShowNarrativeIncompleteBanner(declared = false, narrativeIncomplete = null))
    }

    @Test
    fun `golden sample - narrativeIncomplete is declared and false, no banner`() {
        // The golden sample's own raw JSON declares narrativeIncomplete
        // (value false) — real material, not synthetic, for the COMPLETE
        // case. See contract/README.md's "حدّ العيّنة" note: resolveV7Report
        // was never invoked for this sample, so this is NOT a reference for
        // narrativeIncomplete's behavior when the guard actually rejects.
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")!!
        val rawJson = InputStreamReader(stream, Charsets.UTF_8).readText()
        val declared = net.neuralcheck.data.remote.model.isNarrativeIncompleteDeclared(rawJson)
        val response = Gson().fromJson(rawJson, AiAnalysisResponse::class.java)
        assertEquals(true, declared)
        assertEquals(false, response.narrativeIncomplete)
        assertEquals(false, AiV7Presentation.shouldShowNarrativeIncompleteBanner(declared, response.narrativeIncomplete))
    }

    // ── ReportScreen.kt's AiAnalysisSectionV7 wiring: `ai.narrativeIncompleteDeclared
    // ?: false` (2026-09-24، جولة جرد القيم الافتراضية، البند د — REVERSED
    // from the prior `?: true`). Not Compose-testable directly (same
    // limitation noted above), so the exact expression is replicated here
    // and its CONSEQUENCE is asserted — rule 30. An older Room-cached
    // AiAnalysisResult (narrativeIncompleteDeclared == null, predating the
    // field) must classify NOT_DECLARED, never the COMPLETE that `?: true`
    // used to silently produce.

    @Test
    fun `ReportScreen wiring - narrativeIncompleteDeclared null (old Room row) - NOT_DECLARED, never COMPLETE`() {
        val narrativeIncompleteDeclared: Boolean? = null
        val effectiveDeclared = narrativeIncompleteDeclared ?: false // ReportScreen.kt's exact fallback
        assertEquals(false, effectiveDeclared)
        assertEquals(
            "an old Room row with no recorded presence signal must classify NOT_DECLARED, not COMPLETE",
            AiV7Presentation.NarrativeIncompleteKind.NOT_DECLARED,
            AiV7Presentation.classifyNarrativeIncomplete(declared = effectiveDeclared, narrativeIncomplete = null)
        )
    }

    @Test
    fun `ReportScreen wiring - narrativeIncompleteDeclared true (fresh report) - real value used, not overridden`() {
        val narrativeIncompleteDeclared: Boolean? = true
        val effectiveDeclared = narrativeIncompleteDeclared ?: false
        assertEquals(true, effectiveDeclared)
        assertEquals(
            AiV7Presentation.NarrativeIncompleteKind.INCOMPLETE,
            AiV7Presentation.classifyNarrativeIncomplete(declared = effectiveDeclared, narrativeIncomplete = true)
        )
    }

    // ── areIssuesDeclared (order 2026-09-24، جولة جرد القيم الافتراضية،
    // البند ج، القرار ٧٦) ──
    // Same technique/rationale as isNarrativeIncompleteDeclared above, for
    // the root `issues` key. `issues` is shared shape between V6 and V7.

    @Test
    fun `areIssuesDeclared - key present with entries - true`() {
        val json = """{"issues":[{"severity":"FAIL","title":"x"}]}"""
        assertTrue(net.neuralcheck.data.remote.model.areIssuesDeclared(json))
    }

    @Test
    fun `areIssuesDeclared - key present but an empty array - true (declared, genuinely empty)`() {
        val json = """{"issues":[]}"""
        assertTrue(net.neuralcheck.data.remote.model.areIssuesDeclared(json))
    }

    @Test
    fun `areIssuesDeclared - key present with explicit null - true (declared, whatever the value)`() {
        val json = """{"issues":null}"""
        assertTrue(net.neuralcheck.data.remote.model.areIssuesDeclared(json))
    }

    @Test
    fun `areIssuesDeclared - key absent entirely - false`() {
        val json = """{"summary":"ok"}"""
        assertEquals(false, net.neuralcheck.data.remote.model.areIssuesDeclared(json))
    }

    @Test
    fun `areIssuesDeclared - malformed json - false, never throws`() {
        assertEquals(false, net.neuralcheck.data.remote.model.areIssuesDeclared("not json"))
        assertEquals(false, net.neuralcheck.data.remote.model.areIssuesDeclared("[1,2,3]"))
    }

    @Test
    fun `golden sample - issues is declared with real entries`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")!!
        val rawJson = InputStreamReader(stream, Charsets.UTF_8).readText()
        assertTrue(net.neuralcheck.data.remote.model.areIssuesDeclared(rawJson))
        val response = Gson().fromJson(rawJson, AiAnalysisResponse::class.java)
        assertTrue(response.issues?.isNotEmpty() == true)
    }

    // ── isHarnessErrorDeclared / isNoDataAtAllDeclared / isPartialAssessmentDeclared
    // (order 2026-09-24، جولة جرد القيم الافتراضية، البند ح، القرار ٧٦) ──
    // Same technique as areIssuesDeclared above, but the key checked is
    // nested inside root `overall`, not at the document root.

    @Test
    fun `isHarnessErrorDeclared - key present (false) inside overall - true`() {
        val json = """{"overall":{"harnessError":false}}"""
        assertTrue(net.neuralcheck.data.remote.model.isHarnessErrorDeclared(json))
    }

    @Test
    fun `isHarnessErrorDeclared - key present (true) inside overall - true`() {
        val json = """{"overall":{"harnessError":true}}"""
        assertTrue(net.neuralcheck.data.remote.model.isHarnessErrorDeclared(json))
    }

    @Test
    fun `isHarnessErrorDeclared - key absent from overall - false`() {
        val json = """{"overall":{"letter":"C"}}"""
        assertEquals(false, net.neuralcheck.data.remote.model.isHarnessErrorDeclared(json))
    }

    @Test
    fun `isHarnessErrorDeclared - overall itself absent - false`() {
        val json = """{"summary":"ok"}"""
        assertEquals(false, net.neuralcheck.data.remote.model.isHarnessErrorDeclared(json))
    }

    @Test
    fun `isHarnessErrorDeclared - malformed json - false, never throws`() {
        assertEquals(false, net.neuralcheck.data.remote.model.isHarnessErrorDeclared("not json"))
        assertEquals(false, net.neuralcheck.data.remote.model.isHarnessErrorDeclared("""{"overall":"not an object"}"""))
    }

    @Test
    fun `isNoDataAtAllDeclared - present true false and absent`() {
        assertTrue(net.neuralcheck.data.remote.model.isNoDataAtAllDeclared("""{"overall":{"noDataAtAll":false}}"""))
        assertTrue(net.neuralcheck.data.remote.model.isNoDataAtAllDeclared("""{"overall":{"noDataAtAll":true}}"""))
        assertEquals(false, net.neuralcheck.data.remote.model.isNoDataAtAllDeclared("""{"overall":{}}"""))
    }

    @Test
    fun `isPartialAssessmentDeclared - present true false and absent`() {
        assertTrue(net.neuralcheck.data.remote.model.isPartialAssessmentDeclared("""{"overall":{"partialAssessment":false}}"""))
        assertTrue(net.neuralcheck.data.remote.model.isPartialAssessmentDeclared("""{"overall":{"partialAssessment":true}}"""))
        assertEquals(false, net.neuralcheck.data.remote.model.isPartialAssessmentDeclared("""{"overall":{}}"""))
    }

    @Test
    fun `golden sample - all three triad keys are declared, matching the backend's confirmed-always-emitted answer`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")!!
        val rawJson = InputStreamReader(stream, Charsets.UTF_8).readText()
        assertTrue(net.neuralcheck.data.remote.model.isHarnessErrorDeclared(rawJson))
        assertTrue(net.neuralcheck.data.remote.model.isNoDataAtAllDeclared(rawJson))
        assertTrue(net.neuralcheck.data.remote.model.isPartialAssessmentDeclared(rawJson))
    }

    // ── ReportScreen.kt's AiAnalysisSectionV7 wiring: `ai.harnessErrorDeclared
    // ?: false` (and the two siblings) feeding AiV7OverallCard/classifyOverallStatus.
    // Not Compose-testable directly, so the exact expressions are replicated
    // here and their consequence asserted — rule 30.

    @Test
    fun `ReportScreen wiring - all three declared signals null (old Room row) - classifies NOT_DECLARED`() {
        val harnessErrorDeclared: Boolean? = null
        val noDataAtAllDeclared: Boolean? = null
        val partialAssessmentDeclared: Boolean? = null
        assertEquals(
            AiV7Presentation.OverallStatusKind.NOT_DECLARED,
            AiV7Presentation.classifyOverallStatus(
                harnessError = null, noDataAtAll = null, partialAssessment = null,
                harnessErrorDeclared = harnessErrorDeclared ?: false,
                noDataAtAllDeclared = noDataAtAllDeclared ?: false,
                partialAssessmentDeclared = partialAssessmentDeclared ?: false
            )
        )
    }

    @Test
    fun `ReportScreen wiring - all three declared signals true (fresh report) - real values used, not overridden`() {
        val harnessErrorDeclared: Boolean? = true
        val noDataAtAllDeclared: Boolean? = true
        val partialAssessmentDeclared: Boolean? = true
        assertEquals(
            AiV7Presentation.OverallStatusKind.NORMAL,
            AiV7Presentation.classifyOverallStatus(
                harnessError = false, noDataAtAll = false, partialAssessment = false,
                harnessErrorDeclared = harnessErrorDeclared ?: false,
                noDataAtAllDeclared = noDataAtAllDeclared ?: false,
                partialAssessmentDeclared = partialAssessmentDeclared ?: false
            )
        )
    }

    // ── ReportScreen.kt's AiAnalysisSectionV7/AiAnalysisSection wiring:
    // `if (ai.issues.isNotEmpty()) ... else if (ai.issuesDeclared == false) ...`
    // Not Compose-testable directly, so the exact condition is replicated
    // here and its consequence asserted — rule 30.

    @Test
    fun `ReportScreen wiring - issues empty and declared false - shows the not-declared line`() {
        val issues = emptyList<Any>()
        val issuesDeclared: Boolean? = false
        val showsIssuesSection = issues.isNotEmpty()
        val showsNotDeclaredLine = !showsIssuesSection && issuesDeclared == false
        assertEquals(false, showsIssuesSection)
        assertTrue(showsNotDeclaredLine)
    }

    @Test
    fun `ReportScreen wiring - issues empty and declared true - shows neither line, a real empty answer`() {
        val issues = emptyList<Any>()
        val issuesDeclared: Boolean? = true
        val showsIssuesSection = issues.isNotEmpty()
        val showsNotDeclaredLine = !showsIssuesSection && issuesDeclared == false
        assertEquals(false, showsIssuesSection)
        assertEquals(false, showsNotDeclaredLine)
    }

    @Test
    fun `ReportScreen wiring - issues empty and declared unknown (old Room row or local report) - stays silent`() {
        val issues = emptyList<Any>()
        val issuesDeclared: Boolean? = null
        val showsIssuesSection = issues.isNotEmpty()
        val showsNotDeclaredLine = !showsIssuesSection && issuesDeclared == false
        assertEquals(false, showsIssuesSection)
        assertEquals(
            "an unknown (never-computed) declaration must not be announced as " +
                "'not declared' — only a confirmed absence (false) is",
            false,
            showsNotDeclaredLine
        )
    }

    // ── ReportScreen.kt's AiAnalysisSectionV7 wiring: `ai.categoriesMissingLetterCeilingReason
    // ?: cats.keys` (order 2026-09-23، القرار ٧٢، البند ٣ — REVERSED from
    // the prior `?: emptySet()`). Not Compose-testable directly, so the
    // exact expression is replicated here as a pure computation and its
    // CONSEQUENCE (the classification it produces) is what's asserted —
    // rule 30. Backend confirmed (neuralcheck-backend @ e8cd67c,
    // test/v7AbsenceVsNullFieldsGuard.test.js 56/56) letterCeilingReason is
    // ALWAYS emitted per category live, so a null list now means only
    // "signal never computed" (an older Room-cached result), never
    // "nothing missing".

    @Test
    fun `the whole list absent (null) - every category classifies NOT_DECLARED, never NO_CEILING`() {
        val categoryKeys = setOf("gpu", "cpu", "battery")
        val missingList: Set<String>? = null
        val effectiveMissing = missingList ?: categoryKeys // ReportScreen.kt's exact fallback
        for (key in categoryKeys) {
            val declared = key !in effectiveMissing
            assertEquals("$key must be undeclared when the whole list is null", false, declared)
            assertEquals(
                "$key must classify NOT_DECLARED, not NO_CEILING, when the list itself was never computed",
                AiV7Presentation.LetterCeilingReasonKind.NOT_DECLARED,
                AiV7Presentation.classifyLetterCeilingReason(declared = declared, reason = null)
            )
        }
    }

    @Test
    fun `the list present but genuinely empty - every category classifies NO_CEILING, distinct from the null-list case`() {
        val categoryKeys = setOf("gpu", "cpu", "battery")
        val missingList: Set<String>? = emptySet()
        val effectiveMissing = missingList ?: categoryKeys
        for (key in categoryKeys) {
            val declared = key !in effectiveMissing
            assertEquals("$key must be declared when the list is a real (even if empty) Set", true, declared)
            assertEquals(
                "$key must classify NO_CEILING — a present, empty list is the real 'no gate ceilinged anything' answer",
                AiV7Presentation.LetterCeilingReasonKind.NO_CEILING,
                AiV7Presentation.classifyLetterCeilingReason(declared = declared, reason = null)
            )
        }
    }

    // ── shouldShowGradeSummary (order 2026-09-24، القرار ٧٦، البند ١) ──
    // Test ceiling per this order's own text: "أقصاه أن المكوّن يقرأ
    // الحالة، والرسم يُحسم على الجهاز" — proves the pure function
    // ReportScreen.kt's `AiAnalysisSectionV7`... actually the grade-summary
    // gate lives one level up, in the composable that calls
    // AiAnalysisSection(ai) — calls with the report's pipeline; does not
    // prove Compose actually omits GradeSummaryCard on a rendered screen.

    // Order 2026-09-26 (القرار ١٠٢ البند ٢) — rule 23 replacement: the
    // function's signature changed (now content-aware, not tag-only) to
    // correctly route `cached`. The three tests below are REWRITTEN (not
    // appended to) for the new signature; three NEW tests cover the
    // v3_legacy/v5_precomputed/cached cases this order added.

    @Test
    fun `V7 pipeline - grade summary must NOT show, a buyer must never see a fabricated score`() {
        assertEquals(
            false,
            AiV7Presentation.shouldShowGradeSummary(
                AiPipeline.V7_FINDINGS_BASED, hasRootLetterOrGrade = false, hasV7Overall = true
            )
        )
    }

    @Test
    fun `V6 pipeline - grade summary shows as before`() {
        assertEquals(
            true,
            AiV7Presentation.shouldShowGradeSummary(
                AiPipeline.V6_RAW_TIMESERIES, hasRootLetterOrGrade = true, hasV7Overall = false
            )
        )
    }

    @Test
    fun `null pipeline - legacy report predating the discriminator - grade summary shows as before`() {
        assertEquals(
            true,
            AiV7Presentation.shouldShowGradeSummary(null, hasRootLetterOrGrade = true, hasV7Overall = false)
        )
    }

    @Test
    fun `v3_legacy and v5_precomputed pipelines - both root-shape V6 - grade summary shows`() {
        assertEquals(
            true,
            AiV7Presentation.shouldShowGradeSummary(
                AiPipeline.V3_LEGACY, hasRootLetterOrGrade = true, hasV7Overall = false
            )
        )
        assertEquals(
            true,
            AiV7Presentation.shouldShowGradeSummary(
                AiPipeline.V5_PRECOMPUTED, hasRootLetterOrGrade = true, hasV7Overall = false
            )
        )
    }

    @Test
    fun `cached pipeline with real V7 content - grade summary must NOT show`() {
        assertEquals(
            false,
            AiV7Presentation.shouldShowGradeSummary(
                AiPipeline.CACHED, hasRootLetterOrGrade = false, hasV7Overall = true
            )
        )
    }

    @Test
    fun `cached pipeline with real V6 content - grade summary shows`() {
        assertEquals(
            true,
            AiV7Presentation.shouldShowGradeSummary(
                AiPipeline.CACHED, hasRootLetterOrGrade = true, hasV7Overall = false
            )
        )
    }

    @Test
    fun `cached pipeline with neither signal, or a wholly unrecognised tag - grade summary must NOT show`() {
        assertEquals(
            false,
            AiV7Presentation.shouldShowGradeSummary(
                AiPipeline.CACHED, hasRootLetterOrGrade = false, hasV7Overall = false
            )
        )
        assertEquals(
            false,
            AiV7Presentation.shouldShowGradeSummary(
                "some_future_tag", hasRootLetterOrGrade = false, hasV7Overall = false
            )
        )
    }

    // ── classifyCategoryReason (order 2026-09-25، القرار ٨٤ البند ١ / القرار
    // ٨٦ البند ٣ / القرار ٨٧ البند ٤) — rule 23: this SUPERSEDES and
    // replaces the narrower isBatteryUncalibratedPartial (PARTIAL-only)
    // this file tested before; that function no longer exists. Rule 30 —
    // the full matrix below exercises every state this app recognises
    // against every named reason code, not spot checks alone.

    private fun loadBatteryCategory(fileName: String): net.neuralcheck.data.remote.model.AiCategoryV7 {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$fileName")
            ?: error("Fixture not found on classpath — expected at app/src/test/resources/contract/$fileName")
        val rawJson = InputStreamReader(stream, Charsets.UTF_8).readText()
        val rawMap: Map<String, Any?> = Gson().fromJson(
            rawJson, object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
        )
        val flat = net.neuralcheck.data.remote.flattenReportDocument(rawMap)
        val response = Gson().fromJson(Gson().toJson(flat), AiAnalysisResponse::class.java)
        return response.categories!!.getValue("battery")
    }

    @Test
    fun `sample B battery (100% coverage, PARTIAL, NO_CALIBRATED_THRESHOLD) classifies BATTERY_UNCALIBRATED`() {
        val battery = loadBatteryCategory("v7-reports-document-sample.json")
        assertEquals("PARTIAL", battery.state)
        assertEquals(1.0, battery.coverage!!, 0.0001) // the order's own "100% تغطية" claim, verified
        assertEquals(
            "sample B's battery category is exactly the pair this order names",
            AiV7Presentation.CategoryReasonKind.BATTERY_UNCALIBRATED,
            AiV7Presentation.classifyCategoryReason(battery.state, battery.reason)
        )
    }

    @Test
    fun `Honor B battery (one measured indicator, PARTIAL, NO_CALIBRATED_THRESHOLD) is ALSO BATTERY_UNCALIBRATED`() {
        val battery = loadBatteryCategory("v7-reports-document-sample-honor.json")
        assertEquals("PARTIAL", battery.state)
        assertEquals(0.25, battery.coverage!!, 0.0001) // 1 of 4 indicators measured
        assertEquals(
            "the fix must not depend on the coverage percentage — same reason code, same result",
            AiV7Presentation.CategoryReasonKind.BATTERY_UNCALIBRATED,
            AiV7Presentation.classifyCategoryReason(battery.state, battery.reason)
        )
    }

    @Test
    fun `MEASURED with NO_CALIBRATED_THRESHOLD is ALSO BATTERY_UNCALIBRATED — the table's own PARTIAL-or-MEASURED row`() {
        // Not from a real fixture (neither sample has battery.state=MEASURED)
        // — the table's own text names this pairing explicitly, so it must
        // classify correctly even without an observed real occurrence yet.
        assertEquals(
            AiV7Presentation.CategoryReasonKind.BATTERY_UNCALIBRATED,
            AiV7Presentation.classifyCategoryReason("MEASURED", AiV7Presentation.NO_CALIBRATED_THRESHOLD_REASON)
        )
    }

    @Test
    fun `UNMEASURED with NO_CALIBRATED_THRESHOLD classifies BATTERY_UNMEASURED, not BATTERY_UNCALIBRATED`() {
        assertEquals(
            AiV7Presentation.CategoryReasonKind.BATTERY_UNMEASURED,
            AiV7Presentation.classifyCategoryReason("UNMEASURED", AiV7Presentation.NO_CALIBRATED_THRESHOLD_REASON)
        )
    }

    @Test
    fun `UNMEASURED with HARNESS_ERROR reason classifies HARNESS_ERROR_UNMEASURED`() {
        assertEquals(
            AiV7Presentation.CategoryReasonKind.HARNESS_ERROR_UNMEASURED,
            AiV7Presentation.classifyCategoryReason("UNMEASURED", "HARNESS_ERROR")
        )
    }

    @Test
    fun `NO_HEALTH_BEARING_TESTS_DECLARED and DECLARED_BUT_NONE_EXECUTED apply regardless of state`() {
        for (state in listOf("MEASURED", "PARTIAL", "UNMEASURED", "ATTESTED_ONLY", "HARNESS_ERROR", "NO_DATA", "SOME_FUTURE_STATE")) {
            assertEquals(
                "state='$state' must not change this reason's classification",
                AiV7Presentation.CategoryReasonKind.NO_HEALTH_BEARING_TESTS_DECLARED,
                AiV7Presentation.classifyCategoryReason(state, "NO_HEALTH_BEARING_TESTS_DECLARED")
            )
            assertEquals(
                "state='$state' must not change this reason's classification",
                AiV7Presentation.CategoryReasonKind.DECLARED_BUT_NONE_EXECUTED,
                AiV7Presentation.classifyCategoryReason(state, "DECLARED_BUT_NONE_EXECUTED")
            )
        }
    }

    @Test
    fun `the two named pairs are HIDDEN — the state text alone already covers them`() {
        assertEquals(
            AiV7Presentation.CategoryReasonKind.HIDDEN,
            AiV7Presentation.classifyCategoryReason("HARNESS_ERROR", "HARNESS_ERROR")
        )
        assertEquals(
            AiV7Presentation.CategoryReasonKind.HIDDEN,
            AiV7Presentation.classifyCategoryReason("ATTESTED_ONLY", "MANUAL_ATTESTATION_ONLY")
        )
    }

    @Test
    fun `a null or blank reason is always HIDDEN, regardless of state`() {
        for (state in listOf("MEASURED", "PARTIAL", "UNMEASURED", "ATTESTED_ONLY", "HARNESS_ERROR", "NO_DATA")) {
            assertEquals(AiV7Presentation.CategoryReasonKind.HIDDEN, AiV7Presentation.classifyCategoryReason(state, null))
            assertEquals(AiV7Presentation.CategoryReasonKind.HIDDEN, AiV7Presentation.classifyCategoryReason(state, ""))
        }
    }

    @Test
    fun `NO_BATTERY_INDICATORS_MEASURED classifies BATTERY_UNMEASURED on a category row, regardless of state`() {
        // Order 2026-10-06 (the user's decision) — rule 23: REPLACES
        // `NO_BATTERY_INDICATORS_MEASURED (a dead value per the coordinator)
        // classifies UNKNOWN`, which pinned the OLD behaviour (the row showed
        // report_v7_state_unknown for this code while the abstentionReasons
        // list showed the approved battery-unmeasured text). Same code, same
        // shared constant, same text in both places now. State-independent,
        // like NO_HEALTH_BEARING_TESTS_DECLARED / DECLARED_BUT_NONE_EXECUTED.
        // SYNTHETIC — «لا يُنال من مُدخل حقيقي» (backend 8e49fc6
        // `codesNotReachable`): no real input carries this code.
        for (state in listOf("MEASURED", "PARTIAL", "UNMEASURED", "ATTESTED_ONLY", "HARNESS_ERROR", "NO_DATA", "SOME_FUTURE_STATE", null)) {
            assertEquals(
                "state='$state'",
                AiV7Presentation.CategoryReasonKind.BATTERY_UNMEASURED,
                AiV7Presentation.classifyCategoryReason(state, AiV7Presentation.REASON_NO_BATTERY_INDICATORS_MEASURED)
            )
        }
    }

    @Test
    fun `the category row and the abstentionReasons list agree on NO_BATTERY_INDICATORS_MEASURED, from the same constant`() {
        // Both classifiers compare against REASON_NO_BATTERY_INDICATORS_MEASURED
        // and both mean "battery unmeasured" — the kinds are different enums
        // (CategoryReasonKind vs AbstentionReasonKind) but the same name.
        assertEquals(
            AiV7Presentation.CategoryReasonKind.BATTERY_UNMEASURED.name,
            AiV7Presentation.classifyCategoryReason("UNMEASURED", AiV7Presentation.REASON_NO_BATTERY_INDICATORS_MEASURED).name
        )
        assertEquals(
            AiV7Presentation.CategoryReasonKind.BATTERY_UNMEASURED.name,
            AiV7Presentation.classifyAbstentionReason(AiV7Presentation.REASON_NO_BATTERY_INDICATORS_MEASURED).name
        )
    }

    @Test
    fun `HARNESS_ERROR reason on a state OTHER than its two table pairs classifies UNKNOWN`() {
        // (UNMEASURED, HARNESS_ERROR) -> HARNESS_ERROR_UNMEASURED and
        // (HARNESS_ERROR, HARNESS_ERROR) -> HIDDEN are the only two table
        // rows for this reason code — every other state pairing with it is
        // an unrecognised combination, not silently folded into either.
        for (state in listOf("MEASURED", "PARTIAL", "ATTESTED_ONLY", "NO_DATA")) {
            assertEquals(
                "state='$state' + reason=HARNESS_ERROR is not in the table",
                AiV7Presentation.CategoryReasonKind.UNKNOWN,
                AiV7Presentation.classifyCategoryReason(state, "HARNESS_ERROR")
            )
        }
    }

    @Test
    fun `MANUAL_ATTESTATION_ONLY reason on a state other than ATTESTED_ONLY classifies UNKNOWN`() {
        for (state in listOf("MEASURED", "PARTIAL", "UNMEASURED", "HARNESS_ERROR", "NO_DATA")) {
            assertEquals(
                "state='$state' + reason=MANUAL_ATTESTATION_ONLY is not in the table",
                AiV7Presentation.CategoryReasonKind.UNKNOWN,
                AiV7Presentation.classifyCategoryReason(state, "MANUAL_ATTESTATION_ONLY")
            )
        }
    }

    @Test
    fun `an entirely unrecognised reason string classifies UNKNOWN for every state`() {
        for (state in listOf("MEASURED", "PARTIAL", "UNMEASURED", "ATTESTED_ONLY", "HARNESS_ERROR", "NO_DATA", "SOME_FUTURE_STATE")) {
            assertEquals(
                AiV7Presentation.CategoryReasonKind.UNKNOWN,
                AiV7Presentation.classifyCategoryReason(state, "SOME_FUTURE_REASON_CODE")
            )
        }
    }

    // ── harnessErrorDisplayText (order 2026-10-01، A8/S-13، أمر القرارات) ──

    @Test
    fun `harnessErrorDisplayText returns the message when non-blank`() {
        assertEquals(
            "تعذّر تجهيز نتائج الاختبارات لهذا الفحص.",
            AiV7Presentation.harnessErrorDisplayText("تعذّر تجهيز نتائج الاختبارات لهذا الفحص.")
        )
    }

    @Test
    fun `harnessErrorDisplayText returns null for a null or blank message — caller must show its own unavailable text, never the raw reason`() {
        assertNull(AiV7Presentation.harnessErrorDisplayText(null))
        assertNull(AiV7Presentation.harnessErrorDisplayText(""))
        assertNull(AiV7Presentation.harnessErrorDisplayText("   "))
    }

    // ── classifyAbstentionReason (order 2026-10-02، أمر «abstentionReasons» v2) ──
    // One test per row of the user's approved table (matched by code
    // directly, no state — see the function's doc). Reasons are the
    // server's documented raw strings, referenced through the SAME shared
    // constants production compares against (rule 24) — not retyped here.
    // These are unit tests of a pure classifier over string constants, not
    // golden-sample tests: the reachable codes are exercised on the REAL
    // zero-measured samples (synced 2026-10-07 from backend@d5b6c29) in
    // AbstentionReasonsParsingTest / AbstentionReasonDisplayTest.
    // `MANUAL_ATTESTATION_ONLY` and `NO_BATTERY_INDICATORS_MEASURED` have
    // no real input at all — «لا يُنال من مُدخل حقيقي» (neuralcheck-backend
    // @8e49fc6 `codesNotReachable`, relayed, unverified from this repo;
    // the user's decision 2026-10-07: they stay synthetic, no sample is
    // fabricated and no backend code is changed for them).
    //
    // Rule 23: REPLACES the 2026-10-01 tests that pinned the wrong
    // behaviour — `NO_CALIBRATED_THRESHOLD_REASON classifies
    // BATTERY_UNMEASURED here, never BATTERY_UNCALIBRATED` (a false
    // statement: that battery WAS measured), `MANUAL_ATTESTATION_ONLY
    // classifies HIDDEN` (a borrowed-enum workaround, gone with the fake
    // state), and the "unrecognised" list that included
    // NO_BATTERY_INDICATORS_MEASURED (a table row now).

    @Test
    fun `MANUAL_ATTESTATION_ONLY classifies ATTESTED_ONLY`() {
        // SYNTHETIC — «لا يُنال من مُدخل حقيقي» (backend 8e49fc6
        // `codesNotReachable`): no real input carries this code.
        assertEquals(
            AiV7Presentation.AbstentionReasonKind.ATTESTED_ONLY,
            AiV7Presentation.classifyAbstentionReason(AiV7Presentation.REASON_MANUAL_ATTESTATION_ONLY)
        )
    }

    @Test
    fun `NO_HEALTH_BEARING_TESTS_DECLARED classifies directly`() {
        assertEquals(
            AiV7Presentation.AbstentionReasonKind.NO_HEALTH_BEARING_TESTS_DECLARED,
            AiV7Presentation.classifyAbstentionReason(AiV7Presentation.REASON_NO_HEALTH_BEARING_TESTS_DECLARED)
        )
    }

    @Test
    fun `DECLARED_BUT_NONE_EXECUTED classifies directly`() {
        assertEquals(
            AiV7Presentation.AbstentionReasonKind.DECLARED_BUT_NONE_EXECUTED,
            AiV7Presentation.classifyAbstentionReason(AiV7Presentation.REASON_DECLARED_BUT_NONE_EXECUTED)
        )
    }

    @Test
    fun `NO_CALIBRATED_THRESHOLD_REASON classifies BATTERY_UNCALIBRATED`() {
        // The battery was measured, it just gets no letter — "uncalibrated",
        // not "unmeasured". The most common real case.
        assertEquals(
            AiV7Presentation.AbstentionReasonKind.BATTERY_UNCALIBRATED,
            AiV7Presentation.classifyAbstentionReason(AiV7Presentation.NO_CALIBRATED_THRESHOLD_REASON)
        )
    }

    @Test
    fun `HARNESS_ERROR classifies HARNESS_ERROR_UNMEASURED`() {
        assertEquals(
            AiV7Presentation.AbstentionReasonKind.HARNESS_ERROR_UNMEASURED,
            AiV7Presentation.classifyAbstentionReason(AiV7Presentation.REASON_HARNESS_ERROR)
        )
    }

    @Test
    fun `NO_BATTERY_INDICATORS_MEASURED classifies BATTERY_UNMEASURED`() {
        assertEquals(
            AiV7Presentation.AbstentionReasonKind.BATTERY_UNMEASURED,
            AiV7Presentation.classifyAbstentionReason(AiV7Presentation.REASON_NO_BATTERY_INDICATORS_MEASURED)
        )
    }

    @Test
    fun `any other code, a bare category state string, null or blank classifies UNKNOWN`() {
        for (reason in listOf("UNMEASURED", "PARTIAL", "ATTESTED_ONLY", "SOME_FUTURE_CODE", null, "", "   ")) {
            assertEquals(
                "reason='$reason'",
                AiV7Presentation.AbstentionReasonKind.UNKNOWN,
                AiV7Presentation.classifyAbstentionReason(reason)
            )
        }
    }

    @Test
    fun `the shared reason constants equal the literals classifyCategoryReason has always matched`() {
        // classifyCategoryReason now compares against these constants instead
        // of inline literals (rule 24). Its behaviour is byte-for-byte what
        // it was — the same strings, resolved through the same state rules
        // (the rest of this file's classifyCategoryReason tests pin the
        // results) — EXCEPT the one deliberate 2026-10-06 addition,
        // NO_BATTERY_INDICATORS_MEASURED (its own test above).
        assertEquals("NO_HEALTH_BEARING_TESTS_DECLARED", AiV7Presentation.REASON_NO_HEALTH_BEARING_TESTS_DECLARED)
        assertEquals("DECLARED_BUT_NONE_EXECUTED", AiV7Presentation.REASON_DECLARED_BUT_NONE_EXECUTED)
        assertEquals("MANUAL_ATTESTATION_ONLY", AiV7Presentation.REASON_MANUAL_ATTESTATION_ONLY)
        assertEquals("HARNESS_ERROR", AiV7Presentation.REASON_HARNESS_ERROR)
        assertEquals("NO_BATTERY_INDICATORS_MEASURED", AiV7Presentation.REASON_NO_BATTERY_INDICATORS_MEASURED)
    }
}
