package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.domain.consent.ConsentPartRead
import net.neuralcheck.presentation.navigation.CONSENT_ROUTE
import net.neuralcheck.presentation.navigation.PRE_SCAN_AFTER_JOIN_ROUTE
import net.neuralcheck.presentation.navigation.afterJoinRoute
import net.neuralcheck.presentation.screen.CONSENT_TEXTS_APPROVED
import net.neuralcheck.util.LogRedaction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ق15 (2026-10-23): the join's answer carries `consentEnabled`, and both join entry points - the typed code and the QR - route by it. Rules 22, 30, 37, 41.
 *
 * Real: the two join view models, `joinScanSessionWithDeviceIdentity`, `FirebaseAnalysisService.joinScanSession` (the callable name, the request, the Gson read of the answer INTO [net.neuralcheck.data.remote.JoinSessionResult]),
 * the holder, the log, and the routing function the two screens call. Replaced: only the HTTP round trip and the Firebase auth object (the harness). The answers are SPELLED from the contract as the coordinator relayed
 * it on 2026-10-23 (`{sessionId, ownerId, status, evaluationEnabled, consentEnabled}`; `consentEnabled` a boolean, always, true iff the owner's `protocolParts.consent` is true) - not produced by the server, whose
 * half is not deployed. What it cannot see: the composition of the two screens (`ConsentGuardTest` pins that each calls the function once with the state's part) and a live server.
 *
 * Every value of the enumerated input (rule 30): `true`, `false`, the key missing and a JSON null - through BOTH entry points - give their own [ConsentPartRead] and their own route. The absent answer is its own
 * state, never `false` and never `true`: it has no consent screen, and the log line says `absent`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConsentJoinRoutingRealPathTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun answer(vararg extra: Pair<String, Any?>): HashMap<String, Any?> =
        hashMapOf<String, Any?>("sessionId" to "sess-1", "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true).also { m -> extra.forEach { (k, v) -> m[k] = v } }

    private enum class Entry { TYPED_CODE, QR }

    /** What the app made of the answer at [entry], and where the join leads with the REAL guard. */
    private data class Outcome(val part: ConsentPartRead, val route: String, val sessionId: String?, val heldSession: String?, val lines: List<String>, val consentCalls: Int)

    private fun joinThrough(entry: Entry, response: Any?): Outcome {
        RealJoinPathHarness(signedInAtStart = false, viewModelContext = context).use { h ->
            h.joinResponse = response
            val part: ConsentPartRead
            val sessionId: String?
            when (entry) {
                Entry.TYPED_CODE -> {
                    val vm = h.joinViewModel()
                    vm.join("NC-AB3F7K")
                    val s = vm.state.value
                    assertNull("the join must not surface an error: ${s.error}", s.error)
                    assertTrue("the session and its part come together", s.sessionId != null && s.consentPart != null)
                    part = s.consentPart!!
                    sessionId = s.sessionId
                }
                Entry.QR -> {
                    val vm = h.scannerViewModel()
                    try {
                        vm.onQrDetected("NC-AB3F7K")
                        val s = vm.state.value as QRScannerViewModel.QrState.JoinSuccess
                        part = s.consentPart
                        sessionId = s.sessionId
                    } finally {
                        vm.viewModelScope.cancel()
                    }
                }
            }
            return Outcome(part, afterJoinRoute(CONSENT_TEXTS_APPROVED, part), sessionId, h.holder.get(), h.logLines.toList(), h.callableCalls("recordScanConsent"))
        }
    }

    private val sessionHash = LogRedaction.hashId("sess-1")

    @Test
    fun `consentEnabled true - the shop has the part - both entry points read it, and the join leads to the consent screen exactly when the screen is live`() {
        for (entry in Entry.values()) {
            val o = joinThrough(entry, answer("consentEnabled" to true))
            assertEquals("$entry", ConsentPartRead.Enabled, o.part)
            assertEquals("$entry: the route follows the guard for a shop that has the part", if (CONSENT_TEXTS_APPROVED) CONSENT_ROUTE else PRE_SCAN_AFTER_JOIN_ROUTE, o.route)
            assertEquals("$entry: the join itself is unchanged", "sess-1", o.sessionId)
            assertEquals("$entry: the holder holds the session, as before", "sess-1", o.heldSession)
            assertTrue("$entry: ${o.lines}", "join-consent-part consentEnabled=true session=$sessionHash" in o.lines)
            assertEquals("$entry: joining records nothing - the consent is the screen's call", 0, o.consentCalls)
        }
    }

    @Test
    fun `consentEnabled false - the shop has no consent part - no consent screen, the scan goes on, and nothing is recorded`() {
        for (entry in Entry.values()) {
            val o = joinThrough(entry, answer("consentEnabled" to false))
            assertEquals("$entry", ConsentPartRead.NotEnabled, o.part)
            assertEquals("$entry: the join leads where it always led, whatever the guard says", PRE_SCAN_AFTER_JOIN_ROUTE, o.route)
            assertEquals("$entry: the holder holds the session, so the scan starts in it", "sess-1", o.heldSession)
            assertTrue("$entry: ${o.lines}", "join-consent-part consentEnabled=false session=$sessionHash" in o.lines)
            assertEquals("$entry: no consent call leaves the phone", 0, o.consentCalls)
        }
    }

    @Test
    fun `the key missing - or a JSON null - is ABSENT, a state of its own - no consent screen, never false, never true, and the line says absent`() {
        val withoutKey = answer()
        val withNull = answer("consentEnabled" to null)
        for (entry in Entry.values()) for ((label, response) in listOf("missing" to withoutKey, "null" to withNull)) {
            val o = joinThrough(entry, response)
            assertEquals("$entry / $label", ConsentPartRead.Absent, o.part)
            assertEquals("$entry / $label: no consent screen for an answer that did not carry the part", PRE_SCAN_AFTER_JOIN_ROUTE, o.route)
            assertEquals("$entry / $label", "sess-1", o.heldSession)
            assertTrue("$entry / $label: ${o.lines}", "join-consent-part consentEnabled=absent session=$sessionHash" in o.lines)
        }
    }

    /**
     * «محلّ بلا الجزء ⟵ لا شاشة موافقة، والفحص يبدأ» through the REAL gate (rule 37; the guard is true since 2026-10-23, so the service's own gate is open and the test opens and shuts nothing): the join of a shop
     * whose answer says `consentEnabled: false` leads to the pre-scan route, no consent call leaves the phone, the holder holds the session, and a scan starts IN that session (the record the analysis reads).
     */
    @Test
    fun `a shop WITHOUT the part, through the REAL gate - no consent screen, no consent call, and the scan starts in the joined session`() {
        assertTrue("the guard of the consent screen is true: the real gate is open", CONSENT_TEXTS_APPROVED)
        for (entry in Entry.values()) {
            RealJoinPathHarness(signedInAtStart = false, viewModelContext = context).use { h ->
                assertTrue("nobody opened the consent gate: the real one reads the guard", h.service.consentCallsOpen())
                h.joinResponse = answer("consentEnabled" to false)
                val part = when (entry) {
                    Entry.TYPED_CODE -> h.joinViewModel().also { it.join("NC-AB3F7K") }.state.value.consentPart!!
                    Entry.QR -> h.scannerViewModel().let { vm -> try { vm.onQrDetected("NC-AB3F7K"); (vm.state.value as QRScannerViewModel.QrState.JoinSuccess).consentPart } finally { vm.viewModelScope.cancel() } }
                }
                assertEquals("$entry", ConsentPartRead.NotEnabled, part)
                assertEquals("$entry: no consent screen - the join leads to the pre-scan screen", PRE_SCAN_AFTER_JOIN_ROUTE, afterJoinRoute(CONSENT_TEXTS_APPROVED, part))
                val started = h.startScan("scan-1")
                assertEquals("$entry: the scan starts in the joined session", "sess-1", started?.sessionId)
                assertEquals("$entry: nothing was recorded, nothing was cancelled", 0, h.callableCalls("recordScanConsent") + h.callableCalls("cancelScanSession"))
            }
        }
    }

    /**
     * The other way a shop can end without a consent: the join said `true`, the part was switched off before the user pressed accept, and the server answers `CONSENT_PART_NOT_ENABLED` (ق40) - through the REAL
     * gate, the real view model, controller, client and leaver. The screen's state is «لا ينطبق» (it leaves for the pre-scan screen), the session is neither cancelled nor left, and a scan starts in it.
     */
    @Test
    fun `a part switched off after the join, through the REAL gate - the server's not-enabled refusal is not applicable, the scan starts in the session, and nothing is cancelled`() {
        RealJoinPathHarness(signedInAtStart = false, viewModelContext = context).use { h ->
            h.joinResponse = answer("consentEnabled" to true)
            val joinVm = h.joinViewModel().also { it.join("NC-AB3F7K") }
            assertEquals(ConsentPartRead.Enabled, joinVm.state.value.consentPart)
            assertEquals(if (CONSENT_TEXTS_APPROVED) CONSENT_ROUTE else PRE_SCAN_AFTER_JOIN_ROUTE, afterJoinRoute(CONSENT_TEXTS_APPROVED, joinVm.state.value.consentPart!!))
            h.consentFailure = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody("FAILED_PRECONDITION", "made-up message", "CONSENT_PART_NOT_ENABLED"))
            val vm = ConsentViewModel(context, h.service, net.neuralcheck.data.remote.SessionLeaver(h.canceller, h.holder, h.records))
            vm.accept(net.neuralcheck.domain.consent.ConsentLocale.AR)
            assertEquals(ConsentUi.NotApplicable, vm.state.value)
            assertEquals("the call was made once, by the real gate", 1, h.callableCalls("recordScanConsent"))
            assertEquals("sess-1", h.holder.get())
            assertEquals("nothing is cancelled", 0, h.callableCalls("cancelScanSession"))
            assertEquals("a scan starts in the session", "sess-1", h.startScan("scan-1")?.sessionId)
        }
    }

    @Test
    fun `the evaluation flag and the consent flag are read apart - each value of one with each of the other`() {
        for (evaluation in listOf(true, false, null)) for (consent in listOf(true, false, null)) {
            val o = joinThrough(Entry.TYPED_CODE, answer("evaluationEnabled" to evaluation, "consentEnabled" to consent))
            assertEquals("evaluation=$evaluation consent=$consent", ConsentPartRead.of(consent), o.part)
            assertTrue("evaluation=$evaluation consent=$consent: ${o.lines}", o.lines.any { it.startsWith("join-response-parsed evaluationEnabled=${evaluation ?: "absent"} ") })
        }
    }
}
