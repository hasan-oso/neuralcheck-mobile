package net.neuralcheck.service.stress.v2

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.neuralcheck.data.local.files.CpuKnownAnswerNames
import net.neuralcheck.data.local.files.CpuKnownAnswerRun
import net.neuralcheck.data.local.files.KnownAnswerLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Part 2-b, decision D (T-20): the CPU known-answer checks' SCHEDULING and
 * STATUSES — three planned checks per CPU phase, at 25 / 60 / 92 % of its
 * duration, each stamped in the `v[0]` frame, with every unreached slot declared.
 *
 * ينتظر الفحص الحقيقي (waits for the real device check): the two native kernels
 * are a FAKE here (rule 37), on virtual time. These tests prove the scheduler's
 * logic, not that the kernels count 78,498 primes or hash the buffer right, and
 * not what the checks cost the measured load — which only a device shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CpuKnownAnswerSchedulerTest {

    // SCRIPTED: what a fv70 native layer returns for D's sieve: N = 1,000,000 and pi(N) = 78,498
    // (KnownAnswerReferenceTest checks both against reference_values.h and a sieve run on the JVM).
    private val limit = 1_000_000L
    private val reference = 78_498L

    private fun sieveResult(
        status: Long = KnownAnswerLayout.STATUS_MATCH, count: Long = reference, ms: Long = 90, n: Long = limit
    ) = longArrayOf(KnownAnswerLayout.SIEVE_VERSION, KnownAnswerLayout.SIEVE_SIZE.toLong(), status, count, ms, n)

    private fun shaResult(status: Long = KnownAnswerLayout.STATUS_MATCH, ms: Long = 30) =
        longArrayOf(KnownAnswerLayout.SHA_VERSION, KnownAnswerLayout.SHA_SIZE.toLong(), status, ms)

    /** Time the fake native calls "spent": the clock is virtual time plus this, so a run has a length. */
    private val spentMs = java.util.concurrent.atomic.AtomicLong(0)

    private fun TestScope.scheduler(fake: FakeNativeVerification) = CpuKnownAnswerScheduler(
        native = fake,
        elapsedRealtime = { testScheduler.currentTime + spentMs.get() },
        callDispatcher = StandardTestDispatcher(testScheduler)
    )

    /** The sieve takes 90 ms and the SHA 30 ms of clock, as a real kernel would (the numbers are the test's). */
    private fun happyFake(order: MutableList<String>? = null) = FakeNativeVerification(
        onSieve = { order?.add("sieve"); spentMs.addAndGet(90); sieveResult() },
        onSha = { order?.add("sha"); spentMs.addAndGet(30); shaResult() }
    )

    @Test
    fun `the planned moments are 25, 60 and 92 percent - one source`() {
        assertEquals(listOf(25, 60, 92), CpuKnownAnswerNames.PLANNED_AT_PCT_LIST)
    }

    @Test
    fun `a whole phase runs three checks at the planned moments, stamped in the scan frame`() = runTest {
        val order = CopyOnWriteArrayList<String>()
        val s = scheduler(happyFake(order))
        // Phase body starts at scan ms 10_000 on a 75 s phase.
        s.runPhase(phaseId = 3, phaseStartRealtime = 10_000, phaseDurationMs = 75_000, scanStartRealtime = 0)

        val runs = s.snapshot()
        assertEquals(3, runs.size)
        assertEquals(listOf(25, 60, 92), runs.map { it.plannedAtPct })
        assertEquals("every run is phase 3", listOf(3, 3, 3), runs.map { it.phase })
        // due = 10_000 + 75_000 * pct / 100 — on a clock that includes the time the kernels spent
        assertEquals(listOf(28_750L, 55_000L, 79_000L), runs.map { it.t })
        assertEquals("each run ends 120 ms after it began: the sieve's 90 and the SHA's 30, the hand-off between them included",
            listOf(28_870L, 55_120L, 79_120L), runs.map { it.tEnd })
        assertEquals(mapOf(3 to 360L), s.totalMsByPhase())
        assertEquals(listOf("sieve", "sha", "sieve", "sha", "sieve", "sha"), order.toList())
        for (r in runs) {
            assertEquals("match", r.sieveStatus)
            assertEquals("the raw count ships, not a verdict", reference, r.sievePrimeCount)
            assertEquals(90L, r.sieveDurationMs)
            assertEquals("the N the native layer ran the sieve to ships raw (fv70)", limit, r.sieveLimit)
            assertEquals("match", r.sha256Status)
            assertEquals(30L, r.sha256DurationMs)
        }
        assertEquals("ran", s.state(aiCpuPathSelected = true))
    }

    @Test
    fun `phase 3 and phase 5 accumulate six runs in order`() = runTest {
        val s = scheduler(happyFake())
        s.runPhase(3, phaseStartRealtime = 0, phaseDurationMs = 75_000, scanStartRealtime = 0)
        s.runPhase(5, phaseStartRealtime = testScheduler.currentTime, phaseDurationMs = 60_000, scanStartRealtime = 0)
        val runs = s.snapshot()
        assertEquals(listOf(3, 3, 3, 5, 5, 5), runs.map { it.phase })
        assertTrue("phase 5's checks come after phase 3's", runs[3].t!! > runs[2].t!!)
    }

    @Test
    fun `a phase that ends before a planned moment leaves that slot declared not_reached`() = runTest {
        val s = scheduler(happyFake())
        val job: Job = launch { s.runPhase(3, phaseStartRealtime = 0, phaseDurationMs = 75_000, scanStartRealtime = 0) }
        advanceTimeBy(30_000)       // the 25 % check (18_750) ran; 60 % (45_000) and 92 % (69_000) did not
        job.cancel()
        advanceUntilIdle()

        val runs = s.snapshot()
        assertEquals(3, runs.size)
        assertEquals("match", runs[0].sieveStatus)
        assertEquals("match", runs[0].sha256Status)
        assertEquals(120L, runs[0].tEnd!! - runs[0].t!!)
        assertEquals("only the run that began counts", mapOf(3 to 120L), s.totalMsByPhase())
        for (r in runs.drop(1)) {
            assertEquals("not_reached", r.sieveStatus)
            assertEquals("not_reached", r.sha256Status)
            assertNull("a check that never began has no time", r.t)
            assertNull("... and no end: an absence, not a 0", r.tEnd)
            assertNull(r.sievePrimeCount)
            assertNull(r.sieveDurationMs)
            assertNull("a run that never began ran the sieve to no N", r.sieveLimit)
            assertNull(r.sha256DurationMs)
        }
        assertEquals("ran", s.state(true))
    }

    @Test
    fun `a phase cancelled before any check is three not_reached and not_run - not an empty list`() = runTest {
        val s = scheduler(happyFake())
        val job = launch { s.runPhase(3, phaseStartRealtime = 0, phaseDurationMs = 75_000, scanStartRealtime = 0) }
        advanceTimeBy(1_000)
        job.cancel()
        advanceUntilIdle()

        val runs = s.snapshot()
        assertEquals(3, runs.size)
        assertTrue(runs.all { it.sieveStatus == "not_reached" && it.sha256Status == "not_reached" })
        assertTrue(runs.all { it.t == null && it.tEnd == null })
        assertNull("no run began: no total, not a 0", s.totalMsByPhase())
        assertEquals("not_run:no_check_reached", s.state(true))
    }

    @Test
    fun `cancelled between the sieve and the SHA - the sieve's answer is kept and the SHA is cancelled`() = runTest {
        lateinit var job: Job
        val fake = FakeNativeVerification(
            onSieve = { job.cancel(); spentMs.addAndGet(90); sieveResult(count = reference) },   // the cancel lands while the kernel runs
            onSha = { error("SHA must not start after the cancel") }
        )
        val s = scheduler(fake)
        job = launch { s.runPhase(3, phaseStartRealtime = 0, phaseDurationMs = 75_000, scanStartRealtime = 0) }
        advanceUntilIdle()

        val first = s.snapshot()[0]
        assertEquals("a native call cannot be interrupted: its answer is kept", "match", first.sieveStatus)
        assertEquals(reference, first.sievePrimeCount)
        assertEquals("the cut run keeps the limit its sieve answered with", limit, first.sieveLimit)
        assertEquals("cancelled", first.sha256Status)
        assertNull(first.sha256DurationMs)
        assertEquals("a cut run still has its end: where it stopped", first.t!! + 90, first.tEnd)
        assertEquals("not_reached", s.snapshot()[1].sieveStatus)
    }

    @Test
    fun `the end of a run is in the same frame as its start - the scan clock, not the phase's`() = runTest {
        val s = scheduler(happyFake())
        // phase body began at scan ms 40_000; the scan's own zero is at 30_000 (v[0] = clock - 30_000)
        s.runPhase(3, phaseStartRealtime = 40_000, phaseDurationMs = 75_000, scanStartRealtime = 30_000)
        val r = s.snapshot()[0]
        assertEquals(40_000L + 18_750L - 30_000L, r.t)
        assertEquals(r.t!! + 120, r.tEnd)
    }

    @Test
    fun `a wrong count is shipped as the raw count with status mismatch - the server judges`() = runTest {
        val s = scheduler(FakeNativeVerification(
            onSieve = { sieveResult(status = KnownAnswerLayout.STATUS_MISMATCH, count = reference - 1) },
            onSha = { shaResult(status = KnownAnswerLayout.STATUS_MISMATCH) }
        ))
        s.runPhase(3, 0, 75_000, 0)
        val r = s.snapshot()[0]
        assertEquals("mismatch", r.sieveStatus)
        assertEquals(reference - 1, r.sievePrimeCount)
        assertEquals("a mismatch still says what the sieve ran to", limit, r.sieveLimit)
        assertEquals("mismatch", r.sha256Status)
        assertEquals("ran", s.state(true))
    }

    @Test
    fun `every SHA failure cause has its own status - not one false`() = runTest {
        val causes = mapOf(
            KnownAnswerLayout.STATUS_MISMATCH to "mismatch",
            KnownAnswerLayout.STATUS_ALLOCATION_FAILED to "alloc_failed",
            KnownAnswerLayout.STATUS_SELF_TEST_FAILED to "self_test_failed"
        )
        for ((code, expected) in causes) {
            val s = scheduler(FakeNativeVerification(onSieve = { sieveResult() }, onSha = { shaResult(status = code) }))
            s.runPhase(3, testScheduler.currentTime, 75_000, 0)
            assertEquals(expected, s.snapshot()[0].sha256Status)
        }
        val unknown = scheduler(FakeNativeVerification(onSieve = { sieveResult() }, onSha = { shaResult(status = 42) }))
        unknown.runPhase(3, testScheduler.currentTime, 75_000, 0)
        assertEquals("native_status_unknown:42", unknown.snapshot()[0].sha256Status)
    }

    @Test
    fun `a sieve that could not allocate has no count - not a zero`() = runTest {
        val s = scheduler(FakeNativeVerification(
            onSieve = { sieveResult(status = KnownAnswerLayout.STATUS_ALLOCATION_FAILED, count = -1, ms = 3) },
            onSha = { shaResult() }
        ))
        s.runPhase(3, 0, 75_000, 0)
        val r = s.snapshot()[0]
        assertEquals("alloc_failed", r.sieveStatus)
        assertNull(r.sievePrimeCount)
        assertEquals(3L, r.sieveDurationMs)
        assertEquals("the N that was attempted still ships", limit, r.sieveLimit)
    }

    @Test
    fun `a native throw is a status with the message - and every slot still runs`() = runTest {
        val s = scheduler(FakeNativeVerification(
            onSieve = { throw UnsatisfiedLinkError("no libstress") },
            onSha = { throw UnsatisfiedLinkError("no libstress") }
        ))
        s.runPhase(3, 0, 75_000, 0)
        val runs = s.snapshot()
        assertEquals(3, runs.size)
        for (r in runs) {
            assertEquals("native_call_failed:no libstress", r.sieveStatus)
            assertEquals("native_call_failed:no libstress", r.sha256Status)
            assertNull(r.sievePrimeCount)
            assertNull("a sieve call that threw has no limit - not a guessed one", r.sieveLimit)
            assertTrue("it began, so it has a time", r.t != null)
        }
        assertEquals("not_run:native_call_failed:no libstress", s.state(true))
    }

    @Test
    fun `a result of another shape is declared unreadable`() = runTest {
        val s = scheduler(FakeNativeVerification(onSieve = { longArrayOf(1, 2) }, onSha = { longArrayOf() }))
        s.runPhase(3, 0, 75_000, 0)
        val r = s.snapshot()[0]
        assertEquals("native_result_unreadable:size=2", r.sieveStatus)
        assertEquals("native_result_unreadable:size=0", r.sha256Status)
        assertNull("an unreadable sieve array has no limit", r.sieveLimit)
        assertEquals("not_run:no_check_reached", s.state(true))
    }

    @Test
    fun `a layout-1 sieve array - what an fv68 or fv69 library returns - is unreadable, not read without its limit`() = runTest {
        val s = scheduler(FakeNativeVerification(
            onSieve = { longArrayOf(1, 5, KnownAnswerLayout.STATUS_MATCH, 664_579, 90) },
            onSha = { shaResult() }
        ))
        s.runPhase(3, 0, 75_000, 0)
        val r = s.snapshot()[0]
        assertEquals("native_result_unreadable:size=5", r.sieveStatus)
        assertNull(r.sievePrimeCount)
        assertNull(r.sieveLimit)
        assertEquals("the SHA half is a different array and still reads", "match", r.sha256Status)
    }

    // ───────────────────────── the time budget (fieldsVersion 70, contract 7-t (b)) ─────────────────────────

    private val skipped = CpuKnownAnswerNames.STATUS_SKIPPED_TIME_BUDGET

    /**
     * A fake native layer in which the k-th run (the k-th sieve call) takes [runMs]`[k]` ms of clock in total: the
     * SHA's 30 ms and the sieve's rest. A call beyond the list throws, which the scheduler turns into a
     * `native_call_failed` status (as it would in production) — so a skipped run that wrongly called native code
     * fails the status assertions and the call counters below.
     */
    private inner class RunLengths(vararg val runMs: Long) {
        val sieveCalls = AtomicInteger(0)
        val shaCalls = AtomicInteger(0)
        val fake = FakeNativeVerification(
            onSieve = {
                val n = sieveCalls.incrementAndGet()
                check(n <= runMs.size) { "native sieve call #$n was not expected: a skipped run must not call native code" }
                val ms = runMs[n - 1] - 30
                spentMs.addAndGet(ms)
                sieveResult(ms = ms)
            },
            onSha = { shaCalls.incrementAndGet(); spentMs.addAndGet(30); shaResult() }
        )
    }

    @Test
    fun `the budget percent is the user's 1 - one source`() {
        assertEquals(1, CpuKnownAnswerNames.BUDGET_PERCENT_OF_PHASE)
        assertEquals("skipped:time_budget", CpuKnownAnswerNames.STATUS_SKIPPED_TIME_BUDGET)
    }

    @Test
    fun `the first run always runs - even when it alone is far over the budget`() = runTest {
        val lengths = RunLengths(5_000)
        val s = scheduler(lengths.fake)
        s.runPhase(3, phaseStartRealtime = 0, phaseDurationMs = 75_000, scanStartRealtime = 0)
        val runs = s.snapshot()
        assertEquals("match", runs[0].sieveStatus)
        assertEquals("match", runs[0].sha256Status)
        assertEquals(5_000L, runs[0].tEnd!! - runs[0].t!!)
        assertEquals(listOf(skipped, skipped), runs.drop(1).map { it.sieveStatus })
        assertEquals(1, lengths.sieveCalls.get())
    }

    @Test
    fun `phase 3 - a run whose sum plus longest EQUALS 1 percent runs, one more ms and it is skipped`() = runTest {
        // limit = 1 % of 75 000 = 750 ms. First run 375: (375 + 375) x 100 = 75 000 = the limit -> the second RUNS.
        val equal = RunLengths(375, 375)
        val a = scheduler(equal.fake)
        a.runPhase(3, 0, 75_000, 0)
        assertEquals(listOf("match", "match", skipped), a.snapshot().map { it.sieveStatus })   // third: 750 + 375 > 750
        assertEquals(2, equal.sieveCalls.get())

        // 376: (376 + 376) x 100 = 75 200 > 75 000 -> the second AND the third are skipped.
        spentMs.set(0)
        val over = RunLengths(376)
        val b = scheduler(over.fake)
        b.runPhase(3, testScheduler.currentTime, 75_000, 0)
        assertEquals(listOf("match", skipped, skipped), b.snapshot().map { it.sieveStatus })
        assertEquals(1, over.sieveCalls.get())
    }

    @Test
    fun `the third alone is skipped when the first two together push it over`() = runTest {
        val lengths = RunLengths(300, 300)
        val s = scheduler(lengths.fake)
        s.runPhase(3, 0, 75_000, 0)
        // second: (300 + 300) = 600 <= 750 runs; third: (600 + 300) = 900 > 750 skipped
        assertEquals(listOf("match", "match", skipped), s.snapshot().map { it.sieveStatus })
        assertEquals(listOf("match", "match", skipped), s.snapshot().map { it.sha256Status })
        assertEquals(2, lengths.sieveCalls.get())
    }

    @Test
    fun `the third runs when the sum plus the longest lands exactly on the limit`() = runTest {
        val lengths = RunLengths(250, 250, 250)
        val s = scheduler(lengths.fake)
        s.runPhase(3, 0, 75_000, 0)
        // second: 500 <= 750; third: (500 + 250) = 750 = the limit -> runs
        assertEquals(listOf("match", "match", "match"), s.snapshot().map { it.sieveStatus })
        assertEquals("nothing skipped: ran", "ran", s.state(true))
    }

    @Test
    fun `it is the LONGEST earlier run that is projected, not the last one`() = runTest {
        val lengths = RunLengths(350, 100)
        val s = scheduler(lengths.fake)
        s.runPhase(3, 0, 75_000, 0)
        // second: (350 + 350) = 700 <= 750 runs (100 ms). third: spent 450 + longest 350 = 800 > 750 skipped;
        // with the LAST run (100) it would be 550 and would run - the rule says longest.
        assertEquals(listOf("match", "match", skipped), s.snapshot().map { it.sieveStatus })
    }

    @Test
    fun `phase 5 has its own limit - 600 ms, from its planned 60 000`() = runTest {
        val equal = RunLengths(300, 300)
        val a = scheduler(equal.fake)
        a.runPhase(5, 0, 60_000, 0)
        // second: (300 + 300) x 100 = 60 000 = the limit -> runs; third: 900 > 600 skipped
        assertEquals(listOf("match", "match", skipped), a.snapshot().map { it.sieveStatus })

        spentMs.set(0)
        val over = RunLengths(301)
        val b = scheduler(over.fake)
        b.runPhase(5, testScheduler.currentTime, 60_000, 0)
        assertEquals(listOf("match", skipped, skipped), b.snapshot().map { it.sieveStatus })
    }

    @Test
    fun `a skipped run has two statuses and nothing else - no time, no end, no count, no duration, no limit - and calls no native code`() = runTest {
        val lengths = RunLengths(400)
        val s = scheduler(lengths.fake)
        s.runPhase(3, 0, 75_000, 0)
        val runs = s.snapshot()
        assertEquals(3, runs.size)
        for (r in runs.drop(1)) {
            assertEquals(skipped, r.sieveStatus)
            assertEquals(skipped, r.sha256Status)
            assertNull(r.t); assertNull(r.tEnd)
            assertNull(r.sievePrimeCount); assertNull(r.sieveDurationMs); assertNull(r.sieveLimit)
            assertNull(r.sha256DurationMs)
        }
        assertEquals(listOf(60, 92), runs.drop(1).map { it.plannedAtPct })
        assertEquals("one sieve and one SHA call, for the first run only", 1, lengths.sieveCalls.get())
        assertEquals(1, lengths.shaCalls.get())
        assertEquals("the skipped runs add nothing to the phase total", mapOf(3 to 400L), s.totalMsByPhase())
        assertEquals("some ran and some were left out: partial, not ran", "partial:time_budget", s.state(true))
    }

    @Test
    fun `a phase that ends before the second run's moment leaves it not_reached - not skipped`() = runTest {
        val lengths = RunLengths(400)
        val s = scheduler(lengths.fake)
        val job = launch { s.runPhase(3, phaseStartRealtime = 0, phaseDurationMs = 75_000, scanStartRealtime = 0) }
        advanceTimeBy(30_000)       // the first run (18 750) ran and was over budget; the second's moment (45 000) never came
        job.cancel()
        advanceUntilIdle()
        val runs = s.snapshot()
        assertEquals("match", runs[0].sieveStatus)
        assertEquals(listOf("not_reached", "not_reached"), runs.drop(1).map { it.sieveStatus })
        assertEquals("ran", s.state(true))
    }

    @Test
    fun `once the budget has spoken the rest of the phase is skipped at once - a later cancel changes nothing`() = runTest {
        val lengths = RunLengths(400)
        val s = scheduler(lengths.fake)
        val job = launch { s.runPhase(3, phaseStartRealtime = 0, phaseDurationMs = 75_000, scanStartRealtime = 0) }
        advanceTimeBy(46_000)       // past the second run's moment (45 000), before the third's (69 000)
        runCurrent()
        assertEquals("the third is already recorded - it does not wait for its own moment", listOf(skipped, skipped),
            s.snapshot().drop(1).map { it.sieveStatus })
        assertTrue("the coroutine returned", job.isCompleted)
        job.cancel()
        advanceUntilIdle()
        assertEquals(listOf(skipped, skipped), s.snapshot().drop(1).map { it.sieveStatus })
    }

    @Test
    fun `the phases are decided apart - phase 3 skipping does not touch phase 5`() = runTest {
        val lengths = RunLengths(500, 120, 120, 120)
        val s = scheduler(lengths.fake)
        s.runPhase(3, 0, 75_000, 0)
        s.runPhase(5, phaseStartRealtime = testScheduler.currentTime + spentMs.get(), phaseDurationMs = 60_000, scanStartRealtime = 0)
        val runs = s.snapshot()
        assertEquals(listOf(3, 3, 3, 5, 5, 5), runs.map { it.phase })
        assertEquals(listOf("match", skipped, skipped, "match", "match", "match"), runs.map { it.sieveStatus })
        assertEquals(mapOf(3 to 500L, 5 to 360L), s.totalMsByPhase())
        assertEquals("partial:time_budget", s.state(true))
    }

    @Test
    fun `a run whose native call threw is a finished run - it counts by its real, short time`() = runTest {
        val calls = AtomicInteger(0)
        val s = scheduler(FakeNativeVerification(
            onSieve = {
                if (calls.incrementAndGet() == 1) { spentMs.addAndGet(100); throw UnsatisfiedLinkError("no libstress") }
                spentMs.addAndGet(90); sieveResult()
            },
            onSha = {
                if (calls.get() == 1) { spentMs.addAndGet(100); throw UnsatisfiedLinkError("no libstress") }
                spentMs.addAndGet(30); shaResult()
            }
        ))
        s.runPhase(3, 0, 75_000, 0)
        val runs = s.snapshot()
        assertEquals("native_call_failed:no libstress", runs[0].sieveStatus)
        assertEquals("it has its end, so the budget measured it: 200 ms", 200L, runs[0].tEnd!! - runs[0].t!!)
        // second: (200 + 200) = 400 <= 750 runs; third: spent 200 + 120 = 320 + longest 200 = 520 <= 750 runs
        assertEquals(listOf("match", "match"), runs.drop(1).map { it.sieveStatus })
        assertEquals("ran", s.state(true))
    }

    @Test
    fun `a first run that failed after a long time still uses the budget - the rest is skipped and the state is not_run`() = runTest {
        val s = scheduler(FakeNativeVerification(
            onSieve = { spentMs.addAndGet(2_000); throw UnsatisfiedLinkError("no libstress") },
            onSha = { spentMs.addAndGet(2_000); throw UnsatisfiedLinkError("no libstress") }
        ))
        s.runPhase(3, 0, 75_000, 0)
        val runs = s.snapshot()
        assertEquals(listOf("native_call_failed:no libstress", skipped, skipped), runs.map { it.sieveStatus })
        assertEquals("no half reported: not_run - skipped or not", "not_run:native_call_failed:no libstress", s.state(true))
    }

    @Test
    fun `the scan-level state when no phase registered anything names why`() {
        val s = CpuKnownAnswerScheduler(native = FakeNativeVerification())
        assertEquals("not_run:phase_not_reached", s.state(aiCpuPathSelected = true))
        assertEquals("not_run:ai_cpu_workload_path_not_selected", s.state(aiCpuPathSelected = false))
        assertEquals(emptyList<CpuKnownAnswerRun>(), s.snapshot())
    }
}
