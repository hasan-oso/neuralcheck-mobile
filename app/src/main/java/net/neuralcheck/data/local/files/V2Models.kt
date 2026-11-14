package net.neuralcheck.data.local.files

/**
 * One throughput sample from the storage stress phase. Each segment
 * represents a fixed-size write window (typically 256 MiB), captured
 * sequentially as the buffer fills. The Cloud Function side computes
 * slope and SLC cache exhaustion point from the full sequence — the
 * mobile side only collects raw measurements.
 */
data class StorageThroughputSegment(
    val cumulativeBytes: Long,   // total bytes written by end of this segment
    // A70: binary MiB/s (bytes / elapsed_seconds / 2^20) as of the build
    // that first ships this fix — same convention as v[9] memory_bandwidth_mbps
    // and S-03/S-04 (StorageTests.kt). fieldsVersion <= 67 segments/v[8]
    // samples were decimal MB/s (÷10^6) instead; see the fix's comment at
    // its computation site in StressTestServiceV2.kt and
    // docs/MEASUREMENT_SPEC.md §3.1 note 1.
    val mbps: Float              // throughput during this segment
)

/**
 * V2 stress test data models. Lives alongside V1 (TimeSeriesFileWriter.kt)
 * for the duration of the migration; V1 components keep working while
 * V2 lands incrementally.
 *
 * Per master plan §2 — three things change vs V1:
 *   1. 22 sample variables (was 18)
 *   2. Explicit phase 1..8 (was 1..5, with phases 2 and 4 ignored by stats)
 *   3. Footer carries verification + adaptive intensity + throttling
 */

/**
 * One throttling event, recorded when DynamicThermalThrottler reduces
 * (or terminates) a phase due to skin_temp crossing a threshold. The
 * footer's `throttlingEvents` list captures every transition so the
 * AI can correlate slowdowns with thermal pressure.
 */
data class ThrottlingEvent(
    val timestamp: Long,
    val phase: Int,
    val skinTemp: Float,
    val newIntensity: Float,
    val reason: String,  // "skin>45", "skin>48", "skin>50_terminate"
    /**
     * Bug fix #27: distinguishes sysfs-measured skin temperature
     * (true) from synthetic values derived from
     * PowerManager.thermalStatus (false). Synthetic events DO NOT
     * trigger phase terminate — they are recorded for AI visibility
     * but the scan continues. Without this distinction, devices
     * that block thermal sysfs (e.g. Samsung S10 on API 30) would
     * see PowerManager status = SEVERE mapped to a synthetic 52°C,
     * which trespassed the throttler's TERMINATE threshold (50°C)
     * and ended every stress phase prematurely while the actual
     * battery temp was only 39°C.
     */
    val sourceIsSysfs: Boolean = true,
    /**
     * fieldsVersion 65 (additive): true ONLY when this event actually
     * changed the workload — i.e. a level-3 event whose phase-terminate
     * callback really fired. Level 1/2 "reduce intensity" events are
     * ALWAYS applied=false: [DynamicThermalThrottler.effectiveIntensity]
     * is computed and recorded here, but no stress workload consumes it
     * (they all read the probe's constant intensityFactor). Before v65
     * the file claimed "newIntensity=0.8" while the load never changed —
     * a recorded decision masquerading as an applied one.
     */
    val applied: Boolean = false
)

/**
 * V2 fix #56 (2.4): per-sensor data quality summary. Lets the AI know
 * how much of the scan duration each signal was actually populated, and
 * the source(s) used. Critical for scoring confidence: a device that
 * blocked thermal sysfs the entire scan should not be graded the same
 * as one that delivered first-class measurements.
 *
 * Coverage values are 0..100 (% of total samples). Source tags identify
 * the primary path (sysfs vs proxy vs missing). All fields are additive
 * — adding more in the future will not break older parsers.
 */
data class DataQuality(
    /** % of samples where cpu_temp_c > 0 (regardless of source). */
    val cpuTempCoveragePct: Float = 0f,
    /** "sysfs_zone_match" | "thermal_fallback" | "max_zone_filtered" |
     *  "hardware_properties_manager" | "missing". */
    val cpuTempPrimarySource: String = "missing",
    /** % of samples where gpu_temp_c > 0. */
    val gpuTempCoveragePct: Float = 0f,
    /** % of samples where skin_temp_c > 0. */
    val skinTempCoveragePct: Float = 0f,
    /** Per-source counts from ThermalFallback's tally:
     *  e.g. {"sysfs_skin": 1240, "battery_proxy": 60, "missing": 0}. */
    val skinTempSourceBreakdown: Map<String, Int> = emptyMap(),
    /** % of samples where cpu_freq_big_khz > 0. */
    val cpuFreqBigCoveragePct: Float = 0f,
    /** True if at least one sample produced /proc/stat-based usage
     *  (vs process-level fallback).
     *
     *  fieldsVersion 66 SEMANTIC FIX: builds ≤65 computed this as
     *  "any v[6] > 0" — i.e. channel non-emptiness — so it read true even
     *  when every sample came from the process-level fallback. It is now
     *  derived from the reader's actual provenance flag: true iff
     *  [cpuUsageFallbackFromSample] != 0 (no fallback, or a fallback that
     *  engaged after ≥1 real /proc/stat sample). */
    val procStatAvailable: Boolean = false,
    /** True if BatteryReader.register() succeeded before first sample. */
    val batteryReaderRegistered: Boolean = false,
    /** True if scaling was committed via decisive evidence (not default). */
    val batteryScalingLocked: Boolean = false,
    /** Detailed lockSource string (e.g. "data_ma_format_evidence_3"). */
    val batteryScalingDetailedSource: String = "unknown",
    /** Active scale (1 = µA, 1000 = mA). */
    val batteryScalingMultiplier: Int = 1,
    /** Active sign flip (-1 = vendor-inverted, +1 = AOSP-compliant). */
    val batteryScalingSignFlip: Int = 1,
    // ─────── fieldsVersion 65 (additive) — measurement integrity ───────
    /** Sample index (BatteryReader's own counter) at which the scaling
     *  lock committed. -1 = never locked. battery_current_ua samples
     *  BEFORE this index were normalised with the soft manufacturer
     *  default and can be re-interpreted by the AI / training pipeline. */
    val batteryScalingLockAtSample: Int = -1,
    /** Count of Int.MIN_VALUE CURRENT_NOW readings filtered mid-scan.
     *  Each surfaced as the 0 "no data" sentinel in v[3]/v[7] instead of
     *  leaking ±2.1e9 µA. >0 tells the AI some v[3]=0 ticks were sensor
     *  failures, not idle current. */
    val batteryInvalidCurrentReadings: Int = 0,
    /** Count of EXTRA_VOLTAGE readings detected as µV (raw > 100 000)
     *  and divided by 1000 before entering v[2]. >0 means this vendor
     *  reports µV and the conversion was applied for the whole scan. */
    val batteryVoltageUvScaledCount: Int = 0,
    // ─────── fieldsVersion 66 (additive) — v[6] provenance ───────
    /**
     * What v[6] cpu_usage_pct actually measured at end of scan:
     *   "procstat"         — global `/proc/stat` delta (system utilisation)
     *   "process_fallback" — this app's own CPU time ÷ (wall × cores):
     *                        the workload's share, NOT system load.
     *   "unknown"          — pre-66 footer.
     * The fallback is sticky in SystemFileReader (never switches back), so
     * together with [cpuUsageFallbackFromSample] this describes every
     * sample's source without touching v[]. `/proc/stat` is SELinux-denied
     * to untrusted apps since Android 8, so "process_fallback" is the
     * expected value on virtually every commercial device.
     */
    val cpuUsageSource: String = "unknown",
    /**
     * Index (0-based, sampler order) of the FIRST sample whose v[6] came
     * from the process-level fallback; -1 = every sample used /proc/stat.
     * Samples before this index are system utilisation, samples from it
     * on are the app's own share.
     */
    val cpuUsageFallbackFromSample: Int = -1
)

/**
 * V2 fix #54: stress effectiveness assessment. Detects "stress test
 * failed to stress" — when the device's thermal governor (e.g. MIUI)
 * pre-emptively caps frequency before the test could observe real load.
 *
 * Without this, the AI sees benign metrics (low temp, low usage) on a
 * device that simply wasn't allowed to push hard, and may award an
 * undeserved A grade. The flags here let the AI know whether the data
 * actually reflects the device's stress capability.
 *
 * The mobile side reports raw measurements; the AI is the judge.
 */
data class StressEffectiveness(
    /**
     * Ratio of phase 3 peak cpu_freq_big to the SoC's nominal max.
     *
     * @deprecated since fieldsVersion 58 (2026-05-04, V2 fix #56-3): superseded
     * by per-cluster fields [phase3CpuFreqRatioBig], [phase3CpuFreqRatioMid],
     * [phase3CpuFreqRatioLittle]. Kept as alias = max(big, mid, little) so the
     * lab's Cloud Function and DeepSeek prompts that read this name keep working
     * during the rollout. fieldsVersion 60 also added the multi-axis saturation
     * criterion (satC1-C4Passed) which supersedes any single freq threshold.
     * Removal target: fieldsVersion 61.
     */
    @Deprecated(
        message = "Use phase3CpuFreqRatioBig/Mid/Little or satC4Passed. Removal target: fieldsVersion 61.",
        replaceWith = ReplaceWith("phase3CpuFreqRatioBig")
    )
    val phase3CpuFreqRatio: Float = 0f,
    /**
     * Average cpu_usage_pct across phase 3 samples (GLOBAL, /proc/stat overall line).
     *
     * @deprecated since fieldsVersion 58 (2026-05-04, V2 fix #56-3): superseded by
     * per-cluster fields [phase3CpuUsageAvgBig], [phase3CpuUsageAvgMid],
     * [phase3CpuUsageAvgLittle]. The global metric was the root cause of the
     * Samsung S10 5G false-negative saturation bug — workload runs on big+mid
     * (4 of 8 cores), so global usage maxes out near 50%, never crossing the
     * 85% threshold. Kept as alias so lab/DeepSeek prompts keep working.
     * fieldsVersion 60 also retained this for C2 Path B (fallback when
     * per-CPU /proc/stat is SELinux-blocked, e.g. on Xiaomi). Removal
     * target: fieldsVersion 61.
     */
    @Deprecated(
        message = "Use phase3CpuUsageAvgBig/Mid/Little. Path B of multi-axis criterion still consumes this internally. Removal target: fieldsVersion 61.",
        replaceWith = ReplaceWith("phase3CpuUsageAvgBig")
    )
    val phase3CpuUsageAvg: Float = 0f,
    /** Peak package_power_mw across phases 4-5. Real stress draws
     *  hundreds of mW above idle baseline; capped governors keep this
     *  flat near idle. */
    val phase5PowerPeakMw: Int = 0,
    /** Composite verdict: did the test reach thermal saturation?
     *  Pre-58 logic: (freqRatio >= 0.85 AND cpuUsageAvg >= 85) on globals.
     *  Post-58 logic: per-cluster check on targeted clusters (big + mid).
     *  When per-CPU usage is unavailable (legacy scan or SELinux-blocked
     *  /proc/stat), this falls back to freq-only with reason
     *  "per_cpu_usage_unavailable_freq_only_check". */
    val passedSaturationThreshold: Boolean = false,
    /** When passedSaturationThreshold is false, this string identifies
     *  why for the AI's downstream reasoning. Examples:
     *    "cpu_freq_capped_by_governor (55% of nominal)"
     *    "low_cpu_usage_during_stress (avg 62%)"
     *    "big_cluster_oscillating (mean ratio 0.63, peak 0.92)"
     *    "per_cpu_usage_unavailable, freq_only_check_passed"
     *    "no_phase3_data" — sampler didn't capture phase 3
     *    null when saturated. */
    val saturationFailureReason: String? = null,
    // ─────── V2 fix #56 follow-up #3 (fieldsVersion 58) ───────
    // Per-cluster freq ratios. Computable from existing scans because v[6/7/8]
    // already record big/little/mid frequencies per sample. null when phase 3
    // had no samples or when nominal freq lookup failed for that cluster.
    /** Ratio of phase 3 MEAN cpu_freq_big_khz / nominal big-cluster max. */
    val phase3CpuFreqRatioBig: Float? = null,
    /** Ratio of phase 3 MEAN cpu_freq_mid_khz / nominal mid-cluster max. */
    val phase3CpuFreqRatioMid: Float? = null,
    /** Ratio of phase 3 MEAN cpu_freq_little_khz / nominal little-cluster max. */
    val phase3CpuFreqRatioLittle: Float? = null,
    // Per-cluster usage. Requires per-CPU /proc/stat reads in the sampler;
    // legacy scans stored only global usage at v[9] and so report null here.
    /** Average phase-3 utilization on big-cluster cores (0..100). */
    val phase3CpuUsageAvgBig: Float? = null,
    /** Average phase-3 utilization on mid-cluster cores (0..100). */
    val phase3CpuUsageAvgMid: Float? = null,
    /** Average phase-3 utilization on little-cluster cores (0..100). */
    val phase3CpuUsageAvgLittle: Float? = null,
    /** True when the sampler successfully captured per-CPU /proc/stat
     *  data during the scan. False on legacy scans (pre-58) and on
     *  devices where /proc/stat per-CPU lines are SELinux-blocked.
     *  When false, [passedSaturationThreshold] falls back to freq-only. */
    val perCpuUsageAvailable: Boolean = false,
    /**
     * V2 fix #56-3 / Commit 3 (fieldsVersion 59): early-exit signal for
     * Xiaomi/HyperOS-class governor capping.
     *
     * Set TRUE when the StressTestServiceV2 watcher coroutine detects,
     * within ~10 seconds of phase 3 starting, that big-cluster freq is
     * sustained below 70% of nominal AND PowerManager.getThermalHeadroom
     * returns sentinel/near-zero (signal blocked or device cool). This
     * pattern is HyperOS-specific: the OS caps frequency by policy, not
     * thermal pressure, so headroom never registers and the stress
     * test cannot exercise the silicon.
     *
     * When TRUE, V2 service skips phases 4 (GPU_SUSTAINED), 5 (COMBINED_PEAK),
     * and 6 (BURST_PATTERN), running only phase 7 COOLDOWN to bound
     * teardown. Total scan duration drops from ~8 minutes to ~3.
     * phaseCompletionStatus marks the skipped phases as
     * "skipped_governor_capped" so the AI knows the gap is intentional.
     *
     * Kept FALSE on healthy devices and on Samsung/Google/Pixel where
     * the cap (if any) IS thermal — those devices' headroom registers
     * normally and the watcher doesn't trip.
     */
    val governorCappingDetected: Boolean = false,
    // ─────── V2 fix #56-4 / Commit 4 (fieldsVersion 60) ───────
    // Multi-axis evidence-based saturation criterion. The legacy 0.85
    // mean_freq threshold (Commits 1-3) is unachievable on healthy
    // modern Android devices because every OEM applies energy-aware
    // DVFS under sustained load. Mean freq lands at 60-70% of nominal
    // even on Snapdragon 855, Cortex-X925, etc. running properly.
    //
    // The new criterion checks 4 independent conditions:
    //   C1 — peak_power_check:           max(phase3, phase5) >= 4000 mW
    //   C2 — sustained_compute_engagement: any of paths A/B/C
    //         A: per-cluster avg(big, mid) >= 75%
    //         B: !perCpuUsageAvail AND global usage in [50, 95]
    //         C: freq dropped >= 15% from burst to sustained AND verif OK
    //   C3 — verification_integrity:     all stress-kernel verification OK
    //   C4 — peak_freq_capability:       peak big freq across ALL phases
    //                                     >= 0.85 * nominal big
    //
    // satConditionsMet = number passed (0..4).
    //   4 → "fully_saturated"        passedSaturationThreshold = true
    //   3 → "effectively_saturated"  passedSaturationThreshold = true (caveat)
    //   2 → "partial_saturation"     passedSaturationThreshold = false
    //  ≤1 → "not_saturated"          passedSaturationThreshold = false
    /** Did peak package power across phases 3 + 5 reach the 4 W envelope? */
    val satC1Passed: Boolean = false,
    /** Did the workload sustain compute engagement (any of paths A/B/C)? */
    val satC2Passed: Boolean = false,
    /** Did the stress-kernel verification pass with no defects? */
    val satC3Passed: Boolean = false,
    /** Did peak big-cluster freq (anywhere in scan) reach ≥85% of nominal? */
    val satC4Passed: Boolean = false,
    /** Number of conditions passed (0..4). Saturated when ≥3. */
    val satConditionsMet: Int = 0,
    /** "fully_saturated" / "effectively_saturated" / "partial_saturation" / "not_saturated" */
    val satGrade: String = "not_saturated",
    /** Set ONLY when satConditionsMet == 3. Identifies the single failed
     *  condition with explanation. Null when all 4 passed (no caveat needed)
     *  or when satConditionsMet ≤ 2 (use saturationFailureReason instead). */
    val satCaveat: String? = null,
    /** Diagnostic: peak package_power_mw observed during phase 3. */
    val peakPackagePowerMwPhase3: Int = 0,
    /** Diagnostic: peak package_power_mw observed during phase 5. */
    val peakPackagePowerMwPhase5: Int = 0,
    /** Diagnostic: peak cpu_freq_big_khz observed across ALL phases.
     *  Used by C4. AI can also read this directly to assess silicon
     *  burst capability independent of saturation grading. */
    val peakBigFreqKhzAnywhere: Long = 0L,
    /** Diagnostic: percentage drop from peak phase-3 first-second freq
     *  to mean freq after the first 5 seconds. Used by C2 Path C as a
     *  proxy for "did the OS observe real load and reduce freq".
     *  Always populated even when Path C wasn't decisive. */
    val freqDropFromBurstToSustainedPct: Float = 0f,
    /**
     * Phase 1.9 fix #2: stress-window-only counterpart to
     * [peakBigFreqKhzAnywhere]. Max `cpu_freq_big_khz` observed
     * across the stress phases ONLY (phases 3 CPU_SUSTAINED, 4
     * GPU_SUSTAINED, 5 COMBINED_PEAK, 6 BURST_PATTERN). Excludes
     * INIT/STORAGE/MEMORY/COOLDOWN where peak freq might come from
     * UI animation, the probe's NEON FMA mini-burst, or any other
     * non-stress activity.
     *
     * Null when no phase-3..6 sample carried a positive value.
     * Existing [peakBigFreqKhzAnywhere] kept unchanged for backward
     * compat; analysts comparing the two can spot scans where the
     * observed peak came from outside the stress window.
     */
    val peakBigFreqKhzInStressPhases: Long? = null,
    /**
     * Phase 1.9 fix #2: c4-style ratio computed against
     * [peakBigFreqKhzInStressPhases] instead of the whole-scan peak.
     * Same denominator as the existing C4 ratio (`bigNominalKhz`
     * from `SocNominalFrequencies.getPerClusterMaxKhz`), so an
     * analyst can compare:
     *   c4Ratio (whole-scan)  vs  c4RatioStressWindow (phases 3-6)
     * to detect scans where the saturation pass came from a non-
     * stress sample. Null when the new peak is null or the nominal
     * couldn't be resolved.
     */
    val c4RatioStressWindow: Float? = null
)

/**
 * Aggregate verification result across all V2 stress kernels. The
 * `criticalDefectDetected` flag is the headline — true means at least
 * one kernel produced silent data corruption (bit flip, wrong hash,
 * cell stuck-at), which the AI must surface immediately as CRITICAL
 * FAIL regardless of throughput numbers.
 *
 * fieldsVersion 68 (rule 22, S-23): a default is no longer allowed to read as
 * a pass. A check that did not run is `null` with a state that says so
 * (see [VerificationStates]); the defaults of the fields below that carry a
 * state describe "nothing ran", so a partial-V2 scan can still be persisted
 * and parsed without a fabricated success.
 */
data class VerificationSummary(
    // Phase 1.7.B: legacy NDK kernel verification fields
    // (cpuFmaIterationsTotal/Verified/Errors, cpuPrimeSieveCorrect,
    // cpuShaHashesTotal/Verified, memoryMarchPassed,
    // memoryWalkingOnesPassed, memoryRandomVerifyErrors) removed.
    // Phase 1.6 replaced the synthetic NEON FMA / SHA / prime / March-C /
    // Walking-Ones kernels with TFLite inference + image-processing
    // workloads, so those counters were always 0 / true / 0 on the AI
    // path and confused lab parsers. The canonical "scan ran" signal
    // is now [aiInferenceIterationsTotal] (Phase 1.7.C, see TimeSeriesFooter).
    // GPU
    /** fieldsVersion 68 (ت-18 row 1, N-11): `null` = the GPU compute GEMM
     *  verification did NOT run (phases 4/5 skipped or no attempt completed) —
     *  not "correct". [gpuComputeGemmState] always says which of the three. */
    val gpuComputeGemmCorrect: Boolean? = null,
    /** `passed` | `failed` | `not_run:<reason>` ([VerificationStates]). The
     *  default is "nothing ran", never a pass. */
    val gpuComputeGemmState: String = VerificationStates.notRun(VerificationStates.REASON_NO_V2_RESULT),
    val gpuFragmentHashMatches: Int = 0,
    val gpuFragmentHashTotal: Int = 0,
    // Bug fix #20: deterministic GPU benchmark via glFinish.
    // Counter-based FPS is unreliable on offscreen FBOs due to
    // OpenGL ES pipelining (tens of ms buffer between draw and
    // execute). These two values capture single-frame render
    // completion time at the start (cold) and end (warm) of
    // GPU_SUSTAINED phase. Cold value reflects raw GPU capability;
    // warm value reflects performance after sustained load. The
    // ratio (warm / cold) is a clean thermal-throttling indicator
    // for the AI to interpret, independent of FPS-counter noise.
    // Both -1 if benchmark unavailable.
    val gpuRenderCompletionMsCold: Long = -1L,
    val gpuRenderCompletionMsWarm: Long = -1L,
    // Memory — Phase 1.7.B kept memoryTestedBytes only. The
    // March-C / Walking-Ones pass flags + random-verify error count
    // were synthetic-kernel artefacts; the AI memory workload tracks
    // bytesTouched only and the AI side judges success from that.
    val memoryTestedBytes: Long = 0L,
    // fieldsVersion 68 (T-17, N-10, S-11, S-30): the memory verification
    // PRODUCER's raw output. The kernel (C++: March C- + Walking Ones + a
    // size-independent random verify, tile by tile) runs FIRST inside the
    // 60 s phase under a time cap; the AI workload takes the rest. Counts are
    // bytes and are `null` unless the battery executed — a 0 is then a measured
    // 0 (rule 22). The app derives no `…Passed` (rule 20): the server does.
    // [memoryMarchErrors] and [memoryWalkingOnesErrors] are shipped by S-30
    // ("the raw count ships, the server judges"); [memoryTestedBytes] above keeps
    // ITS meaning (bytes the AI workload touched, the bandwidth denominator) —
    // the two have different sources and are not merged.
    /** `ran` | `partial:<reason>` | `not_run:<reason>` ([VerificationStates]). The
     *  default is "nothing ran". */
    val memoryVerifyState: String = MemoryVerifyOutcome.DEFAULT.state,
    /** Bytes that received the WHOLE battery; LESS than [memoryVerifyBufferBytes] on a partial run. */
    val memoryVerifyBytes: Long? = null,
    val memoryMarchErrors: Long? = null,
    val memoryWalkingOnesErrors: Long? = null,
    val memoryRandomVerifyErrors: Long? = null,
    /** [م] Bytes the buffer was divided into tiles over — the coverage denominator. */
    val memoryVerifyBufferBytes: Long? = null,
    /** [م] The time cap this run was given, ms. */
    val memoryVerifyTimeCapMs: Long? = null,
    /** [م] Wall time the native call took, ms. */
    val memoryVerifyDurationMs: Long? = null,
    /** [م] Free memory as `sysinfo` reported it, bytes, RAW — present whenever `sysinfo` succeeded,
     *  the test having run or not (part 2-c). `null` = `sysinfo` failed. */
    val memoryVerifyFreeBytes: Long? = null,
    /** [م] The sub-windows MEMORY_STRESS was split into (kernel first, then the AI
     *  workload), same frame as v[0]. While the kernel runs, v[9] reads 0 because
     *  nothing publishes it — this is what says so. `null` = the phase did not run. */
    val memoryPhaseWindows: List<PhaseWindow>? = null,
    // Storage
    // fieldsVersion 68 (ت-18 row 3, N-09): these two were the literals `= 0` with
    // no producer — a constant that read as "storage verified, no errors". They
    // are `null` until a verification really ran, and [storageVerifyState] says
    // which. (storageSlcCacheSizeMb, the third literal 0, is deleted: the server
    // computes it from [storageThroughputSegments], T-08.)
    val storageWriteVerifyErrors: Int? = null,
    val storageReadVerifyErrors: Int? = null,
    /** `ran` | `partial:<reason>` | `not_run:<reason>` ([VerificationStates]). The
     *  default is "nothing ran", never "ran". fieldsVersion 68 (T-16): the producer
     *  reads the LAST 256 MiB of the written file back and compares it block by
     *  block with an offset-keyed pattern. [storageReadVerifyErrors] is then the
     *  count of 1 MiB blocks that differ / could not be read / were missing, present only
     *  when a comparison executed. [storageWriteVerifyErrors] is the write loop's errors
     *  (failed fsyncs + a write that threw), present WHENEVER THE WRITE RAN - whether the
     *  read-back ran or not (part 2-c) - and absent when no write ran (see
     *  [StorageWriteErrors]). */
    val storageVerifyState: String = VerificationStates.notRun(VerificationStates.REASON_NO_V2_RESULT),
    /** Bytes read back and compared; LESS than [storageVerifyBytesPlanned] on a partial run. `null` = none. */
    val storageVerifyBytesCompared: Long? = null,
    /** [م] What the read-back set out to cover (256 MiB). `null` = it never started. */
    val storageVerifyBytesPlanned: Long? = null,
    /** Wall time of the native read-back, ms. `null` = it did not return a result. */
    val storageVerifyDurationMs: Long? = null,
    /** How it read: `o_direct_sequential` | `dontneed_sequential:<why>` ([StorageVerifyMethods]). `null` = no read opened. */
    val storageVerifyMethod: String? = null,
    // Bug 6 fix: raw throughput segments collected during STORAGE_STRESS.
    // Each entry: (cumulativeBytes, mbpsForSegment). Empty if phase ended
    // early or if fewer than one segment fit in the time budget. The
    // backend's computeStorageCurveSlope consumes this list.
    val storageThroughputSegments: List<StorageThroughputSegment> = emptyList(),
    /**
     * fieldsVersion 68 (T-19, N-21): the unit of [storageThroughputSegments]'
     * `mbps` field, which carries no unit of its own — `MiB/s` (÷2^20, the A70
     * convention). A declaration, so a reader does not have to infer decimal vs
     * binary from the field name "mbps".
     */
    val storageThroughputSegmentsUnit: String = THROUGHPUT_UNIT_MIB_PER_S,
    // fieldsVersion 68 (decision D, T-20): the CPU known-answer checks — the prime
    // sieve (to 10,000,000 until fv69; to the native limit `sieveLimit` since fv70)
    // and the SHA-256 of a 1 MiB buffer, run beside the AI
    // workload in phases 3 and 5 (three checks each). The raw prime COUNT ships,
    // the server judges; there is no aggregate boolean and no `Passed` here. These
    // two names are NOT `cpuPrimeSieveCorrect` / `cpuFmaErrors`: the server reads
    // those with another meaning today. See [CpuKnownAnswerNames].
    /** `ran` | `partial:time_budget` (fv70: some run skipped) | `not_run:<reason>` — whether any check reported; says nothing about the answers. */
    val cpuKnownAnswerState: String = VerificationStates.notRun(VerificationStates.REASON_NO_V2_RESULT),
    /** One element per planned check (unreached ones included, as `not_reached`). `null` = none registered. */
    val cpuKnownAnswerRuns: List<CpuKnownAnswerRun>? = null,
    /** [م] Per CPU phase id, the sum of (tEnd - t) of its runs — a TRANSITIONAL AGGREGATE of the raw
     *  runs above ([CpuKnownAnswerNames.TOTAL_MS_BY_PHASE]). `null` = no run began. */
    val cpuKnownAnswerTotalMsByPhase: Map<Int, Long>? = null,
    // Aggregate
    /**
     * fieldsVersion 68 (ت-18 row 2, N-11, S-31): a TRANSITIONAL AGGREGATE
     * (declared in the footer's transitionalAggregates; the judgement from the
     * raw is the server's). `null` when an input did not run, with
     * [criticalDefectInputsNotRun] naming it — a missing input is not "no
     * defect". Its condition is the GPU compute GEMM result only; S-31 does not
     * widen it to the storage or memory errors.
     */
    val criticalDefectDetected: Boolean? = null,
    /** fieldsVersion 68 (N-11): the inputs of [criticalDefectDetected] that did
     *  not run; `[]` = all ran. The default is "the one input did not run". */
    val criticalDefectInputsNotRun: List<String> = listOf(VerificationStates.INPUT_GPU_COMPUTE_GEMM),
    // V2 fix #56-3 (fieldsVersion 59): per-cluster pin telemetry. Counts
    // of workers that successfully pinned to each cluster, plus per-cluster
    // failure counts. AI uses this to weight cluster-level diagnostics —
    // e.g. "little cluster pinning failed all workers" means the temperature
    // and freq data for that cluster reflects scheduler-chosen placement,
    // not stress-test intent. Captured from the LAST runCpuKernelsForDuration
    // call (CPU_SUSTAINED or COMBINED_PEAK), since pinning telemetry is
    // stable for the life of one start_cpu_stress batch.
    // fieldsVersion 68 (ت-13, N-12): the six counters are `null` until a pin
    // attempt really happened (the AI workload path never tries) — a default 0
    // read as "tried, none failed". [workersPinState] says which.
    val workersPinnedBig: Int? = null,
    val workersPinnedMid: Int? = null,
    val workersPinnedLittle: Int? = null,
    val workersPinFailedBig: Int? = null,
    val workersPinFailedMid: Int? = null,
    val workersPinFailedLittle: Int? = null,
    /** `attempted` | `not_attempted:<reason>` ([VerificationStates]); the default
     *  is "no V2 result", never "attempted". */
    val workersPinState: String = VerificationStates.notAttempted(VerificationStates.REASON_NO_V2_RESULT),
    // Phase 1.7.C — canonical "scan ran" signal for the AI workload path.
    // Lab parsers should gate on aiInferenceIterationsTotal > 0 (was
    // gating on cpuFmaIterationsTotal > 0 which always held under
    // synthetic kernels, never under the AI workload).
    /** Sum of inference iterations across phases 2 + 3 + 4 + 5. Phase 2
     *  contribution is the AI memory workload's pass count (each pass
     *  is a 4-buffer convolution); 3/4/5 are TFLite forward passes. */
    val aiInferenceIterationsTotal: Long = 0L,
    /** Worker count reported by the heaviest CPU phase (3 or 5). 0 in
     *  rollback / pure-V5 mode. */
    val aiInferenceWorkers: Int = 0,
    /** Mean inference latency across the CPU + GPU AI workloads, in
     *  milliseconds. Phase 2 contribution excluded — image-processing
     *  passes have a different cost model than TFLite forward passes. */
    val aiInferenceAvgMs: Float = 0f,
    /** Most-frequent active TFLite delegate. One of "NNAPI" | "GPU" |
     *  "CPU" | "STUB" | "NONE". "STUB" surfaces when the TFLite native
     *  lib is missing for this ABI; iterations may still be > 0 but
     *  no real inference happened — lab parsers should treat that as
     *  "scan ran but AI path failed" and weight the result accordingly. */
    val aiActiveDelegate: String = "NONE",
    /** Phase 1.15 (Fix 4): when [aiActiveDelegate] == "STUB", the exact
     *  TFLite interpreter-construction failure captured by
     *  `InferenceEngineImpl.getDelegateInitError()` — e.g.
     *  "UnsatisfiedLinkError: ...". Lets the backend / lab learn WHY the
     *  delegate fell to STUB on a field device without a manual logcat
     *  capture. Null when a real delegate served inference (or in V1
     *  mode where the AI path doesn't run). */
    val aiDelegateInitError: String? = null,
    /** Phase 1.15 (Fix 1): true when the AI CPU workload fell to STUB and
     *  the synthetic NEON kernels were run as a fallback so the CPU was
     *  actually loaded. When true, [aiActiveDelegate] is "STUB" yet the
     *  workersPinned* counters reflect the NEON fallback — so the scan's
     *  CPU phase carries real load and the saturation verdict is valid. */
    val cpuStressFallbackUsed: Boolean = false,
    // ─────── fieldsVersion 66 (additive) ───────
    /**
     * Number of 30-frame GPU fence waits that timed out (1 s cap) or
     * failed across the GPU phases (4 + 5). The frame counter behind
     * v[10] counts ISSUED draws; a timed-out fence means the loop kept
     * issuing while the GPU was behind, so v[10] in that phase may
     * overstate real throughput. 0 = every batch completed inside its
     * wait — v[10] is trustworthy. -1 never appears (pre-66 footers
     * simply lack the field).
     */
    val gpuFenceTimeoutCount: Int = 0,
    /**
     * Total bytes the STORAGE_STRESS write loop actually wrote before it
     * stopped (byte cap = min(4 GiB, 80 % of usable space) or the phase
     * deadline). -1 = not measured (pre-66 / phase skipped).
     */
    val storageBytesWritten: Long = -1L,
    /**
     * Wall-clock milliseconds the STORAGE_STRESS write loop was active.
     * The phase always runs its nominal 75 s, but the WORKLOAD stops at
     * the byte cap and the sampler's v[8] reads 0 for the remainder — so
     * "v[8] = 0 after the last non-zero value" means the write finished
     * (Honor MTN-NX1: 4 GiB in ~5.4 s at 792 MB/s), not that storage
     * collapsed. -1 = not measured. The byte cap itself is deliberately
     * unchanged (flash-wear decision, 2026-09-14).
     */
    val storageWriteDurationMs: Long = -1L
)

/**
 * G2/P2.3: one phase's observed start/end, timestamped in the SAME
 * reference frame as v[0] (`SystemClock.elapsedRealtime() - scanStartTime`
 * — see DisciplinedSampler.buildSample's values[0]/values[10] handling and
 * StressTestServiceV2's own scanStartTime constructor param), so a
 * consumer can slice the raw v[] time series to an exact phase window by
 * timestamp instead of trusting only the per-sample `phase` tag.
 *
 * Motivation: the per-sample `phase` tag is written by
 * DisciplinedSampler.currentPhase, set from a separate coroutine
 * (StressTestService's phaseDriver) polling the throttler every 500ms —
 * see the A46 fix (currentPhase init race) for one way that tag could
 * already be wrong for a sample or two around a transition. phaseBoundaries
 * is recorded directly inside StressTestServiceV2.runPhase(), at the
 * actual start/end of each phase body, independent of that polling loop —
 * an authoritative cross-check, not a replacement for the per-sample tag.
 *
 * Used by: the removal of G-03/G-04/G-05 on-device aggregation (this same
 * G2/P2.3 change) — those three GPU tests used to be computed on-device
 * from SamplerStats' running max/min/initial FPS. That is peak-picking +
 * a derived ratio, an irreversible aggregation forbidden in the app per
 * the raw-in/transform-on-server rule; the app now only ships the raw
 * v[10] gpu_fps series. A consumer that wants to recompute G-03/G-04/G-05
 * needs to know exactly which samples fall inside GPU_SUSTAINED (phaseId
 * 4) and COMBINED_PEAK (phaseId 5) — this list gives that window by
 * timestamp instead of requiring a per-sample phase-tag scan.
 */
data class PhaseBoundary(
    val phaseId: Int,
    val phaseName: String,
    /** Elapsed ms since scan start when this phase's body began running. */
    val startMs: Long,
    /** Elapsed ms since scan start when this phase's body returned
     *  (completed, early-terminated, or threw) — NOT the nominal
     *  duration; see [V2Phase.durationMs] for that. */
    val endMs: Long,
    /**
     * fieldsVersion 68 (S-30, batch 3 part 2-b): the named sub-windows a phase
     * split itself into — MEMORY_STRESS declares its kernel window and the AI
     * workload's. `null` = the phase declares none (every other phase, and every
     * file written before this field). Kept INSIDE the phase's own entry so the
     * list stays one entry per phase: a reader that looks a phase up by
     * [phaseId] still finds exactly one.
     */
    val windows: List<PhaseWindow>? = null
)

/**
 * V2 phase enum — 8 phases totalling 480 seconds (8 minutes), per
 * master plan §2.2. Order is significant (storage + memory FIRST,
 * while the device is cold; combined peak + burst LAST after
 * sustained heat soak).
 */
enum class V2Phase(
    val phaseId: Int,
    val phaseName: String,
    val durationMs: Long,
    val samplingHz: Int
) {
    INIT_PROFILING(0, "INIT_PROFILING", 30_000L, 2),
    STORAGE_STRESS(1, "STORAGE_STRESS", 75_000L, 1),
    MEMORY_STRESS(2, "MEMORY_STRESS", 60_000L, 1),
    CPU_SUSTAINED(3, "CPU_SUSTAINED", 75_000L, 4),
    GPU_SUSTAINED(4, "GPU_SUSTAINED", 75_000L, 4),
    COMBINED_PEAK(5, "COMBINED_PEAK", 60_000L, 4),
    BURST_PATTERN(6, "BURST_PATTERN", 45_000L, 10),
    COOLDOWN(7, "COOLDOWN", 60_000L, 2);

    companion object {
        const val TOTAL_DURATION_MS: Long = 480_000L  // 8 minutes exact
        fun fromId(id: Int): V2Phase? = values().firstOrNull { it.phaseId == id }
    }
}

/**
 * Outcome of [AdaptiveIntensityController]'s thermal probe. The
 * intensity factor multiplies stress kernel iterations downstream:
 * flagship → 1.0, mid → 0.8, budget/compromised → 0.65. Per master
 * plan §2.6.
 */
enum class DeviceTier(val intensityFactor: Float, val tag: String) {
    FLAGSHIP_COOLING(1.0f, "flagship_cooling"),
    MID_COOLING(0.8f, "mid_cooling"),
    BUDGET_COOLING(0.65f, "budget_cooling"),
    UNKNOWN(0.8f, "unknown")
}

/**
 * V5 schema (2026): universal channels only.
 * Removed v4 channels: cpu_temp_c, gpu_temp_c, accel/gyro, skin_temp_c,
 * separate cpu_freq_big/little/mid (consolidated into cpu_freq_max_khz).
 * Blocked-by-OS channels moved to footer.thermalSensorAvailability.
 * Schema break: v4 sample files are NOT compatible.
 *
 * The contract is now "every channel here is universally available
 * across mobile vendors". Per-device extras (thermal zones blocked by
 * SELinux, vendor BMS quirks, accelerometer telemetry) live in the
 * footer where the AI can read availability flags instead of guessing
 * from sentinel values in the time-series.
 */
/**
 * V5 schema (Task D): channel-discovery report run once at scan start to
 * record which signal sources actually responded on this device. The
 * footer carries this so the AI can interpret missing channels as
 * "device-blocked" vs "we never tried".
 *
 * Each channel name is attempted exactly once during the probe; the result
 * is bucketed into one of three lists. `summary` is a single-line human-
 * readable digest for log output / report screens.
 */
data class ChannelDiscoveryReport(
    val attempted: List<String>,
    val available: List<String>,
    val blockedBySelinux: List<String>,
    val missing: List<String>,
    val summary: String,
    /**
     * fieldsVersion 68 (N-07, S-02, S-35): for every attempted channel that is
     * NOT in [available], the TEXT that explains it — the error message, or a
     * statement of what the source returned (rule 21: text, not exception
     * class), at most 200 characters (ReasonText). The app declares the
     * reason; the server names the state (rule 20). [blockedBySelinux] and
     * [missing] stay as the legacy two-way lists (the name
     * "blockedBySelinux" attributes the refusal to SELinux before the cause is
     * known; it is kept for compatibility, and the cause is in this map).
     * null = a file written before fieldsVersion 68 (no reasons recorded);
     * an empty map = every attempted channel was available.
     */
    val reasons: Map<String, String>? = null
)

/**
 * fieldsVersion 68 (N-06, S-06): one cpufreq policy of the device, as the
 * sysfs files state it — raw, no classification into little / mid / big (that
 * is an inference and belongs to the server, rule 20).
 *
 * @property policy the N of `/sys/devices/system/cpu/cpufreq/policy{N}`, and of
 *   the channel `cpu_freq_policy{N}_khz`.
 * @property relatedCpus the text of `related_cpus` verbatim ("0 1 2 3"); null
 *   when it could not be read (then [unreadableReason] says why).
 * @property cpuinfoMaxFreqKhz `cpuinfo_max_freq` in kHz; null when it could
 *   not be read (then [unreadableReason] says why).
 * @property unreadableReason the failure text(s) of the file(s) above that
 *   could not be read, joined with " | " (each message names its file), at
 *   most 200 characters; null = both were read. The footer file writes null
 *   explicitly; the request payload omits a null key, so a missing
 *   `cpuinfoMaxFreqKhz` there is always accompanied by this field.
 */
data class CpuPolicyInfo(
    val policy: Int,
    val relatedCpus: String?,
    val cpuinfoMaxFreqKhz: Long?,
    val unreadableReason: String?
)

/**
 * V5 schema (Task E): aggregate accelerometer-stillness report for one
 * scan. Computed by [net.neuralcheck.service.sampler.SamplerStats] from
 * the magnitude of every accel reading across the run. The boolean
 * threshold matches the spec — std-dev > 0.5 m/s² flags the device as
 * having been moved during the scan, which AI consumers should weigh
 * against any motion-correlated metric (battery_current spikes, fps
 * jitter, etc.).
 */
data class StillnessReport(
    /** `null` = the stillness could not be judged ([stillnessState] says why). */
    val isStill: Boolean?,
    /** `null` = fewer than 2 accelerometer readings, the variance is undefined. */
    val accelStdDev: Float?,
    /** Accelerometer-magnitude readings the statistic was computed from. */
    val sampleCount: Int,
    /** fieldsVersion 68 (N-13): `measured` | `insufficient_accel_samples`
     *  ([StillnessStates]). Always present; the two nullable fields above are
     *  absent exactly when this is not `measured`. */
    val stillnessState: String
)

/**
 * fieldsVersion 71 (A-P6-13, N71-07): the battery window the build applied to the scan's start — `{minPct, maxPct}`, the one object
 * `ScanBatteryWindow` — so the server holds no copy of the two numbers (rule 24). null = a file written before fieldsVersion 71 (stamp < 71).
 */
data class BatteryWindowPctFooter(val minPct: Int, val maxPct: Int)

/**
 * fieldsVersion 71 (A-P6-13, N71-08): what came of holding the screen's brightness during the scan — `BrightnessLock`: [state] `applied` | `apply_failed:<message>` |
 * `not_attempted:<reason>`; [value] (a fraction in [0, 1], the brightness REQUESTED) and [method] (`window_attribute` | `system_setting`) are null — ABSENT keys — when
 * nothing was attempted. null = a file written before fieldsVersion 71 (stamp < 71).
 */
data class BrightnessLockFooter(val state: String, val value: Float?, val method: String?)

/**
 * fieldsVersion 68 (ت-18 row 7, N-14): the state of reading the accepted pre-scan
 * warnings at the end of a scan — `read` | `read_failed:<message>` (the message
 * text, rule 21, capped at 200 characters by ReasonText).
 */
object PreScanWarningsStates {
    const val READ = "read"
    /**
     * fieldsVersion 68 (the user's third state, 2026-10-08): the holder was drained and
     * nobody had written to it — the pre-scan check did not put anything there (the scan
     * started by a route that does not go through PreScanScreen, or the holder was already
     * drained). NOT the same as `read` with `[]`, which is a check that ran and found nothing.
     */
    const val NOT_WRITTEN = "not_written"
    const val READ_FAILED_PREFIX = "read_failed:"

    fun of(read: Result<PreScanWarningsRead>): String =
        read.fold(
            onSuccess = { if (it.written) READ else NOT_WRITTEN },
            onFailure = { net.neuralcheck.util.ReasonText.cap(READ_FAILED_PREFIX + net.neuralcheck.util.ReasonText.of(it)) }
        )
}

/**
 * What draining `PreScanWarningsHolder` returns: the warnings and whether anything was
 * written at all. [warnings] is meaningful only when [written]; when it is not, the footer
 * writes no list (null), exactly as for a failed read.
 */
data class PreScanWarningsRead(val written: Boolean, val warnings: List<String>)

/** fieldsVersion 68 (N-13): the state vocabulary of [StillnessReport.stillnessState]. */
object StillnessStates {
    const val MEASURED = "measured"
    const val INSUFFICIENT_ACCEL_SAMPLES = "insufficient_accel_samples"
}

/**
 * V5 schema (Task F): per-thermal-channel availability flag, derived
 * from [ChannelDiscoveryReport] but surfaced as a small dedicated
 * footer field for AI consumers that only care about the thermal
 * dimension. Values per channel: `"available"` | `"blocked_by_selinux"`
 * | `"missing"`. `source` is the upstream signal that produced this
 * report — currently always `"discovery"`.
 */
data class ThermalSensorAvailability(
    val cpuTemp: String,
    val gpuTemp: String,
    val skinTemp: String,
    val source: String
) {
    companion object {
        /** Default for null discovery reports — every channel "missing". */
        val UNKNOWN = ThermalSensorAvailability(
            cpuTemp = "missing",
            gpuTemp = "missing",
            skinTemp = "missing",
            source = "discovery"
        )

        /**
         * Project a [ChannelDiscoveryReport] into the thermal-only view.
         * Looks up each thermal channel by name in the three lists and
         * tags accordingly. Channels not in any list (defensive — should
         * never happen) tag as "missing".
         */
        fun fromDiscovery(report: ChannelDiscoveryReport): ThermalSensorAvailability {
            return ThermalSensorAvailability(
                cpuTemp = classify(report, "cpu_temp_c"),
                gpuTemp = classify(report, "gpu_temp_c"),
                skinTemp = classify(report, "skin_temp_c_real"),
                source = "discovery"
            )
        }

        private fun classify(report: ChannelDiscoveryReport, channel: String): String = when (channel) {
            in report.available -> "available"
            in report.blockedBySelinux -> "blocked_by_selinux"
            else -> "missing"
        }
    }
}

/**
 * fieldsVersion 68 (T-19, S-21, N-21): the unit of the two binary throughput
 * channels (v[8], v[9]) and of verificationResults.storageThroughputSegments[].mbps
 * — mebibytes per second, ÷2^20. One literal (rule 24).
 */
const val THROUGHPUT_UNIT_MIB_PER_S = "MiB/s"

/**
 * The BASE layout of the V2 sample, `v[0..10]`. Frozen by contract
 * (docs/FV68_CONTRACT.md §٢.١): from fieldsVersion 68 no index of this list
 * moves and none changes meaning; per-device channels (charge_counter_raw,
 * cpu_freq_policy{N}_khz) are APPENDED after it, per scan, and the list a
 * given scan actually wrote is the `variables[]` of its own file
 * (see ScanChannelLayout, added with the per-scan layout). Readers resolve a column by NAME
 * ([ChannelNames]); the fixed indices ≤ 10 are read only by code that runs
 * on samples this same build produced, and V2BaseLayoutTest pins every
 * index to its name.
 */
val V2_VARIABLE_DEFINITIONS: List<VariableDefinition> = listOf(
    // fieldsVersion 68 (T-12, S-21): the declaration matches the value. v[0] is
    // NOT the absolute elapsedRealtime the old text named: DisciplinedSampler
    // writes `elapsedRealtime() - startTimeMs`, milliseconds since the scan
    // started (the frame every `t` of this fieldsVersion — thermal lists, battery
    // pairs, phaseBoundaries — shares).
    VariableDefinition(0, ChannelNames.TIMESTAMP_MS, "ms", "SystemClock.elapsedRealtime() minus scan start (ms since scan start)"),
    VariableDefinition(1, ChannelNames.BATTERY_TEMP_C, "°C", "BatteryManager EXTRA_TEMPERATURE / 10"),
    VariableDefinition(2, ChannelNames.BATTERY_VOLTAGE_MV, "mV", "EXTRA_VOLTAGE"),
    VariableDefinition(3, ChannelNames.BATTERY_CURRENT_UA, "µA", "BATTERY_PROPERTY_CURRENT_NOW (vendor-cadence variation)"),
    VariableDefinition(4, ChannelNames.BATTERY_LEVEL_PCT, "%", "EXTRA_LEVEL / EXTRA_SCALE × 100"),
    VariableDefinition(5, ChannelNames.CPU_FREQ_MAX_KHZ, "kHz", "max(scaling_cur_freq) across clusters"),
    VariableDefinition(6, ChannelNames.CPU_USAGE_PCT, "%", "/proc/stat (with process-fallback when blocked)"),
    VariableDefinition(7, ChannelNames.PACKAGE_POWER_MW, "mW", "|voltage_mv × current_µA| / 1e6"),
    // A70: v[8] and v[9] were both labeled "MB/s" but computed with DIFFERENT
    // divisors — v[8] decimal (÷10^6), v[9] binary (÷2^20), a factor of ~1.048576
    // apart (docs/MEASUREMENT_SPEC.md §3.1 note 1). A70 (commit 91d5394) switched
    // v[8]'s computation (StressTestServiceV2.kt, storage-stress segment loop) to
    // the binary ÷2^20 convention of v[9] and of S-03/S-04 (StorageTests.kt).
    // fieldsVersion 68 (T-19, S-21): both are now DECLARED "MiB/s", the unit they
    // are — and that declaration is also the marker that tells a reader which
    // convention it is looking at: the old text "MB/s" was written both by builds
    // that computed decimal and by builds that computed binary, under the same
    // stamp 67 and the same versionName 1.0.8-v6beta (docs/FV68_CONTRACT.md T-19,
    // S-36). A <= 67 file still says "MB/s" and is read by header.appVersion.
    VariableDefinition(8, ChannelNames.STORAGE_THROUGHPUT_MBPS, THROUGHPUT_UNIT_MIB_PER_S, "instant I/O rate during STORAGE_STRESS"),
    // fieldsVersion 66: v[9] is a LIVE series (rolling MiB/s published ~1 Hz
    // from the memory workload), no longer a single end-of-phase aggregate
    // that in practice never reached the file. ≤65 files carry 0 here.
    VariableDefinition(9, ChannelNames.MEMORY_BANDWIDTH_MBPS, THROUGHPUT_UNIT_MIB_PER_S, "live rolling MiB/s (~1 Hz) during MEMORY_STRESS; 0 outside"),
    // fieldsVersion 66: v[10] is a ≥1 s sliding-window rate (last 4 sampler
    // queries), removing the 30-frame fence aliasing that produced
    // 0/120/0/120 patterns at ~60 fps on ≤65 builds. Not directly
    // comparable with ≤65 v[10] on GPUs in the 60–120 fps band.
    // fieldsVersion 68 (T-12, S-21): the window is also cleared at the END of
    // phase 5 (GpuFpsTracker.reset() after the GPU load stops), so the tail of
    // non-zero values after the load stopped is gone; the declaration says what
    // the value is — a rate over the last 4 sampler queries, not "1 s" at every rate.
    VariableDefinition(
        10, ChannelNames.GPU_FPS, "fps",
        "OpenGL offscreen frame counter; rate over the last 4 sampler queries (about 1 s at 4 Hz); " +
            "window cleared at the start of phases 4 and 5 and at the end of phase 5; 0 outside phases 4 and 5"
    )
)


/**
 * Width of the V2 BASE layout ([V2_VARIABLE_DEFINITIONS], `v[0..10]`). It is
 * NO LONGER the width of a V2 sample: from fieldsVersion 68 a sample is this
 * base plus one entry per per-device channel that passed discovery, so its
 * width differs between devices (ScanChannelLayout.sampleWidth). Use this
 * constant only for "the base block".
 */
const val V2_SAMPLE_VAR_COUNT: Int = 11
