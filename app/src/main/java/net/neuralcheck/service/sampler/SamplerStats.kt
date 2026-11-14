package net.neuralcheck.service.sampler

import net.neuralcheck.data.local.files.TimeSeriesSample

/**
 * Streaming aggregator for time-series samples — collects the aggregates needed
 * by FunctionalTestRunner.computeTimeSeriesTests without holding every sample in memory.
 *
 * Thread model: updated from the sampler thread, snapshotted from the service coroutine
 * at end-of-scan. `snapshot()` is synchronized with `update()` via the instance lock.
 *
 * Temperature handling: some devices (e.g., Samsung Exynos) expose no CPU/GPU thermal
 * zone readable by untrusted apps. We track all 3 sources (cpu, gpu, battery) and
 * compute a "best available" temperature, preferring cpu > gpu > battery.
 */
class SamplerStats {

    // Battery level (index 4)
    private var startBatteryLevel: Int = -1
    private var endBatteryLevel: Int = -1

    // Battery temp (index 1) — ALWAYS available on Android
    private var startBatteryTempC: Float = Float.NaN
    private var firstBattTempC: Float = Float.NaN
    private var lastBattTempC: Float = Float.NaN
    private var maxBattTempC: Float = Float.NEGATIVE_INFINITY

    // CPU temp — V5 schema: no longer in v[]; fed via [updateThermalSidecar].
    private var maxCpuTempC: Float = Float.NEGATIVE_INFINITY
    private var firstCpuTempC: Float = Float.NaN
    private var lastCpuTempC: Float = Float.NaN

    // GPU temp — V5 schema: no longer in v[]; fed via [updateThermalSidecar].
    private var maxGpuTempC: Float = Float.NEGATIVE_INFINITY
    private var firstGpuTempC: Float = Float.NaN
    private var lastGpuTempC: Float = Float.NaN

    // Skin temp — V5 schema: no longer in v[]; fed via [updateThermalSidecar].
    private var maxSkinTempC: Float = Float.NEGATIVE_INFINITY
    private var firstSkinTempC: Float = Float.NaN
    private var lastSkinTempC: Float = Float.NaN

    // GPU FPS (index 10)
    private var maxFps: Float = 0f
    private var minFpsDuringStress: Float = Float.POSITIVE_INFINITY
    private var firstFpsDuringStress: Float = Float.NaN

    // CPU frequency — V5 schema: v[5] is now max-across-clusters
    // (was v[6] cpu_freq_big_khz in V4). The aggregated peak / minimum
    // semantics carry over: we still want the highest steady-state freq
    // observed in the first 30s and the lowest under stress.
    private var maxFreqFirst30sKhz: Long = 0L
    private var minFreqStressKhz: Long = Long.MAX_VALUE
    private val freqReadingsStressMhz: MutableList<Float> = ArrayList(800)

    // Durations & counts
    private var firstSampleElapsedMs: Long = -1L
    private var lastSampleElapsedMs: Long = -1L
    private var samplesSeen: Int = 0
    private var stressStartElapsedMs: Long = -1L
    private var stressEndElapsedMs: Long = -1L

    // Phase 7 (V2 COOLDOWN) tracking — needed to derive T-03's °C/min rate
    // from the temperature drop across the actual cooldown window.
    // Using the whole-scan max/last pair would double-count heat still being
    // dumped at the start of the cooldown phase and yield a bogus rate.
    private var cooldownStartCpuTempC: Float = Float.NaN
    private var cooldownEndCpuTempC: Float = Float.NaN
    private var cooldownStartGpuTempC: Float = Float.NaN
    private var cooldownEndGpuTempC: Float = Float.NaN
    private var cooldownStartBattTempC: Float = Float.NaN
    private var cooldownEndBattTempC: Float = Float.NaN
    private var cooldownStartElapsedMs: Long = -1L
    private var cooldownEndElapsedMs: Long = -1L

    // PowerManager thermal headroom (Android 11+) — only thermal signal that
    // survives SELinux on stock Samsung. Tracked as running mean (Welford-ish)
    // so we don't have to keep every reading. Peak is the worst-case (= highest
    // headroom value, since 1.0 ≈ throttling threshold).
    private var headroomSum: Double = 0.0
    private var headroomCount: Int = 0
    private var headroomPeak: Float = Float.NaN
    // PowerManager.currentThermalStatus (Android 10+): tracked as the WORST
    // (= highest) value seen during the run.
    private var thermalStatusMax: Int = -1

    // V2 fix #56-3 (fieldsVersion 58): per-cluster usage tracking during phase 3.
    // Lazy-initialized on first sample where perCpuUsage is non-null.
    //   perCpuUsageSum[i]  : running sum of utilization for CPU index i (phase 3)
    //   perCpuUsageCount[i]: count of samples that reported a valid (>=0) reading
    //   perCpuMaxFreqKhz   : set once at sampler init via setPerCpuMaxFreqKhz();
    //                        forwarded to the snapshot so the calculator can
    //                        group CPUs into clusters without re-reading sysfs.
    private var perCpuUsageSum: DoubleArray? = null
    private var perCpuUsageCount: IntArray? = null
    private var perCpuMaxFreqKhz: LongArray? = null
    @Volatile private var perCpuUsageEverObserved: Boolean = false

    // V5 schema (Task E): scanStillness via accelerometer std-dev.
    // Welford-style running sums on the magnitude (sqrt(x²+y²+z²)) so we
    // produce variance / std-dev with constant memory regardless of scan
    // length.
    private var accelMagSum: Double = 0.0
    private var accelMagSumSq: Double = 0.0
    private var accelMagCount: Int = 0

    @Synchronized
    fun reset() {
        startBatteryLevel = -1
        endBatteryLevel = -1
        startBatteryTempC = Float.NaN
        firstBattTempC = Float.NaN
        lastBattTempC = Float.NaN
        maxBattTempC = Float.NEGATIVE_INFINITY
        maxCpuTempC = Float.NEGATIVE_INFINITY
        firstCpuTempC = Float.NaN
        lastCpuTempC = Float.NaN
        maxGpuTempC = Float.NEGATIVE_INFINITY
        firstGpuTempC = Float.NaN
        lastGpuTempC = Float.NaN
        maxSkinTempC = Float.NEGATIVE_INFINITY
        firstSkinTempC = Float.NaN
        lastSkinTempC = Float.NaN
        maxFps = 0f
        minFpsDuringStress = Float.POSITIVE_INFINITY
        firstFpsDuringStress = Float.NaN
        maxFreqFirst30sKhz = 0L
        minFreqStressKhz = Long.MAX_VALUE
        freqReadingsStressMhz.clear()
        firstSampleElapsedMs = -1L
        lastSampleElapsedMs = -1L
        samplesSeen = 0
        stressStartElapsedMs = -1L
        stressEndElapsedMs = -1L
        cooldownStartCpuTempC = Float.NaN
        cooldownEndCpuTempC = Float.NaN
        cooldownStartGpuTempC = Float.NaN
        cooldownEndGpuTempC = Float.NaN
        cooldownStartBattTempC = Float.NaN
        cooldownEndBattTempC = Float.NaN
        cooldownStartElapsedMs = -1L
        cooldownEndElapsedMs = -1L
        headroomSum = 0.0
        headroomCount = 0
        headroomPeak = Float.NaN
        thermalStatusMax = -1
        // V2 fix #56-3: clear per-CPU accumulators. perCpuMaxFreqKhz is
        // intentionally NOT cleared here — it's a topology constant set
        // once via setPerCpuMaxFreqKhz at sampler.start(); resetting it
        // every scan would force a sysfs re-read.
        perCpuUsageSum = null
        perCpuUsageCount = null
        perCpuUsageEverObserved = false
        accelMagSum = 0.0
        accelMagSumSq = 0.0
        accelMagCount = 0
    }

    /**
     * V2 fix #56-3 (fieldsVersion 58): caches the per-CPU `cpuinfo_max_freq`
     * topology read once at sampler start. Forwarded into the snapshot so
     * StressEffectivenessCalculator can derive cluster boundaries without
     * a second sysfs walk at end-of-scan. Pass null when the read failed
     * (sysfs blocked) — the snapshot will report null and the calculator
     * will skip per-cluster grouping.
     */
    @Synchronized
    fun setPerCpuMaxFreqKhz(arr: LongArray?) {
        perCpuMaxFreqKhz = arr
    }

    /**
     * V2 fix #56-3 (fieldsVersion 58): accumulates per-CPU utilization
     * during phase 3 only. Other phases ignore the input — phase-3 is the
     * only window where the workload is continuously running and per-cluster
     * saturation is meaningful. perCpuUsage = null (sampler never observed
     * a valid /proc/stat per-CPU read) keeps perCpuUsageEverObserved false
     * so the snapshot reports phase3PerCpuUsageAvg = null and the calculator
     * falls back to freq-only saturation logic.
     */
    @Synchronized
    fun updatePerCpuUsage(perCpuUsage: FloatArray?, phase: Int) {
        if (perCpuUsage == null) return
        if (phase != 3) return
        // Lazy-init accumulators sized to the first observed CPU count.
        // If subsequent reads return a different size (extremely rare —
        // would require offline-online CPU hotplug mid-scan), we ignore
        // the mismatched read rather than reset and lose history.
        val sumArr = perCpuUsageSum ?: DoubleArray(perCpuUsage.size).also {
            perCpuUsageSum = it
        }
        val countArr = perCpuUsageCount ?: IntArray(perCpuUsage.size).also {
            perCpuUsageCount = it
        }
        if (sumArr.size != perCpuUsage.size) return
        for (i in perCpuUsage.indices) {
            val u = perCpuUsage[i]
            if (u >= 0f && u.isFinite()) {
                sumArr[i] += u.toDouble()
                countArr[i]++
                perCpuUsageEverObserved = true
            }
        }
    }

    /**
     * Push a thermal sidecar reading captured outside the v[] sample array.
     * Called from [DisciplinedSampler.collectSample] once per sample.
     *
     * Headroom + status come from the PowerManager API.
     * cpu/gpu/skin temps were V4 v[] channels; under the V5 schema they no
     * longer occupy v[] slots and flow through this sidecar instead.
     *
     * NaN/-1 sentinels mark "not available" and are ignored by the running
     * aggregates.
     */
    /**
     * V5 schema (Task E): push the magnitude of one accelerometer sample
     * into the stillness aggregator. Magnitude is sqrt(x² + y² + z²). The
     * running mean / variance is collapsed into accelStdDev at snapshot
     * time. Negative / NaN / non-finite values are ignored.
     */
    @Synchronized
    fun updateAccelMagnitude(magnitude: Float) {
        if (magnitude.isNaN() || !magnitude.isFinite() || magnitude < 0f) return
        accelMagSum += magnitude.toDouble()
        accelMagSumSq += magnitude.toDouble() * magnitude.toDouble()
        accelMagCount++
    }

    @Synchronized
    fun updateThermalSidecar(
        headroom: Float,
        thermalStatus: Int,
        cpuTempC: Float = -1f,
        gpuTempC: Float = -1f,
        skinTempC: Float = -1f
    ) {
        if (!headroom.isNaN() && headroom.isFinite() && headroom > 0f) {
            headroomSum += headroom.toDouble()
            headroomCount++
            if (headroomPeak.isNaN() || headroom > headroomPeak) headroomPeak = headroom
        }
        if (thermalStatus >= 0 && thermalStatus > thermalStatusMax) {
            thermalStatusMax = thermalStatus
        }
        if (!cpuTempC.isNaN() && cpuTempC > 0f) {
            if (firstCpuTempC.isNaN()) firstCpuTempC = cpuTempC
            lastCpuTempC = cpuTempC
            if (cpuTempC > maxCpuTempC) maxCpuTempC = cpuTempC
        }
        if (!gpuTempC.isNaN() && gpuTempC > 0f) {
            if (firstGpuTempC.isNaN()) firstGpuTempC = gpuTempC
            lastGpuTempC = gpuTempC
            if (gpuTempC > maxGpuTempC) maxGpuTempC = gpuTempC
        }
        if (!skinTempC.isNaN() && skinTempC > 0f) {
            if (firstSkinTempC.isNaN()) firstSkinTempC = skinTempC
            lastSkinTempC = skinTempC
            if (skinTempC > maxSkinTempC) maxSkinTempC = skinTempC
        }
    }

    @Synchronized
    fun update(sample: TimeSeriesSample, phase: Int) {
        val v = sample.v
        val elapsedMs = v[0].toLong() // scan-relative ms (index 0)

        samplesSeen++
        if (firstSampleElapsedMs < 0) firstSampleElapsedMs = elapsedMs
        lastSampleElapsedMs = elapsedMs

        val battTemp = v[1]
        if (!battTemp.isNaN() && battTemp > 0f) {
            if (startBatteryTempC.isNaN()) startBatteryTempC = battTemp
            if (firstBattTempC.isNaN()) firstBattTempC = battTemp
            lastBattTempC = battTemp
            if (battTemp > maxBattTempC) maxBattTempC = battTemp
        }

        val battLevel = v[4].toInt()
        if (battLevel in 0..100) {
            if (startBatteryLevel < 0) startBatteryLevel = battLevel
            endBatteryLevel = battLevel
        }

        // V5 schema: cpu/gpu temps are NOT in v[] anymore — they flow through
        // updateThermalSidecar(). The cooldown CPU/GPU temp captures below
        // therefore use the latest sidecar reading (lastCpuTempC/lastGpuTempC)
        // observed inside the same phase.

        // V5 v[5] is cpu_freq_max_khz (was v[6] cpu_freq_big_khz under V4).
        // The aggregates below keep the same intent — peak freq early in the
        // scan, minimum freq under stress, mean for diagnostics — they just
        // now reflect the cluster-max rather than the big-cluster reading.
        val maxFreqKhz = v[5].toLong()

        if (elapsedMs <= 30_000L && maxFreqKhz > 0L && maxFreqKhz > maxFreqFirst30sKhz) {
            maxFreqFirst30sKhz = maxFreqKhz
        }

        // During intensive stress (phase 3): track min freq, readings, fps drop
        if (phase == 3) {
            if (stressStartElapsedMs < 0) stressStartElapsedMs = elapsedMs
            stressEndElapsedMs = elapsedMs

            if (maxFreqKhz > 0L) {
                if (maxFreqKhz < minFreqStressKhz) minFreqStressKhz = maxFreqKhz
                freqReadingsStressMhz += (maxFreqKhz / 1000f)
            }

            val fps = v[10]
            if (!fps.isNaN() && fps > 0f) {
                if (firstFpsDuringStress.isNaN()) firstFpsDuringStress = fps
                if (fps > maxFps) maxFps = fps
                if (fps < minFpsDuringStress) minFpsDuringStress = fps
            }
        }

        // During cooldown — V2 path: phase 7 is COOLDOWN (V2Phase enum).
        // The previous `phase == 5` gate dated back to V1 when phase 5 WAS
        // the cooldown phase; under V2 phase 5 is COMBINED_PEAK (the hottest
        // phase of the scan), so capturing the "cooldown window" there
        // meant every V2 scan saw temperature rising and T-03 was clamped
        // to 0 → FAIL regardless of device. See
        // docs/audits/phase_1_11_t03_diagnosis/REPORT.md for the trace.
        //
        // V5: cpu/gpu temps come from the sidecar (lastCpuTempC/lastGpuTempC),
        // not v[]. We just pin them to the cooldown-start/end snapshots here.
        if (phase == 7) {
            if (cooldownStartElapsedMs < 0) cooldownStartElapsedMs = elapsedMs
            cooldownEndElapsedMs = elapsedMs
            if (!lastCpuTempC.isNaN() && lastCpuTempC > 0f) {
                if (cooldownStartCpuTempC.isNaN()) cooldownStartCpuTempC = lastCpuTempC
                cooldownEndCpuTempC = lastCpuTempC
            }
            if (!lastGpuTempC.isNaN() && lastGpuTempC > 0f) {
                if (cooldownStartGpuTempC.isNaN()) cooldownStartGpuTempC = lastGpuTempC
                cooldownEndGpuTempC = lastGpuTempC
            }
            if (!battTemp.isNaN() && battTemp > 0f) {
                if (cooldownStartBattTempC.isNaN()) cooldownStartBattTempC = battTemp
                cooldownEndBattTempC = battTemp
            }
        }
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val durationMs = if (firstSampleElapsedMs >= 0 && lastSampleElapsedMs >= firstSampleElapsedMs)
            lastSampleElapsedMs - firstSampleElapsedMs else 0L
        val stressDurationMs = if (stressStartElapsedMs >= 0 && stressEndElapsedMs >= stressStartElapsedMs)
            stressEndElapsedMs - stressStartElapsedMs else 0L

        // Pick best temperature source: cpu > gpu > battery
        val cpuAvailable = maxCpuTempC != Float.NEGATIVE_INFINITY && maxCpuTempC > 0f
        val gpuAvailable = maxGpuTempC != Float.NEGATIVE_INFINITY && maxGpuTempC > 0f
        val battAvailable = maxBattTempC != Float.NEGATIVE_INFINITY && maxBattTempC > 0f

        val tempSource: String
        val maxBestTemp: Float
        val firstBestTemp: Float
        when {
            cpuAvailable -> {
                tempSource = "cpu"
                maxBestTemp = maxCpuTempC
                firstBestTemp = firstCpuTempC
            }
            gpuAvailable -> {
                tempSource = "gpu"
                maxBestTemp = maxGpuTempC
                firstBestTemp = firstGpuTempC
            }
            battAvailable -> {
                tempSource = "battery"
                maxBestTemp = maxBattTempC
                firstBestTemp = firstBattTempC
            }
            else -> {
                tempSource = "none"
                maxBestTemp = -1f
                firstBestTemp = Float.NaN
            }
        }

        val maxTemp = if (maxBestTemp > 0f) maxBestTemp else -1f
        // Phase 1.14: T-02 is now a true rate (°C/min) per JEDEC JESD51-1
        // thermal-rise convention. Pre-1.14 this field held a raw °C delta
        // but was reported through ThermalTests.computeHeatRise with the
        // unit string "°C/min" — a labeling lie that surfaced as the 38.20
        // °C/min Samsung S24 Ultra anomaly in production scan b69da673.
        //
        // Denominator choice: `durationMs` (full sample window:
        // firstSampleElapsedMs → lastSampleElapsedMs) rather than
        // `stressDurationMs` (phase-3 window only). `firstBestTemp` is
        // captured at the first valid CPU sample (≈ scan start) and
        // `maxBestTemp` at the hottest sample of any phase, so the delta
        // spans much more time than phase 3 alone. Dividing by only the
        // phase-3 window would over-state the rate. Including the cooldown
        // (phase 7) samples in `durationMs` slightly *under*-states the
        // rate, but the bias is on the safe side (false PASS rather than
        // false FAIL) and the absolute error is ≈ 8 % at typical scan
        // lengths.
        val heatRise = if (
            !firstBestTemp.isNaN() &&
            maxBestTemp > 0f &&
            durationMs > 0L
        ) {
            val deltaC = (maxBestTemp - firstBestTemp).coerceAtLeast(0f)
            val durationMin = durationMs / 60_000f
            if (durationMin > 0f) deltaC / durationMin else -1f
        } else {
            -1f
        }

        // T-03 is a RATE in °C/min — measured across the actual cooldown window
        // (V2 phase 7) rather than peak→scan-end. That window is typically ~1 min,
        // so dividing by it converts a raw drop into a comparable rate.
        val (coolStart, coolEnd) = when (tempSource) {
            "cpu" -> cooldownStartCpuTempC to cooldownEndCpuTempC
            "gpu" -> cooldownStartGpuTempC to cooldownEndGpuTempC
            "battery" -> cooldownStartBattTempC to cooldownEndBattTempC
            else -> Float.NaN to Float.NaN
        }
        // Note: the result is NOT clamped at 0 — a negative rate means
        // temperature ROSE during the cooldown window, which is a real
        // signal of a thermally distressed device and should surface as
        // FAIL downstream (handled in ThermalTests.computeCooldown).
        // Sentinel for "no phase-7 samples captured" is Float.NaN so
        // ThermalTests can distinguish "missing data" (UNAVAILABLE) from
        // "data shows rising temp" (FAIL) without sentinel collisions.
        val cooldownRate: Float = if (
            !coolStart.isNaN() && !coolEnd.isNaN() &&
            cooldownEndElapsedMs > cooldownStartElapsedMs
        ) {
            val drop = coolStart - coolEnd
            val durationMin = (cooldownEndElapsedMs - cooldownStartElapsedMs) / 60_000f
            if (durationMin > 0f) drop / durationMin else Float.NaN
        } else Float.NaN

        val fMaxFps = if (maxFps > 0f) maxFps else -1f
        val fMinFps = if (minFpsDuringStress == Float.POSITIVE_INFINITY) -1f else minFpsDuringStress
        val fInitialFps = if (firstFpsDuringStress.isNaN()) -1f else firstFpsDuringStress

        val fMinFreqStress = if (minFreqStressKhz == Long.MAX_VALUE) -1L else minFreqStressKhz

        val headroomAvg: Float = if (headroomCount > 0)
            (headroomSum / headroomCount).toFloat() else Float.NaN
        // If we never got a real CPU/GPU thermal-zone read but headroom was
        // available, promote "headroom" as the canonical source so the report
        // has a meaningful gauge to show. Battery temp is still a worse signal
        // than headroom (delayed, ambient-coupled), so headroom wins over it.
        val effectiveTempSource: String = when {
            tempSource == "cpu" || tempSource == "gpu" -> tempSource
            !headroomAvg.isNaN() && headroomCount >= 3 -> "headroom"
            tempSource == "battery" -> "battery"
            else -> tempSource
        }

        // V2 fix #56-3: compute per-CPU phase-3 usage averages. Returns
        // null when no per-CPU sample was ever observed (sampler couldn't
        // read /proc/stat per-CPU lines, or phase 3 had no samples).
        val phase3PerCpuUsageAvg: FloatArray? = if (perCpuUsageEverObserved) {
            val sums = perCpuUsageSum
            val counts = perCpuUsageCount
            if (sums != null && counts != null && sums.size == counts.size) {
                FloatArray(sums.size) { i ->
                    if (counts[i] > 0) (sums[i] / counts[i]).toFloat() else -1f
                }
            } else null
        } else null

        // V5 schema (Task E): collapse the running accel-magnitude sums
        // into a std-dev. Sample variance = E[X²] - E[X]². Negative
        // results from float-precision chatter are floored at 0.
        val accelStdDev: Float? = if (accelMagCount >= 2) {
            val mean = accelMagSum / accelMagCount.toDouble()
            val variance = (accelMagSumSq / accelMagCount.toDouble()) - (mean * mean)
            kotlin.math.sqrt(variance.coerceAtLeast(0.0)).toFloat()
        } else {
            null // fieldsVersion 68: the variance of fewer than 2 readings is undefined, not -1
        }
        val accelSampleCount = accelMagCount

        return Snapshot(
            startBatteryLevel = startBatteryLevel,
            endBatteryLevel = endBatteryLevel,
            startBatteryTempC = if (startBatteryTempC.isNaN()) -1f else startBatteryTempC,
            durationMs = durationMs,
            maxTempC = maxTemp,
            firstBestTempC = firstBestTemp,
            heatRiseC = heatRise,
            cooldownRateCPerMin = cooldownRate,
            tempSource = effectiveTempSource,
            maxFps = fMaxFps,
            minFpsDuringStress = fMinFps,
            initialFpsDuringStress = fInitialFps,
            maxFreqFirst30sKhz = maxFreqFirst30sKhz,
            minFreqStressKhz = fMinFreqStress,
            freqReadingsStressMhz = freqReadingsStressMhz.toList(),
            stressDurationMs = stressDurationMs,
            samplesSeen = samplesSeen,
            thermalHeadroomAvg = if (headroomCount > 0) headroomAvg else null,
            thermalHeadroomPeak = if (!headroomPeak.isNaN()) headroomPeak else null,
            thermalStatusMax = if (thermalStatusMax >= 0) thermalStatusMax else null,
            perCpuMaxFreqKhz = perCpuMaxFreqKhz,
            phase3PerCpuUsageAvg = phase3PerCpuUsageAvg,
            accelStdDev = accelStdDev,
            accelSampleCount = accelSampleCount
        )
    }

    data class Snapshot(
        val startBatteryLevel: Int,
        val endBatteryLevel: Int,
        val startBatteryTempC: Float,
        val durationMs: Long,
        /** Max temperature from the best available source (cpu > gpu > battery). -1 if none. */
        val maxTempC: Float,
        /** Phase 1.14 hotfix continuation (2026-05-18): the FIRST valid
         *  temperature reading captured during the scan, from the same source
         *  cascade as [maxTempC] (cpu > gpu > battery). Float.NaN when no
         *  source was ever available. Exposed so downstream consumers (T-02
         *  in StressTestService) can recompute their own delta against a
         *  trusted maxTemp value without re-deriving from the contaminated
         *  internal [heatRiseC] field. */
        val firstBestTempC: Float,
        /** Phase 1.14: heat-rise RATE in °C per minute (was a raw °C delta
         *  pre-1.14, which produced the 38 °C/min Samsung S24 Ultra anomaly).
         *  Computed as `(maxBestTemp - firstBestTemp) / scanDurationMinutes`.
         *  Field name kept for ABI continuity; the unit is °C/min.
         *  -1 when no valid temperature samples were captured.
         *
         *  Phase 1.14 hotfix continuation (2026-05-18): this field uses the
         *  internal `maxBestTemp` which can be contaminated by zone-naming
         *  artifacts on some vendors (the same contamination T-01 worked
         *  around by routing through StressTestService.selectFooterMaxTemp).
         *  The T-02 path in StressTestService no longer consumes this
         *  value directly — it recomputes a delta against `footerMaxTemp`
         *  using [firstBestTempC] + [durationMs]. This field is kept for
         *  other consumers and for backwards-compat. */
        val heatRiseC: Float,
        /** T-03 cooling rate in °C per minute, measured across the phase-7 (COOLDOWN) window.
         *  Float.NaN when phase 7 was never captured (early stop, missing thermal channel).
         *  Negative values are valid — they mean temperature ROSE during cooldown
         *  (a real signal that ThermalTests grades as FAIL). */
        val cooldownRateCPerMin: Float,
        /** Which source populated max/heat/cooldown: "cpu", "gpu", "battery", "headroom", or "none". */
        val tempSource: String,
        val maxFps: Float,
        val minFpsDuringStress: Float,
        val initialFpsDuringStress: Float,
        val maxFreqFirst30sKhz: Long,
        val minFreqStressKhz: Long,
        val freqReadingsStressMhz: List<Float>,
        val stressDurationMs: Long,
        val samplesSeen: Int,
        /** PowerManager.getThermalHeadroom mean across the run. 1.0 ≈ throttling. null if unsupported. */
        val thermalHeadroomAvg: Float?,
        /** PowerManager.getThermalHeadroom worst-case (highest) across the run. null if unsupported. */
        val thermalHeadroomPeak: Float?,
        /** PowerManager.currentThermalStatus worst-case during the run (0=NONE..6=SHUTDOWN). null if unsupported. */
        val thermalStatusMax: Int?,
        // ── V2 fix #56-3 (fieldsVersion 58): per-CPU sidecar ──
        /** Per-CPU `cpuinfo_max_freq` snapshot taken at sampler init.
         *  Used by [StressEffectivenessCalculator] to bucket [phase3PerCpuUsageAvg]
         *  into per-cluster averages. null when sysfs walk failed at start. */
        val perCpuMaxFreqKhz: LongArray?,
        /** Phase-3 mean utilization per CPU index (0..100). -1 in any
         *  slot that received no valid reading. null when sampler couldn't
         *  read /proc/stat per-CPU lines (legacy /proc/stat blocked on
         *  HyperOS, etc.) — calculator falls back to freq-only saturation. */
        val phase3PerCpuUsageAvg: FloatArray?,
        // ── V5 schema (Task E): accelerometer stillness sidecar ──
        /** Std-dev of accelerometer magnitude across all collected samples
         *  in m/s². `null` when fewer than 2 samples were observed (the
         *  variance is undefined; fieldsVersion 68 — it was the sentinel -1).
         *  The footer's [scanStillness.isStill] flag thresholds this against
         *  0.5 m/s² to detect device motion. */
        val accelStdDev: Float?,
        /** Count of accelerometer-magnitude samples folded into [accelStdDev]. */
        val accelSampleCount: Int
    )
}
