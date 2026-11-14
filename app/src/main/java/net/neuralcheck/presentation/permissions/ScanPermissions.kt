package net.neuralcheck.presentation.permissions

import android.Manifest
import net.neuralcheck.R

/**
 * Runtime permissions the functional-test scan needs, and the exact test IDs
 * each one silently turns into NOT_TESTED (never FAIL) when denied.
 *
 * Order 2026-09-30 (طلب الأذونات قبل الفحص، بند ١): requested together right
 * after login, before the user can start a scan — see HomeScreen.kt's
 * ScanPermissionsGate — instead of the old behaviour where each test/dialog
 * only asked for its own permission mid-scan the first time it happened to
 * run (QRScannerScreen.kt:257 for CAMERA, ManualTestDialogs.kt:567 for
 * RECORD_AUDIO / :1347 for CAMERA again, and the silent
 * checkSelfPermission-gated NOT_TESTED paths inside CameraTests.kt,
 * GpsTests.kt and NetworkTests.kt).
 *
 * The [autoNotTestedIds] lists are read off the CURRENT behaviour of each
 * hardware test class when its permission is missing — verified against the
 * source on 2026-09-30, NOT off TestSpec.kt's requiredPermission field. That
 * field is a stale documentation index (see TestSpec.kt's own doc comment:
 * "no production code reads them ... only .category and, in a few spots,
 * .requiredPermission are consumed") and does not match what the code
 * actually gates — e.g. TestSpec claims N-11 (Bluetooth) needs
 * BLUETOOTH_CONNECT, but NetworkTests.kt's testBluetooth() never checks any
 * permission (BluetoothAdapter.isEnabled() doesn't require it); TestSpec also
 * claims N-09 needs ACCESS_FINE_LOCATION, but NetworkTests.testWifiLinkSpeed()
 * has no permission check either. Neither is included below because neither
 * is actually gated in code.
 */
enum class ScanRuntimePermission(
    val androidPermission: String,
    /**
     * Test IDs that end up NOT_TESTED when this permission is denied,
     * verified against the real source:
     *  - CAMERA: CameraTests.kt:35-41 → cameraPermissionDeniedResults()
     *    (CameraTests.kt:225-240) marks CM-01..CM-08, CM-10, CM-11 as
     *    NOT_TESTED. CM-08 (fingerprint, BiometricManager) and CM-11
     *    (vibrator, Vibrator service) don't actually need CAMERA — that's a
     *    pre-existing test-catalog quirk (CameraTests.kt's own gate runs
     *    before any of the individual test functions, so it folds all of
     *    them in together) that is explicitly OUT OF SCOPE to change per
     *    this order, but the notice text must still describe what really
     *    happens, not an idealised version.
     *  - MICROPHONE: ManualTestDialogs.kt's MicrophoneTestDialog (M-06).
     *  - LOCATION: GpsTests.kt:63-79 (L-02 testSatellites) and :162-178
     *    (L-03 testAccuracy) both return NOT_TESTED when
     *    ACCESS_FINE_LOCATION is missing. L-01 (testGpsAvailable) is
     *    deliberately NOT listed here: it has no checkSelfPermission call at
     *    all — it just calls isProviderEnabled() wrapped in a
     *    catch-SecurityException-as-false, so a denial there surfaces as a
     *    FAIL ("disabled") rather than NOT_TESTED. That is a separate,
     *    pre-existing mislabelling this order did not ask to fix (see final
     *    report, "القيود المعروفة غير المُصلَحة").
     *  - ACTIVITY_RECOGNITION (fieldsVersion 68, S-18): SensorTests.testSensor
     *    for SN-08 (step detector) and SN-09 (step counter) records NOT_TESTED
     *    when it is missing on API >= 29 ([minSdk]). On API < 29 the permission
     *    does not exist, is not requested and cannot be missing.
     *  - PHONE_STATE: NetworkTests.kt:129-135 (N-04 testNetworkType).
     */
    val autoNotTestedIds: List<String>,
    val labelRes: Int,
    /**
     * The first Android API level at which this permission exists and is
     * requested (fieldsVersion 68). Below it the gate neither requests it nor
     * lists it, and the test that needs it runs without it.
     */
    val minSdk: Int = 1
) {
    CAMERA(
        Manifest.permission.CAMERA,
        listOf("CM-01", "CM-02", "CM-03", "CM-04", "CM-05", "CM-06", "CM-07", "CM-08", "CM-10", "CM-11"),
        R.string.scan_permission_label_camera
    ),
    MICROPHONE(
        Manifest.permission.RECORD_AUDIO,
        listOf("M-06"),
        R.string.scan_permission_label_microphone
    ),
    LOCATION(
        Manifest.permission.ACCESS_FINE_LOCATION,
        listOf("L-02", "L-03"),
        R.string.scan_permission_label_location
    ),
    PHONE_STATE(
        Manifest.permission.READ_PHONE_STATE,
        listOf("N-04"),
        R.string.scan_permission_label_phone_state
    ),
    ACTIVITY_RECOGNITION(
        Manifest.permission.ACTIVITY_RECOGNITION,
        listOf("SN-08", "SN-09"),
        R.string.scan_permission_label_activity_recognition,
        minSdk = net.neuralcheck.hardware.sensor.SensorTests.ACTIVITY_RECOGNITION_MIN_SDK
    );

    companion object {
        /** The exact bundle requested together by the post-login gate, for THIS device's API level. */
        val ALL: List<ScanRuntimePermission>
            get() = forSdk(android.os.Build.VERSION.SDK_INT)

        /**
         * The bundle for a given API level: every permission whose [minSdk] the
         * level reaches (fieldsVersion 68, S-18 — "conditional on API >= 29":
         * ACTIVITY_RECOGNITION does not exist before it, so it is neither
         * requested nor listed there).
         */
        fun forSdk(sdkInt: Int): List<ScanRuntimePermission> = entries.filter { sdkInt >= it.minSdk }
    }
}


/**
 * Pure mapping from "these permissions were denied" to "these test IDs will
 * be auto-recorded NOT_TESTED" — no Context, no Android framework calls
 * beyond the plain string/int constants above, so it is unit-testable as
 * plain JVM code (see ScanRuntimePermissionTest).
 */
fun skippedTestIdsFor(denied: Collection<ScanRuntimePermission>): List<String> =
    denied.flatMap { it.autoNotTestedIds }.distinct()
