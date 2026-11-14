package net.neuralcheck.presentation.viewmodel

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * ملحق خامس بالقرار ١٠٤ البند ٣، مصحَّحاً بالملحق السادس البند ١.
 * [createSessionErrorMessage] (ViewModels.kt, used by
 * `GenerateSessionViewModel.create()`'s catch block) used to let
 * `GenerateSessionScreen.kt`'s `ErrorState` (:110-111) show
 * `"[create] ${t.message}"` verbatim to the shop owner —
 * `createScanSession.js` builds that message from the underlying caught
 * error's own text (`createScanSession.js:76`, relayed, not
 * independently verified — neuralcheck-backend is a separate repository,
 * rule 2), so it could carry raw technical detail or third-party-provider
 * text. Fixed: the banner now always shows `session_error_generic`
 * (the existing, on-topic string for this screen and failure — corrected
 * from `error_generic`, which the previous round used, per the
 * coordinator's own note), and the raw message goes only to `Log.e`,
 * capped to 200 characters and without the `Throwable` itself passed to
 * `Log.e` (which would otherwise append its own unbounded
 * `toString()`/stack trace) — the rule 36 judgment this function's own
 * doc comment states in full.
 *
 * Extracted (A110 precedent) so this is testable with a mocked [Context],
 * without constructing `GenerateSessionViewModel` or
 * `FirebaseAnalysisService` (same construction blocker as
 * `AiScoringEngine`'s `analysisErrorMessage` tests —
 * `FirebaseAnalysisService`'s eager `Firebase.functions` field
 * initializer throws with no `FirebaseApp` in a plain JVM test).
 */
class CreateSessionErrorMessageTest {

    // Order 2026-09-27 (ملحق سادس بالقرار ١٠٤ البند ١، القاعدة 23) —
    // REPLACES this test's prior expected string (error_generic): the
    // coordinator's own correction names session_error_generic as the
    // right existing string for this screen/failure. The NEGATIVE
    // assertion (raw message absent) is unchanged and still meaningful.
    @Test
    fun `createSessionErrorMessage always returns session_error_generic, never the raw message`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.session_error_generic))
            .thenReturn("تعذّر إنشاء الجلسة")
        // Deliberately distinctive so this test would fail loudly if the
        // raw text ever leaked into the result.
        val t = RuntimeException("XYZ-SECRET-LOOKING-TOKEN from createScanSession's underlying error")

        val result = createSessionErrorMessage(context, t)

        assertEquals("تعذّر إنشاء الجلسة", result)
        assertFalse(
            "the raw message must be completely absent from what reaches the screen",
            result.contains("XYZ-SECRET-LOOKING-TOKEN")
        )
    }

    @Test
    fun `createSessionErrorMessage returns session_error_generic even when the message is null`() {
        val context = mock<Context>()
        whenever(context.getString(net.neuralcheck.R.string.session_error_generic))
            .thenReturn("تعذّر إنشاء الجلسة")
        val t = RuntimeException()

        assertEquals("تعذّر إنشاء الجلسة", createSessionErrorMessage(context, t))
    }
}
