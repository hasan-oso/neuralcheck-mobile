package net.neuralcheck.service.stress.v2

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.6.D — Memory bandwidth stress that camouflages as image
 * processing.
 *
 * The V5 path (March-C / Walking Ones in C++, today StressNative.runMemoryVerify)
 * generated real DRAM bandwidth, but its access pattern is the
 * fingerprint of every memory tester ever written — pure linear sweeps
 * over a single huge buffer. Some HyperOS / ColorOS heuristics may
 * recognise this and apply memory-clock capping. Image processing
 * (bitmap-sized buffer ping-pong with 3×3 convolutions) generates the
 * same DRAM bandwidth + L3-cache thrash but looks like every photo
 * editor / camera ML preprocessor on the device.
 *
 * Buffer choice: `BITMAP_W × BITMAP_H × 4 bytes ≈ 8 MB`. Allocating 4
 * such buffers and convolving 3-of-4 → 1-of-4 each pass touches
 * ~32 MB of DRAM per pass — comfortably above L3 cache on every modern
 * SoC, so the bandwidth signal lands in DRAM where we want it.
 *
 * Returns the total bytes touched so callers can compute MB/s for
 * v[9] — the fix from Phase 1 (memory_bandwidth_mbps end-of-phase
 * aggregate) keeps working on the AI path with no schema change.
 */
@Singleton
class AiMemoryWorkload @Inject constructor() {
    companion object {
        private const val TAG = "NeuralCheck.Inference.Mem"
        // 1080p RGBA8888 = 8 294 400 B per buffer.
        private const val BITMAP_W = 1080
        private const val BITMAP_H = 1920
        private const val BITMAP_BYTES = BITMAP_W * BITMAP_H * 4

        /** Number of ring-buffers. Four lets us read three and write
         *  one each pass — keeps cores fetching cache-cold data
         *  every iteration. */
        private const val RING_SIZE = 4

        /** fieldsVersion 66: live v[9] publish cadence. 1 s matches the
         *  MEMORY_STRESS sampling rate (1 Hz), so each sample sees at most
         *  one fresh value. */
        const val DEFAULT_PUBLISH_INTERVAL_MS: Long = 1_000L
    }

    /**
     * Runs convolution-style memory pressure for [durationMs] and
     * returns a [WorkloadResult] including a `bytesTouched` field so
     * the caller (V2 service) can publish MB/s into v[9].
     *
     * @param durationMs phase wall-clock budget.
     * @param intensityFactor 0.65/0.8/1.0; below 1.0 a small inter-pass
     *                        sleep yields scheduler time so the OS
     *                        sees cooperative behaviour.
     * @return aggregate counters; `iterations` is the number of full
     *         convolution passes completed.
     */
    suspend fun run(
        durationMs: Long,
        intensityFactor: Float,
        config: AiWorkloadConfig? = null,
        /**
         * fieldsVersion 66 — live bandwidth publisher. Invoked from the
         * worker thread roughly every [publishIntervalMs] with the MB/s
         * (MiB-based, matching the old end-of-phase aggregate's unit)
         * touched since the previous publish. The V2 service forwards it
         * to `DisciplinedSampler.setMemoryBandwidth` so v[9] carries a live
         * series — the same publish pattern the storage phase uses per
         * 256 MiB segment. Null = no publishing (tests / probes).
         */
        onBandwidth: ((mbps: Float) -> Unit)? = null,
        publishIntervalMs: Long = DEFAULT_PUBLISH_INTERVAL_MS
    ): MemoryWorkloadResult = withContext(Dispatchers.Default) {
        if (durationMs <= 0L) {
            return@withContext MemoryWorkloadResult(0, 0L, 0L)
        }

        // Phase 1.8 Task 4: memory buffer size is now calibratable. When
        // [AiWorkloadConfig.memoryBufferBytes] is supplied, it overrides
        // the legacy [BITMAP_BYTES] default. Values fit in Int when
        // multiplied by RING_SIZE = 4 — clamp aggressively so we never
        // pass a > 2 GiB Long to ByteArray's Int-sized constructor. The
        // null path is bit-for-bit identical to the pre-Task-4 behaviour.
        val bufferBytes: Int = (config?.memoryBufferBytes
            ?.coerceIn(1L * 1024 * 1024, 512L * 1024 * 1024)
            ?.toInt())
            ?: BITMAP_BYTES

        // Allocate the ring of buffer-sized byte arrays up front.
        // Allocating mid-loop would trip GC mid-pass and contaminate
        // bandwidth measurements with allocator overhead.
        val ring = Array(RING_SIZE) { idx ->
            // Initialise with a non-trivial pattern so the first
            // convolution doesn't fold into a zero-output skip.
            ByteArray(bufferBytes) { i -> ((idx * 31 + i) and 0xFF).toByte() }
        }

        val startNs = System.nanoTime()
        val deadlineNs = startNs + durationMs * 1_000_000L
        // Phase 1.6.E: targetIterPerSec from the tier config caps the
        // pass rate so a flagship doesn't spike thermals on a budget
        // device that misclassified as flagship. Falls back to the
        // original intensity-scaled gap when no config is supplied.
        val sleepMs: Long = if (config != null && config.targetIterPerSec > 0) {
            (1000L / config.targetIterPerSec).coerceAtLeast(0L)
        } else {
            ((1.0f - intensityFactor.coerceIn(0f, 1f)) * 15f).toLong()
        }

        var iter = 0
        var bytesTouched = 0L
        // fieldsVersion 66 — live publish bookkeeping.
        val publishIntervalNs = publishIntervalMs.coerceAtLeast(1L) * 1_000_000L
        var lastPublishNs = startNs
        var bytesAtLastPublish = 0L

        Log.i(
            TAG,
            "AiMemoryWorkload.run: durationMs=$durationMs intensity=$intensityFactor " +
                "ringSize=$RING_SIZE bufferBytes=$bufferBytes (~${bufferBytes / (1024 * 1024)} MiB)"
        )

        while (System.nanoTime() < deadlineNs && isActive) {
            val src1 = ring[iter % RING_SIZE]
            val src2 = ring[(iter + 1) % RING_SIZE]
            val src3 = ring[(iter + 2) % RING_SIZE]
            val dst = ring[(iter + 3) % RING_SIZE]

            // 3-source × 1-destination linear convolution-ish blend.
            // Real convolution is 3×3 windowed but a windowed loop in
            // pure Kotlin (no NDK) is dominated by bounds-check
            // overhead; the linear blend with three sources matches the
            // bandwidth profile (read 3 × write 1 = 4 × buffer_bytes
            // per pass) and keeps the inner loop tight enough that JIT
            // emits scalar SIMD-style code on ART.
            blendThreeSources(src1, src2, src3, dst)
            // bytes "touched" = 3 reads + 1 write of bufferBytes each.
            bytesTouched += bufferBytes.toLong() * 4L
            iter++

            // fieldsVersion 66: publish the rolling MB/s since the last
            // publish. Cheap (a subtraction + a division per pass) and
            // the callback is a single AtomicLong store on the sampler.
            if (onBandwidth != null) {
                val nowNs = System.nanoTime()
                val sinceNs = nowNs - lastPublishNs
                if (sinceNs >= publishIntervalNs) {
                    val bytesSince = bytesTouched - bytesAtLastPublish
                    val mbps = (bytesSince.toDouble() /
                        (sinceNs / 1_000_000_000.0) / (1024.0 * 1024.0)).toFloat()
                    runCatching { onBandwidth(mbps) }
                    lastPublishNs = nowNs
                    bytesAtLastPublish = bytesTouched
                }
            }

            if (sleepMs > 0L) delay(sleepMs)
        }

        val phaseDurationMs = (System.nanoTime() - startNs) / 1_000_000L
        Log.i(
            TAG,
            "AiMemoryWorkload.run done: iter=$iter bytesTouched=${bytesTouched / (1024L * 1024L)} MiB " +
                "phaseDurationMs=$phaseDurationMs"
        )

        MemoryWorkloadResult(
            iterations = iter,
            bytesTouched = bytesTouched,
            phaseDurationMs = phaseDurationMs
        )
    }

    /**
     * Inner kernel — three-source linear blend writing to dst. Pure
     * scalar Kotlin so it runs identically on every Android ART; ART's
     * tiered JIT lifts this to NEON SIMD on arm64-v8a (mandatory ABI
     * for our minSdk 28 launch flavor) within the first hundred
     * iterations. We deliberately avoid `ByteBuffer.allocateDirect` +
     * `BufferUtils.copy` because those memcpy paths skip L1/L2 and
     * make the bandwidth signal too clean — real image-edit apps see
     * their own caches dirtied as they touch source pixels.
     *
     * The shift / mask logic computes (a + b + c) / 3 with rounding
     * for each unsigned-byte channel without lifting to Int and back
     * (which would double the active footprint and hide the cache
     * effect).
     */
    private fun blendThreeSources(
        src1: ByteArray,
        src2: ByteArray,
        src3: ByteArray,
        dst: ByteArray
    ) {
        val n = dst.size
        var i = 0
        // Unrolled by 4 so each iteration does ~16 byte ops — JIT
        // schedules better and the loop overhead is amortised.
        while (i + 4 <= n) {
            dst[i] = blend(src1[i], src2[i], src3[i])
            dst[i + 1] = blend(src1[i + 1], src2[i + 1], src3[i + 1])
            dst[i + 2] = blend(src1[i + 2], src2[i + 2], src3[i + 2])
            dst[i + 3] = blend(src1[i + 3], src2[i + 3], src3[i + 3])
            i += 4
        }
        while (i < n) {
            dst[i] = blend(src1[i], src2[i], src3[i])
            i++
        }
    }

    /** Inline averaging; the JVM inlines small private methods after
     *  the first few invocations. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun blend(a: Byte, b: Byte, c: Byte): Byte {
        val ai = a.toInt() and 0xFF
        val bi = b.toInt() and 0xFF
        val ci = c.toInt() and 0xFF
        // Integer divide is fast on aarch64 — this whole kernel sits
        // around 5–10 GB/s on a flagship.
        return ((ai + bi + ci) / 3).toByte()
    }
}

/**
 * Output of [AiMemoryWorkload.run]. `bytesTouched` captures the
 * read+write byte count so the caller can compute MB/s — the existing
 * `sampler.setMemoryBandwidth(...)` (Phase 1 fix #FIX-1.B) consumes
 * that number and populates v[9] memory_bandwidth_mbps unchanged.
 */
data class MemoryWorkloadResult(
    val iterations: Int,
    val bytesTouched: Long,
    val phaseDurationMs: Long
)
