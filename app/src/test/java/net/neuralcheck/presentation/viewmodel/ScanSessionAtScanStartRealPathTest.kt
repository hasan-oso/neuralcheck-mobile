package net.neuralcheck.presentation.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Order 2026-10-14 (user decision, device check 03 round 2, «Record the session at scan start»): "The scan keeps the
 * session it started in; its analysis and its evaluation draft use that one, never the session joined later. No new
 * text." This file is the EVALUATION-DRAFT half, through the real join path ([RealJoinPathHarness]: only the network /
 * identity boundary is replaced): a session joined by typing the code or by scanning the QR, the real
 * [net.neuralcheck.data.remote.ScanSessionHolder], the real [net.neuralcheck.data.remote.ScanSessionRecords], the
 * real [TechnicianEvaluationViewModel.bind], and — for the persisted row — the view model's real tap pipeline. The
 * ANALYSIS half is `AiScoringEngineSessionTest`.
 *
 * What is not real here (declared, not hidden): `harness.startScan` calls the real `recordAtScanStart`, but the call is
 * made by the test, not by `StressTestService` (which cannot run on the JVM) — that its one call site sits where the
 * scan's id is minted is pinned as text in `ScanSessionAtScanStartSourceShapeTest`; Room is `FakeSessionEvaluationDao`.
 *
 * ── The order of events on the phone, as these tests replay it ──
 * 1. the device joins A  2. the scan screen builds its view model (it snapshots the holder: the card's visibility gate)
 * 3. StressTestService mints the scan's id and records the holder's session  4. [B is joined]
 * 5. the first Running state binds the view model to the scan id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class ScanSessionAtScanStartRealPathTest(private val path: JoinEntryPoint) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun paths(): List<JoinEntryPoint> = JoinEntryPoint.values().toList()

        private const val CODE_A = "NC-AB3F7K"
        private const val CODE_B = "NC-ZZ9Q2M"
        private const val SCAN_ID = "scan-0001"

        private const val SESSION_A = "#abe633f3a47a" // sess-1
        private const val SESSION_B = "#2b8ea9758113" // sess-2
        private const val SCAN = "#d6b64f1f202b"      // scan-0001

        private val RAW_IDS = listOf("sess-1", "sess-2", "owner-1", "owner-2", "anon-uid-1", SCAN_ID, CODE_A, CODE_B)
    }

    private lateinit var harness: RealJoinPathHarness

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = RealJoinPathHarness()
    }

    @After
    fun tearDown() {
        harness.close()
        Dispatchers.resetMain()
    }

    private fun answer(sessionId: String, ownerId: String, evaluationEnabled: Boolean = true): HashMap<String, Any?> =
        hashMapOf(
            "sessionId" to sessionId,
            "ownerId" to ownerId,
            "status" to "scanning",
            "evaluationEnabled" to evaluationEnabled
        )

    /** Joins the session the server's next answer names, through the entry point under test. */
    private fun join(code: String) {
        when (path) {
            JoinEntryPoint.TYPED_CODE -> harness.joinViewModel().join(code)
            JoinEntryPoint.QR -> harness.scannerViewModel().onQrDetected(code)
        }
    }

    private val lines: List<String> get() = harness.logLines.toList()

    private fun assertNoRawIdAnywhere() = lines.forEach { line ->
        RAW_IDS.forEach { id -> assertFalse("`$line` carries the raw id $id", line.contains(id)) }
    }

    // ======================= A, then B joined later =======================

    @Test
    fun `a scan that started under A binds its draft to A after B is joined - and the row a tap persists carries A`() {
        harness.joinResponse = answer("sess-1", "owner-1")
        join(CODE_A)
        val dao = FakeSessionEvaluationDao()
        val vm = harness.evaluationViewModel(dao) // 2. snapshots A
        harness.startScan(SCAN_ID)                // 3. the scan starts under A

        harness.joinResponse = answer("sess-2", "owner-2")
        join(CODE_B)                              // 4. B joined later
        assertEquals("the holder now holds B", "sess-2", harness.holder.get())
        harness.logLines.clear()

        vm.bind(SCAN_ID)                          // 5.
        harness.awaitDraft(vm)

        assertEquals("the draft is A's, not the holder's current B", "sess-1", vm.draft!!.sessionId)
        assertEquals(
            listOf(
                "scan-session-differs use=draft scan=$SCAN recorded=$SESSION_A holder=$SESSION_B",
                "eval-bind result=started scan=$SCAN",
                "eval-bind-read-start scan=$SCAN",
                "eval-bind-read-done found=false scan=$SCAN",
                "eval-bind-draft state=new scan=$SCAN"
            ),
            lines
        )

        // the row that reaches disk (and, from there, submitEvaluation) names A
        vm.onGradeTap("good")
        awaitCondition("the first answer's row") { dao.rows[SCAN_ID] != null }
        assertEquals("sess-1", dao.rows[SCAN_ID]!!.sessionId)
        assertNoRawIdAnywhere()
    }

    @Test
    fun `without a later join the draft carries the recorded session and no differs line is printed`() {
        harness.joinResponse = answer("sess-1", "owner-1")
        join(CODE_A)
        val vm = harness.evaluationViewModel(FakeSessionEvaluationDao())
        harness.startScan(SCAN_ID)
        harness.logLines.clear()

        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)

        assertEquals("sess-1", vm.draft!!.sessionId)
        assertFalse(lines.any { it.startsWith("scan-session-") })
    }

    @Test
    fun `the holder emptied after the scan started (a sign-out) does not take the draft's session`() {
        harness.joinResponse = answer("sess-1", "owner-1")
        join(CODE_A)
        val vm = harness.evaluationViewModel(FakeSessionEvaluationDao())
        harness.startScan(SCAN_ID)
        harness.holder.clear(net.neuralcheck.data.remote.ScanSessionHolder.ClearReason.SIGN_OUT)
        harness.logLines.clear()

        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)

        assertEquals("sess-1", vm.draft!!.sessionId)
        assertEquals(
            "scan-session-differs use=draft scan=$SCAN recorded=$SESSION_A holder=missing",
            lines.first()
        )
    }

    // ======================= a personal scan stays personal =======================

    @Test
    fun `a scan that started in no session stays personal - a session joined afterwards gives it no draft`() {
        harness.startScan(SCAN_ID) // 3. the holder is empty: a personal scan
        harness.joinResponse = answer("sess-2", "owner-2")
        join(CODE_B)               // joined afterwards
        val dao = FakeSessionEvaluationDao()
        val vm = harness.evaluationViewModel(dao) // the view model's own snapshot says: an evaluation session
        assertEquals(true, vm.isEvaluationSession)
        harness.logLines.clear()

        vm.bind(SCAN_ID)

        assertNull("no draft", vm.draft)
        assertEquals("bind started no IO", 0, dao.getByScanIdCalls)
        assertEquals(
            listOf(
                "scan-session-holder-ignored use=draft scan=$SCAN holder=$SESSION_B",
                "eval-bind result=no-session-id scan=$SCAN"
            ),
            lines
        )
        assertNoRawIdAnywhere()
    }

    @Test
    fun `a scan whose recorded session is not an evaluation session gets no draft even if the view model's snapshot says it is`() {
        harness.joinResponse = answer("sess-1", "owner-1", evaluationEnabled = false)
        join(CODE_A)
        harness.startScan(SCAN_ID) // recorded: sess-1, evaluationEnabled=false
        harness.joinResponse = answer("sess-1", "owner-1", evaluationEnabled = true)
        join(CODE_A)               // the same session answered again, now with the flag on
        val dao = FakeSessionEvaluationDao()
        val vm = harness.evaluationViewModel(dao)
        assertEquals("the snapshot says yes", true, vm.isEvaluationSession)
        harness.logLines.clear()

        vm.bind(SCAN_ID)

        assertNull(vm.draft)
        assertEquals(0, dao.getByScanIdCalls)
        assertEquals(listOf("eval-bind result=not-evaluation-session scan=$SCAN"), lines)
    }

    @Test
    fun `a scan whose recorded session had no flag in its join answer is refused as flag-absent - even if the view model snapshot says yes (order 2026-10-15)`() {
        harness.joinResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "ownerId" to "owner-1", "status" to "scanning") // no flag
        join(CODE_A)
        harness.startScan(SCAN_ID) // recorded: sess-1, evaluationEnabled=absent
        harness.joinResponse = answer("sess-1", "owner-1", evaluationEnabled = true)
        join(CODE_A)               // the same session answered again, now with the flag on
        val dao = FakeSessionEvaluationDao()
        val vm = harness.evaluationViewModel(dao)
        assertEquals("the snapshot says yes", true, vm.isEvaluationSession)
        harness.logLines.clear()

        vm.bind(SCAN_ID)

        assertNull(vm.draft)
        assertEquals(0, dao.getByScanIdCalls)
        assertEquals(listOf("eval-bind result=flag-absent scan=$SCAN"), lines)
    }

    // ======================= control =======================

    @Test
    fun `control - the record is what decides - a scan with a record and a holder that differs gets the record, not the holder`() {
        // The same scenario as the first test, but proving the NEGATIVE of the old behaviour: with the holder's session
        // the draft would have said sess-2. If `bind` read the holder (or the construction-time snapshot taken after a
        // later join), this would fail.
        harness.joinResponse = answer("sess-1", "owner-1")
        join(CODE_A)
        harness.startScan(SCAN_ID)
        harness.joinResponse = answer("sess-2", "owner-2")
        join(CODE_B)
        val vm = harness.evaluationViewModel(FakeSessionEvaluationDao()) // built AFTER B: its snapshot is B's

        vm.bind(SCAN_ID)
        harness.awaitDraft(vm)

        assertNotNull(vm.draft)
        assertEquals("sess-1", vm.draft!!.sessionId)
    }
}
