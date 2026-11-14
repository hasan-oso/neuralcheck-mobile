package net.neuralcheck.service.stress.v2

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.neuralcheck.service.inference.Delegate
import net.neuralcheck.service.inference.InferenceEngine
import net.neuralcheck.service.inference.InferenceEngineImpl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.6.B — AI-camouflage replacement for the synthetic CPU stress
 * kernel (NEON FMA + SHA-256 + prime sieve).
 *
 * Phase 3 (CPU_SUSTAINED) and the CPU half of Phase 5 (COMBINED_PEAK)
 * call [run] to keep the big + mid clusters loaded for the phase
 * duration. Each parallel worker holds its own [InferenceEngineImpl]
 * with `Delegate.CPU` forced — TFLite's `Interpreter` is not thread-
 * safe, so sharing one engine across workers would serialise inference
 * back to a single thread (and surrender the multi-core stress that
 * matters for thermal pressure + voltage sag).
 *
 * Strategic goal: the OS governor sees N parallel TFLite inference
 * loops, recognises this as a legitimate AI workload (image
 * classification is the de-facto fingerprint), and does not aggressively
 * cap CPU frequency. On HyperOS / ColorOS this is the difference
 * between phase 3 actually exercising the silicon and phase 3 sitting
 * at the OS-imposed 60 % cap.
 */
@Singleton
class AiCpuWorkload @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NeuralCheck.Inference.Cpu"

        /** Default model when no [AiWorkloadConfig] is supplied (test
         *  / instrumentation paths). Production callers pass an
         *  [AiWorkloadConfig.cpuModelChoice] from
         *  [AdaptiveIntensityController.aiConfigForTier]. */
        private const val DEFAULT_MODEL_NAME = "mobilenet_v3_large_224.tflite"
    }

    /**
     * Run continuous CPU-delegate inference for [durationMs]. Returns
     * the aggregated [WorkloadResult] across all parallel workers.
     *
     * @param durationMs phase wall-clock budget. Workers sample
     *                   System.nanoTime() and stop crossing the
     *                   deadline — there is no hard cancel here, so
     *                   the run terminates within one inference of the
     *                   deadline (~10–60 ms slop).
     * @param intensityFactor 0.65/0.8/1.0 from [AdaptiveIntensityController].
     *                        Currently only used to scale the inter-
     *                        inference sleep — at 1.0 the workers spin
     *                        flat-out, at 0.65 they sleep ~30 % of the
     *                        time. Stage E will replace this with model
     *                        size + iter-per-second targets.
     * @return Aggregated counters across workers.
     */
    suspend fun run(
        durationMs: Long,
        intensityFactor: Float,
        config: AiWorkloadConfig? = null
    ): WorkloadResult {
        if (durationMs <= 0L) {
            return WorkloadResult(0, 0f, 0L, 0)
        }

        val startNs = System.nanoTime()
        val deadlineNs = startNs + durationMs * 1_000_000L

        // One worker per (cores - 1) so the OS scheduler keeps a core
        // free for the sampler / safety monitor / driving coroutine.
        // Match the NEON FMA kernel's pinning strategy: big + mid
        // clusters are the heat-generating cores, little is left alone.
        val workerCount = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2)
        val modelName = config?.cpuModelChoice ?: DEFAULT_MODEL_NAME
        // Phase 1.6.E: tier-aware iter cap. targetIterPerSec sets a
        // ceiling on each worker's throughput by computing the
        // matching inter-inference sleep. intensityFactor still
        // applies multiplicatively so the legacy 0.65/0.8/1.0 path
        // continues working when no config is provided.
        val sleepMs: Long = if (config != null && config.targetIterPerSec > 0) {
            (1000L / config.targetIterPerSec).coerceAtLeast(0L)
        } else {
            ((1.0f - intensityFactor.coerceIn(0f, 1f)) * 30f).toLong()
        }
        Log.i(
            TAG,
            "AiCpuWorkload.run: durationMs=$durationMs intensity=$intensityFactor " +
                "workers=$workerCount model=$modelName sleepMs=$sleepMs"
        )

        // Phase 1.15 (Fix 2): early STUB guard. Probe the CPU delegate ONCE
        // before spawning the worker fleet. If TFLite can't construct a real
        // CPU interpreter on this device, getActiveDelegate() resolves to
        // "STUB" — every worker would then just Thread.sleep(10ms) for the
        // whole phase (~10% CPU, zero real load: the Honor X9d failure mode).
        // Mirror CpuProbe's STUB guard: don't let it pass silently. Throw a
        // StubDelegateException so StressTestServiceV2 can fall back to the
        // NEON stress kernels for the remainder of the phase. Detected within
        // one model load (~hundreds of ms) rather than at the phase deadline,
        // so nearly the whole budget is left for the fallback.
        val probe: InferenceEngine = InferenceEngineImpl(context)
        probe.loadModel(modelName, Delegate.CPU)
        val probeDelegate = probe.getActiveDelegate()
        val probeInitError = probe.getDelegateInitError()
        probe.close()
        if (probeDelegate == "STUB") {
            Log.w(
                TAG,
                "AiCpuWorkload.run: CPU delegate probe resolved to STUB " +
                    "(initError=$probeInitError) — signalling NEON fallback"
            )
            throw StubDelegateException(probeDelegate, probeInitError)
        }

        val results = coroutineScope {
            (0 until workerCount).map { workerId ->
                async(Dispatchers.Default) {
                    runWorker(workerId, deadlineNs, sleepMs, modelName)
                }
            }.map { it.await() }
        }

        val totalIters = results.sumOf { it.iterations }
        val activeMsSum = results.sumOf { it.activeMs }
        val avgInferenceMs = if (totalIters > 0) {
            activeMsSum.toFloat() / totalIters.toFloat()
        } else 0f
        val phaseDurationMs = (System.nanoTime() - startNs) / 1_000_000L

        val activeDelegate = results.firstOrNull()?.delegate ?: "NONE"
        Log.i(
            TAG,
            "AiCpuWorkload.run done: workers=$workerCount totalIters=$totalIters " +
                "avgInferenceMs=${"%.1f".format(avgInferenceMs)} delegate=$activeDelegate " +
                "phaseDurationMs=$phaseDurationMs"
        )

        return WorkloadResult(
            iterations = totalIters,
            avgInferenceTimeMs = avgInferenceMs,
            phaseDurationMs = phaseDurationMs,
            workerCount = workerCount,
            activeDelegate = activeDelegate
        )
    }

    private data class WorkerResult(
        val iterations: Int,
        val activeMs: Long,
        val delegate: String
    )

    /**
     * One worker = one Interpreter on Delegate.CPU running back-to-back
     * inferences until the shared deadline. Catches any per-worker
     * failure rather than crashing the whole phase — a degraded device
     * can produce wildly inconsistent latencies and the kernel
     * occasionally trips an InternalError; we record the partial count
     * and let the others keep running.
     */
    private suspend fun runWorker(
        workerId: Int,
        deadlineNs: Long,
        sleepMs: Long,
        modelName: String
    ): WorkerResult = withContext(Dispatchers.Default) {
        val engine: InferenceEngine = InferenceEngineImpl(context)
        engine.loadModel(modelName, Delegate.CPU)
        val delegate = engine.getActiveDelegate()
        Log.d(TAG, "worker $workerId: delegate=$delegate")

        // Reusable input. Workers don't need different inputs — the
        // GPU fragment shader doesn't either, and what matters here is
        // the ALU + cache pressure, not the output's information value.
        val input = FloatArray(InferenceEngineImpl.INPUT_SIZE) { (it and 0xFF) / 255f }

        var iter = 0
        var activeMs = 0L
        try {
            while (System.nanoTime() < deadlineNs && isActive) {
                val tStart = System.nanoTime()
                runCatching { engine.runInference(input) }
                    .onFailure {
                        Log.w(TAG, "worker $workerId iter=$iter inference threw " +
                            "${it.javaClass.simpleName}: ${it.message}")
                    }
                activeMs += (System.nanoTime() - tStart) / 1_000_000L
                iter++
                if (sleepMs > 0L) {
                    delay(sleepMs)
                }
            }
        } finally {
            engine.close()
        }
        WorkerResult(iter, activeMs, delegate)
    }
}

/**
 * Aggregate outcome of one [AiCpuWorkload.run]. Surfaced to the V2
 * service so log lines + future verification footer fields can carry
 * concrete iteration counts. Phase 1.6 keeps the existing
 * [VerificationSummary] schema unchanged — we don't claim FMA or SHA
 * counters because we're not running those kernels — but Stage F's
 * report documents this delta for downstream consumers.
 */
data class WorkloadResult(
    val iterations: Int,
    val avgInferenceTimeMs: Float,
    val phaseDurationMs: Long,
    val workerCount: Int,
    /** Phase 1.7.C: TFLite delegate that ended up serving inference.
     *  One of "NNAPI" | "GPU" | "CPU" | "STUB" | "NONE". Captured from
     *  the first worker's engine.getActiveDelegate() — all workers in
     *  one [AiCpuWorkload.run] load with the same forced delegate, so
     *  the values agree. "STUB" surfaces when the TFLite native lib
     *  is missing for this ABI / when the model file is missing — the
     *  workload still iterates but no real inference happened. */
    val activeDelegate: String = "NONE"
)

/**
 * Phase 1.15 (Fix 1 + Fix 2): thrown by [AiCpuWorkload.run] when its early
 * delegate probe resolves to "STUB" — i.e. TFLite could not build a real
 * CPU interpreter on this device, so running the AI workload would just
 * sleep (no CPU load). [StressTestServiceV2] catches this in the CPU
 * phases (3 + 5) and falls back to the synthetic NEON stress kernels so
 * the silicon is actually exercised. Mirrors the STUB guard already
 * present in [net.neuralcheck.calibration.probes.CpuProbe].
 *
 * @property delegate   the resolved delegate name (always "STUB" here).
 * @property initError  the captured interpreter-construction failure from
 *                       InferenceEngine.getDelegateInitError(), forwarded
 *                       into the footer for field diagnosis.
 */
class StubDelegateException(
    val delegate: String,
    val initError: String?
) : IllegalStateException(
    "AiCpuWorkload: TFLite delegate resolved to '$delegate' " +
        "(initError=$initError); no real CPU inference — caller should " +
        "fall back to the NEON stress kernels"
)
