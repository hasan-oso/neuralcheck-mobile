package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.data.remote.LeaveFailureRead
import net.neuralcheck.data.remote.SessionLeaver
import net.neuralcheck.presentation.viewmodel.LeaveSessionViewModel.State
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The path of a customer LEAVING a session, whole, with the real texts — batch 2 (order 2026-10-15), item 2, part c (decisions D2 and D3: the leave sends
 * `left_by_customer`; the holder is emptied only if it still holds that session; the scan's record goes with it).
 *
 * The order: every code of Appendix A table (b) at the call that throws it, the path whole, the text SHOWN checked, the simulated reply in the wire shape.
 * REAL here: the SDK's parse of the wire JSON, [net.neuralcheck.data.remote.FirebaseAnalysisService] (`joinScanSession`, `cancelScanSession`), the real join
 * ([JoinSessionViewModel], which fills the holder as the app does), [net.neuralcheck.data.remote.ScanSessionRecords] over in-memory preferences, the real
 * [SessionLeaver] and [LeaveSessionViewModel], and the app's string resources. REPLACED: the HTTP round trip and the Firebase auth object (the harness).
 *
 * What it cannot see: the dialogs and the navigation (composables; pinned by `SessionScreensSourceShapeTest`), and a live service.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class SessionLeavePathBase {

    internal abstract fun approved(text: ApprovedSessionTexts.Text): String

    /** The dispatcher the view models' coroutines run on; its scheduler is the virtual clock the real timeout of the call is measured on. */
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun existing(id: Int): String = context.getString(id)

    private fun leaveVm(h: RealJoinPathHarness) = LeaveSessionViewModel(context, SessionLeaver(h.canceller, h.holder, h.records))

    /** What the harness's join answers, for session [id]: the customer device joins it and the holder holds it. */
    private fun join(h: RealJoinPathHarness, id: String) {
        h.joinResponse = hashMapOf<String, Any?>("sessionId" to id, "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true)
        h.joinViewModel().join("NC-1234")
        assertEquals("the join stored the session in the holder", id, h.holder.get())
    }

    private fun expectedCancelText(reasonCode: String?): String = when (reasonCode) {
        "SESSION_COMPLETED" -> approved(ApprovedSessionTexts.CANCEL_COMPLETED)
        "SESSION_ANALYSIS_IN_FLIGHT" -> approved(ApprovedSessionTexts.CANCEL_IN_FLIGHT)
        else -> existing(R.string.error_generic)
    }

    private fun named(id: String) = mapOf("sessionId" to id, "reason" to "left_by_customer")

    // ---- from the pre-scan screen ----

    @Test
    fun `leave from the pre-scan screen - sends left_by_customer for the held session, empties the holder, and says it left`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            val vm = leaveVm(h)
            assertEquals("sess-1", vm.sessionOfPreScan())
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals(State.Left, vm.state.value)
            assertEquals(listOf<Any?>(named("sess-1")), h.cancelRequests.toList())
            assertNull("the holder was emptied", h.holder.getInfo())
            assertTrue(h.logLines.any { it.startsWith("session-cancel-sent role=customer reason=left_by_customer session=#") })
            assertTrue(h.logLines.any { it.startsWith("session-leave role=customer phase=pre-scan result=left ") && it.endsWith(" holder=cleared") })
        }
    }

    @Test
    fun `leave - a device in no session has nothing to leave`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            val vm = leaveVm(h)
            assertNull(vm.sessionOfPreScan())
            assertNull("a scan that never recorded a session is a personal scan", vm.sessionOfScan("scan-without-record"))
        }
    }

    @Test
    fun `leave - the holder is emptied only if it still holds the session being left, and the other session stays`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-A")
            join(h, "sess-B") // joined after A: the holder holds B now
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-A")
            assertEquals(State.Left, vm.state.value)
            assertEquals("only the named session was cancelled", listOf<Any?>(named("sess-A")), h.cancelRequests.toList())
            assertEquals("sess-B", h.holder.get())
            assertTrue(h.logLines.any { it.startsWith("session-leave ") && it.endsWith(" holder=kept-other-session") })
            assertTrue(h.logLines.any { it.startsWith("holder-clear-skipped reason=left-by-customer ") })
        }
    }

    // ---- from the scan screen ----

    @Test
    fun `leave from the scan screen - the session is the one the SCAN started in, and the scan's record goes with the leave`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-A")
            h.startScan("scan-1") // the scan records the session it starts in: A
            join(h, "sess-B")     // joined after the scan started: the holder holds B
            val vm = leaveVm(h)
            assertEquals("the scan keeps the session it started in", "sess-A", vm.sessionOfScan("scan-1"))
            vm.leave(SessionLeaver.Phase.SCAN, "sess-A", "scan-1")
            assertEquals(State.Left, vm.state.value)
            assertEquals(listOf<Any?>(named("sess-A")), h.cancelRequests.toList())
            assertEquals("the holder's session B is not touched", "sess-B", h.holder.get())
            assertNull("the scan will never be analysed: its record is gone", h.records.sessionFor("scan-1", net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS))
            assertTrue(h.logLines.any { it.startsWith("session-leave role=customer phase=scan result=left ") })
        }
    }

    // ---- offline: the phone leaves locally (batch 3, item 1; user decision 4) ----

    /** The failures a cancellation that never reached the server ends in - the one list the six places share ([SessionWireFixtures.offlineFailures]). */
    private fun offlineFailures(): List<Pair<String, Exception>> = SessionWireFixtures.offlineFailures()

    @Test
    fun `leave offline from the pre-scan screen - the phone leaves locally, the holder is emptied, one request is sent, and the log names the local leave`() {
        for ((label, failure) in offlineFailures()) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                join(h, "sess-1")
                h.cancelFailure = failure
                val vm = leaveVm(h)
                vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
                assertEquals(label, State.Left, vm.state.value)
                assertEquals("$label: the cancellation was attempted once", listOf<Any?>(named("sess-1")), h.cancelRequests.toList())
                assertNull("$label: the holder was emptied", h.holder.getInfo())
                assertEquals(
                    "$label: one named line says the leave was local, and why, with the session hashed",
                    1, h.logLines.count { it.startsWith("session-leave-local role=customer phase=pre-scan reason=offline session=#") && !it.contains("sess-1") }
                )
                assertTrue("$label: the leave line says left", h.logLines.any { it.startsWith("session-leave role=customer phase=pre-scan result=left ") && it.endsWith(" holder=cleared") })
                assertTrue("$label: nothing was refused - no session-refused line", h.logLines.none { it.startsWith("session-refused ") })
                assertTrue("$label: no failed leave line", h.logLines.none { it.contains("result=failed") })
            }
        }
    }

    @Test
    fun `leave offline from the scan screen - the scan's record goes with the local leave, and the session is the one the scan started in`() {
        for ((label, failure) in offlineFailures()) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                join(h, "sess-A")
                h.startScan("scan-1")
                join(h, "sess-B")
                h.cancelFailure = failure
                val vm = leaveVm(h)
                vm.leave(SessionLeaver.Phase.SCAN, vm.sessionOfScan("scan-1")!!, "scan-1")
                assertEquals(label, State.Left, vm.state.value)
                assertEquals("$label: the request named the scan's session", listOf<Any?>(named("sess-A")), h.cancelRequests.toList())
                assertEquals("$label: the holder's session B is not touched", "sess-B", h.holder.get())
                assertNull("$label: the scan will never be analysed: its record is gone", h.records.sessionFor("scan-1", net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS))
                assertTrue(h.logLines.any { it.startsWith("session-leave-local role=customer phase=scan reason=offline session=#") })
                assertTrue(h.logLines.any { it.startsWith("session-leave role=customer phase=scan result=left ") && it.endsWith(" holder=kept-other-session") })
            }
        }
    }

    @Test
    fun `an offline failure that arrives while the call is in flight ends the leave locally`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            h.cancelHold = com.google.android.gms.tasks.TaskCompletionSource()
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals("the call is out", State.InProgress, vm.state.value)
            h.cancelHold!!.setException(offlineFailures().first().second)
            assertEquals("the call failed offline: the leave is local", State.Left, vm.state.value)
            assertNull(h.holder.getInfo())
        }
    }

    /**
     * Batch 5, item 2 (user decision 2, 2026-10-16: «مهلة الاتّصال («failed to connect») تُعدّ «لا اتّصال» فيغادر محلّياً، ومهلة القراءة لا تُعدّ»): a socket CONNECT timeout is no connection, so the
     * phone leaves locally, from both screens. The failure is the SDK's own for it - `DEADLINE_EXCEEDED`, the text `failed to connect to ... after Nms` in the cause only - so a reading
     * that took the CODE for «timeout, so not offline» would fail here and nowhere else in this file. It is one of the shapes of the loops above too ([SessionWireFixtures.offlineFailures]);
     * it is named here so the decision keeps its test if that list changes. The other half, the READ timeout staying a failed leave, is the row «SDK: a socket timeout (DEADLINE_EXCEEDED)» of
     * the test of the failures that are not offline, below (batch 3, item 1; the whole path with the real 30 s of `callWithTimeout` is further below, batch 4, item 3).
     */
    @Test
    fun `leave - a connect timeout (DEADLINE_EXCEEDED, text failed to connect to a host after N ms) is no connection - the phone leaves locally from both screens`() {
        for (phase in SessionLeaver.Phase.values()) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                join(h, "sess-1")
                val scanId = if (phase == SessionLeaver.Phase.SCAN) "scan-1" else null
                if (scanId != null) h.startScan(scanId)
                val failure = SessionWireFixtures.connectTimeoutFailure()
                assertEquals(
                    "$phase: the shape is the SDK's wrapper of a socket timeout, the same code a read timeout gets",
                    com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED, failure.code
                )
                h.cancelFailure = failure
                val vm = leaveVm(h)
                vm.leave(phase, "sess-1", scanId)
                assertEquals("$phase: the leave is local", State.Left, vm.state.value)
                assertEquals("$phase: the cancellation was attempted once", listOf<Any?>(named("sess-1")), h.cancelRequests.toList())
                assertNull("$phase: the holder was emptied", h.holder.getInfo())
                if (scanId != null) {
                    assertNull("$phase: the scan's record went with the leave", h.records.sessionFor(scanId, net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS))
                }
                assertEquals(
                    "$phase: one named line says the leave was local because of no connection, with the session hashed",
                    1, h.logLines.count { it.startsWith("session-leave-local role=customer phase=${phase.wire} reason=offline session=#") && !it.contains("sess-1") }
                )
                assertEquals("$phase: the leave line says left", 1, h.logLines.count { it.startsWith("session-leave role=customer phase=${phase.wire} result=left ") })
                assertTrue("$phase: nothing was refused and nothing failed", h.logLines.none { it.startsWith("session-refused ") || it.contains("result=failed") })
            }
        }
    }

    // ---- SESSION_EXPIRED: the session is over - a successful leave (batch 3, item 1; user decision 4) ----

    private val expiredRefusal get() = SessionWireFixtures.CANCEL_REFUSALS.single { it.reasonCode == "SESSION_EXPIRED" }

    @Test
    fun `leave answered SESSION_EXPIRED - from the pre-scan screen it is a successful leave, with its own log line and no refusal text`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            h.cancelFailure = expiredRefusal.exception()
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals(State.Left, vm.state.value)
            assertEquals(listOf<Any?>(named("sess-1")), h.cancelRequests.toList())
            assertNull("the holder was emptied", h.holder.getInfo())
            assertEquals(1, h.logLines.count { it.startsWith("session-leave-expired role=customer phase=pre-scan session=#") && !it.contains("sess-1") })
            assertTrue(h.logLines.none { it.startsWith("session-leave-local ") })
            assertTrue(h.logLines.any { it.startsWith("session-leave role=customer phase=pre-scan result=left ") && it.endsWith(" holder=cleared") })
            assertTrue("no text was chosen for a refusal that is not one", h.logLines.none { it.startsWith("session-refused ") })
        }
    }

    @Test
    fun `leave answered SESSION_EXPIRED - from the scan screen the scan's record goes too`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            h.startScan("scan-1")
            h.cancelFailure = expiredRefusal.exception()
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.SCAN, "sess-1", "scan-1")
            assertEquals(State.Left, vm.state.value)
            assertNull(h.holder.getInfo())
            assertNull(h.records.sessionFor("scan-1", net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS))
            assertTrue(h.logLines.any { it.startsWith("session-leave-expired role=customer phase=scan session=#") })
            assertTrue(h.logLines.any { it.startsWith("session-leave role=customer phase=scan result=left ") })
        }
    }

    // ---- refusals: every code of table (b) except SESSION_EXPIRED, the text shown ----

    @Test
    fun `leave - every other refusal of the cancel call shows the text of its code, and nothing changes - the session, the holder, the scan's record`() {
        val refusals = SessionWireFixtures.CANCEL_REFUSALS.filter { it.reasonCode != "SESSION_EXPIRED" && it.reasonCode != "SESSION_NOT_PARTICIPANT" }
        assertEquals("table (b) of the Appendix has twelve rows", 12, SessionWireFixtures.CANCEL_REFUSALS.size)
        assertEquals(
            "ten refuse the leave; SESSION_EXPIRED is a successful leave (batch 3) and SESSION_NOT_PARTICIPANT a local one (batch 4, item 2)",
            10, refusals.size
        )
        for (row in refusals) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                join(h, "sess-1")
                h.startScan("scan-1")
                h.cancelFailure = row.exception()
                val vm = leaveVm(h)
                vm.leave(SessionLeaver.Phase.SCAN, "sess-1", "scan-1")
                val shown = (vm.state.value as? State.Failed)?.message
                assertEquals("${row.reasonCode} / ${row.label}", expectedCancelText(row.reasonCode), shown)
                assertEquals("a refused leave keeps the session in the holder", "sess-1", h.holder.get())
                assertNotNull("... and the scan's record", h.records.sessionFor("scan-1", net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS))
                assertEquals(listOf<Any?>(named("sess-1")), h.cancelRequests.toList())
                val refused = h.logLines.single { it.startsWith("session-refused call=cancel-customer ") }
                assertTrue("${row.label}: $refused", refused.contains(" reasonCode=${row.reasonCode} "))
                assertTrue(h.logLines.any { it.startsWith("session-leave role=customer phase=scan result=failed ") && it.endsWith(" holder=n/a") })
            }
        }
    }

    // ---- SESSION_NOT_PARTICIPANT: the server does not know this device as a participant - the phone leaves locally (batch 4, item 2; user decision 2) ----

    private val notParticipantRefusal get() = SessionWireFixtures.CANCEL_REFUSALS.single { it.reasonCode == "SESSION_NOT_PARTICIPANT" }

    /** The same code on four wire shapes: the code decides, never the Functions code or the message (rule 21). */
    private fun notParticipantShapes(): List<Pair<String, Exception>> = listOf(
        "as the cancel sends it (PERMISSION_DENIED)" to notParticipantRefusal.exception(),
        "under NOT_FOUND and the join's text" to SessionWireFixtures.Refusal("x", "NOT_FOUND", SessionWireFixtures.JOIN_INVALID_OR_EXPIRED_MESSAGE, "SESSION_NOT_PARTICIPANT").exception(),
        "under FAILED_PRECONDITION with a message that says network" to SessionWireFixtures.Refusal("x", "FAILED_PRECONDITION", "network", "SESSION_NOT_PARTICIPANT").exception(),
        "under INTERNAL with the message of another code" to SessionWireFixtures.Refusal("x", "INTERNAL", "SESSION_EXPIRED", "SESSION_NOT_PARTICIPANT").exception()
    )

    /**
     * Batch 4, item 2 REPLACES the test that stood here («SESSION_NOT_PARTICIPANT ... shows the generic text and the log names the code»): it pinned the leave FAILING for this answer.
     * User decision 2, 2026-10-16: «SESSION_NOT_PARTICIPANT عند المغادرة: (ب) مغادرة محلّية مع سطر سجلّ». Now, from the pre-scan screen: one request, the holder emptied, the screen says it
     * left (D3), one named log line with its reason, and no refusal text and no refusal line.
     */
    @Test
    fun `leave answered SESSION_NOT_PARTICIPANT from the pre-scan screen - the phone leaves locally, the holder is emptied, and the log names the reason`() {
        for ((label, failure) in notParticipantShapes()) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                join(h, "sess-1")
                h.cancelFailure = failure
                val vm = leaveVm(h)
                vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
                assertEquals(label, State.Left, vm.state.value)
                assertEquals("$label: the cancellation was attempted once", listOf<Any?>(named("sess-1")), h.cancelRequests.toList())
                assertNull("$label: the holder was emptied", h.holder.getInfo())
                assertEquals(
                    "$label: one named line says the leave was local, and why, with the session hashed",
                    1, h.logLines.count { it.startsWith("session-leave-local role=customer phase=pre-scan reason=not_participant session=#") && !it.contains("sess-1") }
                )
                assertTrue("$label: the leave line says left", h.logLines.any { it.startsWith("session-leave role=customer phase=pre-scan result=left ") && it.endsWith(" holder=cleared") })
                assertTrue("$label: nothing was refused - no session-refused line", h.logLines.none { it.startsWith("session-refused ") })
                assertTrue("$label: no failed leave line, no expired line, no offline line", h.logLines.none {
                    it.contains("result=failed") || it.startsWith("session-leave-expired ") || it.contains("reason=offline")
                })
            }
        }
    }

    @Test
    fun `leave answered SESSION_NOT_PARTICIPANT from the scan screen - the scan's record goes with the local leave, and the session joined later is not touched`() {
        for ((label, failure) in notParticipantShapes()) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                join(h, "sess-A")
                h.startScan("scan-1")
                join(h, "sess-B")
                h.cancelFailure = failure
                val vm = leaveVm(h)
                vm.leave(SessionLeaver.Phase.SCAN, vm.sessionOfScan("scan-1")!!, "scan-1")
                assertEquals(label, State.Left, vm.state.value)
                assertEquals("$label: the request named the scan's session", listOf<Any?>(named("sess-A")), h.cancelRequests.toList())
                assertEquals("$label: the holder's session B is not touched", "sess-B", h.holder.get())
                assertNull("$label: the scan will never be analysed: its record is gone", h.records.sessionFor("scan-1", net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS))
                assertTrue(h.logLines.any { it.startsWith("session-leave-local role=customer phase=scan reason=not_participant session=#") })
                assertTrue(h.logLines.any { it.startsWith("session-leave role=customer phase=scan result=left ") && it.endsWith(" holder=kept-other-session") })
                assertTrue("$label: no session-refused line", h.logLines.none { it.startsWith("session-refused ") })
            }
        }
    }

    @Test
    fun `LeaveFailureRead - SESSION_NOT_PARTICIPANT is its own named state, decided by the exact code alone - a code that only looks like it is a refusal`() {
        for ((label, failure) in notParticipantShapes()) assertEquals(label, LeaveFailureRead.NotParticipant, LeaveFailureRead.of(failure))
        assertEquals(
            "the exact wire text only (rule 21 is about the Functions code and the message; the code itself is matched as the server writes it)",
            LeaveFailureRead.Refused, LeaveFailureRead.of(SessionWireFixtures.Refusal("x", "PERMISSION_DENIED", "جلسة الفحص غير صالحة", "session_not_participant").exception())
        )
        assertEquals("the other codes stay what they were", LeaveFailureRead.Refused, LeaveFailureRead.of(SessionWireFixtures.CANCEL_REFUSALS.single { it.reasonCode == "SESSION_COMPLETED" }.exception()))
        assertEquals(LeaveFailureRead.SessionExpired, LeaveFailureRead.of(expiredRefusal.exception()))
    }

    @Test
    fun `leave - a refusal with no reasonCode and an unknown one each show what that point showed before, and the leave does not happen`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            h.cancelFailure = SessionWireFixtures.CANCEL_REFUSALS_WITHOUT_CODE.single().exception()
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals(existing(R.string.error_generic), (vm.state.value as State.Failed).message)
            assertTrue(h.logLines.single { it.startsWith("session-refused call=cancel-customer ") }.contains(" reasonCode=absent "))
        }
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            h.cancelFailure = SessionWireFixtures.CANCEL_REFUSAL_UNRECOGNIZED.exception()
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals(existing(R.string.error_generic), (vm.state.value as State.Failed).message)
            assertTrue(h.logLines.single { it.startsWith("session-refused call=cancel-customer ") }.contains(" reasonCode=unrecognized "))
        }
    }

    /**
     * Batch 3, item 1 replaces here the test that pinned «no connection, no leave» (it set a bare `IOException("Unable to resolve host")` and expected the
     * connection text and a held session, rule 23). What stays a FAILED leave - text shown, nothing changed - is every failure that is not «no connection» as the
     * app reads it (`LeaveFailureRead`): a timeout (the request may have arrived), a transport failure whose text names no connection phrase, and a failure that
     * carries a server `reasonCode` - even one whose message says "network" - because the server answered.
     */
    @Test
    fun `leave - a timeout, a transport failure with no connection text, and a refusal that merely says network, are NOT an offline leave - the customer stays, nothing changes`() {
        val notOffline: List<Pair<String, Exception>> = listOf(
            "the call timed out (callWithTimeout's IOException)" to java.io.IOException("Callable 'cancelScanSession' timed out after 30000ms"),
            "SDK: a socket timeout (DEADLINE_EXCEEDED)" to SessionWireFixtures.exceptionFromTransportFailure(java.net.SocketTimeoutException("timeout")),
            "SDK: a reset connection" to SessionWireFixtures.exceptionFromTransportFailure(java.io.IOException("Connection reset")),
            "SDK: a TLS failure" to SessionWireFixtures.exceptionFromTransportFailure(javax.net.ssl.SSLException("Read error: ssl=0x1: I/O error during system call")),
            "an unrecognized reasonCode whose message says network" to SessionWireFixtures.Refusal("x", "FAILED_PRECONDITION", "network error", "SESSION_BRAND_NEW_REASON").exception(),
            "a reasonCode whose text is network (batch 4, item 1)" to SessionWireFixtures.REASON_CODE_NETWORK_REFUSAL.exception(),
            "a known reasonCode whose message says Unable to resolve host" to SessionWireFixtures.Refusal("x", "FAILED_PRECONDITION", "Unable to resolve host", "SESSION_STATUS_UNKNOWN").exception()
        )
        for ((label, failure) in notOffline) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                join(h, "sess-1")
                h.startScan("scan-1")
                h.cancelFailure = failure
                val vm = leaveVm(h)
                vm.leave(SessionLeaver.Phase.SCAN, "sess-1", "scan-1")
                assertTrue("$label: the leave failed and the customer stays (${vm.state.value})", vm.state.value is State.Failed)
                assertEquals("$label: the holder keeps the session", "sess-1", h.holder.get())
                assertNotNull("$label: the scan's record stays", h.records.sessionFor("scan-1", net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS))
                assertTrue("$label: no local leave was printed", h.logLines.none { it.startsWith("session-leave-local ") || it.startsWith("session-leave-expired ") })
                assertTrue("$label: the failed leave was printed", h.logLines.any { it.startsWith("session-leave role=customer phase=scan result=failed ") })
                // batch 4, item 1: none of them is a disconnect for the text either - the connection text is never shown for a leave that did not happen offline
                assertEquals(
                    "$label: the text of a failure that is not a disconnect",
                    existing(R.string.error_generic), (vm.state.value as State.Failed).message
                )
            }
        }
    }

    /**
     * The measured fact the offline decision rests on, kept in the suite: the SDK's own callback turns a transport `IOException` into a
     * `FirebaseFunctionsException` whose MESSAGE is the bare code name and whose CAUSE carries the connection text - so a reading of the message alone does NOT see it, and
     * the connection reading has to follow the cause chain. If a later SDK puts the text in the message, this test fails first and says the assumption changed.
     * Batch 4, item 1 REPLACES the last assertion here, which pinned the old behaviour (`networkErrorMessageOrNull`, the message-only reading, returned null for this
     * failure): that function is now the same reading the leave uses, and it finds the text.
     */
    @Test
    fun `the SDK turns a transport IOException into an exception whose message is the bare code and whose cause has the connection text - and the connection reading finds it there`() {
        val io = java.net.UnknownHostException("Unable to resolve host \"cloudfunctions.net\": No address associated with hostname")
        val e = SessionWireFixtures.exceptionFromTransportFailure(io)
        assertEquals("INTERNAL", e.message)
        assertEquals(com.google.firebase.functions.FirebaseFunctionsException.Code.INTERNAL, e.code)
        assertNull(e.details)
        assertTrue("the cause is the transport exception itself", e.cause === io)
        assertEquals(
            "the one connection reading follows the cause: it sees this failure, and says the connection text",
            existing(R.string.login_error_network),
            net.neuralcheck.domain.scoring.networkErrorMessageOrNull(context, e)
        )
        assertEquals(
            "a socket timeout is an InterruptedIOException: the SDK calls it DEADLINE_EXCEEDED",
            com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            SessionWireFixtures.exceptionFromTransportFailure(java.net.SocketTimeoutException("timeout")).code
        )
    }

    @Test
    fun `LeaveFailureRead - the cause chain is read to its bound, a cycle in it ends, and a server reasonCode wins over any text`() {
        val a = RuntimeException("outer")
        val b = RuntimeException("inner", a)
        a.initCause(b) // a -> b -> a: a cycle no real SDK makes; the read must still end
        assertEquals(net.neuralcheck.data.remote.LeaveFailureRead.Refused, net.neuralcheck.data.remote.LeaveFailureRead.of(a))
        var deep: Throwable = java.net.UnknownHostException("Unable to resolve host")
        repeat(7) { deep = RuntimeException("wrapper", deep) } // the text is the 8th message: read
        assertEquals(net.neuralcheck.data.remote.LeaveFailureRead.NoConnection, net.neuralcheck.data.remote.LeaveFailureRead.of(deep))
        repeat(2) { deep = RuntimeException("wrapper", deep) } // the 10th: past the bound, not read
        assertEquals(net.neuralcheck.data.remote.LeaveFailureRead.Refused, net.neuralcheck.data.remote.LeaveFailureRead.of(deep))
        assertEquals(
            net.neuralcheck.data.remote.LeaveFailureRead.SessionExpired,
            net.neuralcheck.data.remote.LeaveFailureRead.of(SessionWireFixtures.Refusal("x", "FAILED_PRECONDITION", "network", "SESSION_EXPIRED").exception())
        )
    }

    @Test
    fun `leave - a failed leave can be tried again, and a dismissed one goes back to nothing happening`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            // not an offline failure: that one now leaves locally (batch 3, item 1). The server's own `internal` is a refusal with no reasonCode and no connection text.
            h.cancelFailure = SessionWireFixtures.CANCEL_REFUSALS_WITHOUT_CODE.single().exception()
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertTrue(vm.state.value is State.Failed)
            vm.dismiss()
            assertEquals(State.Idle, vm.state.value)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertTrue(vm.state.value is State.Failed)
            h.cancelFailure = null // the connection is back
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals(State.Left, vm.state.value)
            assertNull(h.holder.getInfo())
            assertEquals("three attempts, three requests", 3, h.cancelRequests.size)
        }
    }

    @Test
    fun `leave - a success answer that does not say cancelled is a failure, the holder keeps the session`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            h.cancelResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "scanning")
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals(existing(R.string.error_generic), (vm.state.value as State.Failed).message)
            assertEquals("sess-1", h.holder.get())
        }
    }

    @Test
    fun `leave - a second tap while the first is out sends nothing, and a leave that succeeded is not repeated`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            val hold = com.google.android.gms.tasks.TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
            h.cancelHold = hold
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals(State.InProgress, vm.state.value)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals(1, h.cancelRequests.size)
            hold.setResult(h.callableResultOf(hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "cancelled", "alreadyCancelled" to false)))
            assertEquals(State.Left, vm.state.value)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals("after Left, nothing more is sent", 1, h.cancelRequests.size)
        }
    }

    /**
     * Batch 4, item 3 (user decision 3, 2026-10-16: «انتهاء المهلة أثناء المغادرة: يبقى فشلاً، وإعادة المحاولة تُرجع «أُلغيت سابقاً» فيغادر»), the whole path, from both screens. The first attempt's
     * call is out and never answers: the REAL `callWithTimeout` ends it after its 30 seconds (the test advances the virtual clock the view model's coroutine runs on) with the
     * `IOException` it throws - not «no connection» (the request may have reached the server), so the leave FAILED and the customer stays: holder, scan record and session as they
     * were. The second attempt is answered by the server, which had received the first request and cancelled the session: `alreadyCancelled: true`, a confirmed cancellation
     * ([net.neuralcheck.data.remote.SessionCanceller] returns it like the first), so the customer leaves. The failed attempt is printed as failed and the retry as left.
     */
    @Test
    fun `leave - a timeout stays a failure and the customer stays, and the retry, answered alreadyCancelled true, leaves`() {
        for (phase in SessionLeaver.Phase.values()) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                join(h, "sess-1")
                val scanId = if (phase == SessionLeaver.Phase.SCAN) "scan-1" else null
                if (scanId != null) h.startScan(scanId)
                h.cancelHold = com.google.android.gms.tasks.TaskCompletionSource() // the call is out and never answers
                val vm = leaveVm(h)
                vm.leave(phase, "sess-1", scanId)
                assertEquals("$phase: the call is out", State.InProgress, vm.state.value)

                testDispatcher.scheduler.advanceTimeBy(120_000) // past the 30 s of callWithTimeout
                testDispatcher.scheduler.runCurrent()
                val failed = vm.state.value
                assertTrue("$phase: the timeout is a FAILED leave ($failed)", failed is State.Failed)
                assertEquals("$phase: the text of a failure that is neither a disconnect nor a code", existing(R.string.error_generic), (failed as State.Failed).message)
                assertEquals("$phase: the customer stays - the holder keeps the session", "sess-1", h.holder.get())
                if (scanId != null) {
                    assertNotNull("$phase: ... and the scan's record", h.records.sessionFor(scanId, net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS))
                }
                assertEquals("$phase: one request so far", 1, h.cancelRequests.size)
                assertTrue("$phase: no local leave, no expired leave", h.logLines.none { it.startsWith("session-leave-local ") || it.startsWith("session-leave-expired ") })
                assertEquals("$phase: the failed attempt is printed as failed", 1, h.logLines.count { it.startsWith("session-leave role=customer phase=${phase.wire} result=failed ") })
                assertTrue("$phase: no left line yet", h.logLines.none { it.contains("result=left") })

                // the retry: the first request had arrived, and the server cancelled the session
                h.cancelHold = null
                h.cancelResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "cancelled", "alreadyCancelled" to true)
                vm.dismiss()
                assertEquals(State.Idle, vm.state.value)
                vm.leave(phase, "sess-1", scanId)
                assertEquals("$phase: the retry leaves", State.Left, vm.state.value)
                assertEquals("$phase: two attempts, two requests", listOf<Any?>(named("sess-1"), named("sess-1")), h.cancelRequests.toList())
                assertNull("$phase: the holder was emptied", h.holder.getInfo())
                if (scanId != null) {
                    assertNull("$phase: the scan's record went with the leave", h.records.sessionFor(scanId, net.neuralcheck.data.remote.ScanSessionRecords.Use.ANALYSIS))
                }
                assertTrue("$phase: the server's answer was the already-cancelled one", h.logLines.any { it.startsWith("session-cancel-answer role=customer outcome=already-cancelled ") })
                assertEquals("$phase: the retry is printed as left", 1, h.logLines.count { it.startsWith("session-leave role=customer phase=${phase.wire} result=left ") })
            }
        }
    }

    @Test
    fun `leave - a second cancel of a session already cancelled (alreadyCancelled true) is a success`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            join(h, "sess-1")
            h.cancelResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "cancelled", "alreadyCancelled" to true)
            val vm = leaveVm(h)
            vm.leave(SessionLeaver.Phase.PRE_SCAN, "sess-1")
            assertEquals(State.Left, vm.state.value)
            assertTrue(h.logLines.any { it.startsWith("session-cancel-answer role=customer outcome=already-cancelled ") })
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionLeavePathArabicTest : SessionLeavePathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.ar
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr")
class SessionLeavePathTurkishTest : SessionLeavePathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.tr
}
