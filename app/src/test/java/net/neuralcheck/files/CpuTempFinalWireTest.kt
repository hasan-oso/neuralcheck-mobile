package net.neuralcheck.files

import android.content.Context
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * fieldsVersion 68 (T-10, S-13, S-33, N-08; order 2026-10-08): the footer's CPU
 * temperature is ONE field whose SOURCE is declared — `cpuTempFinalC` +
 * `cpuTempFinalSource` replace `maxCpuTemp`, which said "max" for a last live
 * reading and shipped -1f for "no reading". Through the real writer -> reader ->
 * builder -> Gson chain, plus the one case the replacement must not break: an
 * OLD (≤ 67) file that still carries `maxCpuTemp` and is read again.
 */
class CpuTempFinalWireTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun filesJson(path: String): String =
        GZIPInputStream(FileInputStream(path)).bufferedReader().readText()

    @Test
    fun `a live read is forwarded with its source and the retired key is gone from the wire and the file`() {
        val dir = tmp.newFolder()
        val rt = FooterRoundTrip(dir)
        val r = rt.run(rt.baseFooter().copy(cpuTempFinalC = 43.6f, cpuTempFinalSource = "live_read_at_finalize"))

        assertEquals(43.6f, r.wireFooter.get("cpuTempFinalC").asFloat, 0f)
        assertEquals("live_read_at_finalize", r.wireFooter.get("cpuTempFinalSource").asString)
        assertFalse("maxCpuTemp leaves the payload in >= 68", r.wireFooter.has("maxCpuTemp"))

        val written = dir.walkTopDown().first { it.name.endsWith(".ts.json.gz") }
        assertFalse("and the file this build writes does not carry the key either (not even as null)",
            filesJson(written.absolutePath).contains("maxCpuTemp"))
    }

    @Test
    fun `no reading is an absent value and a source that says why - never -1`() {
        val reason = "absent:no live CPU read (readCpuTemp returned -1.0, not > 0) and the sampler's temperature source was 'battery', not 'cpu'"
        val rt = FooterRoundTrip(tmp.newFolder())
        val r = rt.run(rt.baseFooter().copy(cpuTempFinalC = null, cpuTempFinalSource = reason))

        assertNull(r.data.footer.cpuTempFinalC)
        assertFalse("a null is an absent key, not -1 and not 0", r.wireFooter.has("cpuTempFinalC"))
        assertEquals(reason, r.wireFooter.get("cpuTempFinalSource").asString)
        assertFalse(r.wireFooter.has("maxCpuTemp"))
    }

    @Test
    fun `a sampler maximum says so`() {
        val rt = FooterRoundTrip(tmp.newFolder())
        val r = rt.run(rt.baseFooter().copy(cpuTempFinalC = 81f, cpuTempFinalSource = "sampler_max_cpu"))
        assertEquals("sampler_max_cpu", r.wireFooter.get("cpuTempFinalSource").asString)
        assertEquals(81f, r.wireFooter.get("cpuTempFinalC").asFloat, 0f)
    }

    @Test
    fun `an old pre-68 file still read forwards its maxCpuTemp and invents no new fields`() {
        // A fixture DERIVED from a file this build's writer produced (so every footer key a
        // real file has is there), edited to be what a <= 67 build wrote: the retired key
        // present with 55.5, fieldsVersion 67, no cpuTempFinal* keys. (Edits: footer.maxCpuTemp
        // added, footer.cpuTempFinalC / cpuTempFinalSource removed, footer.fieldsVersion = 67,
        // header.scanId = old-scan.)
        val dir = tmp.newFolder()
        val context: Context = mock { on { filesDir } doReturn dir }
        FooterRoundTrip(dir).run(
            FooterRoundTrip(dir).baseFooter().copy(cpuTempFinalC = null, cpuTempFinalSource = "absent:fixture"),
            scanId = "old-scan"
        )
        val written = File(dir, "timeseries/old-scan.ts.json.gz")
        val doc = com.google.gson.JsonParser.parseString(filesJson(written.absolutePath)).asJsonObject
        val footer = doc.getAsJsonObject("footer")
        footer.addProperty("maxCpuTemp", 55.5f)
        footer.remove("cpuTempFinalC")
        footer.remove("cpuTempFinalSource")
        footer.addProperty("fieldsVersion", 67)
        GZIPOutputStream(written.outputStream()).use { it.write(doc.toString().toByteArray()) }

        val data = runBlocking { TimeSeriesFileReader(context).readFile("old-scan") }!!
        assertEquals("the legacy value survives the read", 55.5f, data.footer.maxCpuTemp!!, 0f)
        assertNull(data.footer.cpuTempFinalC)
        assertNull("a >= 68 field is not made up for an old file", data.footer.cpuTempFinalSource)

        val wire = FooterRoundTrip(tmp.newFolder()).wireGson.toJsonTree(
            BehaviorFingerprintBuilder().build(FooterRoundTrip(tmp.newFolder()).report("old-scan"), data)
        ).asJsonObject.getAsJsonObject("timeSeries").getAsJsonObject("footer")

        assertEquals("the server reads this key for <= 67", 55.5f, wire.get("maxCpuTemp").asFloat, 0f)
        assertFalse(wire.has("cpuTempFinalC"))
        assertFalse(wire.has("cpuTempFinalSource"))
        assertEquals(67, wire.get("fieldsVersion").asInt)
        assertTrue(wire.has("batteryLevelEnd"))
    }
}
