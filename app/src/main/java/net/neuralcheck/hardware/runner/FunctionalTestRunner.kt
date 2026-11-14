package net.neuralcheck.hardware.runner

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import net.neuralcheck.BuildConfig
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.hardware.battery.BatteryTests
import net.neuralcheck.hardware.cpu.CpuTests
import net.neuralcheck.hardware.gpu.GpuTests
import net.neuralcheck.hardware.info.ExtendedInfoTests
import net.neuralcheck.hardware.storage.StorageTests
import net.neuralcheck.hardware.thermal.ThermalTests
import net.neuralcheck.hardware.memory.MemoryTests
import net.neuralcheck.hardware.network.NetworkTests
import net.neuralcheck.hardware.gps.GpsTests
import net.neuralcheck.hardware.sensor.SensorTests
import net.neuralcheck.hardware.camera.CameraTests
import net.neuralcheck.hardware.display.DisplayTests
import net.neuralcheck.hardware.security.SecurityTests
import javax.inject.Inject

/**
 * Orchestrates parallel execution of all functional test categories (ADR 5.13).
 */
class FunctionalTestRunner @Inject constructor(
    private val batteryTests: BatteryTests,
    private val cpuTests: CpuTests,
    private val gpuTests: GpuTests,
    private val storageTests: StorageTests,
    private val thermalTests: ThermalTests,
    private val memoryTests: MemoryTests,
    private val networkTests: NetworkTests,
    private val gpsTests: GpsTests,
    private val sensorTests: SensorTests,
    private val cameraTests: CameraTests,
    private val displayTests: DisplayTests,
    private val securityTests: SecurityTests,
    // Datacollect-only INFO-01. Always injected (Hilt graph stays
    // identical across flavors); whether the test actually runs is decided
    // by BuildConfig.ENABLE_EXTENDED_INFO_TESTS at the call site.
    private val extendedInfoTests: ExtendedInfoTests
) {
    /**
     * Run all static/instant tests in parallel.
     * Called during Phase 1 (init).
     *
     * C-04 (MFLOPS benchmark) runs SOLO before the parallel block because
     * it's CPU-bound and sensitive to contention — running it alongside
     * storage/network/display/security test groups makes it report ~30–40%
     * of the device's real throughput.
     */
    suspend fun runAllStaticTests(): List<TestResult> = supervisorScope {
        // Run the CPU benchmark SOLO first. It dispatches itself onto
        // Dispatchers.Default and is sensitive to CPU contention — launching
        // it alongside the parallel category tests below halves throughput.
        val benchmark = try {
            cpuTests.runBenchmark()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "Test 'cpu_benchmark' failed: ${t.message}")
            null
        }

        // Use supervisorScope so one failing category does not cancel the others.
        val results = mutableListOf(
            async { safeRun("battery") { batteryTests.runAll() } },
            async { safeRun("cpu_static") { cpuTests.runStaticTests() } },
            async { safeRun("gpu_static") { gpuTests.runStaticTests() } },
            async { safeRun("memory") { memoryTests.runAll() } },
            async { safeRun("network") { networkTests.runAll() } },
            async { safeRun("display") { displayTests.runAll() } },
            async { safeRun("security") { securityTests.runAll() } },
            async { safeRun("thermal_static") { thermalTests.runStaticTests() } }
        )
        if (BuildConfig.ENABLE_EXTENDED_INFO_TESTS) {
            // INFO-01 (CSC) — datacollect-only diagnostic.
            // Run alongside the other static categories; ExtendedInfoTests is
            // safe to call on launch builds too (returns nothing) but gating
            // here keeps the flag truth in one place.
            results += async { safeRun("extended_info") { extendedInfoTests.runAll() } }
        }
        val parallel = results.flatMap { it.await() }
        if (benchmark != null) parallel + benchmark else parallel
    }

    private suspend inline fun safeRun(
        tag: String,
        block: () -> List<TestResult>
    ): List<TestResult> = try {
        block()
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (t: Throwable) {
        Log.w("NeuralCheck", "Test group '$tag' failed: ${t.message}")
        emptyList()
    }

    /**
     * Run storage benchmarks (Phase 2).
     * Wrapped in safeRun so a single bad sector / IOException can't take
     * down the whole storage category — we'd rather ship empty results
     * than abort the entire scan over a partial-disk failure.
     */
    suspend fun runStorageTests(): List<TestResult> = safeRun("storage") { storageTests.runAll() }

    /**
     * Run sensor tests requiring data collection time.
     */
    suspend fun runSensorTests(): List<TestResult> = safeRun("sensor") { sensorTests.runAll() }

    /**
     * Run camera tests (Phase 4, at minute 6).
     */
    fun runCameraTests(): List<TestResult> = try {
        cameraTests.runAll()
    } catch (t: Throwable) {
        Log.w("NeuralCheck", "Test group 'camera' failed: ${t.message}")
        emptyList()
    }

    /**
     * Run GPS tests (Phase 4, at minute 7).
     */
    suspend fun runGpsTests(): List<TestResult> = safeRun("gps") { gpsTests.runAll() }

    /**
     * Compute time-series dependent tests after stress test completes.
     * Each test is independent: a single computation throwing (e.g. a
     * divide-by-zero on a degenerate time-series) must not lose every
     * other computed result. Each is wrapped in its own try and the
     * failed entries simply drop out of the returned list.
     *
     * G2/P2.3: G-03 (FPS drop), G-04 (GPU thermal throttle), and G-05
     * (min sustained FPS) used to be computed HERE from
     * gpuTests.computeFpsDropPercent/computeGpuThrottle/computeMinSustainedFps
     * — each a peak-picking (max/min FPS) plus a derived ratio over the
     * whole scan's GPU FPS history. That is exactly the irreversible
     * aggregation the raw-in/transform-on-server rule forbids doing in the
     * app. Removed; the app now ships only the raw v[10] gpu_fps time
     * series plus footer.phaseBoundaries (added in this same change — see
     * PhaseBoundary), and the server computes G-03/G-04/G-05 from those.
     * The maxFps/minFps/initialFps parameters this function used to take
     * are gone too — GpuTests.computeFpsDropPercent and friends were their
     * only callers; see GpuTests.kt.
     */
    fun computeTimeSeriesTests(
        startLevel: Int, endLevel: Int, durationMin: Float,
        maxTemp: Float, heatRise: Float, cooldown: Float, tempSource: String,
        @Suppress("UNUSED_PARAMETER") maxFreqFirst30s: Long,
        @Suppress("UNUSED_PARAMETER") minFreqStress: Long,
        freqReadings: List<Float>,
        @Suppress("UNUSED_PARAMETER") totalIterations: Long,
        @Suppress("UNUSED_PARAMETER") stressDurationMs: Long
    ): List<TestResult> {
        fun safe(tag: String, block: () -> TestResult): TestResult? = try {
            block()
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "computeTimeSeries '$tag' failed: ${t.message}")
            null
        }
        return listOfNotNull(
            safe("B-06") { batteryTests.computeDrainRate(startLevel, endLevel, durationMin) },
            // C-06 now uses early-vs-late stress samples for throttle % so
            // it matches BehaviorFingerprintBuilder.throttlePercent. The
            // legacy max/min args are kept on the outer signature only for
            // backwards-compatibility with the (single) caller in
            // StressTestService — they're intentionally unused here.
            safe("C-06") { cpuTests.computeThrottling(freqReadings) },
            safe("C-07") { cpuTests.computeStability(freqReadings) },
            safe("T-01") { thermalTests.computeMaxTemp(maxTemp, tempSource) },
            safe("T-02") { thermalTests.computeHeatRise(heatRise, tempSource) },
            safe("T-03") { thermalTests.computeCooldown(cooldown, tempSource) }
        )
    }
}
