package net.neuralcheck.service.stress.v2

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * fieldsVersion 66 — `skin_temp_c_real` discovery must not report the
 * battery proxy as an available skin sensor. Field reference: Honor
 * MTN-NX1 and Samsung S10 5G were both tagged "available" while 100 % of
 * their skin readings (1829 / 1835) were skinTempSourceBreakdown
 * "battery_proxy".
 */
class ChannelDiscoverySkinTest {

    @Test
    fun `positive reading from sysfs is available`() {
        assertEquals(
            ChannelDiscoveryProbe.ProbeResult.AVAILABLE,
            ChannelDiscoveryProbe.classifySkin(realReading = 36.5f, fromSysfs = true)
        )
    }

    @Test
    fun `positive reading that is only the battery proxy is missing`() {
        // ThermalFallback.read() fills the skin slot with battery temp when
        // no native skin zone exists — the value is >0 but not a skin sensor.
        assertEquals(
            ChannelDiscoveryProbe.ProbeResult.MISSING,
            ChannelDiscoveryProbe.classifySkin(realReading = 36.5f, fromSysfs = false)
        )
    }

    @Test
    fun `sentinel reading is missing regardless of provenance flag`() {
        assertEquals(
            ChannelDiscoveryProbe.ProbeResult.MISSING,
            ChannelDiscoveryProbe.classifySkin(realReading = -1f, fromSysfs = true)
        )
        assertEquals(
            ChannelDiscoveryProbe.ProbeResult.MISSING,
            ChannelDiscoveryProbe.classifySkin(realReading = 0f, fromSysfs = true)
        )
    }
}
