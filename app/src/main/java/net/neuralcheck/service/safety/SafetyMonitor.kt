package net.neuralcheck.service.safety

import android.util.Log
import net.neuralcheck.service.collector.BatteryReader
import net.neuralcheck.service.collector.SystemFileReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Safety monitor running every 250ms to enforce 4 safety rules (ADR 4.11):
 * 1. Battery temp > 45°C → STOP
 * 2. CPU temp > 90°C → STOP
 * 3. Battery drain > 15% in 3 minutes → STOP
 * 4. Battery level < 10% → STOP
 */
class SafetyMonitor(
    private val batteryReader: BatteryReader,
    private val systemFileReader: SystemFileReader
) {
    companion object {
        private const val TAG = "NeuralCheck"
        private const val CHECK_INTERVAL_MS = 250L
        private const val BATTERY_TEMP_LIMIT = 45f
        // V2 fix #47: رفع من 80°C إلى 97°C.
        //
        // السبب: 80°C صارم جداً. SoCs تحت stress تصل 70-85°C طبيعياً
        // والثابت السابق كان يُنهي الفحص قبل بدئه فعلياً على الأجهزة
        // التي تقرأ cpu_temp بنجاح (S10 لا يقرأها فيستخدم battery_temp
        // كـ fallback، بطيء، لا يُحفّز الـ limit).
        //
        // 97°C: SoCs تخنق ذاتياً 85-95°C، الفحص يستفيد من رؤية
        // throttling حقيقي. SafetyMonitor يتدخل فقط عند 97°C+
        // كـ catastrophic safety net. 100°C+ = ضرر دائم.
        private const val CPU_TEMP_LIMIT = 97f
        private const val DRAIN_LIMIT_PERCENT = 15
        private const val DRAIN_WINDOW_MS = 3 * 60 * 1000L // 3 minutes
        private const val MIN_BATTERY_LEVEL = 10
        private const val LEVEL_HISTORY_SIZE = 12 // 12 entries at 250ms intervals within 3min
    }

    private var executor: ScheduledExecutorService? = null
    private var onSafetyStop: ((reason: String) -> Unit)? = null
    private val levelHistory = ArrayDeque<Pair<Long, Int>>(LEVEL_HISTORY_SIZE)
    private val startBatteryLevel = AtomicInteger(100)
    private val triggered = AtomicBoolean(false)

    fun start(initialLevel: Int, callback: (String) -> Unit) {
        startBatteryLevel.set(initialLevel)
        onSafetyStop = callback
        triggered.set(false)
        levelHistory.clear()

        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "NeuralCheck-Safety").apply { isDaemon = true }
        }
        executor?.scheduleAtFixedRate(
            { checkSafety() },
            CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
        onSafetyStop = null
    }

    fun isTriggered(): Boolean = triggered.get()

    private fun checkSafety() {
        if (triggered.get()) return

        try {
            val battery = batteryReader.snapshot()

            // Rule 1: Battery temp > 45°C
            if (battery.tempCelsius > BATTERY_TEMP_LIMIT) {
                triggerStop("battery_temp_critical")
                return
            }

            // Rule 2: CPU temp > 90°C
            val cpuTemp = systemFileReader.readCpuTemp()
            if (cpuTemp > CPU_TEMP_LIMIT) {
                triggerStop("cpu_temp_critical")
                return
            }

            // Rule 3: Battery drain > 15% in 3 minutes
            updateDrainHistory(battery.levelPercent)
            if (checkExcessiveDrain()) {
                triggerStop("excessive_drain")
                return
            }

            // Rule 4: Battery level < 10%
            if (battery.levelPercent in 0 until MIN_BATTERY_LEVEL) {
                triggerStop("battery_level_critical")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Safety check error: ${e.message}")
        }
    }

    private fun updateDrainHistory(currentLevel: Int) {
        val now = System.currentTimeMillis()
        levelHistory.addLast(Pair(now, currentLevel))

        // Remove entries older than 3 minutes
        while (levelHistory.isNotEmpty() &&
               now - levelHistory.first().first > DRAIN_WINDOW_MS) {
            levelHistory.removeFirst()
        }
    }

    private fun checkExcessiveDrain(): Boolean {
        if (levelHistory.size < 2) return false

        val oldest = levelHistory.first()
        val newest = levelHistory.last()
        val timeDiff = newest.first - oldest.first

        // Only check if we have at least 1 minute of data
        if (timeDiff < 60_000L) return false

        val drain = oldest.second - newest.second
        // Extrapolate to 3 minutes
        val drainPerThreeMin = (drain.toFloat() / timeDiff) * DRAIN_WINDOW_MS
        return drainPerThreeMin > DRAIN_LIMIT_PERCENT
    }

    private fun triggerStop(reason: String) {
        if (triggered.compareAndSet(false, true)) {
            Log.w(TAG, "Safety stop triggered: $reason")
            onSafetyStop?.invoke(reason)
        }
    }
}
