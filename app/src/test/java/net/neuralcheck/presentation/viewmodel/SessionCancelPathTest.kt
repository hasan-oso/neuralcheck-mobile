package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.CancelUi
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import net.neuralcheck.presentation.viewmodel.SessionWireFixtures.Refusal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The path of a CANCEL, whole, with the real texts — batch 2 (order 2026-10-15), item 2, part b: the owner's «إلغاء الجلسة» button.
 * (Part c adds the customer's leave in a sibling test file.)
 *
 * The order: every code of Appendix A table (b), at the call that throws it, through the whole path, checking the TEXT SHOWN (rule 30), the simulated
 * server reply in the wire shape (rule 21: the message is not what is matched). What is REAL: the SDK's parse of the wire JSON, the real
 * [net.neuralcheck.data.remote.FirebaseAnalysisService] (`createScanSession`, `cancelScanSession`), the real [net.neuralcheck.data.remote.SessionCanceller],
 * the real [GenerateSessionViewModel] and the app's string resources. REPLACED: the HTTP round trip, the Firebase auth object and the Firestore listener
 * (the harness's three static mocks; the snapshot a test delivers is a mock, everything that reads it is real).
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class SessionCancelPathBase {

    internal abstract fun approved(text: ApprovedSessionTexts.Text): String

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun existing(id: Int): String = context.getString(id)

    /** The owner is a signed-in account: createScanSession needs one (requireAuth), so every harness here starts signed in. */
    private fun ownerVm(h: RealJoinPathHarness) = GenerateSessionViewModel(context, h.service, h.canceller)

    /** The text a cancel refusal must show, written out here: only the two the user approved a text for; everything else shows the text that point already showed. */
    private fun expectedCancelText(reasonCode: String?): String = when (reasonCode) {
        "SESSION_COMPLETED" -> approved(ApprovedSessionTexts.CANCEL_COMPLETED)
        "SESSION_ANALYSIS_IN_FLIGHT" -> approved(ApprovedSessionTexts.CANCEL_IN_FLIGHT)
        else -> existing(R.string.error_generic)
    }

    private val namedSession = mapOf("sessionId" to "sess-1", "reason" to "cancelled_by_owner")

    // ---- success ----

    @Test
    fun `owner cancel - a confirmed cancel sends cancelled_by_owner for the session on screen and shows the cancelled state`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            val vm = ownerVm(h)
            assertEquals(Phase.WAITING, vm.state.value.phase)
            assertEquals("sess-1", vm.state.value.sessionId)
            vm.cancelSession()
            assertEquals(listOf<Any?>(namedSession), h.cancelRequests.toList())
            assertEquals(Phase.CANCELLED, vm.state.value.phase)
            assertEquals(CancelUi.Idle, vm.state.value.cancel)
            assertTrue(h.logLines.any { it.startsWith("session-cancel-sent role=owner reason=cancelled_by_owner session=#") })
            assertTrue(h.logLines.any { it.startsWith("session-cancel-answer role=owner outcome=cancelled status=cancelled ") })
        }
    }

    @Test
    fun `owner cancel - a second cancel is a success on the server (alreadyCancelled true) and on the screen`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.cancelResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "cancelled", "alreadyCancelled" to true)
            val vm = ownerVm(h)
            vm.cancelSession()
            assertEquals(Phase.CANCELLED, vm.state.value.phase)
            assertTrue(h.logLines.any { it.startsWith("session-cancel-answer role=owner outcome=already-cancelled ") })
        }
    }

    @Test
    fun `owner cancel - an answer without the alreadyCancelled flag is still a cancellation, and the log says the flag was absent`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.cancelResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "cancelled")
            val vm = ownerVm(h)
            vm.cancelSession()
            assertEquals(Phase.CANCELLED, vm.state.value.phase)
            assertTrue(h.logLines.any { it.startsWith("session-cancel-answer role=owner outcome=cancelled-flag-absent ") })
        }
    }

    // ---- refusals: every code of table (b), the text shown ----

    @Test
    fun `owner cancel - every refusal of the cancel call shows the text of its code, the session stays as it was, and the log names the code`() {
        for (row in SessionWireFixtures.CANCEL_REFUSALS) {
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                h.cancelFailure = row.exception()
                val vm = ownerVm(h)
                vm.cancelSession()
                val shown = (vm.state.value.cancel as? CancelUi.Failed)?.message
                assertEquals("${row.reasonCode} / ${row.label}", expectedCancelText(row.reasonCode), shown)
                assertEquals("a refused cancel does not change the session on screen", Phase.WAITING, vm.state.value.phase)
                assertEquals(listOf<Any?>(namedSession), h.cancelRequests.toList())
                val line = h.logLines.single { it.startsWith("session-refused call=cancel-owner ") }
                assertTrue("${row.label}: $line", line.contains(" reasonCode=${row.reasonCode} "))
            }
        }
    }

    @Test
    fun `owner cancel - the same reasonCode under a different message and Functions code shows the same text (the message is not matched)`() {
        // SESSION_COMPLETED is sent by the cancel as FAILED_PRECONDITION + the English technical «SESSION_COMPLETED»; the join sends the same code as
        // NOT_FOUND / FAILED_PRECONDITION with Arabic messages. On the cancel it must show the CANCEL text whatever the wire says.
        for (variant in listOf(
            Refusal("completed, as the server sends it", "FAILED_PRECONDITION", "SESSION_COMPLETED", "SESSION_COMPLETED"),
            Refusal("completed, with another message and status", "NOT_FOUND", SessionWireFixtures.JOIN_INVALID_OR_EXPIRED_MESSAGE, "SESSION_COMPLETED"),
            Refusal("completed, with an empty-looking message", "PERMISSION_DENIED", "x", "SESSION_COMPLETED")
        )) {
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                h.cancelFailure = variant.exception()
                val vm = ownerVm(h)
                vm.cancelSession()
                assertEquals(variant.label, approved(ApprovedSessionTexts.CANCEL_COMPLETED), (vm.state.value.cancel as CancelUi.Failed).message)
            }
        }
    }

    @Test
    fun `owner cancel - a refusal with no reasonCode and an unknown reasonCode each show what that point showed before`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.cancelFailure = SessionWireFixtures.CANCEL_REFUSALS_WITHOUT_CODE.single().exception()
            val vm = ownerVm(h)
            vm.cancelSession()
            assertEquals(existing(R.string.error_generic), (vm.state.value.cancel as CancelUi.Failed).message)
            assertTrue(h.logLines.single { it.startsWith("session-refused call=cancel-owner ") }.contains(" reasonCode=absent "))
        }
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.cancelFailure = SessionWireFixtures.CANCEL_REFUSAL_UNRECOGNIZED.exception()
            val vm = ownerVm(h)
            vm.cancelSession()
            // the message of this refusal is «SESSION_COMPLETED», and its code is not one the app knows: no approved text is shown for it
            assertEquals(existing(R.string.error_generic), (vm.state.value.cancel as CancelUi.Failed).message)
            assertTrue(h.logLines.single { it.startsWith("session-refused call=cancel-owner ") }.contains(" reasonCode=unrecognized raw=SESSION_BRAND_NEW_REASON "))
        }
    }

    /**
     * Batch 4, item 1 (user decision 1, 2026-10-16): the connection failure of the owner's cancel, whole. REPLACES the block that stood in the test above, which built the failure
     * as a bare `IOException` carrying the text itself - a shape the SDK never makes. Every shape of [SessionWireFixtures.offlineFailures] (five built by the SDK's own
     * `onFailure`: message INTERNAL, the text in the cause; one bare) shows the connection text; the session stays as it was and the owner can tap again. A failure that carries
     * a `reasonCode` - even the unknown text «network» - is an answer, not a disconnect: the text this point showed for it before.
     */
    @Test
    fun `owner cancel - a real disconnect shows the connection text and the session stays, the owner can try again, and a reasonCode - even the text network - is not a disconnect`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                h.cancelFailure = failure
                val vm = ownerVm(h)
                vm.cancelSession()
                assertEquals(label, existing(R.string.login_error_network), (vm.state.value.cancel as CancelUi.Failed).message)
                assertEquals("$label: a failed cancel does not change the session on screen", Phase.WAITING, vm.state.value.phase)
                assertEquals("$label: one attempt, no automatic retry", listOf<Any?>(namedSession), h.cancelRequests.toList())
                val line = h.logLines.single { it.startsWith("session-refused call=cancel-owner ") }
                assertTrue("$label: $line", line.contains(" reasonCode=absent ") && line.endsWith(" shown=network"))
                h.cancelFailure = null // the connection is back; the owner taps again
                vm.cancelSession()
                assertEquals("$label: the second tap cancels", Phase.CANCELLED, vm.state.value.phase)
                assertEquals("$label: two taps, two requests", 2, h.cancelRequests.size)
            }
        }
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.cancelFailure = SessionWireFixtures.REASON_CODE_NETWORK_REFUSAL.exception()
            val vm = ownerVm(h)
            vm.cancelSession()
            assertEquals(
                "a reasonCode that is the text network is an answer, not a disconnect",
                existing(R.string.error_generic), (vm.state.value.cancel as CancelUi.Failed).message
            )
            val line = h.logLines.single { it.startsWith("session-refused call=cancel-owner ") }
            assertTrue(line, line.contains(" reasonCode=unrecognized raw=network ") && line.endsWith(" shown=error_generic"))
        }
    }

    @Test
    fun `owner cancel - a success answer that does not say cancelled is a failure, not a cancellation`() {
        for (answer in listOf<Any?>(
            hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "scanning", "alreadyCancelled" to false),
            hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "done"),
            hashMapOf<String, Any?>("sessionId" to "sess-1"),
            null
        )) {
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                h.cancelResponse = answer
                val vm = ownerVm(h)
                vm.cancelSession()
                assertEquals("answer=$answer", existing(R.string.error_generic), (vm.state.value.cancel as CancelUi.Failed).message)
                assertEquals(Phase.WAITING, vm.state.value.phase)
                assertTrue(h.logLines.any { it.startsWith("session-cancel-answer role=owner outcome=unexpected ") })
            }
        }
    }

    // ---- which session, and when ----

    @Test
    fun `owner cancel - it names the session on screen only - a new code cancels nothing, and the old session is left alone`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            val vm = ownerVm(h)
            assertEquals("sess-1", vm.state.value.sessionId)
            h.createResponse = hashMapOf<String, Any?>("sessionId" to "sess-2", "code" to "NC-5678", "expiresInMinutes" to 60)
            vm.create()
            assertEquals("sess-2", vm.state.value.sessionId)
            assertTrue("\"+ new code\" sends no cancellation", h.cancelRequests.isEmpty())
            vm.cancelSession()
            assertEquals(
                "only the session the screen shows is named",
                listOf<Any?>(mapOf("sessionId" to "sess-2", "reason" to "cancelled_by_owner")), h.cancelRequests.toList()
            )
            assertEquals(Phase.CANCELLED, vm.state.value.phase)
        }
    }

    @Test
    fun `owner cancel - a cancel still in flight does not touch the screen of the next code, and a second tap does nothing`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            val hold = com.google.android.gms.tasks.TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
            h.cancelHold = hold
            val vm = ownerVm(h)
            vm.cancelSession()
            assertEquals(CancelUi.InFlight, vm.state.value.cancel)
            vm.cancelSession()
            assertEquals("the second tap sent nothing", 1, h.cancelRequests.size)
            // the owner makes another code while the cancel of the first is out
            h.cancelHold = null
            h.createResponse = hashMapOf<String, Any?>("sessionId" to "sess-2", "code" to "NC-5678", "expiresInMinutes" to 60)
            vm.create()
            hold.setResult(h.callableResultOf(hashMapOf<String, Any?>("sessionId" to "sess-1", "status" to "cancelled", "alreadyCancelled" to false)))
            assertEquals("the screen shows the NEW session, untouched by the cancel of the old one", Phase.WAITING, vm.state.value.phase)
            assertEquals("sess-2", vm.state.value.sessionId)
            assertEquals(CancelUi.Idle, vm.state.value.cancel)
        }
    }

    @Test
    fun `owner cancel - only a pending or scanning session can be cancelled`() {
        // WAITING (pending) and SCANNING are cancellable; a completed or expired session, or one that was never created, is not
        for ((fields, cancellable) in listOf(
            mapOf<String, Any?>("status" to "pending") to true,
            mapOf<String, Any?>("status" to "scanning") to true,
            mapOf<String, Any?>("status" to "completed", "scanId" to "scan-1") to false,
            mapOf<String, Any?>("status" to "expired") to false
        )) {
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                val vm = ownerVm(h)
                h.deliverSnapshot("sess-1", fields = fields)
                vm.cancelSession()
                assertEquals("status=${fields["status"]}", if (cancellable) 1 else 0, h.cancelRequests.size)
            }
        }
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createFailure = java.io.IOException("Unable to resolve host")
            val vm = ownerVm(h)
            assertEquals(Phase.ERROR, vm.state.value.phase)
            vm.cancelSession()
            assertTrue("no session, nothing to cancel", h.cancelRequests.isEmpty())
        }
    }

    // ---- the owner's listener sees the cancellation (by the customer, or by this owner) ----

    @Test
    fun `owner listener - a snapshot with status cancelled shows the cancelled state`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            val vm = ownerVm(h)
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "cancelled", "cancelReason" to "left_by_customer"))
            assertEquals(Phase.CANCELLED, vm.state.value.phase)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionCancelPathArabicTest : SessionCancelPathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.ar
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr")
class SessionCancelPathTurkishTest : SessionCancelPathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.tr
}
