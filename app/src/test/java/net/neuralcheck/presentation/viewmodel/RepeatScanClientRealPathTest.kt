package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.data.remote.RepeatScanClient
import net.neuralcheck.data.remote.RepeatScanClient.OfferOutcome
import net.neuralcheck.data.remote.RepeatScanClient.Role
import net.neuralcheck.data.remote.RepeatScanClient.StartOutcome
import net.neuralcheck.domain.repeat.FirstTempState
import net.neuralcheck.domain.repeat.RepeatDecision
import net.neuralcheck.domain.repeat.RepeatRefusal
import net.neuralcheck.domain.repeat.RepeatState
import net.neuralcheck.domain.repeat.StartableReference
import net.neuralcheck.util.EvalCardLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The repeat scan's two calls through the REAL path - A-P6-07 (rules 30, 37, 22, 21). Real: [RepeatScanClient], `FirebaseAnalysisService.getRepeatScanOffer` / `startRepeatScan` (the callable names, the request
 * map, the Gson tree of the answer - the one that KEEPS the nulls the server sends), the reader and the decisions, the log. Replaced: only the HTTP round trip and auth (the harness) and - declared - the repeat
 * scan's exit gate: opened by [RealJoinPathHarness.openRepeatCalls] for most tests, shut by [RealJoinPathHarness.closeRepeatCalls] for the one that shows a shut gate, and left as built for ONE
 * (`the real gate is open`: the guard is true since 2026-10-23).
 *
 * THE ANSWERS ARE WRITTEN FROM THE CONTRACT, NOT PRODUCED BY THE SERVER: the server's half (`cccd027`, inside `fd730ac`) is not deployed and was not run from here. Each is spelled key by key as A4 (5) and (4) of
 * `evidence/run-2026-10-18/protocol-contract-A0-A10-neuralcheck-backend-fd730ac.md` spell them (a copy, rule 29). The refusal bodies' status and message are made up and vary on purpose: the app reads
 * `details.reasonCode` only (rule 21).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepeatScanClientRealPathTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun withClient(signedIn: Boolean = true, gateOpen: Boolean = true, check: RealJoinPathHarness.(RepeatScanClient) -> Unit) {
        RealJoinPathHarness(signedInAtStart = signedIn, viewModelContext = context).use { h ->
            if (gateOpen) h.openRepeatCalls() else h.closeRepeatCalls()
            h.check(RepeatScanClient(h.service))
        }
    }

    /** A4 (5): the seven keys of the offer's answer. The null ones are the server's JSON nulls. */
    private fun offerAnswer(
        state: String = "SELECTED",
        reason: String? = null,
        temp: Map<String, Any?>? = mapOf("state" to "PRESENT", "valueC" to 32.8),
        childSessionId: String? = null,
        childStatus: String? = null
    ): HashMap<String, Any?> = hashMapOf(
        "sessionId" to "sess-1", "scanId" to "scan-1", "state" to state, "reason" to reason,
        "firstScanStartTemp" to temp, "childSessionId" to childSessionId, "childStatus" to childStatus
    )

    private fun refusal(code: String, status: String = "FAILED_PRECONDITION") =
        SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", code))

    private fun offer(client: RepeatScanClient, role: Role = Role.SCANNED) = runBlocking { client.offer("sess-1", "scan-1", role) }
    private fun start(client: RepeatScanClient, flag: Boolean? = null, role: Role = Role.SCANNED) = runBlocking { client.start("sess-1", "scan-1", flag, role) }

    // ---- the offer ----

    @Test
    fun `the offer asks for the pair, and a chosen phone is a startable offer with its reference - the line says so`() = withClient { client ->
        repeatOfferResponse = offerAnswer()
        val outcome = offer(client)
        assertEquals(1, callableCalls("getRepeatScanOffer"))
        assertEquals("the contract's request: {sessionId, scanId}", listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1", "scanId" to "scan-1")), repeatOfferRequests.toList())
        assertEquals(OfferOutcome.Answered(RepeatDecision.Startable(RepeatState.SELECTED, StartableReference.Usable(32.8))), outcome)
        assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("offer", "scanned", "sess-1", "answered", "startable_selected") in logLines)
    }

    @Test
    fun `the server's JSON null survives the real pipeline - a phone not chosen is NotApplicable, not an unreadable answer (the default Gson would have dropped the null key)`() = withClient { client ->
        repeatOfferResponse = offerAnswer(state = "NOT_SELECTED", temp = null)
        assertEquals(OfferOutcome.Answered(RepeatDecision.NotApplicable(RepeatState.NOT_SELECTED, "-")), offer(client))
        repeatOfferResponse = offerAnswer(state = "NOT_ELIGIBLE", reason = "PART_NOT_ENABLED", temp = null)
        assertEquals(OfferOutcome.Answered(RepeatDecision.NotApplicable(RepeatState.NOT_ELIGIBLE, "PART_NOT_ENABLED")), offer(client))
        // and a key the server really left out is not the same as a null: the reference is required to be null, so a missing one is unreadable
        repeatOfferResponse = offerAnswer(state = "NOT_SELECTED").also { it.remove("firstScanStartTemp") }
        assertEquals(OfferOutcome.Answered(RepeatDecision.Unreadable("not_applicable_temp_not_null")), offer(client))
    }

    @Test
    fun `every one of the eleven states and the five temperature states reaches the app as itself through the real path`() = withClient { client ->
        for (state in RepeatState.values()) {
            val temp: Map<String, Any?>? = when (state) {
                RepeatState.NOT_ELIGIBLE, RepeatState.NOT_SELECTED -> null
                else -> mapOf("state" to "PRESENT", "valueC" to 32.8)
            }
            repeatOfferResponse = offerAnswer(state = state.wire, reason = if (state == RepeatState.NOT_ELIGIBLE) "RATE_ABSENT" else null, temp = temp)
            val decision = (offer(client) as OfferOutcome.Answered).decision
            assertEquals("$state", state.startable, decision is RepeatDecision.Startable)
            assertTrue("$state: ${decision.token}", decision.token.endsWith(state.wire.lowercase()))
        }
        for (tempState in FirstTempState.values()) {
            repeatOfferResponse = offerAnswer(temp = mapOf("state" to tempState.wire, "valueC" to if (tempState == FirstTempState.PRESENT) 33.1 else null))
            val decision = (offer(client) as OfferOutcome.Answered).decision as RepeatDecision.Startable
            assertEquals("$tempState", if (tempState == FirstTempState.PRESENT) StartableReference.Usable(33.1) else StartableReference.NotUsable(tempState), decision.reference)
        }
    }

    @Test
    fun `an answer for another pair, an empty answer and a shape the reader does not know are unreadable answers - each its own line, none a chosen phone`() = withClient { client ->
        val cases = listOf<Pair<Any?, String>>(
            offerAnswer().also { it["sessionId"] = "sess-2" } to "unreadable_echo_sessionId_differs",
            hashMapOf<String, Any?>() to "unreadable_echo_sessionId_missing",
            null to "unreadable_absent",
            "an answer that is a text" to "unreadable_not_an_object_string",
            offerAnswer(state = "COMPLETED_WARM") to "unreadable_state_unrecognized"
        )
        for ((answer, token) in cases) {
            repeatOfferResponse = answer
            val outcome = offer(client) as OfferOutcome.Answered
            assertTrue("$answer", outcome.decision is RepeatDecision.Unreadable)
            assertEquals(token, outcome.decision.token)
            assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("offer", "scanned", "sess-1", "answered", token) in logLines)
        }
    }

    @Test
    fun `the role is the line's, and the owner's call is the same call`() = withClient { client ->
        repeatOfferResponse = offerAnswer()
        offer(client, Role.OWNER)
        assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("offer", "owner", "sess-1", "answered", "startable_selected") in logLines)
        assertEquals(1, callableCalls("getRepeatScanOffer"))
    }

    @Test
    fun `the session id is hashed in the line - never printed raw - and no temperature is printed`() = withClient { client ->
        repeatOfferResponse = offerAnswer(temp = mapOf("state" to "PRESENT", "valueC" to 32.8))
        offer(client)
        val line = logLines.single { it.startsWith("repeat-scan ") }
        assertTrue(line, !line.contains("sess-1") && !line.contains("scan-1") && !line.contains("32.8"))
    }

    // ---- the refusals ----

    @Test
    fun `every refusal code of the contract is read as itself, for any Functions status - the code is the text, not the exception`() = withClient { client ->
        for (r in RepeatRefusal.values()) {
            for (status in listOf("FAILED_PRECONDITION", "PERMISSION_DENIED", "INVALID_ARGUMENT", "NOT_FOUND")) {
                repeatOfferFailure = refusal(r.wire, status)
                assertEquals("${r.wire} / $status", OfferOutcome.Refused(r.wire, r), offer(client))
                repeatStartFailure = refusal(r.wire, status)
                assertEquals("${r.wire} / $status", StartOutcome.Refused(r.wire, r), start(client))
            }
        }
        // the session contract's own code is a refusal too, and a code the app has no case for keeps its text but is none of the twelve
        repeatOfferFailure = refusal("SESSION_STATUS_UNKNOWN")
        assertEquals(OfferOutcome.Refused("SESSION_STATUS_UNKNOWN", null), offer(client))
        repeatStartFailure = refusal("REPEAT_BRAND_NEW")
        assertEquals(StartOutcome.Refused("REPEAT_BRAND_NEW", null), start(client))
        assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("start", "scanned", "sess-1", "refused", "REPEAT_BRAND_NEW") in logLines)
    }

    @Test
    fun `a disconnect, a plain failure and a signed-out phone are not-sent - never a refusal, even when the text of the failure says one`() {
        withClient { client ->
            for ((label, failure) in SessionWireFixtures.offlineFailures() + ("a plain exception" to RuntimeException("REPEAT_ALREADY_STARTED"))) {
                repeatOfferFailure = failure
                assertEquals(label, OfferOutcome.NotSent, offer(client))
                repeatStartFailure = failure
                assertEquals(label, StartOutcome.NotSent, start(client))
            }
            assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("offer", "scanned", "sess-1", "not_sent", "-") in logLines)
        }
        withClient(signedIn = false) { client ->
            assertEquals("no signed-in account: the call is not made", OfferOutcome.NotSent, offer(client))
            assertEquals(StartOutcome.NotSent, start(client))
            assertEquals(0, events.count { it.startsWith("callable:") })
        }
    }

    // ---- the start ----

    private fun startAnswer() = hashMapOf<String, Any?>("sessionId" to "child-1", "parentSessionId" to "sess-1", "state" to "STARTED", "expiresInMinutes" to 60)

    @Test
    fun `the start sends the pair, and the flag only when it is true - a cooled start declares nothing`() = withClient { client ->
        repeatStartResponse = startAnswer()
        assertEquals(StartOutcome.Started("child-1", 60), start(client, flag = null))
        assertEquals(StartOutcome.Started("child-1", 60), start(client, flag = true))
        assertEquals(
            listOf<Any?>(
                hashMapOf<String, Any?>("sessionId" to "sess-1", "scanId" to "scan-1"),
                hashMapOf<String, Any?>("sessionId" to "sess-1", "scanId" to "scan-1", "startedBeforeCooling" to true)
            ),
            repeatStartRequests.toList()
        )
        assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("start", "scanned", "sess-1", "started", "cooled") in logLines)
        assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("start", "scanned", "sess-1", "started", "before_cooling") in logLines)
    }

    @Test
    fun `a start answer that is not a start is an unreadable answer with its own line - not a child session`() = withClient { client ->
        val cases = listOf<Pair<Any?, String>>(
            startAnswer().also { it["state"] = "SELECTED" } to "state_not_started",
            startAnswer().also { it["parentSessionId"] = "sess-9" } to "parent_session_id_differs",
            startAnswer().also { it.remove("sessionId") } to "child_session_id_not_a_text",
            hashMapOf<String, Any?>() to "state_absent"
        )
        for ((answer, why) in cases) {
            repeatStartResponse = answer
            assertEquals("$answer", StartOutcome.UnreadableAnswer(why), start(client))
            assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("start", "scanned", "sess-1", "unreadable_answer", why) in logLines)
        }
    }

    @Test
    fun `a start answer that names no lifetime is a child session with no lifetime - none is made up`() = withClient { client ->
        repeatStartResponse = startAnswer().also { it.remove("expiresInMinutes") }
        assertEquals(StartOutcome.Started("child-1", null), start(client))
    }

    // ---- the gate ----

    /**
     * Rule 23: REPLACES `the real gate is closed - while REPEAT_TEXTS_APPROVED is false neither call leaves the phone, and nothing is printed` (it pinned the guard false, and the real gate with it). The guard is
     * TRUE since 2026-10-23; the REAL gate - the service as built, nothing opened or shut by the test - reads it, so both calls leave the phone through the real service and are printed.
     */
    @Test
    fun `the real gate is open - the guard is true, so with nothing opened by the test both calls leave the phone through the real service and are printed`() {
        assertTrue("the guard of the surfaces is true since 2026-10-23", net.neuralcheck.presentation.screen.REPEAT_TEXTS_APPROVED)
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            assertTrue("the service says it is open - nobody opened it", h.service.repeatCallsOpen())
            h.repeatOfferResponse = offerAnswer()
            h.repeatStartResponse = startAnswer()
            val client = RepeatScanClient(h.service)
            assertTrue(offerOutcomeOf(h, client) is OfferOutcome.Answered)
            assertEquals(StartOutcome.Started("child-1", 60), runBlocking { client.start("sess-1", "scan-1", true, Role.SCANNED) })
            assertEquals(1, h.callableCalls("getRepeatScanOffer"))
            assertEquals(1, h.callableCalls("startRepeatScan"))
            assertEquals(2, h.logLines.count { it.startsWith("repeat-scan ") })
        }
    }

    private fun offerOutcomeOf(h: RealJoinPathHarness, client: RepeatScanClient) = runBlocking { client.offer("sess-1", "scan-1", Role.SCANNED) }

    @Test
    fun `a shut gate - neither call leaves the phone, and nothing is printed`() {
        withClient(gateOpen = false) { client ->
            // nothing replaced but the gate's state: the real service
            assertEquals(OfferOutcome.GuardClosed, offer(client))
            assertEquals(StartOutcome.GuardClosed, start(client, flag = true))
            assertEquals("no callable call at all", 0, events.count { it.startsWith("callable:") })
            assertTrue(logLines.toString(), logLines.none { it.startsWith("repeat-scan ") })
            assertTrue("the service says it is closed", !service.repeatCallsOpen())
            // straight at the service
            val offerFailure = runCatching { runBlocking { service.getRepeatScanOffer("sess-1", "scan-1") } }.exceptionOrNull()
            val startFailure = runCatching { runBlocking { service.startRepeatScan("sess-1", "scan-1", null) } }.exceptionOrNull()
            assertTrue("$offerFailure", offerFailure is net.neuralcheck.data.remote.RepeatCallsNotApprovedException)
            assertTrue("$startFailure", startFailure is net.neuralcheck.data.remote.RepeatCallsNotApprovedException)
            assertEquals(0, events.count { it.startsWith("callable:") })
        }
        // and the form's own gate is another gate: opening it opens nothing here (the repeat scan's is shut)
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.closeRepeatCalls()
            h.openFormCalls()
            assertNull(runCatching { runBlocking { h.service.getRepeatScanOffer("s", "c") } }.getOrNull())
            assertEquals(0, h.events.count { it.startsWith("callable:") })
        }
    }
}
