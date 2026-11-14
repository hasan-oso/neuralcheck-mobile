package net.neuralcheck.domain.evaluation

import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Order 2026-09-30 («أمر — التقييم (ب)، ومهلة الجلسة، والبنود المعلّقة»،
 * البند ١): [isFinalEvaluationRejection] classifies by CODE **and** TEXT
 * together (القاعدة ٢١) — every branch below pins that both must match, not
 * either alone, and that an IOException-typed cause always defeats it (same
 * guard ViewModels.kt's joinSessionErrorMessage already applies to its own
 * three codes).
 *
 * Pure function, no Context/Room/Firebase Functions network call — this IS
 * the production classification EvaluationSubmitter.submitOne calls, not a
 * re-implementation of it.
 */
class IsFinalEvaluationRejectionTest {

    private fun functionsException(
        code: FirebaseFunctionsException.Code,
        message: String,
        cause: Throwable? = null
    ): FirebaseFunctionsException {
        val t = mock<FirebaseFunctionsException>()
        whenever(t.code).thenReturn(code)
        whenever(t.message).thenReturn(message)
        whenever(t.cause).thenReturn(cause)
        return t
    }

    @Test
    fun `PERMISSION_DENIED with the exact server text is final`() {
        val t = functionsException(FirebaseFunctionsException.Code.PERMISSION_DENIED, "جلسة الفحص غير صالحة")
        assertTrue(isFinalEvaluationRejection(t))
    }

    @Test
    fun `FAILED_PRECONDITION with the exact server text is final`() {
        val t = functionsException(FirebaseFunctionsException.Code.FAILED_PRECONDITION, "الجلسة منتهية")
        assertTrue(isFinalEvaluationRejection(t))
    }

    @Test
    fun `PERMISSION_DENIED with an unexpected message is transient, not final - text matters too`() {
        // Rule 21: the code alone is not enough. A PERMISSION_DENIED from
        // some other cause (a future, different backend check) must not be
        // silently treated as this specific "session not scanning" case.
        val t = functionsException(FirebaseFunctionsException.Code.PERMISSION_DENIED, "some other reason entirely")
        assertFalse(isFinalEvaluationRejection(t))
    }

    @Test
    fun `FAILED_PRECONDITION with an unexpected message is transient, not final`() {
        val t = functionsException(FirebaseFunctionsException.Code.FAILED_PRECONDITION, "a different precondition failed")
        assertFalse(isFinalEvaluationRejection(t))
    }

    @Test
    fun `PERMISSION_DENIED with the right text but an IOException cause is transient`() {
        val t = functionsException(
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            "جلسة الفحص غير صالحة",
            cause = java.io.IOException("local transport detail")
        )
        assertFalse(isFinalEvaluationRejection(t))
    }

    @Test
    fun `FAILED_PRECONDITION with the right text but an IOException cause is transient`() {
        val t = functionsException(
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            "الجلسة منتهية",
            cause = java.io.IOException("local transport detail")
        )
        assertFalse(isFinalEvaluationRejection(t))
    }

    @Test
    fun `an unrelated FirebaseFunctionsException code is transient regardless of message`() {
        val t = functionsException(FirebaseFunctionsException.Code.UNAVAILABLE, "جلسة الفحص غير صالحة")
        assertFalse(
            "even the exact server TEXT under the wrong CODE must not be treated as final",
            isFinalEvaluationRejection(t)
        )
    }

    @Test
    fun `a plain IOException (not FirebaseFunctionsException) is transient`() {
        assertFalse(isFinalEvaluationRejection(java.io.IOException("Unable to resolve host")))
    }

    @Test
    fun `a generic RuntimeException is transient`() {
        assertFalse(isFinalEvaluationRejection(RuntimeException("some local failure")))
    }
}
