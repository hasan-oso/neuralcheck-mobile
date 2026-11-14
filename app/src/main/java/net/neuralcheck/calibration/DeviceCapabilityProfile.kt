package net.neuralcheck.calibration

/**
 * Phase 1.8 — raw probe results captured at scan start, used to drive
 * relative calibration of the workload parameters.
 *
 * The probe runs each subsystem (memory bandwidth, CPU TFLite throughput,
 * GPU raymarching FPS, sequential storage write) for a short window and
 * records what THIS device can sustain. Downstream, [CalibratedWorkloadConfig]
 * derives per-phase workload sizes from these numbers so the diagnostic
 * stress is anchored to device capability instead of fixed thresholds.
 *
 * Fields are populated independently — if one subsystem probe throws, its
 * name is added to [probeFailedSubsystems] and the corresponding metric
 * is set to a sentinel value (typically 0.0). Consumers must guard against
 * zero/negative values.
 *
 * Serialised by Gson via reflection (project convention — no
 * `@Serializable` / `@JsonClass` annotations are used in this codebase).
 */
data class DeviceCapabilityProfile(
    /** Sustained read+write memory bandwidth observed during the probe, MB/s. */
    val memoryMbps: Double,
    /** TFLite inferences per second across 7 CPU workers. */
    val cpuInferencesPerSec: Double,
    /** Raymarching frames-per-second at a 1024×1024 offscreen FBO. */
    val gpuFpsAt1024Sq: Double,
    /** Sequential write throughput observed during the probe, MB/s. */
    val storageMbps: Double,
    /** `System.currentTimeMillis()` captured immediately before the probe began. */
    val probeStartedAtMs: Long,
    /** Total wall-clock time spent inside the probe, milliseconds. */
    val probeDurationMs: Long,
    /**
     * Subsystem probes that threw or returned an unusable value. Entries
     * are short identifiers like `"memory"`, `"cpu"`, `"gpu"`, `"storage"`.
     * Empty list when every probe succeeded.
     */
    val probeFailedSubsystems: List<String>
)
