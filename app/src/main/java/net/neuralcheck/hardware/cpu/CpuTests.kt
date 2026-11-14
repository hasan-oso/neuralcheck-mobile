package net.neuralcheck.hardware.cpu

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.service.collector.SystemFileReader
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

/**
 * CPU functional tests C-01 through C-07.
 */
class CpuTests @Inject constructor(
    private val systemFileReader: SystemFileReader,
    private val context: android.content.Context
) {
    /**
     * Fast, non-CPU-bound CPU tests — safe to run in parallel with other
     * test groups during Phase 1 init. C-04 is excluded here because the
     * benchmark is sensitive to contention; call [runBenchmark] separately.
     */
    fun runStaticTests(): List<TestResult> = listOf(
        testCoreCount(),            // C-01
        testMaxFrequency(),         // C-02
        testRestingFrequency(),     // C-03 (ADR: scaling_cur_freq at idle)
        testCpuModel()              // C-05
    )

    /**
     * Runs the C-04 matrix-multiply MFLOPS benchmark. Must be invoked on its
     * own (no other CPU-heavy tests in parallel) — otherwise contention from
     * storage/network/display/security test groups starves the benchmark
     * thread and the reported MFLOPS is artificially low (typically ~30–40%
     * of the device's actual capability).
     *
     * Dispatches onto Dispatchers.Default so the benchmark never runs on the
     * caller's main thread (StressTestService launches static tests from
     * lifecycleScope, whose default dispatcher IS Main — the benchmark
     * previously ran on Main and got throttled alongside UI work).
     */
    suspend fun runBenchmark(): TestResult = withContext(Dispatchers.Default) {
        testCpuBenchmark()
    }

    // C-01: Core Count
    private fun testCoreCount(): TestResult {
        val runtimeCores = Runtime.getRuntime().availableProcessors()
        val sysCores = systemFileReader.readCpuCoreCount()
        val match = runtimeCores == sysCores || sysCores == -1
        val status = when {
            runtimeCores >= 2 && match -> TestStatus.PASS
            runtimeCores == 1 -> TestStatus.FAIL
            else -> TestStatus.WARNING
        }
        val nameAr = context.getString(net.neuralcheck.R.string.test_c01_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_c01_tr)
        return TestResult("C-01", nameAr, nameTr, "cpu", status, null, "$runtimeCores", "cores", null)
    }

    // C-02: Max Frequency
    private fun testMaxFrequency(): TestResult {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val maxFreqs = (0 until coreCount).map { systemFileReader.readCpuMaxFrequency(it) }
        val maxFreq = maxFreqs.maxOrNull() ?: -1L
        val nameAr = context.getString(net.neuralcheck.R.string.test_c02_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_c02_tr)
        return if (maxFreq > 0) {
            val ghz = maxFreq / 1_000_000f
            TestResult("C-02", nameAr, nameTr, "cpu", TestStatus.PASS, null, String.format(Locale.US, "%.2f", ghz), "GHz", null)
        } else {
            TestResult("C-02", nameAr, nameTr, "cpu", TestStatus.UNAVAILABLE, null, "N/A", "", null)
        }
    }

    // C-03: Resting frequency — scaling_cur_freq averaged across cores before any stress
    // is applied. Per ADR 3.3, the expected baseline is *below* the per-core max: if the
    // governor is idling the CPU should be running at a low power state. Hitting the max
    // frequency at rest (suggesting a background process is pinning a core) is a WARNING.
    private fun testRestingFrequency(): TestResult {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val currents = (0 until coreCount).map { systemFileReader.readCpuFrequency(it) }
            .filter { it > 0 }
        val maxes = (0 until coreCount).map { systemFileReader.readCpuMaxFrequency(it) }
            .filter { it > 0 }
        val nameAr = context.getString(net.neuralcheck.R.string.test_c03_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_c03_tr)
        if (currents.isEmpty() || maxes.isEmpty()) {
            return TestResult("C-03", nameAr, nameTr, "cpu", TestStatus.UNAVAILABLE, null, "N/A", "", null)
        }
        val avgCurrent = currents.average()
        val maxOfMaxes = maxes.max()
        val ratio = avgCurrent / maxOfMaxes // how much of peak we are running at, 0.0-1.0
        val status = when {
            ratio < 0.8 -> TestStatus.PASS
            ratio < 0.95 -> TestStatus.WARNING
            else -> TestStatus.WARNING
        }
        val ghz = avgCurrent.toFloat() / 1_000_000f
        return TestResult("C-03", nameAr, nameTr, "cpu", status, null, String.format(Locale.US, "%.2f", ghz), "GHz", null)
    }

    // C-04: CPU computational benchmark — 64×64 matrix multiplication loop for 500ms.
    // Runs with a short JIT warmup before measurement so the result reflects the steady-state
    // JIT-compiled performance. rawValue is MFLOPS so the scoring engine can interpolate.
    private fun testCpuBenchmark(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_c04_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_c04_tr)
        return try {
            val mflops = runMflopsTest()
            val status = when {
                mflops >= 5000f -> TestStatus.PASS
                mflops >= 2000f -> TestStatus.WARNING
                else -> TestStatus.FAIL
            }
            val score = (mflops / 10000f * 100f).coerceIn(0f, 100f)
            TestResult("C-04", nameAr, nameTr, "cpu", status, score,
                String.format(Locale.US, "%.0f", mflops), "MFLOPS", null)
        } catch (e: Exception) {
            TestResult("C-04", nameAr, nameTr, "cpu", TestStatus.UNAVAILABLE, null, "N/A", "", null)
        }
    }

    private fun runMflopsTest(): Float {
        // 128×128 gives ~2M FMA ops per iteration — the inner-loop body
        // dominates loop/branch overhead, so the measured throughput tracks
        // the true CPU capability more closely. A 64×64 matrix spent a
        // disproportionate fraction of its time in loop bookkeeping and
        // reported 1300–1400 MFLOPS on devices known to deliver 4000+.
        val size = 128
        val a = FloatArray(size * size) { Random.nextFloat() }
        val b = FloatArray(size * size) { Random.nextFloat() }
        val c = FloatArray(size * size)

        // Lift the thread out of the service's default background priority
        // (set by ForegroundService). Without this the kernel consistently
        // throttles the benchmark thread and reports 30–40% of real throughput.
        val tid = android.os.Process.myTid()
        val prevPriority = runCatching { android.os.Process.getThreadPriority(tid) }
            .getOrDefault(android.os.Process.THREAD_PRIORITY_DEFAULT)
        runCatching {
            android.os.Process.setThreadPriority(tid, android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
        }
        try {
            // JIT warmup: 5 full 128×128 multiplications triggers tiered
            // compilation on ART without burning significant wall-clock time.
            repeat(5) { matMul(a, b, c, size) }

            val startNs = System.nanoTime()
            val testDurationNs = 600_000_000L // 600ms — enough iterations for a stable mean
            var iterations = 0L
            while ((System.nanoTime() - startNs) < testDurationNs) {
                matMul(a, b, c, size)
                iterations++
            }
            val elapsedNs = System.nanoTime() - startNs
            val opsPerIteration = size.toLong() * size * size * 2 // fused multiply-add = 2 ops
            val totalOps = iterations * opsPerIteration
            return (totalOps / (elapsedNs / 1_000_000_000.0) / 1_000_000).toFloat()
        } finally {
            runCatching { android.os.Process.setThreadPriority(tid, prevPriority) }
        }
    }

    private fun matMul(a: FloatArray, b: FloatArray, c: FloatArray, n: Int) {
        // ikj loop order with accumulation: the b[k*n + j] access pattern walks
        // a row of b for every increment of k, so it stays cache-hot across the
        // inner j loop. The original ijk order walked b by column and thrashed
        // the L1 on any n > ~48. Zero c up front so the += in the inner loop
        // starts from a clean state (otherwise values compound across calls).
        java.util.Arrays.fill(c, 0f)
        for (i in 0 until n) {
            val iN = i * n
            for (k in 0 until n) {
                val aik = a[iN + k]
                val kN = k * n
                for (j in 0 until n) {
                    c[iN + j] += aik * b[kN + j]
                }
            }
        }
    }

    // C-06: Thermal Throttling — computed from time-series (ADR 3.3 C-06).
    //
    // Methodology change (vs the original max-vs-min): we now compare the
    // first ~10 samples of the stress phase against the last ~10 samples,
    // matching the formula in BehaviorFingerprintBuilder.calculateStats so
    // the C-06 PDF figure and the AI's `throttlePercent` agree.
    //
    // The earlier max/min approach was overly punitive — a transient
    // 1-sample dip during a frequency-governor transition (very common
    // mid-scan on Snapdragon 855) registered as 80%+ throttle even when
    // the device was sustaining its target clock for 95% of the run.
    // Early-vs-late averages out that jitter and reflects true sustained
    // performance degradation.
    //
    // rawValue is the throttle percent as a plain number so the scoring
    // engine can interpolate against scoring_config.json#C-06 ("lower_better").
    fun computeThrottling(freqReadingsMhz: List<Float>): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_c06_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_c06_tr)
        // Need a minimum sample count so a too-short stress phase or a
        // device that produces noisy readings isn't fitted blindly.
        if (freqReadingsMhz.size < 20) {
            return TestResult("C-06", nameAr, nameTr, "cpu", TestStatus.UNAVAILABLE,
                null, "N/A", "%", null)
        }
        val early = freqReadingsMhz.take(10).map { it.toDouble() }.average()
        val late = freqReadingsMhz.takeLast(10).map { it.toDouble() }.average()
        val throttlePercent = if (early > 0.0) {
            ((1.0 - late / early) * 100.0).toFloat().coerceAtLeast(0f)
        } else 0f
        val status = when {
            throttlePercent < 20f -> TestStatus.PASS
            throttlePercent < 40f -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        return TestResult("C-06", nameAr, nameTr, "cpu", status, null,
            String.format(Locale.US, "%.1f", throttlePercent), "%", null)
    }

    // C-07: Performance Stability (ADR 3.3 C-07) — relative standard deviation
    // of the frequency samples during steady-state stress, reported as a plain
    // percentage so the scoring engine can interpolate against C-07 thresholds.
    fun computeStability(freqReadings: List<Float>): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_c07_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_c07_tr)
        if (freqReadings.size < 10) {
            return TestResult("C-07", nameAr, nameTr, "cpu", TestStatus.UNAVAILABLE, null, "N/A", "", null)
        }
        val mean = freqReadings.average().toFloat()
        val variance = freqReadings.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = kotlin.math.sqrt(variance)
        val relStdDev = if (mean > 0) (stdDev / mean) * 100f else 0f
        val status = when {
            relStdDev < 10f -> TestStatus.PASS
            relStdDev < 25f -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        // Phase 1.14: when every collected freq sample is the same value the
        // RSD computation is mathematically 0 but uninformative — it almost
        // always means the OS governor pinned the prime cluster to peak (the
        // S24 Ultra anomaly in scan b69da673), not that the device's
        // frequency is actually stable under load. The math + PASS status
        // are kept (they ARE technically correct), but we surface a note so
        // the report layer can show the caveat next to the misleading
        // "0.0 %RSD PASS". `distinct().size == 1` is the precise signal —
        // any spread (even one outlier) means the sampler IS picking up
        // changes, so the variance figure is informative.
        val note = if (freqReadings.distinct().size == 1)
            context.getString(net.neuralcheck.R.string.c07_constant_freq_note)
        else null
        return TestResult("C-07", nameAr, nameTr, "cpu", status, null,
            String.format(Locale.US, "%.1f", relStdDev), "%RSD", note)
    }

    // C-05 model: CPU Model/SoC info
    private fun testCpuModel(): TestResult {
        val model = if (Build.VERSION.SDK_INT >= 31) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        } else {
            "${Build.HARDWARE} / ${Build.BOARD}"
        }
        val nameAr = context.getString(net.neuralcheck.R.string.test_c05_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_c05_tr)
        return TestResult("C-05", nameAr, nameTr, "cpu", TestStatus.PASS, null, model, "", null)
    }
}
