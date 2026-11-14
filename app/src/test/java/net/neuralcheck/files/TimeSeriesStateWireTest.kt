package net.neuralcheck.files

import android.content.Context
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.local.files.TimeSeriesData
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.local.files.TimeSeriesFileWriter
import net.neuralcheck.data.local.files.TimeSeriesStates
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * fieldsVersion 68 (ت-18 row 11, rule 22): a payload with no time series (or
 * with only the footer backup) used to ship a "structurally empty" header and
 * footer — 0 / "" / false / 0f in every field, none of them ever read. Now
 * those fields are ABSENT keys and `timeSeriesState`, always present, says
 * where the time series came from.
 */
class TimeSeriesStateWireTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val headerKeys = listOf(
        "deviceModel", "androidApiLevel", "totalRamMb", "cpuCores", "batteryLevelStart",
        "batteryTempStart", "isPowerSaveMode", "startTimestamp", "deviceFingerprint", "appVersion", "scanType"
    )
    private val footerMeasurementKeys = listOf(
        "endTimestamp", "maxBatteryTemp", "maxCpuTemp", "cpuTempFinalC", "batteryLevelEnd",
        "intensityFactor", "deviceTierMeasured", "tempRiseRateInProbe"
    )

    private val gson = com.google.gson.GsonBuilder().create()

    private fun wireOf(rt: FooterRoundTrip, data: TimeSeriesData?, reason: String? = null): com.google.gson.JsonObject {
        val fp = BehaviorFingerprintBuilder().build(rt.report(), data, noTimeSeriesReason = reason)
        return gson.toJsonTree(fp).asJsonObject
    }

    // ───────────────────────── no file ─────────────────────────

    @Test
    fun `with no time series every header and footer measurement is an absent key`() {
        val wire = wireOf(FooterRoundTrip(tmp.newFolder()), data = null, reason = "disk read error: EIO")
        val ts = wire.getAsJsonObject("timeSeries")
        val header = ts.getAsJsonObject("header")
        val footer = ts.getAsJsonObject("footer")

        for (k in headerKeys + "scanId") assertFalse("header.$k must be absent, not 0 / \"\" / false", header.has(k))
        for (k in footerMeasurementKeys) assertFalse("footer.$k must be absent, not 0f", footer.has(k))
        assertFalse("powerSaveMode must not claim 'off'", wire.has("powerSaveMode"))
        assertEquals("no_file:disk read error: EIO", ts.get("timeSeriesState").asString)
        assertEquals("the one footer field that states its own absence", "absent:no_time_series_file",
            footer.get("cpuTempFinalSource").asString)
        // the exact facts stay
        assertEquals(0, footer.get("totalSamples").asInt)
        assertFalse(footer.get("isComplete").asBoolean)
        assertEquals(0, ts.getAsJsonArray("samples").size())
        assertEquals(0, ts.getAsJsonArray("variables").size())
    }

    @Test
    fun `no time series and no reason still says no_file with the plain fact`() {
        val ts = wireOf(FooterRoundTrip(tmp.newFolder()), data = null).getAsJsonObject("timeSeries")
        assertEquals("no_file:no time series was provided to the payload builder", ts.get("timeSeriesState").asString)
    }

    @Test
    fun `the no_file reason is capped at 200 characters in all`() {
        assertEquals(200, TimeSeriesStates.noFile("q".repeat(900)).length)
        assertTrue(TimeSeriesStates.noFile("q".repeat(900)).startsWith("no_file:"))
    }

    // ───────────────────────── a real file ─────────────────────────

    @Test
    fun `a real file is state file with its header, its power-save flag and its measurements`() {
        val rt = FooterRoundTrip(tmp.newFolder())
        val r = rt.run(rt.baseFooter())

        val ts = r.wire.getAsJsonObject("timeSeries")
        assertEquals("file", ts.get("timeSeriesState").asString)
        val header = ts.getAsJsonObject("header")
        for (k in headerKeys + "scanId") assertTrue("header.$k present for a real file", header.has(k))
        assertEquals(35, header.get("androidApiLevel").asInt)
        assertFalse("a real false is a value", header.get("isPowerSaveMode").asBoolean)
        assertTrue(r.wire.has("powerSaveMode"))
        assertFalse(r.wire.get("powerSaveMode").asBoolean)
        val footer = ts.getAsJsonObject("footer")
        assertTrue(footer.has("maxBatteryTemp"))
        assertTrue(footer.has("batteryLevelEnd"))
    }

    // ───────────────────────── the footer backup ─────────────────────────

    @Test
    fun `a footer recovered from the backup is state footer_backup - its placeholder header is not shipped`() {
        val dir = tmp.newFolder()
        val rt = FooterRoundTrip(dir)
        val context: Context = mock { on { filesDir } doReturn dir }
        // The real recovery path: only the footer backup exists, the main file does not.
        TimeSeriesFileWriter(context).writeFooterBackup("scan-recovered", rt.baseFooter().copy(fieldsVersion = 68))
        val recovered = runBlocking { TimeSeriesFileReader(context).readFile("scan-recovered") }

        assertNotNull(recovered)
        assertTrue("the reader marks its own recovery object", recovered!!.recoveredFromFooterBackup)

        val wire = wireOf(rt, recovered)
        val ts = wire.getAsJsonObject("timeSeries")
        assertEquals("footer_backup", ts.get("timeSeriesState").asString)
        val header = ts.getAsJsonObject("header")
        assertEquals("scan-recovered", header.get("scanId").asString)
        for (k in headerKeys) assertFalse("header.$k is a placeholder in the recovery object, not a reading", header.has(k))
        assertFalse(wire.has("powerSaveMode"))
        // the footer is real
        val footer = ts.getAsJsonObject("footer")
        assertEquals(75, footer.get("batteryLevelEnd").asInt)
        assertEquals(68, footer.get("fieldsVersion").asInt)
    }

    @Test
    fun `the recovery marker is never written to or read from a file`() {
        val rt = FooterRoundTrip(tmp.newFolder())
        val r = rt.run(rt.baseFooter())
        assertFalse(r.data.recoveredFromFooterBackup)

        val raw = java.util.zip.GZIPInputStream(java.io.FileInputStream(findWrittenFile()))
            .bufferedReader().readText()
        assertFalse("@Transient: no such key in the file", raw.contains("recoveredFromFooterBackup"))
    }

    private fun findWrittenFile(): java.io.File =
        tmp.root.walkTopDown().first { it.name.endsWith(".ts.json.gz") }
}
