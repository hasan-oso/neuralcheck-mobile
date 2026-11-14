package net.neuralcheck.hardware.thermal

import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.service.sampler.SamplerStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.14: unit tests for the heat-rise *rate* computation inside
 * [SamplerStats.snapshot]. Pre-1.14 the value was a raw °C delta with the
 * unit string "°C/min" (a labeling lie that produced the 38.20 "°C/min"
 * Samsung S24 Ultra anomaly). The field now carries a real rate:
 * `(maxBestTemp − firstBestTemp) / scanDurationMinutes`.
 *
 * Reference: docs/audits/phase_1_14_thermal_diagnosis/findings.md
 */
class HeatRiseRateTest {

    /** Builds a TimeSeriesSample with only v[0] set — everything else is 0. */
    private fun sampleAt(elapsedMs: Long, phase: Int = 1): TimeSeriesSample =
        TimeSeriesSample(
            ts = elapsedMs,
            phase = phase,
            v = FloatArray(V2_SAMPLE_VAR_COUNT).also { it[0] = elapsedMs.toFloat() }
        )

    /**
     * Drives a SamplerStats with two endpoint samples (durations are bounded
     * by `firstSampleElapsedMs..lastSampleElapsedMs`) and two
     * `updateThermalSidecar` calls bracketing them with CPU temperatures.
     * Returns the resulting Snapshot.
     */
    private fun runScenario(
        firstCpuTempC: Float,
        peakCpuTempC: Float,
        scanDurationMs: Long
    ): SamplerStats.Snapshot {
        val stats = SamplerStats()
        // First sample at t=0 with the cold temperature
        stats.update(sampleAt(0L, phase = 1), phase = 1)
        if (!firstCpuTempC.isNaN()) {
            stats.updateThermalSidecar(
                headroom = Float.NaN, thermalStatus = -1,
                cpuTempC = firstCpuTempC
            )
        }
        // Final sample at t=scanDurationMs with the peak temperature
        if (scanDurationMs > 0L) {
            stats.update(sampleAt(scanDurationMs, phase = 1), phase = 1)
        }
        if (!peakCpuTempC.isNaN() && peakCpuTempC > 0f) {
            stats.updateThermalSidecar(
                headroom = Float.NaN, thermalStatus = -1,
                cpuTempC = peakCpuTempC
            )
        }
        return stats.snapshot()
    }

    // ─── Scenario 1: realistic flagship rise across 8 min → ≈1.25 °C/min ───

    @Test
    fun `27 to 37C over 8 minutes yields 1_25 C per min`() {
        val snap = runScenario(
            firstCpuTempC = 27f,
            peakCpuTempC  = 37f,
            scanDurationMs = 8L * 60_000L
        )
        assertEquals(1.25f, snap.heatRiseC, 0.01f)
        assertEquals("cpu", snap.tempSource)
        assertEquals(37f, snap.maxTempC, 0.01f)
    }

    // ─── Scenario 2: no rise at all → 0 °C/min ───

    @Test
    fun `flat temperature yields zero rate`() {
        val snap = runScenario(
            firstCpuTempC = 27f,
            peakCpuTempC  = 27f,
            scanDurationMs = 8L * 60_000L
        )
        assertEquals(0f, snap.heatRiseC, 0.001f)
    }

    // ─── Scenario 3: no CPU temp captured at all → -1 sentinel ───

    @Test
    fun `no thermal sidecar updates yields -1`() {
        val stats = SamplerStats()
        stats.update(sampleAt(0L), phase = 1)
        stats.update(sampleAt(8L * 60_000L), phase = 1)
        val snap = stats.snapshot()
        assertEquals(-1f, snap.heatRiseC, 0.001f)
    }

    // ─── Scenario 4: zero-duration scan (only one sample) → -1 sentinel ───

    @Test
    fun `zero-duration scan yields -1`() {
        val snap = runScenario(
            firstCpuTempC = 27f,
            peakCpuTempC  = 30f,
            scanDurationMs = 0L
        )
        // durationMs computation in snapshot() returns 0 when first==last,
        // and the rate guard requires durationMs > 0L → -1f sentinel.
        assertEquals(-1f, snap.heatRiseC, 0.001f)
    }

    // ─── Scenario 5: old-anomaly inputs still produce a FAIL-band rate ───

    @Test
    fun `80C peak in 80s would still flag thermal runaway`() {
        // If Commit 1's zone-selector fix ever regresses and a non-CPU 80 °C
        // reading leaks back into the sampler, the new rate computation
        // gives ~39.75 °C/min — well into the new FAIL band (≥ 3 °C/min).
        // The labeling lie is gone; the number is now a real rate that
        // correctly signals "this can't be CPU silicon" rather than a
        // delta-pretending-to-be-a-rate.
        val snap = runScenario(
            firstCpuTempC = 27f,
            peakCpuTempC  = 80f,
            scanDurationMs = 80_000L  // 80 s = 1.333 min
        )
        // (80 - 27) / (80_000 / 60_000) = 53 / 1.333 ≈ 39.75 °C/min
        assertTrue(
            "Expected high °C/min rate; got ${snap.heatRiseC}",
            snap.heatRiseC > 30f
        )
    }

    // ─── Scenario 6: Xiaomi POCO F6 reference (≈4 °C across 7-8 min → ≈0.5 °C/min) ───

    @Test
    fun `POCO F6 reference 29 to 31C across 7 minutes is sub-1 C per min`() {
        val snap = runScenario(
            firstCpuTempC = 29.3f,
            peakCpuTempC  = 31.4f,
            scanDurationMs = 7L * 60_000L
        )
        // (31.4 - 29.3) / 7 = 0.3 °C/min — under the 1 °C/min PASS threshold
        assertEquals(0.3f, snap.heatRiseC, 0.05f)
    }
}
