package net.neuralcheck.service.stress

import net.neuralcheck.service.stress.ScanBatteryWindow.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * fieldsVersion 71, row A-P6-13 (توحيد الظروف), contract docs/FV68_CONTRACT.md section 7-ي ف-05 (N71-06..08, approved 2026-10-15): the three decisions the app makes about the
 * scan's conditions, as pure functions — the battery window, the power-saving state, the brightness lock — and where they are read (the one-source checks, rule 24).
 */
class ConditionsUnificationTest {

    // ───────────────────────── the battery window ─────────────────────────

    @Test
    fun `the window is 30 to 85 - a known level outside it is refused, with the side named`() {
        assertEquals(30, ScanBatteryWindow.MIN_PCT)
        assertEquals(85, ScanBatteryWindow.MAX_PCT)
        for (level in 0..29) assertEquals("$level", Position.BELOW, ScanBatteryWindow.positionOf(level))
        for (level in 30..85) assertEquals("$level", Position.WITHIN, ScanBatteryWindow.positionOf(level))
        for (level in 86..100) assertEquals("$level", Position.ABOVE, ScanBatteryWindow.positionOf(level))
        for (level in 0..100) assertEquals("$level", level < 30 || level > 85, ScanBatteryWindow.refuses(level))
    }

    @Test
    fun `an unknown or implausible level is UNKNOWN and passes - as the gate always did`() {
        for (level in listOf(null, -1, 101, 1_000, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals("$level", Position.UNKNOWN, ScanBatteryWindow.positionOf(level))
            assertFalse("$level", ScanBatteryWindow.refuses(level))
        }
    }

    @Test
    fun `the refusal tokens are the existing one below and BATTERY_TOO_HIGH above - and the wire form of the window is minPct and maxPct`() {
        assertEquals("BATTERY_TOO_LOW", ScanBatteryWindow.REFUSAL_BELOW)
        assertEquals("BATTERY_TOO_HIGH", ScanBatteryWindow.REFUSAL_ABOVE)
        assertEquals(linkedMapOf<String, Any?>("minPct" to 30, "maxPct" to 85), ScanBatteryWindow.applied())
        assertEquals(10, ScanBatteryWindow.YELLOW_BAND_POINTS)
    }

    private val mainSources: File by lazy {
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory } ?: error("cannot find app/src/main/java from ${File(".").absolutePath}")
    }

    private fun read(relative: String) = File(mainSources, relative).readText()

    @Test
    fun `the window replaces the lone floor in its four places - the gate, the pre-scan bands, the quoted number - and the old name is gone`() {
        val service = read("net/neuralcheck/service/stress/StressTestService.kt")
        val preScan = read("net/neuralcheck/presentation/viewmodel/PreScanViewModel.kt")
        val active = read("net/neuralcheck/presentation/screen/ActiveScanScreen.kt")
        // the old constant is not DECLARED or READ anywhere; its name survives only inside comments
        for ((name, text) in mapOf("StressTestService" to service, "PreScanViewModel" to preScan, "ActiveScanScreen" to active)) {
            val code = text.lines().joinToString("\n") { it.substringBefore("//") }.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            assertFalse("$name still uses PRE_FLIGHT_MIN_BATTERY_PCT", code.contains("PRE_FLIGHT_MIN_BATTERY_PCT"))
        }
        assertTrue("the gate reads the window", service.contains("ScanBatteryWindow.refuses(levelPct)"))
        assertTrue("the pre-scan bands read the window", preScan.contains("ScanBatteryWindow.MIN_PCT") && preScan.contains("ScanBatteryWindow.MAX_PCT"))
        assertTrue("the refusal dialog quotes the window's minimum", active.contains("val minBatteryPct = net.neuralcheck.service.stress.ScanBatteryWindow.MIN_PCT"))
    }

    // ───────────────────────── the power-saving state ─────────────────────────

    @Test
    fun `power save - true is on and stops the scan, false is off, and the boolean follows the state`() {
        val on = PowerSaveReading.read { true }
        assertEquals("on", on.state); assertEquals(true, on.isPowerSaveMode); assertTrue(on.stopsScan)
        val off = PowerSaveReading.read { false }
        assertEquals("off", off.state); assertEquals(false, off.isPowerSaveMode); assertFalse(off.stopsScan)
    }

    @Test
    fun `power save - a failed read is read_failed with NO boolean, never false - and it does not stop the scan`() {
        val noService = PowerSaveReading.read { null }
        assertEquals("read_failed", noService.state)
        assertNull("an absence is not 'off' (rule 22)", noService.isPowerSaveMode)
        assertFalse(noService.stopsScan)
        val threw = PowerSaveReading.read { throw SecurityException("no permission to ask") }
        assertEquals("read_failed", threw.state)
        assertNull(threw.isPowerSaveMode)
        assertEquals("the message text is kept for the log, not the class (rule 21)", "no permission to ask", threw.failure)
        assertEquals("exception without message", PowerSaveReading.read { throw RuntimeException() }.failure)
        assertEquals(listOf("off", "on", "read_failed"), PowerSaveReading.ALL)
    }

    // ───────────────────────── the brightness lock ─────────────────────────

    @Test
    fun `no brightness is decided today - the lock attempts nothing and says why`() {
        assertNull("PENDING: the value is nowhere in the sources", BrightnessLock.VALUE)
        var asked = false
        val r = BrightnessLock.lock(BrightnessLock.VALUE) { asked = true }
        assertFalse("the window was not touched", asked)
        assertEquals("not_attempted:value_not_decided", r.state)
        assertNull(r.value); assertNull(r.method)
        assertEquals("value and method are ABSENT keys when nothing was attempted", linkedMapOf<String, Any?>("state" to "not_attempted:value_not_decided"), r.toWire())
    }

    @Test
    fun `a decided brightness is applied through the window attribute and reported with its value and method`() {
        var set: Float? = null
        val r = BrightnessLock.lock(0.5f) { set = it }
        assertEquals(0.5f, set)
        assertEquals("applied", r.state)
        assertEquals(linkedMapOf<String, Any?>("state" to "applied", "value" to 0.5f, "method" to "window_attribute"), r.toWire())
    }

    @Test
    fun `a window that refuses is apply_failed with the message text - and the value and method that were tried`() {
        val r = BrightnessLock.lock(0.4f) { throw IllegalStateException("the window refused the brightness attribute") }
        assertEquals("apply_failed:the window refused the brightness attribute", r.state)
        assertEquals(0.4f, r.value)
        assertEquals("window_attribute", r.method)
        val long = BrightnessLock.lock(0.4f) { throw IllegalStateException("q".repeat(500)) }
        assertEquals("apply_failed:".length + 200, long.state.length)
    }

    @Test
    fun `a brightness outside 0 to 1 is refused loudly - and the lists of states and methods are the contract's`() {
        assertThrows(IllegalArgumentException::class.java) { BrightnessLock.lock(1.5f) { } }
        assertThrows(IllegalArgumentException::class.java) { BrightnessLock.lock(-0.1f) { } }
        assertEquals(listOf("window_attribute", "system_setting"), BrightnessLock.Methods.ALL)
        assertEquals("applied", BrightnessLock.States.APPLIED)
        assertEquals(listOf("state", "value", "method"), listOf(BrightnessLock.Keys.STATE, BrightnessLock.Keys.VALUE, BrightnessLock.Keys.METHOD))
    }

    @Test
    fun `the holder hands the report to the footer once - and a scan with no scan screen says no_scan_screen_reported`() {
        val holder = BrightnessLockHolder()
        assertEquals("nothing reported", "not_attempted:no_scan_screen_reported", holder.readAndClear().state)
        holder.report(BrightnessLock.Result("applied", 0.5f, "window_attribute"))
        val first = holder.readAndClear()
        assertEquals("applied", first.state)
        assertEquals("drained: the next scan does not inherit it", "not_attempted:no_scan_screen_reported", holder.readAndClear().state)
    }

    @Test
    fun `the scan screen holds the brightness through the effect and reports to the holder - the footer reads the holder once`() {
        val active = read("net/neuralcheck/presentation/screen/ActiveScanScreen.kt")
        assertTrue(active.contains("BrightnessLockEffect { brightnessLockViewModel.report(it) }"))
        val effect = read("net/neuralcheck/presentation/util/BrightnessLockEffect.kt")
        assertTrue("the effect asks BrightnessLock, with its one value", effect.contains("BrightnessLock.lock(BrightnessLock.VALUE)"))
        val service = read("net/neuralcheck/service/stress/StressTestService.kt")
        assertEquals("the footer drains the holder in one place", 1, Regex("""brightnessLockHolder\.readAndClear\(\)""").findAll(service).count())
        assertTrue("and writes what it got", service.contains("brightnessLockResult.state, brightnessLockResult.value, brightnessLockResult.method"))
        assertTrue(service.contains("PowerSaveReading.read { powerManager?.isPowerSaveMode }"))
        assertTrue("only 'on' stops the scan", service.contains("if (powerSave.stopsScan) {"))
    }
}
