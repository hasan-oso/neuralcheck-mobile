package net.neuralcheck.data.remote.model

import net.neuralcheck.util.DeviceProfileKey

/**
 * Payload sent to the analyzeDevice callable Cloud Function.
 *
 * PHILOSOPHY (May 2026 redesign): the app collects raw data and
 * forwards it untransformed. NO local statistics, NO downsampling,
 * NO per-phase aggregation, NO heuristic flags. The AI sees the
 * full sample stream exactly as written to the time-series file.
 *
 * If the backend needs derived metrics (peak temp, drain rate,
 * throttle %, phase averages, etc.) it computes them server-side
 * from these raw samples. Mobile code must remain a pure data
 * collector — see project memory rule "code collects, AI judges".
 *
 * Raw payload size: ~270 KB JSON for a full 480 s V2 scan
 * (~1240 samples × 22 floats). Firebase callable framework
 * compresses with HTTP/2; on-the-wire size is much smaller.
 * The 10 MB callable limit is not approached.
 *
 * The same .ts.json.gz file is also uploaded to Firebase Storage
 * via FirebaseSyncService for long-term archival and future
 * model-training pipelines. Both paths carry identical content.
 */
data class BehaviorFingerprint(
    val device: DeviceInfo,
    val functionalResults: List<FunctionalResultDto>,
    val manualResults: List<ManualResultDto>,
    /**
     * fieldsVersion 69 (N-17, contract section 7-h): the header of [manualResults]
     * — a list has none of its own. Present on every payload the production path
     * builds (AiScoringEngine); `null` = the caller supplied none, an ABSENT key,
     * which is what a build <= fv68 sends too — "no knowledge", never "nothing
     * hidden" (that is `hiddenByModel = []`).
     */
    val manualResultsHeader: ManualResultsHeaderDto? = null,
    val timeSeries: RawTimeSeriesPayload,
    /** fieldsVersion 68 (T-18 row 11): `null` (an ABSENT key) when no time-series
     *  header was available — it used to be `false`, a claim that power-save was off. */
    val powerSaveMode: Boolean?,
    val antiTamperFlags: List<String>,
    /**
     * fieldsVersion 71 (A-P6-13): `off` | `on` | `read_failed`, beside [powerSaveMode] (which stays for older readers and is ABSENT when this is `read_failed`).
     * Absent = a build <= 70, or no time-series header.
     */
    val powerSaveState: String? = null
)

/**
 * Complete raw time-series — every sample collected during the scan,
 * plus header and footer metadata exactly as written to the
 * .ts.json.gz file. The backend reconstructs whatever derived view
 * it needs from this payload.
 *
 * variables carries the schema (22 entries for V2, 18 for V1
 * legacy scans). Each sample's v[] array indexes into this
 * schema, e.g. v[5] is cpu_temp_c when variables[5].name ==
 * "cpu_temp_c".
 *
 * footer carries scan-wide raw measurements: verification
 * counters, throttling events, storage segments, intensity
 * factor, device tier classification. None of these are computed
 * by the app — they are raw outputs of the stress kernels and
 * the throttler.
 */
data class RawTimeSeriesPayload(
    val header: TimeSeriesHeaderDto,
    val variables: List<VariableDto>,
    val samples: List<TimeSeriesSampleDto>,
    val footer: TimeSeriesFooterDto,
    /**
     * fieldsVersion 68 (T-18 row 11): where this payload's time series came
     * from, ALWAYS present — `file` (the .ts.json.gz was read), `footer_backup`
     * (only the footer-only backup was recovered: no header, no samples), or
     * `no_file:<reason>`. In the last two the header fields (and, for no_file,
     * the footer measurements) are ABSENT keys, not zeros and empty strings that
     * look like readings.
     */
    val timeSeriesState: String
)

/**
 * fieldsVersion 68 (T-18 row 11): every field is nullable so that a payload
 * with no header can leave them ABSENT instead of shipping 0 / "" / false that
 * read as measurements. A header read from a real file always has all of them.
 */
data class TimeSeriesHeaderDto(
    val scanId: String?,
    val deviceModel: String?,
    val androidApiLevel: Int?,
    val totalRamMb: Long?,
    val cpuCores: Int?,
    val batteryLevelStart: Int?,
    val batteryTempStart: Float?,
    val isPowerSaveMode: Boolean?,
    val startTimestamp: Long?,
    val deviceFingerprint: String?,
    val appVersion: String?,
    val scanType: String?,
    /** fieldsVersion 71 (A-P6-13): `off` | `on` | `read_failed`; absent = a file written before 71. */
    val powerSaveState: String? = null
)

data class VariableDto(
    val name: String,
    val unit: String,
    /** Source of the variable (e.g. "battery", "cpu_temp", "system_file").
     *  Mirrors VariableDefinition.source from TimeSeriesFileWriter — kept
     *  identical so backend can route variables by source verbatim. */
    val source: String
)

data class TimeSeriesSampleDto(
    val phase: Int,
    val ts: Long,
    val v: List<Float>
)

/**
 * Footer carries raw scan-wide outputs. Every field is either a
 * raw counter from a stress kernel, a raw flag from the system,
 * or a raw event recorded by the throttler. Nothing here is
 * derived by aggregating samples.
 *
 * The backend computes any aggregate it needs (peak temp, drain
 * rate, phase averages, etc.) by walking the samples list above.
 */
data class TimeSeriesFooterDto(
    /** With no file: `0` — the exact number of samples sent, a fact, not a measurement. */
    val totalSamples: Int,
    /** fieldsVersion 68: nullable (absent with no file); it was `0`. */
    val endTimestamp: Long?,
    /** With no file: `false`, kept as the long-standing declaration that the scan's
     *  footer is not a complete one (the server's reaction to its absence is not
     *  visible from this repository) — `timeSeriesState` is the explicit declaration. */
    val isComplete: Boolean,
    val earlyStopReason: String?,
    /** fieldsVersion 68: nullable (absent with no file); it was `0f`. */
    val maxBatteryTemp: Float?,
    /**
     * LEGACY: present only when the file is a pre-68 one that carries it
     * (the server reads it for fieldsVersion ≤ 67). A ≥ 68 file has
     * [cpuTempFinalC] + [cpuTempFinalSource] instead and this key is absent.
     */
    val maxCpuTemp: Float?,
    /** fieldsVersion 68: nullable (absent with no file); it was `0`. */
    val batteryLevelEnd: Int?,
    val verificationResults: Map<String, Any>?,
    /** fieldsVersion 68: nullable (absent with no file); it was `0f`. */
    val intensityFactor: Float?,
    /** fieldsVersion 68: nullable (absent with no file); it was `""`. */
    val deviceTierMeasured: String?,
    /** fieldsVersion 68: nullable (absent with no file); it was `0f`. */
    val tempRiseRateInProbe: Float?,
    val tempSource: String?,
    val thermalHeadroomAvg: Float?,
    val thermalHeadroomPeak: Float?,
    val thermalStatusMax: Int?,
    val phaseCompletionStatus: Map<String, String>?,
    val throttlingEvents: List<Map<String, Any>>?,
    val batteryCurrentScalingApplied: String?,
    /** V2 fix #54: stress effectiveness diagnostics. AI gates confidence
     *  in benign metrics on `passedSaturationThreshold = true` — false
     *  means the OS governor capped frequency before saturation. */
    val stressEffectiveness: Map<String, Any?>? = null,
    /** V2 fix #56 (2.3): comprehensive battery health snapshot. Design
     *  capacity, current capacity, cycle count, official SoH, charge/
     *  energy counters. Each numeric paired with a source tag. */
    val batteryHealth: Map<String, Any?>? = null,
    /** V2 fix #56 (2.4): per-sensor coverage and source breakdown. */
    val dataQuality: Map<String, Any?>? = null,
    /** V2 fix #56 (3.4): cross-reference of sysfs design vs known model.
     *  Tags like "full_capacity", "likely_single_cell_reading", etc. */
    val knownCapacityClassification: String? = null,
    /** V2 fix #56 follow-up (2026-05-04): build schema version stamp.
     *  AI / backend can log this and reject reports that don't carry
     *  the expected `fieldsVersion`. Zero or absent means the device
     *  shipped a pre-stamp build. Same value across both flavors so a
     *  datacollect baseline carries the same stamp as a launch device. */
    val fieldsVersion: Int? = null,
    /** V2 fix #56 follow-up (2026-05-04): per-field computation status
     *  recorded by StressTestService.completeScan. Values: "ok" or
     *  "fail:<exception class>". Lets the AI tell apart "field missing
     *  because computation threw" from "field missing because
     *  serialization truncated" — the latter requires recovering from
     *  the .footer.json backup file. */
    val footerDiagnostics: Map<String, String>? = null,
    /** V5 schema (Task D): channel-discovery report serialized as a map. */
    val channelDiscovery: Map<String, Any?>? = null,
    /** V5 schema (Task F): per-thermal-channel availability summary. */
    val thermalSensorAvailability: Map<String, Any?>? = null,
    /** V5 schema (Task E): aggregate accel-stillness boolean + stdDev. */
    val scanStillness: Map<String, Any?>? = null,
    // ─────── fieldsVersion 65 (additive) — measurement integrity ───────
    /** Footer fields whose values are compile-time constants, NOT
     *  measurements (e.g. deviceTierMeasured / intensityFactor, constant
     *  since V2 fix #30). Consumers should exclude them from any
     *  device-quality judgement. Null = pre-65 build. */
    val unmeasuredDefaults: List<String>? = null,
    /** Nominal sample count for a complete scan (Σ phase duration ×
     *  sampling rate). Deficit vs totalSamples on a complete scan =
     *  silent sampler loss. Null on V1 / pre-65 builds. */
    val expectedSamples: Int? = null,
    /** SHA-256 of the canonical samples serialisation as written to the
     *  .ts.json.gz (per-sample JSON joined by '\n'). Null = pre-65. */
    val samplesSha256: String? = null,
    /** Buffered-flush failures during the scan (writer retried; finalize
     *  de-duplicated). Null = pre-65 build. */
    val flushFailureCount: Int? = null,
    // ─────── fieldsVersion 68 (additive) — per-device channel layout ───────
    /** The cpufreq policies of the device, raw: `{policy, relatedCpus,
     *  cpuinfoMaxFreqKhz, unreadableReason}` per element, ascending policy
     *  (see CpuPolicyInfo). The payload builder uses GsonBuilder().create()
     *  (no serializeNulls), so inside an element a null field is an ABSENT
     *  key — and `unreadableReason` is present whenever relatedCpus or
     *  cpuinfoMaxFreqKhz is absent. Empty list = discovery found no policy;
     *  absent = discovery did not run / failed, or a pre-68 file. */
    val cpuPolicies: List<Map<String, Any?>>? = null,
    /** Per per-device channel of THIS scan (the names in variables[] after
     *  v[10]): 0-based indices in samples[] whose read failed. v[] holds the
     *  last valid value at those indices — stale, never a measurement; every
     *  reader must leave them out (rule 25). Every per-device channel is a
     *  key (empty list = no failed read); absent = not tracked. At most 200
     *  indices per channel — see [channelReadFailureCounts]. */
    val channelReadFailures: Map<String, List<Int>>? = null,
    /** Per per-device channel: TOTAL failed reads. A count larger than the
     *  channel's list in [channelReadFailures] means that list is truncated
     *  and the channel has further, unlocated, failures. Absent = not tracked. */
    val channelReadFailureCounts: Map<String, Int>? = null,
    // ─────── fieldsVersion 68 (additive) — slow thermal channels as lists ───────
    /** One element per `getThermalHeadroom(0)` call, a call every 10 s from
     *  t≈0: `{t, headroom, state}` — `t` = ms since the scan started (the
     *  frame of v[0]); `headroom` as the platform returned it, ABSENT as a key
     *  (the payload builder has no serializeNulls) whenever the call gave no
     *  number, and then `state` (`nan` · `exception:<message>` ·
     *  `api_below_30` · …) is the explanation. Absent list = not tracked. */
    val thermalHeadroomReadings: List<Map<String, Any?>>? = null,
    /** State of the headroom channel as a whole (`ok` · `api_below_30` ·
     *  `no_reading_taken` · `start_failed:<text>`); present whenever the list is. */
    val thermalHeadroomState: String? = null,
    /** `{t, status}` per thermal-status change; the first element is a read at
     *  listener registration. `status` is the raw 0..6. Absent = not tracked. */
    val thermalStatusEvents: List<Map<String, Any?>>? = null,
    /** `ok` · `api_below_29` · `listener_registration_failed:<text>`; present
     *  whenever the events list is. */
    val thermalStatusState: String? = null,
    // ─────── fieldsVersion 68 (additive) — raw battery pairs ───────
    /** The raw battery pairs taken at each ACTION_BATTERY_CHANGED broadcast:
     *  `{t, voltageRaw, currentRaw, chargeCounterRaw, levelRaw, levelScale,
     *  tempRaw}` — `t` = ms since scan start (the frame of v[0]); every other
     *  field exactly as the platform returned it, ABSENT as a key when that read
     *  was not a value (see batteryPairsState.invalidReads). The first 200 only. */
    val batteryPairs: List<Map<String, Any?>>? = null,
    /** `{receiverRegistered, broadcastCount, droppedByCap, invalidReads}` —
     *  distinguishes "receiver not registered" from "registered, no broadcast"
     *  from "n broadcasts, some beyond the cap". Present whenever batteryPairs is. */
    val batteryPairsState: Map<String, Any?>? = null,
    // ─────── fieldsVersion 68 (additive) — transitional aggregates declared ───────
    /** Names of the footer fields that are on-device aggregates kept beside the
     *  raw for the transitional display (`<object>.<field>` for nested ones),
     *  e.g. thermalHeadroomAvg, batteryHealth.voltageStabilityMv,
     *  verificationResults.criticalDefectDetected. Absent = a pre-68 build. */
    val transitionalAggregates: List<String>? = null,
    /**
     * fieldsVersion 68 (T-10, S-33, N-08): the CPU temperature in °C — a live read
     * taken when the scan was finalised OR the sampler's maximum, the source says
     * which — ABSENT when there was no trustworthy reading (never -1). Replaces
     * maxCpuTemp for ≥ 68 files.
     */
    val cpuTempFinalC: Float? = null,
    /** `live_read_at_finalize` | `sampler_max_cpu` | `absent:<reason>`; with no
     *  time-series file `absent:no_time_series_file`. */
    val cpuTempFinalSource: String? = null,
    /** `read` | `read_failed:<message>` — whether the accepted pre-scan warnings
     *  could be read at the end of the scan. (The warnings list itself is not
     *  in this payload; that is an open contract question, S-22.) Absent =
     *  a pre-68 build. */
    val preScanWarningsState: String? = null,
    /** fieldsVersion 71 (A-P6-13, N71-07): `{minPct, maxPct}` — the battery window the build applied. Absent = a file written before 71. */
    val batteryWindowPct: Map<String, Any?>? = null,
    /** fieldsVersion 71 (A-P6-13, N71-08): `{state, value?, method?}` — what came of holding the screen's brightness. Absent = a file written before 71. */
    val brightnessLock: Map<String, Any?>? = null,
    /**
     * fieldsVersion 68 (batch 3 part 2-b, rules 22 and 25): the names the builder
     * asked the FILE for and did not find — never filled in. Footer fields the
     * file lacked (a reference-typed one Gson left null), and, for a file stamped
     * below 68, every field that only exists since 68 (`verificationResults.<key>`
     * for the verification keys). `[]` = the builder checked and nothing was missing.
     * `null` (absent) only when no file was read.
     */
    val fieldsNotInFile: List<String>? = null
)

data class DeviceInfo(
    val model: String,
    val cpuModel: String,
    val gpuModel: String,
    val androidVersion: String,
    val apiLevel: Int,
    val totalRamGb: String,
    val availableRamGb: String,
    val totalStorageGb: String,
    val availableStorageGb: String,
    val storagePercent: String,
    val securityPatch: String,
    val deviceProfileKey: DeviceProfileKey? = null,
    val buildFingerprint: String? = null,
    val buildBoard: String? = null,
    val buildHardware: String? = null,
    val buildBootloader: String? = null
)

data class FunctionalResultDto(
    val testId: String,
    val componentName: String,
    val categoryName: String,
    val rawValue: String,
    val rawValueUnit: String,
    val status: String,
    val notes: String?,
    /**
     * fieldsVersion 68 (S-29 (a), N-16): compound raw measurements of the
     * test (L-02 satellite numbers, light-sensor reading lists, ...). null =
     * the test supplied none; Gson then OMITS the key (the payload builder
     * uses GsonBuilder().create(), no serializeNulls), so an absent key
     * means "no compound raw", never "measured zero". Forwarded verbatim,
     * no derived value.
     */
    val measurements: Map<String, Any?>? = null
)

/**
 * One row from the manual-tests checklist (M-01..M-22, minus M-13/M-16 which
 * are defined in TestSpec but never rendered).
 *
 * Bug 5 fix (2026-05-17): added [categoryName] alongside the existing
 * `{testId, status}` pair so the AI doesn't have to infer the category from
 * the test ID alone. The diagnosis at docs/audits/bug_5_manual_tests/findings.md
 * showed Xiaomi POCO F6 received camera grade "?" despite 7 PASS manual camera
 * tests because the wire payload told the AI nothing about which category each
 * M-* result belonged to.
 *
 * Nullable + default-null for forward / backward compatibility:
 *  - older APKs that ship without this field still parse via Gson reflection
 *    (the value lands as null on the Backend side, which the V6 prompt's
 *    fallback rule already handles).
 *  - the Backend can roll the matching prompt change at any time after this
 *    APK ships — no coordinated cutover required.
 *
 * The string is the TestCategory's lowercase id ("camera", "display",
 * "wireless", "sensors", "battery", "cpu", "gpu", "storage", "memory",
 * "thermal", "security", "cellular", "gps"). Matches the categoryName field
 * shipped by [FunctionalResultDto] so the AI can union both arrays by category
 * without a translation table.
 */
data class ManualResultDto(
    val testId: String,
    val status: String,
    val categoryName: String? = null,
    /**
     * fieldsVersion 68 (S-29 (a), N-16): compound raw measurements of the
     * manual test (touch-grid raw, ...). Same absence semantics as
     * [FunctionalResultDto.measurements]: null = none supplied, key omitted.
     */
    val measurements: Map<String, Any?>? = null
)

/**
 * fieldsVersion 69 (N-17, contract docs/FV68_CONTRACT.md section 7-h): the header of
 * the manual-results list, sibling of `manualResults` in [BehaviorFingerprint].
 *
 * [hiddenByModel] = the `testId`s the app did NOT ask because the device model does
 * not have the part («ما لا يملكه الطراز لا سجلّ له»): those tests have no row in
 * `manualResults`, and this list is how the server tells "not asked, the model has
 * no such part" from "asked and skipped" (`not_tested`) and "the technician says
 * the device does not have it" (`unavailable`). An EMPTY list is the explicit
 * "nothing was hidden"; the whole header being absent is "this build told us
 * nothing".
 */
data class ManualResultsHeaderDto(
    val hiddenByModel: List<String>
)
