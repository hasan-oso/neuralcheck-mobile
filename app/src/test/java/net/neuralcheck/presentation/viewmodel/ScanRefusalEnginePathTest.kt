package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.neuralcheck.R
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
import net.neuralcheck.domain.scoring.ScoringResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An analysis refused WITH a `reasonCode`, through the REAL engine and the REAL service, to the text on the report's error card - A-SUB wave 1 (order 2026-10-17; rules 30, 37, 41).
 *
 * What is REAL: the SDK's own parse of the wire body ([SessionWireFixtures.exceptionFromWire]) over the five probe-wire bodies ([ScanRefusalWire.ANALYZE_BODIES]: from probe-wire, NOT from
 * production - see that object), [net.neuralcheck.data.remote.FirebaseAnalysisService] (`analyzeDevice`, `callWithTimeout`, the `await`), [AiScoringEngine.calculateScore] and its catch, `analysisErrorMessage`,
 * the matrix, the app's resources (Arabic = the default set, Turkish = `values-tr`). What is REPLACED: only the HTTP round trip and the Firebase auth object (the harness's static mocks), the
 * fingerprint builder (the payload is not under test), the time-series reader (no file) and the evaluation submitter (a success's fire-and-forget).
 *
 * What it compares with: the literal the user approved ([ApprovedSessionTexts]) - never the resource under test - so a swapped key, a text read from the server's message (which is the same
 * sentence under two codes), or a missing Turkish text fails. Every code runs in BOTH contexts (a code outside its cell shows the text of before - the server's message - and prints
 * `combination=unexpected`).
 */
abstract class ScanRefusalEnginePathBase {

    internal abstract fun approved(text: ApprovedSessionTexts.Text): String

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The text each (context, code) cell shows - written out here from the order's matrix, not read from the code under test. */
    private fun expectedText(session: Boolean, code: String): String? = when {
        !session && code == "NO_CREDIT" -> approved(ApprovedSessionTexts.NO_CREDIT_EXISTING)
        !session && code == "SUBSCRIPTION_EXPIRED" -> approved(ApprovedSessionTexts.SUBSCRIPTION_EXPIRED)
        code == "FREE_DEVICE_USED" -> approved(ApprovedSessionTexts.FREE_DEVICE_USED)
        session && code == "SHOP_NO_CREDIT" -> approved(ApprovedSessionTexts.SHOP_NO_CREDIT)
        session && code == "SHOP_SUBSCRIPTION_EXPIRED" -> approved(ApprovedSessionTexts.SHOP_SUBSCRIPTION_EXPIRED)
        else -> null
    }

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

    /** Runs the real engine on a refused analysis. [session] = the scan started in a session (the customer's phone); otherwise it is the account's own scan. */
    private fun refused(body: String, session: Boolean): Pair<ScoringResult, List<String>> =
        RealJoinPathHarness(signedInAtStart = !session).use { h ->
            val texts = context
            // the engine reads strings through this context; everything else it asks the context for is answered by Mockito's defaults, as in AiScoringEngineSessionTest
            val engineContext = mock<Context> { on { getString(any<Int>()) } .thenAnswer { inv -> texts.getString(inv.getArgument<Int>(0)) } }
            val fingerprint = mock<BehaviorFingerprint>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
            val fingerprintBuilder = mock<BehaviorFingerprintBuilder> {
                on { build(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()) } doReturn fingerprint
            }
            val reader = mock<TimeSeriesFileReader>()
            val timeSeries = mock<TimeSeriesRepository> { on { getReader() } doReturn reader }
            val engine = AiScoringEngine(
                context = engineContext,
                fingerprintBuilder = fingerprintBuilder,
                firebaseService = h.service,
                timeSeriesRepository = timeSeries,
                scanSessionHolder = h.holder,
                scanSessionRecords = h.records,
                authManager = h.authManager,
                evaluationSubmitter = mock<EvaluationSubmitter>(),
                analysisProgress = AnalysisProgress()
            )
            if (session) {
                h.holder.setFromJoin(JoinSessionResult(sessionId = "sess-1", ownerId = "owner-1", status = "scanning", evaluationEnabled = true))
            }
            h.startScan("scan-0001") // records the holder's session (or none) under the scan, as StressTestService does
            h.analyzeFailure = SessionWireFixtures.exceptionFromWire(body)
            val result = runBlocking { engine.calculateScore(report("scan-0001")) }
            assertEquals("the real service was called once", 1, h.callableCalls("analyzeDevice"))
            result to h.logLines.toList()
        }

    @Test
    fun `a personal analysis refused with each of the five codes shows the text of its cell - and the text of before outside it`() {
        for ((code, body) in ScanRefusalWire.ANALYZE_BODIES) {
            val (result, lines) = refused(body, session = false)
            assertNull("$code: a refusal carries no analysis", result.aiAnalysis)
            assertFalse("$code: it is a refusal, not 'still processing'", result.analysisInProgress)
            val expected = expectedText(session = false, code = code)
            val line = lines.single { it.startsWith("scan-refused ") }
            assertTrue("$code: $line", line.startsWith("scan-refused context=personal reasonCode=$code raw=- "))
            if (expected != null) {
                assertEquals("personal x $code", expected, result.error)
                assertTrue("$code: $line", line.contains(" combination=expected "))
            } else {
                // outside its cell: the text that point showed before = the server's own message, verbatim; the line says so
                assertEquals("personal x $code (outside its cell: the server's message, as before)", serverMessage(body), result.error)
                assertTrue("$code: $line", line.contains(" combination=unexpected ") && line.endsWith(" shown=server_message"))
            }
        }
    }

    @Test
    fun `a session analysis refused with each of the five codes shows the text of its cell - and the text of before outside it`() {
        for ((code, body) in ScanRefusalWire.ANALYZE_BODIES) {
            val (result, lines) = refused(body, session = true)
            assertNull("$code: a refusal carries no analysis", result.aiAnalysis)
            val expected = expectedText(session = true, code = code)
            val line = lines.single { it.startsWith("scan-refused ") }
            assertTrue("$code: $line", line.startsWith("scan-refused context=session reasonCode=$code raw=- "))
            if (expected != null) {
                assertEquals("session x $code", expected, result.error)
                assertTrue("$code: $line", line.contains(" combination=expected "))
            } else {
                assertEquals("session x $code (outside its cell: the server's message, as before)", serverMessage(body), result.error)
                assertTrue("$code: $line", line.contains(" combination=unexpected ") && line.endsWith(" shown=server_message"))
            }
        }
    }

    @Test
    fun `the same message under two codes shows two texts, and the same code in two contexts shows what its cell says - the message is not matched (rule 21)`() {
        // probe-wire: NO_CREDIT and SHOP_NO_CREDIT carry the SAME message, SUBSCRIPTION_EXPIRED and SHOP_SUBSCRIPTION_EXPIRED the same one too
        assertEquals(serverMessage(ScanRefusalWire.ANALYZE_BODIES.getValue("NO_CREDIT")), serverMessage(ScanRefusalWire.ANALYZE_BODIES.getValue("SHOP_NO_CREDIT")))
        assertEquals(serverMessage(ScanRefusalWire.ANALYZE_BODIES.getValue("SUBSCRIPTION_EXPIRED")), serverMessage(ScanRefusalWire.ANALYZE_BODIES.getValue("SHOP_SUBSCRIPTION_EXPIRED")))
        val personalNoCredit = refused(ScanRefusalWire.ANALYZE_BODIES.getValue("NO_CREDIT"), session = false).first.error
        val sessionNoCredit = refused(ScanRefusalWire.ANALYZE_BODIES.getValue("SHOP_NO_CREDIT"), session = true).first.error
        assertTrue("the personal and the shop refusals read differently though the server sent one sentence", personalNoCredit != sessionNoCredit)
        val personalExpired = refused(ScanRefusalWire.ANALYZE_BODIES.getValue("SUBSCRIPTION_EXPIRED"), session = false).first.error
        val sessionExpired = refused(ScanRefusalWire.ANALYZE_BODIES.getValue("SHOP_SUBSCRIPTION_EXPIRED"), session = true).first.error
        assertTrue(personalExpired != sessionExpired)
    }

    @Test
    fun `the message is not read - a code with a different server message shows the same approved text`() {
        val reworded = SessionWireFixtures.wireBody("PERMISSION_DENIED", "a message this app has never seen", "SHOP_SUBSCRIPTION_EXPIRED")
        assertEquals(approved(ApprovedSessionTexts.SHOP_SUBSCRIPTION_EXPIRED), refused(reworded, session = true).first.error)
        val otherStatus = SessionWireFixtures.wireBody("FAILED_PRECONDITION", "x", "FREE_DEVICE_USED")
        assertEquals("the Functions code is not read either", approved(ApprovedSessionTexts.FREE_DEVICE_USED), refused(otherStatus, session = false).first.error)
    }

    @Test
    fun `a code this app does not know, and the session states, keep the text of before and print their own combination`() {
        val unknown = SessionWireFixtures.wireBody("PERMISSION_DENIED", "رسالة الخادم", "BRAND_NEW_REFUSAL")
        for (session in listOf(false, true)) {
            val (result, lines) = refused(unknown, session)
            assertEquals("رسالة الخادم", result.error)
            val line = lines.single { it.startsWith("scan-refused ") }
            assertTrue(line, line.contains(" reasonCode=unrecognized raw=BRAND_NEW_REFUSAL combination=unexpected "))
        }
        // the session states the analysis now carries (order 2026-10-17): no approved text this wave, the message of before, and the line says so
        for (code in listOf("SESSION_NOT_PARTICIPANT", "SESSION_NOT_STARTED", "SESSION_COMPLETED", "SESSION_CANCELLED", "SESSION_EXPIRED", "SESSION_EXPIRES_AT_INVALID", "SESSION_STATUS_UNKNOWN")) {
            val (result, lines) = refused(SessionWireFixtures.wireBody("PERMISSION_DENIED", "جلسة الفحص غير صالحة", code), session = true)
            assertEquals("$code: the server's own text, as before", "جلسة الفحص غير صالحة", result.error)
            val line = lines.single { it.startsWith("scan-refused ") }
            assertTrue("$code: $line", line.contains(" reasonCode=$code raw=- combination=no-approved-text "))
        }
    }

    @Test
    fun `a refusal with no reasonCode prints no scan-refused line and shows what it showed before`() {
        val body = SessionWireFixtures.wireBody("PERMISSION_DENIED", "لا يوجد رصيد كافٍ", null)
        val (result, lines) = refused(body, session = false)
        assertEquals("لا يوجد رصيد كافٍ", result.error)
        assertTrue(lines.none { it.startsWith("scan-refused ") })
        assertNotNull(result.error)
    }

    /** The message the body carries (what the text of before was: the server's own, verbatim). */
    private fun serverMessage(body: String): String =
        com.google.gson.JsonParser.parseString(body).asJsonObject.getAsJsonObject("error").get("message").asString

    /** Rule 23: REPLACES `the resources of the five texts are the keys the matrix prints`. */
    @Test
    fun `the resources of the six texts are the keys the matrix prints (the key is the resource name)`() {
        val names = mapOf(
            R.string.subscription_no_scans to "subscription_no_scans",
            R.string.subscription_expired to "subscription_expired",
            R.string.free_device_used to "free_device_used",
            R.string.shop_no_credit to "shop_no_credit",
            R.string.shop_subscription_expired to "shop_subscription_expired",
            R.string.scan_report_not_available to "scan_report_not_available"
        )
        for ((id, key) in names) assertEquals(key, context.resources.getResourceEntryName(id))
    }

    // ---- 2026-10-20 (order app-engineer-5; the user's decision of that date: the Arabic text as it is, the Turkish one approved): the neutral stored-report refusal ----
    // The body is BUILT from the order's text (ScanRefusalWire.STORED_REPORT_REFUSAL: not probe-wire, not production). Before this change the code read as `unrecognized` and the engine showed the server's own message
    // verbatim (the Arabic sentence, to a Turkish user too): `combination=unexpected shown=server_message` - the behaviour these tests REPLACE by name (rule 23): no earlier test pinned it for this code.

    @Test
    fun `the stored-report refusal on the account's own scan shows the approved neutral text of the language - never the server's message`() {
        // a message invented here on purpose, one that WOULD tell the user a report exists and whose it is: if the app ever shows the wire's words, this fails
        val revealing = "التقرير موجود لكنه يخص مستخدماً آخر"
        val (result, lines) = refused(SessionWireFixtures.wireBody("PERMISSION_DENIED", revealing, "SCAN_REPORT_NOT_AVAILABLE"), session = false)
        assertNull("a refusal carries no analysis", result.aiAnalysis)
        assertFalse("it is a refusal, not 'still processing'", result.analysisInProgress)
        assertEquals(approved(ApprovedSessionTexts.SCAN_REPORT_NOT_AVAILABLE), result.error)
        val line = lines.single { it.startsWith("scan-refused ") }
        assertTrue(line, line.startsWith("scan-refused context=personal reasonCode=SCAN_REPORT_NOT_AVAILABLE raw=- combination=expected ") && line.endsWith(" shown=scan_report_not_available"))
        assertTrue("no log line carries the server's words", lines.none { it.contains(revealing) })
    }

    @Test
    fun `the stored-report refusal is told by its code alone - the real-shaped body, any message, any Functions code, show the same text`() {
        val expected = approved(ApprovedSessionTexts.SCAN_REPORT_NOT_AVAILABLE)
        assertEquals("the body built from the order", expected, refused(ScanRefusalWire.STORED_REPORT_REFUSAL.body, session = false).first.error)
        for ((status, message) in listOf("PERMISSION_DENIED" to "no such report", "PERMISSION_DENIED" to "this report belongs to someone else", "FAILED_PRECONDITION" to "x", "NOT_FOUND" to "y")) {
            assertEquals("$status / $message", expected, refused(SessionWireFixtures.wireBody(status, message, "SCAN_REPORT_NOT_AVAILABLE"), session = false).first.error)
        }
    }

    @Test
    fun `the neutral text names neither a report nor its owner, in either language`() {
        val shown = refused(ScanRefusalWire.STORED_REPORT_REFUSAL.body, session = false).first.error!!
        val trLower = java.util.Locale("tr")
        val forbidden = listOf("تقرير", "مالك", "صاحب", "حساب", "غير موجود", "rapor", "sahib", "hesap", "bulunamad", "mevcut değil", "yok")
        for (word in forbidden) {
            assertFalse("'$word' is in the text shown: $shown", shown.lowercase(trLower).contains(word))
        }
    }

    @Test
    fun `the same code on a session scan has no cell - the server's text as before, and the line says unexpected`() {
        val (result, lines) = refused(ScanRefusalWire.STORED_REPORT_REFUSAL.body, session = true)
        assertEquals("a code outside its cell: the text of before", ScanRefusalWire.STORED_REPORT_MESSAGE, result.error)
        val line = lines.single { it.startsWith("scan-refused ") }
        assertTrue(line, line.startsWith("scan-refused context=session reasonCode=SCAN_REPORT_NOT_AVAILABLE raw=- combination=unexpected ") && line.endsWith(" shown=server_message"))
    }

    /** The order: the session path's own code exists and its text is not changed - it stays an unnamed code with the server's text. */
    @Test
    fun `the session path's own code, SESSION_REPORT_NOT_AVAILABLE, is left as it was - the server's text and an unrecognized code`() {
        val (result, lines) = refused(SessionWireFixtures.wireBody("PERMISSION_DENIED", SessionWireFixtures.SESSION_NOT_VALID_MESSAGE, "SESSION_REPORT_NOT_AVAILABLE"), session = true)
        assertEquals(SessionWireFixtures.SESSION_NOT_VALID_MESSAGE, result.error)
        val line = lines.single { it.startsWith("scan-refused ") }
        assertTrue(line, line.contains(" reasonCode=unrecognized raw=SESSION_REPORT_NOT_AVAILABLE combination=unexpected ") && line.endsWith(" shown=server_message"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanRefusalEnginePathArabicTest : ScanRefusalEnginePathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.ar
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr")
class ScanRefusalEnginePathTurkishTest : ScanRefusalEnginePathBase() {
    override fun approved(text: ApprovedSessionTexts.Text): String = text.tr
}
