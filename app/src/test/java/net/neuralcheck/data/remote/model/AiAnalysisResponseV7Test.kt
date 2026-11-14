package net.neuralcheck.data.remote.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3-a — wire-level cover for the V7 findings-based read layer,
 * using a HAND-BUILT fixture for controlled, minimal-surface-area
 * assertions (isComplete branching, lenient-adapter edge cases). The
 * COMPREHENSIVE, authoritative shape check against a real payload lives
 * in [V7GoldenSampleTest] instead, which deserializes the actual golden
 * sample copied from neuralcheck-backend — this file's fixture is
 * deliberately synthetic and smaller.
 *
 * Rule 23 — this fixture's `overall.partialAssessmentTag` /
 * `overall.governingGateReason` / `battery.indicators` shapes, and the
 * `issues[]` item shape, USED TO assert the wrong types (bare strings /
 * list-of-strings, and no id/evidenceUnit) before the golden sample
 * arrived and proved them wrong — see git history on this file. Replaced
 * here, not added alongside; the corrected shapes below match
 * [AiAnalysisResponse.kt]'s doc comments, each citing the golden sample.
 *
 * Same replacement applied to `battery.adjustmentEvidence` below: this
 * fixture used to assert it as a bare STRING (`"8% drop in 11 min"`).
 * A report relayed from neuralcheck-backend (`buildDisplayShapeV7.js:158`,
 * `analyzeDevice.js:78`, both gating this field with `Array.isArray(...)`)
 * plus an observed real value shows the true shape is an ARRAY — see
 * [AiAnalysisResponse.kt]'s doc comment on `adjustmentEvidence`. NOT
 * confirmed against this app repo's own golden sample (its one sample has
 * no LLM_ADJUSTED category), so this fixture stays hand-built/synthetic
 * for this field too, same as the rest of this file's documented scope —
 * an authoritative golden-sample check for the array shape belongs in
 * [V7GoldenSampleTest] once a real LLM_ADJUSTED sample is available there.
 */
class AiAnalysisResponseV7Test {

    private val gson = Gson()

    private val fullV7Json = """
        {
          "pipeline": "v7_findings_based",
          "language": "ar",
          "overall": {
            "letter": "B",
            "letterSource": "LLM_ADJUSTED",
            "coverage": 0.82,
            "coveragePct": 82,
            "partialAssessment": true,
            "partialAssessmentTag": {
              "label": "تقييم جزئي",
              "coveragePct": 82,
              "text": "تقييم جزئي — تغطية 82%"
            },
            "governingGateReason": {
              "name": "CAMERA_UNMEASURED",
              "ceilingLetter": "B",
              "source": "CM-01=blocked"
            },
            "noDataAtAll": false,
            "zeroMeasuredMessage": null,
            "abstentionReasons": [
              {"category": "camera", "reason": "MANUAL_ATTESTATION_ONLY"},
              {"category": "gps", "reason": "NO_HEALTH_BEARING_TESTS_DECLARED"}
            ],
            "harnessError": false,
            "harnessErrorReason": null
          },
          "categories": {
            "gpu": {
              "state": "MEASURED",
              "letter": "A",
              "letterSource": "DETERMINISTIC",
              "adjustmentReason": null,
              "adjustmentEvidence": null,
              "coverage": 1.0,
              "reason": "all gpu tests ran",
              "letterCeilingReason": {
                "name": "LOW_CATEGORY_COVERAGE",
                "ceilingLetter": "B",
                "text": "synthetic fixture text — not sample-derived"
              },
              "testIds": ["GP-01", "GP-02"]
            },
            "battery": {
              "state": "MEASURED",
              "letter": "B",
              "letterSource": "LLM_ADJUSTED",
              "adjustmentReason": "drain rate borderline",
              "adjustmentEvidence": ["categoryStates.categories.battery.deterministicLetter"],
              "coverage": 0.9,
              "reason": "battery tests mostly ran",
              "testIds": ["BAT-01"],
              "confidence": "medium",
              "tag": "borderline",
              "indicators": {
                "BAT-DRAIN": {
                  "id": "BAT-DRAIN",
                  "status": "MEASURED",
                  "value": 8.0,
                  "unit": "%/11min"
                }
              },
              "socAtStart": 90,
              "socAtEnd": 82
            }
          },
          "issues": [
            {
              "id": "ISS-01",
              "category": "battery",
              "severity": "warning",
              "title": "t",
              "evidence": "8",
              "evidenceUnit": "%",
              "description": "d"
            }
          ],
          "narrative": {
            "summary": "ملخص V7",
            "details": "تفاصيل V7",
            "verdict": "حكم V7",
            "buyerAdvice": {
              "casual": "casual advice",
              "gamer": "gamer advice",
              "photographer": null
            }
          },
          "narrativeIncomplete": true,
          "narrativeIncompleteFields": ["details"],
          "shortId": "NC-V7TEST",
          "isFreeTrial": false,
          "aiModel": "gemini-v7",
          "deviceMarketingName": "Test Device V7"
        }
    """.trimIndent()

    @Test
    fun `full V7 payload parses without throwing and drops no documented field`() {
        val r = gson.fromJson(fullV7Json, AiAnalysisResponse::class.java)

        // Root
        assertEquals(AiPipeline.V7_FINDINGS_BASED, r.pipeline)
        assertEquals("ar", r.language)
        assertEquals(1, r.issues?.size)
        assertEquals(true, r.narrativeIncomplete)
        assertEquals(listOf("details"), r.narrativeIncompleteFields)
        // Operational fields (outside display, shared with V6) still parse.
        assertEquals("NC-V7TEST", r.shortId)
        assertEquals(false, r.isFreeTrial)
        assertEquals("gemini-v7", r.aiModel)
        assertEquals("Test Device V7", r.deviceMarketingName)

        // overall
        val overall = r.overall!!
        assertEquals("B", overall.letter)
        assertEquals("LLM_ADJUSTED", overall.letterSource)
        assertEquals(0.82, overall.coverage!!, 0.0001)
        assertEquals(82.0, overall.coveragePct!!, 0.0001)
        assertEquals(true, overall.partialAssessment)
        assertEquals("تقييم جزئي", overall.partialAssessmentTag?.label)
        assertEquals(82.0, overall.partialAssessmentTag?.coveragePct!!, 0.0001)
        assertEquals("تقييم جزئي — تغطية 82%", overall.partialAssessmentTag?.text)
        assertEquals("CAMERA_UNMEASURED", overall.governingGateReason?.name)
        assertEquals("B", overall.governingGateReason?.ceilingLetter)
        assertEquals("CM-01=blocked", overall.governingGateReason?.source)
        assertEquals(false, overall.noDataAtAll)
        assertNull(overall.zeroMeasuredMessage)
        // Order 2026-10-01 (أمر «abstentionReasons») — rule 23: this fixture
        // and assertion USED TO assert abstentionReasons as a bare
        // List<String> (`["camera_not_tested", "gps_unavailable"]`) — wrong
        // type, proven by this round's step-1 test
        // (AbstentionReasonsParsingTest's class doc has the verbatim
        // exception). Real shape is `{category, reason}` objects
        // (buildDisplayShapeV7.js:353, relayed/unverified — rule 29).
        // This whole document is hand-built (not a golden sample), and its
        // camera entry's code is one that has NO real input —
        // «لا يُنال من مُدخل حقيقي» (neuralcheck-backend@8e49fc6
        // `codesNotReachable`, relayed, unverified from this repo). The
        // real-sample coverage of this field is AbstentionReasonsParsingTest.
        assertEquals("camera", overall.abstentionReasons?.get(0)?.category)
        assertEquals("MANUAL_ATTESTATION_ONLY", overall.abstentionReasons?.get(0)?.reason)
        assertEquals("gps", overall.abstentionReasons?.get(1)?.category)
        assertEquals("NO_HEALTH_BEARING_TESTS_DECLARED", overall.abstentionReasons?.get(1)?.reason)
        assertEquals(false, overall.harnessError)
        assertNull(overall.harnessErrorReason)

        // categories — non-battery
        val gpu = r.categories!!.getValue("gpu")
        assertEquals("MEASURED", gpu.state)
        assertEquals("A", gpu.letter)
        assertEquals("DETERMINISTIC", gpu.letterSource)
        assertNull(gpu.adjustmentReason)
        assertNull(gpu.adjustmentEvidence)
        assertEquals(1.0, gpu.coverage!!, 0.0001)
        assertEquals("all gpu tests ran", gpu.reason)
        assertEquals("LOW_CATEGORY_COVERAGE", gpu.letterCeilingReason?.name)
        assertEquals("B", gpu.letterCeilingReason?.ceilingLetter)
        assertEquals("synthetic fixture text — not sample-derived", gpu.letterCeilingReason?.text)
        assertEquals(listOf("GP-01", "GP-02"), gpu.testIds)
        assertNull("non-battery category must not carry the battery-only fields", gpu.confidence)
        assertNull(gpu.tag)
        assertNull(gpu.indicators)
        assertNull(gpu.socAtStart)
        assertNull(gpu.socAtEnd)

        // categories — battery (the only one with confidence/tag/indicators/soc*)
        val battery = r.categories!!.getValue("battery")
        assertEquals("drain rate borderline", battery.adjustmentReason)
        assertEquals(listOf("categoryStates.categories.battery.deterministicLetter"), battery.adjustmentEvidence)
        assertEquals("medium", battery.confidence)
        assertEquals("borderline", battery.tag)
        assertEquals(90, battery.socAtStart)
        assertEquals(82, battery.socAtEnd)
        val indicator = battery.indicators!!.getValue("BAT-DRAIN")
        assertEquals("BAT-DRAIN", indicator["id"])
        assertEquals("MEASURED", indicator["status"])
        assertEquals("%/11min", indicator["unit"])

        // issues[] — the two V7 additions this file's fixture used to omit
        val issue = r.issues!!.first()
        assertEquals("ISS-01", issue.id)
        assertEquals("%", issue.evidenceUnit)
        assertEquals("8", issue.evidence)

        // narrative
        val narrative = r.narrative!!
        assertEquals("ملخص V7", narrative.summary)
        assertEquals("تفاصيل V7", narrative.details)
        assertEquals("حكم V7", narrative.verdict)
        assertEquals("casual advice", narrative.buyerAdvice?.casual)
        assertEquals("gamer advice", narrative.buyerAdvice?.gamer)
        assertNull(narrative.buyerAdvice?.photographer)

        // The flat V6 fields must NOT be populated by a V7 payload — this
        // pins the "not merged into the display-feeding fields" boundary
        // decision from the Phase 3-a report.
        assertNull(r.summary)
        assertNull(r.details)
        assertNull(r.verdict)
        assertNull(r.grade)
        assertNull(r.score)
        assertNull(r.categoryScores)
    }

    @Test
    fun `isComplete is true for V7 when overall letter is present`() {
        val r = gson.fromJson(fullV7Json, AiAnalysisResponse::class.java)
        assertTrue(r.isComplete())
    }

    @Test
    fun `isComplete is false for V7 when overall is entirely absent`() {
        val json = """{"pipeline":"v7_findings_based","narrative":{"summary":"s"}}"""
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertFalse(
            "a V7 response with content in narrative but no overall.letter must " +
                "still read incomplete — narrative content alone was never a V6 " +
                "completeness signal for V7 (see isComplete's doc)",
            r.isComplete()
        )
    }

    @Test
    fun `isComplete is false for V7 when overall letterSource is blank, even if present as a key`() {
        // Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦) — القاعدة 23: this test
        // used to be named "... when overall letter is blank" and asserted
        // false on `{"letter":""}` alone (no letterSource key at all). That
        // fixture happened to still read false after this fix too, but for
        // the WRONG reason by the test's own old name (letterSource being
        // absent, not letter being blank) — replaced so the fixture and
        // assertion actually test what the name claims: a blank
        // letterSource (the real gate), independent of `letter`'s value.
        val json = """{"pipeline":"v7_findings_based","overall":{"letter":"B","letterSource":""}}"""
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertFalse(r.isComplete())
    }

    @Test
    fun `isComplete is true for V7 when letter is blank or absent but letterSource is declared — no letter required`() {
        // The order's own requirement, at the isComplete() level (see the
        // HARNESS_ERROR-specific fixture-backed proof in
        // FirebaseAnalysisServiceCacheGateTest's Honor B tests): a
        // finished V7 response never requires `letter` — only the
        // declared state (letterSource) — so `noDataAtAll`'s
        // `letterSource: "NO_DATA"` with no letter at all must also read
        // complete, not just the HARNESS_ERROR case.
        val json = """{"pipeline":"v7_findings_based","overall":{"letterSource":"NO_DATA","noDataAtAll":true}}"""
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertNull(r.overall?.letter)
        assertTrue(r.isComplete())
    }

    @Test
    fun `isComplete V6 branch is unchanged for explicit v6_raw_timeseries`() {
        val json = """
            {"pipeline":"v6_raw_timeseries","summary":"s","details":"","verdict":""}
        """.trimIndent()
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertTrue(r.isComplete())
    }

    @Test
    fun `isComplete V6 branch is unchanged for legacy reports with no pipeline field at all`() {
        val json = """{"summary":"s"}"""
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertNull("legacy fixture must not accidentally carry a pipeline value", r.pipeline)
        assertTrue(r.isComplete())
    }

    @Test
    fun `isComplete V6 branch is still false for grade+score only, exactly as before`() {
        // Pins the pre-existing V6 behaviour this task deliberately left
        // unchanged (see isComplete's doc: "A doc with only grade + score
        // is NOT complete").
        val json = """{"grade":"B","score":70}"""
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertFalse(r.isComplete())
    }

    @Test
    fun `resync 2026-09-26 (القرار ١٠٢ البند ٢) — an unrecognised pipeline value is NOT complete, not a crash`() {
        // Rule 23 — REPLACES the old test of the same slot, which asserted
        // an unrecognised tag falls back to the V6 branch and passes just
        // because it happens to carry V6-shaped content (`summary`). That
        // silently treats "we don't recognise this tag" as "this is a
        // known-good V6 report" — exactly the rule-22 concern this order's
        // AiPipeline.classifyShape addresses: UNKNOWN is never complete.
        // "Not a crash" is still proven — no exception, just `false`.
        val json = """{"pipeline":"v8_unknown_future_shape","summary":"still readable"}"""
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertEquals("v8_unknown_future_shape", r.pipeline)
        assertFalse(r.isComplete())
    }

    @Test
    fun `lenient coverage and coveragePct adapters degrade to null instead of throwing on an unexpected token`() {
        // coverage/coveragePct as a JSON object — the one shape none of
        // Gson's stock primitive adapters, lenient or not, can coerce.
        // Confirms the WHOLE response still parses (the point of
        // LenientDoubleAdapter) instead of the parse failing and
        // AiScoringEngine's catch(Throwable) discarding every other
        // field in the response. Both fields use LenientDoubleAdapter
        // now — coveragePct was retyped from Int to Double after the
        // golden sample showed the contract's real type is a general
        // number, not guaranteed integer.
        // letterSource included (order 2026-09-24، القرار ٨٠/٨٢ البند ٦: it,
        // not `letter`, is what isComplete() gates V7 on now) — this
        // fixture's own point is the coverage/coveragePct lenient adapters,
        // not the completeness gate, so it carries a realistic letterSource
        // rather than accidentally exercising the "letterSource absent"
        // branch.
        val json = """
            {"pipeline":"v7_findings_based","overall":{"letter":"C","letterSource":"BAND","coverage":{"nested":true},"coveragePct":{"nested":true}}}
        """.trimIndent()
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertEquals("C", r.overall?.letter)
        assertNull(r.overall?.coverage)
        assertNull(r.overall?.coveragePct)
        assertTrue("the rest of the response must still be usable", r.isComplete())
    }

    @Test
    fun `lenient coverage adapters accept a numeric-looking string`() {
        val json = """
            {"overall":{"coverage":"0.5","coveragePct":"50"}}
        """.trimIndent()
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertEquals(0.5, r.overall?.coverage!!, 0.0001)
        assertEquals(50.0, r.overall?.coveragePct!!, 0.0001)
    }

    @Test
    fun `LLM_ADJUSTED category with a multi-item adjustmentEvidence array parses with full content, not just non-crash`() {
        // Rule 30 — tests what the reader PRODUCES (every element lands),
        // not merely that parsing survives. Two elements, deliberately in
        // the internal-code-path shape the relayed backend report showed
        // (e.g. "categoryStates.categories.storage.deterministicLetter"),
        // not prose — this field carries evidence *references*, not text.
        val json = """
            {
              "pipeline": "v7_findings_based",
              "overall": {"letter": "D", "letterSource": "LLM_ADJUSTED"},
              "categories": {
                "storage": {
                  "state": "MEASURED",
                  "letter": "D",
                  "letterSource": "LLM_ADJUSTED",
                  "adjustmentReason": "storage degraded vs deterministic baseline",
                  "adjustmentEvidence": [
                    "categoryStates.categories.storage.deterministicLetter",
                    "categoryStates.categories.storage.rawWriteIops"
                  ]
                }
              }
            }
        """.trimIndent()
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        val storage = r.categories!!.getValue("storage")
        assertEquals(
            listOf(
                "categoryStates.categories.storage.deterministicLetter",
                "categoryStates.categories.storage.rawWriteIops"
            ),
            storage.adjustmentEvidence
        )
        assertTrue("the rest of the response must still be usable", r.isComplete())
    }

    @Test
    fun `LenientStringListAdapter degrades adjustmentEvidence to null instead of throwing on an unexpected token, whole response still parses`() {
        // The exact failure this fix targets: before LenientStringListAdapter,
        // a plain String? field hitting a JSON object here threw
        // JsonSyntaxException out of gson.fromJson with no local catch,
        // discarding the ENTIRE AiAnalysisResponse (AiScoringEngine's
        // catch(Throwable) swallows it as a generic connection-style
        // error) — not just this one field. An object is deliberately the
        // shape under test since it is the one token neither the old
        // String? field nor a bare-array coercion could ever have
        // accepted.
        val json = """
            {
              "pipeline": "v7_findings_based",
              "overall": {"letter": "C", "letterSource": "LLM_ADJUSTED"},
              "categories": {
                "storage": {
                  "state": "MEASURED",
                  "letterSource": "LLM_ADJUSTED",
                  "adjustmentEvidence": {"unexpected": "object"}
                }
              }
            }
        """.trimIndent()
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertNull(r.categories!!.getValue("storage").adjustmentEvidence)
        assertEquals("C", r.overall?.letter)
        assertTrue("the rest of the response must still be usable", r.isComplete())
    }

    @Test
    fun `LenientStringListAdapter degrades a bare string adjustmentEvidence to null instead of throwing, in case the backend ever regresses to the old shape`() {
        // The shape this field itself used to be typed as here (a bare
        // STRING) — this app repo cannot confirm the backend will never
        // send it again, so the adapter must survive it too rather than
        // crash the parse, exactly like the object case above.
        val json = """
            {"pipeline":"v7_findings_based","categories":{"storage":{"adjustmentEvidence":"a bare string"}}}
        """.trimIndent()
        val r = gson.fromJson(json, AiAnalysisResponse::class.java)
        assertNull(r.categories!!.getValue("storage").adjustmentEvidence)
    }
}
