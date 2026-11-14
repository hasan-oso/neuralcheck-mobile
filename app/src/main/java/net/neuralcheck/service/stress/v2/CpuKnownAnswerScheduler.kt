package net.neuralcheck.service.stress.v2

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.neuralcheck.data.local.files.CpuKnownAnswerBudget
import net.neuralcheck.data.local.files.CpuKnownAnswerNames
import net.neuralcheck.data.local.files.CpuKnownAnswerRun
import net.neuralcheck.data.local.files.CpuKnownAnswerState
import net.neuralcheck.data.local.files.CpuKnownAnswerTotals
import net.neuralcheck.data.local.files.Sha256Reading
import net.neuralcheck.data.local.files.SieveReading
import java.util.concurrent.atomic.AtomicReference

/**
 * The CPU known-answer checks' scheduler (fieldsVersion 68, decision D, T-20).
 *
 * It runs as a SIBLING coroutine beside the AI workload in the two CPU phases
 * (3 and 5) — NOT inside `AiCpuWorkload`, which stays exactly the workload it
 * was: three checks per phase, planned at 25 %, 60 % and 92 % of the phase's
 * duration, each stamped with the elapsed ms in the `v[0]` frame when it began.
 * A check is the prime sieve (to the limit the native layer names, fv70) then the SHA-256 of a 1 MiB buffer;
 * the native layer returns raw outcomes and this class keeps them — it computes
 * no verdict and ships no aggregate "passed" (rule 20).
 *
 * Every planned slot is registered as `not_reached` up front, so a phase that
 * ends early (governor capping, thermal termination) leaves its unreached
 * checks visibly unreached instead of absent.
 *
 * fieldsVersion 70: a time budget keeps D under 1 % of its phase — before each run
 * after the first, if (the time D has used in the phase + its longest run) is above
 * that, the run and the rest of the phase are `skipped:time_budget` (contract section
 * 7-t, [CpuKnownAnswerBudget]).
 *
 * What a JVM test can and cannot cover: the scheduling, the statuses, the
 * cancellation. The two native kernels are replaced by a fake there (rule 37).
 */
class CpuKnownAnswerScheduler(
    private val native: NativeVerificationApi = StressNativeVerification,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    /**
     * Where the native calls run. IO, not Default: the AI workers hold the
     * Default threads flat out, and a check that has to queue behind them would
     * not run when it was planned.
     */
    private val callDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val lock = Any()
    private val runs = mutableListOf<CpuKnownAnswerRun>()

    /** The runs so far, in registration order (phase 3's three, then phase 5's). */
    fun snapshot(): List<CpuKnownAnswerRun> = synchronized(lock) { runs.toList() }

    /** `ran` | `partial:time_budget` | `not_run:<reason>` — see [CpuKnownAnswerState]. */
    fun state(aiCpuPathSelected: Boolean): String = CpuKnownAnswerState.of(snapshot(), aiCpuPathSelected)

    /** Per phase, the sum of its runs' (tEnd - t); `null` when no run has both — see [CpuKnownAnswerTotals]. */
    fun totalMsByPhase(): Map<Int, Long>? = CpuKnownAnswerTotals.byPhase(snapshot()).takeIf { it.isNotEmpty() }

    private fun register(phaseId: Int): Int = synchronized(lock) {
        val first = runs.size
        for (pct in CpuKnownAnswerNames.PLANNED_AT_PCT_LIST) {
            runs += CpuKnownAnswerRun(phase = phaseId, plannedAtPct = pct)
        }
        first
    }

    private fun update(index: Int, transform: (CpuKnownAnswerRun) -> CpuKnownAnswerRun) = synchronized(lock) {
        runs[index] = transform(runs[index])
    }

    /** The phase's runs registered before [index] (indices [first] until [index]), in order. */
    private fun earlierRunsOfPhase(first: Int, index: Int): List<CpuKnownAnswerRun> =
        synchronized(lock) { runs.subList(first, index).toList() }

    /** Marks the runs [from] until [until] as left out by the time budget — both halves, nothing else set. */
    private fun skip(from: Int, until: Int) = synchronized(lock) {
        for (i in from until until) {
            runs[i] = runs[i].copy(
                sieveStatus = CpuKnownAnswerNames.STATUS_SKIPPED_TIME_BUDGET,
                sha256Status = CpuKnownAnswerNames.STATUS_SKIPPED_TIME_BUDGET
            )
        }
    }

    /**
     * Runs the planned checks of one phase (three, unless the time budget leaves some out). Returns
     * when the last has run, the budget skipped the rest, or the coroutine is cancelled (the caller
     * cancels it when the phase ends).
     *
     * The time budget (fieldsVersion 70, [CpuKnownAnswerBudget]): at the planned moment of every run
     * after the first, if (the time D has used in this phase + its longest run) is above
     * [CpuKnownAnswerNames.BUDGET_PERCENT_OF_PHASE] % of [phaseDurationMs], that run and the rest of
     * the phase are recorded `skipped:time_budget` and nothing more is run. The first run always runs.
     *
     * @param phaseStartRealtime `SystemClock.elapsedRealtime()` at the phase body's start.
     * @param phaseDurationMs the phase's PLANNED duration — the base of the planned moments and of the budget.
     * @param scanStartRealtime the same clock at scan start — `v[0]`'s zero.
     */
    suspend fun runPhase(phaseId: Int, phaseStartRealtime: Long, phaseDurationMs: Long, scanStartRealtime: Long) {
        val first = register(phaseId)
        val slots = CpuKnownAnswerNames.PLANNED_AT_PCT_LIST.size
        for ((slot, pct) in CpuKnownAnswerNames.PLANNED_AT_PCT_LIST.withIndex()) {
            val index = first + slot
            val dueAt = phaseStartRealtime + phaseDurationMs * pct / 100
            val wait = dueAt - elapsedRealtime()
            // Cancelled here = the phase ended before this check's moment: it stays not_reached.
            if (wait > 0) delay(wait)

            // The budget is decided at the moment, after the wait: a phase that ended first never gets here.
            if (slot > 0 &&
                CpuKnownAnswerBudget.exceeded(
                    earlierRunsOfPhase(first, index), elapsedRealtime() - scanStartRealtime, phaseDurationMs
                )
            ) {
                skip(from = index, until = first + slots)
                return
            }

            val began = elapsedRealtime() - scanStartRealtime
            update(index) { it.copy(t = began) }
            // The END of the run, in the same frame (part 2-c, user decision 2026-10-08): the
            // moment the check stopped being this phase's extra load — after the SHA, or after
            // whichever half was the last to run. The run is [began, end]: both kernels AND the
            // JNI calls and the hand-off between them, not the two kernels' own durations.
            fun endedAt(): Long = elapsedRealtime() - scanStartRealtime

            // ── prime sieve ──
            val sieveHolder = AtomicReference<SieveReading?>(null)
            try {
                withContext(callDispatcher) {
                    sieveHolder.set(
                        try {
                            SieveReading.fromNative(native.runSieveKnownAnswer())
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            SieveReading.nativeCallFailed(t)
                        }
                    )
                }
            } catch (ce: CancellationException) {
                // The native call cannot be interrupted: if it had already answered, keep that.
                val got = sieveHolder.get()
                update(index) {
                    it.copy(
                        sieveStatus = got?.status ?: CpuKnownAnswerNames.STATUS_CANCELLED,
                        sievePrimeCount = got?.primeCount,
                        sieveDurationMs = got?.durationMs,
                        sieveLimit = got?.limit,
                        sha256Status = CpuKnownAnswerNames.STATUS_CANCELLED,
                        tEnd = endedAt()
                    )
                }
                throw ce
            }
            val sieve = sieveHolder.get()!!
            update(index) {
                it.copy(
                    sieveStatus = sieve.status,
                    sievePrimeCount = sieve.primeCount,
                    sieveDurationMs = sieve.durationMs,
                    sieveLimit = sieve.limit
                )
            }

            // ── SHA-256 ──
            try {
                currentCoroutineContext().ensureActive()
            } catch (ce: CancellationException) {
                update(index) { it.copy(sha256Status = CpuKnownAnswerNames.STATUS_CANCELLED, tEnd = endedAt()) }
                throw ce
            }
            val shaHolder = AtomicReference<Sha256Reading?>(null)
            try {
                withContext(callDispatcher) {
                    shaHolder.set(
                        try {
                            Sha256Reading.fromNative(native.runSha256KnownAnswer())
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            Sha256Reading.nativeCallFailed(t)
                        }
                    )
                }
            } catch (ce: CancellationException) {
                val got = shaHolder.get()
                update(index) {
                    it.copy(
                        sha256Status = got?.status ?: CpuKnownAnswerNames.STATUS_CANCELLED,
                        sha256DurationMs = got?.durationMs,
                        tEnd = endedAt()
                    )
                }
                throw ce
            }
            val sha = shaHolder.get()!!
            update(index) {
                it.copy(sha256Status = sha.status, sha256DurationMs = sha.durationMs, tEnd = endedAt())
            }
        }
    }
}
