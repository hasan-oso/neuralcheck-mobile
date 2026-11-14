package net.neuralcheck.calibration.probes

import java.nio.ByteBuffer

/**
 * Phase 1.8 — sustained R+W memory-bandwidth probe.
 *
 * Allocates two 64 MB direct (off-heap) ByteBuffers and copies between
 * them in a tight loop for ~5 seconds. Each iteration counts as 64 MB
 * read from `src` and 64 MB written to `dst` = 128 MB processed; the
 * resulting `mbps` reflects sustained DRAM throughput rather than L1/L2
 * cache hit rate, because 64 MB exceeds the L3 cache on every
 * Android-class SoC we target.
 *
 * Pure JVM, no Android imports — runs identically on the unit-test JVM
 * and on-device. If the platform refuses 64 MB of native heap (rare —
 * tests below confirm the budget is reasonable), `allocateDirect`
 * throws `OutOfMemoryError` which is allowed to propagate; the
 * surrounding [ProbePhase] catches it and tags `"memory"` as failed.
 */
object MemoryProbe {

    data class Result(
        /** Sustained R+W throughput in megabytes per second. */
        val mbps: Double,
        /** Wall-clock time spent inside the copy loop, milliseconds. */
        val durationMs: Long,
        /** Total bytes read+written across all iterations. */
        val bytesProcessed: Long
    )

    private const val BUFFER_BYTES: Int = 64 * 1024 * 1024  // 64 MB
    private const val TARGET_DURATION_MS: Long = 5_000L

    /**
     * Run one probe pass. Blocks the calling thread for ~5 seconds.
     * The probe is deliberately synchronous (no coroutine) so it
     * remains a pure-JVM unit-testable function — callers that need
     * coroutine semantics wrap it in `withContext(Dispatchers.Default)`.
     */
    fun measure(): Result {
        val src = ByteBuffer.allocateDirect(BUFFER_BYTES)
        val dst = ByteBuffer.allocateDirect(BUFFER_BYTES)

        // Pre-fill `src` so the first iteration is doing real R+W work
        // (otherwise the kernel may serve zero-pages without DRAM hits).
        for (i in 0 until BUFFER_BYTES / 4) {
            src.putInt(i)
        }

        val start = System.nanoTime()
        val deadlineNanos = start + TARGET_DURATION_MS * 1_000_000L
        var bytesProcessed = 0L

        while (System.nanoTime() < deadlineNanos) {
            src.rewind()
            dst.rewind()
            dst.put(src)  // sequential R from src + W to dst, 64 MB each
            bytesProcessed += (BUFFER_BYTES * 2).toLong()
        }

        val elapsedMs = (System.nanoTime() - start) / 1_000_000L
        val mbps = (bytesProcessed.toDouble() / 1_000_000.0) /
            (elapsedMs.toDouble() / 1_000.0)
        return Result(
            mbps = mbps,
            durationMs = elapsedMs,
            bytesProcessed = bytesProcessed
        )
    }
}
