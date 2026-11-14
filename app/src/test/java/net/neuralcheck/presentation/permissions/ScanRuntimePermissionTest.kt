package net.neuralcheck.presentation.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Order 2026-09-30 (طلب الأذونات قبل الفحص، بند ١/٥): covers the three
 * required branches — grant all, deny one, deny all — for the post-login
 * scan-permission gate's skipped-test mapping (HomeScreen.kt's
 * ScanPermissionsGate calls the exact same [ScanRuntimePermission] /
 * [skippedTestIdsFor] under test here; this is not a stand-in that replaces
 * the real decision — it IS the real decision, since it takes no Context and
 * does no platform I/O to fake).
 *
 * This does NOT exercise the real android.permission checkSelfPermission
 * layer — see the final report's "هل يمرّ بطبقة الإذن الحقيقية أم يستبدلها"
 * column: when this was written the unit-test setup lacked Robolectric (a test dependency since 3284cca; not used here), so
 * ContextCompat.checkSelfPermission itself (called from
 * HomeScreen.ScanPermissionsGate.isGranted / CameraTests / GpsTests /
 * NetworkTests) cannot run under plain JUnit — those call sites are gated by
 * the OS at runtime only, not covered by this test class.
 */
class ScanRuntimePermissionTest {

    @Test
    fun `grant all - nothing denied means nothing skipped`() {
        val skipped = skippedTestIdsFor(denied = emptyList())
        assertTrue("expected no skipped tests when nothing is denied, got $skipped", skipped.isEmpty())
    }

    @Test
    fun `deny one - microphone denial skips only M-06`() {
        val skipped = skippedTestIdsFor(denied = listOf(ScanRuntimePermission.MICROPHONE))
        assertEquals(listOf("M-06"), skipped)
    }

    @Test
    fun `deny one - location denial skips L-02 and L-03 only (not L-01)`() {
        val skipped = skippedTestIdsFor(denied = listOf(ScanRuntimePermission.LOCATION))
        // L-01 (testGpsAvailable) has no checkSelfPermission gate in
        // GpsTests.kt — see ScanPermissions.kt's doc comment — so it must
        // NOT appear here even though it's in the same GPS category.
        assertEquals(listOf("L-02", "L-03"), skipped)
        assertTrue("L-01" !in skipped)
    }

    @Test
    fun `deny one - phone_state denial skips only N-04`() {
        val skipped = skippedTestIdsFor(denied = listOf(ScanRuntimePermission.PHONE_STATE))
        assertEquals(listOf("N-04"), skipped)
    }

    @Test
    fun `deny one - camera denial preserves the CM-08 and CM-11 catalog quirk`() {
        // CameraTests.kt's cameraPermissionDeniedResults() folds CM-08
        // (fingerprint, BiometricManager) and CM-11 (vibrator motor, no
        // CAMERA use) in alongside the real camera tests. That's a
        // pre-existing quirk this order explicitly leaves untouched — the
        // notice text must describe what really happens, not an idealised
        // subset.
        val skipped = skippedTestIdsFor(denied = listOf(ScanRuntimePermission.CAMERA))
        assertEquals(
            listOf("CM-01", "CM-02", "CM-03", "CM-04", "CM-05", "CM-06", "CM-07", "CM-08", "CM-10", "CM-11"),
            skipped
        )
    }

    // fieldsVersion 68 (S-18) REPLACES the old "deny all" test, which treated a
    // fixed four-permission list as THE bundle: ACTIVITY_RECOGNITION joins it
    // from API 29 only, so the bundle is asked of the API level (forSdk).

    @Test
    fun `deny all on API 29 and above - the union now includes SN-08 and SN-09, no duplicates, order preserved`() {
        val skipped = skippedTestIdsFor(denied = ScanRuntimePermission.forSdk(35))
        val expected = listOf(
            "CM-01", "CM-02", "CM-03", "CM-04", "CM-05", "CM-06", "CM-07", "CM-08", "CM-10", "CM-11",
            "M-06",
            "L-02", "L-03",
            "N-04",
            "SN-08", "SN-09"
        )
        assertEquals(expected, skipped)
        assertEquals("no duplicate IDs", skipped.distinct().size, skipped.size)
    }

    @Test
    fun `deny all below API 29 - ACTIVITY_RECOGNITION does not exist, so it is not in the bundle and skips nothing`() {
        val bundle = ScanRuntimePermission.forSdk(28)
        assertTrue(ScanRuntimePermission.ACTIVITY_RECOGNITION !in bundle)
        assertEquals(4, bundle.size)
        val skipped = skippedTestIdsFor(denied = bundle)
        assertTrue("SN-08" !in skipped && "SN-09" !in skipped)
    }

    @Test
    fun `the bundle starts including ACTIVITY_RECOGNITION exactly at API 29`() {
        assertTrue(ScanRuntimePermission.ACTIVITY_RECOGNITION !in ScanRuntimePermission.forSdk(28))
        assertTrue(ScanRuntimePermission.ACTIVITY_RECOGNITION in ScanRuntimePermission.forSdk(29))
        assertEquals(29, ScanRuntimePermission.ACTIVITY_RECOGNITION.minSdk)
        // the four older permissions are asked at every level
        assertEquals(4, ScanRuntimePermission.forSdk(1).size)
    }

    @Test
    fun `deny one - activity recognition denial skips SN-08 and SN-09 only`() {
        assertEquals(
            listOf("SN-08", "SN-09"),
            skippedTestIdsFor(denied = listOf(ScanRuntimePermission.ACTIVITY_RECOGNITION))
        )
    }

    @Test
    fun `the manifest declares every permission the gate asks for`() {
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.exists() }.readText()
        for (p in ScanRuntimePermission.entries) {
            assertTrue("AndroidManifest.xml must declare ${p.androidPermission}", manifest.contains("\"${p.androidPermission}\""))
        }
    }

    @Test
    fun `every ScanRuntimePermission maps to the real android permission constant`() {
        // Guards against a typo silently requesting the wrong permission —
        // these are the same java.lang.String constants
        // ContextCompat.checkSelfPermission and ActivityResultContracts
        // .RequestMultiplePermissions consume at runtime, not a fake.
        assertEquals(android.Manifest.permission.CAMERA, ScanRuntimePermission.CAMERA.androidPermission)
        assertEquals(android.Manifest.permission.RECORD_AUDIO, ScanRuntimePermission.MICROPHONE.androidPermission)
        assertEquals(android.Manifest.permission.ACCESS_FINE_LOCATION, ScanRuntimePermission.LOCATION.androidPermission)
        assertEquals(android.Manifest.permission.READ_PHONE_STATE, ScanRuntimePermission.PHONE_STATE.androidPermission)
        assertEquals(
            android.Manifest.permission.ACTIVITY_RECOGNITION,
            ScanRuntimePermission.ACTIVITY_RECOGNITION.androidPermission
        )
    }
}
