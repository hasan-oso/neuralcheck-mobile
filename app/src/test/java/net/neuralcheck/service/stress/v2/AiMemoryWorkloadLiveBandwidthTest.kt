package net.neuralcheck.service.stress.v2

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * fieldsVersion 66 — the memory workload must publish a LIVE bandwidth
 * series through the callback (the storage-segment pattern), replacing the
 * ≤65 in-memory backfill that never reached the .ts.json.gz (v[9] was
 * ≥85 % zeros in phase 2 on both comparison devices).
 *
 * Runs the real kernel on the JVM (4 × 8 MiB buffers, ~0.7 s) with a
 * shortened publish interval so the test stays fast.
 */
class AiMemoryWorkloadLiveBandwidthTest {

    @Test
    fun `callback receives positive rolling MB per second during the run`() = runBlocking {
        val published = CopyOnWriteArrayList<Float>()
        val result = AiMemoryWorkload().run(
            durationMs = 700L,
            intensityFactor = 1.0f,
            config = null,
            onBandwidth = { mbps -> published += mbps },
            publishIntervalMs = 100L
        )
        assertTrue("workload did not iterate", result.iterations > 0)
        assertTrue("workload published nothing live: $published", published.size >= 2)
        assertTrue("every published value must be positive: $published", published.all { it > 0f })
        assertTrue("published values must be finite: $published", published.all { it.isFinite() })
    }

    @Test
    fun `no callback means no publishing and the run still completes`() = runBlocking {
        val result = AiMemoryWorkload().run(
            durationMs = 150L,
            intensityFactor = 1.0f,
            config = null,
            onBandwidth = null
        )
        assertTrue(result.bytesTouched > 0L)
    }
}
