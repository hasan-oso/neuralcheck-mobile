package net.neuralcheck.data.remote

import net.neuralcheck.data.local.files.CpuKnownAnswerNames
import net.neuralcheck.data.local.files.CpuTempSources
import net.neuralcheck.data.local.files.FileShape
import net.neuralcheck.data.local.files.TimeSeriesData
import net.neuralcheck.data.local.files.TimeSeriesStates
import net.neuralcheck.data.local.files.VerificationWireNames
import net.neuralcheck.data.remote.model.BehaviorFingerprint
import net.neuralcheck.data.remote.model.DeviceInfo
import net.neuralcheck.data.remote.model.FunctionalResultDto
import net.neuralcheck.data.remote.model.ManualResultDto
import net.neuralcheck.data.remote.model.ManualResultsHeaderDto
import net.neuralcheck.data.remote.model.RawTimeSeriesPayload
import net.neuralcheck.data.remote.model.TimeSeriesFooterDto
import net.neuralcheck.data.remote.model.TimeSeriesHeaderDto
import net.neuralcheck.data.remote.model.TimeSeriesSampleDto
import net.neuralcheck.data.remote.model.VariableDto
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.util.DeviceProfileKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the analyzeDevice callable payload. Pure data forwarder:
 * device info, functional/manual test outcomes, and the complete
 * raw time-series with every sample, header, and footer field
 * untouched.
 *
 * The previous implementation computed 40+ derived statistics and
 * downsampled to 50 points. That violated the project's core
 * principle ("code collects, AI judges") because the AI never saw
 * the raw data — it saw the app's interpretation of the raw data.
 * If the app's heuristics were wrong (and several were — see Bugs
 * 2, 11, 15, 16), the AI couldn't notice. This rewrite removes all
 * that computation.
 *
 * ALL of the following were removed:
 *   - calculateStats / calculateStatsV2 (40+ scalar stats)
 *   - buildCompactSeries / buildCompactSeriesV2 (downsampling)
 *   - buildPhaseAnalysis (per-phase aggregation)
 *   - bestTemp (CPU/GPU/battery temp picking heuristic)
 *   - estimateAgeYears (security-patch-to-years computation)
 *   - emptyStats / safeRead / computeRate / computeThrottlePercent
 *     / computeRsd / computeBurstStats (helpers for the above)
 *
 * The single build() entry point handles both V1 and V2 scans
 * uniformly because raw data has the same shape in both — the
 * variables[] schema (18 vs 22 entries) is forwarded as-is and the
 * AI side reads it.
 */
@Singleton
class BehaviorFingerprintBuilder @Inject constructor() {

    /**
     * @param noTimeSeriesReason why [timeSeries] is null, as TEXT (the error
     *   message when reading threw, rule 21): it becomes
     *   `timeSeriesState = "no_file:<reason>"`. Ignored when [timeSeries] is
     *   present.
     * @param manualResultsHeader fieldsVersion 69 (N-17): the header of the manual
     *   results (`hiddenByModel`). `null` leaves the key ABSENT — "the caller supplied
     *   none", which the server reads as "no knowledge", not "nothing hidden"; the
     *   production caller (AiScoringEngine) always passes one, an empty list included.
     */
    fun build(
        scanReport: ScanReport,
        timeSeries: TimeSeriesData?,
        deviceProfileKey: DeviceProfileKey? = null,
        noTimeSeriesReason: String? = null,
        manualResultsHeader: ManualResultsHeaderDto? = null
    ): BehaviorFingerprint {
        val device = buildDeviceInfo(scanReport, scanReport.functionalResults, deviceProfileKey)

        val funcDtos = scanReport.functionalResults
            .filterNot { it.testId.startsWith("M-") }
            .map { it.toFunctionalDto() }

        // Bug 5 fix (2026-05-17): propagate categoryName onto every manualResults
        // entry so the AI can union it with functionalResults by category without
        // inferring the bucket from the testId alone. The categoryName is the
        // same string ManualTestsViewModel.setResult already writes into the Room
        // FunctionalResultEntity row (TestSpec.fromId(id)?.category?.name?.
        // lowercase(Locale.US) ?: "manual"), so funcDtos and manualDtos use a
        // single canonical vocabulary on the wire.
        val manualDtos = scanReport.functionalResults
            .filter { it.testId.startsWith("M-") }
            .map { ManualResultDto(
                testId = it.testId,
                status = statusToString(it.status),
                // Send null instead of the legacy "manual" sentinel that
                // ManualTestsViewModel.setResult writes when TestSpec.fromId
                // can't resolve the testId — the AI's category-grading rule
                // works in terms of TestCategory.id strings, and an unknown
                // category string would just confuse it. M-01..M-22 all
                // resolve in TestSpec so this branch is defensive, not hot.
                categoryName = it.categoryName.takeIf { c ->
                    c.isNotBlank() && c != "manual"
                },
                // fieldsVersion 68 (N-16): compound raw, verbatim; null = none.
                measurements = it.measurements
            ) }

        val rawTs = buildRawTimeSeries(timeSeries, noTimeSeriesReason)

        return BehaviorFingerprint(
            device = device,
            functionalResults = funcDtos,
            manualResults = manualDtos,
            manualResultsHeader = manualResultsHeader,
            timeSeries = rawTs,
            // fieldsVersion 68 (T-18 row 11): no header = no knowledge, not "off".
            powerSaveMode = if (timeSeries == null || timeSeries.recoveredFromFooterBackup) null
            else timeSeries.header.isPowerSaveMode,
            antiTamperFlags = scanReport.antiTamperFlags.map { it.type.name },
            // fieldsVersion 71 (A-P6-13): the state beside the boolean; no header = no knowledge, like the boolean.
            powerSaveState = if (timeSeries == null || timeSeries.recoveredFromFooterBackup) null
            else timeSeries.header.powerSaveState
        )
    }

    /**
     * Mirrors the on-disk .ts.json.gz structure 1:1. No transformation.
     *
     * fieldsVersion 68 (T-18 row 11, rule 22): when there is no time series
     * (null: V1 legacy without a file, or a read failure) the payload used to
     * be "structurally empty" — a header of 0 / "" / false and a footer of 0f
     * measurements, none of which were ever read. Now every such field is ABSENT
     * and `timeSeriesState` (always present) says why: `no_file:<reason>`. The
     * footer-backup recovery (header lost, footer intact) is declared
     * `footer_backup` with its header absent the same way. Only the facts that
     * are exact stay: 0 samples were sent, and `isComplete = false` (the
     * long-standing declaration; see [TimeSeriesFooterDto.isComplete]).
     */
    private fun buildRawTimeSeries(timeSeries: TimeSeriesData?, noTimeSeriesReason: String?): RawTimeSeriesPayload {
        if (timeSeries == null) {
            return RawTimeSeriesPayload(
                header = TimeSeriesHeaderDto(
                    scanId = null,
                    deviceModel = null,
                    androidApiLevel = null,
                    totalRamMb = null,
                    cpuCores = null,
                    batteryLevelStart = null,
                    batteryTempStart = null,
                    isPowerSaveMode = null,
                    startTimestamp = null,
                    deviceFingerprint = null,
                    appVersion = null,
                    scanType = null
                ),
                variables = emptyList(),
                samples = emptyList(),
                footer = TimeSeriesFooterDto(
                    totalSamples = 0,
                    endTimestamp = null,
                    isComplete = false,
                    earlyStopReason = null,
                    maxBatteryTemp = null,
                    maxCpuTemp = null,
                    cpuTempFinalSource = CpuTempSources.ABSENT_NO_TIME_SERIES_FILE,
                    batteryLevelEnd = null,
                    verificationResults = null,
                    intensityFactor = null,
                    deviceTierMeasured = null,
                    tempRiseRateInProbe = null,
                    tempSource = null,
                    thermalHeadroomAvg = null,
                    thermalHeadroomPeak = null,
                    thermalStatusMax = null,
                    phaseCompletionStatus = null,
                    throttlingEvents = null,
                    batteryCurrentScalingApplied = null
                ),
                timeSeriesState = TimeSeriesStates.noFile(noTimeSeriesReason)
            )
        }
        val h = timeSeries.header
        val f = timeSeries.footer
        // Header — verbatim copy.
        // fieldsVersion 68 (T-18 row 11): a header recovered from the footer
        // backup holds placeholders (0 / ""), not readings — the scan id is the
        // one thing known there — so its fields are left ABSENT, like no_file.
        val headerDto = if (timeSeries.recoveredFromFooterBackup) {
            TimeSeriesHeaderDto(
                scanId = h.scanId,
                deviceModel = null, androidApiLevel = null, totalRamMb = null, cpuCores = null,
                batteryLevelStart = null, batteryTempStart = null, isPowerSaveMode = null,
                startTimestamp = null, deviceFingerprint = null, appVersion = null, scanType = null
            )
        } else TimeSeriesHeaderDto(
            scanId = h.scanId,
            deviceModel = h.deviceModel,
            androidApiLevel = h.androidApiLevel,
            totalRamMb = h.totalRamMb,
            cpuCores = h.cpuCores,
            batteryLevelStart = h.batteryLevelStart,
            batteryTempStart = h.batteryTempStart,
            isPowerSaveMode = h.isPowerSaveMode,
            startTimestamp = h.startTimestamp,
            deviceFingerprint = h.deviceFingerprint,
            appVersion = h.appVersion,
            scanType = h.scanType,
            powerSaveState = h.powerSaveState
        )
        // Variables — verbatim copy of the schema.
        // V2 fix #30: log the size to diagnose the empty-variables[]
        // bug seen in scan_27.json. Empty here ⇒ the writer didn't
        // populate them; non-empty here but missing in serialized
        // payload ⇒ Gson dropped them.
        android.util.Log.i("BehaviorFingerprint",
            "buildRawTimeSeries: variables.size=${timeSeries.variables.size} " +
            "samples.size=${timeSeries.samples.size}")
        val variablesDto = timeSeries.variables.map {
            VariableDto(name = it.name, unit = it.unit, source = it.source)
        }
        // Samples — verbatim copy of every sample. NO downsampling.
        val samplesDto = timeSeries.samples.map { s ->
            TimeSeriesSampleDto(phase = s.phase, ts = s.ts, v = s.v.toList())
        }
        // Footer — verbatim copy of every field. The verificationResults
        // map and throttlingEvents list are pass-through Map/List<Any>
        // so the backend sees identical structure to what the app wrote
        // to disk. Storage segments, throttling event objects, and any
        // future verification fields surface here without code changes.
        val footerDto = TimeSeriesFooterDto(
            totalSamples = f.totalSamples,
            endTimestamp = f.endTimestamp,
            isComplete = f.isComplete,
            earlyStopReason = f.earlyStopReason,
            maxBatteryTemp = f.maxBatteryTemp,
            // fieldsVersion 68 (S-33): maxCpuTemp is forwarded ONLY from a pre-68 file
            // that carries it (the server reads it for <= 67); a >= 68 file has the two
            // new fields instead.
            maxCpuTemp = f.maxCpuTemp,
            cpuTempFinalC = f.cpuTempFinalC,
            cpuTempFinalSource = f.cpuTempFinalSource,
            batteryLevelEnd = f.batteryLevelEnd,
            verificationResults = footerVerificationToMap(f.verificationResults, f.fieldsVersion),
            intensityFactor = f.intensityFactor,
            deviceTierMeasured = f.deviceTierMeasured,
            tempRiseRateInProbe = f.tempRiseRateInProbe,
            tempSource = f.tempSource,
            thermalHeadroomAvg = f.thermalHeadroomAvg,
            thermalHeadroomPeak = f.thermalHeadroomPeak,
            thermalStatusMax = f.thermalStatusMax,
            phaseCompletionStatus = f.phaseCompletionStatus
                ?.mapKeys { it.key.toString() },
            // fieldsVersion 68 (batch 3 part 2-b): `maybe` — a file without the key
            // leaves this list null though its type says it cannot be; `.map` on it
            // threw. Absent stays absent, and fieldsNotInFile names it.
            throttlingEvents = maybe(f.throttlingEvents)?.map { ev ->
                mapOf(
                    "phase" to ev.phase,
                    "skinTemp" to ev.skinTemp,
                    "newIntensity" to ev.newIntensity,
                    "reason" to ev.reason,
                    "timestamp" to ev.timestamp,
                    // V2 fix #54: surface sourceIsSysfs in payload so the
                    // AI can distinguish "real sensor terminate" from
                    // "synthetic event recorded but ignored" — was
                    // already in the data class, now in the wire format.
                    "sourceIsSysfs" to ev.sourceIsSysfs,
                    // fieldsVersion 65: true only when the event actually
                    // changed the workload (level-3 terminate that fired).
                    // Level-1/2 intensity reductions are recorded-only —
                    // no kernel consumes effectiveIntensity.
                    "applied" to ev.applied
                )
            },
            batteryCurrentScalingApplied = f.batteryCurrentScalingApplied,
            stressEffectiveness = stressEffectivenessToMap(f.stressEffectiveness),
            // V2 fix #56: forward batteryHealth + dataQuality + known
            // capacity classification verbatim. AI gates confidence in
            // battery samples on dataQuality.batteryScalingLocked and
            // dataQuality.batteryReaderRegistered.
            batteryHealth = f.batteryHealth,
            dataQuality = dataQualityToMap(f.dataQuality),
            knownCapacityClassification = f.knownCapacityClassification,
            // V2 fix #56 follow-up (2026-05-04): forward build schema
            // version + per-field computation diagnostics. AI uses these
            // to distinguish "device on stale build" / "field computation
            // failed" / "serialization truncated" — the three failure
            // modes that produced the same on-the-wire symptom of
            // missing footer fields.
            fieldsVersion = f.fieldsVersion.takeIf { it > 0 },
            footerDiagnostics = f.footerDiagnostics,
            // V5 schema: forward the new metadata fields verbatim. Each
            // is converted to a small Map<String, Any?> that the Firebase
            // callable framework can serialize without knowing the
            // Kotlin class.
            channelDiscovery = f.channelDiscovery?.let { cd ->
                mapOf(
                    "attempted" to cd.attempted,
                    "available" to cd.available,
                    "blockedBySelinux" to cd.blockedBySelinux,
                    "missing" to cd.missing,
                    "summary" to cd.summary,
                    // fieldsVersion 68 (N-07): the TEXT behind every channel
                    // that is not available; null (pre-68 file) omits the key.
                    "reasons" to cd.reasons
                )
            },
            thermalSensorAvailability = f.thermalSensorAvailability?.let { ta ->
                mapOf(
                    "cpuTemp" to ta.cpuTemp,
                    "gpuTemp" to ta.gpuTemp,
                    "skinTemp" to ta.skinTemp,
                    "source" to ta.source
                )
            },
            scanStillness = f.scanStillness?.let { st ->
                mapOf(
                    // fieldsVersion 68: isStill / accelStdDev are ABSENT keys
                    // when the stillness could not be judged; stillnessState
                    // (always present) says why.
                    "isStill" to st.isStill,
                    "accelStdDev" to st.accelStdDev,
                    "sampleCount" to st.sampleCount,
                    "stillnessState" to st.stillnessState
                )
            },
            // fieldsVersion 65 — measurement-integrity fields, forwarded
            // verbatim: constant-value declarations, nominal-vs-actual
            // sample count, sample-content checksum, flush-failure tally.
            unmeasuredDefaults = f.unmeasuredDefaults,
            expectedSamples = f.expectedSamples,
            samplesSha256 = f.samplesSha256,
            flushFailureCount = f.flushFailureCount,
            fieldsNotInFile = fieldsNotInFile(f),
            // fieldsVersion 68 — per-device channel layout, forwarded
            // verbatim. A null stays null (the key is then absent from the
            // payload, which is how "not tracked / did not run" is declared
            // here — footerDiagnostics names the failure when there was one).
            cpuPolicies = f.cpuPolicies?.map { p ->
                mapOf(
                    "policy" to p.policy,
                    "relatedCpus" to p.relatedCpus,
                    "cpuinfoMaxFreqKhz" to p.cpuinfoMaxFreqKhz,
                    "unreadableReason" to p.unreadableReason
                )
            },
            channelReadFailures = f.channelReadFailures,
            channelReadFailureCounts = f.channelReadFailureCounts,
            // fieldsVersion 68 — the slow thermal channels as lists, verbatim
            // (a null headroom stays an absent key; its element's state says why).
            thermalHeadroomReadings = f.thermalHeadroomReadings?.map { r ->
                mapOf(
                    "t" to r.t,
                    "headroom" to r.headroom,
                    "state" to r.state
                )
            },
            thermalHeadroomState = f.thermalHeadroomState,
            thermalStatusEvents = f.thermalStatusEvents?.map { e ->
                mapOf(
                    "t" to e.t,
                    "status" to e.status
                )
            },
            thermalStatusState = f.thermalStatusState,
            // fieldsVersion 68 — raw battery pairs, verbatim (a null field is an
            // absent key; batteryPairsState.invalidReads counts them).
            batteryPairs = f.batteryPairs?.map { p ->
                mapOf(
                    "t" to p.t,
                    "voltageRaw" to p.voltageRaw,
                    "currentRaw" to p.currentRaw,
                    "chargeCounterRaw" to p.chargeCounterRaw,
                    "levelRaw" to p.levelRaw,
                    "levelScale" to p.levelScale,
                    "tempRaw" to p.tempRaw
                )
            },
            transitionalAggregates = f.transitionalAggregates,
            preScanWarningsState = f.preScanWarningsState,
            // fieldsVersion 71 (A-P6-13): the battery window and the brightness lock, verbatim; null (a file stamped < 71) is an ABSENT key and fieldsNotInFile names it.
            batteryWindowPct = f.batteryWindowPct?.let { w ->
                mapOf(
                    net.neuralcheck.service.stress.ScanBatteryWindow.KEY_MIN_PCT to w.minPct,
                    net.neuralcheck.service.stress.ScanBatteryWindow.KEY_MAX_PCT to w.maxPct
                )
            },
            brightnessLock = f.brightnessLock?.let { b ->
                net.neuralcheck.service.stress.BrightnessLock.Result(b.state, b.value, b.method).toWire()
            },
            batteryPairsState = f.batteryPairsState?.let { s ->
                mapOf(
                    "receiverRegistered" to s.receiverRegistered,
                    "broadcastCount" to s.broadcastCount,
                    "droppedByCap" to s.droppedByCap,
                    "invalidReads" to s.invalidReads
                )
            }
        )
        return RawTimeSeriesPayload(
            header = headerDto,
            variables = variablesDto,
            samples = samplesDto,
            footer = footerDto,
            timeSeriesState = if (timeSeries.recoveredFromFooterBackup) TimeSeriesStates.FOOTER_BACKUP
            else TimeSeriesStates.FILE
        )
    }

    /**
     * Converts the V2 VerificationSummary to a flat Map<String, Any> so
     * Firebase callable framework can serialize it without knowing the
     * Kotlin class. All numeric values (Long, Int, Float, Boolean) and
     * the storageThroughputSegments list are forwarded verbatim — no
     * thresholding, no derived flags, no scoring.
     */
    private fun footerVerificationToMap(
        v: net.neuralcheck.data.local.files.VerificationSummary?,
        fileStamp: Int
    ): Map<String, Any>? {
        val map = verificationMapOf(maybe(v) ?: return null)
        // fieldsVersion 68 (batch 3 part 2-b): a file stamped below 68 cannot carry the
        // explicit-state keys. Gson built its VerificationSummary through the no-arg
        // constructor, so those fields hold Kotlin DEFAULTS — "no V2 result",
        // `criticalDefectInputsNotRun: [gpuComputeGemm]`, "MiB/s" — claims that are
        // false for a scan that did run (and contradict the file's own
        // gpuComputeGemmCorrect / criticalDefectDetected). Absent stays absent;
        // fieldsNotInFile names them.
        if (FileShape.predatesStates(fileStamp)) {
            VerificationWireNames.SINCE_68.forEach { map.remove(it) }
        }
        return map
    }

    /** The names the file lacked — see [TimeSeriesFooterDto.fieldsNotInFile]. */
    private fun fieldsNotInFile(f: net.neuralcheck.data.local.files.TimeSeriesFooter): List<String> {
        val missing = mutableListOf<String>()
        // Fields typed non-null that a file may not carry (Gson builds this class without
        // running Kotlin defaults, so a missing key is null, not the default).
        if (maybe(f.throttlingEvents) == null) missing += "throttlingEvents"
        if (maybe(f.verificationResults) == null) missing += "verificationResults"
        if (maybe(f.phaseCompletionStatus) == null) missing += "phaseCompletionStatus"
        if (maybe(f.stressEffectiveness) == null) missing += "stressEffectiveness"
        if (maybe(f.dataQuality) == null) missing += "dataQuality"
        if (maybe(f.deviceTierMeasured) == null) missing += "deviceTierMeasured"
        if (maybe(f.batteryCurrentScalingApplied) == null) missing += "batteryCurrentScalingApplied"
        // fieldsVersion 71: a file stamped below 71 has neither the window nor the brightness lock (its header has no powerSaveState either)
        if (f.fieldsVersion < FileShape.WINDOW_STAMP) {
            missing += FileShape.FOOTER_FIELDS_SINCE_71
        }
        if (FileShape.predatesStates(f.fieldsVersion)) {
            missing += FileShape.FOOTER_FIELDS_SINCE_68
            missing += FileShape.NESTED_FIELDS_SINCE_68
            missing += VerificationWireNames.SINCE_68.map { FileShape.VERIFICATION_PREFIX + it }
        }
        return missing
    }

    /**
     * Returns [value] typed nullable. A parsed file can hold null where the Kotlin type
     * says it cannot (Gson allocates the footer without running constructors); reading
     * such a field through this function is what stops the compiler assuming otherwise.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun <T> maybe(value: T?): T? = value

    private fun verificationMapOf(v: net.neuralcheck.data.local.files.VerificationSummary): MutableMap<String, Any> {
        val map = mutableMapOf<String, Any>()
        // Phase 1.7.B: legacy synthetic-kernel fields removed —
        // cpuFmaIterations*, cpuFmaErrors, cpuPrimeSieveCorrect,
        // cpuShaHashes*, memoryMarchPassed, memoryWalkingOnesPassed,
        // memoryRandomVerifyErrors. Lab parsers should consume the
        // new aiInferenceIterationsTotal in the footer instead.
        // fieldsVersion 68 (ت-18 rows 1-2): an absent verification is an ABSENT
        // key plus its state, never a default true/false.
        v.gpuComputeGemmCorrect?.let { map["gpuComputeGemmCorrect"] = it }
        map["gpuComputeGemmState"] = v.gpuComputeGemmState
        map["gpuFragmentHashTotal"] = v.gpuFragmentHashTotal
        map["gpuFragmentHashMatches"] = v.gpuFragmentHashMatches
        map["gpuRenderCompletionMsCold"] = v.gpuRenderCompletionMsCold
        map["gpuRenderCompletionMsWarm"] = v.gpuRenderCompletionMsWarm
        map["memoryTestedBytes"] = v.memoryTestedBytes
        // fieldsVersion 68 (T-17, N-10): the memory verification producer's RAW
        // output. A count exists only when the battery executed (ABSENT key
        // otherwise, memoryVerifyState says why) — a 0 is then a measured 0.
        // No `…Passed` is written: the server derives it (rule 20).
        map[VerificationWireNames.MEMORY_VERIFY_STATE] = v.memoryVerifyState
        v.memoryVerifyBytes?.let { map[VerificationWireNames.MEMORY_VERIFY_BYTES] = it }
        v.memoryMarchErrors?.let { map[VerificationWireNames.MEMORY_MARCH_ERRORS] = it }
        v.memoryWalkingOnesErrors?.let { map[VerificationWireNames.MEMORY_WALKING_ONES_ERRORS] = it }
        v.memoryRandomVerifyErrors?.let { map[VerificationWireNames.MEMORY_RANDOM_VERIFY_ERRORS] = it }
        v.memoryVerifyBufferBytes?.let { map[VerificationWireNames.MEMORY_VERIFY_BUFFER_BYTES] = it }
        v.memoryVerifyTimeCapMs?.let { map[VerificationWireNames.MEMORY_VERIFY_TIME_CAP_MS] = it }
        v.memoryVerifyDurationMs?.let { map[VerificationWireNames.MEMORY_VERIFY_DURATION_MS] = it }
        v.memoryVerifyFreeBytes?.let { map[VerificationWireNames.MEMORY_VERIFY_FREE_BYTES] = it }
        v.memoryPhaseWindows?.let { windows ->
            map[VerificationWireNames.MEMORY_PHASE_WINDOWS] = windows.map { w ->
                mapOf(
                    VerificationWireNames.WINDOW_NAME to w.name,
                    VerificationWireNames.WINDOW_START_MS to w.startMs,
                    VerificationWireNames.WINDOW_END_MS to w.endMs
                )
            }
        }
        // Phase 1.7.C: canonical AI workload signals — lab parsers gate
        // 'scan ran' on aiInferenceIterationsTotal > 0.
        map["aiInferenceIterationsTotal"] = v.aiInferenceIterationsTotal
        map["aiInferenceWorkers"] = v.aiInferenceWorkers
        map["aiInferenceAvgMs"] = v.aiInferenceAvgMs
        map["aiActiveDelegate"] = v.aiActiveDelegate
        // Phase 1.15 (Fix 1 + Fix 4): STUB→NEON fallback telemetry. Lets the
        // backend tell "TFLite STUB but NEON loaded the CPU" (valid scan)
        // apart from "STUB and nothing loaded the CPU" (invalid), and learn
        // the exact TFLite init failure without a manual logcat.
        // fieldsVersion 68 (T-18 row 12): "no error" is the ABSENCE of this key —
        // `?: ""` wrote an empty string for it, a value for something that does
        // not exist. Read "no delegate error" from aiActiveDelegate (a real
        // delegate = no error), "STUB" + this text = the error.
        v.aiDelegateInitError?.let { map["aiDelegateInitError"] = it }
        map["cpuStressFallbackUsed"] = v.cpuStressFallbackUsed
        // fieldsVersion 66: v[10] trust signal — timed-out 30-frame fences.
        map["gpuFenceTimeoutCount"] = v.gpuFenceTimeoutCount
        // fieldsVersion 68 (ت-18 row 3, T-08, N-09): the error counts exist only
        // when a verification ran (ABSENT key otherwise, with storageVerifyState
        // saying why); storageSlcCacheSizeMb is deleted — the server computes it
        // from storageThroughputSegments, and it was a literal 0.
        v.storageWriteVerifyErrors?.let { map["storageWriteVerifyErrors"] = it }
        v.storageReadVerifyErrors?.let { map["storageReadVerifyErrors"] = it }
        map["storageVerifyState"] = v.storageVerifyState
        // fieldsVersion 68 (T-16, N-09): what the read-back covered and how. Absent
        // unless a read was opened / compared.
        v.storageVerifyBytesCompared?.let { map[VerificationWireNames.STORAGE_VERIFY_BYTES_COMPARED] = it }
        v.storageVerifyBytesPlanned?.let { map[VerificationWireNames.STORAGE_VERIFY_BYTES_PLANNED] = it }
        v.storageVerifyDurationMs?.let { map[VerificationWireNames.STORAGE_VERIFY_DURATION_MS] = it }
        v.storageVerifyMethod?.let { map[VerificationWireNames.STORAGE_VERIFY_METHOD] = it }
        // fieldsVersion 66: real extent of the storage write loop, so the
        // backend reads v[8]'s zero tail as "write finished at the byte
        // cap", not "storage collapsed".
        map["storageBytesWritten"] = v.storageBytesWritten
        map["storageWriteDurationMs"] = v.storageWriteDurationMs
        // fieldsVersion 68 (T-19, N-21): the unit of the segments' `mbps` field.
        map["storageThroughputSegmentsUnit"] = v.storageThroughputSegmentsUnit
        map["storageThroughputSegments"] = v.storageThroughputSegments.map { seg ->
            mapOf(
                "cumulativeBytes" to seg.cumulativeBytes,
                "mbps" to seg.mbps
            )
        }
        v.criticalDefectDetected?.let { map["criticalDefectDetected"] = it }
        map["criticalDefectInputsNotRun"] = v.criticalDefectInputsNotRun
        // V2 fix #56-3 (fieldsVersion 59): per-cluster pin telemetry.
        // AI uses these to weight cluster-level temp + freq signals — e.g.
        // when workersPinFailedLittle > 0 and workersPinnedLittle == 0,
        // the little-cluster diagnostics reflect scheduler-chosen placement,
        // not the test's intent.
        //
        // fieldsVersion 68 (ت-13, N-12): the counters exist only when a pin
        // attempt was made; otherwise the keys are ABSENT and workersPinState
        // says why — a 0 here used to read as "tried, none failed".
        v.workersPinnedBig?.let { map["workersPinnedBig"] = it }
        v.workersPinnedMid?.let { map["workersPinnedMid"] = it }
        v.workersPinnedLittle?.let { map["workersPinnedLittle"] = it }
        v.workersPinFailedBig?.let { map["workersPinFailedBig"] = it }
        v.workersPinFailedMid?.let { map["workersPinFailedMid"] = it }
        v.workersPinFailedLittle?.let { map["workersPinFailedLittle"] = it }
        map["workersPinState"] = v.workersPinState
        // fieldsVersion 68 (decision D, T-20): the CPU known-answer checks. The raw
        // prime count and the per-check statuses; no aggregate boolean (rule 20).
        map[CpuKnownAnswerNames.STATE] = v.cpuKnownAnswerState
        v.cpuKnownAnswerRuns?.let { runs -> map[CpuKnownAnswerNames.RUNS] = runs.map { it.toWireMap() } }
        // Part 2-c: D's time per phase (a declared transitional aggregate of the runs above).
        v.cpuKnownAnswerTotalMsByPhase?.let { totals ->
            map[CpuKnownAnswerNames.TOTAL_MS_BY_PHASE] = totals.mapKeys { it.key.toString() }
        }
        return map
    }

    /**
     * V2 fix #54: forward StressEffectiveness as a flat map. AI uses
     * passedSaturationThreshold to gate confidence in the rest of the
     * metrics — false-positive A grades on governor-capped devices
     * (e.g. MIUI on Xiaomi) are the failure mode this catches.
     */
    @Suppress("DEPRECATION")
    private fun stressEffectivenessToMap(
        s: net.neuralcheck.data.local.files.StressEffectiveness?
    ): Map<String, Any?>? {
        if (s == null) return null
        return mapOf(
            // Deprecated aliases — kept for one release while lab/DeepSeek migrate
            // to the per-cluster fields. Removal target: fieldsVersion 59.
            "phase3CpuFreqRatio" to s.phase3CpuFreqRatio,
            "phase3CpuUsageAvg" to s.phase3CpuUsageAvg,
            "phase5PowerPeakMw" to s.phase5PowerPeakMw,
            "passedSaturationThreshold" to s.passedSaturationThreshold,
            "saturationFailureReason" to s.saturationFailureReason,
            // V2 fix #56-3 (fieldsVersion 58): per-cluster breakdown.
            "phase3CpuFreqRatioBig" to s.phase3CpuFreqRatioBig,
            "phase3CpuFreqRatioMid" to s.phase3CpuFreqRatioMid,
            "phase3CpuFreqRatioLittle" to s.phase3CpuFreqRatioLittle,
            "phase3CpuUsageAvgBig" to s.phase3CpuUsageAvgBig,
            "phase3CpuUsageAvgMid" to s.phase3CpuUsageAvgMid,
            "phase3CpuUsageAvgLittle" to s.phase3CpuUsageAvgLittle,
            "perCpuUsageAvailable" to s.perCpuUsageAvailable,
            // V2 fix #56-3 / Commit 3 (fieldsVersion 59): governor capping flag.
            "governorCappingDetected" to s.governorCappingDetected,
            // V2 fix #56-4 / Commit 4 (fieldsVersion 60): multi-axis criterion.
            // Per-condition flags so AI can correlate with thermal / power /
            // verification signals. satGrade is the human-readable verdict;
            // satCaveat is set ONLY when satConditionsMet == 3 (3-of-4 pass
            // with a single explicit failure). Diagnostic peak power/freq
            // values let AI cross-check against its own interpretation
            // without re-walking the samples list.
            "satC1Passed" to s.satC1Passed,
            "satC2Passed" to s.satC2Passed,
            "satC3Passed" to s.satC3Passed,
            "satC4Passed" to s.satC4Passed,
            "satConditionsMet" to s.satConditionsMet,
            "satGrade" to s.satGrade,
            "satCaveat" to s.satCaveat,
            "peakPackagePowerMwPhase3" to s.peakPackagePowerMwPhase3,
            "peakPackagePowerMwPhase5" to s.peakPackagePowerMwPhase5,
            "peakBigFreqKhzAnywhere" to s.peakBigFreqKhzAnywhere,
            "freqDropFromBurstToSustainedPct" to s.freqDropFromBurstToSustainedPct
        )
    }

    /**
     * V2 fix #56 (2.4): forward DataQuality as a flat map. AI uses
     * coverage % + source tags to weight per-sensor confidence —
     * a scan with cpuTempCoveragePct = 0 should be graded differently
     * than one with 100%, even if both report similar peak temps.
     */
    private fun dataQualityToMap(
        q: net.neuralcheck.data.local.files.DataQuality?
    ): Map<String, Any?>? {
        if (q == null) return null
        return mapOf(
            "cpuTempCoveragePct" to q.cpuTempCoveragePct,
            "cpuTempPrimarySource" to q.cpuTempPrimarySource,
            "gpuTempCoveragePct" to q.gpuTempCoveragePct,
            "skinTempCoveragePct" to q.skinTempCoveragePct,
            "skinTempSourceBreakdown" to q.skinTempSourceBreakdown,
            "cpuFreqBigCoveragePct" to q.cpuFreqBigCoveragePct,
            "procStatAvailable" to q.procStatAvailable,
            "batteryReaderRegistered" to q.batteryReaderRegistered,
            "batteryScalingLocked" to q.batteryScalingLocked,
            "batteryScalingDetailedSource" to q.batteryScalingDetailedSource,
            "batteryScalingMultiplier" to q.batteryScalingMultiplier,
            "batteryScalingSignFlip" to q.batteryScalingSignFlip,
            // fieldsVersion 65 — battery-channel integrity: lock timing
            // (samples before this index used the soft default heuristic),
            // filtered invalid CURRENT_NOW readings, applied µV→mV
            // voltage conversions.
            "batteryScalingLockAtSample" to q.batteryScalingLockAtSample,
            "batteryInvalidCurrentReadings" to q.batteryInvalidCurrentReadings,
            "batteryVoltageUvScaledCount" to q.batteryVoltageUvScaledCount,
            // fieldsVersion 66 — v[6] provenance: what cpu_usage_pct really
            // measured ("procstat" system load vs "process_fallback" = this
            // app's own share) and the first sample index of the fallback.
            "cpuUsageSource" to q.cpuUsageSource,
            "cpuUsageFallbackFromSample" to q.cpuUsageFallbackFromSample
        )
    }

    /**
     * Builds DeviceInfo from raw sources only:
     *   - Domain DeviceInfo (model, manufacturer, androidVersion, apiLevel)
     *   - Build.* properties (fingerprint, board, hardware, bootloader)
     *   - Functional test raw values for hardware specs (CPU/GPU model,
     *     RAM, storage, security patch) — these are readouts from
     *     /proc and Build.*, not derived metrics
     *
     * No computed fields, no heuristics. estimatedAgeYears was removed
     * (backend computes it from securityPatch if needed).
     */
    private fun buildDeviceInfo(
        scanReport: ScanReport,
        functional: List<TestResult>,
        deviceProfileKey: DeviceProfileKey?
    ): DeviceInfo {
        val di = scanReport.deviceInfo
        return DeviceInfo(
            model = di.model,
            cpuModel = findResultValue(functional, "C-05") ?: "unknown",
            gpuModel = findResultValue(functional, "G-01") ?: "unknown",
            androidVersion = di.androidVersion,
            apiLevel = di.apiLevel,
            totalRamGb = findResultValue(functional, "R-01") ?: "0",
            availableRamGb = findResultValue(functional, "R-02") ?: "0",
            totalStorageGb = findResultValue(functional, "S-01") ?: "0",
            // fieldsVersion 67 — both storage fields were mis-sourced:
            //   • availableStorageGb read S-02's rawValue, which is the
            //     percent-FREE (StorageTests.testAvailableStorage documents
            //     rawValue as percent so the rule engine can interpolate);
            //     the GB figure lives in that result's UNIT string
            //     "% (149.2 GB)". Honor MTN-NX1 therefore shipped "67.5"
            //     as gigabytes while 149.2 GB were actually free.
            //   • storagePercent read S-03, which is SEQUENTIAL WRITE SPEED
            //     in MB/s — hence "635" (Honor) and "166" (Samsung) landing
            //     in a field the model reads as a percentage.
            // Both now come from S-02, each from the part that holds it.
            availableStorageGb = extractStorageAvailableGb(functional),
            storagePercent = findResultValue(functional, "S-02") ?: "0",
            securityPatch = findResultValue(functional, "SEC-03") ?: "",
            deviceProfileKey = deviceProfileKey,
            buildFingerprint = android.os.Build.FINGERPRINT,
            buildBoard = android.os.Build.BOARD,
            buildHardware = android.os.Build.HARDWARE,
            buildBootloader = android.os.Build.BOOTLOADER
        )
    }

    private fun findResultValue(results: List<TestResult>, testId: String): String? =
        results.find { it.testId == testId }?.rawValue

    /**
     * fieldsVersion 67: pulls the free-space GB figure out of S-02.
     *
     * S-02 splits its measurement across two fields (StorageTests.kt):
     *   rawValue      = "67.5"            → percent free
     *   rawValueUnit  = "% (149.2 GB)"    → the gigabytes
     * The previous implementation took the first space-separated token of
     * rawValue — i.e. the percent — and shipped it as gigabytes.
     *
     * Returns "0" when the unit string doesn't carry a parenthesised GB
     * figure, so a malformed/absent result degrades to the existing
     * "unknown" sentinel rather than to a wrong number.
     */
    private fun extractStorageAvailableGb(results: List<TestResult>): String {
        val unit = results.find { it.testId == "S-02" }?.rawValueUnit ?: return "0"
        val gb = Regex("""\(([0-9]+(?:\.[0-9]+)?)\s*GB\)""").find(unit)
            ?.groupValues?.getOrNull(1)
        return gb ?: "0"
    }

    private fun TestResult.toFunctionalDto(): FunctionalResultDto = FunctionalResultDto(
        testId = testId,
        componentName = componentName,
        categoryName = categoryName,
        rawValue = rawValue.orEmpty(),
        rawValueUnit = rawValueUnit.orEmpty(),
        status = statusToString(status),
        notes = notes,
        // fieldsVersion 68 (N-16): compound raw, verbatim; null = none.
        measurements = measurements
    )

    /**
     * Forwards the enum value as-is using the canonical string form
     * (lowercase enum name). The TestStatus.toDbString helper already
     * does exactly that and stays in sync with any future enum changes.
     * Possible values: pass, fail, warning, unavailable, not_tested, error.
     */
    private fun statusToString(status: TestStatus): String =
        TestStatus.toDbString(status)
}
