package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import net.neuralcheck.data.remote.JoinSessionResult
import net.neuralcheck.data.remote.model.BehaviorFingerprint
import net.neuralcheck.data.repository.TimeSeriesRepository
import net.neuralcheck.domain.evaluation.EvaluationSubmitter
import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import net.neuralcheck.domain.scoring.AiScoringEngine
import net.neuralcheck.domain.scoring.AnalysisProgress
import net.neuralcheck.util.LogRedaction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * The raw session code and session id stay out of logcat - A-SUB wave 1, item 10 (user decision «الآن»; rules 33/36 in spirit: a value the app printed is a value anything with the device can read; rule 37: a
 * real flow, not a read of the source). `SessionCodeLogGuardTest` reads the source; this one RUNS the six flows that print them and reads what reached `android.util.Log` (Robolectric's `ShadowLog`, every tag):
 *  - the QR scanner decodes a session link and joins (`QRScannerViewModel`);
 *  - the typed-code screen joins (`JoinSessionViewModel`);
 *  - the owner's screen creates a session, attaches its listener and gets a listener error (`GenerateSessionViewModel`, `sessionListenerErrorMessage`);
 *  - the analysis engine bootstraps an anonymous identity for a session scan (`AiScoringEngine`);
 *  - the pre-scan gate bypasses for a session (`PreScanViewModel`).
 * The values below are made up for this test and distinctive enough that a substring match means a leak. What must hold: none of them appears in ANY log line, and the events still print (their hash does).
 * The QR's drawing payload (`GenerateSessionScreen`) is a composable and is covered by the source guard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionCodeNotLoggedTest {

    private companion object {
        const val SESSION_CODE = "NC-ZQ4K9X"
        const val SESSION_LINK = "neuralcheck://session/NC-ZQ4K9X"
        const val SESSION_ID = "sess-secret-77"
        const val OWNER_SESSION_ID = "sess-secret-88"
        const val OWNER_CODE = "NC-OWNR77"
        const val ENGINE_SESSION_ID = "sess-secret-55"
        const val PRESCAN_SESSION_ID = "sess-secret-66"
        val SECRETS = listOf(SESSION_CODE, SESSION_LINK, SESSION_ID, OWNER_SESSION_ID, OWNER_CODE, ENGINE_SESSION_ID, PRESCAN_SESSION_ID, "zq4k9x", "ZQ4K9X", "OWNR77")
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun logged(): List<String> = ShadowLog.getLogs().mapNotNull { it.msg }

    private fun joinAnswer(id: String) = hashMapOf<String, Any?>("sessionId" to id, "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true)

    @Test
    fun `no flow that handles a session code or a session id prints it - the events print its hash`() {
        // 1. the QR scanner decodes a session link and joins
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.joinResponse = joinAnswer(SESSION_ID)
            h.scannerViewModel().onQrDetected(SESSION_LINK)
            assertEquals(SESSION_ID, h.holder.get())
        }
        // 2. the typed-code screen joins
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.joinResponse = joinAnswer(SESSION_ID)
            h.joinViewModel().join(SESSION_CODE.lowercase())
            assertEquals(SESSION_ID, h.holder.get())
        }
        // 3. the owner's screen creates a session, attaches its listener, and the listener fails
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createResponse = hashMapOf<String, Any?>("sessionId" to OWNER_SESSION_ID, "code" to OWNER_CODE, "expiresInMinutes" to 60)
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            assertEquals(OWNER_SESSION_ID, vm.state.value.sessionId)
            h.deliverSnapshotError(OWNER_SESSION_ID, FirebaseFirestoreException("the listener failed", FirebaseFirestoreException.Code.PERMISSION_DENIED))
        }
        // 4. the engine bootstraps an anonymous identity for a session scan
        RealJoinPathHarness(signedInAtStart = false).use { h ->
            val engineContext = mock<Context> { on { getString(any<Int>()) } doReturn "x" }
            val fingerprint = mock<BehaviorFingerprint>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
            val builder = mock<BehaviorFingerprintBuilder> { on { build(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn fingerprint }
            val timeSeries = mock<TimeSeriesRepository> { on { getReader() } doReturn mock<TimeSeriesFileReader>() }
            val engine = AiScoringEngine(
                context = engineContext, fingerprintBuilder = builder, firebaseService = h.service, timeSeriesRepository = timeSeries,
                scanSessionHolder = h.holder, scanSessionRecords = h.records, authManager = h.authManager,
                evaluationSubmitter = mock<EvaluationSubmitter>(), analysisProgress = AnalysisProgress()
            )
            h.holder.setFromJoin(JoinSessionResult(sessionId = ENGINE_SESSION_ID, ownerId = "owner-1", status = "scanning", evaluationEnabled = true))
            h.startScan("scan-0001")
            h.analyzeFailure = RuntimeException("stop here")
            val report = ScanReport(
                scanId = "scan-0001", scanType = ScanType.GENERAL, linkedScanId = null, deviceInfo = mock<DeviceInfo>(),
                scanMetadata = ScanMetadata(0L, 1L, 90, 80, 30f, 1L, true, null, "test"),
                functionalResults = emptyList(), timeSeriesSummary = null, antiTamperFlags = emptyList(), technicianInput = null, peerInput = null
            )
            runBlocking { engine.calculateScore(report) }
            assertEquals("the real bootstrap ran", 1, h.signInAnonymouslyCalls)
        }
        // 5. the pre-scan gate bypasses for a session
        RealJoinPathHarness(viewModelContext = context).use { h ->
            h.holder.setFromJoin(JoinSessionResult(sessionId = PRESCAN_SESSION_ID, ownerId = "owner-1", status = "scanning", evaluationEnabled = true))
            h.preScanViewModel().checkAccess()
        }

        val lines = logged()
        assertTrue("the flows printed something (${lines.size} lines)", lines.size > 10)
        for (secret in SECRETS) {
            val leaks = lines.filter { it.contains(secret) }
            assertTrue("\"$secret\" reached logcat in: $leaks", leaks.isEmpty())
        }
        // the events stay, with the hash in place of the value
        fun printed(prefix: String, value: String) = lines.any { it.contains(prefix + LogRedaction.hashId(value)) }
        assertTrue("QR: the decoded text is hashed", printed("onQrDetected: raw=", SESSION_LINK))
        assertTrue("QR: the parsed code is hashed", printed("parseSessionCode -> ", SESSION_CODE))
        assertTrue("typed: the code is hashed", printed("Joining session with code: ", SESSION_CODE))
        assertTrue("owner: the id is hashed", printed("createScanSession result: sessionId=", OWNER_SESSION_ID))
        assertTrue("owner: the code is hashed", printed(", code=", OWNER_CODE))
        assertTrue("owner: the listener path is hashed", printed("Attaching session listener: path=scanSessions/", OWNER_SESSION_ID))
        assertTrue("owner: the listener error path is hashed", printed("session listener error: path=scanSessions/", OWNER_SESSION_ID))
        assertTrue("engine: the bootstrap line is hashed", printed("bootstrapping anonymous auth for sessionId=", ENGINE_SESSION_ID))
        assertTrue("pre-scan: the bypass line is hashed", printed("bypassing auth/subscription — sessionId=", PRESCAN_SESSION_ID))
    }
}
