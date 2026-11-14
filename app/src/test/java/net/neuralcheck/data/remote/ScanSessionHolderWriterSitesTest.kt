package net.neuralcheck.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Order 2026-10-10 (coordinator, item 4 of the DC-03 test order): prove that nothing between the join and the
 * scan screen clears [ScanSessionHolder] — "the auth listener, and efd8210".
 *
 * The EXECUTED half is in `JoinToEvaluationCardRealPathTest` (the real `AuthManager.uidFlow` collector and a
 * late auth-state event run between the join and the view model, and the holder is compared at every step).
 * It can only exercise the collectors it can build. This is the OTHER half, and it is weaker on purpose:
 *
 * ── القاعدة ٣٧ — what this actually exercises ──
 * A STATIC SOURCE SCAN (comments removed), the technique `DiagnosticLoggingGuardTest` uses. It pins the
 * COMPLETE list of places in `app/src/main` that write the holder, register an auth-state listener, collect
 * `uidFlow` or sign out. It does not run any of them, and it cannot tell a write inside a collector from a
 * write in the next function of the same file; what it does is make a NEW writer, a NEW listener or a NEW
 * `uidFlow` collector fail this test, so whoever adds one has to read the order in
 * `JoinToEvaluationCardRealPathTest` and decide. The two writers that exist today are:
 *   - `clear()` in `AuthManager.signOut` (a button: Settings / Profile) and in `AiScoringEngine` (after the
 *     analysis — a later screen than the scan);
 *   - `setFromJoin` in the two join entry points.
 * `set(String)`, the legacy setter that stores `evaluationEnabled = null` (absent: it has no join answer), has no caller.
 *
 * Rule 23: no earlier test listed these sites (`git grep -l ScanSessionHolder -- app/src/test` held only the
 * two join tests and the pre-scan banner test, none scanning sources), so nothing is replaced.
 */
class ScanSessionHolderWriterSitesTest {

    private val mainSourceRoot: File = listOf(
        "app/src/main/java",
        "../app/src/main/java"
    ).map(::File).firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    /** relative path (forward slashes) -> source without comments, for every `.kt` file in main. */
    private val sources: Map<String, String> by lazy {
        mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(mainSourceRoot).invariantSeparatorsPath to stripComments(it.readText()) }
    }

    private fun filesMatching(regex: Regex): Set<String> =
        sources.filter { regex.containsMatchIn(it.value) }.keys

    private fun countsPerFile(regex: Regex): Map<String, Int> =
        sources.mapValues { regex.findAll(it.value).count() }.filterValues { it > 0 }

    @Test
    fun `the guard can see the sources it is meant to protect`() {
        assertTrue("expected the main sources, found ${sources.size}", sources.size > 50)
        assertTrue(sources.containsKey("net/neuralcheck/data/remote/ScanSessionHolder.kt"))
    }

    @Test
    fun `the files that use ScanSessionHolder are exactly these eleven`() {
        // Order 2026-10-14 (replaces "exactly these seven", rule 23): ScanSessionRecords reads the holder at scan start
        // and when asked (it never writes it), and AppModule hands the holder to it. Neither is a writer: the
        // writer-sites test below still pins the four calls that set or clear.
        // Batch 2, item 2, part c (replaces "exactly these nine", rule 23): SessionLeaver and CustomerSessionWatch each empty the holder ONLY if it still
        // holds the session they name (clearIfHolding) - two new writers, pinned below with the others.
        assertEquals(
            setOf(
                "net/neuralcheck/data/auth/AuthManager.kt",
                "net/neuralcheck/data/remote/ScanSessionHolder.kt",
                "net/neuralcheck/data/remote/ScanSessionRecords.kt",
                "net/neuralcheck/data/remote/CustomerSessionWatch.kt",
                "net/neuralcheck/data/remote/SessionLeaver.kt",
                "net/neuralcheck/di/AppModule.kt",
                "net/neuralcheck/domain/scoring/AiScoringEngine.kt",
                "net/neuralcheck/presentation/viewmodel/PreScanViewModel.kt",
                "net/neuralcheck/presentation/viewmodel/QRScannerViewModel.kt",
                "net/neuralcheck/presentation/viewmodel/TechnicianEvaluationViewModel.kt",
                "net/neuralcheck/presentation/viewmodel/ViewModels.kt"
            ),
            filesMatching(Regex("""\bScanSessionHolder\b"""))
        )
    }

    @Test
    fun `the holder is written in exactly seven places - two clear, two setFromJoin, two clearIfHolding, one adoptRepeatChild - and set(String) has no caller`() {
        // A-P6-07 (2026-10-21; replaces "exactly six places", rule 23): ReportViewModel hands the scanned phone the repeat scan's CHILD session through adoptRepeatChild (no code, no join) - the third way in.
        val calls = sources.mapValues { (_, text) ->
            Regex("""\b(?:sessionHolder|scanSessionHolder)\.(set|setFromJoin|clear|clearIfHolding|adoptRepeatChild)\(""")
                .findAll(text).map { it.groupValues[1] }.toList()
        }.filterValues { it.isNotEmpty() }

        assertEquals(
            mapOf(
                "net/neuralcheck/data/auth/AuthManager.kt" to listOf("clear"),
                "net/neuralcheck/data/remote/CustomerSessionWatch.kt" to listOf("clearIfHolding"),
                "net/neuralcheck/data/remote/SessionLeaver.kt" to listOf("clearIfHolding"),
                "net/neuralcheck/domain/scoring/AiScoringEngine.kt" to listOf("clear"),
                "net/neuralcheck/presentation/viewmodel/QRScannerViewModel.kt" to listOf("setFromJoin"),
                // in file order: ReportViewModel (the repeat's child) comes before JoinSessionViewModel (the typed code)
                "net/neuralcheck/presentation/viewmodel/ViewModels.kt" to listOf("adoptRepeatChild", "setFromJoin")
            ),
            calls
        )
    }

    @Test
    fun `the only auth-state listener is AuthManager's, and uidFlow has exactly three collectors outside it`() {
        assertEquals(
            setOf("net/neuralcheck/data/auth/AuthManager.kt"),
            filesMatching(Regex("""\bAuthStateListener\b"""))
        )
        assertEquals(
            "the three uidFlow consumers: QRScannerViewModel.preRepairScans, HomeViewModel.allScans, HistoryViewModel.scans",
            mapOf(
                "net/neuralcheck/presentation/viewmodel/QRScannerViewModel.kt" to 1,
                "net/neuralcheck/presentation/viewmodel/ViewModels.kt" to 2
            ),
            countsPerFile(Regex("""\.uidFlow\b"""))
        )
    }

    @Test
    fun `signOut is called from exactly two buttons' view models, both in ViewModels_kt`() {
        assertEquals(
            mapOf("net/neuralcheck/presentation/viewmodel/ViewModels.kt" to 2),
            countsPerFile(Regex("""\bauthManager\.signOut\(\)"""))
        )
    }
}
