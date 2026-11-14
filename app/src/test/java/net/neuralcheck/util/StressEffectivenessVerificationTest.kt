package net.neuralcheck.util

import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.VerificationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 68 (rule 22): the verification fields became nullable — a check
 * that did not run is `null`, not a default pass. C3 (verification_integrity)
 * keeps its meaning "no POSITIVE evidence of a defect": a not-run check neither
 * fails nor vouches, so the saturation grade of a scan whose GEMM did not run
 * is unchanged; only a check that RAN and failed fails C3.
 */
class StressEffectivenessVerificationTest {

    /** Phase-3 samples with a usable v[5] / v[6] / v[7] — just enough for compute() to evaluate C3. */
    private fun samples() = (0 until 12).map { i ->
        val v = FloatArray(11)
        v[0] = (i * 250).toFloat()
        v[5] = 2_800_000f
        v[6] = 80f
        v[7] = 5_000f
        TimeSeriesSample(ts = 1_000L + i, phase = 3, v = v)
    }

    private fun c3(v: VerificationSummary?) = StressEffectivenessCalculator
        .compute(samples = samples(), buildHardware = "test", verification = v)
        .satC3Passed

    private val ran = VerificationSummary(
        gpuComputeGemmCorrect = true, gpuComputeGemmState = "passed",
        criticalDefectDetected = false, criticalDefectInputsNotRun = emptyList(),
        aiActiveDelegate = "NNAPI", aiInferenceIterationsTotal = 100L
    )

    @Test
    fun `a GEMM that did not run does not fail C3 - and is not counted as a defect`() {
        val notRun = ran.copy(
            gpuComputeGemmCorrect = null, gpuComputeGemmState = "not_run:governor_capped",
            criticalDefectDetected = null, criticalDefectInputsNotRun = listOf("gpuComputeGemm")
        )
        assertTrue(c3(notRun))
    }

    @Test
    fun `a GEMM that ran and failed fails C3`() {
        assertFalse(c3(ran.copy(gpuComputeGemmCorrect = false, gpuComputeGemmState = "failed")))
    }

    @Test
    fun `a GEMM that passed keeps C3`() {
        assertTrue(c3(ran))
    }

    @Test
    fun `no verification at all (V1) keeps C3 as it was`() {
        assertTrue(c3(null))
    }

    @Test
    fun `storage error counts that were never produced do not fail C3 - real ones do`() {
        assertTrue(c3(ran.copy(storageWriteVerifyErrors = null, storageReadVerifyErrors = null)))
        assertTrue("a measured zero is no defect", c3(ran.copy(storageWriteVerifyErrors = 0, storageReadVerifyErrors = 0, storageVerifyState = "ran")))
        assertFalse(c3(ran.copy(storageReadVerifyErrors = 2, storageWriteVerifyErrors = 0, storageVerifyState = "ran")))
    }

    // ── the load-applied gate reads pin counters as a POSITIVE signal only ──

    private fun grade(v: VerificationSummary) = StressEffectivenessCalculator
        .compute(samples = samples(), buildHardware = "test", verification = v)
        .satGrade

    private val stubNoLoad = ran.copy(aiActiveDelegate = "STUB", aiInferenceIterationsTotal = 0L)

    @Test
    fun `STUB with no pin attempt (null counters) still trips the load-not-applied gate`() {
        assertEquals("stress_not_applied", grade(stubNoLoad))
    }

    @Test
    fun `STUB with pinned NEON workers is a loaded CPU - the gate does not trip`() {
        val v = stubNoLoad.copy(
            workersPinnedBig = 2, workersPinnedMid = 0, workersPinnedLittle = 0,
            workersPinFailedBig = 0, workersPinFailedMid = 0, workersPinFailedLittle = 0,
            workersPinState = "attempted"
        )
        assertFalse("stress_not_applied" == grade(v))
    }

    @Test
    fun `the failure text lists storage errors only when a verification ran and found some`() {
        fun reason(v: VerificationSummary) = StressEffectivenessCalculator.composeSaturationReasons(
            conditionsMet = 3, c1Passed = true, c2Passed = true, c3Passed = false, c4Passed = true,
            maxPeakPower = 9000, pathA = true, pathB = false, pathC = false, freqDropPct = 14f,
            verification = v, c4Ratio = 0.9f, governorCappingDetected = false
        ).first!!

        assertTrue(reason(ran.copy(storageWriteVerifyErrors = 3, storageVerifyState = "ran")).contains("storage_write_errors=3"))
        assertFalse(reason(ran.copy(storageWriteVerifyErrors = null)).contains("storage_write_errors"))
        assertFalse("a measured zero is not listed", reason(ran.copy(storageWriteVerifyErrors = 0, storageVerifyState = "ran")).contains("storage_write_errors"))
    }

    @Test
    fun `the failure text names the GEMM only when it ran and failed`() {
        fun reason(v: VerificationSummary) = StressEffectivenessCalculator.composeSaturationReasons(
            conditionsMet = 3, c1Passed = true, c2Passed = true, c3Passed = false, c4Passed = true,
            maxPeakPower = 9000, pathA = true, pathB = false, pathC = false, freqDropPct = 14f,
            verification = v, c4Ratio = 0.9f, governorCappingDetected = false
        ).first!!

        assertTrue(reason(ran.copy(gpuComputeGemmCorrect = false)).contains("gpu_gemm_incorrect"))
        assertFalse(
            "a GEMM that never ran is not reported as incorrect",
            reason(ran.copy(gpuComputeGemmCorrect = null)).contains("gpu_gemm_incorrect")
        )
        assertEquals(
            "no known defect -> the text says so",
            true, reason(ran.copy(gpuComputeGemmCorrect = null)).contains("no_failures")
        )
    }
}
