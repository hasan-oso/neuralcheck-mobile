package net.neuralcheck.service.stress

/**
 * fieldsVersion 66 — sliding-window FPS estimator behind [GpuFpsTracker].
 *
 * Why a window: [net.neuralcheck.service.stress.v2.GpuLoadGeneratorV2]
 * counts frames as they are ISSUED and blocks on a GPU fence every 30
 * frames (FENCE_EVERY_N_FRAMES). The counter therefore advances in
 * bursts of 30 and freezes while the render thread waits. A per-query
 * rate (frames since last query ÷ elapsed) aliases against that 30-frame
 * quantum whenever the GPU needs roughly one sampler period (250 ms at
 * the 4 Hz GPU phases) per batch: a Honor MTN-NX1 field scan produced a
 * perfect 0 / 120 / 0 / 120 alternation — 120 = 30 frames ÷ 0.25 s, the
 * zeros = windows spent blocked on the fence — while the true rate was
 * ~60 fps. Summing the last [maxQueries] windows (4 × 250 ms = 1 s in the
 * GPU phases) integrates over several fence batches, so the estimate
 * converges on the real throughput regardless of where the batch
 * boundaries fall. Fast GPUs (Samsung S10 5G: ~311 frames per 250 ms)
 * complete many batches per window and were never aliased; the window
 * leaves their values unchanged.
 *
 * Pure state machine (no clock, no GL) so the aliasing case is unit-
 * testable; [GpuFpsTracker] feeds it `System.nanoTime()`.
 */
internal class FpsWindow(private val maxQueries: Int = DEFAULT_QUERIES) {

    companion object {
        /** 4 queries = 1 s at the 4 Hz GPU-phase sampling rate. */
        const val DEFAULT_QUERIES: Int = 4
        /** Ignore query gaps under 10 ms — they only amplify counter noise. */
        private const val MIN_QUERY_GAP_NS: Long = 10_000_000L
        /** Same plausibility band as the pre-66 per-query filter. */
        private const val MIN_FPS: Float = 0.1f
        private const val MAX_FPS: Float = 3000f
    }

    private data class Slice(val frames: Int, val elapsedNs: Long)

    private val slices = ArrayDeque<Slice>()
    private var lastQueryNs: Long = 0L

    /**
     * Records [frames] counted since the previous query at time [nowNs]
     * and returns the windowed FPS. The first query after [reset] has no
     * elapsed window and returns 0 (its frames are discarded, as before).
     */
    fun push(frames: Int, nowNs: Long): Float {
        val prev = lastQueryNs
        lastQueryNs = nowNs
        if (prev == 0L) return 0f
        val elapsedNs = nowNs - prev
        if (elapsedNs <= MIN_QUERY_GAP_NS) return 0f

        slices.addLast(Slice(frames, elapsedNs))
        while (slices.size > maxQueries) slices.removeFirst()

        var totalFrames = 0L
        var totalNs = 0L
        for (s in slices) {
            totalFrames += s.frames
            totalNs += s.elapsedNs
        }
        if (totalNs <= 0L) return 0f
        val raw = (totalFrames.toDouble() / (totalNs / 1_000_000_000.0)).toFloat()
        if (raw.isNaN() || raw.isInfinite()) return 0f
        return if (raw in MIN_FPS..MAX_FPS) raw else 0f
    }

    fun reset() {
        slices.clear()
        lastQueryNs = 0L
    }
}

/**
 * Thread-safe bridge that converts the raw frame counter exposed by
 * [GpuLoadGenerator] / [net.neuralcheck.service.stress.v2.GpuLoadGeneratorV2]
 * into a frames-per-second value for the sampler's v[10] channel.
 *
 * fieldsVersion 66: the value is a ≥1 s sliding-window rate ([FpsWindow]),
 * not the per-query rate written by builds ≤65 — see [FpsWindow] for the
 * 30-frame fence aliasing this removes. v[10] from ≤65 files is NOT
 * directly comparable with ≥66 files on GPUs in the 60–120 fps band.
 */
object GpuFpsTracker {

    @Volatile
    var gpuLoadGenerator: GpuLoadGenerator? = null

    /** V2 — set by [StressTestServiceV2] when the V2 GPU phase starts.
     *  Bug 4 fix: V1 tracker only knew about V1 [GpuLoadGenerator];
     *  V2 mode left FPS at 0 because the V2 generator (running on the
     *  same thread but a different class) wasn't bridged. Both
     *  references can be set; V2 wins when both are non-null because
     *  V1's generator is never started in V2 mode.
     */
    @Volatile
    var gpuLoadGeneratorV2: net.neuralcheck.service.stress.v2.GpuLoadGeneratorV2? = null

    private val window = FpsWindow()

    @Synchronized
    fun getAndResetFps(): Float {
        // V2 takes precedence — V1 generator is left in initComponents'
        // never-started state on V2 scans, so its frame counter is
        // always 0 and would otherwise mask V2's real numbers.
        val v2 = gpuLoadGeneratorV2
        val frames = if (v2 != null) {
            v2.getAndResetFrameCount()
        } else {
            val v1 = gpuLoadGenerator ?: return 0f
            v1.getAndResetFrameCount()
        }
        return window.push(frames, System.nanoTime())
    }

    /** Clear state between scans / at GPU-phase start so no stale window
     *  carries over into the new phase's first samples. */
    @Synchronized
    fun reset() {
        window.reset()
        gpuLoadGenerator?.getAndResetFrameCount()
        gpuLoadGeneratorV2?.getAndResetFrameCount()
    }
}
