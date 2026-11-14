package net.neuralcheck.hardware.battery

import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.service.collector.BatteryHealthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 63 (2026-05-18): unit tests for [BatteryIndicators.compute]
 * — the pure function that produces the V6 battery footer schema. Pure
 * JUnit 4 (no Mockito, no Android test runner) because BatteryIndicators is
 * Context-free and the only collaborators ([TimeSeriesSample],
 * [BatteryHealthSnapshot]) are plain data classes.
 *
 * Reference: docs/architecture/BATTERY_INDICATORS.md
 */
class BatteryIndicatorsTest {

    // ── Helpers ───────────────────────────────────────────────────────

    /** V5 sample with the four fields the indicator builder reads.
     *  v[1] = battery_temp_c, v[2] = battery_voltage_mv,
     *  v[3] = battery_current_ua, v[4] = battery_level_pct. */
    private fun v5Sample(
        phase: Int,
        tempC: Float = 0f,
        voltageMv: Float = 0f,
        currentUa: Float = 0f,
        levelPct: Float = 0f,
        ts: Long = 0L
    ): TimeSeriesSample = TimeSeriesSample(
        ts = ts,
        phase = phase,
        v = FloatArray(V2_SAMPLE_VAR_COUNT).also {
            it[1] = tempC
            it[2] = voltageMv
            it[3] = currentUa
            it[4] = levelPct
        }
    )

    private fun snapshot(
        designMah: Int? = 4855,
        cycles: Int? = null,
        cyclesSource: String = "unavailable"
    ): BatteryHealthSnapshot = BatteryHealthSnapshot(
        designCapacityMah = designMah,
        designCapacitySource = "powerprofile_reflection",
        cycleCount = cycles,
        cycleCountSource = cyclesSource,
        cycleCountState = if (cycles != null) "read" else "not_read:unsupported_by_device",
        chargeCounterUah = null,
        chargeCounterUahState = "not_read:unsupported_by_device",
        energyCounterNwh = null,
        energyCounterNwhState = "not_read:unsupported_by_device"
    )

    // ── Scenario 1: dischargeRateUnderLoadMa from V2 phases 3-5 ──

    @Test
    fun `dischargeRateUnderLoadMa averages absolute current in phases 3 4 5`() {
        // Mixed-phase samples; only phases 3, 4, 5 should count.
        // Phase 0: 100 µA — ignored
        // Phase 3: 2_000_000 µA = 2000 mA — counted
        // Phase 4: -2_500_000 µA = abs -> 2500 mA — counted
        // Phase 5: 1_500_000 µA = 1500 mA — counted
        // Phase 7: 500 µA — ignored (cooldown)
        // Expected average over 3 in-window samples: (2000+2500+1500)/3 = 2000.0
        val samples = listOf(
            v5Sample(phase = 0, currentUa = 100_000f),
            v5Sample(phase = 3, currentUa = 2_000_000f),
            v5Sample(phase = 4, currentUa = -2_500_000f),
            v5Sample(phase = 5, currentUa = 1_500_000f),
            v5Sample(phase = 7, currentUa = 500_000f)
        )
        val indicators = BatteryIndicators.compute(
            samples = samples,
            batterySnapshot = snapshot(),
            batteryLevelStart = 92,
            batteryLevelEnd = 87,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(2000.0, indicators.dischargeRateUnderLoadMa!!, 0.01)
    }

    @Test
    fun `dischargeRateUnderLoadMa skips zero-current samples`() {
        // 0 µA = "register not populated"; must NOT halve the average.
        val samples = listOf(
            v5Sample(phase = 3, currentUa = 2_000_000f),
            v5Sample(phase = 3, currentUa = 0f),
            v5Sample(phase = 4, currentUa = 2_500_000f),
            v5Sample(phase = 5, currentUa = 0f)
        )
        val indicators = BatteryIndicators.compute(
            samples = samples,
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        // (2000 + 2500) / 2 = 2250 mA, NOT (2000+0+2500+0)/4
        assertEquals(2250.0, indicators.dischargeRateUnderLoadMa!!, 0.01)
    }

    // ── Scenario 2: voltageStabilityMv ──

    @Test
    fun `voltageStabilityMv is zero when all voltages equal`() {
        val samples = (0..10).map {
            v5Sample(phase = 3, voltageMv = 3950f)
        }
        val indicators = BatteryIndicators.compute(
            samples = samples,
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(0.0, indicators.voltageStabilityMv!!, 0.001)
    }

    @Test
    fun `voltageStabilityMv non-zero when voltages vary`() {
        // Three readings: 3900, 4000, 4100 mV. Population stddev:
        // mean = 4000, variance = ((100^2) + 0 + (100^2)) / 3 ≈ 6666.67,
        // stddev ≈ 81.65 mV.
        val samples = listOf(
            v5Sample(phase = 3, voltageMv = 3900f),
            v5Sample(phase = 4, voltageMv = 4000f),
            v5Sample(phase = 5, voltageMv = 4100f)
        )
        val indicators = BatteryIndicators.compute(
            samples = samples,
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(81.65, indicators.voltageStabilityMv!!, 0.1)
    }

    @Test
    fun `one voltage reading has no spread - absent, not a perfectly stable zero`() {
        val indicators = BatteryIndicators.compute(
            samples = listOf(v5Sample(phase = 3, voltageMv = 4000f)),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(null, indicators.voltageStabilityMv)
        assertEquals("not_computed:fewer_than_2_voltage_readings_in_load_window", indicators.voltageStabilityMvState)
    }

    @Test
    fun `two equal voltage readings are a real zero spread - computed`() {
        val indicators = BatteryIndicators.compute(
            samples = listOf(v5Sample(phase = 3, voltageMv = 4000f), v5Sample(phase = 4, voltageMv = 4000f)),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(0.0, indicators.voltageStabilityMv!!, 0.0)
        assertEquals("computed", indicators.voltageStabilityMvState)
    }

    @Test
    fun `only zero currents in the load window means no average - not a 0 mA draw`() {
        val indicators = BatteryIndicators.compute(
            samples = listOf(v5Sample(phase = 3, currentUa = 0f), v5Sample(phase = 4, currentUa = 0f)),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(null, indicators.dischargeRateUnderLoadMa)
        assertEquals("not_computed:no_current_reading_in_load_window", indicators.dischargeRateUnderLoadMaState)
    }

    @Test
    fun `voltageStabilityMv ignores zero readings`() {
        val samples = listOf(
            v5Sample(phase = 3, voltageMv = 4000f),
            v5Sample(phase = 3, voltageMv = 0f),       // ignored
            v5Sample(phase = 4, voltageMv = 4000f)
        )
        val indicators = BatteryIndicators.compute(
            samples = samples,
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        // Two real readings, both = 4000 → stddev = 0
        assertEquals(0.0, indicators.voltageStabilityMv!!, 0.001)
    }

    // ── Scenario 3: batteryTempRiseC NaN / negative-clamp safety ──

    // fieldsVersion 68 (T-18, user decision 2026-10-08: "a missing batteryTempRiseC is
    // absent with a declared state, not 0.0") REPLACES the two tests that stood here
    // ("handles NaN start / max safely"), which pinned 0.0 °C - "the battery did not
    // warm" - for a temperature that was never read.

    @Test
    fun `batteryTempRiseC is absent with its reason when the start temperature is unavailable`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = Double.NaN,
            batteryTempMaxC = 35.0
        )
        assertEquals(null, indicators.batteryTempRiseC)
        assertEquals("not_computed:battery_temp_start_unavailable", indicators.batteryTempRiseCState)
        assertEquals("an unavailable temperature is not 0.0", null, indicators.batteryTempStartC)
        assertEquals(35.0, indicators.batteryTempMaxC!!, 0.001)
    }

    @Test
    fun `batteryTempRiseC is absent with its reason when the max temperature is unavailable`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = Double.NaN
        )
        assertEquals(null, indicators.batteryTempRiseC)
        assertEquals("not_computed:battery_temp_max_unavailable", indicators.batteryTempRiseCState)
        assertEquals(null, indicators.batteryTempMaxC)
    }

    @Test
    fun `batteryTempRiseC names both when neither temperature is available - infinity counts as unavailable`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = Double.NaN,
            batteryTempMaxC = Double.POSITIVE_INFINITY
        )
        assertEquals(null, indicators.batteryTempRiseC)
        assertEquals("not_computed:battery_temp_start_and_max_unavailable", indicators.batteryTempRiseCState)
    }

    // fieldsVersion 68, part 2-c (user decision 2026-10-08: "a negative battery-temperature
    // rise ships with its sign, no clamp") REPLACES "batteryTempRiseC clamps negative delta
    // to zero", which pinned 0.0 for start > max (sensor drift / cold start).
    @Test
    fun `batteryTempRiseC keeps its sign - start above max is a negative rise`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 30.0,
            batteryTempMaxC = 28.0
        )
        assertEquals(-2.0, indicators.batteryTempRiseC!!, 0.001)
        assertEquals("computed", indicators.batteryTempRiseCState)
    }

    @Test
    fun `batteryTempRiseC computes correctly when valid`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.5
        )
        assertEquals(7.5, indicators.batteryTempRiseC!!, 0.001)
        assertEquals("computed", indicators.batteryTempRiseCState)
    }

    // ── Scenario 4: levelDropPct keeps its sign ──
    // fieldsVersion 68 (user decision 2026-10-08: "levelDropPct ships with its sign,
    // no clamp") REPLACES "levelDropPct clamps negative drops to zero", which pinned
    // 0 for a battery that GAINED charge during the scan.

    @Test
    fun `levelDropPct keeps its sign - a battery that gained charge is negative`() {
        // User plugged in mid-scan; end > start.
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(),
            batteryLevelStart = 80,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(-5, indicators.levelDropPct)
        assertEquals("computed", indicators.levelDropPctState)
    }

    @Test
    fun `levelDropPct is absent with its reason when a level was never read`() {
        for ((start, end) in listOf(-1 to -1, 80 to -1, -1 to 85)) {
            val indicators = BatteryIndicators.compute(
                samples = emptyList(),
                batterySnapshot = snapshot(),
                batteryLevelStart = start,
                batteryLevelEnd = end,
                batteryTempStartC = 28.0,
                batteryTempMaxC = 35.0
            )
            assertEquals("start=$start end=$end must not become a number", null, indicators.levelDropPct)
            assertEquals("not_computed:battery_level_unavailable", indicators.levelDropPctState)
        }
    }

    @Test
    fun `the discharge ratio follows the signed drop - a battery that gained charge is negative mAh`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(designMah = 5000),
            batteryLevelStart = 80,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(-250.0, indicators.dischargeVsDesignRatioMah!!, 0.001)
    }

    @Test
    fun `levelDropPct computes correctly when valid`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(),
            batteryLevelStart = 92,
            batteryLevelEnd = 87,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(5, indicators.levelDropPct)
    }

    // ── Scenario 5: dischargeVsDesignRatioMah ──

    @Test
    fun `dischargeVsDesignRatioMah computes from levelDrop and designCap`() {
        // 5% drop × 5000 mAh design → 250 mAh consumed
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(designMah = 5000),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(250.0, indicators.dischargeVsDesignRatioMah!!, 0.001)
    }

    // fieldsVersion 68 (T-18 row 9) REPLACES the test that stood here
    // ("dischargeVsDesignRatioMah is zero when design capacity unavailable",
    // which also pinned designCapacityMah == 0): a ratio over a capacity that
    // does not exist is not "0 mAh consumed".

    @Test
    fun `dischargeVsDesignRatioMah is absent with its reason when design capacity is unavailable`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(designMah = null),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(null, indicators.dischargeVsDesignRatioMah)
        assertEquals("not_computed:design_capacity_unavailable", indicators.dischargeVsDesignRatioMahState)
        assertEquals("a missing design capacity is not 0 mAh", null, indicators.designCapacityMah)
    }

    @Test
    fun `dischargeVsDesignRatioMah is absent when the battery level was never read`() {
        // BatteryReader reports -1 when no ACTION_BATTERY_CHANGED arrived; start - end = 0 would
        // then read as "nothing consumed".
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(designMah = 5000),
            batteryLevelStart = -1,
            batteryLevelEnd = -1,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(null, indicators.dischargeVsDesignRatioMah)
        assertEquals("not_computed:battery_level_unavailable", indicators.dischargeVsDesignRatioMahState)
    }

    @Test
    fun `dischargeVsDesignRatioMah states computed when it is`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(designMah = 5000),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals("computed", indicators.dischargeVsDesignRatioMahState)
        assertEquals(250.0, indicators.dischargeVsDesignRatioMah!!, 0.001)
    }

    @Test
    fun `a real zero ratio is a value - no level drop over a known capacity`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(designMah = 5000),
            batteryLevelStart = 90,
            batteryLevelEnd = 90,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(0.0, indicators.dischargeVsDesignRatioMah!!, 0.0)
        assertEquals("computed", indicators.dischargeVsDesignRatioMahState)
    }

    // ── Scenario 6: empty timeseries doesn't crash ──

    // fieldsVersion 68 (T-18 row 9) REPLACES "empty timeseries returns
    // well-defined zeros not NaN": it pinned 0.0 mA and 0.0 mV for a scan with no
    // readings at all. The values are absent now; the finite-values guarantee
    // (nothing NaN/Inf reaches the footer) is kept.
    @Test
    fun `empty timeseries has no average current and no voltage spread - absent, with reasons`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(null, indicators.dischargeRateUnderLoadMa)
        assertEquals("not_computed:no_current_reading_in_load_window", indicators.dischargeRateUnderLoadMaState)
        assertEquals(null, indicators.voltageStabilityMv)
        assertEquals("not_computed:fewer_than_2_voltage_readings_in_load_window", indicators.voltageStabilityMvState)
        assertTrue(
            "Every output Double that exists must be finite — backend serialisation can't ship NaN/Inf",
            (indicators.dischargeRateUnderLoadMa?.isFinite() ?: true) &&
                (indicators.voltageStabilityMv?.isFinite() ?: true) &&
                (indicators.batteryTempRiseC?.isFinite() ?: true) &&
                (indicators.dischargeVsDesignRatioMah?.isFinite() ?: true)
        )
    }

    // ── Scenario 7: cycleCount propagation ──

    @Test
    fun `cycleCount and source propagate through unchanged`() {
        val withCycles = snapshot(cycles = 247, cyclesSource = "sysfs:/sys/class/power_supply/battery/cycle_count")
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = withCycles,
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(247, indicators.cycleCount)
        assertEquals("sysfs:/sys/class/power_supply/battery/cycle_count", indicators.cycleCountSource)
    }

    @Test
    fun `cycleCount is null when source reports unavailable`() {
        val indicators = BatteryIndicators.compute(
            samples = emptyList(),
            batterySnapshot = snapshot(cycles = null, cyclesSource = "unavailable"),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(null, indicators.cycleCount)
        assertEquals("unavailable", indicators.cycleCountSource)
    }

    // ── Scenario 8: V1 legacy sample (v.size < 4) doesn't crash ──

    @Test
    fun `legacy sample with insufficient v size is skipped silently`() {
        // V1 samples have a different layout; the guard `v.size > 3`
        // must filter them out rather than throwing ArrayIndexOutOfBounds.
        val v1Sample = TimeSeriesSample(
            ts = 0L,
            phase = 3,
            v = FloatArray(2)  // too short for v[3]
        )
        val v5Sample = v5Sample(phase = 3, currentUa = 2_000_000f, voltageMv = 4000f)
        val indicators = BatteryIndicators.compute(
            samples = listOf(v1Sample, v5Sample),
            batterySnapshot = snapshot(),
            batteryLevelStart = 90,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        // Only the V5 sample counts: 2_000_000 µA / 1000 = 2000 mA
        assertEquals(2000.0, indicators.dischargeRateUnderLoadMa!!, 0.01)
    }

    // ── Scenario 9: S24 Ultra-shaped reproducer ──

    @Test
    fun `S24 Ultra-shaped reproducer produces sensible indicators`() {
        // Simulates 30 samples across phases 3, 4, 5 with a 5005 mAh
        // design battery. Voltage ~3950 mV with 50 mV swings; current
        // ~2.5 A discharge during load. Temp rises 28 → 35 °C. 6 %
        // drop over 8 minutes.
        val samples = buildList {
            for (i in 0 until 10) add(v5Sample(phase = 3, voltageMv = 3950f + (i % 2) * 50, currentUa = -2_500_000f))
            for (i in 0 until 10) add(v5Sample(phase = 4, voltageMv = 3940f + (i % 2) * 50, currentUa = -2_700_000f))
            for (i in 0 until 10) add(v5Sample(phase = 5, voltageMv = 3930f + (i % 2) * 50, currentUa = -3_000_000f))
        }
        val indicators = BatteryIndicators.compute(
            samples = samples,
            batterySnapshot = snapshot(designMah = 5005, cycles = 187,
                cyclesSource = "extra_api34"),
            batteryLevelStart = 91,
            batteryLevelEnd = 85,
            batteryTempStartC = 28.0,
            batteryTempMaxC = 35.0
        )
        assertEquals(5005, indicators.designCapacityMah)
        assertEquals(187, indicators.cycleCount)
        assertEquals("extra_api34", indicators.cycleCountSource)
        // Average current: (-2500 + -2700 + -3000) / 3 = -2733.33, abs = 2733.33 mA
        assertEquals(2733.33, indicators.dischargeRateUnderLoadMa!!, 1.0)
        assertEquals(28.0, indicators.batteryTempStartC!!, 0.001)
        assertEquals(35.0, indicators.batteryTempMaxC!!, 0.001)
        assertEquals(7.0, indicators.batteryTempRiseC!!, 0.001)
        assertEquals(6, indicators.levelDropPct)
        // 6 % × 5005 mAh = 300.3 mAh
        assertEquals(300.3, indicators.dischargeVsDesignRatioMah!!, 0.1)
        // Voltage swings 50 mV; stddev should be in a sane range
        assertTrue(
            "voltageStability should be a positive single/double-digit mV, got ${indicators.voltageStabilityMv}",
            indicators.voltageStabilityMv!! in 1.0..50.0
        )
    }
}
