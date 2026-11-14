package net.neuralcheck.presentation.screen

import net.neuralcheck.presentation.viewmodel.ApprovedSessionTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the session screens do that no view-model test can see (they are composables), pinned from the source text with comments removed —
 * the technique `ManualRowSourceShapeTest` and `EvalCardLogSourceShapeTest` use. Batch 2 (order 2026-10-15), item 2: the owner's cancel button
 * and its confirmation (part b). The behaviour behind them is proved by `SessionCancelPath*Test` (the real view model, the real texts, the
 * server's wire shape); this file proves the screen READS those texts from the resources, offers the button only where the decision says, and does not
 * let the «+ كود جديد» button cancel anything (user decision 2026-10-15: a cancel names ONE session and touches no other).
 */
class SessionScreensSourceShapeTest {

    private val mainDir: File = listOf("app/src/main/java", "../app/src/main/java")
        .map { File(it) }
        .firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun source(path: String): String = stripComments(File(mainDir, "net/neuralcheck/$path").readText())

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private val generateScreen: String by lazy { squash(source("presentation/screen/GenerateSessionScreen.kt")) }
    private val preScanScreen: String by lazy { squash(source("presentation/screen/PreScanScreen.kt")) }
    private val activeScanScreen: String by lazy { squash(source("presentation/screen/ActiveScanScreen.kt")) }
    private val viewModelsSource: String by lazy { squash(source("presentation/viewmodel/ViewModels.kt")) }

    // ---- the owner's cancel (part b) ----

    @Test
    fun `the owner's cancel control is offered only on a waiting or scanning session`() {
        val offered = Regex("""if \(state\.phase == Phase\.WAITING \|\| state\.phase == Phase\.SCANNING\) \{ Spacer\(Modifier\.height\(16\.dp\)\) CancelSessionControl\(""")
        assertEquals("one call, guarded by the two phases", 1, offered.findAll(generateScreen).count())
        assertEquals("the control is called nowhere else", 1, Regex("""\bCancelSessionControl\(""").findAll(generateScreen).count() - 1)
    }

    @Test
    fun `the cancel button and its confirmation read the approved texts and the existing confirm buttons`() {
        for (key in listOf(
            "session_cancel_btn", "session_cancel_confirm_title", "session_cancel_confirm_text", "btn_confirm", "btn_cancel"
        )) {
            assertEquals("R.string.$key once", 1, Regex("""R\.string\.$key\b""").findAll(generateScreen).count())
        }
        assertTrue(
            "the confirmation is a dialog the confirm button closes before it sends",
            Regex("""TextButton\(onClick = \{ confirming = false onConfirmed\(\) \}\)""").containsMatchIn(generateScreen)
        )
    }

    @Test
    fun `the cancelled state is its own branch of the screen, with the new-code button the expired state has`() {
        // Batch 3, item 2 (replaces the pins of the single text, rule 23): the branch hands the screen's cancelReason to the state, and the state's text is ownerCancelledTextFor's decision
        assertEquals(1, Regex("""Phase\.CANCELLED -> CancelledState\(state\.cancelReason\) \{ viewModel\.create\(\) \}""").findAll(generateScreen).count())
        val cancelled = generateScreen.substringAfter("fun CancelledState(").substringBefore("@Composable")
        assertEquals("the text comes from the decision, once", 1, Regex("""stringResource\(ownerCancelledTextFor\(cancelReason\)\.resId\)""").findAll(cancelled).count())
        assertTrue("the same existing text as the expired state's button", cancelled.contains("R.string.session_new_code"))
        assertFalse("the screen names neither text itself", generateScreen.contains("R.string.session_cancelled_state") || generateScreen.contains("R.string.session_customer_left_state"))
    }

    @Test
    fun `ownerCancelledTextFor is a when over the three reasons with no else - a fourth reason does not compile until someone decides what the owner reads`() {
        val text = squash(source("presentation/viewmodel/OwnerSessionStatus.kt"))
        val body = text.substringAfter("internal fun ownerCancelledTextFor(reason: CancelReason.Read): OwnerCancelledText = when (reason) {").substringBefore("internal fun ownerCancelReasonAfterOwnCancel")
        assertTrue("found the decision", body.length > 100)
        assertEquals(3, Regex("""CancelReason\.[A-Z_]+ -> OwnerCancelledText\.[A-Z_]+""").findAll(body).count())
        assertFalse("no else branch", body.contains("else"))
        assertEquals("the customer-left text is chosen for one reason only", 1, Regex("""CancelReason\.LEFT_BY_CUSTOMER -> OwnerCancelledText\.CUSTOMER_LEFT""").findAll(body).count())
    }

    @Test
    fun `the new-code button creates a session and cancels nothing, and the cancel is sent only from the confirmation`() {
        assertEquals(
            "the + new code button still calls create() and nothing else",
            1, Regex("""onCreateAnother = \{ viewModel\.create\(\) \}""").findAll(generateScreen).count()
        )
        assertEquals("viewModel.cancelSession() is called once, from the confirmation's callback", 1, Regex("""viewModel\.cancelSession\(\)""").findAll(generateScreen).count())
        assertTrue(generateScreen.contains("onCancelConfirmed = { viewModel.cancelSession() }"))
        val viewModels = squash(source("presentation/viewmodel/ViewModels.kt"))
        val create = viewModels.substringAfter("class GenerateSessionViewModel").substringAfter("fun create() {").substringBefore("fun cancelSession()")
        assertTrue("found create()", create.length > 100)
        assertFalse("create() sends no cancellation", create.contains("canceller") || create.contains("cancelScanSession"))
    }

    // ---- the customer's leave (part c; D2, D3, D5) ----

    @Test
    fun `the pre-scan screen opens the leave confirmation from its button, from Back and from the toolbar arrow - only on a device that holds a session`() {
        assertEquals(1, Regex("""BackHandler\(enabled = heldSessionId != null\) \{ showLeaveDialog = true \}""").findAll(preScanScreen).count())
        assertEquals(
            "the toolbar arrow is Back too: it opens the confirmation on a session device and pops as before otherwise",
            1, Regex("""IconButton\(onClick = \{ if \(heldSessionId != null\) showLeaveDialog = true else navController\.popBackStack\(\) \}\)""").findAll(preScanScreen).count()
        )
        assertEquals(1, Regex("""if \(heldSessionId != null\) \{ Spacer\(Modifier\.size\(8\.dp\)\) OutlinedButton\(onClick = \{ showLeaveDialog = true \}""").findAll(preScanScreen).count())
        assertEquals("the device's session comes from the holder, through the one leaver", 1, Regex("""val heldSessionId = leaveViewModel\.sessionOfPreScan\(\)""").findAll(preScanScreen).count())
        for (key in listOf("session_leave_btn", "session_leave_confirm_title", "session_leave_confirm_text")) {
            assertEquals("R.string.$key once", 1, Regex("""R\.string\.$key\b""").findAll(preScanScreen).count())
        }
        assertEquals(
            "the confirmation sends the leave for the held session",
            1, Regex("""leaveViewModel\.leave\(SessionLeaver\.Phase\.PRE_SCAN, heldSessionId\)""").findAll(preScanScreen).count()
        )
        assertTrue(
            "a confirmed leave goes back to where the funnel started (D3)",
            Regex("""if \(leaveState is LeaveSessionViewModel\.State\.Left\) navController\.leaveToScanOrigin\(\)""").containsMatchIn(preScanScreen)
        )
    }

    @Test
    fun `the scan screen's stop dialog leaves the session for a session scan, and stops exactly as before for a personal scan`() {
        assertEquals(
            1,
            Regex(
                """val sessionId = leaveViewModel\.sessionOfScan\(currentScanId\) if \(sessionId == null\) \{ showStopDialog = false viewModel\.stopScan\(\) navController\.popBackStack\(\) \} else \{ leaveViewModel\.leave\(SessionLeaver\.Phase\.SCAN, sessionId, currentScanId\) \}"""
            ).findAll(activeScanScreen).count()
        )
        assertEquals(
            "Back is still the stop dialog's request while a scan runs (batch 1 decision), once",
            1, Regex("""BackHandler\( enabled = scanState is net\.neuralcheck\.presentation\.viewmodel\.ActiveScanViewModel\.ScanUiState\.Running \) \{ showStopDialog = true \}""").findAll(activeScanScreen).count()
        )
        assertTrue(
            "a confirmed leave stops the scan, then goes back to where the funnel started (D3)",
            Regex("""if \(leaveState is LeaveSessionViewModel\.State\.Left\) \{ showStopDialog = false viewModel\.stopScan\(\) navController\.leaveToScanOrigin\(\) \}""").containsMatchIn(activeScanScreen)
        )
    }

    @Test
    fun `the owner's cancellation reaching the scan shows the approved SESSION_CANCELLED text, then leaves by D3`() {
        val dialog = activeScanScreen.substringAfter("if (sessionCancelledByOwner) {").substringBefore("if (showPowerSaveDialog)")
        assertTrue("found the dialog", dialog.length > 100)
        assertEquals(1, Regex("""R\.string\.session_join_cancelled\b""").findAll(dialog).count())
        assertEquals("dismiss and OK both leave", 2, Regex("""navController\.leaveToScanOrigin\(\)""").findAll(dialog).count())
    }

    @Test
    fun `the scan's completion pops the funnel down to where it started, not to a literal home`() {
        assertEquals(1, Regex("""popUpTo\(navController\.scanOriginRoute\(\)\)""").findAll(activeScanScreen).count())
        assertFalse("the literal that removed nothing on a login-start device is gone", activeScanScreen.contains("popUpTo(\"home\")"))
    }

    @Test
    fun `the scan view model starts the session read once per scan, and ends it in the four places a scan ends or is dropped`() {
        val vm = viewModelsSource.substringAfter("class ActiveScanViewModel").substringBefore("sealed class ScanUiState")
        assertEquals(1, Regex("""CustomerSessionWatch\(SessionDocWatcher\(\), sessionHolder, scanSessionRecords\)""").findAll(vm).count())
        // A-P6-09 (replaces the bare `sessionWatch.start(state.scanId)`, rule 23): the same single call, whose answer the scan screen now also keeps - was the scan a session's
        assertEquals(
            "started once, the first time the scan's id is known, and its answer kept as `scanStartedInSession`",
            1, Regex("""if \(state\.scanId\.isNotBlank\(\) && sessionWatchAttemptedFor != state\.scanId\) \{ sessionWatchAttemptedFor = state\.scanId _scanStartedInSession\.value = sessionWatch\.start\(state\.scanId\) \}""").findAll(vm).count()
        )
        assertEquals("the read is started in that one place", 1, Regex("""sessionWatch\.start\(""").findAll(vm).count())
        assertEquals("a safety stop, completion, the stop button's stopScan(), onCleared()", 4, Regex("""sessionWatch\.stop\(\)""").findAll(vm).count())
        assertEquals(
            "the owner's cancellation stops the scan exactly as the stop button does",
            1, Regex("""private fun onSessionCancelledByOwner\(\) \{ stopScan\(\) _sessionCancelledByOwner\.value = true \}""").findAll(vm).count()
        )
    }

    @Test
    fun `reading the session starts, binds and keeps alive no service`() {
        for (file in listOf("data/remote/CustomerSessionWatch.kt", "data/remote/SessionDocWatcher.kt", "data/remote/SessionLeaver.kt")) {
            val text = source(file)
            for (forbidden in listOf("startService", "startForegroundService", "bindService", "android.app.Service", "android.content", "StressTestService")) {
                assertFalse("$file mentions $forbidden", text.contains(forbidden))
            }
        }
    }

    // ---- the owner's listener reads the five statuses (item 3) ----

    @Test
    fun `the owner's view model names no status literal and does not lower-case one - the five come from SessionStatus`() {
        val owner = viewModelsSource.substringAfter("class GenerateSessionViewModel").substringBefore("class JoinSessionViewModel")
        assertTrue("found the owner's view model", owner.length > 1000)
        for (literal in listOf("\"pending\"", "\"waiting\"", "\"joined\"", "\"scanning\"", "\"in_progress\"", "\"completed\"", "\"done\"", "\"expired\"", "\"cancelled\"")) {
            assertFalse("the owner's view model spells $literal", owner.contains(literal))
        }
        assertFalse("no lower-casing of a status", owner.contains("lowercase()"))
        assertFalse("no JOINED phase", owner.contains("Phase.JOINED") || owner.contains("JOINED"))
        assertFalse("no JOINED phase on the screen", generateScreen.contains("Phase.JOINED"))
        assertEquals("the owner reads a snapshot through the shared watcher", 1, Regex("""sessionWatcher\.watch\(""").findAll(owner).count())
        assertEquals("the phase of a status is decided in one place", 1, Regex("""ownerPhaseFor\(read\.status\)""").findAll(owner).count())
    }

    @Test
    fun `ownerPhaseFor is one when over the enum with no else - a sixth status does not compile until someone decides`() {
        val text = squash(source("presentation/viewmodel/OwnerSessionStatus.kt"))
        val body = text.substringAfter("internal fun ownerPhaseFor(status: SessionStatus): Phase = when (status) {").substringBefore("}")
        assertEquals(5, Regex("""SessionStatus\.[A-Z]+ -> Phase\.[A-Z]+""").findAll(body).count())
        assertFalse("no else branch", body.contains("else"))
    }

    // ---- no default text behind a failure (batch 3, item 3) ----

    @Test
    fun `the join screen shows the view model's error as it is - no default text stands behind it, and a join answer's id has no empty default`() {
        val join = squash(source("presentation/screen/JoinSessionScreen.kt"))
        assertEquals("the error is read once into a local, then shown", 1, Regex("""val joinError = state\.error if \(joinError != null\) \{""").findAll(join).count())
        assertEquals("shown as it is", 1, Regex("""Text\( joinError,""").findAll(join).count())
        assertFalse("no elvis on the error", join.contains("state.error ?:"))
        assertFalse("no default text behind any failure on this screen", Regex("""\?: stringResource\(""").containsMatchIn(join))
        val service = squash(source("data/remote/FirebaseAnalysisService.kt"))
        val dto = service.substringAfter("data class JoinSessionResult(").substringBefore("val ownerId")
        assertTrue("the join answer's session id is null when absent", dto.contains("val sessionId: String? = null"))
        assertFalse("no empty-string default on the join answer's session id", Regex("sessionId: String = \"\"").containsMatchIn(dto))
    }

    // ---- no empty session code (item 4) ----

    @Test
    fun `the owner's session has no empty default - the DTO and the screen's state hold null until a session exists, and the chip never takes a blank code`() {
        val service = squash(source("data/remote/FirebaseAnalysisService.kt"))
        val dto = service.substringAfter("data class ScanSession(").substringBefore("expiresInMinutes")
        assertTrue("found the DTO's id and code", dto.contains("val sessionId: String? = null") && dto.contains("val code: String? = null"))
        val emptyDefault = Regex("(sessionId|code): String = \"\"")
        assertFalse("no empty-string default on the DTO", emptyDefault.containsMatchIn(dto))
        val owner = viewModelsSource.substringAfter("class GenerateSessionViewModel").substringBefore("class JoinSessionViewModel")
        assertFalse("no empty-string default on the owner's state", emptyDefault.containsMatchIn(owner))
        assertEquals(
            "a missing or blank id or code ends in the named failure",
            1, Regex("""if \(sessionId == null \|\| code == null\) \{ _state\.value = State\( phase = Phase\.ERROR, error = sessionCreateFailedMessage\(""").findAll(owner).count()
        )
        assertFalse("the chip is never given a blank code to hide", generateScreen.contains("state.code.isBlank()"))
        assertFalse("StatusCard has no blank-text default", Regex("else -> \"\"").containsMatchIn(generateScreen))
        // A-SUB wave 1, item 4 (REPLACES the pin of `ErrorState(null)`, rule 23): the error screen takes the retry flag too; the guard's call is the retryable one
        assertEquals("the guard for a content phase without a code", 1, Regex("""if \(code == null\) \{ ErrorState\(null, retryable = true\)""").findAll(generateScreen).count())
        assertEquals("the failed phase hands the view model's flag to the screen", 1, Regex("""Phase\.ERROR -> ErrorState\(state\.error, state\.retryable\)""").findAll(generateScreen).count())
    }

    // ---- the texts have ONE source: the resources ----

    @Test
    fun `no approved text is copied as a literal anywhere in main - they live in the resources only`() {
        val approved = listOf(
            ApprovedSessionTexts.OWNER_BUTTON, ApprovedSessionTexts.OWNER_CONFIRM_TITLE, ApprovedSessionTexts.OWNER_CONFIRM_BODY,
            ApprovedSessionTexts.OWNER_CANCELLED_STATE, ApprovedSessionTexts.OWNER_CUSTOMER_LEFT_STATE, ApprovedSessionTexts.JOIN_COMPLETED, ApprovedSessionTexts.LEAVE_BUTTON,
            ApprovedSessionTexts.LEAVE_CONFIRM_TITLE, ApprovedSessionTexts.LEAVE_CONFIRM_BODY, ApprovedSessionTexts.JOIN_ALREADY_JOINED,
            ApprovedSessionTexts.JOIN_CANCELLED, ApprovedSessionTexts.CANCEL_COMPLETED, ApprovedSessionTexts.CANCEL_IN_FLIGHT,
            // A-SUB wave 1: K1-K4 and the existing no-credit text live in the resources only (the matrix and the tables name resource ids, never a text)
            ApprovedSessionTexts.SHOP_SUBSCRIPTION_EXPIRED, ApprovedSessionTexts.SUBSCRIPTION_EXPIRED, ApprovedSessionTexts.SHOP_NO_CREDIT,
            ApprovedSessionTexts.FREE_DEVICE_USED, ApprovedSessionTexts.NO_CREDIT_EXISTING
        ).flatMap { listOf(it.ar, it.tr) }
        val files = mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("scanned the main sources", files.size > 100)
        for (file in files) {
            val text = stripComments(file.readText())  // code only: a comment may quote a text
            for (literal in approved) {
                assertFalse("${file.name} holds a copy of an approved text: $literal", text.contains(literal))
            }
        }
    }
}
