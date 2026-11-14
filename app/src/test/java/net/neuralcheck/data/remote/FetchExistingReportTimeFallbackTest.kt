package net.neuralcheck.data.remote

import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.presentation.screen.scanHeaderDateText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.InputStreamReader
import java.util.Date

/**
 * Order 2026-09-26 (القرار ١٠٢ البند ١ / البند ٤، القاعدة 41). Proves the
 * THREE cases named by the order, chained through the exact same steps
 * [FirebaseAnalysisService.fetchExistingReport] performs (flatten -> Gson
 * round-trip -> `.copy(createdAtMs = extractCreatedAtMs(data))` — that
 * function itself is not directly callable here without mocking
 * `DocumentSnapshot`/`FirebaseFirestore` statics, the same A109/A110
 * reasoning `FirebaseAnalysisServiceCacheGateTest` already documents, so
 * this test reproduces its documented chain step by step instead), ending
 * at [scanHeaderDateText] — the same function `ReportContent` calls — so
 * this is a genuine end-to-end proof of what reaches the screen, not just
 * that a field is reachable (rule 30).
 *
 * Derived from the REAL `v6-reports-document-sample.json` golden sample by
 * mutating ONLY the timestamp fields for each case — no hand-built
 * document. `createdAt` is converted from the fixture's Node-Admin-SDK
 * shape (`{_seconds, _nanoseconds}`) to a REAL `com.google.firebase.Timestamp`
 * object first (constructed via `Timestamp(Date(millis))` from those same
 * two numbers) — the shape this function actually receives from the
 * Android Firestore SDK's `DocumentSnapshot.getData()`, per
 * [extractCreatedAtMs]'s own doc — not the fixture's on-disk shape, which
 * would never appear at this exact read point in the real app (that shape
 * is proven REJECTED, not accepted, by `ExtractCreatedAtMsTest`).
 */
class FetchExistingReportTimeFallbackTest {

    private fun loadRawDoc(fileName: String): Map<String, Any?> {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$fileName")
            ?: error("Fixture not found on classpath — expected at app/src/test/resources/contract/$fileName")
        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return Gson().fromJson(text, type)
    }

    /** The fixture's on-disk `{_seconds, _nanoseconds}` -> a real `Timestamp`,
     *  as documented on the class above. */
    private fun realCreatedAtTimestamp(rawDoc: Map<String, Any?>): Timestamp {
        val raw = rawDoc["createdAt"] as Map<*, *>
        val seconds = (raw["_seconds"] as Number).toLong()
        val nanos = (raw["_nanoseconds"] as Number).toInt()
        return Timestamp(Date(seconds * 1000L + nanos / 1_000_000L))
    }

    /** Reproduces fetchExistingReport's exact chain (see class doc) against
     *  a raw document map that already carries a REAL Timestamp object at
     *  "createdAt" (mirroring what DocumentSnapshot.getData() hands the
     *  real reader) — never through Gson's automatic parse. */
    private fun parseWithCreatedAt(rawDoc: Map<String, Any?>): AiAnalysisResponse {
        val flat = flattenReportDocument(rawDoc)
        val json = Gson().toJson(flat)
        return Gson().fromJson(json, AiAnalysisResponse::class.java)
            .copy(createdAtMs = extractCreatedAtMs(rawDoc))
    }

    @Test
    fun `case 1 - analyzedAt present wins over createdAt`() {
        val v6 = loadRawDoc("v6-reports-document-sample.json")
        val doc = v6 + mapOf(
            "analyzedAt" to 1800000000000L,
            "createdAt" to realCreatedAtTimestamp(v6)
        )
        val response = parseWithCreatedAt(doc)
        assertEquals(1800000000000L, response.analyzedAt)
        val timestampMs = response.analyzedAt ?: response.createdAtMs
        assertEquals(1800000000000L, timestampMs)
        // Real timestamp reaches the screen text as a formatted date, not
        // the fallback text.
        val text = scanHeaderDateText(timestampMs, unavailableText = "SHOULD_NOT_APPEAR")
        assertEquals(false, text.contains("SHOULD_NOT_APPEAR"))
    }

    @Test
    fun `case 2 - analyzedAt absent (V6's real shape), createdAt present - falls back to createdAt`() {
        val v6 = loadRawDoc("v6-reports-document-sample.json")
        // V6's OWN real shape already has no `analyzedAt` — confirmed
        // (not assumed) by the assertNull below, reading the untouched
        // fixture, before applying the one real mutation this case needs
        // (the real Timestamp object).
        val withRealTimestamp = v6 + ("createdAt" to realCreatedAtTimestamp(v6))
        val response = parseWithCreatedAt(withRealTimestamp)
        assertNull("v6-reports-document-sample.json must not carry analyzedAt", response.analyzedAt)
        val expectedMs = (v6["createdAt"] as Map<*, *>).let { raw ->
            (raw["_seconds"] as Number).toLong() * 1000L + (raw["_nanoseconds"] as Number).toInt() / 1_000_000L
        }
        assertEquals(expectedMs, response.createdAtMs)
        val timestampMs = response.analyzedAt ?: response.createdAtMs
        assertEquals(expectedMs, timestampMs)
        val text = scanHeaderDateText(timestampMs, unavailableText = "SHOULD_NOT_APPEAR")
        assertEquals(false, text.contains("SHOULD_NOT_APPEAR"))
    }

    @Test
    fun `case 3 - both analyzedAt and createdAt absent - report still renders, with the explicit unavailable text`() {
        val doc = loadRawDoc("v6-reports-document-sample.json") - "analyzedAt" - "createdAt"
        val response = parseWithCreatedAt(doc)
        assertNull(response.analyzedAt)
        assertNull("no createdAt key at all -> extractCreatedAtMs must return null, not a fabricated time", response.createdAtMs)
        val timestampMs = response.analyzedAt ?: response.createdAtMs
        assertNull(timestampMs)
        // The one thing this order actually changed: this null no longer
        // fails the whole report load (see ViewModels.kt's
        // loadFromRemoteFallback — deviceModel is the ONLY required field
        // now; not re-tested here since that requires a Hilt/mocked
        // ViewModel harness this codebase doesn't have anywhere — verified
        // by reading that function's current code instead, cited in this
        // task's report). Here: it renders the explicit fallback text.
        val text = scanHeaderDateText(timestampMs, unavailableText = "تاريخ الفحص: غير متوفر")
        assertEquals("تاريخ الفحص: غير متوفر", text)
        // deviceMarketingName is real on this sample — confirms the header
        // WOULD build (loadFromRemoteFallback's only remaining gate).
        assertEquals("Samsung Galaxy S10 5G", response.deviceMarketingName)
    }
}
