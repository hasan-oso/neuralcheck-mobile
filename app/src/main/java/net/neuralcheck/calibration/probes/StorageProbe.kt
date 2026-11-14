package net.neuralcheck.calibration.probes

import java.io.File
import java.io.FileOutputStream

/**
 * Phase 1.8 — sequential write probe.
 *
 * Writes 256 MB of deterministic non-zero data to a temp file inside
 * `workDir` in 4 MB chunks, ends with `fd.sync()` to drain the kernel
 * page cache, and reports the achieved MB/s. The file is always
 * deleted in `finally`.
 *
 * Takes the working directory as a parameter so the probe is unit-
 * testable with any `java.io.File` (e.g., JUnit `TemporaryFolder`).
 * The production caller passes `context.cacheDir` — same convention as
 * `StressTestServiceV2` storage stress and `StorageTests`.
 */
object StorageProbe {

    data class Result(
        /** Sustained sequential write throughput in MB/s. */
        val mbps: Double,
        /** Wall-clock time from first write to fsync return, ms. */
        val durationMs: Long,
        /** Total bytes written before fsync. */
        val bytesWritten: Long
    )

    private const val TARGET_BYTES: Long = 256L * 1024 * 1024     // 256 MB
    private const val CHUNK_BYTES: Int = 4 * 1024 * 1024          // 4 MB chunks
    private const val MIN_FREE_BYTES_REQUIRED: Long = 512L * 1024 * 1024  // 512 MB safety

    /**
     * Run one probe pass. Blocks the calling thread until the 256 MB
     * write + fsync complete (typically 0.3-3 s on flagship UFS,
     * up to 10+ s on low-end eMMC).
     *
     * @throws IllegalArgumentException if [workDir] doesn't exist, isn't
     *         a directory, or has less than 512 MB usable space.
     */
    fun measure(workDir: File): Result {
        require(workDir.exists() && workDir.isDirectory) {
            "workDir must exist and be a directory: $workDir"
        }
        require(workDir.usableSpace > MIN_FREE_BYTES_REQUIRED) {
            "Insufficient free space: have ${workDir.usableSpace}, need > $MIN_FREE_BYTES_REQUIRED"
        }

        val tempFile = File(workDir, "probe_storage_test_${System.nanoTime()}.bin")

        // Deterministic non-zero pattern. Filesystems with transparent
        // compression (e.g., F2FS data_compression on some Xiaomi) collapse
        // all-zero writes — the byte ramp keeps every chunk distinct.
        val chunk = ByteArray(CHUNK_BYTES) { (it and 0xFF).toByte() }

        val start = System.nanoTime()
        var bytesWritten = 0L

        try {
            FileOutputStream(tempFile).use { fos ->
                while (bytesWritten < TARGET_BYTES) {
                    fos.write(chunk)
                    bytesWritten += CHUNK_BYTES.toLong()
                }
                fos.fd.sync()  // drain page cache so the timing reflects real I/O
            }
        } finally {
            tempFile.delete()
        }

        val elapsedMs = (System.nanoTime() - start) / 1_000_000L
        val mbps = (bytesWritten.toDouble() / 1_000_000.0) /
            (elapsedMs.toDouble() / 1_000.0)
        return Result(
            mbps = mbps,
            durationMs = elapsedMs,
            bytesWritten = bytesWritten
        )
    }
}
