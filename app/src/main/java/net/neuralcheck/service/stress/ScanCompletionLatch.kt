package net.neuralcheck.service.stress

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one-shot latch of "this scan is being finalised" — what `StressTestService.completeScan` takes so that a
 * second caller (`stopScan`, `onDestroy`, the safety stop) cannot finalise the same scan twice.
 *
 * Order 2026-10-15 (batch item 3, "reset isCompleting"): the latch used to be an `AtomicBoolean` that was set by the
 * first `completeScan` and NEVER reset. A service instance that lives on after a scan (the screen's view model keeps
 * it bound while its navigation entry stays in the back stack — on a customer device, where `popUpTo("home")` finds
 * no `home` entry to pop, the scan screen stays) and then starts ANOTHER scan found the latch already taken, so
 * that scan's `completeScan` returned at once: it never persisted, never reached the report, and `onDestroy` did not
 * stop it either (`isCompleting` was true). A new scan starts with a free latch: [resetForNewScan], called by
 * `startScan` once it has decided to start (after its early-return gates, before the scan id is minted).
 *
 * Pure Kotlin, so a test can drive it; that `startScan` calls [resetForNewScan] and `completeScan` calls [tryBegin]
 * is pinned as text by `ScanCompletionLatchTest` (a Service cannot run on the JVM here).
 */
internal class ScanCompletionLatch {

    private val completing = AtomicBoolean(false)

    /** True for exactly ONE caller per scan: the one that begins finalising it. Every later caller gets false. */
    fun tryBegin(): Boolean = completing.compareAndSet(false, true)

    /** Whether a finalisation has begun for the current scan. */
    val isCompleting: Boolean get() = completing.get()

    /** A new scan starts with a free latch. */
    fun resetForNewScan() {
        completing.set(false)
    }
}
