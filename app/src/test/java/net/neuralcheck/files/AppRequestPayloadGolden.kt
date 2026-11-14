package net.neuralcheck.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import net.neuralcheck.BuildConfig
import net.neuralcheck.data.local.db.converter.MeasurementsJson
import net.neuralcheck.data.local.files.ChannelNames
import net.neuralcheck.data.local.files.BatteryPairsRecord
import net.neuralcheck.data.local.files.CpuKnownAnswerRun
import net.neuralcheck.data.local.files.CriticalDefectOutcome
import net.neuralcheck.data.local.files.DataQuality
import net.neuralcheck.data.local.files.GpuGemmOutcome
import net.neuralcheck.data.local.files.KnownAnswerLayout
import net.neuralcheck.data.local.files.MemoryVerifyLayout
import net.neuralcheck.data.local.files.MemoryVerifyOutcome
import net.neuralcheck.data.local.files.PhaseBoundary
import net.neuralcheck.data.local.files.PhaseWindow
import net.neuralcheck.data.local.files.PhaseWindowNames
import net.neuralcheck.data.local.files.PreScanWarningsRead
import net.neuralcheck.data.local.files.PreScanWarningsStates
import net.neuralcheck.data.local.files.StressEffectiveness
import net.neuralcheck.data.local.files.ThrottlingEvent
import net.neuralcheck.data.local.files.ScanChannelLayout
import net.neuralcheck.data.local.files.StorageThroughputSegment
import net.neuralcheck.data.local.files.StorageVerifyLayout
import net.neuralcheck.data.local.files.StorageVerifyOutcome
import net.neuralcheck.data.local.files.StorageWriteErrors
import net.neuralcheck.data.local.files.ThermalSensorAvailability
import net.neuralcheck.data.local.files.TimeSeriesData
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.local.files.TimeSeriesFileWriter
import net.neuralcheck.data.local.files.TimeSeriesFooter
import net.neuralcheck.data.local.files.TimeSeriesHeader
import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.TransitionalAggregates
import net.neuralcheck.data.local.files.UnmeasuredDefaults
import net.neuralcheck.data.local.files.V2Phase
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.data.local.files.VerificationSummary
import net.neuralcheck.data.local.files.WorkersPinOutcome
import net.neuralcheck.data.remote.AnalysisRequestSerializer
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import net.neuralcheck.data.remote.model.BehaviorFingerprint
import net.neuralcheck.data.remote.model.ManualResultsHeaderDto
import net.neuralcheck.domain.manual.ManualTestVisibility
import net.neuralcheck.domain.manual.PxRect
import net.neuralcheck.domain.manual.Reading
import net.neuralcheck.domain.manual.RoundedCornerRadii
import net.neuralcheck.domain.manual.RoundedCorners
import net.neuralcheck.domain.manual.TouchGridContract
import net.neuralcheck.domain.manual.TouchGridRecorder
import net.neuralcheck.domain.manual.TouchGridSurface
import net.neuralcheck.domain.manual.TouchGridTestKit
import net.neuralcheck.domain.manual.VolumeKeyRecorder
import net.neuralcheck.service.stress.BrightnessLock
import net.neuralcheck.service.stress.PowerSaveReading
import net.neuralcheck.service.stress.ScanBatteryWindow
import net.neuralcheck.hardware.gps.GpsTests
import net.neuralcheck.hardware.sensor.CoverPromptChannel
import net.neuralcheck.hardware.sensor.SensorTests
import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.hardware.battery.BatteryIndicators
import net.neuralcheck.service.collector.BatteryCycleCount
import net.neuralcheck.service.collector.BatteryHealthSnapshot
import net.neuralcheck.service.collector.BatteryPairInputs
import net.neuralcheck.service.collector.BatteryPairRecorder
import net.neuralcheck.service.collector.BatteryReadStates
import net.neuralcheck.service.collector.CpuUsageProvenance
import net.neuralcheck.service.collector.ThermalHeadroomRead
import net.neuralcheck.service.collector.ThermalPlatform
import net.neuralcheck.service.collector.ThermalStatusRegistration
import net.neuralcheck.service.sampler.ExtraChannelSampler
import net.neuralcheck.service.sampler.StillnessClassifier
import net.neuralcheck.service.sampler.ThermalListCollector
import net.neuralcheck.service.stress.StressTestService
import net.neuralcheck.service.stress.v2.ChannelDiscoveryProbe
import net.neuralcheck.service.stress.v2.CpuKnownAnswerScheduler
import net.neuralcheck.service.stress.v2.CpuPolicyDiscovery
import net.neuralcheck.service.stress.v2.FakeNativeVerification
import net.neuralcheck.util.ChannelRead
import net.neuralcheck.util.DeviceProfileKey
import net.neuralcheck.util.FileTextRead
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt

/**
 * The golden APP REQUEST PAYLOAD generator (fieldsVersion 68, batch 3 part 3;
 * contract section 3 item 7, rules 37, 39, 41): what the app sends to the
 * `analyzeDevice` callable, pushed through the REAL chain with no layer of it
 * replaced —
 *
 *   real TimeSeriesFileWriter (Context mocked for `filesDir` ONLY)
 *     -> a real `.ts.json.gz`
 *     -> real TimeSeriesFileReader
 *     -> real BehaviorFingerprintBuilder.build
 *     -> [AnalysisRequestSerializer.fingerprintToMap] — the very function
 *        FirebaseAnalysisService.analyzeDevice calls (Gson -> Map<String, Any>).
 *
 * Everything that is NOT produced by its real producer is named in
 * `app/src/test/resources/derived/README.md` (the modifications list) and the
 * lines of this file say it too: every value below that is scripted carries a
 * comment starting `SCRIPTED:`, every value that is a real raw number starts
 * `REAL:`, and a call to a production helper starts `PRODUCER:`.
 *
 * What this cannot reach (the real DisciplinedSampler, StressTestService.completeScan,
 * the native kernels, sysfs, the platform) is stage 2 of contract section 3 item 7:
 * a real device file compared key-set to key-set with these samples.
 */
object AppRequestPayloadGolden {

    /** The real raw source: byte-for-byte a server fixture, never edited (rule 9). */
    const val REAL_SOURCE_RESOURCE =
        "/derived/fv67-golden-1.0.8-v6beta-from-neuralcheck-backend-19fc7bc-34dff2b8.ts.json"
    const val REAL_SOURCE_NAME = "fv67-golden-1.0.8-v6beta-from-neuralcheck-backend-19fc7bc-34dff2b8.ts.json"
    const val REAL_SOURCE_SHA256 = "e451306b31e74b6c77d6513989341cfe7a5f0408ed2119bcc085bf251b1720d3"

    // fieldsVersion 71 (A-M02): renamed from "fv70-..." — the samples now carry footer.fieldsVersion 71 and M-02's new grid raw; the fv70 files stay in git
    // history at 077bbf2 (the fv69 ones at c22251f, the fv68 ones at 99c0548).
    const val FILE_PREFIX = "fv71-app-request-payload-derived-from-fv67-golden-34dff2b8-"

    /**
     * SCRIPTED: `header.appVersion`. A literal, NOT BuildConfig.VERSION_NAME, so a
     * version bump is not a golden change. 1.0.11 is the version the legacy-file
     * test (LegacyFv67FileTest) names as the fv68 update target.
     */
    const val GENERATED_APP_VERSION = "1.0.11"

    /** Decimal MB/s (÷10^6, a 1.0.8 file) to binary MiB/s (÷2^20, what an fv68 file declares). */
    private const val MB_TO_MIB = 1_000_000.0 / 1_048_576.0

    // ─────────────────────────── scenarios ───────────────────────────

    /** One cpufreq policy of a scripted device. */
    data class PolicySpec(
        val n: Int,
        val related: String,
        val maxKhz: Long,
        /** `scaling_cur_freq` refuses at discovery (EACCES): the policy is reported, the channel not admitted. */
        val curBlocked: Boolean = false,
        /** `cpuinfo_max_freq` refuses (EACCES): the topology record carries `unreadableReason`. */
        val maxBlocked: Boolean = false,
        /** 0-based sample rows at which the per-sample read of this policy fails. */
        val failRows: Set<Int> = emptySet()
    )

    /** What the fake platform answers to the thermal collector (the OS is what is scripted, not the collector). */
    class ThermalScenario(
        val sdkInt: Int = 30,
        val headroom: (call: Int) -> ThermalHeadroomRead,
        val statusAtStart: Int = 0,
        /** (ms since scan start, status) callbacks, ascending. */
        val callbacks: List<Pair<Long, Int>> = emptyList(),
        val registerFails: Exception? = null
    )

    class KnownAnswerPlan(
        /** 1-based sieve call whose native result is a MISMATCH (a different prime count). */
        val mismatchAtSieveCall: Int? = null,
        /** 1-based sieve call that THROWS. */
        val throwAtSieveCall: Int? = null,
        /**
         * 1-based sieve call -> the ms of virtual clock that call spends (default [SIEVE_MS]). A slow first run
         * pushes the REAL scheduler over its time budget (fv70) so its later runs are `skipped:time_budget`.
         */
        val sieveMsAtCall: Map<Int, Long> = emptyMap()
    )

    /** Which GPU / AI / pinning story the scan tells. */
    class RuntimeStory(
        val gpuAttempts: Int = 1,
        val gpuAnyFailed: Boolean = false,
        val gpuGovernorCapped: Boolean = false,
        val aiActiveDelegate: String = "CPU",
        val aiDelegateInitError: String? = null,
        val cpuStressFallbackUsed: Boolean = false,
        /** Pin counters (big, mid, little, failedBig, failedMid, failedLittle); null = no pin attempt. */
        val pins: List<Int>? = null
    )

    class Variant(
        val id: String,
        val title: String,
        /** Whether the platform serves BATTERY_PROPERTY_CHARGE_COUNTER (discovery, the snapshot, the pairs). */
        val chargeSupported: Boolean,
        val chargeFailRows: Set<Int>,
        val cpuCount: Int,
        val policies: List<PolicySpec>,
        val thermal: ThermalScenario,
        val firstBroadcastMs: Long,
        val broadcastPeriodMs: Long,
        val invalidCurrentAtBroadcasts: Set<Int>,
        val preScanRead: Result<PreScanWarningsRead>,
        val liveCpuC: Float,
        val samplerMaxCpuC: Float,
        val samplerTempSource: String,
        val memoryNative: LongArray,
        val memoryTimeCapMs: Long,
        val storageNative: LongArray,
        val storageFailedSyncs: Int,
        val knownAnswer: KnownAnswerPlan,
        val story: RuntimeStory,
        /** The scan is stopped (by the user) at this % of COMBINED_PEAK: later rows do not exist, the checks of phase 5 stop with it. */
        val earlyStopDuringPhase5Pct: Int? = null,
        val throttlingEvents: List<ThrottlingEvent>? = null,
        val stressEffectiveness: (StressEffectiveness) -> StressEffectiveness = { it },
        val dataQuality: (DataQuality) -> DataQuality = { it },
        /**
         * The platform API level of the scripted device: `header.androidApiLevel`, `device.apiLevel` /
         * `androidVersion`, the thermal platform's SDK and the cycle-count reader's gate. The real source is API 30.
         */
        val apiLevel: Int = 30,
        /** What the battery broadcast carries as `EXTRA_CYCLE_COUNT` (null = the extra is absent). Read only on API >= 34. */
        val cycleExtra: Int? = null,
        /** What the sysfs cycle_count file answers (null = unreadable or absent). */
        val cycleSysfs: Int? = null,
        /**
         * The manual-test hiding rules this scripted device runs (fieldsVersion 69, N-17). null = the production
         * table, [ManualTestVisibility.RULES] — EMPTY today, so `manualResultsHeader.hiddenByModel` is `[]`.
         */
        val hideRules: List<ManualTestVisibility.HideRule>? = null,
        /** How the scripted technician ran the M-02 touch grid (fieldsVersion 69, N-19). */
        val touchGrid: TouchGridScript = TouchGridScript.FULL_AUTO,
        /** How the scripted technician ran the two volume-button tests M-09 and M-10 (fieldsVersion 71, A-VOL). */
        val volumeRuns: VolumeScript = VolumeScript.DETECTED_BOTH_WAYS,
        /**
         * What the scripted phone answers when the scan start asks `PowerManager.isPowerSaveMode` (fieldsVersion 71, A-P6-13): the REAL [PowerSaveReading] turns it into
         * `powerSaveState` and the boolean (a throw is `read_failed` and no boolean).
         */
        val powerSaveRead: () -> Boolean? = { false },
        /** The brightness the scan screen is asked to hold (fieldsVersion 71): `BrightnessLock.VALUE` is null in production — nothing is attempted. */
        val brightnessValue: Float? = BrightnessLock.VALUE,
        /** What the scripted scan window does when the real [BrightnessLock.lock] sets the attribute (a throw is `apply_failed:<message>`). */
        val brightnessWindow: (Float) -> Unit = { },
        /** How the scripted technician treated the light sensor in SN-04 (fieldsVersion 69, N-20). */
        val lightCover: LightCoverScript = LightCoverScript.COVERED
    )

    /** The three cases of the contract's SN-04 table that a sample can show (the fourth, "no readings", is a unit test). */
    enum class LightCoverScript { COVERED, NOT_COVERED, ALL_ZERO }

    /**
     * The three runs of M-02 the samples show (fieldsVersion 71): a complete sweep that passes by itself; a partial sweep with a camera cut-out, the three marks
     * the technician saw, a palm and a button press (fail); and a run that reached the 15 000 point cap and was ended by «لم أختبر».
     */
    enum class TouchGridScript { FULL_AUTO, PARTIAL_NOT_WORKING, CAPPED_NOT_TESTED }

    /**
     * The three pairs of volume runs the samples show (fieldsVersion 71): M-09 detected by its key event and M-10 detected by the level moving with no key event
     * reaching the dialog; M-09 claimed by the technician's «يعمل» after a wrong key and M-10 marked «لا يعمل»; M-09 marked «لم أختبر» on a stream with no room
     * to set a mid level and M-10 dismissed after a wrong key. Together: every `endReason` and both `detectionSource` values.
     */
    enum class VolumeScript { DETECTED_BOTH_WAYS, CLAIMED_AND_FAILED, NOT_TESTED_AND_DISMISSED }

    private const val GIB = 1024L * 1024 * 1024
    private const val MIB = 1024L * 1024
    private val MEMORY_TILE = 256L * MIB

    private fun memoryResult(
        status: Long, tilesCompleted: Long, tilesTotal: Long, march: Long = 0, walking: Long = 0, random: Long = 0,
        elapsedMs: Long, freeBytes: Long
    ) = longArrayOf(
        MemoryVerifyLayout.VERSION, MemoryVerifyLayout.SIZE.toLong(), status, 0L,
        tilesTotal * MEMORY_TILE, tilesTotal * MEMORY_TILE, MEMORY_TILE, tilesTotal, tilesCompleted,
        march, walking, random, elapsedMs, freeBytes
    )

    private fun storageResult(
        status: Long, method: Long = StorageVerifyLayout.METHOD_O_DIRECT, fallbackStage: Long = 0, fallbackErrno: Long = 0,
        planned: Long = 256L * MIB, compared: Long = 256L * MIB, mismatched: Long = 0, ioErrors: Long = 0, elapsedMs: Long
    ) = longArrayOf(
        StorageVerifyLayout.VERSION, StorageVerifyLayout.SIZE.toLong(), status, method, fallbackStage, fallbackErrno, 0L,
        planned, compared, mismatched, ioErrors, elapsedMs
    )

    /** SCRIPTED: the thermal headroom of an ordinary scan: ramps to the real peak (1.0266666) at tick 36, then eases. */
    private fun rampHeadroom(call: Int, realPeak: Float): ThermalHeadroomRead {
        val v = if (call <= 36) 0.60f + (realPeak - 0.60f) * call / 36f else realPeak - 0.35f * (call - 36) / 12f
        return ThermalHeadroomRead(v, "ok")
    }

    private fun enoent(path: String) = FileTextRead.Failed("$path: open failed: ENOENT (No such file or directory)")
    private fun eacces(path: String) = FileTextRead.Failed("$path: open failed: EACCES (Permission denied)")

    private const val REAL_PEAK_HEADROOM = 1.0266666f

    /** SCRIPTED: the three layouts. See README.md in derived/ for why these three. */
    val VARIANTS: List<Variant> by lazy {
        val a = Variant(
            id = "11ch-nothing-admitted",
            title = "11 columns: discovery admitted no per-device channel (charge counter unsupported, no cpufreq policy readable)",
            chargeSupported = false, chargeFailRows = emptySet(),
            cpuCount = 8, policies = emptyList(),
            thermal = ThermalScenario(headroom = { rampHeadroom(it, REAL_PEAK_HEADROOM) },
                callbacks = listOf(95_000L to 1, 190_000L to 2, 285_000L to 3, 400_000L to 2)),
            firstBroadcastMs = 2_000L, broadcastPeriodMs = 20_000L, invalidCurrentAtBroadcasts = emptySet(),
            preScanRead = Result.success(PreScanWarningsRead(written = true, warnings = emptyList())),
            liveCpuC = -1f, samplerMaxCpuC = -1f, samplerTempSource = "headroom",
            memoryNative = memoryResult(MemoryVerifyLayout.STATUS_RAN, 12, 12, elapsedMs = 9_800, freeBytes = 5_900_000_000L),
            memoryTimeCapMs = 30_000L,
            storageNative = storageResult(StorageVerifyLayout.STATUS_RAN, elapsedMs = 900),
            storageFailedSyncs = 0,
            // SCRIPTED (fv70): phase 3's first run takes 380 ms in the sieve + 30 ms in the SHA = 410 ms; the REAL scheduler's time
            // budget (1 % of the planned 75 000 ms = 750 ms) then says (410 + 410) x 100 > 75 000, so runs 2 and 3 of phase 3 are
            // `skipped:time_budget`; phase 5 runs its three at the default speed. The state is `partial:time_budget`.
            knownAnswer = KnownAnswerPlan(sieveMsAtCall = mapOf(1 to 380L)),
            story = RuntimeStory(pins = null),
            // SCRIPTED: a device whose /proc/stat per-CPU usage was readable (the real source's S10 never had it), so
            // perCpuUsageAvailable is true and the three per-cluster usage averages are present.
            stressEffectiveness = {
                it.copy(
                    perCpuUsageAvailable = true,
                    phase3CpuUsageAvgBig = 88.1f, phase3CpuUsageAvgMid = 86.4f, phase3CpuUsageAvgLittle = 12.3f
                )
            },
            dataQuality = {
                it.copy(
                    procStatAvailable = CpuUsageProvenance.procStatEverUsed(-1),          // PRODUCER
                    cpuUsageSource = CpuUsageProvenance.sourceLabel(readerStillOnProcStat = true),   // PRODUCER
                    cpuUsageFallbackFromSample = -1
                )
            }
        )
        val b = Variant(
            id = "13ch-selinux-blocked-policy",
            title = "13 columns: charge counter + policy 0 admitted; policy 4 refused by SELinux (visible only in channelDiscovery and cpuPolicies, not in v[])",
            chargeSupported = true, chargeFailRows = setOf(100, 101, 102, 900),
            cpuCount = 8,
            policies = listOf(
                PolicySpec(0, "0 1 2 3", 1_950_000L),
                PolicySpec(4, "4 5 6 7", 2_730_000L, curBlocked = true, maxBlocked = true)
            ),
            apiLevel = 34, cycleExtra = 187,   // SCRIPTED: an API-34 device whose broadcast carries EXTRA_CYCLE_COUNT = 187
            // SCRIPTED: the production table is empty (the signal per test is a user decision), so this sample shows the
            // SHAPE with two hidden tests — M-14 and M-15 have no row in manualResults, only a name in the header.
            touchGrid = TouchGridScript.PARTIAL_NOT_WORKING,   // SCRIPTED: rows 0-17 swept with two jumps and a palm, the technician presses «لا يعمل»
            volumeRuns = VolumeScript.CLAIMED_AND_FAILED,
            brightnessValue = 0.5f,   // SCRIPTED: a build that had decided on a half brightness — shows the shape of `applied` (production attempts nothing today)
            lightCover = LightCoverScript.NOT_COVERED,          // SCRIPTED: the technician did not cover the sensor
            hideRules = listOf(
                ManualTestVisibility.HideRule("M-15", "SCRIPTED: golden demonstration, not a production rule") { true },
                ManualTestVisibility.HideRule("M-14", "SCRIPTED: golden demonstration, not a production rule") { true }
            ),
            thermal = ThermalScenario(
                sdkInt = 34,
                headroom = { call ->
                    when (call) {
                        7 -> ThermalHeadroomRead(null, "nan")
                        20 -> ThermalHeadroomRead(null, "exception:getThermalHeadroom: too many calls (rate limited)")
                        else -> rampHeadroom(call, REAL_PEAK_HEADROOM)
                    }
                },
                callbacks = listOf(95_000L to 1, 190_000L to 2, 285_000L to 3)
            ),
            firstBroadcastMs = 2_000L, broadcastPeriodMs = 20_000L, invalidCurrentAtBroadcasts = setOf(5),
            preScanRead = Result.success(PreScanWarningsRead(written = false, warnings = emptyList())),
            liveCpuC = 52.0f, samplerMaxCpuC = 60.0f, samplerTempSource = "cpu",
            memoryNative = memoryResult(MemoryVerifyLayout.STATUS_PARTIAL_TIME_CAP, 5, 12, elapsedMs = 30_000, freeBytes = 5_900_000_000L),
            memoryTimeCapMs = 30_000L,
            storageNative = storageResult(
                StorageVerifyLayout.STATUS_RAN, method = StorageVerifyLayout.METHOD_DONTNEED_FALLBACK,
                fallbackStage = StorageVerifyLayout.FALLBACK_STAGE_OPEN, fallbackErrno = 22, elapsedMs = 2_400
            ),
            storageFailedSyncs = 2,
            knownAnswer = KnownAnswerPlan(mismatchAtSieveCall = 5),
            story = RuntimeStory(pins = null),
            // SCRIPTED: one recorded throttling event (the real source has none).
            throttlingEvents = listOf(
                ThrottlingEvent(
                    timestamp = 200_000L, phase = 3, skinTemp = 46.5f, newIntensity = 0.8f, reason = "skin>45",
                    sourceIsSysfs = false, applied = false
                )
            ),
            // SCRIPTED: 3 of 4 saturation conditions: satCaveat present, saturationFailureReason absent.
            stressEffectiveness = {
                it.copy(
                    satC4Passed = false, satConditionsMet = 3, satGrade = "effectively_saturated",
                    satCaveat = "scripted: peak frequency stayed below 85 % of nominal"
                )
            }
        )
        val c = Variant(
            id = "16ch-four-policies-truncated-failures",
            touchGrid = TouchGridScript.CAPPED_NOT_TESTED,   // SCRIPTED: one finger scribbling past the 15 000 point cap, the technician presses «لم أختبر»
            volumeRuns = VolumeScript.NOT_TESTED_AND_DISMISSED,
            powerSaveRead = { throw IllegalStateException("the power service refused the read") },   // SCRIPTED: read_failed, so no isPowerSaveMode / powerSaveMode key
            brightnessValue = 0.4f,                                                               // SCRIPTED: and a window that refuses the attribute: apply_failed
            brightnessWindow = { throw IllegalStateException("the window refused the brightness attribute") },
            lightCover = LightCoverScript.ALL_ZERO,        // SCRIPTED: every reading is 0
            title = "16 columns: charge counter + four policies; one policy's failed reads exceed the 200-index cap (list truncated, count larger)",
            chargeSupported = true, chargeFailRows = emptySet(),
            cpuCount = 8,
            policies = listOf(
                PolicySpec(0, "0 1", 1_785_600L),
                PolicySpec(2, "2 3", 2_054_400L, failRows = (300..529).toSet()),
                PolicySpec(4, "4 5", 2_419_200L, maxBlocked = true),
                PolicySpec(6, "6 7", 2_730_000L, failRows = setOf(5, 800, 1200))
            ),
            apiLevel = 34, cycleExtra = null, cycleSysfs = 212,   // SCRIPTED: API 34, no extra in the broadcast, sysfs answers 212
            thermal = ThermalScenario(
                sdkInt = 34,
                headroom = { call ->
                    if (call % 9 == 4) ThermalHeadroomRead(null, "exception:getThermalHeadroom: PowerManager unavailable")
                    else rampHeadroom(call, REAL_PEAK_HEADROOM)
                },
                statusAtStart = 1,
                registerFails = SecurityException("no thermal listener for this uid")
            ),
            // SCRIPTED: a broadcast every second, so the recorder reaches its 200-pair cap (droppedByCap > 0).
            firstBroadcastMs = 2_000L, broadcastPeriodMs = 1_000L, invalidCurrentAtBroadcasts = setOf(6),
            preScanRead = Result.failure(IllegalStateException("the pre-scan holder was already drained")),
            liveCpuC = -1f, samplerMaxCpuC = 58.5f, samplerTempSource = "cpu",
            memoryNative = memoryResult(
                MemoryVerifyLayout.STATUS_INSUFFICIENT_FREE_MEMORY, 0, 0, elapsedMs = 3, freeBytes = 440L * MIB
            ),
            memoryTimeCapMs = 30_000L,
            storageNative = storageResult(
                StorageVerifyLayout.STATUS_PARTIAL_TIME_CAP, compared = 100L * MIB, elapsedMs = 3_000
            ),
            storageFailedSyncs = 0,
            knownAnswer = KnownAnswerPlan(throwAtSieveCall = 2),
            story = RuntimeStory(
                gpuAttempts = 0, aiActiveDelegate = "STUB",
                aiDelegateInitError = "UnsatisfiedLinkError: dlopen failed: library \"libtensorflowlite_jni.so\" not found",
                cpuStressFallbackUsed = true, pins = listOf(2, 3, 2, 0, 0, 1)
            ),
            earlyStopDuringPhase5Pct = 70,
            // SCRIPTED: a partially saturated scan (2 of 4 conditions), so satCaveat is absent and saturationFailureReason present.
            stressEffectiveness = {
                it.copy(
                    satC3Passed = false, satC4Passed = false, satConditionsMet = 2, satGrade = "partial_saturation",
                    passedSaturationThreshold = false, saturationFailureReason = "scripted: two of four saturation conditions held"
                )
            }
        )
        listOf(a, b, c)
    }

    fun fileName(v: Variant) = "$FILE_PREFIX${v.id}.json"
    fun keysFileName(v: Variant) = "$FILE_PREFIX${v.id}.keys.txt"

    // ─────────────────────────── the build ───────────────────────────

    class Built(
        val variant: Variant,
        val data: TimeSeriesData,
        val fingerprint: BehaviorFingerprint,
        /** What the callable receives as `fingerprint` (AnalysisRequestSerializer.fingerprintToMap). */
        val payload: Map<String, Any>,
        /** The footer as the app handed it to the writer (before the file) — what was hand-built is visible here. */
        val handedFooter: TimeSeriesFooter
    ) {
        val text: String by lazy { GoldenJson.canonicalText(payload) }
    }

    private fun contextFor(dir: File): Context = mock { on { filesDir } doReturn dir }

    /** REAL: the fv67 raw file, read with the real reader. */
    fun readRealSource(workDir: File): TimeSeriesData {
        val bytes = AppRequestPayloadGolden::class.java.getResourceAsStream(REAL_SOURCE_RESOURCE)!!.readBytes()
        val file = File(workDir, "real-source.ts.json.gz")
        GZIPOutputStream(file.outputStream()).use { it.write(bytes) }
        return runBlocking { TimeSeriesFileReader(contextFor(workDir)).readFromPath(file.absolutePath) }
            ?: error("the real fv67 source must read")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun build(variant: Variant, workDir: File): Built {
        val realData = readRealSource(File(workDir, "real").also { it.mkdirs() })
        val g = realData.footer
        val h = realData.header
        val allRows = realData.samples
        check(allRows.size == 1606 && realData.variables.size == 11) { "the real source changed shape: ${allRows.size} rows" }
        val fullExtent: Map<Int, Pair<Long, Long>> = allRows.groupBy { it.phase }.mapValues { (_, r) ->
            r.minOf { it.v[0] }.toLong() to r.maxOf { it.v[0] }.toLong()
        }
        // SCRIPTED: an early stop (the user cancelled) cuts the REAL rows at a point inside COMBINED_PEAK.
        val stopAtMs: Long? = variant.earlyStopDuringPhase5Pct?.let { pct ->
            val (start, end) = fullExtent.getValue(V2Phase.COMBINED_PEAK.phaseId)
            start + (end - start) * pct / 100
        }
        val rows = if (stopAtMs == null) allRows else allRows.filter { it.v[0] <= stopAtMs }
        val bigMaxKhz = g.stressEffectiveness.peakBigFreqKhzAnywhere
            ?: error("REAL: the source footer must carry stressEffectiveness.peakBigFreqKhzAnywhere")
        val lastRow = rows.size - 1
        val scanEndMs = rows.last().v[0].toLong()

        // REAL: phase extents in the v[0] frame, from the real rows.
        val phaseExtent: Map<Int, Pair<Long, Long>> = rows.groupBy { it.phase }.mapValues { (_, r) ->
            r.minOf { it.v[0] }.toLong() to r.maxOf { it.v[0] }.toLong()
        }

        // PRODUCER: the memory outcome, from a SCRIPTED native array.
        val memoryOutcome = MemoryVerifyOutcome.fromNative(variant.memoryNative, variant.memoryTimeCapMs)
        // SCRIPTED: the two sub-windows of MEMORY_STRESS (kernel first, then the workload).
        val memStart = phaseExtent.getValue(V2Phase.MEMORY_STRESS.phaseId).first
        val memEnd = phaseExtent.getValue(V2Phase.MEMORY_STRESS.phaseId).second
        val kernelEnd = memStart + (memoryOutcome.durationMs ?: 0L)
        val memoryWindows = listOf(
            PhaseWindow(PhaseWindowNames.MEMORY_KERNEL, memStart, kernelEnd),
            PhaseWindow(PhaseWindowNames.MEMORY_WORKLOAD, kernelEnd, memEnd)
        )

        // ── channel discovery: PRODUCERS over a SCRIPTED sysfs ──
        var currentRow = 0
        fun policySpec(n: Int) = variant.policies.first { it.n == n }
        // SCRIPTED: a policy's frequency = the REAL cpu_freq_max_khz column scaled by (its max / the real big-cluster peak), floored to MHz.
        fun scaledKhz(n: Int, row: Int): Long = rows[row].v[5].toLong() * policySpec(n).maxKhz / bigMaxKhz / 1000 * 1000
        // SCRIPTED: charge counter = REAL footer chargeCounterUah falling by the REAL level drop (5 %) of the REAL design capacity (4400 mAh).
        fun chargeAt(row: Int): Long = 1_711_600L - 220_000L * row / (allRows.size - 1)

        val sysfs = HashMap<String, FileTextRead>()
        for (p in variant.policies) {
            val dir = "${CpuPolicyDiscovery.BASE_PATH}/policy${p.n}"
            sysfs["$dir/scaling_cur_freq"] =
                if (p.curBlocked) eacces("$dir/scaling_cur_freq") else FileTextRead.Text(scaledKhz(p.n, 0).toString())
            sysfs["$dir/related_cpus"] = FileTextRead.Text(p.related)
            sysfs["$dir/cpuinfo_max_freq"] =
                if (p.maxBlocked) eacces("$dir/cpuinfo_max_freq") else FileTextRead.Text(p.maxKhz.toString())
        }
        val policies = CpuPolicyDiscovery.discover(variant.cpuCount) { path -> sysfs[path] ?: enoent(path) }   // PRODUCER

        // The text BatteryReader.readChargeCounterRaw writes for the unsupported marker (BatteryReader.kt readChargeCounterRaw).
        val chargeUnsupportedText = "getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER) returned Long.MIN_VALUE " +
            "(the platform's unsupported marker)"
        val chargeAtDiscovery: ChannelRead =
            if (variant.chargeSupported) ChannelRead.Value(chargeAt(0).toFloat()) else ChannelRead.Failed(chargeUnsupportedText)
        val readCharge: () -> ChannelRead = {
            when {
                !variant.chargeSupported -> ChannelRead.Failed(chargeUnsupportedText)
                currentRow in variant.chargeFailRows ->
                    ChannelRead.Failed("binder died during getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER)")
                else -> ChannelRead.Value(chargeAt(currentRow).toFloat())
            }
        }
        val curFreqPath = Regex("policy(\\d+)/scaling_cur_freq\$")
        val readText: (String) -> FileTextRead = { path ->
            val n = curFreqPath.find(path)?.groupValues?.get(1)?.toInt()
                ?: error("the scan reads only scaling_cur_freq per sample, not $path")
            if (currentRow in policySpec(n).failRows) FileTextRead.Failed("$path: read failed: EIO (Input/output error)")
            else FileTextRead.Text(scaledKhz(n, currentRow).toString())
        }

        // SCRIPTED: the outcome of every base channel but the charge counter, matching the REAL source's channelDiscovery
        // (available: battery temp / voltage / current, cpu freq, cpu usage, headroom, status; missing: the rest).
        val scriptedBase: Map<String, ChannelDiscoveryProbe.ProbeOutcome> = mapOf(
            ChannelNames.CPU_TEMP_C to ChannelDiscoveryProbe.ProbeOutcome.noValue("readCpuTemp", "-1.0 (not > 0)"),
            ChannelNames.GPU_TEMP_C to ChannelDiscoveryProbe.ProbeOutcome.noValue("readGpuTemp", "-1.0 (not > 0)"),
            ChannelNames.SKIN_TEMP_C_REAL to ChannelDiscoveryProbe.ProbeOutcome(
                ChannelDiscoveryProbe.ProbeResult.MISSING,
                "no native skin sensor: ThermalFallback reading=32.8, fromSysfs=false (a battery proxy is not a skin sensor)"
            ),
            ChannelNames.BATTERY_TEMP_C to ChannelDiscoveryProbe.ProbeOutcome.AVAILABLE,
            ChannelNames.BATTERY_VOLTAGE_MV to ChannelDiscoveryProbe.ProbeOutcome.AVAILABLE,
            ChannelNames.BATTERY_CURRENT_UA to ChannelDiscoveryProbe.ProbeOutcome.AVAILABLE,
            ChannelNames.CPU_FREQ_MAX_KHZ to ChannelDiscoveryProbe.ProbeOutcome.AVAILABLE,
            ChannelNames.CPU_USAGE_PCT to ChannelDiscoveryProbe.ProbeOutcome.AVAILABLE,
            ChannelNames.THERMAL_HEADROOM to ChannelDiscoveryProbe.ProbeOutcome.AVAILABLE,
            ChannelNames.THERMAL_STATUS to ChannelDiscoveryProbe.ProbeOutcome.AVAILABLE,
            ChannelNames.ENERGY_COUNTER to ChannelDiscoveryProbe.ProbeOutcome.noValue(
                "snapshotHealth().energyCounterNwh", "null (null or 0)"
            )
        )
        val baseOutcomes = LinkedHashMap<String, ChannelDiscoveryProbe.ProbeOutcome>()
        for (channel in ChannelDiscoveryProbe.CHANNELS) {
            baseOutcomes[channel] = when (channel) {
                ChannelNames.CHARGE_COUNTER_RAW -> ChannelDiscoveryProbe.outcomeOf(chargeAtDiscovery)   // PRODUCER
                else -> scriptedBase[channel]
                    ?: error("no scripted discovery outcome for base channel '$channel': the production list gained a channel")
            }
        }
        val discovery = ChannelDiscoveryProbe.assemble(baseOutcomes, chargeAtDiscovery, readCharge, policies, readText)   // PRODUCER
        val layout: ScanChannelLayout = discovery.layout

        // ── the sample rows: REAL v[0..10] (units fixed, kernel window zeroed) + SCRIPTED per-device columns via the PRODUCER sampler ──
        val sampler = ExtraChannelSampler(layout.extras)
        val written = ArrayList<TimeSeriesSample>(rows.size)
        for ((i, r) in rows.withIndex()) {
            currentRow = i
            val values = FloatArray(layout.sampleWidth)
            for (c in 0 until V2_SAMPLE_VAR_COUNT) values[c] = r.v[c]
            // MODIFIED: the real file is 1.0.8 (decimal MB/s); an fv68 file declares MiB/s for v[8] and v[9].
            values[8] = (r.v[8] * MB_TO_MIB).toFloat()
            values[9] = (r.v[9] * MB_TO_MIB).toFloat()
            // MODIFIED: while the memory kernel runs nothing publishes v[9] (the real file predates the kernel).
            if (r.phase == V2Phase.MEMORY_STRESS.phaseId && r.v[0].toLong() in memStart until kernelEnd) values[9] = 0f
            sampler.fill(values, V2_SAMPLE_VAR_COUNT, i)   // PRODUCER
            written += TimeSeriesSample(ts = r.ts, phase = r.phase, v = values)
        }
        val failures = sampler.failures()   // PRODUCER

        // ── PRODUCER: the slow thermal lists, the real collector over a SCRIPTED platform ──
        val thermal = variant.thermal
        val scanStart = 1_000_000L
        val clock = AtomicLong(scanStart)
        val platform = object : ThermalPlatform {
            var calls = 0
            var onChanged: ((Int) -> Unit)? = null
            override val sdkInt: Int = thermal.sdkInt
            override fun readHeadroom(): ThermalHeadroomRead = thermal.headroom(calls++)
            override fun currentStatus(): Int = thermal.statusAtStart
            override fun registerStatusListener(executor: Executor, onChanged: (Int) -> Unit): ThermalStatusRegistration {
                thermal.registerFails?.let { throw it }
                this.onChanged = onChanged
                return ThermalStatusRegistration { }
            }
        }
        val collector = ThermalListCollector(platform) { clock.get() }
        collector.startWithoutScheduling(scanStart)
        val pending = thermal.callbacks.toMutableList()
        var tickAt = 0L
        while (tickAt <= scanEndMs - 8_000L) {
            while (pending.isNotEmpty() && pending.first().first <= tickAt) {
                val (at, status) = pending.removeAt(0)
                clock.set(scanStart + at)
                platform.onChanged?.invoke(status)
            }
            clock.set(scanStart + tickAt)
            collector.tickHeadroom()
            tickAt += ThermalListCollector.HEADROOM_PERIOD_MS
        }
        val thermalLists = collector.snapshot()

        // ── PRODUCER: the raw battery pairs, the real recorder fed SCRIPTED broadcasts whose values come from the REAL rows ──
        val recorder = BatteryPairRecorder()
        recorder.start(0L)
        var broadcastAt = variant.firstBroadcastMs
        var broadcastNo = 0
        while (broadcastAt <= scanEndMs) {
            val idx = rows.indexOfFirst { it.v[0] >= broadcastAt }.let { if (it < 0) lastRow else it }
            val row = rows[idx]
            recorder.onBroadcast(
                broadcastAt,
                BatteryPairInputs(
                    voltageRaw = row.v[2].toInt(),
                    // REAL v[3] is the file's current AFTER the declared x1000_no_flip scaling; the pair holds it before.
                    currentRaw = if (broadcastNo in variant.invalidCurrentAtBroadcasts) null else (row.v[3] / 1000f).roundToInt(),
                    chargeCounterRaw = if (variant.chargeSupported) chargeAt(idx) else null,
                    levelRaw = row.v[4].toInt(),
                    levelScale = 100,
                    tempRaw = (row.v[1] * 10f).roundToInt()
                )
            )
            broadcastAt += variant.broadcastPeriodMs
            broadcastNo++
        }
        val batteryPairs: BatteryPairsRecord = recorder.finish(receiverRegistered = true)
            ?: error("the recorder was started, so it has a record")

        // ── PRODUCER: the CPU known-answer checks, the real scheduler over a SCRIPTED native layer on virtual time ──
        val knownAnswer = runKnownAnswers(
            variant.knownAnswer,
            phase3Start = phaseExtent.getValue(V2Phase.CPU_SUSTAINED.phaseId).first,
            phase5Start = phaseExtent.getValue(V2Phase.COMBINED_PEAK.phaseId).first,
            phase5CutMs = stopAtMs
        )

        // ── PRODUCER: battery health from the REAL rows and a snapshot hand-built from the REAL source footer ──
        val sourceHealth = g.batteryHealth ?: error("REAL: the source footer must carry batteryHealth")
        // PRODUCER: the one cycle-count reader (T-02) over a MOCKED battery broadcast (a JVM cannot build a real Intent)
        // and a scripted sysfs answer; the S10 of the real source (API 30) reads `unavailable`, as its real footer did.
        val cycleIntent: Intent = mock {
            on { hasExtra(BatteryManager.EXTRA_CYCLE_COUNT) } doReturn (variant.cycleExtra != null)
            if (variant.cycleExtra != null) {
                on { getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, BatteryCycleCount.EXTRA_DEFAULT) } doReturn variant.cycleExtra
            }
        }
        val cycle = BatteryCycleCount.read(cycleIntent, variant.apiLevel) { variant.cycleSysfs }
        val snapshot = BatteryHealthSnapshot(
            designCapacityMah = (sourceHealth["designCapacityMah"] as Number).toInt(),          // REAL
            designCapacitySource = sourceHealth["designCapacitySource"] as String,               // REAL
            cycleCount = cycle.count,
            cycleCountSource = cycle.source,
            cycleCountState = cycle.state,
            chargeCounterUah = if (variant.chargeSupported) (sourceHealth["chargeCounterUah"] as Number).toLong() else null,
            chargeCounterUahState =
                if (variant.chargeSupported) BatteryReadStates.READ
                else BatteryReadStates.notRead(BatteryReadStates.REASON_UNSUPPORTED_BY_DEVICE),
            energyCounterNwh = null,                                                              // REAL (null)
            energyCounterNwhState = BatteryReadStates.notRead(BatteryReadStates.REASON_UNSUPPORTED_BY_DEVICE)
        )
        val indicators = BatteryIndicators.compute(
            samples = written, batterySnapshot = snapshot,
            batteryLevelStart = h.batteryLevelStart, batteryLevelEnd = rows.last().v[4].toInt(),
            batteryTempStartC = h.batteryTempStart.toDouble(), batteryTempMaxC = rows.maxOf { it.v[1] }.toDouble()
        )
        val batteryHealthMap = BatteryIndicators.footerMap(indicators, snapshot)

        // ── PRODUCERS: the remaining pure footer helpers ──
        val sourceStillness = g.scanStillness ?: error("REAL: the source footer must carry scanStillness")
        val stillness = StillnessClassifier.report(sourceStillness.accelStdDev, sourceStillness.sampleCount)
        val cpuTemp = StressTestService.selectFooterCpuTemp(variant.liveCpuC, variant.samplerMaxCpuC, variant.samplerTempSource)
        val availability = ThermalSensorAvailability.fromDiscovery(discovery.report)
        val gemm = GpuGemmOutcome.derive(variant.story.gpuAttempts, variant.story.gpuAnyFailed, variant.story.gpuGovernorCapped)
        val critical = CriticalDefectOutcome.derive(gemm.correct)
        val storageOutcome = StorageVerifyOutcome.fromNative(variant.storageNative)
        val pins = variant.story.pins
        val rv = g.verificationResults

        val verification = VerificationSummary(
            gpuComputeGemmCorrect = gemm.correct,
            gpuComputeGemmState = gemm.state,
            gpuFragmentHashMatches = rv.gpuFragmentHashMatches,                       // REAL
            gpuFragmentHashTotal = rv.gpuFragmentHashTotal,                           // REAL
            gpuRenderCompletionMsCold = rv.gpuRenderCompletionMsCold,                 // REAL
            gpuRenderCompletionMsWarm = rv.gpuRenderCompletionMsWarm,                 // REAL
            memoryTestedBytes = rv.memoryTestedBytes,                                 // REAL (the AI workload's bytes)
            memoryVerifyState = memoryOutcome.state,
            memoryVerifyBytes = memoryOutcome.verifyBytes,
            memoryMarchErrors = memoryOutcome.marchErrors,
            memoryWalkingOnesErrors = memoryOutcome.walkingOnesErrors,
            memoryRandomVerifyErrors = memoryOutcome.randomVerifyErrors,
            memoryVerifyBufferBytes = memoryOutcome.bufferBytes,
            memoryVerifyTimeCapMs = memoryOutcome.timeCapMs,
            memoryVerifyDurationMs = memoryOutcome.durationMs,
            memoryVerifyFreeBytes = memoryOutcome.freeBytes,
            memoryPhaseWindows = memoryWindows,
            storageWriteVerifyErrors = StorageWriteErrors.count(
                writeAttempted = true, failedSyncs = variant.storageFailedSyncs, writeExceptions = 0
            ),
            storageReadVerifyErrors = storageOutcome.readErrors,
            storageVerifyState = storageOutcome.state,
            storageVerifyBytesCompared = storageOutcome.bytesCompared,
            storageVerifyBytesPlanned = storageOutcome.bytesPlanned,
            storageVerifyDurationMs = storageOutcome.durationMs,
            storageVerifyMethod = storageOutcome.method,
            // REAL values, MODIFIED unit: the real 1.0.8 segments are decimal MB/s; the unit the fv68 payload declares is MiB/s.
            storageThroughputSegments = rv.storageThroughputSegments.map {
                StorageThroughputSegment(it.cumulativeBytes, (it.mbps * MB_TO_MIB).toFloat())
            },
            cpuKnownAnswerState = knownAnswer.state,
            cpuKnownAnswerRuns = knownAnswer.runs.takeIf { it.isNotEmpty() },
            cpuKnownAnswerTotalMsByPhase = knownAnswer.totals,
            criticalDefectDetected = critical.detected,
            criticalDefectInputsNotRun = critical.inputsNotRun,
            workersPinnedBig = pins?.get(0), workersPinnedMid = pins?.get(1), workersPinnedLittle = pins?.get(2),
            workersPinFailedBig = pins?.get(3), workersPinFailedMid = pins?.get(4), workersPinFailedLittle = pins?.get(5),
            workersPinState = WorkersPinOutcome.stateOf(attempted = pins != null, aiWorkloadPathSelected = true),
            aiInferenceIterationsTotal = rv.aiInferenceIterationsTotal,               // REAL
            aiInferenceWorkers = rv.aiInferenceWorkers,                               // REAL
            aiInferenceAvgMs = rv.aiInferenceAvgMs,                                   // REAL
            aiActiveDelegate = variant.story.aiActiveDelegate,
            aiDelegateInitError = variant.story.aiDelegateInitError,
            cpuStressFallbackUsed = variant.story.cpuStressFallbackUsed,
            gpuFenceTimeoutCount = rv.gpuFenceTimeoutCount,                           // REAL
            storageBytesWritten = rv.storageBytesWritten,                             // REAL
            storageWriteDurationMs = rv.storageWriteDurationMs                        // REAL
        )

        // SCRIPTED: one boundary per phase, from the REAL extents; MEMORY_STRESS carries its two windows (file-only, not shipped).
        val phaseBoundaries = V2Phase.values().filter { it.phaseId in phaseExtent }.map { p ->
            val (start, end) = phaseExtent.getValue(p.phaseId)
            PhaseBoundary(
                p.phaseId, p.phaseName, start, end,
                windows = if (p == V2Phase.MEMORY_STRESS) memoryWindows else null
            )
        }
        val sourceDiagnostics = g.footerDiagnostics ?: error("REAL: the source footer must carry footerDiagnostics")

        // ── the real chain ──
        val scanDir = File(workDir, "scan").also { it.mkdirs() }
        val context = contextFor(scanDir)
        // PRODUCER (fieldsVersion 71, A-P6-13): the real PowerSaveReading over the scripted phone's answer (the real source's header said false; the state is now off | on | read_failed)
        val powerSave = PowerSaveReading.read(variant.powerSaveRead)
        val header = TimeSeriesHeader(
            scanId = h.scanId, deviceFingerprint = h.deviceFingerprint, scanType = h.scanType,
            appVersion = GENERATED_APP_VERSION,                                           // SCRIPTED (see the constant)
            androidApiLevel = variant.apiLevel, deviceModel = h.deviceModel, cpuCores = h.cpuCores,   // SCRIPTED when != 30
            totalRamMb = h.totalRamMb, startTimestamp = h.startTimestamp,
            batteryLevelStart = h.batteryLevelStart, batteryTempStart = h.batteryTempStart,
            isPowerSaveMode = powerSave.isPowerSaveMode,
            powerSaveState = powerSave.state
        )
        // PRODUCER (fieldsVersion 71, A-P6-13): the real BrightnessLock.lock over the scripted window
        val brightness = BrightnessLock.lock(variant.brightnessValue, variant.brightnessWindow)
        var handed: TimeSeriesFooter? = null
        val path = runBlocking {
            val writer = TimeSeriesFileWriter(context)
            writer.enableV2Mode()
            writer.setVariableDefinitions(layout.variables)
            writer.startNewFile(header)
            for (s in written) writer.addSample(s)
            val footer = TimeSeriesFooter(
                endTimestamp = if (stopAtMs == null) g.endTimestamp else rows.last().ts + 700L,   // REAL / SCRIPTED when stopped
                totalSamples = written.size,
                isComplete = if (stopAtMs == null) g.isComplete else false,                // REAL / SCRIPTED when stopped
                earlyStopReason = if (stopAtMs == null) g.earlyStopReason else "user_cancelled",
                batteryLevelEnd = rows.last().v[4].toInt(),                                // REAL (the last real row)
                maxBatteryTemp = rows.maxOf { it.v[1] },                                   // REAL (the max over the real rows)
                maxCpuTemp = null,                                                         // fv68 does not write it
                thermalHeadroomAvg = g.thermalHeadroomAvg,                                 // REAL (transitional aggregate)
                thermalHeadroomPeak = g.thermalHeadroomPeak,                               // REAL
                thermalStatusMax = g.thermalStatusMax,                                     // REAL
                tempSource = g.tempSource,                                                 // REAL
                intensityFactor = g.intensityFactor,                                       // REAL (a constant, declared)
                deviceTierMeasured = g.deviceTierMeasured,                                 // REAL (a constant, declared)
                batteryCurrentScalingApplied = g.batteryCurrentScalingApplied,             // REAL
                tempRiseRateInProbe = g.tempRiseRateInProbe,                               // REAL
                throttlingEvents = variant.throttlingEvents ?: g.throttlingEvents,                // REAL unless the variant scripts one
                verificationResults = verification,
                phaseCompletionStatus = if (stopAtMs == null) g.phaseCompletionStatus else    // REAL / SCRIPTED when stopped
                    V2Phase.values().associate { p ->
                        p.phaseId to when {
                            p.phaseId < V2Phase.COMBINED_PEAK.phaseId -> "completed"
                            p == V2Phase.COMBINED_PEAK -> "early_terminated"
                            else -> "skipped"
                        }
                    },
                phaseBoundaries = phaseBoundaries,
                stressEffectiveness = variant.stressEffectiveness(g.stressEffectiveness),  // REAL unless the variant scripts it
                batteryHealth = batteryHealthMap,
                dataQuality = variant.dataQuality(g.dataQuality),                          // REAL unless the variant scripts it
                knownCapacityClassification = g.knownCapacityClassification,               // REAL
                fieldsVersion = BuildConfig.FIELDS_VERSION,
                // SCRIPTED: the REAL diagnostics plus the three keys completeScan adds in fieldsVersion 68.
                footerDiagnostics = sourceDiagnostics + mapOf(
                    "channelReadFailures" to "ok", "thermalLists" to "ok", "batteryPairs" to "ok"
                ),
                channelDiscovery = discovery.report,
                thermalSensorAvailability = availability,
                scanStillness = stillness,
                preScanWarnings = variant.preScanRead.getOrNull()?.takeIf { it.written }?.warnings,
                preScanWarningsState = PreScanWarningsStates.of(variant.preScanRead),
                // PRODUCER (fieldsVersion 71, A-P6-13): the window the app applies, and what the real lock reported
                batteryWindowPct = net.neuralcheck.data.local.files.BatteryWindowPctFooter(ScanBatteryWindow.MIN_PCT, ScanBatteryWindow.MAX_PCT),
                brightnessLock = net.neuralcheck.data.local.files.BrightnessLockFooter(brightness.state, brightness.value, brightness.method),
                unmeasuredDefaults = UnmeasuredDefaults.forScan(v2Ran = true),
                // The completeScan formula: the sum of the nominal phase table (1605 for V2).
                expectedSamples = V2Phase.values().sumOf { (it.durationMs / 1000L) * it.samplingHz }.toInt(),
                flushFailureCount = writer.getFlushFailureCount(),
                cpuPolicies = discovery.cpuPolicies,
                channelReadFailures = failures.indices,
                channelReadFailureCounts = failures.counts,
                thermalHeadroomReadings = thermalLists.headroomReadings,
                thermalHeadroomState = thermalLists.headroomState,
                thermalStatusEvents = thermalLists.statusEvents,
                thermalStatusState = thermalLists.statusState,
                batteryPairs = batteryPairs.pairs,
                batteryPairsState = batteryPairs.state,
                transitionalAggregates = TransitionalAggregates.NAMES,
                cpuTempFinalC = cpuTemp.valueC,
                cpuTempFinalSource = cpuTemp.source
            )
            handed = footer
            writer.finalize(footer)
        } ?: error("finalize must write the file")
        val data = runBlocking { TimeSeriesFileReader(context).readFromPath(path) } ?: error("the file just written must read back")
        // PRODUCER: ManualTestVisibility.hiddenByModel over the variant's rules (the production table when it scripts none).
        val hidden = ManualTestVisibility.hiddenByModel(context, variant.hideRules ?: ManualTestVisibility.RULES)
        val fingerprint = BehaviorFingerprintBuilder().build(
            scanReport(header, variant), data, DEVICE_PROFILE_KEY,
            manualResultsHeader = ManualResultsHeaderDto(hiddenByModel = hidden)
        )
        val payload = AnalysisRequestSerializer.fingerprintToMap(fingerprint)
        return Built(variant, data, fingerprint, payload, handed ?: error("the footer was handed to the writer"))
    }

    // ─────────────────────────── the CPU known-answer checks ───────────────────────────

    private class KnownAnswerResult(val runs: List<CpuKnownAnswerRun>, val totals: Map<Int, Long>?, val state: String)

    /** SCRIPTED: what the two fake kernels "spend" (the sieve and the SHA), in ms of the virtual clock. */
    private const val SIEVE_MS = 90L
    private const val SHA_MS = 30L

    /** SCRIPTED: what a fv70 native layer returns for D's sieve: N = 1,000,000 and pi(N) = 78,498 (KnownAnswerReferenceTest checks both). */
    private const val SIEVE_LIMIT = 1_000_000L
    private const val SIEVE_REFERENCE = 78_498L

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun runKnownAnswers(plan: KnownAnswerPlan, phase3Start: Long, phase5Start: Long, phase5CutMs: Long?): KnownAnswerResult {
        var out: KnownAnswerResult? = null
        runTest {
            val spent = AtomicLong(0)
            val sieveCalls = AtomicInteger(0)
            val fake = FakeNativeVerification(
                onSieve = {
                    val n = sieveCalls.incrementAndGet()
                    if (plan.throwAtSieveCall == n) throw IllegalStateException("sieve kernel aborted: out of memory")
                    val sieveMs = plan.sieveMsAtCall[n] ?: SIEVE_MS
                    spent.addAndGet(sieveMs)
                    val mismatch = plan.mismatchAtSieveCall == n
                    longArrayOf(
                        KnownAnswerLayout.SIEVE_VERSION, KnownAnswerLayout.SIEVE_SIZE.toLong(),
                        if (mismatch) KnownAnswerLayout.STATUS_MISMATCH else KnownAnswerLayout.STATUS_MATCH,
                        if (mismatch) SIEVE_REFERENCE + 1 else SIEVE_REFERENCE,
                        sieveMs,
                        SIEVE_LIMIT
                    )
                },
                onSha = {
                    spent.addAndGet(SHA_MS)
                    longArrayOf(KnownAnswerLayout.SHA_VERSION, KnownAnswerLayout.SHA_SIZE.toLong(), KnownAnswerLayout.STATUS_MATCH, SHA_MS)
                }
            )
            val scheduler = CpuKnownAnswerScheduler(
                native = fake,
                elapsedRealtime = { testScheduler.currentTime + spent.get() },
                callDispatcher = StandardTestDispatcher(testScheduler)
            )
            scheduler.runPhase(V2Phase.CPU_SUSTAINED.phaseId, phase3Start, V2Phase.CPU_SUSTAINED.durationMs, 0)
            val phase5Length = V2Phase.COMBINED_PEAK.durationMs
            if (phase5CutMs == null) {
                scheduler.runPhase(V2Phase.COMBINED_PEAK.phaseId, phase5Start, phase5Length, 0)
            } else {
                val budget = phase5CutMs - (testScheduler.currentTime + spent.get())
                withTimeoutOrNull(budget) {
                    scheduler.runPhase(V2Phase.COMBINED_PEAK.phaseId, phase5Start, phase5Length, 0)
                }
            }
            out = KnownAnswerResult(scheduler.snapshot(), scheduler.totalMsByPhase(), scheduler.state(aiCpuPathSelected = true))
        }
        return out ?: error("the scheduler run must finish")
    }

    // ─────────────────────────── the hand-built results ───────────────────────────

    /** SCRIPTED: placeholders — a real key is device-bound (keystore, Firebase installation id, Android id). */
    private val DEVICE_PROFILE_KEY = DeviceProfileKey(
        primary = "synthetic-golden-primary",
        layerA_keystore = "synthetic-golden-layer-a",
        layerB_firebaseId = "synthetic-golden-layer-b",
        layerC_androidId = "synthetic-golden-layer-c",
        layerD_buildSig = "synthetic-golden-layer-d",
        layerE_sensorSig = "synthetic-golden-layer-e"
    )

    private fun result(
        id: String, category: String, status: TestStatus, raw: String?, unit: String? = null,
        notes: String? = null, measurements: Map<String, Any?>? = null, component: String = id
    ) = TestResult(
        testId = id, componentName = component, componentNameAr = component, categoryName = category,
        status = status, score = null, rawValue = raw, rawValueUnit = unit, notes = notes, measurements = measurements
    )

    private fun sensorEvent(timestampNs: Long, sensor: Sensor, vararg values: Float): SensorEvent {
        val event = mock<SensorEvent>()
        SensorEvent::class.java.getField("values").also { it.isAccessible = true }.set(event, values)
        SensorEvent::class.java.getField("timestamp").also { it.isAccessible = true }.set(event, timestampNs)
        SensorEvent::class.java.getField("sensor").also { it.isAccessible = true }.set(event, sensor)
        return event
    }

    /**
     * PRODUCER: the real [SensorTests.testSensor] for SN-04 over a SCRIPTED SensorManager (a real run needs a light sensor and a
     * hand), on virtual time: the listener is registered at 0, the cover is asked for at 1 000 ms, the test ends at 4 000 ms. The
     * scripted events are (ms since registration, lux). Status, rawValue and notes are whatever the real code computes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun lightSensorResult(script: LightCoverScript): TestResult {
        val events: List<Pair<Long, Float>> = when (script) {
            LightCoverScript.COVERED -> listOf(300L to 312f, 700L to 318f, 1_500L to 41f, 2_100L to 3f, 3_400L to 2f)
            LightCoverScript.NOT_COVERED -> listOf(300L to 120f, 700L to 121f, 1_800L to 120f, 3_000L to 119f)
            LightCoverScript.ALL_ZERO -> listOf(400L to 0f, 1_900L to 0f)
        }
        var out: TestResult? = null
        runTest {
            val sensor: Sensor = mock { on { type } doReturn Sensor.TYPE_LIGHT }
            var listener: SensorEventListener? = null
            val sm: SensorManager = mock {
                on { getDefaultSensor(Sensor.TYPE_LIGHT) } doReturn sensor
                on { registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) } doAnswer { listener = it.getArgument(0); true }
            }
            val resources: Resources = mock { on { getIdentifier(any(), any(), any()) } doReturn 1 }
            val ctx: Context = mock {
                on { getSystemService(Context.SENSOR_SERVICE) } doReturn sm
                on { getString(any()) } doReturn "SCRIPTED: string resource text"
                on { this.resources } doReturn resources
                on { packageName } doReturn "net.neuralcheck"
            }
            val tests = SensorTests(ctx, CoverPromptChannel())
            tests.nanoClock = { currentTime * 1_000_000L }
            val job = launch { out = tests.testSensor("SN-04", "test_sn04_ar", "test_sn04_tr", Sensor.TYPE_LIGHT) }
            runCurrent()
            for ((ms, lux) in events) {
                advanceTimeBy(ms - currentTime)
                runCurrent()
                listener!!.onSensorChanged(sensorEvent(ms * 1_000_000L, sensor, lux))
            }
            advanceTimeBy(5_000L)
            runCurrent()
            job.join()
        }
        // SCRIPTED: the component names are the test id, like every other hand-built row of the samples.
        return (out ?: error("the sensor test must finish")).copy(componentName = "SN-04", componentNameAr = "SN-04")
    }

    /**
     * PRODUCER (batch 1.0.12): the real [GpsTests.testSatellites] for L-02 over a SCRIPTED `LocationManager` (a real run needs a sky and
     * 30 s of it), on virtual time: the GNSS callback and the location listener are the objects the real code registered, the scripted
     * events are fed to them, and status, `rawValue`, `notes` and `measurements` are whatever the real code computes. Until this
     * batch the row was hand-built from four numbers and carried four of the nine keys the producer writes.
     *
     * The window is 30 000 ms. SCRIPTED events: 31 satellite-status callbacks, one every 950 ms from 500 ms (the sky fills in, the LAST
     * call is 9 visible / 4 used — the last callback decides, not the best one); the first fix 8 123 ms in; 22 location updates, one
     * per second from 8 500 ms.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun gpsSatellitesResult(): TestResult {
        fun sky(visible: Int, used: Int): GnssStatus = mock {
            on { satelliteCount } doReturn visible
            on { usedInFix(any()) } doReturn false
            for (i in 0 until used) on { usedInFix(i) } doReturn true
        }
        // (ms since the test started, what happens)
        val events = ArrayList<Pair<Long, (GnssStatus.Callback, LocationListener) -> Unit>>()
        for (i in 0..30) {
            val visible = if (i == 30) 9 else minOf(3 + i / 3, 9)      // SCRIPTED: the sky fills in
            val used = if (i == 30) 4 else minOf(i / 8, 3)             // SCRIPTED: never more than 3 used until the last call
            events += (500L + 950L * i) to { cb: GnssStatus.Callback, _: LocationListener -> cb.onSatelliteStatusChanged(sky(visible, used)) }
        }
        events += 8_123L to { cb: GnssStatus.Callback, _: LocationListener -> cb.onFirstFix(8_123) }
        for (i in 0 until 22) {
            events += (8_500L + 1_000L * i) to { _: GnssStatus.Callback, l: LocationListener -> l.onLocationChanged(mock<Location>()) }
        }
        var out: TestResult? = null
        runTest {
            val lm: LocationManager = mock { on { isProviderEnabled(LocationManager.GPS_PROVIDER) } doReturn true }
            val ctx: Context = mock {
                on { getSystemService(Context.LOCATION_SERVICE) } doReturn lm
                on { getString(any()) } doReturn "SCRIPTED: string resource text"
                on { getString(net.neuralcheck.R.string.unit_satellite) } doReturn "satellites"
                on { checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION), any(), any()) } doReturn PackageManager.PERMISSION_GRANTED
            }
            val job = launch { out = GpsTests(ctx).testSatellites() }
            runCurrent()
            val cbCaptor = argumentCaptor<GnssStatus.Callback>()
            val listenerCaptor = argumentCaptor<LocationListener>()
            verify(lm).registerGnssStatusCallback(cbCaptor.capture(), any())
            verify(lm).requestLocationUpdates(eq(LocationManager.GPS_PROVIDER), eq(1_000L), eq(0f), listenerCaptor.capture(), anyOrNull())
            for ((ms, fire) in events.sortedBy { it.first }) {
                advanceTimeBy(ms - currentTime)
                runCurrent()
                fire(cbCaptor.firstValue, listenerCaptor.firstValue)
            }
            advanceTimeBy(31_000L)
            runCurrent()
            job.join()
        }
        // SCRIPTED: the component names are the test id, like every other hand-built row of the samples.
        return (out ?: error("the satellite test must finish")).copy(componentName = "L-02", componentNameAr = "L-02")
    }

    /**
     * PRODUCER (batch 1.0.12): the real [SensorTests.testSensor] for SN-08 (step detector) / SN-09 (step counter) over a SCRIPTED
     * `SensorManager` on virtual time, as [lightSensorResult] does for SN-04. The device is scripted as API 34 with ACTIVITY_RECOGNITION
     * GRANTED (without it the real code returns NOT_TESTED and no `measurements`, which is a unit test: `SensorTestsStepSensorsTest`).
     * [events] are (ms since registration, the sensor's value); `readings[].t` and `.values` and the status, `rawValue` and `notes` are
     * whatever the real code computes. "No readings" (the empty list) is the unit test's case, not a sample's.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun stepSensorResult(testId: String, nameKey: String, sensorType: Int, events: List<Pair<Long, Float>>): TestResult {
        var out: TestResult? = null
        runTest {
            val sensor: Sensor = mock { on { type } doReturn sensorType }
            var listener: SensorEventListener? = null
            val sm: SensorManager = mock {
                on { getDefaultSensor(sensorType) } doReturn sensor
                on { registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) } doAnswer { listener = it.getArgument(0); true }
            }
            val resources: Resources = mock { on { getIdentifier(any(), any(), any()) } doReturn 1 }
            val ctx: Context = mock {
                on { getSystemService(Context.SENSOR_SERVICE) } doReturn sm
                on { getString(any()) } doReturn "SCRIPTED: string resource text"
                on { this.resources } doReturn resources
                on { packageName } doReturn "net.neuralcheck"
                on { checkPermission(eq(Manifest.permission.ACTIVITY_RECOGNITION), any(), any()) } doReturn PackageManager.PERMISSION_GRANTED
            }
            val tests = SensorTests(ctx, CoverPromptChannel())
            tests.sdkIntForGate = 34                                // SCRIPTED: API 34, where the step sensors need ACTIVITY_RECOGNITION (granted above)
            tests.nanoClock = { currentTime * 1_000_000L }
            val job = launch { out = tests.testSensor(testId, "test_${nameKey}_ar", "test_${nameKey}_tr", sensorType) }
            runCurrent()
            for ((ms, value) in events) {
                advanceTimeBy(ms - currentTime)
                runCurrent()
                listener!!.onSensorChanged(sensorEvent(ms * 1_000_000L, sensor, value))
            }
            advanceTimeBy(5_000L)
            runCurrent()
            job.join()
        }
        // SCRIPTED: the component names are the test id, like every other hand-built row of the samples.
        return (out ?: error("the sensor test must finish")).copy(componentName = testId, componentNameAr = testId)
    }

    /**
     * The platform's answers a scripted S10-5G-like phone gives (API 30): Samsung's factory FHD+ on a QHD+ panel — the display runs 1080 x 2280 in a mode whose
     * physical size is 1440 x 3040, and xdpi is the PANEL's 502 (design §1.1), so the density selector must choose XDPI_MODE_SCALED. SCRIPTED: every number.
     */
    private fun s10Environment() = TouchGridTestKit.environment(
        xdpi = 502f, ydpi = 502f, densityDpi = 420,
        displayWidthPx = Reading.Value(1080), displayHeightPx = Reading.Value(2280),
        modePhysicalWidthPx = Reading.Value(1440), modePhysicalHeightPx = Reading.Value(3040),
        gestureInsetsPx = Reading.Value(PxRect(48, 0, 48, 72)), mandatoryGestureInsetsPx = Reading.Value(PxRect(0, 0, 0, 72)),
        exclusionRectsPx = Reading.Value(listOf(PxRect(0, 0, 1080, 2100))), systemBarsHidden = Reading.Value(true),
        cutoutRectsPx = Reading.Value(emptyList()),
        roundedCorners = RoundedCorners.State(TouchGridContract.RoundedCornersStates.NOT_AVAILABLE_API),   // API 30 < 31
        sdkInt = 30
    )

    /**
     * A scripted S21-Ultra-like phone (API 34): 1440 x 3200 at 515 dpi, a camera cut-out at the top centre, rounded corners. SCRIPTED: every number — and the cut-out: a notch 80 x 226 px (about
     * 4 x 11 mm) that reaches 90 px into the grid (the grid starts 160 px below the top, under the empty strip), so the sample carries camera cells that are in the pattern (two of the top row) and
     * two that are not (row 1): the real hole of a phone lies in the strip and takes none.
     */
    private fun s21Environment(
        mode: Reading<Int> = Reading.Value(1440),
        modeHeight: Reading<Int> = Reading.Value(3200)
    ) = TouchGridTestKit.environment(
        xdpi = 515f, ydpi = 515f, densityDpi = 560,
        displayWidthPx = Reading.Value(1440), displayHeightPx = Reading.Value(3200),
        modePhysicalWidthPx = mode, modePhysicalHeightPx = modeHeight,
        gestureInsetsPx = Reading.Value(PxRect(66, 0, 66, 96)), mandatoryGestureInsetsPx = Reading.Value(PxRect(0, 0, 0, 96)),
        exclusionRectsPx = Reading.Value(listOf(PxRect(0, 0, 1440, 2960))), systemBarsHidden = Reading.Value(true),
        cutoutRectsPx = Reading.Value(listOf(PxRect(700, 24, 780, 250))),
        roundedCorners = RoundedCorners.Radii(RoundedCornerRadii(120, 120, 120, 120)),
        sdkInt = 34
    )

    /**
     * PRODUCER: the real [TouchGridRecorder], [net.neuralcheck.domain.manual.TouchGridLayout] and density selector driven by SCRIPTED touch samples on two SCRIPTED
     * clocks (a real run needs a screen and a finger; the dialog's view only converts MotionEvents into these samples). The platform's answers
     * ([TouchGridEnvironment]) are SCRIPTED too. Returns the answer that run records and its raw. The three scripts are described on [TouchGridScript].
     */
    private fun touchGridRun(script: TouchGridScript): Pair<TestStatus, Map<String, Any?>> {
        val open = 50_000L            // SCRIPTED: elapsedRealtime when the dialog opens
        val uptimeOpen = 7_200_000L   // SCRIPTED: uptimeMillis when the dialog opens (a different number: the two clocks are never subtracted)
        return when (script) {
            TouchGridScript.FULL_AUTO -> {
                val rig = TouchGridTestKit.Rig(env = s10Environment(), surface = TouchGridSurface(1080, 2100, 0, 0), nowAtOpen = open, uptimeAtOpen = uptimeOpen)
                rig.rec.setUnbufferedDispatch(Reading.Value(true))             // SCRIPTED: the view asked for unbuffered dispatch and the call returned
                rig.tick(1_730L)                                               // SCRIPTED: the first touch, 1 730 ms after opening
                rig.sweepRows(stepPx = 44f, dtMs = 8L)                         // SCRIPTED: 3 mm per 8 ms, every row, back and forth
                check(rig.rec.isComplete) { "the scripted sweep must light every required cell" }
                TestStatus.PASS to rig.rec.measurements(TouchGridRecorder.EndReasons.AUTO_COMPLETE)
            }
            TouchGridScript.PARTIAL_NOT_WORKING -> {
                val rig = TouchGridTestKit.Rig(env = s21Environment(), surface = TouchGridSurface(1440, 2960, 0, 0), nowAtOpen = open, uptimeAtOpen = uptimeOpen)
                rig.rec.setUnbufferedDispatch(Reading.Value(true))
                rig.tick(2_400L)                                               // SCRIPTED: the first touch
                // stroke 0: rows 0-9, a gap_jump on row 4 (60 ms pause, 600 px) and a coord_jump on row 6 (8 ms, 500 px), lifted at the left edge
                rig.sweepRows(id = 0, fromRow = 0, toRow = 10, stepPx = 61f, dtMs = 8L,
                    jumps = mapOf(4 to TouchGridTestKit.Jump(60L, 600f), 6 to TouchGridTestKit.Jump(8L, 500f)))
                val y9 = ((9 + 0.5) * rig.layout.cellHeightPx).toFloat()
                rig.tick(8L); rig.up(0, 1f, y9, rig.uptime)
                // stroke 1: rows 10-17, lifted at the left edge again
                rig.tick(900L)
                rig.sweepRows(id = 0, fromRow = 10, toRow = 18, stepPx = 61f, dtMs = 8L)
                val y17 = ((17 + 0.5) * rig.layout.cellHeightPx).toFloat()
                rig.tick(8L); rig.up(0, 1f, y17, rig.uptime)
                // stroke 2: a palm, 190 px across, that the platform cancels with FLAG_CANCELED
                rig.tick(300L)
                rig.down(1, 100f, 2700f, tMs = rig.uptime, major = 190f)
                rig.tick(40L)
                rig.rec.pointerEnded(1, TouchGridContract.StrokeEnds.CANCEL_FLAGGED, rig.sample(100f, 2700f, rig.uptime, major = 190f))
                rig.now = open + 16_200L                                        // SCRIPTED: the technician presses the button after the 15 s
                TestStatus.FAIL to rig.rec.measurements(TouchGridRecorder.EndReasons.USER_MARKED_NOT_WORKING)
            }
            TouchGridScript.CAPPED_NOT_TESTED -> {
                // SCRIPTED: the display mode could not be read and the unbuffered dispatch request was refused — both are declared in absentFieldReasons
                val env = s21Environment(
                    mode = Reading.Absent(TouchGridContract.AbsentReasons.readFailed("the display has no current mode")),
                    modeHeight = Reading.Absent(TouchGridContract.AbsentReasons.readFailed("the display has no current mode"))
                )
                val rig = TouchGridTestKit.Rig(env = env, surface = TouchGridSurface(1440, 2960, 0, 0), nowAtOpen = open, uptimeAtOpen = uptimeOpen)
                rig.rec.setUnbufferedDispatch(Reading.Absent(TouchGridContract.AbsentReasons.readFailed("requestUnbufferedDispatch is refused by this view")))
                rig.tick(900L)
                // one finger scribbling along one row, 3 mm per 8 ms, 15 300 samples: 300 past the cap
                val y = ((5 + 0.5) * rig.layout.cellHeightPx).toFloat()
                val w = rig.layout.surface.widthPx.toFloat()
                rig.down(0, 1f, y, rig.uptime)
                var x = 1f
                var dir = 1f
                for (n in 1 until 15_300) {
                    x += dir * 61f
                    if (x >= w - 1f) { x = w - 1f; dir = -1f } else if (x <= 1f) { x = 1f; dir = 1f }
                    rig.tick(8L)
                    rig.move(0, Triple(rig.uptime, x, y))
                }
                rig.now = open + 130_000L                                       // SCRIPTED: the technician presses «لم أختبر»
                TestStatus.NOT_TESTED to rig.rec.measurements(TouchGridRecorder.EndReasons.USER_MARKED_NOT_TESTED)
            }
        }
    }

    /**
     * PRODUCER: the real [VolumeKeyRecorder] driven by SCRIPTED key events (what the dialog window's tap passes it: key code, down / up, event time on the
     * `uptimeMillis` clock, repeat count, device id, source) and a SCRIPTED clock, and the real `TouchGridRecorder.recordOnDismiss` for the dismissed run.
     * Returns the two rows M-09 and M-10 of the variant's script. SCRIPTED: every event, every level, device id 5 and source 257 (SOURCE_KEYBOARD).
     */
    private fun volumeRows(script: VolumeScript): List<TestResult> {
        val open = 9_000_000L                                  // SCRIPTED: uptimeMillis when the dialog opens
        fun run(testId: String, scripted: (VolumeKeyRecorder, (Long) -> Unit) -> Pair<String, Map<String, Any?>>): TestResult {
            var uptime = open
            val rec = VolumeKeyRecorder(VolumeKeyRecorder.expectedKeyCodeFor(testId), { uptime })
            val (status, raw) = scripted(rec) { uptime = open + it }
            return result(testId, "audio", TestStatus.valueOf(status.uppercase()), null, measurements = MeasurementsJson.decode(MeasurementsJson.encode(raw)))
        }
        fun VolumeKeyRecorder.key(keyCode: Int, down: Boolean, atMs: Long) = onKeyEvent(keyCode, down, open + atMs, 0, 5, 257)
        fun answer(rec: VolumeKeyRecorder, reason: String, source: String?, levels: VolumeKeyRecorder.VolumeLevels?): Pair<String, Map<String, Any?>> {
            val raw = rec.measurements(reason, source, levels)
            return checkNotNull(VolumeKeyRecorder.statusFor(reason)) { "$reason records no answer of its own" } to raw
        }
        val up = VolumeKeyRecorder.KEYCODE_VOLUME_UP
        val down = VolumeKeyRecorder.KEYCODE_VOLUME_DOWN
        return when (script) {
            VolumeScript.DETECTED_BOTH_WAYS -> listOf(
                run("M-09") { rec, at ->
                    rec.key(up, true, 2_310); rec.key(up, false, 2_410)                 // the key event arrives; the dialog stays 600 ms for the up
                    at(2_910)
                    answer(rec, VolumeKeyRecorder.EndReasons.AUTO_DETECTED, VolumeKeyRecorder.DetectionSources.KEY_EVENT, VolumeKeyRecorder.VolumeLevels(15, 7, 8))
                },
                run("M-10") { rec, at ->
                    at(2_460)                                                            // no key event reached the dialog; the level moved at 1 860 ms
                    answer(rec, VolumeKeyRecorder.EndReasons.AUTO_DETECTED, VolumeKeyRecorder.DetectionSources.VOLUME_LEVEL_CHANGE, VolumeKeyRecorder.VolumeLevels(15, 7, 6))
                }
            )
            VolumeScript.CLAIMED_AND_FAILED -> listOf(
                run("M-09") { rec, at ->
                    rec.key(down, true, 4_100); rec.key(down, false, 4_190)             // the wrong key
                    at(9_700)
                    answer(rec, VolumeKeyRecorder.EndReasons.USER_MARKED_WORKING, null, VolumeKeyRecorder.VolumeLevels(15, 7, 6))
                },
                run("M-10") { rec, at ->
                    at(12_400)
                    answer(rec, VolumeKeyRecorder.EndReasons.USER_MARKED_NOT_WORKING, null, VolumeKeyRecorder.VolumeLevels(15, 7, 7))
                }
            )
            VolumeScript.NOT_TESTED_AND_DISMISSED -> listOf(
                run("M-09") { rec, at ->
                    at(6_000)                                                            // a stream of max 1 has no room for a mid level: no levels
                    answer(rec, VolumeKeyRecorder.EndReasons.USER_MARKED_NOT_TESTED, null, null)
                },
                run("M-10") { rec, at ->
                    rec.key(up, true, 1_200); rec.key(up, false, 1_290)                 // the wrong key, then back
                    at(3_000)
                    val raw = rec.measurements(VolumeKeyRecorder.EndReasons.DISMISSED, null, VolumeKeyRecorder.VolumeLevels(15, 7, 8))
                    val (status, kept) = checkNotNull(TouchGridRecorder.recordOnDismiss(null, raw)) { "a run with an event is recorded" }
                    status to kept
                }
            )
        }
    }

    /**
     * SCRIPTED: every functionalResults / manualResults row. The test classes need a device, so these are hand-built
     * `TestResult`s — EXCEPT the four marked PRODUCER below (L-02, SN-04, SN-08, SN-09: the real test class over a scripted
     * platform, on virtual time) and M-02's raw (the real TouchGridRecorder); the shapes are what BehaviorFingerprintBuilder reads (C-05 / G-01 / R-01 /
     * R-02 / S-01 / S-02 / SEC-03 feed `device`), the six TestStatus values all appear (rule 30), and the
     * compound `measurements` go through the same MeasurementsJson encode/decode the Room column uses.
     */
    private fun results(variant: Variant): List<TestResult> {
        // PRODUCER: the real TouchGridRecorder (with the real layout and density selector) over scripted touch samples, scripted platform answers and
        // scripted clocks; the raw goes through the same MeasurementsJson encode/decode the Room column uses.
        val (gridStatus, gridRaw) = touchGridRun(variant.touchGrid)
        val grid = MeasurementsJson.decode(MeasurementsJson.encode(gridRaw))
        return listOf(
            result("C-05", "cpu", TestStatus.PASS, "Exynos 9820"),
            result("G-01", "gpu", TestStatus.PASS, "Mali-G76 MP12"),
            result("R-01", "memory", TestStatus.PASS, "6.9", "GB"),
            result("R-02", "memory", TestStatus.PASS, "2.1", "GB"),
            result("S-01", "storage", TestStatus.PASS, "256", "GB"),
            result("S-02", "storage", TestStatus.PASS, "58.4", "% (149.2 GB)"),
            result("SEC-03", "security", TestStatus.WARNING, "2022-09-01", notes = "patch older than 12 months"),
            result("B-02", "battery", TestStatus.FAIL, "62", "%", notes = "below the grading floor"),
            // PRODUCER (batch 1.0.12): the real GpsTests.testSatellites over a scripted LocationManager, on virtual time.
            gpsSatellitesResult(),
            result("T-03", "thermal", TestStatus.UNAVAILABLE, null),
            result("N-04", "cellular", TestStatus.NOT_TESTED, null),
            result("SN-02", "sensors", TestStatus.ERROR, null, notes = "SensorManager returned null"),
            result("M-02", "display", gridStatus, null, measurements = grid),
            result("M-07", "camera", TestStatus.PASS, null),
            // "manual" is the legacy sentinel for an unresolvable category: the builder sends categoryName absent.
            result("M-11", "manual", TestStatus.NOT_TESTED, null),
            // PRODUCER (fieldsVersion 71, A-VOL): M-09 and M-10 from the real VolumeKeyRecorder over scripted key events — see `volumeRows`.
            *volumeRows(variant.volumeRuns).toTypedArray(),
            // fieldsVersion 69 (S-15): the four manual answers all appear — pass (M-02, M-07), fail (M-19),
            // not_tested (M-11) and unavailable (M-08: the technician says the device has no headphone jack).
            result("M-08", "audio", TestStatus.UNAVAILABLE, null),
            result("M-19", "camera", TestStatus.FAIL, null),
            // fieldsVersion 69 (N-20): the REAL SensorTests.testSensor over a scripted SensorManager, on virtual time.
            lightSensorResult(variant.lightCover),
            // PRODUCER (batch 1.0.12): SN-08 (step detector) and SN-09 (step counter) from the real SensorTests.testSensor over a
            // scripted SensorManager, on virtual time: `windowMs` and `readings[]` of `{t, values}`. SCRIPTED events (ms, value):
            // three steps; the counter's running total at two moments.
            stepSensorResult("SN-08", "sn08", Sensor.TYPE_STEP_DETECTOR, listOf(700L to 1f, 1_450L to 1f, 2_200L to 1f)),
            stepSensorResult("SN-09", "sn09", Sensor.TYPE_STEP_COUNTER, listOf(900L to 4_021f, 2_300L to 4_024f))
        )
    }

    private fun scanReport(h: TimeSeriesHeader, variant: Variant) = ScanReport(
        scanId = h.scanId, scanType = ScanType.GENERAL, linkedScanId = null,
        deviceInfo = DeviceInfo(
            h.deviceFingerprint, h.deviceModel, "samsung",
            when (h.androidApiLevel) { 30 -> "11"; 34 -> "14"; else -> error("no Android version scripted for API ${h.androidApiLevel}") },
            h.androidApiLevel, "1440x3040", h.cpuCores
        ),
        scanMetadata = ScanMetadata(h.startTimestamp, null, h.batteryLevelStart, null, h.batteryTempStart, 0L, true, null, GENERATED_APP_VERSION),
        functionalResults = results(variant), timeSeriesSummary = null, antiTamperFlags = emptyList(),
        technicianInput = null, peerInput = null
    )

    /** Builds every variant. */
    fun buildAll(workDir: File): List<Built> = VARIANTS.map { build(it, File(workDir, it.id).also { d -> d.mkdirs() }) }
}
