package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.data.remote.SessionLeaver
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentRefusal
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
 * The consent screen's two buttons, whole - A-P6-12 (rules 30, 37, 22, 21): the REAL join (the holder holds the session as the app's join fills it), the REAL [ConsentViewModel] over the real
 * [ScanConsentController], [net.neuralcheck.data.remote.ScanConsentClient], [net.neuralcheck.data.remote.FirebaseAnalysisService], [SessionLeaver] and canceller, the real log, and the app's string resources. REPLACED:
 * the HTTP round trip and the Firebase auth object (the harness) - and the server: every answer is SPELLED from the contract A8 copy (`protocol-contract-A8-A11-A12-neuralcheck-backend-5361116.md`), not produced
 * by the server (its half is not deployed). The consent callable's gate is opened for the test (declared; the closed one is `ScanConsentClientRealPathTest`).
 *
 * What it cannot see: the screen's composition and the navigation (`ConsentSurfaceTest`, `ConsentGuardTest`), and a live service.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanConsentPathTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun withScreen(check: RealJoinPathHarness.(ConsentViewModel) -> Unit) {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.openConsentCalls()
            h.check(ConsentViewModel(context, h.service, SessionLeaver(h.canceller, h.holder, h.records)))
        }
    }

    /** The customer device joins [id]: the holder holds it, as the app's join leaves it. */
    private fun RealJoinPathHarness.join(id: String) {
        joinResponse = hashMapOf<String, Any?>("sessionId" to id, "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true)
        joinViewModel().join("NC-1234")
        assertEquals("the join stored the session in the holder", id, holder.get())
    }

    private fun recorded(session: String = "sess-1", locale: String = "ar", already: Boolean = false) =
        hashMapOf<String, Any?>("sessionId" to session, "state" to "accepted", "textVersion" to 2, "locale" to locale, "alreadyRecorded" to already)

    private fun refusal(code: String, status: String = "FAILED_PRECONDITION") =
        SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", code))

    // ---- «أوافق وأبدأ الفحص» ----

    @Test
    fun `accept - the held session is recorded in the screen's language, and ONLY then does the screen move on`() = withScreen { vm ->
        join("sess-1")
        assertEquals(ConsentUi.Idle, vm.state.value)
        consentResponse = recorded()
        vm.accept(ConsentLocale.AR)
        assertEquals(ConsentUi.Recorded, vm.state.value)
        assertEquals(listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1", "textVersion" to 2, "locale" to "ar")), consentRequests.toList())
        assertEquals("accepting does not cancel or leave anything", 0, callableCalls("cancelScanSession"))
        assertEquals("sess-1", holder.get())
    }

    @Test
    fun `accept - while the call is out the screen is recording and a second press does nothing - and after the answer the screen is finished and presses do nothing`() = withScreen { vm ->
        join("sess-1")
        consentHold = TaskCompletionSource()
        vm.accept(ConsentLocale.AR)
        assertEquals(ConsentUi.Recording, vm.state.value)
        vm.accept(ConsentLocale.AR)
        vm.decline()
        assertEquals("one call, not three", 1, callableCalls("recordScanConsent"))
        assertEquals("a press of «لا أوافق» while the call is out does nothing either", 0, callableCalls("cancelScanSession"))
        assertEquals(ConsentUi.Recording, vm.state.value)
        consentHold!!.setResult(callableResultOf(recorded()))
        assertEquals(ConsentUi.Recorded, vm.state.value)
        vm.accept(ConsentLocale.AR)
        vm.decline()
        assertEquals(1, callableCalls("recordScanConsent"))
        assertEquals(0, callableCalls("cancelScanSession"))
    }

    @Test
    fun `a consent that was not recorded keeps the user on the screen - each failure is its own state, and the user may try again`() = withScreen { vm ->
        join("sess-1")
        val cases = listOf<Pair<Exception, ConsentFailure>>(
            refusal(ConsentRefusal.SESSION_NOT_SCANNING.wire) to ConsentFailure.SESSION_NOT_SCANNING,
            refusal(ConsentRefusal.STORED_UNREADABLE.wire) to ConsentFailure.STORED_UNREADABLE,
            refusal("SESSION_EXPIRED", "FAILED_PRECONDITION") to ConsentFailure.OTHER,
            refusal("SESSION_NOT_PARTICIPANT", "PERMISSION_DENIED") to ConsentFailure.OTHER,
            refusal(ConsentRefusal.LOCALE_INVALID.wire, "INVALID_ARGUMENT") to ConsentFailure.OTHER,
            refusal("CONSENT_BRAND_NEW") to ConsentFailure.OTHER,
            RuntimeException("a plain failure") to ConsentFailure.OTHER
        ) + SessionWireFixtures.offlineFailures().map { it.second to ConsentFailure.OTHER }
        for ((failure, expected) in cases) {
            consentFailure = failure
            vm.accept(ConsentLocale.AR)
            assertEquals("$failure", ConsentUi.RecordFailed(expected), vm.state.value)
        }
        // an answer that is not a recorded consent is a failure too
        consentFailure = null
        for (answer in listOf<Any?>(null, hashMapOf<String, Any?>(), recorded().also { it["state"] = "declined" }, recorded(session = "sess-9"))) {
            consentResponse = answer
            vm.accept(ConsentLocale.AR)
            assertEquals("$answer", ConsentUi.RecordFailed(ConsentFailure.OTHER), vm.state.value)
        }
        assertEquals("the session was never left or cancelled by a failed recording", 0, callableCalls("cancelScanSession"))
        assertEquals("sess-1", holder.get())
        // and then it works
        consentResponse = recorded()
        vm.accept(ConsentLocale.AR)
        assertEquals(ConsentUi.Recorded, vm.state.value)
    }

    /**
     * ق40 (2026-10-23): the server refuses `recordScanConsent` with `CONSENT_PART_NOT_ENABLED` for a shop without the consent part. That is «لا ينطبق», NOT a failure that stops the scan: the screen's state is
     * [ConsentUi.NotApplicable] (it moves on to the pre-scan screen), the session is neither cancelled nor left, nothing is recorded, and the refusal is classified by the reasonCode alone (rule 21) - whatever the
     * Functions status says. A second press does nothing.
     */
    @Test
    fun `the server's CONSENT_PART_NOT_ENABLED is not applicable - the screen moves on, the session stays held and is not cancelled, and a second press sends nothing`() = withScreen { vm ->
        join("sess-1")
        // the Functions status is made up and does not matter (rule 21): `ScanConsentClientRealPathTest` reads this refusal as itself for every status
        consentFailure = refusal(ConsentRefusal.PART_NOT_ENABLED.wire, "PERMISSION_DENIED")
        vm.accept(ConsentLocale.AR)
        assertEquals(ConsentUi.NotApplicable, vm.state.value)
        assertEquals("sess-1", holder.get())
        assertEquals("a part that is not enabled cancels nothing", 0, callableCalls("cancelScanSession"))
        assertTrue(logLines.toString(), net.neuralcheck.util.EvalCardLog.consentRecordLine("refused", "CONSENT_PART_NOT_ENABLED", "sess-1") in logLines)
        vm.accept(ConsentLocale.TR)
        vm.decline()
        assertEquals("finished: neither button sends anything more", 1, callableCalls("recordScanConsent"))
        assertEquals(0, callableCalls("cancelScanSession"))
    }

    @Test
    fun `no session is held - nothing to consent to, and nothing is sent for either button`() = withScreen { vm ->
        vm.accept(ConsentLocale.AR)
        assertEquals(ConsentUi.NoSession, vm.state.value)
        assertEquals(0, events.count { it.startsWith("callable:") })
    }

    @Test
    fun `no session is held - the refusal has nothing to cancel either`() = withScreen { vm ->
        vm.decline()
        assertEquals(ConsentUi.NoSession, vm.state.value)
        assertEquals(0, events.count { it.startsWith("callable:") })
    }

    // ---- «لا أوافق» ----

    @Test
    fun `decline - cancels the held session with consent_declined and NOTHING else - no identifier, no consent record - and empties the holder`() = withScreen { vm ->
        join("sess-1")
        vm.decline()
        assertEquals(ConsentUi.Declined, vm.state.value)
        assertEquals(
            "A8 (4): the call that has existed since batch 9, `{sessionId, reason: consent_declined}` - the two keys",
            listOf<Any?>(mapOf("sessionId" to "sess-1", "reason" to "consent_declined")),
            cancelRequests.toList()
        )
        assertEquals("a refusal records no consent", 0, callableCalls("recordScanConsent"))
        assertNull("the holder was emptied", holder.getInfo())
        assertTrue(logLines.toString(), logLines.any { it.startsWith("session-cancel-sent role=customer reason=consent_declined session=#") })
        assertTrue(logLines.toString(), logLines.any { it.startsWith("session-leave role=customer phase=consent result=left ") && it.endsWith(" holder=cleared") })
        assertTrue(logLines.toString(), logLines.any { it.startsWith("holder-clear reason=consent-declined ") })
        assertTrue("no raw session id in any line", logLines.none { it.contains("sess-1") })
    }

    @Test
    fun `decline - the holder is emptied only if it still holds the session being refused`() = withScreen { vm ->
        join("sess-A")
        join("sess-B")
        // the screen asks for the session the holder holds NOW (B): the refusal names it, and A is nobody's business
        vm.decline()
        assertEquals(listOf<Any?>(mapOf("sessionId" to "sess-B", "reason" to "consent_declined")), cancelRequests.toList())
        assertNull(holder.getInfo())
    }

    @Test
    fun `decline offline - the refusal is never blocked - the phone leaves locally, the holder is emptied, and the log says it was local`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            withScreen { vm ->
                join("sess-1")
                cancelFailure = failure
                vm.decline()
                assertEquals(label, ConsentUi.Declined, vm.state.value)
                assertEquals("$label: attempted once", listOf<Any?>(mapOf("sessionId" to "sess-1", "reason" to "consent_declined")), cancelRequests.toList())
                assertNull("$label: the holder was emptied", holder.getInfo())
                assertEquals(
                    "$label: one named line says the leave was local, with the phase",
                    1, logLines.count { it.startsWith("session-leave-local role=customer phase=consent reason=offline session=#") }
                )
            }
        }
    }

    @Test
    fun `decline - the server saying the session is over, or that it does not know this device, ends the refusal too - each printed`() {
        withScreen { vm ->
            join("sess-1")
            cancelFailure = refusal("SESSION_EXPIRED")
            vm.decline()
            assertEquals(ConsentUi.Declined, vm.state.value)
            assertTrue(logLines.toString(), logLines.any { it.startsWith("session-leave-expired role=customer phase=consent session=#") })
        }
        withScreen { vm ->
            join("sess-1")
            cancelFailure = refusal("SESSION_NOT_PARTICIPANT", "PERMISSION_DENIED")
            vm.decline()
            assertEquals(ConsentUi.Declined, vm.state.value)
            assertNull(holder.getInfo())
            assertTrue(logLines.toString(), logLines.any { it.startsWith("session-leave-local role=customer phase=consent reason=not_participant session=#") })
        }
    }

    @Test
    fun `decline - any other refusal leaves the user on the screen with the text the cancel already shows, the holder is kept, and a second press works`() = withScreen { vm ->
        join("sess-1")
        cancelFailure = refusal("CANCEL_REASON_NOT_ALLOWED_FOR_ROLE", "PERMISSION_DENIED")
        vm.decline()
        assertEquals("the existing generic text - no new text", ConsentUi.DeclineFailed(context.getString(R.string.error_generic)), vm.state.value)
        assertEquals("sess-1", holder.get())
        assertTrue(logLines.toString(), logLines.any { it.startsWith("session-leave role=customer phase=consent result=failed ") })
        // an approved cancel text is the one shown when the server names it
        cancelFailure = refusal("SESSION_COMPLETED")
        vm.decline()
        assertEquals(ConsentUi.DeclineFailed(context.getString(R.string.session_cancel_completed)), vm.state.value)
        // and then it works
        cancelFailure = null
        vm.decline()
        assertEquals(ConsentUi.Declined, vm.state.value)
        assertNull(holder.getInfo())
    }
}
