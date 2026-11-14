package net.neuralcheck.battery

import net.neuralcheck.service.collector.BatteryChannelSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 65 — battery-channel poisoning filters.
 *
 * Regression anchor: BatteryManager.getIntProperty returns Int.MIN_VALUE
 * on failure. Before v65, a mid-scan MIN_VALUE leaked ±2.1e9 µA into
 * v[3]/v[7] AND — because abs(Int.MIN_VALUE) stays negative — was counted
 * as mA-format evidence by the scaling lock (absRaw < MA_LOCK_THRESHOLD),
 * risking a wrong 1000× lock for the whole scan on any vendor whose
 * battery service hiccups (observed on Honor/Realme field units).
 */
class BatteryChannelSanitizerTest {

    // ─────────────── Int.MIN_VALUE / Long.MIN_VALUE filtering ───────────────

    @Test
    fun `Int MIN_VALUE current is flagged invalid`() {
        assertTrue(BatteryChannelSanitizer.isInvalidCurrent(Int.MIN_VALUE))
    }

    @Test
    fun `ordinary currents are not flagged invalid`() {
        // Discharge (AOSP negative), charge, zero ("no data"), large-but-real
        // µA magnitudes, and the extremes short of the sentinel.
        assertFalse(BatteryChannelSanitizer.isInvalidCurrent(0))
        assertFalse(BatteryChannelSanitizer.isInvalidCurrent(-250_000))
        assertFalse(BatteryChannelSanitizer.isInvalidCurrent(3_500_000))
        assertFalse(BatteryChannelSanitizer.isInvalidCurrent(Int.MIN_VALUE + 1))
        assertFalse(BatteryChannelSanitizer.isInvalidCurrent(Int.MAX_VALUE))
    }

    @Test
    fun `Long MIN_VALUE property is flagged invalid`() {
        assertTrue(BatteryChannelSanitizer.isInvalidLong(Long.MIN_VALUE))
        assertFalse(BatteryChannelSanitizer.isInvalidLong(0L))
        assertFalse(BatteryChannelSanitizer.isInvalidLong(4_500_000L)) // µAh charge counter
        assertFalse(BatteryChannelSanitizer.isInvalidLong(Long.MIN_VALUE + 1))
    }

    // ─────────────────────────── µV detection ───────────────────────────

    @Test
    fun `microvolt reading above threshold is divided by 1000 and flagged`() {
        // ColorOS-style µV report: 3.85 V as 3,850,000.
        val r = BatteryChannelSanitizer.normaliseVoltageToMv(3_850_000)
        assertEquals(3850, r.millivolts)
        assertTrue(r.uvConversionApplied)
    }

    @Test
    fun `millivolt reading passes through unchanged`() {
        val r = BatteryChannelSanitizer.normaliseVoltageToMv(4200)
        assertEquals(4200, r.millivolts)
        assertFalse(r.uvConversionApplied)
    }

    @Test
    fun `threshold boundary - exactly 100000 stays mV, 100001 converts`() {
        val atThreshold = BatteryChannelSanitizer.normaliseVoltageToMv(100_000)
        assertEquals(100_000, atThreshold.millivolts)
        assertFalse(atThreshold.uvConversionApplied)

        val aboveThreshold = BatteryChannelSanitizer.normaliseVoltageToMv(100_001)
        assertEquals(100, aboveThreshold.millivolts)
        assertTrue(aboveThreshold.uvConversionApplied)
    }

    // ───────────── per-channel failure behaviour (missing sentinels) ─────────────

    @Test
    fun `voltage failure sentinels map to 0 no-data without conversion flag`() {
        // Int.MIN_VALUE (property failure), 0 (intent absent), negative junk.
        for (raw in intArrayOf(Int.MIN_VALUE, 0, -1, -42)) {
            val r = BatteryChannelSanitizer.normaliseVoltageToMv(raw)
            assertEquals("raw=$raw", 0, r.millivolts)
            assertFalse("raw=$raw", r.uvConversionApplied)
        }
    }

    @Test
    fun `power is -1 no-data when current channel failed`() {
        // Current failure surfaces as the 0 sentinel upstream → power must
        // be -1 (missing), never a fabricated 0 mW measurement.
        assertEquals(-1f, BatteryChannelSanitizer.computePackagePowerMw(4000, 0))
    }

    @Test
    fun `power is -1 no-data when voltage channel failed`() {
        assertEquals(-1f, BatteryChannelSanitizer.computePackagePowerMw(0, -250_000))
        assertEquals(-1f, BatteryChannelSanitizer.computePackagePowerMw(-1, -250_000))
    }

    // ───────────── v[7] reproducibility from v[2] × v[3] ─────────────

    @Test
    fun `power equals abs(voltage times current) over 1e6`() {
        // 4000 mV × |-250,000 µA| / 1e6 = 1000 mW.
        assertEquals(1000f, BatteryChannelSanitizer.computePackagePowerMw(4000, -250_000))
        // Charging (positive current) — same magnitude.
        assertEquals(1000f, BatteryChannelSanitizer.computePackagePowerMw(4000, 250_000))
        // Sustained-stress-class draw: 4400 mV × 3.5 A = 15,400 mW.
        assertEquals(15_400f, BatteryChannelSanitizer.computePackagePowerMw(4400, -3_500_000))
    }

    @Test
    fun `power does not overflow on extreme but real inputs`() {
        // 5500 mV × |Int.MIN_VALUE + 1| — the Long math must not wrap.
        val mw = BatteryChannelSanitizer.computePackagePowerMw(5500, Int.MIN_VALUE + 1)
        assertTrue(mw > 0f)
    }
}
