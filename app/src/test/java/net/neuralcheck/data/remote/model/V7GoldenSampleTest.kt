package net.neuralcheck.data.remote.model

import com.google.gson.Gson
import net.neuralcheck.domain.model.isComplete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Phase 3-a barrier condition (أ): "لا يُعدّ 3-أ منجزاً قبل أن يُقابَل
 * الشكل بعيّنة حقيقية من المسار نفسه" — deserializes the ACTUAL golden
 * sample (`app/src/test/resources/contract/v7-client-payload-sample.json`,
 * copied verbatim from neuralcheck-backend @ `77b710a` (2026-09-24 —
 * was `154f3f2`, 2026-09-23, before this `letterCeilingReason.text`
 * wording refinement; see `contract/README.md`'s own "التحديث 2026-09-23"
 * and "التحديث 2026-09-24" sections for the full path-by-path diff),
 * `functions/test/fixtures/golden/v7-client-payload-sample.json`,
 * generated through the real backend chain — computeFindings ->
 * computeCategoryStates -> applyV7Adjustments -> buildDisplayShapeV7 ->
 * buildV7ResponseForStorage -> V7_OPERATIONAL_FIELDS — zero model calls)
 * through the SAME `Gson()` + the SAME `AiAnalysisResponse` class +
 * the SAME `@JsonAdapter`s production uses
 * ([net.neuralcheck.data.remote.FirebaseAnalysisService]'s `gson.fromJson(resultJson,
 * AiAnalysisResponse::class.java)`), not a hand-built fixture and not a
 * manually re-implemented parser.
 *
 * Never edits the sample file — see `contract/README.md`, read in full
 * before writing these assertions. Three-way null classification used
 * throughout, per that README:
 *   - "حقيقي" (real) — the field IS null for this device, for the named
 *     reason. Asserted null; no different value would ever be correct
 *     for this exact sample.
 *   - "نقص عيّنة" (sample gap) — null here because the generator's input
 *     predates something (a decision, real device.model data), NOT
 *     because the field is unparseable. Asserted null too, but the
 *     comment says explicitly this is a sample artifact, not evidence
 *     the field is always this value.
 *   - "حدّ العيّنة" (sample limit) — `resolveV7Report` was never called
 *     for this sample, so `narrativeIncomplete`/`narrativeIncompleteFields`
 *     do NOT exercise the guard-rejection path. Asserted as given, with
 *     an explicit note that this is NOT a reference for their behavior
 *     under a rejected guard.
 */
class V7GoldenSampleTest {

    private val gson = Gson()

    private fun loadSample(): AiAnalysisResponse {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")
            ?: error(
                "contract/v7-client-payload-sample.json not found on the test " +
                    "classpath — expected at app/src/test/resources/contract/"
            )
        return InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, AiAnalysisResponse::class.java)
        }
    }

    @Test
    fun `the golden sample parses at all, through the real production deserializer`() {
        val r = loadSample()
        assertNotNull(r)
        assertEquals(AiPipeline.V7_FINDINGS_BASED, r.pipeline)
    }

    @Test
    fun `every root field arrives with its documented value`() {
        val r = loadSample()
        assertEquals("ar", r.language)
        // shortId is minted fresh on every sample regeneration (random per
        // README.md) — resynced 2026-10-07 (rule 23) from
        // neuralcheck-backend@d5b6c29, was "NC-BJFRL2" under the c7446a4
        // sample (and "NC-SEZDC6" under the 78d115b-era one before it).
        assertEquals("NC-N4KMF5", r.shortId)
        assertEquals(false, r.isFreeTrial)
        assertEquals("deepseek-flash", r.aiModel)
        // "نقص عيّنة" — fingerprint carried no device.model, not a parse
        // failure. Not evidence this field is always "Unknown".
        assertEquals("Unknown", r.deviceMarketingName)
        // "حدّ العيّنة" — resolveV7Report (the guard) was never invoked for
        // this sample. Not a reference for these two fields' behavior
        // when guard.ok=false; see contract/README.md.
        assertEquals(false, r.narrativeIncomplete)
        assertEquals(emptyList<String>(), r.narrativeIncompleteFields)
    }

    @Test
    fun `overall arrives complete, including the two nested objects this app originally mistyped as strings`() {
        val overall = loadSample().overall!!
        assertEquals("C", overall.letter)
        assertEquals("GATE:ROOT_DETECTED", overall.letterSource)
        // Rule 23 — resynced 2026-09-30 from neuralcheck-backend@c7446a4:
        // was 0.4503809523809524/45.0 before gps lost its LOW_CATEGORY_COVERAGE
        // ceiling (DECLARED_BUT_NONE_EXECUTED instead) and security went
        // PARTIAL->MEASURED, which shifted the coverage denominator.
        assertEquals(0.4587142857142857, overall.coverage!!, 0.00000001)
        assertEquals(45.9, overall.coveragePct!!, 0.00000001)
        assertEquals(true, overall.partialAssessment)
        assertEquals(false, overall.noDataAtAll)
        assertEquals(false, overall.harnessError)

        // Corrected shape #1: was assumed String, is an object.
        val tag = overall.partialAssessmentTag!!
        assertEquals("تقييم جزئي", tag.label)
        assertEquals(45.9, tag.coveragePct!!, 0.00000001)
        assertEquals("تقييم جزئي — تغطية 45.9%", tag.text)

        // Corrected shape #2: was assumed String, is an object.
        val reason = overall.governingGateReason!!
        assertEquals("ROOT_DETECTED", reason.name)
        assertEquals("C", reason.ceilingLetter)
        assertEquals("SEC-04=fail", reason.source)
        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29
        // (the order's explicit item: the sync adds governingGateReason.text,
        // a92d503). The old assertions above stopped at name/ceilingLetter/
        // source because the sample had no `text`; the sample now carries the
        // server's own display sentence, and THIS is the value the gate card
        // shows (GoverningGateReasonTextTest asserts what the reader produces
        // from it). Asserted verbatim: a backend wording change must fail here.
        assertEquals("أعلى حرف C: صلاحيات الجذر (الروت) مكتشَفة على الجهاز.", reason.text)

        // "حقيقي" per README — genuinely null for this device/run, not a
        // parsing gap. No positive value in this sample confirms their
        // types; kept as the most defensible guess (see AiOverallV7's doc).
        assertNull(overall.zeroMeasuredMessage)
        assertNull(overall.abstentionReasons)
        assertNull(overall.harnessErrorReason)
        // Order 2026-10-01 (A8/S-13، أمر القرارات): new field, absent from
        // this sample (predates it) — same "حقيقي، لا فجوة تحليل" status as
        // its siblings above.
        assertNull(overall.harnessErrorMessage)
    }

    @Test
    fun `all 15 categories parse and no category is silently dropped`() {
        val categories = loadSample().categories!!
        assertEquals(15, categories.size)
        assertEquals(
            setOf(
                "battery", "thermal", "cpu", "gpu", "display", "memory", "storage",
                "camera", "gps", "security", "sensors", "cellular", "wireless",
                "audio", "input"
            ),
            categories.keys
        )
    }

    @Test
    fun `battery carries its five battery-only fields, including the two never in the textual contract`() {
        val battery = loadSample().categories!!.getValue("battery")
        assertEquals("PARTIAL", battery.state)
        // "حقيقي" per README — genuinely null.
        assertNull(battery.letter)
        assertEquals("DETERMINISTIC", battery.letterSource)
        assertEquals(1.0, battery.coverage!!, 0.00000001)
        assertEquals(listOf<String>(), battery.testIds)
        assertEquals("ESTIMATED", battery.tag)
        // "حقيقي" per README — the confidence KEY is present but its
        // value is genuinely null for this run; not a parsing gap.
        assertNull(battery.confidence)
        // socAtStart/socAtEnd — observed directly in the golden sample,
        // not in the original textual contract at all.
        assertEquals(43, battery.socAtStart)
        assertEquals(38, battery.socAtEnd)

        // Corrected shape #3: indicators is a heterogeneous map, not a
        // list of strings. All four indicators arrive, keyed by id.
        val indicators = battery.indicators!!
        assertEquals(4, indicators.size)
        assertEquals(
            setOf("BAT-THERMAL-EFF", "BAT-R-APPARENT", "BAT-FCC", "BAT-COULOMB"),
            indicators.keys
        )
        val thermalEff = indicators.getValue("BAT-THERMAL-EFF")
        assertEquals("BAT-THERMAL-EFF", thermalEff["id"])
        assertEquals("MEASURED", thermalEff["status"])
        assertEquals("°C/Wh", thermalEff["unit"])
        // A field that exists on ONLY this one indicator, not the other
        // three — proof the generic map does not drop indicator-specific
        // fields a fixed schema would have had to omit or misname.
        assertEquals(false, thermalEff["comparedToModelBaseline"])
        val coulomb = indicators.getValue("BAT-COULOMB")
        // consistencyK is explicitly null in the sample (a real,
        // documented null — see the sample's own consistencyGateNote),
        // not absent — confirms the generic map preserves an explicit
        // null leaf too, not just present-with-value keys.
        assertTrue(coulomb.containsKey("consistencyK"))
        assertNull(coulomb["consistencyK"])
    }

    @Test
    fun `non-battery categories never carry the battery-only fields`() {
        val categories = loadSample().categories!!
        for (key in listOf(
            "thermal", "cpu", "gpu", "display", "memory", "storage", "camera",
            "gps", "security", "sensors", "cellular", "wireless", "audio", "input"
        )) {
            val cat = categories.getValue(key)
            assertNull("$key.confidence must be null (battery-only)", cat.confidence)
            assertNull("$key.tag must be null (battery-only)", cat.tag)
            assertNull("$key.indicators must be null (battery-only)", cat.indicators)
            assertNull("$key.socAtStart must be null (battery-only)", cat.socAtStart)
            assertNull("$key.socAtEnd must be null (battery-only)", cat.socAtEnd)
        }
    }

    @Test
    fun `three categories carry the 'حقيقي' null letter or adjustmentReason the README names`() {
        val categories = loadSample().categories!!
        // "حقيقي" per README, each with its own named reason elsewhere
        // (NEURALCHECK-FINDINGS.md) — asserted null because that IS the
        // correct value for this sample, not a parsing failure.
        assertNull(categories.getValue("thermal").letter)
        assertNull(categories.getValue("cpu").adjustmentReason)
        assertNull(categories.getValue("thermal").adjustmentReason)
        assertNull(categories.getValue("security").adjustmentReason)
    }

    @Test
    fun `letterCeilingReason arrives on the 7 gated categories with per-category text, null-but-present on the other 8`() {
        // Added to the golden sample by the 154f3f2 regeneration, wording
        // refined by the 77b710a regeneration — see contract/README.md's
        // "التحديث 2026-09-23" and "التحديث 2026-09-24" sections. Rule 30:
        // the reader's PRODUCED value is asserted, not merely non-null.
        //
        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29.
        // TWO things in this test's old content were pinned to the c7446a4
        // sample and are REPLACED (not added to):
        //   1. the sentence's second half. Was "... — حرف مسقوف: لم يُقَس
        //      في هذه الفئة أيّ اختبار معتمد قياساً للحالة." (said "nothing
        //      measured" — false for a MEASURED category like display).
        //      Is "... — أعلى حرف لهذه الفئة B في هذا الإصدار: اختباراتها
        //      تتأكّد من وجود المكوّنات وعملها، ولا تقيس أداءها بعد."
        //   2. the gated set. storage moved INTO it (was letter C with the
        //      key declared null; is B under LOW_CATEGORY_COVERAGE) — so 7
        //      gated and 8 null, not 6 and 9.
        val categories = loadSample().categories!!

        val gpu = categories.getValue("gpu").letterCeilingReason!!
        assertEquals("LOW_CATEGORY_COVERAGE", gpu.name)
        assertEquals("B", gpu.ceilingLetter)
        assertEquals(
            "تغطية منخفضة — اختباران من خمسة، وأعلى حرف لهذه الفئة B في هذا الإصدار: اختباراتها تتأكّد من وجود المكوّنات وعملها، ولا تقيس أداءها بعد.",
            gpu.text
        )

        // display: same gate/ceilingLetter as gpu, DIFFERENT text with no
        // "تغطية منخفضة" lead-in — proof the text is built per-category
        // (executedCount/declaredCount), not a shared constant, per the
        // contract handoff's own documented example.
        val display = categories.getValue("display").letterCeilingReason!!
        assertEquals("LOW_CATEGORY_COVERAGE", display.name)
        assertEquals("B", display.ceilingLetter)
        assertEquals(
            "ثلاثة اختبارات من ثلاثة — أعلى حرف لهذه الفئة B في هذا الإصدار: اختباراتها تتأكّد من وجود المكوّنات وعملها، ولا تقيس أداءها بعد.",
            display.text
        )
        assertTrue(
            "display's text must NOT share gpu's 'تغطية منخفضة' lead-in — proves per-category text, not a template",
            !display.text!!.startsWith("تغطية منخفضة")
        )

        for (key in listOf("memory", "sensors", "cellular", "wireless", "storage")) {
            val reason = categories.getValue(key).letterCeilingReason
            assertNotNull("$key.letterCeilingReason must be a present object", reason)
            assertEquals("$key.letterCeilingReason.name", "LOW_CATEGORY_COVERAGE", reason!!.name)
            assertEquals("$key.letterCeilingReason.ceilingLetter", "B", reason.ceilingLetter)
            assertTrue("$key.letterCeilingReason.text must be non-blank", reason.text!!.isNotBlank())
        }

        // Rule 23 — resynced 2026-09-30 from neuralcheck-backend@c7446a4:
        // gps moved OUT of this gated set. It is no longer PARTIAL/B with a
        // LOW_CATEGORY_COVERAGE ceiling — it is now UNMEASURED, letter null,
        // reason DECLARED_BUT_NONE_EXECUTED, and letterCeilingReason itself
        // is null (no gate fired at all, not merely "ceiling not hit").
        // The other 8 categories: key IS present in this sample (regenerated
        // by 154f3f2 for every category), value is null — the contract's
        // "no gate ceilinged this letter" answer, NOT the "field predates
        // this report" answer (see the NOT_DECLARED test against the
        // llm-adjusted sample below / AiCategoryLetterCeilingReasonTest).
        for (key in listOf("battery", "thermal", "cpu", "camera", "security", "audio", "input", "gps")) {
            assertNull("$key.letterCeilingReason must be null (no LOW_CATEGORY_COVERAGE ceiling this run)", categories.getValue(key).letterCeilingReason)
        }
        // storage's letter moved with its ceiling (C -> B, d5b6c29).
        assertEquals("B", categories.getValue("storage").letter)

        // This sample's own raw JSON declares the key on every one of the
        // 15 categories (even where the value is null) — the presence
        // side-channel must report zero categories missing it, for THIS
        // sample specifically (contrast with the llm-adjusted sample,
        // which declares it on none).
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/v7-client-payload-sample.json")!!
        val rawJson = InputStreamReader(stream, Charsets.UTF_8).readText()
        assertEquals(emptySet<String>(), categoriesMissingLetterCeilingReason(rawJson))
    }

    @Test
    fun `all 25 issues parse and none is silently dropped`() {
        // Rule 23 — resynced 2026-09-30 from neuralcheck-backend@c7446a4:
        // was 31. Eleven tests became non-health-bearing (no issues[] entry,
        // no letter/ceiling effect); six issues actually disappeared from
        // THIS sample: ISS-SEC-05, ISS-SEC-06, DISC-SEC-07, DISC-CM-08,
        // DISC-T-01, DISC-T-02.
        val issues = loadSample().issues!!
        assertEquals(25, issues.size)
    }

    @Test
    fun `issues carry id and evidenceUnit — the two V7 fields this app originally had no field for at all`() {
        val issues = loadSample().issues!!
        // Every issue has an id — pick the first and confirm it's non-null
        // (previously would have silently had no Kotlin field to land in).
        // Rule 23 — resynced 2026-09-30: was "DISC-T-01", removed from this
        // sample by neuralcheck-backend@c7446a4; "DISC-T-03" is now first.
        assertEquals("DISC-T-03", issues.first().id)
        assertTrue("every issue in the sample has a non-null id", issues.all { it.id != null })

        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29.
        // REPLACES the block that read `ISS-S-06` (storage, WARNING,
        // evidence "2597", evidenceUnit "IOPS"): that issue is GONE from the
        // sample (the storage category left the WARNING floor — its letter
        // is B under a coverage ceiling now), and no issue in THIS sample
        // carries a non-null evidenceUnit any more. What replaces it:
        //   - the sample's one FAIL issue, ISS-SEC-04, read field by field
        //     (its titles also changed in this sync — "فشل في الاختبار
        //     SEC-04" became "فشل — الأمان" — so a stale pin would fail
        //     here too);
        //   - the evidenceUnit non-null proof moves to the one REAL issue
        //     that still has it: ISS-C-06 ("%"), in the reports-document
        //     sample cpu-c06-downgrade, read through the same
        //     cache-gate -> flatten -> Gson path fetchExistingReport uses.
        val secFail = issues.first { it.id == "ISS-SEC-04" }
        assertEquals("security", secFail.category)
        assertEquals("FAIL", secFail.severity)
        assertEquals("فشل — الأمان", secFail.title)
        assertEquals("مكتشف", secFail.evidence)
        assertNull("ISS-SEC-04 carries no unit", secFail.evidenceUnit)

        val cpuIssue = loadCpuC06Issues().first { it.id == "ISS-C-06" }
        assertEquals("cpu", cpuIssue.category)
        assertEquals("FAIL", cpuIssue.severity)
        assertEquals("9.1", cpuIssue.evidence)
        assertEquals("%", cpuIssue.evidenceUnit)

        // Confirms evidenceUnit is correctly null (not "%" leaking
        // across entries) when the issue's own evidence is also null.
        assertNull(issues.first { it.id == "DISC-T-03" }.evidenceUnit)
    }

    /**
     * The issues of the real reports-document sample `…cpu-c06-downgrade`,
     * read the way `fetchExistingReport` reads a Firestore document:
     * `isCachedReportComplete` gate, `flattenReportDocument`, then the
     * production Gson round trip. Only used for the one real non-null
     * `evidenceUnit` (see the test above).
     */
    private fun loadCpuC06Issues(): List<AiIssue> {
        val stream = javaClass.classLoader!!.getResourceAsStream(
            "contract/v7-reports-document-sample-cpu-c06-downgrade.json"
        ) ?: error("contract/v7-reports-document-sample-cpu-c06-downgrade.json not found on the test classpath")
        val raw: Map<String, Any?> = InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type)
        }
        assertTrue(net.neuralcheck.data.remote.isCachedReportComplete(raw))
        val flat = net.neuralcheck.data.remote.flattenReportDocument(raw)
        return gson.fromJson(gson.toJson(flat), AiAnalysisResponse::class.java).issues!!
    }

    @Test
    fun `severity carries the fifth value, POSITIVE, this app's original enum comment didn't name`() {
        val issues = loadSample().issues!!
        val positive = issues.filter { it.severity == "POSITIVE" }
        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29:
        // was 4 (POS-cpu, POS-display, POS-memory, POS-wireless). POS-storage
        // is new — storage now reads MEASURED with no WARNING, so it earns a
        // "no defects" positive instead of the old ISS-S-06 warning.
        assertEquals(
            listOf("POS-cpu", "POS-display", "POS-memory", "POS-storage", "POS-wireless"),
            positive.map { it.id }
        )
    }

    @Test
    fun `narrative arrives with summary and all three real buyerAdvice leaves, details and verdict absent - the d5b6c29 sample`() {
        val narrative = loadSample().narrative!!
        assertTrue(narrative.summary!!.isNotBlank())
        // Rule 23 — resynced 2026-10-07 from neuralcheck-backend@d5b6c29.
        // REPLACES `assertTrue(narrative.details!!.isNotBlank())` and the
        // same for `verdict`: the d5b6c29 sample's narrative object has
        // exactly two keys, `summary` and `buyerAdvice` (the c7446a4 one
        // also had `details` and `verdict`). They parse to null — the real
        // "absent from the payload" shape, and the one the app already
        // renders without (ReportScreen.kt:2393-2410: details/verdict are
        // removed from the V7 render path). Asserted null, not skipped: a
        // backend that starts sending them again would be a contract change
        // to read, not a silent pass.
        assertNull(narrative.details)
        assertNull(narrative.verdict)

        // Corrected-shape confirmation for the ONE assumption that was
        // already right: buyerAdvice IS the triple object (not a single
        // string), reusing the same BuyerAdvice class V6 already had.
        val advice = narrative.buyerAdvice
        assertNotNull(
            "buyerAdvice must parse as a present object, not null itself",
            advice
        )
        // REPLACES the prior assertNull(casual/gamer/photographer) — rule
        // 23, not added alongside. The 43dee66 sample's README-documented
        // "نقص عيّنة" (source capture predated decision 24, a single-string
        // era) is CLOSED by the 154f3f2 regeneration: all three leaves now
        // arrive with real, distinct, non-blank text. See contract/README.md.
        assertTrue("buyerAdvice.casual must be real non-blank text on this sample", advice?.casual?.isNotBlank() == true)
        assertTrue("buyerAdvice.gamer must be real non-blank text on this sample", advice?.gamer?.isNotBlank() == true)
        assertTrue("buyerAdvice.photographer must be real non-blank text on this sample", advice?.photographer?.isNotBlank() == true)
        assertTrue(
            "the three personas must carry genuinely different advice, not one string copied three times",
            setOf(advice?.casual, advice?.gamer, advice?.photographer).size == 3
        )
    }

    @Test
    fun `the domain AiAnalysisResult mirrors the same golden sample losslessly through AiScoringEngine-style pass-through`() {
        // Confirms the pass-through wiring in AiScoringEngine.kt (response.overall
        // -> AiAnalysisResult.overall, etc.) doesn't need any transformation
        // to carry the real payload — same object references, no re-parsing.
        val response = loadSample()
        val result = net.neuralcheck.domain.model.AiAnalysisResult(
            pipeline = response.pipeline,
            language = response.language,
            overall = response.overall,
            categories = response.categories,
            narrative = response.narrative,
            narrativeIncomplete = response.narrativeIncomplete,
            narrativeIncompleteFields = response.narrativeIncompleteFields
        )
        assertEquals(true, result.isComplete())
        assertEquals("C", result.overall?.letter)
        assertEquals(15, result.categories?.size)
    }
}
