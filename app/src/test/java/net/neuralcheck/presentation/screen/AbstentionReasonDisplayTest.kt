package net.neuralcheck.presentation.screen

import com.google.gson.GsonBuilder
import net.neuralcheck.R
import net.neuralcheck.data.remote.model.AiAbstentionReasonV7
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.util.AiV7Presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Order 2026-10-01 (أمر «abstentionReasons»), corrected 2026-10-02 and
 * moved onto the real samples 2026-10-07: what each
 * `overall.abstentionReasons[]` entry shows — one test per row of the
 * user's approved table, exercised through [abstentionReasonDisplayOrNull],
 * the exact `internal` function `AiV7OverallCard` calls (not a copy of its
 * `when`), fed by entries PARSED through `GsonBuilder().create()` (the same
 * parse the app does), not hand-constructed objects.
 *
 * **Input sources, by reachability.** The four codes the backend can really
 * produce are read from the REAL samples synced 2026-10-07 from
 * neuralcheck-backend@d5b6c29 (`contract/`):
 *   - `v7-client-payload-sample-zero-measured.json` — battery
 *     `NO_CALIBRATED_THRESHOLD (…)`, twelve `DECLARED_BUT_NONE_EXECUTED`,
 *     audio + input `NO_HEALTH_BEARING_TESTS_DECLARED`;
 *   - `v7-client-payload-sample-zero-measured-battery-harness-error.json` —
 *     battery `HARNESS_ERROR`, the rest as above.
 * The parse paths themselves (analyzeDevice / Firestore document / Room)
 * are exercised on the same samples in `AbstentionReasonsParsingTest`.
 *
 * Three inputs stay SYNTHETIC and are labeled **«لا يُنال من مُدخل حقيقي»**
 * (cannot be obtained from a real input — neuralcheck-backend@8e49fc6
 * `codesNotReachable`, relayed, UNVERIFIED from this repo, القاعدة ٢٩): the
 * codes `MANUAL_ATTESTATION_ONLY` and `NO_BATTERY_INDICATORS_MEASURED`, and
 * an unknown code. Per the user's decision (2026-10-07): they remain two
 * hand-built test inputs plus the unknown one — no technician attestation
 * is fabricated into a sample and no backend code is changed for them.
 *
 * Rule 23 (2026-10-07): REPLACES the earlier version of this file, which
 * parsed ONE hand-built JSON of eight entries under a "NOT A GOLDEN SAMPLE"
 * banner, mixing the reachable and the unreachable codes in one fixture
 * (and, with them, a `reason` shape — three `battery` rows in one list —
 * no real scan produces). Same assertions per reachable code, now fed by
 * the real entries; the unreachable ones moved to their own labeled block.
 * Rule 23 (2026-10-02, kept): REPLACED the 2026-10-01 tests that pinned a
 * FALSE behaviour — the calibration code showing "لم تُقَس صحّة البطارية"
 * (that battery WAS measured; the server's condition for this list is "no
 * category produced a LETTER", not "none measured"), and
 * `NO_BATTERY_INDICATORS_MEASURED` being treated as an unrecognised code
 * (it is a table row: battery-unmeasured).
 *
 * What this does NOT prove: that the Composable renders the title bold and
 * the text beneath it (no test renders it; Robolectric and compose-test are
 * test dependencies since 3284cca) — only
 * the decision it renders from. The `textRes` ids are resolved to real
 * texts by reading the real `values{,-tr}/strings.xml` from disk (same
 * technique as PreScanStringKeysTest), not by retyping them.
 */
class AbstentionReasonDisplayTest {

    private val gson = GsonBuilder().create()

    // ── real inputs ─────────────────────────────────────────────────────

    private fun realEntries(sampleFile: String): List<AiAbstentionReasonV7> {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$sampleFile")
            ?: error("contract/$sampleFile not found on the test classpath — expected at app/src/test/resources/contract/")
        val response = InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, AiAnalysisResponse::class.java)
        }
        return response.overall?.abstentionReasons
            ?: error("$sampleFile carries no overall.abstentionReasons — the sample changed")
    }

    private val zeroMeasured: List<AiAbstentionReasonV7> by lazy {
        realEntries("v7-client-payload-sample-zero-measured.json")
    }
    private val batteryHarnessError: List<AiAbstentionReasonV7> by lazy {
        realEntries("v7-client-payload-sample-zero-measured-battery-harness-error.json")
    }

    private fun List<AiAbstentionReasonV7>.of(category: String) = single { it.category == category }

    private val arStrings by lazy { stringsXml("src/main/res/values/strings.xml") }
    private val trStrings by lazy { stringsXml("src/main/res/values-tr/strings.xml") }

    // ── the real samples: every entry, then each reachable code ─────────

    @Test
    fun `real zero-measured sample - all 15 entries parse and each shows the approved text for its code`() {
        assertEquals(15, zeroMeasured.size)
        assertEquals("one entry per category", 15, zeroMeasured.map { it.category }.toSet().size)
        for (entry in zeroMeasured) {
            val expected = when {
                entry.reason!!.startsWith("NO_CALIBRATED_THRESHOLD") -> R.string.report_v7_reason_battery_uncalibrated
                entry.reason == "DECLARED_BUT_NONE_EXECUTED" -> R.string.report_v7_reason_declared_none_executed
                entry.reason == "NO_HEALTH_BEARING_TESTS_DECLARED" -> R.string.report_v7_reason_no_health_bearing_declared
                else -> error("a code this sample did not carry at d5b6c29: ${entry.reason} (${entry.category})")
            }
            val d = abstentionReasonDisplayOrNull(entry, "ar")!!
            assertEquals("${entry.category}: ${entry.reason}", expected, d.textRes)
            assertFalse("${entry.category}: a real code must be recognised", d.reasonWasUnrecognised)
            assertTrue("${entry.category}: title must not be blank", d.categoryName.isNotBlank())
        }
        // The distribution itself, so a backend change of which category
        // carries which code is visible here and not silently re-mapped.
        assertEquals(
            mapOf(
                "NO_CALIBRATED_THRESHOLD (PLAN-03 لم يُنجَز — انظر batteryLayer.js)" to 1,
                "DECLARED_BUT_NONE_EXECUTED" to 12,
                "NO_HEALTH_BEARING_TESTS_DECLARED" to 2
            ),
            zeroMeasured.groupingBy { it.reason }.eachCount()
        )
    }

    @Test
    fun `real battery-harness-error sample - battery shows the harness-error-unmeasured text, the other 14 are unchanged`() {
        assertEquals(15, batteryHarnessError.size)
        val battery = abstentionReasonDisplayOrNull(batteryHarnessError.of("battery"), "ar")!!
        assertEquals("HARNESS_ERROR", batteryHarnessError.of("battery").reason)
        assertEquals(R.string.report_v7_reason_harness_error_unmeasured, battery.textRes)
        assertFalse(battery.reasonWasUnrecognised)
        // The other 14 entries are the zero-measured sample's, byte for byte
        // in category+reason (that is what makes this sample "the same
        // scan, but its battery hit a harness error").
        assertEquals(
            zeroMeasured.filter { it.category != "battery" },
            batteryHarnessError.filter { it.category != "battery" }
        )
    }

    @Test
    fun `NO_HEALTH_BEARING_TESTS_DECLARED (real - audio and input) shows its approved text`() {
        for (category in listOf("audio", "input")) {
            val entry = zeroMeasured.of(category)
            assertEquals("NO_HEALTH_BEARING_TESTS_DECLARED", entry.reason)
            assertEquals(
                category,
                R.string.report_v7_reason_no_health_bearing_declared,
                abstentionReasonDisplayOrNull(entry, "ar")!!.textRes
            )
        }
        assertEquals(
            "لم تُقَس: لم يصل في هذا الفحص اختبار يُحتسب لحالة هذه الفئة.",
            arStrings["report_v7_reason_no_health_bearing_declared"]
        )
        assertEquals(
            "Ölçülmedi: Bu taramada bu kategorinin durumu için sayılan hiçbir test yok.",
            trStrings["report_v7_reason_no_health_bearing_declared"]
        )
    }

    @Test
    fun `DECLARED_BUT_NONE_EXECUTED (real - thermal) shows number 15`() {
        val entry = zeroMeasured.of("thermal")
        assertEquals("DECLARED_BUT_NONE_EXECUTED", entry.reason)
        assertEquals(
            R.string.report_v7_reason_declared_none_executed,
            abstentionReasonDisplayOrNull(entry, "ar")!!.textRes
        )
    }

    @Test
    fun `number 15's real text, both languages, is the user's approved wording`() {
        // The user's verbatim text for DECLARED_BUT_NONE_EXECUTED (and for
        // "any other code" in this list — see the synthetic block below).
        assertEquals("لم تُقَس هذه الفئة في هذا الفحص.", arStrings["report_v7_reason_declared_none_executed"])
        assertEquals("Bu kategori bu taramada ölçülmedi.", trStrings["report_v7_reason_declared_none_executed"])
    }

    @Test
    fun `the battery calibration code (real - zero-measured battery) shows battery-uncalibrated (number 16) - the battery was measured`() {
        val entry = zeroMeasured.of("battery")
        assertEquals(
            "NO_CALIBRATED_THRESHOLD (PLAN-03 لم يُنجَز — انظر batteryLayer.js)",
            entry.reason
        )
        val d = abstentionReasonDisplayOrNull(entry, "ar")!!
        assertEquals(R.string.report_v7_reason_battery_uncalibrated, d.textRes)
        assertNotEquals(
            "'لم تُقَس صحّة البطارية' would be false here — the battery was measured",
            R.string.report_v7_reason_battery_unmeasured, d.textRes
        )
        assertFalse(d.reasonWasUnrecognised)
        // The user's verbatim #16 text, both languages, from the real files.
        assertEquals("تُقاس البطارية ولا يُعطى لها حرف في هذا الإصدار.", arStrings["report_v7_reason_battery_uncalibrated"])
        assertEquals(
            "Pil ölçülür, ancak bu sürümde harf notu verilmez.",
            trStrings["report_v7_reason_battery_uncalibrated"]
        )
    }

    @Test
    fun `HARNESS_ERROR (real - harness-error sample battery) text is the approved wording, both languages`() {
        assertEquals(
            "لم تُقَس: لم تصل بيانات هذه الفئة من أداة الفحص. ليس حكماً على الجهاز.",
            arStrings["report_v7_reason_harness_error_unmeasured"]
        )
        assertEquals(
            "Ölçülmedi: Bu kategorinin verileri tarama aracından gelmedi. Cihaz hakkında bir yargı değildir.",
            trStrings["report_v7_reason_harness_error_unmeasured"]
        )
    }

    // ── the title: category name from CategoryTranslations alone ────────

    @Test
    fun `the title is the category name in the requested language (real entries)`() {
        fun title(category: String, lang: String) =
            abstentionReasonDisplayOrNull(zeroMeasured.of(category), lang)!!.categoryName
        assertEquals("الكاميرا", title("camera", "ar"))
        assertEquals("Kamera", title("camera", "tr"))
        assertEquals("البطارية", title("battery", "ar"))
        assertEquals("Pil", title("battery", "tr"))
        assertEquals("الموقع (GPS)", title("gps", "ar"))
        assertEquals("Konum (GPS)", title("gps", "tr"))
        assertEquals("اللمس والأزرار", title("input", "ar"))
        assertEquals("Dokunma ve Tuşlar", title("input", "tr"))
    }

    // ═════════════════════════════════════════════════════════════════════
    // لا يُنال من مُدخل حقيقي — cannot be obtained from a real input
    // (neuralcheck-backend@8e49fc6 `codesNotReachable`, relayed, unverified
    // from this repo). Synthetic by the user's decision 2026-10-07: not
    // golden-sample coverage, not an attempt to fabricate a real sample.
    // ═════════════════════════════════════════════════════════════════════

    /** SYNTHETIC — لا يُنال من مُدخل حقيقي. Server-shaped, parsed through
     *  the production `GsonBuilder().create()` like everything above. The
     *  last two are an unknown code and a bare category `state` string
     *  (the backend's `|| entry.state` fallback). */
    private val syntheticJson = """
        {
          "overall": {
            "abstentionReasons": [
              {"category": "camera",  "reason": "MANUAL_ATTESTATION_ONLY"},
              {"category": "battery", "reason": "NO_BATTERY_INDICATORS_MEASURED"},
              {"category": "gps",     "reason": "SOME_FUTURE_CODE"},
              {"category": "input",   "reason": "UNMEASURED"}
            ]
          }
        }
    """.trimIndent()

    private val synthetic: List<AiAbstentionReasonV7> by lazy {
        gson.fromJson(syntheticJson, AiAnalysisResponse::class.java).overall!!.abstentionReasons!!
    }

    @Test
    fun `NOT FROM A REAL INPUT (لا يُنال من مُدخل حقيقي - backend 8e49fc6 codesNotReachable) - MANUAL_ATTESTATION_ONLY shows the approved attested text`() {
        val d = abstentionReasonDisplayOrNull(synthetic[0], "ar")!!
        assertEquals(R.string.report_v7_state_attested_only, d.textRes)
        assertFalse(d.reasonWasUnrecognised)
    }

    @Test
    fun `NOT FROM A REAL INPUT (لا يُنال من مُدخل حقيقي - backend 8e49fc6 codesNotReachable) - NO_BATTERY_INDICATORS_MEASURED shows battery-unmeasured`() {
        val d = abstentionReasonDisplayOrNull(synthetic[1], "ar")!!
        assertEquals(R.string.report_v7_reason_battery_unmeasured, d.textRes)
        assertFalse("it is a table row, not an unrecognised code", d.reasonWasUnrecognised)
    }

    @Test
    fun `NOT FROM A REAL INPUT (لا يُنال من مُدخل حقيقي - backend 8e49fc6 codesNotReachable) - an unknown code and a bare state string both show number 15, flagged unrecognised`() {
        // "any other code → #15", never the raw code, never report_v7_state_unknown.
        for (index in listOf(2, 3)) {
            val d = abstentionReasonDisplayOrNull(synthetic[index], "ar")!!
            assertEquals("entry $index", R.string.report_v7_reason_declared_none_executed, d.textRes)
            assertNotEquals(
                "entry $index must NOT take the per-category row's unrecognised text",
                R.string.report_v7_state_unknown, d.textRes
            )
            assertTrue("entry $index must be flagged so the caller logs the raw code", d.reasonWasUnrecognised)
        }
    }

    @Test
    fun `every text shown exists and is non-blank in both languages, and is never the raw reason code - real and synthetic entries`() {
        val all = zeroMeasured + batteryHarnessError + synthetic
        for ((index, entry) in all.withIndex()) {
            val d = abstentionReasonDisplayOrNull(entry, "ar")!!
            for ((label, strings) in listOf("values" to arStrings, "values-tr" to trStrings)) {
                val name = resourceName(d.textRes)
                val text = strings[name]
                assertNotNull("$name missing from $label", text)
                assertTrue("$name blank in $label", text!!.isNotBlank())
                assertNotEquals("entry $index (${entry.category}) shows the raw reason code", entry.reason, text)
            }
        }
    }

    // ── absent fields: ARTIFICIAL, hand-built objects (not parsed JSON) ─

    @Test
    fun `ARTIFICIAL hand-built - an entry with a blank or missing category is skipped, not shown with an empty title`() {
        assertNull(abstentionReasonDisplayOrNull(AiAbstentionReasonV7(category = null, reason = "HARNESS_ERROR"), "ar"))
        assertNull(abstentionReasonDisplayOrNull(AiAbstentionReasonV7(category = "  ", reason = "HARNESS_ERROR"), "ar"))
    }

    @Test
    fun `ARTIFICIAL hand-built - an entry whose reason is itself missing shows number 15 and is not flagged as an unrecognised code`() {
        val d = abstentionReasonDisplayOrNull(AiAbstentionReasonV7(category = "gps", reason = null), "ar")!!
        assertEquals(R.string.report_v7_reason_declared_none_executed, d.textRes)
        assertFalse("nothing was sent, so there is no raw code to log", d.reasonWasUnrecognised)
        assertEquals(
            AiV7Presentation.AbstentionReasonKind.UNKNOWN,
            AiV7Presentation.classifyAbstentionReason(null)
        )
    }

    // ── helpers: read the REAL resources from disk ──────────────────────

    private fun resFile(relative: String): File {
        val fromModule = File(relative)
        if (fromModule.exists()) return fromModule
        val fromRoot = File("app", relative)
        check(fromRoot.exists()) { "strings file not found from '${File(".").absolutePath}': $relative" }
        return fromRoot
    }

    private fun stringsXml(relative: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resFile(relative))
        val nodes = doc.getElementsByTagName("string")
        val out = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
            out[name] = node.textContent
        }
        return out
    }

    /** R.string.<name> id → <name>, by reflection over the real R class. */
    private fun resourceName(id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name
}
