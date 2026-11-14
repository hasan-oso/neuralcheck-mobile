package net.neuralcheck.service.collector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Collects sensor data using SensorEventListener at SENSOR_DELAY_FASTEST (ADR 4.7).
 * Stores latest readings in AtomicReferences for thread-safe snapshot access.
 *
 * Every interaction with [SensorManager] is wrapped in try/catch — registration
 * can fail with IllegalArgumentException on devices where a vendor-specific
 * sensor refuses the requested rate, and unregistration can NPE on builds
 * that null out the underlying HAL after a sensor service crash. Failure
 * is non-fatal: snapshots return whatever was last cached (zeros if nothing
 * was ever delivered).
 */
class SensorCollector(private val context: Context) {

    companion object {
        private const val TAG = "NeuralCheck"

        /** What [snapshot] shows for a sensor that has delivered nothing (the V1 18-column
         *  layout still writes these zeros; [SensorSnapshot.accelDelivered] says they are not readings). */
        private val NO_EVENT = floatArrayOf(0f, 0f, 0f)
    }

    private val sensorManager: SensorManager? = try {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    } catch (t: Throwable) {
        Log.w(TAG, "SensorManager not available: ${t.message}")
        null
    }
    // fieldsVersion 68 (ت-18 row 6): null until the first event arrives, so "no
    // event yet" is distinguishable from a real reading of 0 (an accelerometer
    // that never delivered used to look like a perfectly still device).
    private val latestAccel = AtomicReference<FloatArray?>(null)
    private val latestGyro = AtomicReference<FloatArray?>(null)
    private var isStarted = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            try {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> latestAccel.set(event.values.copyOf())
                    Sensor.TYPE_GYROSCOPE -> latestGyro.set(event.values.copyOf())
                }
            } catch (t: Throwable) {
                // Some vendor builds emit malformed events with truncated values
                // arrays; silently drop those instead of crashing the sampler.
                Log.w(TAG, "onSensorChanged failed: ${t.message}")
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // Not needed for data collection
        }
    }

    fun start() {
        if (isStarted) return
        isStarted = true
        registerSensor(Sensor.TYPE_ACCELEROMETER)
        registerSensor(Sensor.TYPE_GYROSCOPE)
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        try {
            sensorManager?.unregisterListener(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "unregisterListener failed: ${t.message}")
        }
    }

    /**
     * Returns atomic snapshot of all sensor readings.
     */
    fun snapshot(): SensorSnapshot {
        val accelEvent = latestAccel.get()
        val accel = accelEvent ?: NO_EVENT
        val gyro = latestGyro.get() ?: NO_EVENT
        return SensorSnapshot(
            accelX = accel[0],
            accelY = accel[1],
            accelZ = accel[2],
            gyroX = gyro[0],
            gyroY = gyro[1],
            gyroZ = gyro[2],
            accelDelivered = accelEvent != null
        )
    }

    /**
     * Check if a specific sensor type is available on this device.
     */
    fun hasSensor(sensorType: Int): Boolean = try {
        sensorManager?.getDefaultSensor(sensorType) != null
    } catch (t: Throwable) {
        Log.w(TAG, "hasSensor($sensorType) failed: ${t.message}")
        false
    }

    /**
     * Get list of all available sensor types on this device.
     */
    fun getAvailableSensors(): List<Sensor> = try {
        sensorManager?.getSensorList(Sensor.TYPE_ALL) ?: emptyList()
    } catch (t: Throwable) {
        Log.w(TAG, "getAvailableSensors failed: ${t.message}")
        emptyList()
    }

    private fun registerSensor(sensorType: Int) {
        try {
            val sm = sensorManager ?: return
            val sensor = sm.getDefaultSensor(sensorType)
            if (sensor == null) {
                Log.w(TAG, "Sensor type $sensorType not available on this device")
                return
            }
            // registerListener returns false if the sensor refused the requested
            // sample rate (rare but observed on some vendor sensors that pin
            // themselves to a fixed cadence). Falling back to NORMAL keeps the
            // sampler producing data instead of leaving the channel at 0.
            val ok = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            if (!ok) {
                Log.w(TAG, "Sensor $sensorType refused FASTEST — retrying with NORMAL")
                runCatching {
                    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }.onFailure { Log.w(TAG, "Sensor $sensorType also refused NORMAL: ${it.message}") }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "registerSensor($sensorType) failed: ${t.message}")
        }
    }
}

data class SensorSnapshot(
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    /**
     * fieldsVersion 68: true once the accelerometer has delivered at least one
     * event. While false, [accelX]..[accelZ] are the placeholder zeros of "no
     * event yet" — not a reading, and not a motionless device.
     */
    val accelDelivered: Boolean = true
) {
    /**
     * |accel| = sqrt(x² + y² + z²) of a DELIVERED reading; `null` while the
     * accelerometer has delivered nothing (fieldsVersion 68, rule 22: the
     * stillness statistic must be computed from readings, never from the
     * zeros that stand in for their absence).
     */
    fun accelMagnitudeOrNull(): Float? =
        if (!accelDelivered) null
        else kotlin.math.sqrt((accelX * accelX + accelY * accelY + accelZ * accelZ).toDouble()).toFloat()
}
