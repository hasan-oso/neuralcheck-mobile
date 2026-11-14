package net.neuralcheck.files

import net.neuralcheck.data.local.files.CpuKnownAnswerNames
import net.neuralcheck.data.local.files.CpuKnownAnswerRun
import net.neuralcheck.data.local.files.CpuKnownAnswerState
import net.neuralcheck.data.local.files.CpuKnownAnswerTotals
import net.neuralcheck.data.local.files.KnownAnswerLayout
import net.neuralcheck.data.local.files.Sha256Reading
import net.neuralcheck.data.local.files.SieveReading
import net.neuralcheck.data.local.files.VerificationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Part 2-b, decision D (T-20): the CPU known-answer result as the app reads and
 * ships it — raw prime counts and per-check statuses, absent where nothing ran,
 * and none of the old names with a new meaning.
 *
 * ينتظر الفحص الحقيقي: the native kernels are not in this test (rule 37); the
 * layout parsing, the wire shape and the names are.
 */
class CpuKnownAnswerWireTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // SCRIPTED: the numbers a fv70 native layer returns for D's sieve: N = 1,000,000 and pi(N) = 78,498
    // (KnownAnswerReferenceTest checks both against reference_values.h and a sieve run on the JVM).
    private val limit = 1_000_000L
    private val reference = 78_498L

    private fun run(
        phase: Int = 3, pct: Int = 25, t: Long? = 28_750, tEnd: Long? = if (t == null) null else t + 140,
        // a sieve with no duration produced no reading (never began, or the call threw): it has no limit either
        sieve: String = "match", count: Long? = reference, sieveMs: Long? = 90,
        sieveLimit: Long? = if (sieveMs == null) null else limit,
        sha: String = "match", shaMs: Long? = 30
    ) = CpuKnownAnswerRun(
        phase = phase, plannedAtPct = pct, t = t, tEnd = tEnd,
        sieveStatus = sieve, sievePrimeCount = count, sieveDurationMs = sieveMs, sieveLimit = sieveLimit,
        sha256Status = sha, sha256DurationMs = shaMs
    )

    private fun vrOf(v: VerificationSummary) = FooterRoundTrip(tmp.newFolder()).let { rt ->
        rt.run(rt.baseFooter().copy(verificationResults = v)).wireFooter.getAsJsonObject("verificationResults")
    }

    // ───────────────────────── reading the two native arrays ─────────────────────────

    /** The sieve array as a fv70 native layer returns it: layout 2, six slots, the limit in `[5]`. */
    private fun sieveArray(status: Long, count: Long, ms: Long, n: Long = limit) =
        longArrayOf(KnownAnswerLayout.SIEVE_VERSION, KnownAnswerLayout.SIEVE_SIZE.toLong(), status, count, ms, n)

    @Test
    fun `the sieve array is read into status, raw count, duration and the limit it ran to`() {
        val r = SieveReading.fromNative(sieveArray(KnownAnswerLayout.STATUS_MISMATCH, reference - 1, 120))
        assertEquals(SieveReading("mismatch", reference - 1, 120, limit), r)
        assertEquals(
            SieveReading("match", reference, 90, limit),
            SieveReading.fromNative(sieveArray(KnownAnswerLayout.STATUS_MATCH, reference, 90))
        )
    }

    @Test
    fun `the limit is whatever the native layer reported - Kotlin holds no copy of it`() {
        // a limit the app never heard of travels as it came (the server reads the reference from it)
        assertEquals(
            12_345L,
            SieveReading.fromNative(sieveArray(KnownAnswerLayout.STATUS_MATCH, 1_000, 5, n = 12_345)).limit
        )
    }

    @Test
    fun `an allocation failure has no count - its -1 sentinel is never shipped - but the limit it attempted is`() {
        val r = SieveReading.fromNative(sieveArray(KnownAnswerLayout.STATUS_ALLOCATION_FAILED, -1, 3))
        assertEquals("alloc_failed", r.status)
        assertNull(r.primeCount)
        assertEquals(3L, r.durationMs)
        assertEquals("the N that was attempted", limit, r.limit)
    }

    @Test
    fun `the SHA array keeps the cause`() {
        fun sha(code: Long) = Sha256Reading.fromNative(longArrayOf(1, 4, code, 31))
        assertEquals("match", sha(KnownAnswerLayout.STATUS_MATCH).status)
        assertEquals("mismatch", sha(KnownAnswerLayout.STATUS_MISMATCH).status)
        assertEquals("alloc_failed", sha(KnownAnswerLayout.STATUS_ALLOCATION_FAILED).status)
        assertEquals("self_test_failed", sha(KnownAnswerLayout.STATUS_SELF_TEST_FAILED).status)
        assertEquals("native_status_unknown:9", sha(9).status)
        assertEquals(31L, sha(KnownAnswerLayout.STATUS_MATCH).durationMs)
    }

    @Test
    fun `arrays of another shape are unreadable - never read as a match or as zeros - and have no limit`() {
        assertEquals("native_result_unreadable:size=-1", SieveReading.fromNative(null).status)
        assertEquals("native_result_unreadable:size=4", SieveReading.fromNative(longArrayOf(2, 6, 0, reference)).status)
        // a LAYOUT-1 sieve array (fv68/69: five slots, no limit) is not read with a missing limit
        assertEquals("native_result_unreadable:size=5",
            SieveReading.fromNative(longArrayOf(1, 5, 0, 664_579, 90)).status)
        // six slots under the wrong version, or announcing the wrong length
        assertEquals("native_result_unreadable:size=6",
            SieveReading.fromNative(longArrayOf(3, 6, 0, reference, 90, limit)).status)
        assertEquals("native_result_unreadable:size=6",
            SieveReading.fromNative(longArrayOf(2, 5, 0, reference, 90, limit)).status)
        assertEquals("native_result_unreadable:size=-1", Sha256Reading.fromNative(null).status)
        assertEquals("native_result_unreadable:size=5",
            Sha256Reading.fromNative(longArrayOf(1, 4, 0, 5, 6)).status)
        assertNull(SieveReading.fromNative(longArrayOf(2, 6, 0)).primeCount)
        for (bad in listOf(null, longArrayOf(1, 5, 0, 664_579, 90), longArrayOf(2, 6, 0))) {
            assertNull("an unreadable array has no limit", SieveReading.fromNative(bad).limit)
        }
        assertNull("a throw has no limit", SieveReading.nativeCallFailed(IllegalStateException("x")).limit)
    }

    @Test
    fun `a native status this build does not know keeps the limit and the duration - and no count`() {
        val r = SieveReading.fromNative(sieveArray(status = 9, count = 5, ms = 7))
        assertEquals("native_status_unknown:9", r.status)
        assertNull(r.primeCount)
        assertEquals(7L, r.durationMs)
        assertEquals(limit, r.limit)
    }

    // ───────────────────────── the scan-level state ─────────────────────────

    @Test
    fun `the state is ran when any half reported - and says nothing about the answers`() {
        assertEquals("ran", CpuKnownAnswerState.of(listOf(run(sieve = "mismatch", sha = "mismatch")), true))
        assertEquals("ran", CpuKnownAnswerState.of(
            listOf(run(sieve = "not_reached", count = null, sieveMs = null, sha = "match", t = 1)), true
        ))
    }

    @Test
    fun `the state names why nothing ran`() {
        assertEquals("not_run:phase_not_reached", CpuKnownAnswerState.of(emptyList(), true))
        assertEquals("not_run:ai_cpu_workload_path_not_selected", CpuKnownAnswerState.of(emptyList(), false))
        val unreached = run(t = null, sieve = "not_reached", count = null, sieveMs = null, sha = "not_reached", shaMs = null)
        assertEquals("not_run:no_check_reached", CpuKnownAnswerState.of(listOf(unreached, unreached), true))
        val failed = run(sieve = "native_call_failed:no lib", count = null, sieveMs = null, sha = "native_call_failed:no lib", shaMs = null)
        assertEquals("not_run:native_call_failed:no lib", CpuKnownAnswerState.of(listOf(failed), true))
    }

    @Test
    fun `the state is partial - time_budget when some half reported and some run was skipped by the budget`() {
        val skipped = CpuKnownAnswerNames.STATUS_SKIPPED_TIME_BUDGET
        fun skippedRun(pct: Int) = CpuKnownAnswerRun(phase = 3, plannedAtPct = pct, sieveStatus = skipped, sha256Status = skipped)
        assertEquals(
            "partial:time_budget",
            CpuKnownAnswerState.of(listOf(run(), skippedRun(60), skippedRun(92)), true)
        )
        assertEquals("an alloc_failed half is a half that reported", "partial:time_budget", CpuKnownAnswerState.of(
            listOf(run(sieve = "alloc_failed", count = null, sha = "not_reached", shaMs = null), skippedRun(60), skippedRun(92)), true
        ))
        assertEquals("nothing skipped: ran", "ran", CpuKnownAnswerState.of(listOf(run(), run(pct = 60), run(pct = 92)), true))
        assertEquals(
            "the partial value is the one literal of the contract",
            "partial:" + CpuKnownAnswerNames.REASON_TIME_BUDGET, "partial:time_budget"
        )
    }

    @Test
    fun `no half reported - not_run, skipped or not - the budget never makes a partial out of nothing`() {
        val skipped = CpuKnownAnswerNames.STATUS_SKIPPED_TIME_BUDGET
        fun skippedRun(pct: Int) = CpuKnownAnswerRun(phase = 3, plannedAtPct = pct, sieveStatus = skipped, sha256Status = skipped)
        val unreadable = run(sieve = "native_result_unreadable:size=5", count = null, sieveMs = null,
            sha = "native_result_unreadable:size=5", shaMs = null)
        assertEquals("not_run:no_check_reached", CpuKnownAnswerState.of(listOf(unreadable, skippedRun(60), skippedRun(92)), true))
        assertEquals("not_run:no_check_reached", CpuKnownAnswerState.of(listOf(skippedRun(25), skippedRun(60)), true))
        val failed = run(sieve = "native_call_failed:no lib", count = null, sieveMs = null, sha = "native_call_failed:no lib", shaMs = null)
        assertEquals("not_run:native_call_failed:no lib", CpuKnownAnswerState.of(listOf(failed, skippedRun(60), skippedRun(92)), true))
    }

    // ───────────────────────── the wire ─────────────────────────

    @Test
    fun `a skipped run goes on the wire with its two statuses and nothing else - and the partial state beside it`() {
        val skipped = CpuKnownAnswerNames.STATUS_SKIPPED_TIME_BUDGET
        val skippedRun = CpuKnownAnswerRun(phase = 3, plannedAtPct = 60, sieveStatus = skipped, sha256Status = skipped)
        assertEquals(
            listOf("phase", "plannedAtPct", "sieveStatus", "sha256Status"),
            skippedRun.toWireMap().keys.toList()
        )
        val vr = vrOf(
            VerificationSummary(
                cpuKnownAnswerState = "partial:time_budget",
                cpuKnownAnswerRuns = listOf(run(), skippedRun, skippedRun.copy(plannedAtPct = 92)),
                cpuKnownAnswerTotalMsByPhase = mapOf(3 to 140L)
            )
        )
        assertEquals("partial:time_budget", vr.get(CpuKnownAnswerNames.STATE).asString)
        val arr = vr.getAsJsonArray(CpuKnownAnswerNames.RUNS)
        assertEquals(3, arr.size())
        for (i in 1..2) {
            val el = arr[i].asJsonObject
            assertEquals("skipped:time_budget", el.get(CpuKnownAnswerNames.SIEVE_STATUS).asString)
            assertEquals("skipped:time_budget", el.get(CpuKnownAnswerNames.SHA256_STATUS).asString)
            assertEquals(
                "a run that never ran has no time, no end, no count, no duration and no limit - never a 0",
                setOf("phase", "plannedAtPct", "sieveStatus", "sha256Status"), el.keySet()
            )
        }
        assertEquals("the run that ran still carries its limit", limit, arr[0].asJsonObject.get(CpuKnownAnswerNames.SIEVE_LIMIT).asLong)
    }

    @Test
    fun `a default summary says nothing ran and has no runs key`() {
        val v = VerificationSummary()
        assertEquals("not_run:no_v2_result", v.cpuKnownAnswerState)
        assertNull(v.cpuKnownAnswerRuns)
        val vr = vrOf(v)
        assertEquals("not_run:no_v2_result", vr.get(CpuKnownAnswerNames.STATE).asString)
        assertFalse(vr.has(CpuKnownAnswerNames.RUNS))
    }

    @Test
    fun `the runs go out with the raw count, and only the keys that have a value`() {
        val runs = listOf(
            run(pct = 25, t = 28_750),
            run(pct = 60, t = null, sieve = "not_reached", count = null, sieveMs = null, sha = "not_reached", shaMs = null),
            run(pct = 92, t = 79_000, sieve = "mismatch", count = reference - 1, sha = "self_test_failed", shaMs = 4)
        )
        val vr = vrOf(VerificationSummary(cpuKnownAnswerState = "ran", cpuKnownAnswerRuns = runs))
        assertEquals("ran", vr.get(CpuKnownAnswerNames.STATE).asString)
        val arr = vr.getAsJsonArray(CpuKnownAnswerNames.RUNS)
        assertEquals(3, arr.size())

        val a = arr[0].asJsonObject
        assertEquals(3, a.get(CpuKnownAnswerNames.PHASE).asInt)
        assertEquals(25, a.get(CpuKnownAnswerNames.PLANNED_AT_PCT).asInt)
        assertEquals(28_750L, a.get(CpuKnownAnswerNames.T).asLong)
        assertEquals("the run's end, same frame, ms", 28_890L, a.get(CpuKnownAnswerNames.T_END).asLong)
        assertEquals("match", a.get(CpuKnownAnswerNames.SIEVE_STATUS).asString)
        assertEquals(reference, a.get(CpuKnownAnswerNames.SIEVE_PRIME_COUNT).asLong)
        assertEquals(90L, a.get(CpuKnownAnswerNames.SIEVE_DURATION_MS).asLong)
        assertEquals("the N the sieve ran to, raw (fv70)", limit, a.get(CpuKnownAnswerNames.SIEVE_LIMIT).asLong)
        assertEquals("match", a.get(CpuKnownAnswerNames.SHA256_STATUS).asString)
        assertEquals(30L, a.get(CpuKnownAnswerNames.SHA256_DURATION_MS).asLong)

        val unreached = arr[1].asJsonObject
        assertEquals("not_reached", unreached.get(CpuKnownAnswerNames.SIEVE_STATUS).asString)
        assertEquals("not_reached", unreached.get(CpuKnownAnswerNames.SHA256_STATUS).asString)
        for (key in listOf(
            CpuKnownAnswerNames.T, CpuKnownAnswerNames.T_END, CpuKnownAnswerNames.SIEVE_PRIME_COUNT,
            CpuKnownAnswerNames.SIEVE_DURATION_MS, CpuKnownAnswerNames.SIEVE_LIMIT, CpuKnownAnswerNames.SHA256_DURATION_MS
        )) {
            assertFalse("$key must be absent for a check that never began", unreached.has(key))
        }

        val bad = arr[2].asJsonObject
        assertEquals("a wrong count travels as the count", reference - 1, bad.get(CpuKnownAnswerNames.SIEVE_PRIME_COUNT).asLong)
        assertEquals("a wrong count still says what it was counted to", limit, bad.get(CpuKnownAnswerNames.SIEVE_LIMIT).asLong)
        assertEquals("self_test_failed", bad.get(CpuKnownAnswerNames.SHA256_STATUS).asString)
    }

    @Test
    fun `the old names are never written and no aggregate verdict exists`() {
        val vr = vrOf(
            VerificationSummary(cpuKnownAnswerState = "ran", cpuKnownAnswerRuns = listOf(run(), run(pct = 60), run(pct = 92)))
        )
        for (old in listOf("cpuPrimeSieveCorrect", "cpuFmaErrors", "cpuFmaIterationsTotal", "cpuShaHashesVerified")) {
            assertFalse("$old is read by the server with ANOTHER meaning; it must not come back here", vr.has(old))
        }
        assertFalse("no aggregate boolean, no passed", vr.keySet().any {
            it.startsWith("cpuKnownAnswer") && it != CpuKnownAnswerNames.STATE && it != CpuKnownAnswerNames.RUNS
        })
        assertFalse(vr.keySet().any { it.contains("Passed", ignoreCase = true) && it.startsWith("cpu") })
        // none of the run elements carries a boolean
        for (el in vr.getAsJsonArray(CpuKnownAnswerNames.RUNS)) {
            assertTrue(el.asJsonObject.entrySet().none { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isBoolean })
        }
    }

    @Test
    fun `the names are one source - the element keys match what the wire shows`() {
        val keys = run().toWireMap().keys
        assertEquals(
            setOf(
                CpuKnownAnswerNames.PHASE, CpuKnownAnswerNames.PLANNED_AT_PCT, CpuKnownAnswerNames.T,
                CpuKnownAnswerNames.T_END, CpuKnownAnswerNames.SIEVE_STATUS, CpuKnownAnswerNames.SIEVE_PRIME_COUNT,
                CpuKnownAnswerNames.SIEVE_DURATION_MS, CpuKnownAnswerNames.SIEVE_LIMIT, CpuKnownAnswerNames.SHA256_STATUS,
                CpuKnownAnswerNames.SHA256_DURATION_MS
            ),
            keys
        )
        assertEquals(
            listOf(
                "phase", "plannedAtPct", "t", "tEnd", "sieveStatus", "sievePrimeCount", "sieveDurationMs", "sieveLimit",
                "sha256Status", "sha256DurationMs"
            ),
            keys.toList()
        )
    }

    // ───────────────────────── D's time per phase (part 2-c) ─────────────────────────

    @Test
    fun `the per-phase total is the sum of tEnd minus t over the runs that have both`() {
        val runs = listOf(
            run(phase = 3, pct = 25, t = 28_750, tEnd = 28_890),   // 140
            run(phase = 3, pct = 60, t = 55_000, tEnd = 55_150),   // 150
            run(phase = 3, pct = 92, t = 79_000, tEnd = 79_120),   // 120
            run(phase = 5, pct = 25, t = 150_000, tEnd = 150_200), // 200
        )
        assertEquals(mapOf(3 to 410L, 5 to 200L), CpuKnownAnswerTotals.byPhase(runs))
    }

    @Test
    fun `a run that never began adds nothing - and a phase with none has no entry, not zero`() {
        val unreached = run(phase = 5, t = null, tEnd = null, sieve = "not_reached", count = null, sieveMs = null,
            sha = "not_reached", shaMs = null)
        val totals = CpuKnownAnswerTotals.byPhase(
            listOf(run(phase = 3, t = 1_000, tEnd = 1_100), unreached, unreached, unreached)
        )
        assertEquals(mapOf(3 to 100L), totals)
        assertFalse("phase 5 had no run that began: absent, not 0", totals.containsKey(5))
        assertEquals(emptyMap<Int, Long>(), CpuKnownAnswerTotals.byPhase(emptyList()))
    }

    @Test
    fun `a run cut by the phase counts from its start to where it stopped`() {
        // started, the sieve answered, the SHA was cancelled: it has both ends
        val cut = run(phase = 3, t = 10_000, tEnd = 10_090, sha = "cancelled", shaMs = null)
        assertEquals(mapOf(3 to 90L), CpuKnownAnswerTotals.byPhase(listOf(cut)))
    }

    @Test
    fun `the per-phase total goes on the wire with string phase keys - and is declared a transitional aggregate`() {
        val vr = vrOf(
            VerificationSummary(
                cpuKnownAnswerState = "ran",
                cpuKnownAnswerRuns = listOf(run()),
                cpuKnownAnswerTotalMsByPhase = mapOf(3 to 410L, 5 to 200L)
            )
        )
        val totals = vr.getAsJsonObject(CpuKnownAnswerNames.TOTAL_MS_BY_PHASE)
        assertEquals(410L, totals.get("3").asLong)
        assertEquals(200L, totals.get("5").asLong)
        assertTrue(
            "verificationResults.${CpuKnownAnswerNames.TOTAL_MS_BY_PHASE}" in
                net.neuralcheck.data.local.files.TransitionalAggregates.NAMES
        )
    }

    @Test
    fun `no per-phase total key when no run began`() {
        val vr = vrOf(VerificationSummary(cpuKnownAnswerState = "not_run:no_check_reached"))
        assertFalse(vr.has(CpuKnownAnswerNames.TOTAL_MS_BY_PHASE))
    }

    @Test
    fun `the acceptance verdict is not computed in the app - no percentage, no passed key`() {
        val vr = vrOf(
            VerificationSummary(cpuKnownAnswerState = "ran", cpuKnownAnswerRuns = listOf(run()),
                cpuKnownAnswerTotalMsByPhase = mapOf(3 to 410L))
        )
        assertFalse(vr.keySet().any { it.contains("Pct", ignoreCase = true) || it.contains("accept", ignoreCase = true) })
    }
}
