package net.neuralcheck.scoring

import net.neuralcheck.domain.scoring.AnalysisProgress
import net.neuralcheck.domain.scoring.AnalysisStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A20 — the analysis wait runs ~3 minutes. The old overlay rotated five
 * messages on a 3-second timer with nothing behind them, so it showed
 * motion but never said WHERE in the wait the user was; a field log caught
 * 11 taps in 6 seconds against it.
 *
 * This holder reports the app's own steps. It deliberately does not model
 * a percentage: the app cannot know how far along the model is, and a bar
 * that creeps to 90% and stalls would be a worse lie than silence.
 */
class AnalysisProgressTest {

    @Test
    fun `starts idle with no start time`() {
        val p = AnalysisProgress()
        assertEquals(AnalysisStage.IDLE, p.stage.value)
        assertNull(p.startedAtMs)
    }

    @Test
    fun `begin enters the first real stage and stamps the clock`() {
        val p = AnalysisProgress()
        p.begin(nowMs = 1_000L)
        assertEquals(AnalysisStage.READING_MEASUREMENTS, p.stage.value)
        assertEquals(1_000L, p.startedAtMs)
    }

    @Test
    fun `stages advance in the engine's real order`() {
        val p = AnalysisProgress()
        p.begin(nowMs = 0L)
        val seen = mutableListOf(p.stage.value)
        listOf(
            AnalysisStage.PREPARING_PAYLOAD,
            AnalysisStage.CHECKING_CACHE,
            AnalysisStage.ANALYSING,
            AnalysisStage.BUILDING_REPORT
        ).forEach { p.set(it); seen += p.stage.value }

        assertEquals(
            listOf(
                AnalysisStage.READING_MEASUREMENTS,
                AnalysisStage.PREPARING_PAYLOAD,
                AnalysisStage.CHECKING_CACHE,
                AnalysisStage.ANALYSING,
                AnalysisStage.BUILDING_REPORT
            ),
            seen
        )
    }

    @Test
    fun `finish clears the clock so a stale elapsed counter cannot linger`() {
        val p = AnalysisProgress()
        p.begin(nowMs = 5_000L)
        assertNotNull(p.startedAtMs)
        p.finish()
        assertEquals(AnalysisStage.DONE, p.stage.value)
        assertNull("a leftover start time would keep the counter ticking", p.startedAtMs)
    }

    @Test
    fun `finish is what runs on the failure path too`() {
        // The engine calls finish() from the .also{} that covers success
        // AND failure. A spinner left running after an error is the same
        // silence this feature exists to end.
        val p = AnalysisProgress()
        p.begin(nowMs = 1L)
        p.set(AnalysisStage.ANALYSING)
        p.finish()   // as if analyzeDevice threw
        assertEquals(AnalysisStage.DONE, p.stage.value)
        assertNull(p.startedAtMs)
    }

    @Test
    fun `a second run restarts cleanly after the first finished`() {
        val p = AnalysisProgress()
        p.begin(nowMs = 1_000L); p.finish()
        p.begin(nowMs = 9_000L)
        assertEquals(AnalysisStage.READING_MEASUREMENTS, p.stage.value)
        assertEquals("the clock must restart, not resume", 9_000L, p.startedAtMs)
    }

    @Test
    fun `no stage implies a percentage`() {
        // Locks the design decision: coarse honest stages, never a number
        // the app would have to invent.
        assertEquals(7, AnalysisStage.entries.size)
        assertEquals(AnalysisStage.IDLE, AnalysisStage.entries.first())
        assertEquals(AnalysisStage.DONE, AnalysisStage.entries.last())
    }
}
