package net.neuralcheck.presentation.screen

import net.neuralcheck.data.remote.ScanConsentClient
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentPartRead
import net.neuralcheck.domain.consent.ConsentRefusal
import net.neuralcheck.presentation.navigation.CONSENT_ROUTE
import net.neuralcheck.presentation.navigation.PRE_SCAN_AFTER_JOIN_ROUTE
import net.neuralcheck.presentation.navigation.afterJoinRoute
import net.neuralcheck.presentation.viewmodel.ConsentFailure
import net.neuralcheck.presentation.viewmodel.ConsentUi
import net.neuralcheck.presentation.viewmodel.consentUiFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guard of the consent screen and where its literals live - A-P6-12. Source-shape, with comments removed (the technique of `RepeatScanGuardTest`), plus the approvals as the code states them.
 *
 * Why: the consent's texts - version 2, both languages, the three failure texts - were approved by Hasan's delegation (2026-10-22) and reviewed on 2026-10-23, and are written in the resources; the server's half
 * (`5ab5eaf`, inside `5361116`, and the ق15 commit) is not deployed. [CONSENT_TEXTS_APPROVED] was flipped by its own change (2026-10-23), which replaced the test that pinned it false (rule 23); this test pins where
 * the guard sits and that it may be true only with nothing pending. The wording itself is pinned by `ConsentApprovedTextsTest` and by `ConsentTextVersionTest` (its digest, tied to the version).
 *
 * Rule 24: each wire literal of this feature is spelled in ONE main file, and the wording lives in the resources and in NO Kotlin file.
 */
class ConsentGuardTest {

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun code(path: String): String = squash(stripComments(File(mainDir, "java/net/neuralcheck/$path").readText()))

    private fun mainKotlin(): List<File> = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun stringsFiles(): List<File> = File(mainDir, "res").walkTopDown().filter { it.isFile && it.name == "strings.xml" && it.parentFile.name.startsWith("values") }.toList()

    private fun names(file: File): Set<String> = Regex("""<string name="(consent_[^"]*)"""").findAll(file.readText()).map { it.groupValues[1] }.toSet()

    /**
     * Rule 23: REPLACES `the guard is still false, and every one of the nine texts is approved and written in both languages` (it pinned the guard false, the one thing this change flips; its own predecessor
     * pinned six Arabic texts in Kotlin, no Turkish and three missing failure texts). The guard is TRUE since 2026-10-23: every text is approved and written in both resource files, and
     * `ConsentLiveScreenShapeTest` draws the screen with the real labels.
     */
    @Test
    fun `the guard is true, and every one of the nine texts is approved and written in both languages`() {
        assertTrue("every text of the screen is approved and written (2026-10-23): the guard is open", CONSENT_TEXTS_APPROVED)
        assertTrue("CONSENT_TEXTS_APPROVED implies nothing pending", !CONSENT_TEXTS_APPROVED || ConsentTexts.pending.isEmpty())
        assertTrue("no text is pending: the guard may be flipped", ConsentTexts.pending.isEmpty())
        assertEquals("nine places: the six of the screen (title, three paragraphs, two buttons) and three failure texts", 9, ConsentTexts.all.size)
        assertEquals(ConsentTexts.all.size, ConsentTexts.all.count { it.approval == ConsentTextApproval.APPROVED_BY_DELEGATION })
        val expected = ApprovedConsentTable.entries.map { it.resource }.toSet()
        assertEquals("one resource per text, nine of them", 9, expected.size)
        val files = stringsFiles()
        assertEquals("the two default files are found: values and values-tr", setOf("values", "values-tr"), files.map { it.parentFile.name }.toSet())
        for (file in files) assertEquals("${file.parentFile.name}: exactly the nine consent texts, nothing else with that name", expected, names(file))
    }

    /**
     * Rule 23: REPLACES `the Turkish wording and the proposed failure texts are written nowhere - not in code, not in a resource` and `the approved Arabic wording is written in one main file and in no resource`
     * (they pinned the Arabic in Kotlin and the Turkish and the failure texts nowhere): the wording is in the resources and in no Kotlin file.
     */
    @Test
    fun `the wording is in the resources and in no Kotlin file - not the Arabic, not the Turkish, not a failure text`() {
        val sources = mainKotlin().map { it.name to stripComments(it.readText()) }
        for (e in ApprovedConsentTable.entries) for (text in listOf(e.ar, e.tr)) {
            if (text.length < 8) continue // a button's two words are another file's label as well; the sentences are what must not be copied into Kotlin
            val holders = sources.filter { it.second.contains(text) }.map { it.first }
            assertEquals("«$text» is written in $holders", emptyList<String>(), holders)
        }
        val consentSurface = stripComments(File(mainDir, "java/net/neuralcheck/presentation/screen/ConsentSurface.kt").readText())
        assertFalse("ConsentSurface.kt holds no Arabic letter outside a comment", Regex("""[؀-ۿ]""").containsMatchIn(consentSurface))
        assertFalse("and no Turkish letter", Regex("""[ÇçĞğİıÖöŞşÜü]""").containsMatchIn(consentSurface))
    }

    /**
     * Rule 23: REPLACES `both join entry points route through the one function - with the real guard - ...` (it pinned `afterJoinRoute(Boolean)`: the route depended on the guard alone). The route depends on the
     * guard AND on what the join's answer said about the shop's part (ق15): only [ConsentPartRead.Enabled] leads to the consent screen.
     */
    @Test
    fun `the join leads to the consent screen only when the screen is live AND the shop has the part - the others, the absent answer included, lead where it led`() {
        assertEquals("consent", CONSENT_ROUTE)
        assertEquals("pre_scan/general/none", PRE_SCAN_AFTER_JOIN_ROUTE)
        for (live in listOf(false, true)) for (part in listOf(ConsentPartRead.Enabled, ConsentPartRead.NotEnabled, ConsentPartRead.Absent)) {
            val expected = if (live && part == ConsentPartRead.Enabled) CONSENT_ROUTE else PRE_SCAN_AFTER_JOIN_ROUTE
            assertEquals("live=$live part=$part", expected, afterJoinRoute(live, part))
        }
        // Rule 23: REPLACES `while the guard is false the join leads where it led, whatever the shop says` (it pinned the guard false): with the real guard, a shop that has the part leads to the screen
        assertEquals("with the real guard, a shop that has the part is led to the consent screen", CONSENT_ROUTE, afterJoinRoute(CONSENT_TEXTS_APPROVED, ConsentPartRead.Enabled))
        assertEquals("and a shop that has not is led where the join always led", PRE_SCAN_AFTER_JOIN_ROUTE, afterJoinRoute(CONSENT_TEXTS_APPROVED, ConsentPartRead.NotEnabled))
        val qr = code("presentation/screen/QRScannerScreen.kt")
        assertEquals("QRScannerScreen.kt: one navigation by the function, with the real guard and the state's part", 1, Regex("""navController\.navigate\(afterJoinRoute\(CONSENT_TEXTS_APPROVED, s\.consentPart\)\)""").findAll(qr).count())
        val join = code("presentation/screen/JoinSessionScreen.kt")
        assertEquals("JoinSessionScreen.kt: one navigation by the function, with the real guard and the state's part", 1, Regex("""navController\.navigate\(afterJoinRoute\(CONSENT_TEXTS_APPROVED, consentPart\)\)""").findAll(join).count())
        assertTrue("the typed screen navigates only with a join answer: a session id AND its part", join.contains("if (!sessionId.isNullOrBlank() && consentPart != null) {"))
        for ((site, text) in listOf("QRScannerScreen.kt" to qr, "JoinSessionScreen.kt" to join)) assertFalse("$site: no navigation to the pre-scan screen by hand", text.contains("""navigate("pre_scan/general/none")"""))
        val callers = mainKotlin().filter { stripComments(it.readText()).contains("afterJoinRoute(") && it.name != "ConsentRouting.kt" }.map { it.name }.sorted()
        assertEquals("the two join entry points and nobody else", listOf("JoinSessionScreen.kt", "QRScannerScreen.kt"), callers)
    }

    @Test
    fun `the route is registered once, to the screen - and the screen leaves for the scan only when the server recorded the consent or said there is none to record`() {
        val nav = code("presentation/navigation/NeuralCheckNavigation.kt")
        assertEquals(1, Regex("""composable\(CONSENT_ROUTE\) \{ ConsentScreen\(navController\) \}""").findAll(nav).count())
        val screen = code("presentation/screen/ConsentScreen.kt")
        assertEquals(
            "the pre-scan route is navigated to once, behind the recorded state and the server's not-applicable, removing this screen", 1,
            Regex("""ConsentUi\.Recorded, ConsentUi\.NotApplicable -> navController\.navigate\(PRE_SCAN_AFTER_JOIN_ROUTE\) \{ popUpTo\(CONSENT_ROUTE\) \{ inclusive = true \} \}""").findAll(screen).count()
        )
        assertEquals("and nowhere else in the screen", 1, Regex("""\bnavigate\(""").findAll(screen).count())
        assertEquals("the refusal and the missing session leave the funnel the way a leave does", 1, Regex("""ConsentUi\.Declined, ConsentUi\.NoSession -> navController\.leaveToScanOrigin\(\)""").findAll(screen).count())
        assertTrue("Back does nothing while the screen is drawn: the refusal is the button's", screen.contains("BackHandler(enabled = labels != null) { }"))
        assertEquals("the language recorded is the language drawn: one reading", 1, Regex("""\bConsentLocaleRead\.of\(""").findAll(screen).count())
        assertTrue("the labels come from the same locale as the record", screen.contains("val labels = locale?.let { ConsentTexts.labelsFor(context, it) }") && screen.contains("viewModel.accept(locale)"))
    }

    /**
     * Rule 23: REPLACES `only a recorded consent lets the user on - every other outcome of the call is a failure of the screen` (it listed every [ConsentRefusal] as a failure): the ninth refusal, ق40
     * `CONSENT_PART_NOT_ENABLED`, is «لا ينطبق» - the shop has no consent part - and is NOT a failure that stops the scan.
     */
    @Test
    fun `only a recorded consent or the server's not-applicable lets the user on - every other outcome of the call is a failure of the screen`() {
        val recorded = ScanConsentClient.RecordOutcome.Recorded(2, ConsentLocale.AR, false, differs = false)
        assertEquals(ConsentUi.Recorded, consentUiFor(recorded))
        assertEquals("ق40: classified by the reasonCode alone", ConsentUi.NotApplicable, consentUiFor(ScanConsentClient.RecordOutcome.Refused("CONSENT_PART_NOT_ENABLED", ConsentRefusal.PART_NOT_ENABLED)))
        val others = listOf(
            ScanConsentClient.RecordOutcome.GuardClosed,
            ScanConsentClient.RecordOutcome.NotSent,
            ScanConsentClient.RecordOutcome.UnreadableAnswer("state_not_accepted"),
            ScanConsentClient.RecordOutcome.Refused("SESSION_EXPIRED", null),
            ScanConsentClient.RecordOutcome.Refused("CONSENT_BRAND_NEW", null)
        ) + ConsentRefusal.values().filter { it != ConsentRefusal.PART_NOT_ENABLED }.map { ScanConsentClient.RecordOutcome.Refused(it.wire, it) }
        for (outcome in others) assertTrue("$outcome", consentUiFor(outcome) is ConsentUi.RecordFailed)
        assertEquals(ConsentUi.RecordFailed(ConsentFailure.SESSION_NOT_SCANNING), consentUiFor(ScanConsentClient.RecordOutcome.Refused("CONSENT_SESSION_NOT_SCANNING", ConsentRefusal.SESSION_NOT_SCANNING)))
        assertEquals(ConsentUi.RecordFailed(ConsentFailure.STORED_UNREADABLE), consentUiFor(ScanConsentClient.RecordOutcome.Refused("CONSENT_STORED_UNREADABLE", ConsentRefusal.STORED_UNREADABLE)))
        assertEquals(ConsentUi.RecordFailed(ConsentFailure.OTHER), consentUiFor(ScanConsentClient.RecordOutcome.Refused("CONSENT_LOCALE_INVALID", ConsentRefusal.LOCALE_INVALID)))
    }

    @Test
    fun `each wire literal of the feature is spelled in one main file`() {
        val sources = mainKotlin().associateWith { stripComments(it.readText()) }
        val literals = mapOf(
            "recordScanConsent" to "ScanConsent.kt",
            "textVersion" to "ScanConsent.kt",
            "alreadyRecorded" to "ScanConsent.kt",
            "consentEnabled" to "ScanConsent.kt",
            "CONSENT_NOT_AUTHENTICATED" to "ScanConsent.kt",
            "CONSENT_SESSION_NOT_SCANNING" to "ScanConsent.kt",
            "CONSENT_STORED_UNREADABLE" to "ScanConsent.kt",
            "CONSENT_LOCALE_INVALID" to "ScanConsent.kt",
            "CONSENT_PART_NOT_ENABLED" to "ScanConsent.kt",
            "consent_declined" to "SessionContract.kt"
        )
        val acceptedState = mainKotlin().filter { stripComments(it.readText()).contains("STATE_ACCEPTED") }.map { it.name }
        assertEquals("the one state the call answers is read through its constant, in the one file that spells it", listOf("ScanConsent.kt"), acceptedState)
        for ((literal, owner) in literals) {
            val holders = sources.filter { it.value.contains("\"$literal\"") }.keys.map { it.name }
            assertEquals("\"$literal\" is spelled in $holders", listOf(owner), holders)
        }
        // the join's answer reads the key through the constant, not through a second spelling
        assertTrue(code("data/remote/FirebaseAnalysisService.kt").contains("@SerializedName(ConsentWire.KEY_CONSENT_ENABLED) val consentEnabled: Boolean? = null"))
    }

    @Test
    fun `the callable leaves the phone through one gate - the gate, then the request - and nobody else asks for it`() {
        val service = code("data/remote/FirebaseAnalysisService.kt")
        assertEquals("the gate comes first, then the request - and no local auth gate: the server's own answer is read", 1, Regex("""requireConsentCallsApproved\(ConsentWire\.CALLABLE\) val result = callWithTimeout\(ConsentWire\.CALLABLE\)""").findAll(service).count())
        assertEquals("requested in one place", 1, Regex("""getHttpsCallable\(ConsentWire\.CALLABLE\)""").findAll(service).count())
        assertTrue("the gate reads the guard of the screen", service.contains("internal var consentCallsApproved: () -> Boolean = { CONSENT_TEXTS_APPROVED }"))
        assertTrue("and throws when it is closed", service.contains("if (!consentCallsApproved()) throw ConsentCallsNotApprovedException(callable)"))
        assertEquals("the answer keeps the server's nulls", 1, Regex("""getHttpsCallable\(ConsentWire\.CALLABLE\)\.call\(ConsentWire\.request\(sessionId, textVersion, locale\)\)\.await\(\) \} return gsonKeepingNulls\.toJsonTree\(result\.data\)""").findAll(service).count())
        val askers = mainKotlin().filter { stripComments(it.readText()).contains("ConsentWire.CALLABLE") }.map { it.name }.sorted()
        assertEquals("only the service names the callable to the SDK", listOf("FirebaseAnalysisService.kt"), askers)
        val callers = mainKotlin().filter { stripComments(it.readText()).contains(".recordScanConsent(") }.map { it.name }.sorted()
        assertEquals("the one caller of the service call is the client", listOf("ScanConsentClient.kt"), callers)
        val clients = mainKotlin().filter { stripComments(it.readText()).contains("ScanConsentClient(") && it.name != "ScanConsentClient.kt" }.map { it.name }
        assertEquals("the client is built in the view model only", listOf("ConsentViewModel.kt"), clients)
        assertEquals(1, Regex("""\bScanConsentClient\(""").findAll(code("presentation/viewmodel/ConsentViewModel.kt")).count())
    }

    @Test
    fun `the refusal is the existing cancellation with the existing reason - sent by the leaver, called by the controller, and by nobody else`() {
        val leaver = code("data/remote/SessionLeaver.kt")
        assertEquals(1, Regex("""suspend fun declineConsent\(sessionId: String\) = end\(CancelReason\.CONSENT_DECLINED, ScanSessionHolder\.ClearReason\.CONSENT_DECLINED, Phase\.CONSENT, sessionId, scanId = null\)""").findAll(leaver).count())
        assertEquals("the leave still sends left_by_customer", 1, Regex("""suspend fun leave\(phase: Phase, sessionId: String, scanId: String\? = null\) = end\(CancelReason\.LEFT_BY_CUSTOMER, ScanSessionHolder\.ClearReason\.LEFT_BY_CUSTOMER, phase, sessionId, scanId\)""").findAll(leaver).count())
        assertEquals("the one cancellation is sent from one place", 1, Regex("""canceller\.cancel\(CancelRole\.CUSTOMER, sessionId, reason\)""").findAll(leaver).count())
        val callers = mainKotlin().filter { stripComments(it.readText()).contains(".declineConsent(") }.map { it.name }
        assertEquals(listOf("ScanConsentUi.kt"), callers)
        val users = mainKotlin().filter { it.name != "SessionContract.kt" && stripComments(it.readText()).contains("CancelReason.CONSENT_DECLINED") }.map { it.name }.sorted()
        assertEquals("the reason is used by the roles table, the owner's reading of a cancel, the watch's own-cancel echo and the leaver", listOf("CustomerSessionWatch.kt", "OwnerSessionStatus.kt", "SessionCanceller.kt", "SessionLeaver.kt"), users)
    }
}
