package net.neuralcheck.presentation.viewmodel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.data.remote.RepeatScanClient
import net.neuralcheck.domain.repeat.RepeatDecision
import net.neuralcheck.domain.repeat.RepeatState
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import net.neuralcheck.util.EvalCardLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The technician's phone and the repeat scan - A-P6-07, screen ف١٤ (PLAN-06:956, :629: «جهاز الفنّي يعرض بعد اكتمال الفحص الأوّل: «اختير هذا الهاتف لفحص مكرّر بارد»»). Rules 22, 30, 37. Real:
 * [GenerateSessionViewModel], the owner's session listener (a snapshot is delivered to it), [RepeatScanClient], `FirebaseAnalysisService.getRepeatScanOffer`, the reader, the log. Replaced: the HTTP round trip,
 * Firestore and auth (the harness) and - declared - the repeat scan's exit gate: opened by [RealJoinPathHarness.openRepeatCalls] for most tests, shut by [RealJoinPathHarness.closeRepeatCalls] in the one that
 * shows a shut gate, and left as built in the one that runs the REAL gate (rule 37: the guard is true since 2026-10-23, so it is open).
 *
 * The answers are written from the contract (A4 (5)), not produced by the server, which is not deployed. What the SCREEN draws for a notice is `RepeatScanSurfaceTest`'s (with the test's own labels) and
 * `RepeatLiveScreenShapeTest`'s (with the real ones).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerRepeatNoticeTest {

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
                true -> h.openRepeatCalls()
                false -> h.closeRepeatCalls()
                null -> Unit
            }
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            assertEquals("the session was created and the owner is waiting", Phase.WAITING, vm.state.value.phase)
            body(h, vm)
        }
    }

    private fun RealJoinPathHarness.complete(scanId: String = "scan-9") = deliverSnapshot("sess-1", fields = mapOf("status" to "completed", "scanId" to scanId))

    private fun answer(state: String = "SELECTED", scanId: String = "scan-9", temp: Map<String, Any?>? = mapOf("state" to "PRESENT", "valueC" to 32.8), reason: String? = null) = hashMapOf<String, Any?>(
        "sessionId" to "sess-1", "scanId" to scanId, "state" to state, "reason" to reason, "firstScanStartTemp" to temp, "childSessionId" to null, "childStatus" to null
    )

    @Test
    fun `a completed session whose phone was chosen shows the notice - one question for the pair, as the owner`() = withOwner { h, vm ->
        h.repeatOfferResponse = answer()
        assertEquals("nothing is asked before the session is completed", RepeatNoticeUi.NotAsked, vm.state.value.repeatNotice)
        assertEquals(0, h.callableCalls("getRepeatScanOffer"))
        h.complete()
        assertEquals(Phase.COMPLETED, vm.state.value.phase)
        assertEquals(RepeatNoticeUi.Notice(dismissed = false), vm.state.value.repeatNotice)
        assertEquals(1, h.callableCalls("getRepeatScanOffer"))
        assertEquals(listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1", "scanId" to "scan-9")), h.repeatOfferRequests.toList())
        assertTrue(h.logLines.toString(), EvalCardLog.repeatScanLine("offer", "owner", "sess-1", "answered", "startable_selected") in h.logLines)
        h.complete()
        h.complete()
        assertEquals("the same completion delivered again does not ask again", 1, h.callableCalls("getRepeatScanOffer"))
    }

    @Test
    fun `a notice is for the four startable states - and every other state shows nothing, with its own token`() {
        val expected = mapOf(
            RepeatState.NOT_ELIGIBLE to RepeatNoticeUi.NothingToOffer("not_applicable_not_eligible"),
            RepeatState.NOT_SELECTED to RepeatNoticeUi.NothingToOffer("not_applicable_not_selected"),
            RepeatState.SELECTED to RepeatNoticeUi.Notice(),
            RepeatState.PROMPT_DELIVERED to RepeatNoticeUi.Notice(),
            RepeatState.DECLINED to RepeatNoticeUi.Notice(),
            RepeatState.WAITING_COOLING to RepeatNoticeUi.Notice(),
            RepeatState.STARTED to RepeatNoticeUi.NothingToOffer("not_startable_started"),
            RepeatState.COMPLETED_COLD to RepeatNoticeUi.NothingToOffer("not_startable_completed_cold"),
            RepeatState.COMPLETED_HOT to RepeatNoticeUi.NothingToOffer("not_startable_completed_hot"),
            RepeatState.COMPLETED_UNCLASSIFIED to RepeatNoticeUi.NothingToOffer("not_startable_completed_unclassified"),
            RepeatState.EXPIRED to RepeatNoticeUi.NothingToOffer("not_startable_expired")
        )
        assertEquals("every state is in the table", RepeatState.values().toSet(), expected.keys)
        for ((state, ui) in expected) {
            withOwner { h, vm ->
                val none = state == RepeatState.NOT_ELIGIBLE || state == RepeatState.NOT_SELECTED
                h.repeatOfferResponse = answer(state = state.wire, temp = if (none) null else mapOf("state" to "PRESENT", "valueC" to 32.8), reason = if (state == RepeatState.NOT_ELIGIBLE) "PART_NOT_ENABLED" else null)
                h.complete()
                assertEquals("$state", ui, vm.state.value.repeatNotice)
            }
        }
    }

    @Test
    fun `an answer that cannot be read, a refusal and a failure are named states of their own - nothing is drawn for any, and the log says which`() {
        val cases = listOf<Triple<String, (RealJoinPathHarness) -> Unit, String>>(
            Triple("an empty answer", { h -> h.repeatOfferResponse = hashMapOf<String, Any?>() }, "unreadable_echo_sessionId_missing"),
            Triple("a refusal", { h -> h.repeatOfferFailure = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody("FAILED_PRECONDITION", "x", "REPEAT_RECORD_ABSENT")) }, "refused_REPEAT_RECORD_ABSENT"),
            Triple("a disconnect", { h -> h.repeatOfferFailure = SessionWireFixtures.offlineFailures().first().second }, "not_sent")
        )
        for ((label, arrange, token) in cases) {
            withOwner { h, vm ->
                arrange(h)
                h.complete()
                val ui = vm.state.value.repeatNotice
                assertTrue("$label: $ui", ui is RepeatNoticeUi.Unavailable)
                assertEquals(label, RepeatNoticeUi.Unavailable(token), ui)
            }
        }
    }

    @Test
    fun `the notice is the owner's to dismiss on this screen - nothing is sent, and a tap with no notice does nothing`() = withOwner { h, vm ->
        vm.dismissRepeatNotice()
        assertEquals(RepeatNoticeUi.NotAsked, vm.state.value.repeatNotice)
        h.repeatOfferResponse = answer()
        h.complete()
        vm.dismissRepeatNotice()
        assertEquals(RepeatNoticeUi.Notice(dismissed = true), vm.state.value.repeatNotice)
        assertEquals("«ليس الآن» sends nothing: the plan names no call for it", 1, h.events.count { it.startsWith("callable:") && it != "callable:createScanSession" })
    }

    @Test
    fun `a new code resets the notice - the next session starts with nothing asked`() = withOwner { h, vm ->
        h.repeatOfferResponse = answer()
        h.complete()
        assertTrue(vm.state.value.repeatNotice is RepeatNoticeUi.Notice)
        vm.create()
        assertEquals(RepeatNoticeUi.NotAsked, vm.state.value.repeatNotice)
    }

    /**
     * Rule 23: REPLACES `the real gate is closed - a completed session asks nothing, prints nothing and changes nothing` (it pinned the guard false, and the real gate with it). The guard is TRUE since
     * 2026-10-23; the REAL gate - the service as built, nothing opened or shut by the test - reads it, so a completed session asks the offer once, through the real service, and the notice shows.
     */
    @Test
    fun `the real gate is open - the guard is true, so a completed session asks the offer through the real service, once, and the notice shows`() = withOwner(gateOpen = null) { h, vm ->
        assertTrue("the guard of the surfaces is true since 2026-10-23", net.neuralcheck.presentation.screen.REPEAT_TEXTS_APPROVED)
        assertTrue("nobody opened the gate", h.service.repeatCallsOpen())
        h.repeatOfferResponse = answer()
        h.complete()
        h.complete()
        assertEquals(1, h.callableCalls("getRepeatScanOffer"))
        assertEquals(RepeatNoticeUi.Notice(dismissed = false), vm.state.value.repeatNotice)
        assertTrue(h.logLines.toString(), EvalCardLog.repeatScanLine("offer", "owner", "sess-1", "answered", "startable_selected") in h.logLines)
    }

    @Test
    fun `a shut gate - a completed session asks nothing, prints nothing and changes nothing`() = withOwner(gateOpen = false) { h, vm ->
        h.complete()
        h.complete()
        assertEquals("no callable call at all", 0, h.events.count { it.startsWith("callable:") && it != "callable:createScanSession" })
        assertTrue(h.logLines.toString(), h.logLines.none { it.startsWith("repeat-scan ") })
        assertEquals("the screen is as it was", RepeatNoticeUi.NotAsked, vm.state.value.repeatNotice)
        assertEquals(Phase.COMPLETED, vm.state.value.phase)
    }

    @Test
    fun `the notice's mapping is total - every outcome of the call is one state`() {
        val startable = RepeatDecision.Startable(RepeatState.SELECTED, net.neuralcheck.domain.repeat.StartableReference.Usable(32.8))
        assertEquals(RepeatNoticeUi.NotAsked, repeatNoticeFor(RepeatScanClient.OfferOutcome.GuardClosed))
        assertEquals(RepeatNoticeUi.Notice(), repeatNoticeFor(RepeatScanClient.OfferOutcome.Answered(startable)))
        assertEquals(RepeatNoticeUi.Unavailable("not_sent"), repeatNoticeFor(RepeatScanClient.OfferOutcome.NotSent))
        assertEquals(RepeatNoticeUi.Unavailable("refused_X"), repeatNoticeFor(RepeatScanClient.OfferOutcome.Refused("X", null)))
        assertEquals(RepeatNoticeUi.Unavailable("unreadable_y"), repeatNoticeFor(RepeatScanClient.OfferOutcome.Answered(RepeatDecision.Unreadable("y"))))
    }
}
