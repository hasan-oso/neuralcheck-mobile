package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.domain.pair.PairFailureKind
import net.neuralcheck.domain.pair.PairRefusal
import net.neuralcheck.domain.pair.failureKindOf
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

/**
 * The owner's pair list through the REAL path - order of 2026-10-22 (rules 30 and 37). Real: the owner's view model, `FirebaseAnalysisService` (`setSessionPurpose`, `listOpenMaintenanceBefores` and
 * `confirmMaintenanceAfterLink`: the callable names, the request maps, the Gson tree of the answers), `PairLinkClient`, the readers, the state functions of `PairListUi.kt` and the session watcher. Replaced: only the
 * HTTP round trip, auth and the Firestore listener (the harness), the callables' exit gate - declared, opened by [RealJoinPathHarness.openPairCalls] for every test but ONE (`the real gate is closed`) - and the
 * screen: the list's texts are not approved, so what is asserted is the STATE the screen would draw and the calls that leave the phone.
 *
 * THE ANSWERS ARE WRITTEN FROM THE CONTRACT, NOT PRODUCED BY THE SERVER (its half is not deployed): A3 (2) and (3) of `app/src/test/resources/contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md`.
 * The refusal bodies' status and message are made up and vary on purpose: the app reads `details.reasonCode` only (rule 21).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerPairListTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val purposesOn: HashMap<String, Any?> get() = hashMapOf("sessionId" to "sess-1", "code" to "NC-1234", "expiresInMinutes" to 60, "protocolParts" to hashMapOf<String, Any?>("purposes" to true, "batteryBeforeAfter" to true))

    private fun withOwner(gateOpen: Boolean = true, check: (RealJoinPathHarness, GenerateSessionViewModel) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createResponse = purposesOn
            if (gateOpen) h.openPairCalls() else h.closePairCalls()
            check(h, GenerateSessionViewModel(context, h.service, h.canceller))
        }
    }

    private fun name(model: String) = hashMapOf<String, Any?>("state" to "name_unmapped", "model" to model, "names" to null, "reason" to "MODEL_NOT_IN_MAP")

    private fun candidate(id: String, at: Long = 1_760_000_000_000L) = hashMapOf<String, Any?>("beforeScanId" to id, "deviceName" to name("M-$id"), "scannedAtMs" to at)

    private fun listAnswer(vararg ids: String, hours: Int = 72): HashMap<String, Any?> = hashMapOf("validityHours" to hours, "candidates" to ids.map { candidate(it) })

    private fun confirmAnswer(before: String, session: String = "sess-1"): HashMap<String, Any?> = hashMapOf("sessionId" to session, "beforeScanId" to before, "linkMethod" to "manual")

    private fun refusal(code: String, status: String = "FAILED_PRECONDITION") = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", code))

    private fun chooseAfter(vm: GenerateSessionViewModel, type: DefectType = DefectType.BATTERY) {
        vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
        vm.onDefectType(type)
    }

    private fun pairs(vm: GenerateSessionViewModel) = vm.state.value.pairs

    private val listedTwo = PairListUi.Choosing(
        PairListing(
            72,
            listOf(
                net.neuralcheck.domain.pair.PairCandidate("a", net.neuralcheck.domain.pair.DeviceNameRead.Unmapped(net.neuralcheck.domain.repeat.TextRead.Text("M-a"), net.neuralcheck.domain.repeat.TextRead.Text("MODEL_NOT_IN_MAP")), 1_760_000_000_000L),
                net.neuralcheck.domain.pair.PairCandidate("b", net.neuralcheck.domain.pair.DeviceNameRead.Unmapped(net.neuralcheck.domain.repeat.TextRead.Text("M-b"), net.neuralcheck.domain.repeat.TextRead.Text("MODEL_NOT_IN_MAP")), 1_760_000_000_000L)
            )
        ),
        selected = null,
        confirm = PairConfirmUi.Idle
    )

    // ---- when the list is asked for ----

    @Test
    fun `the list is asked for when the server accepted an after purpose - once, with the session id alone - and not before the type is chosen`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer("a", "b")
        assertEquals("nothing asked at creation", PairListUi.NotShown, pairs(vm))
        vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
        assertEquals("the chip alone: no purpose, no list", 0, h.callableCalls("setSessionPurpose") + h.callableCalls("listOpenMaintenanceBefores"))
        vm.onDefectType(DefectType.BATTERY)
        assertEquals(1, h.callableCalls("setSessionPurpose"))
        assertEquals(1, h.callableCalls("listOpenMaintenanceBefores"))
        assertEquals(listOf<Any?>(linkedMapOf("sessionId" to "sess-1")), h.pairListRequests.toList())
        assertEquals(listedTwo, pairs(vm))
        assertTrue(h.logLines.toString(), EvalCardLog.pairLinkLine("list", "sess-1", "listed", "n2_v72", emptyList()) in h.logLines)
        assertTrue("the order: the purpose is accepted, then the list is asked", h.events.indexOf("callable:setSessionPurpose") < h.events.indexOf("callable:listOpenMaintenanceBefores"))
    }

    @Test
    fun `no other accepted purpose asks for the list - before maintenance and normal take it away`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer("a")
        vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
        vm.onDefectType(DefectType.THERMAL)
        assertEquals(0, h.callableCalls("listOpenMaintenanceBefores"))
        assertEquals(PairListUi.NotShown, pairs(vm))
        chooseAfter(vm)
        assertTrue(pairs(vm) is PairListUi.Choosing)
        vm.onPurposeChip(ScanPurposeChoice.NORMAL)
        assertEquals("normal takes the list away", PairListUi.NotShown, pairs(vm))
        chooseAfter(vm)
        vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
        vm.onDefectType(DefectType.SOFTWARE)
        assertEquals("so does before maintenance", PairListUi.NotShown, pairs(vm))
        assertEquals("the list was asked twice, each time an after purpose was accepted", 2, h.callableCalls("listOpenMaintenanceBefores"))
    }

    @Test
    fun `a purpose the server refused asks for nothing - the list stays as it was`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer("a")
        h.purposeFailure = refusal("SHOP_NO_CREDIT", "PERMISSION_DENIED")
        chooseAfter(vm)
        assertEquals(0, h.callableCalls("listOpenMaintenanceBefores"))
        assertEquals(PairListUi.NotShown, pairs(vm))
    }

    @Test
    fun `asking the same after purpose again - a new type, or the same - asks the list again and a choice made or a link shown is gone`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer("a", "b")
        chooseAfter(vm, DefectType.BATTERY)
        vm.onPairCandidate("a")
        h.pairConfirmResponse = confirmAnswer("a")
        vm.onPairConfirm()
        assertTrue(pairs(vm) is PairListUi.Linked)
        vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
        vm.onDefectType(DefectType.THERMAL)
        assertEquals(2, h.callableCalls("listOpenMaintenanceBefores"))
        assertEquals("the server cleared the link with the purpose; the app shows the fresh list with no choice", listedTwo, pairs(vm))
    }

    // ---- the list's answers ----

    @Test
    fun `no candidate is the none-open state with the hours the server named`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer(hours = 24)
        chooseAfter(vm)
        assertEquals(PairListUi.Empty(24), pairs(vm))
        assertTrue(h.logLines.toString(), EvalCardLog.pairLinkLine("list", "sess-1", "listed", "n0_v24", emptyList()) in h.logLines)
    }

    @Test
    fun `every refusal of the list is shown with the text of its own reasonCode - and the line carries the code, named or not`() {
        for (r in PairRefusal.values()) {
            withOwner { h, vm ->
                h.pairListFailure = refusal(r.wire)
                chooseAfter(vm)
                assertEquals(r.wire, PairListUi.LoadFailed(PairFailure(failureKindOf(r), r.wire)), pairs(vm))
                assertTrue(h.logLines.toString(), EvalCardLog.pairLinkLine("list", "sess-1", "refused", r.wire, emptyList()) in h.logLines)
            }
        }
        withOwner { h, vm ->
            h.pairListFailure = refusal("PAIR_BRAND_NEW")
            chooseAfter(vm)
            assertEquals(PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "PAIR_BRAND_NEW")), pairs(vm))
            assertTrue("an unnamed code is in the line: ${h.logLines}", EvalCardLog.pairLinkLine("list", "sess-1", "refused", "PAIR_BRAND_NEW", emptyList()) in h.logLines)
        }
    }

    @Test
    fun `an unreadable answer and a failure with no reasonCode are the general failure - never a list`() = withOwner { h, vm ->
        h.pairListResponse = hashMapOf<String, Any?>("validityHours" to 72)
        chooseAfter(vm)
        assertEquals(PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "unreadable_candidates_absent")), pairs(vm))
        for ((label, failure) in SessionWireFixtures.offlineFailures() + ("a plain exception" to RuntimeException("BEFORE_EXPIRED"))) {
            h.pairListFailure = failure
            chooseAfter(vm)
            assertEquals(label, PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "not_sent")), pairs(vm))
        }
    }

    @Test
    fun `a late answer for an earlier asking is dropped - another purpose, or another code, replaced it`() = withOwner { h, vm ->
        val hold = TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
        h.pairListHold = hold
        chooseAfter(vm)
        assertEquals(PairListUi.Loading, pairs(vm))
        h.pairListHold = null
        vm.onPurposeChip(ScanPurposeChoice.NORMAL)
        assertEquals(PairListUi.NotShown, pairs(vm))
        hold.setResult(h.callableResultOf(listAnswer("a", "b")))
        assertEquals("the answer belonged to the asking normal replaced", PairListUi.NotShown, pairs(vm))
        // and a new code
        val hold2 = TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
        h.pairListHold = hold2
        chooseAfter(vm)
        h.pairListHold = null
        h.createResponse = purposesOn.also { it["sessionId"] = "sess-2"; it["code"] = "NC-5678" }
        vm.create()
        assertEquals("sess-2", vm.state.value.sessionId)
        assertEquals(PairListUi.NotShown, pairs(vm))
        hold2.setResult(h.callableResultOf(listAnswer("a")))
        assertEquals("the new code's list is not the old code's", PairListUi.NotShown, pairs(vm))
    }

    // ---- the choice and its confirmation ----

    @Test
    fun `a candidate is chosen then confirmed - the request is the session id and the scan id, and the list becomes linked with the candidate chosen`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer("a", "b")
        chooseAfter(vm)
        vm.onPairCandidate("b")
        assertEquals((listedTwo).copy(selected = "b"), pairs(vm))
        assertEquals("choosing sends nothing", 0, h.callableCalls("confirmMaintenanceAfterLink"))
        h.pairConfirmResponse = confirmAnswer("b")
        vm.onPairConfirm()
        assertEquals(1, h.callableCalls("confirmMaintenanceAfterLink"))
        assertEquals(listOf<Any?>(linkedMapOf("sessionId" to "sess-1", "beforeScanId" to "b")), h.pairConfirmRequests.toList())
        assertEquals(PairListUi.Linked(listedTwo.listing, listedTwo.listing.candidates[1]), pairs(vm))
        assertTrue(h.logLines.toString(), EvalCardLog.pairLinkLine("confirm", "sess-1", "confirmed", "-", emptyList()) in h.logLines)
    }

    @Test
    fun `choosing again after a link replaces it - the second confirmation is sent and the link is the second candidate`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer("a", "b")
        chooseAfter(vm)
        vm.onPairCandidate("a"); h.pairConfirmResponse = confirmAnswer("a"); vm.onPairConfirm()
        vm.onPairCandidate("b")
        assertEquals(listedTwo.copy(selected = "b"), pairs(vm))
        h.pairConfirmResponse = confirmAnswer("b"); vm.onPairConfirm()
        assertEquals(2, h.callableCalls("confirmMaintenanceAfterLink"))
        assertEquals(PairListUi.Linked(listedTwo.listing, listedTwo.listing.candidates[1]), pairs(vm))
    }

    @Test
    fun `a button with no choice sends nothing, and a tap on an id the list does not hold is ignored - with a named reason in the log`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer("a", "b")
        chooseAfter(vm)
        vm.onPairConfirm()
        assertEquals(0, h.callableCalls("confirmMaintenanceAfterLink"))
        assertTrue(h.logLines.toString(), EvalCardLog.pairListEventLine("ignored", "sess-1", "nothing_selected") in h.logLines)
        vm.onPairCandidate("zzz")
        assertEquals(listedTwo, pairs(vm))
        assertTrue(h.logLines.toString(), EvalCardLog.pairListEventLine("ignored", "sess-1", "unknown_candidate") in h.logLines)
    }

    @Test
    fun `every refusal of the confirmation keeps the choice and shows the text of its own reasonCode - and a second try for the same choice is sent`() {
        val named = mapOf(
            PairRefusal.BEFORE_NOT_FOUND to PairFailureKind.NOT_AVAILABLE, PairRefusal.BEFORE_NOT_OWNED to PairFailureKind.NOT_AVAILABLE, PairRefusal.BEFORE_WRONG_PURPOSE to PairFailureKind.NOT_AVAILABLE,
            PairRefusal.BEFORE_ALREADY_PAIRED to PairFailureKind.ALREADY_PAIRED, PairRefusal.BEFORE_EXPIRED to PairFailureKind.EXPIRED,
            PairRefusal.BEFORE_WRONG_DEFECT_TYPE to PairFailureKind.WRONG_DEFECT_TYPE, PairRefusal.PART_NOT_ENABLED to PairFailureKind.NOT_ENABLED,
            PairRefusal.OWNER_MISMATCH to PairFailureKind.NOT_AVAILABLE
        )
        for ((r, kind) in named) {
            withOwner { h, vm ->
                h.pairListResponse = listAnswer("a", "b")
                chooseAfter(vm)
                vm.onPairCandidate("a")
                h.pairConfirmFailure = refusal(r.wire)
                vm.onPairConfirm()
                val choosing = pairs(vm) as PairListUi.Choosing
                assertEquals(r.wire, PairConfirmUi.Failed(PairFailure(kind, r.wire)), choosing.confirm)
                assertEquals("the choice stays", "a", choosing.selected)
                assertTrue(h.logLines.toString(), EvalCardLog.pairLinkLine("confirm", "sess-1", "refused", r.wire, emptyList()) in h.logLines)
                h.pairConfirmFailure = null
                h.pairConfirmResponse = confirmAnswer("a")
                vm.onPairConfirm()
                assertEquals("the retry is sent", 2, h.callableCalls("confirmMaintenanceAfterLink"))
                assertTrue(pairs(vm) is PairListUi.Linked)
            }
        }
        withOwner { h, vm ->
            h.pairListResponse = listAnswer("a")
            chooseAfter(vm)
            vm.onPairCandidate("a")
            h.pairConfirmFailure = refusal("SESSION_EXPIRED")
            vm.onPairConfirm()
            assertEquals(PairConfirmUi.Failed(PairFailure(PairFailureKind.GENERAL, "SESSION_EXPIRED")), (pairs(vm) as PairListUi.Choosing).confirm)
            h.pairConfirmFailure = null
            h.pairConfirmResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "beforeScanId" to "a", "linkMethod" to "auto_confirmed")
            vm.onPairConfirm()
            assertEquals("a confirmation that is not manual is not a link", PairConfirmUi.Failed(PairFailure(PairFailureKind.GENERAL, "unreadable_link_method_not_manual")), (pairs(vm) as PairListUi.Choosing).confirm)
        }
    }

    @Test
    fun `a confirmation in flight ignores a second tap and a new choice - and its late answer after the purpose changed is dropped`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer("a", "b")
        chooseAfter(vm)
        vm.onPairCandidate("a")
        val hold = TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
        h.pairConfirmHold = hold
        vm.onPairConfirm()
        assertEquals(PairConfirmUi.InFlight("a"), (pairs(vm) as PairListUi.Choosing).confirm)
        vm.onPairConfirm()
        vm.onPairCandidate("b")
        assertEquals("one call, and the choice did not move", 1, h.callableCalls("confirmMaintenanceAfterLink"))
        assertEquals("a", (pairs(vm) as PairListUi.Choosing).selected)
        assertTrue(h.logLines.toString(), EvalCardLog.pairListEventLine("ignored", "sess-1", "in_flight") in h.logLines)
        // the purpose changes while it is out: the answer belongs to the asking that is gone
        h.pairConfirmHold = null
        vm.onPurposeChip(ScanPurposeChoice.NORMAL)
        hold.setResult(h.callableResultOf(confirmAnswer("a")))
        assertEquals(PairListUi.NotShown, pairs(vm))
    }

    // ---- the session's phase ----

    @Test
    fun `the choice can be confirmed while the scanned phone is scanning - and not once the session completed, cancelled or expired`() = withOwner { h, vm ->
        h.pairListResponse = listAnswer("a", "b")
        chooseAfter(vm)
        h.deliverSnapshot("sess-1", fields = mapOf("status" to "scanning"))
        assertEquals(Phase.SCANNING, vm.state.value.phase)
        assertTrue(pairListVisible(vm.state.value.phase, pairs(vm)))
        vm.onPairCandidate("a")
        h.pairConfirmResponse = confirmAnswer("a")
        vm.onPairConfirm()
        assertTrue("confirmed while scanning", pairs(vm) is PairListUi.Linked)
        for (status in listOf("completed", "expired", "cancelled")) {
            h.deliverSnapshot("sess-1", fields = mapOf("status" to status))
            assertTrue("$status", !pairListVisible(vm.state.value.phase, pairs(vm)))
            val before = h.callableCalls("confirmMaintenanceAfterLink")
            vm.onPairCandidate("b")
            vm.onPairConfirm()
            assertEquals("$status: nothing is sent", before, h.callableCalls("confirmMaintenanceAfterLink"))
        }
        assertTrue(h.logLines.toString(), EvalCardLog.pairListEventLine("ignored", "sess-1", "not_visible") in h.logLines)
    }

    // ---- the exit gate ----

    @Test
    fun `a shut gate - an accepted after purpose asks nothing, the list stays unshown, and not a line is printed`() = withOwner(gateOpen = false) { h, vm ->
        chooseAfter(vm)
        assertEquals("the purpose itself is not behind this gate", 1, h.callableCalls("setSessionPurpose"))
        assertEquals(0, h.callableCalls("listOpenMaintenanceBefores"))
        assertEquals(0, h.callableCalls("confirmMaintenanceAfterLink"))
        assertEquals(PairListUi.NotShown, pairs(vm))
        assertTrue(h.logLines.toString(), h.logLines.none { it.startsWith("pair-link ") })
        vm.onPairCandidate("a")
        vm.onPairConfirm()
        assertEquals(0, h.callableCalls("confirmMaintenanceAfterLink"))
    }
}
