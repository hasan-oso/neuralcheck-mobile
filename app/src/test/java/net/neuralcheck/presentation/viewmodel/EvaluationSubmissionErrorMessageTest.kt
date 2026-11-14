package net.neuralcheck.presentation.viewmodel

import android.content.Context
import com.google.firebase.functions.FirebaseFunctionsException
import net.neuralcheck.domain.evaluation.evaluationSubmissionErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Order 2026-09-30 (أمر «مسار الشريك، والنصوص، وإصلاح الخادم»، البند ٣، ثم
 * «ملحق — النصّ، ورسالة التقييم، والبروتوكول»):
 * [evaluationSubmissionErrorMessage] is the SHARED failure-message logic —
 * originally written placement-independent after a verifier caught that no
 * confirm button exists in TechnicianEvaluationCard.kt (القاعدة ١٤), NOW
 * called from [net.neuralcheck.domain.evaluation.EvaluationSubmitter
 * .submitOnScanComplete] (moved there from ViewModels.kt so the domain
 * layer doesn't import a presentation-layer function — see that file's own
 * doc). This test file stays in its original package/location; only the
 * import changed. These tests still cover the function itself, pinning
 * the two confirmed rejection
 * codes so a later change can't silently widen or narrow them.
 */
class EvaluationSubmissionErrorMessageTest {

    @Test
    fun `network failure returns the shared network message, unchanged`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.login_error_network))
            .thenReturn("لا يوجد اتصال بالإنترنت")
        val t = java.io.IOException("Unable to resolve host \"neuralchek-9b6d7.cloudfunctions.net\": No address associated with hostname")

        assertEquals("لا يوجد اتصال بالإنترنت", evaluationSubmissionErrorMessage(context, t))
    }

    @Test
    fun `PERMISSION_DENIED shows the server's own message verbatim (session not scanning)`() {
        // submitEvaluation.js:73-74, relayed by the coordinator from
        // neuralcheck-backend@c7446a4 — not verified from this repo.
        val context = mock<Context>()
        val t = mock<FirebaseFunctionsException>()
        whenever(t.code).thenReturn(FirebaseFunctionsException.Code.PERMISSION_DENIED)
        whenever(t.message).thenReturn("جلسة الفحص غير صالحة")

        assertEquals("جلسة الفحص غير صالحة", evaluationSubmissionErrorMessage(context, t))
    }

    @Test
    fun `FAILED_PRECONDITION shows the server's own message verbatim (session expired)`() {
        // submitEvaluation.js:76-80 — 15-minute session timeout from
        // createScanSession.js:58, relayed by the coordinator.
        val context = mock<Context>()
        val t = mock<FirebaseFunctionsException>()
        whenever(t.code).thenReturn(FirebaseFunctionsException.Code.FAILED_PRECONDITION)
        whenever(t.message).thenReturn("الجلسة منتهية")

        assertEquals("الجلسة منتهية", evaluationSubmissionErrorMessage(context, t))
    }

    @Test
    fun `an unconfirmed code (e_g_ INTERNAL) falls back to the generic message, raw text absent`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.eval_submit_failed_generic))
            .thenReturn("تعذّر إرسال التقييم")
        val t = mock<FirebaseFunctionsException>()
        whenever(t.code).thenReturn(FirebaseFunctionsException.Code.INTERNAL)
        whenever(t.message).thenReturn("some internal SDK detail that must not reach the screen")

        val result = evaluationSubmissionErrorMessage(context, t)

        assertEquals("تعذّر إرسال التقييم", result)
        assertFalse(
            "the raw message must be completely absent for an unconfirmed code",
            result.contains("internal SDK detail")
        )
    }

    @Test
    fun `a non-FirebaseFunctionsException failure falls back to the generic message`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.eval_submit_failed_generic))
            .thenReturn("تعذّر إرسال التقييم")
        val t = RuntimeException("some local Room/coroutine failure detail")

        assertEquals("تعذّر إرسال التقييم", evaluationSubmissionErrorMessage(context, t))
    }
}
