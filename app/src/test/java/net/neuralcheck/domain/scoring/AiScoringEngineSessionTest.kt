package net.neuralcheck.domain.scoring

import android.content.Context
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.auth.AuthManager
import net.neuralcheck.data.local.files.TimeSeriesFileReader
import net.neuralcheck.data.remote.BehaviorFingerprintBuilder
import net.neuralcheck.data.remote.FirebaseAnalysisService
import net.neuralcheck.data.remote.JoinSessionResult
import net.neuralcheck.data.remote.ScanSessionHolder
import net.neuralcheck.data.remote.ScanSessionRecords
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.BehaviorFingerprint
import net.neuralcheck.data.repository.TimeSeriesRepository
import net.neuralcheck.domain.evaluation.EvaluationSubmitter
import net.neuralcheck.domain.model.DeviceInfo
import net.neuralcheck.domain.model.ScanMetadata
import net.neuralcheck.domain.model.ScanReport
import net.neuralcheck.domain.model.ScanType
import net.neuralcheck.util.EvalCardLog
import net.neuralcheck.util.InMemorySharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * Order 2026-10-14 (user decision, device check 03 round 2, «Record the session at scan start»): "The scan keeps the
 * session it started in; its analysis and its evaluation draft use that one, never the session joined later." This
 * file is the ANALYSIS half — the REAL [AiScoringEngine.calculateScore], run end to end on the JVM — and, with it, the
 * user's item 6: a retry after a rejection in a session keeps the session. (The draft half is
 * `ScanSessionAtScanStartRealPathTest`.)
 *
 * ── القاعدة ٣٧ — what is real, what is replaced ──
 * REAL: the engine (its real constructor and `calculateScore`), [ScanSessionHolder], [ScanSessionRecords] (over an
 * in-memory preferences file), `buildScoringResultFromResponse`, the error classification, and the engine's own
 * decisions after the answer (what to do with the holder, what to do with the record). REPLACED, all at the
 * boundary the engine does not decide anything on: [FirebaseAnalysisService] (the network: `analyzeDevice` records the
 * session id it is handed — that argument IS what this test reads — and answers with a response or throws what the
 * server throws), the fingerprint builder (a deep-stub fingerprint: the payload's content is not under test), the
 * time-series reader (no file), [AuthManager] (signed in, or not), [EvaluationSubmitter] (fire-and-forget after a
 * success), and the Android [Context] (strings). `DeviceFingerprint.generateProfileKey` is the real one: on the JVM
 * it fails layer by layer and the engine's own `runCatching` takes its documented fallback key.
 *
 * What this does NOT show: that the server accepts or rejects what it is sent (relayed from neuralcheck-backend
 * `a666036`, `analyzeDevice.js:371`: the server judges the session id it is handed — NOT read from this repository),
 * nor a release build's behaviour.
 */
class AiScoringEngineSessionTest {

    private lateinit var holder: ScanSessionHolder
    private lateinit var records: ScanSessionRecords
    private lateinit var service: FirebaseAnalysisService
    private lateinit var auth: AuthManager
    private lateinit var engine: AiScoringEngine

    /** The `sessionId` argument of every `analyzeDevice` call, in order. null = a personal call. */
    private val sessionsSent = mutableListOf<String?>()

    /** What the next `analyzeDevice` does; consumed in order, the last one repeats. */
    private val answers = ArrayDeque<() -> AiAnalysisResponse>()

    private val lines = mutableListOf<String>()

    @Before
    fun setUp() {
        EvalCardLog.sink = { lines.add(it) }
        holder = ScanSessionHolder()
        records = ScanSessionRecords(InMemorySharedPreferences(), holder)

        val context = mock<Context> { on { getString(any<Int>()) } doReturn "error" }

        val fingerprint = mock<BehaviorFingerprint>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val fingerprintBuilder = mock<BehaviorFingerprintBuilder> {
            on { build(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn fingerprint
        }

        val reader = mock<TimeSeriesFileReader>()
        val timeSeries = mock<TimeSeriesRepository> { on { getReader() } doReturn reader }

        service = mock {
            onBlocking { fetchExistingReport(any()) } doReturn null
            onBlocking { analyzeDevice(any(), any(), any(), anyOrNull()) } doAnswer { inv ->
                sessionsSent.add(inv.getArgument<String?>(3))
                val next = if (answers.size > 1) answers.removeFirst() else answers.first()
                next()
            }
        }

        auth = mock { on { isLoggedIn } doReturn true }

        engine = AiScoringEngine(
            context = context,
            fingerprintBuilder = fingerprintBuilder,
            firebaseService = service,
            timeSeriesRepository = timeSeries,
            scanSessionHolder = holder,
            scanSessionRecords = records,
            authManager = auth,
            evaluationSubmitter = mock<EvaluationSubmitter>(),
            analysisProgress = AnalysisProgress()
        )
    }

    @After
    fun tearDown() {
        EvalCardLog.resetSink()
    }

    // ---- what the server answers ----

    private fun succeeds() = { AiAnalysisResponse(grade = "B", score = 70, summary = "ok") }

    private fun rejects(code: FirebaseFunctionsException.Code): () -> AiAnalysisResponse = {
        throw mock<FirebaseFunctionsException> { on { this.code } doReturn code }
    }

    // ---- the phone ----

    private fun join(sessionId: String, ownerId: String) = holder.setFromJoin(
        JoinSessionResult(sessionId = sessionId, ownerId = ownerId, status = "scanning", evaluationEnabled = true)
    )

    private fun report(scanId: String) = ScanReport(
        scanId = scanId,
        scanType = ScanType.GENERAL,
        linkedScanId = null,
        deviceInfo = mock<DeviceInfo>(),
        scanMetadata = ScanMetadata(
            startTimestamp = 0L, endTimestamp = 1L, startBatteryLevel = 90, endBatteryLevel = 80,
            startBatteryTemp = 30f, uptimeMs = 1L, isComplete = true, earlyStopReason = null, appVersion = "test"
        ),
        functionalResults = emptyList(),
        timeSeriesSummary = null,
        antiTamperFlags = emptyList(),
        technicianInput = null,
        peerInput = null
    )

    private fun analyse(scanId: String) = runBlocking { engine.calculateScore(report(scanId)) }

    // ======================= A, then B joined later =======================

    @Test
    fun `a scan that started under A is analysed with A after B is joined - and the later session is left alone`() {
        join("sess-1", "owner-1")
        records.recordAtScanStart("scan-0001") // the scan starts under A
        join("sess-2", "owner-2")              // B joined later
        answers.add(succeeds())

        val result = analyse("scan-0001")

        assertEquals("the request carried the session the scan started in", listOf<String?>("sess-1"), sessionsSent)
        assertNotNull("the analysis succeeded", result.aiAnalysis)
        assertEquals("B, joined later, is NOT emptied by A's analysis", "sess-2", holder.get())
        assertNull("a success spends the scan's record", records.sessionFor("scan-0001", ScanSessionRecords.Use.ANALYSIS))
    }

    @Test
    fun `without a later join the analysis is sent with the session, and a success empties the holder and spends the record - one-shot, as before`() {
        join("sess-1", "owner-1")
        records.recordAtScanStart("scan-0001")
        answers.add(succeeds())

        analyse("scan-0001")

        assertEquals(listOf<String?>("sess-1"), sessionsSent)
        assertNull("the holder is emptied: a follow-up scan by a signed-in user does not inherit it", holder.getInfo())
        assertNull(records.sessionFor("scan-0001", ScanSessionRecords.Use.ANALYSIS))
    }

    // ======================= a personal scan stays personal =======================

    @Test
    fun `a scan that started in no session is analysed as a personal scan after a session is joined - the holder's session is never sent`() {
        records.recordAtScanStart("scan-0001") // started with an empty holder
        join("sess-2", "owner-2")              // joined afterwards
        answers.add(succeeds())

        analyse("scan-0001")

        assertEquals("no session id on the request", listOf<String?>(null), sessionsSent)
        assertEquals("the later session is untouched", "sess-2", holder.get())
        assertTrue(
            "the log says the holder's session was ignored",
            lines.any { it.startsWith("scan-session-holder-ignored use=analysis") }
        )
    }

    // ======================= a retry after a rejection keeps the session =======================

    @Test
    fun `a retry after a rejection in a session sends the same session - the holder was emptied by the failure, the record was not`() {
        join("sess-1", "owner-1")
        records.recordAtScanStart("scan-0001")
        answers.add(rejects(FirebaseFunctionsException.Code.PERMISSION_DENIED))
        answers.add(succeeds())

        val first = analyse("scan-0001")

        assertNull("the rejection carries no analysis", first.aiAnalysis)
        assertNotNull("and an error to show", first.error)
        assertNull("the holder was emptied by the failure - as before (the known limit)", holder.getInfo())

        lines.clear()
        val retry = analyse("scan-0001") // the user presses retry

        assertEquals("both calls carried the session", listOf<String?>("sess-1", "sess-1"), sessionsSent)
        assertNotNull("the retry succeeded", retry.aiAnalysis)
        assertEquals(
            "the line says the recorded session was used although the holder is empty",
            true,
            lines.any { it.startsWith("scan-session-differs use=analysis") && it.endsWith("holder=missing") }
        )
        assertNull("the success spends the record", records.sessionFor("scan-0001", ScanSessionRecords.Use.ANALYSIS))
    }

    @Test
    fun `every failure keeps the record - the retry has its session whatever the server's code was (rule 30)`() {
        for (code in FirebaseFunctionsException.Code.values()) {
            val scan = "scan-" + code.name
            join("sess-$code", "owner-1")
            records.recordAtScanStart(scan)
            sessionsSent.clear()
            answers.clear()
            answers.add(rejects(code))

            analyse(scan)
            analyse(scan)

            assertEquals("$code: the failure and its retry both carried the session", listOf<String?>("sess-$code", "sess-$code"), sessionsSent)
        }
    }

    @Test
    fun `while the server is still processing both the holder and the record are kept`() {
        join("sess-1", "owner-1")
        records.recordAtScanStart("scan-0001")
        answers.add(rejects(FirebaseFunctionsException.Code.ALREADY_EXISTS))
        answers.add(succeeds())

        val waiting = analyse("scan-0001")

        assertTrue("a state of its own, not an error card", waiting.analysisInProgress)
        assertEquals("the holder is kept (fieldsVersion 69, S-26)", "sess-1", holder.get())
        assertEquals(ScanSessionRecords.Recorded("sess-1", true), records.sessionFor("scan-0001", ScanSessionRecords.Use.ANALYSIS))

        analyse("scan-0001")
        assertEquals(listOf<String?>("sess-1", "sess-1"), sessionsSent)
    }

    // ======================= two scans =======================

    @Test
    fun `two scans in two sessions are each analysed with their own session, whichever order they finish in`() {
        join("sess-1", "owner-1")
        records.recordAtScanStart("scan-0001")
        join("sess-2", "owner-2")
        records.recordAtScanStart("scan-0002")
        answers.add(succeeds())

        analyse("scan-0001") // the older scan finishes second-to-start, first-to-finish
        analyse("scan-0002")

        assertEquals(listOf<String?>("sess-1", "sess-2"), sessionsSent)
    }

    // ======================= the anonymous bootstrap follows the scan's session =======================

    @Test
    fun `a signed-out device with a scan session signs in anonymously before the call - a personal scan does not`() {
        whenever(auth.isLoggedIn).thenReturn(false)
        join("sess-1", "owner-1")
        records.recordAtScanStart("scan-0001")
        answers.add(succeeds())

        analyse("scan-0001")
        verifyBlocking(auth, times(1)) { signInAnonymously() }

        holder.clear(ScanSessionHolder.ClearReason.SIGN_OUT)
        records.recordAtScanStart("scan-0003") // personal
        join("sess-2", "owner-2")              // a session joined afterwards must not trigger the bootstrap for it
        analyse("scan-0003")

        verifyBlocking(auth, times(1)) { signInAnonymously() }
        assertFalse("the personal scan was not sent under a session", sessionsSent.last() != null)
    }

    @Test
    fun `control - the holder and the scan's record disagree after a later join, and this test sees both`() {
        join("sess-1", "owner-1")
        records.recordAtScanStart("scan-0001")
        join("sess-2", "owner-2")

        // what `scanSessionHolder.get()` answered at analysis time before the change: B, for a scan that started under A
        assertEquals("sess-2", holder.get())
        // what the scan's record answers
        assertEquals("sess-1", records.sessionFor("scan-0001", ScanSessionRecords.Use.ANALYSIS)?.sessionId)
        // and nothing was sent: this test only looked
        assertEquals(emptyList<String?>(), sessionsSent)
    }
}
