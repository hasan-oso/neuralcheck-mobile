package net.neuralcheck.presentation.screen

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.flattenReportDocument
import net.neuralcheck.data.remote.isCachedReportComplete
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiGoverningGateReasonV7
import net.neuralcheck.domain.model.AiAnalysisResult
import net.neuralcheck.util.AiV7Presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Order 2026-10-07 (A1 + decision S1 + the same-day "item 4" decision): the
 * overall gate card shows the server's `governingGateReason.text` sentence
 * and NOTHING else — no `name`, no `source`, no letter badge — and is
 * hidden ENTIRELY (no card, no title on its own, no Spacer) when `text` is
 * absent or the gate itself is null.
 *
 * Fixtures: the REAL golden samples synced 2026-10-07 from
 * neuralcheck-backend@d5b6c29 (`v7-client-payload-sample.json` for the
 * analyzeDevice shape, `v7-reports-document-sample.json` for the Firestore
 * document shape), which carry `governingGateReason.text` (a92d503).
 * "text present" is read from them as they are. "text absent" is NOT a
 * second file: it is built IN MEMORY from the real sample by deleting the
 * one key — **the shape of pre-a92d503 reports** (name/ceilingLetter/source
 * only), which still sit in users' Firestore history and Room — and it is
 * labeled so wherever it is used. No resource file was added for it.
 *
 * Parts: (1) `text` parses on each path the screen reads through, (2) the
 * display decision — exercised through [gateReasonCardDecision], the exact
 * `internal` function the screen's `when` branches on, fed by objects
 * PARSED through Gson, (3) the approved title text, and the strings this
 * change retired.
 *
 * Rule 23 (2026-10-07, golden sync): REPLACES the earlier version of this
 * file, which marked itself "NOT A GOLDEN SAMPLE" — its "text present"
 * object was a relayed, unverified literal (القاعدة ٢٩) and its path-2 test
 * injected `gate["text"]` into the real document because the repo's copy
 * had no `text`. The copy has it now, so nothing is injected and nothing
 * is relayed: the sentence below is asserted against the sample's own
 * value. Earlier in the same round (rule 23): it replaced the tests written
 * for a stand-in/title-only state — superseded by the decision that the
 * card hides entirely. No committed test ever pinned the title alone, the
 * name, the source or the letter badge.
 *
 * What this does NOT prove: that the Composable omits the card and its
 * Spacer when the decision is Hide (no test renders it; Robolectric and compose-test
 * are test dependencies since 3284cca) — only the decision the call site's `when` branches on.
 */
class GoverningGateReasonTextTest {

    private val gson = GsonBuilder().create()

    /** The sentence the real d5b6c29 sample carries — asserted equal to the
     *  value PARSED from the sample in [the real sample carries the sentence]
     *  so a backend rewording fails there, once, with a clear message. */
    private val sentence = "أعلى حرف C: صلاحيات الجذر (الروت) مكتشَفة على الجهاز."

    // ── real-sample helpers ─────────────────────────────────────────────

    private fun readResource(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$name")
            ?: error("contract/$name not found on the test classpath — expected at app/src/test/resources/contract/")
        return InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
    }

    /** The analyzeDevice shape, as the Firebase Functions SDK hands it back:
     *  plain nested maps. A fresh, mutable copy on every call. */
    private fun realClientPayloadMap(): MutableMap<String, Any?> =
        gson.fromJson(
            readResource("v7-client-payload-sample.json"),
            object : TypeToken<MutableMap<String, Any?>>() {}.type
        )

    /** The Firestore document shape. A fresh, mutable copy on every call. */
    private fun realReportsDocumentMap(): MutableMap<String, Any?> =
        gson.fromJson(
            readResource("v7-reports-document-sample.json"),
            object : TypeToken<MutableMap<String, Any?>>() {}.type
        )

    @Suppress("UNCHECKED_CAST")
    private fun gateOf(payload: Map<String, Any?>): MutableMap<String, Any?> =
        (payload["overall"] as Map<String, Any?>)["governingGateReason"] as MutableMap<String, Any?>

    /** The real client sample's gate parsed through the production parse
     *  (`GsonBuilder().create()`), after [mutate] edits ITS COPY of the gate
     *  map in memory (the file is never touched). */
    private fun parsedRealGate(
        serializer: Gson = gson,
        mutate: (MutableMap<String, Any?>) -> Unit = {}
    ): AiGoverningGateReasonV7? {
        val payload = realClientPayloadMap()
        mutate(gateOf(payload))
        return gson.fromJson(serializer.toJson(payload), AiAnalysisResponse::class.java).overall?.governingGateReason
    }

    /** What reports written before the server sent `text` look like: the
     *  real gate, minus that one key — built in memory, no resource file. */
    private fun parsedPreA92d503Gate(): AiGoverningGateReasonV7? =
        parsedRealGate { it.remove("text") }

    // ── (1) `text` reaches the app on each path ─────────────────────────
    //  1. analyzeDevice response: `gson.toJson(result.data)` → `fromJson`
    //     (FirebaseAnalysisService.analyzeDevice, uncaught).
    //  2. Firestore reports/{scanId}: `flattenReportDocument` → `toJson` →
    //     the same `fromJson` (fetchExistingReport, best-effort catch).
    //  3. Room `aiAnalysisJson`: `Gson().fromJson(.., AiAnalysisResult)`
    //     (ViewModels.kt:894-895) — `overall` is the SAME AiOverallV7, so the
    //     field lives INSIDE the serialized JSON column: no Room schema
    //     change, no migration (DB stays at version 7).

    @Test
    fun `the real sample carries the sentence, and name and source still parse (they stay in the data)`() {
        val gate = parsedRealGate()!!
        assertEquals("ROOT_DETECTED", gate.name)
        assertEquals("C", gate.ceilingLetter)
        assertEquals("SEC-04=fail", gate.source)
        assertEquals(
            "the d5b6c29 sample's governingGateReason.text — a rewording on the backend fails here",
            sentence, gate.text
        )
    }

    @Test
    fun `path 1 - analyzeDevice callable result (the real client sample as nested maps) carries text through the production two-step`() {
        val resultData: Map<String, Any?> = realClientPayloadMap()
        val parsed = gson.fromJson(gson.toJson(resultData), AiAnalysisResponse::class.java)
        assertEquals(sentence, parsed.overall?.governingGateReason?.text)
    }

    @Test
    fun `path 2 - the real Firestore report document, through the cache gate and flattenReportDocument`() {
        val raw = realReportsDocumentMap()
        assertTrue("the cache gate must accept the real document", isCachedReportComplete(raw))
        val parsed = gson.fromJson(gson.toJson(flattenReportDocument(raw)), AiAnalysisResponse::class.java)
        assertEquals(sentence, parsed.overall?.governingGateReason?.text)
        assertEquals("ROOT_DETECTED", parsed.overall?.governingGateReason?.name)
    }

    @Test
    fun `path 3 - Room aiAnalysisJson round trip of the real sample through AiAnalysisResult, plain Gson`() {
        val response = gson.fromJson(readResource("v7-client-payload-sample.json"), AiAnalysisResponse::class.java)
        // What AiScoringEngine hands to Room: the response's overall carried
        // by reference into AiAnalysisResult (same construction as
        // V7GoldenSampleTest's pass-through test).
        val result = AiAnalysisResult(
            pipeline = response.pipeline,
            language = response.language,
            overall = response.overall,
            categories = response.categories,
            narrative = response.narrative,
            narrativeIncomplete = response.narrativeIncomplete,
            narrativeIncompleteFields = response.narrativeIncompleteFields
        )
        val plain = Gson()  // ViewModels.kt:894-895 — plain Gson, not GsonBuilder
        val stored = plain.toJson(result)        // the save leg
        val read = plain.fromJson(stored, AiAnalysisResult::class.java)  // the load leg
        assertEquals(sentence, read.overall?.governingGateReason?.text)
        assertEquals("SEC-04=fail", read.overall?.governingGateReason?.source)
        // and a second pass re-reads identically
        val reread = plain.fromJson(plain.toJson(read), AiAnalysisResult::class.java)
        assertEquals(sentence, reread.overall?.governingGateReason?.text)
    }

    // ── (2) the display decision ────────────────────────────────────────

    @Test
    fun `text present (the real sample) - the decision is Show with the sentence, and nothing but the sentence`() {
        val decision = gateReasonCardDecision(parsedRealGate())
        assertTrue("expected Show, got $decision", decision is GateReasonCardDecision.Show)
        val shown = (decision as GateReasonCardDecision.Show).sentence
        // verbatim, nothing prefixed or appended (no letter badge, no name)
        assertEquals(sentence, shown)
        assertFalse("name must not be displayed", shown.contains("ROOT_DETECTED"))
        assertFalse("source must not be displayed", shown.contains("SEC-04"))
        assertFalse("the retired letter-badge wording must not come back", shown.contains("سقف الحرف"))
    }

    @Test
    fun `text absent (the shape of pre-a92d503 reports - name ceilingLetter source only, built in memory from the real sample) - the decision is not to display`() {
        val gate = parsedPreA92d503Gate()
        assertNotNull("an object arrived", gate)
        assertEquals("the rest of the real gate is intact", "ROOT_DETECTED", gate!!.name)
        assertEquals("C", gate.ceilingLetter)
        assertEquals("SEC-04=fail", gate.source)
        assertNull(gate.text)
        assertSame(GateReasonCardDecision.HideTextAbsent, gateReasonCardDecision(gate))
    }

    @Test
    fun `blank or null text, and UNMATCHED without text, are all hidden the same way (each built in memory from the real sample)`() {
        val keepNulls = GsonBuilder().serializeNulls().create()  // so `"text": null` really is written
        val variants: List<Pair<String, AiGoverningGateReasonV7?>> = listOf(
            "text empty" to parsedRealGate { it["text"] = "" },
            "text whitespace" to parsedRealGate { it["text"] = "   " },
            "text explicit null" to parsedRealGate(keepNulls) { it["text"] = null },
            "UNMATCHED, no text (pre-a92d503 shape)" to parsedRealGate {
                it["name"] = "UNMATCHED"
                it["source"] = "X=y"
                it.remove("text")
            }
        )
        for ((label, gate) in variants) {
            assertNotNull("$label: an object arrived", gate)
            assertSame(label, GateReasonCardDecision.HideTextAbsent, gateReasonCardDecision(gate))
        }
    }

    @Test
    fun `a null governingGateReason (the Honor shape) is not displayed either, and is not the absent-text case`() {
        val parsed = gson.fromJson(
            """{"overall":{"governingGateReason":null}}""", AiAnalysisResponse::class.java
        ).overall?.governingGateReason
        assertNull(parsed)
        assertSame(GateReasonCardDecision.HideNoGate, gateReasonCardDecision(parsed))
        assertSame(GateReasonCardDecision.HideNoGate, gateReasonCardDecision(null))
        // the two Hide states differ ONLY so the call site logs the second
        // (rule 22) and not the first
        assertNotSame(GateReasonCardDecision.HideNoGate, GateReasonCardDecision.HideTextAbsent)
    }

    @Test
    fun `governingGateSentenceOrNull takes only the text and treats null or blank as absent`() {
        assertEquals(sentence, AiV7Presentation.governingGateSentenceOrNull(sentence))
        assertNull(AiV7Presentation.governingGateSentenceOrNull(null))
        assertNull(AiV7Presentation.governingGateSentenceOrNull(""))
        assertNull(AiV7Presentation.governingGateSentenceOrNull("  "))
    }

    // ── (3) the approved title, and the strings this change retired ─────
    // NOT a "lock suite": the app has no general lock test for approved
    // texts (PreScanStringKeysTest pins key presence / format specifiers /
    // non-blank for a listed set, not exact wording). These are plain
    // feature-test assertions, one per language so a change to either fails
    // on its own.

    @Test
    fun `the card title is the approved Arabic text, without the emoji`() {
        assertEquals(
            "سبب الحرف العامّ",
            stringsXml("src/main/res/values/strings.xml")["report_v7_gate_reason_title"]
        )
    }

    @Test
    fun `the card title is the approved Turkish text, without the emoji`() {
        assertEquals(
            "Genel harf notunun nedeni",
            stringsXml("src/main/res/values-tr/strings.xml")["report_v7_gate_reason_title"]
        )
    }

    @Test
    fun `the title carries no emoji of its own - the card's separate icon is the only one`() {
        for (file in listOf("src/main/res/values/strings.xml", "src/main/res/values-tr/strings.xml")) {
            assertFalse("$file: title still carries the emoji", stringsXml(file)["report_v7_gate_reason_title"]!!.contains("🚧"))
        }
    }

    @Test
    fun `the letter-badge format and the name-slot fallback are gone from both languages`() {
        for (file in listOf("src/main/res/values/strings.xml", "src/main/res/values-tr/strings.xml")) {
            val strings = stringsXml(file)
            assertFalse("$file: report_v7_gate_reason_fmt came back", strings.containsKey("report_v7_gate_reason_fmt"))
            assertFalse(
                "$file: report_v7_gate_reason_name_unavailable came back",
                strings.containsKey("report_v7_gate_reason_name_unavailable")
            )
        }
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
}
