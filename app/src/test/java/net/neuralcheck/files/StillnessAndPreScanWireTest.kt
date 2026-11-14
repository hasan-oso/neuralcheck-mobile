package net.neuralcheck.files

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import net.neuralcheck.data.local.files.PreScanWarningsRead
import net.neuralcheck.data.local.files.PreScanWarningsStates
import net.neuralcheck.data.local.files.StillnessReport
import net.neuralcheck.service.collector.SensorCollector
import net.neuralcheck.service.collector.SensorSnapshot
import net.neuralcheck.service.sampler.SamplerStats
import net.neuralcheck.service.sampler.StillnessClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * fieldsVersion 68 (ت-18 rows 6-7, N-13, N-14): `scanStillness` and
 * `preScanWarnings` stop reading absence as the benign answer.
 *
 *  - Stillness: fewer than 2 accelerometer READINGS no longer writes
 *    `isStill = true` beside `accelStdDev = -1`; an accelerometer that never
 *    delivered no longer feeds zeros (= a perfectly still device) into the
 *    statistic.
 *  - Pre-scan warnings: a failed read no longer writes `[]` (= "clean").
 */
class StillnessAndPreScanWireTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun rt() = FooterRoundTrip(tmp.newFolder())

    // ───────────────────────── the wire ─────────────────────────

    @Test
    fun `insufficient samples are absent keys plus a state - in the file and on the wire`() {
        val rt = rt()
        val r = rt.run(
            rt.baseFooter().copy(scanStillness = StillnessClassifier.report(accelStdDev = null, sampleCount = 1))
        )

        assertNull(r.data.footer.scanStillness!!.isStill)
        assertNull(r.data.footer.scanStillness!!.accelStdDev)
        val wire = r.wireFooter.getAsJsonObject("scanStillness")
        assertFalse("not 'still'", wire.has("isStill"))
        assertFalse("not -1", wire.has("accelStdDev"))
        assertEquals(1, wire.get("sampleCount").asInt)
        assertEquals("insufficient_accel_samples", wire.get("stillnessState").asString)
    }

    @Test
    fun `a measured stillness carries its values and the state measured`() {
        val rt = rt()
        val r = rt.run(
            rt.baseFooter().copy(scanStillness = StillnessReport(true, 0.3f, 1_600, "measured"))
        )
        val wire = r.wireFooter.getAsJsonObject("scanStillness")
        assertTrue(wire.get("isStill").asBoolean)
        assertEquals(0.3f, wire.get("accelStdDev").asFloat, 0f)
        assertEquals(1_600, wire.get("sampleCount").asInt)
        assertEquals("measured", wire.get("stillnessState").asString)
    }

    @Test
    fun `a failed pre-scan read is a null list and read_failed with the message, not an empty list`() {
        val failed = PreScanWarningsStates.of(Result.failure(IllegalStateException("holder was cleared concurrently")))
        assertEquals("read_failed:holder was cleared concurrently", failed)
        assertFalse(failed.contains("IllegalStateException"))

        val rt = rt()
        val r = rt.run(rt.baseFooter().copy(preScanWarnings = null, preScanWarningsState = failed))

        assertNull("null in the file, not []", r.data.footer.preScanWarnings)
        assertEquals(failed, r.data.footer.preScanWarningsState)
        assertEquals(failed, r.wireFooter.get("preScanWarningsState").asString)
    }

    @Test
    fun `a successful pre-scan read keeps the list - an empty one is a clean check`() {
        val rt = rt()
        // written = true with [] : the pre-scan check ran and placed no warning
        val ok = PreScanWarningsStates.of(Result.success(PreScanWarningsRead(written = true, warnings = emptyList())))
        assertEquals("read", ok)

        val r = rt.run(rt.baseFooter().copy(preScanWarnings = emptyList(), preScanWarningsState = ok))

        assertNotNull(r.data.footer.preScanWarnings)
        assertTrue(r.data.footer.preScanWarnings!!.isEmpty())
        assertEquals("read", r.wireFooter.get("preScanWarningsState").asString)
    }

    @Test
    fun `nothing written to the holder is not_written and a null list - not read with an empty list`() {
        val state = PreScanWarningsStates.of(Result.success(PreScanWarningsRead(written = false, warnings = emptyList())))
        assertEquals("not_written", state)

        val rt = rt()
        val r = rt.run(rt.baseFooter().copy(preScanWarnings = null, preScanWarningsState = state))
        assertNull("null in the file, not []", r.data.footer.preScanWarnings)
        assertEquals("not_written", r.wireFooter.get("preScanWarningsState").asString)
    }

    @Test
    fun `the pre-scan read-failed state is capped at 200 characters`() {
        val long = "z".repeat(600)
        assertEquals(200, PreScanWarningsStates.of(Result.failure(RuntimeException(long))).length)
    }

    // ───────────────────────── the statistic ─────────────────────────

    @Test
    fun `SamplerStats has no standard deviation for fewer than 2 readings - null, not -1`() {
        val stats = SamplerStats()
        assertNull(stats.snapshot().accelStdDev)
        assertEquals(0, stats.snapshot().accelSampleCount)

        stats.updateAccelMagnitude(9.81f)
        assertNull("one reading has no variance", stats.snapshot().accelStdDev)
        assertEquals(1, stats.snapshot().accelSampleCount)

        stats.updateAccelMagnitude(9.81f)
        assertEquals("two equal readings: a real zero", 0f, stats.snapshot().accelStdDev!!, 1e-4f)
    }

    // ───────────────────────── the accelerometer that never delivered ─────────────────────────

    @Test
    fun `an undelivered accelerometer has no magnitude - the placeholder zeros are not a reading`() {
        val none = SensorSnapshot(0f, 0f, 0f, 0f, 0f, 0f, accelDelivered = false)
        assertNull(none.accelMagnitudeOrNull())

        val delivered = SensorSnapshot(0f, 0f, 9.81f, 0f, 0f, 0f, accelDelivered = true)
        assertEquals(9.81f, delivered.accelMagnitudeOrNull()!!, 1e-4f)

        val genuinelyZero = SensorSnapshot(0f, 0f, 0f, 0f, 0f, 0f, accelDelivered = true)
        assertEquals("a delivered 0 is a reading", 0f, genuinelyZero.accelMagnitudeOrNull()!!, 0f)
    }

    @Test
    fun `the real SensorCollector says accelDelivered only after an event`() {
        val accel: Sensor = mock { on { type } doReturn Sensor.TYPE_ACCELEROMETER }
        val sm: SensorManager = mock {
            on { getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } doReturn accel
            on { registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) } doReturn true
        }
        val context: Context = mock { on { getSystemService(Context.SENSOR_SERVICE) } doReturn sm }
        val collector = SensorCollector(context)
        collector.start()

        assertFalse("nothing delivered yet", collector.snapshot().accelDelivered)
        assertNull(collector.snapshot().accelMagnitudeOrNull())

        val captor = argumentCaptor<SensorEventListener>()
        verify(sm).registerListener(captor.capture(), any<Sensor>(), any<Int>())
        val event = mock<SensorEvent>()
        SensorEvent::class.java.getField("values").also { it.isAccessible = true }
            .set(event, floatArrayOf(0f, 0f, 9.81f))
        SensorEvent::class.java.getField("sensor").also { it.isAccessible = true }
            .set(event, accel)
        captor.firstValue.onSensorChanged(event)

        val after = collector.snapshot()
        assertTrue(after.accelDelivered)
        assertEquals(9.81f, after.accelMagnitudeOrNull()!!, 1e-4f)
    }
}
