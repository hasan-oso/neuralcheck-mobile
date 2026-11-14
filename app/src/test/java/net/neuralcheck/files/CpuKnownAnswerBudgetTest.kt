package net.neuralcheck.files

import net.neuralcheck.data.local.files.CpuKnownAnswerBudget
import net.neuralcheck.data.local.files.CpuKnownAnswerNames
import net.neuralcheck.data.local.files.CpuKnownAnswerRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * fieldsVersion 70, contract section 7-t (b): the time-budget rule as a pure function, on exact numbers.
 * The scheduler's own tests (CpuKnownAnswerSchedulerTest) run it through the real coroutine on virtual time;
 * this file pins the arithmetic and the cases the scheduler's loop cannot reach (a run with no `tEnd`).
 *
 * The rule (the user's, 2026-10-13): before each run after the first, if
 * (the time D has used in the phase + its longest run) is above 1 % of the phase's PLANNED duration, the run
 * and the rest of the phase are skipped. Equal is not skipped.
 */
class CpuKnownAnswerBudgetTest {

    private val phase3 = 75_000L   // the planned duration of phase 3 (V2Phase.CPU_SUSTAINED)

    /** A run that began at [t] and ended after [took] ms. */
    private fun ran(t: Long, took: Long, pct: Int = 25) =
        CpuKnownAnswerRun(phase = 3, plannedAtPct = pct, t = t, tEnd = t + took)

    private val neverBegan = CpuKnownAnswerRun(phase = 3, plannedAtPct = 60)

    @Test
    fun `the percent is the user's 1`() {
        assertEquals(1, CpuKnownAnswerNames.BUDGET_PERCENT_OF_PHASE)
    }

    @Test
    fun `with no run before it nothing is exceeded - the first run always runs, whatever the phase length`() {
        assertFalse(CpuKnownAnswerBudget.exceeded(emptyList(), nowMs = 18_750, phaseDurationMs = phase3))
        assertFalse(CpuKnownAnswerBudget.exceeded(emptyList(), nowMs = 0, phaseDurationMs = 0))
        assertFalse("runs that never began add nothing", CpuKnownAnswerBudget.exceeded(listOf(neverBegan, neverBegan), 1_000, phaseDurationMs = 1))
    }

    @Test
    fun `equal to the limit is not exceeded - one ms more is`() {
        // limit = 1 % of 75 000 = 750; one earlier run of d: (d + d) must be <= 750
        assertFalse(CpuKnownAnswerBudget.exceeded(listOf(ran(18_750, 375)), 45_000, phase3))
        assertTrue(CpuKnownAnswerBudget.exceeded(listOf(ran(18_750, 376)), 45_000, phase3))
        // two earlier runs of 250 each: (500 + 250) = 750 -> not exceeded; 251 each: (502 + 251) = 753 -> exceeded
        assertFalse(CpuKnownAnswerBudget.exceeded(listOf(ran(18_750, 250), ran(45_000, 250, 60)), 69_000, phase3))
        assertTrue(CpuKnownAnswerBudget.exceeded(listOf(ran(18_750, 251), ran(45_000, 251, 60)), 69_000, phase3))
    }

    @Test
    fun `it is the sum plus the LONGEST, not plus the last`() {
        // 350 then 100: (450 + 350) = 800 > 750 (with the last it would be 550)
        assertTrue(CpuKnownAnswerBudget.exceeded(listOf(ran(18_750, 350), ran(45_000, 100, 60)), 69_000, phase3))
        // 100 then 350: the same sum and the same longest
        assertTrue(CpuKnownAnswerBudget.exceeded(listOf(ran(18_750, 100), ran(45_000, 350, 60)), 69_000, phase3))
        // 100 then 300: (400 + 300) = 700 <= 750
        assertFalse(CpuKnownAnswerBudget.exceeded(listOf(ran(18_750, 100), ran(45_000, 300, 60)), 69_000, phase3))
    }

    @Test
    fun `the limit follows the phase's own planned duration`() {
        val phase5 = 60_000L   // V2Phase.COMBINED_PEAK: 1 % = 600
        assertFalse(CpuKnownAnswerBudget.exceeded(listOf(ran(15_000, 300)), 36_000, phase5))
        assertTrue(CpuKnownAnswerBudget.exceeded(listOf(ran(15_000, 301)), 36_000, phase5))
    }

    @Test
    fun `a run that never began adds nothing to the sum or to the longest`() {
        // 375 alone is exactly at the limit; an unbegun run beside it must not change that ...
        assertFalse(CpuKnownAnswerBudget.exceeded(listOf(ran(18_750, 375), neverBegan), 45_000, phase3))
        assertFalse(CpuKnownAnswerBudget.exceeded(listOf(neverBegan, ran(18_750, 375)), 45_000, phase3))
        // ... and 376 is over with or without it
        assertTrue(CpuKnownAnswerBudget.exceeded(listOf(ran(18_750, 376), neverBegan), 45_000, phase3))
    }

    @Test
    fun `a run with a start and no end counts what it has used until now - never zero (rule 22)`() {
        // began at 18 750 with no tEnd; "now" is 19 125 -> 375 ms so far: (375 + 375) = 750 -> not exceeded ...
        val open = CpuKnownAnswerRun(phase = 3, plannedAtPct = 25, t = 18_750, tEnd = null)
        assertFalse(CpuKnownAnswerBudget.exceeded(listOf(open), nowMs = 19_125, phaseDurationMs = phase3))
        // ... and one ms later it is 376 -> exceeded. Read as 0 it would never be exceeded.
        assertTrue(CpuKnownAnswerBudget.exceeded(listOf(open), nowMs = 19_126, phaseDurationMs = phase3))
        assertTrue("a long-open run is plainly over", CpuKnownAnswerBudget.exceeded(listOf(open), nowMs = 40_000, phaseDurationMs = phase3))
    }

    @Test
    fun `the arithmetic is integer - no rounding at the boundary, no overflow at sizes a phase could not reach`() {
        // a phase of 1 ms: limit 0.01 ms -> any positive spent is over; a zero-length run is not
        assertTrue(CpuKnownAnswerBudget.exceeded(listOf(ran(0, 1)), 10, 1))
        assertFalse(CpuKnownAnswerBudget.exceeded(listOf(ran(0, 0)), 10, 1))
        // large values stay exact
        val big = 1_000_000_000_000L
        assertFalse(CpuKnownAnswerBudget.exceeded(listOf(ran(0, big / 100 / 2)), 0, big))
        assertTrue(CpuKnownAnswerBudget.exceeded(listOf(ran(0, big / 100 / 2 + 1)), 0, big))
    }

    @Test
    fun `the scheduler asks this function and does not compute the rule itself - one source`() {
        val dir = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
            ?: error("cannot find app/src/main/java from ${File(".").absolutePath}")
        val scheduler = File(dir, "net/neuralcheck/service/stress/v2/CpuKnownAnswerScheduler.kt").readText(Charsets.UTF_8)
        assertTrue(scheduler.contains("CpuKnownAnswerBudget.exceeded("))
        // the percent constant is read by the budget object only; the scheduler holds no copy of the rule
        val code = scheduler.replace(Regex("/\\*[\\s\\S]*?\\*/"), "").replace(Regex("//[^\\n]*"), "")
        assertFalse(code.contains("BUDGET_PERCENT_OF_PHASE"))
        assertFalse(code.contains("* 100"))
    }
}
