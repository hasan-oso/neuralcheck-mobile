package net.neuralcheck.service.collector

import kotlin.math.abs

/**
 * fieldsVersion 65 — pure sanitisation layer for the battery channels
 * (v[1..4] + v[7]). Extracted as a top-level object (same pattern as
 * [estimateBatterySoh]) so the poisoning filters are unit-testable
 * without an Android Context.
 *
 * Why this exists: BatteryManager.getIntProperty returns Int.MIN_VALUE
 * on failure (and BATTERY_PROPERTY_* long reads return Long.MIN_VALUE).
 * Before v65 the register() probe filtered MIN_VALUE but snapshot() did
 * NOT — a mid-scan failure leaked ±2.1e9 µA into v[3], poisoned the
 * v[7] power product, and (worse) `abs(Int.MIN_VALUE)` stays negative,
 * so the invalid reading was silently counted as *mA-format evidence*
 * by the scaling lock. Missing-value convention follows the existing
 * project pattern: 0 = "no data" for current (documented at
 * BatteryReader.normaliseCurrent), 0 = "no data" for voltage,
 * -1 = "no data" for level (calculateLevel) and power (readPackagePowerMw).
 */
object BatteryChannelSanitizer {

    /**
     * Some vendors (observed on Oppo/Realme ColorOS forks) report
     * EXTRA_VOLTAGE in µV instead of the documented mV. A real battery
     * never exceeds ~5500 mV, and a µV reading is never below ~2.5e6,
     * so anything above this threshold is unambiguously µV.
     */
    const val VOLTAGE_UV_THRESHOLD = 100_000

    /** True when a raw CURRENT_NOW reading is the platform failure
     *  sentinel and must not reach normalisation, the scaling-lock
     *  evidence counters, v[3], or the v[7] power product. */
    fun isInvalidCurrent(raw: Int): Boolean = raw == Int.MIN_VALUE

    /** Long-property variant (CHARGE_COUNTER / ENERGY_COUNTER). */
    fun isInvalidLong(raw: Long): Boolean = raw == Long.MIN_VALUE

    /**
     * Result of voltage normalisation. [millivolts] is 0 when the raw
     * reading was absent/invalid (existing "no data" convention for
     * v[2]); [uvConversionApplied] is true when the raw value was
     * detected as µV and divided by 1000.
     */
    data class VoltageResult(val millivolts: Int, val uvConversionApplied: Boolean)

    /**
     * Normalise a raw EXTRA_VOLTAGE reading to millivolts.
     *  - Int.MIN_VALUE / non-positive → 0 ("no data", existing convention)
     *  - > [VOLTAGE_UV_THRESHOLD] → treated as µV, divided by 1000
     *  - otherwise passed through unchanged (already mV)
     */
    fun normaliseVoltageToMv(raw: Int): VoltageResult = when {
        raw == Int.MIN_VALUE || raw <= 0 -> VoltageResult(0, false)
        raw > VOLTAGE_UV_THRESHOLD -> VoltageResult(raw / 1000, true)
        else -> VoltageResult(raw, false)
    }

    /**
     * Package power in mW from ONE (voltage, current) pair — the same
     * pair that feeds v[2]/v[3], so v[7] is always reproducible as
     * |v[2] × v[3]| / 1e6. Returns -1f ("no data", existing convention
     * from readPackagePowerMw) when either input is missing.
     *
     * @param voltageMv sanitised millivolts (0 = missing)
     * @param currentUa normalised µA (0 = missing)
     */
    fun computePackagePowerMw(voltageMv: Int, currentUa: Int): Float {
        if (voltageMv <= 0 || currentUa == 0) return -1f
        return ((voltageMv.toLong() * abs(currentUa.toLong())) / 1_000_000L).toFloat()
    }
}
