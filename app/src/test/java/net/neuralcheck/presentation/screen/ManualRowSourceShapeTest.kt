package net.neuralcheck.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batch 1.0.12: what the unit tests of that batch could not see of the manual-test row (a composable; there was not yet a Compose harness - `ManualUnavailableLineLayoutTest` renders the row since 3284cca),
 * pinned from the source text — a STATIC SCAN with comments removed, the technique `EvalCardLogSourceShapeTest` uses.
 * The decisions themselves are proved by `ManualChipActionsTest` (what a chip tap does) and `ManualRowActionsTest` (what each
 * exit of the row writes, does to the dialog and prints); this file proves the row CALLS them and nothing else — so no
 * exit can appear in the row without its line.
 */
class ManualRowSourceShapeTest {

    private val screenDir: File = listOf("app/src/main/java", "../app/src/main/java")
        .map { File(it, "net/neuralcheck/presentation/screen") }
        .firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private val reportScreen: String by lazy { stripComments(File(screenDir, "ReportScreen.kt").readText()) }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    /** The text of the `ManualTestRow` composable: from its signature to the next top-level `@Composable`. */
    private val row: String by lazy {
        val start = reportScreen.indexOf("internal fun ManualTestRow(")
        check(start >= 0) { "cannot find ManualTestRow" }
        val end = reportScreen.indexOf("@Composable", start)
        check(end > start) { "cannot find the end of ManualTestRow" }
        reportScreen.substring(start, end)
    }

    // ---- the chips and the start button ----

    @Test
    fun `the three answer chips each call actions onChip with their own answer - none writes by itself`() {
        for (status in listOf("PASS", "FAIL", "NOT_TESTED")) {
            assertEquals(
                "the $status chip's onClick",
                1, Regex("""onClick\s*=\s*\{\s*actions\.onChip\(ManualStatuses\.$status\)\s*\}""").findAll(row).count()
            )
        }
        assertFalse(
            "no chip writes a literal answer directly any more",
            Regex("""onResult\(item\.id,\s*"(pass|fail|not_tested)"""").containsMatchIn(reportScreen)
        )
    }

    @Test
    fun `the start button calls actions onStartTest - it does not open a dialog by itself`() {
        assertEquals(1, Regex("""onClick\s*=\s*\{\s*actions\.onStartTest\(\)\s*\}""").findAll(row).count())
    }

    // ---- the one exit ----

    @Test
    fun `the row reaches its screen's writer through ManualRowActions alone`() {
        val s = squash(row)
        assertTrue(s.contains("val actions = remember(item.id) { ManualRowActions( testId = item.id,"))
        assertTrue(s.contains("write = { id, status, measurements -> currentOnResult(id, status, measurements) }"))
        assertTrue(s.contains("setActiveDialog = { activeDialog = it }"))
        // onResult is only read (into the updated state) by the row; it is never CALLED from it
        assertEquals(0, Regex("""\bonResult\(""").findAll(row).count())
        assertEquals("the dialog state is set in one place", 1, Regex("""\bactiveDialog\s*=[^=]""").findAll(row).count())
    }

    @Test
    fun `a dialog's answer and its dismissal leave through the actions - the answer helper is one line`() {
        val s = squash(row)
        assertTrue(s.contains("fun answer(status: String, measurements: Map<String, Any?>? = null) = actions.onDialogAnswer(status, measurements)"))

        val routes = row.substringAfter("when (activeDialog) {")
        val dismissals = Regex("""onDismiss\s*=\s*([^\n]*)""").findAll(routes).map { squash(it.groupValues[1]) }.toList()
        assertTrue("found the dialog routes", dismissals.size >= 16)
        val allowed = setOf("{ actions.onDialogDismissed() }", "{ raw -> actions.onRunDismissed(currentStatus, raw) }")
        dismissals.forEach { assertTrue("`$it` closes a dialog without going through the actions", it in allowed || it.trimEnd(',') in allowed) }
        assertEquals("exactly three routes record a raw run: the grid and the two volume rows (A-VOL)", 3, dismissals.count { it.contains("onRunDismissed") })

        val answers = Regex("""onResult\s*=\s*([^\n]*)""").findAll(routes).map { squash(it.groupValues[1]) }.toList()
        assertEquals("one answer per route", dismissals.size, answers.size)
        val allowedAnswers = setOf("{ status -> answer(status) }", "{ status, raw -> answer(status, raw) }")
        answers.forEach { assertTrue("`$it` hands an answer up without `answer`", it.trimEnd(',') in allowedAnswers) }
    }

    @Test
    fun `no dialog is closed by hand any more`() {
        assertFalse(Regex("""activeDialog\s*=\s*null""").containsMatchIn(row))
    }

    // ---- the grid ----

    @Test
    fun `the grid and the volume rows are routed by the exported ids, not by a second literal`() {
        assertEquals(1, Regex("""TouchGridRecorder\.TEST_ID\s*->\s*TouchGridTestDialog\(""").findAll(reportScreen).count())
        assertFalse("no \"M-02\" -> route is left", Regex(""""M-02"\s*->""").containsMatchIn(reportScreen))
        assertEquals(1, Regex("""VolumeKeyRecorder\.TEST_ID_UP\s*->\s*VolumeButtonTestDialog\(""").findAll(reportScreen).count())
        assertEquals(1, Regex("""VolumeKeyRecorder\.TEST_ID_DOWN\s*->\s*VolumeButtonTestDialog\(""").findAll(reportScreen).count())
        assertFalse("no \"M-09\" -> or \"M-10\" -> route is left", Regex(""""M-(09|10)"\s*->""").containsMatchIn(reportScreen))
    }

    // ---- the chips' layout and the unavailable line (batch 1.0.12, second part; the line: station-2 build, 2026-10-15) ----

    /** The chips' own block: from the FlowRow to the `if (offersUnavailable)` that opens the line under it. */
    private val chipsBlock: String by lazy {
        val start = row.indexOf("FlowRow(")
        check(start >= 0) { "the chips are not in a FlowRow" }
        val end = row.indexOf("if (offersUnavailable) {", start)
        check(end > start) { "cannot find the end of the chips block" }
        row.substring(start, end)
    }

    /** The unavailable line's block: from that `if (offersUnavailable)` to the dialog routes. */
    private val lineBlock: String by lazy {
        val start = row.indexOf("if (offersUnavailable) {", row.indexOf("FlowRow("))
        check(start >= 0) { "cannot find the unavailable line" }
        val end = row.indexOf("when (activeDialog) {", start)
        check(end > start) { "cannot find the end of the unavailable line block" }
        row.substring(start, end)
    }

    @Test
    fun `the chips take their natural width in a FlowRow - no equal weights, no plain Row`() {
        assertEquals("one FlowRow", 1, Regex("""\bFlowRow\(""").findAll(row).count())
        assertEquals("the chips are the only place a weight was", 0, Regex("""\.weight\(""").findAll(row).count())
        assertEquals("the plain Row of equal slots is gone (a FlowRow( does not match this)", 0, Regex("""\bRow\(""").findAll(row).count())
        // experimental in foundation-layout 1.6.x: the opt-in is declared on the composable (TechnicianEvaluationCard does the same)
        assertTrue(reportScreen.contains("@OptIn(ExperimentalLayoutApi::class)"))
        assertEquals("FlowRow and the three answer chips only - the unavailable answer is not a fourth chip", 3, Regex("""\bFilterChip\(""").findAll(chipsBlock).count())
        assertFalse("no unavailable chip inside the FlowRow", chipsBlock.contains("ManualStatuses.UNAVAILABLE") || chipsBlock.contains("manual_mark_unavailable"))
        assertTrue(
            "the chip row carries the tag the layout test finds it by",
            Regex("""\.testTag\(ManualUnavailableLineSpec\.TAG_CHIPS\)""").containsMatchIn(chipsBlock)
        )
    }

    @Test
    fun `the unavailable line is on the rows ManualUnavailableOffer names - below the chip row, one gap constant from it, reading the one list and writing through the actions with no confirmation`() {
        assertEquals(
            "the row asks the one list",
            1, Regex("""val offersUnavailable = ManualUnavailableOffer\.isOffered\(item\.id\)""").findAll(row).count()
        )
        assertTrue("the line is the first branch of `if (offersUnavailable)`, after the FlowRow", lineBlock.startsWith("if (offersUnavailable) {"))
        val spacer = lineBlock.indexOf("Spacer(Modifier.height(ManualUnavailableLineSpec.MIN_GAP_FROM_CHIPS_DP.dp))")
        val line = lineBlock.indexOf("ManualUnavailableLine(")
        assertTrue("the gap spacer is the gap constant (rule 24) and comes before the line", spacer in 0 until line)
        // one tap writes the answer through the same exit every chip uses; no AlertDialog, no extra state between the tap and the write
        assertEquals(
            1, Regex("""onClick\s*=\s*\{\s*actions\.onChip\(ManualStatuses\.UNAVAILABLE\)\s*\}""").findAll(row).count()
        )
        assertTrue("selected when the row holds unavailable", Regex("""selected\s*=\s*currentStatus\s*==\s*ManualStatuses\.UNAVAILABLE""").containsMatchIn(lineBlock))
        assertTrue("the line carries the tag the layout test finds it by", Regex("""\.testTag\(ManualUnavailableLineSpec\.TAG_LINE\)""").containsMatchIn(lineBlock))
        assertFalse("no confirmation dialog is opened by the line", lineBlock.contains("AlertDialog") || row.contains("showUnavailableConfirm"))
    }

    @Test
    fun `a row that is not offered but holds unavailable shows a plain label, not a control - the else branch`() {
        assertEquals(
            1, Regex("""\}\s*else if \(currentStatus == ManualStatuses\.UNAVAILABLE\) \{""").findAll(lineBlock).count()
        )
        assertEquals(
            "the label's text is the one resource",
            1, Regex("""stringResource\(R\.string\.manual_mark_unavailable\)""").findAll(lineBlock).count()
        )
    }

    @Test
    fun `the text of the answer has ONE source - one resource key per language, read by the line, the label and the five dialog buttons, with no literal copy in the code`() {
        val mainDir = File(screenDir, "../../../../..").canonicalFile  // app/src/main/java/net/neuralcheck/presentation/screen -> app/src/main
        val resDir = File(mainDir, "res")
        check(resDir.isDirectory) { "cannot find app/src/main/res from ${screenDir.absolutePath}" }
        val ar = "هذا الجهاز لا يحتوي هذا الجزء"
        val tr = "Bu cihazda bu parça yok"
        fun keysWithValue(file: File, value: String): List<String> =
            Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(file.readText())
                .filter { it.groupValues[2].trim() == value }.map { it.groupValues[1] }.toList()
        assertEquals(listOf("manual_mark_unavailable"), keysWithValue(File(resDir, "values/strings.xml"), ar))
        assertEquals(listOf("manual_mark_unavailable"), keysWithValue(File(resDir, "values-tr/strings.xml"), tr))
        // no source file holds either text as a literal
        val kotlinFiles = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("found the sources", kotlinFiles.size > 100)
        for (f in kotlinFiles) {
            val text = f.readText()
            assertFalse("${f.name} holds the Arabic text as a literal", text.contains(ar))
            assertFalse("${f.name} holds the Turkish text as a literal", text.contains(tr))
        }
        // every reader of the answer's text reads that one key: the line (1), the plain label (1) and the five dialog buttons (5)
        val readers = kotlinFiles.associate { it.name to Regex("""R\.string\.manual_mark_unavailable\b""").findAll(it.readText()).count() }
            .filterValues { it > 0 }
        assertEquals(
            mapOf("ManualUnavailableLine.kt" to 1, "ReportScreen.kt" to 1, "ManualTestDialogs.kt" to 5),
            readers
        )
    }

    @Test
    fun `the dialogs that carry the button are told by the row - exactly the offered rows that have a dialog`() {
        val routes = row.substringAfter("when (activeDialog) {")
        // "M-08" -> HeadphoneJackTestDialog( ... ) : the id of every route, and whether its argument list passes offerUnavailable
        val routeStarts = Regex("""(?:"(M-\d{2})"|(TouchGridRecorder\.TEST_ID|VolumeKeyRecorder\.TEST_ID_UP|VolumeKeyRecorder\.TEST_ID_DOWN))\s*->\s*\w+\(""").findAll(routes).toList()
        // a route keyed by an exported id (the grid's, the two volume rows') is that id's test
        fun idOf(m: MatchResult): String = m.groupValues[1].ifEmpty {
            when (m.groupValues[2]) {
                "TouchGridRecorder.TEST_ID" -> "M-02"
                "VolumeKeyRecorder.TEST_ID_UP" -> "M-09"
                else -> "M-10"
            }
        }
        assertTrue("found the dialog routes", routeStarts.size >= 16)
        val passing = routeStarts.mapIndexedNotNull { i, m ->
            val body = routes.substring(m.range.last, routeStarts.getOrNull(i + 1)?.range?.first ?: routes.length)
            val id = idOf(m)
            if (Regex("""\bofferUnavailable\s*=\s*offersUnavailable\b""").containsMatchIn(body)) id else null
        }.toSet()
        val routed = routeStarts.map { idOf(it) }.toSet()
        // The routes whose dialog CAN carry the button — a signature that takes `offerUnavailable`: the headphone jack, the charging port
        // (M-12; it shares the battery-state detector with M-14), the wireless-charging pad and the SD card. The ROW tells each of them
        // whether to show it, by asking the one list — so M-12, which left the list on 2026-10-15, still passes the flag, and gets false.
        assertEquals(
            "a route passes `offerUnavailable = offersUnavailable` exactly when its dialog can carry the button",
            setOf("M-08", "M-12", "M-14", "M-15"), passing
        )
        val offered = net.neuralcheck.domain.manual.ManualUnavailableOffer.TEST_IDS.toSet()
        assertEquals("every id in the list has a route that passes the flag (M-08, M-14, M-15)", offered, offered.filter { it in routed && it in passing }.toSet())
        assertEquals("the one route that passes the flag and is not on the list is M-12", setOf("M-12"), passing - offered)
    }
}
