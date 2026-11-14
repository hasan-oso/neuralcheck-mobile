package net.neuralcheck.hardware.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * fieldsVersion 68 (S-18, S-32; order of batch 3 part 2-a): SN-08 (step
 * detector) and SN-09 (step counter) —
 *  - from API 29 they need ACTIVITY_RECOGNITION; without it the test is
 *    NOT_TESTED ("permission denied"), not a "no readings" WARNING that read as a
 *    broken sensor, and the sensor is not even registered;
 *  - the raw readings ship in measurements, and "no readings" is the explicit
 *    empty list — the device may simply not have moved. The status and the text
 *    are unchanged: the server classifies "not measured" from the raw (S-32);
 *    the app invents no state name.
 * Run through the real [SensorTests.testSensor] with a mocked SensorManager and
 * virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensorTestsStepSensorsTest {

    private val resources: Resources = mock {
        on { getIdentifier(any(), any(), any()) } doReturn 1
    }

    private val stepSensor: Sensor = mock { on { type } doReturn Sensor.TYPE_STEP_COUNTER }

    private fun context(sm: SensorManager, activityRecognitionGranted: Boolean): Context = mock {
        on { getSystemService(Context.SENSOR_SERVICE) } doReturn sm
        on { getString(any()) } doReturn "txt"
        on { resources } doReturn this@SensorTestsStepSensorsTest.resources
        on { packageName } doReturn "net.neuralcheck"
        on { checkPermission(eq(Manifest.permission.ACTIVITY_RECOGNITION), any(), any()) } doReturn
            (if (activityRecognitionGranted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED)
    }

    private fun sensorManager(hasStepSensors: Boolean = true): SensorManager = mock {
        on { getDefaultSensor(any()) } doReturn (if (hasStepSensors) stepSensor else null)
        on { registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) } doReturn true
    }

    private fun sensorEvent(timestampNs: Long, vararg values: Float): SensorEvent {
        val event = mock<SensorEvent>()
        SensorEvent::class.java.getField("values").also { it.isAccessible = true }.set(event, values)
        SensorEvent::class.java.getField("timestamp").also { it.isAccessible = true }.set(event, timestampNs)
        SensorEvent::class.java.getField("sensor").also { it.isAccessible = true }.set(event, stepSensor)
        return event
    }

    private fun tests(ctx: Context, sdkInt: Int) = SensorTests(ctx, CoverPromptChannel()).also { it.sdkIntForGate = sdkInt }

    // ───────────────────────── the gate ─────────────────────────

    @Test
    fun `the gate applies to the two step sensors from API 29 and to nothing else`() {
        assertTrue(SensorTests.needsActivityRecognition(Sensor.TYPE_STEP_COUNTER, 29))
        assertTrue(SensorTests.needsActivityRecognition(Sensor.TYPE_STEP_DETECTOR, 35))
        assertFalse(SensorTests.needsActivityRecognition(Sensor.TYPE_STEP_COUNTER, 28))
        assertFalse(SensorTests.needsActivityRecognition(Sensor.TYPE_ACCELEROMETER, 35))
        assertFalse(SensorTests.needsActivityRecognition(Sensor.TYPE_LIGHT, 35))
    }

    @Test
    fun `SN-09 without the permission on API 29+ is NOT_TESTED and the sensor is not even registered`() = runTest {
        val sm = sensorManager()
        var result: TestResult? = null
        val job = launch { result = tests(context(sm, activityRecognitionGranted = false), 34)
            .testSensor("SN-09", "test_sn09_ar", "test_sn09_tr", Sensor.TYPE_STEP_COUNTER) }
        runCurrent()
        assertTrue("no 3 s wait: nothing was listened to", job.isCompleted)

        val r = result!!
        assertEquals(TestStatus.NOT_TESTED, r.status)
        assertEquals("N/A", r.rawValue)
        assertNull("no raw: nothing was measured", r.measurements)
        verify(sm, never()).registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>())
    }

    @Test
    fun `below API 29 the step sensors run as before - the permission does not exist`() = runTest {
        val sm = sensorManager()
        var result: TestResult? = null
        val job = launch { result = tests(context(sm, activityRecognitionGranted = false), 28)
            .testSensor("SN-08", "test_sn08_ar", "test_sn08_tr", Sensor.TYPE_STEP_DETECTOR) }
        runCurrent()
        verify(sm).registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>())
        advanceTimeBy(3_500)
        job.join()
        assertTrue(result!!.status != TestStatus.NOT_TESTED)
    }

    @Test
    fun `a device with no step sensor is UNAVAILABLE whatever the permission says`() = runTest {
        var result: TestResult? = null
        val job = launch { result = tests(context(sensorManager(hasStepSensors = false), activityRecognitionGranted = false), 34)
            .testSensor("SN-09", "test_sn09_ar", "test_sn09_tr", Sensor.TYPE_STEP_COUNTER) }
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(TestStatus.UNAVAILABLE, result!!.status)
    }

    // ───────────────────────── the raw ─────────────────────────

    @Test
    fun `readings ship raw with t in ms since registration and values as the sensor gave them`() = runTest {
        val sm = sensorManager()
        val captor = argumentCaptor<SensorEventListener>()
        var result: TestResult? = null
        val job = launch { result = tests(context(sm, activityRecognitionGranted = true), 34)
            .testSensor("SN-09", "test_sn09_ar", "test_sn09_tr", Sensor.TYPE_STEP_COUNTER) }
        runCurrent()
        verify(sm).registerListener(captor.capture(), any<Sensor>(), any<Int>())

        // SystemClock.elapsedRealtimeNanos() is 0 under the unit-test stubs, so t is the timestamp in ms.
        captor.firstValue.onSensorChanged(sensorEvent(900_000_000L, 4_021f))
        captor.firstValue.onSensorChanged(sensorEvent(2_150_000_000L, 4_022f))
        advanceTimeBy(3_500)
        job.join()

        val r = result!!
        assertEquals(TestStatus.PASS, r.status)
        val readings = r.measurements!!["readings"] as List<*>
        assertEquals(2, readings.size)
        val first = readings[0] as Map<*, *>
        assertEquals(900L, first["t"])
        assertEquals(listOf(4_021f), first["values"])
        assertEquals(2_150L, (readings[1] as Map<*, *>)["t"])
        assertEquals(3_000L, r.measurements!!["windowMs"])
    }

    @Test
    fun `no readings is the explicit empty list - and the status and text are the ones it always had`() = runTest {
        val sm = sensorManager()
        var result: TestResult? = null
        val job = launch { result = tests(context(sm, activityRecognitionGranted = true), 34)
            .testSensor("SN-08", "test_sn08_ar", "test_sn08_tr", Sensor.TYPE_STEP_DETECTOR) }
        runCurrent()
        advanceTimeBy(3_500) // the device did not move: no step event
        job.join()

        val r = result!!
        assertEquals("unchanged: the app does not turn 'no readings' into a new state (S-32)", TestStatus.WARNING, r.status)
        assertEquals("txt", r.rawValue)
        val m = r.measurements!!
        assertEquals("a declared empty list, not an absent key", emptyList<Any>(), m["readings"])
        assertEquals(3_000L, m["windowMs"])
    }

    @Test
    fun `other sensors carry no measurements - the raw list is the step sensors' only`() = runTest {
        val sm = sensorManager()
        val captor = argumentCaptor<SensorEventListener>()
        var result: TestResult? = null
        val job = launch { result = tests(context(sm, activityRecognitionGranted = true), 34)
            .testSensor("SN-01", "test_sn01_ar", "test_sn01_tr", Sensor.TYPE_ACCELEROMETER) }
        runCurrent()
        verify(sm).registerListener(captor.capture(), any<Sensor>(), any<Int>())
        captor.firstValue.onSensorChanged(sensorEvent(1_000_000L, 0f, 0f, 9.8f))
        advanceTimeBy(3_500)
        job.join()
        assertNull(result!!.measurements)
        assertEquals(TestStatus.PASS, result!!.status)
    }
}
