package net.neuralcheck.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.8 — unit tests for [WorkloadCalibration.calibrate].
 *
 * Pure JVM, no Android imports — runs under `:app:testLaunchDebugUnitTest`
 * without needing a device, NDK, or Android stubs.
 */
class WorkloadCalibrationTest {

    /** Reasonable defaults for fields a test doesn't care about. */
    private fun makeProfile(
        memoryMbps: Double = 12_000.0,
        cpuInferencesPerSec: Double = 30.0,
        gpuFpsAt1024Sq: Double = 240.0,
        storageMbps: Double = 900.0,
        probeFailedSubsystems: List<String> = emptyList()
    ) = DeviceCapabilityProfile(
        memoryMbps = memoryMbps,
        cpuInferencesPerSec = cpuInferencesPerSec,
        gpuFpsAt1024Sq = gpuFpsAt1024Sq,
        storageMbps = storageMbps,
        probeStartedAtMs = 1_700_000_000_000L,
        probeDurationMs = 2_500L,
        probeFailedSubsystems = probeFailedSubsystems
    )

    // 1
    @Test
    fun flagshipDevice_producesCalibratedValuesAboveFloors() {
        val profile = makeProfile(
            memoryMbps = 12_000.0,
            cpuInferencesPerSec = 30.0,
            gpuFpsAt1024Sq = 240.0,
            storageMbps = 900.0
        )
        val cfg = WorkloadCalibration.calibrate(profile)

        // Phase 1.8.F: buffer 8 MiB → (12000 × 1e6 × 0.90) / 8 MiB
        // ≈ 1287.46. Spec allows ≈ 1287 ± 1.0 (tolerance widened by
        // ~2× vs the old 0.5 because the absolute number is ~8× larger).
        assertEquals(1287.0, cfg.memoryIterationRateHz, 1.0)
        // 30 * 0.90 = 27.0 (above the 5.0 floor)
        assertEquals(27.0, cfg.cpuInferencesPerSec, 0.0001)
        // sqrt((240*1024*1024*0.90)/60) ≈ 1942 → rounded down to 7×256 = 1792
        assertEquals(1792, cfg.gpuFboSidePixels)
        // 900 * 1e6 * 0.85 = 765_000_000 (between 50 MB/s floor and 2 GB/s ceiling)
        assertEquals(765_000_000L, cfg.storageWriteBytesPerSec)

        assertTrue("probeFailed expected empty: ${cfg.flags.probeFailed}",
            cfg.flags.probeFailed.isEmpty())
        assertTrue("probeBelowFloor expected empty: ${cfg.flags.probeBelowFloor}",
            cfg.flags.probeBelowFloor.isEmpty())
        assertTrue("probeAboveCeiling expected empty: ${cfg.flags.probeAboveCeiling}",
            cfg.flags.probeAboveCeiling.isEmpty())
    }

    // 2
    @Test
    fun weakDevice_multipleFloorsHit() {
        val profile = makeProfile(
            memoryMbps = 50.0,
            cpuInferencesPerSec = 2.0,
            gpuFpsAt1024Sq = 10.0,
            storageMbps = 20.0
        )
        val cfg = WorkloadCalibration.calibrate(profile)

        assertEquals(10.0, cfg.memoryIterationRateHz, 0.0001)              // floor
        assertEquals(5.0, cfg.cpuInferencesPerSec, 0.0001)                  // floor
        assertEquals(512, cfg.gpuFboSidePixels)                             // floor
        assertEquals(50L * 1024 * 1024, cfg.storageWriteBytesPerSec)        // floor

        val below = cfg.flags.probeBelowFloor
        assertTrue("memory in probeBelowFloor: $below", "memory" in below)
        assertTrue("cpu in probeBelowFloor: $below", "cpu" in below)
        assertTrue("gpu in probeBelowFloor: $below", "gpu" in below)
        assertTrue("storage in probeBelowFloor: $below", "storage" in below)
        assertEquals(4, below.size)

        assertTrue(cfg.flags.probeFailed.isEmpty())
        assertTrue(cfg.flags.probeAboveCeiling.isEmpty())
    }

    // 3
    @Test
    fun memoryProbeFailed_usesFallback() {
        val profile = makeProfile(
            memoryMbps = 0.0,
            probeFailedSubsystems = listOf("memory")
        )
        val cfg = WorkloadCalibration.calibrate(profile)

        // Fallback constant lives in WorkloadCalibration.kt as
        // FALLBACK_MEMORY_RATE_HZ = 15.0 (private — re-stated in the test).
        assertEquals(15.0, cfg.memoryIterationRateHz, 0.0001)

        assertTrue("memory in probeFailed", "memory" in cfg.flags.probeFailed)
        assertEquals(1, cfg.flags.probeFailed.size)
        // No double-flagging: memory fallback path skips the floor check.
        assertTrue("memory should NOT be in probeBelowFloor",
            "memory" !in cfg.flags.probeBelowFloor)
    }

    // 4
    @Test
    fun allProbesFailed_returnsAllFallbacks() {
        val profile = makeProfile(
            probeFailedSubsystems = listOf("memory", "cpu", "gpu", "storage")
        )
        val cfg = WorkloadCalibration.calibrate(profile)

        assertEquals(15.0, cfg.memoryIterationRateHz, 0.0001)
        assertEquals(10.0, cfg.cpuInferencesPerSec, 0.0001)
        assertEquals(1024, cfg.gpuFboSidePixels)
        assertEquals(100L * 1024 * 1024, cfg.storageWriteBytesPerSec)
        assertEquals(500_000L, cfg.burstFmaPerPulse)

        val failed = cfg.flags.probeFailed
        assertEquals(4, failed.size)
        assertTrue("memory" in failed)
        assertTrue("cpu" in failed)
        assertTrue("gpu" in failed)
        assertTrue("storage" in failed)

        assertTrue(cfg.flags.probeBelowFloor.isEmpty())
        assertTrue(cfg.flags.probeAboveCeiling.isEmpty())
    }

    // 5
    @Test
    fun extremelyHighMemory_hitsCeiling() {
        val profile = makeProfile(memoryMbps = 1_000_000.0)
        val cfg = WorkloadCalibration.calibrate(profile)

        // Phase 1.8.F: ceiling raised from 1000 to 2500 Hz to keep
        // 90% bandwidth utilization with the 8 MB buffer. 1_000_000 MB/s
        // probe is still well above the ceiling.
        assertEquals(2500.0, cfg.memoryIterationRateHz, 0.0001)
        assertTrue("memory in probeAboveCeiling: ${cfg.flags.probeAboveCeiling}",
            "memory" in cfg.flags.probeAboveCeiling)
    }

    // 6
    @Test
    fun extremelyHighGpu_hitsCeiling() {
        val profile = makeProfile(gpuFpsAt1024Sq = 10_000.0)
        val cfg = WorkloadCalibration.calibrate(profile)

        assertEquals(4096, cfg.gpuFboSidePixels)
        assertTrue("gpu in probeAboveCeiling: ${cfg.flags.probeAboveCeiling}",
            "gpu" in cfg.flags.probeAboveCeiling)
    }

    // 7
    @Test
    fun burstFma_scalesWithCpu() {
        val a = WorkloadCalibration.calibrate(makeProfile(cpuInferencesPerSec = 20.0))
        val b = WorkloadCalibration.calibrate(makeProfile(cpuInferencesPerSec = 40.0))

        // Both above floor (20 * 0.9 = 18 > 5; 40 * 0.9 = 36 > 5) and below
        // ceiling (raw FMA stays in tight band). Ratio should be 2.0.
        val ratio = b.burstFmaPerPulse.toDouble() / a.burstFmaPerPulse.toDouble()
        assertEquals(
            "burstFma should scale linearly with cpu probe (got A=${a.burstFmaPerPulse}, B=${b.burstFmaPerPulse})",
            2.0, ratio, 0.01
        )
    }

    // 8
    @Test
    fun burstFma_clampsToFloor_whenCpuVeryLow() {
        val profile = makeProfile(cpuInferencesPerSec = 0.001)
        val cfg = WorkloadCalibration.calibrate(profile)

        // Raw fma ≈ 0.001 * 0.9 * 0.5 * 50_000 = 22.5 → coerce up to floor.
        assertEquals(100_000L, cfg.burstFmaPerPulse)
    }

    // 9
    @Test
    fun sourceProfile_passesThroughUnchanged() {
        val profile = makeProfile()
        val cfg = WorkloadCalibration.calibrate(profile)
        // assertSame: reference identity, not value equality.
        assertSame("sourceProfile must be the exact same instance",
            profile, cfg.sourceProfile)
    }

    // 10
    @Test
    fun factors_alwaysDefaults() {
        // Run calibration twice with very different profiles; factors
        // should be the dataclass defaults regardless of inputs.
        val a = WorkloadCalibration.calibrate(makeProfile())
        val b = WorkloadCalibration.calibrate(
            makeProfile(probeFailedSubsystems = listOf("memory", "cpu", "gpu", "storage"))
        )
        val expected = CalibrationFactors()
        assertEquals(expected, a.factors)
        assertEquals(expected, b.factors)
        assertEquals(0.90, a.factors.memory, 0.0001)
        assertEquals(0.90, a.factors.cpu, 0.0001)
        assertEquals(0.90, a.factors.gpu, 0.0001)
        assertEquals(0.85, a.factors.storage, 0.0001)
    }
}
