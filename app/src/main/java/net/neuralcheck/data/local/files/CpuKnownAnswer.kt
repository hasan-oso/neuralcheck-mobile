package net.neuralcheck.data.local.files

import net.neuralcheck.util.ReasonText

/**
 * The wire names of the CPU known-answer checks (fieldsVersion 68, decision D)
 * — ONE source (rule 24): the payload writer, the file, the tests and the
 * scheduler all read them here, so a rename is a change to these lines.
 *
 * fieldsVersion 70 (contract docs/FV68_CONTRACT.md section 7-t): the sieve runs
 * to the limit the native layer names (`sieveLimit`, shipped raw), a run the time
 * budget left out is `skipped:time_budget`, and the scan-level state gains
 * `partial:time_budget`.
 *
 * Approved by the user 2026-10-08: `cpuKnownAnswerState` and
 * `cpuKnownAnswerRuns[]`. The ELEMENT fields and the status values below are
 * the coordinator's proposal; the agent's additions are marked [م]: the three
 * status spellings (part 2-b) and `tEnd` / `cpuKnownAnswerTotalMsByPhase` (part
 * 2-c — the user decided D's time is shipped raw, start and end in ms, and its sum
 * per phase; the names are the agent's).
 *
 * Deliberately NOT here, and never to be re-introduced with a new meaning:
 * `cpuPrimeSieveCorrect` and `cpuFmaErrors`. The server reads those two names
 * with the OLD meaning today (a Boolean and a counter, with `|| 0` and
 * `!== false` defaults), and `cpuFmaErrors` would lie anyway: nothing here
 * checks floating point. The app also ships no aggregate boolean and no
 * "passed": the count (`sievePrimeCount`) goes out raw and the server judges.
 */
object CpuKnownAnswerNames {
    const val STATE = "cpuKnownAnswerState"
    const val RUNS = "cpuKnownAnswerRuns"

    /**
     * [م] `verificationResults.cpuKnownAnswerTotalMsByPhase`: per phase id (as a string key), the
     * sum of (tEnd - t) over that phase's runs that have both. A TRANSITIONAL AGGREGATE kept in
     * the app (added by the coordinator, not by the user's text) — declared in
     * `transitionalAggregates`. The acceptance "D's time in a phase <= 1 % of the phase" is NOT
     * computed here; the server and the device check judge it.
     */
    const val TOTAL_MS_BY_PHASE = "cpuKnownAnswerTotalMsByPhase"

    // element fields
    const val PHASE = "phase"
    const val PLANNED_AT_PCT = "plannedAtPct"
    const val T = "t"
    /** [م] ms since scan start (the `v[0]` frame) when the run ended — see [CpuKnownAnswerRun.tEnd]. */
    const val T_END = "tEnd"
    const val SIEVE_STATUS = "sieveStatus"
    const val SIEVE_PRIME_COUNT = "sievePrimeCount"
    const val SIEVE_DURATION_MS = "sieveDurationMs"
    /**
     * fieldsVersion 70 (the name is the user's text, 2026-10-13): the N the sieve was run to, raw. It comes
     * from the native layer (`PRIME_SIEVE_1M_LIMIT` in reference_values.h) through the sieve array's slot 5;
     * Kotlin keeps no copy of the number (rule 24). Absent for a run that never began, and for a sieve call
     * that threw or returned an unreadable array.
     */
    const val SIEVE_LIMIT = "sieveLimit"
    const val SHA256_STATUS = "sha256Status"
    const val SHA256_DURATION_MS = "sha256DurationMs"

    // status values
    const val STATUS_MATCH = "match"
    const val STATUS_MISMATCH = "mismatch"
    const val STATUS_ALLOC_FAILED = "alloc_failed"
    /** SHA only: the FIPS 180-4 "abc" vector failed, so the implementation itself is broken. */
    const val STATUS_SELF_TEST_FAILED = "self_test_failed"
    /** The check had started and the phase was cancelled before this half ran. */
    const val STATUS_CANCELLED = "cancelled"
    /** The phase ended before the planned moment: nothing was run for this slot. */
    const val STATUS_NOT_REACHED = "not_reached"
    /**
     * fieldsVersion 70 (the literal is the user's text, 2026-10-13): the planned moment came and the time
     * budget ([CpuKnownAnswerBudget]) said no, so neither half ran. Written in BOTH `sieveStatus` and
     * `sha256Status`, like [STATUS_NOT_REACHED]; the run has no `t`, `tEnd`, count, durations or limit.
     */
    const val STATUS_SKIPPED_TIME_BUDGET = "skipped:time_budget"
    /** [م] The JNI call threw: `native_call_failed:<message>` (rule 21: the message, not the class). */
    const val STATUS_NATIVE_CALL_FAILED_PREFIX = "native_call_failed:"
    /** [م] The native result was not the layout this build reads: `native_result_unreadable:size=<n>`. */
    const val STATUS_NATIVE_RESULT_UNREADABLE_PREFIX = "native_result_unreadable:size="
    /** [م] The native status code is one this build does not know: `native_status_unknown:<n>`. */
    const val STATUS_NATIVE_STATUS_UNKNOWN_PREFIX = "native_status_unknown:"

    /** Statuses that mean "the native kernel ran and reported". */
    val EXECUTED_STATUSES: Set<String> =
        setOf(STATUS_MATCH, STATUS_MISMATCH, STATUS_ALLOC_FAILED, STATUS_SELF_TEST_FAILED)

    // state values / reasons (`not_run:<reason>`)
    const val REASON_AI_CPU_PATH_NOT_SELECTED = "ai_cpu_workload_path_not_selected"
    const val REASON_NO_CHECK_REACHED = "no_check_reached"

    /** [م] `partial:time_budget`: at least one half reported, and at least one run was skipped by the budget. */
    const val REASON_TIME_BUDGET = "time_budget"

    /**
     * The user's cap (2026-10-13): D may take at most this percent of its phase's PLANNED duration. Before
     * each run after the first, if (the time D has used in the phase so far + the longest run so far) is
     * above it, this run and the rest of the phase are skipped. See [CpuKnownAnswerBudget].
     */
    const val BUDGET_PERCENT_OF_PHASE = 1

    /**
     * Where in a phase the checks run, as a percent of its duration. The agent's
     * estimate from the part-1 report ("three checks per phase, at 25 %, 60 % and
     * 92 %"), approved as the design by the coordinator's order, not a number the
     * user fixed. Three, so a fault that appears with heat can show late; the last
     * early enough (92 %) that the answer lands inside the phase.
     */
    val PLANNED_AT_PCT_LIST: List<Int> = listOf(25, 60, 92)
}

/**
 * ONE check of the CPU known-answer pair (the prime sieve to the limit the native
 * layer names — [sieveLimit] — and the SHA-256 of a 1 MiB buffer), run beside the
 * AI workload in a CPU phase.
 *
 * Absent values are `null`, never 0: a check that did not run has no time, no
 * count and no duration. The statuses say which of "ran" / "did not run" / "ran
 * and failed this way" it is.
 */
data class CpuKnownAnswerRun(
    /** 3 (CPU_SUSTAINED) or 5 (COMBINED_PEAK). */
    val phase: Int,
    /** Where in the phase this check was PLANNED (25 / 60 / 92). */
    val plannedAtPct: Int,
    /** ms since scan start, the same frame as `v[0]`, when the check began. `null` = it never began. */
    val t: Long? = null,
    /**
     * ms since scan start, the same frame as [t], when the run ENDED: after the SHA-256, or —
     * when the phase cancelled it — after whichever half was the last to run. The run is
     * [t, tEnd], so it includes the JNI calls and the hand-off between the two kernels, not
     * only their own durations. `null` for a check that never began (`not_reached`): an
     * absence, not a 0.
     */
    val tEnd: Long? = null,
    val sieveStatus: String = CpuKnownAnswerNames.STATUS_NOT_REACHED,
    /** The prime count the kernel produced, raw — the server judges it against pi([sieveLimit]). `null` = none. */
    val sievePrimeCount: Long? = null,
    val sieveDurationMs: Long? = null,
    /** fieldsVersion 70: the N the sieve ran to, as the native layer reported it. `null` = none was reported. */
    val sieveLimit: Long? = null,
    val sha256Status: String = CpuKnownAnswerNames.STATUS_NOT_REACHED,
    val sha256DurationMs: Long? = null
) {
    /** The element as it goes on the wire: a key is written only when it has a value. */
    fun toWireMap(): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m[CpuKnownAnswerNames.PHASE] = phase
        m[CpuKnownAnswerNames.PLANNED_AT_PCT] = plannedAtPct
        t?.let { m[CpuKnownAnswerNames.T] = it }
        tEnd?.let { m[CpuKnownAnswerNames.T_END] = it }
        m[CpuKnownAnswerNames.SIEVE_STATUS] = sieveStatus
        sievePrimeCount?.let { m[CpuKnownAnswerNames.SIEVE_PRIME_COUNT] = it }
        sieveDurationMs?.let { m[CpuKnownAnswerNames.SIEVE_DURATION_MS] = it }
        sieveLimit?.let { m[CpuKnownAnswerNames.SIEVE_LIMIT] = it }
        m[CpuKnownAnswerNames.SHA256_STATUS] = sha256Status
        sha256DurationMs?.let { m[CpuKnownAnswerNames.SHA256_DURATION_MS] = it }
        return m
    }
}

/** The layouts of the two LongArrays `runSieveKnownAnswer` / `runSha256KnownAnswer` return (stress_jni.cpp). */
object KnownAnswerLayout {
    /**
     * fieldsVersion 70: the sieve array is layout 2 (6 slots: `[5]` = the limit N); layout 1 had 5 slots and no
     * limit. A layout-1 array is "unreadable" to this reader, never read with a missing slot.
     */
    const val SIEVE_VERSION = 2L

    /** The SHA array is unchanged: layout 1, 4 slots. */
    const val SHA_VERSION = 1L

    const val SIEVE_SIZE = 6
    const val SIEVE_IDX_VERSION = 0
    const val SIEVE_IDX_LENGTH = 1
    const val SIEVE_IDX_STATUS = 2
    const val SIEVE_IDX_PRIME_COUNT = 3
    const val SIEVE_IDX_DURATION_MS = 4
    const val SIEVE_IDX_LIMIT = 5

    const val SHA_SIZE = 4
    const val SHA_IDX_VERSION = 0
    const val SHA_IDX_LENGTH = 1
    const val SHA_IDX_STATUS = 2
    const val SHA_IDX_DURATION_MS = 3

    // KnownAnswerStatus in known_answer.h
    const val STATUS_MATCH = 0L
    const val STATUS_MISMATCH = 1L
    const val STATUS_ALLOCATION_FAILED = 2L
    const val STATUS_SELF_TEST_FAILED = 3L
}

/**
 * What the sieve half of a check produced: its status text and the raw numbers (null = none).
 * [limit] has no default on purpose: a reading that forgets the limit would silently ship none.
 */
data class SieveReading(val status: String, val primeCount: Long?, val durationMs: Long?, val limit: Long?) {
    companion object {
        fun nativeCallFailed(t: Throwable) =
            SieveReading(CpuKnownAnswerNames.STATUS_NATIVE_CALL_FAILED_PREFIX + ReasonText.of(t), null, null, null)

        fun fromNative(result: LongArray?): SieveReading {
            if (result == null ||
                result.size != KnownAnswerLayout.SIEVE_SIZE ||
                result[KnownAnswerLayout.SIEVE_IDX_VERSION] != KnownAnswerLayout.SIEVE_VERSION ||
                result[KnownAnswerLayout.SIEVE_IDX_LENGTH] != KnownAnswerLayout.SIEVE_SIZE.toLong()
            ) {
                return SieveReading(
                    CpuKnownAnswerNames.STATUS_NATIVE_RESULT_UNREADABLE_PREFIX + (result?.size ?: -1), null, null, null
                )
            }
            val duration = result[KnownAnswerLayout.SIEVE_IDX_DURATION_MS]
            val count = result[KnownAnswerLayout.SIEVE_IDX_PRIME_COUNT]
            // The N the kernel ran to, raw: present in every readable array, also when the allocation failed
            // (it is the N that was attempted) or the status code is one this build does not know.
            val limit = result[KnownAnswerLayout.SIEVE_IDX_LIMIT]
            return when (val status = result[KnownAnswerLayout.SIEVE_IDX_STATUS]) {
                KnownAnswerLayout.STATUS_MATCH -> SieveReading(CpuKnownAnswerNames.STATUS_MATCH, count, duration, limit)
                KnownAnswerLayout.STATUS_MISMATCH ->
                    SieveReading(CpuKnownAnswerNames.STATUS_MISMATCH, count, duration, limit)
                // The kernel produced no count (its sentinel is -1): there is nothing to ship.
                KnownAnswerLayout.STATUS_ALLOCATION_FAILED ->
                    SieveReading(CpuKnownAnswerNames.STATUS_ALLOC_FAILED, null, duration, limit)
                else ->
                    SieveReading(CpuKnownAnswerNames.STATUS_NATIVE_STATUS_UNKNOWN_PREFIX + status, null, duration, limit)
            }
        }
    }
}

data class Sha256Reading(val status: String, val durationMs: Long?) {
    companion object {
        fun nativeCallFailed(t: Throwable) =
            Sha256Reading(CpuKnownAnswerNames.STATUS_NATIVE_CALL_FAILED_PREFIX + ReasonText.of(t), null)

        fun fromNative(result: LongArray?): Sha256Reading {
            if (result == null ||
                result.size != KnownAnswerLayout.SHA_SIZE ||
                result[KnownAnswerLayout.SHA_IDX_VERSION] != KnownAnswerLayout.SHA_VERSION ||
                result[KnownAnswerLayout.SHA_IDX_LENGTH] != KnownAnswerLayout.SHA_SIZE.toLong()
            ) {
                return Sha256Reading(
                    CpuKnownAnswerNames.STATUS_NATIVE_RESULT_UNREADABLE_PREFIX + (result?.size ?: -1), null
                )
            }
            val duration = result[KnownAnswerLayout.SHA_IDX_DURATION_MS]
            return when (val status = result[KnownAnswerLayout.SHA_IDX_STATUS]) {
                KnownAnswerLayout.STATUS_MATCH -> Sha256Reading(CpuKnownAnswerNames.STATUS_MATCH, duration)
                KnownAnswerLayout.STATUS_MISMATCH -> Sha256Reading(CpuKnownAnswerNames.STATUS_MISMATCH, duration)
                KnownAnswerLayout.STATUS_ALLOCATION_FAILED ->
                    Sha256Reading(CpuKnownAnswerNames.STATUS_ALLOC_FAILED, duration)
                KnownAnswerLayout.STATUS_SELF_TEST_FAILED ->
                    Sha256Reading(CpuKnownAnswerNames.STATUS_SELF_TEST_FAILED, duration)
                else -> Sha256Reading(CpuKnownAnswerNames.STATUS_NATIVE_STATUS_UNKNOWN_PREFIX + status, duration)
            }
        }
    }
}

/**
 * `cpuKnownAnswerState` — the scan-level summary of the runs: `ran` when at
 * least one half of one check reported, otherwise `not_run:<reason>`; since
 * fieldsVersion 70 also `partial:time_budget` when at least one half reported
 * AND at least one run was skipped by the time budget ([CpuKnownAnswerBudget]).
 * It says NOTHING about whether the answers were right; the per-run statuses and
 * the raw prime count carry that, and the server judges them.
 *
 * `partial:*` is the pattern the memory and storage producers already use
 * (`VerificationStates.PARTIAL_PREFIX`): what ran is real, and it is never a pass
 * for the whole. A scan in which no half reported is `not_run:*` whether or not a
 * run was skipped.
 */
object CpuKnownAnswerState {
    const val RAN = "ran"

    fun of(runs: List<CpuKnownAnswerRun>, aiCpuPathSelected: Boolean): String {
        if (runs.isEmpty()) {
            return VerificationStates.notRun(
                if (aiCpuPathSelected) VerificationStates.REASON_PHASE_NOT_REACHED
                else CpuKnownAnswerNames.REASON_AI_CPU_PATH_NOT_SELECTED
            )
        }
        val statuses = runs.flatMap { listOf(it.sieveStatus, it.sha256Status) }
        if (statuses.any { it in CpuKnownAnswerNames.EXECUTED_STATUSES }) {
            return if (statuses.any { it == CpuKnownAnswerNames.STATUS_SKIPPED_TIME_BUDGET }) {
                VerificationStates.partial(CpuKnownAnswerNames.REASON_TIME_BUDGET)
            } else {
                RAN
            }
        }
        val failure = statuses.firstOrNull { it.startsWith(CpuKnownAnswerNames.STATUS_NATIVE_CALL_FAILED_PREFIX) }
        return VerificationStates.notRun(failure ?: CpuKnownAnswerNames.REASON_NO_CHECK_REACHED)
    }
}

/**
 * The time budget of D inside one phase (fieldsVersion 70, the user's rule of 2026-10-13, contract
 * section 7-t (b)): before each run after the FIRST, at its planned moment, if
 *
 *     (spent + longest) x 100 > phaseDurationMs x [CpuKnownAnswerNames.BUDGET_PERCENT_OF_PHASE]
 *
 * where `spent` is the sum of (tEnd - t) of the phase's earlier runs that began and `longest` is the
 * longest of them, this run and every later one of the phase are skipped (`skipped:time_budget`).
 * Equal is NOT skipped; the first run always runs (nothing before it to measure). Integer arithmetic only.
 *
 * A run that began and has no `tEnd` cannot arise in the scheduler's loop (every path that ends a run
 * stamps `tEnd`), but the function is defined for it so an absence is never read as a cheap run (rule
 * 22): it counts (now - t), what it has used until the moment of the decision. A run with no `t` never
 * began (`not_reached`, `skipped:time_budget`) and adds nothing.
 */
object CpuKnownAnswerBudget {
    /**
     * @param previous the phase's runs registered BEFORE the one being decided, in order.
     * @param nowMs the decision moment, in the `v[0]` frame (ms since scan start).
     * @param phaseDurationMs the phase's PLANNED duration.
     */
    fun exceeded(previous: List<CpuKnownAnswerRun>, nowMs: Long, phaseDurationMs: Long): Boolean {
        var spent = 0L
        var longest = 0L
        var anyBegan = false
        for (r in previous) {
            val start = r.t ?: continue
            val took = (r.tEnd ?: nowMs) - start
            anyBegan = true
            spent += took
            if (took > longest) longest = took
        }
        // The first run always runs: with no run before it there is nothing spent and nothing to project.
        if (!anyBegan) return false
        return (spent + longest) * 100L > phaseDurationMs * CpuKnownAnswerNames.BUDGET_PERCENT_OF_PHASE
    }
}

/**
 * The time D occupied in each CPU phase, from the raw runs (part 2-c, user decision 2026-10-08):
 * per phase, the sum of (tEnd - t) over the runs that have both. A run that never began has
 * neither and contributes nothing — a phase none of whose runs began has NO entry (absent, not 0).
 *
 * This is the numerator of the acceptance "D's time in a phase <= 1 % of that phase's time". The
 * phase's own time is not shipped: it derives from the samples (`phase` and `ts` / `v[0]`).
 * The comparison is not made in the app.
 */
object CpuKnownAnswerTotals {
    fun byPhase(runs: List<CpuKnownAnswerRun>): Map<Int, Long> {
        val out = LinkedHashMap<Int, Long>()
        for (r in runs) {
            val start = r.t ?: continue
            val end = r.tEnd ?: continue
            out[r.phase] = (out[r.phase] ?: 0L) + (end - start)
        }
        return out
    }
}
