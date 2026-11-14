package net.neuralcheck.files

import net.neuralcheck.data.local.files.ChannelNames
import net.neuralcheck.data.local.files.TimeSeriesVariables
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.data.local.files.V2_VARIABLE_DEFINITIONS
import net.neuralcheck.hardware.battery.BatteryIndicators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 68 — the V2 layout is `v[0..10]` FIXED + per-device channels
 * appended (docs/FV68_CONTRACT.md §٢.١, N-22). This file replaces the old
 * assertion `assertEquals(11, V2_VARIABLE_DEFINITIONS.size)` (rule 23: a test
 * that pinned the former "exactly 11 channels" shape is replaced, not joined
 * by a second one): what is frozen is now the NAME at each base index, not
 * the total width.
 *
 * Why the indices must stay pinned: the server reads columns 1..5 and 10 by
 * fixed index (`findings.js` `colByIndex`, transferred in the contract §٤.٢
 * — not verified from this repository), and the in-process readers
 * (SamplerStats, StressEffectivenessCalculator, BatteryIndicators) read
 * `v[0..7]` and `v[10]` of samples this same build produced. If a base index
 * moves, all of them break silently — this test breaks loudly instead.
 */
class V2BaseLayoutTest {

    private val expectedBase = listOf(
        0 to ChannelNames.TIMESTAMP_MS,
        1 to ChannelNames.BATTERY_TEMP_C,
        2 to ChannelNames.BATTERY_VOLTAGE_MV,
        3 to ChannelNames.BATTERY_CURRENT_UA,
        4 to ChannelNames.BATTERY_LEVEL_PCT,
        5 to ChannelNames.CPU_FREQ_MAX_KHZ,
        6 to ChannelNames.CPU_USAGE_PCT,
        7 to ChannelNames.PACKAGE_POWER_MW,
        8 to ChannelNames.STORAGE_THROUGHPUT_MBPS,
        9 to ChannelNames.MEMORY_BANDWIDTH_MBPS,
        10 to ChannelNames.GPU_FPS
    )

    @Test
    fun `base layout names sit at their frozen indices`() {
        assertEquals(
            expectedBase,
            V2_VARIABLE_DEFINITIONS.map { it.index to it.name }
        )
    }

    @Test
    fun `the literal wire names of the base layout are the contract names`() {
        // The ChannelNames constants are the single source — but their VALUES
        // are the wire contract and must not drift with a refactor.
        assertEquals(
            listOf(
                "timestamp_ms", "battery_temp_c", "battery_voltage_mv", "battery_current_ua",
                "battery_level_pct", "cpu_freq_max_khz", "cpu_usage_pct", "package_power_mw",
                "storage_throughput_mbps", "memory_bandwidth_mbps", "gpu_fps"
            ),
            V2_VARIABLE_DEFINITIONS.map { it.name }
        )
    }

    @Test
    fun `base width constant is the base block, and index equals position`() {
        assertEquals(V2_VARIABLE_DEFINITIONS.size, V2_SAMPLE_VAR_COUNT)
        V2_VARIABLE_DEFINITIONS.forEachIndexed { position, def -> assertEquals(position, def.index) }
    }

    @Test
    fun `the battery temperature index used by BatteryIndicators is the battery_temp_c column`() {
        assertEquals(
            ChannelNames.BATTERY_TEMP_C,
            V2_VARIABLE_DEFINITIONS[BatteryIndicators.batteryTempIndex()].name
        )
    }

    @Test
    fun `legacy v1 definitions use the same names for the columns they share with v2`() {
        val v1 = TimeSeriesVariables.definitions.associate { it.name to it.index }
        // Shared names resolve (at their own V1 positions) — that is what lets
        // one by-name reader serve both layouts.
        for (shared in listOf(
            ChannelNames.TIMESTAMP_MS, ChannelNames.BATTERY_TEMP_C, ChannelNames.BATTERY_VOLTAGE_MV,
            ChannelNames.BATTERY_CURRENT_UA, ChannelNames.BATTERY_LEVEL_PCT,
            ChannelNames.CPU_USAGE_PCT, ChannelNames.GPU_FPS
        )) {
            assertTrue("V1 declares $shared", v1.containsKey(shared))
        }
        assertEquals(5, v1[ChannelNames.CPU_TEMP_C])
        assertEquals(9, v1[ChannelNames.CPU_USAGE_PCT])
        assertEquals(10, v1[ChannelNames.GPU_FPS])
        assertEquals(18, TimeSeriesVariables.definitions.size)
    }

    @Test
    fun `per-policy channel names round trip and reject non-canonical spellings`() {
        assertEquals("cpu_freq_policy0_khz", ChannelNames.cpuFreqPolicy(0))
        assertEquals("cpu_freq_policy12_khz", ChannelNames.cpuFreqPolicy(12))
        for (n in listOf(0, 1, 4, 6, 7, 12)) {
            assertEquals(n, ChannelNames.cpuFreqPolicyIndexOf(ChannelNames.cpuFreqPolicy(n)))
        }
        for (bad in listOf(
            "cpu_freq_policy_khz", "cpu_freq_policy1x_khz", "cpu_freq_policy-1_khz",
            "cpu_freq_policy01_khz", "cpu_freq_policy1_mhz", "cpu_freq_max_khz",
            "charge_counter_raw", "", "cpu_freq_policy99999999999_khz"
        )) {
            assertNull("must not parse: '$bad'", ChannelNames.cpuFreqPolicyIndexOf(bad))
        }
    }
}
