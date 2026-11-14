package net.neuralcheck.files

import net.neuralcheck.data.local.files.DataQuality
import net.neuralcheck.data.local.files.V2_VARIABLE_DEFINITIONS
import net.neuralcheck.data.local.files.VerificationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 66 — additive fields default to their documented "not
 * measured" sentinels (so ≤65 parsers and pre-66 footers stay consistent),
 * and the variables[] source strings describe the new v[9]/v[10] semantics.
 */
class FieldsVersion66FieldsTest {

    @Test
    fun `verification summary additive fields default to not-measured sentinels`() {
        val v = VerificationSummary()
        assertEquals(0, v.gpuFenceTimeoutCount)
        assertEquals(-1L, v.storageBytesWritten)
        assertEquals(-1L, v.storageWriteDurationMs)
    }

    @Test
    fun `verification summary carries the storage write extent when measured`() {
        val v = VerificationSummary(
            storageBytesWritten = 4L * 1024 * 1024 * 1024,
            storageWriteDurationMs = 5_400L,
            gpuFenceTimeoutCount = 3
        )
        assertEquals(4L * 1024 * 1024 * 1024, v.storageBytesWritten)
        assertEquals(5_400L, v.storageWriteDurationMs)
        assertEquals(3, v.gpuFenceTimeoutCount)
    }

    @Test
    fun `data quality provenance fields default to unknown`() {
        val q = DataQuality()
        assertEquals("unknown", q.cpuUsageSource)
        assertEquals(-1, q.cpuUsageFallbackFromSample)
    }

    @Test
    fun `variable definitions describe the live v9 series and the v10 window`() {
        val v9 = V2_VARIABLE_DEFINITIONS.first { it.index == 9 }
        val v10 = V2_VARIABLE_DEFINITIONS.first { it.index == 10 }
        assertEquals("memory_bandwidth_mbps", v9.name)
        assertTrue("v9 source must describe a live series: ${v9.source}", v9.source.contains("live"))
        assertEquals("gpu_fps", v10.name)
        // fieldsVersion 68 REPLACES the old assertion that v10's source contains
        // "sliding window": the declaration now says "rate over the last 4 sampler
        // queries" and where the window is cleared — pinned by V2DeclarationsTest.
        assertTrue("v10 source must describe the window: ${v10.source}", v10.source.contains("last 4 sampler queries"))
        // The "exactly 11 channels" pin that stood here is replaced by
        // V2BaseLayoutTest (fieldsVersion 68: the V2 layout is the frozen
        // base v[0..10] plus per-device channels, so its total width is no
        // longer a constant; what stays frozen is the name at each base index).
    }
}
