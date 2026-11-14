package net.neuralcheck.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Order 2026-10-10 (coordinator, item 2 of the DC-03 test order): "if the repository has a Compose test
 * framework that runs on the JVM (Robolectric + compose-ui-test) or an existing tool test: prove that
 * `TechnicianEvaluationCard` shows its title (`eval_title`) when the view model says so, and that it is the
 * first thing in `ActiveScanScreen`'s column. If not, say so, and do not add a framework this round."
 *
 * ── What is true of this repository ──
 * At 2026-10-10, when this was written, the module had NEITHER Robolectric NOR an `androidx.compose.ui.test` dependency: `app/build.gradle.kts` listed only
 * junit, kotlinx-coroutines-test, mockito-kotlin and sqlite-jdbc as `testImplementation`, and a dozen
 * existing tests said the same in their own docs (`ScanPermissionsGatePlacementTest`, `V7SecurityLetterSourceReaderTest`,
 * ...). Both have been test dependencies since 3284cca (2026-10-15), so a rendered test of the card is no longer ruled out by the setup; none exists, and
 * this file does not claim the title is drawn.
 *
 * ── What this does instead (القاعدة ٣٧ — what it actually exercises) ──
 * A STATIC SOURCE SCAN, the technique `ScanPermissionsGatePlacementTest` and `DiagnosticLoggingGuardTest`
 * already use: it reads the `.kt` files as text, with comments removed. It pins the shape that lets "the view
 * model says yes" turn into "the title is the first thing on the scan screen":
 *   1. the card is the first statement in the scan screen's scroll column, unconditionally;
 *   2. the view model is built by `hiltViewModel()` and bound on the first non-blank scanId;
 *   3. the card has exactly two early `return`s before it renders — the view model's two answers — and the
 *      title text sits inside the card with no condition on the way;
 *   4. none of the three files reads a subscription, a sign-in state, `BuildConfig` or the scan type;
 *   5. the title string exists in both locale files the app ships.
 * It cannot catch a composable that is present in the text but never reached at run time, a layout that
 * pushes the card off screen, or a theme that paints the title invisible. That gap is the user's field check.
 *
 * Rule 23: no earlier test pinned the card's placement or gates (`git grep -l TechnicianEvaluationCard --
 * app/src/test` was empty), so nothing is replaced.
 */
class EvaluationCardSourceShapeTest {

    private val projectRoot: File = listOf(".", "..")
        .map(::File)
        .firstOrNull { File(it, "app/src/main/java").isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun main(relativePath: String): File = File(projectRoot, "app/src/main/$relativePath")

    private fun kotlin(relativePath: String): String {
        val file = main("java/net/neuralcheck/$relativePath")
        assertTrue("expected to find $relativePath", file.isFile)
        return stripComments(file.readText())
    }

    /** Block comments and `//` comments removed; none of these three files holds a `//` inside a string. */
    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private val activeScan get() = kotlin("presentation/screen/ActiveScanScreen.kt")
    private val card get() = kotlin("presentation/screen/TechnicianEvaluationCard.kt")
    private val evalViewModel get() = kotlin("presentation/viewmodel/TechnicianEvaluationViewModel.kt")

    // ---- 1. placement ----

    @Test
    fun `the card is called exactly once, and it is the first statement of the scan screen's scroll column`() {
        val text = activeScan
        // A-P6-09 (replaces the bare call, rule 23): the card is still the first statement of the column, now under the ONE line that says whether the form has replaced it (ScannedPhoneScreen.parts) -
        // the card's own two gates are unchanged, below.
        val call = "if (scanParts.evaluationCard) TechnicianEvaluationCard(evalViewModel)"
        assertEquals("exactly one call, under that line", 1, Regex(Regex.escape(call)).findAll(text).count())
        assertEquals("and no other call of the card", 1, Regex(Regex.escape("TechnicianEvaluationCard(evalViewModel)")).findAll(text).count())

        val at = text.indexOf(call)
        val openBrace = text.lastIndexOf(") {", at)
        assertTrue("the call sits inside a `) {` block", openBrace >= 0)

        val between = text.substring(openBrace + 3, at)
        assertEquals(
            "nothing — no `if`, no `when`, no other composable — between the column's opening brace and the card",
            "", between.trim()
        )

        val columnHeader = text.substring(maxOf(0, openBrace - 400), openBrace)
        assertTrue("that block is the Column's content", columnHeader.contains("Column("))
        assertTrue(
            "and the Column is the scrolling one (the card is above the fold of a scrolling column)",
            columnHeader.contains(".verticalScroll(rememberScrollState())")
        )
    }

    // ---- 2. the view model and the bind ----

    @Test
    fun `the scan screen builds the view model with hiltViewModel and binds it on the first non-blank scanId`() {
        val text = squash(activeScan)
        assertTrue(
            "evalViewModel comes from hiltViewModel()",
            text.contains(
                "val evalViewModel: net.neuralcheck.presentation.viewmodel.TechnicianEvaluationViewModel = " +
                    "androidx.hilt.navigation.compose.hiltViewModel()"
            )
        )
        assertTrue(
            "bind runs once per scanId, only when it is non-blank",
            text.contains("LaunchedEffect(currentScanId) { if (currentScanId.isNotBlank()) evalViewModel.bind(currentScanId) }")
        )
        assertTrue(
            "the scanId comes from the Running state",
            text.contains("currentScanId = state.scanId")
        )
    }

    // ---- 3. the card's two gates, and the title ----

    @Test
    fun `before it renders, the card returns in exactly two places - the view model's two answers`() {
        val text = card
        val start = text.indexOf("fun TechnicianEvaluationCard(viewModel: TechnicianEvaluationViewModel)")
        assertTrue("found the composable", start >= 0)
        val firstCard = text.indexOf("NCCard(", start)
        assertTrue("found the card body", firstCard > start)

        val prefix = text.substring(start, firstCard)
        assertEquals("exactly two `return`s before the card", 2, Regex("""\breturn\b""").findAll(prefix).count())
        val flat = squash(prefix)
        assertTrue(
            "gate 1: not an evaluation session, or the technician pressed skip",
            flat.contains("if (!viewModel.isEvaluationSession || viewModel.dismissed) return")
        )
        assertTrue(
            "gate 2: the draft has not been built yet",
            flat.contains("val draft = viewModel.draft ?: return")
        )
    }

    @Test
    fun `the title text sits inside the card with no condition on the way`() {
        val text = card
        val firstCard = text.indexOf("NCCard(")
        val title = text.indexOf("text = stringResource(R.string.eval_title)")
        assertTrue("the card renders R.string.eval_title", title > firstCard && firstCard >= 0)
        val onTheWay = text.substring(firstCard, title)
        assertFalse("no `if` between the card and its title", Regex("""\bif\b""").containsMatchIn(onTheWay))
        assertFalse("no `when` between the card and its title", Regex("""\bwhen\b""").containsMatchIn(onTheWay))
        assertFalse("no early return between the card and its title", Regex("""\breturn\b""").containsMatchIn(onTheWay))
    }

    @Test
    fun `the view model's own gate reads the holder once and nothing else`() {
        val text = squash(evalViewModel)
        assertEquals(
            "the join context is read from the holder exactly once, at construction",
            1, Regex(Regex.escape("sessionHolder.getInfo()")).findAll(text).count()
        )
        assertTrue(
            "isEvaluationSession is the snapshot's own flag, and ONLY an explicit true shows the card (order 2026-10-15: an absent flag does not)",
            text.contains("val isEvaluationSession: Boolean = sessionSnapshot != null && sessionSnapshot.evaluationEnabled == true")
        )
        // Replaces the earlier assertion `if (bound || scanId.isBlank() || !isEvaluationSession) return` (rule 23):
        // since the permanent log lines the refusal and its NAME are one `when` — the same three conditions, in the
        // same order, and the first one that holds names the refusal and ends `bind`. Order 2026-10-15 (rule 22): the
        // third condition names an ABSENT flag by its own result (flag-absent) instead of calling it
        // not-evaluation-session, which would be the server saying no when it said nothing.
        assertTrue(
            "bind is closed by it, and names which condition held",
            text.contains(
                "val refusal = when { bound -> EvalCardLog.BindResult.ALREADY_BOUND " +
                    "scanId.isBlank() -> EvalCardLog.BindResult.SCAN_ID_BLANK " +
                    "!isEvaluationSession -> if (sessionSnapshot != null && sessionSnapshot.evaluationEnabled == null) " +
                    "EvalCardLog.BindResult.FLAG_ABSENT else EvalCardLog.BindResult.NOT_EVALUATION_SESSION else -> null }"
            )
        )
        assertTrue(
            "any refusal returns before `bound = true` is set",
            text.contains("if (refusal != null) { EvalCardLog.bindResult(refusal, scanId) return } bound = true")
        )
    }

    // ---- 4. what the gate does not read ----

    @Test
    fun `neither the card, nor its view model, nor the scan screen reads a subscription, a sign-in state, BuildConfig or the scan type`() {
        val forbidden = Regex(
            """subscription|trialBanner|checkSubscription|isLoggedIn|currentUser|authManager|BuildConfig|isAnonymous""",
            RegexOption.IGNORE_CASE
        )
        mapOf(
            "ActiveScanScreen.kt" to activeScan,
            "TechnicianEvaluationCard.kt" to card,
            "TechnicianEvaluationViewModel.kt" to evalViewModel
        ).forEach { (name, text) ->
            val hit = forbidden.find(text)
            assertTrue("$name reads `${hit?.value}` — the card's gate would then depend on it", hit == null)
        }
        // The scan type is an argument of ActiveScanScreen; only startScan(scanType, ...) may use it.
        val uses = Regex("""\bscanType\b""").findAll(activeScan).count()
        assertEquals("scanType appears in the signature and in the one startScan call", 2, uses)
    }

    // ---- 5. the string ----

    @Test
    fun `eval_title exists in both locale files the app ships`() {
        listOf("values", "values-tr").forEach { dir ->
            val file = main("res/$dir/strings.xml")
            assertTrue("expected $dir/strings.xml", file.isFile)
            assertTrue("$dir/strings.xml defines eval_title", file.readText().contains("name=\"eval_title\""))
        }
    }
}
