package net.neuralcheck.files

import net.neuralcheck.data.local.files.BatteryWindowPctFooter
import net.neuralcheck.data.local.files.BrightnessLockFooter
import net.neuralcheck.data.local.files.FileShape
import net.neuralcheck.service.stress.BrightnessLock
import net.neuralcheck.service.stress.PowerSaveReading
import net.neuralcheck.service.stress.ScanBatteryWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * fieldsVersion 71, row A-P6-13 (contract docs/FV68_CONTRACT.md section 7-ي ف-05, N71-06..08): the real chain — the real writer, reader and builder, the production serialization
 * ([FooterRoundTrip]) — carries `powerSaveState` (header and root, beside the boolean a pre-71 reader reads), `batteryWindowPct` and `brightnessLock` (footer), and names them in
 * `fieldsNotInFile` for a file stamped below 71. The values come from the real producers ([PowerSaveReading], [ScanBatteryWindow], [BrightnessLock]).
 */
class ConditionsWireTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun rt() = FooterRoundTrip(tmp.newFolder())

    private fun footer71(rt: FooterRoundTrip, brightness: BrightnessLock.Result = BrightnessLock.lock(null) { }) = rt.baseFooter().copy(
        fieldsVersion = 71,
        batteryWindowPct = BatteryWindowPctFooter(ScanBatteryWindow.MIN_PCT, ScanBatteryWindow.MAX_PCT),
        brightnessLock = BrightnessLockFooter(brightness.state, brightness.value, brightness.method)
    )

    // ───────────────────────── powerSaveState ─────────────────────────

    @Test
    fun `off is false and off - in the file header, the payload header and the payload root`() {
        val rt = rt()
        val ps = PowerSaveReading.read { false }
        val r = rt.run(footer71(rt), isPowerSaveMode = ps.isPowerSaveMode, powerSaveState = ps.state)
        assertEquals("off", r.data.header.powerSaveState)
        val header = r.wire.getAsJsonObject("timeSeries").getAsJsonObject("header")
        assertEquals("off", header.get("powerSaveState").asString)
        assertFalse("a real false is a value", header.get("isPowerSaveMode").asBoolean)
        assertEquals("off", r.wire.get("powerSaveState").asString)
        assertFalse(r.wire.get("powerSaveMode").asBoolean)
    }

    @Test
    fun `on is true and on - the shape a build that let a power-saving scan through would send`() {
        val rt = rt()
        val ps = PowerSaveReading.read { true }
        val r = rt.run(footer71(rt), isPowerSaveMode = ps.isPowerSaveMode, powerSaveState = ps.state)
        val header = r.wire.getAsJsonObject("timeSeries").getAsJsonObject("header")
        assertEquals("on", header.get("powerSaveState").asString)
        assertTrue(header.get("isPowerSaveMode").asBoolean)
        assertTrue(r.wire.get("powerSaveMode").asBoolean)
    }

    @Test
    fun `read_failed is the state and NO boolean - not false - in the header and at the root (rule 22)`() {
        val rt = rt()
        val ps = PowerSaveReading.read { throw SecurityException("no permission to ask") }
        val r = rt.run(footer71(rt), isPowerSaveMode = ps.isPowerSaveMode, powerSaveState = ps.state)
        assertNull("absent in the file", r.data.header.isPowerSaveMode)
        val header = r.wire.getAsJsonObject("timeSeries").getAsJsonObject("header")
        assertEquals("read_failed", header.get("powerSaveState").asString)
        assertFalse("not written as false", header.has("isPowerSaveMode"))
        assertEquals("read_failed", r.wire.get("powerSaveState").asString)
        assertFalse("not written as false", r.wire.has("powerSaveMode"))
    }

    @Test
    fun `a header written before 71 has no powerSaveState - the key is absent and the old boolean is what a reader reads`() {
        val rt = rt()
        val r = rt.run(rt.baseFooter(), isPowerSaveMode = false, powerSaveState = null)
        val header = r.wire.getAsJsonObject("timeSeries").getAsJsonObject("header")
        assertFalse(header.has("powerSaveState"))
        assertFalse(r.wire.has("powerSaveState"))
        assertFalse(header.get("isPowerSaveMode").asBoolean)
    }

    // ───────────────────────── batteryWindowPct and brightnessLock ─────────────────────────

    @Test
    fun `the window the build applied is declared in the footer - 30 and 85 from the one object`() {
        val rt = rt()
        val r = rt.run(footer71(rt))
        val w = r.wireFooter.getAsJsonObject("batteryWindowPct")
        assertEquals(setOf("minPct", "maxPct"), w.keySet())
        assertEquals(30, w.get("minPct").asInt)
        assertEquals(85, w.get("maxPct").asInt)
        assertEquals(ScanBatteryWindow.MIN_PCT, r.data.footer.batteryWindowPct!!.minPct)
    }

    @Test
    fun `brightnessLock - not attempted has a state alone, applied adds its value and method, apply_failed keeps both - the real lock produced each`() {
        val rt = rt()
        val none = rt.run(footer71(rt, BrightnessLock.lock(null) { })).wireFooter.getAsJsonObject("brightnessLock")
        assertEquals(setOf("state"), none.keySet())
        assertEquals("not_attempted:value_not_decided", none.get("state").asString)

        val applied = rt.run(footer71(rt, BrightnessLock.lock(0.5f) { })).wireFooter.getAsJsonObject("brightnessLock")
        assertEquals(setOf("state", "value", "method"), applied.keySet())
        assertEquals("applied", applied.get("state").asString)
        assertEquals(0.5f, applied.get("value").asFloat, 0f)
        assertEquals("window_attribute", applied.get("method").asString)

        val failed = rt.run(footer71(rt, BrightnessLock.lock(0.4f) { throw IllegalStateException("the window refused") })).wireFooter.getAsJsonObject("brightnessLock")
        assertEquals("apply_failed:the window refused", failed.get("state").asString)
        assertEquals(0.4f, failed.get("value").asFloat, 0f)
        assertEquals("window_attribute", failed.get("method").asString)
    }

    // ───────────────────────── a file stamped below 71 ─────────────────────────

    @Test
    fun `a file stamped below 71 has neither key - they are absent and fieldsNotInFile names them`() {
        val rt = rt()
        val r = rt.run(rt.baseFooter().copy(fieldsVersion = 70))
        assertFalse(r.wireFooter.has("batteryWindowPct"))
        assertFalse(r.wireFooter.has("brightnessLock"))
        val named = r.wireFooter.getAsJsonArray("fieldsNotInFile").map { it.asString }
        for (key in FileShape.FOOTER_FIELDS_SINCE_71) assertTrue("$key must be named", key in named)
        assertEquals(71, FileShape.WINDOW_STAMP)
        assertEquals(listOf("batteryWindowPct", "brightnessLock"), FileShape.FOOTER_FIELDS_SINCE_71)
    }

    @Test
    fun `a file stamped 71 that carries them does not name them as missing`() {
        val rt = rt()
        val named = rt.run(footer71(rt)).wireFooter.getAsJsonArray("fieldsNotInFile").map { it.asString }
        assertFalse("batteryWindowPct" in named)
        assertFalse("brightnessLock" in named)
    }
}
