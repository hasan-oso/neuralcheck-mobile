package net.neuralcheck.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiPipeline
import net.neuralcheck.data.remote.model.isComplete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * A109 — [isCachedReportComplete] is the gate `fetchExistingReport` uses
 * to decide whether a raw `reports/{scanId}` Firestore doc is worth
 * rehydrating instead of paying for a fresh `analyzeDevice` call.
 * Extracted to a top-level function (A110 precedent) so it's testable
 * here without mocking `DocumentSnapshot`/`FirebaseFirestore` statics.
 *
 * Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦) — القاعدة 23: the V7-branch
 * tests below REPLACE the ones this file had before (they asserted
 * `data["overall"]` at the document ROOT, which is not the real shape —
 * see [flattenReportDocument]'s doc — so they encoded the exact bug this
 * round fixes: a gate that could never pass on a real V7 document, not
 * just one missing `letter`). Nothing here duplicates prior coverage
 * unchanged except the V6 branch tests, which are untouched behaviour.
 */
class FirebaseAnalysisServiceCacheGateTest {

    @Test
    fun `null doc is never complete`() {
        assertFalse(isCachedReportComplete(null))
    }

    // Order 2026-09-26 (القرار ١٠٢ البند ٤، القاعدة 41) — rule 23
    // replacement: the four tests below were hand-written maps
    // (`mapOf("grade" to "B", "score" to 70)`), not derived from a real
    // document. Replaced with the real V6 golden sample
    // (`v6-reports-document-sample.json`), each mutating ONLY the one
    // field the test is about — same coverage, real material.

    @Test
    fun `real V6 sample doc with grade and numeric score passes`() {
        assertTrue(isCachedReportComplete(loadRawDoc("v6-reports-document-sample.json")))
    }

    @Test
    fun `real V6 sample doc with score removed fails`() {
        val data = loadRawDoc("v6-reports-document-sample.json") - "score"
        assertFalse(isCachedReportComplete(data))
    }

    @Test
    fun `real V6 sample doc with grade blanked fails`() {
        val data = loadRawDoc("v6-reports-document-sample.json") + ("grade" to "")
        assertFalse(isCachedReportComplete(data))
    }

    @Test
    fun `real V6 sample doc with the pipeline field removed still uses the V6 branch`() {
        // Room text from a June-era app write with no `pipeline` key at
        // all — explicitly OUT of this order's sample-creation scope (a
        // separate upgrade test measures that Room shape) — but this IS
        // the same absent-pipeline case for the RAW DOCUMENT path, derived
        // from the real sample rather than invented.
        val data = loadRawDoc("v6-reports-document-sample.json") - "pipeline"
        assertTrue(isCachedReportComplete(data))
    }

    @Test
    fun `resync 2026-09-26 (القرار ١٠٢ البند ٢) — an unrecognised pipeline value is NOT complete, even with a real-looking grade+score`() {
        // Rule 23 — REPLACES the old test of the same slot, which asserted
        // an unrecognised tag falls back to the V6 grade+score check and
        // passes. That was exactly the silent-success-on-unknown rule 22
        // objects to: a tag this app has never seen is not known to be
        // V6-shaped just because it happens to carry grade/score fields —
        // AiPipeline.classifyShape now routes it to UNKNOWN, and an
        // UNKNOWN shape is never "complete" (forces a live re-fetch
        // instead of trusting an ambiguous cached blob).
        val data = mapOf(
            "pipeline" to "v8_unknown_future_shape",
            "grade" to "C",
            "score" to 55
        )
        assertFalse(isCachedReportComplete(data))
    }

    // Order 2026-09-26 (القرار ١٠٢ البند ٢) — v3_legacy/v5_precomputed
    // (both root-shape V6) and cached (content-routed) — new coverage, not
    // a replacement (this exact behaviour didn't exist before this order).
    // A comprehensive golden-sample-based rewrite of this whole file is
    // queued separately (القرار ١٠٢ البند ٤) — these are the minimal direct
    // proofs for THIS order's own code change.

    @Test
    fun `v3_legacy and v5_precomputed docs use the V6 grade+score check`() {
        for (tag in listOf(AiPipeline.V3_LEGACY, AiPipeline.V5_PRECOMPUTED)) {
            assertTrue("tag=$tag", isCachedReportComplete(mapOf("pipeline" to tag, "grade" to "B", "score" to 70)))
            assertFalse("tag=$tag", isCachedReportComplete(mapOf("pipeline" to tag, "grade" to "")))
        }
    }

    @Test
    fun `cached doc with real V7 content passes via the V7 check, not the V6 one`() {
        val data = mapOf(
            "pipeline" to AiPipeline.CACHED,
            "aiResponse" to mapOf("overall" to mapOf("letterSource" to "BAND"))
            // Deliberately NO root grade/score — the old `else` branch
            // would have failed this real, complete cached-V7 document.
        )
        assertTrue(isCachedReportComplete(data))
    }

    @Test
    fun `cached doc with real V6 content passes via the V6 check`() {
        val data = mapOf("pipeline" to AiPipeline.CACHED, "grade" to "A", "score" to 90)
        assertTrue(isCachedReportComplete(data))
    }

    @Test
    fun `cached doc with neither a V6 nor a V7 signal is not complete`() {
        val data = mapOf("pipeline" to AiPipeline.CACHED, "deviceModel" to "Pixel 8")
        assertFalse(isCachedReportComplete(data))
    }

    // ---- V7 branch: real document shape is data.aiResponse.overall, not
    // ---- data.overall (root). See flattenReportDocument's doc.

    @Test
    fun `V7 doc with root-level overall (the old, wrong shape) fails — overall must be under aiResponse`() {
        // This is the exact shape the OLD gate accepted (and the old test
        // file asserted true for) — proof the fix actually moved the read
        // path, not just relaxed the required field.
        val data = mapOf(
            "pipeline" to AiPipeline.V7_FINDINGS_BASED,
            "overall" to mapOf("letter" to "B", "letterSource" to "BAND")
        )
        assertFalse(
            "overall at the document root is not the real shape — must be nested under aiResponse",
            isCachedReportComplete(data)
        )
    }

    @Test
    fun `V7 doc with aiResponse overall letterSource passes even with letter null`() {
        // The order's own requirement: no letter required, only the
        // declared state (letterSource).
        val data = mapOf(
            "pipeline" to AiPipeline.V7_FINDINGS_BASED,
            "aiResponse" to mapOf(
                "overall" to mapOf("letter" to null, "letterSource" to "HARNESS_ERROR")
            )
        )
        assertTrue(
            "letterSource present is enough — letter is legitimately null for HARNESS_ERROR",
            isCachedReportComplete(data)
        )
    }

    @Test
    fun `V7 doc with aiResponse overall but blank letterSource fails`() {
        val data = mapOf(
            "pipeline" to AiPipeline.V7_FINDINGS_BASED,
            "aiResponse" to mapOf("overall" to mapOf("letterSource" to ""))
        )
        assertFalse(isCachedReportComplete(data))
    }

    @Test
    fun `V7 doc with no aiResponse block at all fails without throwing`() {
        val data = mapOf("pipeline" to AiPipeline.V7_FINDINGS_BASED)
        assertFalse(isCachedReportComplete(data))
    }

    @Test
    fun `V7 doc with aiResponse but no overall block fails without throwing`() {
        val data = mapOf(
            "pipeline" to AiPipeline.V7_FINDINGS_BASED,
            "aiResponse" to mapOf("language" to "ar")
        )
        assertFalse(isCachedReportComplete(data))
    }

    // ---- Real fixtures: sample B (complete, real letter) and Honor B
    // ---- (complete, no letter, harnessError declared). Structural/
    // ---- semantic assertions only — no identifier values, per the
    // ---- coordinator's own warning that the backend regenerates these
    // ---- fixtures with different identifiers.

    private fun loadRawDoc(fileName: String): Map<String, Any?> {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$fileName")
            ?: error("Fixture not found on classpath — expected at app/src/test/resources/contract/$fileName")
        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return Gson().fromJson(text, type)
    }

    private fun parseResponse(flat: Map<String, Any?>): AiAnalysisResponse {
        val gson = Gson()
        return gson.fromJson(gson.toJson(flat), AiAnalysisResponse::class.java)
    }

    @Test
    fun `sample B raw doc passes the cache gate`() {
        val data = loadRawDoc("v7-reports-document-sample.json")
        assertTrue(isCachedReportComplete(data))
    }

    @Test
    fun `sample B flattens and decodes a real letter`() {
        val data = loadRawDoc("v7-reports-document-sample.json")
        val response = parseResponse(flattenReportDocument(data))
        assertEquals(AiPipeline.V7_FINDINGS_BASED, response.pipeline)
        assertEquals("C", response.overall?.letter)
        assertEquals("GATE:ROOT_DETECTED", response.overall?.letterSource)
        assertEquals(false, response.overall?.harnessError)
        assertTrue("sample B must read as a complete response", response.isComplete())
    }

    @Test
    fun `Honor B raw doc passes the cache gate despite no letter`() {
        val data = loadRawDoc("v7-reports-document-sample-honor.json")
        assertTrue(
            "a harnessError report is complete — letter absence is declared, not a partial write",
            isCachedReportComplete(data)
        )
    }

    @Test
    fun `Honor B flattens and decodes a null letter with harnessError declared`() {
        val data = loadRawDoc("v7-reports-document-sample-honor.json")
        val response = parseResponse(flattenReportDocument(data))
        assertEquals(AiPipeline.V7_FINDINGS_BASED, response.pipeline)
        assertNull("Honor B has no letter by design", response.overall?.letter)
        assertEquals("HARNESS_ERROR", response.overall?.letterSource)
        assertEquals(true, response.overall?.harnessError)
        assertEquals("FUNCTIONAL_RESULTS_UNAVAILABLE", response.overall?.harnessErrorReason)
        assertTrue(
            "Honor B must read as complete — the order's own requirement: " +
                "a harnessError report is complete, not gated on a missing letter",
            response.isComplete()
        )
    }
}
