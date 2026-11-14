package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Order 2026-10-10 (user decision «Permanent named log lines»): what can be pinned about the lines from the source
 * text. A STATIC SOURCE SCAN (comments removed), the technique `DiagnosticLoggingGuardTest` uses — it runs nothing.
 * The lines' content and timing are proved by `EvalCardLogTest` and `EvalCardLogRealPathTest`; this file pins
 * the things a runtime test cannot see:
 *
 *  1. ONE tag literal in all of `app/src/main` (rule 24), in `EvalCardLog.kt`.
 *  2. WHERE the lines are called from — an allowlist of three files. None is a composable: the card composable
 *     (measurement-safety contract: "NO LaunchedEffect/timer here", recomposes only on a tap) has no line, and the
 *     scan screen has no `EvalCardLog` call, only the view model's `logHandoffToManualTests` in its existing
 *     scan-completed handler — once, before it navigates.
 *  3. Every `ScanSessionHolder.clear` names its reason, and each of the two callers passes its own.
 *  4. `EvalCardLog` prints through exactly one `Log.i` and never an `Enum.name` or a stack trace (R8 renames classes
 *     in the release build, so those would print letters).
 *  5. R8: nothing in the proguard setup strips `android.util.Log`. `app/proguard-rules.pro` has no
 *     `assumenosideeffects` and does not mention `Log`, and `app/build.gradle.kts` names no other proguard file than
 *     the default optimize file and that one. The default file's own content is not in this repository: the
 *     report that accompanies this test reads the merged `configuration.txt` of the 1.0.11 build (no `Log` rule)
 *     — that is a reading, not something this test can re-run without a build.
 *
 * `DiagnosticLoggingGuardTest` stays as it was and still forbids its three temporary tags; this file adds
 * nothing to its list and `EvalCardLog` uses none of them.
 */
class EvalCardLogSourceShapeTest {

    private val projectRoot: File = listOf(".", "..")
        .map(::File)
        .firstOrNull { File(it, "app/src/main/java").isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private val mainSourceRoot = File(projectRoot, "app/src/main/java")

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

    private val evalCardLogPath = "net/neuralcheck/util/EvalCardLog.kt"

    // ---- 1. the tag ----

    @Test
    fun `the tag literal appears once in all of main, in EvalCardLog`() {
        val hits = sources.filter { it.value.contains("\"NeuralCheck.EvalCard\"") }.keys
        assertEquals(setOf(evalCardLogPath), hits)
        assertEquals(1, Regex(Regex.escape("\"NeuralCheck.EvalCard\"")).findAll(source(evalCardLogPath)).count())
    }

    // ---- 2. where the lines come from ----

    @Test
    fun `EvalCardLog is called from exactly these nineteen files, none a composable`() {
        // Order 2026-10-14 (replaces "exactly three files", rule 23): ScanSessionRecords prints the scan-session-*
        // lines (a plain class, not a composable) — the fourth caller.
        // Batch 2, item 2 (order 2026-10-15; replaces "exactly four files", rule 23): the session-* lines — SessionCanceller
        // (session-cancel-sent / session-cancel-answer: a plain class) and SessionMessages (session-refused: a plain function).
        // Batch 2, item 2, part c (replaces "exactly six files", rule 23): SessionLeaver (session-leave) and SessionDocWatcher
        // (session-watch-start / -event / -error / -stop) - both plain classes, neither a composable.
        // Batch 3, item 2 (replaces "exactly eight files", rule 23): OwnerSessionStatus.kt prints owner-cancelled-shown - a file of plain functions that the view model calls.
        // Batch 3, item 3 (replaces "exactly nine files", rule 23): JoinWithDeviceIdentity.kt prints session-join-failed - the one function both join entry points call.
        // A-SUB wave 1 (2026-10-17; replaces "exactly ten files", rule 23): AiScoringEngine.kt prints scan-refused from analysisErrorMessage - a plain function called from the engine's catch, not a composable.
        // A-SUB wave 1, item 5 (replaces "exactly eleven files", rule 23): PreScanViewModel.kt prints scan-gate decision=allowed-unverified from checkAccess's catch - a view model, not a composable.
        // A-P6-03 (2026-10-18; replaces "exactly twelve files", rule 23): SessionPurposeUi.kt prints the session-protocol-parts and session-purpose-* lines - plain functions the owner's view model calls, not a composable.
        // A-P6-08 (2026-10-18; replaces "exactly thirteen files", rule 23): TechnicianFormViewModel.kt prints the technician-form-* lines - a view model, not a composable.
        // A-P6-08 wire match (2026-10-18; replaces "exactly fourteen files", rule 23): ReportFirstOpenedRecorder.kt prints the report-first-opened line - a plain class both devices' call sites use, not a composable.
        // A-P6-07 (2026-10-21; replaces "exactly fifteen files", rule 23): RepeatScanClient.kt prints the repeat-scan line - a plain class both devices' repeat calls go through, not a composable.
        // A-P6-12 (2026-10-21; replaces "exactly sixteen files", rule 23): ScanConsentClient.kt prints the consent-record line - a plain class the consent screen's view model uses, not a composable.
        // Maintenance link (2026-10-22; replaces "exactly seventeen files", rule 23): PairLinkClient.kt prints the pair-link line - a plain class the owner's view model uses, not a composable.
        // Maintenance link, the list's state (2026-10-22; replaces "exactly eighteen files", rule 23): PairListUi.kt prints the pair-list line for a tap that was not applied - plain functions the owner's view model calls, not a composable.
        val callers = sources.filter { (path, text) ->
            path != evalCardLogPath && Regex("""\bEvalCardLog\b""").containsMatchIn(text)
        }.keys
        assertEquals(
            setOf(
                "net/neuralcheck/data/remote/FirebaseAnalysisService.kt",
                "net/neuralcheck/data/remote/JoinWithDeviceIdentity.kt",
                "net/neuralcheck/data/remote/PairLinkClient.kt",
                "net/neuralcheck/data/remote/ReportFirstOpenedRecorder.kt",
                "net/neuralcheck/data/remote/RepeatScanClient.kt",
                "net/neuralcheck/data/remote/ScanConsentClient.kt",
                "net/neuralcheck/data/remote/ScanSessionHolder.kt",
                "net/neuralcheck/data/remote/ScanSessionRecords.kt",
                "net/neuralcheck/data/remote/SessionCanceller.kt",
                "net/neuralcheck/data/remote/SessionDocWatcher.kt",
                "net/neuralcheck/data/remote/SessionLeaver.kt",
                "net/neuralcheck/domain/scoring/AiScoringEngine.kt",
                "net/neuralcheck/presentation/viewmodel/OwnerSessionStatus.kt",
                "net/neuralcheck/presentation/viewmodel/PairListUi.kt",
                "net/neuralcheck/presentation/viewmodel/PreScanViewModel.kt",
                "net/neuralcheck/presentation/viewmodel/SessionMessages.kt",
                "net/neuralcheck/presentation/viewmodel/SessionPurposeUi.kt",
                "net/neuralcheck/presentation/viewmodel/TechnicianFormViewModel.kt",
                "net/neuralcheck/presentation/viewmodel/TechnicianEvaluationViewModel.kt"
            ),
            callers
        )
    }

    @Test
    fun `the card composable has no log line`() {
        val card = source("net/neuralcheck/presentation/screen/TechnicianEvaluationCard.kt")
        assertFalse("no EvalCardLog in the card", Regex("""\bEvalCardLog\b""").containsMatchIn(card))
        assertFalse("no Log call in the card", Regex("""\bLog\.""").containsMatchIn(card))
    }

    @Test
    fun `the scan screen prints one line, through the view model, in its scan-completed handler before it navigates`() {
        val screen = source("net/neuralcheck/presentation/screen/ActiveScanScreen.kt")
        assertFalse("the screen does not call EvalCardLog itself", Regex("""\bEvalCardLog\b""").containsMatchIn(screen))

        val call = "evalViewModel.logHandoffToManualTests(state.scanId)"
        assertEquals("exactly one call", 1, Regex(Regex.escape(call)).findAll(screen).count())

        val completed = screen.indexOf("ScanUiState.Completed ->")
        val navigate = screen.indexOf("navController.navigate(\"manual_tests/")
        val at = screen.indexOf(call)
        assertTrue("found the handler", completed >= 0 && navigate > completed)
        assertTrue("the call is inside the Completed branch, before the navigation", at in (completed + 1) until navigate)
    }

    @Test
    fun `the read start line is the statement right before the Room read, and the done line is in the branch where it did not throw`() {
        // Order 2026-10-14 (diagnostic build). The timing is proved at run time by EvalCardLogRealPathTest (a read that
        // has started and not returned); this pins the placement as text: one start line, one read, one done line, in
        // that order, with the throw branch printing the read-failed line instead.
        val text = squash(source("net/neuralcheck/presentation/viewmodel/TechnicianEvaluationViewModel.kt"))
        assertEquals(1, Regex(Regex.escape("EvalCardLog.bindReadStart(")).findAll(text).count())
        assertEquals(1, Regex(Regex.escape("EvalCardLog.bindReadDone(")).findAll(text).count())
        assertTrue(
            text.contains(
                "EvalCardLog.bindReadStart(scanId) val read = runCatching { dao.getByScanId(scanId) } " +
                    "val readError = read.exceptionOrNull() " +
                    "if (readError != null) { EvalCardLog.draftReadFailed(readError, scanId) } " +
                    "else { EvalCardLog.bindReadDone(found = read.getOrNull() != null, scanId = scanId) }"
            )
        )
    }

    // ---- 3. every clear names its reason ----

    @Test
    fun `no caller clears the holder without a reason, and the two callers pass their own`() {
        sources.forEach { (path, text) ->
            assertFalse(
                "$path clears the holder with no reason",
                Regex("""\b(?:sessionHolder|scanSessionHolder)\.clear\(\s*\)""").containsMatchIn(text)
            )
        }
        assertTrue(
            squash(source("net/neuralcheck/data/auth/AuthManager.kt"))
                .contains("scanSessionHolder.clear(ScanSessionHolder.ClearReason.SIGN_OUT)")
        )
        // The scoring engine cannot be run on the JVM here, so its call site is pinned as text.
        assertTrue(
            squash(source("net/neuralcheck/domain/scoring/AiScoringEngine.kt"))
                .contains("scanSessionHolder.clear(ScanSessionHolder.ClearReason.ANALYSIS_COMPLETE)")
        )
    }

    // ---- 4. how EvalCardLog prints ----

    @Test
    fun `EvalCardLog prints through one Log_i, with no Enum name and no stack trace`() {
        val text = source(evalCardLogPath)
        assertEquals("one Log call", 1, Regex("""\bLog\.""").findAll(text).count())
        assertTrue(Regex("""Log\.i\(TAG, it\)""").containsMatchIn(text))
        // Order 2026-10-14 (replaces the earlier "exactly one `.name`", rule 23): the draft-failed line names the
        // exception class the way the read-failed line does, so there are two `.name`, and both are that.
        assertEquals(
            "the only `.name` are the exception class names of the two failure lines (read-failed, draft-failed)",
            2, Regex("""\.name\b""").findAll(text).count()
        )
        assertEquals(
            "and both are `error.javaClass.name`",
            2, Regex("""error\.javaClass\.name""").findAll(text).count()
        )
        assertFalse("no stack trace", Regex("""printStackTrace|getStackTrace|stackTrace|Throwable\(""").containsMatchIn(text))
        assertFalse("no third-party class names in the lines", Regex("""\bsimpleName\b""").containsMatchIn(text))
    }

    // ---- 5. R8 ----

    @Test
    fun `nothing in the proguard setup strips android util Log`() {
        val rules = File(projectRoot, "app/proguard-rules.pro")
        assertTrue("expected app/proguard-rules.pro", rules.isFile)
        val rulesText = rules.readText()
        assertFalse("no assumenosideeffects in proguard-rules.pro", rulesText.contains("assumenosideeffects"))
        assertFalse("proguard-rules.pro does not mention android.util.Log", rulesText.contains("android.util.Log"))

        val gradle = File(projectRoot, "app/build.gradle.kts").readText()
        val files = Regex("""proguardFiles\(([^)]*\)[^)]*)\)""").findAll(gradle).map { squash(it.groupValues[1]) }.toList()
        assertTrue("found the proguardFiles call", files.isNotEmpty())
        files.forEach { call ->
            assertEquals(
                "only the default optimize file and proguard-rules.pro",
                "getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"proguard-rules.pro\"",
                call
            )
        }
    }
}
