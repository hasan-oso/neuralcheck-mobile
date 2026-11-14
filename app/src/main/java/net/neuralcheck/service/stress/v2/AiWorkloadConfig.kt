package net.neuralcheck.service.stress.v2

/**
 * Phase 1.6.E — Tier-derived AI workload knobs.
 *
 * Built once at scan start by
 * [AdaptiveIntensityController.aiConfigForTier] and consumed by
 * [AiCpuWorkload], [AiGpuWorkload], and [AiMemoryWorkload]. Each
 * workload picks the model name + iteration target appropriate for
 * its phase.
 *
 * Kept as a data class (not a sealed hierarchy) so adding new tiers
 * later is a one-arm change to the `when` block in the controller —
 * no per-workload signature churn.
 */
data class AiWorkloadConfig(
    /** Asset filename of the heavy MobileNet model (~14 MB). */
    val largeModelName: String,
    /** Asset filename of the lighter MobileNet variant (~6 MB). */
    val smallModelName: String,
    /** Target inferences per second (per worker). The CPU workload
     *  caps inter-inference sleep so worker throughput stays at or
     *  below this; the GPU workload uses it the same way. Memory
     *  workload uses it as a guideline for the inter-pass sleep. */
    val targetIterPerSec: Int,
    /** Asset filename selected for the CPU phase (3 + half of 5). */
    val cpuModelChoice: String,
    /** Asset filename selected for the GPU phase (4 + half of 5). */
    val gpuModelChoice: String,
    /**
     * Phase 1.8 Task 4 — calibrated buffer size for the memory workload.
     *
     * Default `null` so existing call sites (Phase 1.6.E
     * AdaptiveIntensityController, tests) keep producing the legacy
     * 1080×1920×4 = 8 294 400 B BITMAP_BYTES default that
     * [AiMemoryWorkload] applies internally when this is null.
     *
     * Will be populated from
     * [net.neuralcheck.calibration.CalibratedWorkloadConfig.memoryBufferBytes]
     * once Task 5 wires the calibration path. Until then this stays
     * null on every code path and the workload behaves identically to
     * Phase 1.7.
     */
    val memoryBufferBytes: Long? = null
) {
    companion object {
        const val MODEL_LARGE: String = "mobilenet_v3_large_224.tflite"
        const val MODEL_SMALL: String = "mobilenet_v3_small_224.tflite"
    }
}
