package net.neuralcheck.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Order 2026-10-14 (user decision, device check 03 round 2, «Record the session at scan start»): what can be pinned
 * about the call sites from the source text — the technique `EvalCardLogSourceShapeTest` and
 * `ScanSessionHolderWriterSitesTest` use (comments removed, nothing run).
 *
 * ── القاعدة ٣٧ — what this exercises, said once ──
 * `StressTestService` (an Android service) cannot run on the JVM here, so the moment it records the session is pinned
 * as TEXT: one call, in `startScan`, right after the scan's id is minted and before anything else uses the scan. The
 * engine's call is ALSO executed, by `AiScoringEngineSessionTest`; the view model's call is executed by
 * `ScanSessionAtScanStartRealPathTest`. This file adds what those cannot see: that no OTHER reader of the holder
 * remains on the two paths that decide a scan's session, and that the set of callers of the record is exactly the
 * three the order names. A new caller, or the old holder read creeping back, fails here and makes whoever adds it
 * read the order.
 */
class ScanSessionAtScanStartSourceShapeTest {

    private val mainSourceRoot: File = listOf(
        "app/src/main/java",
        "../app/src/main/java"
    ).map(::File).firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private val sources: Map<String, String> by lazy {
        mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(mainSourceRoot).invariantSeparatorsPath to stripComments(it.readText()) }
    }

    private fun source(relative: String): String =
        sources[relative] ?: error("expected to find $relative under ${mainSourceRoot.path}")

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private val servicePath = "net/neuralcheck/service/stress/StressTestService.kt"
    private val enginePath = "net/neuralcheck/domain/scoring/AiScoringEngine.kt"
    private val viewModelPath = "net/neuralcheck/presentation/viewmodel/TechnicianEvaluationViewModel.kt"

    @Test
    fun `the guard can see the sources it is meant to protect`() {
        assertTrue("expected the main sources, found ${sources.size}", sources.size > 50)
        assertTrue(sources.containsKey("net/neuralcheck/data/remote/ScanSessionRecords.kt"))
    }

    // ---- who writes the record ----

    @Test
    fun `the record is written from one place - StressTestService startScan, right after the scan id is minted`() {
        val callers = sources.filter { (path, text) ->
            path != "net/neuralcheck/data/remote/ScanSessionRecords.kt" && text.contains("recordAtScanStart(")
        }.keys
        assertEquals(setOf(servicePath), callers)

        val text = squash(source(servicePath))
        assertEquals(1, Regex(Regex.escape("scanSessionRecords.recordAtScanStart(scanId)")).findAll(text).count())

        val minted = text.indexOf("val scanId = UUID.randomUUID().toString() currentScanId = scanId")
        val recorded = text.indexOf("runCatching { scanSessionRecords.recordAtScanStart(scanId) }")
        val firstUse = text.indexOf("collectedResults.clear()", minted)
        assertTrue("found where the id is minted", minted >= 0)
        assertTrue("the record call follows it", recorded > minted)
        assertTrue(
            "and comes before the scan is set up any further (the first statement after the type/link assignments)",
            firstUse > recorded
        )
        assertTrue(
            "a failure to record is logged and never stops the scan",
            text.contains(".onFailure { Log.w(TAG, \"scan session record failed: \${it.javaClass.name}\") }")
        )
    }

    // ---- the analysis ----

    @Test
    fun `the engine takes the analysis session from the scan's record, passes exactly that to analyzeDevice, and reads the holder once - to decide whether to empty it`() {
        val text = squash(source(enginePath))

        assertEquals(
            "the analysis session is the scan's record",
            1,
            Regex(Regex.escape("val sessionId = scanSessionRecords.sessionFor( scanReport.scanId, net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS )?.sessionId")).findAll(text).count()
        )
        assertEquals(
            "analyzeDevice gets that variable",
            1, Regex(Regex.escape("sessionId = sessionId )")).findAll(text).count()
        )
        assertEquals(
            "the holder is read in one place only: the decision to empty it, after the analysis",
            1, Regex(Regex.escape("scanSessionHolder.get()")).findAll(text).count()
        )
        assertTrue(
            "and that one read is the argument of the clear decision",
            text.contains("if (shouldClearSessionAfterAnalysis(sessionId, scanSessionHolder.get(), it)) {")
        )
        assertTrue(
            "the record is removed after a success",
            text.contains("if (shouldConsumeRecordAfterAnalysis(sessionId, it)) { scanSessionRecords.consume(scanReport.scanId) }")
        )
    }

    // ---- the draft ----

    @Test
    fun `the view model's draft takes its session from the scan's record - the construction-time snapshot only gates the card`() {
        val text = squash(source(viewModelPath))

        assertEquals(
            1, Regex(Regex.escape("val recorded = scanSessionRecords.sessionFor(scanId, ScanSessionRecords.Use.DRAFT)")).findAll(text).count()
        )
        assertTrue("the draft is built from the recorded id", text.contains("val sessionId = recorded.sessionId"))
        assertEquals(
            "the snapshot's session id is printed by two lines (view model built, dismissed) and used for nothing else",
            2, Regex(Regex.escape("sessionSnapshot?.sessionId")).findAll(text).count()
        )
    }

    // ---- the callers of the lookup ----

    @Test
    fun `the lookup has exactly five callers - the engine for the analysis, the view model for the draft, the leaver, the watch and the report's first open`() {
        // Batch 2, item 2, part c (replaces "exactly two callers", rule 23): SessionLeaver asks for the session a scan started in to know which session a
        // leave from the scan screen cancels (Use.LEAVE), and CustomerSessionWatch asks for the session whose document the scan screen follows (Use.WATCH).
        // Both ask for the scan's RECORDED session - never the holder's: that is what this test protects.
        // A-P6-08 (replaces "exactly four callers", rule 23): ReportViewModel.loadScan asks for the session the scan started in (Use.REPORT) to tell the server the report was shown for the first time,
        // read BEFORE the analysis spends the record - the record again, never the holder.
        val callers = sources.filter { (path, text) ->
            path != "net/neuralcheck/data/remote/ScanSessionRecords.kt" && text.contains(".sessionFor(")
        }.mapValues { (_, text) -> Regex("""Use\.(\w+)""").findAll(text).map { it.groupValues[1] }.toList() }

        assertEquals(
            mapOf(
                "net/neuralcheck/data/remote/CustomerSessionWatch.kt" to listOf("WATCH"),
                "net/neuralcheck/data/remote/SessionLeaver.kt" to listOf("LEAVE"),
                enginePath to listOf("ANALYSIS"),
                viewModelPath to listOf("DRAFT"),
                "net/neuralcheck/presentation/viewmodel/ViewModels.kt" to listOf("REPORT")
            ),
            callers
        )
    }
}
