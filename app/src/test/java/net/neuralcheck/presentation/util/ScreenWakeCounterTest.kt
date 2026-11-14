package net.neuralcheck.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * fix/keep-screen-on — the reference count behind FLAG_KEEP_SCREEN_ON.
 *
 * The bug this guards: the flag lives on the shared activity window while
 * add/clear calls live in per-screen effects. During a Compose Navigation
 * transition the INCOMING screen composes before the OUTGOING one is
 * disposed, so a naive add/clear pair runs
 *
 *     ManualTests.acquire()  →  ActiveScan.release()
 *
 * and the screen sleeps on the screen that just asked to stay awake —
 * exactly the reported symptom (screen dying during the questions).
 *
 * Window is null throughout: these tests exercise the counting contract,
 * which is what decides whether clearFlags is called at all.
 */
class ScreenWakeCounterTest {

    @Before fun reset() = ScreenWakeCounter.resetForTest()

    @Test
    fun `single holder acquires and releases`() {
        ScreenWakeCounter.acquire(null)
        assertEquals(1, ScreenWakeCounter.holderCount())
        ScreenWakeCounter.release(null)
        assertEquals(0, ScreenWakeCounter.holderCount())
    }

    @Test
    fun `overlapping handover never drops to zero`() {
        // ActiveScan holds it.
        ScreenWakeCounter.acquire(null)
        // Navigation: ManualTests composes BEFORE ActiveScan is disposed.
        ScreenWakeCounter.acquire(null)
        assertEquals(2, ScreenWakeCounter.holderCount())
        // ActiveScan finally disposes.
        ScreenWakeCounter.release(null)
        assertEquals("the flag must survive the handover",
            1, ScreenWakeCounter.holderCount())
    }

    @Test
    fun `full scan flow keeps the screen awake end to end`() {
        // active_scan -> manual_tests -> report(loading) -> report(ready)
        ScreenWakeCounter.acquire(null)            // ActiveScan composes
        ScreenWakeCounter.acquire(null)            // ManualTests composes
        ScreenWakeCounter.release(null)            // ActiveScan disposes
        assertEquals(1, ScreenWakeCounter.holderCount())
        ScreenWakeCounter.acquire(null)            // Report composes (loading)
        ScreenWakeCounter.release(null)            // ManualTests disposes
        assertEquals("still held while the analysis runs",
            1, ScreenWakeCounter.holderCount())
        ScreenWakeCounter.release(null)            // analysis done -> active=false
        assertEquals("released once the report is on screen",
            0, ScreenWakeCounter.holderCount())
    }

    @Test
    fun `early exit from any screen releases the flag`() {
        // Cancel / error / back out of active_scan before anything else.
        ScreenWakeCounter.acquire(null)
        ScreenWakeCounter.release(null)
        assertEquals("no holder may be left dangling on an abnormal exit",
            0, ScreenWakeCounter.holderCount())
    }

    @Test
    fun `unbalanced release cannot drive the count negative`() {
        // An acquire recorded against a previous window (activity
        // recreation) can leave a release without its pair. Going negative
        // would make the next release skip clearFlags and pin the screen on
        // for the rest of the process.
        ScreenWakeCounter.release(null)
        ScreenWakeCounter.release(null)
        assertEquals(0, ScreenWakeCounter.holderCount())
        ScreenWakeCounter.acquire(null)
        assertEquals(1, ScreenWakeCounter.holderCount())
        ScreenWakeCounter.release(null)
        assertEquals("a later balanced pair must still reach zero",
            0, ScreenWakeCounter.holderCount())
    }

    @Test
    fun `report screen holding nothing when analysis is already done`() {
        // KeepScreenOn(active = false) acquires nothing at all.
        assertEquals(0, ScreenWakeCounter.holderCount())
    }
}
