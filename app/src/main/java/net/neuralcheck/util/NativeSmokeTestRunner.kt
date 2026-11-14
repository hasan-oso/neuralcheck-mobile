package net.neuralcheck.util

import android.os.SystemClock
import android.util.Log

/**
 * V2 sub-phase 1h: integration smoke test skeleton.
 *
 * Runs every native verification entry point in a single ordered batch
 * and reports a structured result (per-kernel booleans + per-kernel
 * elapsed milliseconds + an "all pass" verdict). This is the
 * skeleton-service pattern that V2's full StressTestService will adopt
 * — orchestrate kernels, collect results, hand them to the verifier —
 * minus the actual stress phases, sampling, safety monitor, etc. The
 * sole purpose right now is to prove the JNI bridge end-to-end:
 *
 *   - Library loads
 *   - Each external function reachable
 *   - Each kernel produces its reference value (or matches the
 *     deterministic in-process reference for FP32)
 *   - No native crashes / signals propagate to the JVM
 *
 * This runner is deliberately stateless and side-effect-free apart
 * from logcat. It can be called from NeuralCheckApp.onCreate (the
 * current call site), from a future StressTestService boot path, or
 * from a debug UI without coordinating shared state.
 */
object NativeSmokeTestRunner {
    private const val TAG = "NeuralCheck.Smoke"

    /**
     * Per-kernel outcome. [elapsedMs] is wall-clock time spent in the
     * native call; useful as a first hint at thermal/CPU pressure on
     * cold-start (a 600 ms memory test that starts taking 1500 ms is
     * the canary for thermal throttling outside our control).
     */
    data class KernelResult(
        val name: String,
        val passed: Boolean,
        val elapsedMs: Long
    )

    /** Aggregate result from [runAll]. [allPassed] is true iff every
     *  kernel verified successfully — excluding [affinity], whose
     *  failure is documented degraded mode (Q2.6) and not a defect. */
    data class SmokeReport(
        val libraryLoaded: Boolean,
        val kernels: List<KernelResult>,
        val totalElapsedMs: Long,
        val allPassed: Boolean
    )

    /**
     * Runs every kernel in the order CPU work → memory work → sysfs
     * probes → affinity. Order is not strictly enforced for kernels
     * that don't share state, but keeping it stable makes Logcat
     * diffing across runs / devices straightforward.
     *
     * Returns a [SmokeReport] regardless of any kernel failing — the
     * runner never throws (every native call is already wrapped in
     * runCatching by its verify* helper). The caller decides what to
     * do with partial failures.
     */
    fun runAll(): SmokeReport {
        val loaded = StressNative.isLoaded()
        if (!loaded) {
            Log.e(TAG, "runAll: libstress.so not loaded — skipping every kernel")
            return SmokeReport(
                libraryLoaded = false,
                kernels = emptyList(),
                totalElapsedMs = 0L,
                allPassed = false
            )
        }

        val totalStart = SystemClock.elapsedRealtime()
        val kernels = mutableListOf<KernelResult>()

        kernels += time("ping")          { StressNative.verifyPing() }
        kernels += time("primeSieve")    { StressNative.verifyPrimeSieve() }
        kernels += time("sha256")        { StressNative.verifySha256() }
        kernels += time("neonFma")       { StressNative.verifyNeonFma() }
        kernels += time("memory")        { StressNative.verifyMemory() }
        kernels += time("thermalZones")  { StressNative.verifyThermalZones() }
        kernels += time("affinity")      { StressNative.verifyAffinity() }

        val totalElapsed = SystemClock.elapsedRealtime() - totalStart

        // affinity AND thermalZones failures are documented degraded
        // modes — both are blocked by SELinux policies on Samsung One
        // UI 11+ / MIUI / ColorOS (see ThermalFallback for the thermal
        // workaround that V2 components use). Exclude them from the
        // all-passed verdict so a Samsung device doesn't get falsely
        // flagged as defective for vendor security choices.
        val allPassed = kernels
            .filter { it.name != "affinity" && it.name != "thermalZones" }
            .all { it.passed }

        Log.i(
            TAG,
            "runAll: total=${totalElapsed}ms allPassed=$allPassed " +
                kernels.joinToString(separator = " ") { "${it.name}=${if (it.passed) "✓" else "✗"}/${it.elapsedMs}ms" }
        )
        return SmokeReport(
            libraryLoaded = true,
            kernels = kernels,
            totalElapsedMs = totalElapsed,
            allPassed = allPassed
        )
    }

    private inline fun time(name: String, block: () -> Boolean): KernelResult {
        val start = SystemClock.elapsedRealtime()
        val passed = try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "runAll: kernel '$name' threw — ${t.javaClass.simpleName}: ${t.message}", t)
            false
        }
        return KernelResult(name = name, passed = passed, elapsedMs = SystemClock.elapsedRealtime() - start)
    }
}
