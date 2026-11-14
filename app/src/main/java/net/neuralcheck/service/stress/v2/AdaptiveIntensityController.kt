package net.neuralcheck.service.stress.v2

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import net.neuralcheck.data.local.files.DeviceTier
import net.neuralcheck.util.StressNative
import net.neuralcheck.util.ThermalFallback

/**
 * V2 master plan §2.6 — adaptive intensity controller.
 *
 * Runs an 8-second NEON FMA mini-burst at the end of INIT_PROFILING
 * (seconds 22–30) and measures the rate of CPU + skin temperature
 * rise. Classifies the device by that rate, NOT by SoC name —  a
 * Snapdragon 8 Gen 3 with a clogged heat pipe behaves like a budget
 * device under stress and should be tested as such.
 *
 * Intensity factor multiplies stress-kernel iteration counts in every
 * subsequent phase; lower factor = fewer iterations = less aggressive
 * heat profile = a fairer test for a thermally-compromised device.
 *
 * Thresholds (per master plan §2.6):
 *   rate < 0.3 °C/s → flagship_cooling, factor 1.0
 *   rate 0.3–0.6   → mid_cooling,      factor 0.8
 *   rate > 0.6     → budget_cooling,   factor 0.65
 *
 * Returns UNKNOWN with factor 0.8 (mid default) when the probe can't
 * read temperatures — better to err on the conservative side than
 * over-stress an instrumented device.
 */
class AdaptiveIntensityController(private val context: Context) {

    companion object {
        private const val TAG = "NeuralCheck.V2.Adaptive"
        const val PROBE_DURATION_MS: Long = 8_000L
        private const val POLL_INTERVAL_MS: Long = 500L

        /**
         * Phase 1.6.E — pick the AI workload configuration for the
         * classified [DeviceTier]. Consumers ([AiCpuWorkload],
         * [AiGpuWorkload], [AiMemoryWorkload]) read this once at
         * phase start and use it to cap iteration rate / model size.
         *
         * V5's probe is currently always returning FLAGSHIP_COOLING
         * (V2 fix #30 disabled the local-judgement classification;
         * the AI on the backend grades cooling capacity from the raw
         * tempRiseRatePerSec). The mapping below is deliberately
         * exhaustive across all four tier values so future re-
         * enablement of local classification picks up the right
         * config without code changes.
         */
        fun aiConfigForTier(tier: DeviceTier): AiWorkloadConfig = when (tier) {
            DeviceTier.FLAGSHIP_COOLING -> AiWorkloadConfig(
                largeModelName = AiWorkloadConfig.MODEL_LARGE,
                smallModelName = AiWorkloadConfig.MODEL_SMALL,
                targetIterPerSec = 25,
                cpuModelChoice = AiWorkloadConfig.MODEL_LARGE,
                gpuModelChoice = AiWorkloadConfig.MODEL_LARGE
            )
            DeviceTier.MID_COOLING -> AiWorkloadConfig(
                largeModelName = AiWorkloadConfig.MODEL_LARGE,
                smallModelName = AiWorkloadConfig.MODEL_SMALL,
                targetIterPerSec = 18,
                cpuModelChoice = AiWorkloadConfig.MODEL_SMALL,
                gpuModelChoice = AiWorkloadConfig.MODEL_SMALL
            )
            DeviceTier.BUDGET_COOLING -> AiWorkloadConfig(
                largeModelName = AiWorkloadConfig.MODEL_LARGE,
                smallModelName = AiWorkloadConfig.MODEL_SMALL,
                targetIterPerSec = 10,
                cpuModelChoice = AiWorkloadConfig.MODEL_SMALL,
                gpuModelChoice = AiWorkloadConfig.MODEL_SMALL
            )
            DeviceTier.UNKNOWN -> AiWorkloadConfig(
                largeModelName = AiWorkloadConfig.MODEL_LARGE,
                smallModelName = AiWorkloadConfig.MODEL_SMALL,
                targetIterPerSec = 18,
                cpuModelChoice = AiWorkloadConfig.MODEL_LARGE,
                gpuModelChoice = AiWorkloadConfig.MODEL_LARGE
            )
        }
    }

    /** Last probe outcome — caller persists into TimeSeriesFooter. */
    data class ProbeResult(
        val tier: DeviceTier,
        val tempRiseRatePerSec: Float,
        val startTempC: Float,
        val endTempC: Float
    )

    /**
     * Runs the probe. Suspending — sleeps in 500ms ticks while the
     * native NEON FMA loop keeps a single big-cluster core busy. We
     * sample temperature at start and end and divide.
     *
     * The "FMA mini-burst" reuses the existing runNeonFmaTest entry
     * point (which runs scalar + NEON for ~150 ms each call). We loop
     * it until [PROBE_DURATION_MS] elapses; each iteration touches
     * the FMA pipeline + L1/L2 + memory subsystem, generating real
     * heat without the full stress phase's overhead.
     */
    suspend fun probe(): ProbeResult {
        val startTemp = readBestTemp()
        val deadline = System.currentTimeMillis() + PROBE_DURATION_MS

        // V2 fix #30: probe الآن يستخدم نفس start_cpu_stress لضمان
        // CPU pinning على big cores. كان يستخدم runNeonFmaTest (smoke
        // entry بدون pinning متعدد الأنوية)، فالحرارة لم ترتفع،
        // النتيجة: tempRiseRateInProbe = 0 و tier = flagship_cooling
        // خطأً. الآن probe يحصل على نفس workload الفعلي للـ stress
        // phases — workers على big+mid clusters لـ 8 ثوانٍ كاملة.
        val startCode = runCatching { StressNative.startCpuStress(1.0f, PROBE_DURATION_MS) }
            .getOrDefault(-1L)
        if (startCode != 0L) {
            Log.w(TAG, "probe: startCpuStress returned $startCode — probe will measure idle drift only")
        }

        try {
            while (System.currentTimeMillis() < deadline) {
                delay(POLL_INTERVAL_MS)  // yields scheduler so safety monitor can run
            }
        } finally {
            // Always reap workers — even if startCpuStress returned an
            // error code, stopCpuStress is idempotent (runs prime sieve
            // probe internally but otherwise no-ops on empty pool).
            runCatching { StressNative.stopCpuStress() }
        }

        val endTemp = readBestTemp()
        val rate = if (PROBE_DURATION_MS > 0L) {
            (endTemp - startTemp) / (PROBE_DURATION_MS / 1000f)
        } else 0f

        // V2 fix #30: لا نُصنّف tier. كل الأجهزة تحصل على intensity 1.0.
        // الفلسفة: العيوب تظهر تحت الضغط الكامل. تخفيف الضغط للأجهزة
        // الضعيفة يُخفي عيوباً حقيقية ويُنتج false negative. الحماية
        // تأتي من Pre-flight checks + SafetyMonitor + Throttler.
        //
        // tempRiseRate ما زال يُحسب ويُرسل خاماً للـ AI كقياس باسلاين.
        // الـ AI يحكم بنفسه على قدرة التبريد من الأرقام الخام، بدون
        // حكم محلي من الـ probe.
        val tier = DeviceTier.FLAGSHIP_COOLING

        Log.i(
            TAG,
            "probe: startTemp=$startTemp°C endTemp=$endTemp°C " +
                "rate=${"%.3f".format(rate)}°C/s → tier=${tier.tag} factor=${tier.intensityFactor}"
        )

        return ProbeResult(
            tier = tier,
            tempRiseRatePerSec = rate,
            startTempC = startTemp,
            endTempC = endTemp
        )
    }

    /** Best-available temperature — prefers CPU, falls back to skin
     *  then battery. Uses [ThermalFallback] so Samsung One UI / MIUI /
     *  ColorOS (where /sys/class/thermal is SELinux-blocked) still
     *  produce a usable rate-of-change signal via BatteryManager +
     *  PowerManager.getCurrentThermalStatus. Returns 0f only when
     *  every source fails. */
    private fun readBestTemp(): Float {
        val zones = ThermalFallback.read(context)
        if (zones.size < StressNative.THERMAL_COUNT) return 0f
        val cpu = zones[StressNative.THERMAL_CPU]
        val skin = zones[StressNative.THERMAL_SKIN]
        val batt = zones[StressNative.THERMAL_BATTERY]
        return when {
            cpu > 0f -> cpu
            skin > 0f -> skin
            batt > 0f -> batt
            else -> 0f
        }
    }
}
