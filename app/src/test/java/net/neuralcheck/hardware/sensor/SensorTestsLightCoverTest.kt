package net.neuralcheck.hardware.sensor

import android.content.Context
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.hardware.sensor.SensorTests.Companion.LightCoverKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * fieldsVersion 69 (SN-04, contract docs/FV68_CONTRACT.md section 7-h (c), N-20; user decision 3
 * of 2026-10-10): the light sensor is listened to in two windows on one listener, the technician is
 * asked to cover it between them, and the raw readings ship in two lists. The status, the text and
 * the note are what the app computed in fv68 — the app sends no "not measured".
 *
 * Run through the REAL [SensorTests.testSensor] with a mocked SensorManager and virtual time: the
 * listener is registered at t = 0, the cover is asked for at t = 1 000 ms, the test ends at
 * t = 4 000 ms. (The platform pieces replaced are only the sensor service and the clock.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensorTestsLightCoverTest {

    private val resources: Resources = mock { on { getIdentifier(any(), any(), any()) } doReturn 1 }
    private val lightSensor: Sensor = mock { on { type } doReturn Sensor.TYPE_LIGHT }
    private val accelerometer: Sensor = mock { on { type } doReturn Sensor.TYPE_ACCELEROMETER }

    private fun context(sm: SensorManager): Context = mock {
        on { getSystemService(Context.SENSOR_SERVICE) } doReturn sm
        on { getString(any()) } doReturn "txt"
        on { resources } doReturn this@SensorTestsLightCoverTest.resources
        on { packageName } doReturn "net.neuralcheck"
    }

    private fun sensorManager(sensor: Sensor?): SensorManager = mock {
        on { getDefaultSensor(any()) } doReturn sensor
        on { registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) } doReturn true
    }

    private fun event(timestampMs: Long, sensor: Sensor, vararg values: Float): SensorEvent {
        val e = mock<SensorEvent>()
        SensorEvent::class.java.getField("values").also { it.isAccessible = true }.set(e, values)
        SensorEvent::class.java.getField("timestamp").also { it.isAccessible = true }.set(e, timestampMs * 1_000_000L)
        SensorEvent::class.java.getField("sensor").also { it.isAccessible = true }.set(e, sensor)
        return e
    }

    /** The sensor test under virtual time: the clock the events and the prompt are stamped on is the scheduler's. */
    private fun TestScope.tests(sm: SensorManager, channel: CoverPromptChannel): SensorTests =
        SensorTests(context(sm), channel).also { it.nanoClock = { currentTime * 1_000_000L } }

    /** Runs SN-04 with scripted (ms since registration, lux) events; returns its result. */
    private fun TestScope.runLight(events: List<Pair<Long, Float>>, channel: CoverPromptChannel = CoverPromptChannel()): TestResult {
        val sm = sensorManager(lightSensor)
        val captor = argumentCaptor<SensorEventListener>()
        var result: TestResult? = null
        val job = launch { result = tests(sm, channel).testSensor("SN-04", "test_sn04_ar", "test_sn04_tr", Sensor.TYPE_LIGHT) }
        runCurrent()
        verify(sm).registerListener(captor.capture(), any<Sensor>(), any<Int>())
        for ((ms, lux) in events) {
            advanceTimeBy(ms - currentTime)
            runCurrent()
            captor.firstValue.onSensorChanged(event(ms, lightSensor, lux))
        }
        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue("the test ended", job.isCompleted)
        return result!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun list(r: TestResult, key: String) = r.measurements!![key] as List<Map<String, Any?>>

    // ───────────────────────── the contract's table, case by case ─────────────────────────

    @Test
    fun `case 1 - covered, the reading changes - PASS, two lists split where the cover was asked for`() = runTest {
        val r = runLight(listOf(400L to 320f, 1_600L to 4f, 2_800L to 2f))

        assertEquals(TestStatus.PASS, r.status)
        assertEquals("the average of ALL readings of both windows, as in fv68: (320 + 4 + 2) / 3", "108.67", r.rawValue)
        assertEquals("3 readings", r.notes)
        assertEquals(4_000L, r.measurements!![LightCoverKeys.WINDOW_MS])
        assertEquals(1_000L, r.measurements!![LightCoverKeys.COVER_PROMPT_AT_MS])
        assertEquals(listOf(mapOf("t" to 400L, "values" to listOf(320f))), list(r, LightCoverKeys.BEFORE))
        assertEquals(
            listOf(mapOf("t" to 1_600L, "values" to listOf(4f)), mapOf("t" to 2_800L, "values" to listOf(2f))),
            list(r, LightCoverKeys.DURING)
        )
    }

    @Test
    fun `case 2 - NOT covered - the app cannot tell, so it is the same PASS (the server classifies)`() = runTest {
        val r = runLight(listOf(400L to 120f, 1_800L to 120f, 3_000L to 119f))

        assertEquals("S-32 (b): no state of the app's own for a sensor nobody covered", TestStatus.PASS, r.status)
        assertEquals(1, list(r, LightCoverKeys.BEFORE).size)
        assertEquals(2, list(r, LightCoverKeys.DURING).size)
        assertFalse("not_measured is never sent by the app", r.measurements.toString().contains("not_measured"))
    }

    @Test
    fun `case 3 - every reading is 0 - WARNING exactly as in fv68, text 0 point 00`() = runTest {
        val r = runLight(listOf(400L to 0f, 1_900L to 0f))

        assertEquals(TestStatus.WARNING, r.status)
        assertEquals("0.00", r.rawValue)
        assertEquals("2 readings", r.notes)
        assertEquals(1, list(r, LightCoverKeys.BEFORE).size)
        assertEquals(1, list(r, LightCoverKeys.DURING).size)
    }

    @Test
    fun `case 4 - no readings at all - WARNING with the no-readings text, and BOTH lists present and empty (rule 22)`() = runTest {
        val r = runLight(emptyList())

        assertEquals(TestStatus.WARNING, r.status)
        assertEquals("the no-readings text, as in fv68", "txt", r.rawValue)
        assertNull(r.notes)
        val m = r.measurements!!
        assertTrue("the key is present, not absent", m.containsKey(LightCoverKeys.BEFORE) && m.containsKey(LightCoverKeys.DURING))
        assertEquals(emptyList<Any>(), m[LightCoverKeys.BEFORE])
        assertEquals(emptyList<Any>(), m[LightCoverKeys.DURING])
        assertEquals(1_000L, m[LightCoverKeys.COVER_PROMPT_AT_MS])
    }

    @Test
    fun `a reading stamped exactly when the cover was asked for is a DURING reading`() = runTest {
        val r = runLight(listOf(999L to 100f, 1_000L to 90f))
        assertEquals(listOf(999L), list(r, LightCoverKeys.BEFORE).map { it["t"] })
        assertEquals(listOf(1_000L), list(r, LightCoverKeys.DURING).map { it["t"] })
    }

    // ───────────────────────── the request to the technician ─────────────────────────

    @Test
    fun `the cover is asked for at 1000 ms and withdrawn when the test ends`() = runTest {
        val channel = CoverPromptChannel()
        val sm = sensorManager(lightSensor)
        val job = launch { tests(sm, channel).testSensor("SN-04", "test_sn04_ar", "test_sn04_tr", Sensor.TYPE_LIGHT) }
        runCurrent()
        assertFalse("not before the baseline window", channel.active.value)
        advanceTimeBy(999L); runCurrent()
        assertFalse("999 ms: still the baseline", channel.active.value)
        advanceTimeBy(2L); runCurrent()
        assertTrue("1001 ms: the technician is asked", channel.active.value)
        advanceTimeBy(2_900L); runCurrent()
        assertTrue("3901 ms: still asked", channel.active.value)
        advanceTimeBy(200L); runCurrent()
        assertTrue(job.isCompleted)
        assertFalse("withdrawn at the end", channel.active.value)
    }

    @Test
    fun `a cancelled test withdraws the request and unregisters the sensor - it is never left up`() = runTest {
        val channel = CoverPromptChannel()
        val sm = sensorManager(lightSensor)
        val captor = argumentCaptor<SensorEventListener>()
        val job = launch { tests(sm, channel).testSensor("SN-04", "test_sn04_ar", "test_sn04_tr", Sensor.TYPE_LIGHT) }
        runCurrent()
        verify(sm).registerListener(captor.capture(), any<Sensor>(), any<Int>())
        advanceTimeBy(1_500L); runCurrent()
        assertTrue(channel.active.value)

        job.cancel()
        runCurrent()

        assertFalse(channel.active.value)
        verify(sm).unregisterListener(captor.firstValue)
    }

    @Test
    fun `no other sensor raises the request or ships a light raw - they keep the 3 s window and no measurements`() = runTest {
        val channel = CoverPromptChannel()
        val sm = sensorManager(accelerometer)
        var result: TestResult? = null
        val job = launch { result = tests(sm, channel).testSensor("SN-01", "test_sn01_ar", "test_sn01_tr", Sensor.TYPE_ACCELEROMETER) }
        runCurrent()
        advanceTimeBy(1_500L); runCurrent()
        assertFalse("the accelerometer test never asks anyone to cover anything", channel.active.value)
        advanceTimeBy(1_501L); runCurrent()
        assertTrue("3 s, not 4 s", job.isCompleted)
        assertNull(result!!.measurements)
    }

    @Test
    fun `a device with no light sensor is UNAVAILABLE as before - no request, no raw`() = runTest {
        val channel = CoverPromptChannel()
        var result: TestResult? = null
        val job = launch { result = tests(sensorManager(null), channel).testSensor("SN-04", "test_sn04_ar", "test_sn04_tr", Sensor.TYPE_LIGHT) }
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(TestStatus.UNAVAILABLE, result!!.status)
        assertNull(result!!.measurements)
        assertFalse(channel.active.value)
    }

    // ───────────────────────── the pure split ─────────────────────────

    @Test
    fun `lightCoverMeasurements - t is ms since registration and values are copied as given`() {
        val m = SensorTests.lightCoverMeasurements(
            readings = listOf(5_400_000_000L to floatArrayOf(10f), 6_700_000_000L to floatArrayOf(3f, 0f)),
            startNanos = 5_000_000_000L,
            coverPromptAtNanos = 6_000_000_000L
        )
        assertEquals(1_000L, m[LightCoverKeys.COVER_PROMPT_AT_MS])
        assertEquals(listOf(mapOf("t" to 400L, "values" to listOf(10f))), m[LightCoverKeys.BEFORE])
        assertEquals(listOf(mapOf("t" to 1_700L, "values" to listOf(3f, 0f))), m[LightCoverKeys.DURING])
    }

    @Test
    fun `the keys and the windows are the contract's`() {
        assertEquals("windowMs", LightCoverKeys.WINDOW_MS)
        assertEquals("coverPromptAtMs", LightCoverKeys.COVER_PROMPT_AT_MS)
        assertEquals("readingsBeforeCover", LightCoverKeys.BEFORE)
        assertEquals("readingsDuringCover", LightCoverKeys.DURING)
        assertEquals(1_000L, SensorTests.LIGHT_BEFORE_COVER_MS)
        assertEquals(3_000L, SensorTests.LIGHT_DURING_COVER_MS)
        assertEquals(4_000L, SensorTests.LIGHT_WINDOW_MS)
    }
}
