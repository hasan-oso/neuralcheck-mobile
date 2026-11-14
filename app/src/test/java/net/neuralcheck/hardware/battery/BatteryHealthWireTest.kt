package net.neuralcheck.hardware.battery

import net.neuralcheck.data.local.files.TimeSeriesSample
import net.neuralcheck.data.local.files.V2_SAMPLE_VAR_COUNT
import net.neuralcheck.files.FooterRoundTrip
import net.neuralcheck.service.collector.BatteryHealthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * fieldsVersion 68 (T-18 row 9): `footer.batteryHealth` through the real
 * writer -> reader -> builder -> Gson chain, built by the same
 * [BatteryIndicators.footerMap] the service calls. An indicator that could not
 * be computed is an ABSENT key on the wire (and an explicit null in the file),
 * with its `<name>State` present.
 */
class BatteryHealthWireTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val health = BatteryHealthSnapshot(
        designCapacityMah = null, designCapacitySource = "unavailable",
        cycleCount = null, cycleCountSource = "unavailable",
        cycleCountState = "not_read:unsupported_by_device",
        chargeCounterUah = null, chargeCounterUahState = "not_read:unsupported_by_device",
        energyCounterNwh = null, energyCounterNwhState = "not_read:unsupported_by_device"
    )

    private fun sample(phase: Int, voltageMv: Float, currentUa: Float) = TimeSeriesSample(
        ts = 0L, phase = phase,
        v = FloatArray(V2_SAMPLE_VAR_COUNT).also { it[2] = voltageMv; it[3] = currentUa }
    )

    private fun wireHealth(
        samples: List<TimeSeriesSample>,
        snapshot: BatteryHealthSnapshot,
        levelStart: Int = 90,
        levelEnd: Int = 85
    ): Pair<com.google.gson.JsonObject, Map<String, Any?>> {
        val real = BatteryIndicators.compute(samples, snapshot, levelStart, levelEnd, 28.0, 35.0)
        val map = BatteryIndicators.footerMap(real, snapshot)
        val rt = FooterRoundTrip(tmp.newFolder())
        val r = rt.run(rt.baseFooter().copy(batteryHealth = map))
        return r.wireFooter.getAsJsonObject("batteryHealth") to r.data.footer.batteryHealth!!
    }

    @Test
    fun `nothing computable - absent keys with their states, not zeros`() {
        val (wire, file) = wireHealth(samples = emptyList(), snapshot = health)

        for (key in listOf("designCapacityMah", "dischargeRateUnderLoadMa", "voltageStabilityMv", "dischargeVsDesignRatioMah")) {
            assertFalse("$key must be an absent key, a 0 would read as a measurement", wire.has(key))
            assertTrue("$key is an explicit null in the file", file.containsKey(key) && file[key] == null)
        }
        assertEquals("not_computed:no_current_reading_in_load_window", wire.get("dischargeRateUnderLoadMaState").asString)
        assertEquals("not_computed:fewer_than_2_voltage_readings_in_load_window", wire.get("voltageStabilityMvState").asString)
        assertEquals("not_computed:design_capacity_unavailable", wire.get("dischargeVsDesignRatioMahState").asString)
        assertEquals("unavailable", wire.get("designCapacitySource").asString)
    }

    @Test
    fun `computable indicators are forwarded with the state computed`() {
        val samples = listOf(
            sample(3, 3900f, -2_000_000f), sample(4, 4000f, -2_500_000f), sample(5, 4100f, -3_000_000f)
        )
        val (wire, _) = wireHealth(samples, health.copy(designCapacityMah = 5000, designCapacitySource = "sysfs_charge_full_design"))

        assertEquals(2500.0, wire.get("dischargeRateUnderLoadMa").asDouble, 0.01)
        assertEquals("computed", wire.get("dischargeRateUnderLoadMaState").asString)
        assertEquals(81.65, wire.get("voltageStabilityMv").asDouble, 0.1)
        assertEquals("computed", wire.get("voltageStabilityMvState").asString)
        assertEquals(250.0, wire.get("dischargeVsDesignRatioMah").asDouble, 0.01)
        assertEquals("computed", wire.get("dischargeVsDesignRatioMahState").asString)
        assertEquals(5000, wire.get("designCapacityMah").asInt)
    }

    @Test
    fun `an unread battery level makes the consumed-capacity ratio absent`() {
        val (wire, _) = wireHealth(
            samples = emptyList(),
            snapshot = health.copy(designCapacityMah = 5000, designCapacitySource = "sysfs_charge_full_design"),
            levelStart = -1, levelEnd = -1
        )
        assertFalse(wire.has("dischargeVsDesignRatioMah"))
        assertEquals("not_computed:battery_level_unavailable", wire.get("dischargeVsDesignRatioMahState").asString)
    }

    @Test
    fun `zero cycles, a zero charge counter and a zero energy counter reach the wire as zeros with state read`() {
        val (wire, file) = wireHealth(
            samples = emptyList(),
            snapshot = health.copy(
                cycleCount = 0, cycleCountSource = "sysfs:/sys/class/power_supply/battery/cycle_count", cycleCountState = "read",
                chargeCounterUah = 0L, chargeCounterUahState = "read",
                energyCounterNwh = 0L, energyCounterNwhState = "read"
            )
        )
        assertEquals("a real 0 is a value", 0, wire.get("cycleCount").asInt)
        assertEquals(0L, wire.get("chargeCounterUah").asLong)
        assertEquals(0L, wire.get("energyCounterNwh").asLong)
        for (name in listOf("cycleCountState", "chargeCounterUahState", "energyCounterNwhState")) {
            assertEquals("read", wire.get(name).asString)
            assertEquals("read", file[name])
        }
    }

    @Test
    fun `readings the device did not give are absent keys with their states`() {
        val (wire, _) = wireHealth(samples = emptyList(), snapshot = health)
        for (key in listOf("cycleCount", "chargeCounterUah", "energyCounterNwh")) assertFalse("$key must be absent", wire.has(key))
        assertEquals("not_read:unsupported_by_device", wire.get("cycleCountState").asString)
        assertEquals("not_read:unsupported_by_device", wire.get("chargeCounterUahState").asString)
        assertEquals("not_read:unsupported_by_device", wire.get("energyCounterNwhState").asString)
    }

    @Test
    fun `an unavailable temperature is an absent rise with a state - and no NaN ever reaches the file`() {
        val real = BatteryIndicators.compute(emptyList(), health, 90, 85, Double.NaN, Double.NaN)
        val map = BatteryIndicators.footerMap(real, health)
        assertTrue("a NaN in this map would make Gson fail the footer and the payload",
            map.values.none { it is Double && !it.isFinite() })
        val rt = FooterRoundTrip(tmp.newFolder())
        val r = rt.run(rt.baseFooter().copy(batteryHealth = map))
        val wire = r.wireFooter.getAsJsonObject("batteryHealth")
        for (key in listOf("batteryTempStartC", "batteryTempMaxC", "batteryTempRiseC")) {
            assertFalse("$key must be absent, 0.0 would read as 'did not warm'", wire.has(key))
        }
        assertEquals("not_computed:battery_temp_start_and_max_unavailable", wire.get("batteryTempRiseCState").asString)
    }

    @Test
    fun `a negative temperature rise reaches the wire with its sign`() {
        val real = BatteryIndicators.compute(emptyList(), health, 90, 85, 30.0, 28.0)
        val rt = FooterRoundTrip(tmp.newFolder())
        val r = rt.run(rt.baseFooter().copy(batteryHealth = BatteryIndicators.footerMap(real, health)))
        val wire = r.wireFooter.getAsJsonObject("batteryHealth")
        assertEquals("start above max is -2.0, not a clamped 0.0", -2.0, wire.get("batteryTempRiseC").asDouble, 0.001)
        assertEquals("computed", wire.get("batteryTempRiseCState").asString)
    }

    @Test
    fun `a battery that gained charge ships a negative level drop`() {
        val (wire, _) = wireHealth(samples = emptyList(), snapshot = health, levelStart = 80, levelEnd = 85)
        assertEquals(-5, wire.get("levelDropPct").asInt)
        assertEquals("computed", wire.get("levelDropPctState").asString)
    }

    @Test
    fun `an unread battery level makes the level drop absent with its reason`() {
        val (wire, _) = wireHealth(samples = emptyList(), snapshot = health, levelStart = -1, levelEnd = 85)
        assertFalse(wire.has("levelDropPct"))
        assertEquals("not_computed:battery_level_unavailable", wire.get("levelDropPctState").asString)
    }

    @Test
    fun `the map has the same keys the service always wrote plus the states`() {
        val real = BatteryIndicators.compute(emptyList(), health, 90, 85, 28.0, 35.0)
        val keys = BatteryIndicators.footerMap(real, health).keys
        assertEquals(
            setOf(
                "designCapacityMah", "designCapacitySource", "cycleCount", "cycleCountSource",
                "cycleCountState",
                "dischargeRateUnderLoadMa", "dischargeRateUnderLoadMaState",
                "batteryTempStartC", "batteryTempMaxC", "batteryTempRiseC", "batteryTempRiseCState",
                "voltageStabilityMv", "voltageStabilityMvState", "levelDropPct", "levelDropPctState",
                "dischargeVsDesignRatioMah", "dischargeVsDesignRatioMahState",
                "chargeCounterUah", "chargeCounterUahState", "energyCounterNwh", "energyCounterNwhState"
            ),
            keys
        )
        assertNull(BatteryIndicators.footerMap(real, health)["cycleCount"])
    }
}
