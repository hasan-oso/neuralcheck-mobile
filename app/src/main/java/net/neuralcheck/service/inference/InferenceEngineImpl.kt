package net.neuralcheck.service.inference

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.6.A — TensorFlow Lite-backed [InferenceEngine].
 *
 * Reflection is used in two specific places (interpreter construction +
 * delegate options) so the Kotlin compile keeps working even when the
 * `org.tensorflow:tensorflow-lite*` artefacts are missing from the
 * classpath — for example in `:app:testLaunchDebugUnitTest`, which
 * runs on JVM and has no NNAPI / no native libtensorflowlite_jni.so.
 *
 * On a real Android device the reflection paths resolve to the actual
 * `org.tensorflow.lite.Interpreter` and the engine takes the fast path.
 * On JVM (smoke test) every reflective call throws ClassNotFoundException
 * which the `runCatching` shims downgrade to the STUB path — the
 * Interpreter is null but [runInference] still produces a 1001-vector
 * by sleeping for a representative time and returning a hashed dummy
 * output so caller code (workloads, smoke test assertions) keeps
 * exercising end-to-end.
 *
 * Threading: backed by an [AtomicReference] to the current Interpreter,
 * so [close] from one thread races safely against [runInference] from
 * another. Successive [loadModel] calls close the previous interpreter
 * before swapping the reference. Concurrent [runInference] reads the
 * same Interpreter snapshot; TFLite's Interpreter is not thread-safe,
 * so each parallel worker should hold its own [InferenceEngineImpl] —
 * the workloads in Stage B/C/D will spawn one engine per coroutine.
 */
@Singleton
class InferenceEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

    companion object {
        private const val TAG = "NeuralCheck.Inference"
        private const val INPUT_W = 224
        private const val INPUT_H = 224
        private const val INPUT_C = 3
        const val INPUT_SIZE: Int = INPUT_W * INPUT_H * INPUT_C   // 150 528
        private const val STUB_OUTPUT_SIZE = 1001
        // Stub-mode inference time. ~10 ms keeps the smoke test snappy
        // and is in the right ballpark for what a real CPU-delegated
        // MobileNetV2 takes on a flagship — caller pacing logic stays
        // realistic when running unit tests.
        private const val STUB_INFERENCE_MS = 10L
    }

    /** Current Interpreter reference. null in STUB mode. Volatile-ish:
     *  AtomicReference handles publication. */
    private val interpreterRef = AtomicReference<Any?>(null)

    /** Output tensor shape captured from the loaded model so
     *  [runInference] can right-size the output FloatArray. Defaults to
     *  the stub size when no real model is loaded. */
    @Volatile private var outputSize: Int = STUB_OUTPUT_SIZE

    /** Most recently resolved delegate name, surfaced by [getActiveDelegate]. */
    @Volatile private var activeDelegate: String = "NONE"

    /** Held so [close] can release the GPU delegate explicitly. */
    @Volatile private var gpuDelegate: AutoCloseable? = null

    /** Phase 1.15 (Fix 4): the most recent reason [loadModel] fell to STUB
     *  (last failing delegate's "<ExceptionClass>: <message>", or an
     *  asset-missing tag). Cleared to null on a successful real load.
     *  Surfaced by [getDelegateInitError] so the footer can record WHY a
     *  field device degraded to STUB without a manual logcat capture. */
    @Volatile private var lastInitError: String? = null

    override fun loadModel(modelName: String, preferredDelegate: Delegate): Boolean {
        close()
        val mapped = openModelAsset(modelName)
        if (mapped == null) {
            // Either the asset is missing or we couldn't mmap it. Stay
            // in STUB so callers (workloads + tests) still get a useful
            // exercise of the wrapper code.
            activeDelegate = "STUB"
            outputSize = STUB_OUTPUT_SIZE
            lastInitError = "asset_missing_or_unreadable: models/$modelName"
            Log.w(TAG, "loadModel($modelName) → STUB (asset missing or unreadable)")
            return false
        }

        val attempts: List<Delegate> = when (preferredDelegate) {
            Delegate.AUTO -> listOf(Delegate.NNAPI, Delegate.GPU, Delegate.CPU)
            else -> listOf(preferredDelegate)
        }

        for (attempt in attempts) {
            val pair = createInterpreter(mapped, attempt)
            if (pair != null) {
                interpreterRef.set(pair.first)
                gpuDelegate = pair.second
                activeDelegate = attempt.name
                outputSize = inferOutputSize(pair.first)
                lastInitError = null   // real delegate loaded — clear any prior failure
                Log.i(TAG, "loadModel($modelName) → ${attempt.name} (output=$outputSize)")
                return true
            }
            Log.i(TAG, "loadModel($modelName) delegate ${attempt.name} unavailable, trying next")
        }

        // All real delegates failed. Most common on JVM unit tests (TFLite
        // native lib absent); but on a real device this is a genuine
        // interpreter-construction failure (e.g. UnsatisfiedLinkError on a
        // newer SoC/API the bundled TFLite wasn't validated against — the
        // Honor X9d case). Fall back to STUB rather than throwing, but log
        // at WARN with the captured cause so field devices surface it.
        activeDelegate = "STUB"
        outputSize = STUB_OUTPUT_SIZE
        Log.w(
            TAG,
            "loadModel($modelName) → STUB (no real delegate available); " +
                "lastInitError=$lastInitError"
        )
        return false
    }

    override fun runInference(input: FloatArray): FloatArray {
        require(input.size == INPUT_SIZE) {
            "InferenceEngine: expected input size $INPUT_SIZE, got ${input.size}"
        }
        val interp = interpreterRef.get()
        if (interp == null) {
            // STUB path. Sleep for a representative time + return a
            // deterministic hash-derived FloatArray so caller code paths
            // (assertions, downstream consumers) see a non-trivial output.
            try { Thread.sleep(STUB_INFERENCE_MS) } catch (_: InterruptedException) {}
            val out = FloatArray(outputSize)
            // Cheap PRNG seeded by the input — reproducible, no state.
            var seed = input.fold(0xCAFEBABE.toInt()) { acc, f -> acc xor f.toRawBits() }
            for (i in out.indices) {
                seed = seed * 0x9E3779B1.toInt() + 0xDEADBEEF.toInt()
                out[i] = ((seed ushr 8) and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat()
            }
            return out
        }
        return runRealInference(interp, input)
    }

    override fun runInferenceLoop(
        durationMs: Long,
        onIteration: ((Int) -> Unit)?
    ): Int {
        if (durationMs <= 0L) return 0
        // Use System.nanoTime() rather than SystemClock.elapsedRealtime() —
        // the latter is an Android stub that returns 0 under JVM unit
        // tests (testOptions.unitTests.isReturnDefaultValues = true),
        // which would turn this loop into an infinite spin. nanoTime()
        // works identically on Android (per Android docs) AND on the
        // JVM, so the smoke test can finish in 60 ms and the on-device
        // path keeps the same semantics.
        val deadlineNs = System.nanoTime() + durationMs * 1_000_000L
        // One reusable input buffer per loop — workloads typically call
        // this in a tight inner loop and reallocating 150 528 floats
        // each tick would dominate runtime on slower devices.
        val input = FloatArray(INPUT_SIZE) { (it and 0xFF) / 255f }
        var iter = 0
        while (System.nanoTime() < deadlineNs) {
            try {
                runInference(input)
            } catch (e: Throwable) {
                Log.w(TAG, "runInferenceLoop iter=$iter threw ${e.javaClass.simpleName}: ${e.message}")
                break
            }
            iter++
            onIteration?.invoke(iter)
        }
        return iter
    }

    override fun getActiveDelegate(): String = activeDelegate

    override fun getDelegateInitError(): String? = lastInitError

    @Synchronized
    override fun close() {
        val current = interpreterRef.getAndSet(null)
        if (current != null) {
            runCatching {
                val closeMethod = current.javaClass.getMethod("close")
                closeMethod.invoke(current)
            }.onFailure { Log.w(TAG, "interpreter.close failed: ${it.message}") }
        }
        gpuDelegate?.let { runCatching { it.close() } }
        gpuDelegate = null
    }

    // ─────────── internal helpers ───────────

    /**
     * mmap the requested asset. Returns null if the asset is missing or
     * the mmap fails. The mmap is mandatory for TFLite's Interpreter
     * constructor — falling back to a heap copy doubles RAM footprint
     * on a 14 MB classifier and can OOM on budget devices.
     *
     * Per [androidResources.noCompress += "tflite"] in the Gradle
     * config, the .tflite asset is stored uncompressed in the APK so
     * AssetFileDescriptor.getStartOffset/.getDeclaredLength carve out a
     * regular file region the kernel can mmap directly. Compressed
     * .tflite would get unpacked into the data dir on first access and
     * mmap would fail with the original asset path.
     */
    private fun openModelAsset(name: String): MappedByteBuffer? {
        val assetPath = "models/$name"
        return runCatching {
            context.assets.openFd(assetPath).use { afd ->
                FileInputStream(afd.fileDescriptor).use { stream ->
                    stream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        }.onFailure {
            Log.w(TAG, "openModelAsset($assetPath) failed: ${it.javaClass.simpleName}: ${it.message}")
        }.getOrNull()
    }

    /**
     * Reflectively construct `org.tensorflow.lite.Interpreter` with
     * delegate-specific options. Returns (interpreter, gpuDelegate-or-null)
     * on success, null on any failure (NoClassDefFound, native
     * UnsatisfiedLinkError, GPU lib missing, NNAPI unavailable on this
     * SoC, etc.).
     *
     * Reflection lets the launch flavor still compile when the test
     * classpath omits the TFLite artefacts — the standard `Interpreter(buffer, options)`
     * constructor is exactly the path the smoke test exercises in
     * STUB mode, since the reflection lookup throws and we fall through.
     */
    private fun createInterpreter(model: MappedByteBuffer, delegate: Delegate): Pair<Any, AutoCloseable?>? {
        return runCatching {
            val optionsClass = Class.forName("org.tensorflow.lite.Interpreter\$Options")
            val options = optionsClass.getConstructor().newInstance()
            // Common: cap thread count to physical CPUs so the OS
            // scheduler doesn't oversubscribe the big cluster (would
            // invite a thermal trip mid-phase).
            val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            optionsClass.getMethod("setNumThreads", Int::class.javaPrimitiveType)
                .invoke(options, threads)

            val gpuDel: AutoCloseable? = when (delegate) {
                Delegate.NNAPI -> {
                    // NnApiDelegate(NnApiDelegate.Options) — version-tolerant.
                    val nnDelegate = Class.forName("org.tensorflow.lite.nnapi.NnApiDelegate")
                        .getConstructor().newInstance() as AutoCloseable
                    optionsClass.getMethod("addDelegate",
                        Class.forName("org.tensorflow.lite.Delegate"))
                        .invoke(options, nnDelegate)
                    nnDelegate
                }
                Delegate.GPU -> {
                    val gpuD = Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                        .getConstructor().newInstance() as AutoCloseable
                    optionsClass.getMethod("addDelegate",
                        Class.forName("org.tensorflow.lite.Delegate"))
                        .invoke(options, gpuD)
                    gpuD
                }
                Delegate.CPU, Delegate.AUTO -> null
            }

            val interp = Class.forName("org.tensorflow.lite.Interpreter")
                .getConstructor(java.nio.ByteBuffer::class.java, optionsClass)
                .newInstance(model, options)
            interp to gpuDel
        }.onFailure {
            // Phase 1.15 (Fix 4): capture the exact cause + raise to WARN.
            // This is the single most useful line for diagnosing why a
            // field device fell to STUB (e.g. UnsatisfiedLinkError /
            // IllegalArgumentException from the TFLite Interpreter ctor).
            lastInitError = "${it.javaClass.simpleName}: ${it.message}"
            Log.w(
                TAG,
                "createInterpreter(${delegate.name}) failed: " +
                    "${it.javaClass.simpleName}: ${it.message}"
            )
        }.getOrNull()
    }

    /**
     * Read the output tensor shape and return its product. Falls back
     * to [STUB_OUTPUT_SIZE] if the reflection path fails — the engine
     * still produces a usable (but mis-sized) output, which the
     * caller's right-shape assumption catches via [require] on the
     * input only. Output size mismatches surface as a warning log and
     * the next [runInference] reshape — acceptable for a fallback.
     */
    private fun inferOutputSize(interp: Any): Int {
        return runCatching {
            val tensor = interp.javaClass.getMethod("getOutputTensor", Int::class.javaPrimitiveType)
                .invoke(interp, 0)
            val shape = tensor.javaClass.getMethod("shape").invoke(tensor) as IntArray
            var product = 1
            for (d in shape) product *= d
            if (product <= 0) STUB_OUTPUT_SIZE else product
        }.getOrDefault(STUB_OUTPUT_SIZE)
    }

    /**
     * Reflective Interpreter.run(input, output). The on-Android path
     * lights up here; the JVM/test path never reaches here because
     * [interpreterRef] is null in STUB mode and [runInference]
     * short-circuits to the synthetic output above.
     */
    private fun runRealInference(interp: Any, input: FloatArray): FloatArray {
        // Pack into a direct ByteBuffer once. TFLite's Interpreter.run
        // accepts FloatArray directly via the var-args overload, but
        // wrapping in a Buffer makes the call signature unambiguous
        // for reflection.
        val inBuf = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
        inBuf.asFloatBuffer().put(input).position(0)

        val outBuf = ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())

        runCatching {
            interp.javaClass.getMethod(
                "run",
                Any::class.java,
                Any::class.java
            ).invoke(interp, inBuf, outBuf)
        }.onFailure {
            Log.w(TAG, "runRealInference threw ${it.javaClass.simpleName}: ${it.message}")
        }

        val out = FloatArray(outputSize)
        outBuf.position(0)
        outBuf.asFloatBuffer().get(out)
        return out
    }
}
