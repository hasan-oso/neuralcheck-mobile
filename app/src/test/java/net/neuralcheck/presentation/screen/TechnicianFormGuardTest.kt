package net.neuralcheck.presentation.screen

import net.neuralcheck.domain.form.GradeAnswer
import net.neuralcheck.domain.form.GradeCategory
import net.neuralcheck.domain.form.Symptom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guard of the technician's form and where its literals live - A-P6-08. Source-shape, with comments removed (the technique of `SessionScreensSourceShapeTest`).
 *
 * Why: the form's texts were not all approved. The approved-before ones are the levels' words, their sixteen descriptions and the three rules (`plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md`, final approval
 * 2026-10-10), the twelve symptoms and «لم ألاحظ شيئاً» in Arabic (PLAN-06:1262-1278), and «جُرّبت لعبة: نعم/لا» (the user's wording, TL:31); everything else - the B2 table (contract A5 / PC:321) and the texts
 * nothing ever wrote (the blocked reasons, the refusals, the states of the configuration) - was pending until Hasan's delegation of 2026-10-22, reviewed on 2026-10-23: the 48 are approved and written (the
 * existing `btn_retry` is the 87th), so [FormTexts.pending] is empty and [technicianFormLabels] reads the resources. The guard itself, [TECH_FORM_TEXTS_APPROVED], was flipped by its own change
 * (2026-10-23), which replaced the test that pinned it false (rule 23). The texts' wording is pinned by [FormRepeatApprovedTextsTest] and what the screens draw with them by `FormLiveScreenShapeTest`; this test
 * pins where the guard sits, that it may be true only with nothing pending, and what the review left unwritten.
 *
 * Rule 24: each wire literal of this feature is spelled in ONE main file.
 */
class TechnicianFormGuardTest {

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun code(path: String): String = squash(stripComments(File(mainDir, "java/net/neuralcheck/$path").readText()))

    private fun mainKotlin(): List<File> = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * Rule 23: REPLACES `the guard is still false, the labels are the resources' behind it, and the form is called once, behind both` (it pinned the guard false, the one thing this change flips; its own
     * predecessor pinned `technicianFormLabels(): FormLabels? = null`): the guard is TRUE since 2026-10-23, every text is approved and written, and `FormLiveScreenShapeTest` draws the form with the real labels.
     */
    @Test
    fun `the guard is true, the labels are the resources' behind it, and the form is called once, behind both`() {
        assertTrue("every text of the form is approved and written (2026-10-23): the guard is open", TECH_FORM_TEXTS_APPROVED)
        val surface = code("presentation/screen/TechnicianFormSurface.kt")
        assertTrue(
            "no labels while the guard is false; the resources of the phone's language otherwise",
            surface.contains("internal fun technicianFormLabels(): FormLabels? { if (!TECH_FORM_TEXTS_APPROVED) return null LocalConfiguration.current return formLabelsOf(LocalContext.current.resources) }")
        )
        assertTrue("one resource per text, read as it is", surface.contains("internal fun formLabelsOf(resources: Resources): FormLabels = { text -> resources.getString(text.resource()) }"))
        val screen = code("presentation/screen/GenerateSessionScreen.kt")
        assertEquals("the section is called once", 1, Regex("""\bTechnicianFormSection\(""").findAll(screen).count())
        assertTrue(
            "behind the guard, labels that exist and a session",
            Regex("""if \(TECH_FORM_TEXTS_APPROVED && formLabels != null && .*?\) \{ [^}]*TechnicianFormSection\(""").containsMatchIn(screen)
        )
        assertEquals("the labels are read once, from the one function", 1, Regex("""\btechnicianFormLabels\(\)""").findAll(screen).count())
    }

    /**
     * Rule 23: REPLACES `the guard may be true only when no text is pending - and the approved set is exactly what the user approved` (it pinned `texts are pending today` and `49 pending`): all 87 are approved now -
     * the 38 approved before, the 48 by delegation (2026-10-22, reviewed 2026-10-23) and the existing `btn_retry`.
     */
    @Test
    fun `the guard may be true only when no text is pending - and every text is approved, by the user, by delegation, or existing`() {
        assertTrue("TECH_FORM_TEXTS_APPROVED implies nothing pending", !TECH_FORM_TEXTS_APPROVED || FormTexts.pending.isEmpty())
        assertTrue("no text is pending: the guard may be flipped", FormTexts.pending.isEmpty())
        val approvedBefore = FormTexts.all.filter { it.approval == TextApproval.APPROVED_LEVELS || it.approval == TextApproval.APPROVED_SYMPTOMS_OR_USER_WORDING }
        // 5 level words (four levels and «لم أفحصها») + 16 descriptions (4 categories x 4 levels) + 3 rules + 13 symptom texts + the game question
        assertEquals(5 + 16 + 3 + 13 + 1, approvedBefore.size)
        assertEquals(GradeAnswer.values().size, approvedBefore.count { it is FormText.Level })
        assertEquals(GradeCategory.values().size * GradeAnswer.values().count { it.isLevel }, approvedBefore.count { it is FormText.LevelDescription })
        assertEquals(3, approvedBefore.count { it is FormText.Rule })
        assertEquals(Symptom.values().size, approvedBefore.count { it is FormText.SymptomText })
        assertEquals(1, approvedBefore.count { it === FormText.GameTried })
        // the counts the commit message and the report quote: 87 texts = 38 approved before + 48 by delegation + 1 existing (kept here so the numbers come from a test, not from a hand count)
        assertEquals(87, FormTexts.all.size)
        assertEquals(48, FormTexts.all.count { it.approval == TextApproval.APPROVED_BY_DELEGATION })
        assertEquals(listOf<FormText>(FormText.ConfigRetry), FormTexts.all.filter { it.approval == TextApproval.EXISTING_APPROVED })
        assertEquals("every text is approved in one of the three ways", FormTexts.all.size, approvedBefore.size + 48 + 1)
        assertTrue(
            "the second form's two lines and the scanned phone's sentence are approved by delegation",
            (listOf(FormText.SecondEvalNeeded, FormText.HandOverDevice) + FormTexts.drawnByScannedPhone).all { it.approval == TextApproval.APPROVED_BY_DELEGATION }
        )
    }

    /**
     * Rule 23: REPLACES `no resource holds a text of the form that is not approved` (it banned the proposed texts from the resources, which are the approved ones now). What the review left UNWRITTEN is pinned instead:
     * A2-29's «إجاباتك تبدو متعارضة: %1$s» (the slot takes no argument, so the placeholder would have shown as «%1$s» - the approved text drops it), A2-34's «حُفظ على الجهاز ...» (no save-offline path exists: P6-11,
     * after the handover) and A5h-1's «انزع الغطاء ...» (no slot, and nothing checks the cover).
     */
    @Test
    fun `the texts the review did not approve are in no resource - the unfilled placeholder, the offline save, the cover`() {
        val unwritten = listOf("إجاباتك تبدو متعارضة: %1\$s", "حُفظ على الجهاز", "ويُرسل حين تعود الشبكة", "انزع الغطاء", "وضع الهاتف مسطّحاً على الطاولة")
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
            "submitTechnicianForm" to "TechnicianForm.kt",
            "getTechnicianFormConfig" to "TechnicianForm.kt",
            "formContractVersion" to "TechnicianForm.kt",
            "known_fault_before_scan" to "TechnicianForm.kt",
            "symptomOrderShown" to "TechnicianForm.kt",
            "FORM_SLOT_SAME_TECHNICIAN" to "TechnicianForm.kt",
            "battery_dies_before_day_end" to "TechnicianForm.kt",
            // the three defect rows are the existing list's keys, read from the one place that spells them (EvaluationAnswers.kt, rule 24)
            "replaced_screen" to "EvaluationAnswers.kt",
            "water_damage" to "EvaluationAnswers.kt",
            "prior_repair" to "EvaluationAnswers.kt",
            "screen_touch" to "TechnicianForm.kt",
            "technicianId" to "TechnicianForm.kt",
            "symptomOrder" to "TechnicianFormConfig.kt",
            // the wire match of 2026-10-18: the server's names and values, each spelled once
            "confirmed_send" to "TechnicianForm.kt",
            "will_review" to "TechnicianForm.kt",
            "recordReportFirstOpened" to "ReportFirstOpened.kt",
            "settingsVersion" to "TechnicianFormConfig.kt",
            "not_submitted" to "TechnicianFormConfig.kt",
            "not_enabled" to "TechnicianFormConfig.kt"
        )
        for ((literal, owner) in literals) {
            val holders = sources.filter { it.value.contains("\"$literal\"") }.keys.map { it.name }
            assertEquals("\"$literal\" is spelled in $holders", listOf(owner), holders)
        }
    }

    @Test
    fun `the form's three callables leave the phone through one gate - the gate, then the auth, then the request - and nobody else asks for them`() {
        val service = code("data/remote/FirebaseAnalysisService.kt")
        for (callable in listOf("TechnicianFormWire.CONFIG_CALLABLE", "TechnicianFormWire.SUBMIT_CALLABLE", "ReportFirstOpenedWire.CALLABLE")) {
            assertEquals("$callable: the gate comes first, then the auth", 1, Regex("""requireFormCallsApproved\(${Regex.escape(callable)}\) requireAuth\(\)""").findAll(service).count())
            assertEquals("$callable is requested in one place", 1, Regex("""getHttpsCallable\(${Regex.escape(callable)}\)""").findAll(service).count())
        }
        assertTrue("the gate reads the guard of the surface", service.contains("internal var formCallsApproved: () -> Boolean = { TECH_FORM_TEXTS_APPROVED }"))
        assertTrue("and throws when it is closed", service.contains("if (!formCallsApproved()) throw FormCallsNotApprovedException(callable)"))
        val askers = mainKotlin().filter { f ->
            val s = stripComments(f.readText())
            s.contains("TechnicianFormWire.CONFIG_CALLABLE") || s.contains("TechnicianFormWire.SUBMIT_CALLABLE") || s.contains("ReportFirstOpenedWire.CALLABLE")
        }.map { it.name }
        assertEquals("only the service names the callables to the SDK", listOf("FirebaseAnalysisService.kt"), askers)
        val reportCallers = mainKotlin().filter { stripComments(it.readText()).contains(".recordReportFirstOpened(") }.map { it.name }
        assertEquals("the one caller of the report call is the recorder", listOf("ReportFirstOpenedRecorder.kt"), reportCallers)
    }

    @Test
    fun `the report's first open has two callers, one per device, each through the recorder - and the customer's reads the scan's session BEFORE the analysis spends it`() {
        val viewModels = code("presentation/viewmodel/ViewModels.kt")
        assertEquals("two constructions: the owner's view model and the report's", 2, Regex("""\bReportFirstOpenedRecorder\(""").findAll(viewModels).count())
        assertEquals(1, Regex("""ReportFirstOpenedRecorder\.Source\.OWNER""").findAll(viewModels).count())
        assertEquals(1, Regex("""ReportFirstOpenedRecorder\.Source\.CUSTOMER""").findAll(viewModels).count())

        // A-P6-07 (replaces the read that waited on the form's gate alone, rule 23): the same read also serves the repeat scan's offer, so either gate opens it
        val read = viewModels.indexOf("val sessionAtScan = if (firebaseService.formCallsOpen() || firebaseService.repeatCallsOpen()) scanSessionRecords.sessionFor(scanId, ScanSessionRecords.Use.REPORT)?.sessionId else null")
        val analyse = viewModels.indexOf("val result = scoringEngine.calculateScore(scanReport)")
        val shown = viewModels.indexOf("_scoringResult.value = finalResult", analyse)
        val record = viewModels.indexOf("if (sessionAtScan != null && finalResult.aiAnalysis != null && finalResult.error == null) {", shown)
        assertTrue("the session is read at all", read >= 0)
        assertTrue("before the analysis (a success spends the record)", read < analyse)
        assertTrue("the report is on the screen, then the first open is recorded - launched, not awaited", analyse in 0 until shown && shown < record)
        assertTrue("launched in the view model's scope", viewModels.substring(record, record + 200).contains("viewModelScope.launch {"))

        val screen = code("presentation/screen/GenerateSessionScreen.kt")
        assertTrue("the owner's button tells the server just before it navigates", screen.contains("onReportOpened() navController.navigate(\"report/\$scanId\")"))
        assertEquals("the button's call, and the one place the screen hands it to the view model", 2, Regex("""\bonReportOpened\(\)""").findAll(screen).count())
        assertEquals(1, Regex("""onReportOpened = \{ viewModel\.onReportOpened\(\) \}""").findAll(screen).count())
    }
}
