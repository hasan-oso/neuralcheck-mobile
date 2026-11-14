package net.neuralcheck.service.inference

/**
 * Phase 1.6.A — Unified inference façade.
 *
 * The mission is "AI camouflage": replace synthetic stress kernels (NEON
 * FMA, March-C-, OpenGL FBO) with real TensorFlow Lite inference so the
 * Android governor stops flagging the workload as a synthetic stress
 * test and stops capping CPU/GPU frequency on locked-down vendor builds
 * (HyperOS, ColorOS). The OS recognises TFLite inference as a legitimate
 * workload (battery optimisation, photography ML, AR) and lets it run
 * unthrottled.
 *
 * The interface is intentionally narrow:
 *   - [loadModel] picks a delegate (NNAPI > GPU > CPU under AUTO)
 *   - [runInference] executes a single forward pass
 *   - [runInferenceLoop] runs continuously for a duration window
 *   - [getActiveDelegate] reports which backend ended up serving requests
 *
 * Any model file shipped under `assets/models/<modelName>` is acceptable,
 * provided it has a single 1×224×224×3 FP32 input and a single FP32
 * output. Models that don't load fall the engine into "STUB" mode where
 * inference simulates with a sleep + dummy output — the wrapper code
 * stays exercised so [InferenceEngineSmokeTest] can run on a JVM
 * without the native TFLite library.
 */
interface InferenceEngine {
    /**
     * Open a model from `assets/models/<modelName>` and bind it to the
     * preferred delegate. Returns true on success, false on failure
     * (model missing, delegate unsupported, OOM). Successive calls
     * close the previous interpreter before loading the new one.
     */
    fun loadModel(modelName: String, preferredDelegate: Delegate = Delegate.AUTO): Boolean

    /**
     * Run one forward pass.
     *
     * @param input flat FloatArray of size 224×224×3 = 150 528, RGB
     *              normalised to [0, 1]. Wrong-size input throws
     *              IllegalArgumentException — caller must reshape.
     * @return output tensor flattened to a FloatArray. Size depends on
     *         the model (1001 for ImageNet classifiers, 1024 for
     *         embedders). Stub mode returns a synthetic 1001-vector.
     */
    fun runInference(input: FloatArray): FloatArray

    /**
     * Run inference back-to-back for the requested duration. Optional
     * [onIteration] callback fires after each inference with the running
     * iteration count (allows the workload to pace itself). Returns
     * the total number of completed iterations.
     */
    fun runInferenceLoop(
        durationMs: Long,
        onIteration: ((Int) -> Unit)? = null
    ): Int

    /**
     * Last-resolved delegate name. One of:
     *   "NNAPI" | "GPU" | "CPU" | "STUB" | "NONE"
     *
     * "NONE" means [loadModel] hasn't run yet. "STUB" means the engine
     * is operating without a real interpreter (model file missing or
     * native library unavailable on this JVM).
     */
    fun getActiveDelegate(): String

    /**
     * Phase 1.15 (Fix 4): the most recent interpreter-construction failure,
     * formatted "<ExceptionClass>: <message>" (or a short tag for an absent
     * asset). Non-null only when [getActiveDelegate] is "STUB" because a
     * real delegate could not be built — lets callers surface the exact
     * on-device cause (e.g. "UnsatisfiedLinkError: ...") into the footer
     * for field diagnosis. Null when a real delegate loaded, or before
     * [loadModel] runs. Default implementation returns null so existing
     * test doubles keep compiling.
     */
    fun getDelegateInitError(): String? = null

    /** Free the underlying interpreter + delegate. Idempotent. */
    fun close()
}

/**
 * Backends ordered in [InferenceEngine.loadModel] under [Delegate.AUTO]:
 * NNAPI first (cheapest, leverages vendor NPU when present),
 * then GPU (covers the gap on devices where NNAPI is partial),
 * finally CPU as the universal fallback.
 *
 * Workloads can also force a specific delegate to load asymmetric
 * stress (CPU-heavy for [Phase 3], GPU-heavy for [Phase 4]).
 */
enum class Delegate { AUTO, NNAPI, GPU, CPU }
