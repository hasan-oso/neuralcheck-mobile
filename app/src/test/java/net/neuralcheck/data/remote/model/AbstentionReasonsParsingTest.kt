package net.neuralcheck.data.remote.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.AnalysisRequestSerializer
import net.neuralcheck.data.remote.flattenReportDocument
import net.neuralcheck.data.remote.isCachedReportComplete
import net.neuralcheck.domain.model.AiAnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Order 2026-10-01 (أمر «abstentionReasons»), moved onto the real samples
 * 2026-10-07. The DEFECT this file guards — `overall.abstentionReasons`
 * typed `List<String>` while the server sends `{category, reason}` objects,
 * so `Gson.fromJson` threw and failed the ENTIRE `AiAnalysisResponse` parse
 * — was proven in the order's step 1, verbatim:
 * ```
 * com.google.gson.JsonSyntaxException: java.lang.IllegalStateException:
 * Expected a string but was BEGIN_OBJECT at line 5 column 8
 * path $.overall.abstentionReasons[0]
 * ```
 * (historical record; the type is `List<AiAbstentionReasonV7>` now). Every
 * test below fails with a thrown exception if it regresses.
 *
 * Inputs are the REAL samples synced 2026-10-07 from
 * neuralcheck-backend@d5b6c29, one per read path (القاعدة 41 — each reader
 * is tested with the shape it actually receives):
 *   1. analyzeDevice response → `v7-client-payload-sample-zero-measured*.json`
 *      (FirebaseAnalysisService.analyzeDevice: `gson.toJson(result.data)`
 *      then `gson.fromJson(json, AiAnalysisResponse)`, NOT wrapped in
 *      try/catch — a throw propagates out and fails the whole live report);
 *   2. Firestore `reports/{scanId}` → `v7-reports-document-sample-zero-measured*.json`
 *      (fetchExistingReport: `isCachedReportComplete` gate, then
 *      `flattenReportDocument` → `gson.toJson` → the same `fromJson`,
 *      wrapped in a catch-all that returns null — so the old defect there
 *      silently cost a live re-fetch instead of crashing);
 *   3. Room `aiAnalysisJson` → a round trip of the parsed real response
 *      through `AiAnalysisResult` with a PLAIN `Gson()` (ReportViewModel
 *      .loadScan, ViewModels.kt:894-895, inside `runCatching`, so the old
 *      defect silently dropped the cached result there too). `overall` is
 *      the SAME AiOverallV7, so the field lives inside the serialized JSON
 *      column: no schema change, no migration (DB stays at version 7).
 * The expected entries are read from the sample file by an INDEPENDENT
 * parse (`JsonParser`, not the DTO under test — rule 38) and the literal
 * count is also asserted, so a sample change is visible, not re-mapped.
 *
 * Rule 23 (2026-10-07): REPLACES the earlier version of this file, which
 * said "NOT a golden sample" and built its input three ways by hand — an
 * inline JSON of three entries, a nested `mapOf(...)`, and the real sample
 * B document with `abstentionReasons` INJECTED under `aiResponse.overall`
 * (a shape no server writes: sample B has `abstentionReasons: null`). The
 * same three paths are asserted, now on documents the backend actually
 * produced. The synthetic codes the real samples cannot carry
 * (`MANUAL_ATTESTATION_ONLY`, `NO_BATTERY_INDICATORS_MEASURED`, an unknown
 * code) are labeled «لا يُنال من مُدخل حقيقي» in AbstentionReasonDisplayTest.
 */
class AbstentionReasonsParsingTest {

    /** The production construction — `AnalysisRequestSerializer.gson` (what FirebaseAnalysisService uses). */
    private val gson = AnalysisRequestSerializer.gson

    private val zeroMeasuredClient = "v7-client-payload-sample-zero-measured.json"
    private val harnessErrorClient = "v7-client-payload-sample-zero-measured-battery-harness-error.json"
    private val zeroMeasuredDocument = "v7-reports-document-sample-zero-measured.json"
    private val harnessErrorDocument = "v7-reports-document-sample-zero-measured-battery-harness-error.json"

    private val batteryCalibrationCode = "NO_CALIBRATED_THRESHOLD (PLAN-03 لم يُنجَز — انظر batteryLayer.js)"

    private fun readResource(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$name")
            ?: error("contract/$name not found on the test classpath — expected at app/src/test/resources/contract/")
        return InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
    }

    /** The expected entries, read from the file by an independent parse. */
    private fun entriesRead(name: String, overallPath: List<String>): List<AiAbstentionReasonV7> {
        var node = JsonParser.parseString(readResource(name))
        for (key in overallPath) node = node.asJsonObject.get(key)
        return node.asJsonObject.getAsJsonArray("abstentionReasons").map {
            val o = it.asJsonObject
            AiAbstentionReasonV7(o.get("category").asString, o.get("reason").asString)
        }
    }

    private fun clientExpected(name: String) = entriesRead(name, listOf("overall"))
    private fun documentExpected(name: String) = entriesRead(name, listOf("aiResponse", "overall"))

    private fun rawMap(name: String): MutableMap<String, Any?> =
        gson.fromJson(readResource(name), object : TypeToken<MutableMap<String, Any?>>() {}.type)

    // ── path 1 — analyzeDevice ──────────────────────────────────────────

    private fun analyzeDevicePath(name: String): AiAnalysisResponse {
        // What the Firebase Functions SDK hands back as `result.data`: plain
        // nested Map/List/String, NOT a JSON string.
        val resultData: Map<String, Any?> = rawMap(name)
        val resultJson = gson.toJson(resultData)                                 // as analyzeDevice does
        return gson.fromJson(resultJson, AiAnalysisResponse::class.java)         // as analyzeDevice does
    }

    @Test
    fun `path 1 - analyzeDevice, real zero-measured sample - object-shaped abstentionReasons parse without throwing`() {
        val parsed = analyzeDevicePath(zeroMeasuredClient)
        val reasons = parsed.overall?.abstentionReasons
        assertEquals(15, reasons?.size)
        assertEquals(clientExpected(zeroMeasuredClient), reasons)
        assertEquals(AiAbstentionReasonV7("battery", batteryCalibrationCode), reasons?.get(0))
        assertEquals(12, reasons!!.count { it.reason == "DECLARED_BUT_NONE_EXECUTED" })
        assertEquals(2, reasons.count { it.reason == "NO_HEALTH_BEARING_TESTS_DECLARED" })
    }

    @Test
    fun `path 1 - analyzeDevice, real battery-harness-error sample - battery reads HARNESS_ERROR`() {
        val reasons = analyzeDevicePath(harnessErrorClient).overall?.abstentionReasons
        assertEquals(15, reasons?.size)
        assertEquals(clientExpected(harnessErrorClient), reasons)
        assertEquals(AiAbstentionReasonV7("battery", "HARNESS_ERROR"), reasons?.get(0))
    }

    @Test
    fun `GsonBuilder create parses the real sample file directly too (the reader form), without throwing`() {
        val parsed = gson.fromJson(readResource(zeroMeasuredClient), AiAnalysisResponse::class.java)
        assertEquals(clientExpected(zeroMeasuredClient), parsed.overall?.abstentionReasons)
    }

    // ── path 2 — Firestore report document ──────────────────────────────

    private fun firestorePath(name: String): AiAnalysisResponse {
        val raw = rawMap(name)
        assertEquals(
            "the cache gate must accept the real document, or fetchExistingReport never reaches the parse",
            true,
            isCachedReportComplete(raw)
        )
        val flat = flattenReportDocument(raw)                                    // as fetchExistingReport does
        return gson.fromJson(gson.toJson(flat), AiAnalysisResponse::class.java)  // as fetchExistingReport does
    }

    @Test
    fun `path 2 - Firestore document, real zero-measured sample, through the cache gate and flattenReportDocument`() {
        val reasons = firestorePath(zeroMeasuredDocument).overall?.abstentionReasons
        assertEquals(15, reasons?.size)
        assertEquals(documentExpected(zeroMeasuredDocument), reasons)
        assertEquals(AiAbstentionReasonV7("battery", batteryCalibrationCode), reasons?.get(0))
        // The document and the client payload of the SAME scan carry the
        // same list — the two paths read one fact, not two.
        assertEquals(clientExpected(zeroMeasuredClient), reasons)
    }

    @Test
    fun `path 2 - Firestore document, real battery-harness-error sample, battery reads HARNESS_ERROR`() {
        val reasons = firestorePath(harnessErrorDocument).overall?.abstentionReasons
        assertEquals(15, reasons?.size)
        assertEquals(documentExpected(harnessErrorDocument), reasons)
        assertEquals(AiAbstentionReasonV7("battery", "HARNESS_ERROR"), reasons?.get(0))
        assertEquals(clientExpected(harnessErrorClient), reasons)
    }

    // ── path 3 — Room aiAnalysisJson round trip, plain Gson ─────────────

    private fun roomRoundTrip(name: String): Pair<AiAnalysisResult, AiAnalysisResult> {
        val response = gson.fromJson(readResource(name), AiAnalysisResponse::class.java)
        // What AiScoringEngine hands to Room: the response's `overall`
        // carried by reference into AiAnalysisResult.
        val result = AiAnalysisResult(
            pipeline = response.pipeline,
            language = response.language,
            overall = response.overall,
            categories = response.categories,
            narrative = response.narrative,
            narrativeIncomplete = response.narrativeIncomplete,
            narrativeIncompleteFields = response.narrativeIncompleteFields
        )
        val plainGson = Gson()  // ViewModels.kt:894-895 — plain Gson, not GsonBuilder
        val stored = plainGson.toJson(result)                                        // the save leg
        val read = plainGson.fromJson(stored, AiAnalysisResult::class.java)          // the load leg
        val reread = plainGson.fromJson(plainGson.toJson(read), AiAnalysisResult::class.java)
        return read to reread
    }

    @Test
    fun `path 3 - Room round trip of the real zero-measured sample through AiAnalysisResult, plain Gson`() {
        val (read, reread) = roomRoundTrip(zeroMeasuredClient)
        val expected = clientExpected(zeroMeasuredClient)
        assertEquals(expected, read.overall?.abstentionReasons)
        // And what gets WRITTEN back (the save leg) re-reads identically.
        assertEquals(expected, reread.overall?.abstentionReasons)
        assertEquals(15, read.overall?.abstentionReasons?.size)
    }

    @Test
    fun `path 3 - Room round trip of the real battery-harness-error sample keeps HARNESS_ERROR`() {
        val (read, reread) = roomRoundTrip(harnessErrorClient)
        val expected = clientExpected(harnessErrorClient)
        assertEquals(expected, read.overall?.abstentionReasons)
        assertEquals(expected, reread.overall?.abstentionReasons)
        assertEquals(AiAbstentionReasonV7("battery", "HARNESS_ERROR"), read.overall?.abstentionReasons?.get(0))
    }

    // ── the other shapes of the field, from the real samples ────────────

    @Test
    fun `absent abstentionReasons still parses to null - the real main sample carries it as JSON null`() {
        val parsed = gson.fromJson(readResource("v7-client-payload-sample.json"), AiAnalysisResponse::class.java)
        assertNull(parsed.overall?.abstentionReasons)
        // The real Honor document (harness error, no letter) too.
        val honor = gson.fromJson(
            gson.toJson(flattenReportDocument(rawMap("v7-reports-document-sample-honor.json"))),
            AiAnalysisResponse::class.java
        )
        assertNull(honor.overall?.abstentionReasons)
    }

    @Test
    fun `the key absent altogether (deleted in memory from the real zero-measured sample) parses to null, unaffected by the shape fix`() {
        val payload = rawMap(zeroMeasuredClient)
        @Suppress("UNCHECKED_CAST")
        val overall = payload["overall"] as MutableMap<String, Any?>
        assertTrue("sanity: the key was there to delete", overall.containsKey("abstentionReasons"))
        overall.remove("abstentionReasons")
        val parsed = gson.fromJson(gson.toJson(payload), AiAnalysisResponse::class.java)
        assertNull(parsed.overall?.abstentionReasons)
    }

    @Test
    fun `an empty abstentionReasons array (set in memory on the real zero-measured sample) parses to an empty list, not null`() {
        val payload = rawMap(zeroMeasuredClient)
        @Suppress("UNCHECKED_CAST")
        val overall = payload["overall"] as MutableMap<String, Any?>
        overall["abstentionReasons"] = emptyList<Any?>()
        val parsed = gson.fromJson(gson.toJson(payload), AiAnalysisResponse::class.java)
        assertEquals(emptyList<AiAbstentionReasonV7>(), parsed.overall?.abstentionReasons)
    }
}
