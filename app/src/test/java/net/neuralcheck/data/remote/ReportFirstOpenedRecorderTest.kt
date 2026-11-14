package net.neuralcheck.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.remote.ReportFirstOpenedRecorder.Outcome
import net.neuralcheck.data.remote.ReportFirstOpenedRecorder.Source
import net.neuralcheck.presentation.screen.TECH_FORM_TEXTS_APPROVED
import net.neuralcheck.presentation.viewmodel.RealJoinPathHarness
import net.neuralcheck.presentation.viewmodel.SessionWireFixtures
import net.neuralcheck.util.EvalCardLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ReportFirstOpenedRecorder] through the REAL path - A-P6-08, the form's blindness: the call that tells the server the report of a session was shown for the first time (see
 * [net.neuralcheck.domain.form.ReportFirstOpenedWire]). Rules 30 and 37.
 *
 * Real: the recorder, [FirebaseAnalysisService.recordReportFirstOpened] (the callable name, the request map, the Gson parse of the answer), the answer reader, the SDK's own parse of a refusal body
 * ([SessionWireFixtures.exceptionFromWire]), the log. Replaced: the HTTP round trip and the auth object (the harness), and - declared - the form's exit gate: opened by
 * [RealJoinPathHarness.openFormCalls] for most tests, SHUT by [RealJoinPathHarness.closeFormCalls] for the one that shows what a shut gate does, and left as built for the one that runs the REAL gate (rule 37: a path
 * through production with nothing replaced; the guard is true since 2026-10-23, so that gate is open).
 *
 * The server's answer is `{sessionId, recorded}` (technicianForm.js:377 in `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md`, neuralcheck-backend@05275ba).
 * The refusal bodies' status and message are made up and vary on purpose: the app reads `details.reasonCode` only (rule 21). NOT verified against the server: that the deployed function exists (it does not,
 * yet: the call then fails and is only logged), and the refusal codes it will send (the copy names `SESSION_REPORT_NOT_AVAILABLE` and the session ones; their strings are `sessionNames.js`'s).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportFirstOpenedRecorderTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** [gateOpen]: `true` opens the gate (a test says what it relies on), `false` SHUTS it (the real one reads the guard, which is true), `null` leaves the REAL gate as the service was built (rule 37). */
    private fun withRecorder(gateOpen: Boolean? = true, body: (RealJoinPathHarness, ReportFirstOpenedRecorder) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true).use { h ->
            when (gateOpen) {
                true -> h.openFormCalls()
                false -> h.closeFormCalls()
                null -> Unit
            }
            body(h, ReportFirstOpenedRecorder(h.service))
        }
    }

    private fun refusal(code: String, status: String = "FAILED_PRECONDITION") =
        SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", code))

    private val reportLines: (RealJoinPathHarness) -> List<String> = { h -> h.logLines.filter { it.startsWith("report-first-opened ") } }

    // ---- the real gate ----

    /**
     * Rule 23: REPLACES `the real gate is closed - while TECH_FORM_TEXTS_APPROVED is false nothing is sent and nothing is printed, for either device` (it pinned the guard false, and the real gate with it). The
     * guard is TRUE since 2026-10-23; the REAL gate - the service as built, nothing opened or shut by the test - reads it, so the call leaves the phone for either device and is printed.
     */
    @Test
    fun `the real gate is open - the guard is true, so with nothing opened by the test the call leaves the phone through the real service for either device and is printed`() = withRecorder(gateOpen = null) { h, recorder ->
        assertTrue("the guard of the form's surface is true since 2026-10-23", TECH_FORM_TEXTS_APPROVED)
        assertTrue("the service says it is open - nobody opened it", h.service.formCallsOpen())
        for (source in Source.values()) {
            assertEquals(Outcome.Recorded, runBlocking { recorder.record("sess-1", source) })
        }
        assertEquals(2, h.callableCalls("recordReportFirstOpened"))
        assertEquals(2, reportLines(h).size)
    }

    @Test
    fun `a shut gate sends nothing and prints nothing, for either device - the service itself says no`() = withRecorder(gateOpen = false) { h, recorder ->
        assertTrue("the service says it is closed", !h.service.formCallsOpen())
        for (source in Source.values()) {
            assertEquals(Outcome.GuardClosed, runBlocking { recorder.record("sess-1", source) })
        }
        assertEquals("no callable call at all", 0, h.events.count { it.startsWith("callable:") })
        assertTrue("and no line: a shut gate is silent", reportLines(h).isEmpty())
        assertTrue(h.reportOpenedRequests.isEmpty())
        // the service itself says no - not only the recorder
        val direct = runCatching { runBlocking { h.service.recordReportFirstOpened("sess-1") } }.exceptionOrNull()
        assertTrue("$direct", direct is FormCallsNotApprovedException)
        assertEquals(0, h.callableCalls("recordReportFirstOpened"))
    }

    // ---- the call, with the gate open ----

    @Test
    fun `the call is {sessionId} to recordReportFirstOpened, and the first open is recorded - the log says who asked`() = withRecorder { h, recorder ->
        assertEquals(Outcome.Recorded, runBlocking { recorder.record("sess-1", Source.OWNER) })
        assertEquals(1, h.callableCalls("recordReportFirstOpened"))
        assertEquals(listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1")), h.reportOpenedRequests.toList())
        assertEquals(listOf(EvalCardLog.reportFirstOpenedLine("sess-1", "owner", "recorded", "-")), reportLines(h))
        assertEquals(Outcome.Recorded, runBlocking { recorder.record("sess-1", Source.CUSTOMER) })
        assertTrue(EvalCardLog.reportFirstOpenedLine("sess-1", "customer", "recorded", "-") in h.logLines)
    }

    @Test
    fun `a later open is already recorded, and every other answer is named - none is read as recorded`() = withRecorder { h, recorder ->
        h.reportOpenedResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "recorded" to false)
        assertEquals(Outcome.AlreadyRecorded, runBlocking { recorder.record("sess-1", Source.CUSTOMER) })
        assertTrue(EvalCardLog.reportFirstOpenedLine("sess-1", "customer", "already_recorded", "-") in h.logLines)
        val cases = listOf<Pair<Any?, String>>(
            null to "absent",
            hashMapOf<String, Any?>() to "recorded_absent",
            hashMapOf<String, Any?>("recorded" to "true") to "recorded_not_a_boolean_string",
            hashMapOf<String, Any?>("recorded" to 1) to "recorded_not_a_boolean_number",
            "yes" to "not_an_object_string"
        )
        for ((answer, why) in cases) {
            h.reportOpenedResponse = answer
            assertEquals("answer $answer", Outcome.UnreadableAnswer(why), runBlocking { recorder.record("sess-1", Source.OWNER) })
            assertTrue(EvalCardLog.reportFirstOpenedLine("sess-1", "owner", "unreadable_answer", why) in h.logLines)
        }
    }

    @Test
    fun `a refusal is classified by its reasonCode - a known session code and one the app has no name for are each a refusal with their token`() = withRecorder { h, recorder ->
        for (code in listOf("SESSION_REPORT_NOT_AVAILABLE", "SESSION_CANCELLED", "FORM_SESSION_ID_REQUIRED", "BRAND_NEW_REFUSAL")) {
            for (status in listOf("FAILED_PRECONDITION", "PERMISSION_DENIED", "INVALID_ARGUMENT")) {
                h.reportOpenedFailure = refusal(code, status)
                assertEquals("$code / $status", Outcome.Refused(code), runBlocking { recorder.record("sess-1", Source.OWNER) })
                assertTrue(EvalCardLog.reportFirstOpenedLine("sess-1", "owner", "refused", code) in h.logLines)
            }
        }
    }

    @Test
    fun `a disconnect, a plain failure and a signed-out phone are not-sent, never a refusal - and the recorder never throws`() = withRecorder { h, recorder ->
        for ((label, failure) in SessionWireFixtures.offlineFailures() + ("a plain exception" to RuntimeException("SESSION_REPORT_NOT_AVAILABLE"))) {
            h.reportOpenedFailure = failure
            assertEquals(label, Outcome.NotSent, runBlocking { recorder.record("sess-1", Source.CUSTOMER) })
        }
        assertTrue(EvalCardLog.reportFirstOpenedLine("sess-1", "customer", "not_sent", "-") in h.logLines)
        h.reportOpenedFailure = null
        val callsBefore = h.callableCalls("recordReportFirstOpened")
        h.signInAs(null)
        assertEquals("no signed-in user: the service stops before the call", Outcome.NotSent, runBlocking { recorder.record("sess-1", Source.OWNER) })
        assertEquals(callsBefore, h.callableCalls("recordReportFirstOpened"))
    }

    @Test
    fun `no line carries the session id in the clear`() = withRecorder { h, recorder ->
        runBlocking { recorder.record("sess-secret-1234", Source.OWNER) }
        assertTrue(reportLines(h).isNotEmpty())
        assertTrue(reportLines(h).toString(), reportLines(h).none { it.contains("sess-secret-1234") })
    }
}
