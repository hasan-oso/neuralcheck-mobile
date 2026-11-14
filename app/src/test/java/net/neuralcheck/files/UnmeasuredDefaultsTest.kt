package net.neuralcheck.files

import net.neuralcheck.data.local.files.UnmeasuredDefaults
import net.neuralcheck.data.local.files.VerificationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * fieldsVersion 68 (T-18 rows 8, 12, 13): constants that remain by design are
 * DECLARED, not shipped as measurements; "no delegate error" is an absent key,
 * not an empty string.
 */
class UnmeasuredDefaultsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the V2 path declares the two fv65 constants and the V5 dataQuality literals`() {
        assertEquals(
            listOf(
                "deviceTierMeasured", "intensityFactor",
                "dataQuality.cpuTempCoveragePct", "dataQuality.gpuTempCoveragePct",
                "dataQuality.skinTempCoveragePct", "dataQuality.cpuTempPrimarySource"
            ),
            UnmeasuredDefaults.forScan(v2Ran = true)
        )
    }

    @Test
    fun `the V1 path also declares tempRiseRateInProbe - no probe runs there`() {
        val v1 = UnmeasuredDefaults.forScan(v2Ran = false)
        assertTrue("tempRiseRateInProbe" in v1)
        assertEquals(v1.size, v1.toSet().size)
        assertEquals(UnmeasuredDefaults.forScan(true).size + 1, v1.size)
    }

    @Test
    fun `what became an explicit state is not declared as a constant`() {
        val names = UnmeasuredDefaults.forScan(true)
        for (stateful in listOf(
            "storageWriteVerifyErrors", "storageReadVerifyErrors", "storageSlcCacheSizeMb",
            "workersPinnedBig", "gpuComputeGemmCorrect", "criticalDefectDetected", "scanStillness", "preScanWarnings"
        )) {
            assertFalse("$stateful says its own state now", names.any { it.endsWith(stateful) })
        }
    }

    @Test
    fun `the declaration reaches the payload`() {
        val rt = FooterRoundTrip(tmp.newFolder())
        val declared = UnmeasuredDefaults.forScan(v2Ran = true)
        val r = rt.run(rt.baseFooter().copy(unmeasuredDefaults = declared))

        assertEquals(declared, r.data.footer.unmeasuredDefaults)
        assertEquals(declared, r.wireFooter.getAsJsonArray("unmeasuredDefaults").map { it.asString })
    }

    @Test
    fun `no delegate error is an absent key - not an empty string`() {
        val rt = FooterRoundTrip(tmp.newFolder())

        val none = rt.run(
            rt.baseFooter().copy(verificationResults = VerificationSummary(aiActiveDelegate = "NNAPI", aiDelegateInitError = null))
        ).wireFooter.getAsJsonObject("verificationResults")
        assertFalse("an empty string would be a value for 'no error'", none.has("aiDelegateInitError"))
        assertEquals("NNAPI", none.get("aiActiveDelegate").asString)

        val stub = rt.run(
            rt.baseFooter().copy(
                verificationResults = VerificationSummary(
                    aiActiveDelegate = "STUB", aiDelegateInitError = "UnsatisfiedLinkError: libtensorflowlite_jni.so not found"
                )
            )
        ).wireFooter.getAsJsonObject("verificationResults")
        assertEquals("UnsatisfiedLinkError: libtensorflowlite_jni.so not found", stub.get("aiDelegateInitError").asString)
    }
}
