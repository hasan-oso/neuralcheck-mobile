package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.remote.RepeatScanClient
import net.neuralcheck.data.remote.ScanSessionRecords
import net.neuralcheck.domain.repeat.CoolingDecision
import net.neuralcheck.domain.repeat.CoolingSide
import net.neuralcheck.domain.repeat.RepeatRefusal
import net.neuralcheck.domain.repeat.RepeatState
import net.neuralcheck.domain.repeat.TempRead
import net.neuralcheck.util.EvalCardLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The scanned phone and the repeat scan - A-P6-07, screen ف١٣ (PLAN-06:955, :630-636, :639). Rules 22, 30, 37. Real: [ScannedRepeatCooling], [RepeatScanClient], `FirebaseAnalysisService.getRepeatScanOffer` /
 * `startRepeatScan`, the reader, the cooling decision, the REAL [net.neuralcheck.data.remote.ScanSessionHolder] and [ScanSessionRecords] (the child session must end up recorded for the scan that starts next: the
 * record is what the analysis reads). Replaced: only the HTTP round trip and auth (the harness) and - declared - the repeat scan's exit gate (opened for most tests, shut for the one that shows a shut gate, and
 * left as built for the one that runs the REAL gate, which is open: the guard is true since 2026-10-23).
 *
 * The answers are written from the contract (A4 (5), (4)), not produced by the server, which is not deployed. The phone's temperature is fed by the test ([TempRead]); what reads the real battery broadcast is
 * `BatteryTemperatureTest`'s. `ReportViewModel` (which owns this controller in the app) cannot be built in a test (its constructor, see `OwnerReportFirstOpenedTest`): what it does with it is `RepeatScanGuardTest`'s
 * source shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScannedRepeatCoolingRealPathTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** [gateOpen]: `true` opens the gate (a test says what it relies on), `false` SHUTS it (the real one reads the guard, which is true), `null` leaves the REAL gate as the service was built (rule 37). */
    private fun withScanned(gateOpen: Boolean? = true, body: RealJoinPathHarness.(ScannedRepeatCooling) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            when (gateOpen) {
                true -> h.openRepeatCalls()
                false -> h.closeRepeatCalls()
                null -> Unit
            }
            h.body(ScannedRepeatCooling(RepeatScanClient(h.service)) { child -> h.holder.adoptRepeatChild(child) })
        }
    }

    private fun offerAnswer(state: String = "SELECTED", temp: Map<String, Any?>? = mapOf("state" to "PRESENT", "valueC" to 32.8), reason: String? = null) = hashMapOf<String, Any?>(
        "sessionId" to "sess-1", "scanId" to "scan-1", "state" to state, "reason" to reason, "firstScanStartTemp" to temp, "childSessionId" to null, "childStatus" to null
    )

    private fun startAnswer() = hashMapOf<String, Any?>("sessionId" to "child-1", "parentSessionId" to "sess-1", "state" to "STARTED", "expiresInMinutes" to 60)

    private fun ScannedRepeatCooling.shown() = runBlocking { onReportShown("sess-1", "scan-1") }

    private fun ScannedRepeatCooling.cooling(): ScannedRepeatUi.Cooling = state.value as ScannedRepeatUi.Cooling

    // ---- the offer, and the reference ----

    @Test
    fun `a chosen phone gets a cooling screen with the first scan's temperature in tenths - and no decision until the phone's own temperature is read`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        assertEquals(ScannedRepeatUi.Idle, c.state.value)
        c.shown()
        assertEquals(ScannedRepeatUi.Cooling(referenceTenths = 328, decision = null, waitChosen = false, start = RepeatStartUi.Idle), c.state.value)
        assertEquals(listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1", "scanId" to "scan-1")), repeatOfferRequests.toList())
        assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("offer", "scanned", "sess-1", "answered", "startable_selected") in logLines)
        c.shown()
        c.shown()
        assertEquals("one question for the pair", 1, callableCalls("getRepeatScanOffer"))
    }

    @Test
    fun `the decision follows the phone - warm, within the degree, cooler than the band, and no reading at all`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        c.shown()
        c.onTemperature(TempRead.Present(378))
        assertEquals(CoolingDecision.NotCooled(CoolingSide.ABOVE, 0f), c.cooling().decision)
        c.onTemperature(TempRead.Present(358))
        assertEquals(0.5f, (c.cooling().decision as CoolingDecision.NotCooled).progress, 0.001f)
        c.onTemperature(TempRead.Present(338))
        assertEquals(CoolingDecision.Ready, c.cooling().decision)
        c.onTemperature(TempRead.Present(300))
        assertEquals(CoolingDecision.NotCooled(CoolingSide.BELOW, 0f), c.cooling().decision)
        c.onTemperature(TempRead.Unreadable("no_temperature_extra"))
        assertEquals(CoolingDecision.TemperatureUnreadable("no_temperature_extra"), c.cooling().decision)
    }

    @Test
    fun `a reading that arrives before the offer's answer is not lost`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        c.onTemperature(TempRead.Present(330))
        assertEquals("nothing asked yet: nothing to decide", ScannedRepeatUi.Idle, c.state.value)
        c.shown()
        assertEquals(CoolingDecision.Ready, c.cooling().decision)
    }

    @Test
    fun `every state of the offer is one state of the screen - only a startable offer with a usable reference is a cooling screen`() {
        val expectedToken = mapOf(
            RepeatState.NOT_ELIGIBLE to "not_applicable_not_eligible",
            RepeatState.NOT_SELECTED to "not_applicable_not_selected",
            RepeatState.STARTED to "not_startable_started",
            RepeatState.COMPLETED_COLD to "not_startable_completed_cold",
            RepeatState.COMPLETED_HOT to "not_startable_completed_hot",
            RepeatState.COMPLETED_UNCLASSIFIED to "not_startable_completed_unclassified",
            RepeatState.EXPIRED to "not_startable_expired"
        )
        for (state in RepeatState.values()) {
            withScanned { c ->
                val none = state == RepeatState.NOT_ELIGIBLE || state == RepeatState.NOT_SELECTED
                repeatOfferResponse = offerAnswer(state = state.wire, temp = if (none) null else mapOf("state" to "PRESENT", "valueC" to 32.8), reason = if (state == RepeatState.NOT_ELIGIBLE) "RATE_INVALID" else null)
                c.shown()
                if (state.startable) assertTrue("$state: ${c.state.value}", c.state.value is ScannedRepeatUi.Cooling)
                else assertEquals("$state", ScannedRepeatUi.NotOffered(expectedToken.getValue(state)), c.state.value)
            }
        }
    }

    @Test
    fun `a chosen phone whose reference is not usable draws nothing and says why - the sentinel, an absent one, a malformed one`() {
        val cases = listOf(
            mapOf("state" to "SENTINEL_ZERO", "valueC" to 0.0) to "reference_sentinel_zero",
            mapOf("state" to "ABSENT", "valueC" to null) to "reference_absent",
            mapOf("state" to "NOT_A_NUMBER", "valueC" to null) to "reference_not_a_number",
            mapOf("state" to "STORED_MALFORMED", "valueC" to null) to "reference_stored_malformed",
            mapOf("state" to "PRESENT", "valueC" to null) to "unreadable_temp_present_without_value"
        )
        for ((temp, token) in cases) {
            withScanned { c ->
                repeatOfferResponse = offerAnswer(temp = temp)
                c.shown()
                assertEquals("$temp", ScannedRepeatUi.NotOffered(token), c.state.value)
            }
        }
    }

    @Test
    fun `a refusal and a failure of the offer are named states - nothing is drawn`() = withScanned { c ->
        repeatOfferFailure = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody("PERMISSION_DENIED", "x", "REPEAT_PAIR_NOT_FOUND"))
        c.shown()
        assertEquals(ScannedRepeatUi.NotOffered("refused_REPEAT_PAIR_NOT_FOUND"), c.state.value)
    }

    // ---- the start ----

    @Test
    fun `ready starts the repeat - no flag, the child session is held and recorded for the scan that starts next, and the screen is spent`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        repeatStartResponse = startAnswer()
        c.shown()
        runBlocking { c.startReady() }
        assertEquals("not within the degree yet (no reading): nothing is sent", 0, callableCalls("startRepeatScan"))
        c.onTemperature(TempRead.Present(338))
        runBlocking { c.startReady() }
        assertEquals(1, callableCalls("startRepeatScan"))
        assertEquals("a cooled start declares nothing", listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1", "scanId" to "scan-1")), repeatStartRequests.toList())
        assertEquals(ScannedRepeatUi.Started("child-1"), c.state.value)
        // the holder holds the child, and nothing else of it is known
        val info = holder.getInfo()!!
        assertEquals("child-1", info.sessionId)
        assertEquals("", info.ownerId)
        assertNull("absent, not false: the child asks for no form", info.evaluationEnabled)
        assertTrue(logLines.toString(), logLines.any { it.startsWith("holder-set via=repeat ") })
        // the scan that starts next is recorded in the child session - what the analysis reads (rule 37: the real record, not the holder)
        startScan("scan-child")
        assertEquals("child-1", records.sessionFor("scan-child", ScanSessionRecords.Use.ANALYSIS)?.sessionId)
        // a second press changes nothing
        runBlocking { c.startReady() }
        runBlocking { c.startAnyway() }
        assertEquals(1, callableCalls("startRepeatScan"))
        c.startedConsumed()
        assertEquals(ScannedRepeatUi.NotOffered("started_consumed"), c.state.value)
    }

    @Test
    fun `start anyway is for a phone that is not within the degree - the flag is true, and a ready phone has no such button`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        repeatStartResponse = startAnswer()
        c.shown()
        c.onTemperature(TempRead.Present(338))
        runBlocking { c.startAnyway() }
        assertEquals("a phone within the degree does not start anyway - it starts ready", 0, callableCalls("startRepeatScan"))
        c.onTemperature(TempRead.Present(360))
        runBlocking { c.startAnyway() }
        assertEquals(1, callableCalls("startRepeatScan"))
        assertEquals(listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1", "scanId" to "scan-1", "startedBeforeCooling" to true)), repeatStartRequests.toList())
        assertEquals(ScannedRepeatUi.Started("child-1"), c.state.value)
        assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("start", "scanned", "sess-1", "started", "before_cooling") in logLines)
    }

    @Test
    fun `a phone whose temperature cannot be read has no start at all - neither button`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        repeatStartResponse = startAnswer()
        c.shown()
        c.onTemperature(TempRead.Unreadable("no_battery_intent"))
        runBlocking { c.startReady() }
        runBlocking { c.startAnyway() }
        assertEquals(0, callableCalls("startRepeatScan"))
        assertEquals(CoolingDecision.TemperatureUnreadable("no_battery_intent"), c.cooling().decision)
    }

    @Test
    fun `before any reading there is no start either - neither button`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        repeatStartResponse = startAnswer()
        c.shown()
        runBlocking { c.startReady() }
        runBlocking { c.startAnyway() }
        assertEquals(0, callableCalls("startRepeatScan"))
    }

    @Test
    fun `wait hides the warning and is only for a phone that is not within the degree`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        c.shown()
        c.onTemperature(TempRead.Present(338))
        c.chooseWait()
        assertEquals("ready: nothing to wait for", false, c.cooling().waitChosen)
        c.onTemperature(TempRead.Present(360))
        c.chooseWait()
        assertEquals(true, c.cooling().waitChosen)
    }

    @Test
    fun `a refused start leaves the screen as it was with the refusal, and pressing again goes through`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        c.shown()
        c.onTemperature(TempRead.Present(338))
        repeatStartFailure = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody("FAILED_PRECONDITION", "x", "REPEAT_ALREADY_STARTED"))
        runBlocking { c.startReady() }
        assertEquals(RepeatStartUi.Failed(RepeatRefusal.ALREADY_STARTED, "REPEAT_ALREADY_STARTED"), c.cooling().start)
        assertEquals("the holder was not touched", null, holder.getInfo())
        repeatStartFailure = SessionWireFixtures.offlineFailures().first().second
        runBlocking { c.startReady() }
        assertEquals(RepeatStartUi.Failed(null, "not_sent"), c.cooling().start)
        repeatStartFailure = null
        repeatStartResponse = startAnswer()
        runBlocking { c.startReady() }
        assertEquals(ScannedRepeatUi.Started("child-1"), c.state.value)
        assertEquals(3, callableCalls("startRepeatScan"))
    }

    @Test
    fun `a start whose answer is not a start is a failure with its own token - the holder keeps nothing`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        repeatStartResponse = startAnswer().also { it["state"] = "SELECTED" }
        c.shown()
        c.onTemperature(TempRead.Present(338))
        runBlocking { c.startReady() }
        assertEquals(RepeatStartUi.Failed(null, "unreadable_state_not_started"), c.cooling().start)
        assertNull(holder.getInfo())
    }

    @Test
    fun `while the start is out a second press does not send twice, and the buttons are locked`() = withScanned { c ->
        repeatOfferResponse = offerAnswer()
        c.shown()
        c.onTemperature(TempRead.Present(338))
        val hold = TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
        repeatStartHold = hold
        // the held call suspends the first press at the network; Unconfined runs it up to there and hands control back, so the second press happens while the first is out
        val first = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch { c.startReady() }
        assertEquals(RepeatStartUi.InFlight, c.cooling().start)
        runBlocking { c.startReady() }
        runBlocking { c.startAnyway() }
        assertEquals(1, callableCalls("startRepeatScan"))
        hold.setResult(callableResultOf(startAnswer()))
        runBlocking { first.join() }
        assertEquals(ScannedRepeatUi.Started("child-1"), c.state.value)
        assertEquals(1, callableCalls("startRepeatScan"))
    }

    // ---- the gate ----

    /**
     * Rule 23: REPLACES `the real gate is closed - the report of a session scan asks nothing, prints nothing, and the screen stays idle` (it pinned the guard false, and the real gate with it). The guard is TRUE
     * since 2026-10-23; the REAL gate - the service as built, nothing opened or shut by the test - reads it, so the report of a session scan asks the offer once through the real service and the cooling screen opens.
     */
    @Test
    fun `the real gate is open - the guard is true, so the report of a session scan asks the offer through the real service, once, and the cooling screen opens`() = withScanned(gateOpen = null) { c ->
        assertTrue("the guard of the surfaces is true since 2026-10-23", net.neuralcheck.presentation.screen.REPEAT_TEXTS_APPROVED)
        assertTrue("nobody opened the gate", service.repeatCallsOpen())
        repeatOfferResponse = offerAnswer()
        c.shown()
        c.shown()
        assertEquals(1, callableCalls("getRepeatScanOffer"))
        assertEquals(ScannedRepeatUi.Cooling(referenceTenths = 328, decision = null, waitChosen = false, start = RepeatStartUi.Idle), c.state.value)
        assertTrue(logLines.toString(), EvalCardLog.repeatScanLine("offer", "scanned", "sess-1", "answered", "startable_selected") in logLines)
    }

    @Test
    fun `a shut gate - the report of a session scan asks nothing, prints nothing, and the screen stays idle`() = withScanned(gateOpen = false) { c ->
        c.shown()
        c.shown()
        assertEquals(ScannedRepeatUi.Idle, c.state.value)
        assertEquals("no callable call at all", 0, events.count { it.startsWith("callable:") })
        assertTrue(logLines.toString(), logLines.none { it.startsWith("repeat-scan ") })
        c.onTemperature(TempRead.Present(330))
        runBlocking { c.startReady() }
        runBlocking { c.startAnyway() }
        assertEquals(0, events.count { it.startsWith("callable:") })
        assertNull(holder.getInfo())
    }
}
