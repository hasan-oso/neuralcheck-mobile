package net.neuralcheck.service.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * fieldsVersion 68 (ت-06): the REAL [BatteryReader] with its real receiver — the
 * glue between a broadcast and the recorder — so the two contract constraints
 * that live in that glue are tested where they live (rule 37), not by replacing
 * the layer: the broadcast-time reads must NOT feed the scaling-lock evidence
 * counters nor `invalidCurrentReadings`, and the receiver must not throw.
 */
class BatteryReaderPairsTest {

    private val bm: BatteryManager = mock {
        on { getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } doReturn 123_456 // decisive µA-sized value
        on { getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) } doReturn 1_711_600L
    }

    private val context: Context = mock {
        on { getSystemService(Context.BATTERY_SERVICE) } doReturn bm
    }

    private fun intent(): Intent = mock {
        on { hasExtra(BatteryManager.EXTRA_VOLTAGE) } doReturn true
        on { getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE) } doReturn 4_350
        on { hasExtra(BatteryManager.EXTRA_LEVEL) } doReturn true
        on { getIntExtra(BatteryManager.EXTRA_LEVEL, Int.MIN_VALUE) } doReturn 72
        on { hasExtra(BatteryManager.EXTRA_SCALE) } doReturn true
        on { getIntExtra(BatteryManager.EXTRA_SCALE, Int.MIN_VALUE) } doReturn 100
        on { hasExtra(BatteryManager.EXTRA_TEMPERATURE) } doReturn true
        on { getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) } doReturn 305
    }

    /** A registered reader and the receiver it handed to the platform. */
    private fun registered(): Pair<BatteryReader, BroadcastReceiver> {
        val reader = BatteryReader(context)
        assertTrue(reader.register())
        val captor = argumentCaptor<BroadcastReceiver>()
        verify(context).registerReceiver(captor.capture(), any())
        return reader to captor.firstValue
    }

    @Test
    fun `a broadcast during a scan is recorded raw`() {
        val (reader, receiver) = registered()
        reader.startBatteryPairs(scanStartElapsedMs = 0L)

        receiver.onReceive(context, intent())

        val rec = reader.finishBatteryPairs()!!
        val p = rec.pairs.single()
        assertEquals(123_456, p.currentRaw)
        assertEquals(1_711_600L, p.chargeCounterRaw)
        assertEquals(4_350, p.voltageRaw)
        assertEquals(72, p.levelRaw)
        assertEquals(100, p.levelScale)
        assertEquals(305, p.tempRaw)
        assertTrue("registered when the recording ended", rec.state.receiverRegistered)
        assertEquals(1, rec.state.broadcastCount)
    }

    @Test
    fun `the broadcast-time reads do not feed the scaling-lock counters nor invalidCurrentReadings`() {
        val (reader, receiver) = registered()
        reader.startBatteryPairs(0L)
        val before = reader.getScalingDiagnostic()

        // three decisive µA-sized readings would LOCK the scale if they fed the evidence counters
        repeat(3) { receiver.onReceive(context, intent()) }
        // and a failure marker would count as an invalid reading if it fed that counter
        org.mockito.kotlin.whenever(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)).thenReturn(Int.MIN_VALUE)
        receiver.onReceive(context, intent())

        val after = reader.getScalingDiagnostic()
        assertEquals(before.uaEvidenceCount, after.uaEvidenceCount)
        assertEquals(before.maEvidenceCount, after.maEvidenceCount)
        assertEquals(before.sampleCount, after.sampleCount)
        assertEquals(before.invalidReadingCount, after.invalidReadingCount)
        assertFalse("the lock must not have been committed by broadcast reads", after.locked)
        assertEquals(before.source, after.source)
        // ...while the recorder did see all four, the last with its current missing
        val rec = reader.finishBatteryPairs()!!
        assertEquals(4, rec.state.broadcastCount)
        assertNull(rec.pairs.last().currentRaw)
        assertEquals(1, rec.state.invalidReads)
    }

    @Test
    fun `a broadcast before the scan started is not recorded`() {
        val (reader, receiver) = registered()
        receiver.onReceive(context, intent())
        assertNull(reader.finishBatteryPairs())
    }

    @Test
    fun `the receiver never throws, whatever the platform does`() {
        val (reader, receiver) = registered()
        reader.startBatteryPairs(0L)
        org.mockito.kotlin.whenever(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
            .thenThrow(RuntimeException("binder transaction failed"))
        org.mockito.kotlin.whenever(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
            .thenThrow(RuntimeException("binder transaction failed"))

        receiver.onReceive(context, intent())

        val rec = reader.finishBatteryPairs()!!
        assertEquals(1, rec.state.broadcastCount)
        assertNull(rec.pairs.single().currentRaw)
        assertNull(rec.pairs.single().chargeCounterRaw)
        assertEquals(2, rec.state.invalidReads)
    }

    @Test
    fun `a reader whose register failed reports the receiver as not registered`() {
        val failing: Context = mock {
            on { getSystemService(Context.BATTERY_SERVICE) } doReturn bm
            on { registerReceiver(any<BroadcastReceiver>(), any()) } doThrow SecurityException("receiver not allowed")
        }
        val reader = BatteryReader(failing)
        assertFalse(reader.register())
        reader.startBatteryPairs(0L)

        val rec = reader.finishBatteryPairs()
        assertNotNull(rec)
        assertFalse(rec!!.state.receiverRegistered)
        assertEquals("the empty list is explained by the state, not by a quiet battery", 0, rec.state.broadcastCount)
    }
}
