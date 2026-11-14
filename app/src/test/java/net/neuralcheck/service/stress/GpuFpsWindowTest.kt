package net.neuralcheck.service.stress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 66 — sliding-window FPS contract ([FpsWindow]).
 *
 * Reproduces the two field devices from the 2026-09-13 comparison:
 *  • Honor MTN-NX1 (Adreno, SM6650): the render loop issues 30 draws,
 *    then blocks on the fence for ~250 ms while the GPU catches up. With a
 *    250 ms sampler window the ≤65 per-query rate alternated 0 / 120
 *    (120 = 30 frames ÷ 0.25 s). True throughput ≈ 60 fps.
 *  • Samsung S10 5G (Mali-G76): ~311 frames per 250 ms window — many
 *    fence batches per window, values already smooth (~1245 fps).
 */
class GpuFpsWindowTest {

    private companion object {
        const val Q_NS: Long = 250_000_000L   // 250 ms = 4 Hz GPU-phase sampling
        const val T0: Long = 1_000_000_000L   // arbitrary non-zero start
    }

    /** Feed [frames] per query at a fixed 250 ms cadence, return every output. */
    private fun run(window: FpsWindow, framesPerQuery: List<Int>): List<Float> {
        var now = T0
        val out = mutableListOf<Float>()
        // Priming query: establishes the first timestamp (returns 0 by contract).
        window.push(0, now)
        for (f in framesPerQuery) {
            now += Q_NS
            out += window.push(f, now)
        }
        return out
    }

    @Test
    fun `Honor alternating 30-frame bursts converge on ~60 fps, never 120 or 0`() {
        val w = FpsWindow()
        // 0,30,0,30,... for 12 windows (3 s) — exactly the field pattern.
        val pattern = List(12) { i -> if (i % 2 == 0) 0 else 30 }
        val out = run(w, pattern)
        // After the window is full (4 queries = 1 s) every value must be
        // the integrated rate: 60 frames per 1.0 s = 60 fps.
        val steady = out.drop(4)
        assertTrue("no steady-state samples", steady.isNotEmpty())
        for (v in steady) {
            assertEquals("windowed fps should integrate the bursts", 60f, v, 0.5f)
        }
        // The ≤65 failure mode must be gone: no zeros and no 120s once warm.
        assertTrue("alternation still present: $steady", steady.none { it == 0f || it >= 119f })
    }

    @Test
    fun `Honor pattern with the burst landing in the other parity also gives 60`() {
        val w = FpsWindow()
        val pattern = List(12) { i -> if (i % 2 == 0) 30 else 0 }
        val steady = run(w, pattern).drop(4)
        for (v in steady) assertEquals(60f, v, 0.5f)
    }

    @Test
    fun `Samsung smooth 311 frames per window stays smooth at ~1244`() {
        val w = FpsWindow()
        val out = run(w, List(12) { 311 })
        // Steady rate is 311 / 0.25 = 1244 — the window must not distort it.
        for (v in out.drop(1)) assertEquals(1244f, v, 0.5f)
    }

    @Test
    fun `window holds exactly the last four queries`() {
        val w = FpsWindow(maxQueries = 4)
        // Four windows of 40 frames, then four of 80: after the second block
        // the first must have fully aged out → 80/0.25 = 320.
        val out = run(w, List(4) { 40 } + List(4) { 80 })
        assertEquals(160f, out[3], 0.5f)   // 4×40 / 1 s
        assertEquals(320f, out[7], 0.5f)   // 4×80 / 1 s — no residue of the 40s
    }

    @Test
    fun `first query after reset returns zero and discards its frames`() {
        val w = FpsWindow()
        assertEquals(0f, w.push(999, T0), 0f)
        // Next query at +250 ms counts only its own frames.
        assertEquals(120f, w.push(30, T0 + Q_NS), 0.5f)
        w.reset()
        assertEquals(0f, w.push(999, T0 + 2 * Q_NS), 0f)
    }

    @Test
    fun `sub-10ms query gap is ignored`() {
        val w = FpsWindow()
        w.push(0, T0)
        assertEquals(0f, w.push(30, T0 + 5_000_000L), 0f)
    }

    @Test
    fun `implausible rates are filtered like the pre-66 band`() {
        val w = FpsWindow()
        w.push(0, T0)
        // 1_000_000 frames in 250 ms = 4 Mfps → outside [0.1, 3000] → 0.
        assertEquals(0f, w.push(1_000_000, T0 + Q_NS), 0f)
    }
}
