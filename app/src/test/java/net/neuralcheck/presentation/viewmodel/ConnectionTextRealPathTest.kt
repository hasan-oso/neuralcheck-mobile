package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import net.neuralcheck.data.remote.JoinSessionResult
import net.neuralcheck.data.remote.ScanSessionRecords
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.repository.TimeSeriesRepository
import net.neuralcheck.domain.evaluation.EvaluationSubmissionStatusHolder
import net.neuralcheck.domain.evaluation.EvaluationSubmitter
import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import net.neuralcheck.domain.scoring.AiScoringEngine
import net.neuralcheck.domain.scoring.AnalysisProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The connection text at the two places whose failure is not a screen of its own - the evaluation's submission and the analysis call - whole, with the real texts
 * (batch 4, item 1; user decision 1, 2026-10-16: «يُقرأ الخطأ الداخلي بالطريقة نفسها في الانضمام المكتوب، وإلغاء المالك، وإرسال التقييم، ونداء التحليل، بالنصّ القائم»).
 * The typed join and the owner's cancel are in `SessionJoinRefusalPathTest` and `SessionCancelPathTest`; the customer's leave in `SessionLeavePathTest`.
 *
 * REAL here, rule 37: the SDK's own `onFailure` builds the failure ([SessionWireFixtures.offlineFailures]; the SDK's parse of the wire JSON for the refusal that carries a
 * `reasonCode`), the real [net.neuralcheck.data.remote.FirebaseAnalysisService] (`submitEvaluation` and `analyzeDevice` through `callWithTimeout` and the task's `await`),
 * the real [EvaluationSubmitter] with its own IO scope and the real [EvaluationSubmissionStatusHolder], the real [AiScoringEngine] (its fingerprint, its cache lookup of
 * `reports/{scanId}`, its `catch`, `analysisErrorMessage`, the session holder and the scan's record), and the app's string resources. REPLACED: the HTTP round trip and the
 * Firestore client (the harness), Room (the harness's [FakeSessionEvaluationDao], a declared substitute) and the time-series file reader (no file: the payload then says why).
 *
 * What a disconnect did before this change, and does now - what the tests below pin as "nothing else changes": the TEXT. The evaluation row stays in Room unsent for the
 * sync worker's retry, and a failed analysis keeps the scan's record of its session for the resend; none of that read the text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ConnectionTextRealPathBase {

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

    // ---------------------------------------------------------------- the evaluation's submission ----------------------------------------------------------------

    private fun evaluationRow(scanId: String) = SessionEvaluationEntity(
        scanId = scanId,
        sessionId = "session-1",
        technicianGradeRaw = "good",
        categoryRatingsJson = "{}",
        visibleDefectsJson = "[]",
        shownAtMs = 1_000L,
        lastAnswerAtMs = 2_000L,
        submitted = false
    )

    /** What `ActiveScanViewModel`'s `Completed` branch does: submit the scan's evaluation, and read the failure the status holder publishes for the next screen. */
    private fun submitAndReadFailure(h: RealJoinPathHarness, dao: FakeSessionEvaluationDao, scanId: String): EvaluationSubmissionStatusHolder.ScanFailure = runBlocking {
        val statusHolder = EvaluationSubmissionStatusHolder()
        val submitter = EvaluationSubmitter(dao, h.service, statusHolder)
        // subscribed BEFORE the submission can publish (the holder has replay = 0)
        val received = async(start = CoroutineStart.UNDISPATCHED) { statusHolder.failures.first() }
        submitter.submitOnScanComplete(scanId, context)
        kotlinx.coroutines.withTimeout(60_000) { received.await() } // a hang guard: a failure that is never published would otherwise wait for ever
    }

    @Test
    fun `evaluation submission - a real disconnect publishes the connection text, and the row stays unsent for the sync retry, which sends it when the connection is back`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            RealJoinPathHarness(viewModelContext = context).use { h ->
                val dao = FakeSessionEvaluationDao().also { it.rows["scan-1"] = evaluationRow("scan-1") }
                h.submitEvaluationFailure = failure
                val received = submitAndReadFailure(h, dao, "scan-1")
                assertEquals(label, EvaluationSubmissionStatusHolder.ScanFailure("scan-1", existing(R.string.login_error_network)), received)
                assertEquals("$label: one attempt reached the callable", 1, h.submitEvaluationRequests.size)
                // nothing else changed: not sent, not marked terminal - it stays eligible for the sync worker, as it did before the text was read from the cause
                val row = dao.rows.getValue("scan-1")
                assertFalse("$label: not submitted", row.submitted)
                assertFalse("$label: not a final failure", row.finalFailure)
                assertEquals("$label: still pending", listOf("scan-1"), runBlocking { dao.getPending() }.map { it.scanId })
                h.submitEvaluationFailure = null // the connection is back
                val sent = runBlocking { EvaluationSubmitter(dao, h.service, EvaluationSubmissionStatusHolder()).submitPendingBlocking() }
                assertEquals("$label: the retry sends it", 1, sent)
                assertTrue("$label: and marks it submitted", dao.rows.getValue("scan-1").submitted)
            }
        }
    }

    @Test
    fun `evaluation submission - a failure that carries a reasonCode, even the text network, is not a disconnect - the evaluation generic text`() {
        RealJoinPathHarness(viewModelContext = context).use { h ->
            val dao = FakeSessionEvaluationDao().also { it.rows["scan-1"] = evaluationRow("scan-1") }
            h.submitEvaluationFailure = SessionWireFixtures.REASON_CODE_NETWORK_REFUSAL.exception()
            val received = submitAndReadFailure(h, dao, "scan-1")
            assertEquals(
                "the server answered: not the connection text",
                EvaluationSubmissionStatusHolder.ScanFailure("scan-1", existing(R.string.eval_submit_failed_generic)),
                received
            )
            assertNotEquals(existing(R.string.login_error_network), received.message)
        }
    }

    // ---------------------------------------------------------------- the analysis call ----------------------------------------------------------------

    private fun scanReport(scanId: String) = ScanReport(
        scanId = scanId, scanType = ScanType.GENERAL, linkedScanId = null,
        deviceInfo = DeviceInfo("fp", "Model", "Mfr", "14", 34, "1080x2400", 8),
        scanMetadata = ScanMetadata(0L, 1L, 100, 95, 27f, 1L, true, null, "1.0.0"),
        functionalResults = emptyList(), timeSeriesSummary = null, antiTamperFlags = emptyList(),
        technicianInput = null, peerInput = null
    )

    /** The real engine over the real service; the harness is the network. A customer device: signed in anonymously already, in a joined session, with the scan's record made. */
    private fun analyse(h: RealJoinPathHarness, scanId: String) = runBlocking {
        val reader = mock<TimeSeriesFileReader>()
        val timeSeries = mock<TimeSeriesRepository> { on { getReader() } doReturn reader }
        val engine = AiScoringEngine(
            context = context,
            fingerprintBuilder = BehaviorFingerprintBuilder(),
            firebaseService = h.service,
            timeSeriesRepository = timeSeries,
            scanSessionHolder = h.holder,
            scanSessionRecords = h.records,
            authManager = h.authManager,
            evaluationSubmitter = mock<EvaluationSubmitter>(),
            analysisProgress = AnalysisProgress()
        )
        engine.calculateScore(scanReport(scanId))
    }

    private fun joinedAndScanning(h: RealJoinPathHarness) {
        h.holder.setFromJoin(JoinSessionResult(sessionId = "sess-1", ownerId = "owner-1", status = "scanning", evaluationEnabled = true))
        h.startScan("scan-0001")
    }

    @Test
    fun `analysis - a real disconnect shows the connection text on the error card, and the scan keeps its session record for the resend`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                joinedAndScanning(h)
                h.analyzeFailure = failure
                val result = analyse(h, "scan-0001")
                assertEquals(label, existing(R.string.login_error_network), result.error)
                assertNull("$label: a failure carries no analysis", result.aiAnalysis)
                assertFalse("$label: and it is not the 'still processing' state", result.analysisInProgress)
                assertEquals("$label: the finished-report lookup was asked first, and found nothing", listOf("scan-0001"), h.reportReads.toList())
                assertEquals("$label: one live call", 1, h.analyzeRequests.size)
                assertEquals("$label: the call carried the scan's session", "sess-1", (h.analyzeRequests.single() as Map<*, *>)["sessionId"])
                // nothing else changed (none of it reads the text): the record stays for the resend; the holder is emptied by any failure but "still processing", as it was
                assertNotNull("$label: the record of the scan's session stays", h.records.sessionFor("scan-0001", ScanSessionRecords.Use.ANALYSIS))
                assertNull("$label: the holder is emptied, as by any failure", h.holder.getInfo())
            }
        }
    }

    @Test
    fun `analysis - a failure that carries a reasonCode, even the text network, is not a disconnect - the generic error text`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            joinedAndScanning(h)
            h.analyzeFailure = SessionWireFixtures.REASON_CODE_NETWORK_REFUSAL.exception()
            val result = analyse(h, "scan-0001")
            assertEquals("the server answered: not the connection text", existing(R.string.error_generic), result.error)
            assertNull(result.aiAnalysis)
        }
    }

    @Test
    fun `analysis - a timeout is not a disconnect, whatever layer it comes from`() {
        for ((label, failure) in listOf(
            "the call timed out (callWithTimeout's IOException)" to java.io.IOException("Callable 'analyzeDevice' timed out after 300000ms"),
            "SDK: a socket timeout (DEADLINE_EXCEEDED)" to SessionWireFixtures.exceptionFromTransportFailure(java.net.SocketTimeoutException("timeout"))
        )) {
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                joinedAndScanning(h)
                h.analyzeFailure = failure
                assertEquals(label, existing(R.string.error_generic), analyse(h, "scan-0001").error)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionTextRealPathArabicTest : ConnectionTextRealPathBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr")
class ConnectionTextRealPathTurkishTest : ConnectionTextRealPathBase()
