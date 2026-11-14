package net.neuralcheck.util

import android.os.Build
import android.util.Log
import net.neuralcheck.data.local.files.StressEffectiveness
import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.VerificationSummary
import net.neuralcheck.service.sampler.SamplerStats
import java.util.Locale

/**
 * V2 fix #54 — computes [StressEffectiveness] from a scan's raw samples.
 *
 * V2 fix #56-4 / Commit 4 (fieldsVersion 60, 2026-05-05): MULTI-AXIS
 * EVIDENCE-BASED SATURATION CRITERION.
 *
 * ── Why this rewrite ──
 *
 * The legacy single-metric criterion (phase3 mean freq ratio ≥ 0.85) is
 * unachievable on healthy modern Android devices. Every OEM applies
 * energy-aware DVFS under sustained load — Cortex-Prime cores burst to
 * peak only briefly, and mean freq lands at 60-70% of nominal even on
 * silicon running properly. This was the root cause of the V3 Samsung
 * scan reporting `passedSaturationThreshold = false` despite peak
 * power 10.4W, sustained usage 76.7%, and zero verification errors.
 *
 * Literature: AnandTech smartphone power measurements, Microsoft Research
 * mobile workload methodology, Pete Warden's mobile-ML thermal analysis
 * — all converge on 4-5W as the smartphone slab thermal envelope, with
 * mean freq under sustained load consistently below "burst peak" by
 * design. A criterion that demands 85% sustained freq is not measuring
 * silicon health; it's measuring "did the OS choose not to apply DVFS",
 * which is uncorrelated with device defects.
 *
 * ── The multi-axis criterion ──
 *
 *   C1 — peak_power_check
 *     max(phase3.peak_power, phase5.peak_power) ≥ 4000 mW
 *     Devices that physically reach 4 W have been stressed; below 4 W
 *     means the workload didn't engage the silicon (or it's a sub-
 *     flagship — flag separately, do not auto-fail).
 *
 *   C2 — sustained_compute_engagement (any of three OR-paths)
 *     A: per-cluster usage available AND avg(big, mid) ≥ 75%
 *     B: !per-cluster AND global usage in [50, 95] (workload on
 *        4 of 8 cores caps near 50%; >95% smells like sensor error)
 *     C: freq dropped ≥15% from burst (first sec) to sustained
 *        (after first 5 s) AND verification all passed —
 *        proves OS observed real load and reduced freq to compensate
 *
 *   C3 — verification_integrity
 *     All NDK kernel results clean: cpuFmaErrors == 0,
 *     memoryRandomVerifyErrors == 0, storage*VerifyErrors == 0,
 *     cpuPrimeSieveCorrect, gpuComputeGemmCorrect.
 *     A device producing correct results under stress IS healthy
 *     regardless of frequency — this is the most direct integrity
 *     signal in the entire scan.
 *
 *   C4 — peak_freq_capability
 *     peak cpu_freq_big_khz across ALL phases ≥ 0.85 × nominal big.
 *     If silicon hits 85% nominal in ANY burst, the chip is capable;
 *     not sustaining peak is OS policy, not silicon defect.
 *
 * ── Grading ──
 *
 *   satConditionsMet | satGrade               | passedSaturationThreshold
 *   ─────────────────┼────────────────────────┼──────────────────────────
 *           4        | "fully_saturated"      | true
 *           3        | "effectively_saturated"| true (with satCaveat set)
 *           2        | "partial_saturation"   | false
 *          ≤1        | "not_saturated"        | false
 *
 * Sample variable indices (V5 schema, see V2_VARIABLE_DEFINITIONS):
 *   v[5]  = cpu_freq_max_khz (max across clusters; replaces V4 big/little/mid)
 *   v[6]  = cpu_usage_pct (GLOBAL — kept for Path B fallback)
 *   v[7]  = package_power_mw
 *
 * V4→V5 schema break: per-cluster freqs (big/little/mid) are no longer in v[].
 * The per-cluster ratio fields exposed in [StressEffectiveness] now derive from
 * the single max-freq channel — bigRatio reflects observed peak; midRatio /
 * littleRatio collapse to null and the AI consumer should treat them as
 * "data unavailable in V5" rather than "device idle".
 */
object StressEffectivenessCalculator {
    private const val TAG = "NeuralCheck.StressEff"

    // ── C1 thresholds ──
    private const val C1_PEAK_POWER_MW_THRESHOLD = 4000

    // ── C2 thresholds ──
    private const val C2_PATH_A_USAGE_THRESHOLD_PCT = 75f
    private const val C2_PATH_B_USAGE_LOWER_PCT = 50f
    private const val C2_PATH_B_USAGE_UPPER_PCT = 95f
    private const val C2_PATH_C_FREQ_DROP_THRESHOLD_PCT = 15f
    // Phase 1.15 (Fix 3b): a burst→sustained freq drop is DVFS-under-load
    // evidence ONLY if the CPU was actually busy in the sustained window.
    // 30% global usage sits well above any idle baseline (~10%) yet well
    // below a real load (4-of-8-core ≈ 50%, 7-worker AI ≈ 87%) — so it kills
    // the Honor X9d idle false-positive without risking a false-negative on
    // a genuinely loaded device.
    private const val C2_PATH_C_MIN_SUSTAINED_USAGE_PCT = 30f

    // ── C4 threshold ──
    private const val C4_PEAK_FREQ_RATIO_THRESHOLD = 0.85f

    // ── Path C burst window ──
    // V2 phase 3 (CPU_SUSTAINED) samples at 4 Hz; 1 s = 4 samples,
    // 5 s = 20 samples. We use timestamp-based windowing (v[0] is
    // scan-relative ms) so the calculation is robust to sampling-rate
    // changes in future builds.
    private const val PATH_C_BURST_WINDOW_MS = 1_000f
    private const val PATH_C_SUSTAINED_AFTER_MS = 5_000f

    /** Sample variable indices — keep in sync with V2_VARIABLE_DEFINITIONS.
     *  V5 schema: per-cluster freq channels removed; everything reads from
     *  v[5] cpu_freq_max_khz. The legacy IDX_CPU_FREQ_BIG name now points at
     *  max-freq (semantically the closest match — under stress the big
     *  cluster dominates the max). LITTLE / MID indices are set to -1 so
     *  v.getOrNull(IDX) returns null and per-cluster ratios cleanly
     *  degrade to "unavailable" rather than reading the wrong channel. */
    private const val IDX_TIMESTAMP_MS = 0
    private const val IDX_CPU_FREQ_BIG = 5         // V5: cpu_freq_max_khz
    private const val IDX_CPU_FREQ_LITTLE = -1     // V5: not in v[]
    private const val IDX_CPU_FREQ_MID = -1        // V5: not in v[]
    private const val IDX_CPU_USAGE = 6
    private const val IDX_PKG_POWER = 7

    /**
     * Primary entry point.
     *
     * @param samples            raw time-series samples for this scan
     * @param buildHardware      Build.HARDWARE for SoC nominal lookup fallback
     * @param stats              SamplerStats snapshot (per-cluster usage + topo)
     * @param verification       VerificationSummary from V2 service. null in V1
     *                           mode → C3 defaults to TRUE (no defect evidence).
     */
    fun compute(
        samples: List<TimeSeriesSample>,
        buildHardware: String = Build.HARDWARE.orEmpty(),
        stats: SamplerStats.Snapshot? = null,
        verification: VerificationSummary? = null,
        /**
         * fieldsVersion 67: the watcher's governor-capping observation,
         * passed IN rather than copy()'d on afterwards, so the C4 caveat
         * text can be conditioned on what we actually measured instead of
         * asserting a vendor policy we never observed. Default false keeps
         * every existing caller's behaviour.
         */
        governorCappingDetected: Boolean = false
    ): StressEffectiveness {
        val phase3 = samples.filter { it.phase == 3 }
        val phase5 = samples.filter { it.phase == 5 }

        if (phase3.isEmpty()) {
            Log.i(TAG, "compute: no phase 3 samples — cannot assess stress effectiveness")
            @Suppress("DEPRECATION")
            return StressEffectiveness(
                phase3CpuFreqRatio = 0f,
                phase3CpuUsageAvg = 0f,
                phase5PowerPeakMw = 0,
                passedSaturationThreshold = false,
                saturationFailureReason = "no_phase3_data",
                perCpuUsageAvailable = false,
                satGrade = "not_saturated"
            )
        }

        // ── Diagnostic fields (always reported, regardless of saturation outcome) ──

        val peakPowerPhase3 = peakPowerOf(phase3)
        val peakPowerPhase5 = peakPowerOf(phase5)
        val peakBigFreqKhzAnywhere = samples
            .mapNotNull { it.v.getOrNull(IDX_CPU_FREQ_BIG)?.toLong()?.takeIf { f -> f > 0L } }
            .maxOrNull() ?: 0L
        // Phase 1.9 fix #2: a tighter peak that only counts samples from
        // the stress phases (3=CPU_SUSTAINED, 4=GPU_SUSTAINED,
        // 5=COMBINED_PEAK, 6=BURST_PATTERN). Useful for distinguishing
        // "scan reached this freq while stressing" from "scan reached
        // this freq during INIT probe / UI animation / cooldown idle".
        // Null surfaces "no positive freq in the stress window" rather
        // than collapsing to 0L (existing field already does that).
        val peakBigFreqKhzInStressPhases: Long? = samples
            .asSequence()
            .filter { it.phase in 3..6 }
            .mapNotNull { it.v.getOrNull(IDX_CPU_FREQ_BIG)?.toLong()?.takeIf { f -> f > 0L } }
            .maxOrNull()
        val freqDropPct = computeFreqDropFromBurstToSustained(phase3)

        // ── Per-cluster freq ratios + usage (Commit 1 logic, retained) ──

        val (bigNominalKhz, midNominalKhz, littleNominalKhz) =
            SocNominalFrequencies.getPerClusterMaxKhz(stats?.perCpuMaxFreqKhz)
                .let { Triple(it.first, it.second, it.third) }
                .takeIf { it.first != null || it.second != null || it.third != null }
                ?: deriveNominalsFromBuildHardware(buildHardware)

        val bigMeanKhz = phase3MeanFreqAt(phase3, IDX_CPU_FREQ_BIG)
        val midMeanKhz = phase3MeanFreqAt(phase3, IDX_CPU_FREQ_MID)
        val littleMeanKhz = phase3MeanFreqAt(phase3, IDX_CPU_FREQ_LITTLE)

        val bigRatio = ratio(bigMeanKhz, bigNominalKhz)
        val midRatio = ratio(midMeanKhz, midNominalKhz)
        val littleRatio = ratio(littleMeanKhz, littleNominalKhz)

        val perCpuUsageAvail = stats?.phase3PerCpuUsageAvg != null &&
                stats.perCpuMaxFreqKhz != null
        val (bigUsage, midUsage, littleUsage) = if (perCpuUsageAvail) {
            computePerClusterUsage(
                stats!!.phase3PerCpuUsageAvg!!,
                stats.perCpuMaxFreqKhz!!
            )
        } else {
            Triple(null, null, null)
        }

        val globalUsages = phase3.mapNotNull {
            it.v.getOrNull(IDX_CPU_USAGE)?.takeIf { v -> v >= 0f }
        }
        val globalUsageAvg = if (globalUsages.isNotEmpty()) {
            globalUsages.average().toFloat()
        } else 0f

        // ── Multi-axis condition evaluation (C1..C4) ──

        // C3: verification integrity. Computed first because C2 Path C
        // depends on it. null verification (V1 mode or rare V2 service
        // failure) → no evidence of defects → treat as PASS.
        // fieldsVersion 68: a check that did not run is now null, not a default
        // pass; C3 keeps its meaning "no POSITIVE evidence of a defect" (so a
        // not-run check neither fails nor vouches — the grade of a scan whose
        // GEMM did not run is unchanged), and the raw state is in the footer.
        // Phase 1.7.B: legacy NDK kernel checks (cpuFmaErrors,
        // memoryRandomVerifyErrors, cpuPrimeSieveCorrect) removed —
        // those fields no longer exist on the AI path. C3 now relies on
        // the GPU compute GEMM correctness flag plus the storage
        // verify error counts (storage phase still runs the original
        // sequential write).
        val verificationAllPassed = verification == null || (
            (verification.storageWriteVerifyErrors ?: 0) == 0 &&
                (verification.storageReadVerifyErrors ?: 0) == 0 &&
                verification.gpuComputeGemmCorrect != false
            )
        val c3Passed = verificationAllPassed

        // C1: peak power check.
        val maxPeakPower = maxOf(peakPowerPhase3, peakPowerPhase5)
        val c1Passed = maxPeakPower >= C1_PEAK_POWER_MW_THRESHOLD

        // C2: sustained compute engagement.
        val pathA: Boolean = perCpuUsageAvail &&
            bigUsage != null && midUsage != null &&
            ((bigUsage + midUsage) / 2f) >= C2_PATH_A_USAGE_THRESHOLD_PCT
        val pathB: Boolean = !perCpuUsageAvail &&
            globalUsageAvg >= C2_PATH_B_USAGE_LOWER_PCT &&
            globalUsageAvg <= C2_PATH_B_USAGE_UPPER_PCT
        // Phase 1.15 (Fix 3b): require a sustained-usage floor so an IDLE
        // device that briefly blips to peak then returns to idle (a large
        // "drop" the OS never caused by load) can't satisfy Path C. The
        // Honor X9d scored Path C with 10% usage; this floor closes it.
        val pathC: Boolean = freqDropPct >= C2_PATH_C_FREQ_DROP_THRESHOLD_PCT &&
            verificationAllPassed &&
            globalUsageAvg >= C2_PATH_C_MIN_SUSTAINED_USAGE_PCT
        val c2Passed = pathA || pathB || pathC

        // C4: peak freq capability. The whole-scan ratio is kept as a
        // diagnostic, but grading now uses the stress-window-only ratio
        // (phases 3..6) — see c4RatioForGrade below.
        val c4Ratio: Float? = if (bigNominalKhz != null && bigNominalKhz > 0 && peakBigFreqKhzAnywhere > 0L) {
            peakBigFreqKhzAnywhere.toFloat() / bigNominalKhz.toFloat()
        } else null

        // Phase 1.9 fix #2: stress-window-only counterpart to c4Ratio.
        // Uses the same denominator (bigNominalKhz from sysfs), so the
        // two ratios are directly comparable — analysts can spot scans
        // where a peak came from outside the stress window.
        val c4RatioStressWindow: Float? =
            if (bigNominalKhz != null && bigNominalKhz > 0 &&
                peakBigFreqKhzInStressPhases != null && peakBigFreqKhzInStressPhases > 0L
            ) {
                peakBigFreqKhzInStressPhases.toFloat() / bigNominalKhz.toFloat()
            } else null

        // Phase 1.15 (Fix 3c): grade C4 on the stress-window ratio so a
        // momentary peak during INIT / cooldown / UI animation can't satisfy
        // "capability" — the Honor X9d touched 2.3 GHz once (outside the
        // stress load) and passed C4 on it. Fall back to the whole-scan
        // ratio ONLY when the stress window had no positive freq reading
        // (avoids a false-negative when sysfs freq was readable only outside
        // phases 3..6).
        val c4RatioForGrade: Float? = c4RatioStressWindow ?: c4Ratio
        val c4Passed = c4RatioForGrade != null && c4RatioForGrade >= C4_PEAK_FREQ_RATIO_THRESHOLD

        // ── Grading ──

        val conditionsMet = listOf(c1Passed, c2Passed, c3Passed, c4Passed).count { it }
        var saturated = conditionsMet >= 3
        var grade = when (conditionsMet) {
            4 -> "fully_saturated"
            3 -> "effectively_saturated"
            2 -> "partial_saturation"
            else -> "not_saturated"
        }

        var (satCaveat, satFailureReason) = composeSaturationReasons(
            conditionsMet = conditionsMet,
            c1Passed = c1Passed,
            c2Passed = c2Passed,
            c3Passed = c3Passed,
            c4Passed = c4Passed,
            maxPeakPower = maxPeakPower,
            pathA = pathA,
            pathB = pathB,
            pathC = pathC,
            freqDropPct = freqDropPct,
            verification = verification,
            c4Ratio = c4RatioForGrade,
            governorCappingDetected = governorCappingDetected
        )

        // Phase 1.15 (Fix 3a): "was load actually applied?" gate. The
        // multi-axis criterion infers load from second-order shadows (a
        // power spike, a freq drop, clean verification, a peak-freq touch)
        // — all of which an IDLE device can cast. If our own harness never
        // loaded the CPU (TFLite fell to STUB AND the NEON fallback never
        // ran), the saturation verdict is meaningless and MUST NOT read as
        // "device fine" — the Honor X9d scored 4/4 on a dead workload.
        //
        // Detector (per the diagnosis): aiActiveDelegate == "STUB" with no
        // real inference AND no pinned NEON workers. workersPinned > 0 is the
        // POSITIVE signal that the NEON fallback ran — it is NOT used as an
        // absence detector (it's 0 on every healthy AI scan too). A null
        // verification = V1 / datacollect path (real NEON kernels ran, just
        // no VerificationSummary) → never invalidate.
        val loadApplied = verification == null || run {
            val realAiInference = verification.aiActiveDelegate != "STUB" &&
                verification.aiActiveDelegate != "NONE" &&
                verification.aiInferenceIterationsTotal > 0L
            // fieldsVersion 68: the counters are null when no pin attempt was
            // made; only a positive count is the signal that NEON ran.
            val neonRan = ((verification.workersPinnedBig ?: 0) +
                (verification.workersPinnedMid ?: 0) +
                (verification.workersPinnedLittle ?: 0)) > 0
            realAiInference || neonRan
        }
        if (!loadApplied) {
            saturated = false
            grade = "stress_not_applied"
            satCaveat = null
            satFailureReason = "stress_not_applied: harness did not load the CPU " +
                "(aiActiveDelegate=${verification?.aiActiveDelegate}, " +
                "aiInferences=${verification?.aiInferenceIterationsTotal ?: 0L}, " +
                "workersPinned=0). Saturation verdict is INVALID — not a device-" +
                "health signal. Most likely TFLite STUB with no NEON fallback."
            Log.w(TAG, "compute: LOAD-NOT-APPLIED gate tripped — $satFailureReason")
        }

        Log.i(
            TAG,
            "compute: hw='$buildHardware' " +
                "C1(peakPower=${maxPeakPower}mW)=$c1Passed " +
                "C2(A=$pathA B=$pathB C=$pathC drop=${fmt1(freqDropPct)}%)=$c2Passed " +
                "C3(verifOk=$verificationAllPassed)=$c3Passed " +
                "C4(peakBig=${peakBigFreqKhzAnywhere}kHz/${bigNominalKhz}kHz" +
                "=${c4RatioForGrade?.let { fmt3(it) } ?: "n/a"})=$c4Passed " +
                "loadApplied=$loadApplied " +
                "→ ${conditionsMet}/4 grade=$grade saturated=$saturated"
        )

        @Suppress("DEPRECATION")
        return StressEffectiveness(
            // Deprecated aliases (Commit 1) — kept populated for one release.
            phase3CpuFreqRatio = listOfNotNull(bigRatio, midRatio, littleRatio).maxOrNull() ?: 0f,
            phase3CpuUsageAvg = globalUsageAvg,
            // Existing fields, semantics now driven by multi-axis criterion.
            phase5PowerPeakMw = peakPowerPhase5,
            passedSaturationThreshold = saturated,
            saturationFailureReason = satFailureReason,
            // Per-cluster (Commit 1).
            phase3CpuFreqRatioBig = bigRatio,
            phase3CpuFreqRatioMid = midRatio,
            phase3CpuFreqRatioLittle = littleRatio,
            phase3CpuUsageAvgBig = bigUsage,
            phase3CpuUsageAvgMid = midUsage,
            phase3CpuUsageAvgLittle = littleUsage,
            perCpuUsageAvailable = perCpuUsageAvail,
            // fieldsVersion 67: populated from the compute() parameter (was:
            // left false here and copy()'d by StressTestService). Single
            // source of truth, and the C4 caveat text now reads the same
            // value it reports.
            governorCappingDetected = governorCappingDetected,
            // V2 fix #56-4 / Commit 4: multi-axis fields.
            satC1Passed = c1Passed,
            satC2Passed = c2Passed,
            satC3Passed = c3Passed,
            satC4Passed = c4Passed,
            satConditionsMet = conditionsMet,
            satGrade = grade,
            satCaveat = satCaveat,
            peakPackagePowerMwPhase3 = peakPowerPhase3,
            peakPackagePowerMwPhase5 = peakPowerPhase5,
            peakBigFreqKhzAnywhere = peakBigFreqKhzAnywhere,
            freqDropFromBurstToSustainedPct = freqDropPct,
            // Phase 1.9 fix #2.
            peakBigFreqKhzInStressPhases = peakBigFreqKhzInStressPhases,
            c4RatioStressWindow = c4RatioStressWindow
        )
    }

    // ─────── Helpers ───────

    /** Peak v[19] across the supplied (already phase-filtered) sample list. */
    private fun peakPowerOf(samples: List<TimeSeriesSample>): Int {
        return samples
            .mapNotNull { it.v.getOrNull(IDX_PKG_POWER)?.toInt()?.takeIf { p -> p > 0 } }
            .maxOrNull() ?: 0
    }

    /** Mean of v[idx] across non-zero readings. Returns 0 when none valid. */
    private fun phase3MeanFreqAt(phase3: List<TimeSeriesSample>, idx: Int): Long {
        val readings = phase3.mapNotNull {
            it.v.getOrNull(idx)?.toLong()?.takeIf { v -> v > 0L }
        }
        return if (readings.isEmpty()) 0L else readings.average().toLong()
    }

    /** ratio = mean / nominal, null if either is unavailable. */
    private fun ratio(meanKhz: Long, nominalKhz: Int?): Float? {
        if (meanKhz <= 0L) return null
        if (nominalKhz == null || nominalKhz <= 0) return null
        return meanKhz.toFloat() / nominalKhz.toFloat()
    }

    /**
     * Group the per-CPU phase-3 usage averages into per-cluster averages
     * using cluster IDs derived from per-CPU max-freq. Returns Triple
     * (big, mid, little) in 0..100. null when the cluster has no CPUs
     * with valid readings.
     */
    private fun computePerClusterUsage(
        perCpuUsage: FloatArray,
        perCpuMaxFreqKhz: LongArray
    ): Triple<Float?, Float?, Float?> {
        val clusterIds = SocNominalFrequencies.assignClusterIds(perCpuMaxFreqKhz)
        if (clusterIds.size != perCpuUsage.size) {
            Log.w(TAG, "computePerClusterUsage: cpu count mismatch " +
                "(usage=${perCpuUsage.size} maxFreq=${perCpuMaxFreqKhz.size})")
            return Triple(null, null, null)
        }
        var bigSum = 0.0; var bigCnt = 0
        var midSum = 0.0; var midCnt = 0
        var littleSum = 0.0; var littleCnt = 0
        for (i in perCpuUsage.indices) {
            val u = perCpuUsage[i]
            if (u < 0f || !u.isFinite()) continue
            when (clusterIds[i]) {
                2 -> { bigSum += u; bigCnt++ }
                1 -> { midSum += u; midCnt++ }
                0 -> { littleSum += u; littleCnt++ }
                else -> { /* skip */ }
            }
        }
        return Triple(
            if (bigCnt > 0) (bigSum / bigCnt).toFloat() else null,
            if (midCnt > 0) (midSum / midCnt).toFloat() else null,
            if (littleCnt > 0) (littleSum / littleCnt).toFloat() else null
        )
    }

    /**
     * V2 fix #56-4 / Commit 4: C2 Path C input + always-reported diagnostic.
     *
     * Computes the percentage drop from peak big-cluster freq during the
     * first second of phase 3 (workload spinup, typically near peak) to
     * the mean big-cluster freq AFTER the first 5 seconds (sustained
     * regime). A large drop (≥15%) proves the OS observed real workload
     * pressure and applied DVFS — direct evidence the silicon was
     * engaged, even when usage % signals are unavailable or untrusted.
     *
     * Returns 0 when phase 3 has no samples, when timestamps don't span
     * the burst+sustained windows, or when no valid freq readings exist.
     * Always finite — never NaN/infinity.
     */
    private fun computeFreqDropFromBurstToSustained(phase3: List<TimeSeriesSample>): Float {
        if (phase3.isEmpty()) return 0f
        val phase3StartMs = phase3.first().v.getOrNull(IDX_TIMESTAMP_MS) ?: return 0f
        val burstSamples = phase3.filter { sample ->
            val t = sample.v.getOrNull(IDX_TIMESTAMP_MS) ?: return@filter false
            (t - phase3StartMs) <= PATH_C_BURST_WINDOW_MS
        }
        val sustainedSamples = phase3.filter { sample ->
            val t = sample.v.getOrNull(IDX_TIMESTAMP_MS) ?: return@filter false
            (t - phase3StartMs) >= PATH_C_SUSTAINED_AFTER_MS
        }
        val burstPeakKhz = burstSamples
            .mapNotNull { it.v.getOrNull(IDX_CPU_FREQ_BIG)?.toLong()?.takeIf { f -> f > 0L } }
            .maxOrNull() ?: return 0f
        val sustainedFreqs = sustainedSamples
            .mapNotNull { it.v.getOrNull(IDX_CPU_FREQ_BIG)?.toLong()?.takeIf { f -> f > 0L } }
        if (sustainedFreqs.isEmpty()) return 0f
        val sustainedMeanKhz = sustainedFreqs.average()
        if (burstPeakKhz <= 0L) return 0f
        val drop = ((burstPeakKhz.toDouble() - sustainedMeanKhz) / burstPeakKhz.toDouble()) * 100.0
        // Clamp to [0, 100] — negative would mean sustained > burst (unusual
        // but possible if first second was warmup-cold). We treat that as
        // "no drop observed" rather than reporting a misleading negative.
        return drop.coerceIn(0.0, 100.0).toFloat()
    }

    /**
     * V2 fix #56-4 / Commit 4: composes [satCaveat] when conditionsMet == 3
     * (single-condition failure) and [saturationFailureReason] when
     * conditionsMet ≤ 2 (multiple failures). Returns Pair(caveat, reason);
     * exactly one of the two is non-null in failure cases, both null when
     * all 4 passed.
     */
    /**
     * fieldsVersion 67: `internal` (was private) so the caveat/reason text
     * — which ships verbatim into footer.stressEffectiveness and from
     * there into the model prompt — is directly unit-testable. Kotlin
     * unit tests in this module are a friend of the main source set, so
     * no production visibility is widened outside the app.
     */
    internal fun composeSaturationReasons(
        conditionsMet: Int,
        c1Passed: Boolean,
        c2Passed: Boolean,
        c3Passed: Boolean,
        c4Passed: Boolean,
        maxPeakPower: Int,
        pathA: Boolean,
        pathB: Boolean,
        pathC: Boolean,
        freqDropPct: Float,
        verification: VerificationSummary?,
        c4Ratio: Float?,
        governorCappingDetected: Boolean
    ): Pair<String?, String?> {
        if (conditionsMet == 4) return Pair(null, null)

        // Per-condition descriptions used by both caveat and reason paths.
        val c1Desc = "C1 peak_power failed (peak ${maxPeakPower}mW < ${C1_PEAK_POWER_MW_THRESHOLD}mW)"
        val c2Desc = "C2 sustained_compute failed (path A=$pathA B=$pathB C=$pathC, " +
            "freq_drop=${fmt1(freqDropPct)}%)"
        val c3Desc = "C3 verification_integrity failed (${verificationDetail(verification)})"
        // fieldsVersion 67: 3 decimals, not 2. The Samsung S10 5G field scan
        // measured 0.84761906 — "%.2f" rounded it to 0.85 and the message
        // read "peak ratio 0.85 < 0.85", which is self-contradictory to any
        // reader (human or model).
        val c4Desc = "C4 peak_freq_capability failed (peak ratio " +
            "${c4Ratio?.let { fmt3(it) } ?: "n/a"} < $C4_PEAK_FREQ_RATIO_THRESHOLD)"

        if (conditionsMet == 3) {
            // Exactly one condition failed — verbose caveat with interpretation.
            val caveat = when {
                !c1Passed -> "$c1Desc. Workload didn't reach the 4 W thermal envelope; " +
                    "likely a sub-flagship device or workload didn't engage silicon. " +
                    "Other integrity checks passed."
                !c2Passed -> "$c2Desc. Workload didn't sustain compute load on the " +
                    "targeted clusters. Other integrity checks passed."
                !c3Passed -> "$c3Desc. The stress test successfully exercised the " +
                    "silicon, but defects were detected in verification kernels. " +
                    "AI should flag this as CRITICAL."
                // fieldsVersion 67: describe ONLY what we measured. The
                // previous text hard-coded "likely due to OEM governor policy
                // (Xiaomi/HyperOS class)" onto every C4 failure — it shipped
                // that vendor attribution on a Samsung S10 5G whose own
                // footer carried governorCappingDetected=false, i.e. the
                // sentence contradicted the data beside it. The governor
                // hypothesis is now stated only when the phase-3 watcher
                // actually observed capping.
                !c4Passed -> buildString {
                    append(c4Desc)
                    append(". Peak frequency during the stress window stayed ")
                    append("below the nominal maximum for this SoC.")
                    if (governorCappingDetected) {
                        append(" The phase-3 watcher observed sustained ")
                        append("frequency capping with no matching thermal ")
                        append("pressure, which is consistent with an OS ")
                        append("governor policy cap rather than silicon limits.")
                    }
                    append(" Other integrity checks passed.")
                }
                else -> null
            }
            return Pair(caveat, null)
        }

        // conditionsMet ≤ 2: comma/semicolon-separated reason list.
        val parts = mutableListOf<String>()
        if (!c1Passed) parts += c1Desc
        if (!c2Passed) parts += c2Desc
        if (!c3Passed) parts += c3Desc
        if (!c4Passed) parts += c4Desc
        return Pair(null, parts.joinToString("; "))
    }

    /**
     * fieldsVersion 67: locale-independent number formatting for every
     * string that reaches the footer, the AI payload, or logcat.
     *
     * `"%.2f".format(x)` uses the DEFAULT locale. On an Arabic-locale
     * device that renders Arabic-Indic digits: the Samsung S10 5G field
     * scan shipped `satCaveat` containing "peak ratio ٠٫٨٥ < 0.85" —
     * an automated field carrying digits the backend cannot parse and the
     * model must guess at, next to an ASCII threshold in the same sentence.
     */
    internal fun fmt1(v: Float): String = String.format(Locale.ROOT, "%.1f", v)

    internal fun fmt3(v: Float): String = String.format(Locale.ROOT, "%.3f", v)

    /** Identifies which specific verification fields failed (or "no_failures").
     *  Phase 1.7.B: cpu_fma_errors / memory_random_errors / prime_sieve_incorrect
     *  removed — those fields are no longer on VerificationSummary. */
    private fun verificationDetail(v: VerificationSummary?): String {
        if (v == null) return "verification_unavailable"
        val failures = mutableListOf<String>()
        // fieldsVersion 68: null = the verification did not run — not an error count.
        val writeErrors = v.storageWriteVerifyErrors
        val readErrors = v.storageReadVerifyErrors
        if (writeErrors != null && writeErrors > 0) failures += "storage_write_errors=$writeErrors"
        if (readErrors != null && readErrors > 0) failures += "storage_read_errors=$readErrors"
        if (v.gpuComputeGemmCorrect == false) failures += "gpu_gemm_incorrect"
        return if (failures.isEmpty()) "no_failures" else failures.joinToString(", ")
    }

    /**
     * Fallback when per-CPU max-freq topology is unavailable (sysfs blocked at
     * sampler init): hard-coded per-cluster nominals for known SoCs. Mirrors
     * the entries in [SocNominalFrequencies.getSocNominalMaxFreq] but split
     * into the three clusters.
     */
    private fun deriveNominalsFromBuildHardware(buildHardware: String): Triple<Int?, Int?, Int?> {
        val hw = buildHardware.lowercase()
        return when {
            hw.contains("mt6991") -> Triple(3_626_000, 3_330_000, 2_500_000) // Dimensity 9400 (X925 + A725 + A720)
            hw.contains("mt6989") -> Triple(3_250_000, 3_000_000, 2_400_000) // Dimensity 9300
            hw.contains("mt6985") -> Triple(3_350_000, 3_000_000, 2_000_000) // Dimensity 9200
            hw.contains("mt6983") -> Triple(3_050_000, 2_850_000, 1_800_000) // Dimensity 9000
            hw.contains("exynos2400") -> Triple(3_210_000, 2_900_000, 1_950_000) // Galaxy S24 Korea/EU
            hw.contains("exynos2200") -> Triple(2_800_000, 2_500_000, 1_950_000) // S22
            hw.contains("exynos9820") -> Triple(2_730_000, 2_310_000, 1_950_000) // S10 5G prime + Cortex-A75 + A55
            hw.contains("exynos9810") -> Triple(2_700_000, 2_310_000, 1_950_000) // S9
            hw.contains("exynos8895") -> Triple(2_316_000, 1_690_000, 1_690_000) // S8
            // Snapdragon 855 (SDM855): Kryo 485 Prime + Gold + Silver
            // SDM855 reports Build.HARDWARE = "qcom" so we can't disambiguate
            // by hw string alone. Caller falls through to sysfs at runtime.
            // This is a hard-coded fallback for when sysfs is also blocked.
            else -> Triple(null, null, null)
        }
    }
}
