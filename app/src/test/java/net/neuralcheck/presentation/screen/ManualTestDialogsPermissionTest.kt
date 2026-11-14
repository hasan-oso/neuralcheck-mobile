package net.neuralcheck.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Order 2026-09-30 (بند ٢): M-06 (primary microphone) must record
 * "not_tested" — never "fail" — when RECORD_AUDIO is denied, because a
 * denial means recording was never attempted, not that the hardware failed.
 *
 * [micPermissionResultToStatus] is the exact function
 * MicrophoneTestDialog's permission-result launcher calls
 * (ManualTestDialogs.kt) — this test exercises that function directly, not
 * a re-implementation of it. It does NOT exercise
 * ActivityResultContracts.RequestPermission()/ContextCompat
 * .checkSelfPermission themselves (this test does not use Robolectric, a test
 * dependency since 3284cca — see the task's final report), only the decision the Composable
 * makes once the OS hands back a grant/deny result.
 */
class ManualTestDialogsPermissionTest {

    @Test
    fun `denied permission maps to not_tested, never fail`() {
        assertEquals("not_tested", micPermissionResultToStatus(granted = false))
    }

    @Test
    fun `granted permission produces no auto status - the record flow decides pass fail later`() {
        assertNull(micPermissionResultToStatus(granted = true))
    }
}
