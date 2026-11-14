package net.neuralcheck.files

import net.neuralcheck.data.local.files.CriticalDefectOutcome
import net.neuralcheck.data.local.files.GpuGemmOutcome
import net.neuralcheck.data.local.files.TransitionalAggregates
import net.neuralcheck.data.local.files.VerificationStates
import net.neuralcheck.data.local.files.VerificationSummary
import net.neuralcheck.data.local.files.WorkersPinOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * fieldsVersion 68 (ت-18 rows 1-2, S-23, N-11, S-31, N-23): `verificationResults`
 * stops reading absence as success. The derivation is tested as a pure function;
 * the wire shape through the real writer -> reader -> builder -> Gson chain.
 */
class VerificationStatesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ───────────────────────── the derivation ─────────────────────────

    @Test
    fun `no attempt completed is not a pass - it is null with a not_run reason`() {
        val o = GpuGemmOutcome.derive(attempts = 0, anyFailed = false, governorCapped = false)
        assertNull("the old default was TRUE here", o.correct)
        assertEquals("not_run:no_attempt_completed", o.state)
    }

    @Test
    fun `governor capping is named as the reason when phases 4 and 5 were skipped`() {
        val o = GpuGemmOutcome.derive(attempts = 0, anyFailed = false, governorCapped = true)
        assertNull(o.correct)
        assertEquals("not_run:governor_capped", o.state)
    }

    @Test
    fun `completed attempts without a failure are passed, with a failure are failed`() {
        assertEquals(GpuGemmOutcome(true, "passed"), GpuGemmOutcome.derive(6, anyFailed = false, governorCapped = false))
        assertEquals(GpuGemmOutcome(false, "failed"), GpuGemmOutcome.derive(6, anyFailed = true, governorCapped = false))
        assertEquals(
            "a failure after capping cannot be, but the state follows the attempts, not the flag",
            GpuGemmOutcome(false, "failed"), GpuGemmOutcome.derive(1, anyFailed = true, governorCapped = true)
        )
    }

    @Test
    fun `critical defect is null while its input did not run and names that input`() {
        assertEquals(CriticalDefectOutcome(null, listOf("gpuComputeGemm")), CriticalDefectOutcome.derive(null))
        assertEquals(CriticalDefectOutcome(false, emptyList()), CriticalDefectOutcome.derive(true))
        assertEquals(CriticalDefectOutcome(true, emptyList()), CriticalDefectOutcome.derive(false))
    }

    @Test
    fun `a default VerificationSummary claims nothing ran - never a pass`() {
        val v = VerificationSummary()
        assertNull(v.gpuComputeGemmCorrect)
        assertEquals("not_run:no_v2_result", v.gpuComputeGemmState)
        assertNull(v.criticalDefectDetected)
        assertEquals(listOf("gpuComputeGemm"), v.criticalDefectInputsNotRun)
    }

    // ───────────────────────── storage verification (ت-18 row 3, N-09, T-08) ─────────────────────────

    @Test
    fun `a default summary has no storage error counts and says nothing ran`() {
        val v = VerificationSummary()
        assertNull("was the literal 0", v.storageWriteVerifyErrors)
        assertNull(v.storageReadVerifyErrors)
        assertEquals("not_run:no_v2_result", v.storageVerifyState)
    }

    @Test
    fun `storage verification that did not run is absent counts plus a state - and no SLC key at all`() {
        val vr = wireOf(
            VerificationSummary(storageVerifyState = "not_run:no_time_left")
        ).wireFooter.getAsJsonObject("verificationResults")

        assertFalse("0 here read as 'verified, no errors'", vr.has("storageWriteVerifyErrors"))
        assertFalse(vr.has("storageReadVerifyErrors"))
        assertEquals("not_run:no_time_left", vr.get("storageVerifyState").asString)
        assertFalse("T-08: the literal-0 SLC size is deleted", vr.has("storageSlcCacheSizeMb"))
    }

    @Test
    fun `storage verification that ran forwards its counts - a measured zero stays a zero`() {
        val vr = wireOf(
            VerificationSummary(storageWriteVerifyErrors = 0, storageReadVerifyErrors = 3, storageVerifyState = "ran")
        ).wireFooter.getAsJsonObject("verificationResults")

        assertEquals(0, vr.get("storageWriteVerifyErrors").asInt)
        assertEquals(3, vr.get("storageReadVerifyErrors").asInt)
        assertEquals("ran", vr.get("storageVerifyState").asString)
    }

    // ───────────────────────── workersPinState (ت-13, N-12) ─────────────────────────

    @Test
    fun `pin state names the AI workload path when nothing tried to pin`() {
        assertEquals(
            "not_attempted:ai_workload_path",
            WorkersPinOutcome.stateOf(attempted = false, aiWorkloadPathSelected = true)
        )
        assertEquals(
            "attempted",
            WorkersPinOutcome.stateOf(attempted = true, aiWorkloadPathSelected = true)
        )
        assertEquals(
            "not_attempted:cpu_phases_not_run",
            WorkersPinOutcome.stateOf(attempted = false, aiWorkloadPathSelected = false)
        )
    }

    @Test
    fun `a native result too short for the pin tail is unreadable - not attempted-with-zeros`() {
        assertEquals(
            "pin_result_unreadable:size=0",
            WorkersPinOutcome.stateOf(attempted = false, aiWorkloadPathSelected = false, unreadableResultSize = 0)
        )
        assertEquals(
            "a complete result elsewhere wins: its counters are real",
            "attempted",
            WorkersPinOutcome.stateOf(attempted = true, aiWorkloadPathSelected = false, unreadableResultSize = 0)
        )
    }

    @Test
    fun `a default summary has no pin counters and does not claim an attempt`() {
        val v = VerificationSummary()
        assertNull(v.workersPinnedBig)
        assertNull(v.workersPinFailedLittle)
        assertEquals("not_attempted:no_v2_result", v.workersPinState)
    }

    @Test
    fun `pin counters that were never produced are absent keys on the wire - not zeros`() {
        val vr = wireOf(
            VerificationSummary(workersPinState = "not_attempted:ai_workload_path")
        ).wireFooter.getAsJsonObject("verificationResults")

        for (key in listOf(
            "workersPinnedBig", "workersPinnedMid", "workersPinnedLittle",
            "workersPinFailedBig", "workersPinFailedMid", "workersPinFailedLittle"
        )) {
            assertFalse("$key must be absent, 0 would read as 'tried, none failed'", vr.has(key))
        }
        assertEquals("not_attempted:ai_workload_path", vr.get("workersPinState").asString)
    }

    @Test
    fun `pin counters of a real attempt are forwarded with their zeros`() {
        val vr = wireOf(
            VerificationSummary(
                workersPinnedBig = 2, workersPinnedMid = 3, workersPinnedLittle = 0,
                workersPinFailedBig = 0, workersPinFailedMid = 0, workersPinFailedLittle = 4,
                workersPinState = "attempted"
            )
        ).wireFooter.getAsJsonObject("verificationResults")

        assertEquals(2, vr.get("workersPinnedBig").asInt)
        assertEquals("a real zero stays a zero", 0, vr.get("workersPinnedLittle").asInt)
        assertEquals(4, vr.get("workersPinFailedLittle").asInt)
        assertEquals("attempted", vr.get("workersPinState").asString)
    }

    @Test
    fun `the transitional aggregate list has no duplicates and names the verdict aggregate`() {
        val names = TransitionalAggregates.NAMES
        assertEquals(names.size, names.toSet().size)
        assertTrue("verificationResults.criticalDefectDetected" in names)
        assertTrue("thermalHeadroomAvg" in names && "thermalHeadroomPeak" in names && "thermalStatusMax" in names)
        assertTrue("batteryHealth.voltageStabilityMv" in names)
    }

    // ───────────────────────── the wire ─────────────────────────

    private fun wireOf(v: VerificationSummary) = FooterRoundTrip(tmp.newFolder()).let { rt ->
        rt.run(rt.baseFooter().copy(verificationResults = v, transitionalAggregates = TransitionalAggregates.NAMES))
    }

    @Test
    fun `a GEMM that did not run is an absent key plus its state - on the wire and in the file`() {
        val notRun = VerificationSummary(
            gpuComputeGemmCorrect = null,
            gpuComputeGemmState = VerificationStates.notRun("governor_capped"),
            criticalDefectDetected = null,
            criticalDefectInputsNotRun = listOf("gpuComputeGemm")
        )
        val r = wireOf(notRun)

        // file
        assertNull(r.data.footer.verificationResults.gpuComputeGemmCorrect)
        assertEquals("not_run:governor_capped", r.data.footer.verificationResults.gpuComputeGemmState)
        // payload: no key that could be read as a pass or a no-defect verdict
        val vr = r.wireFooter.getAsJsonObject("verificationResults")
        assertFalse(vr.has("gpuComputeGemmCorrect"))
        assertFalse(vr.has("criticalDefectDetected"))
        assertEquals("not_run:governor_capped", vr.get("gpuComputeGemmState").asString)
        assertEquals("[\"gpuComputeGemm\"]", vr.get("criticalDefectInputsNotRun").toString())
    }

    @Test
    fun `a GEMM that ran carries its boolean and an empty not-run list`() {
        val passed = wireOf(
            VerificationSummary(
                gpuComputeGemmCorrect = true, gpuComputeGemmState = "passed",
                criticalDefectDetected = false, criticalDefectInputsNotRun = emptyList()
            )
        ).wireFooter.getAsJsonObject("verificationResults")
        assertTrue(passed.get("gpuComputeGemmCorrect").asBoolean)
        assertEquals("passed", passed.get("gpuComputeGemmState").asString)
        assertFalse(passed.get("criticalDefectDetected").asBoolean)
        assertEquals("[]", passed.get("criticalDefectInputsNotRun").toString())

        val failed = wireOf(
            VerificationSummary(
                gpuComputeGemmCorrect = false, gpuComputeGemmState = "failed",
                criticalDefectDetected = true, criticalDefectInputsNotRun = emptyList()
            )
        ).wireFooter.getAsJsonObject("verificationResults")
        assertFalse(failed.get("gpuComputeGemmCorrect").asBoolean)
        assertEquals("failed", failed.get("gpuComputeGemmState").asString)
        assertTrue(failed.get("criticalDefectDetected").asBoolean)
    }

    @Test
    fun `the transitional aggregates are declared in the file and on the wire`() {
        val r = wireOf(VerificationSummary())
        assertEquals(TransitionalAggregates.NAMES, r.data.footer.transitionalAggregates)
        assertEquals(TransitionalAggregates.NAMES, r.fingerprint.timeSeries.footer.transitionalAggregates)
        val wire = r.wireFooter.getAsJsonArray("transitionalAggregates").map { it.asString }
        assertEquals(TransitionalAggregates.NAMES, wire)
        assertTrue("verificationResults.criticalDefectDetected" in wire)
    }
}
