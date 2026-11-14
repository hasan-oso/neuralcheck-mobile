package net.neuralcheck.hardware.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import javax.inject.Inject

/** Camera & biometric tests CM-01 to CM-11. */
class CameraTests @Inject constructor(private val context: Context) {
    fun runAll(): List<TestResult> {
        // CAMERA_SERVICE returning null is rare but documented for restricted
        // profiles / Knox policies; the safe cast keeps Hilt graph build alive.
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cm == null) {
            Log.w("NeuralCheck", "CameraTests: CAMERA_SERVICE unavailable")
            return cameraUnavailableResults()
        }
        // Android 14+ on Samsung One UI 6/7 throws SecurityException from
        // CameraManager.getCameraIdList()/getCameraCharacteristics() when the
        // CAMERA permission has not been granted to the calling app — even
        // for metadata-only access. Skip the whole category instead of
        // crashing on the cameraIdList read below.
        val hasCameraPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PermissionChecker.PERMISSION_GRANTED
        if (!hasCameraPerm) {
            Log.w("NeuralCheck", "CameraTests: CAMERA permission not granted; returning NOT_TESTED")
            return cameraPermissionDeniedResults()
        }
        val cameraIds = try { cm.cameraIdList } catch (e: Exception) {
            Log.w("NeuralCheck", "cameraIdList failed: ${e.javaClass.simpleName}: ${e.message}")
            emptyArray()
        }
        val backId = cameraIds.firstOrNull { isFacing(cm, it, CameraCharacteristics.LENS_FACING_BACK) }
        val frontId = cameraIds.firstOrNull { isFacing(cm, it, CameraCharacteristics.LENS_FACING_FRONT) }
        val cm01NameAr = context.getString(net.neuralcheck.R.string.test_cm01_ar)
        val cm01NameTr = context.getString(net.neuralcheck.R.string.test_cm01_tr)
        val cm02NameAr = context.getString(net.neuralcheck.R.string.test_cm02_ar)
        val cm02NameTr = context.getString(net.neuralcheck.R.string.test_cm02_tr)
        val cm06NameAr = context.getString(net.neuralcheck.R.string.test_cm06_ar)
        val cm06NameTr = context.getString(net.neuralcheck.R.string.test_cm06_tr)
        val cm07NameAr = context.getString(net.neuralcheck.R.string.test_cm07_ar)
        val cm07NameTr = context.getString(net.neuralcheck.R.string.test_cm07_tr)
        // Test IDs follow ADR 3.11 exactly:
        //   CM-01 rear cam, CM-02 front cam, CM-03 extra cams,
        //   CM-04 flash,    CM-05 autofocus, CM-06/07 resolutions,
        //   CM-08 fingerprint, CM-10 face detection, CM-11 vibrator.
        // CM-09 (fingerprint response speed) is a manual test and not
        // returned here.
        val extraIds = cameraIds.filter { it != backId && it != frontId }
        return listOf(
            testCamera("CM-01", cm01NameAr, cm01NameTr, backId),
            testCamera("CM-02", cm02NameAr, cm02NameTr, frontId),
            testExtraCameras(extraIds, cm),
            testFlash(backId, cm),
            testAutofocus(backId, cm),
            testResolution("CM-06", cm06NameAr, cm06NameTr, backId, cm),
            testResolution("CM-07", cm07NameAr, cm07NameTr, frontId, cm),
            testFingerprint(),
            testFaceDetection(),
            testVibrator()
        )
    }

    private fun isFacing(cm: CameraManager, id: String, facing: Int): Boolean =
        try { cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing } catch (e: Exception) { false }

    private fun testCamera(tid: String, nameAr: String, nameTr: String, cameraId: String?): TestResult {
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_access_ar)
        val availableAr = context.getString(net.neuralcheck.R.string.value_available_ar)
        if (cameraId == null) return TestResult(tid, nameAr, nameTr, "camera", TestStatus.FAIL, null, unavailableAr, "", null)
        return TestResult(tid, nameAr, nameTr, "camera", TestStatus.PASS, null, "$availableAr (ID: $cameraId)", "", null)
    }

    // CM-03: count and validate extra (non-primary) cameras such as ultra-wide,
    // telephoto, depth, macro (ADR 3.11 CM-03).
    private fun testExtraCameras(extraIds: List<String>, cm: CameraManager): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_cm03_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_cm03_tr)
        if (extraIds.isEmpty()) {
            return TestResult("CM-03", nameAr, nameTr, "camera", TestStatus.UNAVAILABLE, null, "0", "", null)
        }
        val working = extraIds.count { id ->
            try { cm.getCameraCharacteristics(id); true } catch (_: Exception) { false }
        }
        val status = when {
            working == extraIds.size -> TestStatus.PASS
            working > 0 -> TestStatus.WARNING
            else -> TestStatus.FAIL
        }
        return TestResult("CM-03", nameAr, nameTr, "camera", status, null, "$working/${extraIds.size}", "", null)
    }

    // CM-04: flash — hasFlash == true means the test passes (ADR 3.11 CM-04).
    private fun testFlash(cameraId: String?, cm: CameraManager): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_cm04_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_cm04_tr)
        if (cameraId == null) return TestResult("CM-04", nameAr, nameTr, "camera", TestStatus.UNAVAILABLE, null, "N/A", "", null)
        val hasFlash = try { cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false } catch (e: Exception) { false }
        val availableAr = context.getString(net.neuralcheck.R.string.value_available_ar)
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        return TestResult("CM-04", nameAr, nameTr, "camera",
            if (hasFlash) TestStatus.PASS else TestStatus.UNAVAILABLE, null,
            if (hasFlash) availableAr else unavailableAr, "", null)
    }

    // CM-05: autofocus (ADR 3.11 CM-05).
    private fun testAutofocus(cameraId: String?, cm: CameraManager): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_cm05_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_cm05_tr)
        if (cameraId == null) return TestResult("CM-05", nameAr, nameTr, "camera", TestStatus.UNAVAILABLE, null, "N/A", "", null)
        val modes = try { cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) } catch (e: Exception) { null }
        val hasAF = modes?.any { it != CameraMetadata.CONTROL_AF_MODE_OFF } ?: false
        val supportedAr = context.getString(net.neuralcheck.R.string.value_supported_ar)
        val unsupportedAr = context.getString(net.neuralcheck.R.string.value_unsupported_ar)
        return TestResult("CM-05", nameAr, nameTr, "camera",
            if (hasAF) TestStatus.PASS else TestStatus.WARNING, null,
            if (hasAF) supportedAr else unsupportedAr, "", null)
    }

    private fun testResolution(tid: String, nameAr: String, nameTr: String, cameraId: String?, cm: CameraManager): TestResult {
        if (cameraId == null) return TestResult(tid, nameAr, nameTr, "camera", TestStatus.UNAVAILABLE, null, "N/A", "", null)
        val mp = try {
            val configs = cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = configs?.getOutputSizes(android.graphics.ImageFormat.JPEG)
            val maxSize = sizes?.maxByOrNull { it.width * it.height }
            maxSize?.let { (it.width.toLong() * it.height) / 1_000_000f } ?: 0f
        } catch (e: Exception) { 0f }
        return TestResult(tid, nameAr, nameTr, "camera", if (mp > 0) TestStatus.PASS else TestStatus.WARNING, null, String.format(java.util.Locale.US, "%.1f", mp), "MP", null)
    }

    private fun testFingerprint(): TestResult {
        val bm = BiometricManager.from(context)
        val can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val status = when(can) { BiometricManager.BIOMETRIC_SUCCESS -> TestStatus.PASS; BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> TestStatus.WARNING; else -> TestStatus.UNAVAILABLE }
        val nameAr = context.getString(net.neuralcheck.R.string.test_cm08_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_cm08_tr)
        val registeredAr = context.getString(net.neuralcheck.R.string.value_registered_ar)
        val notRegisteredAr = context.getString(net.neuralcheck.R.string.value_not_registered_ar)
        val unsupportedAr = context.getString(net.neuralcheck.R.string.value_unsupported_ar)
        return TestResult("CM-08", nameAr, nameTr, "camera", status, null,
            when(can) { BiometricManager.BIOMETRIC_SUCCESS -> registeredAr; BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> notRegisteredAr; else -> unsupportedAr }, "", null)
    }

    // CM-10: face detection support (ADR 3.11 CM-10).
    private fun testFaceDetection(): TestResult {
        val bm = BiometricManager.from(context)
        val canWeak = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val nameAr = context.getString(net.neuralcheck.R.string.test_cm09_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_cm09_tr)
        val supportedAr = context.getString(net.neuralcheck.R.string.value_supported_ar)
        val unsupportedAr = context.getString(net.neuralcheck.R.string.value_unsupported_ar)
        return TestResult("CM-10", nameAr, nameTr, "camera",
            if (canWeak == BiometricManager.BIOMETRIC_SUCCESS) TestStatus.PASS else TestStatus.UNAVAILABLE, null,
            if (canWeak == BiometricManager.BIOMETRIC_SUCCESS) supportedAr else unsupportedAr, "", null)
    }

    // CM-11: vibration motor presence (ADR 3.11 CM-11). We only check for the
    // hardware because programmatic verification of actual vibration is not
    // possible on Android; a manual follow-up covers the perceptual check.
    @Suppress("DEPRECATION")
    private fun testVibrator(): TestResult {
        val nameAr = context.getString(net.neuralcheck.R.string.test_cm10_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_cm10_tr)
        val availableAr = context.getString(net.neuralcheck.R.string.value_available_ar)
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        val vibrator: Vibrator? = try {
            if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (t: Throwable) {
            Log.w("NeuralCheck", "Vibrator service unavailable: ${t.message}")
            null
        }
        if (vibrator == null) {
            return TestResult("CM-11", nameAr, nameTr, "camera",
                TestStatus.UNAVAILABLE, null, unavailableAr, "", null)
        }
        val has = try { vibrator.hasVibrator() } catch (_: Exception) { false }
        return TestResult("CM-11", nameAr, nameTr, "camera",
            if (has) TestStatus.PASS else TestStatus.FAIL, null,
            if (has) availableAr else unavailableAr, "", null)
    }

    /**
     * Fallback returned when CAMERA_SERVICE itself is unreachable. Every test
     * shows up as UNAVAILABLE so the report screen still renders the section
     * (rather than silently dropping every camera/biometric test).
     */
    private fun cameraUnavailableResults(): List<TestResult> {
        val unavailableAr = context.getString(net.neuralcheck.R.string.value_unavailable_ar)
        return listOf("CM-01","CM-02","CM-03","CM-04","CM-05","CM-06","CM-07","CM-08","CM-10","CM-11")
            .map { tid ->
                val arRes = arNameRes(tid)
                val trRes = trNameRes(tid)
                TestResult(
                    tid,
                    runCatching { context.getString(arRes) }.getOrDefault(tid),
                    runCatching { context.getString(trRes) }.getOrDefault(tid),
                    "camera", TestStatus.UNAVAILABLE, null, unavailableAr, "", null
                )
            }
    }

    /**
     * Fallback returned when the user has not granted CAMERA permission.
     * Returns NOT_TESTED (not FAIL) so the score engine doesn't penalise the
     * device for a permission denial that's outside the device's control.
     */
    private fun cameraPermissionDeniedResults(): List<TestResult> {
        val noPermAr = runCatching {
            context.getString(net.neuralcheck.R.string.value_unavailable_access_ar)
        }.getOrDefault("permission_denied")
        return listOf("CM-01","CM-02","CM-03","CM-04","CM-05","CM-06","CM-07","CM-08","CM-10","CM-11")
            .map { tid ->
                val arRes = arNameRes(tid)
                val trRes = trNameRes(tid)
                TestResult(
                    tid,
                    runCatching { context.getString(arRes) }.getOrDefault(tid),
                    runCatching { context.getString(trRes) }.getOrDefault(tid),
                    "camera", TestStatus.NOT_TESTED, null, noPermAr, "", null
                )
            }
    }

    private fun arNameRes(tid: String): Int = when (tid) {
        "CM-01" -> net.neuralcheck.R.string.test_cm01_ar
        "CM-02" -> net.neuralcheck.R.string.test_cm02_ar
        "CM-03" -> net.neuralcheck.R.string.test_cm03_ar
        "CM-04" -> net.neuralcheck.R.string.test_cm04_ar
        "CM-05" -> net.neuralcheck.R.string.test_cm05_ar
        "CM-06" -> net.neuralcheck.R.string.test_cm06_ar
        "CM-07" -> net.neuralcheck.R.string.test_cm07_ar
        "CM-08" -> net.neuralcheck.R.string.test_cm08_ar
        "CM-10" -> net.neuralcheck.R.string.test_cm09_ar
        "CM-11" -> net.neuralcheck.R.string.test_cm10_ar
        else -> net.neuralcheck.R.string.test_cm01_ar
    }

    private fun trNameRes(tid: String): Int = when (tid) {
        "CM-01" -> net.neuralcheck.R.string.test_cm01_tr
        "CM-02" -> net.neuralcheck.R.string.test_cm02_tr
        "CM-03" -> net.neuralcheck.R.string.test_cm03_tr
        "CM-04" -> net.neuralcheck.R.string.test_cm04_tr
        "CM-05" -> net.neuralcheck.R.string.test_cm05_tr
        "CM-06" -> net.neuralcheck.R.string.test_cm06_tr
        "CM-07" -> net.neuralcheck.R.string.test_cm07_tr
        "CM-08" -> net.neuralcheck.R.string.test_cm08_tr
        "CM-10" -> net.neuralcheck.R.string.test_cm09_tr
        "CM-11" -> net.neuralcheck.R.string.test_cm10_tr
        else -> net.neuralcheck.R.string.test_cm01_tr
    }
}
