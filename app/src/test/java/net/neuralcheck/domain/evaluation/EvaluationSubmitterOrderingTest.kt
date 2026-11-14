package net.neuralcheck.domain.evaluation

import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.test.runTest
import net.neuralcheck.data.local.db.dao.SessionEvaluationDao
import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity
import net.neuralcheck.data.remote.FirebaseAnalysisService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Order 2026-09-30 («أمر — التقييم (ب)، ومهلة الجلسة، والبنود المعلّقة» ثم
 * «ملحق — النصّ، ورسالة التقييم، والبروتوكول»): "اختبار الترتيب في التطبيق"
 * — the SHARED submission/retry logic. ActiveScanViewModel actually calls
 * [EvaluationSubmitter.submitOnScanComplete] (see that method's own doc for
 * why — it wraps [submitNow] in the submitter's OWN persistent scope so the
 * fire-and-forget submission survives ActiveScanViewModel being cleared
 * right after navigation); this test class exercises [submitNow] directly,
 * the lower-level primitive `submitOnScanComplete` is built from — its OWN
 * doc/tests live in EvaluationSubmitOnScanCompleteTest.
 *
 * ── ما يفحصه هذا الاختبار بدقّة (القاعدة ٣٧) ──
 * [EvaluationSubmitter.submitNow] and [EvaluationSubmitter.submitAsync]
 * (the analysis-time fallback, AiScoringEngine.kt, unchanged by this order)
 * both route through the SAME private submitOne(). This test calls
 * submitNow() twice for the same scanId and proves the second call does NOT
 * re-invoke the submitEvaluation callable — because submitOne's
 * `row.submitted` guard already makes it a no-op. That is the exact
 * mechanism that keeps the analysis-time fallback call from ever
 * double-sending once the scan-completion trigger has already succeeded —
 * submitAsync differs from submitNow only in "fire-and-forget vs propagates
 * the exception", not in which guard it hits, so proving the guard once via
 * the awaitable entry point covers both call sites without racing
 * submitAsync's own background CoroutineScope(Dispatchers.IO) against this
 * test's virtual dispatcher (that race would make an assertion on
 * submitAsync's own timing flaky, not more thorough).
 *
 * Later tests in this class cover the final-vs-transient failure split
 * (order 2026-09-30 البند ١) and [EvaluationSubmitter.discard] (the
 * cancel-drop path) — [isFinalEvaluationRejection] itself (the code+text
 * classifier) has its OWN dedicated test class, IsFinalEvaluationRejectionTest;
 * here it is exercised only through submitOne's real usage of it.
 *
 * This does NOT exercise Room or the real Firebase Functions callable — this test does not
 * use Robolectric (a test dependency since 3284cca) — [SessionEvaluationDao] and
 * [FirebaseAnalysisService] are Mockito mocks. It also does NOT exercise
 * submitEvaluation.js's own session-status/timeout checks — that half of
 * "the ordering fix" is the backend's contract, verified in
 * neuralcheck-backend, not this repo (القاعدة ٢) — this order explicitly
 * forbids changing that function, and this test does not assume anything
 * about it beyond what the coordinator relayed.
 */
class EvaluationSubmitterOrderingTest {

    // Real instance (not a mock) — EvaluationSubmissionStatusHolder has no
    // dependencies of its own, and these tests don't assert on it (that's
    // EvaluationSubmissionStatusHolderTest's job) — it only needs to exist
    // so EvaluationSubmitter's constructor is satisfied. JUnit4 gives each
    // @Test method a fresh instance of this class, so a fresh holder too.
    private val statusHolder = EvaluationSubmissionStatusHolder()

    private fun rowFor(scanId: String, submitted: Boolean) = SessionEvaluationEntity(
        scanId = scanId,
        sessionId = "session-1",
        technicianGradeRaw = "good",
        categoryRatingsJson = "{}",
        visibleDefectsJson = "[]",
        shownAtMs = 1_000L,
        lastAnswerAtMs = 2_000L,
        submitted = submitted
    )

    @Test
    fun `submitNow is idempotent - a second call for an already-submitted row does not resend`() = runTest {
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        // Consecutive stubbing: unsubmitted on the first read, already
        // submitted on every read after — avoids a thenAnswer{} side
        // effect (Unit-returning suspend stub) that doesn't compile
        // cleanly, and models exactly what markSubmitted's real UPDATE
        // does between the two submitNow() calls below.
        whenever(dao.getByScanId("scan-1")).thenReturn(
            rowFor("scan-1", submitted = false),
            rowFor("scan-1", submitted = true)
        )
        whenever(dao.markSubmitted("scan-1")).thenReturn(Unit)

        val submitter = EvaluationSubmitter(dao, firebaseService, statusHolder)

        val first = submitter.submitNow("scan-1")
        val second = submitter.submitNow("scan-1")

        assertTrue("expected the first (confirm-time) call to actually submit", first)
        assertFalse(
            "expected the second (analysis-time-fallback-shaped) call to be a no-op " +
                "once the row is marked submitted",
            second
        )
        verify(firebaseService, times(1)).submitEvaluation(any())
    }

    @Test
    fun `submitNow returns false and never calls the callable when no row exists`() = runTest {
        // Mirrors a card that was fully skipped (zero taps) — no row was
        // ever persisted (TechnicianEvaluationViewModel.applyOnTap only
        // upserts on a judgement or an existing row), so confirm() calling
        // submitNow must not crash or send an empty payload.
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        whenever(dao.getByScanId("scan-none")).thenAnswer { null }

        val submitter = EvaluationSubmitter(dao, firebaseService, statusHolder)
        val result = submitter.submitNow("scan-none")

        assertFalse(result)
        verify(firebaseService, times(0)).submitEvaluation(any())
    }

    @Test
    fun `a judgement-free row is marked submitted without ever calling the callable`() = runTest {
        // EvaluationPayloadBuilder.build returns null for a row with no
        // grade/ratings/defects (shouldn't exist per the class's own doc,
        // but submitOne still guards it defensively).
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        val emptyRow = SessionEvaluationEntity(
            scanId = "scan-empty",
            sessionId = "session-1",
            technicianGradeRaw = null,
            categoryRatingsJson = "{}",
            visibleDefectsJson = "[]",
            shownAtMs = 1_000L,
            lastAnswerAtMs = null,
            submitted = false
        )
        whenever(dao.getByScanId("scan-empty")).thenAnswer { emptyRow }

        val submitter = EvaluationSubmitter(dao, firebaseService, statusHolder)
        val result = submitter.submitNow("scan-empty")

        assertFalse(result)
        verify(firebaseService, times(0)).submitEvaluation(any())
        verify(dao, times(1)).markSubmitted("scan-empty")
    }

    // ── Order 2026-09-30 («أمر — التقييم (ب)...»، البند ١) ──────────────

    @Test
    fun `a confirmed-terminal rejection marks finalFailure, never markSubmitted, and propagates`() = runTest {
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        whenever(dao.getByScanId("scan-rejected")).thenReturn(rowFor("scan-rejected", submitted = false))
        val rejection = mock<FirebaseFunctionsException>()
        whenever(rejection.code).thenReturn(FirebaseFunctionsException.Code.PERMISSION_DENIED)
        whenever(rejection.message).thenReturn("جلسة الفحص غير صالحة")
        // doAnswer { throw ... } rather than doThrow(...)/thenThrow(...):
        // Mockito validates a stubbed throw against the mocked METHOD's
        // declared checked exceptions, and a Kotlin suspend function's
        // JVM-visible signature declares none — doThrow() still hits that
        // validation (confirmed: it failed identically to
        // whenever().thenThrow() here), only an Answer lambda that itself
        // performs the `throw` bypasses Mockito's exception-type machinery
        // entirely.
        org.mockito.kotlin.doAnswer { throw rejection }.whenever(firebaseService).submitEvaluation(any())

        val submitter = EvaluationSubmitter(dao, firebaseService, statusHolder)

        var thrown: Throwable? = null
        try {
            submitter.submitNow("scan-rejected")
        } catch (t: Throwable) {
            thrown = t
        }

        assertTrue("submitNow must propagate the rejection, not swallow it", thrown === rejection)
        verify(dao, times(1)).markFinalFailure("scan-rejected")
        verify(dao, never()).markSubmitted("scan-rejected")
    }

    @Test
    fun `a transient failure (network) does NOT mark finalFailure - stays eligible for retry`() = runTest {
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        whenever(dao.getByScanId("scan-network-blip")).thenReturn(rowFor("scan-network-blip", submitted = false))
        // doAnswer { throw ... } — same reason as the test above (IOException
        // is checked too, and doThrow() hits the same Mockito validation).
        org.mockito.kotlin.doAnswer { throw java.io.IOException("Unable to resolve host") }
            .whenever(firebaseService).submitEvaluation(any())

        val submitter = EvaluationSubmitter(dao, firebaseService, statusHolder)

        var thrown: Throwable? = null
        try {
            submitter.submitNow("scan-network-blip")
        } catch (t: Throwable) {
            thrown = t
        }

        assertTrue(thrown is java.io.IOException)
        verify(dao, never()).markFinalFailure("scan-network-blip")
        verify(dao, never()).markSubmitted("scan-network-blip")
    }

    @Test
    fun `a row already marked finalFailure is never re-attempted, even if handed to submitOne directly`() = runTest {
        // Defence in depth: SessionEvaluationDao.getPending()'s own
        // "AND finalFailure = 0" clause is the primary guard (verified
        // against real SQLite in SessionEvaluationMigration6To7JvmTest,
        // though NOT via the actual Room-generated method — no Room database
        // here). This test proves submitOne's in-memory check is a second,
        // independent line of defence even if a finalFailure row somehow
        // reached it.
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        val finalFailureRow = SessionEvaluationEntity(
            scanId = "scan-already-final",
            sessionId = "session-1",
            technicianGradeRaw = "good",
            categoryRatingsJson = "{}",
            visibleDefectsJson = "[]",
            shownAtMs = 1_000L,
            lastAnswerAtMs = 2_000L,
            submitted = false,
            finalFailure = true
        )
        whenever(dao.getByScanId("scan-already-final")).thenReturn(finalFailureRow)

        val result = EvaluationSubmitter(dao, firebaseService, statusHolder).submitNow("scan-already-final")

        assertFalse(result)
        verify(firebaseService, never()).submitEvaluation(any())
    }

    @Test
    fun `discard deletes the row - a cancelled scan's evaluation is never sent`() = runTest {
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()

        EvaluationSubmitter(dao, firebaseService, statusHolder).discard("scan-cancelled")

        verify(dao, times(1)).deleteByScanId("scan-cancelled")
        verify(firebaseService, never()).submitEvaluation(any())
    }
}
