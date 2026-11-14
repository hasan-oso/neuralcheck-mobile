package net.neuralcheck.calibration.probes

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.neuralcheck.service.inference.Delegate
import net.neuralcheck.service.inference.InferenceEngine
import net.neuralcheck.service.inference.InferenceEngineImpl

/**
 * Phase 1.8 — TFLite inference throughput probe.
 *
 * Mirrors `net.neuralcheck.service.stress.v2.AiCpuWorkload` exactly:
 * same model file (`mobilenet_v3_large_224.tflite`), same per-worker
 * `InferenceEngineImpl` (which sets `numThreads = cores` and forces
 * `Delegate.CPU`; XNNPACK is implicitly enabled on TFLite 2.16's CPU
 * path for ARM), same parallel worker count
 * `(Runtime.availableProcessors() - 1).coerceAtLeast(2)`.
 *
 * Reusing the production stack is the whole point — the probe predicts
 * phase performance only when probe and phase use the same setup.
 *
 * ### Test strategy
 * No JVM unit test. TFLite needs the native `libtensorflowlite_jni.so`
 * which isn't on the test classpath; the existing
 * [InferenceEngineSmokeTest][net.neuralcheck.service.inference.InferenceEngineSmokeTest]
 * exercises the wrapper in STUB mode — that's the closest-to-meaningful
 * coverage available without an instrumented test. Real measurement
 * validation happens in Phase 1.8.E hardware testing on Samsung S10 5G
 * (and any other device the lab adds to the matrix).
 *
 * ### Failure modes (all propagate to ProbePhase, which tags `"cpu"`)
 *  - Model file missing from assets       → InferenceEngineImpl falls to STUB
 *    (stub measures the wrapper itself, not the device — still produces
 *    a number, just an unrealistic one. Caller should treat
 *    `activeDelegate == "STUB"` as a probe failure separately if needed.)
 *  - Native TFLite lib missing for ABI    → STUB path, same as above
 *  - OOM during interpreter construction  → propagates as Throwable
 *  - Coroutine cancellation               → propagates; finally block closes engines
 */
object CpuProbe {

    private const val TAG = "NeuralCheck.Probe.Cpu"
    /** Same model AiCpuWorkload uses by default. Hard-coded so probe and
     *  phase can NEVER drift. If AdaptiveIntensityController later picks
     *  a different model per tier, the probe must be adjusted in lock-step. */
    private const val MODEL_NAME: String = "mobilenet_v3_large_224.tflite"

    /** 1 second of inferences before the measurement window starts.
     *  TFLite's first-inference cost includes XNNPACK partitioning +
     *  weight materialisation; counting it would underreport steady-state
     *  throughput by 10-30 %. */
    private const val WARMUP_NS: Long = 1_000_000_000L

    /** 5 seconds of timed inferences. Long enough to average over JIT
     *  recompiles and sampler scheduling jitter; short enough that the
     *  total scan-start probe budget stays under ~30 s. */
    private const val MEASUREMENT_NS: Long = 5_000_000_000L
    private const val MEASUREMENT_SECONDS: Double = 5.0

    data class Result(
        val inferencesPerSec: Double,
        val durationMs: Long,
        val inferencesCompleted: Long
    )

    /**
     * Run the probe. Suspend to match the rest of the V2 service stack;
     * actual heavy work runs on `Dispatchers.Default` workers.
     */
    suspend fun measure(context: Context): Result = withContext(Dispatchers.Default) {
        // Match AiCpuWorkload: cores - 1 keeps the OS scheduler with one
        // core free for the sampler / safety monitor / driving coroutine.
        // On Samsung S10 5G (8 cores) this resolves to 7. On a 4-core
        // budget device it resolves to 3 — and the probe + phase are still
        // consistent because production uses the same formula.
        val workerCount = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2)

        val startNs = System.nanoTime()
        val warmupEndNs = startNs + WARMUP_NS
        val measurementEndNs = warmupEndNs + MEASUREMENT_NS

        Log.i(
            TAG,
            "CpuProbe.measure: workerCount=$workerCount model=$MODEL_NAME " +
                "warmupMs=${WARMUP_NS / 1_000_000} measurementMs=${MEASUREMENT_NS / 1_000_000}"
        )

        val perWorkerCounts: List<Long> = coroutineScope {
            (0 until workerCount).map { workerId ->
                async(Dispatchers.Default) {
                    runWorker(workerId, context, warmupEndNs, measurementEndNs)
                }
            }.map { it.await() }
        }

        val totalInferences = perWorkerCounts.sum()
        val durationMs = (System.nanoTime() - startNs) / 1_000_000L
        val inferencesPerSec = totalInferences.toDouble() / MEASUREMENT_SECONDS

        Log.i(
            TAG,
            "CpuProbe.measure done: totalInferences=$totalInferences " +
                "inferencesPerSec=${"%.2f".format(inferencesPerSec)} " +
                "durationMs=$durationMs"
        )

        Result(
            inferencesPerSec = inferencesPerSec,
            durationMs = durationMs,
            inferencesCompleted = totalInferences
        )
    }

    /**
     * One worker = one `InferenceEngineImpl` running back-to-back
     * inferences. Counts only inferences that complete during the
     * measurement window — warmup inferences are run but not counted.
     *
     * `engine.runInference` is wrapped in `runCatching` per AiCpuWorkload's
     * pattern: a worker that hits an `InternalError` mid-loop on a
     * degraded device records its partial count and stops; the others
     * keep running so the aggregate stays meaningful.
     */
    private suspend fun runWorker(
        workerId: Int,
        context: Context,
        warmupEndNs: Long,
        measurementEndNs: Long
    ): Long = withContext(Dispatchers.Default) {
        val engine: InferenceEngine = InferenceEngineImpl(context)
        engine.loadModel(MODEL_NAME, Delegate.CPU)
        val delegate = engine.getActiveDelegate()
        Log.d(TAG, "worker $workerId: delegate=$delegate")

        // Phase 1.8 Task 5: STUB-delegate guard. When the TFLite native
        // lib + asset are wired correctly the active delegate resolves
        // to "CPU"; "STUB" means InferenceEngineImpl fell into its
        // wrapper-only path (model file missing, native lib absent for
        // this ABI, JVM unit-test classpath, etc.). A STUB worker
        // returns synthetic timings unrelated to device capability —
        // letting it count would over-promise on calibration.
        // Throwing aborts this worker; coroutineScope in measure()
        // cancels its siblings; ProbePhase's runCatchingProbe catches
        // the IllegalStateException and tags "cpu" as failed so
        // WorkloadCalibration falls back to its CPU floor.
        if (delegate == "STUB") {
            engine.close()
            throw IllegalStateException(
                "CpuProbe worker $workerId: TFLite XNNPACK fell to STUB delegate; " +
                    "probe value would not reflect device capability"
            )
        }

        val input = FloatArray(InferenceEngineImpl.INPUT_SIZE) { (it and 0xFF) / 255f }
        var measurementCount = 0L
        try {
            // Warmup phase: run, but do NOT count.
            while (System.nanoTime() < warmupEndNs) {
                runCatching { engine.runInference(input) }
                    .onFailure { /* logged below if it persists */ }
            }
            // Measurement phase: count successful inferences.
            while (System.nanoTime() < measurementEndNs) {
                val ok = runCatching { engine.runInference(input) }
                if (ok.isFailure) {
                    Log.w(
                        TAG,
                        "worker $workerId iter=$measurementCount inference threw " +
                            "${ok.exceptionOrNull()?.javaClass?.simpleName}: " +
                            "${ok.exceptionOrNull()?.message}"
                    )
                    break
                }
                measurementCount++
            }
        } finally {
            engine.close()
        }
        measurementCount
    }
}
