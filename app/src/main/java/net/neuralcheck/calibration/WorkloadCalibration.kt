package net.neuralcheck.calibration

import kotlin.math.sqrt

// ─── Subsystem factors (% of probed capability the workload asks for) ───
private const val FACTOR_MEMORY = 0.90
private const val FACTOR_CPU = 0.90
private const val FACTOR_GPU = 0.90
private const val FACTOR_STORAGE = 0.85

// ─── Memory ───
// Reduced from 64 MB after Phase 1.8.E.
// AiMemoryWorkload uses RING_SIZE=4. With 64 MB:
// 4 × 64 MB = 256 MB exceeded Samsung S10 5G's
// process growth_limit (256 MB) when added to
// existing 224 MB footprint of TFLite engines +
// raymarcher + samples buffer.
// 8 MB × 4 = 32 MB total ring, matches legacy
// AiMemoryWorkload.BITMAP_BYTES (7.91 MB) ring
// size that has run safely in production since
// Phase 1.6. Still > 4 MB Samsung L3 cache, so
// memory pressure (RAM access) is preserved.
private const val MEMORY_BUFFER_BYTES = 8L * 1024 * 1024    // 8 MB
private const val MEMORY_RATE_FLOOR_HZ = 10.0
// Raised from 1000 after buffer reduction.
// 8 MB buffer requires ~2250 Hz rate to maintain
// 90% bandwidth utilization on Samsung S10 5G
// (probe = 20366 MB/s, target = 18329 MB/s,
// rate = 18329/8 = 2291 Hz). Ceiling 2500
// accommodates Samsung at full 90% target.
// Future flagship phones (>27 GB/s) will hit
// ceiling and calibrate to ~67% target — acceptable
// for now, can raise ceiling later if needed.
private const val MEMORY_RATE_CEILING_HZ = 2500.0

// ─── CPU ───
private const val CPU_INFERENCES_FLOOR = 5.0

// ─── GPU ───
private const val GPU_PROBE_FBO_SIDE = 1024
private const val GPU_TARGET_FPS = 60
private const val GPU_FBO_FLOOR = 512
private const val GPU_FBO_CEILING = 4096
private const val GPU_FBO_ROUND_TO = 256  // round fbo_side down to multiple of 256

// ─── Storage ───
private const val STORAGE_FLOOR_BYTES_PER_SEC = 50L * 1024 * 1024  // 50 MB/s
private const val STORAGE_CEILING_BYTES_PER_SEC = 2L * 1024 * 1024 * 1024  // 2 GB/s

// ─── BURST FMA — placeholder ratio, tune in Phase 1.8.E validation ───
private const val BURST_PULSE_DURATION_SEC = 0.5
private const val BURST_FMA_PER_INFERENCE = 50_000.0  // TODO: tune from Samsung 1.8.E data
private const val BURST_FMA_FLOOR = 100_000L
private const val BURST_FMA_CEILING = 100_000_000L

// ─── Conservative fallbacks (used when a subsystem's probe failed) ───
private const val FALLBACK_MEMORY_RATE_HZ = 15.0
private const val FALLBACK_CPU_INFERENCES_PER_SEC = 10.0
private const val FALLBACK_GPU_FBO_SIDE = 1024
private const val FALLBACK_STORAGE_BYTES_PER_SEC = 100L * 1024 * 1024  // 100 MB/s
private const val FALLBACK_BURST_FMA_PER_PULSE = 500_000L

/**
 * Phase 1.8 — pure JVM logic that turns a [DeviceCapabilityProfile]
 * (raw probe results) into a [CalibratedWorkloadConfig] (per-phase
 * workload parameters).
 *
 * Per subsystem, the rules are:
 *   1. If the probe failed (subsystem name appears in
 *      [DeviceCapabilityProfile.probeFailedSubsystems]), use the
 *      conservative fallback constant and add the name to
 *      [CalibrationFlags.probeFailed]. No double-flagging — the
 *      floor/ceiling check is skipped.
 *   2. Otherwise apply the subsystem factor (~90% by default), then
 *      clamp to [floor, ceiling]. If a clamp triggers, add the
 *      subsystem name to [CalibrationFlags.probeBelowFloor] or
 *      [CalibrationFlags.probeAboveCeiling].
 *
 * BURST FMA is special-cased: it derives from the CPU probe, so a CPU
 * probe failure routes BURST through the fallback too (without adding
 * a separate flag — `cpu` is already in `probeFailed`).
 *
 * No Android imports — fully unit-testable on the JVM. No state — the
 * function is pure and idempotent.
 */
object WorkloadCalibration {

    fun calibrate(profile: DeviceCapabilityProfile): CalibratedWorkloadConfig {
        val failed = profile.probeFailedSubsystems.toSet()

        // Accumulate flag lists across subsystems. Each list ends up
        // small (<= 4 entries) so the `+= name` cost is negligible.
        val probeFailed = mutableListOf<String>()
        val probeBelowFloor = mutableListOf<String>()
        val probeAboveCeiling = mutableListOf<String>()

        // ── Memory iteration rate ──────────────────────────────────────
        val memoryRate: Double = if ("memory" in failed) {
            probeFailed += "memory"
            FALLBACK_MEMORY_RATE_HZ
        } else {
            val rawRate = (profile.memoryMbps * 1_000_000.0 * FACTOR_MEMORY) /
                MEMORY_BUFFER_BYTES.toDouble()
            when {
                rawRate < MEMORY_RATE_FLOOR_HZ -> {
                    probeBelowFloor += "memory"
                    MEMORY_RATE_FLOOR_HZ
                }
                rawRate > MEMORY_RATE_CEILING_HZ -> {
                    probeAboveCeiling += "memory"
                    MEMORY_RATE_CEILING_HZ
                }
                else -> rawRate
            }
        }

        // ── CPU inferences/sec ─────────────────────────────────────────
        val cpuInferences: Double = if ("cpu" in failed) {
            probeFailed += "cpu"
            FALLBACK_CPU_INFERENCES_PER_SEC
        } else {
            val rawInf = profile.cpuInferencesPerSec * FACTOR_CPU
            if (rawInf < CPU_INFERENCES_FLOOR) {
                probeBelowFloor += "cpu"
                CPU_INFERENCES_FLOOR
            } else {
                rawInf
                // No upper ceiling — bounded by probe input.
            }
        }

        // ── GPU FBO side ───────────────────────────────────────────────
        val gpuFboSide: Int = if ("gpu" in failed) {
            probeFailed += "gpu"
            FALLBACK_GPU_FBO_SIDE
        } else {
            val probePps = profile.gpuFpsAt1024Sq *
                GPU_PROBE_FBO_SIDE.toDouble() *
                GPU_PROBE_FBO_SIDE.toDouble()
            val targetPps = probePps * FACTOR_GPU
            val targetPixelsPerFrame = targetPps / GPU_TARGET_FPS.toDouble()
            val rawSide = sqrt(targetPixelsPerFrame).toInt()
            // Round DOWN to multiple of GPU_FBO_ROUND_TO (256). Integer
            // division does the floor.
            val rounded = (rawSide / GPU_FBO_ROUND_TO) * GPU_FBO_ROUND_TO
            when {
                rounded < GPU_FBO_FLOOR -> {
                    probeBelowFloor += "gpu"
                    GPU_FBO_FLOOR
                }
                rounded > GPU_FBO_CEILING -> {
                    probeAboveCeiling += "gpu"
                    GPU_FBO_CEILING
                }
                else -> rounded
            }
        }

        // ── Storage write bytes/sec ────────────────────────────────────
        val storageBps: Long = if ("storage" in failed) {
            probeFailed += "storage"
            FALLBACK_STORAGE_BYTES_PER_SEC
        } else {
            val rawBps = (profile.storageMbps * 1_000_000.0 * FACTOR_STORAGE).toLong()
            when {
                rawBps < STORAGE_FLOOR_BYTES_PER_SEC -> {
                    probeBelowFloor += "storage"
                    STORAGE_FLOOR_BYTES_PER_SEC
                }
                rawBps > STORAGE_CEILING_BYTES_PER_SEC -> {
                    probeAboveCeiling += "storage"
                    STORAGE_CEILING_BYTES_PER_SEC
                }
                else -> rawBps
            }
        }

        // ── BURST FMA per pulse ────────────────────────────────────────
        // CPU-probe failure already produced a "cpu" flag above —
        // route BURST through the fallback without a second flag.
        val burstFma: Long = if ("cpu" in failed) {
            FALLBACK_BURST_FMA_PER_PULSE
        } else {
            val rawFma = profile.cpuInferencesPerSec *
                FACTOR_CPU *
                BURST_PULSE_DURATION_SEC *
                BURST_FMA_PER_INFERENCE
            rawFma.toLong().coerceIn(BURST_FMA_FLOOR, BURST_FMA_CEILING)
        }

        return CalibratedWorkloadConfig(
            memoryBufferBytes = MEMORY_BUFFER_BYTES,
            memoryIterationRateHz = memoryRate,
            cpuInferencesPerSec = cpuInferences,
            gpuFboSidePixels = gpuFboSide,
            storageWriteBytesPerSec = storageBps,
            burstFmaPerPulse = burstFma,
            factors = CalibrationFactors(),
            flags = CalibrationFlags(
                probeFailed = probeFailed.toList(),
                probeBelowFloor = probeBelowFloor.toList(),
                probeAboveCeiling = probeAboveCeiling.toList()
            ),
            sourceProfile = profile
        )
    }
}
