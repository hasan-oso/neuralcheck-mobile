package net.neuralcheck.battery

import net.neuralcheck.service.collector.estimateBatterySoh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-function tests for the AccuBattery-style SoH estimator added in
 * Phase 1.11 follow-up (2026-05-14). The estimator is the third tier in
 * BatteryReader.snapshotHealth, fired only when the SoH API and sysfs
 * charge_full both fail — which on Samsung Exynos pre-Android-14 is
 * effectively always.
 *
 * The function is intentionally side-effect-free (no Context, no
 * BatteryManager, no sticky-intent reads) so these tests are JVM-only.
 */
class BatterySohEstimateTest {

    /** S10 5G empirical: 2 358 400 µAh at 53 %, design 4 400 mAh. */
    @Test
    fun `estimates 100 percent capacity in the linear 20-90 range`() {
        val r = estimateBatterySoh(
            chargeCounterUah = 2_358_400L,
            batteryLevelPct = 53,
            designCapacityMah = 4_400
        )
        // full = 2_358_400 * 100 / 53 = 4_449_811 µAh = 4449 mAh
        // soh  = 4449 / 4400 = 101.1 % → clamped to 101 (under the 110 ceiling)
        assertNotNull("estimate should fire inside linear range", r.estimatedFullMah)
        assertEquals("estimated_from_charge_level", r.source)
        assertEquals(4_449, r.estimatedFullMah)
        // SoH ~101 % is plausible (manufacturers under-rate by a few %).
        assertEquals(101, r.sohPercent)
    }

    /** Mid-range case: battery in the middle of the band. */
    @Test
    fun `estimates correctly at 50 percent and reports degradation`() {
        // 4000 mAh design device worn to ~3000 mAh actual.
        // At 50 %, chargeCounter would read 3000 mAh × 50 % = 1500 mAh
        //   = 1_500_000 µAh.
        val r = estimateBatterySoh(
            chargeCounterUah = 1_500_000L,
            batteryLevelPct = 50,
            designCapacityMah = 4_000
        )
        assertEquals(3_000, r.estimatedFullMah)
        assertEquals(75, r.sohPercent)  // 75 % SoH ⇒ WARNING band
        assertEquals("estimated_from_charge_level", r.source)
    }

    /**
     * Below 20 % SOC the Li-ion voltage cliff means chargeCounter is no
     * longer a linear function of capacity. The estimator must refuse
     * rather than report a misleading number — and the diagnostic source
     * tag drives the user-facing "rescan in 20–90 %" prompt.
     */
    @Test
    fun `refuses below 20 percent with linear range tag`() {
        val r = estimateBatterySoh(
            chargeCounterUah = 600_000L,
            batteryLevelPct = 15,
            designCapacityMah = 4_400
        )
        assertNull(r.estimatedFullMah)
        assertNull(r.sohPercent)
        assertEquals("outside_linear_range", r.source)
    }

    /** Same refusal at the top of the curve (CV-stage non-linearity). */
    @Test
    fun `refuses above 90 percent with linear range tag`() {
        val r = estimateBatterySoh(
            chargeCounterUah = 4_200_000L,
            batteryLevelPct = 95,
            designCapacityMah = 4_400
        )
        assertNull(r.estimatedFullMah)
        assertEquals("outside_linear_range", r.source)
    }

    /** No chargeCounter (BatteryManager returned Long.MIN_VALUE / 0). */
    @Test
    fun `refuses when chargeCounter missing`() {
        val r = estimateBatterySoh(
            chargeCounterUah = null,
            batteryLevelPct = 50,
            designCapacityMah = 4_400
        )
        assertNull(r.estimatedFullMah)
        assertEquals("missing_charge_counter", r.source)
    }

    /** ZeroChargeCounter is treated as missing, not a real reading. */
    @Test
    fun `refuses when chargeCounter is zero`() {
        val r = estimateBatterySoh(
            chargeCounterUah = 0L,
            batteryLevelPct = 50,
            designCapacityMah = 4_400
        )
        assertNull(r.estimatedFullMah)
        assertEquals("missing_charge_counter", r.source)
    }

    /** Design capacity missing → can't compute SoH, refuse cleanly. */
    @Test
    fun `refuses when design capacity missing`() {
        val r = estimateBatterySoh(
            chargeCounterUah = 2_000_000L,
            batteryLevelPct = 50,
            designCapacityMah = null
        )
        assertNull(r.estimatedFullMah)
        assertEquals("missing_design_capacity", r.source)
    }

    /**
     * 110 % ceiling: a brand-new battery can briefly report slightly above
     * the published design capacity. We clamp to 110 so a misread doesn't
     * surface as an absurd "SoH 142 %" while still allowing the realistic
     * 100-110 % range for fresh batteries.
     */
    @Test
    fun `clamps soh at 110 percent ceiling for misreads`() {
        // chargeCounter / level implies full = 8000 mAh on a 4000 mAh design
        // → raw SoH = 200 %; clamp to 110.
        val r = estimateBatterySoh(
            chargeCounterUah = 4_000_000L,
            batteryLevelPct = 50,
            designCapacityMah = 4_000
        )
        assertEquals(8_000, r.estimatedFullMah)
        assertEquals(110, r.sohPercent)
    }
}
