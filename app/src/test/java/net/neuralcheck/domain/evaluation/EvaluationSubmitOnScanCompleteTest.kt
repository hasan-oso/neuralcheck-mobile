package net.neuralcheck.domain.evaluation

import android.content.Context
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.neuralcheck.data.local.db.dao.SessionEvaluationDao
import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity
import net.neuralcheck.data.remote.FirebaseAnalysisService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Order 2026-09-30 («ملحق — النصّ، ورسالة التقييم، والبروتوكول»):
 * [EvaluationSubmitter.submitOnScanComplete] — the exact call
 * ActiveScanViewModel's `Completed` branch makes.
 *
 * ── ما يفحصه هذا الاختبار بدقّة (القاعدة ٣٧) ──
 * Exercises the REAL [EvaluationSubmitter] and a REAL
 * [EvaluationSubmissionStatusHolder] (not mocked — it has no dependencies
 * of its own) — [SessionEvaluationDao]/[FirebaseAnalysisService]/[Context]
 * are Mockito mocks (no Room, no real network, no Android resources; this test
 * does not use Robolectric, a test dependency since 3284cca).
 *
 * `submitOnScanComplete` launches on [EvaluationSubmitter]'s OWN persistent
 * `CoroutineScope(SupervisorJob() + Dispatchers.IO)` — a REAL background
 * dispatcher — by design (see that method's own doc for why: surviving
 * ActiveScanViewModel being cleared). That means this test canNOT use
 * `kotlinx.coroutines.test.runTest`'s virtual-time scheduler (it has no
 * visibility into work happening on a genuinely different real
 * dispatcher/thread, so it could time out a `withTimeoutOrNull` virtually
 * before the real background coroutine ever runs) — every test body here
 * runs entirely inside `runBlocking` (real time) instead. The "nothing is
 * published" assertions (success / no evaluation row) rely on the bound
 * being long enough for fast local Mockito calls to complete, which they
 * are (no real I/O) — not on proving a true negative for all time.
 *
 * ── 2026-10-08: the two POSITIVE tests no longer race a real timeout ──
 * `a rejection publishes the classified message...` and `an unconfirmed-code
 * rejection publishes the generic fallback text` used to wait with
 * `withTimeoutOrNull(2_000)` (real time). A run at the 1.0.10 release build
 * (2026-10-07) failed the second one with `received == null` after 2.374 s
 * (the same test takes 0.047-0.063 s warm). Two device-speed dependencies
 * were in that shape, both removed here and both test-only:
 *   1. the 2 s bound — a cold or loaded machine can exceed it;
 *   2. a subscribe/publish race — `async { ... first() }` was started
 *      lazily-dispatched, so the collector subscribed only once the test
 *      body suspended at `await()`, while the submitter's IO coroutine
 *      could already have published; `EvaluationSubmissionStatusHolder.
 *      failures` has replay = 0, so an event with no subscriber is DROPPED.
 * Now the collector starts [CoroutineStart.UNDISPATCHED] — it runs to its
 * first suspension inside `first()`, i.e. it is subscribed, BEFORE
 * `submitOnScanComplete` is called — and the test then awaits the event
 * ITSELF with no timeout. `runTest`'s virtual clock is deliberately not
 * used: production launches on its own private `Dispatchers.IO` scope
 * (EvaluationSubmitter.kt:221, not injectable), which a test scheduler does
 * not control, so a virtual timeout would fire while the real IO work is
 * still running. The only real-clock element left is `@Test(timeout =
 * 60_000)`, a hang guard: it can only turn "the event never comes" into a
 * failure instead of an endless wait, and has no bearing on a normal run.
 * The two NEGATIVE tests below keep their 500 ms bound — a true negative
 * can only be bounded, and a slower machine can only make them pass more
 * easily, never fail them.
 */
class EvaluationSubmitOnScanCompleteTest {

    private fun rowFor(scanId: String) = SessionEvaluationEntity(
        scanId = scanId,
        sessionId = "session-1",
        technicianGradeRaw = "good",
        categoryRatingsJson = "{}",
        visibleDefectsJson = "[]",
        shownAtMs = 1_000L,
        lastAnswerAtMs = 2_000L,
        submitted = false
    )

    @Test(timeout = 60_000)
    fun `a rejection publishes the classified message to the status holder`() = runBlocking {
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        val context = mock<Context>()
        whenever(dao.getByScanId("scan-1")).thenReturn(rowFor("scan-1"))
        val rejection = mock<FirebaseFunctionsException>()
        whenever(rejection.code).thenReturn(FirebaseFunctionsException.Code.PERMISSION_DENIED)
        whenever(rejection.message).thenReturn("جلسة الفحص غير صالحة")
        doAnswer { throw rejection }.whenever(firebaseService).submitEvaluation(any())
        val statusHolder = EvaluationSubmissionStatusHolder()
        val submitter = EvaluationSubmitter(dao, firebaseService, statusHolder)

        // UNDISPATCHED: subscribed to `failures` (replay = 0) before the
        // submission below can publish; then await the event, no timeout.
        val deferred = async(start = CoroutineStart.UNDISPATCHED) { statusHolder.failures.first() }
        submitter.submitOnScanComplete("scan-1", context)
        val received = deferred.await()

        assertEquals(
            EvaluationSubmissionStatusHolder.ScanFailure("scan-1", "جلسة الفحص غير صالحة"),
            received
        )
    }

    @Test(timeout = 60_000)
    fun `an unconfirmed-code rejection publishes the generic fallback text`() = runBlocking {
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.eval_submit_failed_generic))
            .thenReturn("تعذّر إرسال التقييم")
        whenever(dao.getByScanId("scan-2")).thenReturn(rowFor("scan-2"))
        val rejection = mock<FirebaseFunctionsException>()
        whenever(rejection.code).thenReturn(FirebaseFunctionsException.Code.INTERNAL)
        whenever(rejection.message).thenReturn("some internal SDK detail that must not reach the screen")
        doAnswer { throw rejection }.whenever(firebaseService).submitEvaluation(any())
        val statusHolder = EvaluationSubmissionStatusHolder()
        val submitter = EvaluationSubmitter(dao, firebaseService, statusHolder)

        // UNDISPATCHED + no timeout — see the class doc (2026-10-08).
        val deferred = async(start = CoroutineStart.UNDISPATCHED) { statusHolder.failures.first() }
        submitter.submitOnScanComplete("scan-2", context)
        val received = deferred.await()

        assertEquals(EvaluationSubmissionStatusHolder.ScanFailure("scan-2", "تعذّر إرسال التقييم"), received)
    }

    @Test
    fun `a success publishes nothing`() = runBlocking {
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        val context = mock<Context>()
        whenever(dao.getByScanId("scan-ok")).thenReturn(rowFor("scan-ok"))
        // submitEvaluation left unstubbed — an unstubbed suspend Unit-returning
        // mock call succeeds silently (Mockito's default answer), modelling a
        // successful submission with no need to mock a real response object.
        val statusHolder = EvaluationSubmissionStatusHolder()
        val submitter = EvaluationSubmitter(dao, firebaseService, statusHolder)

        val deferred = async { withTimeoutOrNull(500) { statusHolder.failures.first() } }
        submitter.submitOnScanComplete("scan-ok", context)
        val received = deferred.await()

        assertNull("a successful submission must not publish anything — no success line exists", received)
    }

    @Test
    fun `no evaluation row publishes nothing`() = runBlocking {
        val dao = mock<SessionEvaluationDao>()
        val firebaseService = mock<FirebaseAnalysisService>()
        val context = mock<Context>()
        whenever(dao.getByScanId("scan-none")).thenReturn(null)
        val statusHolder = EvaluationSubmissionStatusHolder()
        val submitter = EvaluationSubmitter(dao, firebaseService, statusHolder)

        val deferred = async { withTimeoutOrNull(500) { statusHolder.failures.first() } }
        submitter.submitOnScanComplete("scan-none", context)
        val received = deferred.await()

        assertNull(
            "no evaluation ever existed (non-QR scan, or a QR scan with zero taps) — nothing to show",
            received
        )
    }
}
