package net.neuralcheck.service.stress.v2

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.neuralcheck.data.local.files.DeviceTier
import net.neuralcheck.data.local.files.V2Phase
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.data.local.files.VerificationStates
import net.neuralcheck.data.local.files.VerificationSummary
import net.neuralcheck.service.sampler.DisciplinedSampler
import net.neuralcheck.util.StressNative

/**
 * V2 master plan §2 — 8-phase stress test orchestrator.
 *
 * Skeleton implementation. Wires the V2 components together (NDK
 * kernels via [StressNative], GPU via [GpuLoadGeneratorV2], adaptive
 * intensity via [AdaptiveIntensityController], thermal throttling via
 * [DynamicThermalThrottler]) but is NOT a drop-in replacement for the
 * V1 [net.neuralcheck.service.stress.StressTestService] — wiring it
 * into the production scan flow (Hilt module, foreground service
 * boilerplate, sampler integration with existing TimeSeriesFileWriter,
 * functional-test orchestration) is the next sub-phase after V2
 * components have been validated on a real device.
 *
 * Public surface is [runV2Scan] which executes the 8 phases in
 * sequence and returns the aggregated [VerificationSummary]. The
 * caller is responsible for:
 *   - Foregrounding any host service before invoking
 *   - Wiring sampling output into the existing TimeSeriesFileWriter
 *   - Persisting the returned VerificationSummary into the footer
 *
 * Total wall-clock duration: 480 s (8 minutes), plus up to a few
 * seconds of probe + throttler shutdown overhead.
 */
class StressTestServiceV2(
    private val context: Context,
    private val gpu: GpuLoadGeneratorV2 = GpuLoadGeneratorV2(),
    /** V2 fix #FIX-1.A/B: when non-null, the storage and memory phases
     *  publish their MB/s aggregates into the per-sample stream so v[20]
     *  (storage_throughput_mbps) and v[21] (memory_bandwidth_mbps) carry
     *  non-zero values in those phases. Default null preserves existing
     *  test/instrumentation callers; the production wrapper passes the
     *  active sampler. */
    private val sampler: DisciplinedSampler? = null,
    /** Phase 1.6.B: AI-camouflage workload for the CPU phases.
     *  Provided by the production wrapper when the feature flag is on;
     *  null in tests / instrumentation that don't need it. */
    private val aiCpuWorkload: AiCpuWorkload? = null,
    /** Phase 1.6.C: AI-camouflage workload for the GPU phases. */
    private val aiGpuWorkload: AiGpuWorkload? = null,
    /** Phase 1.6.D: image-processing memory workload. Replaces the
     *  C++ March-C / Walking-Ones kernel under [USE_AI_WORKLOAD_MEMORY]. */
    private val aiMemoryWorkload: AiMemoryWorkload? = null,
    /** Phase 1.8 Task 5: relative-calibration result. When non-null the
     *  per-phase workload params (CPU iter/sec, memory rate + buffer,
     *  GPU FBO side) come from this object instead of
     *  [AdaptiveIntensityController.aiConfigForTier]. Default null
     *  preserves Phase 1.7 behaviour for callers that don't supply it
     *  (every existing test + instrumentation path). The V1 wrapper
     *  passes a non-null value only when the
     *  `BuildConfig.USE_RELATIVE_CALIBRATION` flag is true AND the
     *  pre-scan probe completed successfully. AiGpuWorkload's
     *  `targetIterPerSec` stays tier-based (no GPU-inference probe yet);
     *  the GPU FBO side is the GPU-side knob that gets calibrated. */
    private val calibratedConfig: net.neuralcheck.calibration.CalibratedWorkloadConfig? = null,
    /** G2/P2.3: SystemClock.elapsedRealtime() at scan start — the same
     *  value passed to DisciplinedSampler.start(scanStartTime, ...), so
     *  [PhaseBoundary] timestamps land in the same reference frame as
     *  v[0]. Defaults to "now" so existing tests/instrumentation that
     *  don't care about phase boundaries don't need to pass it; the
     *  production wrapper (StressTestService) passes its own
     *  scanStartTime explicitly. */
    private val scanStartTime: Long = SystemClock.elapsedRealtime()
) {
    companion object {
        private const val TAG = "NeuralCheck.V2.Service"

        /** Phase 1.6.B feature flag — true: phase 3 + the CPU half of
         *  phase 5 run AI inference instead of synthetic NEON FMA / SHA
         *  / prime sieve. Stage F flips to true permanently after
         *  hardware validation; meanwhile flipping to false is a clean
         *  rollback to the V5 synthetic kernel.
         *  TODO(phase-1.6.F): remove flag after validation. */
        private const val USE_AI_WORKLOAD_CPU: Boolean = true

        /** Phase 1.6.C feature flag — true: phase 4 GPU stress + phase
         *  5 GPU half use the raymarching fragment shader plus AI
         *  inference on the GPU delegate, in parallel. false: legacy
         *  V5 8-texture/30-ALU shader, no AI. Rollback path: flip false. */
        private const val USE_AI_WORKLOAD_GPU: Boolean = true

        /** Phase 1.6.D feature flag — true: phase 2 (MEMORY_STRESS)
         *  uses the image-processing buffer pattern instead of the
         *  C++ March-C / Walking-Ones kernel. v[9] memory_bandwidth_mbps
         *  is still populated; the bandwidth signal shifts from
         *  "synthetic linear sweep" to "convolution-style mixed access". */
        private const val USE_AI_WORKLOAD_MEMORY: Boolean = true

        /** Phase 1.15 (Fix 1): floor for the STUB→NEON fallback duration.
         *  STUB is detected within one model load (~hundreds of ms) via
         *  [AiCpuWorkload]'s early probe, so in practice the fallback runs
         *  for almost the full phase; this floor only guards the degenerate
         *  case where detection somehow consumed most of the phase budget. */
        private const val MIN_NEON_FALLBACK_MS: Long = 5_000L
    }

    /** Phase 1.6.C: when [USE_AI_WORKLOAD_GPU] is true the V2 service
     *  swaps in a fresh [GpuLoadGeneratorV2] built with the raymarching
     *  shader instead of the constructor-provided one. Keeps the
     *  rollback path (`USE_AI_WORKLOAD_GPU = false`) hooked into the
     *  same [gpu] instance the legacy code expects.
     *
     *  Phase 1.8 Task 5: when [calibratedConfig] is supplied (only
     *  when the V1 wrapper's USE_RELATIVE_CALIBRATION flag is on),
     *  the FBO side comes from the calibration. Otherwise the
     *  constructor's default kicks in (legacy 2048). */
    private val gpuActive: GpuLoadGeneratorV2 =
        if (USE_AI_WORKLOAD_GPU) {
            val cfg = calibratedConfig
            if (cfg != null) {
                GpuLoadGeneratorV2(useRaymarchShader = true, fboPixels = cfg.gpuFboSidePixels)
            } else {
                GpuLoadGeneratorV2(useRaymarchShader = true)
            }
        } else {
            gpu
        }

    /**
     * Phase 1.8 Task 5 — pick the correct [AiWorkloadConfig] for a given
     * phase. When [calibratedConfig] is null (flag off OR probe failed)
     * every phase gets [tierConfig] verbatim — bit-for-bit identical to
     * Phase 1.7 behaviour. When [calibratedConfig] is non-null the
     * tier-based config is overridden per spec:
     *   - MEMORY_STRESS → memoryIterationRateHz + memoryBufferBytes
     *   - CPU_SUSTAINED → cpuInferencesPerSec
     *   - GPU_SUSTAINED → tier-based (AiGpuWorkload not calibrated)
     *   - COMBINED_PEAK → cpuInferencesPerSec for the CPU half;
     *     the GPU half reuses this same config but AiGpuWorkload's
     *     own logic only consumes targetIterPerSec, which is OK because
     *     the calibrated value is in the same units. (Out-of-scope:
     *     having a separate GPU-half config; the existing single-config
     *     contract pre-dates calibration.)
     */
    private fun configForPhase(
        phase: V2Phase,
        tierConfig: AiWorkloadConfig
    ): AiWorkloadConfig {
        val cal = calibratedConfig ?: return tierConfig
        return when (phase) {
            V2Phase.MEMORY_STRESS -> tierConfig.copy(
                targetIterPerSec = cal.memoryIterationRateHz.toInt().coerceAtLeast(1),
                memoryBufferBytes = cal.memoryBufferBytes
            )
            V2Phase.CPU_SUSTAINED, V2Phase.COMBINED_PEAK -> tierConfig.copy(
                targetIterPerSec = cal.cpuInferencesPerSec.toInt().coerceAtLeast(1)
            )
            // Phase 4 GPU_SUSTAINED stays tier-based per spec — there
            // is no GPU-inference probe yet; the GPU calibration knob
            // is the FBO side, applied to GpuLoadGeneratorV2 directly
            // (see `gpuActive` initialisation above) rather than via
            // AiWorkloadConfig.
            else -> tierConfig
        }
    }

    /** Aggregate result of one V2 scan. Caller writes [verification]
     *  into TimeSeriesFooter; the rest is for logging / diagnostics. */
    data class V2ScanResult(
        val verification: VerificationSummary,
        val deviceTier: DeviceTier,
        val intensityFactor: Float,
        val tempRiseRateInProbe: Float,
        val phaseCompletionStatus: Map<Int, String>,
        val throttlingEventCount: Int,
        // Bug 5b fix: full event list for footer transparency. AI uses
        // this raw list to correlate phase timing anomalies with skin temp
        // spikes. The thresholds that produced these events live in
        // DynamicThermalThrottler and are deliberately not changed here —
        // the AI judges, the code merely reports.
        val throttlingEvents: List<net.neuralcheck.data.local.files.ThrottlingEvent> = emptyList(),
        /**
         * V2 fix #56-3 / Commit 3 (fieldsVersion 59): true when phase 3 watcher
         * detected the OS policy-capping big-cluster freq below 70% of nominal
         * with no thermal headroom signal. When true, phases 4/5/6 were
         * skipped — see [phaseCompletionStatus] for "skipped_governor_capped"
         * tags. StressTestService propagates this flag into the footer's
         * [net.neuralcheck.data.local.files.StressEffectiveness.governorCappingDetected].
         */
        val governorCappingDetected: Boolean = false,
        /** G2/P2.3: observed start/end (ms since scan start, same frame as
         *  v[0]) of every phase this scan actually ran. See
         *  [net.neuralcheck.data.local.files.PhaseBoundary]. */
        val phaseBoundaries: List<net.neuralcheck.data.local.files.PhaseBoundary> = emptyList()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var currentPhaseJob: Job? = null

    /**
     * Bug fix #19: expose the live throttler so the StressTestService's
     * phaseDriver can read throttler.getCurrentPhase() and synchronise
     * the sampler with helper's actual phase progression. Set at the
     * top of runV2Scan(); cleared (left dangling) after the scan
     * completes — callers should treat null as "scan not running".
     * Volatile because phaseDriver runs on a different coroutine than
     * runV2Scan.
     */
    @Volatile
    var activeThrottler: DynamicThermalThrottler? = null
        private set

    /**
     * V2 fix #56-3 / Commit 3 (fieldsVersion 59): governor capping detection.
     *
     * Set TRUE by [launchGovernorCappingWatcher] when, ~10-15 s into phase 3,
     * the big cluster freq stays below 70% of nominal AND PowerManager's
     * getThermalHeadroom returns sentinel/near-zero (signal blocked or
     * device cool). This pattern is HyperOS-specific: OS caps frequency
     * by policy, not thermal pressure, so headroom never registers and
     * the stress test cannot exercise the silicon.
     *
     * runV2Scan reads this flag after CPU_SUSTAINED completes; if set, it
     * skips phases 4 (GPU_SUSTAINED), 5 (COMBINED_PEAK), 6 (BURST_PATTERN)
     * and runs only phase 7 COOLDOWN — total scan ~3 minutes instead of
     * ~8. phaseCompletionStatus marks skipped phases as
     * "skipped_governor_capped" so the AI knows the gap is intentional.
     */
    @Volatile
    var governorCappingDetected: Boolean = false
        private set

    /** G2/P2.3: recorded as each phase's body starts/finishes inside
     *  [runPhase] (and, for phase 0, inline in [runV2Scan]).
     *  synchronizedList since runPhase's body executes inside a launched
     *  child coroutine on [scope] — same defensive pattern
     *  DisciplinedSampler.sampleBuffer already uses. */
    private val phaseBoundaries =
        java.util.Collections.synchronizedList(mutableListOf<net.neuralcheck.data.local.files.PhaseBoundary>())

    /** fieldsVersion 68 (S-30): named sub-windows a phase body declares, attached to
     *  that phase's [PhaseBoundary] when [runPhase] records it. Written from the
     *  phase's coroutine, read by [runPhase] after it joins. */
    private val phaseSubWindows =
        java.util.concurrent.ConcurrentHashMap<Int, List<net.neuralcheck.data.local.files.PhaseWindow>>()

    private fun declarePhaseWindow(phaseId: Int, window: net.neuralcheck.data.local.files.PhaseWindow) {
        phaseSubWindows.compute(phaseId) { _, current -> (current ?: emptyList()) + window }
    }

    /** fieldsVersion 68 (T-17): the memory verification kernel's Kotlin half. */
    private val memoryVerifyRunner = MemoryVerifyRunner()

    /** fieldsVersion 68 (T-16): the storage read-back's Kotlin half. */
    private val storageVerifyRunner = StorageVerifyRunner()

    /**
     * fieldsVersion 68 (decision D, T-20): starts the CPU known-answer checks of
     * [phase] as a SIBLING of the AI workload — a coroutine of its own, not part
     * of `AiCpuWorkload`. Its failures never take the phase down: a native error is
     * already a status inside the scheduler.
     */
    private fun launchKnownAnswerChecks(
        scheduler: CpuKnownAnswerScheduler,
        phase: V2Phase,
        phaseStartRealtime: Long
    ): Job = scope.launch {
        try {
            scheduler.runPhase(phase.phaseId, phaseStartRealtime, phase.durationMs, scanStartTime)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "known-answer checks of phase ${phase.phaseId} threw: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Cancels the checks and WAITS for the one that may be mid-call (a native call cannot be
     *  interrupted; it returns within a fraction of a second), so the runs are final before
     *  the next phase starts. */
    private suspend fun stopKnownAnswerChecks(job: Job?) {
        if (job == null) return
        withContext(NonCancellable) { job.cancelAndJoin() }
    }

    /**
     * Executes all 8 V2 phases and returns the aggregate result.
     * Suspending — must be called from a coroutine. Cancellation
     * (scope.cancel(), or the calling job's cancellation) cleanly
     * stops the current phase and tears down GPU + throttler
     * resources.
     */
    suspend fun runV2Scan(): V2ScanResult {
        Log.i(TAG, "runV2Scan: starting 8-phase V2 scan (480 s nominal)")

        // V2 fix #56-3 / Commit 3: reset governor-capping flag at scan start
        // so a previous scan's verdict can't leak into this one.
        governorCappingDetected = false

        // ─────── Phase 0: INIT_PROFILING + adaptive intensity probe ───────
        val phaseStatus = mutableMapOf<Int, String>()
        // G2/P2.3: phase 0 doesn't go through runPhase() (it has no
        // throttler yet — the throttler is constructed AFTER this probe,
        // just below), so its boundary is recorded inline here instead.
        val phase0StartMs = SystemClock.elapsedRealtime() - scanStartTime
        val (probeResult, phase0Status) = executePhase0Init()
        val phase0EndMs = SystemClock.elapsedRealtime() - scanStartTime
        phaseStatus[V2Phase.INIT_PROFILING.phaseId] = phase0Status
        phaseBoundaries.add(
            net.neuralcheck.data.local.files.PhaseBoundary(
                phaseId = V2Phase.INIT_PROFILING.phaseId,
                phaseName = V2Phase.INIT_PROFILING.phaseName,
                startMs = phase0StartMs,
                endMs = phase0EndMs
            )
        )

        // Phase 1.6.E: build the tier-aware AI config once and pass it
        // into every workload for the remainder of the scan. Survives
        // even when the AI flags are off — the workloads just don't
        // reach for it then.
        val aiConfig = AdaptiveIntensityController.aiConfigForTier(probeResult.tier)
        Log.i(
            TAG,
            "aiConfigForTier(${probeResult.tier.tag}) = " +
                "cpu=${aiConfig.cpuModelChoice} gpu=${aiConfig.gpuModelChoice} " +
                "iter/s=${aiConfig.targetIterPerSec}"
        )

        // Throttler starts AFTER probe; runs throughout phases 1–7.
        val throttler = DynamicThermalThrottler(context, probeResult.tier.intensityFactor)
        // Expose the throttler instance so the outer
        // StressTestService.executePhasesV2 phaseDriver can read
        // throttler.getCurrentPhase() and stay synced with helper.
        activeThrottler = throttler
        throttler.start(onTerminatePhase = { currentPhaseJob?.cancel() })

        // Verification accumulators populated as phases run.
        var verifPrimeOk = true
        var verifShaOk = true
        var verifFmaOk = true
        var verifGpuComputeOk = true
        var verifGpuFragMatches = 0
        var verifGpuFragTotal = 0
        // Phase 1.7.C: AI workload aggregates surfaced into
        // VerificationSummary.aiInferenceIterationsTotal/Workers/AvgMs/
        // ActiveDelegate. Iterations sum across phases 2+3+4+5; worker
        // count + delegate take the latest CPU phase value (phase 5
        // overwrites phase 3 — same forced delegate, same parallelism).
        // Avg is weighted by the number of contributing phases.
        var aiIterationsTotal: Long = 0L
        var aiWorkerCount: Int = 0
        var aiAvgMsSum: Float = 0f
        var aiAvgMsCount: Int = 0
        var aiActiveDelegate: String = "NONE"
        // Bug fix #20: deterministic GPU benchmark — single-frame
        // glFinish timing at start and end of GPU phase. Immune to
        // OpenGL ES pipelining noise that breaks counter-based FPS.
        var gpuRenderColdMs: Long = -1L
        var gpuRenderWarmMs: Long = -1L
        // Bug-fix: aggregate CPU kernel counters across CPU_SUSTAINED + COMBINED_PEAK
        // phases. Both phases call runCpuKernelsForDuration which calls into
        // production_stress's start/stop pair; counters reset between calls in C++,
        // so we must sum here in Kotlin.
        var verifCpuFmaIterationsTotal = 0L
        var verifCpuFmaIterationsVerified = 0L
        var verifCpuFmaErrorsTotal = 0
        var verifCpuShaHashesVerifiedTotal = 0
        // Bug fix: separate attempts counter from C++ side, allowing
        // AI to compute SHA mismatch rate = (attempts - verified).
        var verifCpuShaHashesAttemptedTotal = 0
        // V2 fix #56-3 (fieldsVersion 59): per-cluster pin telemetry. Pinning
        // is stable per start_cpu_stress batch (workers pin once at thread
        // launch and stay there for the life of the batch). We track LAST-
        // observed values from the most recent runCpuKernelsForDuration call —
        // both CPU_SUSTAINED and COMBINED_PEAK call into the same NDK code
        // and produce the same pinning numbers, so last-write-wins is fine.
        var lastWorkersPinnedBig = 0
        var lastWorkersPinnedMid = 0
        var lastWorkersPinnedLittle = 0
        var lastWorkersPinFailedBig = 0
        var lastWorkersPinFailedMid = 0
        var lastWorkersPinFailedLittle = 0
        // fieldsVersion 68 (ت-13, N-12): true once any CPU phase ran the NEON
        // pinning path (accumulateCpuOutcome). On the AI workload path nothing
        // tries to pin, and the six counters must say so instead of reading 0.
        var workersPinAttempted = false
        var workersPinUnreadableSize: Int? = null
        // Bug 6 fix: raw throughput segments captured during STORAGE_STRESS.
        // Backend computes slope and SLC cache size from this list.
        val storageSegments = mutableListOf<net.neuralcheck.data.local.files.StorageThroughputSegment>()
        // fieldsVersion 68 (decision D, T-20): the CPU known-answer checks of phases 3
        // and 5. One scheduler per scan: its runs are this scan's.
        val knownAnswerScheduler = CpuKnownAnswerScheduler()
        // fieldsVersion 68 (T-16): failed fsyncs of the write loop, and what the
        // read-back of the last 256 MiB produced (or why it did not run).
        var storageWriteSyncFailures = 0
        // fieldsVersion 68, part 2-c (user decision 2026-10-08: "write errors, fsync among
        // them, ship whenever the write ran, whether the read-back ran or not"): did the
        // write loop reach a write, and did a write throw. See StorageWriteErrors.
        var storageWriteAttempted = false
        var storageWriteExceptions = 0
        var storageOutcome: net.neuralcheck.data.local.files.StorageVerifyOutcome =
            net.neuralcheck.data.local.files.StorageVerifyOutcome.notRun(VerificationStates.REASON_PHASE_NOT_REACHED)
        // Bytes the AI memory workload touched in MEMORY_STRESS (the bandwidth
        // denominator). NOT the memory verification kernel's coverage — that is
        // memoryVerifyBytes, a different source (fieldsVersion 68, T-17).
        var verifMemoryTestedBytes: Long = 0L
        // fieldsVersion 66 telemetry: GPU fence timeouts across phases 4+5
        // (v[10] trust), and the storage write loop's real extent so an
        // all-zero v[8] tail reads as "write finished", not "storage died".
        var gpuFenceTimeouts = 0
        var storageBytesWrittenTotal = -1L
        var storageWriteDurationMs = -1L
        // Phase 1.15 (Fix 1 + Fix 4): STUB→NEON fallback telemetry.
        // aiDelegateInitError carries the captured TFLite failure cause when
        // the CPU delegate fell to STUB; cpuStressFallbackUsed is true when
        // the synthetic NEON kernels ran because the AI workload could not
        // load a real delegate. Both surface into VerificationSummary so the
        // footer + saturation gate can tell "harness loaded the CPU" apart
        // from "harness idled".
        var aiDelegateInitError: String? = null
        var cpuStressFallbackUsed = false

        // Phase 1.15 (Fix 1): shared accumulation of a NEON CpuKernelOutcome
        // into the verification + pin-telemetry accumulators. Used by the
        // legacy rollback else-branches AND by the new STUB→NEON fallback so
        // workersPinned* reflect reality whenever the NEON path actually ran.
        val accumulateCpuOutcome: (CpuKernelOutcome) -> Unit = { o ->
            // fieldsVersion 68 (ت-13): the pin counters are real only when the
            // native result carried the whole pin tail; a shorter array is an
            // unreadable result, not "attempted, nothing pinned".
            if (o.pinTelemetryComplete) workersPinAttempted = true
            else workersPinUnreadableSize = o.resultSize
            if (!o.primeOk) verifPrimeOk = false
            if (!o.shaOk) verifShaOk = false
            if (!o.fmaOk) verifFmaOk = false
            verifCpuFmaIterationsTotal += o.iterations
            verifCpuFmaIterationsVerified += o.verifiedIterations
            verifCpuFmaErrorsTotal += o.fmaErrors
            verifCpuShaHashesVerifiedTotal += o.shaHashesVerified
            verifCpuShaHashesAttemptedTotal += o.shaHashesAttempted
            if (o.pinTelemetryComplete) {
                lastWorkersPinnedBig = o.workersPinnedBig
                lastWorkersPinnedMid = o.workersPinnedMid
                lastWorkersPinnedLittle = o.workersPinnedLittle
                lastWorkersPinFailedBig = o.workersPinFailedBig
                lastWorkersPinFailedMid = o.workersPinFailedMid
                lastWorkersPinFailedLittle = o.workersPinFailedLittle
            }
        }

        // Phase 1.15 (Fix 1): run the synthetic NEON kernels as a fallback
        // when the AI CPU workload fell to STUB. Runs for [remainingMs]
        // (floored at MIN_NEON_FALLBACK_MS) so the CPU is actually loaded
        // for the rest of the phase. "Degrade, don't crash": only
        // CancellationException propagates; any other failure is logged and
        // swallowed so a missing libstress.so can't abort the scan.
        suspend fun runNeonFallback(remainingMs: Long) {
            cpuStressFallbackUsed = true
            val dur = remainingMs.coerceAtLeast(MIN_NEON_FALLBACK_MS)
            Log.w(TAG, "CPU STUB → NEON fallback: running synthetic kernels for ${dur}ms")
            try {
                accumulateCpuOutcome(
                    runCpuKernelsForDuration(dur, probeResult.tier.intensityFactor)
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "NEON fallback failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        try {
            // ─────── Phase 1: STORAGE_STRESS ───────
            // Sustained write loop into cacheDir (capped at 4 GB). Bug 6 fix:
            // measure throughput per 256-MiB segment and capture the raw
            // sequence into storageSegments. The backend's
            // computeStorageCurveSlope consumes this list — the mobile
            // code does NOT compute slope, threshold, or SLC cache size
            // here. Code collects, backend judges.
            phaseStatus[V2Phase.STORAGE_STRESS.phaseId] = runPhase(V2Phase.STORAGE_STRESS, throttler) {
                val phaseStartMs = SystemClock.elapsedRealtime()
                val deadline = phaseStartMs + V2Phase.STORAGE_STRESS.durationMs
                val target = java.io.File(context.cacheDir, "v2_storage_stress.tmp")
                // V2 fix #30: cap ديناميكي حسب cacheDir.usableSpace.
                // الفلسفة: نريد ضغط أقصى ممكن، لكن نحمي من ENOSPC.
                // النسبة: 80% من المتاح، حدود 1 GB إلى 4 GB.
                // Pre-flight check ضمن أن usableSpace >= 6 GB، لذا 80% = 4.8 GB
                // ويُقصّ إلى 4 GB cap في معظم الأجهزة.
                //
                // ─────────────────────────────────────────────────────────
                // DECIDED 2026-09-14 (fieldsVersion 67) — DO NOT REOPEN
                // WITHOUT NEW NUMBERS. The byte cap stays a BYTE cap. The
                // phase is deliberately NOT bound to wall-clock time.
                //
                // The temptation: v[8] storage_throughput_mbps goes to zero
                // once the cap is reached, so a fast device leaves most of
                // the 75 s phase with a flat zero channel — Honor MTN-NX1
                // wrote its 4 GiB in 8.6 s (89.3 % of phase-1 samples read
                // 0), Samsung S10 5G took 58.0 s (26.3 % zeros). Recycling
                // the file to keep writing for the full 75 s would fill the
                // channel.
                //
                // Why we will not do it — measured cost of that change:
                //   device speed   per scan        vs now   scans = one
                //                                           full 128 GB write
                //   500 MiB/s      36.6 GiB        9.2x     3.3
                //    75 MiB/s       5.5 GiB        1.4x    21.7
                //   (today)         4.0 GiB        1.0x    29.8
                // At flagship speed that is ~8 days of a phone's normal
                // write volume per scan, and 0.03–0.12 % of the device's
                // rated NAND endurance (TLC 300–1000 P/E, WAF 1.15) burned
                // on EVERY scan. The device belongs to the customer, not to
                // us; a diagnostic must not spend a measurable share of the
                // hardware's life to prettify one channel.
                //
                // The zeros are not a data gap: verificationResults
                // .storageBytesWritten + .storageWriteDurationMs (added in
                // fieldsVersion 66) give the exact extent of the write loop,
                // and the backend already derives throughput from that pair.
                // v[8] = 0 after the last non-zero value means "cap reached",
                // not "I/O collapsed" — that distinction is what those two
                // fields exist to carry.
                // ─────────────────────────────────────────────────────────
                val usableSpace = context.cacheDir.usableSpace
                val targetCap = (usableSpace * 0.8).toLong()
                val cap = targetCap.coerceIn(
                    1L * 1024L * 1024L * 1024L,  // 1 GB حد أدنى
                    4L * 1024L * 1024L * 1024L   // 4 GB حد أعلى — راجع القرار أعلاه
                )
                Log.i(TAG, "STORAGE cap=${cap / 1024 / 1024 / 1024}GB " +
                    "(usable=${usableSpace / 1024 / 1024 / 1024}GB)")
                var bytesWritten = 0L

                val segmentBytes = 256L * 1024 * 1024  // 256 MiB per segment
                var segmentStartNs = System.nanoTime()
                var segmentBaseBytes = 0L
                // fieldsVersion 68 (T-16): where the write loop ended (the read-back
                // that follows must not count as write time), what threw it, and what
                // the read-back produced.
                var writeLoopEndMs = -1L
                var writeFailure: String? = null
                var readBackOutcome: net.neuralcheck.data.local.files.StorageVerifyOutcome? = null

                try {
                    withContext(Dispatchers.IO) {
                        // fieldsVersion 68 (T-16): the chunk is filled and stamped by the
                        // native pattern so every byte of the file is a function of its
                        // offset; without the native library it is EXACTLY the old content.
                        val pattern = StorageWritePattern()
                        java.io.FileOutputStream(target).use { fos ->
                            val chunk = pattern.chunk
                            while (SystemClock.elapsedRealtime() < deadline &&
                                bytesWritten < cap &&
                                isActive
                            ) {
                                pattern.stamp(bytesWritten / chunk.size)
                                storageWriteAttempted = true
                                fos.write(chunk)
                                bytesWritten += chunk.size

                                if (bytesWritten % (32L * 1024 * 1024) == 0L) {
                                    runCatching { fos.fd.sync() }.onFailure { storageWriteSyncFailures++ }
                                }
                                // Segment boundary: compute mbps for this
                                // window and append. No analysis — just record.
                                if (bytesWritten - segmentBaseBytes >= segmentBytes) {
                                    val elapsedNs = System.nanoTime() - segmentStartNs
                                    if (elapsedNs > 0) {
                                        // A70: was `bytes/ns × 1e9 / 1e6` — decimal
                                        // MB/s (÷10⁶) — while v[9] memory_bandwidth_mbps
                                        // (AiMemoryWorkload.kt) computes binary MiB/s
                                        // (÷2²⁰). Both v[8] and v[9] were labeled
                                        // "MB/s" (V2_VARIABLE_DEFINITIONS) despite
                                        // differing by a factor of ~1.048576 — see
                                        // docs/MEASUREMENT_SPEC.md §3.1 note 1. Also
                                        // matches S-03/S-04 sequential storage speed
                                        // (StorageTests.kt), which already divide by
                                        // 1024×1024. Switched v[8] to the same ÷2²⁰
                                        // convention as v[9] and S-03/S-04 rather than
                                        // the reverse, since three of the four existing
                                        // "MB/s" computations already used it.
                                        //
                                        // SCHEMA CHANGE: scans recorded at fieldsVersion
                                        // <= 67 have v[8] in decimal MB/s; this file's
                                        // v[8] is binary MiB/s from the build that first
                                        // ships this fix onward. A consumer must branch
                                        // on footer.fieldsVersion to interpret v[8]
                                        // correctly across old vs new scans — the same
                                        // pattern already used for v[6]/v[10] (see
                                        // V2Models.kt's fieldsVersion 66 SEMANTIC FIX
                                        // comments). This file does not bump
                                        // FIELDS_VERSION — that is a build.gradle.kts
                                        // decision plus a backend/systemPrompt change,
                                        // both out of scope here.
                                        val mbps = ((bytesWritten - segmentBaseBytes).toDouble()
                                            / (elapsedNs / 1_000_000_000.0)
                                            / (1024.0 * 1024.0)).toFloat()
                                        storageSegments += net.neuralcheck.data.local.files
                                            .StorageThroughputSegment(
                                                cumulativeBytes = bytesWritten,
                                                mbps = mbps
                                            )
                                        // V2 fix #FIX-1.A: publish to sampler so v[20]
                                        // storage_throughput_mbps carries this segment's
                                        // throughput until the next segment overwrites
                                        // it (or the phase resets to 0). Setter is
                                        // cheap (one AtomicLong store) — safe to call
                                        // from this IO coroutine.
                                        sampler?.setStorageThroughput(mbps)
                                        Log.i("NeuralCheck.V2.Service",
                                            "phase 1 segment ${storageSegments.size}: " +
                                                "${bytesWritten / (1024 * 1024)} MiB, " +
                                                "${"%.1f".format(mbps)} MB/s")
                                    }
                                    segmentStartNs = System.nanoTime()
                                    segmentBaseBytes = bytesWritten
                                }
                            }
                            runCatching { fos.fd.sync() }.onFailure { storageWriteSyncFailures++ }
                        }
                        // The write loop is over and the file is closed and fsynced.
                        writeLoopEndMs = SystemClock.elapsedRealtime()
                        // v[8] is the WRITE throughput: clear it before the read-back so the
                        // read does not keep emitting the last segment's MB/s.
                        sampler?.setStorageThroughput(0f)
                        Log.i(
                            "NeuralCheck.V2.Service",
                            "phase 1 sustained write: ${bytesWritten / (1024 * 1024)} MiB, " +
                                "${storageSegments.size} segments captured"
                        )
                        // fieldsVersion 68 (T-16): read the last 256 MiB back, in what is
                        // left of the phase, BEFORE the file is deleted below.
                        readBackOutcome = pattern.unavailableReason?.let { why ->
                            net.neuralcheck.data.local.files.StorageVerifyOutcome.notRun(
                                "${VerificationStates.REASON_PATTERN_UNAVAILABLE}:$why"
                            )
                        } ?: storageVerifyRunner.run(
                            target.absolutePath,
                            bytesWritten,
                            V2Phase.STORAGE_STRESS.durationMs - (SystemClock.elapsedRealtime() - phaseStartMs)
                        )
                    }
                } catch (t: Throwable) {
                    Log.w("NeuralCheck.V2.Service", "phase 1 storage write failed: ${t.message}")
                    // A cancelled phase is not a failed write; anything else that threw is.
                    if (t !is kotlinx.coroutines.CancellationException) {
                        writeFailure = net.neuralcheck.util.ReasonText.of(t)
                        // A throw after a write was attempted is a write error (an I/O
                        // exception, N-09); one before the first write (the file could not
                        // be opened) is not a write that ran.
                        if (storageWriteAttempted) storageWriteExceptions++
                    }
                } finally {
                    runCatching { target.delete() }
                }
                storageOutcome = when {
                    writeFailure != null -> net.neuralcheck.data.local.files.StorageVerifyOutcome.notRun(
                        "${VerificationStates.REASON_WRITE_FAILED}:$writeFailure"
                    )
                    readBackOutcome != null -> readBackOutcome!!
                    // Cancelled before / during the read-back: a result the native call had
                    // already returned is kept, otherwise it says it was cancelled.
                    else -> storageVerifyRunner.lastOutcome
                        ?: net.neuralcheck.data.local.files.StorageVerifyOutcome.notRun(VerificationStates.REASON_CANCELLED)
                }
                // fieldsVersion 66: record how long the write loop actually
                // ran and how much it wrote. The byte cap above is
                // deliberately unchanged (flash-wear decision, 2026-09-14);
                // these two fields let the AI read the zero tail of v[8]
                // correctly.
                storageWriteDurationMs =
                    (if (writeLoopEndMs > 0L) writeLoopEndMs else SystemClock.elapsedRealtime()) - phaseStartMs
                storageBytesWrittenTotal = bytesWritten
                // V2 fix #FIX-1.A: I/O loop has stopped — clear v[20] before
                // the wait-out so trailing samples (and the next phase's
                // memory-stress samples) don't keep emitting the last
                // segment's MB/s.
                sampler?.setStorageThroughput(0f)
                // V2 fix #30: المدة الثابتة. لو الـ workload انتهى قبل المدة
                // الاسمية (مثلاً وصلنا cap في 24 ثانية بدل 75)، ننتظر الباقي.
                // هذا يضمن: عدالة المقارنة بين الأجهزة + فرصة thermal capture
                // متساوية + الـ AI يستطيع المقارنة الزمنية.
                val phaseElapsed = SystemClock.elapsedRealtime() - phaseStartMs
                val phaseRemaining = V2Phase.STORAGE_STRESS.durationMs - phaseElapsed
                if (phaseRemaining > 0) {
                    Log.i(TAG, "STORAGE_STRESS workload completed in ${phaseElapsed}ms, " +
                        "waiting ${phaseRemaining}ms to fill nominal duration")
                    delay(phaseRemaining)
                }
            }

            // ─────── Phase 2: MEMORY_STRESS ───────
            // fieldsVersion 68 (T-17, S-11, S-30): the verification KERNEL runs
            // FIRST, under a time cap inside the fixed 60 s, then the AI workload
            // takes the rest. The kernel (C++, tile by tile) is March C- + Walking
            // Ones + a size-independent random verify over the biggest safe buffer;
            // a failure to size or map it is a STATE, never {0,0,0,0}. Both
            // windows are declared (phaseBoundaries and verificationResults),
            // because while the kernel runs nothing publishes v[9] — a zero there
            // is "no publisher", and the window is what says so.
            //
            // Until fieldsVersion 68 the kernel never ran in production: with
            // USE_AI_WORKLOAD_MEMORY = true the phase was the AI workload alone.
            phaseStatus[V2Phase.MEMORY_STRESS.phaseId] = runPhase(V2Phase.MEMORY_STRESS, throttler) {
                val phaseStartMs = SystemClock.elapsedRealtime()
                // 40% RAM cap derived from intensityFactor (master plan §2.7) — a
                // budget-cooled device gets a smaller buffer so it doesn't OOM-kill
                // the foreground service.
                val intensityPct = (probeResult.tier.intensityFactor * 100f).toInt().coerceIn(40, 100)
                val ramPercent = (40 * intensityPct / 100).coerceAtLeast(20)

                val kernelStartMs = SystemClock.elapsedRealtime()
                try {
                    memoryVerifyRunner.run(
                        ramPercent,
                        V2Phase.MEMORY_STRESS.durationMs - (kernelStartMs - phaseStartMs)
                    )
                } finally {
                    // Declared even when the phase is cancelled mid-kernel: the kernel
                    // stops at its next step and the window is how long it really held.
                    declarePhaseWindow(
                        V2Phase.MEMORY_STRESS.phaseId,
                        net.neuralcheck.data.local.files.PhaseWindow(
                            net.neuralcheck.data.local.files.PhaseWindowNames.MEMORY_KERNEL,
                            kernelStartMs - scanStartTime,
                            SystemClock.elapsedRealtime() - scanStartTime
                        )
                    )
                }

                // …then the AI workload for what is left of the phase.
                val workloadStartMs = SystemClock.elapsedRealtime()
                val workloadBudgetMs = V2Phase.MEMORY_STRESS.durationMs - (workloadStartMs - phaseStartMs)
                if (USE_AI_WORKLOAD_MEMORY && aiMemoryWorkload != null && workloadBudgetMs > 0L) {
                    // Phase 1.6.D path. The image-processing buffer pattern
                    // generates DRAM bandwidth without the synthetic March-C
                    // signature. `verifMemoryTestedBytes` is derived from the
                    // workload's bytesTouched counter so the v[9]
                    // memory_bandwidth_mbps publish path keeps working unchanged
                    // (it is NOT the kernel's coverage: that is memoryVerifyBytes).
                    try {
                        val memResult = aiMemoryWorkload.run(
                            workloadBudgetMs,
                            probeResult.tier.intensityFactor,
                            configForPhase(V2Phase.MEMORY_STRESS, aiConfig),
                            // fieldsVersion 66: live v[9] — the workload publishes
                            // its rolling MB/s ~1 Hz; one AtomicLong store each.
                            onBandwidth = { mbps -> sampler?.setMemoryBandwidth(mbps) }
                        )
                        verifMemoryTestedBytes = memResult.bytesTouched
                        // Phase 1.7.C: count memory passes as inference iterations.
                        aiIterationsTotal += memResult.iterations.toLong()
                    } finally {
                        declarePhaseWindow(
                            V2Phase.MEMORY_STRESS.phaseId,
                            net.neuralcheck.data.local.files.PhaseWindow(
                                net.neuralcheck.data.local.files.PhaseWindowNames.MEMORY_WORKLOAD,
                                workloadStartMs - scanStartTime,
                                SystemClock.elapsedRealtime() - scanStartTime
                            )
                        )
                    }
                }
                // (With the AI workload off the phase is the kernel and the wait-out
                // below — the old rollback branch that ran the C++ stress for the
                // whole phase went with `runMemoryStress`.)

                // fieldsVersion 66: the Phase 1.7.A in-memory backfill is
                // gone — it patched the sampler's buffer after the writer had
                // already flushed most phase-2 samples (flush every 10), so
                // the file kept zeros for all but a ≤9-sample tail and no
                // consumer read the patched values. v[9] is now published
                // LIVE by the workload (onBandwidth above). The end-of-phase
                // aggregate is kept for the log only.
                val workloadDurationMs = SystemClock.elapsedRealtime() - workloadStartMs
                val avgMbps: Float = if (workloadDurationMs > 0L && verifMemoryTestedBytes > 0L) {
                    (verifMemoryTestedBytes.toFloat() / workloadDurationMs.toFloat() *
                        1000f) / (1024f * 1024f)
                } else {
                    0f
                }
                Log.i(TAG, "MEMORY_STRESS bandwidth aggregate: " +
                    "${"%.1f".format(avgMbps)} MB/s " +
                    "(${verifMemoryTestedBytes / (1024L * 1024L)} MiB " +
                    "in ${workloadDurationMs}ms) — v[9] published live during the phase")
                // The workload has stopped — clear v[9] BEFORE the wait-out
                // (same discipline as the storage phase) so idle samples
                // don't keep repeating the last live value.
                sampler?.setMemoryBandwidth(0f)
                // V2 fix #30: fixed duration. If the workload ended before the
                // nominal time, wait out the rest — fairness across devices and
                // equal thermal-capture opportunity.
                val phaseElapsed = SystemClock.elapsedRealtime() - phaseStartMs
                val phaseRemaining = V2Phase.MEMORY_STRESS.durationMs - phaseElapsed
                if (phaseRemaining > 0) {
                    Log.i(TAG, "MEMORY_STRESS workload completed in ${phaseElapsed}ms, " +
                        "waiting ${phaseRemaining}ms to fill nominal duration")
                    delay(phaseRemaining)
                }
            }

            // ─────── Phase 3: CPU_SUSTAINED ───────
            // Commit 3 (fieldsVersion 59): launch governor-capping watcher
            // BEFORE runCpuKernelsForDuration. Watcher polls big-cluster
            // freq + thermal headroom and flips [governorCappingDetected]
            // if the OS is policy-capping. Cancellation flows through
            // currentPhaseJob (already wired by runPhase). The
            // try/finally inside runCpuKernelsForDuration harvests
            // counters even on early-cancel.
            phaseStatus[V2Phase.CPU_SUSTAINED.phaseId] = runPhase(V2Phase.CPU_SUSTAINED, throttler) {
                val watcher = launchGovernorCappingWatcher()
                var knownAnswerJob: Job? = null
                try {
                    if (USE_AI_WORKLOAD_CPU && aiCpuWorkload != null) {
                        // Phase 1.6.B path: AI-camouflage workload.
                        // Phase 1.15 (Fix 1): if the AI workload can't build a
                        // real TFLite delegate it throws StubDelegateException
                        // (its early probe) — we then run the NEON kernels for
                        // the remainder so the CPU is actually stressed instead
                        // of idling in STUB sleep (the Honor X9d failure mode).
                        // CPU verification counters (FMA, SHA, prime) stay at
                        // their defaults on the AI path; they get populated only
                        // when the NEON fallback (or rollback else) runs.
                        val phaseStartMs = SystemClock.elapsedRealtime()
                        // fieldsVersion 68 (T-20): the known-answer checks run beside the AI
                        // workload (and beside the NEON fallback if it takes over).
                        knownAnswerJob = launchKnownAnswerChecks(knownAnswerScheduler, V2Phase.CPU_SUSTAINED, phaseStartMs)
                        try {
                            val result = aiCpuWorkload.run(
                                V2Phase.CPU_SUSTAINED.durationMs,
                                probeResult.tier.intensityFactor,
                                configForPhase(V2Phase.CPU_SUSTAINED, aiConfig)
                            )
                            Log.i(
                                TAG,
                                "phase 3 AI workload: iter=${result.iterations} " +
                                    "avgInfMs=${"%.1f".format(result.avgInferenceTimeMs)} " +
                                    "workers=${result.workerCount} " +
                                    "delegate=${result.activeDelegate}"
                            )
                            // Phase 1.7.C: surface AI iter / delegate / latency.
                            aiIterationsTotal += result.iterations.toLong()
                            aiWorkerCount = result.workerCount
                            if (result.iterations > 0) {
                                aiAvgMsSum += result.avgInferenceTimeMs
                                aiAvgMsCount++
                            }
                            aiActiveDelegate = result.activeDelegate
                            // Defensive: if run() ever RETURNS a STUB result
                            // (early guard bypassed), fall back to NEON too.
                            if (result.activeDelegate == "STUB") {
                                runNeonFallback(
                                    V2Phase.CPU_SUSTAINED.durationMs -
                                        (SystemClock.elapsedRealtime() - phaseStartMs)
                                )
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (sde: StubDelegateException) {
                            Log.w(TAG, "phase 3 AI CPU workload STUB (${sde.delegate}) — NEON fallback")
                            if (aiActiveDelegate !in setOf("NNAPI", "GPU")) aiActiveDelegate = "STUB"
                            if (aiDelegateInitError == null) aiDelegateInitError = sde.initError
                            runNeonFallback(
                                V2Phase.CPU_SUSTAINED.durationMs -
                                    (SystemClock.elapsedRealtime() - phaseStartMs)
                            )
                        } catch (t: Throwable) {
                            Log.w(TAG, "phase 3 AI CPU workload failed: ${t.javaClass.simpleName}: ${t.message} — NEON fallback")
                            if (aiActiveDelegate !in setOf("NNAPI", "GPU")) aiActiveDelegate = "STUB"
                            if (aiDelegateInitError == null) aiDelegateInitError = "${t.javaClass.simpleName}: ${t.message}"
                            runNeonFallback(
                                V2Phase.CPU_SUSTAINED.durationMs -
                                    (SystemClock.elapsedRealtime() - phaseStartMs)
                            )
                        }
                    } else {
                        // Legacy V5 synthetic kernel path. Preserved for
                        // rollback safety — flip USE_AI_WORKLOAD_CPU to
                        // false to land here.
                        runCpuKernelsForDuration(
                            V2Phase.CPU_SUSTAINED.durationMs,
                            probeResult.tier.intensityFactor
                        ).also(accumulateCpuOutcome)
                    }
                } finally {
                    watcher.cancel()
                    stopKnownAnswerChecks(knownAnswerJob)
                }
            }

            // Commit 3 (fieldsVersion 59): early-exit gate. When governor
            // capping is detected by the watcher in phase 3, override the
            // CPU_SUSTAINED phase status and skip phases 4/5/6 entirely.
            // Phase 7 COOLDOWN still runs (sees if/how the device cools
            // down with no load — even on a capped device this is useful
            // baseline data). The phaseStatus map carries the explicit
            // "skipped_governor_capped" tags so the AI knows the gap is
            // intentional, not a crash.
            if (governorCappingDetected) {
                Log.w(
                    TAG,
                    "governor capping detected — phases 4/5/6 will be skipped. " +
                        "Phase 7 COOLDOWN will still run."
                )
                phaseStatus[V2Phase.CPU_SUSTAINED.phaseId] = "early_terminated_governor_capped"
                phaseStatus[V2Phase.GPU_SUSTAINED.phaseId] = "skipped_governor_capped"
                phaseStatus[V2Phase.COMBINED_PEAK.phaseId] = "skipped_governor_capped"
                phaseStatus[V2Phase.BURST_PATTERN.phaseId] = "skipped_governor_capped"
            }

            // ─────── Phase 4: GPU_SUSTAINED ───────
            // Skipped when governor capping detected — see gate above.
            if (!governorCappingDetected) phaseStatus[V2Phase.GPU_SUSTAINED.phaseId] = runPhase(V2Phase.GPU_SUSTAINED, throttler) {
                net.neuralcheck.service.stress.GpuFpsTracker.gpuLoadGeneratorV2 = gpuActive
                net.neuralcheck.service.stress.GpuFpsTracker.reset()
                // Cold-state benchmark: one frame before stress starts.
                // Pipelining is naturally drained because gpu wasn't
                // running yet — this is a clean measurement of GPU
                // capability at session-start temperature.
                gpuRenderColdMs = withContext(Dispatchers.Default) {
                    gpuActive.benchmarkSingleFrameMs()
                }
                Log.i(TAG, "phase 4 cold benchmark: ${gpuRenderColdMs}ms")
                gpuActive.start()
                // Phase 1.6.C: launch the AI GPU workload in parallel
                // with the raymarcher / fragment shader. Both compete for
                // the same GPU — this is intentional, real apps emit
                // similar mixed loads (camera ML + UI render). Job is
                // cancelled in the finally block below.
                val aiGpuJob = if (USE_AI_WORKLOAD_GPU && aiGpuWorkload != null) {
                    scope.launch {
                        runCatching {
                            aiGpuWorkload.run(
                                V2Phase.GPU_SUSTAINED.durationMs,
                                probeResult.tier.intensityFactor,
                                configForPhase(V2Phase.GPU_SUSTAINED, aiConfig)
                            )
                        }.onSuccess { res ->
                            // Phase 1.7.C: capture GPU AI iter / delegate.
                            // Writes are visible to the outer scope once
                            // the launch body completes; the phase deadline
                            // matches the workload's own deadline so the
                            // launch normally finishes before the finally
                            // block's cancel() fires.
                            aiIterationsTotal += res.iterations.toLong()
                            if (res.iterations > 0) {
                                aiAvgMsSum += res.avgInferenceTimeMs
                                aiAvgMsCount++
                            }
                            // GPU delegate "wins" the active-delegate slot if
                            // it's a real backend (NNAPI / GPU); otherwise
                            // CPU phase already won it.
                            if (res.activeDelegate in setOf("NNAPI", "GPU")) {
                                aiActiveDelegate = res.activeDelegate
                            }
                        }.onFailure {
                            Log.w(TAG, "phase 4 AI GPU workload threw: ${it.message}")
                        }
                    }
                } else null
                try {
                    // Bug fix: run compute GEMM verification repeatedly
                    // throughout the phase (every ~10s) instead of once
                    // at the end. This populates gpuFragmentHashTotal
                    // and gpuFragmentHashMatches with meaningful counts
                    // (1 attempt per cycle, 1 match per pass) so the AI
                    // sees verification attempts spread across the phase
                    // rather than a single boolean. Each compute test
                    // takes ~3s on Snapdragon 8 Gen 3, ~6s on Mali-G76
                    // — leaves plenty of headroom in 75s.
                    val verifyIntervalMs = 10_000L
                    val phaseDeadline = SystemClock.elapsedRealtime() +
                        V2Phase.GPU_SUSTAINED.durationMs - 3000L
                    while (SystemClock.elapsedRealtime() < phaseDeadline) {
                        val gemmOk = withContext(Dispatchers.Default) { gpuActive.runComputeGemmTest() }
                        verifGpuFragTotal += 1
                        if (gemmOk) {
                            verifGpuFragMatches += 1
                        } else {
                            verifGpuComputeOk = false
                        }
                        // Sleep to next verification window. If the test
                        // itself took longer than verifyIntervalMs, this
                        // becomes a no-op and the next attempt fires
                        // immediately — acceptable because slow GPUs
                        // are exactly the ones we want to stress more.
                        val nextAttemptMs = SystemClock.elapsedRealtime() + verifyIntervalMs
                        while (SystemClock.elapsedRealtime() < nextAttemptMs &&
                            SystemClock.elapsedRealtime() < phaseDeadline) {
                            delay(500L)
                        }
                    }
                    Log.i(TAG, "phase 4 GPU verification: $verifGpuFragMatches / $verifGpuFragTotal passes")
                } finally {
                    aiGpuJob?.cancel()
                    gpuActive.stop()
                    // fieldsVersion 66: harvest fence-timeout telemetry for
                    // this phase (v[10] trust signal).
                    gpuFenceTimeouts += gpuActive.getAndResetFenceTimeoutCount()
                    // Warm-state benchmark: one frame right after stop.
                    // GPU has been under sustained load for ~75s, so
                    // any thermal throttling will show as a longer
                    // completion time vs cold. The benchmark itself
                    // doesn't add stress (one frame + glFinish).
                    gpuRenderWarmMs = withContext(Dispatchers.Default) {
                        gpuActive.benchmarkSingleFrameMs()
                    }
                    Log.i(TAG, "phase 4 warm benchmark: ${gpuRenderWarmMs}ms " +
                        "(cold was ${gpuRenderColdMs}ms, ratio=" +
                        (if (gpuRenderColdMs > 0)
                            "%.2f".format(gpuRenderWarmMs.toDouble() / gpuRenderColdMs)
                        else "n/a") + ")")
                }
            }

            // ─────── Phase 5: COMBINED_PEAK ───────
            // Skipped when governor capping detected — see gate after phase 3.
            if (!governorCappingDetected) phaseStatus[V2Phase.COMBINED_PEAK.phaseId] = runPhase(V2Phase.COMBINED_PEAK, throttler) {
                // Everything together: CPU kernels + GPU + memory pressure.
                net.neuralcheck.service.stress.GpuFpsTracker.gpuLoadGeneratorV2 = gpuActive
                net.neuralcheck.service.stress.GpuFpsTracker.reset()
                gpuActive.start()
                // Phase 1.6.C: AI GPU workload runs in parallel with the
                // raymarcher + GEMM verification. All three subsystems
                // (CPU AI workers, GPU raymarcher, GPU AI inference)
                // saturate the SoC.
                val aiGpuJob = if (USE_AI_WORKLOAD_GPU && aiGpuWorkload != null) {
                    scope.launch {
                        runCatching {
                            aiGpuWorkload.run(
                                V2Phase.COMBINED_PEAK.durationMs,
                                probeResult.tier.intensityFactor,
                                // GPU half of phase 5: configForPhase
                                // routes COMBINED_PEAK to the calibrated
                                // CPU rate. AiGpuWorkload uses
                                // targetIterPerSec the same way the CPU
                                // workload does; using the calibrated
                                // value is closer-to-correct than the
                                // tier value when calibration is active.
                                configForPhase(V2Phase.COMBINED_PEAK, aiConfig)
                            )
                        }.onSuccess { res ->
                            // Phase 1.7.C: same accumulator update as phase 4.
                            aiIterationsTotal += res.iterations.toLong()
                            if (res.iterations > 0) {
                                aiAvgMsSum += res.avgInferenceTimeMs
                                aiAvgMsCount++
                            }
                            if (res.activeDelegate in setOf("NNAPI", "GPU")) {
                                aiActiveDelegate = res.activeDelegate
                            }
                        }.onFailure {
                            Log.w(TAG, "phase 5 AI GPU workload threw: ${it.message}")
                        }
                    }
                } else null
                // Launch a parallel coroutine that runs GPU compute
                // verifications periodically while CPU kernels stress
                // the SoC. Bug fix: previously COMBINED_PEAK ran zero
                // GPU verifications — now it runs ~6 (60s / 10s).
                val gpuVerifyJob = scope.launch {
                    val phaseDeadline = SystemClock.elapsedRealtime() +
                        V2Phase.COMBINED_PEAK.durationMs - 3000L
                    while (SystemClock.elapsedRealtime() < phaseDeadline && isActive) {
                        val gemmOk = withContext(Dispatchers.Default) { gpuActive.runComputeGemmTest() }
                        verifGpuFragTotal += 1
                        if (gemmOk) {
                            verifGpuFragMatches += 1
                        } else {
                            verifGpuComputeOk = false
                        }
                        val nextAttemptMs = SystemClock.elapsedRealtime() + 10_000L
                        while (SystemClock.elapsedRealtime() < nextAttemptMs &&
                            SystemClock.elapsedRealtime() < phaseDeadline && isActive) {
                            delay(500L)
                        }
                    }
                }
                var knownAnswerJob: Job? = null
                try {
                    if (USE_AI_WORKLOAD_CPU && aiCpuWorkload != null) {
                        // Phase 1.6.B: AI-camouflage CPU half. The GPU half
                        // (gpu.start + gpuVerifyJob above) runs in parallel.
                        // Phase 1.15 (Fix 1): same STUB→NEON fallback as phase
                        // 3 — if the AI delegate can't load, run the NEON
                        // kernels for the remainder so COMBINED_PEAK still
                        // loads the CPU (alongside the GPU already running).
                        val phaseStartMs = SystemClock.elapsedRealtime()
                        // fieldsVersion 68 (T-20): the known-answer checks, as in phase 3.
                        knownAnswerJob = launchKnownAnswerChecks(knownAnswerScheduler, V2Phase.COMBINED_PEAK, phaseStartMs)
                        try {
                            val result = aiCpuWorkload.run(
                                V2Phase.COMBINED_PEAK.durationMs,
                                probeResult.tier.intensityFactor,
                                configForPhase(V2Phase.COMBINED_PEAK, aiConfig)
                            )
                            Log.i(
                                TAG,
                                "phase 5 AI workload (CPU half): iter=${result.iterations} " +
                                    "avgInfMs=${"%.1f".format(result.avgInferenceTimeMs)} " +
                                    "workers=${result.workerCount} " +
                                    "delegate=${result.activeDelegate}"
                            )
                            // Phase 1.7.C
                            aiIterationsTotal += result.iterations.toLong()
                            aiWorkerCount = result.workerCount
                            if (result.iterations > 0) {
                                aiAvgMsSum += result.avgInferenceTimeMs
                                aiAvgMsCount++
                            }
                            // CPU phase only overrides the delegate slot if
                            // GPU phase didn't already win it (we prefer
                            // accelerator delegates over CPU when both ran).
                            if (aiActiveDelegate !in setOf("NNAPI", "GPU")) {
                                aiActiveDelegate = result.activeDelegate
                            }
                            if (result.activeDelegate == "STUB") {
                                runNeonFallback(
                                    V2Phase.COMBINED_PEAK.durationMs -
                                        (SystemClock.elapsedRealtime() - phaseStartMs)
                                )
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (sde: StubDelegateException) {
                            Log.w(TAG, "phase 5 AI CPU workload STUB (${sde.delegate}) — NEON fallback")
                            if (aiActiveDelegate !in setOf("NNAPI", "GPU")) aiActiveDelegate = "STUB"
                            if (aiDelegateInitError == null) aiDelegateInitError = sde.initError
                            runNeonFallback(
                                V2Phase.COMBINED_PEAK.durationMs -
                                    (SystemClock.elapsedRealtime() - phaseStartMs)
                            )
                        } catch (t: Throwable) {
                            Log.w(TAG, "phase 5 AI CPU workload failed: ${t.javaClass.simpleName}: ${t.message} — NEON fallback")
                            if (aiActiveDelegate !in setOf("NNAPI", "GPU")) aiActiveDelegate = "STUB"
                            if (aiDelegateInitError == null) aiDelegateInitError = "${t.javaClass.simpleName}: ${t.message}"
                            runNeonFallback(
                                V2Phase.COMBINED_PEAK.durationMs -
                                    (SystemClock.elapsedRealtime() - phaseStartMs)
                            )
                        }
                    } else {
                        runCpuKernelsForDuration(
                            V2Phase.COMBINED_PEAK.durationMs,
                            probeResult.tier.intensityFactor
                        ).also(accumulateCpuOutcome)
                    }
                } finally {
                    stopKnownAnswerChecks(knownAnswerJob)
                    aiGpuJob?.cancel()
                    gpuVerifyJob.cancel()
                    gpuActive.stop()
                    // fieldsVersion 68 (T-12, S-21): the load has stopped — clear the fps
                    // window NOW. Without this the window (the last 4 sampler queries)
                    // kept non-zero slices after the generator stopped, and v[10] showed
                    // a tail of non-zero values in the phases that follow (the BURST
                    // phase, which loads the CPU only). reset() was only called at the
                    // START of phases 4 and 5.
                    net.neuralcheck.service.stress.GpuFpsTracker.reset()
                    // fieldsVersion 66: fence-timeout telemetry, phase 5 share.
                    gpuFenceTimeouts += gpuActive.getAndResetFenceTimeoutCount()
                }
            }

            // ─────── Phase 6: BURST_PATTERN ───────
            // Skipped when governor capping detected — see gate after phase 3.
            if (!governorCappingDetected) phaseStatus[V2Phase.BURST_PATTERN.phaseId] = runPhase(V2Phase.BURST_PATTERN, throttler) {
                executeBurstPattern()
            }

            // ─────── Phase 7: COOLDOWN ───────
            phaseStatus[V2Phase.COOLDOWN.phaseId] = runPhase(V2Phase.COOLDOWN, throttler) {
                // Idle — no kernels. Duration drives the thermal time-
                // constant measurement on the AI side.
                delay(V2Phase.COOLDOWN.durationMs)
            }
        } finally {
            throttler.stop()
        }

        // Bug-fix: actually populate cpu* counters from the production_stress
        // workers' aggregate counters, instead of writing hardcoded zeros.
        // Both CPU_SUSTAINED and COMBINED_PEAK contribute (counters reset between
        // start_cpu_stress calls in C++, so we sum across both phases here).
        // Note on totals: the C++ production worker increments total and verified
        // FMA iterations together, and the SHA counter increments only on hash match.
        // We therefore have no separate "SHA attempts total" — using the verified
        // count for both Total and Verified for SHA is the closest available proxy
        // until production_stress.cpp is extended with attempt counters.
        // Phase 1.7.B: legacy NDK kernel verification fields removed.
        // The local accumulator vars (verifPrimeOk, verifFmaOk, verifShaOk,
        // verifCpuFmaIterations*, verifCpuShaHashes*) only get populated
        // on the legacy synthetic-kernel rollback path. They no longer
        // surface in VerificationSummary; the AI-path "scan ran" signal
        // is aiInferenceIterationsTotal (Phase 1.7.C, in TimeSeriesFooter).
        Log.i(TAG, "verification aggregate (Phase 1.7.B): " +
            "gpuGemmOk=$verifGpuComputeOk " +
            "gpuFragMatches=$verifGpuFragMatches/$verifGpuFragTotal " +
            "memoryTestedBytes=$verifMemoryTestedBytes " +
            "storageSegments=${storageSegments.size}")
        // fieldsVersion 68 (ت-18 rows 1-2): a verification that never ran is
        // not a pass. The GEMM ran iff at least one attempt COMPLETED (phases
        // 4/5 can be skipped by governor capping, cancelled, or end before the
        // first test); criticalDefectDetected reads only that result (S-31).
        val memoryOutcome = memoryVerifyRunner.lastOutcome
            ?: net.neuralcheck.data.local.files.MemoryVerifyOutcome.notRun(
                VerificationStates.REASON_PHASE_NOT_REACHED
            )
        val memoryWindows = synchronized(phaseBoundaries) {
            phaseBoundaries.firstOrNull { it.phaseId == V2Phase.MEMORY_STRESS.phaseId }?.windows
        }
        val gemmOutcome = net.neuralcheck.data.local.files.GpuGemmOutcome.derive(
            attempts = verifGpuFragTotal,
            anyFailed = !verifGpuComputeOk,
            governorCapped = governorCappingDetected
        )
        val criticalDefect = net.neuralcheck.data.local.files.CriticalDefectOutcome
            .derive(gemmOutcome.correct)
        val verification = VerificationSummary(
            gpuComputeGemmCorrect = gemmOutcome.correct,
            gpuComputeGemmState = gemmOutcome.state,
            gpuFragmentHashMatches = verifGpuFragMatches,
            gpuFragmentHashTotal = verifGpuFragTotal,
            gpuRenderCompletionMsCold = gpuRenderColdMs,
            gpuRenderCompletionMsWarm = gpuRenderWarmMs,
            memoryTestedBytes = verifMemoryTestedBytes,
            // fieldsVersion 68 (T-17, N-10): what the memory verification kernel
            // produced — raw counts, or the state saying why there are none. The
            // runner holds the outcome of the kernel call even when the phase was
            // cancelled after the call had started.
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
            // fieldsVersion 68 (T-16, ت-18 row 3): the storage producer's RAW output.
            // storageReadVerifyErrors exists only when a comparison executed (it was the
            // literal 0 and read as "verified, no errors"); the state says ran / partial /
            // why not. storageWriteVerifyErrors (failed fsyncs + a write that threw) ships
            // WHENEVER THE WRITE RAN, whether the read-back ran or not (part 2-c); when no
            // write ran it is absent and storageVerifyState says why.
            // storageSlcCacheSizeMb (the third literal 0) is gone: the server
            // computes it from storageThroughputSegments (T-08).
            storageWriteVerifyErrors = net.neuralcheck.data.local.files.StorageWriteErrors.count(
                writeAttempted = storageWriteAttempted,
                failedSyncs = storageWriteSyncFailures,
                writeExceptions = storageWriteExceptions
            ),
            storageReadVerifyErrors = storageOutcome.readErrors,
            storageVerifyState = storageOutcome.state,
            storageVerifyBytesCompared = storageOutcome.bytesCompared,
            storageVerifyBytesPlanned = storageOutcome.bytesPlanned,
            storageVerifyDurationMs = storageOutcome.durationMs,
            storageVerifyMethod = storageOutcome.method,
            storageThroughputSegments = storageSegments.toList(),
            // fieldsVersion 66: extent of the write loop (byte cap unchanged).
            storageBytesWritten = storageBytesWrittenTotal,
            storageWriteDurationMs = storageWriteDurationMs,
            // Phase 1.7.B: criticalDefectDetected derives only from signals
            // the AI workload actually populates. fieldsVersion 68 (S-31): it
            // is a declared transitional aggregate of the GPU GEMM result ALONE
            // (the old comment named the storage errors, but the condition
            // never read them and S-31 does not widen it), `null` when that
            // input did not run. Memory and CPU soft failures are judged on
            // the server from the raw.
            criticalDefectDetected = criticalDefect.detected,
            criticalDefectInputsNotRun = criticalDefect.inputsNotRun,
            // V2 fix #56-3 (fieldsVersion 59): per-cluster pin telemetry
            // surfaced from the LAST runCpuKernelsForDuration call
            // (CPU_SUSTAINED or COMBINED_PEAK; both call into the same
            // NDK code so values are consistent). When governor capping
            // skipped phases 4/5/6, only CPU_SUSTAINED contributed —
            // still the right values to report.
            workersPinnedBig = lastWorkersPinnedBig.takeIf { workersPinAttempted },
            workersPinnedMid = lastWorkersPinnedMid.takeIf { workersPinAttempted },
            workersPinnedLittle = lastWorkersPinnedLittle.takeIf { workersPinAttempted },
            workersPinFailedBig = lastWorkersPinFailedBig.takeIf { workersPinAttempted },
            workersPinFailedMid = lastWorkersPinFailedMid.takeIf { workersPinAttempted },
            workersPinFailedLittle = lastWorkersPinFailedLittle.takeIf { workersPinAttempted },
            // fieldsVersion 68 (decision D, T-20): the CPU known-answer checks — raw
            // prime counts and per-check statuses, no aggregate verdict.
            cpuKnownAnswerState = knownAnswerScheduler.state(USE_AI_WORKLOAD_CPU && aiCpuWorkload != null),
            cpuKnownAnswerRuns = knownAnswerScheduler.snapshot().takeIf { it.isNotEmpty() },
            cpuKnownAnswerTotalMsByPhase = knownAnswerScheduler.totalMsByPhase(),
            workersPinState = net.neuralcheck.data.local.files.WorkersPinOutcome.stateOf(
                attempted = workersPinAttempted,
                aiWorkloadPathSelected = USE_AI_WORKLOAD_CPU && aiCpuWorkload != null,
                unreadableResultSize = workersPinUnreadableSize
            ),
            // Phase 1.7.C: canonical AI workload signals.
            aiInferenceIterationsTotal = aiIterationsTotal,
            aiInferenceWorkers = aiWorkerCount,
            aiInferenceAvgMs = if (aiAvgMsCount > 0) aiAvgMsSum / aiAvgMsCount else 0f,
            aiActiveDelegate = aiActiveDelegate,
            // Phase 1.15 (Fix 1 + Fix 4): STUB→NEON fallback telemetry.
            aiDelegateInitError = aiDelegateInitError,
            cpuStressFallbackUsed = cpuStressFallbackUsed,
            // fieldsVersion 66: v[10] trust — timed-out 30-frame fences.
            gpuFenceTimeoutCount = gpuFenceTimeouts
        )

        Log.i(
            TAG,
            "runV2Scan: complete tier=${probeResult.tier.tag} " +
                "intensity=${probeResult.tier.intensityFactor} " +
                "criticalDefect=${verification.criticalDefectDetected} " +
                "throttlingEvents=${throttler.events.size}"
        )

        // Clear the exposed throttler reference now that the scan
        // is finishing. phaseDriver in StressTestService treats
        // a null activeThrottler as "no active scan" and stops.
        activeThrottler = null

        return V2ScanResult(
            verification = verification,
            deviceTier = probeResult.tier,
            intensityFactor = probeResult.tier.intensityFactor,
            tempRiseRateInProbe = probeResult.tempRiseRatePerSec,
            phaseCompletionStatus = phaseStatus,
            throttlingEventCount = throttler.events.size,
            throttlingEvents = throttler.events,
            // V2 fix #56-3 / Commit 3: forward to caller so footer can carry it.
            governorCappingDetected = governorCappingDetected,
            // G2/P2.3: defensive copy — phaseBoundaries is a synchronizedList.
            phaseBoundaries = synchronized(phaseBoundaries) { phaseBoundaries.toList() }
        )
    }

    // ─────── Phase 0 (probe) ───────

    private suspend fun executePhase0Init(): Pair<AdaptiveIntensityController.ProbeResult, String> {
        Log.i(TAG, "phase 0 INIT_PROFILING: starting 30 s baseline + 8 s probe")

        // First 22 s: baseline / static info collection. We just delay
        // here for the V2 skeleton; production wires functional static
        // tests in (cpu_static, gpu_static, memory, network, etc.).
        val baselineMs = V2Phase.INIT_PROFILING.durationMs - AdaptiveIntensityController.PROBE_DURATION_MS
        delay(baselineMs)

        // Final 8 s: NEON FMA mini-burst + temp delta classification.
        val probe = AdaptiveIntensityController(context).probe()
        return probe to "completed"
    }

    // ─────── Phase runner with cancellation support ───────

    private suspend fun runPhase(
        phase: V2Phase,
        throttler: DynamicThermalThrottler,
        body: suspend () -> Unit
    ): String {
        Log.i(TAG, "phase ${phase.phaseId} ${phase.phaseName}: starting (${phase.durationMs} ms nominal)")
        throttler.setCurrentPhase(phase.phaseId)
        val started = SystemClock.elapsedRealtime()
        // Bug fix: use AtomicReference for thread-safe status visibility
        // between the launch coroutine and the outer runPhase function.
        // Previously a plain var written from inside the launch wasn't
        // guaranteed visible to the read after join() under all JVM
        // memory models, so cancelled phases sometimes reported "completed".
        val statusRef = java.util.concurrent.atomic.AtomicReference("completed")
        currentPhaseJob = scope.launch {
            try {
                body()
            } catch (_: kotlinx.coroutines.CancellationException) {
                statusRef.set("early_terminated")
            } catch (t: Throwable) {
                Log.w(TAG, "phase ${phase.phaseId} threw: ${t.message}")
                statusRef.set("early_terminated")
            }
        }
        currentPhaseJob?.join()
        val finishedAt = SystemClock.elapsedRealtime()
        val elapsed = finishedAt - started
        val status = statusRef.get()
        Log.i(TAG, "phase ${phase.phaseId} done in $elapsed ms ($status)")
        // G2/P2.3: record the observed window in the same reference frame
        // as v[0] (elapsed ms since scanStartTime), regardless of outcome
        // — a cancelled/early-terminated phase still occupied real wall
        // time and its samples still need a window to be sliced by.
        phaseBoundaries.add(
            net.neuralcheck.data.local.files.PhaseBoundary(
                phaseId = phase.phaseId,
                phaseName = phase.phaseName,
                startMs = started - scanStartTime,
                endMs = finishedAt - scanStartTime,
                windows = phaseSubWindows.remove(phase.phaseId)
            )
        )
        return status
    }

    // ─────── CPU kernel runner ───────

    private data class CpuKernelOutcome(
        val primeOk: Boolean,
        val shaOk: Boolean,
        val fmaOk: Boolean,
        val iterations: Long,
        val verifiedIterations: Long,
        val shaHashesVerified: Int,
        val shaHashesAttempted: Int,
        val fmaErrors: Int,
        // V2 fix #56-3 (fieldsVersion 59): per-cluster pin telemetry
        // surfaced from the NDK stop_cpu_stress IntArray tail.
        val workersPinnedBig: Int = 0,
        val workersPinnedMid: Int = 0,
        val workersPinnedLittle: Int = 0,
        val workersPinFailedBig: Int = 0,
        val workersPinFailedMid: Int = 0,
        val workersPinFailedLittle: Int = 0,
        // fieldsVersion 68 (ت-13): true only when the native array carried the
        // whole pin tail (13 ints, what libstress returns). A shorter array —
        // malformed, or "empty if no kernel was running" — leaves the six
        // counters above at their 0 defaults, and those zeros are NOT a
        // pinning outcome.
        val pinTelemetryComplete: Boolean = false,
        /** Length of the native array this outcome was built from; -1 = not from one. */
        val resultSize: Int = -1
    )

    /**
     * Runs the three CPU NDK kernels (prime sieve, SHA-256, NEON FMA)
     * in a tight loop for `durationMs`, accumulating verification
     * outcomes. Each kernel iteration is fully verified — a defect is
     * detected the first time it manifests rather than at end-of-phase.
     *
     * Future improvement (post-1h): pin kernel calls to specific
     * clusters and parallelise across cores via
     * [StressNative.pinThreadToBigCluster] et al. For now this is
     * single-threaded — adequate for proving the kernels chain
     * correctly but not the full flat-out CPU stress per master plan §3.3.
     */
    /**
     * Bug 1 fix: invoke the production multi-threaded
     * [StressNative.startCpuStress] / [StressNative.stopCpuStress] pair
     * instead of a single-threaded JNI loop over the smoke-test entries.
     *
     * Production path spawns one worker per big+mid cluster core (4–8
     * threads on Snapdragon 855), each running NEON FMA + SHA-256 in a
     * tight C++ loop pinned to high-perf cores via [sched_setaffinity].
     * Compared to the previous Kotlin loop:
     *   - 4-8× higher CPU utilisation (matches master plan §3.3)
     *   - sustained heat generation (probe→tier classification works)
     *   - aggregate verification counters returned in stopCpuStress()'s
     *     5-int array
     */
    /**
     * Bug fix #26: stopCpuStress was leaking when the phase job was
     * cancelled by DynamicThermalThrottler. delay(durationMs) would
     * throw CancellationException and the stop call never ran,
     * leaving C++ workers alive and atomic counters un-collected.
     * The next phase's startCpuStress would return -1 ("already
     * running") and verification fields stayed at 0.
     *
     * try/finally with NonCancellable ensures:
     *   1. stopCpuStress() always runs, harvesting counters
     *   2. workers terminate cleanly before next phase begins
     *   3. CancellationException re-propagates after cleanup
     *      (so the phase job still gets cancelled, just gracefully)
     *
     * On a previous-leak detection (startCode != 0L), we skip this
     * phase's CPU work and return an empty outcome — the previously
     * leaked workers will be reaped on the NEXT cancel-safe stop call,
     * which the finally block of THIS invocation also guarantees.
     */
    private suspend fun runCpuKernelsForDuration(durationMs: Long, intensityFactor: Float): CpuKernelOutcome =
        withContext(Dispatchers.Default) {
            val startCode = StressNative.startCpuStress(intensityFactor, durationMs)
            if (startCode != 0L) {
                Log.w(
                    "NeuralCheck.V2.Service",
                    "startCpuStress returned $startCode — kernel may already be running. " +
                        "Skipping this phase's CPU work; finally block will reap leaked workers."
                )
                // Cleanup the leaked workers from a prior phase before
                // returning. Wrapped NonCancellable so the cleanup itself
                // can't be cancelled. Counters are discarded — they belong
                // to the previous phase, not this one.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching { StressNative.stopCpuStress() }
                        .onFailure { Log.w("NeuralCheck.V2.Service", "leak-reap stopCpuStress failed: ${it.message}") }
                }
                return@withContext CpuKernelOutcome(
                    primeOk = true,
                    shaOk = true,
                    fmaOk = true,
                    iterations = 0L,
                    verifiedIterations = 0L,
                    shaHashesVerified = 0,
                    shaHashesAttempted = 0,
                    fmaErrors = 0
                )
            }
            try {
                // Normal completion path: wait the phase duration, then
                // stop and collect counters. The C++ workers stop
                // themselves when stopCpuStress flips the atomic flag.
                delay(durationMs)
                val results = StressNative.stopCpuStress()
                logStopResults(results)
                buildCpuKernelOutcome(results)
            } finally {
                // Guaranteed cleanup path: runs even on
                // CancellationException from delay() above. Wrapped in
                // NonCancellable so the stop call itself doesn't get
                // cancelled half-way through (would re-orphan workers).
                // If we reached here via normal completion, the C++
                // side already returned; this second call is idempotent
                // (g_running already false, g_workers empty, atomic
                // counters not reset by stop — re-runs the prime-sieve
                // probe which is the only real cost, ~90ms).
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching {
                        val arr = StressNative.stopCpuStress()
                        Log.i(
                            "NeuralCheck.V2.Service",
                            "runCpuKernelsForDuration finally: counters " +
                                "harvested (total=${arr.getOrNull(0) ?: -1})"
                        )
                    }.onFailure {
                        Log.w("NeuralCheck.V2.Service", "stopCpuStress in finally failed: ${it.message}")
                    }
                }
            }
        }

    /**
     * Translates the raw IntArray returned by StressNative.stopCpuStress
     * into a CpuKernelOutcome. Pure function (no side effects), shared
     * by the normal completion path and any future stop-then-build flow.
     *
     * Array layout (V2 fix #56-3, fieldsVersion 59 — 13 ints; old size 9
     * still parses correctly thanks to size-gated reads):
     *   [0]  total iterations
     *   [1]  verified iterations
     *   [2]  fma errors
     *   [3]  prime sieve correct (0/1)
     *   [4]  sha hashes verified
     *   [5]  sha hashes attempted (size>=6; else falls back to verified)
     *   [6]  workers_pinned_big              (size>=9)
     *   [7]  workers_pinned_mid              (size>=9)
     *   [8]  workers_pin_failed (aggregate)  (size>=9)
     *   [9]  workers_pinned_little           (size>=13; new)
     *   [10] workers_pin_failed_big          (size>=13; new)
     *   [11] workers_pin_failed_mid          (size>=13; new)
     *   [12] workers_pin_failed_little       (size>=13; new)
     */
    private fun buildCpuKernelOutcome(results: IntArray): CpuKernelOutcome {
        if (results.size < 5) {
            Log.w("NeuralCheck.V2.Service", "stopCpuStress returned malformed array (size=${results.size})")
            return CpuKernelOutcome(
                primeOk = true,
                shaOk = true,
                fmaOk = true,
                iterations = 0L,
                verifiedIterations = 0L,
                shaHashesVerified = 0,
                shaHashesAttempted = 0,
                fmaErrors = 0,
                resultSize = results.size
            )
        }
        val totalIters = results[0].toLong()
        val verifiedIters = results[1].toLong()
        val fmaErr = results[2]
        val shaVerified = results[4]
        val shaAttempted = if (results.size >= 6) results[5] else shaVerified
        // Pinning telemetry (V2 fix #22): size-gated parsing keeps backward
        // compat with the old 9-int / 5-int layouts. Per-cluster failure
        // split (size>=13) is V2 fix #56-3 — when absent, we fall back to
        // 0 for each per-cluster failure (degrades to "unknown but >=0").
        val pinnedBig = if (results.size >= 9) results[6] else 0
        val pinnedMid = if (results.size >= 9) results[7] else 0
        val pinnedLittle = if (results.size >= 13) results[9] else 0
        val pinFailedBig = if (results.size >= 13) results[10] else 0
        val pinFailedMid = if (results.size >= 13) results[11] else 0
        val pinFailedLittle = if (results.size >= 13) results[12] else 0
        return CpuKernelOutcome(
            primeOk = results[3] == 1,
            shaOk = shaVerified > 0,
            fmaOk = fmaErr == 0,
            iterations = totalIters,
            verifiedIterations = verifiedIters,
            shaHashesVerified = shaVerified,
            shaHashesAttempted = shaAttempted,
            fmaErrors = fmaErr,
            workersPinnedBig = pinnedBig,
            workersPinnedMid = pinnedMid,
            workersPinnedLittle = pinnedLittle,
            workersPinFailedBig = pinFailedBig,
            workersPinFailedMid = pinFailedMid,
            workersPinFailedLittle = pinFailedLittle,
            pinTelemetryComplete = results.size >= 13,
            resultSize = results.size
        )
    }

    private fun logStopResults(results: IntArray) {
        if (results.size < 5) return
        val totalIters = results[0].toLong()
        val verifiedIters = results[1].toLong()
        val fmaErr = results[2]
        val shaVerified = results[4]
        val shaAttempted = if (results.size >= 6) results[5] else shaVerified
        Log.i(
            "NeuralCheck.V2.Service",
            "stopCpuStress: total=$totalIters verified=$verifiedIters " +
                "fmaErrors=$fmaErr primeOk=${results[3]} " +
                "shaVerified=$shaVerified shaAttempted=$shaAttempted"
        )
    }

    // ─────── Commit 3 (fieldsVersion 59): governor capping watcher ───────

    /**
     * Launches a watcher coroutine inside the active phase 3 (CPU_SUSTAINED)
     * that flips [governorCappingDetected] and cancels the current phase
     * when the OS is policy-capping CPU frequency below threshold.
     *
     * Detection criteria (must hold for ≥ 5 consecutive 1-second polls
     * starting at t=10s into phase 3):
     *   1. Big-cluster current freq < 70% of nominal max
     *   2. PowerManager.getThermalHeadroom returns NaN (sentinel) OR ≤ 0.1f
     *      (device reports cool — not thermally throttling)
     *
     * Both conditions together indicate the OS is capping by policy, not
     * thermal pressure. We skip phases 4/5/6 since the workload cannot
     * exercise the silicon under those conditions; running them would
     * waste 180+ seconds and battery. Phase 7 COOLDOWN still runs.
     *
     * Returns the launched Job so the caller can cancel it from the
     * runPhase finally block (cleanup if phase 3 ends without a cap).
     */
    private fun launchGovernorCappingWatcher(): Job {
        return scope.launch {
            // Wait 10 s into phase 3 before sampling. The first ~5-8 s of
            // CPU_SUSTAINED is workload spin-up — even a non-capping OS
            // hasn't ramped freq yet during that window. Sampling earlier
            // would produce false positives.
            kotlinx.coroutines.delay(10_000L)
            if (!isActive) return@launch

            // Build a SystemFileReader once; the watcher uses its
            // readCpuFrequency + readThermalHeadroom methods.
            val sysReader = net.neuralcheck.service.collector.SystemFileReader(context)

            // Determine big-cluster CPU index + nominal freq from sysfs.
            val perCpuMaxFreqs = net.neuralcheck.util.SocNominalFrequencies
                .readPerCpuMaxFreqKhz()
            if (perCpuMaxFreqs == null || perCpuMaxFreqs.isEmpty()) {
                Log.i(TAG, "watcher: per-CPU sysfs unavailable, cannot detect governor cap")
                return@launch
            }
            val (bigNominalKhz, _, _) = net.neuralcheck.util.SocNominalFrequencies
                .getPerClusterMaxKhz(perCpuMaxFreqs)
            if (bigNominalKhz == null || bigNominalKhz <= 0) {
                Log.i(TAG, "watcher: big-cluster nominal unavailable, cannot detect governor cap")
                return@launch
            }
            // Find first CPU index whose max_freq matches the big nominal.
            val bigCpuIdx = perCpuMaxFreqs.indexOfFirst { it.toInt() == bigNominalKhz }
            if (bigCpuIdx < 0) {
                Log.i(TAG, "watcher: big-cluster CPU index unresolvable, cannot detect governor cap")
                return@launch
            }

            // Threshold: 70% of nominal. Below = "OS is keeping us capped".
            val cappedFreqThresholdKhz = (bigNominalKhz.toLong() * 70L) / 100L

            // Poll up to 5 times at 1 Hz; trip the flag only when ALL 5
            // consecutive polls show the cap pattern. Earlier exit if the
            // pattern breaks (freq rises or headroom registers a real value).
            var consecutiveCappedSamples = 0
            val requiredSamples = 5
            repeat(requiredSamples) { sample ->
                if (!isActive) return@launch
                val freqKhz = sysReader.readCpuFrequency(bigCpuIdx)
                val headroom = sysReader.readThermalHeadroom()
                val freqLow = freqKhz in 1..cappedFreqThresholdKhz
                // headroom semantics: NaN = unsupported/blocked, <= 0.1 = "device cool".
                // Both indicate "no thermal pressure to explain a freq drop".
                val headroomCool = headroom.isNaN() || headroom <= 0.1f
                if (freqLow && headroomCool) {
                    consecutiveCappedSamples++
                    Log.i(
                        TAG,
                        "watcher: cap pattern sample ${sample + 1}/$requiredSamples — " +
                            "bigFreq=${freqKhz}kHz (threshold=${cappedFreqThresholdKhz}kHz) " +
                            "headroom=$headroom"
                    )
                } else {
                    // Pattern broken — reset and abort. Healthy device.
                    Log.i(
                        TAG,
                        "watcher: cap pattern broken at sample ${sample + 1} — " +
                            "bigFreq=${freqKhz}kHz headroom=$headroom (no cap detected)"
                    )
                    return@launch
                }
                kotlinx.coroutines.delay(1_000L)
            }

            if (consecutiveCappedSamples >= requiredSamples) {
                Log.w(
                    TAG,
                    "watcher: GOVERNOR CAPPING CONFIRMED — flagging and cancelling phase 3. " +
                        "Phases 4/5/6 will be skipped."
                )
                governorCappingDetected = true
                // Cancel the active phase job so phase 3's runCpuKernels-
                // ForDuration unwinds via its finally block (harvests
                // counters, joins workers). The runPhase wrapper will
                // detect cancellation and set its statusRef to
                // "early_terminated"; the outer governor-cap branch in
                // runV2Scan overrides it to "early_terminated_governor_capped".
                currentPhaseJob?.cancel()
            }
        }
    }

    // ─────── Burst pattern (5 cycles × 6s peak + 3s idle) ───────

    private suspend fun executeBurstPattern() {
        // Master plan §2.2 row 6: 5 cycles, each 6 s peak + 3 s idle.
        // Designed to expose VRM aging — voltage rails that recover
        // poorly between pulses produce wide voltage dips.
        repeat(5) { cycle ->
            Log.i(TAG, "burst cycle ${cycle + 1}/5: peak phase")
            val peakDeadline = SystemClock.elapsedRealtime() + 6_000L
            withContext(Dispatchers.Default) {
                while (SystemClock.elapsedRealtime() < peakDeadline && isActive) {
                    StressNative.runNeonFmaTest()
                }
            }
            Log.i(TAG, "burst cycle ${cycle + 1}/5: idle phase")
            delay(3_000L)
        }
    }

    /** Stops any in-flight scan and releases all V2 resources. Safe to
     *  call multiple times; safe to call when no scan is active. */
    fun shutdown() {
        currentPhaseJob?.cancel()
        runCatching { gpuActive.stop() }
        // Drop the V2 GPU reference from the FPS tracker so a
        // subsequent V1 scan reads from V1's generator cleanly.
        runCatching {
            net.neuralcheck.service.stress.GpuFpsTracker.gpuLoadGeneratorV2 = null
        }
        runCatching { scope.cancel() }
    }

    /** Width of the V2 BASE block `v[0..10]` only. NOT the number of floats
     *  a sample has: from fieldsVersion 68 a sample also carries the
     *  per-device channels its scan admitted, so the real width is a per-scan
     *  fact — [net.neuralcheck.data.local.files.ScanChannelLayout.sampleWidth].
     *  (No caller reads this property; kept to avoid changing the class's
     *  surface in a comment-only fix.) */
    val sampleVariableCount: Int = V2_SAMPLE_VAR_COUNT
}
