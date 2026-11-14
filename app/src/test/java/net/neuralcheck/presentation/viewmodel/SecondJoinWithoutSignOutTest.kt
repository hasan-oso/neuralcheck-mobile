package net.neuralcheck.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.remote.ScanSessionRecords
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The second join on the same phone, with no sign-out - row A-P6-04 (انضمام ثانٍ بلا خروج), `plan/PLAN-06-DATA-COLLECTION.md` section 3-b item 7 (lines 569-571) and screen ف١٦ (:958).
 *
 * The plan's fact 4 (PLAN-06:152-155) was: «الجلسة الثانية على الهاتف نفسه تحتاج تسجيل خروج ... والخروج يمسح Room وملفّات السلاسل». Since e40c165 (A-SUB wave 1, user decision 1, «P1») a customer's phone - a device
 whose only user is anonymous - starts at `login` where «لدي كود فحص» is, and the report's «فحص جديد» goes to the code-entry screen, so a second code is typed without signing out
 (`AnonymousRouting.kt`; the navigation is tested by `AnonymousNavigationTest`). What no test pinned was the session side of it: a second code of a DIFFERENT session, on a device that already holds the first
 session's record and an anonymous uid. This file runs that chain through both join entry points with only the network and identity boundary replaced ([RealJoinPathHarness]).
 It adds no behaviour: it pins what the second join does today. (The plan's «لدي كود فحص» in the HOME screen for an anonymous user is the part that e40c165 contradicts - home is closed to that user - and is
 reported as a decision, not built.)
 *
 * Rule 23: no earlier test pinned a second join of another session (`JoinToEvaluationCardRealPathTest` runs the second join of the SAME session), so none is replaced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecondJoinWithoutSignOutTest {

    private lateinit var harness: RealJoinPathHarness
    private val scanners = mutableListOf<QRScannerViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        harness = RealJoinPathHarness()
    }

    @After
    fun tearDown() {
        scanners.forEach { it.viewModelScope.cancel() }
        harness.close()
        Dispatchers.resetMain()
    }

    private fun answer(session: String, owner: String, evaluationEnabled: Boolean) =
        hashMapOf<String, Any?>("sessionId" to session, "ownerId" to owner, "status" to "scanning", "evaluationEnabled" to evaluationEnabled)

    private enum class Entry { TYPED, QR }

    private fun join(entry: Entry, code: String, response: Map<String, Any?>) {
        harness.joinResponse = response
        when (entry) {
            Entry.TYPED -> {
                val vm = harness.joinViewModel()
                vm.join(code)
                assertNull("the join must not surface an error: ${vm.state.value.error}", vm.state.value.error)
                assertEquals(response["sessionId"], vm.state.value.sessionId)
            }
            Entry.QR -> {
                val vm = harness.scannerViewModel().also { scanners.add(it) }
                vm.onQrDetected(code)
                assertEquals(QRScannerViewModel.QrState.JoinSuccess(response["sessionId"] as String, net.neuralcheck.domain.consent.ConsentPartRead.Absent), vm.state.value)
            }
        }
    }

    /** What the scoring engine does after a final analysis answer for the session the scan used. */
    private fun analysisDone(scanId: String) {
        harness.records.consume(scanId)
        harness.holder.clear(ScanSessionHolder.ClearReason.ANALYSIS_COMPLETE)
    }

    private fun check(first: Entry, second: Entry) {
        // the first customer: joins, scans, the analysis is final
        join(first, "NC-AAAAAA", answer("sess-A", "owner-1", true))
        assertEquals(ScanSessionHolder.ScanSessionInfo("sess-A", "owner-1", true), harness.holder.getInfo())
        assertEquals(ScanSessionRecords.Recorded("sess-A", true), harness.startScan("scan-A"))
        analysisDone("scan-A")
        assertNull("the holder is empty between the two customers", harness.holder.getInfo())

        // the next customer, on the same phone, no sign-out: a different session, another owner, the flag false
        join(second, "NC-BBBBBB", answer("sess-B", "owner-2", false))
        assertEquals("no sign-out and no second sign-in: the one anonymous uid serves both", 1, harness.signInAnonymouslyCalls)
        assertEquals("both joins reached the server", 2, harness.callableCalls("joinScanSession"))
        assertEquals(
            "the holder holds the SECOND session whole - nothing of the first (owner, flag) survives",
            ScanSessionHolder.ScanSessionInfo("sess-B", "owner-2", false), harness.holder.getInfo()
        )
        assertEquals(ScanSessionRecords.Recorded("sess-B", false), harness.startScan("scan-B"))
        assertEquals(
            "the second scan's analysis reads its own record",
            ScanSessionRecords.Recorded("sess-B", false), harness.records.sessionFor("scan-B", ScanSessionRecords.Use.ANALYSIS)
        )
        assertNull("the first scan's record was spent with its analysis and is not the second's", harness.records.sessionFor("scan-A", ScanSessionRecords.Use.ANALYSIS))
        assertTrue("no sign-out line, no listener loop: the auth listeners fired for the one sign-in only", harness.events.count { it == "auth:signInAnonymously" } == 1)
    }

    @Test
    fun `typed code then typed code - a second session on the same phone with no sign-out`() = check(Entry.TYPED, Entry.TYPED)

    @Test
    fun `QR then typed code`() = check(Entry.QR, Entry.TYPED)

    @Test
    fun `typed code then QR`() = check(Entry.TYPED, Entry.QR)

    @Test
    fun `QR then QR`() = check(Entry.QR, Entry.QR)

    @Test
    fun `a second code joined while the first scan's record is still held - the first scan keeps its own session and the holder moves on`() {
        join(Entry.TYPED, "NC-AAAAAA", answer("sess-A", "owner-1", true))
        harness.startScan("scan-A")
        // the first scan has not finished (no analysis yet); the phone joins another session
        join(Entry.TYPED, "NC-BBBBBB", answer("sess-B", "owner-2", true))
        assertEquals(ScanSessionHolder.ScanSessionInfo("sess-B", "owner-2", true), harness.holder.getInfo())
        assertEquals(
            "scan-A's analysis still names sess-A: a session joined after the scan started is not the scan's",
            ScanSessionRecords.Recorded("sess-A", true), harness.records.sessionFor("scan-A", ScanSessionRecords.Use.ANALYSIS)
        )
        assertEquals(1, harness.signInAnonymouslyCalls)
    }
}
