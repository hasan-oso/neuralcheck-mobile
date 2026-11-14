package net.neuralcheck.files

import net.neuralcheck.data.local.files.ChannelNames
import net.neuralcheck.data.local.files.THROUGHPUT_UNIT_MIB_PER_S
import net.neuralcheck.data.local.files.V2_VARIABLE_DEFINITIONS
import net.neuralcheck.data.local.files.VerificationSummary
import net.neuralcheck.service.stress.GpuFpsTracker
import net.neuralcheck.service.stress.v2.GpuLoadGeneratorV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

/**
 * fieldsVersion 68 (T-12, T-19, S-21, N-21; order of batch 3 part 2-a): what
 * `variables[]` DECLARES now matches the value, the throughput units are
 * declared as the units they are, the segments carry their unit, and the
 * GPU-fps window is cleared when the load stops.
 */
class V2DeclarationsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun def(name: String) = V2_VARIABLE_DEFINITIONS.first { it.name == name }

    // ───────────────────────── variables[] ─────────────────────────

    @Test
    fun `v0 declares what it holds - milliseconds since the scan started, not an absolute clock`() {
        val v0 = def(ChannelNames.TIMESTAMP_MS)
        assertEquals(0, v0.index)
        assertEquals("ms", v0.unit)
        assertEquals("SystemClock.elapsedRealtime() minus scan start (ms since scan start)", v0.source)
    }

    @Test
    fun `v8 and v9 are declared MiB per second - the unit they are`() {
        assertEquals("MiB/s", THROUGHPUT_UNIT_MIB_PER_S)
        assertEquals("MiB/s", def(ChannelNames.STORAGE_THROUGHPUT_MBPS).unit)
        assertEquals("MiB/s", def(ChannelNames.MEMORY_BANDWIDTH_MBPS).unit)
        // names and indices did not move (the unit is data, the name is the key)
        assertEquals(8, def(ChannelNames.STORAGE_THROUGHPUT_MBPS).index)
        assertEquals(9, def(ChannelNames.MEMORY_BANDWIDTH_MBPS).index)
        // and no other base channel was relabelled
        assertEquals(
            listOf("ms", "°C", "mV", "µA", "%", "kHz", "%", "mW", "MiB/s", "MiB/s", "fps"),
            V2_VARIABLE_DEFINITIONS.map { it.unit }
        )
    }

    // Rule 23: FieldsVersion66FieldsTest used to assert that v10's source contains
    // "sliding window" — the pre-68 declaration. It is replaced by this one on the new
    // declaration (and edited there to stop pinning the old words).
    @Test
    fun `gpu_fps declares its window, where it is cleared and where it is zero`() {
        val s = def(ChannelNames.GPU_FPS).source
        assertEquals(
            "OpenGL offscreen frame counter; rate over the last 4 sampler queries (about 1 s at 4 Hz); " +
                "window cleared at the start of phases 4 and 5 and at the end of phase 5; 0 outside phases 4 and 5",
            s
        )
    }

    // ───────────────────────── the segments' unit ─────────────────────────

    @Test
    fun `the storage segments carry their unit in verificationResults`() {
        assertEquals("MiB/s", VerificationSummary().storageThroughputSegmentsUnit)

        val rt = FooterRoundTrip(tmp.newFolder())
        val r = rt.run(
            rt.baseFooter().copy(
                verificationResults = VerificationSummary(
                    storageThroughputSegments = listOf(
                        net.neuralcheck.data.local.files.StorageThroughputSegment(268_435_456L, 612.5f)
                    )
                )
            )
        )
        val vr = r.wireFooter.getAsJsonObject("verificationResults")
        assertEquals("MiB/s", vr.get("storageThroughputSegmentsUnit").asString)
        assertEquals(612.5f, vr.getAsJsonArray("storageThroughputSegments")[0].asJsonObject.get("mbps").asFloat, 0f)
    }

    @Test
    fun `the variables the payload carries are the declarations above`() {
        val rt = FooterRoundTrip(tmp.newFolder())
        val r = rt.run(rt.baseFooter())
        // FooterRoundTrip writes V2 mode without a layout override: variables[] is the base list.
        val vars = r.wire.getAsJsonObject("timeSeries").getAsJsonArray("variables")
        assertEquals(def(ChannelNames.TIMESTAMP_MS).source, vars[0].asJsonObject.get("source").asString)
        assertEquals("MiB/s", vars[8].asJsonObject.get("unit").asString)
        assertEquals(def(ChannelNames.GPU_FPS).source, vars[10].asJsonObject.get("source").asString)
    }

    // ───────────────────────── the fps window at the end of phase 5 ─────────────────────────

    @Test
    fun `reset clears the fps tail - after the load stops the next values are zero, not the old window`() {
        val generator = mock<GpuLoadGeneratorV2> { on { getAndResetFrameCount() } doReturn 10 }
        val previous = GpuFpsTracker.gpuLoadGeneratorV2
        GpuFpsTracker.gpuLoadGeneratorV2 = generator
        try {
            GpuFpsTracker.reset()
            // a loaded stretch: >= 10 ms apart (the window ignores shorter gaps)
            val loaded = (0 until 6).map { Thread.sleep(15); GpuFpsTracker.getAndResetFps() }
            assertTrue("a loaded window reads non-zero: $loaded", loaded.drop(2).all { it > 0f })

            // the load stops: the generator now reports no frames
            org.mockito.kotlin.whenever(generator.getAndResetFrameCount()).thenReturn(0)

            // WITHOUT a reset the window still holds the loaded slices for a few queries (the tail)
            Thread.sleep(15)
            val tailWithoutReset = GpuFpsTracker.getAndResetFps()
            assertTrue("the old window leaks a non-zero tail: $tailWithoutReset", tailWithoutReset > 0f)

            // WITH the reset made at the end of phase 5 the value is zero at once and stays zero
            GpuFpsTracker.reset()
            val after = (0 until 4).map { Thread.sleep(15); GpuFpsTracker.getAndResetFps() }
            assertTrue("after reset: $after", after.all { it == 0f })
        } finally {
            GpuFpsTracker.gpuLoadGeneratorV2 = previous
            GpuFpsTracker.reset()
        }
    }

    /**
     * STATIC SOURCE CHECK (the repo's own technique for what cannot run in a JVM —
     * see ScanPermissionsGatePlacementTest): the phase body needs a GL context, so
     * what is checked is the text — that COMBINED_PEAK's `finally` calls
     * GpuFpsTracker.reset() right after gpuActive.stop(). It cannot prove the call
     * runs on a device; the field round (contract §3 item 9 (ط)) does.
     */
    @Test
    fun `the service clears the fps window right after the GPU load stops at the end of phase 5`() {
        val root = listOf(File("app/src/main/java"), File("../app/src/main/java")).first { it.isDirectory }
        val text = File(root, "net/neuralcheck/service/stress/v2/StressTestServiceV2.kt").readText()

        val phase5 = text.substring(text.indexOf("// ─────── Phase 5: COMBINED_PEAK ───────"))
            .substringBefore("// ─────── Phase 6: BURST_PATTERN ───────")
        val finallyBlock = phase5.substringAfterLast("} finally {")
        val stop = finallyBlock.indexOf("gpuActive.stop()")
        val reset = finallyBlock.indexOf("GpuFpsTracker.reset()")
        assertTrue("phase 5's finally must stop the GPU load", stop >= 0)
        assertTrue("phase 5's finally must call GpuFpsTracker.reset()", reset >= 0)
        assertTrue("the reset comes AFTER the load is stopped", reset > stop)
        assertFalse(
            "and it is the only reset in that block",
            finallyBlock.indexOf("GpuFpsTracker.reset()", reset + 1) >= 0
        )
    }
}
