package net.neuralcheck.data.remote.model

import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Task order 2026-09-23, requirement (٥): "إثبات أن التطبيق يقرأ تقرير V7
 * كاملاً بتشغيل لا بالذاكرة" — runs the REAL read path (the same
 * `Gson()`/`AiAnalysisResponse` deserialization
 * [net.neuralcheck.data.remote.FirebaseAnalysisService] uses, see its own
 * `gson.fromJson(resultJson, AiAnalysisResponse::class.java)` /
 * `gson.fromJson(json, AiAnalysisResponse::class.java)` — both of its two
 * report sources, see this class's own doc below) against the FULL golden
 * sample, and for every field named in the contract handoff's
 * `v7ContractFieldMap.js` (transcribed below — copied text, NOT
 * independently re-verified against that file at test time, so it can
 * drift the way rule 29 warns about; re-copy on the next contract update)
 * DIRECTLY accesses the corresponding Kotlin property.
 *
 * Why direct property access instead of runtime reflection: this module
 * has no `kotlin-reflect` dependency (checked: absent from
 * `app/build.gradle.kts`'s `dependencies` block) — adding one is out of
 * this task's scope. Direct access is actually a STRONGER guarantee for
 * "is this field read at all": if a contract field were renamed or
 * dropped from a Kotlin data class, this file would FAIL TO COMPILE, not
 * merely fail an assertion at runtime — caught by the same
 * `./gradlew testLaunchDebugUnitTest` run this whole task's field-coverage
 * table below is generated from.
 *
 * What this test does NOT prove, by design (told explicitly, not
 * overstated — this task's own instruction: "إن لم يكفِ اختبار JVM لإثبات
 * العرض، قل ما الذي لا يُثبته"):
 *   - That a value is actually PAINTED on screen. When written this module did not have
 *     Robolectric/Compose UI test infrastructure (checked then:
 *     `testImplementation` in `app/build.gradle.kts` had no
 *     `robolectric`/`compose...test` entry; both are there since 3284cca, and no test renders these two surfaces). Which Kotlin property each
 *     `ReportScreen.kt`/`ReportPdfGenerator.kt` Composable reads is
 *     determined separately, by grep over those two files (see this
 *     task's own report for that table, not this file) — a real tool
 *     execution against the source, not a memory claim, but not a JVM
 *     test proving pixels either.
 *   - `categories.battery.indicators.<id>.*` field-by-field — deliberately
 *     OPAQUE per `v7ContractFieldMap.js`'s own `OPAQUE_FIELDS` /// this
 *     app's [net.neuralcheck.data.remote.model.AiCategoryV7.indicators]
 *     doc: read as a generic `Map<String, Map<String, Any?>>`, not a fixed
 *     per-indicator schema. Tested for reachability as a map, not
 *     enumerated key by key here (that would invent a schema the contract
 *     explicitly declines to fix).
 *   - The 10 `audit`-only fields `v7ContractFieldMap.js` names as
 *     deliberately EXCLUDED (`measuredCategoriesMean`/`gatesApplied`/
 *     `adjustments`/`disputed`/`categoryLlmSuggestedLetters`/
 *     `crossChecks`/`modelObservations`/`issueSetHash`/`lettersHash`/
 *     `numbersHash`) — these never reach `buildV7ResponseForStorage` at
 *     all per that file's own comment (decision 4+6: stored in `aiAudit`
 *     separately, never sent to the client), so there is nothing for this
 *     app to read; not tested here, not a gap.
 */
class V7FullDocumentReadCoverageTest {

    private val gson = Gson()

    private fun loadSample(): AiAnalysisResponse {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")
            ?: error("contract/v7-client-payload-sample.json not found on the test classpath")
        return InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, AiAnalysisResponse::class.java)
        }
    }

    /**
     * One row of the produced coverage table. [value] is the ACTUAL value
     * read from the live parsed sample (or a short description for a
     * collection), captured by running this test, not typed from memory.
     */
    private data class FieldRow(val path: String, val value: String)

    private val rows = mutableListOf<FieldRow>()
    private fun record(path: String, value: Any?) {
        rows.add(FieldRow(path, value?.toString()?.take(80) ?: "null"))
    }

    @Test
    fun `every field in v7ContractFieldMap-js's ROOT_FIELDS OVERALL_FIELDS CATEGORY_COMMON_FIELDS etc is reachable through the real parsed golden sample`() {
        val r = loadSample()

        // ── ROOT_FIELDS (12) ──
        record("pipeline", r.pipeline)
        record("language", r.language)
        record("overall", if (r.overall != null) "present" else "null")
        record("categories", "size=${r.categories?.size}")
        record("issues", "size=${r.issues?.size}")
        record("narrative", if (r.narrative != null) "present" else "null")
        record("narrativeIncomplete", r.narrativeIncomplete)
        record("narrativeIncompleteFields", r.narrativeIncompleteFields)
        record("shortId", r.shortId)
        record("isFreeTrial", r.isFreeTrial)
        record("aiModel", r.aiModel)
        record("deviceMarketingName", r.deviceMarketingName)
        assertTrue("pipeline must be reachable and non-null on this sample", r.pipeline != null)
        assertTrue("overall must be reachable and non-null on this sample", r.overall != null)
        assertTrue("categories must be reachable with all 15 entries", r.categories?.size == 15)
        // Rule 23 — resynced 2026-09-30 from neuralcheck-backend@c7446a4:
        // was 31 before 11 tests became non-health-bearing (6 issues[]
        // entries actually removed from this sample).
        assertTrue("issues must be reachable with all 25 entries", r.issues?.size == 25)
        assertTrue("narrative must be reachable and non-null on this sample", r.narrative != null)

        // ── OVERALL_FIELDS (12) ──
        val overall = r.overall!!
        record("overall.letter", overall.letter)
        record("overall.letterSource", overall.letterSource)
        record("overall.coverage", overall.coverage)
        record("overall.coveragePct", overall.coveragePct)
        record("overall.partialAssessment", overall.partialAssessment)
        record("overall.partialAssessmentTag", if (overall.partialAssessmentTag != null) "present" else "null")
        record("overall.governingGateReason", if (overall.governingGateReason != null) "present" else "null")
        record("overall.noDataAtAll", overall.noDataAtAll)
        record("overall.zeroMeasuredMessage", overall.zeroMeasuredMessage)
        record("overall.abstentionReasons", overall.abstentionReasons)
        record("overall.harnessError", overall.harnessError)
        record("overall.harnessErrorReason", overall.harnessErrorReason)

        // ── PARTIAL_ASSESSMENT_TAG_FIELDS (3) — non-null on this sample ──
        val tag = overall.partialAssessmentTag!!
        record("overall.partialAssessmentTag.label", tag.label)
        record("overall.partialAssessmentTag.coveragePct", tag.coveragePct)
        record("overall.partialAssessmentTag.text", tag.text)

        // ── GOVERNING_GATE_REASON_FIELDS (4) — non-null on this sample ──
        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29:
        // was (3). The sync adds `governingGateReason.text` (a92d503), so
        // the row set grows by exactly this one row — and the assertion
        // below pins it non-blank: a reader that dropped the field would
        // otherwise still print "null" in the table and pass.
        val gate = overall.governingGateReason!!
        record("overall.governingGateReason.name", gate.name)
        record("overall.governingGateReason.ceilingLetter", gate.ceilingLetter)
        record("overall.governingGateReason.source", gate.source)
        record("overall.governingGateReason.text", gate.text)
        assertTrue(
            "overall.governingGateReason.text must be reachable and non-blank on this sample",
            !gate.text.isNullOrBlank()
        )

        // ── CATEGORY_COMMON_FIELDS (9) x 15 categories — spot-checked on
        // "gpu" (has every field non-null, including the new letterCeilingReason)
        // and "cpu" (letterCeilingReason present-but-null) ──
        val gpu = r.categories!!.getValue("gpu")
        record("categories.gpu.state", gpu.state)
        record("categories.gpu.letter", gpu.letter)
        record("categories.gpu.letterSource", gpu.letterSource)
        record("categories.gpu.adjustmentReason", gpu.adjustmentReason)
        record("categories.gpu.adjustmentEvidence", gpu.adjustmentEvidence)
        record("categories.gpu.coverage", gpu.coverage)
        record("categories.gpu.reason", gpu.reason)
        record("categories.gpu.testIds", gpu.testIds)

        // `letterCeilingReason` is ONE contract field — record it ONCE, not
        // once per category spot-checked. Previously recorded as two
        // SEPARATE rows (gpu's non-null case on one line, cpu's
        // present-key-null-value case on another), inflating this test's
        // own printed field count by one (62 instead of the true 61 —
        // found 2026-09-24, جولة جرد القيم الافتراضية، القسم ٣). Both
        // spot-checks are still performed and asserted; only the row/count
        // is merged so "N fields recorded" below counts distinct contract
        // fields, not distinct example categories.
        val cpu = r.categories!!.getValue("cpu")
        record(
            "categories.<key>.letterCeilingReason",
            "gpu=present(name=${gpu.letterCeilingReason?.name}), cpu=null (key present, value null)"
        )
        assertTrue("gpu.letterCeilingReason must be reachable and non-null on this sample", gpu.letterCeilingReason != null)
        assertTrue(
            "cpu.letterCeilingReason must be reachable as an explicit null (key present, value null) on this sample",
            cpu.letterCeilingReason == null
        )

        // ── CATEGORY_LETTER_CEILING_REASON_FIELDS (3) — non-null on gpu ──
        val gpuCeiling = gpu.letterCeilingReason!!
        record("categories.gpu.letterCeilingReason.name", gpuCeiling.name)
        record("categories.gpu.letterCeilingReason.ceilingLetter", gpuCeiling.ceilingLetter)
        record("categories.gpu.letterCeilingReason.text", gpuCeiling.text)

        // ── CATEGORY_BATTERY_EXTRA_FIELDS (5) — battery only ──
        val battery = r.categories!!.getValue("battery")
        record("categories.battery.confidence", battery.confidence)
        record("categories.battery.tag", battery.tag)
        record("categories.battery.indicators", "size=${battery.indicators?.size} (read as opaque Map, by contract design)")
        record("categories.battery.socAtStart", battery.socAtStart)
        record("categories.battery.socAtEnd", battery.socAtEnd)
        assertTrue("battery.indicators must be reachable with all 4 entries", battery.indicators?.size == 4)

        // ── ISSUE_FIELDS (7) — spot-checked on the sample's one FAIL issue ──
        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29:
        // REPLACES the spot-check on `ISS-S-06` (storage WARNING, the one
        // issue that carried a non-null evidenceUnit, "IOPS"). That issue is
        // gone from the sample and NO issue in it carries an evidenceUnit
        // now, so `evidenceUnit` is recorded here as the explicit null it
        // really is on ISS-SEC-04 — the non-null read of that field is
        // proven on the real cpu-c06-downgrade issue ISS-C-06 ("%") in
        // V7GoldenSampleTest's issues test, not here.
        val issue = r.issues!!.first { it.id == "ISS-SEC-04" }
        record("issues[ISS-SEC-04].id", issue.id)
        record("issues[ISS-SEC-04].category", issue.category)
        record("issues[ISS-SEC-04].severity", issue.severity)
        record("issues[ISS-SEC-04].title", issue.title)
        record("issues[ISS-SEC-04].evidence", issue.evidence)
        record("issues[ISS-SEC-04].evidenceUnit", issue.evidenceUnit)
        record("issues[ISS-SEC-04].description", issue.description)

        // ── NARRATIVE_FIELDS (4) ──
        // d5b6c29 sample: `details` and `verdict` are absent from the
        // payload (only `summary` and `buyerAdvice` arrive) and print as
        // "null" in the table below — the real shape, not a read failure
        // (the app's V7 render path no longer reads them, ReportScreen.kt
        // :2393-2410; V7GoldenSampleTest asserts the nulls).
        val narrative = r.narrative!!
        record("narrative.summary", narrative.summary)
        record("narrative.details", narrative.details)
        record("narrative.verdict", narrative.verdict)
        record("narrative.buyerAdvice", if (narrative.buyerAdvice != null) "present (3 real non-null leaves on this sample, see contract/README.md)" else "null")
        assertTrue("narrative.buyerAdvice must be reachable as a present object (not null itself)", narrative.buyerAdvice != null)
        // buyerAdvice's own three fields — referenced in v7ContractFieldMap.js's
        // NARRATIVE_FIELDS comment (casual/gamer/photographer) though not a
        // separately-guarded FIELDS constant there.
        record("narrative.buyerAdvice.casual", narrative.buyerAdvice?.casual)
        record("narrative.buyerAdvice.gamer", narrative.buyerAdvice?.gamer)
        record("narrative.buyerAdvice.photographer", narrative.buyerAdvice?.photographer)

        // Print the produced table — this is the "تشغيل لا ذاكرة" artifact:
        // every line below is an ACTUAL value from THIS test run against
        // THIS sample, not typed from code reading.
        println("=== V7 contract field read-coverage (produced by this test run) ===")
        rows.forEach { println("${it.path} = ${it.value}") }
        println("=== ${rows.size} fields recorded, 0 threw, 0 required a field this app's Kotlin classes don't declare ===")
    }
}
