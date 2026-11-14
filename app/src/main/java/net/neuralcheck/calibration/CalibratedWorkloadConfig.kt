package net.neuralcheck.calibration

/**
 * Phase 1.8 — calibrated per-phase workload parameters derived from a
 * [DeviceCapabilityProfile]. The probe measures what the device can do;
 * this dataclass records what the workload actually asks of it (which
 * is typically `factor × probed-capability`).
 *
 * Each numeric field is the parameter that gets passed into the
 * corresponding workload class:
 *   - `memoryBufferBytes`        → AiMemoryWorkload buffer size
 *   - `memoryIterationRateHz`    → AiMemoryWorkload pass cadence
 *   - `cpuInferencesPerSec`      → AiCpuWorkload target rate
 *   - `gpuFboSidePixels`         → RaymarchingGpuLoad FBO dimension
 *   - `storageWriteBytesPerSec`  → STORAGE_STRESS write rate
 *   - `burstFmaPerPulse`         → BURST_PATTERN FMA iteration count
 *
 * `factors` records the percentage-of-capability targets that were
 * applied. `flags` records any guard rails that triggered (probe
 * failures, floor/ceiling clamps). `sourceProfile` is the raw probe
 * result the calibration started from — both are kept so the lab can
 * reproduce the calibration end-to-end from one footer block.
 *
 * Serialised by Gson via reflection (project convention).
 */
data class CalibratedWorkloadConfig(
    /** Memory buffer size for AiMemoryWorkload, bytes. */
    val memoryBufferBytes: Long,
    /** Target memory-pass cadence, Hz. */
    val memoryIterationRateHz: Double,
    /** Target TFLite inference rate, inferences/sec. */
    val cpuInferencesPerSec: Double,
    /** Square-FBO side length for the raymarcher, pixels. */
    val gpuFboSidePixels: Int,
    /** Sequential storage write rate, bytes/sec. */
    val storageWriteBytesPerSec: Long,
    /** NEON-FMA iterations per BURST pulse. */
    val burstFmaPerPulse: Long,
    /** Calibration percentages applied to the probe baseline. */
    val factors: CalibrationFactors,
    /** Guard-rail flags that fired during calibration. */
    val flags: CalibrationFlags,
    /** Raw probe results that drove this config — kept for reproducibility. */
    val sourceProfile: DeviceCapabilityProfile
)

/**
 * Phase 1.8 — percentage-of-capability targets applied per subsystem.
 *
 * Defaults match the conservative baseline picked during the Phase 1.8
 * design: 90 % of measured capability for memory / CPU / GPU (leaves
 * thermal headroom + measurement noise budget), 85 % for storage to
 * avoid SLC-cache exhaustion noise during the diagnostic window.
 */
data class CalibrationFactors(
    val memory: Double = 0.90,
    val cpu: Double = 0.90,
    val gpu: Double = 0.90,
    val storage: Double = 0.85
)

/**
 * Phase 1.8 — guard-rail flags surfaced from the calibration step.
 * Each list contains short subsystem identifiers (`"memory"`, `"cpu"`,
 * `"gpu"`, `"storage"`).
 *
 * - [probeFailed]: subsystem probe threw or produced an unusable value.
 * - [probeBelowFloor]: probed value was below the safe minimum and the
 *   corresponding workload param got clamped up.
 * - [probeAboveCeiling]: probed value was above the safe maximum and the
 *   corresponding workload param got clamped down.
 *
 * Empty defaults so a clean device produces a flag-free record.
 */
data class CalibrationFlags(
    val probeFailed: List<String> = emptyList(),
    val probeBelowFloor: List<String> = emptyList(),
    val probeAboveCeiling: List<String> = emptyList()
)
