package net.neuralcheck.service.inference

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Phase 1.6.A — Smoke test for [InferenceEngineImpl].
 *
 * The unit-test JVM has no native libtensorflowlite_jni.so on its
 * java.library.path, so the engine's reflection-based delegate
 * construction throws and falls into STUB mode. This test verifies the
 * STUB code path is fully wired:
 *
 *   - loadModel returns false (real interpreter unavailable)
 *   - getActiveDelegate is "STUB"
 *   - runInference produces a 1001-element output for the standard
 *     224×224×3 input
 *   - runInferenceLoop returns a positive iteration count
 *
 * The on-device path (NNAPI/GPU/CPU) is exercised by the instrumented
 * test that ships with the launch-debug APK and the full hardware-
 * validation procedure that Hasan runs after Stage F. This unit test's
 * job is to keep the wrapper code from regressing — it does NOT
 * pretend to validate real TFLite inference.
 */
class InferenceEngineSmokeTest {

    private lateinit var engine: InferenceEngineImpl

    @Before
    fun setUp() {
        // Mockito-Kotlin's mock() returns a stub Context whose
        // assets / resources / etc. all return null on access. Our
        // engine handles that path by routing to STUB — exactly what
        // we want this test to cover.
        val context: Context = mock()
        engine = InferenceEngineImpl(context)
    }

    @Test
    fun loadModel_reportsStubWhenNativeUnavailable() {
        // Mock context will fail to open the asset → engine drops to STUB.
        val ok = engine.loadModel("mobilenet_v3_large_224.tflite", Delegate.AUTO)
        assertEquals(
            "loadModel must return false when no real delegate is available",
            false, ok
        )
        assertEquals(
            "STUB mode must surface as activeDelegate=STUB",
            "STUB", engine.getActiveDelegate()
        )
    }

    @Test
    fun runInference_producesExpectedOutputSize() {
        engine.loadModel("mobilenet_v3_large_224.tflite", Delegate.AUTO)
        val input = FloatArray(InferenceEngineImpl.INPUT_SIZE) {
            // Cheap, deterministic gradient — anything in [0, 1] works,
            // STUB mode just hashes the bytes anyway.
            (it.toFloat() / InferenceEngineImpl.INPUT_SIZE)
        }
        val out = engine.runInference(input)
        assertNotNull("output must not be null", out)
        assertEquals(
            "STUB output is fixed at 1001 (ImageNet shape)",
            1001, out.size
        )
        // Sanity: outputs must not all be zero — STUB mode hashes the
        // input so values should be spread across [0, 1].
        val nonZero = out.count { it != 0f }
        assertTrue(
            "expected some non-zero outputs in STUB mode (got $nonZero)",
            nonZero > 0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun runInference_rejectsWrongInputSize() {
        engine.loadModel("mobilenet_v3_large_224.tflite", Delegate.AUTO)
        // 100 floats is the wrong size — must throw before reaching the
        // STUB path so caller bugs surface early instead of silently
        // running on garbage data.
        engine.runInference(FloatArray(100))
    }

    @Test
    fun runInferenceLoop_completesAtLeastOneIteration() {
        engine.loadModel("mobilenet_v3_large_224.tflite", Delegate.AUTO)
        val iters = engine.runInferenceLoop(durationMs = 60L)
        assertTrue(
            "60 ms window with ~10 ms per iteration must complete >=1 iteration (got $iters)",
            iters >= 1
        )
    }

    @Test
    fun getActiveDelegate_returnsKnownValue() {
        // Pre-loadModel: NONE. Post-loadModel in STUB env: STUB.
        assertEquals("NONE", engine.getActiveDelegate())
        engine.loadModel("mobilenet_v3_large_224.tflite", Delegate.AUTO)
        val delegate = engine.getActiveDelegate()
        assertTrue(
            "delegate must be one of NNAPI/GPU/CPU/STUB/NONE (got $delegate)",
            delegate in setOf("NNAPI", "GPU", "CPU", "STUB", "NONE")
        )
    }
}
