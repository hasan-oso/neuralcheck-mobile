package net.neuralcheck.calibration.probes

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.neuralcheck.calibration.DeviceCapabilityProfile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.8 — runs the four capability probes in sequence and produces
 * one [DeviceCapabilityProfile]. Per-probe failures are isolated: any
 * `Throwable` (including `NotImplementedError` from the stubbed CPU/GPU
 * probes in Task 2a) downgrades that subsystem to `0.0` in the result
 * and adds its short identifier to `probeFailedSubsystems`. The other
 * probes still run.
 *
 * Hilt-injected (`@Singleton`, constructor-injected
 * `@ApplicationContext`) to match the rest of the V2 service / workload
 * layer (`AiCpuWorkload`, `AiGpuWorkload`, `AiMemoryWorkload`).
 *
 * `run()` is `suspend` because:
 *   - the silent-period delay uses `kotlinx.coroutines.delay` (matches
 *     `StressTestServiceV2`'s coroutine-based phase orchestration), and
 *   - the storage + memory probes block their thread, so the call site
 *     wraps each in `withContext(Dispatchers.Default)` to keep the main
 *     thread responsive.
 */
@Singleton
class ProbePhase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NeuralCheck.Probe"
        // TODO(phase-1.8.future): replace this 5 s placeholder with real
        // silent-period detection (CPU < N%, no foreground notifications,
        // network idle). For now we just sleep — Phase 1.8 Task 2a only
        // delivers the probe primitives; the silent-period detector is a
        // future task once we know what signals are reliable across
        // HyperOS / OneUI / ColorOS.
        private const val SILENT_PERIOD_MS: Long = 5_000L
    }

    /**
     * Execute the four probes and return an aggregate
     * [DeviceCapabilityProfile]. The CPU + GPU probes are stubs in Task
     * 2a and will throw [NotImplementedError] — caught and tagged as
     * failed via `probeFailedSubsystems`. Memory + Storage produce real
     * values.
     *
     * @param workDir directory the storage probe writes its 256 MB temp
     *        file into. Production caller passes `context.cacheDir`;
     *        unit tests pass a JUnit `TemporaryFolder` root.
     */
    suspend fun run(workDir: File): DeviceCapabilityProfile {
        val probeStartedAtMs = System.currentTimeMillis()

        // TODO(phase-1.8.future): replace placeholder silent-period sleep.
        delay(SILENT_PERIOD_MS)

        val failed = mutableListOf<String>()

        // Wrap each probe in Dispatchers.Default so blocking I/O / native
        // copies don't run on the caller's main thread. Each probe is
        // already designed as a one-shot pure function — the dispatcher
        // change is purely about thread affinity.
        val memory = runCatchingProbe("memory", failed) {
            withContext(Dispatchers.Default) { MemoryProbe.measure() }
        }
        val cpu = runCatchingProbe("cpu", failed) {
            withContext(Dispatchers.Default) { CpuProbe.measure(context) }
        }
        val gpu = runCatchingProbe("gpu", failed) {
            withContext(Dispatchers.Default) { GpuProbe.measure(context) }
        }
        val storage = runCatchingProbe("storage", failed) {
            withContext(Dispatchers.IO) { StorageProbe.measure(workDir) }
        }

        return DeviceCapabilityProfile(
            memoryMbps = memory?.mbps ?: 0.0,
            cpuInferencesPerSec = cpu?.inferencesPerSec ?: 0.0,
            gpuFpsAt1024Sq = gpu?.fpsAt1024Sq ?: 0.0,
            storageMbps = storage?.mbps ?: 0.0,
            probeStartedAtMs = probeStartedAtMs,
            probeDurationMs = System.currentTimeMillis() - probeStartedAtMs,
            probeFailedSubsystems = failed.toList()
        )
    }

    /**
     * Run [block]; on any [Throwable] log + add `name` to [failed] +
     * return null. Inline so the caller's lambda stays a regular suspend
     * lambda (cross-inline isn't needed; the lambda runs once).
     */
    private suspend inline fun <T> runCatchingProbe(
        name: String,
        failed: MutableList<String>,
        block: () -> T
    ): T? = try {
        block()
    } catch (t: Throwable) {
        Log.w(
            TAG,
            "Probe '$name' failed: ${t.javaClass.simpleName}: ${t.message}"
        )
        failed += name
        null
    }
}
