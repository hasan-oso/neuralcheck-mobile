package net.neuralcheck.calibration.probes

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Phase 1.8 — unit tests for [StorageProbe.measure].
 *
 * Uses JUnit's [TemporaryFolder] rule so the 256 MB write lands inside
 * a per-test directory that the framework cleans up automatically.
 *
 * Bandwidth ranges are NOT asserted — host disks vary wildly (NVMe vs
 * spinning vs CI scratch). Instead we assert the basics: positive
 * throughput, the full 256 MB got written, and the temp file is gone
 * afterwards.
 */
class StorageProbeTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun measure_returnsPositiveThroughput() {
        val workDir = tempFolder.newFolder()
        val result = StorageProbe.measure(workDir)

        assertTrue(
            "expected mbps > 0.0 (got ${result.mbps})",
            result.mbps > 0.0
        )
        assertTrue(
            "expected bytesWritten >= 256 MB (got ${result.bytesWritten})",
            result.bytesWritten >= 256L * 1024 * 1024
        )

        // Probe must clean up after itself. List should not contain any
        // .bin files — TemporaryFolder cleans the directory on test
        // teardown but the assertion below is what guards against a
        // future change forgetting the `finally { tempFile.delete() }`.
        val leftover = workDir.listFiles { _, name -> name.endsWith(".bin") }
        assertTrue(
            "no leftover .bin files expected, found: ${leftover?.toList()}",
            leftover == null || leftover.isEmpty()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun measure_throwsWhenWorkDirInvalid() {
        // Use a path that cannot exist on any platform — plain string
        // arithmetic is enough; we don't need to create + delete anything.
        StorageProbe.measure(File("/nonexistent/path/that/doesnt/exist"))
    }
}
