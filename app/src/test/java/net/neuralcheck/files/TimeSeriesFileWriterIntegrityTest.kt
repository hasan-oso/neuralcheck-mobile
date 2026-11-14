package net.neuralcheck.files

import android.content.Context
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.local.files.TimeSeriesData
import net.neuralcheck.data.local.files.TimeSeriesFileWriter
import net.neuralcheck.data.local.files.TimeSeriesFooter
import net.neuralcheck.data.local.files.TimeSeriesHeader
import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * fieldsVersion 65 — writer hardening regression tests (silent data loss).
 *
 * Pre-65 failure modes locked down here:
 *  1. A GZIP write failure was swallowed and the FINAL PATH RETURNED AS
 *     IF VALID → the scan record pointed at a missing/corrupt file.
 *  2. When startNewFile couldn't open the temp writer, every sample
 *     stayed in the in-memory buffer and finalize (which reads ONLY the
 *     temp file) silently produced an empty samples array.
 *  3. Flush-retry could duplicate partially-written lines in the temp
 *     file with nothing de-duplicating them downstream.
 */
class TimeSeriesFileWriterIntegrityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesRoot: File
    private lateinit var context: Context

    private val gson = GsonBuilder().serializeNulls().create()

    @Before
    fun setUp() {
        filesRoot = tmp.newFolder("filesdir")
        context = mock<Context> { on { filesDir } doReturn filesRoot }
    }

    private fun header(scanId: String) = TimeSeriesHeader(
        scanId = scanId,
        deviceFingerprint = "fp-test",
        scanType = "general",
        appVersion = "test",
        androidApiLevel = 35,
        deviceModel = "UnitTest",
        cpuCores = 8,
        totalRamMb = 8192,
        startTimestamp = 1_000L,
        batteryLevelStart = 80,
        batteryTempStart = 25f
    )

    private fun sample(ts: Long, phase: Int, v0: Float) = TimeSeriesSample(
        ts = ts, phase = phase,
        v = FloatArray(V2_SAMPLE_VAR_COUNT) { i -> if (i == 0) v0 else i.toFloat() }
    )

    private fun footer() = TimeSeriesFooter(
        endTimestamp = 2_000L,
        totalSamples = 0,
        isComplete = true,
        earlyStopReason = null,
        batteryLevelEnd = 75,
        maxBatteryTemp = 38f,
        cpuTempFinalC = null,
        cpuTempFinalSource = "absent:test fixture"
    )

    private fun readGz(path: String): TimeSeriesData =
        FileInputStream(File(path)).use { fis ->
            GZIPInputStream(fis).use { gz ->
                InputStreamReader(gz, Charsets.UTF_8).use { r ->
                    gson.fromJson(r, TimeSeriesData::class.java)
                }
            }
        }

    // ───────────────────────── success path ─────────────────────────

    @Test
    fun `finalize writes atomic gz, stamps checksum and sidecar, returns valid path`() = runBlocking {
        val writer = TimeSeriesFileWriter(context)
        writer.enableV2Mode()
        writer.startNewFile(header("scan-ok"))
        // 25 samples → two auto-flushes (threshold 10) + 5 buffered for
        // finalize's own final flush.
        repeat(25) { i -> writer.addSample(sample(ts = 1_000L + i, phase = 3, v0 = i.toFloat())) }

        val path = writer.finalize(footer())

        assertNotNull("finalize must return a path on success", path)
        val finalFile = File(path!!)
        assertTrue(finalFile.exists())
        assertTrue("tmp sibling must not survive", !File("$path.tmp").exists())

        val data = readGz(path)
        assertEquals(25, data.samples.size)
        assertEquals(0, data.footer.flushFailureCount)

        // Content checksum: recompute from the parsed samples with the same
        // canonical serialisation — must match footer.samplesSha256.
        val digest = MessageDigest.getInstance("SHA-256")
        data.samples.forEachIndexed { i, s ->
            if (i > 0) digest.update('\n'.code.toByte())
            digest.update(gson.toJson(s).toByteArray(Charsets.UTF_8))
        }
        val recomputed = digest.digest().joinToString("") { "%02x".format(it) }
        assertEquals(recomputed, data.footer.samplesSha256)

        // File-level sidecar: 64 lowercase hex chars.
        val sidecar = File(finalFile.parentFile, "${finalFile.name}.sha256")
        assertTrue(sidecar.exists())
        assertTrue(sidecar.readText().matches(Regex("^[0-9a-f]{64}$")))
    }

    // ─────────────── failure path: GZIP write must not lie ───────────────

    @Test
    fun `finalize returns null when final gz cannot be written`() = runBlocking {
        val writer = TimeSeriesFileWriter(context)
        writer.enableV2Mode()
        writer.startNewFile(header("scan-fail"))
        repeat(5) { i -> writer.addSample(sample(ts = 2_000L + i, phase = 3, v0 = i.toFloat())) }

        // Block FINAL_DIR by occupying its name with a plain FILE — mkdirs
        // fails, FileOutputStream on the .tmp throws.
        File(filesRoot, "timeseries").writeText("not a directory")

        val path = writer.finalize(footer())
        assertNull("a failed GZIP write must return null, never a path", path)
    }

    // ───── silent-loss path: temp writer never opened → buffer merged ─────

    @Test
    fun `finalize merges in-memory buffer when temp writer never opened`() = runBlocking {
        // Block TEMP_DIR with a plain file so startNewFile's FileWriter
        // throws and tempWriter stays null (the pre-65 total-loss path).
        File(filesRoot, "timeseries_temp").writeText("not a directory")

        val writer = TimeSeriesFileWriter(context)
        writer.enableV2Mode()
        writer.startNewFile(header("scan-merge"))
        repeat(12) { i -> writer.addSample(sample(ts = 3_000L + i, phase = 4, v0 = i.toFloat())) }

        val path = writer.finalize(footer())

        assertNotNull("samples held in memory must still be persisted", path)
        val data = readGz(path!!)
        assertEquals("all buffered samples must reach the final file", 12, data.samples.size)
        assertTrue(
            "failed flushes must be visible in footer telemetry",
            (data.footer.flushFailureCount ?: 0) > 0
        )
    }

    // ───────────── duplicate suppression (flush-retry lines) ─────────────

    @Test
    fun `finalize de-duplicates content-identical samples`() = runBlocking {
        val writer = TimeSeriesFileWriter(context)
        writer.enableV2Mode()
        writer.startNewFile(header("scan-dedupe"))
        val dup = sample(ts = 4_000L, phase = 3, v0 = 1f)
        // Same content written twice (what a failed-then-retried flush
        // leaves in the temp file) + one distinct sample.
        writer.addSample(dup)
        writer.addSample(dup.copy())
        writer.addSample(sample(ts = 4_001L, phase = 3, v0 = 2f))
        writer.flush()

        val path = writer.finalize(footer())

        assertNotNull(path)
        val data = readGz(path!!)
        assertEquals("content-identical lines must collapse to one", 2, data.samples.size)
    }
}
