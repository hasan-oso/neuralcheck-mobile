package net.neuralcheck.calibration.probes

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.8 — unit tests for [MemoryProbe.measure].
 *
 * Pure JVM, no Android imports. The 5 s probe runs entirely in the
 * unit-test JVM via DirectByteBuffer copy.
 *
 * Bandwidth thresholds are deliberately loose — the JVM unit-test
 * environment varies from CI runners (limited cores, throttled) to
 * dev machines (modern desktop DDR5). 100 MB/s is well below any
 * platform that can run JUnit at all.
 */
class MemoryProbeTest {

    @Test
    fun measure_returnsPositiveBandwidth() {
        val result = MemoryProbe.measure()
        assertTrue(
            "expected mbps > 100.0 (got ${result.mbps})",
            result.mbps > 100.0
        )
        assertTrue(
            "expected bytesProcessed > 0 (got ${result.bytesProcessed})",
            result.bytesProcessed > 0
        )
    }

    @Test
    fun measure_runsApproximately5Seconds() {
        val result = MemoryProbe.measure()
        // Spec target: 5 s ± 1.5 s tolerance for slow CI / GC pauses.
        assertTrue(
            "expected durationMs in 4500..6500 (got ${result.durationMs})",
            result.durationMs in 4500..6500
        )
    }
}
