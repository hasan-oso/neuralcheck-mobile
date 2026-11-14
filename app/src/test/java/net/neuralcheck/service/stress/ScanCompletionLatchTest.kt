package net.neuralcheck.service.stress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Order 2026-10-15 (batch item 3, "reset isCompleting"; "BackHandler in the scan screen").
 *
 * The defect: `StressTestService.isCompleting` was an AtomicBoolean set by the first `completeScan` and never reset. A
 * service instance that outlived a finished scan (its view model keeps it bound while the scan screen stays in the back
 * stack - on a customer device `popUpTo("home")` has no `home` entry to pop) and then started ANOTHER scan found the
 * latch taken: that scan's `completeScan` returned at once and persisted nothing, and `onDestroy` did not stop it.
 *
 * ── What this proves, and what it cannot (rule 30 / 37) ──
 *  - [ScanCompletionLatch] is the real class the service now uses; the tests below drive it: one winner per scan, the
 *    second caller loses, and a new scan starts with a free latch.
 *  - `StressTestService` and `ActiveScanScreen` cannot run on the JVM here (a Service; a composable, no Compose
 *    harness), so that `startScan` frees the latch, that `completeScan` takes it, and that Back opens the stop dialog
 *    are pinned as TEXT (comments removed), the technique `ScanSessionAtScanStartSourceShapeTest` uses. A source scan
 *    proves the calls are there and in order; it does not prove a second scan on a live service now completes - that is
 *    a device check.
 *
 * Rule 23: no earlier test pinned the latch (`git grep -n isCompleting -- app/src/test` was empty), so nothing is replaced.
 */
class ScanCompletionLatchTest {

    // ---- the latch ----

    @Test
    fun `a fresh latch is free, the first caller wins, every later caller loses`() {
        val latch = ScanCompletionLatch()
        assertFalse(latch.isCompleting)

        assertTrue("the first completeScan begins", latch.tryBegin())
        assertTrue(latch.isCompleting)
        assertFalse("stopScan / onDestroy / the safety stop find it taken", latch.tryBegin())
        assertFalse(latch.tryBegin())
        assertTrue(latch.isCompleting)
    }

    @Test
    fun `a new scan starts with a free latch - the second scan of a service that lived on can finish`() {
        val latch = ScanCompletionLatch()
        assertTrue("scan 1 finalises", latch.tryBegin())
        assertFalse("while scan 1 is finalising, nobody else may", latch.tryBegin())

        // the defect, shown on the real class: without a reset the second scan's completeScan is refused for good
        assertFalse("without the reset scan 2 would never finalise", latch.tryBegin())

        latch.resetForNewScan()
        assertFalse("scan 2 starts: nothing is finalising", latch.isCompleting)
        assertTrue("scan 2 finalises once", latch.tryBegin())
        assertFalse("and only once", latch.tryBegin())
    }

    @Test
    fun `resetting a latch that was never taken changes nothing`() {
        val latch = ScanCompletionLatch()
        latch.resetForNewScan()
        assertFalse(latch.isCompleting)
        assertTrue(latch.tryBegin())
    }

    // ---- the call sites, as text ----

    private val mainSourceRoot: File = listOf("app/src/main/java", "../app/src/main/java")
        .map(::File).firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun source(relative: String): String =
        squash(stripComments(File(mainSourceRoot, relative).readText()))

    @Test
    fun `the service holds the latch class, not a bare AtomicBoolean - startScan frees it before the id is minted, completeScan takes it, onDestroy reads it`() {
        val text = source("net/neuralcheck/service/stress/StressTestService.kt")

        assertFalse("no bare isCompleting flag is left", text.contains("isCompleting.compareAndSet") || text.contains("private val isCompleting"))
        assertEquals(1, Regex(Regex.escape("private val completionLatch = ScanCompletionLatch()")).findAll(text).count())

        // completeScan: the idempotency guard is the latch's tryBegin, and it is the first thing the function does
        val complete = text.indexOf("private fun completeScan(")
        val begin = text.indexOf("if (!completionLatch.tryBegin()) return", complete)
        val firstWork = text.indexOf("sampler.stop()", complete)
        assertTrue("found completeScan", complete >= 0)
        assertTrue("the guard is the latch", begin > complete)
        assertTrue("and it comes before the work", firstWork > begin)

        // onDestroy: stops a scan only when none is finalising
        assertTrue(text.contains("if (!completionLatch.isCompleting && currentScanId != null) {"))

        // startScan: freed once, after the early-return gates (power save), right before the id is minted
        assertEquals(1, Regex(Regex.escape("completionLatch.resetForNewScan()")).findAll(text).count())
        val powerSave = text.indexOf("SafetyStopped(\"POWER_SAVE_MODE\")")
        val reset = text.indexOf("completionLatch.resetForNewScan()")
        val minted = text.indexOf("val scanId = UUID.randomUUID().toString()")
        assertTrue("found the power-save gate and the mint", powerSave >= 0 && minted >= 0)
        assertTrue("the reset is after the last early return of startScan", reset > powerSave)
        assertTrue("and before the scan id exists", reset in (powerSave + 1) until minted)
    }

    @Test
    fun `Back opens the SAME stop dialog the button opens - only while a scan is Running - and the dialog is unchanged`() {
        val text = source("net/neuralcheck/presentation/screen/ActiveScanScreen.kt")

        assertEquals("one BackHandler", 1, Regex("""\bBackHandler\(""").findAll(text).count())
        assertTrue(text.contains("import androidx.activity.compose.BackHandler"))
        assertTrue(
            "enabled only while Running, and it only opens the dialog",
            text.contains(
                "BackHandler( enabled = scanState is net.neuralcheck.presentation.viewmodel.ActiveScanViewModel.ScanUiState.Running ) { showStopDialog = true }"
            )
        )
        // the button and Back reach the dialog the same way: one flag
        assertTrue(text.contains("onClick = { showStopDialog = true }"))
        // the dialog: the existing text and the existing exit (stop, then leave) - not changed by this item
        assertTrue(text.contains("title = { Text(stringResource(R.string.scan_cancel_confirm)) }"))
        assertTrue(text.contains("showStopDialog = false viewModel.stopScan() navController.popBackStack()"))
    }
}
