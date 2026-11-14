package net.neuralcheck.domain.scoring

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.flattenReportDocument
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStreamReader

/**
 * Order 2026-09-24 (القرار ٨٠/٨٢ البند ٦). [buildScoringResultFromResponse]
 * is extracted from [AiScoringEngine.calculateScore]'s success branch so
 * both that live/cache-hit path AND [net.neuralcheck.presentation.viewmodel.ReportViewModel.loadScan]'s
 * new Room-miss fallback build the identical [ScoringResult] shape from
 * the same [AiAnalysisResponse]. Before this extraction the conversion
 * had NO direct unit test — only reachable through the full
 * `AiScoringEngine.calculateScore` path, which needs a live/mocked
 * Firebase call this module cannot make. This is the first direct
 * coverage of that conversion logic, using the same real document
 * fixtures (sample B / Honor B) as [net.neuralcheck.data.remote.FirebaseAnalysisServiceCacheGateTest] —
 * structural/semantic assertions only, no identifier values (the backend
 * regenerates these fixtures' identifiers).
 */
class BuildScoringResultFromResponseTest {

    private fun loadResponse(fileName: String): AiAnalysisResponse {
        val stream = javaClass.classLoader!!.getResourceAsStream("contract/$fileName")
            ?: error("Fixture not found on classpath — expected at app/src/test/resources/contract/$fileName")
        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val raw: Map<String, Any?> = Gson().fromJson(text, type)
        val flat = flattenReportDocument(raw)
        return Gson().fromJson(Gson().toJson(flat), AiAnalysisResponse::class.java)
    }

    @Test
    fun `sample B builds a ScoringResult with the real letter and no error`() {
        val response = loadResponse("v7-reports-document-sample.json")

        val result = buildScoringResultFromResponse(
            response = response,
            antiTamperPenalty = 3f,
            isIncomplete = false
        )

        assertEquals("C", result.overallGrade)
        assertNull("a successful build must never populate error", result.error)
        assertEquals(3f, result.antiTamperPenalty)
        assertEquals(false, result.isIncomplete)
        assertEquals(AiPipeline.V7_FINDINGS_BASED, result.aiAnalysis?.pipeline)
        assertEquals("C", result.aiAnalysis?.overall?.letter)
        assertEquals(false, result.aiAnalysis?.overall?.harnessError)
    }

    @Test
    fun `Honor B builds a ScoringResult with no letter but no error either — harnessError is a declared, complete answer`() {
        val response = loadResponse("v7-reports-document-sample-honor.json")

        val result = buildScoringResultFromResponse(
            response = response,
            antiTamperPenalty = 0f,
            isIncomplete = false
        )

        // No real letter -> resolveOverallGrade's "?" sentinel, same as any
        // V7 report with no letter (suppressed from the grade UI entirely
        // for V7 — AiV7Presentation.shouldShowGradeSummary). The important
        // assertion is `error == null`: this is a SUCCESSFUL build, not a
        // failure — the order's own point that harnessError is a complete,
        // declared answer, not a partial/failed one.
        assertEquals("?", result.overallGrade)
        assertNull("harnessError is a successful, complete response — not an error", result.error)
        assertNull(result.aiAnalysis?.overall?.letter)
        assertEquals(true, result.aiAnalysis?.overall?.harnessError)
        assertEquals("FUNCTIONAL_RESULTS_UNAVAILABLE", result.aiAnalysis?.overall?.harnessErrorReason)
    }

    @Test
    fun `empty realCounts (the remote-fallback path) leaves server-reported category counts untouched`() {
        // Models ReportViewModel.loadScan's Room-miss fallback: no local
        // functional-result rows exist for a scan this device never ran,
        // so realCounts defaults to empty — every category's passCount/
        // totalCount must come through exactly as the server sent them,
        // never coerced to 0 or reconciled against a phantom local count.
        val json = """
            {
              "pipeline": "v7_findings_based",
              "overall": {"letterSource": "BAND", "letter": "B"},
              "category_scores": {
                "battery": {"score": 80, "grade": "B", "pass_count": 4, "total_count": 5, "measured": true}
              }
            }
        """.trimIndent()
        val response = Gson().fromJson(json, AiAnalysisResponse::class.java)

        val result = buildScoringResultFromResponse(
            response = response,
            antiTamperPenalty = 0f,
            isIncomplete = false
        )

        val battery = result.categoryScores.getValue("battery")
        assertEquals(4, battery.passCount)
        assertEquals(5, battery.totalCount)
    }

    @Test
    fun `networkErrorMessageOrNull returns the fixed localized string for a connectivity failure`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.login_error_network))
            .thenReturn("لا يوجد اتصال بالإنترنت")

        val t = java.io.IOException("Unable to resolve host \"neuralchek-9b6d7.cloudfunctions.net\": No address associated with hostname")
        val message = networkErrorMessageOrNull(context, t)

        assertEquals(
            "the raw exception text must never reach the card for a network failure",
            "لا يوجد اتصال بالإنترنت",
            message
        )
    }

    // Order 2026-09-26 (ملحق ثانٍ بالقرار ١٠٤ البند ٣) — REPLACES this
    // test's prior name/body: it asserted `networkErrorMessageOrNull`
    // returns null for a non-network failure "leaving the raw message to
    // the caller" — that assertion on `networkErrorMessageOrNull` ITSELF
    // is still true (unchanged: this function still returns null here),
    // but the phrase described what the CALLER used to do with that null,
    // which is no longer accurate — the caller (analysisErrorMessage,
    // below) now falls back to error_generic, not the raw message. Kept
    // as coverage of networkErrorMessageOrNull's own unchanged contract,
    // renamed so it no longer misdescribes the caller's behaviour.
    @Test
    fun `networkErrorMessageOrNull returns null for a non-network failure (its own contract, unchanged)`() {
        val context = mock<Context>()
        val t = java.io.IOException("Callable 'analyzeDevice' timed out after 300000ms")

        assertNull(
            "only the no-network case is fixed-text per this function's own contract " +
                "— what the CALLER falls back to for null is a separate concern, see " +
                "the analysisErrorMessage tests below",
            networkErrorMessageOrNull(context, t)
        )
    }

    // ---- analysisErrorMessage: ملحق ثانٍ بالقرار ١٠٤ البند ٣ ----
    // The composition AiScoringEngine.calculateScore's catch block actually
    // uses for ScoringResult.error (AiScoringEngine.kt, `error =
    // analysisErrorMessage(context, t)`) — extracted (A110 precedent) so
    // it's testable without constructing AiScoringEngine or
    // FirebaseAnalysisService.

    @Test
    fun `analysisErrorMessage returns error_generic for a non-network failure — the raw message never reaches it`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.error_generic))
            .thenReturn("حدث خطأ غير متوقع")
        // A field-observed non-network cause named explicitly by the
        // order: an analyzeDevice timeout. Its raw message is deliberately
        // distinctive ("XYZ-SECRET-LOOKING-TOKEN") so this test would
        // fail loudly if analysisErrorMessage ever regressed to leaking it.
        val t = java.io.IOException("Callable 'analyzeDevice' timed out after 300000ms (token=XYZ-SECRET-LOOKING-TOKEN)")

        val result = analysisErrorMessage(context, t, RefusalContext.PERSONAL)

        assertEquals("حدث خطأ غير متوقع", result)
        assertTrue(
            "the raw message must be completely absent from what reaches the screen",
            "XYZ-SECRET-LOOKING-TOKEN" !in result && "analyzeDevice" !in result
        )
    }

    // Order 2026-09-26 (ملحق رابع بالقرار ١٠٤ البند ١) — RENAMED, assertion
    // unchanged: this test's OLD name/comment ("Simulates the shape a
    // Firebase Callable HttpsError surfaces as client-side") was
    // inaccurate — a plain RuntimeException is NOT a
    // FirebaseFunctionsException, so this was never actually simulating
    // an HttpsError. The assertion (error_generic) still holds under the
    // new six-code gate, for a DIFFERENT reason: `t is
    // FirebaseFunctionsException` is false, so the code never even checks
    // the message text. Kept and retargeted to prove exactly that — the
    // gate is TYPE + CODE, never a match on message text (the order's own
    // explicit constraint: "ولا يُطابَق على النصّ").
    @Test
    fun `analysisErrorMessage returns error_generic for a non-Functions exception, even with an analyzeDevice-looking message`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.error_generic))
            .thenReturn("حدث خطأ غير متوقع")
        // Deliberately NOT a FirebaseFunctionsException, and its message
        // text even LOOKS like one of the six codes — proves the gate
        // never matches on text, only on real type + real code.
        val t = RuntimeException("already-exists")

        assertEquals("حدث خطأ غير متوقع", analysisErrorMessage(context, t, RefusalContext.PERSONAL))
    }

    // ---- ملحق رابع بالقرار ١٠٤ البند ١: the six-code allow-list ----
    // analyzeDevice.js (neuralcheck-backend, a separate repository from
    // this one, rule 2 — relayed, not independently verified here) throws
    // TEN HttpsError calls across SIX distinct codes: PERMISSION_DENIED,
    // INVALID_ARGUMENT, FAILED_PRECONDITION, UNAUTHENTICATED,
    // ALREADY_EXISTS, UNAVAILABLE.
    // The three required tests below use one representative code
    // (PERMISSION_DENIED) for the "shows its message" case — the gate is
    // a `Set<Code>` membership check, not per-code branching, so one
    // representative proves the mechanism for all six equally; INTERNAL
    // and a non-Functions exception are the two required negative cases.

    @Test
    fun `analysisErrorMessage shows the exact message for a FirebaseFunctionsException with one of analyzeDevice's six intentional codes`() {
        val context = mock<Context>()
        val t = mock<com.google.firebase.functions.FirebaseFunctionsException>()
        whenever(t.code).thenReturn(com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED)
        whenever(t.message).thenReturn("لا يوجد رصيد كافٍ لإجراء هذا الفحص")
        // Mockito's default for an unstubbed Throwable-returning method is
        // null — this test relies on that default for `t.cause` (not
        // stubbed here) to represent the ordinary real-server-response
        // case, which the conservative gate below must still let through.

        assertEquals(
            "one of analyzeDevice's own deliberate, technician-facing Arabic " +
                "strings must reach the screen verbatim for these six codes",
            "لا يوجد رصيد كافٍ لإجراء هذا الفحص",
            analysisErrorMessage(context, t, RefusalContext.PERSONAL)
        )
    }

    // The conservative cause-based gate (AiScoringEngine.kt's
    // analysisErrorMessage doc): a matching code whose `cause` IS an
    // IOException must NOT be trusted as a genuine analyzeDevice
    // response, even though the code matches one of the six.
    @Test
    fun `analysisErrorMessage returns error_generic when a matching code's cause is an IOException — conservative gate`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.error_generic))
            .thenReturn("حدث خطأ غير متوقع")
        val t = mock<com.google.firebase.functions.FirebaseFunctionsException>()
        whenever(t.code).thenReturn(com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE)
        whenever(t.message).thenReturn("a message that must NOT reach the screen for this case")
        whenever(t.cause).thenReturn(java.io.IOException("local transport detail"))

        assertEquals(
            "an IOException-typed cause means this exception does not " +
                "reliably indicate a genuine analyzeDevice server response",
            "حدث خطأ غير متوقع",
            analysisErrorMessage(context, t, RefusalContext.PERSONAL)
        )
    }

    @Test
    fun `analysisErrorMessage returns error_generic for FirebaseFunctionsException INTERNAL — explicitly excluded by the order`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.error_generic))
            .thenReturn("حدث خطأ غير متوقع")
        val t = mock<com.google.firebase.functions.FirebaseFunctionsException>()
        whenever(t.code).thenReturn(com.google.firebase.functions.FirebaseFunctionsException.Code.INTERNAL)
        whenever(t.message).thenReturn("some internal SDK detail that must not reach the screen")

        assertEquals("حدث خطأ غير متوقع", analysisErrorMessage(context, t, RefusalContext.PERSONAL))
    }

    @Test
    fun `analysisErrorMessage returns error_generic for FirebaseFunctionsException UNKNOWN — explicitly excluded by the order`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.error_generic))
            .thenReturn("حدث خطأ غير متوقع")
        val t = mock<com.google.firebase.functions.FirebaseFunctionsException>()
        whenever(t.code).thenReturn(com.google.firebase.functions.FirebaseFunctionsException.Code.UNKNOWN)
        whenever(t.message).thenReturn("some unknown-code detail that must not reach the screen")

        assertEquals("حدث خطأ غير متوقع", analysisErrorMessage(context, t, RefusalContext.PERSONAL))
    }

    @Test
    fun `analysisErrorMessage still returns the network-fixed string for a real connectivity failure`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.login_error_network))
            .thenReturn("لا يوجد اتصال بالإنترنت")
        val t = java.io.IOException("Unable to resolve host \"neuralchek-9b6d7.cloudfunctions.net\": No address associated with hostname")

        assertEquals(
            "the network path (networkErrorMessageOrNull) must be completely unchanged by this order",
            "لا يوجد اتصال بالإنترنت",
            analysisErrorMessage(context, t, RefusalContext.PERSONAL)
        )
    }

    @Test
    fun `networkErrorMessageOrNull matches case-insensitively, same as LoginViewModel's idiom`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.login_error_network)).thenReturn("لا يوجد اتصال بالإنترنت")

        val t = RuntimeException("NETWORK error occurred")
        assertTrue(networkErrorMessageOrNull(context, t) != null)
    }
}
