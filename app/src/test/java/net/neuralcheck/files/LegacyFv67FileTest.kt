package net.neuralcheck.files

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.local.files.FileShape
import net.neuralcheck.data.local.files.TimeSeriesData
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.local.files.VerificationWireNames
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPOutputStream

/**
 * ينتظر الفحص الحقيقي — WAITS FOR THE REAL DEVICE CHECK.
 *
 * An fv67 file, re-analysed by an fv68 build (decision "legacy file", part 2-b):
 * the app updated over 1.0.10 and a scan taken before the update is analysed
 * again. This is the closest fv67 file available and NOT the real thing; the real
 * check is on the next device round (S10 on 1.0.10 -> a scan that is not analysed
 * -> update to 1.0.11 without uninstalling -> re-analysis).
 *
 * ── The file ──
 * `derived/fv67-golden-1.0.8-v6beta-from-neuralcheck-backend-19fc7bc-34dff2b8.ts.json`:
 * byte-for-byte `neuralcheck-backend@19fc7bc:functions/test/fixtures/golden/34dff2b8.ts.json`
 * (sha256 e451306b31e74b6c77d6513989341cfe7a5f0408ed2119bcc085bf251b1720d3,
 * checked after the copy). header.appVersion = 1.0.8-v6beta, footer.fieldsVersion = 67,
 * 1606 samples, 11 variables. Never edited; every variant below is made IN this test.
 *
 * ── Differences from what the d87f2d9 writer wrote on a device ──
 *  1. It is the SERVER's JSON copy of `timeSeries`, not the device's gzip: keys are in
 *     alphabetical order and the test gzips it. (Unverified whether it is the device's
 *     file or the server's copy of the payload; if the latter, what the whitelist did
 *     not pass is already absent from it.)
 *  2. header.deviceFingerprint, header.scanId and footer.samplesSha256 were replaced by
 *     random values of the same shape on the server (neuralcheck-backend@d6c71ea,
 *     2026-09-24). So samplesSha256 does NOT match the samples. The app's reader does
 *     not verify it (it forwards it verbatim), so nothing here depends on it.
 *  3. Its footer has no `phaseBoundaries` (the d87f2d9 writer wrote one, file-only);
 *     a variant below adds it.
 *  4. It was recorded on 1.0.8-v6beta, BEFORE A70 (2026-09-20): its throughput values
 *     are decimal MB/s, where 1.0.9 / 1.0.10 files are binary MiB/s.
 *
 * ── Variants made in this test (the only modifications) ──
 *  - `minus(footer.<key>)` for throttlingEvents, verificationResults, phaseCompletionStatus,
 *    stressEffectiveness, dataQuality, deviceTierMeasured, batteryCurrentScalingApplied:
 *    the key removed from the JSON — the "absent list" cases.
 *  - `minus(<part>)` for header, variables, samples, footer: a whole top-level part removed.
 *  - `withPhaseBoundaries`: adds `footer.phaseBoundaries` with the 8 phases of the nominal
 *    schedule (what the d87f2d9 writer added), to be closer to its output.
 *
 * What these tests cover: the REAL TimeSeriesFileReader, BehaviorFingerprintBuilder and
 * Gson, nothing substituted. What they do not: a file written by a real device, the
 * real update path, and the sampler/service that produced it.
 */
class LegacyFv67FileTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val gson = GsonBuilder().create()

    private fun golden(): JsonObject =
        JsonParser.parseReader(
            InputStreamReader(
                javaClass.getResourceAsStream(
                    "/derived/fv67-golden-1.0.8-v6beta-from-neuralcheck-backend-19fc7bc-34dff2b8.ts.json"
                )!!,
                Charsets.UTF_8
            )
        ).asJsonObject

    private fun report() = ScanReport(
        scanId = "legacy", scanType = ScanType.GENERAL, linkedScanId = null,
        deviceInfo = DeviceInfo("fp", "Model", "Mfr", "14", 34, "1080x2400", 8),
        scanMetadata = ScanMetadata(0L, 0L, 100, 95, 27f, 0L, true, null, "1.0.0"),
        functionalResults = emptyList(), timeSeriesSummary = null, antiTamperFlags = emptyList(),
        technicianInput = null, peerInput = null
    )

    private fun read(json: JsonObject): TimeSeriesData? {
        val dir = tmp.newFolder()
        val file = File(dir, "legacy.ts.json.gz")
        GZIPOutputStream(file.outputStream()).use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        val context: android.content.Context = mock { on { filesDir } doReturn dir }
        return runBlocking { TimeSeriesFileReader(context).readFromPath(file.absolutePath) }
    }

    /** The whole payload as the server receives it. */
    private fun payloadOf(json: JsonObject): JsonObject {
        val data = read(json) ?: error("the file must be readable")
        return gson.toJsonTree(BehaviorFingerprintBuilder().build(report(), data)).asJsonObject
    }

    private fun footerOf(json: JsonObject): JsonObject = payloadOf(json).getAsJsonObject("timeSeries").getAsJsonObject("footer")

    private fun minusFooter(key: String) = golden().also { it.getAsJsonObject("footer").remove(key) }

    // ───────────────────────── the file as it is ─────────────────────────

    @Test
    fun `the fv67 file is read whole and its payload builds without throwing`() {
        val data = read(golden())
        assertNotNull("the real reader must parse it", data)
        assertEquals(1606, data!!.samples.size)
        assertEquals(11, data.variables.size)
        assertEquals(67, data.footer.fieldsVersion)
        val ts = payloadOf(golden()).getAsJsonObject("timeSeries")
        assertEquals(1606, ts.getAsJsonArray("samples").size())
        assertEquals("file", ts.get("timeSeriesState").asString)
    }

    @Test
    fun `the payload carries the FILE's stamp 67 and its own appVersion - not this build's`() {
        val ts = payloadOf(golden()).getAsJsonObject("timeSeries")
        assertEquals("the stamp describes the source of the samples, not the sending app", 67,
            ts.getAsJsonObject("footer").get("fieldsVersion").asInt)
        assertEquals("1.0.8-v6beta", ts.getAsJsonObject("header").get("appVersion").asString)
        assertTrue("this build is newer than the file", net.neuralcheck.BuildConfig.FIELDS_VERSION > 67)
        assertTrue(net.neuralcheck.BuildConfig.VERSION_NAME != "1.0.8-v6beta")
    }

    @Test
    fun `nothing that only exists since fv68 is in the payload built from an fv67 file`() {
        val footer = footerOf(golden())
        for (key in FileShape.FOOTER_FIELDS_SINCE_68) assertFalse("$key must be absent", footer.has(key))
        assertFalse(footer.getAsJsonObject("channelDiscovery").has("reasons"))
        assertFalse(footer.getAsJsonObject("scanStillness").has("stillnessState"))
        val vr = footer.getAsJsonObject("verificationResults")
        for (key in VerificationWireNames.SINCE_68) {
            assertFalse("verificationResults.$key must be absent - its default would be a false claim", vr.has(key))
        }
    }

    @Test
    fun `the free-memory reading and the insufficient-memory state are absent from an fv67 payload and named`() {
        val footer = footerOf(golden())
        val vr = footer.getAsJsonObject("verificationResults")
        assertFalse(vr.has(VerificationWireNames.MEMORY_VERIFY_FREE_BYTES))
        assertFalse(vr.has(VerificationWireNames.MEMORY_VERIFY_STATE))
        val named = footer.getAsJsonArray("fieldsNotInFile").map { it.asString }
        assertTrue("verificationResults.memoryVerifyFreeBytes" in named)
        assertTrue("verificationResults.memoryVerifyState" in named)
    }

    @Test
    fun `what the file did carry is forwarded as it was - with the legacy values it had`() {
        val vr = footerOf(golden()).getAsJsonObject("verificationResults")
        val file = golden().getAsJsonObject("footer").getAsJsonObject("verificationResults")
        assertEquals(file.get("gpuComputeGemmCorrect").asBoolean, vr.get("gpuComputeGemmCorrect").asBoolean)
        assertEquals(file.get("criticalDefectDetected").asBoolean, vr.get("criticalDefectDetected").asBoolean)
        assertEquals("the literal 0 the old build wrote is forwarded as written; the stamp 67 is what flags it",
            file.get("storageWriteVerifyErrors").asInt, vr.get("storageWriteVerifyErrors").asInt)
        assertEquals(file.get("workersPinnedBig").asInt, vr.get("workersPinnedBig").asInt)
        assertEquals(file.getAsJsonArray("storageThroughputSegments").size(),
            vr.getAsJsonArray("storageThroughputSegments").size())
        // The unit was never declared by a 1.0.8-v6beta file; the default MiB/s would be a wrong claim.
        assertFalse(vr.has("storageThroughputSegmentsUnit"))
    }

    @Test
    fun `no key appears in the payload footer that the file did not have - except the declaration`() {
        val file = golden().getAsJsonObject("footer")
        val payload = footerOf(golden())
        val added = mutableListOf<String>()
        fun walk(path: String, a: JsonElement?, b: JsonElement?) {
            if (b == null || !b.isJsonObject) return
            for ((k, v) in b.asJsonObject.entrySet()) {
                val inFile = a?.takeIf { it.isJsonObject }?.asJsonObject?.get(k)
                if (inFile == null) added += "$path.$k" else walk("$path.$k", inFile, v)
            }
        }
        walk("footer", file, payload)
        assertEquals("only the app's own declaration may be added", listOf("footer.fieldsNotInFile"), added)
    }

    @Test
    fun `the payload names every fv68 field the file could not have - nothing is silently missing`() {
        val named = footerOf(golden()).getAsJsonArray("fieldsNotInFile").map { it.asString }
        for (key in FileShape.FOOTER_FIELDS_SINCE_68) assertTrue("$key must be named", key in named)
        for (key in FileShape.NESTED_FIELDS_SINCE_68) assertTrue("$key must be named", key in named)
        for (key in VerificationWireNames.SINCE_68) {
            assertTrue("verificationResults.$key must be named", (FileShape.VERIFICATION_PREFIX + key) in named)
        }
        assertEquals("the names are unique", named.size, named.toSet().size)
    }

    @Test
    fun `a footer with phaseBoundaries as the d87f2d9 writer wrote it reads the same - the list is file-only`() {
        val g = golden()
        val boundaries = com.google.gson.JsonArray()
        listOf(
            "INIT_PROFILING" to 0, "STORAGE_STRESS" to 1, "MEMORY_STRESS" to 2, "CPU_SUSTAINED" to 3,
            "GPU_SUSTAINED" to 4, "COMBINED_PEAK" to 5, "BURST_PATTERN" to 6, "COOLDOWN" to 7
        ).forEachIndexed { i, (name, id) ->
            boundaries.add(JsonObject().apply {
                addProperty("phaseId", id); addProperty("phaseName", name)
                addProperty("startMs", i * 60_000L); addProperty("endMs", (i + 1) * 60_000L)
            })
        }
        g.getAsJsonObject("footer").add("phaseBoundaries", boundaries)
        val data = read(g)!!
        assertEquals(8, data.footer.phaseBoundaries.size)
        assertNull("a boundary from a file with no windows has none", data.footer.phaseBoundaries[2].windows)
        assertFalse(payloadOf(g).getAsJsonObject("timeSeries").getAsJsonObject("footer").has("phaseBoundaries"))
    }

    // ───────────────────────── the absent-list cases ─────────────────────────

    @Test
    fun `a file with no throttlingEvents key does not throw - the key stays absent and is named`() {
        val footer = footerOf(minusFooter("throttlingEvents"))
        assertFalse("not an empty list: [] would say 'no events'", footer.has("throttlingEvents"))
        assertTrue("throttlingEvents" in footer.getAsJsonArray("fieldsNotInFile").map { it.asString })
    }

    @Test
    fun `each other absent reference-typed footer key stays absent and is named - none throws`() {
        for (key in listOf(
            "verificationResults", "phaseCompletionStatus", "stressEffectiveness", "dataQuality",
            "deviceTierMeasured", "batteryCurrentScalingApplied"
        )) {
            val footer = footerOf(minusFooter(key))
            assertFalse("$key must be absent from the payload", footer.has(key))
            assertTrue(
                "$key must be named in fieldsNotInFile",
                key in footer.getAsJsonArray("fieldsNotInFile").map { it.asString }
            )
        }
    }

    @Test
    fun `a file that has everything the builder asks of a 71 footer says nothing is missing - an explicit empty list`() {
        // REPLACES the fv68 version of this test (rule 23): an fv68 footer now lacks the two fields fieldsVersion 71 added, and says so (the next test)
        val rt = FooterRoundTrip(tmp.newFolder())
        val footer = rt.run(
            rt.baseFooter().copy(
                fieldsVersion = 71,
                batteryWindowPct = net.neuralcheck.data.local.files.BatteryWindowPctFooter(30, 85),
                brightnessLock = net.neuralcheck.data.local.files.BrightnessLockFooter("not_attempted:value_not_decided", null, null)
            )
        ).wireFooter
        assertEquals("checked, nothing missing", 0, footer.getAsJsonArray("fieldsNotInFile").size())
        assertEquals(71, footer.get("fieldsVersion").asInt)
    }

    @Test
    fun `an fv68 footer is complete for its own stamp - it lacks exactly the two fields fieldsVersion 71 added, and says so`() {
        val rt = FooterRoundTrip(tmp.newFolder())
        val footer = rt.run(rt.baseFooter()).wireFooter
        assertEquals(68, footer.get("fieldsVersion").asInt)
        assertEquals(
            net.neuralcheck.data.local.files.FileShape.FOOTER_FIELDS_SINCE_71,
            footer.getAsJsonArray("fieldsNotInFile").map { it.asString }
        )
    }

    @Test
    fun `a fresh payload keeps the fv68 states - the legacy rule applies to old stamps only`() {
        val rt = FooterRoundTrip(tmp.newFolder())
        val vr = rt.run(rt.baseFooter()).wireFooter.getAsJsonObject("verificationResults")
        for (key in listOf("gpuComputeGemmState", "storageVerifyState", "workersPinState", "memoryVerifyState",
            "cpuKnownAnswerState", "storageThroughputSegmentsUnit", "criticalDefectInputsNotRun")) {
            assertTrue("$key must be present on an fv68 payload", vr.has(key))
        }
    }

    // ───────────────────────── a file with a whole part missing ─────────────────────────

    @Test
    fun `a file missing a whole part is unusable - the reader says so instead of handing back a half object`() {
        for (part in listOf("header", "variables", "samples", "footer")) {
            val g = golden().also { it.remove(part) }
            assertNull("a file with no $part must read as unusable", read(g))
        }
    }

    @Test
    fun `an unusable file reaches the payload as no_file - not as an exception`() {
        val data = read(golden().also { it.remove("footer") })
        assertNull(data)
        val fp = BehaviorFingerprintBuilder().build(report(), data, noTimeSeriesReason = "unusable test file")
        val ts = gson.toJsonTree(fp).asJsonObject.getAsJsonObject("timeSeries")
        assertTrue(ts.get("timeSeriesState").asString.startsWith("no_file:"))
        assertFalse(ts.getAsJsonObject("footer").has("fieldsNotInFile"))
    }
}
