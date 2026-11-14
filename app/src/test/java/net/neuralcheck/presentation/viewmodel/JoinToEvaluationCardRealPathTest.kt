package net.neuralcheck.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.BuildConfig
import net.neuralcheck.data.remote.ScanSessionHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Order 2026-10-10 (coordinator, relaying the user's item 3): "a test that replays the real path —
 * join with an identity -> scan -> the evaluation form appears".
 *
 * The device-check diagnosis (DC-03, 1.0.11 = af51afc) found no code cause and said, as its own known
 * limit: no JVM test covered "join response -> `JoinSessionResult` -> `ScanSessionHolder.setFromJoin` ->
 * `TechnicianEvaluationViewModel.isEvaluationSession`", and the earlier tests replaced the service layer
 * (rule 37). This file runs that chain with only the network / identity boundary replaced — see
 * [RealJoinPathHarness] for exactly what is real and what is not.
 *
 * ── What the server answers (relayed by the coordinator, NOT read from this repository) ──
 * `functions/src/sessions/joinScanSession.js:239-248` in neuralcheck-backend at `a08eb4a`:
 * `return { sessionId, ownerId, status, evaluationEnabled: decision.value }` with `decision.value` a boolean,
 * one branch for the first join and for a re-join. So the sample below is that shape: a `HashMap` holding the
 * three strings and a `Boolean`.
 *
 * ── What this does NOT prove ──
 * That the real server returns this shape (relayed, unverified here); that the Firebase SDK builds the map as
 * the bytecode reading says; that the card is DRAWN (no render test exists - Robolectric and compose-ui-test are test dependencies
 * since 3284cca, but nothing renders this card — the card's gates and placement are pinned by static source tests in
 * `EvaluationCardSourceShapeTest`, which is a weaker claim and says so).
 *
 * Both entry points are run: the typed code ([JoinSessionViewModel.join]) and the QR scanner
 * ([QRScannerViewModel.onQrDetected]).
 *
 * Rule 23: no earlier test pinned any of this (`git grep -l "TechnicianEvaluationViewModel" -- app/src/test`
 * had no test that builds the view model), so nothing is replaced.
 */
@OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain / UnconfinedTestDispatcher, as in the other view-model tests
@RunWith(Parameterized::class)
class JoinToEvaluationCardRealPathTest(private val path: JoinEntryPoint) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun paths(): List<JoinEntryPoint> = JoinEntryPoint.values().toList()

        private const val CODE = "NC-AB3F7K"
        private const val SCAN_ID = "scan-0001"
    }

    private lateinit var harness: RealJoinPathHarness // reassigned by the one test that needs a signed-in start
    private lateinit var scope: CoroutineScope
    private val scannersToCancel = mutableListOf<QRScannerViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scope = CoroutineScope(UnconfinedTestDispatcher())
        harness = RealJoinPathHarness()
    }

    @After
    fun tearDown() {
        scope.cancel()
        scannersToCancel.forEach { it.viewModelScope.cancel() }
        harness.close()
        Dispatchers.resetMain()
    }

    /** The server's `return { sessionId, ownerId, status, evaluationEnabled }` as Firebase decodes it. */
    private fun answer(vararg extra: Pair<String, Any?>): HashMap<String, Any?> =
        hashMapOf<String, Any?>(
            "sessionId" to "sess-1",
            "ownerId" to "owner-1",
            "status" to "scanning"
        ).also { m -> extra.forEach { (k, v) -> m[k] = v } }

    /** Joins through the entry point under test and asserts the join itself succeeded. */
    private fun join(code: String = CODE) {
        when (path) {
            JoinEntryPoint.TYPED_CODE -> {
                val vm = harness.joinViewModel()
                vm.join(code)
                val s = vm.state.value
                assertNull("the join must not surface an error: ${s.error}", s.error)
                assertEquals("sess-1", s.sessionId)
            }
            JoinEntryPoint.QR -> {
                val vm = harness.scannerViewModel()
                vm.onQrDetected(code)
                assertEquals(QRScannerViewModel.QrState.JoinSuccess("sess-1", net.neuralcheck.domain.consent.ConsentPartRead.Absent), vm.state.value)
            }
        }
    }

    /** What the scan screen does: build the view model once the scan screen is up, then bind on the first scanId. */
    private fun scanScreen(dao: FakeSessionEvaluationDao = FakeSessionEvaluationDao()) =
        harness.evaluationViewModel(dao) to dao

    // ======================= the value matrix (rule 30) =======================

    @Test
    fun `evaluationEnabled true (boolean) - join, scan screen, the view model says yes and bind builds the draft`() {
        harness.joinResponse = answer("evaluationEnabled" to true)

        join()

        // The join reached the (replaced) callable once, with the code, after the sign-in.
        assertEquals(listOf<Any?>(mapOf("code" to CODE)), harness.joinRequests.toList())
        assertEquals(
            "the join context the scan screen will snapshot",
            ScanSessionHolder.ScanSessionInfo(sessionId = "sess-1", ownerId = "owner-1", evaluationEnabled = true),
            harness.holder.getInfo()
        )

        val (vm, dao) = scanScreen()
        assertTrue("isEvaluationSession", vm.isEvaluationSession)
        assertNull("no draft until bind", vm.draft)

        harness.startScan(SCAN_ID) // the scan starts (StressTestService records the session it started in) before the card's bind
        vm.bind(SCAN_ID)
        harness.awaitDraft(vm) // the draft bind builds on Dispatchers.IO, and the line it prints after it

        val draft = vm.draft!!
        assertEquals(SCAN_ID, draft.scanId)
        assertEquals("the session id is the one the join returned", "sess-1", draft.sessionId)
        assertEquals("bind looked for an earlier row exactly once", 1, dao.getByScanIdCalls)
    }

    @Test
    fun `evaluationEnabled false (boolean) - the view model says no and bind does nothing`() {
        harness.joinResponse = answer("evaluationEnabled" to false)
        join()

        val (vm, dao) = scanScreen()
        assertFalse(vm.isEvaluationSession)
        vm.bind(SCAN_ID)

        assertNull("no draft", vm.draft)
        assertEquals("bind started no IO", 0, dao.getByScanIdCalls)
        assertEquals("the join context is still stored", "sess-1", harness.holder.get())
    }

    @Test
    fun `evaluationEnabled absent from the answer - the holder keeps it ABSENT (not false), the card is not shown, and bind refuses it by name`() {
        harness.joinResponse = answer() // no evaluationEnabled key at all
        join()

        val info = harness.holder.getInfo()
        assertNotNull(info)
        assertNull("an absence stays absent - order 2026-10-15, rule 22", info!!.evaluationEnabled)
        val (vm, dao) = scanScreen()
        assertFalse("the gate shows the card only for an explicit true", vm.isEvaluationSession)
        vm.bind(SCAN_ID)
        assertNull(vm.draft)
        assertEquals(0, dao.getByScanIdCalls)
    }

    @Test
    fun `evaluationEnabled present but null (Firebase decodes a JSON null to null) - ABSENT too, and the view model says no`() {
        harness.joinResponse = answer("evaluationEnabled" to null)
        join()

        assertNull(harness.holder.getInfo()!!.evaluationEnabled)
        val (vm, _) = scanScreen()
        assertFalse(vm.isEvaluationSession)
    }

    @Test
    fun `evaluationEnabled as the string true - Gson reads it as true, so the view model says yes (what the code does today)`() {
        harness.joinResponse = answer("evaluationEnabled" to "true")
        join()

        assertEquals(true, harness.holder.getInfo()!!.evaluationEnabled)
        val (vm, dao) = scanScreen()
        assertTrue(vm.isEvaluationSession)
        harness.startScan(SCAN_ID) // the scan starts (StressTestService records the session it started in) before the card's bind
        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)
        assertEquals(1, dao.getByScanIdCalls)
    }

    @Test
    fun `alternate key evaluation_enabled true - the view model says yes`() {
        harness.joinResponse = answer("evaluation_enabled" to true)
        join()

        assertEquals(true, harness.holder.getInfo()!!.evaluationEnabled)
        val (vm, _) = scanScreen()
        assertTrue(vm.isEvaluationSession)
    }

    @Test
    fun `alternate key evaluation_enabled false - the view model says no`() {
        harness.joinResponse = answer("evaluation_enabled" to false)
        join()

        assertEquals(false, harness.holder.getInfo()!!.evaluationEnabled)
        val (vm, _) = scanScreen()
        assertFalse(vm.isEvaluationSession)
    }

    // ======================= the second join of the same session =======================

    @Test
    fun `a second join of the same session, same answer shape - the value is not dropped and no second sign-in happens`() {
        harness.joinResponse = answer("evaluationEnabled" to true)

        join()
        val afterFirst = harness.holder.getInfo()
        join() // a fresh view model for the same session, as when the customer leaves and scans again

        assertEquals("one sign-in only: the device was already signed in for the second join", 1, harness.signInAnonymouslyCalls)
        assertEquals("both joins reached the callable", 2, harness.callableCalls("joinScanSession"))
        assertEquals(afterFirst, harness.holder.getInfo())
        assertEquals(true, harness.holder.getInfo()!!.evaluationEnabled)

        val (vm, dao) = scanScreen()
        assertTrue(vm.isEvaluationSession)
        harness.startScan(SCAN_ID) // the scan starts (StressTestService records the session it started in) before the card's bind
        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)
        assertEquals(1, dao.getByScanIdCalls)
    }

    @Test
    fun `a second join answered WITHOUT the field replaces the first answer - the holder keeps the last answer only (documents setFromJoin, which does not merge)`() {
        // Not a claim about the server (relayed: the re-join answer has the same shape). It documents what the app
        // does if some answer ever lacks the field: ScanSessionHolder.setFromJoin writes the whole record.
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()
        harness.joinResponse = answer()
        join()

        assertNull("the last answer had no flag: absent, not the false the DTO used to default to", harness.holder.getInfo()!!.evaluationEnabled)
        val (vm, _) = scanScreen()
        assertFalse(vm.isEvaluationSession)
    }

    // ======================= the order of events on the phone =======================

    private fun launchFlavorOnly() =
        assumeTrue("needs REQUIRE_SUBSCRIPTION (launch flavor)", BuildConfig.REQUIRE_SUBSCRIPTION)

    @Test
    fun `join, then the pre-scan gate, then the scan screen - nothing between them clears the holder, and no checkSubscription call is made`() {
        launchFlavorOnly()
        harness.joinResponse = answer("evaluationEnabled" to true)

        // What the app has subscribed to the auth state before the customer joins: the real uidFlow (the real
        // AuthStateListener registration), and — on the QR path — the real in-app collector of it.
        val uids = mutableListOf<String?>()
        scope.launch { harness.authManager.uidFlow.collect { uids.add(it) } }
        if (path == JoinEntryPoint.QR) {
            val scanner = harness.scannerViewModel()
            scanner.preRepairScans.let { flow -> scope.launch { flow.collect { } } }
            scannersToCancel.add(scanner)
        }
        assertTrue("the listener is registered before the join", harness.authListeners.isNotEmpty())
        assertEquals("the device is not signed in yet: the first uid event is null", listOf<String?>(null), uids.toList())

        // 1. the join (signs in anonymously first)
        join()
        val joined = harness.holder.getInfo()
        assertEquals(ScanSessionHolder.ScanSessionInfo("sess-1", "owner-1", true), joined)
        assertEquals(
            "sign-in, its auth-state event, then the join callable",
            listOf("auth:signInAnonymously", "auth:listeners-fired", "callable:joinScanSession"),
            harness.events.toList()
        )
        assertEquals("the auth-state event reached the real collector with the device's uid", "anon-uid-1", uids.last())

        // 2. an auth-state event that arrives AFTER the join (Firebase delivers them on the main thread, later)
        harness.fireAuthStateChanged()
        assertEquals("a late auth-state event does not touch the holder", joined, harness.holder.getInfo())

        // 3. the pre-scan screen's view model (efd8210 reads the holder in its init) and the start gate
        val preScan = harness.preScanViewModel()
        assertEquals(joined, harness.holder.getInfo())
        assertEquals("the banner stays hidden in a session", PreScanViewModel.TrialBannerState.Hidden, preScan.trialBanner.value)
        preScan.checkAccess()
        assertEquals(PreScanViewModel.AccessState.Allowed, preScan.accessState.value)
        assertEquals("the gate leaves the holder alone", joined, harness.holder.getInfo())
        assertEquals(
            "no checkSubscription call from the init block or from the gate",
            0, harness.callableCalls("checkSubscription")
        )

        harness.fireAuthStateChanged()
        assertEquals(joined, harness.holder.getInfo())

        // 4. the scan screen: the view model is built here, snapshots the holder, then binds on the first scanId
        val dao = FakeSessionEvaluationDao()
        val vm = harness.evaluationViewModel(dao)
        assertTrue("the card gate is open after the whole sequence", vm.isEvaluationSession)
        harness.startScan(SCAN_ID) // the scan starts (StressTestService records the session it started in) before the card's bind
        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)
        assertEquals("sess-1", vm.draft!!.sessionId)
        assertEquals(
            "the only callable the whole sequence asked for is the join",
            listOf("callable:joinScanSession"),
            harness.events.filter { it.startsWith("callable:") }
        )
    }

    @Test
    fun `control - without a session the pre-scan init does call checkSubscription (this test sees such a call)`() {
        launchFlavorOnly()
        // The static mocks are per thread and one set may be open at a time: swap the harness, tearDown closes the new one.
        harness.close()
        harness = RealJoinPathHarness(signedInAtStart = true)

        harness.preScanViewModel()

        assertEquals(1, harness.callableCalls("checkSubscription"))
    }

    @Test
    fun `control - a clear() between the join and the scan screen hides the card (this test sees a clear)`() {
        harness.joinResponse = answer("evaluationEnabled" to true)
        join()

        harness.holder.clear(ScanSessionHolder.ClearReason.SIGN_OUT)

        val (vm, dao) = scanScreen()
        assertFalse(vm.isEvaluationSession)
        vm.bind(SCAN_ID)
        assertNull(vm.draft)
        assertEquals(0, dao.getByScanIdCalls)
    }
}

/** The two ways a customer device joins a session: typing the code, or scanning the owner's QR. */
enum class JoinEntryPoint { TYPED_CODE, QR }
