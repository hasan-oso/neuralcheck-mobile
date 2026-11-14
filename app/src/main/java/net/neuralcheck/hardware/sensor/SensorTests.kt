package net.neuralcheck.hardware.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.delay
import net.neuralcheck.BuildConfig
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import javax.inject.Inject
import kotlin.math.abs

/**
 * Sensor tests SN-01 to SN-12 — unified methodology: check availability + 3s data collection.
 * SN-04 (light) is the exception since fieldsVersion 69: 1 s, then the technician is asked to cover
 * the sensor, then 3 s ([LIGHT_BEFORE_COVER_MS], [LIGHT_DURING_COVER_MS]); the raw ships in two lists.
 */
class SensorTests @Inject constructor(
    private val context: Context,
    /** fieldsVersion 69 (SN-04): where the "cover the sensor" request to the technician is raised. */
    private val coverPrompt: CoverPromptChannel
) {
    // Hilt instantiates this class up-front when StressTestService injects
    // FunctionalTestRunner. A null SENSOR_SERVICE used to be a hard cast
    // failure (ClassCastException) which crashed the service before its own
    // try/catch could observe it. Safe-cast keeps the graph alive — every
    // call site below null-guards `sm`.
    private val sm: SensorManager? = try {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    } catch (t: Throwable) {
        Log.w("NeuralCheck", "SENSOR_SERVICE unavailable: ${t.message}")
        null
    }

    /**
     * Test hook (fieldsVersion 68): the API level the ACTIVITY_RECOGNITION gate
     * reads. Production never sets it — a constructor parameter would be an
     * injection point Hilt cannot satisfy.
     */
    internal var sdkIntForGate: Int = android.os.Build.VERSION.SDK_INT

    /**
     * Test hook (fieldsVersion 69): the clock `SensorEvent.timestamp` is on (ns, elapsed realtime) —
     * read when the listener is registered and when the cover is asked for, so a test can run both
     * on virtual time. Production never sets it.
     */
    internal var nanoClock: () -> Long = { android.os.SystemClock.elapsedRealtimeNanos() }

    companion object {
        /** Window the sensor is listened to, in ms (unchanged: "3 s data collection"). */
        const val WINDOW_MS = 3_000L

        /** ACTIVITY_RECOGNITION was introduced with Android 10 (API 29). */
        const val ACTIVITY_RECOGNITION_MIN_SDK = 29

        /**
         * fieldsVersion 68 (S-18): the step detector and step counter need the
         * ACTIVITY_RECOGNITION runtime permission from API 29. Pure decision,
         * shared with the tests.
         */
        internal fun needsActivityRecognition(sensorType: Int, sdkInt: Int): Boolean =
            (sensorType == Sensor.TYPE_STEP_DETECTOR || sensorType == Sensor.TYPE_STEP_COUNTER) &&
                sdkInt >= ACTIVITY_RECOGNITION_MIN_SDK

        /**
         * fieldsVersion 68 (S-18, S-32): SN-08 / SN-09 ship their RAW readings —
         * `{t, values}` per event, t = ms since the listener was registered,
         * values exactly as the sensor gave them — and an empty list is the
         * explicit "no readings in the window" (the device may simply not have
         * moved: a step sensor reports nothing at rest). The server classifies
         * (S-32): the app does not turn "no readings" into a state.
         */
        internal fun stepSensorMeasurements(readings: List<Pair<Long, FloatArray>>): Map<String, Any?> = mapOf(
            "windowMs" to WINDOW_MS,
            "readings" to readings.map { (t, values) -> mapOf("t" to t, "values" to values.toList()) }
        )

        private fun isStepSensor(sensorType: Int) =
            sensorType == Sensor.TYPE_STEP_DETECTOR || sensorType == Sensor.TYPE_STEP_COUNTER

        // ── SN-04, the light sensor (fieldsVersion 69, contract section 7-h (c), N-20) ──

        /** Readings taken BEFORE the technician is asked to cover the sensor. [م] — tuned on the device round. */
        const val LIGHT_BEFORE_COVER_MS = 1_000L

        /** From the moment the cover is asked until the end: includes the technician's reaction time. [م] */
        const val LIGHT_DURING_COVER_MS = 3_000L

        const val LIGHT_WINDOW_MS = LIGHT_BEFORE_COVER_MS + LIGHT_DURING_COVER_MS

        /** The keys of SN-04's `measurements` — one source (rule 24). */
        object LightCoverKeys {
            const val WINDOW_MS = "windowMs"
            const val COVER_PROMPT_AT_MS = "coverPromptAtMs"
            const val BEFORE = "readingsBeforeCover"
            const val DURING = "readingsDuringCover"
        }

        /**
         * SN-04 ships its RAW readings in two lists split at the moment the cover was ASKED for:
         * `{t, values}` per event, `t` = ms since the listener was registered, `values` exactly as the
         * sensor gave them (N-20). An empty list is the explicit "no readings in that window" (rule 22).
         * The app does NOT know whether or when the technician covered the sensor and does not compare
         * the windows: `readingsDuringCover` is "from the request on", and the server classifies (S-32 (b)).
         *
         * @param readings (SensorEvent.timestamp in ns, a copy of event.values), in arrival order
         */
        internal fun lightCoverMeasurements(
            readings: List<Pair<Long, FloatArray>>,
            startNanos: Long,
            coverPromptAtNanos: Long
        ): Map<String, Any?> {
            fun element(r: Pair<Long, FloatArray>) = mapOf<String, Any?>(
                "t" to (r.first - startNanos) / 1_000_000L,
                "values" to r.second.toList()
            )
            val (before, during) = readings.partition { it.first < coverPromptAtNanos }
            return mapOf(
                LightCoverKeys.WINDOW_MS to LIGHT_WINDOW_MS,
                LightCoverKeys.COVER_PROMPT_AT_MS to (coverPromptAtNanos - startNanos) / 1_000_000L,
                LightCoverKeys.BEFORE to before.map(::element),
                LightCoverKeys.DURING to during.map(::element)
            )
        }
    }

    suspend fun runAll(): List<TestResult> {
        val base = listOf(
            testSensor("SN-01", "test_sn01_ar", "test_sn01_tr", Sensor.TYPE_ACCELEROMETER),
            testSensor("SN-02", "test_sn02_ar", "test_sn02_tr", Sensor.TYPE_GYROSCOPE),
            testSensor("SN-03", "test_sn03_ar", "test_sn03_tr", Sensor.TYPE_PROXIMITY),
            testSensor("SN-04", "test_sn04_ar", "test_sn04_tr", Sensor.TYPE_LIGHT),
            testSensor("SN-05", "test_sn05_ar", "test_sn05_tr", Sensor.TYPE_MAGNETIC_FIELD),
            testSensor("SN-06", "test_sn06_ar", "test_sn06_tr", Sensor.TYPE_PRESSURE),
            testSensor("SN-07", "test_sn07_ar", "test_sn07_tr", Sensor.TYPE_AMBIENT_TEMPERATURE),
            testSensor("SN-08", "test_sn08_ar", "test_sn08_tr", Sensor.TYPE_STEP_DETECTOR),
            testSensor("SN-09", "test_sn09_ar", "test_sn09_tr", Sensor.TYPE_STEP_COUNTER),
            testSensor("SN-10", "test_sn10_ar", "test_sn10_tr", Sensor.TYPE_GRAVITY),
            testSensor("SN-11", "test_sn11_ar", "test_sn11_tr", Sensor.TYPE_LINEAR_ACCELERATION),
            testSensor("SN-12", "test_sn12_ar", "test_sn12_tr", Sensor.TYPE_ROTATION_VECTOR)
        )
        // SN-13 (accelerometer calibration) is datacollect-only. It re-uses
        // the SN-01 collection window — no extra wait — but the test only
        // makes sense when the device is laid flat, so we ship it as a
        // technician-only diagnostic rather than a consumer-facing result.
        return if (BuildConfig.ENABLE_EXTENDED_INFO_TESTS) {
            base + testAccelCalibration()
        } else {
            base
        }
    }

    /** `internal` so a JVM test can drive one sensor with a mocked SensorManager (fieldsVersion 68). */
    internal suspend fun testSensor(testId: String, nameArResId: String, nameTrResId: String, sensorType: Int): TestResult {
        val nameAr = context.getString(context.resources.getIdentifier(nameArResId, "string", context.packageName))
        val nameTr = context.getString(context.resources.getIdentifier(nameTrResId, "string", context.packageName))
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val sm = this.sm ?: return TestResult(testId, nameAr, nameTr, "sensors",
            TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
        val sensor = try {
            sm.getDefaultSensor(sensorType)
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "getDefaultSensor($sensorType) failed: ${t.message}")
            null
        } ?: return TestResult(testId, nameAr, nameTr, "sensors",
            TestStatus.UNAVAILABLE, null, unavailableAr, "", null)

        // fieldsVersion 68 (S-18): from API 29 the step sensors deliver nothing
        // without ACTIVITY_RECOGNITION — that is "not tested because the
        // permission is denied" (the same reading the scan permission gate
        // announces up front), not a "no readings" WARNING that reads as a
        // broken sensor. Checked after the sensor's existence: a device with
        // no step sensor is still UNAVAILABLE, whatever the permission.
        if (needsActivityRecognition(sensorType, sdkIntForGate) &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACTIVITY_RECOGNITION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return TestResult(
                testId, nameAr, nameTr, "sensors", TestStatus.NOT_TESTED, null, "N/A", "",
                context.getString(net.neuralcheck.R.string.error_permission_denied)
            )
        }

        val readings = java.util.Collections.synchronizedList(mutableListOf<FloatArray>())
        // fieldsVersion 68: when each reading arrived (SensorEvent.timestamp, ns on the
        // elapsed-realtime clock), kept beside the values for the step sensors' raw list.
        val readingNanos = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val startNanos = nanoClock()
        val isLight = sensorType == Sensor.TYPE_LIGHT
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                try {
                    val copy = event.values.copyOf()
                    synchronized(readings) {
                        readings.add(copy)
                        readingNanos.add(event.timestamp)
                    }
                } catch (_: Throwable) { /* malformed event */ }
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) {}
        }
        // registerListener can return false when an OEM-specific sensor
        // refuses the requested rate, and on Android 12+ throws
        // SecurityException for >200Hz without HIGH_SAMPLING_RATE_SENSORS.
        // Capture both as a graceful UNAVAILABLE rather than letting the
        // exception cascade out of the runner.
        val registered = try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "registerListener($testId) failed: ${t.message}")
            false
        }
        if (!registered) {
            return TestResult(testId, nameAr, nameTr, "sensors", TestStatus.UNAVAILABLE,
                null, unavailableAr, "", null)
        }
        // fieldsVersion 69 (SN-04): the light sensor is listened to in two windows on ONE listener —
        // LIGHT_BEFORE_COVER_MS, then the technician is asked to cover it, then LIGHT_DURING_COVER_MS.
        // Every other sensor keeps the single WINDOW_MS window it always had.
        var coverPromptAtNanos = 0L
        try {
            if (isLight) {
                delay(LIGHT_BEFORE_COVER_MS)
                coverPromptAtNanos = nanoClock()
                coverPrompt.show()
                delay(LIGHT_DURING_COVER_MS)
            } else {
                delay(WINDOW_MS) // Collect for 3 seconds
            }
        } finally {
            // Lowered on EVERY exit, cancellation included: a prompt left up asks the technician for
            // something the test is no longer waiting for.
            if (isLight) coverPrompt.hide()
            try { sm.unregisterListener(listener) } catch (_: Throwable) {}
        }

        // The status and the text are what they have always been (WARNING for "no
        // readings", WARNING when every reading is 0): S-32 says the server classifies
        // "not measured" from the raw, and the app invents no new state name. The step
        // sensors (fv68) and the light sensor (fv69) ship their raw readings — or the
        // explicit empty lists — in measurements. Light: contract section 7-h (c), and the
        // user decision of 2026-10-10: «يبقى ما يرسله التطبيق اليوم كما هو، ويُضاف الخامّ».
        val measurements: Map<String, Any?>? = when {
            isStepSensor(sensorType) -> {
                val snapshot = synchronized(readings) { readings.toList() to readingNanos.toList() }
                stepSensorMeasurements(
                    snapshot.first.indices.map { i ->
                        ((snapshot.second[i] - startNanos) / 1_000_000L) to snapshot.first[i]
                    }
                )
            }
            isLight -> {
                val snapshot = synchronized(readings) { readings.toList() to readingNanos.toList() }
                lightCoverMeasurements(
                    snapshot.first.indices.map { i -> snapshot.second[i] to snapshot.first[i] },
                    startNanos, coverPromptAtNanos
                )
            }
            else -> null
        }

        val noReadingsAr = context.getString(net.neuralcheck.R.string.value_no_readings_ar)
        if (readings.isEmpty()) {
            return TestResult(
                testId, nameAr, nameTr, "sensors", TestStatus.WARNING, null, noReadingsAr, "", null,
                measurements = measurements
            )
        }
        val allZero = readings.all { arr -> arr.all { it == 0f } }
        val status = if (allZero) TestStatus.WARNING else TestStatus.PASS
        val avg = readings.first().indices.map { i -> readings.map { it.getOrElse(i) { 0f } }.average() }
        val raw = avg.joinToString(", ") { String.format(java.util.Locale.US, "%.2f", it) }
        return TestResult(
            testId, nameAr, nameTr, "sensors", status, null, raw, "", "${readings.size} readings",
            measurements = measurements
        )
    }

    /**
     * SN-13 (datacollect-only): accelerometer calibration drift. With the
     * device sitting still, ideal accel readings are (0, 0, 9.81 m/s²) — the
     * gravity vector. We sample for 3 s and compute the worst-axis drift
     * (max of |avgX|, |avgY|, |avgZ - 9.81|). Drift <0.3 m/s² is a healthy
     * factory-calibrated sensor; >0.8 m/s² indicates a sensor fault or that
     * the device wasn't laid flat (also a valid signal to surface).
     */
    private suspend fun testAccelCalibration(): TestResult {
        val testId = "SN-13"
        val nameAr = context.getString(net.neuralcheck.R.string.test_sn13_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_sn13_tr)
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val sm = this.sm ?: return TestResult(testId, nameAr, nameTr, "sensors",
            TestStatus.UNAVAILABLE, null, unavailableAr, "", null)

        val sensor = try {
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "getDefaultSensor(ACCEL) for SN-13 failed: ${t.message}")
            null
        } ?: return TestResult(testId, nameAr, nameTr, "sensors",
            TestStatus.UNAVAILABLE, null, unavailableAr, "", null)

        val readings = mutableListOf<FloatArray>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                try { readings.add(event.values.copyOf()) } catch (_: Throwable) {}
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) {}
        }
        val registered = try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "registerListener(SN-13) failed: ${t.message}")
            false
        }
        if (!registered) {
            return TestResult(testId, nameAr, nameTr, "sensors",
                TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
        }
        try {
            delay(3000)
        } finally {
            try { sm.unregisterListener(listener) } catch (_: Throwable) {}
        }

        if (readings.isEmpty() || readings.first().size < 3) {
            return TestResult(testId, nameAr, nameTr, "sensors",
                TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
        }

        val avgX = readings.map { it[0].toDouble() }.average()
        val avgY = readings.map { it[1].toDouble() }.average()
        val avgZ = readings.map { it[2].toDouble() }.average()
        val xDrift = abs(avgX)
        val yDrift = abs(avgY)
        val zDrift = abs(avgZ - 9.81)
        val maxDrift = maxOf(xDrift, yDrift, zDrift)

        val status = when {
            maxDrift < 0.3 -> TestStatus.PASS
            maxDrift < 0.8 -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        val raw = String.format(java.util.Locale.US, "%.2f", maxDrift)
        return TestResult(testId, nameAr, nameTr, "sensors",
            status, null, raw, "m/s²", "${readings.size} readings")
    }
}
