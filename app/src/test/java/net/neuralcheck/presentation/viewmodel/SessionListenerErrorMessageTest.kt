package net.neuralcheck.presentation.viewmodel

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * ملحق سادس بالقرار ١٠٤ البند ١. [sessionListenerErrorMessage]
 * (ViewModels.kt, used by `GenerateSessionViewModel.attachListener`'s
 * Firestore snapshot-listener catch) used to let `GenerateSessionScreen.kt`'s
 * `ErrorState` (:110-111 — the same field `createSessionErrorMessage`
 * feeds) show `"[listener $code] ${err.message}"` verbatim. Fixed:
 * always `error_generic` — no existing string is specific to a listen
 * failure (see the function's own doc for why `session_expired`/
 * `join_session_invalid` don't fit), and the raw message goes only to
 * `Log.e`, capped and without the exception object (rule 36, same
 * treatment as `createSessionErrorMessage`).
 */
class SessionListenerErrorMessageTest {

    @Test
    fun `sessionListenerErrorMessage returns error_generic for a PERMISSION_DENIED listen failure, raw message absent`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.error_generic))
            .thenReturn("حدث خطأ غير متوقع")
        val err = mock<FirebaseFirestoreException>()
        whenever(err.code).thenReturn(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        whenever(err.message).thenReturn("XYZ-SECRET-LOOKING-TOKEN Missing or insufficient permissions")

        val result = sessionListenerErrorMessage(context, "scanSessions", "abc123", err)

        assertEquals("حدث خطأ غير متوقع", result)
        assertFalse(
            "the raw message must be completely absent from what reaches the screen",
            result.contains("XYZ-SECRET-LOOKING-TOKEN")
        )
    }

    @Test
    fun `sessionListenerErrorMessage returns error_generic for a non-FirestoreException too`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.error_generic))
            .thenReturn("حدث خطأ غير متوقع")
        val err = RuntimeException("some unrelated listener failure")

        assertEquals("حدث خطأ غير متوقع", sessionListenerErrorMessage(context, "scanSessions", "abc123", err))
    }
}
