package net.neuralcheck.service.stress

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.neuralcheck.R
import net.neuralcheck.data.local.db.entity.FunctionalResultEntity
import net.neuralcheck.data.local.db.entity.ScanEntity
import net.neuralcheck.data.local.files.TimeSeriesFileWriter
import net.neuralcheck.data.local.files.TimeSeriesHeader
import net.neuralcheck.data.local.files.TimeSeriesFooter
import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.data.repository.TestResultRepository
import net.neuralcheck.domain.model.ScanType
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import net.neuralcheck.hardware.runner.FunctionalTestRunner
import net.neuralcheck.service.collector.BatteryReader
import net.neuralcheck.service.collector.SensorCollector
import net.neuralcheck.service.collector.SystemFileReader
import net.neuralcheck.service.safety.SafetyMonitor
import net.neuralcheck.data.local.files.PreScanWarningsStates
import net.neuralcheck.service.sampler.DisciplinedSampler
import net.neuralcheck.service.sync.SyncScheduler
import net.neuralcheck.util.DeviceFingerprint
import net.neuralcheck.util.DeviceProfileKey
import java.util.UUID
import javax.inject.Inject

/**
 * Foreground Service orchestrating the complete 5-phase stress test (ADR 1.8, 4.2).
 *
 * Phase timing:
 * - Phase 1 (0:00-0:30): Init + static tests + baseline at 1Hz
 * - Phase 2 (0:30-2:00): Storage speed tests at 1Hz
 * - Phase 3 (2:00-5:00): Intensive stress (CPU+GPU) at 4Hz
 * - Phase 4 (5:00-10:00): Sustained stress + camera/GPS at 1Hz
 * - Phase 5 (10:00-11:30): Cooldown at 1Hz
 */
@AndroidEntryPoint
class StressTestService : LifecycleService() {

    @Inject lateinit var scanRepository: ScanRepository
    @Inject lateinit var testResultRepository: TestResultRepository
    @Inject lateinit var functionalTestRunner: FunctionalTestRunner

    /** Phase 1.7.D — accepted pre-scan warnings holder. Read once when
     *  building the footer, drained so a follow-up scan starts clean. */
    @Inject
    lateinit var preScanWarningsHolder: net.neuralcheck.presentation.viewmodel.PreScanWarningsHolder

    /** fieldsVersion 71 (A-P6-13) — what the scan screen reported of the brightness lock; drained once when the footer is built. */
    @Inject
    lateinit var brightnessLockHolder: BrightnessLockHolder

    /** Phase 1.6.B — AI-camouflage CPU workload, injected via Hilt and
     *  forwarded to [StressTestServiceV2]. Always present in the
     *  Hilt graph; the V2 service's USE_AI_WORKLOAD_CPU flag decides
     *  whether to actually invoke it. */
    @Inject
    lateinit var aiCpuWorkload: net.neuralcheck.service.stress.v2.AiCpuWorkload

    /** Phase 1.6.C — AI-camouflage GPU workload, runs in parallel with
     *  the raymarcher during Phase 4 + 5. Hilt-injected; V2 service's
     *  USE_AI_WORKLOAD_GPU flag controls activation. */
    @Inject
    lateinit var aiGpuWorkload: net.neuralcheck.service.stress.v2.AiGpuWorkload

    /** Phase 1.6.D — image-processing memory workload that replaces the
     *  C++ March-C / Walking-Ones kernel under USE_AI_WORKLOAD_MEMORY. */
    @Inject
    lateinit var aiMemoryWorkload: net.neuralcheck.service.stress.v2.AiMemoryWorkload

    /** Phase 1.8 Task 5 — relative-calibration probe. Hilt-injected
     *  ([ProbePhase] is `@Singleton`) so a single instance owns the
     *  ChannelDiscoveryProbe-equivalent infrastructure across scans.
     *  Only used when [BuildConfig.USE_RELATIVE_CALIBRATION] is true. */
    @Inject
    lateinit var probePhase: net.neuralcheck.calibration.probes.ProbePhase

    /** Order 2026-10-14 («Record the session at scan start»): copies the session this device holds under the new
     *  scan's id the moment [startScan] mints it, so the scan's analysis and its evaluation draft use the session
     *  the scan STARTED in, never one joined later. See [net.neuralcheck.data.remote.ScanSessionRecords]. */
    @Inject
    lateinit var scanSessionRecords: net.neuralcheck.data.remote.ScanSessionRecords

    companion object {
        private const val TAG = "NeuralCheck"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "neuralcheck_scan"

        /**
         * Phase 1.14 hotfix (2026-05-17): single canonical "which CPU
         * temperature should we trust" cascade, shared between the footer's
         * CPU temperature (`footer.maxCpuTemp` ≤ 67; fieldsVersion 68:
         * `cpuTempFinalC` + `cpuTempFinalSource`, S-33) and the T-01 input
         * (via [net.neuralcheck.data.local.files.FooterCpuTemp.forT01], whose
         * `-1f` stays the T-01 interface and no longer reaches the footer).
         * Pulled out of the
         * inline `when` block that previously lived only in the
         * file-write path so:
         *  • T-01 and the footer can never drift apart (they used to
         *    show e.g. 81 °C vs 43.6 °C for the same scan because
         *    T-01 read raw `stats.maxTempC` while the footer ran
         *    this cascade);
         *  • the cascade is unit-testable in isolation, without
         *    instantiating the whole StressTestService.
         *
         * Order:
         *   1. liveCpu (a fresh `systemFileReader.readCpuTemp()` taken
         *      at scan finalize time, post-cooldown). Preferred because
         *      it naturally filters out per-tick zone-contamination that
         *      slipped past `selectCpuTempFromZones` on some Snapdragon
         *      variants. Samsung S24 Ultra production scan 18dfd6b4
         *      showed `stats.maxTempC` = 81 °C while liveCpu = 43.6 °C.
         *   2. snapshot.maxTempC, but ONLY when snapshot.tempSource ==
         *      "cpu". Refuses GPU / battery / headroom fallbacks so the
         *      consumer of this value always knows the source class.
         *   3. -1f sentinel = "no trustworthy CPU reading available".
         *      Both T-01 (`maxTemp <= 0` → UNAVAILABLE) and the footer
         *      schema treat -1 as the documented missing-data marker.
         *
         * Pure function. No side effects. Callers compute the inputs
         * once (one [SystemFileReader.readCpuTemp] call + one
         * [SamplerStats.snapshot] read) and feed them in.
         */
        internal fun selectFooterCpuTemp(
            liveCpu: Float,
            snapshotMaxTempC: Float,
            snapshotTempSource: String
        ): net.neuralcheck.data.local.files.FooterCpuTemp = when {
            liveCpu > 0f -> net.neuralcheck.data.local.files.FooterCpuTemp(
                liveCpu, net.neuralcheck.data.local.files.CpuTempSources.LIVE_READ_AT_FINALIZE
            )
            snapshotTempSource == "cpu" && snapshotMaxTempC > 0f -> net.neuralcheck.data.local.files.FooterCpuTemp(
                snapshotMaxTempC, net.neuralcheck.data.local.files.CpuTempSources.SAMPLER_MAX_CPU
            )
            // fieldsVersion 68 (S-13): "-1f" is gone from the footer. No value,
            // and the source says why — the readings the cascade saw, as text.
            else -> net.neuralcheck.data.local.files.FooterCpuTemp(
                null,
                net.neuralcheck.data.local.files.CpuTempSources.ABSENT_PREFIX +
                    "no live CPU read (readCpuTemp returned $liveCpu, not > 0) and the sampler's temperature " +
                    "source was '$snapshotTempSource'" +
                    (if (snapshotTempSource == "cpu") " with a maximum of $snapshotMaxTempC (not > 0)"
                    else ", not 'cpu'")
            )
        }

        /**
         * Phase 1.14 hotfix continuation (2026-05-18): T-02 heat-rise RATE
         * computed against the SAME max temperature T-01 uses
         * ([selectFooterMaxTemp]'s output) rather than the raw
         * [SamplerStats.Snapshot.heatRiseC] field. The Snapshot's internal
         * computation uses `maxBestTemp` which can be contaminated by
         * vendor zone-naming artifacts (the same contamination that drove
         * Samsung S24 Ultra production scan 18dfd6b4 to report T-01 = 81 °C
         * pre-hotfix). T-02 was previously inflated by the same source —
         * this helper closes the gap so T-01 and T-02 always tell a
         * consistent story.
         *
         * Inputs:
         *   • [footerMaxTemp] — the cascade-gated CPU peak from
         *     [selectFooterMaxTemp]. -1f means "no trustworthy reading";
         *     the function short-circuits to -1f (T-02 → UNAVAILABLE).
         *   • [firstBestTempC] — the first valid temperature reading
         *     captured during the scan from the SAME source-class cascade
         *     (cpu > gpu > battery). Float.NaN when no source was ever
         *     available; treated as missing data → -1f.
         *   • [scanDurationMs] — the full sampling window (first sample →
         *     last sample). Picked rather than phase-3-only
         *     `stressDurationMs` because `firstBestTempC` is captured at
         *     the first valid CPU read — usually scan start, not phase 3
         *     start — so dividing by the phase-3 window would over-state
         *     the rate. Matches the denominator [SamplerStats] uses for
         *     its own internal [Snapshot.heatRiseC] computation.
         *
         * Returns -1f (the T-02 UNAVAILABLE sentinel) when any input is
         * missing or non-positive; otherwise the °C/min rate.
         *
         * Pure function. No side effects. Testable in isolation.
         */
        internal fun computeHeatRiseFromFooter(
            footerMaxTemp: Float,
            firstBestTempC: Float,
            scanDurationMs: Long
        ): Float {
            if (footerMaxTemp <= 0f) return -1f
            if (firstBestTempC.isNaN() || firstBestTempC <= 0f) return -1f
            if (scanDurationMs <= 0L) return -1f
            val deltaC = (footerMaxTemp - firstBestTempC).coerceAtLeast(0f)
            val durationMin = scanDurationMs / 60_000f
            if (durationMin <= 0f) return -1f
            return deltaC / durationMin
        }

        /**
         * fieldsVersion 71 (A-P6-13): the pre-flight battery limits live in ONE object, [ScanBatteryWindow] (30..85 %) — it REPLACES the lone floor
         * `PRE_FLIGHT_MIN_BATTERY_PCT` (25 %, a temporary field-trial value of 2026-09-11) and that name is gone. The PreScan screen's bands and the refusal dialog
         * quote the SAME numbers the gate enforces: the original field bug was a device at 34 % showing a green pre-scan indicator and then being refused by the
         * gate with a raw token message. The drain analysis that sized the old 40 % floor (fieldsVersion 59: ~6.5 % worst-case drain for the all-8-core scan) stays in
         * the block comment at the check site in [startScan].
         */

        /**
         * Resolves the battery level the pre-flight gate should judge.
         *
         * BATTERY_PROPERTY_CAPACITY is the primary source but has two
         * documented failure sentinels the old inline read mishandled:
         * Integer.MIN_VALUE (property unsupported on this vendor fork) and
         * 0 (battery service stale — same sentinel BatteryReader.register's
         * probe treats as "not live"). Both used to flow straight into the
         * `< 40` comparison and hard-block the scan with a nonsense level.
         *
         * Order: property value when plausible (1..100) → sticky
         * ACTION_BATTERY_CHANGED LEVEL/SCALE pair → null (= unknown).
         * Pure function; unit-tested in StressTestServiceTest.
         */
        internal fun resolvePreflightBatteryLevel(
            propertyCapacityPct: Int?,
            stickyLevel: Int?,
            stickyScale: Int?
        ): Int? {
            if (propertyCapacityPct != null && propertyCapacityPct in 1..100) {
                return propertyCapacityPct
            }
            if (stickyLevel != null && stickyScale != null &&
                stickyLevel >= 0 && stickyScale > 0
            ) {
                val pct = (stickyLevel * 100) / stickyScale
                if (pct in 0..100) return pct
            }
            return null
        }

        /**
         * Pre-flight battery gate. Blocks only a KNOWN level outside [ScanBatteryWindow] (below its minimum or above its maximum); an unknown / implausible level
         * (null, or outside 0..100) passes — permissive by design, matching the old `?: 100` fallback philosophy: a device we cannot read
         * should not be locked out of scanning, and the scan's own sampling + SafetyMonitor judge the battery from there.
         * (PENDING — «معلّق: مواصفة غائبة»: what to do with an UNKNOWN level is not specified by the contract or the plan; it passes as it always did, and
         * `batteryLevelStart` declares the unknown.)
         */
        internal fun isPreflightBatteryBlocked(levelPct: Int?): Boolean = ScanBatteryWindow.refuses(levelPct)

        /**
         * fieldsVersion 66 — true maximum battery temperature of the scan.
         *
         * Builds ≤65 wrote the END-of-scan snapshot into footer.maxBatteryTemp
         * (S10 5G field scan: 38.1 written, 38.5 actual max) while the real
         * maximum was computed only for batteryHealth.batteryTempMaxC. One
         * pure helper now feeds BOTH fields. v[1] is battery_temp_c in V5;
         * non-positive readings are the "no data" sentinel and are skipped.
         * Falls back to [fallbackC] when no sample carries a positive value.
         */
        internal fun computeMaxBatteryTempC(
            samples: List<net.neuralcheck.data.local.files.TimeSeriesSample>,
            fallbackC: Float,
            batteryTempIdx: Int = net.neuralcheck.hardware.battery
                .BatteryIndicators.batteryTempIndex()
        ): Double = samples
            .asSequence()
            .filter { it.v.size > batteryTempIdx }
            .map { it.v[batteryTempIdx].toDouble() }
            .filter { it > 0.0 }
            .maxOrNull()
            ?: fallbackC.toDouble()

        /**
         * fieldsVersion 67: the stillness threshold and its decision moved
         * to [net.neuralcheck.service.sampler.StillnessClassifier] — a pure
         * object carrying the measured basis for the 0.1 → 0.5 m/s² re-base
         * (7 motionless field scans, max 0.3723) and unit-testable without
         * an Android runtime.
         */

        // Phase durations in milliseconds
        private const val PHASE_1_DURATION = 30_000L      // 30s
        private const val PHASE_2_DURATION = 90_000L      // 90s
        private const val PHASE_3_DURATION = 180_000L     // 180s
        private const val PHASE_4_DURATION = 300_000L     // 300s
        private const val PHASE_5_DURATION = 90_000L      // 90s
        private const val TOTAL_DURATION = 690_000L       // 11.5 min

        const val ACTION_START = "net.neuralcheck.action.START_SCAN"
        const val ACTION_STOP = "net.neuralcheck.action.STOP_SCAN"
        const val EXTRA_SCAN_TYPE = "scan_type"
        const val EXTRA_LINKED_SCAN_ID = "linked_scan_id"
    }

    // Injected dependencies will be wired in Phase 07
    private lateinit var cpuLoadGenerator: CpuLoadGenerator
    private lateinit var gpuLoadGenerator: GpuLoadGenerator

    private lateinit var sensorCollector: SensorCollector
    private lateinit var batteryReader: BatteryReader
    private lateinit var systemFileReader: SystemFileReader
    private lateinit var safetyMonitor: SafetyMonitor
    private lateinit var sampler: DisciplinedSampler
    private lateinit var fileWriter: TimeSeriesFileWriter

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    private var scanJob: Job? = null
    private var currentScanId: String? = null
    private var currentScanType: ScanType = ScanType.GENERAL
    private var currentLinkedScanId: String? = null
    private var scanStartTime = 0L
    private var startBatteryLevel = 0
    private var startBatteryTemp = 0f
    private var wakeLock: PowerManager.WakeLock? = null
    // Thread-safe: tests run in parallel coroutines and all add to this list.
    private val collectedResults = java.util.Collections.synchronizedList(mutableListOf<TestResult>())
    // Track background test coroutines so completeScan can await them before persisting.
    private val pendingTestJobs = java.util.Collections.synchronizedList(mutableListOf<Job>())
    // Order 2026-10-15: one-shot "this scan is being finalised" latch, FREED when a new scan starts (see ScanCompletionLatch).
    private val completionLatch = ScanCompletionLatch()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): StressTestService = this@StressTestService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        Log.d(TAG, "StressTestService.onCreate() called — sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER} ${Build.MODEL}")
        try {
            super.onCreate()
            try { createNotificationChannel() } catch (t: Throwable) {
                Log.e(TAG, "createNotificationChannel failed: ${t.message}", t)
            }
            try { initComponents() } catch (t: Throwable) {
                Log.e(TAG, "initComponents failed: ${t.message}", t)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "StressTestService.onCreate crashed: ${t.javaClass.simpleName}: ${t.message}", t)
            // Best-effort: stop the service rather than leaving it half-init.
            runCatching { stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "StressTestService.onStartCommand(action=${intent?.action}, flags=$flags, startId=$startId) called")
        try {
            super.onStartCommand(intent, flags, startId)
            when (intent?.action) {
                ACTION_START -> {
                    val scanType = intent.getStringExtra(EXTRA_SCAN_TYPE) ?: "general"
                    val linkedScanId = intent.getStringExtra(EXTRA_LINKED_SCAN_ID)
                    startScan(ScanType.fromString(scanType), linkedScanId)
                }
                ACTION_STOP -> stopScan()
            }
        } catch (t: Throwable) {
            // Catch-all so a failure deep inside startScan can't propagate
            // up into the OS and crash the process. We try to surface a
            // SafetyStopped state so the UI can show an explanatory error
            // banner instead of just freezing on the loading screen.
            Log.e(TAG, "onStartCommand crashed: ${t.javaClass.simpleName}: ${t.message}", t)
            runCatching { _scanState.value = ScanState.SafetyStopped("INIT_FAILED:${t.javaClass.simpleName}") }
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            runCatching { stopSelf() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Only cancel the scan if one is still running — don't re-enter completeScan if already completed
        if (!completionLatch.isCompleting && currentScanId != null) {
            stopScan()
        }
        // Guard against leaking the wake lock if completeScan's coroutine never runs.
        releaseWakeLock()
        super.onDestroy()
    }

    /** V2 helper, instantiated lazily (only when USE_V2_STRESS=true). */
    private var v2Service: net.neuralcheck.service.stress.v2.StressTestServiceV2? = null
    /** V2 scan result, captured at end-of-V2-execution and consumed by
     *  finalize() to populate the time-series footer. */
    @Volatile private var v2Result: net.neuralcheck.service.stress.v2.StressTestServiceV2.V2ScanResult? = null

    /** V5 schema (Task D): channel-discovery report run once at scan start.
     *  Captures which signal sources actually responded so the footer can
     *  carry availability flags instead of forcing the AI to infer them
     *  from sentinel values inside v[]. Reset to null at the top of
     *  startScan, populated before sampler.start, consumed by completeScan. */
    @Volatile
    private var channelDiscoveryReport:
        net.neuralcheck.data.local.files.ChannelDiscoveryReport? = null

    /** fieldsVersion 68 (N-06): the cpufreq policies channel discovery found,
     *  for the footer. null = discovery did not run or failed this scan. Reset
     *  with [channelDiscoveryReport], consumed by completeScan. */
    @Volatile
    private var scanCpuPolicies: List<net.neuralcheck.data.local.files.CpuPolicyInfo>? = null

    /** fieldsVersion 68 (ت-03, ت-04): records the slow thermal channels as lists
     *  for THIS scan. null = not started (V1 flavor, or the scan never got that
     *  far); created per scan, stopped and read by completeScan. */
    @Volatile
    private var thermalLists: net.neuralcheck.service.sampler.ThermalListCollector? = null

    /** fieldsVersion 68 (ت-06): what the battery receiver recorded for THIS scan,
     *  captured in completeScan before the receiver is unregistered. null = no
     *  recording was started (V1 flavor, or the scan never got that far). */
    @Volatile
    private var batteryPairsRecord: net.neuralcheck.data.local.files.BatteryPairsRecord? = null

    /** Phase 1.8 Task 5 — raw relative-calibration probe results.
     *  Populated when [BuildConfig.USE_RELATIVE_CALIBRATION] is true and
     *  the probe completes (even partially — failed subsystems are
     *  flagged inside the profile). Stays null otherwise. Consumed by
     *  [completeScan] to write the calibration block into the footer. */
    @Volatile
    private var deviceProfile:
        net.neuralcheck.calibration.DeviceCapabilityProfile? = null

    /** Phase 1.8 Task 5 — calibrated workload params derived from
     *  [deviceProfile]. Same lifecycle: set when flag on + probe
     *  produced a usable profile; otherwise null. Consumed by V2
     *  service for per-phase config + by completeScan for the footer. */
    @Volatile
    private var calibratedConfig:
        net.neuralcheck.calibration.CalibratedWorkloadConfig? = null

    private fun initComponents() {
        cpuLoadGenerator = CpuLoadGenerator()
        gpuLoadGenerator = GpuLoadGenerator()
        GpuFpsTracker.gpuLoadGenerator = gpuLoadGenerator

        sensorCollector = SensorCollector(this)
        batteryReader = BatteryReader(this)
        // Pass the service Context so SystemFileReader can use the
        // HardwarePropertiesManager fallback for CPU/GPU temperature on
        // devices where /sys/class/thermal is empty (Samsung A30s).
        systemFileReader = SystemFileReader(this)
        fileWriter = TimeSeriesFileWriter(this)
        safetyMonitor = SafetyMonitor(batteryReader, systemFileReader)
        sampler = DisciplinedSampler(
            sensorCollector, batteryReader, systemFileReader, fileWriter,
            // V2 needs context for ThermalFallback (skin_temp index 18)
            // and for any future API that needs system services. The
            // service itself is a Context — pass it.
            context = this@StressTestService
        )
    }

    fun startScan(scanType: ScanType, linkedScanId: String?) {
        Log.d(TAG, "StressTestService.startScan(scanType=$scanType, linked=$linkedScanId) called")
        if (_scanState.value is ScanState.Running) {
            Log.d(TAG, "startScan: already running — ignoring duplicate call")
            return
        }

        // ── Stage 1: enter foreground IMMEDIATELY ──────────────────────
        // Android 12+ throws ForegroundServiceDidNotStartInTimeException
        // (and on Android 14+ outright ForegroundServiceStartNotAllowed-
        // Exception) if startForeground is not called within ~5 s of
        // startForegroundService. Even early-return paths (power save,
        // missing permission) MUST first satisfy the foreground contract,
        // then call stopSelf to release.
        //
        // We use FOREGROUND_SERVICE_TYPE_DATA_SYNC on API 29+ (matching the
        // matching permission + manifest type). DATA_SYNC fits the scan —
        // an 8-minute telemetry collection that uploads to Firestore on
        // completion — and unlike SPECIAL_USE does NOT require a Play
        // Console grant, which sidesteps the Samsung S24 Ultra rejection.
        // On API 26-28 we fall through to the un-typed overload (the
        // foreground-service-type concept didn't exist yet).
        val foregroundOk = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(1, 0),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, buildNotification(1, 0))
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground DATA_SYNC failed: ${t.javaClass.simpleName}: ${t.message}", t)
            try {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, buildNotification(1, 0))
                true
            } catch (t2: Throwable) {
                Log.e(TAG, "startForeground fallback failed: ${t2.javaClass.simpleName}: ${t2.message}", t2)
                false
            }
        }
        if (!foregroundOk) {
            _scanState.value = ScanState.SafetyStopped("FOREGROUND_DENIED")
            runCatching { stopSelf() }
            return
        }

        // ═══ Pre-flight safety checks (V2 fix #30) ═══
        // الفحص يضغط الجهاز بقوة. نرفض البدء لو الجهاز غير جاهز.
        // الفلسفة: حماية مسبقة بدل تخفيف الضغط لاحقاً.

        // 1. مستوى البطارية — نحتاج طاقة كافية لاكتمال 8 دقائق
        //
        // Historical measurement (V2 fix #56-3 / Commit 2, fieldsVersion 59;
        // KEPT for the record — the limits themselves now live in
        // ScanBatteryWindow (30..85 %, fieldsVersion 71), see its KDoc):
        // the all-8-core workload raised sustained power draw from ~5 W →
        // ~7-8 W, lifting expected scan-end battery drain from ~3.9% →
        // ~6.5% on Samsung-class flagships. That analysis sized the floor
        // at 40% (a worst-case scan lands near ~33% post-scan); the
        // old temporary 25% trial floor accepted a ~18% landing, the 30% floor
        // of fieldsVersion 71 a ~23% one.
        val preflightBatteryManager = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        // Sticky battery intent hoisted above the level gate: it is both the
        // CAPACITY fallback for check 1 AND the temp/charger source for
        // checks 2 + 3 below.
        val preflightBatteryIntent = registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        // fix/prescan-battery-gate: the old inline read (`getIntProperty ?:
        // 100`) only covered a null BatteryManager — the property's own
        // failure sentinels (Integer.MIN_VALUE = unsupported, 0 = stale
        // service) flowed into the `< 40` comparison and hard-blocked the
        // scan with a nonsense level. resolvePreflightBatteryLevel falls
        // back to the sticky intent's LEVEL/SCALE, and a still-unknown
        // level passes the gate (see isPreflightBatteryBlocked's contract).
        val preflightBatteryLevel = resolvePreflightBatteryLevel(
            propertyCapacityPct = preflightBatteryManager?.getIntProperty(
                android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
            ),
            stickyLevel = preflightBatteryIntent
                ?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1),
            stickyScale = preflightBatteryIntent
                ?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        )
        if (isPreflightBatteryBlocked(preflightBatteryLevel)) {
            val below = ScanBatteryWindow.positionOf(preflightBatteryLevel) == ScanBatteryWindow.Position.BELOW
            Log.w(TAG, "pre-flight rejected: battery $preflightBatteryLevel% " +
                (if (below) "< ${ScanBatteryWindow.MIN_PCT}%" else "> ${ScanBatteryWindow.MAX_PCT}%"))
            // The reason token carries the measured level
            // ("BATTERY_TOO_LOW:19") so ActiveScanScreen's dialog can say
            // "requires ≥ScanBatteryWindow.MIN_PCT% — currently 19%"
            // instead of leaking the raw token. The bare "BATTERY_TOO_LOW"
            // prefix stays parseable for any consumer that prefix-matches.
            // fieldsVersion 71: above the window the token is "BATTERY_TOO_HIGH:<level>"; its dialog text is A5h-2 (approved by delegation
            // 2026-10-23, R.string.scan_error_battery_window_fmt), read by ActiveScanScreen through batteryAboveWindowLevel.
            _scanState.value = ScanState.SafetyStopped(
                (if (below) ScanBatteryWindow.REFUSAL_BELOW else ScanBatteryWindow.REFUSAL_ABOVE) + ":$preflightBatteryLevel"
            )
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            runCatching { stopSelf() }
            return
        }

        // 2. حرارة البطارية الابتدائية — جهاز ساخن سينتهي مبكراً
        val preflightBatteryTempStart = preflightBatteryIntent
            ?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0)
            ?.let { it / 10f } ?: 25f
        if (preflightBatteryTempStart > 38f) {
            Log.w(TAG, "pre-flight rejected: battery temp ${preflightBatteryTempStart}°C > 38°C")
            _scanState.value = ScanState.SafetyStopped("DEVICE_TOO_HOT")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            runCatching { stopSelf() }
            return
        }

        // 3. الشاحن — قياسات الـ stress تعكس الجهاز نفسه ليس "الجهاز + الشاحن".
        // الشاحن يُلوّث drain measurements ويُسبب false negative على بطارية تالفة.
        val preflightChargingState = preflightBatteryIntent?.getIntExtra(
            android.os.BatteryManager.EXTRA_PLUGGED, 0
        ) ?: 0
        val preflightIsCharging = preflightChargingState != 0
        if (preflightIsCharging) {
            Log.w(TAG, "pre-flight rejected: charger connected")
            _scanState.value = ScanState.SafetyStopped("CHARGER_CONNECTED")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            runCatching { stopSelf() }
            return
        }

        // 4. مساحة التخزين — نحتاج 6 GB متاحة (4 GB للـ stress + 2 GB headroom)
        val preflightCacheDirSpace = cacheDir.usableSpace
        val preflightMinStorageBytes = 6L * 1024L * 1024L * 1024L
        if (preflightCacheDirSpace < preflightMinStorageBytes) {
            Log.w(TAG, "pre-flight rejected: cache space " +
                "${preflightCacheDirSpace / 1024 / 1024} MB < 6 GB")
            _scanState.value = ScanState.SafetyStopped("STORAGE_FULL")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            runCatching { stopSelf() }
            return
        }

        // 5. ذاكرة RAM متاحة — نحتاج 1 GB متاحة كحد أدنى
        val preflightActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val preflightMemInfo = ActivityManager.MemoryInfo()
        preflightActivityManager?.getMemoryInfo(preflightMemInfo)
        val preflightAvailableRamMb = (preflightMemInfo.availMem / 1024 / 1024).toInt()
        if (preflightAvailableRamMb < 1024) {
            Log.w(TAG, "pre-flight rejected: available RAM ${preflightAvailableRamMb} MB < 1 GB")
            _scanState.value = ScanState.SafetyStopped("RAM_INSUFFICIENT")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            runCatching { stopSelf() }
            return
        }

        Log.i(TAG, "pre-flight passed: battery=${preflightBatteryLevel ?: "unknown"}% " +
            "temp=${preflightBatteryTempStart}°C charger=false " +
            "cache=${preflightCacheDirSpace / 1024 / 1024 / 1024}GB ram=${preflightAvailableRamMb}MB")
        // ═══ End pre-flight checks ═══

        // ── Stage 2: power-save / power manager guard ───────────────────
        val powerManager = try {
            getSystemService(Context.POWER_SERVICE) as? PowerManager
        } catch (t: Throwable) {
            Log.w(TAG, "POWER_SERVICE unavailable: ${t.message}")
            null
        }
        // fieldsVersion 71 (A-P6-13): the state is off | on | read_failed, never a failed read written as "off" (rule 22). Only "on" stops the scan:
        // a failed read starts it and is declared (PENDING: whether it should refuse — «[اجتهاد]» in the plan, P:1433).
        val powerSave = PowerSaveReading.read { powerManager?.isPowerSaveMode }
        if (powerSave.state == PowerSaveReading.READ_FAILED) Log.w(TAG, "isPowerSaveMode read failed: ${powerSave.failure}")
        if (powerSave.stopsScan) {
            _scanState.value = ScanState.SafetyStopped("POWER_SAVE_MODE")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            runCatching { stopSelf() }
            return
        }

        // Order 2026-10-15 (batch item 3): this scan starts with a free completion latch. A service instance that outlived a
        // finished scan still held the previous scan's latch, so this scan's completeScan returned at once and never persisted.
        completionLatch.resetForNewScan()

        val scanId = UUID.randomUUID().toString()
        currentScanId = scanId
        // Order 2026-10-14 (user decision «Record the session at scan start»): the scan keeps the session it started
        // in. One call, here, the moment the id exists and before anything else can run for this scan; it prints
        // scan-session-recorded either way (session=missing = a personal scan). A failure here must not stop the scan:
        // the scan then has no record and stays personal, and the line below says so.
        runCatching { scanSessionRecords.recordAtScanStart(scanId) }
            .onFailure { Log.w(TAG, "scan session record failed: ${it.javaClass.name}") }
        currentScanType = scanType
        currentLinkedScanId = linkedScanId
        collectedResults.clear()
        pendingTestJobs.clear()

        // ── Stage 3: best-effort init of every collector ────────────────
        // Each runCatching is independent: a failing wake lock or sensor
        // registration must not abort the whole scan, only degrade it.
        if (powerManager != null) {
            runCatching { acquireWakeLock(powerManager) }
                .onFailure { Log.w(TAG, "acquireWakeLock failed: ${it.message}") }
        }
        runCatching { batteryReader.register() }
            .onFailure { Log.w(TAG, "batteryReader.register failed: ${it.message}") }
        runCatching { sensorCollector.start() }
            .onFailure { Log.w(TAG, "sensorCollector.start failed: ${it.message}") }
        runCatching { systemFileReader.dumpAllThermalZones() }
            .onFailure { Log.w(TAG, "thermal dump failed: ${it.message}") }

        val battery = runCatching { batteryReader.snapshot() }
            .getOrElse {
                Log.w(TAG, "batteryReader.snapshot failed: ${it.message}")
                net.neuralcheck.service.collector.BatterySnapshot(
                    tempCelsius = 0f, voltageMv = 0, currentUa = 0,
                    levelPercent = -1, health = 0, isCharging = false,
                    plugType = "None", technology = "", status = 0
                )
            }
        scanStartTime = SystemClock.elapsedRealtime()
        startBatteryLevel = battery.levelPercent
        startBatteryTemp = battery.tempCelsius
        // fieldsVersion 68 (ت-06): from here the battery receiver (registered
        // above) also keeps the raw pair of every broadcast, on the clock of v[0].
        if (net.neuralcheck.BuildConfig.USE_V2_STRESS) {
            runCatching { batteryReader.startBatteryPairs(scanStartTime) }
                .onFailure { Log.w(TAG, "battery pair recording start failed: ${it.message}") }
        }

        // startScan is non-suspend (called from onStartCommand) so the suspend
        // generateProfileKey has to be bridged via runBlocking here. The
        // header + ScanEntity construction below need deviceFp synchronously,
        // and lifecycleScope.launch would defer the value past those reads.
        // Same blocking cost as the previous @Deprecated shim — just made
        // local and explicit so the next refactor can see it.
        val deviceProfileKey = runCatching {
            runBlocking { DeviceFingerprint.generateProfileKey(this@StressTestService) }
        }.getOrElse {
            Log.w(TAG, "DeviceFingerprint.generateProfileKey failed: ${it.message}")
            DeviceProfileKey(
                primary = "unknown_fp",
                layerA_keystore = "no_keystore",
                layerB_firebaseId = "no_fid",
                layerC_androidId = "no_android_id",
                layerD_buildSig = "unknown",
                layerE_sensorSig = "no_sensors"
            )
        }
        val deviceFp = deviceProfileKey.primary

        // Capture the signed-in user's uid at scan-creation time so the row
        // is owner-tagged in Room. Read directly from Firebase.auth instead
        // of injecting AuthManager — the service already pulls in
        // FirebaseAuth transitively via Hilt graph and the read is a single
        // property access with zero failure modes. A customer-device scan run
        // via a session code carries the device's anonymous uid: since
        // 142975e the join signs the device in anonymously BEFORE
        // joinScanSession (joinScanSessionWithDeviceIdentity), so the uid is
        // already here. Null only when:
        //   - that anonymous sign-in failed or timed out (the join then
        //     proceeds without an identity — expected; ownerId stays tracked
        //     server-side through the session),
        //   - the user signed out between bind and start (rare race).
        val ownerUid = runCatching {
            Firebase.auth.currentUser?.uid
        }.getOrNull()

        // Persist scan record to Room (so it shows in history immediately).
        // Already inside its own try/catch since it runs in a coroutine.
        lifecycleScope.launch {
            try {
                scanRepository.createScan(ScanEntity(
                    scanId = scanId,
                    deviceFingerprint = deviceFp,
                    scanType = ScanType.toDbString(scanType),
                    linkedScanId = linkedScanId,
                    timestamp = System.currentTimeMillis(),
                    batteryLevelStart = battery.levelPercent,
                    batteryLevelEnd = null,
                    ambientTempEstimate = battery.tempCelsius,
                    appVersion = net.neuralcheck.BuildConfig.VERSION_NAME,
                    androidApiLevel = Build.VERSION.SDK_INT,
                    deviceModel = net.neuralcheck.util.DeviceName.current(),
                    androidVersion = Build.VERSION.RELEASE,
                    isComplete = false,
                    earlyStopReason = null,
                    earlyStopAtMs = null,
                    timeSeriesFilePath = null,
                    isSynced = false,
                    userId = ownerUid
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist scan record: ${e.message}")
            }
        }

        // ── Stage 4: time-series header + writer ────────────────────────
        val totalRamMb = try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                mi.totalMem / (1024 * 1024)
            } else 0L
        } catch (t: Throwable) {
            Log.w(TAG, "ActivityManager.getMemoryInfo failed: ${t.message}")
            0L
        }
        val header = TimeSeriesHeader(
            scanId = scanId,
            deviceFingerprint = deviceFp,
            scanType = ScanType.toDbString(scanType),
            appVersion = net.neuralcheck.BuildConfig.VERSION_NAME,
            androidApiLevel = Build.VERSION.SDK_INT,
            deviceModel = net.neuralcheck.util.DeviceName.current(),
            cpuCores = runCatching { Runtime.getRuntime().availableProcessors() }.getOrDefault(1),
            totalRamMb = totalRamMb,
            startTimestamp = System.currentTimeMillis(),
            batteryLevelStart = battery.levelPercent,
            batteryTempStart = battery.tempCelsius,
            isPowerSaveMode = powerSave.isPowerSaveMode,
            powerSaveState = powerSave.state
        )
        runCatching { fileWriter.startNewFile(header) }
            .onFailure { Log.w(TAG, "fileWriter.startNewFile failed: ${it.message}") }

        // ── Stage 5: safety monitor + sampler ───────────────────────────
        runCatching {
            safetyMonitor.start(battery.levelPercent) { reason ->
                lifecycleScope.launch { onSafetyStop(reason) }
            }
        }.onFailure { Log.w(TAG, "safetyMonitor.start failed: ${it.message}") }

        // V2 mode: enable 22-var samples BEFORE sampler starts so the
        // first sample carries skin_temp + package_power + V2 indices.
        // Bug 7 fix: also tell the file writer to emit 22-entry variables[]
        // in the footer so consumers can read v[18..21] with a schema.
        if (net.neuralcheck.BuildConfig.USE_V2_STRESS) {
            runCatching { sampler.enableV2Mode() }
                .onFailure { Log.w(TAG, "sampler.enableV2Mode failed: ${it.message}") }
            runCatching { fileWriter.enableV2Mode() }
                .onFailure { Log.w(TAG, "fileWriter.enableV2Mode failed: ${it.message}") }
        }

        // V2 fix #56 (2.4): reset ThermalFallback's per-skin-source tally
        // before this scan starts so DataQuality reflects this scan only.
        runCatching { net.neuralcheck.util.ThermalFallback.resetSourceTracking() }

        // V5 schema (Task D): channel discovery probe runs ONCE at scan
        // start, before any sampler tick fires. The report is captured here
        // and copied into the footer by completeScan (Task G) so the AI
        // sees an authoritative inventory of which signals were reachable
        // on this device, distinct from what showed up in v[].
        channelDiscoveryReport = null
        scanCpuPolicies = null
        thermalLists = null
        batteryPairsRecord = null
        if (net.neuralcheck.BuildConfig.USE_V2_STRESS) {
            // fieldsVersion 68: discovery now also decides this scan's column
            // layout (which per-device channels follow v[0..10]). The layout is
            // handed to BOTH the sampler (width of every sample) and the file
            // writer (variables[]) BEFORE the sampler starts, and they get the
            // very same list, so a sample is always as wide as the list that
            // declares it. If the probe fails, both fall back to the plain
            // 11-column base explicitly — never to whatever the previous scan
            // left behind.
            val discovery = runCatching {
                net.neuralcheck.service.stress.v2.ChannelDiscoveryProbe.run(
                    context = this@StressTestService,
                    batteryReader = batteryReader,
                    systemFileReader = systemFileReader
                )
            }
            val layout = discovery.getOrNull()?.layout
                ?: net.neuralcheck.data.local.files.ScanChannelLayout.BASE_ONLY
            runCatching {
                sampler.configureExtraChannels(layout.extras)
                fileWriter.setVariableDefinitions(layout.variables)
            }.onFailure { Log.w(TAG, "applying the channel layout failed: ${it.message}") }
            discovery.onSuccess { result ->
                channelDiscoveryReport = result.report
                scanCpuPolicies = result.cpuPolicies
                Log.i(
                    TAG,
                    "channelDiscovery: ${result.report.summary}; " +
                        "layout: ${layout.sampleWidth} columns (extras=${layout.extras.map { it.name }})"
                )
            }.onFailure {
                Log.w(TAG, "channelDiscovery probe failed: ${it.message}")
            }
        }

        // Phase 1.8 Task 5: reset calibration state. The probe itself
        // is a suspend fun (~10–15 s wall clock) and runs INSIDE the
        // scanJob coroutine below — calling it on the main thread via
        // runBlocking would ANR. Crucially, sampler.start was MOVED into
        // the same coroutine so the probe runs BEFORE sampling begins;
        // otherwise the probe's CPU/memory/storage activity would
        // contaminate the early phase-1 samples.
        deviceProfile = null
        calibratedConfig = null

        // ── Stage 6: phase orchestrator ─────────────────────────────────
        // Outer try/catch so any uncaught exception inside executePhases
        // (including launches we don't control) finalises the scan rather
        // than leaving the foreground service zombied.
        scanJob = lifecycleScope.launch {
            try {
                // Phase 1.8 Task 5 — relative-calibration probe. Gated on
                // BuildConfig.USE_RELATIVE_CALIBRATION so the flag-off
                // path is bit-for-bit identical to pre-Task-5: skip the
                // probe entirely and proceed straight to sampler.start
                // → executePhasesV2 with deviceProfile = calibratedConfig
                // = null, which the V2 service handles by falling back
                // to AdaptiveIntensityController.aiConfigForTier.
                if (net.neuralcheck.BuildConfig.USE_RELATIVE_CALIBRATION &&
                    net.neuralcheck.BuildConfig.USE_V2_STRESS) {
                    try {
                        val profile = probePhase.run(this@StressTestService.cacheDir)
                        val config = net.neuralcheck.calibration.WorkloadCalibration
                            .calibrate(profile)
                        deviceProfile = profile
                        calibratedConfig = config
                        Log.i(
                            TAG,
                            "calibration applied: failed=${profile.probeFailedSubsystems} " +
                                "memMbps=${"%.0f".format(profile.memoryMbps)} " +
                                "cpuInfPerSec=${"%.0f".format(profile.cpuInferencesPerSec)} " +
                                "gpuFps=${"%.0f".format(profile.gpuFpsAt1024Sq)} " +
                                "stMbps=${"%.0f".format(profile.storageMbps)} " +
                                "→ memHz=${"%.0f".format(config.memoryIterationRateHz)} " +
                                "cpuHz=${"%.0f".format(config.cpuInferencesPerSec)} " +
                                "fboPx=${config.gpuFboSidePixels} " +
                                "stBps=${config.storageWriteBytesPerSec}"
                        )
                    } catch (t: Throwable) {
                        Log.e(
                            TAG,
                            "ProbePhase.run threw — falling back to tier-based config: " +
                                "${t.javaClass.simpleName}: ${t.message}",
                            t
                        )
                        deviceProfile = null
                        calibratedConfig = null
                    }
                }

                // Sampler must start AFTER the probe so the probe's
                // memory + storage I/O doesn't show up as phase-1
                // samples. When the flag is off, the probe block above
                // is skipped entirely and this runs immediately — same
                // behaviour as pre-Task-5 (just under a coroutine
                // launch instead of inline).
                //
                // A46 fix: pass the real initial phase into start() itself
                // instead of setting it afterwards. DisciplinedSampler.start()
                // schedules its first tick with a 0ms initial delay on its
                // own dedicated "NeuralCheck-Sampler" thread; currentPhase
                // used to default to 1 (AtomicInteger(1)) until something
                // called setPhase(). For the V2 path, that setPhase() call
                // lived inside phaseDriver — a SEPARATE coroutine launched
                // later, deep in executePhasesV2 (after building the V2
                // helper and launching three more pendingTestJobs
                // coroutines) — so whether the true initial phase (0 =
                // INIT_PROFILING) was applied before or after the sampler's
                // first tick depended on dispatcher scheduling. That
                // initialization race is why the first sample came back
                // tagged phase=1 in some field checks and not others.
                // start()'s initialPhase parameter sets currentPhase before
                // the tick is even scheduled, so there is no window left to
                // race. V1's initialPhase stays the default (1), matching
                // its own executePhases(), whose first statement is
                // sampler.setPhase(1) anyway — behaviour there is unchanged.
                val initialSamplerPhase = if (net.neuralcheck.BuildConfig.USE_V2_STRESS) {
                    net.neuralcheck.data.local.files.V2Phase.INIT_PROFILING.phaseId
                } else {
                    1
                }
                runCatching { sampler.start(scanStartTime, initialSamplerPhase) }
                    .onFailure { Log.w(TAG, "sampler.start failed: ${it.message}") }
                // fieldsVersion 68 (ت-03, ت-04): the slow thermal channels run
                // beside the sampler on the same clock (t = ms since
                // scanStartTime, the frame of v[0]); the V1 flavor keeps its
                // 18-column layout and records no lists.
                if (net.neuralcheck.BuildConfig.USE_V2_STRESS) {
                    val collector = net.neuralcheck.service.sampler.ThermalListCollector(
                        systemFileReader.thermalPlatform()
                    )
                    thermalLists = collector
                    runCatching { collector.start(scanStartTime) }
                        .onFailure { Log.w(TAG, "thermal list collector start failed: ${it.message}") }
                }

                if (net.neuralcheck.BuildConfig.USE_V2_STRESS) {
                    executePhasesV2(scanId)
                } else {
                    executePhases(scanId)
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "executePhases crashed: ${t.javaClass.simpleName}: ${t.message}", t)
                runCatching {
                    completeScan(scanId, isComplete = false, earlyStopReason = "executor_crash")
                }
            }
        }
    }

    /**
     * V2 stress orchestrator (master plan §2). 480 s total = 8 phases:
     *   0 INIT_PROFILING → adaptive intensity probe + static tests
     *   1 STORAGE_STRESS → existing storage tests + V1 generator off
     *   2 MEMORY_STRESS  → runMemoryVerify (40% RAM, capped) then the AI memory workload
     *   3 CPU_SUSTAINED  → startCpuStress (NDK workers)
     *   4 GPU_SUSTAINED  → GpuLoadGeneratorV2 + compute GEMM verify
     *   5 COMBINED_PEAK  → CPU + GPU concurrently
     *   6 BURST_PATTERN  → 5 cycles 6s peak + 3s idle (VRM probe)
     *   7 COOLDOWN       → idle, measure thermal recovery
     *
     * The sampler keeps running across all phases at per-phase Hz; the
     * V2 helper class drives kernel calls and writes V2 result into
     * v2Result for finalize() to copy into the time-series footer.
     */
    private suspend fun executePhasesV2(scanId: String) {
        try {
            // Build the V2 helper. Re-instantiating on every scan is
            // cheap (no DI graph, no thread pool) — keeps state per-scan
            // clean and avoids carrying stale telemetry across runs.
            // V2 fix #FIX-1.A/B: pass the active sampler so the storage
            // and memory phases can publish per-segment / end-of-phase
            // bandwidth into v[20] / v[21]. Setters existed since V2
            // launch but had zero callers (audit
            // docs/audits/mobile_data_collection_audit_v1/02_per_channel_walkthrough.md
            // §v[20]/§v[21]) — wiring them here closes the dead-channel.
            val helper = net.neuralcheck.service.stress.v2.StressTestServiceV2(
                context = this@StressTestService,
                sampler = sampler,
                // Phase 1.6.B: pass the Hilt-injected AI workload. V2
                // service's USE_AI_WORKLOAD_CPU flag decides whether
                // phases 3 + 5 actually invoke it.
                aiCpuWorkload = aiCpuWorkload,
                // Phase 1.6.C: GPU AI workload + raymarching shader
                // controlled by the V2 service's USE_AI_WORKLOAD_GPU flag.
                aiGpuWorkload = aiGpuWorkload,
                // Phase 1.6.D: memory workload replaces March-C kernel
                // under USE_AI_WORKLOAD_MEMORY.
                aiMemoryWorkload = aiMemoryWorkload,
                // Phase 1.8 Task 5: relative-calibration result, computed
                // by the pre-scan ProbePhase + WorkloadCalibration. Null
                // when USE_RELATIVE_CALIBRATION flag is off OR when the
                // probe failed entirely; the V2 service falls back to
                // tier-based config (Phase 1.7 behaviour preserved).
                calibratedConfig = calibratedConfig,
                // G2/P2.3: same scan-start reference DisciplinedSampler.start()
                // was given a few lines above (initialSamplerPhase block),
                // so PhaseBoundary.startMs/endMs land in the same frame as v[0].
                scanStartTime = scanStartTime
            )
            v2Service = helper

            // Static functional tests run in parallel during INIT — same
            // pattern as V1 phase 1, so the report still has B/C/G/M/N
            // results regardless of which stress path executed.
            pendingTestJobs += lifecycleScope.launch {
                runCatching { collectedResults.addAll(functionalTestRunner.runAllStaticTests()) }
                    .onFailure { Log.w(TAG, "V2 static tests failed: ${it.message}") }
            }
            pendingTestJobs += lifecycleScope.launch {
                runCatching { collectedResults.addAll(functionalTestRunner.runStorageTests()) }
                    .onFailure { Log.w(TAG, "V2 storage tests failed: ${it.message}") }
            }
            pendingTestJobs += lifecycleScope.launch {
                runCatching { collectedResults.addAll(functionalTestRunner.runSensorTests()) }
                    .onFailure { Log.w(TAG, "V2 sensor tests failed: ${it.message}") }
                runCatching { collectedResults.addAll(functionalTestRunner.runGpsTests()) }
                    .onFailure { Log.w(TAG, "V2 gps tests failed: ${it.message}") }
                runCatching { collectedResults.addAll(functionalTestRunner.runCameraTests()) }
                    .onFailure { Log.w(TAG, "V2 camera tests failed: ${it.message}") }
            }

            // Bug fix #19: phaseDriver now reads helper's current phase
            // directly from the throttler's AtomicInteger instead of
            // walking a fixed timeline. helper.runV2Scan calls
            // throttler.setCurrentPhase(N) at the start of each phase;
            // phaseDriver polls throttler.getCurrentPhase() every 500ms
            // and advances the sampler to match. This is event-driven
            // synchronisation: sampler always reflects helper's true
            // phase, regardless of how slow or fast individual phases
            // actually run. Previous attempts (timeline-based, then
            // 3x-overrun-with-v2Result-exit-signal) couldn't keep
            // phase 7 (COOLDOWN) populated because the helper
            // returned its result before phaseDriver could observe
            // the phase 7 transition.
            val phaseDriver = lifecycleScope.launch {
                val phases = net.neuralcheck.data.local.files.V2Phase.values()
                val totalDurationMs = net.neuralcheck.data.local.files.V2Phase.TOTAL_DURATION_MS
                val phasesById = phases.associateBy { it.phaseId }

                // Track which phase we last advanced the sampler into,
                // so we only call sampler.setPhase / switchToHz when
                // the throttler reports a NEW phase.
                var lastObservedPhaseId = -1
                val scanStartMs = SystemClock.elapsedRealtime()

                // V2 fix #30: حتى أثناء phase 0 INIT_PROFILING (30 ثانية)،
                // نُحدّث UI ليعرف المستخدم أن الفحص بدأ. الـ helper بحاجة
                // ثانية أو ثانيتين لإنشاء throttler — هذا التحديث يُغطي
                // فترة الانتظار الأولى ثم phase 0 الكاملة.
                //
                // A46: sampler.start(scanStartTime, initialSamplerPhase) — see
                // the scanJob launch above, before this coroutine even
                // exists — already passed phaseId 0 into the sampler before
                // its first tick was scheduled, so the first sample(s) are
                // no longer at risk of being tagged with DisciplinedSampler's
                // default currentPhase=1. The setPhase() call below is now a
                // harmless re-assertion of the same value once this
                // coroutine gets scheduled; switchToHz(2) below is still the
                // only place INIT_PROFILING's 2 Hz sampling rate gets applied.
                val initialPhase = phases.first()  // INIT_PROFILING
                sampler.setPhase(initialPhase.phaseId)
                sampler.switchToHz(initialPhase.samplingHz)
                runCatching {
                    val initBattery = batteryReader.snapshot()
                    updateState(ScanState.Running(
                        phase = mapV2Phase(initialPhase),
                        progressPercent = 0f,
                        elapsedSeconds = 0,
                        scanId = scanId,
                        batteryTemp = initBattery.tempCelsius,
                        cpuTemp = systemFileReader.readCpuTemp(),
                        gpuTemp = systemFileReader.readGpuTemp(),
                        cpuUsage = systemFileReader.readCpuUsage(),
                        batteryLevel = initBattery.levelPercent,
                        v2PhaseId = initialPhase.phaseId
                    ))
                }

                while (true) {
                    if (safetyMonitor.isTriggered()) break
                    if (v2Result != null) break  // helper finished — sampler can stop

                    // Read helper's current phase via the exposed throttler
                    // reference. activeThrottler is null in two cases:
                    //   1. helper hasn't constructed it yet (race at scan
                    //      start — phaseDriver and helper start concurrently)
                    //   2. helper finished and cleared it (scan complete)
                    // We distinguish via v2Result: if it's set, we're done;
                    // otherwise we wait for helper to catch up.
                    val activeThrottler = helper.activeThrottler
                    if (activeThrottler == null) {
                        if (v2Result != null) break  // helper finished

                        // V2 fix #30: أثناء الانتظار، استمر في تحديث UI
                        // بحالة phase 0 وتقدم زمني فعلي بدل تجميد على 0%.
                        val elapsedTotal = SystemClock.elapsedRealtime() - scanStartMs
                        val progressPct = (elapsedTotal.toFloat() / totalDurationMs * 100f)
                            .coerceIn(0f, 100f)
                        runCatching {
                            val battery = batteryReader.snapshot()
                            updateState(ScanState.Running(
                                phase = mapV2Phase(initialPhase),
                                progressPercent = progressPct,
                                elapsedSeconds = (elapsedTotal / 1000L).toInt(),
                                scanId = scanId,
                                batteryTemp = battery.tempCelsius,
                                cpuTemp = systemFileReader.readCpuTemp(),
                                gpuTemp = systemFileReader.readGpuTemp(),
                                cpuUsage = systemFileReader.readCpuUsage(),
                                batteryLevel = battery.levelPercent,
                                v2PhaseId = initialPhase.phaseId
                            ))
                        }

                        kotlinx.coroutines.delay(500L)
                        continue
                    }
                    val helperPhaseId = activeThrottler.getCurrentPhase()

                    // Detect helper phase change → advance sampler.
                    if (helperPhaseId != lastObservedPhaseId &&
                        helperPhaseId in phasesById.keys
                    ) {
                        val phase = phasesById.getValue(helperPhaseId)
                        sampler.setPhase(phase.phaseId)
                        sampler.switchToHz(phase.samplingHz)
                        Log.i(TAG, "phaseDriver: helper advanced to phase " +
                            "${phase.phaseId} (${phase.phaseName}) — sampler synced")
                        lastObservedPhaseId = helperPhaseId
                    }

                    // Emit ScanState + notification update (UI feed).
                    // Use elapsed since scan start vs total nominal as
                    // a rough progress indicator. The progress can
                    // exceed 100% briefly if helper is slow — clamp it.
                    val elapsedTotal = SystemClock.elapsedRealtime() - scanStartMs
                    val progressPct = (elapsedTotal.toFloat() / totalDurationMs * 100f)
                        .coerceIn(0f, 100f)
                    val elapsedSeconds = (elapsedTotal / 1000L).toInt()

                    val currentPhaseObj = phasesById[lastObservedPhaseId]
                        ?: phases.first()  // before first transition observed
                    val battery = runCatching { batteryReader.snapshot() }.getOrNull()
                    updateState(ScanState.Running(
                        phase = mapV2Phase(currentPhaseObj),
                        progressPercent = progressPct,
                        elapsedSeconds = elapsedSeconds,
                        scanId = scanId,
                        batteryTemp = battery?.tempCelsius ?: 0f,
                        cpuTemp = systemFileReader.readCpuTemp(),
                        gpuTemp = systemFileReader.readGpuTemp(),
                        cpuUsage = systemFileReader.readCpuUsage(),
                        batteryLevel = battery?.levelPercent ?: 0,
                        v2PhaseId = currentPhaseObj.phaseId
                    ))
                    updateNotification(v2PhaseName(currentPhaseObj), progressPct.toInt())

                    kotlinx.coroutines.delay(500L)
                }
            }

            // Run V2 scan to completion (blocking up to 480 s + probe
            // overhead). Result goes into v2Result so finalize() can
            // serialise it into the footer.
            val result = helper.runV2Scan()
            v2Result = result
            phaseDriver.cancel()  // safety — should already be done

            Log.i(
                TAG,
                "V2 scan complete tier=${result.deviceTier.tag} " +
                    "intensity=${result.intensityFactor} " +
                    "criticalDefect=${result.verification.criticalDefectDetected} " +
                    "throttlingEvents=${result.throttlingEventCount}"
            )

            completeScan(scanId, isComplete = true, earlyStopReason = null)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "V2 scan execution error: ${e.message}")
            completeScan(scanId, isComplete = false, earlyStopReason = "v2_error")
        } finally {
            runCatching { v2Service?.shutdown() }
            v2Service = null
        }
    }

    /** Maps V2Phase enum to V1 Phase enum so the existing UI state
     *  flow keeps working. INIT/STORAGE/MEMORY collapse to V1 INIT/
     *  STORAGE; the three CPU/GPU/COMBINED stress phases map to V1
     *  INTENSIVE_STRESS/SUSTAINED_STRESS; BURST + COOLDOWN map to
     *  COOLDOWN. The actual phaseId stamped on samples is the V2 0..7,
     *  not this V1 mapping — UI consumers that key on Phase enum see
     *  consistent labels while data consumers see the granular V2 IDs. */
    private fun mapV2Phase(v2: net.neuralcheck.data.local.files.V2Phase): Phase = when (v2) {
        net.neuralcheck.data.local.files.V2Phase.INIT_PROFILING,
        net.neuralcheck.data.local.files.V2Phase.STORAGE_STRESS -> Phase.STORAGE
        net.neuralcheck.data.local.files.V2Phase.MEMORY_STRESS,
        net.neuralcheck.data.local.files.V2Phase.CPU_SUSTAINED,
        net.neuralcheck.data.local.files.V2Phase.GPU_SUSTAINED -> Phase.INTENSIVE_STRESS
        net.neuralcheck.data.local.files.V2Phase.COMBINED_PEAK,
        net.neuralcheck.data.local.files.V2Phase.BURST_PATTERN -> Phase.SUSTAINED_STRESS
        net.neuralcheck.data.local.files.V2Phase.COOLDOWN -> Phase.COOLDOWN
    }

    private suspend fun executePhases(scanId: String) {
        try {
            // Phase 1: Init + static tests (0:00 - 0:30)
            updateState(ScanState.Running(Phase.INIT, 0f, 0, scanId))
            sampler.setPhase(1)
            // Run all static tests in background while waiting
            pendingTestJobs += lifecycleScope.launch {
                runCatching { collectedResults.addAll(functionalTestRunner.runAllStaticTests()) }
                    .onFailure { Log.w(TAG, "Static tests failed: ${it.message}") }
            }
            delayWithProgress(PHASE_1_DURATION, Phase.INIT, 0L, scanId)

            // Phase 2: Storage tests (0:30 - 2:00)
            updateState(ScanState.Running(Phase.STORAGE, 0f, 30, scanId))
            sampler.setPhase(2)
            pendingTestJobs += lifecycleScope.launch {
                runCatching { collectedResults.addAll(functionalTestRunner.runStorageTests()) }
                    .onFailure { Log.w(TAG, "Storage tests failed: ${it.message}") }
            }
            delayWithProgress(PHASE_2_DURATION, Phase.STORAGE, PHASE_1_DURATION, scanId)

            // Phase 3: Intensive stress (2:00 - 5:00)
            updateState(ScanState.Running(Phase.INTENSIVE_STRESS, 0f, 120, scanId))
            sampler.setPhase(3)
            sampler.switchTo4Hz()
            cpuLoadGenerator.start()
            GpuFpsTracker.reset()
            gpuLoadGenerator.start()
            delayWithProgress(PHASE_3_DURATION, Phase.INTENSIVE_STRESS, PHASE_1_DURATION + PHASE_2_DURATION, scanId)

            // Phase 4: Sustained stress (5:00 - 10:00)
            updateState(ScanState.Running(Phase.SUSTAINED_STRESS, 0f, 300, scanId))
            sampler.setPhase(4)
            sampler.switchTo1Hz()
            // Sensor + GPS tests run during sustained phase
            pendingTestJobs += lifecycleScope.launch {
                runCatching { collectedResults.addAll(functionalTestRunner.runSensorTests()) }
                    .onFailure { Log.w(TAG, "Sensor tests failed: ${it.message}") }
                runCatching { collectedResults.addAll(functionalTestRunner.runGpsTests()) }
                    .onFailure { Log.w(TAG, "GPS tests failed: ${it.message}") }
                runCatching { collectedResults.addAll(functionalTestRunner.runCameraTests()) }
                    .onFailure { Log.w(TAG, "Camera tests failed: ${it.message}") }
            }
            delayWithProgress(PHASE_4_DURATION, Phase.SUSTAINED_STRESS,
                PHASE_1_DURATION + PHASE_2_DURATION + PHASE_3_DURATION, scanId)

            // Phase 5: Cooldown (10:00 - 11:30)
            updateState(ScanState.Running(Phase.COOLDOWN, 0f, 600, scanId))
            sampler.setPhase(5)
            cpuLoadGenerator.stop()
            gpuLoadGenerator.stop()

            delayWithProgress(PHASE_5_DURATION, Phase.COOLDOWN,
                PHASE_1_DURATION + PHASE_2_DURATION + PHASE_3_DURATION + PHASE_4_DURATION, scanId)

            // Complete
            completeScan(scanId, isComplete = true, earlyStopReason = null)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Scan execution error: ${e.message}")
            completeScan(scanId, isComplete = false, earlyStopReason = "error")
        }
    }

    private suspend fun delayWithProgress(
        durationMs: Long,
        phase: Phase,
        phaseStartOffset: Long,
        scanId: String
    ) {
        val startMs = SystemClock.elapsedRealtime()
        val endMs = startMs + durationMs

        while (SystemClock.elapsedRealtime() < endMs) {
            if (safetyMonitor.isTriggered()) return

            val elapsed = SystemClock.elapsedRealtime() - startMs
            val overallElapsed = phaseStartOffset + elapsed
            val overallProgress = (overallElapsed.toFloat() / TOTAL_DURATION).coerceIn(0f, 1f)
            val elapsedSeconds = (overallElapsed / 1000).toInt()

            val battery = batteryReader.snapshot()

            updateState(ScanState.Running(
                phase = phase,
                progressPercent = overallProgress * 100f,
                elapsedSeconds = elapsedSeconds,
                scanId = scanId,
                batteryTemp = battery.tempCelsius,
                cpuTemp = systemFileReader.readCpuTemp(),
                gpuTemp = systemFileReader.readGpuTemp(),
                cpuUsage = systemFileReader.readCpuUsage(),
                batteryLevel = battery.levelPercent
            ))

            updateNotification(phase.ordinal + 1, (overallProgress * 100).toInt())

            delay(1000) // Update progress every second
        }
    }

    private fun onSafetyStop(reason: String) {
        Log.w(TAG, "Safety stop: $reason")
        scanJob?.cancel()
        cpuLoadGenerator.stop()
        gpuLoadGenerator.stop()

        currentScanId?.let { completeScan(it, isComplete = false, earlyStopReason = reason) }
        _scanState.value = ScanState.SafetyStopped(reason)
    }

    private fun completeScan(scanId: String, isComplete: Boolean, earlyStopReason: String?) {
        // Idempotency guard — prevent re-entry from stopScan()/onDestroy() after we've already started finalizing
        if (!completionLatch.tryBegin()) return

        sampler.stop()
        runCatching { thermalLists?.stop() }
            .onFailure { Log.w(TAG, "thermal list collector stop failed: ${it.message}") }
        safetyMonitor.stop()
        cpuLoadGenerator.stop()
        gpuLoadGenerator.stop()

        sensorCollector.stop()

        val battery = batteryReader.snapshot()
        // fieldsVersion 68 (ت-06): close the pair recording BEFORE the receiver
        // goes — receiverRegistered is read now.
        batteryPairsRecord = runCatching { batteryReader.finishBatteryPairs() }
            .onFailure { Log.w(TAG, "battery pair recording finish failed: ${it.message}") }
            .getOrNull()
        batteryReader.unregister()

        // Move to terminal state and tear down foreground ONLY after all data is persisted.
        //
        // V2 fix #56 follow-up #2 (2026-05-04): the previous comment proposed
        // `runBlocking on Dispatchers.IO` to survive lifecycleScope cancellation;
        // that's an ANR risk and was never actually implemented. The real
        // mitigation is `withContext(NonCancellable)` around the persistence
        // chain (below). Cancellation flows:
        //   - happy path: launch runs to completion → stopSelf() → onDestroy →
        //     super.onDestroy() cancels lifecycleScope, but our launch already
        //     finished, so cancel is a no-op for us.
        //   - OS-initiated onDestroy (process reclaim, swipe-close, force-stop):
        //     onDestroy → stopScan → completeScan → lifecycleScope.launch (queued)
        //     → super.onDestroy() cancels lifecycleScope BEFORE the launch ran.
        //     Without NonCancellable, the entire persistence chain is silently
        //     discarded. With NonCancellable wrap, the persistence runs to
        //     completion before the OS actually destroys the process.
        //   - SIGKILL: NonCancellable cannot help. The .footer.json backup file
        //     from writeFooterBackup is the only mitigation; that's already in
        //     place from APK #58.
        lifecycleScope.launch {
            // Await any still-running test groups so their results land in collectedResults
            // before we snapshot it for persistence. Cap wait to keep teardown bounded.
            //
            // This joinAll is OUTSIDE the NonCancellable wrap on purpose: pendingTestJobs
            // are children of lifecycleScope. If lifecycle is cancelled, those jobs cancel
            // too and joinAll returns promptly — the cooperative behaviour we want.
            val jobsToAwait = synchronized(pendingTestJobs) { pendingTestJobs.toList() }
            withTimeoutOrNull(15_000L) { jobsToAwait.joinAll() }

            // Cancellation-resistant persistence section. Everything from here through
            // stopSelf() runs to completion even if lifecycleScope is cancelled mid-way.
            // Per-op try/catch inside still keeps each op's failures local. We do NOT
            // add Dispatchers.IO here: the heavy ops (fileWriter.finalize, Room writes)
            // do their own withContext(Dispatchers.IO) internally; stopForeground/stopSelf
            // expect main-thread invocation; writeFooterBackup is fast enough (~10 ms)
            // to run on main without StrictMode disk-write violations of consequence.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {

            // Compute time-series dependent tests from sampler aggregates (B-06, C-03/04/05,
            // G-02/03, T-01/02/03). Without this, those categories would stay empty.
            val snapshotStats = runCatching { sampler.snapshotStats() }.getOrNull()

            // Phase 1.14 hotfix: compute the cascade-gated CPU temperature
            // ONCE here so T-01 (via computeTimeSeriesTests below) and the
            // footer write further down share a single source of truth.
            // Pre-hotfix T-01 read `stats.maxTempC` (the raw sampler peak,
            // sometimes contaminated by a non-CPU zone passing the per-tick
            // selector) while the footer ran this cascade independently —
            // production scan 18dfd6b4 (Samsung S24 Ultra) ended up showing
            // T-01=81 °C alongside footer.maxCpuTemp=43.6 °C from the same
            // scan. See StressTestService.selectFooterMaxTemp + the unit
            // tests in service/stress/StressTestServiceTest.kt for the
            // documented contract.
            val liveCpuAtFinalize = systemFileReader.readCpuTemp()
            val snapshotMaxTempC = snapshotStats?.maxTempC ?: -1f
            val snapshotTempSource = snapshotStats?.tempSource ?: "none"
            val footerCpuTemp = selectFooterCpuTemp(
                liveCpu = liveCpuAtFinalize,
                snapshotMaxTempC = snapshotMaxTempC,
                snapshotTempSource = snapshotTempSource
            )
            // T-01 / T-02 / T-03 keep reading the cascade's value with their own
            // "-1f = unavailable" convention; it is not what the footer carries.
            val footerMaxTemp = footerCpuTemp.forT01()

            runCatching {
                val stats = snapshotStats ?: return@runCatching
                val durationMin = (stats.durationMs / 60_000.0).toFloat().coerceAtLeast(0.001f)
                val iterations = cpuLoadGenerator.getTotalIterations()
                // Phase 1.14 hotfix continuation (2026-05-18): T-02 now
                // computes its °C/min rate against the SAME footerMaxTemp
                // T-01 just accepted, via the pure helper
                // [computeHeatRiseFromFooter]. Previously T-02 received
                // stats.heatRiseC unchanged when footerMaxTemp>0, which
                // meant T-02 silently used the contaminated maxBestTemp
                // numerator even after T-01 stopped trusting it — on the
                // S24 Ultra reproducer scan that produced T-01 = 43.6 °C
                // alongside T-02 ≈ 6.6 °C/min, internally inconsistent.
                //
                // T-03 (cooldown rate) still uses stats.cooldownRateCPerMin
                // because it's measured across phase 7 against a different
                // window (cooldownStartCpuTempC → cooldownEndCpuTempC) and
                // is not directly comparable to footerMaxTemp. Same
                // UNAVAILABLE gate as before: if the cascade refused the
                // CPU source class, T-03 reports Float.NaN.
                val heatRiseForTest = computeHeatRiseFromFooter(
                    footerMaxTemp = footerMaxTemp,
                    firstBestTempC = stats.firstBestTempC,
                    scanDurationMs = stats.durationMs
                )
                val cooldownForTest = if (footerMaxTemp > 0f) stats.cooldownRateCPerMin else Float.NaN
                val tsResults = functionalTestRunner.computeTimeSeriesTests(
                    startLevel = if (stats.startBatteryLevel >= 0) stats.startBatteryLevel else startBatteryLevel,
                    endLevel = if (stats.endBatteryLevel >= 0) stats.endBatteryLevel else battery.levelPercent,
                    durationMin = durationMin,
                    maxTemp = footerMaxTemp,
                    heatRise = heatRiseForTest,
                    cooldown = cooldownForTest,
                    tempSource = stats.tempSource,
                    // G2/P2.3: maxFps/minFps/initialFps removed — they only
                    // ever fed G-03/G-04/G-05, which are no longer computed
                    // on-device. stats.maxFps/minFpsDuringStress/
                    // initialFpsDuringStress are still tracked in
                    // SamplerStats itself (unused now, left as-is — not part
                    // of this change's scope) but no longer read here.
                    maxFreqFirst30s = stats.maxFreqFirst30sKhz,
                    minFreqStress = stats.minFreqStressKhz,
                    freqReadings = stats.freqReadingsStressMhz,
                    totalIterations = iterations,
                    stressDurationMs = stats.stressDurationMs
                )
                collectedResults.addAll(tsResults)
            }.onFailure { Log.w(TAG, "Time-series test computation failed: ${it.message}") }

            val filePath = try {
                // Phase 1.9 fix #1: footer.maxCpuTemp must reflect a CPU
                // reading or be the explicit -1f sentinel. The previous
                // cascade ("liveCpu → snapshot.maxTempC → -1f") silently
                // substituted battery temp when both per-zone CPU and
                // GPU sysfs were SELinux-blocked (Samsung One UI, HyperOS,
                // ColorOS). The substitution was visible to consumers
                // that read footer.tempSource, but invisible to
                // BehaviorFingerprintBuilder (the only mobile reader
                // of footer.maxCpuTemp at audit time, line 168) which
                // forwarded the misnamed value to the backend.
                //
                // The cascade now refuses the gpu/battery branches of
                // SamplerStats.maxTempC by gating the snapshot read on
                // tempSource == "cpu". When sysfs is blocked everywhere
                // the value is -1f, exactly matching the field's
                // documented "-1 sentinel = unavailable" contract.
                //
                // Phase 1.14 hotfix (2026-05-17): cascade hoisted above
                // the computeTimeSeriesTests call so T-01 and the footer
                // share the same value. This block consumes the hoisted
                // `footerMaxTemp` instead of recomputing it.
                // V2 footer data — populated only when V2 path ran and
                // produced a result. Defaults preserve V1 footer shape.
                val v2 = v2Result
                // V2 fix #56 follow-up (2026-05-04): per-field diagnostics
                // map. Each runCatching writes "ok" or "fail:<class>" so
                // the AI can tell on Xiaomi-style failures whether a missing
                // field came from a thrown exception (caught here) or from
                // a downstream serialization truncation (caught nowhere).
                // Persisted into the footer + footer-backup file.
                val diagnostics = mutableMapOf<String, String>()

                // V2 fix #54: compute stress effectiveness from in-memory
                // sample buffer. AI uses passedSaturationThreshold to know
                // whether benign metrics reflect a healthy device or just
                // a governor that throttled before our test could load it.
                val samplesSnapshot = runCatching { sampler.getSamplesSnapshot() }
                    .also { diagnostics["samplesSnapshot"] = if (it.isSuccess) "ok:size=${it.getOrNull()?.size ?: -1}" else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                    .getOrDefault(emptyList())
                val stressEffBase = runCatching {
                    net.neuralcheck.util.StressEffectivenessCalculator.compute(
                        samples = samplesSnapshot,
                        buildHardware = Build.HARDWARE.orEmpty(),
                        // V2 fix #56-3 (fieldsVersion 58): pass the SamplerStats
                        // snapshot so the calculator can derive per-cluster
                        // freq ratios + usage averages. snapshotStats carries
                        // the per-CPU cpuinfo_max_freq topology and per-CPU
                        // /proc/stat usage averages collected during phase 3.
                        stats = snapshotStats,
                        // V2 fix #56-4 / Commit 4 (fieldsVersion 60): pass the
                        // V2 VerificationSummary so C3 (verification_integrity)
                        // can be evaluated. null in V1 mode → calculator
                        // treats C3 as "no defect evidence → PASS", matching
                        // V1's pre-verification behaviour.
                        verification = v2?.verification,
                        // fieldsVersion 67: pass the watcher's observation IN
                        // so the C4 caveat can condition its governor-policy
                        // sentence on measured evidence instead of asserting a
                        // vendor by default (it claimed "Xiaomi/HyperOS class"
                        // on a Samsung S10 5G whose own flag read false).
                        governorCappingDetected = v2?.governorCappingDetected == true
                    )
                }.also { diagnostics["stressEffectiveness"] = if (it.isSuccess) "ok" else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                .getOrElse {
                    Log.w(TAG, "stressEffectiveness compute failed: ${it.message}")
                    net.neuralcheck.data.local.files.StressEffectiveness()
                }
                // fieldsVersion 67: the flag is now set by the calculator
                // from the compute() parameter above (single source of truth),
                // so the post-hoc copy() is gone. The multi-axis criterion
                // still owns passedSaturationThreshold / failure reason.
                val stressEff = stressEffBase

                // V2 fix #56 (2.3): battery health snapshot. Six fields
                // (design + current cap, cycle count, charge/energy
                // counters, official SoH) so the AI sees wear context.
                val batteryHealthSnap = runCatching {
                    batteryReader.snapshotHealth(systemFileReader)
                }.also { diagnostics["batteryHealthSnap"] = if (it.isSuccess) "ok" else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                .getOrNull()
                // fieldsVersion 63 (2026-05-18): replaced the unreliable
                // sohOfficialPercent single-point estimator (102 % on S24
                // Ultra, 100 % on a 2019 S10 5G) with 6 behavioral
                // indicators measured during the actual scan. See
                // hardware/battery/BatteryIndicators.kt for the pure
                // builder; docs/architecture/BATTERY_INDICATORS.md for
                // the public schema + AI guidance. Backend prompt update
                // pairs with this commit on the wep/neuralcheck-backend
                // repo — do NOT distribute the APK before that lands or
                // the AI loses its battery wear signal.
                //
                // chargeCounterUah and energyCounterNwh are kept in the
                // map because BatteryTests.B-02 (line 352-353) still reads
                // chargeCounterUah through another path (until fieldsVersion
                // 68 ChannelDiscoveryProbe did too; it now reads the raw
                // counter via BatteryReader.readChargeCounterRaw); removing
                // it from the footer would strand that consumer. Both are now diagnostic /
                // calibration signals rather than direct SoH inputs.
                // fieldsVersion 66: ONE max-battery-temp value for both
                // footer.maxBatteryTemp and batteryHealth.batteryTempMaxC
                // (the footer field used to carry the end-of-scan snapshot).
                // Falls back to the end snapshot, then the start temp, when
                // no sample carries a positive v[1].
                val maxBatteryTempC: Double = computeMaxBatteryTempC(
                    samples = samplesSnapshot,
                    fallbackC = if (battery.tempCelsius > 0f) battery.tempCelsius else startBatteryTemp
                )
                val batteryHealthMap: Map<String, Any?>? = runCatching {
                    batteryHealthSnap?.let { h ->
                        val realIndicators = net.neuralcheck.hardware.battery
                            .BatteryIndicators.compute(
                                samples = samplesSnapshot,
                                batterySnapshot = h,
                                batteryLevelStart = if (snapshotStats?.startBatteryLevel != null && snapshotStats.startBatteryLevel >= 0)
                                    snapshotStats.startBatteryLevel else startBatteryLevel,
                                batteryLevelEnd = if (snapshotStats?.endBatteryLevel != null && snapshotStats.endBatteryLevel >= 0)
                                    snapshotStats.endBatteryLevel else battery.levelPercent,
                                // fieldsVersion 68: a temperature that was never read (<= 0,
                                // the same rule computeMaxBatteryTempC uses for a valid one) is
                                // NaN here, which the indicators turn into an absent value with a
                                // state — not a 0.0 that reads as "did not warm".
                                batteryTempStartC = startBatteryTemp.toDouble().takeIf { it > 0.0 } ?: Double.NaN,
                                batteryTempMaxC = maxBatteryTempC.takeIf { it > 0.0 } ?: Double.NaN
                            )
                        // fieldsVersion 68 (T-18 row 9): the map is built by
                        // BatteryIndicators.footerMap (one place, also what the
                        // wire test runs); an indicator that could not be
                        // computed is null in it and its <name>State says why.
                        net.neuralcheck.hardware.battery.BatteryIndicators
                            .footerMap(realIndicators, h)
                    }
                }.also { diagnostics["batteryHealth"] = if (it.isSuccess) (if (it.getOrNull() != null) "ok" else "ok:null_snap") else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                .getOrNull()

                // V2 fix #56 (3.4): cross-reference design capacity against
                // known-published value for this Build.MODEL. Catches
                // dual-cell halving anomalies (kernel reports 2250 mAh on
                // a 4500 mAh device).
                val knownCapacityCls = runCatching {
                    val known = net.neuralcheck.util.KnownBatteryCapacities.get(
                        Build.MODEL.orEmpty()
                    )
                    net.neuralcheck.util.KnownBatteryCapacities.classifyAgainstKnown(
                        sysfsDesignMah = batteryHealthSnap?.designCapacityMah,
                        knownDesignMah = known
                    )
                }.also { diagnostics["knownCapacityClassification"] = if (it.isSuccess) "ok" else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                .getOrDefault("unknown")

                // V2 fix #56 (2.4): per-sensor coverage + source breakdown.
                // Walk the in-memory samples and tally how often each
                // signal was populated (>0). Lets AI weight metrics by
                // signal availability rather than treating all scans equal.
                val dataQuality = runCatching {
                    computeDataQualityFor(
                        samples = samplesSnapshot,
                        batteryReader = batteryReader,
                        // fieldsVersion 66: v[6] provenance from the reader's
                        // sticky flag + the sampler's switch index.
                        cpuUsageSource = net.neuralcheck.service.collector.CpuUsageProvenance
                            .sourceLabel(systemFileReader.isProcStatAvailable()),
                        cpuUsageFallbackFromSample = sampler.getCpuUsageFallbackFromSample()
                    )
                }.also { diagnostics["dataQuality"] = if (it.isSuccess) "ok" else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                .getOrDefault(net.neuralcheck.data.local.files.DataQuality())

                // V5 schema (Task G): footer-level metadata fields.
                val channelDiscoveryFooter = channelDiscoveryReport
                val thermalAvailability = runCatching {
                    channelDiscoveryFooter?.let {
                        net.neuralcheck.data.local.files.ThermalSensorAvailability
                            .fromDiscovery(it)
                    }
                }.also { diagnostics["thermalSensorAvailability"] = if (it.isSuccess) "ok" else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                .getOrNull()

                // V5 stillness: read from SamplerStats snapshot. Threshold 0.5
                // V5 stillness: threshold tightened from 0.5 to 0.1 m/s²
                // in Phase 1.7.E. Source: scientific accelerometer noise
                // studies typically use 0.05–0.1 m/s² for "device
                // stationary" classification (e.g. UCI HAR dataset's
                // SVM threshold ~0.06; Apple's CoreMotion stationary-
                // detection ~0.08). The previous 0.5 threshold was
                // permissive enough to flag a noticeably-shaking phone
                // as 'still' — the Samsung S10 5G post-1.6 capture
                // landed at accelStdDev=0.35 (held in hand) and
                // wrongly reported isStill=true. Anything under 0.1
                // here means the device was on a flat surface; above
                // means the AI should weight motion-correlated
                // metrics (battery_current spikes, fps jitter) down.
                // fieldsVersion 68 (ت-18 row 6): fewer than 2 readings used to
                // write isStill=true beside accelStdDev=-1 — absence read as
                // stillness. Now nothing is claimed: StillnessClassifier.report
                // leaves isStill/accelStdDev absent and states
                // insufficient_accel_samples. No sampler statistics at all is a
                // failure to compute, named in footerDiagnostics, not "still".
                val stillnessFooter = if (snapshotStats == null) {
                    diagnostics["scanStillness"] = "fail:no_sampler_stats"
                    null
                } else {
                    runCatching {
                        net.neuralcheck.service.sampler.StillnessClassifier.report(
                            accelStdDev = snapshotStats.accelStdDev,
                            sampleCount = snapshotStats.accelSampleCount
                        )
                    }.also { diagnostics["scanStillness"] = if (it.isSuccess) "ok" else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                    .getOrNull()
                }

                // fieldsVersion 68 (S-27): the V1 flavor has no per-device channels,
                // so there is nothing to track and the record stays null there.
                val channelReadFailureRecord = if (net.neuralcheck.BuildConfig.USE_V2_STRESS) {
                    runCatching { sampler.getChannelReadFailures() }
                        .also { diagnostics["channelReadFailures"] = if (it.isSuccess) "ok" else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                        .getOrNull()
                } else null

                // fieldsVersion 68 (ت-03, ت-04): the thermal lists, null on V1 or
                // when the collector never existed. A failure to read them is
                // named in footerDiagnostics, never turned into an empty list.
                val thermalCollector = thermalLists
                val thermalListsFooter = if (net.neuralcheck.BuildConfig.USE_V2_STRESS) {
                    if (thermalCollector == null) {
                        // The scan ended before the sampler (and with it the
                        // collector) was started: say so, do not leave a bare gap.
                        diagnostics["thermalLists"] = "not_started"
                        null
                    } else {
                        runCatching { thermalCollector.snapshot() }
                            .also { diagnostics["thermalLists"] = if (it.isSuccess) "ok" else "fail:${it.exceptionOrNull()?.javaClass?.simpleName}" }
                            .getOrNull()
                    }
                } else null

                // fieldsVersion 68 (ت-06): null on V1, or when no recording was
                // started; the diagnostics say so instead of leaving a bare gap.
                val batteryPairs = batteryPairsRecord
                if (net.neuralcheck.BuildConfig.USE_V2_STRESS) {
                    diagnostics["batteryPairs"] = if (batteryPairs != null) "ok" else "not_recorded"
                }

                // The holder is drained exactly once, here, and the outcome (the
                // list or the failure) is what both footer fields come from.
                val preScanRead = runCatching { preScanWarningsHolder.readAndClear() }
                // fieldsVersion 71 (A-P6-13): what the scan screen reported of the brightness lock (drained once, like the warnings above).
                val brightnessLockResult = runCatching { brightnessLockHolder.readAndClear() }
                    .getOrElse { BrightnessLock.Result(BrightnessLock.States.notAttempted("holder_read_failed"), null, null) }

                val footer = TimeSeriesFooter(
                    endTimestamp = System.currentTimeMillis(),
                    totalSamples = sampler.getSampleCount(),
                    isComplete = isComplete,
                    earlyStopReason = earlyStopReason,
                    batteryLevelEnd = battery.levelPercent,
                    // fieldsVersion 66 SEMANTIC FIX: true scan maximum of
                    // v[1] (was the end-of-scan snapshot despite the name).
                    maxBatteryTemp = maxBatteryTempC.toFloat(),
                    // fieldsVersion 68 (T-10, S-33, N-08): ONE field with its source
                    // declared; the old maxCpuTemp is no longer written.
                    cpuTempFinalC = footerCpuTemp.valueC,
                    cpuTempFinalSource = footerCpuTemp.source,
                    thermalHeadroomAvg = snapshotStats?.thermalHeadroomAvg,
                    thermalHeadroomPeak = snapshotStats?.thermalHeadroomPeak,
                    thermalStatusMax = snapshotStats?.thermalStatusMax,
                    tempSource = snapshotStats?.tempSource,
                    intensityFactor = v2?.intensityFactor ?: 1.0f,
                    deviceTierMeasured = v2?.deviceTier?.tag ?: "v1_no_probe",
                    batteryCurrentScalingApplied = batteryReader.getCurrentScalingApplied(),
                    tempRiseRateInProbe = v2?.tempRiseRateInProbe ?: 0.0f,
                    throttlingEvents = v2?.throttlingEvents ?: emptyList(),
                    verificationResults = v2?.verification
                        ?: net.neuralcheck.data.local.files.VerificationSummary(),
                    phaseCompletionStatus = v2?.phaseCompletionStatus ?: emptyMap(),
                    // G2/P2.3: empty on the V1 path (no V2Phase timeline there).
                    phaseBoundaries = v2?.phaseBoundaries ?: emptyList(),
                    stressEffectiveness = stressEff,
                    batteryHealth = batteryHealthMap,
                    dataQuality = dataQuality,
                    knownCapacityClassification = knownCapacityCls,
                    // V2 fix #56 follow-up (2026-05-04): stamp the build's
                    // schema version so the AI can verify which footer
                    // schema produced this file. Increments per release —
                    // see app/build.gradle.kts `fieldsVersion`.
                    fieldsVersion = net.neuralcheck.BuildConfig.FIELDS_VERSION,
                    // V2 fix #56 follow-up (2026-05-04): per-field
                    // computation status. If a field is missing in the
                    // serialized JSON but present in this map as "ok",
                    // the issue is downstream (Gson / GZIP / I/O).
                    footerDiagnostics = diagnostics.toMap(),
                    // V5 schema (Task G): channel-discovery report,
                    // thermal-only summary, and aggregate stillness.
                    channelDiscovery = channelDiscoveryFooter,
                    thermalSensorAvailability = thermalAvailability,
                    scanStillness = stillnessFooter,
                    // fieldsVersion 68: raw policy topology + the samples whose
                    // per-device channel read failed (see ScanChannelLayout /
                    // ChannelReadFailures). null on V1 or when discovery failed.
                    cpuPolicies = scanCpuPolicies,
                    channelReadFailures = channelReadFailureRecord?.indices,
                    channelReadFailureCounts = channelReadFailureRecord?.counts,
                    thermalHeadroomReadings = thermalListsFooter?.headroomReadings,
                    thermalHeadroomState = thermalListsFooter?.headroomState,
                    thermalStatusEvents = thermalListsFooter?.statusEvents,
                    thermalStatusState = thermalListsFooter?.statusState,
                    batteryPairs = batteryPairs?.pairs,
                    batteryPairsState = batteryPairs?.state,
                    // fieldsVersion 68 (N-23): which of this footer's fields are
                    // aggregates kept for the transitional display.
                    transitionalAggregates = net.neuralcheck.data.local.files
                        .TransitionalAggregates.NAMES,
                    // Phase 1.7.D: drain accepted pre-scan warnings.
                    // Empty list (clean pre-scan check) is preserved
                    // distinctly from null (legacy V1 path that never
                    // ran the new evaluator).
                    // fieldsVersion 68 (ت-18 row 7): a FAILED read is no longer
                    // written as an empty list (= "clean"): the list is null
                    // and preScanWarningsState says read_failed:<message>.
                    // fieldsVersion 68 (third state): nothing written is `not_written` and a null
                    // list too, not an empty list.
                    preScanWarnings = preScanRead.getOrNull()?.takeIf { it.written }?.warnings,
                    preScanWarningsState = PreScanWarningsStates.of(preScanRead),
                    // fieldsVersion 71 (A-P6-13): the battery window this build APPLIED (N71-07) and what came of the brightness lock (N71-08).
                    batteryWindowPct = net.neuralcheck.data.local.files.BatteryWindowPctFooter(ScanBatteryWindow.MIN_PCT, ScanBatteryWindow.MAX_PCT),
                    brightnessLock = net.neuralcheck.data.local.files.BrightnessLockFooter(
                        brightnessLockResult.state, brightnessLockResult.value, brightnessLockResult.method
                    ),
                    // ── fieldsVersion 65 (additive) — measurement integrity ──
                    // Declare footer values that are compile-time constants,
                    // NOT measurements, so the AI / training pipeline can
                    // exclude them. deviceTierMeasured + intensityFactor are
                    // hardcoded since V2 fix #30 (tier = FLAGSHIP_COOLING,
                    // factor = 1.0 — see AdaptiveIntensityController.probe).
                    // tempRiseRateInProbe IS measured on the V2 path (real
                    // 8 s stress probe) but is a constant 0.0 on V1 where no
                    // probe runs.
                    // fieldsVersion 68 (T-18 rows 8 and 13): the list also
                    // declares the V5-schema dataQuality literals (coverage % = 0,
                    // cpuTempPrimarySource = "moved_to_footer_v5") that both paths
                    // write; it does NOT list what became an explicit state.
                    unmeasuredDefaults = net.neuralcheck.data.local.files
                        .UnmeasuredDefaults.forScan(v2Ran = v2 != null),
                    // Nominal sample count for a COMPLETE scan from the phase
                    // table (Σ durationMs/1000 × samplingHz = 1605 for V2).
                    // Null on V1 (no per-phase rate table). Compare with
                    // totalSamples to detect silent sampler loss.
                    expectedSamples = if (v2 != null) {
                        net.neuralcheck.data.local.files.V2Phase.values()
                            .sumOf { (it.durationMs / 1000L) * it.samplingHz }
                            .toInt()
                    } else null,
                    // Flush failures so far; finalize() re-stamps the final
                    // value (a failure can still happen during finalize's own
                    // last flush) together with samplesSha256.
                    flushFailureCount = fileWriter.getFlushFailureCount()
                )
                // V2 fix #56 follow-up (2026-05-04): write a small
                // uncompressed footer-only file BEFORE the main GZIP
                // finalize. If the main write later truncates (Xiaomi
                // MT6991 / HyperOS bug), this backup still carries the
                // 4 fields that were dropping out.
                runCatching {
                    fileWriter.writeFooterBackup(scanId, footer)
                }.onFailure {
                    Log.w(TAG, "writeFooterBackup outer-guard caught: ${it.javaClass.simpleName}: ${it.message}")
                }
                // Phase 1.8 Task 5: pass the captured calibration probe +
                // calibrated config to the writer. Either both null
                // (flag off OR probe failed entirely) → finalize emits
                // {"calibration": {"applied": false}} per Task 1+6, OR
                // both non-null → finalize emits the full block.
                fileWriter.finalize(
                    footer = footer,
                    profile = deviceProfile,
                    config = calibratedConfig
                )
            } catch (t: Throwable) {
                // V2 fix #56 follow-up (2026-05-04): catch Throwable so
                // OutOfMemoryError / NoSuchMethodError / StackOverflow-
                // Error during footer construction or finalize() surface
                // here instead of disappearing into the lifecycleScope
                // launch, where they would silently kill the coroutine
                // before persistence + scan record updates ran.
                Log.e(
                    TAG,
                    "Error finalizing time-series: ${t.javaClass.name}: ${t.message} " +
                        "cause=${t.cause?.javaClass?.simpleName}:${t.cause?.message}",
                    t
                )
                null
            }

            try {
                val snapshot = synchronized(collectedResults) { collectedResults.toList() }
                val entities = snapshot.map { tr ->
                    FunctionalResultEntity(
                        scanId = scanId,
                        testId = tr.testId,
                        componentName = tr.componentNameAr.ifBlank { tr.componentName },
                        categoryName = tr.categoryName,
                        status = TestStatus.toDbString(tr.status),
                        score = tr.score,
                        rawValue = tr.rawValue,
                        rawValueUnit = tr.rawValueUnit,
                        notes = tr.notes,
                        timestamp = System.currentTimeMillis(),
                        measurements = net.neuralcheck.data.local.db.converter.MeasurementsJson
                            .encode(tr.measurements)
                    )
                }
                if (entities.isNotEmpty()) testResultRepository.saveResults(entities)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save test results: ${e.message}")
            }

            try {
                scanRepository.getScanById(scanId)?.let { existing ->
                    scanRepository.updateScan(existing.copy(
                        batteryLevelEnd = battery.levelPercent,
                        isComplete = isComplete,
                        earlyStopReason = earlyStopReason,
                        earlyStopAtMs = if (!isComplete) System.currentTimeMillis() else null,
                        timeSeriesFilePath = filePath
                    ))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update scan record: ${e.message}")
            }

            try { SyncScheduler.syncNow(this@StressTestService) } catch (_: Exception) {}

            // Now safe to emit Completed — all persistence is durable
            currentScanId = null
            _scanState.value = if (isComplete) {
                ScanState.Completed(scanId)
            } else if (earlyStopReason != null && earlyStopReason != "user_cancelled") {
                ScanState.SafetyStopped(earlyStopReason)
            } else {
                ScanState.Completed(scanId)
            }

            // Release the wake lock before tearing the service down so the
            // device can sleep promptly if nothing else is keeping it awake.
            releaseWakeLock()

            // Tear down foreground and service only after state has been observed
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            } // end withContext(NonCancellable)
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        currentScanId?.let { completeScan(it, isComplete = false, earlyStopReason = "user_cancelled") }
    }

    private fun updateState(state: ScanState) {
        _scanState.value = state
    }

    private fun acquireWakeLock(powerManager: PowerManager) {
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NeuralCheck:stress")
            .apply {
                setReferenceCounted(false)
                // Scan is bounded by TOTAL_DURATION + a generous finalisation
                // budget. Explicit timeout guarantees the lock is released even
                // if the service is killed before completeScan() runs.
                acquire(TOTAL_DURATION + 60_000L)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /**
     * V2 fix #56 (2.4): walks the in-memory sample buffer and tallies
     * coverage + source breakdown for each sensor. Combined with
     * BatteryReader.getScalingDiagnostic() this gives the AI a complete
     * picture of "which signals were actually populated and how".
     *
     * Sample variable indices (V5 schema, per V2_VARIABLE_DEFINITIONS):
     *   v[5] = cpu_freq_max_khz
     *   v[6] = cpu_usage_pct
     *
     * V5: cpu_temp / gpu_temp / skin_temp no longer in v[]. Their coverage
     * comes from [SamplerStats] sidecar tracking + the new
     * footer.thermalSensorAvailability field. The DataQuality fields are
     * still populated so historical consumers compile and produce sensible
     * (zeroed) values; the AI is expected to read thermalSensorAvailability
     * for the authoritative status under V5.
     */
    private fun computeDataQualityFor(
        samples: List<net.neuralcheck.data.local.files.TimeSeriesSample>,
        batteryReader: net.neuralcheck.service.collector.BatteryReader,
        cpuUsageSource: String = "unknown",
        cpuUsageFallbackFromSample: Int = -1
    ): net.neuralcheck.data.local.files.DataQuality {
        if (samples.isEmpty()) {
            return net.neuralcheck.data.local.files.DataQuality(
                cpuUsageSource = cpuUsageSource,
                cpuUsageFallbackFromSample = cpuUsageFallbackFromSample
            )
        }
        val total = samples.size.toFloat()
        // V5: thermal channels no longer in v[] — coverage % collapses to 0.
        // Authoritative thermal availability lives in
        // footer.thermalSensorAvailability (Task F).
        val cpuTempAvail = 0
        val gpuTempAvail = 0
        val skinTempAvail = 0
        val cpuFreqAvail = samples.count { it.v.getOrNull(5)?.let { v -> v > 0f } == true }

        val skinBreakdown = runCatching {
            net.neuralcheck.util.ThermalFallback.getSourceBreakdown()
        }.getOrDefault(emptyMap())

        // V5: cpu temp source tag now signals "moved to footer" rather than
        // a sample-derived heuristic.
        val cpuTempPrimary = "moved_to_footer_v5"

        val diag = runCatching { batteryReader.getScalingDiagnostic() }.getOrNull()

        return net.neuralcheck.data.local.files.DataQuality(
            cpuTempCoveragePct = (cpuTempAvail / total) * 100f,
            cpuTempPrimarySource = cpuTempPrimary,
            gpuTempCoveragePct = (gpuTempAvail / total) * 100f,
            skinTempCoveragePct = (skinTempAvail / total) * 100f,
            skinTempSourceBreakdown = skinBreakdown,
            cpuFreqBigCoveragePct = (cpuFreqAvail / total) * 100f,
            // fieldsVersion 66 SEMANTIC FIX: derived from the reader's real
            // provenance, not from "any v[6] > 0" (which was true in both
            // the /proc/stat and the process-fallback modes).
            procStatAvailable = net.neuralcheck.service.collector.CpuUsageProvenance
                .procStatEverUsed(cpuUsageFallbackFromSample),
            cpuUsageSource = cpuUsageSource,
            cpuUsageFallbackFromSample = cpuUsageFallbackFromSample,
            batteryReaderRegistered = diag?.isRegistered == true,
            batteryScalingLocked = diag?.locked == true,
            batteryScalingDetailedSource = diag?.source ?: "unknown",
            batteryScalingMultiplier = diag?.scaleMultiplier ?: 1,
            batteryScalingSignFlip = diag?.signFlip ?: 1,
            // fieldsVersion 65 — measurement-integrity telemetry from the
            // battery reader: lock timing (re-interpret pre-lock samples),
            // filtered Int.MIN_VALUE readings, applied µV→mV conversions.
            batteryScalingLockAtSample = diag?.lockAtSampleIndex ?: -1,
            batteryInvalidCurrentReadings = diag?.invalidReadingCount ?: 0,
            batteryVoltageUvScaledCount = diag?.voltageUvConvertedCount ?: 0
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_scan),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "NeuralCheck scan progress"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm == null) {
            Log.w(TAG, "createNotificationChannel: NOTIFICATION_SERVICE unavailable")
            return
        }
        try {
            nm.createNotificationChannel(channel)
        } catch (t: Throwable) {
            Log.w(TAG, "createNotificationChannel failed: ${t.message}")
        }
    }

    private fun buildNotification(phase: Int, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_scan_title))
            .setContentText(getString(R.string.notification_scan_progress, phase, progress))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4ADE80.toInt()) // NeuralGreen — brand accent on the status bar
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildNotification(phaseName: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_scan_title))
            .setContentText(getString(R.string.notification_scan_progress_v2, phaseName, progress))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4ADE80.toInt())
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(phase: Int, progress: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            nm.notify(NOTIFICATION_ID, buildNotification(phase, progress))
        } catch (t: Throwable) {
            // Some Samsung One UI builds throw SecurityException from notify()
            // when the notification permission is revoked mid-scan; swallow so
            // the scan keeps running without UI updates rather than crashing.
            Log.w(TAG, "updateNotification failed: ${t.message}")
        }
    }

    private fun updateNotification(phaseName: String, progress: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            nm.notify(NOTIFICATION_ID, buildNotification(phaseName, progress))
        } catch (t: Throwable) {
            Log.w(TAG, "updateNotification(v2) failed: ${t.message}")
        }
    }

    private fun v2PhaseName(phase: net.neuralcheck.data.local.files.V2Phase): String = when (phase) {
        net.neuralcheck.data.local.files.V2Phase.INIT_PROFILING -> getString(R.string.scan_v2_phase_init_profiling)
        net.neuralcheck.data.local.files.V2Phase.STORAGE_STRESS -> getString(R.string.scan_v2_phase_storage_stress)
        net.neuralcheck.data.local.files.V2Phase.MEMORY_STRESS  -> getString(R.string.scan_v2_phase_memory_stress)
        net.neuralcheck.data.local.files.V2Phase.CPU_SUSTAINED  -> getString(R.string.scan_v2_phase_cpu_sustained)
        net.neuralcheck.data.local.files.V2Phase.GPU_SUSTAINED  -> getString(R.string.scan_v2_phase_gpu_sustained)
        net.neuralcheck.data.local.files.V2Phase.COMBINED_PEAK  -> getString(R.string.scan_v2_phase_combined_peak)
        net.neuralcheck.data.local.files.V2Phase.BURST_PATTERN  -> getString(R.string.scan_v2_phase_burst_pattern)
        net.neuralcheck.data.local.files.V2Phase.COOLDOWN       -> getString(R.string.scan_v2_phase_cooldown)
    }

    // Phase definitions
    enum class Phase {
        INIT,
        STORAGE,
        INTENSIVE_STRESS,
        SUSTAINED_STRESS,
        COOLDOWN
    }

    // Scan state sealed class for UI consumption
    sealed class ScanState {
        object Idle : ScanState()
        data class Running(
            val phase: Phase,
            val progressPercent: Float,
            val elapsedSeconds: Int,
            val scanId: String,
            val batteryTemp: Float = 0f,
            val cpuTemp: Float = -1f,
            val gpuTemp: Float = -1f,
            val cpuUsage: Float = 0f,
            val batteryLevel: Int = 100,
            /** V2 only — granular 0..7 phase id (8 phases). UI uses
             *  this to render V2 phase names when present, falling
             *  back to the V1 [phase] enum's label otherwise. */
            val v2PhaseId: Int? = null
        ) : ScanState()
        data class SafetyStopped(val reason: String) : ScanState()
        data class Completed(val scanId: String) : ScanState()
    }
}
