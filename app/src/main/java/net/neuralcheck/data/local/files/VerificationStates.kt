package net.neuralcheck.data.local.files

/**
 * fieldsVersion 68 (ت-18, S-23, N-11, S-31; order of batch 3 part 2-a):
 * `verificationResults` stops reading ABSENCE as success (rule 22). A check
 * that did not run has no value — `null` — and a state that says so; a default
 * `true` / `0` / `false` in its place was indistinguishable from a real pass.
 *
 * The state literals live here once (rule 24): the producer writes them, the
 * payload forwards them, the tests assert against them.
 */
object VerificationStates {

    /** Prefix of every "this did not run" state: `not_run:<reason>`. */
    const val NOT_RUN_PREFIX = "not_run:"

    // ── gpuComputeGemmState (N-11) ──
    const val GPU_GEMM_PASSED = "passed"
    const val GPU_GEMM_FAILED = "failed"

    // ── reasons behind `not_run:<reason>` ── (the `not_run` names themselves are
    // approved; the reason texts are the agent's, listed in the commit message)

    /** No V2 scan result exists at all (V1 flavor, or the V2 service failed before returning one). */
    const val REASON_NO_V2_RESULT = "no_v2_result"

    /** Phases 4/5 were skipped because governor capping was detected in phase 3. */
    const val REASON_GOVERNOR_CAPPED = "governor_capped"

    /** Phases 4/5 were not skipped but no GEMM verification attempt completed. */
    const val REASON_NO_ATTEMPT_COMPLETED = "no_attempt_completed"

    // ── storageVerifyState (N-09) ──
    /** The storage read-back verification ran: the two error counters are real (a 0 is then a measured 0). */
    const val STORAGE_VERIFY_RAN = "ran"

    // The storage producer's `not_run:<reason>` / `partial:<reason>` texts. [م]:
    // agent's. A reason that carries text after a second colon carries an
    // error's TEXT (rule 21), capped by ReasonText. (The placeholder
    // `producer_not_implemented` of part 2-a went when the producer landed.)

    /** The write loop threw (ENOSPC, EIO, …): `write_failed:<message>`. */
    const val REASON_WRITE_FAILED = "write_failed"

    /** Less than the 256 MiB window was written: `file_smaller_than_256MiB:bytes_written=<n>`. */
    const val REASON_FILE_SMALLER_THAN_WINDOW = "file_smaller_than_256MiB"

    /** The writer could not stamp its chunks (the native library): `pattern_unavailable:<message>`. */
    const val REASON_PATTERN_UNAVAILABLE = "pattern_unavailable"

    /** Neither O_DIRECT nor a buffered open worked: `read_open_failed:errno=<n> (<NAME>)`. */
    const val REASON_READ_OPEN_FAILED = "read_open_failed"

    /** A read failed for a reason other than EIO: `read_failed:errno=<n> (<NAME>)`. */
    const val REASON_READ_FAILED = "read_failed"

    // ── native verification producers (fieldsVersion 68, batch 3 part 2-b) ──
    // The reason texts below are the agent's [م] — only the two state NAMES
    // `memoryVerifyState` / `storageVerifyState` and the `ran` / `not_run:<reason>`
    // shape are approved (N-09, N-10). Every reason that carries text after a
    // second colon carries an error's TEXT (rule 21), capped by ReasonText.

    /** The producer ran to completion: its counts are real, a 0 is a measured 0. */
    const val VERIFY_RAN = "ran"

    /**
     * Prefix of "it ran but did not cover all it planned": `partial:<reason>`.
     * Its counts are real for what was covered; the covered bytes are shipped
     * beside them and are LESS than the planned bytes. A partial run is never
     * a pass for the whole.
     */
    const val PARTIAL_PREFIX = "partial:"

    fun partial(reason: String): String = PARTIAL_PREFIX + reason

    /** The time cap (memory) or the phase's remaining time (storage) ended it. */
    const val REASON_TIME_CAP_REACHED = "time_cap_reached"

    /** The scan cancelled the phase while it ran. */
    const val REASON_CANCELLED = "cancelled"

    /** Not enough of the phase was left to start the producer at all. */
    const val REASON_NO_TIME_LEFT = "no_time_left"

    /** The JNI call threw (e.g. the library did not load): `native_call_failed:<message>`. */
    const val REASON_NATIVE_CALL_FAILED = "native_call_failed"

    /** The native result was not the layout this build reads: `native_result_unreadable:size=<n>`. */
    const val REASON_NATIVE_RESULT_UNREADABLE = "native_result_unreadable"

    // ── memoryVerifyState (N-10) ──
    /** `sysinfo()` failed: `sysinfo_failed:errno=<n> (<NAME>)`. */
    const val REASON_SYSINFO_FAILED = "sysinfo_failed"

    /** `mmap` failed, also after the halved retry: `allocation_failed:requested_bytes=<n>, errno=<n> (<NAME>)`. */
    const val REASON_ALLOCATION_FAILED = "allocation_failed"

    /**
     * Part 2-c (user decision 2026-10-08, reading (b)): `sysinfo` reported too little free memory
     * for the test to run — free memory minus 500 MiB is below 512 MiB, i.e. free memory below
     * 1012 MiB. No buffer was attempted. The raw free memory is `memoryVerifyFreeBytes`. The
     * numbers live in memory_verify.h, once; Kotlin does not copy them.
     */
    const val REASON_INSUFFICIENT_FREE_MEMORY = "insufficient_free_memory"

    /** The producer was called with an unusable argument (a time cap <= 0, an offset that is not whole MiB). */
    const val REASON_INVALID_ARGUMENT = "invalid_argument"

    /** The native status code is one this build does not know: `native_status_unknown:<n>`. */
    const val REASON_NATIVE_STATUS_UNKNOWN = "native_status_unknown"

    /** The scan ended without the phase that hosts the producer having reached it. */
    const val REASON_PHASE_NOT_REACHED = "phase_not_reached"

    // ── names inside criticalDefectInputsNotRun[] (N-11) ──
    /** The one input `criticalDefectDetected` reads (S-31: its condition is not widened here). */
    const val INPUT_GPU_COMPUTE_GEMM = "gpuComputeGemm"

    fun notRun(reason: String): String = NOT_RUN_PREFIX + reason

    // ── workersPinState (N-12) ──
    /** The NEON pinning path ran at least once, so the six counters are real. */
    const val WORKERS_PIN_ATTEMPTED = "attempted"

    /** Prefix of "nothing tried to pin": `not_attempted:<reason>`. */
    const val NOT_ATTEMPTED_PREFIX = "not_attempted:"

    /** Approved reason: the CPU phases ran the AI workload, which never pins workers. */
    const val REASON_AI_WORKLOAD_PATH = "ai_workload_path"

    /** Agent's reason, not yet approved: the legacy pinning path was selected but no CPU phase reached it. */
    const val REASON_CPU_PHASES_NOT_RUN = "cpu_phases_not_run"

    fun notAttempted(reason: String): String = NOT_ATTEMPTED_PREFIX + reason

    /** Agent's state, not yet approved: a pin attempt ran but the native result was shorter than the pin tail. */
    const val PIN_RESULT_UNREADABLE_PREFIX = "pin_result_unreadable:size="
}

/**
 * fieldsVersion 68 (ت-13, ت-18 row 4, N-12): `workersPinned*` / `workersPinFailed*`
 * are written only by the NEON pinning path (`accumulateCpuOutcome`). On the AI
 * workload path (`USE_AI_WORKLOAD_CPU`) nothing tries to pin a worker, and the
 * old default `0` read as "tried, and none failed". Now the six counters are
 * `null` until a pin attempt really happened, and [state] says why not.
 */
object WorkersPinOutcome {
    /**
     * @param attempted whether any CPU phase ran the pinning path and got its whole telemetry back.
     * @param unreadableResultSize the length of a native result that was too short
     *   to hold the pin tail, when no complete one arrived (null = none was short).
     * @param aiWorkloadPathSelected whether the CPU phases are configured to run
     *   the AI workload (which never pins) instead of the pinning kernels.
     */
    fun stateOf(
        attempted: Boolean,
        aiWorkloadPathSelected: Boolean,
        unreadableResultSize: Int? = null
    ): String = when {
        attempted -> VerificationStates.WORKERS_PIN_ATTEMPTED
        unreadableResultSize != null ->
            VerificationStates.PIN_RESULT_UNREADABLE_PREFIX + unreadableResultSize
        aiWorkloadPathSelected -> VerificationStates.notAttempted(VerificationStates.REASON_AI_WORKLOAD_PATH)
        else -> VerificationStates.notAttempted(VerificationStates.REASON_CPU_PHASES_NOT_RUN)
    }
}

/** What the GPU compute GEMM verification produced over a scan. */
data class GpuGemmOutcome(
    /** `null` = the verification did not run — NOT "correct". */
    val correct: Boolean?,
    /** `passed` | `failed` | `not_run:<reason>` — see [VerificationStates]. */
    val state: String
) {
    companion object {
        /**
         * @param attempts GEMM verification attempts that COMPLETED (a test that
         *   returned, true or false) across phases 4 and 5.
         * @param anyFailed whether any completed attempt returned false.
         * @param governorCapped whether phases 4/5 were skipped by the governor
         *   capping watcher.
         */
        fun derive(attempts: Int, anyFailed: Boolean, governorCapped: Boolean): GpuGemmOutcome = when {
            attempts <= 0 -> GpuGemmOutcome(
                correct = null,
                state = VerificationStates.notRun(
                    if (governorCapped) VerificationStates.REASON_GOVERNOR_CAPPED
                    else VerificationStates.REASON_NO_ATTEMPT_COMPLETED
                )
            )
            anyFailed -> GpuGemmOutcome(correct = false, state = VerificationStates.GPU_GEMM_FAILED)
            else -> GpuGemmOutcome(correct = true, state = VerificationStates.GPU_GEMM_PASSED)
        }
    }
}

/**
 * `criticalDefectDetected` as a TRANSITIONAL AGGREGATE (S-31: "a declared
 * transitional aggregate; the judgement from the raw is the server's"). It
 * reads the GPU compute GEMM result only — its condition is NOT widened to
 * storage or memory errors. `detected` is `null` when that input did not run,
 * and [inputsNotRun] names what was missing; `[]` means every input ran.
 */
data class CriticalDefectOutcome(
    val detected: Boolean?,
    val inputsNotRun: List<String>
) {
    companion object {
        fun derive(gpuGemmCorrect: Boolean?): CriticalDefectOutcome = when (gpuGemmCorrect) {
            null -> CriticalDefectOutcome(null, listOf(VerificationStates.INPUT_GPU_COMPUTE_GEMM))
            true -> CriticalDefectOutcome(false, emptyList())
            false -> CriticalDefectOutcome(true, emptyList())
        }
    }
}

/**
 * The names of the footer fields that are on-device aggregates, shipped beside
 * the raw "for the transitional display" (S-20, N-23; S-31 for
 * `criticalDefectDetected`) and removed when the server reads the raw instead.
 * A name with a dot is a field inside a footer object, written as
 * `<object>.<field>`.
 *
 * The first nine are S-20's text ("thermalHeadroomAvg/Peak · thermalStatusMax ·
 * batteryHealth.{dischargeRateUnderLoadMa, voltageStabilityMv, batteryTempMaxC,
 * …} · maxBatteryTemp · stressEffectiveness.* · dataQuality.*") with the three
 * object-wide entries named by their object. The three `batteryHealth.*`
 * entries marked [derived] are this agent's reading of S-20's "…": they are
 * computed on the device from the aggregates above (a rise = max − start, a
 * ratio over the average) — reported for the coordinator to confirm.
 */
object TransitionalAggregates {
    val NAMES: List<String> = listOf(
        "thermalHeadroomAvg",
        "thermalHeadroomPeak",
        "thermalStatusMax",
        "maxBatteryTemp",
        "stressEffectiveness",
        "dataQuality",
        "batteryHealth.dischargeRateUnderLoadMa",
        "batteryHealth.voltageStabilityMv",
        "batteryHealth.batteryTempMaxC",
        // [derived] — agent's reading of S-20's "…"
        "batteryHealth.batteryTempRiseC",
        "batteryHealth.levelDropPct",
        "batteryHealth.dischargeVsDesignRatioMah",
        // S-31
        "verificationResults.criticalDefectDetected",
        // Part 2-c: D's time per phase, summed in the app from the raw runs (the
        // coordinator's addition to the user's text of 2026-10-08)
        "verificationResults.cpuKnownAnswerTotalMsByPhase"
    )
}
