package net.neuralcheck.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guard of the repeat scan's two screens and where its literals live - A-P6-07. Source-shape, with comments removed (the technique of `TechnicianFormGuardTest`).
 *
 * Why: the texts of PLAN-06 rows ف١٣ and ف١٤ were proposed, not approved (PLAN-06:926; `plan/TEXTS-APPROVED-2026-10-10.md:4`: «وما لم يُذكر هنا غير معتمد»), the refusal texts and the failure text were never written
 * before the server's builder proposed them, and the server's half (`cccd027`, inside `fd730ac`) is not deployed. Hasan delegated them on 2026-10-22 and they were reviewed on 2026-10-23: all sixteen are approved
 * and written (two reworded), so [RepeatTexts.pending] is empty and [repeatLabels] reads the resources. The guard itself, [REPEAT_TEXTS_APPROVED], was flipped by its own change (2026-10-23, after the form's), which
 * replaced the test that pinned it false (rule 23). The texts' wording is pinned by [FormRepeatApprovedTextsTest] and what the screens draw with them by `RepeatLiveScreenShapeTest`; this test pins where the guard
 * sits (each screen's one call to a section is behind both, the two callables are refused at the exit when it is shut, rule 35), that it may be true only with nothing pending, and what the review left unwritten.
 *
 * Rule 24: each wire literal of this feature is spelled in ONE main file.
 */
class RepeatScanGuardTest {

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun code(path: String): String = squash(stripComments(File(mainDir, "java/net/neuralcheck/$path").readText()))

    private fun mainKotlin(): List<File> = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * Rule 23: REPLACES `the guard is still false, the labels are the resources' behind it, and each screen calls its section once, behind both` (it pinned the guard false, the one thing this change flips; its
     * own predecessor pinned `repeatLabels(): RepeatLabels? = null`): the guard is TRUE since 2026-10-23, all sixteen texts are approved and written, and `RepeatLiveScreenShapeTest` draws the screens with the real labels.
     */
    @Test
    fun `the guard is true, the labels are the resources' behind it, and each screen calls its section once, behind both`() {
        assertTrue("every text of the repeat screens is approved and written (2026-10-23): the guard is open", REPEAT_TEXTS_APPROVED)
        val surface = code("presentation/screen/RepeatScanSurface.kt")
        assertTrue(
            "no labels while the guard is false; the resources of the phone's language otherwise",
            surface.contains("internal fun repeatLabels(): RepeatLabels? { if (!REPEAT_TEXTS_APPROVED) return null LocalConfiguration.current return repeatLabelsOf(LocalContext.current.resources) }")
        )
        assertTrue("one resource per text, read as it is", surface.contains("internal fun repeatLabelsOf(resources: Resources): RepeatLabels = { text -> resources.getString(text.resource()) }"))

        val owner = code("presentation/screen/GenerateSessionScreen.kt")
        assertEquals("the notice is called once", 1, Regex("""\bRepeatNoticeSection\(""").findAll(owner).count())
        assertTrue(
            "behind the guard, labels that exist, the completed phase and a notice the owner has not dismissed",
            Regex("""if \(REPEAT_TEXTS_APPROVED && repeatLabels != null && state\.phase == Phase\.COMPLETED && repeatNotice is RepeatNoticeUi\.Notice && !repeatNotice\.dismissed\) \{ [^}]*RepeatNoticeSection\(""").containsMatchIn(owner)
        )
        assertEquals("the labels are read once, from the one function", 1, Regex("""\brepeatLabels\(\)""").findAll(owner).count())

        val report = code("presentation/screen/ReportScreen.kt")
        assertEquals("the cooling screen is called once", 1, Regex("""\bCoolingWaitSection\(""").findAll(report).count())
        assertTrue(
            "behind the guard, labels that exist and the cooling state",
            Regex("""if \(REPEAT_TEXTS_APPROVED && repeatLabels != null && cooling != null\) \{ CoolingWaitSection\(""").containsMatchIn(report)
        )
        assertEquals("the labels are read once, from the one function", 1, Regex("""\brepeatLabels\(\)""").findAll(report).count())
        // the one navigation is to the funnel's entry, only for a started repeat, only behind the guard - and the screen tells the view model it went
        assertEquals(1, Regex("""if \(REPEAT_TEXTS_APPROVED && started != null\) \{[^}]*navController\.navigate\("pre_scan/general/none"\) viewModel\.repeatStartConsumed\(\)""").findAll(report).count())
    }

    /**
     * Rule 23: REPLACES `the guard may be true only when no text is pending - and none is approved today, all sixteen` (it pinned `texts are pending today` and `none of them is approved`): all sixteen are approved by
     * delegation now (2026-10-22, reviewed 2026-10-23).
     */
    @Test
    fun `the guard may be true only when no text is pending - and all sixteen are approved by delegation`() {
        assertTrue("REPEAT_TEXTS_APPROVED implies nothing pending", !REPEAT_TEXTS_APPROVED || RepeatTexts.pending.isEmpty())
        assertTrue("no text is pending: the guard may be flipped", RepeatTexts.pending.isEmpty())
        // the counts the commit message and the report quote: 8 texts of the plan (ف١٤: 2, ف١٣: 6), 7 refusals the server's builder proposed, 1 general failure this builder proposed
        assertEquals(8 + 7 + 1, RepeatTexts.all.size)
        assertEquals("every one is approved by delegation", RepeatTexts.all.size, RepeatTexts.all.count { it.approval == TextApproval.APPROVED_BY_DELEGATION })
    }

    /**
     * Rule 23: REPLACES `no resource holds a text of the repeat screens that is not approved` (it banned the proposed texts from the resources, which are the approved ones now). What the review REWORDED is pinned
     * instead - the two proposals as they were are in no resource: A5i-3's «يُسجَّل تكراراً ساخناً» (the app does not record: it sends `startedBeforeCooling: true`, and the server classifies, rule 20) and A5j-1's
     * «يقيس ثبات النتائج» (the app measures nothing between the two scans).
     */
    @Test
    fun `the two proposals the review reworded are in no resource - the recording and the stability claim`() {
        val unwritten = listOf("يُسجَّل تكراراً ساخناً", "يقيس ثبات النتائج")
        val files = File(mainDir.parentFile, "").walkTopDown().filter { it.isFile && it.name == "strings.xml" && it.path.replace('\\', '/').contains("/src/") && it.path.replace('\\', '/').contains("/res/values") }.toList()
        assertTrue("at least the two default string files are found", files.size >= 2)
        for (file in files) {
            val text = file.readText()
            for (p in unwritten) assertFalse("${file.name} (${file.parentFile.name}) holds the unapproved text «$p»", text.contains(p))
        }
    }

    @Test
    fun `each wire literal of the feature is spelled in one main file`() {
        val sources = mainKotlin().associateWith { stripComments(it.readText()) }
        val literals = mapOf(
            "getRepeatScanOffer" to "RepeatScan.kt",
            "startRepeatScan" to "RepeatScan.kt",
            "startedBeforeCooling" to "RepeatScan.kt",
            "firstScanStartTemp" to "RepeatScan.kt",
            "childSessionId" to "RepeatScan.kt",
            "parentSessionId" to "RepeatScan.kt",
            "valueC" to "RepeatScan.kt",
            "COMPLETED_UNCLASSIFIED" to "RepeatScan.kt",
            "SENTINEL_ZERO" to "RepeatScan.kt",
            "STORED_MALFORMED" to "RepeatScan.kt",
            "REPEAT_ALREADY_STARTED" to "RepeatScan.kt",
            "REPEAT_PARENT_JOINER_UNKNOWN" to "RepeatScan.kt",
            "REPEAT_PAIR_NOT_FOUND" to "RepeatScan.kt"
        )
        for ((literal, owner) in literals) {
            val holders = sources.filter { it.value.contains("\"$literal\"") }.keys.map { it.name }
            assertEquals("\"$literal\" is spelled in $holders", listOf(owner), holders)
        }
    }

    @Test
    fun `the two callables leave the phone through one gate - the gate, then the auth, then the request - and nobody else asks for them`() {
        val service = code("data/remote/FirebaseAnalysisService.kt")
        for (callable in listOf("RepeatScanWire.OFFER_CALLABLE", "RepeatScanWire.START_CALLABLE")) {
            assertEquals("$callable: the gate comes first, then the auth", 1, Regex("""requireRepeatCallsApproved\(${Regex.escape(callable)}\) requireAuth\(\)""").findAll(service).count())
            assertEquals("$callable is requested in one place", 1, Regex("""getHttpsCallable\(${Regex.escape(callable)}\)""").findAll(service).count())
        }
        assertTrue("the gate reads the guard of the surfaces", service.contains("internal var repeatCallsApproved: () -> Boolean = { REPEAT_TEXTS_APPROVED }"))
        assertTrue("and throws when it is closed", service.contains("if (!repeatCallsApproved()) throw RepeatCallsNotApprovedException(callable)"))
        // A-P6-12 (2026-10-21; replaces the count of the whole file, rule 23): the consent call keeps the server's nulls through the same Gson, so the file holds three; the repeat scan's two are counted in their own section
        val repeatSection = service.substringAfter("suspend fun getRepeatScanOffer(").substringBefore("internal var consentCallsApproved")
        assertEquals("the answers keep the server's nulls", 2, Regex("""gsonKeepingNulls\.toJsonTree\(result\.data\)""").findAll(repeatSection).count())
        val askers = mainKotlin().filter { f ->
            val s = stripComments(f.readText())
            s.contains("RepeatScanWire.OFFER_CALLABLE") || s.contains("RepeatScanWire.START_CALLABLE")
        }.map { it.name }
        assertEquals("only the service names the callables to the SDK", listOf("FirebaseAnalysisService.kt"), askers)
        val callers = mainKotlin().filter { f ->
            val s = stripComments(f.readText())
            s.contains(".getRepeatScanOffer(") || s.contains(".startRepeatScan(")
        }.map { it.name }.sorted()
        assertEquals("the one caller of the two service calls is the client", listOf("RepeatScanClient.kt"), callers)
        val clients = mainKotlin().filter { stripComments(it.readText()).contains("RepeatScanClient(") && it.name != "RepeatScanClient.kt" }.map { it.name }
        assertEquals("the client is built in the view models' file only", listOf("ViewModels.kt"), clients)
        assertEquals("two constructions: the owner's view model and the report's", 2, Regex("""\bRepeatScanClient\(""").findAll(code("presentation/viewmodel/ViewModels.kt")).count())
    }

    @Test
    fun `the owner asks once, from the snapshot, only when the call is open - and not-now sends nothing`() {
        val vm = code("presentation/viewmodel/ViewModels.kt")
        assertEquals("asked from one place, the snapshot", 1, Regex("""\baskRepeatOffer\(sessionId\)""").findAll(vm).count())
        val ask = vm.substringAfter("private fun askRepeatOffer(sessionId: String) {").substringBefore("fun dismissRepeatNotice()")
        assertTrue("the gate is read to skip the work", ask.contains("if (!analysisService.repeatCallsOpen()) return"))
        assertTrue("the owner's call carries the owner's role", ask.contains("repeatClient.offer(sessionId, scanId, RepeatScanClient.Role.OWNER)"))
        assertTrue("one question per pair", ask.contains("if (repeatAskedFor == sessionId to scanId) return"))
        assertTrue("a new session is a new question", vm.contains("repeatAskedFor = null viewModelScope.launch {"))
        val dismiss = vm.substringAfter("fun dismissRepeatNotice() {").substringBefore("override fun onCleared()")
        assertTrue("the slice is the function and nothing after it", dismiss.length < 400)
        assertFalse("«ليس الآن» sends nothing: the plan names no call for it", dismiss.contains("repeatClient") || dismiss.contains("analysisService"))
    }

    @Test
    fun `the report's view model reads the scan's session BEFORE the analysis, asks the repeat offer only for a real report of a session scan, and hands the child to the holder through the controller`() {
        val vm = code("presentation/viewmodel/ViewModels.kt")
        val read = vm.indexOf("val sessionAtScan = if (firebaseService.formCallsOpen() || firebaseService.repeatCallsOpen()) scanSessionRecords.sessionFor(scanId, ScanSessionRecords.Use.REPORT)?.sessionId else null")
        val analyse = vm.indexOf("val result = scoringEngine.calculateScore(scanReport)")
        val shown = vm.indexOf("_scoringResult.value = finalResult", analyse)
        val block = vm.indexOf("if (sessionAtScan != null && finalResult.aiAnalysis != null && finalResult.error == null) {", shown)
        assertTrue("the session is read at all", read >= 0)
        assertTrue("before the analysis (a success spends the record)", read < analyse)
        assertTrue("the report is on the screen, then the offer is asked for", analyse in 0 until shown && shown < block)
        val offer = vm.indexOf("viewModelScope.launch { repeatCooling.onReportShown(sessionAtScan, scanId) }", block)
        assertTrue("launched, not awaited, inside the same real-report block", offer > block && offer - block < 900)
        assertEquals("the controller is built once, with the holder's adoption", 1, Regex("""private val repeatCooling = ScannedRepeatCooling\(RepeatScanClient\(firebaseService\)\) \{ childSessionId -> sessionHolder\.adoptRepeatChild\(childSessionId\) \}""").findAll(vm).count())
        assertEquals("the child reaches the holder in one place", 1, Regex("""\.adoptRepeatChild\(""").findAll(vm).count())
        val others = mainKotlin().filter { it.name != "ScanSessionHolder.kt" && it.name != "ViewModels.kt" && stripComments(it.readText()).contains(".adoptRepeatChild(") }.map { it.name }
        assertEquals("nobody else adopts a child", emptyList<String>(), others)
    }
}
