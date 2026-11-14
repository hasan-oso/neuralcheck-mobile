package net.neuralcheck.hardware.gpu

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.GLES20
import net.neuralcheck.domain.model.TestResult
import net.neuralcheck.domain.model.TestStatus
import javax.inject.Inject

/**
 * GPU functional tests G-01 through G-05.
 */
class GpuTests @Inject constructor(private val context: Context) {

    fun runStaticTests(): List<TestResult> = listOf(testGpuInfo(), testVulkanSupport())

    // G-01: GPU Info (create temp EGL context)
    private fun testGpuInfo(): TestResult {
        var renderer = "unknown"
        var vendor = "unknown"
        var version = "unknown"
        try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val versionArr = IntArray(2)
            EGL14.eglInitialize(display, versionArr, 0, versionArr, 1)
            val configAttribs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfig = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfig, 0)
            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
            EGL14.eglMakeCurrent(display, surface, surface, eglContext)
            renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"
            vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "unknown"
            version = GLES20.glGetString(GLES20.GL_VERSION) ?: "unknown"
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglTerminate(display)
        } catch (e: Exception) { /* fallback values already set */ }
        val nameAr = context.getString(net.neuralcheck.R.string.test_g01_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_g01_tr)
        // Surface vendor + renderer + version for diagnostics. The renderer
        // string already contains the model (e.g. "Mali-G76 MP12"); vendor
        // ("ARM", "Qualcomm", etc.) is the missing piece for the report.
        val rawValue = "$vendor — $renderer ($version)"
        return TestResult("G-01", nameAr, nameTr, "gpu", TestStatus.PASS, null, rawValue, "", null)
    }

    // G-02: Vulkan Support
    private fun testVulkanSupport(): TestResult {
        val hasVulkan = context.packageManager.systemAvailableFeatures.any { it.name == "android.hardware.vulkan.version" }
        val status = if (hasVulkan) TestStatus.PASS else TestStatus.WARNING
        val nameAr = context.getString(net.neuralcheck.R.string.test_g02_ar)
        val nameTr = context.getString(net.neuralcheck.R.string.test_g02_tr)
        val valueAr = if (hasVulkan) context.getString(net.neuralcheck.R.string.value_supported_ar) else context.getString(net.neuralcheck.R.string.value_unsupported_ar)
        return TestResult("G-02", nameAr, nameTr, "gpu", status, null, valueAr, "", null)
    }

    // G-03 (FPS drop), G-04 (GPU thermal throttle), G-05 (min sustained FPS),
    // and the gpuMeasurementFailed() gate they shared, were removed here
    // (G2/P2.3). All three were on-device aggregates over the whole scan's
    // GPU FPS history — peak-picking (max/min FPS) plus a derived ratio —
    // which is exactly the irreversible transformation the raw-in/
    // transform-on-server rule forbids doing in the app. The app now sends
    // only the raw v[10] gpu_fps time series plus footer.phaseBoundaries
    // (added in this same change) so the server can compute G-03/G-04/G-05
    // itself, scoped to the GPU_SUSTAINED (phaseId 4) / COMBINED_PEAK
    // (phaseId 5) window. See FunctionalTestRunner.computeTimeSeriesTests
    // (the sole caller, until this change) and
    // net.neuralcheck.data.local.files.PhaseBoundary.
}
