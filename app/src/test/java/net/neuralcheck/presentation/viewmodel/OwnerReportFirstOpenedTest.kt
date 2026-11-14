package net.neuralcheck.presentation.viewmodel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import net.neuralcheck.util.EvalCardLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The owner's side of the report's first open - A-P6-08, the form's blindness. [GenerateSessionViewModel.onReportOpened] is what the owner's «عرض التقرير» button calls just before it navigates: the report is
 * about to be shown for the first time on the owner's phone. Real: the view model, the owner's session listener (a snapshot is delivered to it), [net.neuralcheck.data.remote.ReportFirstOpenedRecorder],
 * [net.neuralcheck.data.remote.FirebaseAnalysisService.recordReportFirstOpened], the log. Replaced: the HTTP round trip, Firestore and auth (the harness) and - declared - the form's exit gate: opened by
 * [RealJoinPathHarness.openFormCalls] for most tests, shut by [RealJoinPathHarness.closeFormCalls] in the one that shows a shut gate, and left as built in the one that runs the REAL gate (rule 37: the guard is
 * true since 2026-10-23, so it is open).
 *
 * The customer's side - `ReportViewModel.loadScan` - cannot be built here (its constructor, `ViewModels.kt`'s own note at `ReportViewModel`): it is pinned as source shape in `TechnicianFormGuardTest`,
 * and what it calls is `ReportFirstOpenedRecorderTest`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerReportFirstOpenedTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context = mock<Context>().also {
        whenever(it.getString(R.string.error_generic)).thenReturn("generic-text")
    }

    /** [gateOpen]: `true` opens the gate (a test says what it relies on), `false` SHUTS it (the real one reads the guard, which is true), `null` leaves the REAL gate as the service was built (rule 37). */
    private fun withOwner(gateOpen: Boolean? = true, body: (RealJoinPathHarness, GenerateSessionViewModel) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            when (gateOpen) {
                true -> h.openFormCalls()
                false -> h.closeFormCalls()
                null -> Unit
            }
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            assertEquals("the session was created and the owner is waiting", Phase.WAITING, vm.state.value.phase)
            body(h, vm)
        }
    }

    private fun RealJoinPathHarness.complete() = deliverSnapshot("sess-1", fields = mapOf("status" to "completed", "scanId" to "scan-9"))

    @Test
    fun `the first tap on the report button of a completed session tells the server - once - with the session's id`() = withOwner { h, vm ->
        h.complete()
        assertEquals(Phase.COMPLETED, vm.state.value.phase)
        vm.onReportOpened()
        assertEquals(1, h.callableCalls("recordReportFirstOpened"))
        assertEquals(listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1")), h.reportOpenedRequests.toList())
        assertTrue(h.logLines.toString(), EvalCardLog.reportFirstOpenedLine("sess-1", "owner", "recorded", "-") in h.logLines)
        vm.onReportOpened()
        vm.onReportOpened()
        assertEquals("answered once: the same session's report is not announced again by this screen", 1, h.callableCalls("recordReportFirstOpened"))
    }

    @Test
    fun `an already-recorded answer is an answer too - the screen does not ask again`() = withOwner { h, vm ->
        h.complete()
        h.reportOpenedResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "recorded" to false)
        vm.onReportOpened()
        vm.onReportOpened()
        assertEquals("recorded: false is an answer", 1, h.callableCalls("recordReportFirstOpened"))
        assertTrue(EvalCardLog.reportFirstOpenedLine("sess-1", "owner", "already_recorded", "-") in h.logLines)
    }

    @Test
    fun `a refusal does not stop the owner - the tap asks again next time, and nothing the screen shows waited on it`() = withOwner { h, vm ->
        h.complete()
        h.reportOpenedFailure = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody("FAILED_PRECONDITION", "made-up message", "SESSION_REPORT_NOT_AVAILABLE"))
        vm.onReportOpened()
        assertEquals(Phase.COMPLETED, vm.state.value.phase)
        assertEquals(1, h.callableCalls("recordReportFirstOpened"))
        assertTrue(h.logLines.toString(), EvalCardLog.reportFirstOpenedLine("sess-1", "owner", "refused", "SESSION_REPORT_NOT_AVAILABLE") in h.logLines)
        h.reportOpenedFailure = null
        vm.onReportOpened()
        assertEquals("asked again after the refusal", 2, h.callableCalls("recordReportFirstOpened"))
        vm.onReportOpened()
        assertEquals("and not again after the answer", 2, h.callableCalls("recordReportFirstOpened"))
    }

    @Test
    fun `before the session is completed there is no report to open - nothing is sent`() = withOwner { h, vm ->
        vm.onReportOpened()
        h.deliverSnapshot("sess-1", fields = mapOf("status" to "scanning"))
        vm.onReportOpened()
        assertEquals(0, h.callableCalls("recordReportFirstOpened"))
    }

    /**
     * Rule 23: REPLACES `the real gate is closed - the tap on a completed session sends nothing and prints nothing` (it pinned the guard false, and the real gate with it). The guard is TRUE since 2026-10-23;
     * the REAL gate - the service as built, nothing opened or shut by the test - reads it, so the tap tells the server and prints.
     */
    @Test
    fun `the real gate is open - the guard is true, so the tap on a completed session leaves the phone through the real service, once, and is printed`() = withOwner(gateOpen = null) { h, vm ->
        assertTrue("the guard of the form's surface is true since 2026-10-23", net.neuralcheck.presentation.screen.TECH_FORM_TEXTS_APPROVED)
        assertTrue("nobody opened the gate", h.service.formCallsOpen())
        h.complete()
        vm.onReportOpened()
        vm.onReportOpened()
        assertEquals(1, h.callableCalls("recordReportFirstOpened"))
        assertTrue(h.logLines.toString(), EvalCardLog.reportFirstOpenedLine("sess-1", "owner", "recorded", "-") in h.logLines)
    }

    @Test
    fun `a shut gate - the tap on a completed session sends nothing and prints nothing`() = withOwner(gateOpen = false) { h, vm ->
        // the repeat scan's gate is ANOTHER gate (open since 2026-10-23): a completed session asks the repeat offer through it, so it is shut here to judge the form's gate alone
        h.closeRepeatCalls()
        h.complete()
        vm.onReportOpened()
        vm.onReportOpened()
        assertEquals("no callable call at all", 0, h.events.count { it.startsWith("callable:") && it != "callable:createScanSession" })
        assertEquals(0, h.callableCalls("recordReportFirstOpened"))
        assertTrue(h.logLines.toString(), h.logLines.none { it.startsWith("report-first-opened ") })
        assertEquals("and the screen is as it was", Phase.COMPLETED, vm.state.value.phase)
    }
}
