package net.neuralcheck.service.collector

import android.content.Intent
import android.os.BatteryManager
import net.neuralcheck.data.local.files.BatteryPair
import net.neuralcheck.data.local.files.BatteryPairsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * fieldsVersion 68 (ت-06, N-05, S-10, S-28 corrected, order 2026-10-08): the raw
 * battery pairs. Raw = as the platform returned it ("voltageRaw, and levelRaw
 * with levelScale, and the server infers the unit"); capped at the first 200
 * with a count of what was dropped; the absence of data is an explicit state.
 */
class BatteryPairRecorderTest {

    private fun full(v: Int = 4_350, c: Int = -812, cc: Long = 1_711_600L, l: Int = 72, s: Int = 100, t: Int = 305) =
        BatteryPairInputs(v, c, cc, l, s, t)

    private fun intent(
        voltage: Int? = 4_350, level: Int? = 72, scale: Int? = 100, temp: Int? = 305
    ): Intent = mock {
        fun put(key: String, v: Int?) {
            on { hasExtra(key) } doReturn (v != null)
            if (v != null) on { getIntExtra(key, Int.MIN_VALUE) } doReturn v
        }
        put(BatteryManager.EXTRA_VOLTAGE, voltage)
        put(BatteryManager.EXTRA_LEVEL, level)
        put(BatteryManager.EXTRA_SCALE, scale)
        put(BatteryManager.EXTRA_TEMPERATURE, temp)
    }

    // ───────────────────────── the recorder ─────────────────────────

    @Test
    fun `a broadcast becomes a pair with t relative to the scan start`() {
        val r = BatteryPairRecorder()
        r.start(scanStartElapsedMs = 50_000L)
        r.onBroadcast(50_000L + 3_250L, full())

        val rec = r.finish(receiverRegistered = true)!!
        assertEquals(
            listOf(BatteryPair(3_250L, 4_350, -812, 1_711_600L, 72, 100, 305)),
            rec.pairs
        )
        assertEquals(BatteryPairsState(true, 1, 0, 0), rec.state)
    }

    @Test
    fun `the list keeps the FIRST 200 and the rest is counted, never picked`() {
        val r = BatteryPairRecorder()
        r.start(0L)
        for (i in 0 until 230) r.onBroadcast(i * 1_000L, full(v = 4_000 + i))

        val rec = r.finish(true)!!
        assertEquals(200, rec.pairs.size)
        assertEquals("the first one is kept", 4_000, rec.pairs.first().voltageRaw)
        assertEquals("the 200th is the last kept", 4_199, rec.pairs.last().voltageRaw)
        assertEquals(230, rec.state.broadcastCount)
        assertEquals(30, rec.state.droppedByCap)
        assertEquals("invariant: every broadcast is kept or dropped",
            rec.state.broadcastCount, rec.pairs.size + rec.state.droppedByCap)
    }

    @Test
    fun `invalid reads are counted per field, kept pairs omit the field and dropped ones still count`() {
        val r = BatteryPairRecorder(cap = 1)
        r.start(0L)
        r.onBroadcast(1_000L, BatteryPairInputs(4_350, null, 1L, 72, 100, 305))   // 1 invalid, kept
        r.onBroadcast(2_000L, BatteryPairInputs(null, null, null, 72, 100, 305))  // 3 invalid, dropped

        val rec = r.finish(true)!!
        assertNull(rec.pairs.single().currentRaw)
        assertEquals(4_350, rec.pairs.single().voltageRaw)
        assertEquals(4, rec.state.invalidReads)
        assertEquals(1, rec.state.droppedByCap)
    }

    @Test
    fun `a receiver that was never registered is told apart from one that saw no broadcast`() {
        val notRegistered = BatteryPairRecorder().apply { start(0L) }.finish(receiverRegistered = false)!!
        val quiet = BatteryPairRecorder().apply { start(0L) }.finish(receiverRegistered = true)!!

        assertTrue(notRegistered.pairs.isEmpty() && quiet.pairs.isEmpty())
        assertEquals(BatteryPairsState(false, 0, 0, 0), notRegistered.state)
        assertEquals(BatteryPairsState(true, 0, 0, 0), quiet.state)
    }

    @Test
    fun `nothing is recorded before start or after finish, and finish without start claims nothing`() {
        val r = BatteryPairRecorder()
        r.onBroadcast(1L, full())
        assertNull("never started: no record at all, not an empty one", r.finish(true))

        r.start(0L)
        r.onBroadcast(1_000L, full())
        assertNotNull(r.finish(true))
        r.onBroadcast(2_000L, full())
        assertNull("after finish the recording is closed", r.finish(true))
    }

    @Test
    fun `a new scan starts clean`() {
        val r = BatteryPairRecorder()
        r.start(0L)
        r.onBroadcast(1_000L, full())
        r.finish(true)

        r.start(100_000L)
        r.onBroadcast(101_500L, full(v = 3_900))
        val rec = r.finish(true)!!
        assertEquals(1, rec.pairs.size)
        assertEquals(1_500L, rec.pairs.single().t)
        assertEquals(1, rec.state.broadcastCount)
    }

    // ───────────────────────── the raw reads ─────────────────────────

    @Test
    fun `values are forwarded exactly as the platform gave them - no unit conversion, no flip, no percentage`() {
        // A ColorOS-style microvolt voltage, a mA-format negative current, a level/scale pair
        // whose integer percentage (72*100/100) would hide the scale.
        val inputs = BatteryPairReads.fromIntent(
            intent = intent(voltage = 4_350_000, level = 72, scale = 100, temp = 305),
            readCurrentNow = { -812 },
            readChargeCounter = { 5_596L }
        )

        assertEquals("not divided by 1000 (the sanitiser's µV guess is not applied)", 4_350_000, inputs.voltageRaw)
        assertEquals("not scaled, not sign-flipped", -812, inputs.currentRaw)
        assertEquals(5_596L, inputs.chargeCounterRaw)
        assertEquals(72, inputs.levelRaw)
        assertEquals(100, inputs.levelScale)
        assertEquals("tenths of a degree, as stated", 305, inputs.tempRaw)
        assertEquals(0, inputs.invalidCount)
    }

    @Test
    fun `a real zero is a value - not a missing read`() {
        val inputs = BatteryPairReads.fromIntent(
            intent = intent(),
            readCurrentNow = { 0 },
            readChargeCounter = { 0L }
        )
        assertEquals(0, inputs.currentRaw)
        assertEquals(0L, inputs.chargeCounterRaw)
        assertEquals(0, inputs.invalidCount)
    }

    @Test
    fun `the platform failure markers are never written as numbers`() {
        val inputs = BatteryPairReads.fromIntent(
            intent = intent(),
            readCurrentNow = { Int.MIN_VALUE },
            readChargeCounter = { Long.MIN_VALUE }
        )
        assertNull(inputs.currentRaw)
        assertNull(inputs.chargeCounterRaw)
        assertEquals(2, inputs.invalidCount)
    }

    @Test
    fun `a property read that throws is a missing field, not an exception out of the receiver`() {
        val inputs = BatteryPairReads.fromIntent(
            intent = intent(),
            readCurrentNow = { throw SecurityException("no BATTERY_STATS") },
            readChargeCounter = { throw IllegalStateException("service died") }
        )
        assertNull(inputs.currentRaw)
        assertNull(inputs.chargeCounterRaw)
        assertEquals(4_350, inputs.voltageRaw)
        assertEquals(2, inputs.invalidCount)
    }

    @Test
    fun `an extra the intent does not carry is missing, not a default reading`() {
        val inputs = BatteryPairReads.fromIntent(
            intent = intent(voltage = null, level = null, scale = null, temp = null),
            readCurrentNow = { 5 },
            readChargeCounter = { 6L }
        )
        assertNull(inputs.voltageRaw)
        assertNull(inputs.levelRaw)
        assertNull(inputs.levelScale)
        assertNull(inputs.tempRaw)
        assertEquals(4, inputs.invalidCount)
        assertFalse("a missing level must not read as 0 percent", inputs.levelRaw == 0)
    }
}
