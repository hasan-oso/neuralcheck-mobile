package net.neuralcheck.presentation.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Order 2026-09-30 (فحص NC-TM7PDN، البند ٤): covers the two required
 * location-service branches — enabled, and disabled (which the "تابع"
 * button then dismisses).
 *
 * [shouldShowLocationServiceNotice] is the exact decision
 * ScanPermissionsGate.enterLocationServiceStage() and its ON_RESUME
 * refresh call in HomeScreen.kt — this test exercises that function
 * directly. It does NOT exercise LocationManager.isProviderEnabled() itself
 * (this test does not use Robolectric, a test dependency since 3284cca — see the task's final
 * report), and it does NOT cover the "ثم «تابع»" dismissal, which is a
 * Compose onClick callback (`showLocationServiceNotice = false`) with no
 * decision logic of its own to extract — that half of the branch is a
 * known gap in this repo's current test stack, flagged in the final report
 * rather than papered over with a fake Composable harness.
 */
class ScanPermissionsGateTest {

    @Test
    fun `location service enabled - no notice`() {
        assertFalse(shouldShowLocationServiceNotice(locationServiceOn = true))
    }

    @Test
    fun `location service disabled - notice shows (until تابع dismisses it)`() {
        assertTrue(shouldShowLocationServiceNotice(locationServiceOn = false))
    }
}
