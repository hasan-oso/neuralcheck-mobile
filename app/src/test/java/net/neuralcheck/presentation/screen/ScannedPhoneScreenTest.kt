package net.neuralcheck.presentation.screen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.remote.CustomerSessionWatch
import net.neuralcheck.data.remote.SessionDocWatcher
import net.neuralcheck.presentation.viewmodel.RealJoinPathHarness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * A-P6-09 - «الهاتف المفحوص بشريط وحده» (`plan/PLAN-06-DATA-COLLECTION.md:1050`; ف١٢ `:954`: the card and the two charts leave a SESSION scan; and, by the coordinator's decision of 2026-10-21 - the
 * follow-up the previous builder recommended - the two LIVE-READING tiles, temperature and CPU usage, leave it too; the tiles of time remaining and battery level stay). What is tested, and at which layer:
 *
 *  1. [ScannedPhoneScreen.parts] - the one place that decides - for EVERY combination of its two facts (rule 30: each value of the enumerated input, not one): the scan's session (`true`, `false`, and `null` = not
 *     known yet) against the form's guard, for each of its four parts.
 *  2. The REAL answer fed to it: a real join, a real record at scan start, the real [CustomerSessionWatch.start] - the call the scan view model keeps as `scanStartedInSession` - and then the real policy
 *     (rule 37: nothing between the join and the decision is replaced; only the network boundary of the harness is).
 *  3. The scan screen's SOURCE (Compose does not run on the JVM for this screen: a Service, a Hilt view model; the repository's own tests of it are source scans - `EvaluationCardSourceShapeTest`,
 *     `SessionScreensSourceShapeTest`): each part sits under its one line of the policy and nothing else does; the progress bar, the stop button and the cover prompt sit under none; the sentence is read
 *     through the labels and nowhere else.
 * NOT tested, and said so: what the screen looks like on a device (no run on a phone).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannedPhoneScreenTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- 1. the policy, every combination ----

    @Test
    fun `a session's scan shows no chart, whatever the form's state`() {
        for (formLive in listOf(false, true)) {
            assertFalse("formLive=$formLive", ScannedPhoneScreen.parts(startedInSession = true, formLive = formLive).charts)
        }
    }

    @Test
    fun `a personal scan shows the charts, and a scan not yet known to be either shows none - an absence is not a no`() {
        for (formLive in listOf(false, true)) {
            assertTrue("a personal scan, formLive=$formLive", ScannedPhoneScreen.parts(startedInSession = false, formLive = formLive).charts)
            assertFalse("not known yet, formLive=$formLive", ScannedPhoneScreen.parts(startedInSession = null, formLive = formLive).charts)
        }
    }

    @Test
    fun `the card stays while the form is not live and leaves with it - for every session state`() {
        for (started in listOf<Boolean?>(true, false, null)) {
            assertTrue("form not live, started=$started", ScannedPhoneScreen.parts(started, formLive = false).evaluationCard)
            assertFalse("form live, started=$started", ScannedPhoneScreen.parts(started, formLive = true).evaluationCard)
        }
    }

    @Test
    fun `the sentence is for a session's scan with the form live, and for nothing else`() {
        for (started in listOf<Boolean?>(true, false, null)) {
            for (formLive in listOf(false, true)) {
                assertEquals("started=$started formLive=$formLive", started == true && formLive, ScannedPhoneScreen.parts(started, formLive).inProgressSentence)
            }
        }
    }

    @Test
    fun `the two live readings - temperature and CPU usage - are for a personal scan only, for every combination - a session's scan and a scan not yet known show neither`() {
        // new with the coordinator's decision of 2026-10-21: before it the policy had no part for the two tiles
        for (formLive in listOf(false, true)) {
            assertTrue("a personal scan, formLive=$formLive", ScannedPhoneScreen.parts(startedInSession = false, formLive = formLive).liveTiles)
            assertFalse("a session's scan, formLive=$formLive", ScannedPhoneScreen.parts(startedInSession = true, formLive = formLive).liveTiles)
            assertFalse("not known yet, formLive=$formLive - an absence is not a no", ScannedPhoneScreen.parts(startedInSession = null, formLive = formLive).liveTiles)
        }
    }

    /**
     * Rule 23: REPLACES `today - the form's guard is false - a session's scan loses its charts and its two live tiles and keeps its card, and no sentence is drawn - a personal scan keeps everything` (it pinned the
     * guard false, the card kept and no sentence). The guard is TRUE since 2026-10-23 (the form's texts are approved and written): a session's scan loses its charts, its two live tiles AND the old card, and draws the
     * sentence; a personal scan keeps its charts and tiles and loses the card too - the policy reads the guard, not the shop (`FormLiveScreenShapeTest` pins that known effect).
     */
    @Test
    fun `today - the form's guard is true - a session's scan loses its charts, its two live tiles and its card and draws the sentence - a personal scan keeps its charts and tiles and loses the card`() {
        assertTrue("the guard is true since the form's texts were approved and written (TechnicianFormGuardTest)", TECH_FORM_TEXTS_APPROVED)
        val session = ScannedPhoneScreen.parts(startedInSession = true, formLive = TECH_FORM_TEXTS_APPROVED)
        assertEquals(ScannedPhoneScreen.Parts(charts = false, liveTiles = false, evaluationCard = false, inProgressSentence = true), session)
        val personal = ScannedPhoneScreen.parts(startedInSession = false, formLive = TECH_FORM_TEXTS_APPROVED)
        assertEquals(ScannedPhoneScreen.Parts(charts = true, liveTiles = true, evaluationCard = false, inProgressSentence = false), personal)
    }

    // ---- 2. the real answer, fed to the policy ----

    private fun join(h: RealJoinPathHarness, id: String) {
        h.joinResponse = hashMapOf<String, Any?>("sessionId" to id, "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true)
        h.joinViewModel().join("NC-1234")
    }

    @Test
    fun `the real record decides - a scan that started in a session shows no chart, a personal one does, and a session joined later does not turn a personal scan into a session's`() {
        RealJoinPathHarness().use { h ->
            fun watch() = CustomerSessionWatch(SessionDocWatcher(), h.holder, h.records) { }

            // a personal scan: it started with nothing joined, so nothing was recorded for it ...
            h.startScan("scan-personal")
            // ... and a session joined AFTER it started is not its session (the record is asked, not the holder)
            join(h, "sess-1")
            val personal: Boolean? = watch().start("scan-personal")
            assertEquals(false, personal)
            assertTrue("the personal scan has its charts", ScannedPhoneScreen.parts(personal, TECH_FORM_TEXTS_APPROVED).charts)
            assertTrue("and its two live tiles", ScannedPhoneScreen.parts(personal, TECH_FORM_TEXTS_APPROVED).liveTiles)

            // a session's scan: the scan started while the session was joined - the record is made at scan start
            h.startScan("scan-session")
            val session: Boolean? = watch().start("scan-session")
            assertEquals(true, session)
            assertFalse("the session's scan has none", ScannedPhoneScreen.parts(session, TECH_FORM_TEXTS_APPROVED).charts)
            assertFalse("and neither live tile", ScannedPhoneScreen.parts(session, TECH_FORM_TEXTS_APPROVED).liveTiles)
        }
    }

    // ---- 3. the scan screen's source ----

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun code(path: String): String = squash(stripComments(File(mainDir, "java/net/neuralcheck/$path").readText()))

    private fun mainKotlin(): List<File> = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val screen: String by lazy { code("presentation/screen/ActiveScanScreen.kt") }

    /** The text inside the braces that open right after [header] (which ends with `{`), balanced. */
    private fun blockAfter(text: String, header: String): String {
        val at = text.indexOf(header)
        assertTrue("found `$header`", at >= 0)
        assertEquals("`$header` appears once", at, text.lastIndexOf(header))
        var depth = 1
        var i = at + header.length
        val start = i
        while (i < text.length && depth > 0) {
            when (text[i]) { '{' -> depth++; '}' -> depth-- }
            i++
        }
        assertEquals("the block is closed", 0, depth)
        return text.substring(start, i - 1)
    }

    @Test
    fun `the screen asks the policy once, with the view model's answer and the form's guard - and only four parts are ever put under it`() {
        assertEquals(
            1, Regex("""val startedInSession by viewModel\.scanStartedInSession\.collectAsState\(\)""").findAll(screen).count()
        )
        assertEquals(
            1, Regex("""val scanParts = ScannedPhoneScreen\.parts\(startedInSession, TECH_FORM_TEXTS_APPROVED\)""").findAll(screen).count()
        )
        val uses = Regex("""scanParts\.(\w+)""").findAll(screen).map { it.groupValues[1] }.toList().sorted()
        // replaces "each of the three parts" (rule 23): the live tiles are the fourth
        assertEquals("each of the four parts is read once, and nothing else of the policy", listOf("charts", "evaluationCard", "inProgressSentence", "liveTiles"), uses)
    }

    @Test
    fun `the two charts are each called once and both sit under the charts line - nowhere else`() {
        val chartsBlock = blockAfter(screen, "if (scanParts.charts) {")
        for (chart in listOf("CpuUsageChart(", "BatteryTempChart(")) {
            // the declaration `fun CpuUsageChart(` is another place of the file; the CALLS are what is counted
            val calls = Regex("""(?<!fun )${Regex.escape(chart)}""").findAll(screen).count()
            assertEquals("$chart is called once in the screen", 1, calls)
            assertTrue("$chart sits under the charts line", Regex("""(?<!fun )${Regex.escape(chart)}""").containsMatchIn(chartsBlock))
        }
    }

    @Test
    fun `the progress bar, the stop button, the cover prompt and the tiles of time remaining and battery level sit under no part of the policy - the strip stays on every scan`() {
        // replaces "the progress bar, the stop button and the cover prompt sit under no part" (rule 23): the live tiles' line is a part now, and the two tiles that stay are named
        val gated = listOf("if (scanParts.charts) {", "if (scanParts.liveTiles) {", "if (scanParts.inProgressSentence) {").joinToString(" ") { blockAfter(screen, it) }
        for (kept in listOf(
            "GradientProgressBar(progress = progress / 100f)", "R.string.btn_cancel_scan", "CoverSensorPrompt()",
            // the two tiles that stay (the coordinator's decision of 2026-10-21: functional, not a reading of the scan's result)
            "label = stringResource(R.string.scan_time_remaining)", "label = stringResource(R.string.category_battery)"
        )) {
            // the declaration `fun CoverSensorPrompt()` is another place of the file; the CALLS are what is counted
            assertEquals("$kept is in the screen once", 1, Regex("""(?<!fun )${Regex.escape(kept)}""").findAll(screen).count())
            assertFalse("$kept is not under a part of the policy", gated.contains(kept))
        }
        assertTrue("the cover prompt is the top bar's, under the cover request only", screen.contains("topBar = { if (coverPromptActive) CoverSensorPrompt() }"))
        assertTrue(
            "the progress bar comes before the sentence's line, which comes before the charts' line, which comes before the tiles' line",
            screen.indexOf("GradientProgressBar(progress = progress / 100f)") < screen.indexOf("if (scanParts.inProgressSentence) {") &&
                screen.indexOf("if (scanParts.inProgressSentence) {") < screen.indexOf("if (scanParts.charts) {") &&
                screen.indexOf("if (scanParts.charts) {") < screen.indexOf("if (scanParts.liveTiles) {")
        )
    }

    @Test
    fun `the temperature tile and the CPU-usage tile are each drawn once and both sit under the live tiles line - nowhere else`() {
        val tilesBlock = blockAfter(screen, "if (scanParts.liveTiles) {")
        // the temperature tile's label is the value computed above it; the CPU tile's label is its string (`text = stringResource(R.string.scan_cpu_usage)` is the chart's title, another place)
        for (tile in listOf("label = tempLabel,", "label = stringResource(R.string.scan_cpu_usage),")) {
            assertEquals("$tile is in the screen once", 1, Regex(Regex.escape(tile)).findAll(screen).count())
            assertTrue("$tile sits under the live tiles line", tilesBlock.contains(tile))
        }
        assertEquals("the block holds exactly the two live tiles", 2, Regex("""MetricCard\(""").findAll(tilesBlock).count())
        // the grid's other two stay: four tiles in all (the declaration `fun MetricCard(` is another place of the file)
        assertEquals("four tiles in the grid", 4, Regex("""(?<!fun )MetricCard\(""").findAll(screen).count())
    }

    @Test
    fun `the sentence is read through the labels, once, in the screen of the scanned phone only - and the labels are asked for under its line`() {
        val block = blockAfter(screen, "if (scanParts.inProgressSentence) {")
        assertEquals(1, Regex("""\btechnicianFormLabels\(\)""").findAll(block).count())
        assertEquals(1, Regex("""sentenceLabels\(FormText\.ScanInProgress\)""").findAll(block).count())
        assertTrue("nothing is drawn while there are no labels", block.contains("if (sentenceLabels != null) {"))
        val askers = mainKotlin().filter { stripComments(it.readText()).contains("FormText.ScanInProgress") }.map { it.name }.sorted()
        // Rule 23: REPLACES `listOf("ActiveScanScreen.kt", "TechnicianFormSurface.kt")` - the slot is also named, once, in the file that maps every text to its resource
        assertEquals("the sentence's slot is named in the scan screen, in the list that holds it and in the map to its resource", listOf("ActiveScanScreen.kt", "FormTextResources.kt", "TechnicianFormSurface.kt"), askers)
        // Rule 23: REPLACES «no resource holds the proposed sentence (decision 90: a text nobody approved is not written to a resource)». The sentence is approved now (A4-1, by delegation 2026-10-23: Arabic and
        // Turkish, the customer sees that phone) and is written once in each language file, as the table has it; `FormRepeatApprovedTextsTest` pins the wording and its two locales.
        val sentence = ApprovedTextsTable.form.single { it.resource == "tf_scan_in_progress" }
        val values = File(mainDir, "res/values/strings.xml").readText()
        val valuesTr = File(mainDir, "res/values-tr/strings.xml").readText()
        assertEquals(1, Regex("""name="tf_scan_in_progress"""").findAll(values).count())
        assertEquals(1, Regex("""name="tf_scan_in_progress"""").findAll(valuesTr).count())
        assertTrue(values.contains(">${sentence.ar}</string>"))
        assertTrue(valuesTr.contains(">${sentence.tr}</string>"))
    }

    @Test
    fun `the view model keeps the answer of the one read-start call as the screen's fact - null until the scan is named`() {
        val vm = code("presentation/viewmodel/ViewModels.kt").substringAfter("class ActiveScanViewModel").substringBefore("sealed class ScanUiState")
        assertEquals(1, Regex("""private val _scanStartedInSession = MutableStateFlow<Boolean\?>\(null\)""").findAll(vm).count())
        assertEquals(1, Regex("""val scanStartedInSession: StateFlow<Boolean\?> = _scanStartedInSession""").findAll(vm).count())
        assertEquals("written once, from the read-start call", 1, Regex("""_scanStartedInSession\.value = sessionWatch\.start\(state\.scanId\)""").findAll(vm).count())
        assertEquals("and nowhere else", 1, Regex("""_scanStartedInSession\.value""").findAll(vm).count())
    }
}
