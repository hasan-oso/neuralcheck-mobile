package net.neuralcheck.service.stress.v2

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.neuralcheck.service.inference.Delegate
import net.neuralcheck.service.inference.InferenceEngine
import net.neuralcheck.service.inference.InferenceEngineImpl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.6.C — AI inference forced onto the GPU delegate.
 *
 * Phase 4 (GPU_SUSTAINED) and the GPU half of Phase 5 (COMBINED_PEAK)
 * launch this concurrently with [RaymarchingGpuLoad] (the heavy
 * fragment-shader scene running inside [GpuLoadGeneratorV2]). The two
 * workloads compete for the same GPU resources — exactly what real
 * apps do (camera ML pipeline + AR overlays, generative image apps +
 * UI compositor). The OS recognises the combination as a legitimate
 * compute graph and does not throttle as aggressively as it does for
 * a pure fragment-shader spinner.
 *
 * Single worker: TFLite GPU delegate creates one EGL context that
 * uploads / downloads SSBOs around the inference call, so spawning
 * multiple GPU-delegate engines just contends for the same hardware
 * and slows everything down. The raymarching shader provides the
 * parallelism on its own (one fragment per pixel of a 2048×2048 FBO,
 * issued by the same GPU but on the graphics queue rather than the
 * compute queue most TFLite GPU delegates target).
 */
@Singleton
class AiGpuWorkload @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NeuralCheck.Inference.Gpu"

        /** Default model when no [AiWorkloadConfig] is supplied. */
        private const val DEFAULT_MODEL_NAME = "mobilenet_v3_large_224.tflite"

        /** Inter-inference sleep for GPU at intensity=1.0 (ms). The
         *  raymarcher is generating heavy graphics-queue load
         *  concurrently — a tiny gap between inferences lets the
         *  command-queue drain and keeps temps from spiking past
         *  thermal trip on budget Mali GPUs. */
        private const val MIN_INTER_INFERENCE_MS: Long = 5L
    }

    /**
     * Run continuous GPU-delegate inference for [durationMs]. Returns
     * the iteration count + average inference time. Falls into STUB
     * mode (or the CPU delegate) when the GPU delegate fails to
     * initialise (Adreno driver issue, no `libtensorflowlite_gpu_jni.so`
     * for this ABI, etc.) — we don't try to pretend GPU stress
     * happened when it didn't.
     */
    suspend fun run(
        durationMs: Long,
        intensityFactor: Float,
        config: AiWorkloadConfig? = null
    ): WorkloadResult = withContext(Dispatchers.Default) {
        if (durationMs <= 0L) {
            return@withContext WorkloadResult(0, 0f, 0L, 1)
        }

        val engine: InferenceEngine = InferenceEngineImpl(context)
        val modelName = config?.gpuModelChoice ?: DEFAULT_MODEL_NAME
        // Force GPU delegate. AUTO would promote to NNAPI on devices
        // that have it, which routes to NPU/DSP — wrong subsystem for
        // a GPU stress phase.
        engine.loadModel(modelName, Delegate.GPU)
        val delegate = engine.getActiveDelegate()
        Log.i(
            TAG,
            "AiGpuWorkload.run: durationMs=$durationMs intensity=$intensityFactor " +
                "model=$modelName delegate=$delegate"
        )

        val startNs = System.nanoTime()
        val deadlineNs = startNs + durationMs * 1_000_000L
        // Phase 1.6.E: tier cap when config is provided. Otherwise
        // fall back to the original intensity-scaled sleep with the
        // hard floor.
        val sleepMs: Long = if (config != null && config.targetIterPerSec > 0) {
            (1000L / config.targetIterPerSec).coerceAtLeast(MIN_INTER_INFERENCE_MS)
        } else {
            (
                MIN_INTER_INFERENCE_MS +
                ((1.0f - intensityFactor.coerceIn(0f, 1f)) * 25f).toLong()
            )
        }

        val input = FloatArray(InferenceEngineImpl.INPUT_SIZE) { (it and 0xFF) / 255f }
        var iter = 0
        var activeMs = 0L

        try {
            while (System.nanoTime() < deadlineNs && isActive) {
                val tStart = System.nanoTime()
                runCatching { engine.runInference(input) }
                    .onFailure {
                        Log.w(TAG, "AiGpuWorkload iter=$iter inference threw " +
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

        val phaseDurationMs = (System.nanoTime() - startNs) / 1_000_000L
        val avgInferenceMs = if (iter > 0) activeMs.toFloat() / iter.toFloat() else 0f
        Log.i(
            TAG,
            "AiGpuWorkload.run done: iter=$iter avgInfMs=${"%.1f".format(avgInferenceMs)} " +
                "delegate=$delegate phaseDurationMs=$phaseDurationMs"
        )

        WorkloadResult(
            iterations = iter,
            avgInferenceTimeMs = avgInferenceMs,
            phaseDurationMs = phaseDurationMs,
            workerCount = 1,
            activeDelegate = delegate
        )
    }
}
