package net.neuralcheck.hardware.thermal

import net.neuralcheck.service.collector.SystemFileReader
import net.neuralcheck.service.collector.ThermalZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.14: unit tests for [SystemFileReader.selectCpuTempFromZones].
 * The selector was extracted into a pure function (no file-system access,
 * no Context) so these tests can exercise the Tier 1 + Tier 2 logic
 * directly with synthetic zone lists.
 *
 * Reference: docs/audits/phase_1_14_thermal_diagnosis/findings.md
 */
class ThermalZoneSelectionTest {

    private fun zone(index: Int, type: String, milliC: Long): ThermalZone =
        ThermalZone(index, type, milliC)

    // ─── Scenario 1: CPU + blacklisted zone present → blacklisted rejected ───

    @Test
    fun `tier1 picks a real cpu zone over wpc_big_thermal`() {
        // S24-Ultra-like sample: several CPU zones and an 80 °C charger zone
        // that the old substring matcher would latch onto via the "big" token.
        val zones = listOf(
            zone(0, "cpu-0-step", 42_000L),
            zone(1, "cpu-1-step", 41_000L),
            zone(2, "big",        43_000L),
            zone(3, "wpc_big_thermal", 80_200L)   // pre-1.14: would win
        )
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        // Any of the genuine CPU zones is acceptable (first-match order is
        // implementation detail); the assertion is that the blacklisted one
        // does NOT win.
        assertTrue(
            "Selector returned $result°C — should be one of the cpu/big zones (41-43 °C), not the wpc charger at 80.2 °C",
            result in 40f..45f
        )
    }

    // ─── Scenario 2: only blacklisted zones → fall through to -1 ───

    @Test
    fun `all zones blacklisted returns -1`() {
        val zones = listOf(
            zone(0, "wpc_big",     78_000L),
            zone(1, "usb_charger", 76_000L),
            zone(2, "modem_pa",    72_000L),
            zone(3, "battery_skin", 35_000L)
        )
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        assertEquals(
            "All zones are non-CPU; selector must refuse rather than return a charger temp",
            -1f, result, 0.001f
        )
    }

    // ─── Scenario 3: sanity cap rejects > 105 °C readings ───

    @Test
    fun `sanity cap rejects 200C tier1 match and falls through to cpu_thermal`() {
        val zones = listOf(
            zone(0, "big_cluster_0", 200_000L),  // garbage / kernel scratch
            zone(1, "cpu_thermal",    45_000L)
        )
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        // Tier 1 sees big_cluster_0 first, reads 200 °C, range-rejects,
        // falls through. Tier 2 also range-rejects 200 °C (it's above the
        // 105 °C plausible cap). cpu_thermal at 45 °C survives Tier 2.
        assertEquals(45f, result, 0.001f)
    }

    @Test
    fun `tier2 sanity-cap rejects 110C non-CPU-named zone`() {
        val zones = listOf(
            zone(0, "anonymous_die_a", 110_000L),
            zone(1, "anonymous_die_b",  44_000L)
        )
        // No Tier-1 match (neither name contains a CPU token); Tier 2
        // hottest-non-blacklist would have picked die_a at 110 °C pre-1.14.
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        assertEquals(44f, result, 0.001f)
    }

    // ─── Scenario 4: case-insensitive CPU token match ───

    @Test
    fun `BIG uppercase matches the big token`() {
        val zones = listOf(
            zone(0, "BIG", 42_000L)
        )
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        assertEquals(42f, result, 0.001f)
    }

    @Test
    fun `LITTLE_THERMAL prefix matches the LITTLE token`() {
        // Pixel Tensor uses uppercase BIG/MID/LITTLE; verify the
        // prefix-anchored matcher accepts `LITTLE_THERMAL`.
        val zones = listOf(
            zone(0, "LITTLE_THERMAL", 38_500L)
        )
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        assertEquals(38.5f, result, 0.001f)
    }

    // ─── Scenario 5: short token "big" rejects "wpc_big" / "usb_midbus" ───

    @Test
    fun `big token does not substring-match wpc_big`() {
        // Old code: zone.type.contains("big") matched. New code: must
        // equal "big" or start with "big_" / "big-".
        val zones = listOf(
            zone(0, "wpc_big", 75_000L)
        )
        // wpc_big is also blacklisted by the "wpc" non-CPU token; both
        // gates should reject this.
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        assertEquals(-1f, result, 0.001f)
    }

    @Test
    fun `mid token does not substring-match modem_midbus`() {
        // modem_midbus would match the old `mid` substring filter; the
        // new anchored matcher requires `mid` / `mid_` / `mid-` prefix.
        // It is also non-CPU-blacklisted via "modem".
        val zones = listOf(
            zone(0, "modem_midbus", 60_000L)
        )
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        assertEquals(-1f, result, 0.001f)
    }

    // ─── Scenario 6: empty zone list ───

    @Test
    fun `empty zones returns -1`() {
        val result = SystemFileReader.selectCpuTempFromZones(emptyList())
        assertEquals(-1f, result, 0.001f)
    }

    // ─── Scenario 7: cpuss_0 prefix-anchors against the cpuss token ───

    @Test
    fun `cpuss_0 matches the cpuss token via prefix anchoring`() {
        // CPU_ZONE_NAMES lists "cpuss" — the new anchored matcher accepts
        // any zone starting with "cpuss_" or "cpuss-" plus exact match.
        val zones = listOf(
            zone(0, "cpuss_0", 47_000L)
        )
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        assertEquals(47f, result, 0.001f)
    }

    // ─── Scenario 8: decimalised values (Samsung Exynos quirk) ───

    @Test
    fun `decimalised value 42 is interpreted as 42C not 42 milliC`() {
        // Samsung Exynos kernels write decimalised values directly. The
        // milliCelsius heuristic in SystemFileReader.kt treats values
        // <= 1000 as already-°C. Verify a 42 reading produces 42 °C, not
        // 0.042 °C (which would be range-rejected).
        val zones = listOf(
            zone(0, "cpu", 42L)
        )
        val result = SystemFileReader.selectCpuTempFromZones(zones)
        assertEquals(42f, result, 0.001f)
    }
}
